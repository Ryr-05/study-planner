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

    @Test fun aiSeesAllOpenTasksSoonestFirst() {
        val far = task(2, "Term paper", today.plusDays(30))
        val noDate = task(4, "Someday", null)
        val done = task(5, "Finished", today).copy(done = true)
        assertEquals(listOf(1L, 2L, 4L), AiChangeGuard.visibleTasks(listOf(noDate, far, essay, done), today).map { it.id })
    }

    @Test fun otherEditsOnlyWhenTheyChangeSomething() {
        val lists = listOf(io.github.ryr05.noctil.data.model.TaskList(5, "Chemistry", Color.White, null))
        val c = AiChangeGuard.check(
            listOf(
                AiChange(1, title = "History essay draft", priority = Priority.High, tagsAdd = listOf("history"), list = "chemistry", done = true),
            ),
            known, "rename it and mark it done", now, lists,
        )
        val f = c.fields.single()
        assertEquals("History essay draft", f.title)
        assertEquals(Priority.High, f.priority)
        assertEquals(listOf("history"), f.tagsAdd)
        assertEquals(5L, f.list?.id)
        assertTrue(f.done)
        val unknown = AiChangeGuard.check(listOf(AiChange(1, list = "Physics")), known, "move it to physics", now, lists)
        assertTrue(unknown.fields.isEmpty())
        assertEquals(listOf("Physics"), unknown.unknownLists)
        // Same title as now: nothing to do.
        assertTrue(AiChangeGuard.check(listOf(AiChange(1, title = "Essay")), known, "rename", now, lists).fields.isEmpty())
    }

    @Test fun parsesEditsHabitsAndPlanIds() {
        val c = AiChange.parse(JSONObject("""{"id": 1, "title": "Draft", "priority": "high", "tags_add": ["#hist", "bad tag"], "list": "School", "done": true}"""))!!
        assertEquals("Draft", c.title)
        assertEquals(Priority.High, c.priority)
        assertEquals(listOf("hist"), c.tagsAdd)
        assertEquals("School", c.list)
        assertTrue(c.done)
        val water = AiHabit.parse(JSONObject("""{"name": "Drink water", "goal": 8, "days": ["daily"], "reminder": "09:00"}"""))!!
        assertEquals(8, water.goal)
        assertEquals(7, water.days.size)
        assertEquals(LocalTime.of(9, 0), water.reminder)
        val gym = AiHabit.parse(JSONObject("""{"name": "Gym", "days": ["mon", "wed", "fri"]}"""))!!
        assertEquals(setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.FRIDAY), gym.days)
        val reply = AiReply.parse("""{"reply":"ok","action":"plan","plan_task_ids":[1,2],"new_habits":[{"name":"Read","goal":1,"days":["weekdays"]}],"lengths":[],"favored_ids":[]}""")
        assertEquals(listOf(1L, 2L), reply.planTaskIds)
        assertEquals(5, reply.newHabits.single().days.size)
    }

    @Test fun separateItemsForOneTaskAreMerged() {
        val c = AiChangeGuard.check(listOf(AiChange(1, title = "Bio chapter 5"), AiChange(1, priority = Priority.High)), known, "rename it and make it high priority", now)
        assertEquals("Bio chapter 5", c.fields.single().title)
        assertEquals(Priority.High, c.fields.single().priority)
    }
}
