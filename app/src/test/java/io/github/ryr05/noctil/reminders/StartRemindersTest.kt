package io.github.ryr05.noctil.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/** "Now" for every test: Tuesday, November 10 2026, 2:00 PM. */
class StartRemindersTest {
    private val now = LocalDateTime.of(2026, 11, 10, 14, 0)
    private val today = now.toLocalDate()
    private fun d(day: Int) = LocalDate.of(2026, 11, day)

    @Test fun suggestsTheEveningBefore() {
        assertEquals(d(12).atTime(19, 0), StartReminders.suggest(d(13), LocalTime.of(23, 59), repeats = false, now))
        assertEquals(today.atTime(19, 0), StartReminders.suggest(d(11), LocalTime.of(9, 0), repeats = false, now))
    }

    @Test fun noSuggestionWhenItsTooLateOrRepeats() {
        assertNull(StartReminders.suggest(today, LocalTime.of(23, 0), repeats = false, now))
        assertNull(StartReminders.suggest(d(13), null, repeats = true, now))
        assertNull(StartReminders.suggest(null, null, repeats = false, now))
        // Due tomorrow at 6 PM: 7 PM the day before is today, still fine.
        assertEquals(today.atTime(19, 0), StartReminders.suggest(d(11), LocalTime.of(18, 0), repeats = false, now))
    }

    @Test fun choicesAreSoonestFirstAndBeforeTheDeadline() {
        val choices = StartReminders.choices(d(13), LocalTime.of(17, 0), now)
        assertEquals(
            listOf(today.atTime(15, 0), today.atTime(19, 0), d(11).atTime(19, 0), d(12).atTime(19, 0)),
            choices.map { it.at },
        )
        // Same moment as "tonight": keeps the deadline hint.
        assertEquals("3 days before", choices[1].hint)
        assertEquals("1 day before", choices[3].hint)
        assertTrue(StartReminders.choices(d(11), LocalTime.of(10, 0), now).all { it.at.isBefore(d(11).atTime(10, 0)) })
    }

    @Test fun labelsReadNaturally() {
        assertTrue(StartReminders.label(today.atTime(19, 0), today, Locale.US).startsWith("Tonight "))
        assertTrue(StartReminders.label(today.atTime(16, 0), today, Locale.US).startsWith("Today "))
        assertTrue(StartReminders.label(d(11).atTime(19, 0), today, Locale.US).startsWith("Tomorrow "))
        assertTrue(StartReminders.label(d(12).atTime(19, 0), today, Locale.US).startsWith("Thu "))
        assertTrue(StartReminders.label(d(25).atTime(19, 0), today, Locale.US).startsWith("Nov 25, "))
    }
}
