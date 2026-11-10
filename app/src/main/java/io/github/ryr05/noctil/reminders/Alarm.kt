package io.github.ryr05.noctil.reminders

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.ryr05.noctil.MainActivity
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.R
import io.github.ryr05.noctil.data.localTime
import io.github.ryr05.noctil.ui.components.AuroraBackground
import io.github.ryr05.noctil.ui.theme.Palette
import io.github.ryr05.noctil.ui.theme.StudyTheme
import io.github.ryr05.noctil.ui.theme.rememberReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What's ringing right now, shown on the alarm screen. */
data class Ringing(val taskId: Long, val title: String, val detail: String)

/**
 * Rings a task like a phone alarm: loud sound on the alarm volume, vibration, and a full-screen
 * page that shows even on the lockscreen. Keeps going until Done, Snooze or Stop, or 10 minutes.
 */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var focus: AudioFocusRequest? = null
    private var current: Long = NO_TASK
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_TASK_ID, NO_TASK) ?: NO_TASK
        when (intent?.action) {
            ACTION_DONE -> {
                finishWith { app -> if (id >= 0) app.repository.setDoneNow(id, true) }
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                finishWith { app ->
                    if (id >= 0) {
                        val minutes = AlarmPrefs.snoozeMinutes(this)
                        app.scheduler.scheduleAt(id, System.currentTimeMillis() + minutes * 60_000L, alarmClock = true)
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                finishWith { }
                return START_NOT_STICKY
            }
        }
        // Must go to the foreground right away; the task's details fill in a moment later.
        goForeground(Ringing(id, "Alarm", ""), full = false)
        current = id
        scope.launch {
            val ringing = if (id == PREVIEW) {
                Ringing(PREVIEW, "This is how alarms look", "Tap Stop to end the test")
            } else {
                val app = applicationContext as NoctilApplication
                val task = app.repository.task(id)
                if (task == null || task.done || task.deleted) {
                    stopEverything()
                    return@launch
                }
                val list = app.repository.listName(task.listId)
                val due = task.localTime()?.let { "Due ${it.format(timeFormat)}" } ?: "Due today"
                Ringing(id, task.title, listOfNotNull(due, list).joinToString("  |  "))
            }
            _ringing.value = ringing
            goForeground(ringing, full = true)
            startSound()
            startVibration()
            handler.removeCallbacksAndMessages(TIMEOUT_TOKEN)
            handler.postAtTime({ timeOut() }, TIMEOUT_TOKEN, android.os.SystemClock.uptimeMillis() + RING_LIMIT_MS)
        }
        return START_NOT_STICKY
    }

    /** Nobody answered: stop the noise and leave a normal reminder behind. */
    private fun timeOut() {
        val id = current
        scope.launch {
            val app = applicationContext as NoctilApplication
            if (id >= 0) {
                app.repository.task(id)?.let { task ->
                    Notifications.showReminder(this@AlarmService, task, app.repository.listName(task.listId))
                    if (task.nag) app.scheduler.scheduleAt(id, System.currentTimeMillis() + 5 * 60_000L)
                }
            }
            stopEverything()
        }
    }

    private fun finishWith(action: suspend (NoctilApplication) -> Unit) {
        val app = applicationContext as NoctilApplication
        stopSound()
        app.repository.scope.launch { action(app) }
        stopEverything()
    }

    private fun goForeground(r: Ringing, full: Boolean) {
        val notification = buildNotification(r, full)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** [full] posts the full-screen page too; the first quick placeholder doesn't, to avoid a flash. */
    private fun buildNotification(r: Ringing, full: Boolean): android.app.Notification {
        val screen = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB9A3FF.toInt())
            .setContentTitle(r.title)
            .setContentText(r.detail.ifEmpty { "Alarm" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(screen)
        if (!full) return builder.build()
        builder.setFullScreenIntent(screen, true)
        if (r.taskId >= 0) {
            builder.addAction(0, "Done", serviceIntent(this, ACTION_DONE, r.taskId))
            builder.addAction(0, "Snooze ${AlarmPrefs.snoozeMinutes(this)} min", serviceIntent(this, ACTION_SNOOZE, r.taskId))
        }
        builder.addAction(0, "Stop", serviceIntent(this, ACTION_STOP, r.taskId))
        return builder.build()
    }

    private fun startSound() {
        stopSound()
        val audio = getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).build()
            .also { audio.requestAudioFocus(it) }
        val gentle = AlarmPrefs.gentle(this)
        for (uri in AlarmSounds.playable(this, AlarmPrefs.sound(this))) {
            val started = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setWakeMode(this@AlarmService, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    prepare()
                    if (gentle) setVolume(0.15f, 0.15f)
                    start()
                }
            }.getOrNull()
            if (started != null) {
                player = started
                break
            }
        }
        if (gentle) rampUp(0.15f)
    }

    private fun rampUp(volume: Float) {
        val p = player ?: return
        val next = (volume + 0.06f).coerceAtMost(1f)
        runCatching { p.setVolume(next, next) }
        if (next < 1f) handler.postDelayed({ rampUp(next) }, 1_000)
    }

    @SuppressLint("MissingPermission")
    private fun startVibration() {
        if (!AlarmPrefs.vibrate(this)) return
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (!v.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        vibrator = v
    }

    private fun stopSound() {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun stopEverything() {
        stopSound()
        _ringing.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSound()
        _ringing.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "alarms"
        private const val NOTIFICATION_ID = 7_400
        private const val NO_TASK = -2L
        /** A test ring from settings, not tied to a task. */
        const val PREVIEW = -1L
        private const val RING_LIMIT_MS = 10 * 60_000L
        private val TIMEOUT_TOKEN = Any()
        private const val ACTION_DONE = "io.github.ryr05.noctil.ALARM_DONE"
        private const val ACTION_SNOOZE = "io.github.ryr05.noctil.ALARM_SNOOZE"
        private const val ACTION_STOP = "io.github.ryr05.noctil.ALARM_STOP"

        private val _ringing = MutableStateFlow<Ringing?>(null)
        val ringing: StateFlow<Ringing?> = _ringing

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tasks set to ring like an alarm clock"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Starts ringing. Returns false if Android didn't allow it, so the caller can fall back to a notification. */
        fun start(context: Context, taskId: Long): Boolean = runCatching {
            ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java).putExtra(EXTRA_TASK_ID, taskId))
        }.isSuccess

        /** Rings a test alarm now and opens the alarm screen (Noctil is open, so it's allowed). */
        fun preview(context: Context) {
            if (start(context, PREVIEW)) {
                context.startActivity(Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        fun done(context: Context, taskId: Long) = context.startService(action(context, ACTION_DONE, taskId))
        fun snooze(context: Context, taskId: Long) = context.startService(action(context, ACTION_SNOOZE, taskId))
        fun stop(context: Context, taskId: Long) = context.startService(action(context, ACTION_STOP, taskId))

        private fun action(context: Context, action: String, taskId: Long) =
            Intent(context, AlarmService::class.java).setAction(action)
                .setData("noctil://alarm/$taskId".toUri())
                .putExtra(EXTRA_TASK_ID, taskId)

        private fun serviceIntent(context: Context, action: String, taskId: Long): PendingIntent =
            PendingIntent.getService(context, action.hashCode(), action(context, action, taskId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** The full-screen alarm page. Shows over the lockscreen and turns the screen on. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            StudyTheme {
                val ringing by AlarmService.ringing.collectAsState()
                var seen by remember { mutableStateOf(false) }
                LaunchedEffect(ringing) {
                    if (ringing != null) seen = true
                    // Stopped from the notification, or timed out: close this page too.
                    if (ringing == null && seen) finish()
                    if (ringing == null && !seen) {
                        delay(3_000)
                        if (AlarmService.ringing.value == null) finish()
                    }
                }
                AlarmScreen(
                    ringing = ringing,
                    snoozeMinutes = AlarmPrefs.snoozeMinutes(this),
                    onDone = { ringing?.let { AlarmService.done(this, it.taskId) }; finish() },
                    onSnooze = { ringing?.let { AlarmService.snooze(this, it.taskId) }; finish() },
                    onStop = { AlarmService.stop(this, ringing?.taskId ?: AlarmService.PREVIEW); finish() },
                    onOpen = {
                        val r = ringing
                        if (r != null && r.taskId >= 0) {
                            AlarmService.stop(this, r.taskId)
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .putExtra(EXTRA_OPEN_TASK, r.taskId)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                            )
                        }
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
fun AlarmScreen(
    ringing: Ringing?,
    snoozeMinutes: Int,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
    onOpen: () -> Unit,
) {
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(1_000)
        }
    }
    val reduceMotion = rememberReduceMotion()
    val pulse = rememberInfiniteTransition(label = "alarmPulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (reduceMotion) 1f else 1.18f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val glow by pulse.animateFloat(
        initialValue = 0.10f,
        targetValue = if (reduceMotion) 0.10f else 0.28f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val isTask = (ringing?.taskId ?: -1) >= 0

    Box(Modifier.fillMaxSize().background(Palette.Night)) {
        AuroraBackground()
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(28.dp))
            Text(
                now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                color = Palette.TextPrimary,
            )
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextSecondary,
            )
            Spacer(Modifier.height(40.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
                Box(
                    Modifier
                        .size(150.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(Palette.Lavender.copy(alpha = glow)),
                )
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Palette.LavenderLight, Palette.Lavender))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Alarm, contentDescription = null, tint = Palette.OnLavender, modifier = Modifier.size(52.dp))
                }
            }
            Spacer(Modifier.height(36.dp))
            Text(
                ringing?.title ?: "Alarm",
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = isTask, onClickLabel = "Open task", onClick = onOpen)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            ringing?.detail?.takeIf { it.isNotEmpty() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isTask) {
                BigButton("Done", Icons.Rounded.Check, primary = true, onClick = onDone)
                Spacer(Modifier.height(12.dp))
                BigButton("Snooze $snoozeMinutes min", Icons.Rounded.Snooze, primary = false, onClick = onSnooze)
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Stop",
                style = MaterialTheme.typography.titleMedium,
                color = if (isTask) Palette.TextSecondary else Palette.OnLavender,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(CircleShape)
                    .then(if (isTask) Modifier else Modifier.background(Palette.Lavender))
                    .clickable(role = Role.Button, onClick = onStop)
                    .padding(vertical = 16.dp),
            )
        }
        }
        }
    }
}

@Composable
private fun BigButton(label: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(CircleShape)
            .then(
                if (primary) Modifier.background(Brush.verticalGradient(listOf(Palette.LavenderLight, Palette.Lavender)))
                else Modifier.background(Palette.Surface).border(1.dp, Palette.Outline, CircleShape),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (primary) Palette.OnLavender else Palette.TextPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (primary) Palette.OnLavender else Palette.TextPrimary)
    }
}
