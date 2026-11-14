package io.github.ryr05.noctil.plan

import android.app.Application
import androidx.compose.ui.graphics.Color
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.TaskItem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AiChangesTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val now = today.atTime(14, 0)

    private fun task(id: Long, title: String, date: LocalDate?, time: LocalTime? = null, start: LocalDateTime? = null) = TaskItem(
        id = id, parentId = null, title = title, notes = "", date = date, time = time, priority = Priority.None,
        list = null, tags = emptyList(), recurrence = null, reminder = null, done = false, completedAt = null,
        sortOrder = id, createdAt = 0, subtaskTotal = 0, subtaskDone = 0, checklistTotal = 0, checklistDone = 0,
        startAt = start,
    )

    private val essay = task(1, "Essay", today.plusDays(3), LocalTime.of(23, 59), start = today.plusDays(2).atTime(19, 0))
    private val known = mapOf(1L to essay)

    @Test fun startChangeIsApplied() {
        val c = AiChangeGuard.check(listOf(AiChange(1, start = now.withHour(19))), known, "start the essay tonight", now)
        assertEquals(now.withHour(19), c.starts.single().new)
        assertEquals(essay.startAt, c.starts.single().old)
        assertTrue(c.deadlines.isEmpty())
    }

    @Test fun deadlineNeedsTheStudentToAskForIt() {
        val move = AiChange(1, dueDate = today.plusDays(5))
        val refused = AiChangeGuard.check(listOf(move), known, "I'll do the essay later", now)
        assertTrue(refused.deadlines.isEmpty())
        assertEquals(1, refused.deadlinesRefused)
        val asked = AiChangeGuard.check(listOf(move), known, "my prof extended the essay to sunday", now)
        assertEquals(today.plusDays(5), asked.deadlines.single().newDate)
        assertEquals(LocalTime.of(23, 59), asked.deadlines.single().newTime)
    }

    @Test fun startsMustBeFutureAndBeforeTheDeadline() {
        val late = AiChange(1, start = today.plusDays(4).atTime(9, 0))
        val past = AiChange(1, start = now.minusHours(1))
        val lateCheck = AiChangeGuard.check(listOf(late), known, "start it saturday", now)
        assertTrue(lateCheck.starts.isEmpty())
        assertTrue(lateCheck.startsRefused.single().afterDeadline)
        assertTrue(AiChangeGuard.check(listOf(past), known, "start it at 1", now).starts.isEmpty())
        // With an allowed later deadline, the later start fits.
        val both = AiChange(1, start = today.plusDays(4).atTime(9, 0), dueDate = today.plusDays(6))
        assertEquals(1, AiChangeGuard.check(listOf(both), known, "deadline moved to monday, start saturday", now).starts.size)
    }

    @Test fun unknownTasksAndNoOpsAreIgnored() {
        val c = AiChangeGuard.check(listOf(AiChange(99, start = now.plusHours(2)), AiChange(1, dueDate = essay.date)), known, "due date", now)
        assertTrue(c.starts.isEmpty())
        assertTrue(c.deadlines.isEmpty())
        assertEquals(0, c.deadlinesRefused)
    }

    @Test fun clearingAStart() {
        val c = AiChangeGuard.check(listOf(AiChange(1, clearStart = true)), known, "no start reminder for the essay", now)
        assertNull(c.starts.single().new)
    }

    @Test fun deadlineWords() {
        listOf(
            "change the deadline", "it's now due friday", "she gave me an extension", "postponed to monday",
            "my prof moved the chem quiz to friday 10am", "the midterm got rescheduled",
        ).forEach {
            assertTrue(it, AiChangeGuard.asksAboutDeadline(it))
        }
        listOf("start it tonight", "move my essay to tomorrow evening", "I'll do it later").forEach {
            assertFalse(it, AiChangeGuard.asksAboutDeadline(it))
        }
    }

    @Test fun parsesChangesFromGemini() {
        val o = JSONObject("""{"id": 1, "start": "2026-11-10T19:00", "due_date": "", "due_time": ""}""")
        assertEquals(AiChange(1, start = now.withHour(19)), AiChange.parse(o))
        assertEquals(AiChange(1, clearStart = true), AiChange.parse(JSONObject("""{"id": 1, "start": "none"}""")))
        assertNull(AiChange.parse(JSONObject("""{"id": 1}""")))
        val reply = AiReply.parse("""{"reply":"ok","action":"change","changes":[{"id":1,"start":"2026-11-10T19:00"}],"lengths":[],"favored_ids":[]}""")
        assertEquals(1, reply.changes.size)
        val read = AiRead.parse("""{"tasks":["Lab report 3 nov 20 11:59pm #chem","x"],"note":""}""")
        assertEquals(listOf("Lab report 3 nov 20 11:59pm #chem"), read.tasks)
    }

    @Test fun aiSeesTwoWeeksAndStartedTasks() {
        val far = task(2, "Term paper", today.plusDays(30))
        val farStarted = task(3, "Thesis", today.plusDays(30), start = today.plusDays(1).atTime(19, 0))
        val noDate = task(4, "Someday", null)
        assertEquals(listOf(1L, 3L), AiChangeGuard.visibleTasks(listOf(essay, far, farStarted, noDate), today).map { it.id })
    }
}
