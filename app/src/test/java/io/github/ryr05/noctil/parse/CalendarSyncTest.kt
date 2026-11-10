package io.github.ryr05.noctil.parse

import io.github.ryr05.noctil.calendar.CalendarSync
import io.github.ryr05.noctil.data.db.TaskEntity
import io.github.ryr05.noctil.ui.calendar.snapTo15
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarSyncTest {
    private val day = LocalDate.of(2026, 11, 12)
    private val zone = ZoneId.systemDefault()

    private fun task(time: Int?, duration: Int? = null) = TaskEntity(
        id = 1, title = "Lab report", notes = "Bring goggles",
        dueDate = day.toEpochDay(), dueTime = time, durationMinutes = duration,
    )

    @Test fun timedTaskBecomesTimedEvent() {
        val f = CalendarSync.fieldsFor(task(17 * 60, 90))
        val start = LocalDateTime.of(2026, 11, 12, 17, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(false, f.allDay)
        assertEquals(start, f.start)
        assertEquals(start + 90 * 60_000L, f.end)
        assertEquals("Lab report", f.title)
        assertTrue(f.description.startsWith("Bring goggles"))
    }

    @Test fun timedTaskUsesDefaultLength() {
        val f = CalendarSync.fieldsFor(task(9 * 60))
        assertEquals(30 * 60_000L, f.end - f.start)
    }

    @Test fun dateOnlyTaskBecomesAllDayEventInUtc() {
        val f = CalendarSync.fieldsFor(task(null))
        assertEquals(true, f.allDay)
        assertEquals(day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), f.start)
        assertEquals(86_400_000L, f.end - f.start)
    }

    @Test fun roundTripChangesNothing() {
        listOf(task(17 * 60, 90), task(9 * 60), task(null)).forEach { t ->
            val back = CalendarSync.applyEvent(t, CalendarSync.fieldsFor(t))
            assertEquals(t.dueDate, back.dueDate)
            assertEquals(t.dueTime, back.dueTime)
            assertEquals(t.durationMinutes, back.durationMinutes)
            assertEquals(t.title, back.title)
        }
    }

    @Test fun movedEventMovesTheTask() {
        val original = CalendarSync.fieldsFor(task(17 * 60))
        val newStart = LocalDateTime.of(2026, 11, 13, 10, 30).atZone(zone).toInstant().toEpochMilli()
        val moved = original.copy(title = "Lab report (final)", start = newStart, end = newStart + 60 * 60_000L)
        val result = CalendarSync.applyEvent(task(17 * 60), moved)
        assertEquals(LocalDate.of(2026, 11, 13).toEpochDay(), result.dueDate)
        assertEquals(10 * 60 + 30, result.dueTime)
        assertEquals(60, result.durationMinutes)
        assertEquals("Lab report (final)", result.title)
    }

    @Test fun eventMadeAllDayClearsTheTime() {
        val allDay = CalendarSync.fieldsFor(task(null))
        val result = CalendarSync.applyEvent(task(17 * 60), allDay)
        assertNull(result.dueTime)
        assertEquals(day.toEpochDay(), result.dueDate)
    }

    @Test fun dragSnapsToQuarterHours() {
        assertEquals(15 * 60, snapTo15(15 * 60 + 7))
        assertEquals(15 * 60 + 15, snapTo15(15 * 60 + 8))
        assertEquals(0, snapTo15(-40))
        assertEquals(24 * 60 - 15, snapTo15(24 * 60 + 30))
    }
}
