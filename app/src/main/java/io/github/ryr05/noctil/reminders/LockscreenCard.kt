package io.github.ryr05.noctil.reminders

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.ryr05.noctil.Launch
import io.github.ryr05.noctil.R
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.TaskItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The lockscreen Today card: a quiet, pinned notification listing today's tasks.
 * Most Android phones no longer allow app widgets on the lockscreen, but notifications are
 * shown there, so this is the reliable way to see today's tasks without unlocking.
 * It makes no sound and can be turned off in Me > Lockscreen card.
 */
object LockscreenCard {
    private const val CHANNEL_ID = "today_card"
    private const val NOTIFICATION_ID = 7_000_001
    private const val PREF = "lockscreen_card"
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Lockscreen Today card", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Shows today's tasks on your lockscreen. Silent."
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("noctil", Context.MODE_PRIVATE).getBoolean(PREF, true)

    fun setEnabled(context: Context, enabled: Boolean, snapshot: Snapshot) {
        context.getSharedPreferences("noctil", Context.MODE_PRIVATE).edit().putBoolean(PREF, enabled).apply()
        update(context, snapshot)
    }

    @SuppressLint("MissingPermission")
    fun update(context: Context, snapshot: Snapshot) {
        if (!snapshot.loaded) return
        if (!isEnabled(context) || !Notifications.canNotify(context)) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        val today = LocalDate.now()
        val open = snapshot.tasks.filter { it.parentId == null && !it.done && it.date != null && !it.date.isAfter(today) }
            .sortedWith(compareBy<TaskItem>({ !it.date!!.isBefore(today) }, { it.time ?: LocalTime.MAX }, { -it.priority.value }))
        val overdue = open.count { it.date!!.isBefore(today) }

        val title = when (open.size) {
            0 -> "All clear for today"
            1 -> "1 task today"
            else -> "${open.size} tasks today"
        }
        val summary = when {
            open.isEmpty() -> "Tap to plan tomorrow or add a task"
            overdue > 0 -> "${open.first().title}  ($overdue overdue)"
            else -> open.first().title
        }
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        open.take(6).forEach { style.addLine(line(it, today)) }
        if (open.size > 6) style.setSummaryText("+${open.size - 6} more")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB9A3FF.toInt())
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(if (open.isEmpty()) null else style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(Launch.pending(context, Launch.today(context), 9_001))
            .addAction(0, "Add task", Launch.pending(context, Launch.quickAdd(context), 9_002))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun line(task: TaskItem, today: LocalDate): String {
        val prefix = when {
            task.date!!.isBefore(today) -> "Overdue"
            task.time != null -> task.time.format(timeFormat)
            else -> "Today"
        }
        return "$prefix   ${task.title}"
    }
}
