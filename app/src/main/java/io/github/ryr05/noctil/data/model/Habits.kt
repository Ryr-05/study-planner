package io.github.ryr05.noctil.data.model

import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class Habit(
    val id: Long,
    val name: String,
    val color: Color,
    val days: Set<DayOfWeek>,
    val goal: Int,
    val reminder: LocalTime?,
    val createdOn: LocalDate,
    /** Check-in counts by day (days since 1970-01-01). */
    val checks: Map<Long, Int>,
) {
    val everyDay: Boolean get() = days.size == 7

    fun isDue(date: LocalDate): Boolean = date.dayOfWeek in days && !date.isBefore(createdOn)

    fun count(date: LocalDate): Int = checks[date.toEpochDay()] ?: 0

    fun isDone(date: LocalDate): Boolean = count(date) >= goal

    /**
     * Due days in a row that were done, counting back from today. Today only counts once it's
     * done, so an unfinished today doesn't break the streak until the day is over.
     */
    fun currentStreak(today: LocalDate): Int {
        var day = if (isDue(today) && !isDone(today)) today.minusDays(1) else today
        var streak = 0
        var guard = 0
        while (!day.isBefore(createdOn) && guard < 3660) {
            guard++
            if (isDue(day)) {
                if (isDone(day)) streak++ else break
            }
            day = day.minusDays(1)
        }
        return streak
    }

    fun bestStreak(today: LocalDate): Int {
        var best = 0
        var run = 0
        var day = createdOn
        while (!day.isAfter(today)) {
            if (isDue(day)) {
                if (isDone(day)) {
                    run++
                    best = maxOf(best, run)
                } else if (day != today) {
                    run = 0
                }
            }
            day = day.plusDays(1)
        }
        return best
    }

    /** Share of due days from [from] to [today] that were done. Today counts only once done. Null if none were due. */
    fun rate(from: LocalDate, today: LocalDate): Float? {
        var due = 0
        var done = 0
        var day = maxOf(from, createdOn)
        while (!day.isAfter(today)) {
            if (isDue(day) && (day != today || isDone(day))) {
                due++
                if (isDone(day)) done++
            }
            day = day.plusDays(1)
        }
        return if (due == 0) null else done.toFloat() / due
    }
}

data class FocusSession(val startedAt: Long, val minutes: Int, val taskId: Long?, val kind: String)
