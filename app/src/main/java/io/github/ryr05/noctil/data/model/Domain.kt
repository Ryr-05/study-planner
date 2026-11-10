package io.github.ryr05.noctil.data.model

import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class Folder(val id: Long, val name: String, val sortOrder: Int)

data class TaskList(val id: Long, val name: String, val color: Color, val folderId: Long?)

data class Tag(val id: Long, val name: String, val color: Color)

data class ChecklistItem(val id: Long, val taskId: Long, val text: String, val done: Boolean)

data class TaskItem(
    val id: Long,
    val parentId: Long?,
    val title: String,
    val notes: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val priority: Priority,
    val list: TaskList?,
    val tags: List<Tag>,
    val recurrence: Recurrence?,
    val reminder: Int?,
    val done: Boolean,
    val completedAt: Long?,
    val sortOrder: Long,
    val createdAt: Long,
    val subtaskTotal: Int,
    val subtaskDone: Int,
    val checklistTotal: Int,
    val checklistDone: Int,
    val durationMinutes: Int? = null,
    val stageId: Long? = null,
    val startDate: LocalDate? = null,
    val nag: Boolean = false,
    val placeId: Long? = null,
    val alarm: Boolean = false,
) {
    /** Minutes the task takes up on the calendar. */
    val blockMinutes: Int get() = durationMinutes ?: DEFAULT_TASK_MINUTES
}

const val DEFAULT_TASK_MINUTES = 30

/** A custom board column. "To Do" and "Done" are built in. */
data class Stage(val id: Long, val name: String, val sortOrder: Int)

/** A saved place for location reminders. */
data class Place(val id: Long, val name: String, val latitude: Double, val longitude: Double, val radius: Int)

/** A weekly class from the timetable. */
data class SchoolClass(
    val id: Long,
    val name: String,
    val room: String,
    val color: Color,
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val listId: Long?,
) {
    fun meetsOn(date: LocalDate): Boolean = date.dayOfWeek in days
}

fun Set<DayOfWeek>.toMask(): Int = sumOf { 1 shl (it.value - 1) }

fun daysFromMask(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

/** Everything the screens need, rebuilt whenever the database changes. */
data class Snapshot(
    val tasks: List<TaskItem>,
    val lists: List<TaskList>,
    val folders: List<Folder>,
    val tags: List<Tag>,
    val checklist: Map<Long, List<ChecklistItem>>,
    val loaded: Boolean,
    val classes: List<SchoolClass> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val focus: List<FocusSession> = emptyList(),
    val stages: List<Stage> = emptyList(),
    val places: List<Place> = emptyList(),
) {
    val placesById: Map<Long, Place> by lazy { places.associateBy { it.id } }
    val byId: Map<Long, TaskItem> by lazy { tasks.associateBy { it.id } }
    val childrenOf: Map<Long, List<TaskItem>> by lazy {
        tasks.filter { it.parentId != null }.groupBy { it.parentId!! }
            .mapValues { (_, kids) -> kids.sortedWith(compareBy({ it.done }, { it.sortOrder })) }
    }

    companion object {
        val Empty = Snapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), loaded = false)
    }
}

/** Colors offered for lists and tags. All read well on the dark background. */
val ItemColors = listOf(
    Color(0xFFB9A3FF), // lavender
    Color(0xFF8DB8FF), // blue
    Color(0xFF7FD6B5), // mint
    Color(0xFFFFC978), // amber
    Color(0xFFFF8FA3), // rose
    Color(0xFFF2A7C9), // pink
    Color(0xFF9EE6F0), // cyan
    Color(0xFFD7E38A), // lime
)
