package io.github.ryr05.noctil.plan

import io.github.ryr05.noctil.data.model.TaskItem
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One change Gemini asked for. The app decides whether it's allowed. */
data class AiChange(
    val id: Long,
    /** New start, or null to leave it alone. */
    val start: LocalDateTime? = null,
    /** True to remove the start reminder. */
    val clearStart: Boolean = false,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
) {
    companion object {
        fun parse(o: JSONObject): AiChange? {
            val id = o.optLong("id", -1).takeIf { it > 0 } ?: return null
            val startRaw = o.optString("start").trim()
            val clear = startRaw.equals("none", ignoreCase = true)
            val start = if (clear) null else runCatching { LocalDateTime.parse(startRaw.take(16)) }.getOrNull()
            val date = runCatching { LocalDate.parse(o.optString("due_date").trim()) }.getOrNull()
            val time = runCatching { LocalTime.parse(o.optString("due_time").trim().take(5)) }.getOrNull()
            if (start == null && !clear && date == null && time == null) return null
            return AiChange(id, start, clear, date, time)
        }
    }
}

/**
 * The app's own locks on what the AI may change, whatever it says:
 * - only tasks it was shown;
 * - start times must be in the future and before the deadline;
 * - deadlines only when the student's own message is about the deadline, and even then the
 *   student confirms on a card (the AI never moves a deadline by itself).
 */
object AiChangeGuard {
    data class StartEdit(val task: TaskItem, val old: LocalDateTime?, val new: LocalDateTime?)
    data class DeadlineEdit(
        val task: TaskItem,
        val oldDate: LocalDate?,
        val oldTime: LocalTime?,
        val newDate: LocalDate,
        val newTime: LocalTime?,
    )
    /** A start the app turned down: already past, or after the task's deadline. */
    data class RefusedStart(val task: TaskItem, val at: LocalDateTime, val afterDeadline: Boolean)
    data class Checked(
        val starts: List<StartEdit>,
        val deadlines: List<DeadlineEdit>,
        val deadlinesRefused: Int,
        val startsRefused: List<RefusedStart> = emptyList(),
    )

    private val deadlineWords = Regex(
        "\\b(deadlines?|due|extend(ed|s|ing)?|extensions?|postpone[ds]?|push(ed)?\\s+(it\\s+)?back|resched\\w*)\\b",
        RegexOption.IGNORE_CASE,
    )

    // "My prof moved the quiz", "the midterm got rescheduled": a date set by the class changed.
    private val movedWords = Regex(
        "\\b(mov(e|ed|es|ing)|chang(e|ed|es|ing)|reschedul\\w*|push(ed)?|delay(ed)?|is now|now on|now at)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val setByClass = Regex(
        "\\b(prof|profs|professor|teacher|instructor|lecturer|ta|class|exams?|quiz(zes)?|tests?|midterms?|finals?)\\b",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Does the student's message ask to change a deadline? Either it says so ("deadline", "due",
     * "extension"), or it says the class moved a date ("my prof moved the quiz to Friday").
     * "Move my essay to tomorrow" is about when to start, so it doesn't count.
     */
    fun asksAboutDeadline(message: String): Boolean =
        deadlineWords.containsMatchIn(message) || (movedWords.containsMatchIn(message) && setByClass.containsMatchIn(message))

    fun check(changes: List<AiChange>, known: Map<Long, TaskItem>, studentMessage: String, now: LocalDateTime): Checked {
        val starts = mutableListOf<StartEdit>()
        val deadlines = mutableListOf<DeadlineEdit>()
        var refused = 0
        val startsRefused = mutableListOf<RefusedStart>()
        val deadlineAllowed = asksAboutDeadline(studentMessage)
        for (c in changes.distinctBy { it.id }) {
            val task = known[c.id] ?: continue
            if (task.done) continue
            // Deadline first, so a start can be checked against the new one.
            var dueDate = task.date
            var dueTime = task.time
            val wantsDeadline = c.dueDate != null || c.dueTime != null
            if (wantsDeadline) {
                val newDate = c.dueDate ?: task.date
                val newTime = c.dueTime ?: task.time
                val changed = newDate != task.date || newTime != task.time
                when {
                    !changed -> Unit
                    !deadlineAllowed || newDate == null -> refused++
                    else -> {
                        deadlines += DeadlineEdit(task, task.date, task.time, newDate, newTime)
                        dueDate = newDate
                        dueTime = newTime
                    }
                }
            }
            if (c.clearStart && task.startAt != null) {
                starts += StartEdit(task, task.startAt, null)
            } else if (c.start != null && c.start != task.startAt) {
                val deadline = dueDate?.atTime(dueTime ?: LocalTime.MAX)
                when {
                    !c.start.isAfter(now) -> startsRefused += RefusedStart(task, c.start, afterDeadline = false)
                    deadline != null && !c.start.isBefore(deadline) -> startsRefused += RefusedStart(task, c.start, afterDeadline = true)
                    else -> starts += StartEdit(task, task.startAt, c.start)
                }
            }
        }
        return Checked(starts, deadlines, refused, startsRefused)
    }

    /**
     * What the AI is shown so it can change things by name: open tasks that are overdue, due in
     * the next two weeks, or have a start set. Titles and dates only.
     */
    fun visibleTasks(tasks: List<TaskItem>, today: LocalDate, limit: Int = 40): List<TaskItem> =
        tasks.filter { t ->
            !t.done && t.parentId == null &&
                ((t.date != null && !t.date.isAfter(today.plusDays(14))) || t.startAt != null)
        }.sortedWith(compareBy<TaskItem>({ it.date ?: LocalDate.MAX }, { it.time ?: LocalTime.MAX })).take(limit)
}
