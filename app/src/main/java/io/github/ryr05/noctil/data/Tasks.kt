package io.github.ryr05.noctil.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.LocalTime

enum class Priority { High, Medium, Low, None }

data class Subject(val name: String, val color: Color)

data class Task(
    val id: Int,
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    val subject: Subject?,
    val priority: Priority,
    val done: Boolean = false,
)

data class Exam(val name: String, val date: LocalDate, val subject: Subject)

object Subjects {
    val Calculus = Subject("Calculus", Color(0xFF8DB8FF))
    val Chemistry = Subject("Chemistry", Color(0xFF7FD6B5))
    val Literature = Subject("Literature", Color(0xFFF2A7C9))
    val History = Subject("History", Color(0xFFFFC978))
    val Personal = Subject("Personal", Color(0xFFB9A3FF))
}

/**
 * Phase 0 keeps tasks in memory with sample content so every screen can be tried.
 * Phase 1 replaces this with the on-device Room database.
 */
class TaskStore(today: LocalDate) {
    val tasks = mutableStateListOf<Task>().apply { addAll(sampleTasks(today)) }
    val nextExam = Exam("Chemistry midterm", today.plusDays(6), Subjects.Chemistry)
    private var nextId = 1000

    fun toggle(id: Int) {
        val index = tasks.indexOfFirst { it.id == id }
        if (index >= 0) tasks[index] = tasks[index].copy(done = !tasks[index].done)
    }

    fun add(title: String, date: LocalDate) {
        tasks.add(Task(nextId++, title.trim(), date, null, null, Priority.None))
    }

    fun datesWithOpenTasks(): Set<LocalDate> = tasks.filter { !it.done }.map { it.date }.toSet()
}

private fun sampleTasks(today: LocalDate) = listOf(
    Task(1, "Lab report: titration results", today.minusDays(1), LocalTime.of(23, 59), Subjects.Chemistry, Priority.High),
    Task(2, "Problem set 7, questions 1 to 12", today, LocalTime.of(17, 0), Subjects.Calculus, Priority.High),
    Task(3, "Read chapter 4 of The Great Gatsby", today, LocalTime.of(21, 0), Subjects.Literature, Priority.Medium),
    Task(4, "Email Prof. Okafor about office hours", today, null, Subjects.Personal, Priority.Low),
    Task(5, "Flashcards: Cold War timeline", today.plusDays(1), LocalTime.of(10, 0), Subjects.History, Priority.Medium),
    Task(6, "Study group at the library", today.plusDays(1), LocalTime.of(15, 30), Subjects.Calculus, Priority.None),
    Task(7, "Midterm review session", today.plusDays(3), LocalTime.of(18, 0), Subjects.Chemistry, Priority.Medium),
    Task(8, "Essay outline due", today.plusDays(5), LocalTime.of(9, 0), Subjects.Literature, Priority.High),
)
