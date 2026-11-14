package io.github.ryr05.noctil.plan

import androidx.compose.ui.graphics.Color
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.reminders.StartReminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class WeekPlannerTest {
    private val today = LocalDate.of(2026, 11, 10) // Tuesday
    private val now = today.atTime(14, 0)
    private val defaults: (StartReminders.Size) -> StartReminders.Lead? = StartReminders::defaultLead

    private fun task(id: Long, title: String, date: LocalDate?, start: LocalDateTime? = null) = TaskItem(
        id = id, parentId = null, title = title, notes = "", date = date, time = null, priority = Priority.None,
        list = null, tags = emptyList(), recurrence = null, reminder = null, done = false, completedAt = null,
        sortOrder = id, createdAt = 0, subtaskTotal = 0, subtaskDone = 0, checklistTotal = 0, checklistDone = 0,
        startAt = start,
    )

    @Test fun eachTaskGetsAStartBeforeItsDeadline() {
        val plan = WeekPlanner.plan(listOf(task(1, "History essay", today.plusDays(3)), task(2, "Problem set", today.plusDays(2))), emptyList(), now, defaults)
        assertEquals(2, plan.size)
        plan.forEach { assertTrue(it.start.isAfter(now) && it.start.toLocalDate().isBefore(it.task.date)) }
        // The essay is big: two evenings before.
        assertEquals(today.plusDays(1).atTime(19, 0), plan.first { it.task.id == 1L }.start)
    }

    @Test fun noMoreThanTwoPerDay() {
        val tasks = (1L..4L).map { task(it, "Problem set $it", today.plusDays(3)) }
        val perDay = WeekPlanner.plan(tasks, emptyList(), now, defaults).groupingBy { it.start.toLocalDate() }.eachCount()
        assertTrue(perDay.values.all { it <= WeekPlanner.MAX_PER_DAY })
    }

    @Test fun skipsTinyRepeatingAndAlreadyStartedTasks() {
        val tasks = listOf(
            task(1, "Email prof", today.plusDays(2)),
            task(2, "Lab report", today.plusDays(4), start = today.plusDays(2).atTime(20, 0)),
            task(3, "Due next month", today.plusDays(30)),
        )
        assertTrue(WeekPlanner.plan(tasks, emptyList(), now, defaults).isEmpty())
    }

    @Test fun movesOutOfAClass() {
        val night = SchoolClass(1, "Night lab", "", Color.White, setOf(DayOfWeek.WEDNESDAY), LocalTime.of(18, 30), LocalTime.of(20, 10), null)
        val start = WeekPlanner.clearOfClasses(today.plusDays(1).atTime(19, 0), listOf(night))
        assertEquals(today.plusDays(1).atTime(20, 30), start)
    }
}
