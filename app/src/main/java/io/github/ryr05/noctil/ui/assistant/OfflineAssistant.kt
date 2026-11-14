package io.github.ryr05.noctil.ui.assistant

import io.github.ryr05.noctil.data.model.Habit
import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.data.model.looksLikeExam
import io.github.ryr05.noctil.parse.SmartParser
import io.github.ryr05.noctil.ui.components.relativeDay
import io.github.ryr05.noctil.ui.components.timeFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * What Noctil AI can do with no AI at all: work out what was asked, and answer simple
 * questions straight from the student's own tasks, classes and habits.
 */
object OfflineAssistant {
    enum class Intent { PlanSession, PlanWeek, Due, NextExam, Classes, Habits, AddTasks, Unknown }

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private val planWeek = rx("\\b(plan|organi[sz]e|spread|schedule)\\b.*\\b(week|weekly)\\b|\\bweek\\s*plan\\b")
    private val planSession = rx("\\bplan\\b.*\\b(session|now|today|tonight|study|studying)\\b|\\bplan my session\\b|what should i (do|work on|start)|where (do|should) i start|help me focus")
    private val due = rx("\\b(due|deadlines?|what do i have|what('s| is) (left|on)|to ?do)\\b")
    private val exam = rx("\\b(next|upcoming|when('s| is)( my)?)\\b.*\\b(exams?|tests?|quiz(zes)?|midterms?|finals?)\\b")
    private val classes = rx("\\b(class|classes|lecture|lectures|free|busy)\\b")
    private val habits = rx("\\b(habits?|streaks?)\\b")

    fun intentOf(text: String, now: LocalDateTime): Intent {
        val t = text.trim()
        return when {
            planWeek.containsMatchIn(t) -> Intent.PlanWeek
            planSession.containsMatchIn(t) -> Intent.PlanSession
            exam.containsMatchIn(t) -> Intent.NextExam
            habits.containsMatchIn(t) -> Intent.Habits
            t.endsWith("?") && classes.containsMatchIn(t) -> Intent.Classes
            t.endsWith("?") || rx("^(what|when|which|how|am i|do i)\\b").containsMatchIn(t) ->
                if (due.containsMatchIn(t) || rx("\\b(today|tomorrow|this week|next week)\\b").containsMatchIn(t)) Intent.Due else Intent.Unknown
            looksLikeTasks(t, now) -> Intent.AddTasks
            else -> Intent.Unknown
        }
    }

    /** Several lines, an "add" command, or a date/priority/tag the parser understood. */
    fun looksLikeTasks(text: String, now: LocalDateTime): Boolean =
        text.contains('\n') || text.contains(';') || rx("^add\\b").containsMatchIn(text) ||
            SmartParser.parse(text, now).tokens.isNotEmpty()

    /** "add essay fri" means the task "essay fri". */
    fun stripAdd(text: String): String = text.replace(rx("^add\\s+(a\\s+task:?\\s*)?"), "").trim()

    private fun dueLabel(t: TaskItem, today: LocalDate, withDay: Boolean = true): String {
        val day = if (withDay) relativeDay(t.date!!, today) else null
        val time = t.time?.format(timeFormat)
        val bits = listOfNotNull(day, time)
        return if (bits.isEmpty()) t.title else "${t.title} (${bits.joinToString(", ")})"
    }

    /** "What's due today / tomorrow / this week / next week?" */
    fun dueAnswer(text: String, tasks: List<TaskItem>, today: LocalDate): String {
        val open = tasks.filter { !it.done && it.parentId == null && it.date != null }
        val (label, from, to) = when {
            rx("\\btoday\\b|\\btonight\\b").containsMatchIn(text) -> Triple("today", today, today)
            rx("\\btomorrow\\b").containsMatchIn(text) -> Triple("tomorrow", today.plusDays(1), today.plusDays(1))
            rx("\\bnext week\\b").containsMatchIn(text) -> {
                val mon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                Triple("next week", mon, mon.plusDays(6))
            }
            else -> Triple("in the next 7 days", today, today.plusDays(6))
        }
        val overdue = open.filter { it.date!!.isBefore(today) }
        val hits = open.filter { !it.date!!.isBefore(from) && !it.date.isAfter(to) }
            .sortedWith(compareBy<TaskItem>({ it.date }, { it.time ?: LocalTime.MAX }))
        val main = if (hits.isEmpty()) "Nothing is due $label." else
            "Due $label: " + hits.take(12).joinToString("; ") { dueLabel(it, today, withDay = from != to) } + if (hits.size > 12) "; and ${hits.size - 12} more." else "."
        return if (overdue.isNotEmpty() && from == today) "$main Overdue: " + overdue.take(5).joinToString("; ") { dueLabel(it, today) } + "." else main
    }

    fun nextExamAnswer(tasks: List<TaskItem>, today: LocalDate): String {
        val exam = tasks.filter { !it.done && it.date != null && !it.date.isBefore(today) && looksLikeExam(it.title, it.tags.map { t -> t.name }) }
            .minWithOrNull(compareBy<TaskItem>({ it.date }, { it.time ?: LocalTime.MAX }))
            ?: return "I don't see an upcoming exam. Tag it #exam or name it like \"Chem midterm\" and I'll find it."
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, exam.date)
        val inDays = when (days) {
            0L -> "today"
            1L -> "tomorrow"
            else -> "in $days days"
        }
        return "Your next exam is ${dueLabel(exam, today)}, $inDays."
    }

    fun classesAnswer(text: String, classes: List<SchoolClass>, today: LocalDate): String {
        val day = when {
            rx("\\btomorrow\\b").containsMatchIn(text) -> today.plusDays(1)
            else -> DayOfWeek.entries.firstOrNull { d -> rx("\\b${d.name.lowercase().take(3)}").containsMatchIn(text) }
                ?.let { today.with(TemporalAdjusters.nextOrSame(it)) } ?: today
        }
        val list = classes.filter { it.meetsOn(day) }.sortedBy { it.start }
        val name = relativeDay(day, today)
        if (list.isEmpty()) return if (classes.isEmpty()) "You haven't added classes yet. Add them in Calendar > Timetable." else "No classes $name. You're free."
        return "$name: " + list.joinToString("; ") { "${it.name} ${it.start.format(timeFormat)} to ${it.end.format(timeFormat)}" } + "."
    }

    fun habitsAnswer(habits: List<Habit>, today: LocalDate): String {
        if (habits.isEmpty()) return "You don't have any habits yet. Tell me one to start, like \"drink water 8 times a day\", or add it in Habits."
        return habits.joinToString(" ") { h ->
            val streak = h.currentStreak(today)
            val todayText = when {
                !h.isDue(today) -> "not due today"
                h.isDone(today) -> "done today"
                h.goal > 1 -> "${h.count(today)} of ${h.goal} today"
                else -> "not done yet today"
            }
            "${h.name}: ${if (streak == 1) "1 day" else "$streak days"} in a row, $todayText."
        }
    }
}
