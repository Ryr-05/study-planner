package io.github.ryr05.noctil.plan

import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.data.model.TaskList
import java.time.DayOfWeek
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
    val title: String? = null,
    val priority: Priority? = null,
    val tagsAdd: List<String> = emptyList(),
    val list: String? = null,
    val done: Boolean = false,
) {
    companion object {
        fun parse(o: JSONObject): AiChange? {
            val id = o.optLong("id", -1).takeIf { it > 0 } ?: return null
            val startRaw = o.optString("start").trim()
            val clear = startRaw.equals("none", ignoreCase = true)
            val start = if (clear) null else runCatching { LocalDateTime.parse(startRaw.take(16)) }.getOrNull()
            val date = runCatching { LocalDate.parse(o.optString("due_date").trim()) }.getOrNull()
            val time = runCatching { LocalTime.parse(o.optString("due_time").trim().take(5)) }.getOrNull()
            val title = o.optString("title").trim().takeIf { it.isNotEmpty() }
            val priority = when (o.optString("priority").trim().lowercase()) {
                "high" -> Priority.High
                "medium" -> Priority.Medium
                "low" -> Priority.Low
                "none" -> Priority.None
                else -> null
            }
            val tags = o.optJSONArray("tags_add")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
                .map { it.trim().removePrefix("#").trim() }.filter { it.matches(Regex("[\\p{L}\\p{N}_-]{1,32}")) }
            val list = o.optString("list").trim().takeIf { it.isNotEmpty() }
            val done = o.optBoolean("done", false)
            val c = AiChange(id, start, clear, date, time, title, priority, tags, list, done)
            return c.takeIf { it.start != null || it.clearStart || it.dueDate != null || it.dueTime != null || it.title != null ||
                it.priority != null || it.tagsAdd.isNotEmpty() || it.list != null || it.done }
        }
    }
}

/** A habit Gemini set up from a sentence like "drink water 8 times a day". */
data class AiHabit(val name: String, val goal: Int, val days: Set<DayOfWeek>, val reminder: LocalTime?) {
    companion object {
        private val dayNames = DayOfWeek.entries.associateBy { it.name.take(3).lowercase() }

        fun parse(o: JSONObject): AiHabit? {
            val name = o.optString("name").trim().take(60).takeIf { it.isNotEmpty() } ?: return null
            val goal = o.optInt("goal", 1).coerceIn(1, 50)
            val raw = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optString(it).trim().lowercase() } }.orEmpty()
            val days = when {
                raw.isEmpty() || raw.any { it == "daily" || it == "every day" || it == "everyday" } -> DayOfWeek.entries.toSet()
                raw.any { it == "weekdays" } -> DayOfWeek.entries.filter { it.value <= 5 }.toSet()
                raw.any { it == "weekends" } -> setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                else -> raw.mapNotNull { dayNames[it.take(3)] }.toSet()
            }.ifEmpty { DayOfWeek.entries.toSet() }
            val reminder = runCatching { LocalTime.parse(o.optString("reminder").trim().take(5)) }.getOrNull()
            return AiHabit(name, goal, days, reminder)
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
    /** Other edits that passed the checks; null means "leave it". */
    data class FieldEdit(
        val task: TaskItem,
        val title: String? = null,
        val priority: Priority? = null,
        val tagsAdd: List<String> = emptyList(),
        val list: TaskList? = null,
        val done: Boolean = false,
    )
    data class Checked(
        val starts: List<StartEdit>,
        val deadlines: List<DeadlineEdit>,
        val deadlinesRefused: Int,
        val startsRefused: List<RefusedStart> = emptyList(),
        val fields: List<FieldEdit> = emptyList(),
        /** List names the AI used that don't exist. */
        val unknownLists: List<String> = emptyList(),
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

    /** Gemini sometimes sends one item per field for the same task; fold them into one per task. */
    fun merge(changes: List<AiChange>): List<AiChange> =
        changes.groupBy { it.id }.map { (_, list) ->
            list.reduce { a, b ->
                a.copy(
                    start = b.start ?: a.start,
                    clearStart = a.clearStart || b.clearStart,
                    dueDate = b.dueDate ?: a.dueDate,
                    dueTime = b.dueTime ?: a.dueTime,
                    title = b.title ?: a.title,
                    priority = b.priority ?: a.priority,
                    tagsAdd = (a.tagsAdd + b.tagsAdd).distinct(),
                    list = b.list ?: a.list,
                    done = a.done || b.done,
                )
            }
        }

    /**
     * Does the student's message ask to change a deadline? Either it says so ("deadline", "due",
     * "extension"), or it says the class moved a date ("my prof moved the quiz to Friday").
     * "Move my essay to tomorrow" is about when to start, so it doesn't count.
     */
    fun asksAboutDeadline(message: String): Boolean =
        deadlineWords.containsMatchIn(message) || (movedWords.containsMatchIn(message) && setByClass.containsMatchIn(message))

    fun check(
        changes: List<AiChange>,
        known: Map<Long, TaskItem>,
        studentMessage: String,
        now: LocalDateTime,
        lists: List<TaskList> = emptyList(),
    ): Checked {
        val fields = mutableListOf<FieldEdit>()
        val unknownLists = mutableListOf<String>()
        val starts = mutableListOf<StartEdit>()
        val deadlines = mutableListOf<DeadlineEdit>()
        var refused = 0
        val startsRefused = mutableListOf<RefusedStart>()
        val deadlineAllowed = asksAboutDeadline(studentMessage)
        for (c in merge(changes)) {
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
            // Rename, priority, tags, list, done: only real changes, only lists that exist.
            val title = c.title?.trim()?.take(120)?.takeIf { it.isNotEmpty() && it != task.title }
            val priority = c.priority?.takeIf { it != task.priority }
            val tags = c.tagsAdd.filter { name -> task.tags.none { it.name.equals(name, ignoreCase = true) } }.distinctBy { it.lowercase() }
            val list = c.list?.let { name ->
                lists.firstOrNull { it.name.equals(name.removePrefix("~"), ignoreCase = true) } ?: run { unknownLists += name; null }
            }?.takeIf { it.id != task.list?.id }
            val done = c.done && !task.done
            if (title != null || priority != null || tags.isNotEmpty() || list != null || done) {
                fields += FieldEdit(task, title, priority, tags, list, done)
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
        return Checked(starts, deadlines, refused, startsRefused, fields, unknownLists.distinct())
    }

    /**
     * What the AI is shown: every open task (titles, dates, start, priority, list, tags, length),
     * soonest first. Never notes, checklists or places.
     */
    fun visibleTasks(tasks: List<TaskItem>, today: LocalDate, limit: Int = 150): List<TaskItem> =
        tasks.filter { t -> !t.done && t.parentId == null }
            .sortedWith(compareBy<TaskItem>({ it.date ?: LocalDate.MAX }, { it.time ?: LocalTime.MAX }, { -it.priority.value }))
            .take(limit)
}
