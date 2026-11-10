package io.github.ryr05.noctil.parse

import androidx.compose.ui.graphics.Color
import io.github.ryr05.noctil.data.model.Habit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** Today is Tuesday, November 10 2026. */
class HabitTest {
    private val today = LocalDate.of(2026, 11, 10)
    private fun d(day: Int) = LocalDate.of(2026, 11, day)

    private fun habit(doneDays: List<LocalDate>, days: Set<DayOfWeek> = DayOfWeek.entries.toSet(), goal: Int = 1) = Habit(
        id = 1, name = "Read", color = Color.White, days = days, goal = goal, reminder = null,
        createdOn = d(1), checks = doneDays.associate { it.toEpochDay() to goal },
    )

    @Test fun streakCountsBackFromYesterdayWhenTodayIsOpen() =
        assertEquals(3, habit(listOf(d(7), d(8), d(9))).currentStreak(today))

    @Test fun streakIncludesTodayOnceDone() =
        assertEquals(4, habit(listOf(d(7), d(8), d(9), d(10))).currentStreak(today))

    @Test fun missedDayBreaksStreak() =
        assertEquals(1, habit(listOf(d(6), d(7), d(9))).currentStreak(today))

    @Test fun daysNotDueDontBreakStreak() {
        // Mon, Wed, Fri only. Nov 2 Mon, 4 Wed, 6 Fri, 9 Mon all done; today (Tue) isn't due.
        val mwf = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        assertEquals(4, habit(listOf(d(2), d(4), d(6), d(9)), mwf).currentStreak(today))
    }

    @Test fun bestStreak() =
        assertEquals(4, habit(listOf(d(1), d(2), d(3), d(4), d(6), d(7))).bestStreak(today))

    @Test fun goalNeedsEnoughCheckIns() {
        val h = Habit(1, "Water", Color.White, DayOfWeek.entries.toSet(), goal = 8, reminder = null, createdOn = d(1),
            checks = mapOf(d(9).toEpochDay() to 8, d(10).toEpochDay() to 5))
        assertEquals(true, h.isDone(d(9)))
        assertEquals(false, h.isDone(d(10)))
        assertEquals(1, h.currentStreak(today))
    }

    @Test fun rateSkipsOpenToday() {
        // Nov 1 to 9 is 9 due days, 3 done; today is open so it isn't counted.
        assertEquals(3f / 9f, habit(listOf(d(1), d(5), d(9))).rate(d(1), today)!!, 0.0001f)
    }

    @Test fun rateIsNullBeforeAnythingIsDue() =
        assertNull(habit(emptyList()).rate(d(20), d(20).minusDays(1)))
}
