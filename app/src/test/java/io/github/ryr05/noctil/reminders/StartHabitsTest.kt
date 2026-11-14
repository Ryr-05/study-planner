package io.github.ryr05.noctil.reminders

import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.reminders.StartHabits.Habit
import io.github.ryr05.noctil.reminders.StartReminders.Lead
import io.github.ryr05.noctil.reminders.StartReminders.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class StartHabitsTest {
    private val now = LocalDateTime.of(2026, 11, 10, 14, 0) // Tuesday 2 PM
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private val twoDays8pm = Lead(2, LocalTime.of(20, 0))

    @Test fun sizeComesFromWhatTheTaskIs() {
        assertEquals(Size.Big, StartReminders.sizeOf("Chem midterm", emptyList(), Priority.None, null))
        assertEquals(Size.Big, StartReminders.sizeOf("History essay", emptyList(), Priority.None, null))
        assertEquals(Size.Big, StartReminders.sizeOf("Problem set", emptyList(), Priority.None, 120))
        assertEquals(Size.Big, StartReminders.sizeOf("Problem set", emptyList(), Priority.High, null))
        assertEquals(Size.Tiny, StartReminders.sizeOf("Email prof about lab", emptyList(), Priority.None, null))
        assertEquals(Size.Tiny, StartReminders.sizeOf("Pay rent", emptyList(), Priority.None, null))
        assertEquals(Size.Normal, StartReminders.sizeOf("Problem set 7", emptyList(), Priority.None, null))
        // "Email" with a big job in it is still big.
        assertEquals(Size.Big, StartReminders.sizeOf("Email draft of essay", listOf("exam"), Priority.None, null))
    }

    @Test fun bigTasksStartTwoEveningsAheadTinyOnesNotAtAll() {
        assertEquals(d(11).atTime(19, 0), StartReminders.suggest(d(13), null, false, now, StartReminders.defaultLead(Size.Big)))
        assertNull(StartReminders.suggest(d(13), null, false, now, StartReminders.defaultLead(Size.Tiny)))
    }

    @Test fun tooLateForTheUsualLeadMovesCloser() {
        // Big task due tomorrow: two days ahead has passed, so tonight.
        assertEquals(now.toLocalDate().atTime(19, 0), StartReminders.suggest(d(11), LocalTime.of(9, 0), false, now, StartReminders.defaultLead(Size.Big)))
    }

    @Test fun learnsWhenTwoOfTheLastThreeAgree() {
        assertEquals(Habit.None, StartHabits.learned(emptyList()))
        assertEquals(Habit.None, StartHabits.learned(listOf(twoDays8pm.encode())))
        assertEquals(Habit.At(twoDays8pm), StartHabits.learned(listOf(twoDays8pm.encode(), "1@1140", twoDays8pm.encode())))
        assertEquals(Habit.Off, StartHabits.learned(listOf(StartHabits.encode(null), StartHabits.encode(null))))
        // Only the last three count.
        assertEquals(Habit.None, StartHabits.learned(listOf("off", "off", "1@1140", "2@1200", "3@600")))
    }

    @Test fun leadIsMeasuredFromTheDeadline() {
        assertEquals(twoDays8pm, Lead.of(d(11).atTime(20, 0), d(13)))
        assertEquals(twoDays8pm, Lead.decode(twoDays8pm.encode()))
        assertNull(Lead.decode("nonsense"))
    }
}
