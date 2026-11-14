package io.github.ryr05.noctil.ui.assistant

import androidx.compose.ui.graphics.Color
import io.github.ryr05.noctil.data.model.Habit
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.ui.assistant.OfflineAssistant.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class OfflineAssistantTest {
    private val today = LocalDate.of(2026, 11, 10) // Tuesday
    private val now = today.atTime(14, 0)

    private fun task(id: Long, title: String, date: LocalDate?) = TaskItem(
        id = id, parentId = null, title = title, notes = "", date = date, time = null, priority = Priority.None,
        list = null, tags = emptyList(), recurrence = null, reminder = null, done = false, completedAt = null,
        sortOrder = id, createdAt = 0, subtaskTotal = 0, subtaskDone = 0, checklistTotal = 0, checklistDone = 0,
    )

    @Test fun intents() {
        assertEquals(Intent.PlanWeek, OfflineAssistant.intentOf("plan my week", now))
        assertEquals(Intent.PlanSession, OfflineAssistant.intentOf("Plan my session", now))
        assertEquals(Intent.PlanSession, OfflineAssistant.intentOf("what should I work on?", now))
        assertEquals(Intent.Due, OfflineAssistant.intentOf("What's due this week?", now))
        assertEquals(Intent.NextExam, OfflineAssistant.intentOf("When's my next exam?", now))
        assertEquals(Intent.Classes, OfflineAssistant.intentOf("do I have class tomorrow?", now))
        assertEquals(Intent.Habits, OfflineAssistant.intentOf("how are my habits", now))
        assertEquals(Intent.AddTasks, OfflineAssistant.intentOf("essay fri 5pm", now))
        assertEquals(Intent.AddTasks, OfflineAssistant.intentOf("read ch 4\nlab report thu", now))
        assertEquals(Intent.Unknown, OfflineAssistant.intentOf("hello", now))
        assertEquals(Intent.Unknown, OfflineAssistant.intentOf("why is the sky blue?", now))
    }

    @Test fun answersWhatsDue() {
        val tasks = listOf(task(1, "Essay", today.plusDays(2)), task(2, "Far away", today.plusDays(20)), task(3, "Late", today.minusDays(1)))
        val week = OfflineAssistant.dueAnswer("what's due this week?", tasks, today)
        assertTrue(week, week.contains("Essay") && !week.contains("Far away") && week.contains("Overdue: Late"))
        assertEquals("Nothing is due tomorrow.", OfflineAssistant.dueAnswer("due tomorrow?", tasks, today))
    }

    @Test fun nextExamAndClassesAndHabits() {
        val tasks = listOf(task(1, "Chem midterm", today.plusDays(3)), task(2, "Essay", today.plusDays(1)))
        assertTrue(OfflineAssistant.nextExamAnswer(tasks, today).contains("Chem midterm"))
        val calc = SchoolClass(1, "Calc", "", Color.White, setOf(DayOfWeek.WEDNESDAY), LocalTime.of(10, 0), LocalTime.of(11, 0), null)
        assertTrue(OfflineAssistant.classesAnswer("classes tomorrow?", listOf(calc), today).contains("Calc"))
        assertTrue(OfflineAssistant.classesAnswer("classes today?", listOf(calc), today).startsWith("No classes"))
        val water = Habit(1, "Water", Color.White, DayOfWeek.entries.toSet(), 8, null, today.minusDays(5), mapOf(today.minusDays(1).toEpochDay() to 8, today.toEpochDay() to 3))
        assertEquals("Water: 1 day in a row, 3 of 8 today.", OfflineAssistant.habitsAnswer(listOf(water), today))
    }

    @Test fun addCommandIsStripped() = assertEquals("essay fri", OfflineAssistant.stripAdd("add a task: essay fri"))
}
