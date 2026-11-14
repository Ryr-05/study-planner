package io.github.ryr05.noctil.plan

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import io.github.ryr05.noctil.Launch
import io.github.ryr05.noctil.R
import io.github.ryr05.noctil.reminders.Notifications
import io.github.ryr05.noctil.reminders.Notifications.ownGroup
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * "Remind me" for a plan: a nudge when each block starts, with a tap that opens Focus on that
 * task. Kept apart from task reminders, so a plan never moves or replaces a task's own reminder.
 */
object PlanReminders {
    private const val ACTION = "io.github.ryr05.noctil.PLAN_NUDGE"
    private const val EXTRA_TASK = "task"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_UNTIL = "until"
    private const val BASE_CODE = 9_100
    private const val MAX = 24
    private const val NOTIFICATION_BASE = 8_100

    /** Schedules a nudge for each block that hasn't started yet. Returns how many were set. */
    fun schedule(context: Context, plan: Planner.Plan, day: LocalDate = LocalDate.now()): Int {
        cancelAll(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        var count = 0
        plan.work.filter { it.part == 1 }.take(MAX).forEachIndexed { i, block ->
            val at = block.start.atDate(day).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (at <= now + 30_000) return@forEachIndexed
            val until = plan.work.last { it.taskId == block.taskId }.end
            val pending = pendingIntent(context, i, block.taskId, block.title, until, create = true) ?: return@forEachIndexed
            val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarms.canScheduleExactAlarms() else true
            if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            count++
        }
        return count
    }

    fun cancelAll(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        for (i in 0 until MAX) pendingIntent(context, i, 0, "", LocalTime.MIDNIGHT, create = false)?.let { alarms.cancel(it); it.cancel() }
    }

    private fun pendingIntent(context: Context, index: Int, taskId: Long, title: String, until: LocalTime, create: Boolean): PendingIntent? {
        val intent = Intent(context, PlanReminderReceiver::class.java)
            .setAction(ACTION)
            .setData("noctil://plan/$index".toUri())
            .putExtra(EXTRA_TASK, taskId)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_UNTIL, until.toSecondOfDay())
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, BASE_CODE + index, intent, flags)
    }

    @SuppressLint("MissingPermission")
    internal fun show(context: Context, intent: Intent) {
        if (!Notifications.canNotify(context)) return
        val taskId = intent.getLongExtra(EXTRA_TASK, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val until = LocalTime.ofSecondOfDay(intent.getIntExtra(EXTRA_UNTIL, 0).toLong())
        val open = Launch.pending(context, Launch.focus(context).putExtra(Launch.EXTRA_FOCUS_TASK, taskId), (NOTIFICATION_BASE + taskId).toInt())
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB9A3FF.toInt())
            .setContentTitle("Time for: $title")
            .setContentText("Planned until ${until.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}. Tap to focus on it.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .ownGroup("plan_$taskId")
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify((NOTIFICATION_BASE + taskId).toInt(), notification)
    }
}

class PlanReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = PlanReminders.show(context, intent)
}
