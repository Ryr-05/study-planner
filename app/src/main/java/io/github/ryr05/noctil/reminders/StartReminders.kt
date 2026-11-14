package io.github.ryr05.noctil.reminders

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
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.R
import io.github.ryr05.noctil.data.db.TaskEntity
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.localDate
import io.github.ryr05.noctil.data.localTime
import io.github.ryr05.noctil.data.startAt
import io.github.ryr05.noctil.data.withStart
import io.github.ryr05.noctil.reminders.Notifications.ownGroup
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * "Time to start": a nudge when the student planned to begin a task, separate from the
 * deadline reminder. A deadline reminder 30 minutes before is too late for an essay; this one
 * comes when there is still time to do the work.
 */
object StartReminders {
    private const val ACTION_START = "io.github.ryr05.noctil.START_NUDGE"
    private const val ACTION_LATER = "io.github.ryr05.noctil.START_LATER"
    private const val ACTION_DONE = "io.github.ryr05.noctil.START_DONE"

    /** The time a suggested start lands on, the evening before the deadline. */
    val EVENING: LocalTime = LocalTime.of(19, 0)

    /** How big a task looks, which decides how early to suggest starting. */
    enum class Size { Tiny, Normal, Big }

    private val bigWords = Regex(
        "\\b(essays?|papers?|projects?|reports?|presentations?|thesis|dissertation|research|portfolio|" +
            "study|studying|review|revise|revision|chapters?|lab\\s+reports?|capstone|proposal|draft)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val tinyWords = Regex(
        "^\\s*(email|e-mail|call|text|message|reply|pay|buy|print|sign|book|pick\\s+up|return|drop\\s+off|" +
            "renew|register|rsvp|remind|ask|tell|send|check)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Exams, essays and long tasks are Big; "email prof", "pay rent" and 15-minute jobs are Tiny. */
    fun sizeOf(title: String, tags: Collection<String>, priority: Priority, minutes: Int?): Size = when {
        io.github.ryr05.noctil.data.model.looksLikeExam(title, tags) -> Size.Big
        minutes != null && minutes >= 90 -> Size.Big
        bigWords.containsMatchIn(title) -> Size.Big
        priority == Priority.High -> Size.Big
        minutes != null && minutes <= 15 -> Size.Tiny
        tinyWords.containsMatchIn(title) -> Size.Tiny
        else -> Size.Normal
    }

    /** "Start [daysBefore] days before the deadline at [time]". */
    data class Lead(val daysBefore: Int, val time: LocalTime) {
        fun encode() = "$daysBefore@${time.toSecondOfDay() / 60}"

        companion object {
            fun decode(s: String): Lead? {
                val (d, m) = s.split("@").takeIf { it.size == 2 } ?: return null
                val days = d.toIntOrNull()?.takeIf { it in 0..14 } ?: return null
                val minutes = m.toIntOrNull()?.takeIf { it in 0 until 24 * 60 } ?: return null
                return Lead(days, LocalTime.of(minutes / 60, minutes % 60))
            }

            /** How far [start] is ahead of [dueDate], as a Lead. */
            fun of(start: LocalDateTime, dueDate: LocalDate): Lead? {
                val days = java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(), dueDate).toInt()
                return if (days in 0..14) Lead(days, start.toLocalTime().withSecond(0).withNano(0)) else null
            }
        }
    }

    /** Big tasks start 2 evenings ahead, normal ones the evening before, tiny ones need no nudge. */
    fun defaultLead(size: Size): Lead? = when (size) {
        Size.Big -> Lead(2, EVENING)
        Size.Normal -> Lead(1, EVENING)
        Size.Tiny -> null
    }

    /**
     * The start time quick add suggests for a deadline. Starts from [lead] (the evening before by
     * default) and moves closer when that's already past, but never to the deadline's own day.
     * Null when nothing fits, there is no lead, or the task repeats (a weekly task doesn't need it).
     */
    fun suggest(
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        repeats: Boolean,
        now: LocalDateTime,
        lead: Lead? = Lead(1, EVENING),
    ): LocalDateTime? {
        if (dueDate == null || repeats || lead == null) return null
        val deadline = dueDate.atTime(dueTime ?: LocalTime.MAX)
        val minDays = if (lead.daysBefore == 0) 0 else 1
        for (days in lead.daysBefore downTo minDays) {
            val start = dueDate.minusDays(days.toLong()).atTime(lead.time)
            if (start.isAfter(now) && start.isBefore(deadline)) return start
        }
        return null
    }

    /** A quick choice in the Start menu. [hint] is "1 day before" and so on, when it's tied to the deadline. */
    data class Choice(val at: LocalDateTime, val hint: String?)

    /** Quick choices for the Start menu, soonest first, keeping only ones before the deadline. */
    fun choices(dueDate: LocalDate?, dueTime: LocalTime?, now: LocalDateTime): List<Choice> {
        val today = now.toLocalDate()
        val deadline = dueDate?.atTime(dueTime ?: LocalTime.MAX)
        val list = buildList {
            if (dueDate != null) for (days in 1L..3L) {
                add(Choice(dueDate.minusDays(days).atTime(EVENING), if (days == 1L) "1 day before" else "$days days before"))
            }
            // "Soon" only in waking hours; nobody wants a 3 AM option.
            val soon = now.plusHours(1).withMinute(0).withSecond(0).withNano(0).plusHours(if (now.minute >= 30) 1 else 0)
            if (soon.hour in 8..21) add(Choice(soon, null))
            add(Choice(today.atTime(EVENING), null))
            add(Choice(today.plusDays(1).atTime(EVENING), null))
        }
        return list
            .filter { it.at.isAfter(now) && (deadline == null || it.at.isBefore(deadline)) }
            .distinctBy { it.at }
            .sortedBy { it.at }
    }

    /** "Tonight 7 PM", "Tomorrow 7 PM", "Thu 7 PM" or "Nov 20, 7 PM". */
    fun label(at: LocalDateTime, today: LocalDate, locale: Locale = Locale.getDefault()): String {
        val time = at.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        val day = at.toLocalDate()
        val dayText = when {
            day == today -> if (at.hour >= 17) "Tonight" else "Today"
            day == today.plusDays(1) -> "Tomorrow"
            day.isAfter(today) && day.isBefore(today.plusDays(7)) -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            else -> day.format(DateTimeFormatter.ofPattern("MMM d", locale)) + ","
        }
        return "$dayText $time"
    }

    fun schedule(context: Context, task: TaskEntity) {
        cancel(context, task.id)
        if (task.done || task.deleted) return
        val at = task.startAt() ?: return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (millis <= System.currentTimeMillis()) return
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context, task.id, create = true) ?: return
        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarms.canScheduleExactAlarms() else true
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
    }

    fun cancel(context: Context, taskId: Long) {
        pendingIntent(context, taskId, create = false)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(context: Context, taskId: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, StartReminderReceiver::class.java)
            .setAction(ACTION_START)
            .setData("noctil://start/$taskId".toUri())
            .putExtra(EXTRA_TASK_ID, taskId)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    private fun action(context: Context, action: String, taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, StartReminderReceiver::class.java)
                .setAction(action)
                .setData("noctil://start/$taskId".toUri())
                .putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Kept apart from the deadline reminder's id, so one never replaces the other. */
    fun notificationId(taskId: Long): Int = 0x4000_0000 + taskId.toInt()

    /** "Due tonight, 11:59 PM", "Due tomorrow, 5 PM", "Due Thu, Nov 19". */
    fun dueText(task: TaskEntity, today: LocalDate = LocalDate.now()): String {
        val date = task.localDate() ?: return "No deadline"
        val time = task.localTime()?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        val day = when (date) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        }
        return if (time != null) "Due $day, $time" else "Due $day"
    }

    /**
     * The "not now" button: same time tomorrow, or in an hour when tomorrow would miss the deadline.
     * Returns the button label and the new start.
     */
    fun later(task: TaskEntity, now: LocalDateTime): Pair<String, LocalDateTime> {
        // Same time of day you had planned, a day later.
        val tomorrow = now.toLocalDate().plusDays(1).atTime(task.startAt()?.toLocalTime() ?: now.toLocalTime())
        val deadline = task.localDate()?.atTime(task.localTime() ?: LocalTime.MAX)
        return if (deadline == null || tomorrow.isBefore(deadline)) "Tomorrow" to tomorrow else "In 1 hour" to now.plusHours(1)
    }

    @SuppressLint("MissingPermission")
    internal fun show(context: Context, task: TaskEntity) {
        if (!Notifications.canNotify(context)) return
        val id = task.id
        val nid = notificationId(id)
        val open = Launch.pending(context, Launch.openTask(context, id), nid)
        val focus = Launch.pending(context, Launch.focus(context, startNext = true).putExtra(Launch.EXTRA_FOCUS_TASK, id), nid + 1)
        val laterLabel = later(task, LocalDateTime.now()).first
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB9A3FF.toInt())
            .setContentTitle("Time to start: ${task.title}")
            .setContentText("${dueText(task)}. You planned to start now.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .ownGroup("start_$id")
            .setContentIntent(open)
            .addAction(0, "Start focus", focus)
            .addAction(0, laterLabel, action(context, ACTION_LATER, id))
            .addAction(0, "Done", action(context, ACTION_DONE, id))
            .build()
        NotificationManagerCompat.from(context).notify(nid, notification)
    }

    internal fun handle(context: Context, intent: Intent, receiver: BroadcastReceiver) {
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (id < 0) return
        val app = context.applicationContext as NoctilApplication
        val repo = app.repository
        if (intent.action != ACTION_START) NotificationManagerCompat.from(context).cancel(notificationId(id))
        val pending = receiver.goAsync()
        repo.scope.launch {
            try {
                val task = repo.task(id) ?: return@launch
                if (task.done || task.deleted) return@launch
                when (intent.action) {
                    ACTION_START -> show(context, task)
                    ACTION_LATER -> repo.updateNow(id) { it.withStart(later(it, LocalDateTime.now()).second) }
                    ACTION_DONE -> repo.setDoneNow(id, true)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class StartReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = StartReminders.handle(context, intent, this)
}
