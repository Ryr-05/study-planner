package io.github.ryr05.noctil.plan

import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.data.model.looksLikeExam
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/**
 * Puts a student's tasks in a sensible order and fits them into the time they have, around
 * today's classes. Works fully offline. The AI chat (when turned on) uses this same planner, so
 * the order is always explainable and never made up.
 */
object Planner {
    const val MAX_QUESTIONS = 3
    private const val DEFAULT_MINUTES = 30
    private const val EXAM_STUDY_MINUTES = 60
    private const val LONG_TASK = 90
    private val dayEnd: LocalTime = LocalTime.of(23, 30)
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    /** What the student told the planner. */
    data class Answers(
        /** Minutes free right now; null = not asked yet, [REST_OF_DAY] = until late tonight. */
        val minutesAvailable: Int? = null,
        val lengths: Map<Long, Int> = emptyMap(),
        val favored: Set<Long> = emptySet(),
        val asked: Set<String> = emptySet(),
    )

    const val REST_OF_DAY = -1

    data class Ranked(val task: TaskItem, val score: Int, val reasons: List<String>)

    sealed interface Step {
        val start: LocalTime
        val end: LocalTime

        data class Work(
            val taskId: Long,
            val title: String,
            override val start: LocalTime,
            override val end: LocalTime,
            val reason: String,
            val part: Int = 1,
            val parts: Int = 1,
        ) : Step {
            val minutes: Int get() = ChronoUnit.MINUTES.between(start, end).toInt()
        }

        data class Break(override val start: LocalTime, override val end: LocalTime) : Step

        /** A class from the timetable the plan works around. */
        data class Class(val name: String, override val start: LocalTime, override val end: LocalTime) : Step
    }

    data class Plan(val steps: List<Step>, val later: List<TaskItem>) {
        val work: List<Step.Work> get() = steps.filterIsInstance<Step.Work>()
    }

    data class Option(val label: String, val value: String)

    data class Question(val id: String, val text: String, val options: List<Option>)

    /** The tasks worth planning: overdue, due within a week, or high priority without a date. */
    fun candidates(tasks: List<TaskItem>, today: LocalDate): List<TaskItem> =
        tasks.filter { t ->
            !t.done && t.parentId == null && (
                (t.date != null && !t.date.isAfter(today.plusDays(7))) ||
                    (t.date == null && t.priority == Priority.High) ||
                    t.startAt?.toLocalDate()?.isAfter(today) == false
                )
        }

    fun rank(tasks: List<TaskItem>, answers: Answers, now: LocalDateTime): List<Ranked> =
        tasks.map { score(it, answers, now) }
            .sortedWith(
                compareByDescending<Ranked> { it.score }
                    .thenBy { it.task.date ?: LocalDate.MAX }
                    .thenBy { it.task.time ?: LocalTime.MAX }
                    .thenByDescending { it.task.priority.value }
                    .thenBy { it.task.sortOrder },
            )

    fun score(t: TaskItem, answers: Answers, now: LocalDateTime): Ranked {
        val today = now.toLocalDate()
        val reasons = mutableListOf<String>()
        var s = 0
        val days = t.date?.let { ChronoUnit.DAYS.between(today, it) }
        val exam = t.looksLikeExam()
        s += when {
            days == null -> 10
            days < 0 -> 100.also { reasons += "Overdue" }
            days == 0L -> {
                val dueSoon = t.time != null && !t.time.isAfter(now.toLocalTime().plusHours(3))
                (if (dueSoon) 95 else 85).also { reasons += t.time?.let { "Due ${it.format(timeFormat)} today" } ?: "Due today" }
            }
            days == 1L -> 70.also { reasons += if (exam) "Exam tomorrow" else "Due tomorrow" }
            days <= 3 -> 55.also { reasons += if (exam) "Exam in $days days" else "Due in $days days" }
            else -> 30.also { reasons += if (exam) "Exam in $days days" else "Due ${t.date!!.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}" }
        }
        if (exam && days != null && days in 0..7) s += 20
        s += when (t.priority) {
            Priority.High -> 25.also { reasons += "High priority" }
            Priority.Medium -> 12
            Priority.Low -> 4
            Priority.None -> 0
        }
        // The student set a start time for today (or earlier): honor their own plan.
        if ((days == null || days > 0) && t.startAt?.toLocalDate()?.isAfter(today) == false) {
            s += 15
            reasons.add(0, "You planned to start today")
        }
        if (t.id in answers.favored) {
            s += 30
            reasons.add(0, "You said this matters more")
        }
        val length = lengthOf(t, answers)
        if (length <= 15) {
            s += 6
            reasons += "Quick win"
        }
        if (t.subtaskTotal > 0 && t.subtaskDone in 1 until t.subtaskTotal) {
            s += 4
            reasons += "Already started"
        }
        return Ranked(t, s, reasons)
    }

    fun lengthOf(t: TaskItem, answers: Answers): Int =
        answers.lengths[t.id] ?: t.durationMinutes ?: if (t.looksLikeExam()) EXAM_STUDY_MINUTES else DEFAULT_MINUTES

    /**
     * The next thing worth asking, or null when the plan is ready. Only asks what changes the
     * plan: how much time there is, how long big unknown tasks take, and which of two equally
     * urgent tasks matters more.
     */
    fun nextQuestion(tasks: List<TaskItem>, answers: Answers, now: LocalDateTime): Question? {
        if (answers.asked.size >= MAX_QUESTIONS + 1) return null
        if (answers.minutesAvailable == null && "time" !in answers.asked) {
            return Question(
                "time",
                "How much time do you have right now?",
                listOf(
                    Option("30 min", "30"),
                    Option("1 hour", "60"),
                    Option("2 hours", "120"),
                    Option("3 hours", "180"),
                    Option("Rest of the day", REST_OF_DAY.toString()),
                ),
            )
        }
        val ranked = rank(tasks, answers, now)
        val top = ranked.take(3)
        val lengthAsked = answers.asked.count { it.startsWith("len:") }
        if (lengthAsked < 2) {
            top.firstOrNull { r ->
                r.task.durationMinutes == null && r.task.id !in answers.lengths && "len:${r.task.id}" !in answers.asked && !r.task.looksLikeExam()
            }?.let { r ->
                return Question(
                    "len:${r.task.id}",
                    "About how long will \"${r.task.title}\" take?",
                    listOf(Option("15 min", "15"), Option("30 min", "30"), Option("45 min", "45"), Option("1 hour", "60"), Option("2 hours", "120")),
                )
            }
        }
        if (answers.asked.none { it.startsWith("pick:") } && answers.favored.isEmpty()) {
            ranked.take(4).zipWithNext().firstOrNull { (a, b) -> a.score - b.score <= 5 }?.let { (a, b) ->
                return Question(
                    "pick:${a.task.id}:${b.task.id}",
                    "These two are just as urgent. Which matters more right now?",
                    listOf(Option(a.task.title, a.task.id.toString()), Option(b.task.title, b.task.id.toString()), Option("Same to me", "same")),
                )
            }
        }
        return null
    }

    /** Applies the answer to [question] and remembers it was asked. */
    fun answer(answers: Answers, question: Question, value: String): Answers {
        val asked = answers.asked + question.id
        return when {
            question.id == "time" -> answers.copy(minutesAvailable = value.toIntOrNull() ?: 60, asked = asked)
            question.id.startsWith("len:") -> {
                val id = question.id.removePrefix("len:").toLong()
                answers.copy(lengths = answers.lengths + (id to (value.toIntOrNull() ?: DEFAULT_MINUTES)), asked = asked)
            }
            question.id.startsWith("pick:") -> answers.copy(favored = value.toLongOrNull()?.let { answers.favored + it } ?: answers.favored, asked = asked)
            else -> answers.copy(asked = asked)
        }
    }

    /**
     * Lays the ranked tasks out from now, skipping today's classes, with short breaks in between.
     * Long tasks are split in two. Whatever doesn't fit goes to [Plan.later].
     */
    fun plan(
        tasks: List<TaskItem>,
        answers: Answers,
        now: LocalDateTime,
        classes: List<SchoolClass> = emptyList(),
        breakMinutes: Int = 5,
    ): Plan {
        val today = now.toLocalDate()
        val start = roundUp(now.toLocalTime())
        val available = answers.minutesAvailable ?: 60
        val end = if (available == REST_OF_DAY || start.plusMinutes(available.toLong()).isBefore(start)) {
            dayEnd
        } else {
            minOf(start.plusMinutes(available.toLong()), dayEnd)
        }
        val busy = classes.filter { it.meetsOn(today) && it.end.isAfter(start) && it.start.isBefore(end) }
            .sortedBy { it.start }
        val steps = mutableListOf<Step>()
        val later = mutableListOf<TaskItem>()
        var cursor = start

        fun skipClasses(at: LocalTime): LocalTime {
            var t = at
            busy.forEach { c ->
                if (!t.isBefore(c.start) && t.isBefore(c.end)) {
                    if (steps.none { it is Step.Class && it.name == c.name && it.start == c.start }) steps += Step.Class(c.name, c.start, c.end)
                    t = c.end
                }
            }
            return t
        }

        /** Free minutes from [from] until the next class or the end of the plan. */
        fun freeFrom(from: LocalTime): Int {
            val nextClass = busy.firstOrNull { it.start.isAfter(from) || it.start == from }?.start ?: end
            return ChronoUnit.MINUTES.between(from, minOf(nextClass, end)).toInt()
        }

        var restDue = false
        for (r in rank(tasks, answers, now)) {
            val length = lengthOf(r.task, answers)
            val parts = if (length > LONG_TASK) 2 else 1
            val chunk = (length + parts - 1) / parts
            val reason = r.reasons.firstOrNull() ?: "Next up"
            var placed = 0
            var probe = cursor
            val planned = mutableListOf<Step>()
            // A short break between tasks, unless time is tight: then they go back to back.
            if (restDue) {
                val at = skipClasses(probe)
                if (at == probe && freeFrom(probe) >= length + breakMinutes + (parts - 1) * breakMinutes) {
                    planned += Step.Break(probe, probe.plusMinutes(breakMinutes.toLong()))
                    probe = probe.plusMinutes(breakMinutes.toLong())
                }
            }
            for (p in 1..parts) {
                probe = skipClasses(probe)
                if (freeFrom(probe) < chunk) {
                    // Try after the next class, if that still fits before the end.
                    val after = busy.firstOrNull { it.start.isAfter(probe) }?.end
                    if (after != null && freeFrom(skipClasses(after)) >= chunk) probe = skipClasses(after) else break
                }
                val blockEnd = probe.plusMinutes(chunk.toLong())
                planned += Step.Work(r.task.id, r.task.title, probe, blockEnd, reason, p, parts)
                probe = blockEnd
                placed++
                if (p < parts) {
                    planned += Step.Break(probe, probe.plusMinutes(breakMinutes.toLong()))
                    probe = probe.plusMinutes(breakMinutes.toLong())
                }
            }
            if (placed < parts) {
                later += r.task
                continue
            }
            steps += planned
            cursor = probe
            restDue = true
        }
        // A trailing break isn't useful.
        while (steps.lastOrNull() is Step.Break) steps.removeAt(steps.lastIndex)
        // Show the classes the plan works around, so the gaps make sense.
        val last = steps.filterIsInstance<Step.Work>().maxOfOrNull { it.end }
        if (last != null) {
            busy.filter { it.start.isBefore(last) && it.end.isAfter(start) }.forEach { c ->
                if (steps.none { it is Step.Class && it.name == c.name && it.start == c.start }) steps += Step.Class(c.name, c.start, c.end)
            }
        }
        return Plan(steps.sortedBy { it.start }, later)
    }

    private fun roundUp(t: LocalTime): LocalTime {
        val m = t.minute
        val up = ((m + 4) / 5) * 5
        val base = t.withSecond(0).withNano(0).withMinute(0)
        return base.plusMinutes(up.toLong())
    }
}
