package io.github.ryr05.noctil.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.room.withTransaction
import io.github.ryr05.noctil.data.db.ChecklistEntity
import io.github.ryr05.noctil.data.db.ClassEntity
import io.github.ryr05.noctil.data.db.FolderEntity
import io.github.ryr05.noctil.data.db.ListEntity
import io.github.ryr05.noctil.data.db.NoctilDatabase
import io.github.ryr05.noctil.data.db.TagEntity
import io.github.ryr05.noctil.data.db.TaskEntity
import io.github.ryr05.noctil.data.db.TaskTagEntity
import io.github.ryr05.noctil.data.model.ChecklistItem
import io.github.ryr05.noctil.data.model.Folder
import io.github.ryr05.noctil.data.model.ItemColors
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.Recurrence
import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.data.model.daysFromMask
import io.github.ryr05.noctil.data.model.toMask
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.Tag
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.data.model.TaskList
import io.github.ryr05.noctil.parse.ParsedInput
import io.github.ryr05.noctil.reminders.Notifications
import io.github.ryr05.noctil.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/** What to undo after finishing a task. */
data class Completion(val taskId: Long, val createdNextId: Long?, val recurrence: String?)

fun TaskEntity.localDate(): LocalDate? = dueDate?.let(LocalDate::ofEpochDay)
fun TaskEntity.localTime(): LocalTime? = dueTime?.let { LocalTime.of(it / 60, it % 60) }
fun LocalTime.toMinutes(): Int = hour * 60 + minute

/**
 * The single place the app reads and writes data. Screens observe [snapshot] and call the
 * functions below. Every change goes to the on-device database first, and reminders are
 * rescheduled to match.
 */
class Repository(
    private val context: Context,
    val db: NoctilDatabase,
    private val scheduler: ReminderScheduler,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences("noctil", Context.MODE_PRIVATE)

    val snapshot: StateFlow<Snapshot> = combine(
        combine(db.tasks().observeAll(), db.checklist().observeAll(), db.tags().observeLinks()) { t, c, l ->
            Triple(t, c, l)
        },
        combine(
            db.lists().observeLists(), db.lists().observeFolders(), db.tags().observeTags(), db.classes().observeAll(),
        ) { l, f, g, c -> Quad(l, f, g, c) },
    ) { a, b -> buildSnapshot(a.first, a.second, a.third, b.a, b.b, b.c, b.d) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, Snapshot.Empty)

    init {
        scope.launch {
            seedIfNeeded()
            rescheduleAllNow()
        }
    }

    var askedForNotifications: Boolean
        get() = prefs.getBoolean("asked_notifications", false)
        set(value) = prefs.edit().putBoolean("asked_notifications", value).apply()

    // ---------- Tasks ----------

    fun addTask(parsed: ParsedInput, listId: Long?, parentId: Long? = null, onAdded: (Long) -> Unit = {}) {
        scope.launch {
            val now = System.currentTimeMillis()
            val parent = parentId?.let { db.tasks().get(it) }
            val resolvedList = parsed.listName?.let { ensureList(it) } ?: parent?.listId ?: listId
            val id = db.withTransaction {
                val newId = db.tasks().insert(
                    TaskEntity(
                        title = parsed.title,
                        listId = resolvedList,
                        parentId = parentId,
                        dueDate = parsed.date?.toEpochDay(),
                        dueTime = parsed.time?.toMinutes(),
                        priority = parsed.priority.value,
                        recurrence = parsed.recurrence?.encode(),
                        reminder = if (parsed.date != null) 0 else null,
                        sortOrder = db.tasks().maxSortOrder() + 1,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                parsed.tags.forEach { linkTagByName(newId, it) }
                newId
            }
            db.tasks().get(id)?.let(scheduler::schedule)
            onAdded(id)
        }
    }

    fun update(id: Long, transform: (TaskEntity) -> TaskEntity) {
        scope.launch { updateNow(id, transform) }
    }

    suspend fun updateNow(id: Long, transform: (TaskEntity) -> TaskEntity) {
        val current = db.tasks().get(id) ?: return
        val changed = transform(current)
        if (changed == current) return
        val saved = changed.copy(updatedAt = System.currentTimeMillis())
        db.tasks().update(saved)
        scheduler.schedule(saved)
    }

    /** Sets a date and, when a task gets its first date, turns on the default reminder. */
    fun setDate(id: Long, date: LocalDate?) = update(id) { t ->
        val reminder = when {
            date == null -> null
            t.dueDate == null && t.reminder == null -> 0
            else -> t.reminder
        }
        t.copy(dueDate = date?.toEpochDay(), dueTime = if (date == null) null else t.dueTime, reminder = reminder)
    }

    fun setDone(id: Long, done: Boolean, onResult: (Completion) -> Unit = {}) {
        scope.launch { setDoneNow(id, done)?.let(onResult) }
    }

    suspend fun setDoneNow(id: Long, done: Boolean): Completion? {
        val current = db.tasks().get(id) ?: return null
        if (current.done == done) return null
        val now = System.currentTimeMillis()
        val recurrence = Recurrence.decode(current.recurrence)
        val spawnNext = done && recurrence != null && current.dueDate != null
        var nextId: Long? = null
        db.withTransaction {
            db.tasks().update(
                current.copy(
                    done = done,
                    completedAt = if (done) now else null,
                    // The finished copy stops repeating; the new copy carries the pattern forward.
                    recurrence = if (spawnNext) null else current.recurrence,
                    updatedAt = now,
                ),
            )
            if (spawnNext) {
                val nextDate = recurrence!!.next(LocalDate.ofEpochDay(current.dueDate!!))
                val newId = db.tasks().insert(
                    current.copy(
                        id = 0, done = false, completedAt = null, dueDate = nextDate.toEpochDay(),
                        sortOrder = db.tasks().maxSortOrder() + 1, createdAt = now, updatedAt = now,
                    ),
                )
                db.tags().tagIdsFor(current.id).forEach { db.tags().link(TaskTagEntity(newId, it)) }
                db.checklist().forTask(current.id).forEach {
                    db.checklist().insert(it.copy(id = 0, taskId = newId, done = false))
                }
                nextId = newId
            }
        }
        if (done) {
            scheduler.cancel(id)
            Notifications.cancel(context, id)
        } else {
            db.tasks().get(id)?.let(scheduler::schedule)
        }
        nextId?.let { db.tasks().get(it)?.let(scheduler::schedule) }
        return Completion(id, nextId, current.recurrence)
    }

    fun undoCompletion(c: Completion) {
        scope.launch {
            c.createdNextId?.let {
                db.tasks().setDeleted(listOf(it), true, System.currentTimeMillis())
                scheduler.cancel(it)
            }
            updateNow(c.taskId) { it.copy(done = false, completedAt = null, recurrence = c.recurrence) }
        }
    }

    /** Soft-deletes a task and all its subtasks. Returns the ids so the delete can be undone. */
    fun delete(id: Long, onResult: (List<Long>) -> Unit = {}) {
        scope.launch {
            val ids = mutableListOf<Long>()
            val queue = ArrayDeque(listOf(id))
            while (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                ids += next
                queue.addAll(db.tasks().childIds(next))
            }
            db.tasks().setDeleted(ids, true, System.currentTimeMillis())
            ids.forEach {
                scheduler.cancel(it)
                Notifications.cancel(context, it)
            }
            onResult(ids)
        }
    }

    fun restore(ids: List<Long>) {
        scope.launch {
            db.tasks().setDeleted(ids, false, System.currentTimeMillis())
            ids.forEach { id -> db.tasks().get(id)?.let(scheduler::schedule) }
        }
    }

    /** Places a task at a date and time, as when dragging it on the calendar. */
    fun schedule(id: Long, date: LocalDate, time: LocalTime?) = update(id) { t ->
        t.copy(dueDate = date.toEpochDay(), dueTime = time?.toMinutes(), reminder = t.reminder ?: 0)
    }

    fun setDuration(id: Long, minutes: Int?) = update(id) { it.copy(durationMinutes = minutes) }

    // ---------- Timetable ----------

    fun saveClass(
        id: Long?, name: String, room: String, color: Color,
        days: Set<java.time.DayOfWeek>, start: LocalTime, end: LocalTime, listId: Long?,
    ) {
        if (name.isBlank() || days.isEmpty() || !end.isAfter(start)) return
        scope.launch {
            val entity = ClassEntity(
                id = id ?: 0, name = name.trim(), room = room.trim(), color = color.toArgb(),
                days = days.toMask(), startMinute = start.toMinutes(), endMinute = end.toMinutes(), listId = listId,
            )
            if (id == null) db.classes().insert(entity) else db.classes().update(entity)
        }
    }

    fun deleteClass(id: Long) {
        scope.launch { db.classes().delete(id) }
    }

    // ---------- Checklist ----------

    fun addChecklistItem(taskId: Long, text: String) {
        if (text.isBlank()) return
        scope.launch {
            val order = db.checklist().forTask(taskId).maxOfOrNull { it.sortOrder } ?: 0
            db.checklist().insert(ChecklistEntity(taskId = taskId, text = text.trim(), sortOrder = order + 1))
        }
    }

    fun toggleChecklistItem(id: Long) {
        scope.launch {
            val item = db.checklist().get(id) ?: return@launch
            db.checklist().update(item.copy(done = !item.done, updatedAt = System.currentTimeMillis()))
        }
    }

    fun editChecklistItem(id: Long, text: String) {
        scope.launch {
            val item = db.checklist().get(id) ?: return@launch
            if (text.isBlank()) db.checklist().delete(id)
            else if (text != item.text) db.checklist().update(item.copy(text = text, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteChecklistItem(id: Long) {
        scope.launch { db.checklist().delete(id) }
    }

    // ---------- Tags ----------

    fun addTag(taskId: Long, name: String) {
        val clean = name.trim().removePrefix("#").trim()
        if (clean.isEmpty()) return
        scope.launch { linkTagByName(taskId, clean) }
    }

    fun removeTag(taskId: Long, tagId: Long) {
        scope.launch { db.tags().unlink(taskId, tagId) }
    }

    fun updateTag(id: Long, name: String, color: Color) {
        scope.launch {
            val clean = name.trim().removePrefix("#")
            if (clean.isEmpty()) return@launch
            val clash = db.tags().findByName(clean)
            if (clash != null && clash.id != id) return@launch
            db.tags().update(TagEntity(id = id, name = clean, color = color.toArgb()))
        }
    }

    fun deleteTag(id: Long) {
        scope.launch {
            db.withTransaction {
                db.tags().unlinkAll(id)
                db.tags().delete(id)
            }
        }
    }

    // ---------- Lists and folders ----------

    fun createList(name: String, color: Color, folderId: Long?, onCreated: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        scope.launch {
            val id = db.lists().insertList(
                ListEntity(name = name.trim(), color = color.toArgb(), folderId = folderId, sortOrder = db.lists().listCount()),
            )
            onCreated(id)
        }
    }

    fun updateList(id: Long, name: String, color: Color, folderId: Long?) {
        if (name.isBlank()) return
        scope.launch {
            val current = db.lists().getList(id) ?: return@launch
            db.lists().updateList(
                current.copy(name = name.trim(), color = color.toArgb(), folderId = folderId, updatedAt = System.currentTimeMillis()),
            )
        }
    }

    /** Deletes a list. Its tasks are kept and moved to the Inbox. */
    fun deleteList(id: Long) {
        scope.launch {
            db.withTransaction {
                db.tasks().moveListToInbox(id, System.currentTimeMillis())
                db.lists().deleteList(id)
            }
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        scope.launch { db.lists().insertFolder(FolderEntity(name = name.trim())) }
    }

    fun renameFolder(id: Long, name: String, sortOrder: Int) {
        if (name.isBlank()) return
        scope.launch { db.lists().updateFolder(FolderEntity(id = id, name = name.trim(), sortOrder = sortOrder)) }
    }

    /** Deletes a folder. Its lists are kept and shown ungrouped. */
    fun deleteFolder(id: Long) {
        scope.launch {
            db.withTransaction {
                db.lists().ungroup(id)
                db.lists().deleteFolder(id)
            }
        }
    }

    // ---------- Reminders ----------

    fun rescheduleAll() {
        scope.launch { rescheduleAllNow() }
    }

    suspend fun rescheduleAllNow() {
        db.tasks().withReminders().forEach(scheduler::schedule)
    }

    suspend fun task(id: Long): TaskEntity? = db.tasks().get(id)

    suspend fun listName(id: Long?): String? = id?.let { db.lists().getList(it)?.name }

    // ---------- Internals ----------

    private suspend fun ensureList(name: String): Long {
        db.lists().findListByName(name)?.let { return it.id }
        val count = db.lists().listCount()
        return db.lists().insertList(
            ListEntity(name = name, color = ItemColors[count % ItemColors.size].toArgb(), sortOrder = count),
        )
    }

    private suspend fun linkTagByName(taskId: Long, name: String) {
        val existing = db.tags().findByName(name)
        val tagId = existing?.id ?: run {
            val count = db.tags().count()
            val inserted = db.tags().insert(TagEntity(name = name, color = ItemColors[(count + 3) % ItemColors.size].toArgb()))
            if (inserted > 0) inserted else db.tags().findByName(name)?.id
        } ?: return
        db.tags().link(TaskTagEntity(taskId, tagId))
    }

    private suspend fun seedIfNeeded() {
        if (prefs.getBoolean("seeded_v1", false)) return
        if (db.lists().listCount() == 0) {
            val today = LocalDate.now().toEpochDay()
            db.withTransaction {
                db.lists().insertList(ListEntity(name = "School", color = ItemColors[0].toArgb(), sortOrder = 0))
                db.lists().insertList(ListEntity(name = "Personal", color = ItemColors[2].toArgb(), sortOrder = 1))
                listOf(
                    "Tap the circle to finish a task",
                    "Tap a task to add notes, subtasks and a checklist",
                    "Swipe right to finish, swipe left to move to tomorrow",
                    "Tap Today at the top to see your lists",
                ).forEachIndexed { i, title ->
                    db.tasks().insert(TaskEntity(title = title, dueDate = today, sortOrder = i.toLong()))
                }
            }
        }
        prefs.edit().putBoolean("seeded_v1", true).apply()
    }

    private fun buildSnapshot(
        tasks: List<TaskEntity>,
        checklist: List<ChecklistEntity>,
        links: List<TaskTagEntity>,
        lists: List<ListEntity>,
        folders: List<FolderEntity>,
        tags: List<TagEntity>,
        classes: List<ClassEntity>,
    ): Snapshot {
        val listModels = lists.map { TaskList(it.id, it.name, Color(it.color), it.folderId) }
        val listById = listModels.associateBy { it.id }
        val tagModels = tags.map { Tag(it.id, it.name, Color(it.color)) }
        val tagById = tagModels.associateBy { it.id }
        val tagsByTask = links.groupBy({ it.taskId }, { it.tagId })
        val checklistByTask = checklist.groupBy { it.taskId }
            .mapValues { (_, items) -> items.map { ChecklistItem(it.id, it.taskId, it.text, it.done) } }
        val childrenByParent = tasks.filter { it.parentId != null }.groupBy { it.parentId!! }

        val taskModels = tasks.map { t ->
            val kids = childrenByParent[t.id].orEmpty()
            val items = checklistByTask[t.id].orEmpty()
            TaskItem(
                id = t.id,
                parentId = t.parentId,
                title = t.title,
                notes = t.notes,
                date = t.localDate(),
                time = t.localTime(),
                priority = Priority.of(t.priority),
                list = t.listId?.let(listById::get),
                tags = tagsByTask[t.id].orEmpty().mapNotNull(tagById::get).sortedBy { it.name.lowercase() },
                recurrence = Recurrence.decode(t.recurrence),
                reminder = t.reminder,
                done = t.done,
                completedAt = t.completedAt,
                sortOrder = t.sortOrder,
                createdAt = t.createdAt,
                subtaskTotal = kids.size,
                subtaskDone = kids.count { it.done },
                checklistTotal = items.size,
                checklistDone = items.count { it.done },
                durationMinutes = t.durationMinutes,
            )
        }
        return Snapshot(
            tasks = taskModels,
            lists = listModels,
            folders = folders.map { Folder(it.id, it.name, it.sortOrder) },
            tags = tagModels,
            checklist = checklistByTask,
            loaded = true,
            classes = classes.map {
                SchoolClass(
                    id = it.id, name = it.name, room = it.room, color = Color(it.color),
                    days = daysFromMask(it.days),
                    start = LocalTime.of(it.startMinute / 60, it.startMinute % 60),
                    end = LocalTime.of(it.endMinute / 60, it.endMinute % 60),
                    listId = it.listId,
                )
            },
        )
    }
}
