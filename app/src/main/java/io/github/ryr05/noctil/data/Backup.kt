package io.github.ryr05.noctil.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.calendar.CalendarSync
import io.github.ryr05.noctil.data.db.ChecklistEntity
import io.github.ryr05.noctil.data.db.ClassEntity
import io.github.ryr05.noctil.data.db.FocusSessionEntity
import io.github.ryr05.noctil.data.db.FolderEntity
import io.github.ryr05.noctil.data.db.HabitCheckEntity
import io.github.ryr05.noctil.data.db.HabitEntity
import io.github.ryr05.noctil.data.db.ListEntity
import io.github.ryr05.noctil.data.db.TagEntity
import io.github.ryr05.noctil.data.db.TaskEntity
import io.github.ryr05.noctil.data.db.TaskTagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * Backup and restore as a single JSON file. Save it anywhere the phone's file picker reaches,
 * including Google Drive, so it doubles as a cloud backup with no account in Noctil.
 */
object Backup {
    private const val FORMAT = 1

    suspend fun export(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val b = (context.applicationContext as NoctilApplication).repository.db.backup()
            val root = JSONObject()
                .put("app", "Noctil")
                .put("format", FORMAT)
                .put("exportedAt", System.currentTimeMillis())
                .put("folders", JSONArray(b.folders().map { JSONObject().put("id", it.id).put("name", it.name).put("sortOrder", it.sortOrder).put("updatedAt", it.updatedAt) }))
                .put("lists", JSONArray(b.lists().map { JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).putOpt("folderId", it.folderId).put("sortOrder", it.sortOrder).put("updatedAt", it.updatedAt) }))
                .put("tasks", JSONArray(b.tasks().map { t ->
                    JSONObject().put("id", t.id).put("title", t.title).put("notes", t.notes).putOpt("listId", t.listId).putOpt("parentId", t.parentId)
                        .putOpt("dueDate", t.dueDate).putOpt("dueTime", t.dueTime).put("priority", t.priority).putOpt("recurrence", t.recurrence)
                        .putOpt("reminder", t.reminder).put("done", t.done).putOpt("completedAt", t.completedAt).put("sortOrder", t.sortOrder)
                        .put("createdAt", t.createdAt).put("updatedAt", t.updatedAt).putOpt("durationMinutes", t.durationMinutes)
                }))
                .put("checklist", JSONArray(b.checklist().map { JSONObject().put("id", it.id).put("taskId", it.taskId).put("text", it.text).put("done", it.done).put("sortOrder", it.sortOrder) }))
                .put("tags", JSONArray(b.tags().map { JSONObject().put("id", it.id).put("name", it.name).put("color", it.color) }))
                .put("taskTags", JSONArray(b.taskTags().map { JSONObject().put("taskId", it.taskId).put("tagId", it.tagId) }))
                .put("classes", JSONArray(b.classes().map { JSONObject().put("id", it.id).put("name", it.name).put("room", it.room).put("color", it.color).put("days", it.days).put("startMinute", it.startMinute).put("endMinute", it.endMinute).putOpt("listId", it.listId) }))
                .put("habits", JSONArray(b.habits().map { JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("days", it.days).put("goal", it.goal).putOpt("reminderMinute", it.reminderMinute).put("sortOrder", it.sortOrder).put("createdAt", it.createdAt).put("archived", it.archived) }))
                .put("habitChecks", JSONArray(b.habitChecks().map { JSONObject().put("habitId", it.habitId).put("day", it.day).put("count", it.count) }))
                .put("focusSessions", JSONArray(b.focusSessions().map { JSONObject().put("id", it.id).put("startedAt", it.startedAt).put("minutes", it.minutes).putOpt("taskId", it.taskId).put("kind", it.kind) }))
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString().toByteArray()) }
                ?: error("Couldn't open the file")
            root.getJSONArray("tasks").length()
        }
    }

    /** Replaces everything in Noctil with the backup. Returns the number of tasks restored. */
    suspend fun restore(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("Couldn't open the file")
            val root = JSONObject(text)
            require(root.optString("app") == "Noctil") { "That file isn't a Noctil backup." }
            val app = context.applicationContext as NoctilApplication
            // Events Noctil added to a phone calendar would be orphaned, so take them down first.
            CalendarSync.stopExport(context)
            val db = app.repository.db
            val b = db.backup()
            fun JSONObject.longOrNull(k: String) = if (isNull(k) || !has(k)) null else getLong(k)
            fun JSONObject.intOrNull(k: String) = if (isNull(k) || !has(k)) null else getInt(k)
            fun JSONObject.stringOrNull(k: String) = if (isNull(k) || !has(k)) null else getString(k)
            fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
            fun arr(k: String) = root.optJSONArray(k) ?: JSONArray()

            db.withTransaction {
                b.clearTaskTags(); b.clearChecklist(); b.clearTasks(); b.clearTags(); b.clearLists(); b.clearFolders()
                b.clearClasses(); b.clearHabitChecks(); b.clearHabits(); b.clearFocusSessions(); b.clearCalendarLinks()
                b.insertFolders(arr("folders").objects().map { FolderEntity(it.getLong("id"), it.getString("name"), it.optInt("sortOrder"), it.optLong("updatedAt")) })
                b.insertLists(arr("lists").objects().map { ListEntity(it.getLong("id"), it.getString("name"), it.getInt("color"), it.longOrNull("folderId"), it.optInt("sortOrder"), it.optLong("updatedAt")) })
                b.insertTasks(arr("tasks").objects().map {
                    TaskEntity(
                        id = it.getLong("id"), title = it.getString("title"), notes = it.optString("notes"),
                        listId = it.longOrNull("listId"), parentId = it.longOrNull("parentId"), dueDate = it.longOrNull("dueDate"),
                        dueTime = it.intOrNull("dueTime"), priority = it.optInt("priority"), recurrence = it.stringOrNull("recurrence"),
                        reminder = it.intOrNull("reminder"), done = it.optBoolean("done"), completedAt = it.longOrNull("completedAt"),
                        sortOrder = it.optLong("sortOrder"), createdAt = it.optLong("createdAt"), updatedAt = System.currentTimeMillis(),
                        durationMinutes = it.intOrNull("durationMinutes"),
                    )
                })
                b.insertChecklist(arr("checklist").objects().map { ChecklistEntity(it.getLong("id"), it.getLong("taskId"), it.getString("text"), it.optBoolean("done"), it.optLong("sortOrder")) })
                b.insertTags(arr("tags").objects().map { TagEntity(it.getLong("id"), it.getString("name"), it.getInt("color")) })
                b.insertTaskTags(arr("taskTags").objects().map { TaskTagEntity(it.getLong("taskId"), it.getLong("tagId")) })
                b.insertClasses(arr("classes").objects().map { ClassEntity(it.getLong("id"), it.getString("name"), it.optString("room"), it.getInt("color"), it.getInt("days"), it.getInt("startMinute"), it.getInt("endMinute"), it.longOrNull("listId")) })
                b.insertHabits(arr("habits").objects().map { HabitEntity(it.getLong("id"), it.getString("name"), it.getInt("color"), it.getInt("days"), it.optInt("goal", 1), it.intOrNull("reminderMinute"), it.optInt("sortOrder"), it.optLong("createdAt"), it.optBoolean("archived")) })
                b.insertHabitChecks(arr("habitChecks").objects().map { HabitCheckEntity(it.getLong("habitId"), it.getLong("day"), it.getInt("count")) })
                b.insertFocusSessions(arr("focusSessions").objects().map { FocusSessionEntity(it.getLong("id"), it.getLong("startedAt"), it.getInt("minutes"), it.longOrNull("taskId"), it.optString("kind", "focus")) })
            }
            app.repository.rescheduleAllNow()
            arr("tasks").length()
        }
    }

    /** All tasks as a spreadsheet-friendly CSV file. */
    suspend fun exportCsv(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val snap = (context.applicationContext as NoctilApplication).repository.snapshot.value
            val rows = snap.tasks.sortedWith(compareBy({ it.done }, { it.date ?: LocalDate.MAX }, { it.time ?: LocalTime.MAX }))
            val csv = buildString {
                appendLine("Title,Notes,List,Tags,Due date,Due time,Priority,Repeat,Done,Subtask of")
                rows.forEach { t ->
                    val parent = t.parentId?.let { snap.byId[it]?.title }.orEmpty()
                    appendLine(
                        listOf(
                            t.title, t.notes, t.list?.name.orEmpty(), t.tags.joinToString(" ") { "#${it.name}" },
                            t.date?.toString().orEmpty(), t.time?.toString().orEmpty(), t.priority.label,
                            t.recurrence?.label().orEmpty(), if (t.done) "Yes" else "No", parent,
                        ).joinToString(",") { csvCell(it) },
                    )
                }
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(csv.toByteArray()) } ?: error("Couldn't open the file")
            rows.size
        }
    }

    internal fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
