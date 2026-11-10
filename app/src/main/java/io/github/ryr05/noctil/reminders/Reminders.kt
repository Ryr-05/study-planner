package io.github.ryr05.noctil.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.ryr05.noctil.MainActivity
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.R
import io.github.ryr05.noctil.data.db.TaskEntity
import io.github.ryr05.noctil.data.localDate
import io.github.ryr05.noctil.data.localTime
import io.github.ryr05.noctil.data.model.Reminders
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

const val EXTRA_TASK_ID = "io.github.ryr05.noctil.TASK_ID"
const val EXTRA_OPEN_TASK = "io.github.ryr05.noctil.OPEN_TASK"
private const val ACTION_REMIND = "io.github.ryr05.noctil.REMIND"
private const val ACTION_DONE = "io.github.ryr05.noctil.DONE"
private const val ACTION_SNOOZE = "io.github.ryr05.noctil.SNOOZE"
private const val SNOOZE_MINUTES = 10L
private const val NAG_MINUTES = 5L

/** Schedules one alarm per task at its reminder time. */
class ReminderScheduler(private val context: Context) {
    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(task: TaskEntity) {
        cancel(task.id)
        if (task.done || task.deleted) return
        val at = Reminders.triggerAt(task.localDate(), task.localTime(), task.reminder) ?: return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (millis <= System.currentTimeMillis()) return
        scheduleAt(task.id, millis, alarmClock = task.alarm || AlarmPrefs.allReminders(context))
    }

    /**
     * [alarmClock] uses Android's alarm-clock timing: exact even in battery saver, and it shows
     * the alarm icon in the status bar, like the phone's own Clock app.
     */
    fun scheduleAt(taskId: Long, millis: Long, alarmClock: Boolean = false) {
        val pending = pendingIntent(taskId, create = true) ?: return
        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarms.canScheduleExactAlarms() else true
        when {
            exact && alarmClock -> {
                val show = PendingIntent.getActivity(
                    context,
                    taskId.toInt(),
                    Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_TASK, taskId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), pending)
            }
            exact -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    fun cancel(taskId: Long) {
        pendingIntent(taskId, create = false)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(taskId: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .setData("noctil://task/$taskId".toUri())
            .putExtra(EXTRA_TASK_ID, taskId)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}

object Notifications {
    const val CHANNEL_ID = "reminders"
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts when a task is due"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    @SuppressLint("MissingPermission")
    fun showReminder(context: Context, task: TaskEntity, listName: String?, placeName: String? = null) {
        if (!canNotify(context)) return
        val id = task.id
        val open = PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TASK, id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val detail = listOfNotNull(
            placeName?.let { "You're at $it" } ?: task.localTime()?.let { "Due ${it.format(timeFormat)}" } ?: "Due today",
            listName,
            if (task.nag) "Reminding every 5 minutes" else null,
        ).joinToString("  |  ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB9A3FF.toInt())
            .setContentTitle(task.title)
            .setContentText(detail)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(!task.nag)
            .setOngoing(task.nag)
            .setContentIntent(open)
            .addAction(0, "Done", actionIntent(context, ACTION_DONE, id))
            .addAction(0, "Snooze 10 min", actionIntent(context, ACTION_SNOOZE, id))
            .build()
        NotificationManagerCompat.from(context).notify(id.toInt(), notification)
    }

    fun cancel(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskId.toInt())
    }

    private fun actionIntent(context: Context, action: String, taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(action)
                .setData("noctil://task/$taskId".toUri())
                .putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** Fires at the reminder time and shows the notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (id < 0) return
        val app = context.applicationContext as NoctilApplication
        val pending = goAsync()
        app.repository.scope.launch {
            try {
                val task = app.repository.task(id)
                if (task != null && !task.done && !task.deleted) {
                    val ring = (task.alarm || AlarmPrefs.allReminders(context)) && Notifications.canNotify(context)
                    // Ring like an alarm clock; if Android refuses, fall back to a normal reminder.
                    if (!ring || !AlarmService.start(context, id)) {
                        Notifications.showReminder(context, task, app.repository.listName(task.listId))
                        // "Keep reminding me": come back in a few minutes until Done or Snooze is tapped.
                        if (task.nag) app.scheduler.scheduleAt(id, System.currentTimeMillis() + NAG_MINUTES * 60_000)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Handles the Done and Snooze buttons on a reminder. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (id < 0) return
        val app = context.applicationContext as NoctilApplication
        Notifications.cancel(context, id)
        when (intent.action) {
            ACTION_DONE -> {
                val pending = goAsync()
                app.repository.scope.launch {
                    try {
                        app.repository.setDoneNow(id, true)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_SNOOZE -> app.scheduler.scheduleAt(id, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000)
        }
    }
}

/** Alarms are cleared on reboot, app update and clock changes, so put them back. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NoctilApplication
        val pending = goAsync()
        app.repository.scope.launch {
            try {
                app.repository.rescheduleAllNow()
            } finally {
                pending.finish()
            }
        }
    }
}
