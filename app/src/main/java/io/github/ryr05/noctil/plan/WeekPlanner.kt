package io.github.ryr05.noctil.plan

import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.reminders.StartReminders
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * "Plan my week" without AI: gives each task due in the next 7 days a start time, spread so no
 * evening gets more than [MAX_PER_DAY] and clear of classes. Nothing changes until the student
 * approves it.
 */
object WeekPlanner {
    const val MAX_PER_DAY = 2

    data class Slot(val task: TaskItem, val start: LocalDateTime)

    fun plan(
        tasks: List<TaskItem>,
        classes: List<SchoolClass>,
        now: LocalDateTime,
        leadFor: (StartReminders.Size) -> StartReminders.Lead?,
    ): List<Slot> {
        val today = now.toLocalDate()
        val due = tasks.filter { t ->
            !t.done && t.parentId == null && t.recurrence == null && t.date != null &&
                !t.date.isBefore(today) && !t.date.isAfter(today.plusDays(7)) &&
                (t.startAt == null || t.startAt.isBefore(now))
        }.sortedWith(compareBy<TaskItem>({ it.date }, { it.time ?: LocalTime.MAX }))
        val perDay = mutableMapOf<LocalDate, Int>()
        // Days that already have starts count too.
        tasks.mapNotNull { it.startAt?.takeIf { s -> !it.done && s.isAfter(now) }?.toLocalDate() }
            .forEach { perDay[it] = (perDay[it] ?: 0) + 1 }
        val out = mutableListOf<Slot>()
        for (t in due) {
            val size = StartReminders.sizeOf(t.title, t.tags.map { it.name }, t.priority, t.durationMinutes)
            val lead = leadFor(size) ?: continue
            val deadline = t.date!!.atTime(t.time ?: LocalTime.MAX)
            val wanted = StartReminders.suggest(t.date, t.time, false, now, lead) ?: continue
            // Prefer the wanted day, then earlier days, then later ones, while there's room.
            val days = (0L..7L).map { wanted.toLocalDate().minusDays(it) }.filter { !it.isBefore(today) } +
                (1L..7L).map { wanted.toLocalDate().plusDays(it) }
            val start = days.asSequence()
                .filter { (perDay[it] ?: 0) < MAX_PER_DAY }
                .map { clearOfClasses(it.atTime(wanted.toLocalTime()), classes) }
                .firstOrNull { it.isAfter(now) && it.isBefore(deadline) }
                ?: wanted
            perDay[start.toLocalDate()] = (perDay[start.toLocalDate()] ?: 0) + 1
            out += Slot(t, start)
        }
        return out
    }

    /** Moves a start that falls in a class to when the class ends (on the hour or half hour). */
    fun clearOfClasses(start: LocalDateTime, classes: List<SchoolClass>): LocalDateTime {
        val day = start.toLocalDate()
        val clash = classes.filter { it.meetsOn(day) }
            .firstOrNull { c -> !start.toLocalTime().isBefore(c.start) && start.toLocalTime().isBefore(c.end) }
            ?: return start
        val minutes = clash.end.hour * 60 + clash.end.minute
        val up = ((minutes + 29) / 30) * 30
        if (up >= 24 * 60) return start
        return day.atTime(up / 60, up % 60)
    }
}
