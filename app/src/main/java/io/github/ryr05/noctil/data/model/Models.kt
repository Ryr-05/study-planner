package io.github.ryr05.noctil.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class Priority(val value: Int, val label: String) {
    None(0, "None"),
    Low(1, "Low"),
    Medium(2, "Medium"),
    High(3, "High");

    companion object {
        fun of(value: Int): Priority = entries.firstOrNull { it.value == value } ?: None
    }
}

/**
 * How a task repeats. Stored as a short string such as "W;1;MO,WE" (weekly on Monday and Wednesday).
 */
data class Recurrence(
    val freq: Freq,
    val interval: Int = 1,
    val days: Set<DayOfWeek> = emptySet(),
) {
    enum class Freq(val code: String) { Daily("D"), Weekly("W"), Monthly("M"), Yearly("Y") }

    /** The next date after [from] on which this repeats. */
    fun next(from: LocalDate): LocalDate = when (freq) {
        Freq.Daily -> from.plusDays(interval.toLong())
        Freq.Weekly -> if (days.isEmpty()) {
            from.plusWeeks(interval.toLong())
        } else {
            val sorted = days.sortedBy { it.value }
            val laterThisWeek = sorted.firstOrNull { it.value > from.dayOfWeek.value }
            if (laterThisWeek != null) {
                from.with(TemporalAdjusters.next(laterThisWeek))
            } else {
                from.with(TemporalAdjusters.next(sorted.first())).plusWeeks((interval - 1).toLong())
            }
        }
        Freq.Monthly -> from.plusMonths(interval.toLong())
        Freq.Yearly -> from.plusYears(interval.toLong())
    }

    /** The first date on or after [date] that fits this pattern. */
    fun firstOnOrAfter(date: LocalDate): LocalDate =
        if (freq == Freq.Weekly && days.isNotEmpty()) {
            days.minOf { date.with(TemporalAdjusters.nextOrSame(it)) }
        } else {
            date
        }

    fun encode(): String =
        "${freq.code};$interval;" + days.sortedBy { it.value }.joinToString(",") { it.name.take(2) }

    fun label(): String {
        val n = interval
        return when (freq) {
            Freq.Daily -> if (n == 1) "Every day" else "Every $n days"
            Freq.Weekly -> when {
                days.isEmpty() -> if (n == 1) "Every week" else "Every $n weeks"
                days == WEEKDAYS -> "Every weekday"
                days == WEEKEND -> "Every weekend"
                days.size == 1 -> {
                    val name = days.first().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    when (n) {
                        1 -> "Every $name"
                        2 -> "Every other $name"
                        else -> "Every $n weeks on $name"
                    }
                }
                else -> "Every " + days.sortedBy { it.value }
                    .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
            }
            Freq.Monthly -> if (n == 1) "Every month" else "Every $n months"
            Freq.Yearly -> if (n == 1) "Every year" else "Every $n years"
        }
    }

    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        fun decode(raw: String?): Recurrence? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(";")
            if (parts.size < 2) return null
            val freq = Freq.entries.firstOrNull { it.code == parts[0] } ?: return null
            val interval = parts[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
            val days = parts.getOrNull(2).orEmpty().split(",").filter { it.isNotBlank() }
                .mapNotNull { code -> DayOfWeek.entries.firstOrNull { it.name.startsWith(code) } }
                .toSet()
            return Recurrence(freq, interval, days)
        }
    }
}

/** Reminder offsets in minutes before the task's time. Date-only tasks use 9:00 AM as their time. */
object Reminders {
    val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)
    val options = listOf(0, 5, 15, 30, 60, 1440)

    fun label(minutesBefore: Int?, hasTime: Boolean): String = when (minutesBefore) {
        null -> "No reminder"
        0 -> if (hasTime) "At time of task" else "At 9:00 AM"
        1440 -> if (hasTime) "1 day before" else "Day before at 9:00 AM"
        60 -> "1 hour before"
        else -> "$minutesBefore minutes before"
    }

    fun triggerAt(date: LocalDate?, time: LocalTime?, minutesBefore: Int?): LocalDateTime? {
        if (date == null || minutesBefore == null) return null
        return LocalDateTime.of(date, time ?: DEFAULT_TIME).minusMinutes(minutesBefore.toLong())
    }
}
