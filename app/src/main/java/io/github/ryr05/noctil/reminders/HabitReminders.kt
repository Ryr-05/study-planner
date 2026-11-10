package io.github.ryr05.noctil.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import io.github.ryr05.noctil.Launch
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.R
import io.github.ryr05.noctil.data.model.Habit
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Daily habit reminders: one alarm per habit, for the next day it's due. */
object HabitReminders {
    private const val ACTION_REMIND = "io.github.ryr05.noctil.HABIT_REMIND"
    private const val ACTION_DONE = "io.github.ryr05.noctil.HABIT_DONE"
    private const val EXTRA_HABIT = "io.github.ryr05.noctil.HABIT_ID"
    private const val NOTIFICATION_BASE = 8_000_000

    fun scheduleAll(context: Context, habits: List<Habit>) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        habits.forEach { habit ->
            pending(context, habit.id, ACTION_REMIND, create = false)?.let { alarms.cancel(it) }
            val time = habit.reminder ?: return@forEach
            val now = LocalDateTime.now()
            val today = now.toLocalDate()
            val at = (0L until 8L).map { today.plusDays(it) }.firstOrNull { day ->
                habit.isDue(day) && LocalDateTime.of(day, time).isAfter(now) && !(day == today && habit.isDone(day))
            }?.let { LocalDateTime.of(it, time) }
            val trigger = at ?: return@forEach
            val millis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            pending(context, habit.id, ACTION_REMIND, create = true)?.let {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, it)
            }
        }
    }

    internal fun pending(context: Context, habitId: Long, action: String, create: Boolean): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, HabitReminderReceiver::class.java)
                .setAction(action)
                .setData("noctil://habit/$habitId".toUri())
                .putExtra(EXTRA_HABIT, habitId),
            PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE,
        )

    @SuppressLint("MissingPermission")
    internal fun show(context: Context, habit: Habit) {
        if (!Notifications.canNotify(context)) return
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB9A3FF.toInt())
            .setContentTitle(habit.name)
            .setContentText(
                habit.currentStreak(LocalDate.now()).let { if (it > 0) "Keep your $it day streak going" else "A small step today" },
            )
            .setAutoCancel(true)
            .setContentIntent(Launch.pending(context, Launch.habits(context), 9_300))
            .addAction(0, if (habit.goal > 1) "Check in" else "Done", pending(context, habit.id, ACTION_DONE, create = true))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE + habit.id.toInt(), notification)
    }

    internal fun handle(context: Context, intent: Intent, finish: () -> Unit) {
        val id = intent.getLongExtra(EXTRA_HABIT, -1L)
        val app = context.applicationContext as NoctilApplication
        app.repository.scope.launch {
            try {
                if (id > 0) {
                    if (intent.action == ACTION_DONE) {
                        app.repository.tapHabitNow(id, LocalDate.now())
                        NotificationManagerCompat.from(context).cancel(NOTIFICATION_BASE + id.toInt())
                    } else {
                        val habit = app.repository.snapshot.value.habits.firstOrNull { it.id == id }
                        if (habit != null && !habit.isDone(LocalDate.now())) show(context, habit)
                    }
                }
                scheduleAll(context, app.repository.snapshot.value.habits)
            } finally {
                finish()
            }
        }
    }
}

class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        HabitReminders.handle(context, intent) { pending.finish() }
    }
}
