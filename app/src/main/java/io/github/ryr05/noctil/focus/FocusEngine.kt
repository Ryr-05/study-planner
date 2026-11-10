package io.github.ryr05.noctil.focus

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.ryr05.noctil.Launch
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.R
import io.github.ryr05.noctil.reminders.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class FocusKind(val label: String, val defaultMinutes: Int) {
    Focus("Focus", 25),
    ShortBreak("Short break", 5),
    LongBreak("Long break", 15),
    Stopwatch("Stopwatch", 0),
}

/**
 * The timer's state. Times are wall-clock milliseconds, so the countdown stays right even when
 * the app is closed: the notification counts down by itself and an alarm fires at the end.
 */
data class FocusState(
    val kind: FocusKind = FocusKind.Focus,
    val minutes: Int = FocusKind.Focus.defaultMinutes,
    val running: Boolean = false,
    /** When the countdown ends (countdowns only, while running). */
    val endAt: Long = 0,
    /** Time left when paused or not started (countdowns). */
    val remainingMs: Long = minutes * 60_000L,
    /** When the current run started, for the stopwatch and for recording the session. */
    val startedAt: Long = 0,
    /** Stopwatch time from earlier runs before a pause. */
    val elapsedMs: Long = 0,
    val taskId: Long? = null,
    val noise: Noise = Noise.Off,
    /** Focus sessions finished in a row, to know when a long break is due. */
    val streak: Int = 0,
) {
    fun remainingAt(now: Long): Long = if (running) (endAt - now).coerceAtLeast(0) else remainingMs
    fun elapsedAt(now: Long): Long = elapsedMs + if (running) now - startedAt else 0
    val started: Boolean get() = running || (kind == FocusKind.Stopwatch && elapsedMs > 0) ||
        (kind != FocusKind.Stopwatch && remainingMs < minutes * 60_000L)
}

/** Runs the focus timer. One shared instance for the whole app, saved across restarts. */
object FocusEngine {
    private const val PREFS = "focus"
    private const val CHANNEL = "focus"
    private const val DONE_CHANNEL = Notifications.CHANNEL_ID
    const val NOTIFICATION_ID = 7_000_002
    private const val DONE_ID = 7_000_003
    const val ACTION_PAUSE = "io.github.ryr05.noctil.focus.PAUSE"
    const val ACTION_STOP = "io.github.ryr05.noctil.focus.STOP"
    private const val ACTION_END = "io.github.ryr05.noctil.focus.END"

    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state

    fun init(context: Context) {
        createChannel(context)
        _state.value = load(context)
        val s = _state.value
        if (s.running && s.kind != FocusKind.Stopwatch && s.endAt <= System.currentTimeMillis()) {
            // The countdown ended while the app was closed.
            complete(context)
        } else if (s.running) {
            if (s.kind != FocusKind.Stopwatch) scheduleEnd(context, s.endAt)
            runCatching { FocusService.start(context) }
        }
    }

    fun select(context: Context, kind: FocusKind) {
        if (_state.value.running) return
        val minutes = if (kind == FocusKind.Focus) savedFocusMinutes(context) else kind.defaultMinutes
        set(context, _state.value.copy(kind = kind, minutes = minutes, remainingMs = minutes * 60_000L, elapsedMs = 0))
    }

    fun setMinutes(context: Context, minutes: Int) {
        val s = _state.value
        if (s.running || s.kind == FocusKind.Stopwatch) return
        if (s.kind == FocusKind.Focus) prefs(context).edit().putInt("focus_minutes", minutes).apply()
        set(context, s.copy(minutes = minutes, remainingMs = minutes * 60_000L))
    }

    fun setTask(context: Context, taskId: Long?) = set(context, _state.value.copy(taskId = taskId))

    fun setNoise(context: Context, noise: Noise) {
        set(context, _state.value.copy(noise = noise))
        if (_state.value.running) runCatching { FocusService.start(context) }
    }

    fun start(context: Context) {
        val s = _state.value
        if (s.running) return
        val now = System.currentTimeMillis()
        val next = if (s.kind == FocusKind.Stopwatch) {
            s.copy(running = true, startedAt = now)
        } else {
            val remaining = if (s.remainingMs <= 0) s.minutes * 60_000L else s.remainingMs
            s.copy(running = true, endAt = now + remaining, remainingMs = remaining, startedAt = if (s.started) s.startedAt else now)
        }
        set(context, next)
        if (next.kind != FocusKind.Stopwatch) scheduleEnd(context, next.endAt)
        runCatching { FocusService.start(context) }
    }

    fun pause(context: Context) {
        val s = _state.value
        if (!s.running) return
        val now = System.currentTimeMillis()
        cancelEnd(context)
        set(
            context,
            if (s.kind == FocusKind.Stopwatch) s.copy(running = false, elapsedMs = s.elapsedAt(now))
            else s.copy(running = false, remainingMs = s.remainingAt(now)),
        )
        FocusService.stop(context)
    }

    /** Stops the timer. A stopwatch run of a minute or more is saved to your stats. */
    fun reset(context: Context) {
        val s = _state.value
        val now = System.currentTimeMillis()
        cancelEnd(context)
        if (s.kind == FocusKind.Stopwatch) {
            val minutes = (s.elapsedAt(now) / 60_000L).toInt()
            if (minutes >= 1) record(context, s.startedAt.takeIf { it > 0 } ?: now, minutes, s.taskId, "stopwatch")
        }
        set(context, s.copy(running = false, remainingMs = s.minutes * 60_000L, elapsedMs = 0, endAt = 0, startedAt = 0))
        FocusService.stop(context)
    }

    /** Called when a countdown reaches zero. */
    fun complete(context: Context) {
        val s = _state.value
        cancelEnd(context)
        FocusService.stop(context)
        val finishedFocus = s.kind == FocusKind.Focus
        if (finishedFocus) record(context, s.startedAt.takeIf { it > 0 } ?: (System.currentTimeMillis() - s.minutes * 60_000L), s.minutes, s.taskId, "focus")
        val streak = if (finishedFocus) s.streak + 1 else s.streak
        val nextKind = when {
            !finishedFocus -> FocusKind.Focus
            streak % 4 == 0 -> FocusKind.LongBreak
            else -> FocusKind.ShortBreak
        }
        val nextMinutes = if (nextKind == FocusKind.Focus) savedFocusMinutes(context) else nextKind.defaultMinutes
        set(
            context,
            s.copy(
                kind = nextKind, minutes = nextMinutes, running = false, remainingMs = nextMinutes * 60_000L,
                endAt = 0, startedAt = 0, elapsedMs = 0, streak = if (nextKind == FocusKind.LongBreak) 0 else streak,
            ),
        )
        notifyDone(context, finishedFocus, nextKind)
    }

    private fun record(context: Context, startedAt: Long, minutes: Int, taskId: Long?, kind: String) {
        val app = context.applicationContext as NoctilApplication
        app.repository.scope.launch { app.repository.recordFocus(startedAt, minutes, taskId, kind) }
    }

    private fun savedFocusMinutes(context: Context) = prefs(context).getInt("focus_minutes", FocusKind.Focus.defaultMinutes)

    private fun set(context: Context, s: FocusState) {
        _state.value = s
        save(context, s)
        if (s.running) FocusService.refresh(context)
    }

    // ---------- Notifications ----------

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Focus timer", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "The countdown while you focus. Silent."
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** The ongoing countdown, shown in the status bar and on the lockscreen. */
    fun buildOngoing(context: Context, s: FocusState): Notification {
        val now = System.currentTimeMillis()
        val app = context.applicationContext as NoctilApplication
        val taskTitle = s.taskId?.let { id -> app.repository.snapshot.value.byId[id]?.title }
        val title = when (s.kind) {
            FocusKind.Focus -> taskTitle?.let { "Focusing: $it" } ?: "Focusing"
            FocusKind.Stopwatch -> taskTitle?.let { "Stopwatch: $it" } ?: "Stopwatch running"
            else -> s.kind.label
        }
        val text = buildString {
            if (s.kind == FocusKind.Stopwatch) append("Tap Stop when you're done") else append("${s.minutes} minute ${s.kind.label.lowercase()}")
            if (s.noise != Noise.Off) append(", ${s.noise.label.lowercase()}")
        }
        val minutesLeft = ((s.remainingAt(now) + 59_999) / 60_000L).toInt()
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB9A3FF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(s.kind != FocusKind.Stopwatch)
            .setWhen(if (s.kind == FocusKind.Stopwatch) s.startedAt - s.elapsedMs else s.endAt)
            // Android 16 shows this as a live countdown chip in the status bar.
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(if (s.kind == FocusKind.Stopwatch) "Focus" else "${minutesLeft}m")
            .setContentIntent(Launch.pending(context, Launch.focus(context), 9_200))
            .addAction(0, "Pause", actionIntent(context, ACTION_PAUSE))
            .addAction(0, "Stop", actionIntent(context, ACTION_STOP))
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun notifyDone(context: Context, finishedFocus: Boolean, next: FocusKind) {
        if (!Notifications.canNotify(context)) return
        val notification = NotificationCompat.Builder(context, DONE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB9A3FF.toInt())
            .setContentTitle(if (finishedFocus) "Focus session done" else "Break's over")
            .setContentText(if (finishedFocus) "Nice work. Time for a ${next.label.lowercase()}." else "Ready for another focus session?")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(Launch.pending(context, Launch.focus(context), 9_201))
            .addAction(0, "Start ${next.label.lowercase()}", Launch.pending(context, Launch.focus(context, startNext = true), 9_202))
            .build()
        NotificationManagerCompat.from(context).notify(DONE_ID, notification)
    }

    private fun actionIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, FocusReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // ---------- End alarm ----------

    private fun endIntent(context: Context, create: Boolean): PendingIntent? = PendingIntent.getBroadcast(
        context, 7_100, Intent(context, FocusReceiver::class.java).setAction(ACTION_END),
        PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE,
    )

    private fun scheduleEnd(context: Context, at: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = endIntent(context, true) ?: return
        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarms.canScheduleExactAlarms() else true
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    private fun cancelEnd(context: Context) {
        endIntent(context, false)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }

    // ---------- Saving ----------

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun save(context: Context, s: FocusState) {
        prefs(context).edit()
            .putString("kind", s.kind.name).putInt("minutes", s.minutes).putBoolean("running", s.running)
            .putLong("endAt", s.endAt).putLong("remaining", s.remainingMs).putLong("startedAt", s.startedAt)
            .putLong("elapsed", s.elapsedMs).putLong("taskId", s.taskId ?: -1L).putString("noise", s.noise.name)
            .putInt("streak", s.streak)
            .apply()
    }

    private fun load(context: Context): FocusState {
        val p = prefs(context)
        val kind = runCatching { FocusKind.valueOf(p.getString("kind", null) ?: "") }.getOrDefault(FocusKind.Focus)
        val minutes = p.getInt("minutes", if (kind == FocusKind.Focus) savedFocusMinutes(context) else kind.defaultMinutes)
        return FocusState(
            kind = kind,
            minutes = minutes,
            running = p.getBoolean("running", false),
            endAt = p.getLong("endAt", 0),
            remainingMs = p.getLong("remaining", minutes * 60_000L),
            startedAt = p.getLong("startedAt", 0),
            elapsedMs = p.getLong("elapsed", 0),
            taskId = p.getLong("taskId", -1L).takeIf { it > 0 },
            noise = runCatching { Noise.valueOf(p.getString("noise", null) ?: "") }.getOrDefault(Noise.Off),
            streak = p.getInt("streak", 0),
        )
    }
}

/** Handles the end-of-timer alarm and the Pause / Stop buttons on the notification. */
class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            FocusEngine.ACTION_PAUSE -> FocusEngine.pause(context)
            FocusEngine.ACTION_STOP -> FocusEngine.reset(context)
            else -> FocusEngine.complete(context)
        }
    }
}

/**
 * Keeps the countdown notification up and plays focus sounds while the screen is off.
 * Only runs while a timer is running.
 */
class FocusService : Service() {
    private val player = NoisePlayer()
    private var playing: Noise = Noise.Off

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val s = FocusEngine.state.value
        if (!s.running) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = FocusEngine.buildOngoing(this, s)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = if (s.noise != Noise.Off) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            startForeground(FocusEngine.NOTIFICATION_ID, notification, type)
        } else {
            startForeground(FocusEngine.NOTIFICATION_ID, notification)
        }
        if (s.noise != playing) {
            playing = s.noise
            player.play(s.noise)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player.stop()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FocusService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusService::class.java))
            NotificationManagerCompat.from(context).cancel(FocusEngine.NOTIFICATION_ID)
        }

        /** Updates the notification (title, task, sound) while running. */
        @SuppressLint("MissingPermission")
        fun refresh(context: Context) {
            if (!Notifications.canNotify(context) && Build.VERSION.SDK_INT >= 33) return
            NotificationManagerCompat.from(context).notify(FocusEngine.NOTIFICATION_ID, FocusEngine.buildOngoing(context, FocusEngine.state.value))
        }
    }
}
