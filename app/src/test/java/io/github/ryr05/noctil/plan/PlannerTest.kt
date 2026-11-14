package io.github.ryr05.noctil.plan

import androidx.compose.ui.graphics.Color
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.data.model.Tag
import io.github.ryr05.noctil.data.model.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PlannerTest {
    private val today = LocalDate.of(2026, 11, 11) // a Wednesday
    private val now = LocalDateTime.of(today, LocalTime.of(18, 2))

    private fun task(
        id: Long,
        title: String,
        date: LocalDate? = null,
        time: LocalTime? = null,
        priority: Priority = Priority.None,
        minutes: Int? = null,
        tags: List<String> = emptyList(),
    ) = TaskItem(
        id = id, parentId = null, title = title, notes = "", date = date, time = time, priority = priority,
        list = null, tags = tags.mapIndexed { i, n -> Tag(i.toLong(), n, Color.White) }, recurrence = null, reminder = null,
        done = false, completedAt = null, sortOrder = id, createdAt = 0, subtaskTotal = 0, subtaskDone = 0,
        checklistTotal = 0, checklistDone = 0, durationMinutes = minutes,
    )

    @Test fun overdueAndDueTodayComeFirst() {
        val tasks = listOf(
            task(1, "Read chapter 4", today.plusDays(5), minutes = 30),
            task(2, "Lab report", today.minusDays(1), minutes = 60),
            task(3, "Problem set 7", today, LocalTime.of(23, 0), minutes = 45),
        )
        val order = Planner.rank(tasks, Planner.Answers(), now).map { it.task.id }
        assertEquals(listOf(2L, 3L, 1L), order)
    }

    @Test fun highPriorityBeatsSameDayNormal() {
        val tasks = listOf(task(1, "Email prof", today.plusDays(1)), task(2, "Essay", today.plusDays(1), priority = Priority.High))
        assertEquals(2L, Planner.rank(tasks, Planner.Answers(), now).first().task.id)
    }

    @Test fun examSoonGetsAStudyBlock() {
        val exam = task(1, "Chem midterm", today.plusDays(2))
        val r = Planner.score(exam, Planner.Answers(), now)
        assertTrue(r.reasons.first().startsWith("Exam in 2 days"))
        assertEquals(60, Planner.lengthOf(exam, Planner.Answers()))
    }

    @Test fun asksForTimeFirstThenLengthThenTie() {
        val tasks = listOf(task(1, "Essay", today.plusDays(1)), task(2, "Slides", today.plusDays(1)))
        var answers = Planner.Answers()
        val q1 = Planner.nextQuestion(tasks, answers, now)!!
        assertEquals("time", q1.id)
        answers = Planner.answer(answers, q1, "120")
        val q2 = Planner.nextQuestion(tasks, answers, now)!!
        assertTrue(q2.id.startsWith("len:"))
        answers = Planner.answer(answers, q2, "45")
        val q3 = Planner.nextQuestion(tasks, answers, now)!!
        assertTrue(q3.id.startsWith("len:"))
        answers = Planner.answer(answers, q3, "30")
        val q4 = Planner.nextQuestion(tasks, answers, now)!!
        assertTrue(q4.id.startsWith("pick:"))
        answers = Planner.answer(answers, q4, "2")
        assertNull(Planner.nextQuestion(tasks, answers, now))
        assertEquals(2L, Planner.rank(tasks, answers, now).first().task.id)
    }

    @Test fun doesNotAskLengthWhenKnown() {
        val tasks = listOf(task(1, "Essay", today, minutes = 50))
        val answers = Planner.answer(Planner.Answers(), Planner.nextQuestion(tasks, Planner.Answers(), now)!!, "60")
        assertNull(Planner.nextQuestion(tasks, answers, now))
    }

    @Test fun plansFromNowWithBreaks() {
        val tasks = listOf(task(1, "Problem set", today, minutes = 45), task(2, "Reading", today.plusDays(1), minutes = 30))
        val plan = Planner.plan(tasks, Planner.Answers(minutesAvailable = 120), now)
        val work = plan.work
        assertEquals(LocalTime.of(18, 5), work[0].start) // rounded up to the next 5 minutes
        assertEquals(LocalTime.of(18, 50), work[0].end)
        assertTrue(plan.steps[1] is Planner.Step.Break)
        assertEquals(LocalTime.of(18, 55), work[1].start)
        assertTrue(plan.later.isEmpty())
    }

    @Test fun tightTimeSkipsTheBreak() {
        val tasks = listOf(task(1, "Lab report", today, minutes = 60), task(2, "Problem set", today.plusDays(1), minutes = 30))
        val plan = Planner.plan(tasks, Planner.Answers(minutesAvailable = 90), now)
        assertEquals(listOf(1L, 2L), plan.work.map { it.taskId })
        assertTrue(plan.steps.none { it is Planner.Step.Break })
        assertTrue(plan.later.isEmpty())
    }

    @Test fun whatDoesNotFitGoesToLater() {
        val tasks = listOf(task(1, "Long essay", today, minutes = 80), task(2, "Quick email", today.plusDays(1), minutes = 10))
        val plan = Planner.plan(tasks, Planner.Answers(minutesAvailable = 30), now)
        assertEquals(listOf(2L), plan.work.map { it.taskId })
        assertEquals(listOf(1L), plan.later.map { it.id })
    }

    @Test fun worksAroundTodaysClass() {
        val calc = SchoolClass(1, "Calculus II", "Hall B", Color.White, setOf(DayOfWeek.WEDNESDAY), LocalTime.of(18, 30), LocalTime.of(19, 30), null)
        val tasks = listOf(task(1, "Problem set", today, minutes = 45))
        val plan = Planner.plan(tasks, Planner.Answers(minutesAvailable = 180), now, listOf(calc))
        val block = plan.work.single()
        assertTrue("starts after class", !block.start.isBefore(LocalTime.of(19, 30)))
        assertNotNull(plan.steps.filterIsInstance<Planner.Step.Class>().firstOrNull())
    }

    @Test fun longTasksAreSplitInTwo() {
        val plan = Planner.plan(listOf(task(1, "Thesis chapter", today, minutes = 120)), Planner.Answers(minutesAvailable = 180), now)
        assertEquals(2, plan.work.size)
        assertEquals(60, plan.work[0].minutes)
        assertEquals(2, plan.work[1].part)
    }

    @Test fun candidatesSkipFarAndDoneTasks() {
        val tasks = listOf(
            task(1, "Due next month", today.plusDays(30)),
            task(2, "Due Friday", today.plusDays(2)),
            task(3, "Someday, important", priority = Priority.High),
            task(4, "Someday", null),
        )
        assertEquals(listOf(2L, 3L), Planner.candidates(tasks, today).map { it.id })
    }
}
