package io.github.ryr05.noctil.parse

import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.ui.tasks.quadrantOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MatrixTest {
    private val today = LocalDate.of(2026, 11, 11)

    private fun task(priority: Priority, date: LocalDate?) = TaskItem(
        id = 1, parentId = null, title = "t", notes = "", date = date, time = null, priority = priority, list = null,
        tags = emptyList(), recurrence = null, reminder = null, done = false, completedAt = null, sortOrder = 0,
        createdAt = 0, subtaskTotal = 0, subtaskDone = 0, checklistTotal = 0, checklistDone = 0,
    )

    @Test fun importantAndDueSoonIsDoNow() = assertEquals(0, quadrantOf(task(Priority.High, today.plusDays(1)), today))
    @Test fun overdueAndImportantIsDoNow() = assertEquals(0, quadrantOf(task(Priority.Medium, today.minusDays(3)), today))
    @Test fun importantButFarIsPlanIt() = assertEquals(1, quadrantOf(task(Priority.High, today.plusDays(9)), today))
    @Test fun importantWithNoDateIsPlanIt() = assertEquals(1, quadrantOf(task(Priority.High, null), today))
    @Test fun lowPriorityDueSoonIsFitItIn() = assertEquals(2, quadrantOf(task(Priority.Low, today), today))
    @Test fun twoDaysOutStillCountsAsUrgent() = assertEquals(2, quadrantOf(task(Priority.None, today.plusDays(2)), today))
    @Test fun neitherIsLater() = assertEquals(3, quadrantOf(task(Priority.None, today.plusDays(3)), today))
}
