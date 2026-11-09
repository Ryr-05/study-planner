package io.github.ryr05.noctil.ui.tasks

import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.ui.components.relativeDay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Which tasks the Tasks tab is showing. Smart lists are built from the data, not stored. */
sealed interface TaskView {
    val key: String

    data object Today : TaskView { override val key = "today" }
    data object Next7 : TaskView { override val key = "next7" }
    data object High : TaskView { override val key = "high" }
    data object All : TaskView { override val key = "all" }
    data object Completed : TaskView { override val key = "done" }
    data object Inbox : TaskView { override val key = "inbox" }
    data class OfList(val id: Long) : TaskView { override val key get() = "list:$id" }
    data class OfTag(val id: Long) : TaskView { override val key get() = "tag:$id" }

    companion object {
        val smart = listOf(Today, Next7, High, All, Completed)

        fun fromKey(key: String): TaskView = when {
            key == Next7.key -> Next7
            key == High.key -> High
            key == All.key -> All
            key == Completed.key -> Completed
            key == Inbox.key -> Inbox
            key.startsWith("list:") -> key.removePrefix("list:").toLongOrNull()?.let(::OfList) ?: Today
            key.startsWith("tag:") -> key.removePrefix("tag:").toLongOrNull()?.let(::OfTag) ?: Today
            else -> Today
        }
    }
}

enum class SectionTone { Normal, Danger }

data class Section(
    val key: String,
    val title: String,
    val tasks: List<TaskItem>,
    val showDate: Boolean,
    val tone: SectionTone = SectionTone.Normal,
    val collapsible: Boolean = false,
)

val openTaskOrder: Comparator<TaskItem> = compareBy<TaskItem>(
    { it.date ?: LocalDate.MAX },
    { it.time ?: LocalTime.MAX },
    { -it.priority.value },
    { it.sortOrder },
)

fun TaskItem.completedOn(): LocalDate? =
    completedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

fun viewTitle(view: TaskView, snap: Snapshot): String = when (view) {
    TaskView.Today -> "Today"
    TaskView.Next7 -> "Next 7 days"
    TaskView.High -> "High priority"
    TaskView.All -> "All tasks"
    TaskView.Completed -> "Completed"
    TaskView.Inbox -> "Inbox"
    is TaskView.OfList -> snap.lists.firstOrNull { it.id == view.id }?.name ?: "List"
    is TaskView.OfTag -> snap.tags.firstOrNull { it.id == view.id }?.let { "#${it.name}" } ?: "Tag"
}

/** Number of open tasks a view would show, for the counts in the lists drawer. */
fun openCount(view: TaskView, snap: Snapshot, today: LocalDate): Int {
    val open = snap.tasks.filter { it.parentId == null && !it.done }
    return when (view) {
        TaskView.Today -> open.count { it.date != null && !it.date.isAfter(today) }
        TaskView.Next7 -> open.count { it.date != null && !it.date.isAfter(today.plusDays(6)) }
        TaskView.High -> open.count { it.priority == Priority.High }
        TaskView.All -> open.size
        TaskView.Completed -> 0
        TaskView.Inbox -> open.count { it.list == null }
        is TaskView.OfList -> open.count { it.list?.id == view.id }
        is TaskView.OfTag -> open.count { t -> t.tags.any { it.id == view.id } }
    }
}

fun sectionsFor(view: TaskView, snap: Snapshot, today: LocalDate): List<Section> {
    val top = snap.tasks.filter { it.parentId == null }
    val open = top.filter { !it.done }
    val overdue = Section(
        "overdue", "Overdue",
        open.filter { it.date != null && it.date.isBefore(today) }.sortedWith(openTaskOrder),
        showDate = true, tone = SectionTone.Danger,
    )

    fun listSections(tasks: List<TaskItem>) = listOf(
        Section("open", "Tasks", tasks.filter { !it.done }.sortedWith(openTaskOrder), showDate = true),
        Section(
            "completed", "Completed",
            tasks.filter { it.done }.sortedByDescending { it.completedAt ?: 0 }.take(50),
            showDate = true, collapsible = true,
        ),
    )

    val sections = when (view) {
        TaskView.Today -> listOf(
            overdue,
            Section("today", "Today", open.filter { it.date == today }.sortedWith(openTaskOrder), showDate = false),
            Section(
                "doneToday", "Completed today",
                top.filter { it.done && it.completedOn() == today }.sortedByDescending { it.completedAt ?: 0 },
                showDate = false, collapsible = true,
            ),
        )
        TaskView.Next7 -> listOf(overdue) + (0L..6L).map { offset ->
            val day = today.plusDays(offset)
            Section(
                "day$offset", relativeDay(day, today),
                open.filter { it.date == day }.sortedWith(openTaskOrder),
                showDate = false,
            )
        }
        TaskView.High -> listOf(
            Section("high", "High priority", open.filter { it.priority == Priority.High }.sortedWith(openTaskOrder), showDate = true),
        )
        TaskView.All -> {
            val dated = open.filter { it.date != null }
            listOf(
                overdue,
                Section("today", "Today", dated.filter { it.date == today }.sortedWith(openTaskOrder), showDate = false),
                Section("tomorrow", "Tomorrow", dated.filter { it.date == today.plusDays(1) }.sortedWith(openTaskOrder), showDate = false),
                Section(
                    "week", "Next 7 days",
                    dated.filter { it.date!!.isAfter(today.plusDays(1)) && !it.date.isAfter(today.plusDays(7)) }
                        .sortedWith(openTaskOrder),
                    showDate = true,
                ),
                Section("later", "Later", dated.filter { it.date!!.isAfter(today.plusDays(7)) }.sortedWith(openTaskOrder), showDate = true),
                Section("nodate", "No date", open.filter { it.date == null }.sortedWith(openTaskOrder), showDate = false),
            )
        }
        TaskView.Completed -> listOf(
            Section(
                "completed", "Completed",
                snap.tasks.filter { it.done }.sortedByDescending { it.completedAt ?: 0 }.take(300),
                showDate = true,
            ),
        )
        TaskView.Inbox -> listSections(top.filter { it.list == null })
        is TaskView.OfList -> listSections(top.filter { it.list?.id == view.id })
        is TaskView.OfTag -> listSections(top.filter { t -> t.tags.any { it.id == view.id } })
    }
    return sections.filter { it.tasks.isNotEmpty() }
}

/** Finds the next exam: a task tagged #exam (or #test, #quiz) or with exam-like words in its title. */
private val examWords = Regex("\\b(exams?|midterms?|finals?|quiz(zes)?|tests?)\\b", RegexOption.IGNORE_CASE)
private val examTags = setOf("exam", "exams", "test", "quiz", "midterm", "final")

fun nextExam(snap: Snapshot, today: LocalDate): TaskItem? =
    snap.tasks
        .filter { !it.done && it.date != null && !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(60)) }
        .filter { t -> t.tags.any { it.name.lowercase() in examTags } || examWords.containsMatchIn(t.title) }
        .minWithOrNull(compareBy<TaskItem>({ it.date }, { it.time ?: LocalTime.MAX }))

fun searchTasks(snap: Snapshot, query: String): List<TaskItem> {
    val q = query.trim().removePrefix("#").lowercase()
    if (q.isEmpty()) return emptyList()
    return snap.tasks.filter { t ->
        t.title.lowercase().contains(q) ||
            t.notes.lowercase().contains(q) ||
            t.tags.any { it.name.lowercase().contains(q) } ||
            (t.list?.name?.lowercase()?.contains(q) == true) ||
            snap.checklist[t.id].orEmpty().any { it.text.lowercase().contains(q) }
    }.sortedWith(compareBy<TaskItem>({ it.done }).then(openTaskOrder)).take(100)
}
