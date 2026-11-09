package io.github.ryr05.noctil.parse

import io.github.ryr05.noctil.data.model.Recurrence
import io.github.ryr05.noctil.data.model.Recurrence.Freq
import io.github.ryr05.noctil.data.model.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RecurrenceTest {
    private val monWedFri = Recurrence(Freq.Weekly, 1, setOf(MONDAY, WEDNESDAY, FRIDAY))

    @Test fun weeklyDaysNextInSameWeek() =
        assertEquals(LocalDate.of(2026, 11, 18), monWedFri.next(LocalDate.of(2026, 11, 16)))

    @Test fun weeklyDaysWrapToNextWeek() =
        assertEquals(LocalDate.of(2026, 11, 23), monWedFri.next(LocalDate.of(2026, 11, 20)))

    @Test fun everyOtherMondaySkipsAWeek() =
        assertEquals(
            LocalDate.of(2026, 11, 30),
            Recurrence(Freq.Weekly, 2, setOf(MONDAY)).next(LocalDate.of(2026, 11, 16)),
        )

    @Test fun everyThreeDays() =
        assertEquals(LocalDate.of(2026, 11, 13), Recurrence(Freq.Daily, 3).next(LocalDate.of(2026, 11, 10)))

    @Test fun monthlyClampsToShortMonths() =
        assertEquals(LocalDate.of(2026, 2, 28), Recurrence(Freq.Monthly).next(LocalDate.of(2026, 1, 31)))

    @Test fun encodeDecodeRoundTrip() {
        listOf(monWedFri, Recurrence(Freq.Daily, 3), Recurrence(Freq.Yearly), Recurrence(Freq.Weekly, 2))
            .forEach { assertEquals(it, Recurrence.decode(it.encode())) }
        assertNull(Recurrence.decode(null))
        assertNull(Recurrence.decode("garbage"))
    }

    @Test fun labels() {
        assertEquals("Every Mon, Wed, Fri", monWedFri.label())
        assertEquals("Every Monday", Recurrence(Freq.Weekly, 1, setOf(MONDAY)).label())
        assertEquals("Every other Monday", Recurrence(Freq.Weekly, 2, setOf(MONDAY)).label())
        assertEquals("Every weekday", Recurrence(Freq.Weekly, 1, Recurrence.WEEKDAYS).label())
        assertEquals("Every 3 days", Recurrence(Freq.Daily, 3).label())
        assertEquals("Every month", Recurrence(Freq.Monthly).label())
    }

    @Test fun reminderTimes() {
        val day = LocalDate.of(2026, 11, 12)
        assertEquals(LocalDateTime.of(day, LocalTime.of(16, 45)), Reminders.triggerAt(day, LocalTime.of(17, 0), 15))
        assertEquals(LocalDateTime.of(day, LocalTime.of(9, 0)), Reminders.triggerAt(day, null, 0))
        assertEquals(LocalDateTime.of(day.minusDays(1), LocalTime.of(9, 0)), Reminders.triggerAt(day, null, 1440))
        assertNull(Reminders.triggerAt(null, null, 0))
        assertNull(Reminders.triggerAt(day, null, null))
    }
}
