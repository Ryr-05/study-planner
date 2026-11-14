package io.github.ryr05.noctil.cloud

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import io.github.ryr05.noctil.BuildConfig
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.data.db.ChecklistEntity
import io.github.ryr05.noctil.data.db.ClassEntity
import io.github.ryr05.noctil.data.db.FocusSessionEntity
import io.github.ryr05.noctil.data.db.FolderEntity
import io.github.ryr05.noctil.data.db.HabitCheckEntity
import io.github.ryr05.noctil.data.db.HabitEntity
import io.github.ryr05.noctil.data.db.ListEntity
import io.github.ryr05.noctil.data.db.PlaceEntity
import io.github.ryr05.noctil.data.db.StageEntity
import io.github.ryr05.noctil.data.db.SyncMapEntity
import io.github.ryr05.noctil.data.db.TagEntity
import io.github.ryr05.noctil.data.db.TaskEntity
import io.github.ryr05.noctil.data.db.TaskTagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class CloudUser(val uid: String, val name: String?, val email: String?)

data class CloudStatus(
    val available: Boolean = BuildConfig.CLOUD_SYNC,
    val user: CloudUser? = null,
    val syncing: Boolean = false,
    val lastSynced: Long? = null,
    val error: String? = null,
)

/**
 * Optional Google sign-in and live sync through Firestore.
 *
 * The phone's database stays the source of truth, so everything works offline. Sync copies
 * rows to users/{your id}/... in the cloud and pulls changes made on your other devices.
 * Each side keeps an updatedAt time; when both changed, the newer one wins. Deleting
 * something leaves a small "deleted" marker so other devices delete it too.
 */
object CloudSync {
    private val _status = MutableStateFlow(CloudStatus())
    val status: StateFlow<CloudStatus> = _status

    private val mutex = Mutex()
    private var pending: Job? = null
    private var listeners: List<ListenerRegistration> = emptyList()

    /** Latest documents per collection, kept fresh by live listeners (so a sync costs no extra reads). */
    private val latest = java.util.concurrent.ConcurrentHashMap<String, List<DocumentSnapshot>>()

    fun init(context: Context) {
        if (!BuildConfig.CLOUD_SYNC) return
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        _status.value = _status.value.copy(user = user?.let { CloudUser(it.uid, it.displayName, it.email) })
        if (user != null) startListening(context)
    }

    // ---------- Sign in and out ----------

    /** Shows Google's account picker, then signs in to Firebase. Needs an Activity context. */
    suspend fun signIn(activity: Context): Result<Unit> = runCatching {
        check(BuildConfig.CLOUD_SYNC) { "Cloud sync isn't set up in this build." }
        val webClientId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
            .takeIf { it != 0 }?.let(activity::getString) ?: error("Sign-in isn't configured.")
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()
        val result = CredentialManager.create(activity)
            .getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = result.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google didn't return an account."
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val user = FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
            ?: error("Sign-in didn't finish.")
        _status.value = _status.value.copy(user = CloudUser(user.uid, user.displayName, user.email), error = null)
        startListening(activity.applicationContext)
    }

    /** Signs out. Your tasks stay on this phone; they just stop syncing. */
    suspend fun signOut(context: Context) {
        listeners.forEach { it.remove() }
        listeners = emptyList()
        latest.clear()
        runCatching { FirebaseAuth.getInstance().signOut() }
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        val db = (context.applicationContext as NoctilApplication).repository.db
        withContext(Dispatchers.IO) { db.sync().clearMaps() }
        _status.value = _status.value.copy(user = null, lastSynced = null, error = null)
    }

    // ---------- Triggers ----------

    private fun startListening(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        listeners.forEach { it.remove() }
        val store = FirebaseFirestore.getInstance()
        listeners = TABLES.map { table ->
            store.collection("users").document(uid).collection(table).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _status.value = _status.value.copy(error = error.message)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    latest[table] = snapshot.documents
                    if (!snapshot.metadata.hasPendingWrites()) requestSync(context, 1_500)
                }
            }
        }
        requestSync(context, 500)
    }

    /** Runs a sync shortly, folding bursts of changes into one. */
    fun requestSync(context: Context, delayMs: Long = 3_000) {
        if (!BuildConfig.CLOUD_SYNC || _status.value.user == null) return
        val app = context.applicationContext as NoctilApplication
        pending?.cancel()
        pending = app.repository.scope.launch {
            delay(delayMs)
            syncNow(app)
        }
    }

    suspend fun syncNow(context: Context) = withContext(Dispatchers.IO) {
        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() ?: return@withContext
        mutex.withLock {
            _status.value = _status.value.copy(syncing = true)
            val outcome = runCatching { Engine(context.applicationContext as NoctilApplication, uid).run() }
            _status.value = _status.value.copy(
                syncing = false,
                lastSynced = if (outcome.isSuccess) System.currentTimeMillis() else _status.value.lastSynced,
                error = outcome.exceptionOrNull()?.message,
            )
        }
    }

    /** Cloud collections, in the order that keeps references valid (parents before children). */
    internal val TABLES = listOf(
        "folders", "lists", "tags", "stages", "places", "tasks", "checklist", "taskTags", "classes", "habits", "habitChecks", "focus",
    )

    /**
     * A stable fingerprint of a row's content, the same whether it was built from the local
     * database or read back from Firestore (which turns Ints into Longs).
     */
    internal fun fingerprint(fields: Map<String, Any?>): Int =
        fields.filterKeys { it != "updatedAt" && it != "deleted" }
            .toSortedMap()
            .entries
            .joinToString("|") { (k, v) ->
                val text = when (v) {
                    null -> "null"
                    is Number -> v.toLong().toString()
                    is Boolean -> v.toString()
                    else -> v.toString()
                }
                "$k=$text"
            }
            .hashCode()

    private class Engine(val app: NoctilApplication, uid: String) {
        private val db = app.repository.db
        private val dao = db.sync()
        private val root = FirebaseFirestore.getInstance().collection("users").document(uid)

        /** local key -> map row, and remote id -> local key, per table. */
        private val byLocal = mutableMapOf<String, MutableMap<String, SyncMapEntity>>()
        private val byRemote = mutableMapOf<String, MutableMap<String, String>>()

        suspend fun run() {
            for (t in TABLES) {
                val maps = dao.maps(t)
                byLocal[t] = maps.associateBy { it.localKey }.toMutableMap()
                byRemote[t] = maps.associate { it.remoteId to it.localKey }.toMutableMap()
            }
            pull()
            push()
            app.repository.rescheduleAllNow()
        }

        // ----- references between rows -----

        private fun remoteOf(table: String, localId: Long?): String? = localId?.let { byLocal[table]?.get(it.toString())?.remoteId }
        private fun localOf(table: String, remoteId: Any?): Long? = (remoteId as? String)?.let { byRemote[table]?.get(it)?.toLongOrNull() }

        private suspend fun remember(table: String, localKey: String, remoteId: String, hash: Int) {
            val m = SyncMapEntity(table, localKey, remoteId, hash)
            dao.putMap(m)
            byLocal.getOrPut(table) { mutableMapOf() }[localKey] = m
            byRemote.getOrPut(table) { mutableMapOf() }[remoteId] = localKey
        }

        private suspend fun forget(table: String, localKey: String) {
            val m = byLocal[table]?.remove(localKey) ?: return
            byRemote[table]?.remove(m.remoteId)
            dao.deleteMap(table, localKey)
        }

        // ----- push: send local rows that changed -----

        private data class Row(val key: String, val updatedAt: Long?, val fields: Map<String, Any?>, val fixedId: String? = null)

        private suspend fun localRows(table: String): List<Row> = when (table) {
            "folders" -> db.backup().folders().map { Row(it.id.toString(), it.updatedAt, mapOf("name" to it.name, "sortOrder" to it.sortOrder)) }
            "lists" -> db.backup().lists().map {
                Row(it.id.toString(), it.updatedAt, mapOf("name" to it.name, "color" to it.color, "folder" to remoteOf("folders", it.folderId), "sortOrder" to it.sortOrder))
            }
            "tags" -> db.backup().tags().map { Row(it.id.toString(), it.updatedAt, mapOf("name" to it.name, "color" to it.color)) }
            "stages" -> db.board().stages().map { Row(it.id.toString(), it.updatedAt, mapOf("name" to it.name, "sortOrder" to it.sortOrder)) }
            "places" -> db.board().places().map {
                Row(it.id.toString(), it.updatedAt, mapOf("name" to it.name, "latitude" to it.latitude.toString(), "longitude" to it.longitude.toString(), "radius" to it.radius))
            }
            "tasks" -> {
                val tasks = dao.liveTasks()
                // Parents first, so a subtask can point at its parent's cloud id.
                val ordered = tasks.sortedBy { depth(it, tasks.associateBy { t -> t.id }) }
                ordered.map { t ->
                    Row(
                        t.id.toString(), t.updatedAt,
                        mapOf(
                            "title" to t.title, "notes" to t.notes, "list" to remoteOf("lists", t.listId),
                            "parent" to remoteOf("tasks", t.parentId), "dueDate" to t.dueDate, "dueTime" to t.dueTime,
                            "priority" to t.priority, "recurrence" to t.recurrence, "reminder" to t.reminder, "done" to t.done,
                            "completedAt" to t.completedAt, "sortOrder" to t.sortOrder, "createdAt" to t.createdAt,
                            "durationMinutes" to t.durationMinutes, "stage" to remoteOf("stages", t.stageId),
                            "startDate" to t.startDate, "nag" to t.nag, "place" to remoteOf("places", t.placeId), "alarm" to t.alarm,
                            "doDay" to t.doDay, "doTime" to t.doTime,
                        ),
                    )
                }
            }
            "checklist" -> db.backup().checklist().mapNotNull {
                val task = remoteOf("tasks", it.taskId) ?: return@mapNotNull null
                Row(it.id.toString(), it.updatedAt, mapOf("task" to task, "text" to it.text, "done" to it.done, "sortOrder" to it.sortOrder))
            }
            "taskTags" -> db.backup().taskTags().mapNotNull {
                val task = remoteOf("tasks", it.taskId) ?: return@mapNotNull null
                val tag = remoteOf("tags", it.tagId) ?: return@mapNotNull null
                Row("${it.taskId}:${it.tagId}", null, mapOf("task" to task, "tag" to tag), fixedId = "${task}_$tag")
            }
            "classes" -> db.backup().classes().map {
                Row(
                    it.id.toString(), it.updatedAt,
                    mapOf(
                        "name" to it.name, "room" to it.room, "color" to it.color, "days" to it.days,
                        "startMinute" to it.startMinute, "endMinute" to it.endMinute, "list" to remoteOf("lists", it.listId),
                    ),
                )
            }
            "habits" -> db.backup().habits().map {
                Row(
                    it.id.toString(), it.updatedAt,
                    mapOf(
                        "name" to it.name, "color" to it.color, "days" to it.days, "goal" to it.goal,
                        "reminderMinute" to it.reminderMinute, "sortOrder" to it.sortOrder, "createdAt" to it.createdAt,
                        "archived" to it.archived,
                    ),
                )
            }
            "habitChecks" -> db.backup().habitChecks().mapNotNull {
                val habit = remoteOf("habits", it.habitId) ?: return@mapNotNull null
                Row("${it.habitId}:${it.day}", null, mapOf("habit" to habit, "day" to it.day, "count" to it.count), fixedId = "${habit}_${it.day}")
            }
            "focus" -> db.backup().focusSessions().map {
                Row(it.id.toString(), it.startedAt, mapOf("startedAt" to it.startedAt, "minutes" to it.minutes, "task" to remoteOf("tasks", it.taskId), "kind" to it.kind))
            }
            else -> emptyList()
        }

        private fun depth(task: TaskEntity, all: Map<Long, TaskEntity>): Int {
            var d = 0
            var p = task.parentId
            while (p != null && d < 20) {
                d++
                p = all[p]?.parentId
            }
            return d
        }

        private suspend fun push() {
            val now = System.currentTimeMillis()
            for (table in TABLES) {
                val col = root.collection(table)
                val rows = localRows(table)
                val keys = rows.map { it.key }.toSet()
                for (row in rows) {
                    val hash = fingerprint(row.fields)
                    val existing = byLocal[table]?.get(row.key)
                    if (existing != null && existing.hash == hash) continue
                    val remoteId = existing?.remoteId ?: row.fixedId ?: col.document().id
                    val data = row.fields + mapOf("updatedAt" to maxOf(row.updatedAt ?: now, 1L), "deleted" to false)
                    // Not awaited: Firestore queues writes while offline and sends them later.
                    col.document(remoteId).set(data, SetOptions.merge())
                    remember(table, row.key, remoteId, hash)
                }
                // Rows deleted here since the last sync: tell other devices.
                byLocal[table]?.values?.filter { it.localKey !in keys }?.toList()?.forEach { gone ->
                    col.document(gone.remoteId).set(mapOf("deleted" to true, "updatedAt" to now), SetOptions.merge())
                    forget(table, gone.localKey)
                }
            }
        }

        // ----- pull: apply changes made on other devices -----

        private suspend fun pull() {
            for (table in TABLES) {
                var remaining = latest[table].orEmpty().filter { !it.metadata.hasPendingWrites() }
                // A few passes, so rows whose parent arrives later in the same batch still link up.
                repeat(3) {
                    if (remaining.isEmpty()) return@repeat
                    val deferred = remaining.filter { !applyDoc(table, it, force = false) }
                    val progressed = deferred.size < remaining.size
                    remaining = deferred
                    if (!progressed) return@repeat
                }
                // Anything still waiting on a missing parent is applied without that link.
                remaining.forEach { applyDoc(table, it, force = true) }
            }
        }

        /** Returns false to retry later when the row points at something not synced yet. */
        private suspend fun applyDoc(table: String, doc: DocumentSnapshot, force: Boolean): Boolean {
            val data = doc.data ?: return true
            val remoteId = doc.id
            val localKey = byRemote[table]?.get(remoteId)
            if (data["deleted"] == true) {
                if (localKey != null) {
                    deleteLocal(table, localKey)
                    forget(table, localKey)
                }
                return true
            }
            val hash = fingerprint(data)
            if (localKey != null && byLocal[table]?.get(localKey)?.hash == hash) return true
            val remoteUpdated = (data["updatedAt"] as? Number)?.toLong() ?: 0L
            if (localKey != null) {
                val localUpdated = localUpdatedAt(table, localKey)
                if (localUpdated != null && localUpdated > remoteUpdated) return true // ours is newer; push sends it
            }
            val newKey = upsertLocal(table, data, localKey, remoteUpdated, force) ?: return false
            remember(table, newKey, remoteId, hash)
            return true
        }

        private suspend fun localUpdatedAt(table: String, key: String): Long? {
            val id = key.toLongOrNull() ?: return null
            return when (table) {
                "folders" -> dao.folder(id)?.updatedAt
                "lists" -> dao.list(id)?.updatedAt
                "tags" -> dao.tag(id)?.updatedAt
                "stages" -> db.board().stage(id)?.updatedAt
                "places" -> db.board().place(id)?.updatedAt
                "tasks" -> dao.task(id)?.updatedAt
                "checklist" -> dao.checklistItem(id)?.updatedAt
                "classes" -> dao.schoolClass(id)?.updatedAt
                "habits" -> dao.habit(id)?.updatedAt
                else -> null
            }
        }

        private suspend fun deleteLocal(table: String, key: String) {
            val parts = key.split(":")
            val id = parts[0].toLongOrNull() ?: return
            when (table) {
                "folders" -> dao.deleteFolder(id)
                "lists" -> dao.deleteList(id)
                "tags" -> dao.deleteTag(id)
                "stages" -> {
                    db.board().clearStage(id, System.currentTimeMillis())
                    db.board().deleteStage(id)
                }
                "places" -> {
                    db.board().clearPlace(id, System.currentTimeMillis())
                    db.board().deletePlace(id)
                }
                "tasks" -> {
                    dao.softDeleteTask(id)
                    app.scheduler.cancel(id)
                }
                "checklist" -> dao.deleteChecklist(id)
                "taskTags" -> parts.getOrNull(1)?.toLongOrNull()?.let { dao.deleteTaskTag(id, it) }
                "classes" -> dao.deleteClass(id)
                "habits" -> dao.deleteHabit(id)
                "habitChecks" -> parts.getOrNull(1)?.toLongOrNull()?.let { dao.deleteHabitCheck(id, it) }
                "focus" -> dao.deleteFocus(id)
            }
        }

        private fun Map<String, Any?>.long(k: String): Long? = (this[k] as? Number)?.toLong()
        private fun Map<String, Any?>.int(k: String): Int? = (this[k] as? Number)?.toInt()
        private fun Map<String, Any?>.str(k: String): String? = this[k] as? String
        private fun Map<String, Any?>.bool(k: String): Boolean = this[k] as? Boolean ?: false

        /** Resolves a reference. Returns null if missing and not yet forcing, which defers the row. */
        private fun ref(table: String, value: Any?, force: Boolean, ok: (Long?) -> Unit): Boolean {
            if (value == null) { ok(null); return true }
            val local = localOf(table, value)
            if (local == null && !force) return false
            ok(local)
            return true
        }

        private suspend fun upsertLocal(table: String, d: Map<String, Any?>, key: String?, updated: Long, force: Boolean): String? {
            val id = key?.toLongOrNull()
            when (table) {
                "folders" -> {
                    val e = FolderEntity(id ?: 0, d.str("name") ?: "Folder", d.int("sortOrder") ?: 0, updated)
                    return if (id != null && dao.folder(id) != null) { dao.updateFolder(e); key } else dao.insertFolder(e.copy(id = 0)).toString()
                }
                "lists" -> {
                    var folder: Long? = null
                    if (!ref("folders", d["folder"], force) { folder = it }) return null
                    val e = ListEntity(id ?: 0, d.str("name") ?: "List", d.int("color") ?: 0xFFB9A3FF.toInt(), folder, d.int("sortOrder") ?: 0, updated)
                    return if (id != null && dao.list(id) != null) { dao.updateList(e); key } else dao.insertList(e.copy(id = 0)).toString()
                }
                "tags" -> {
                    val name = d.str("name") ?: return key
                    val e = TagEntity(id ?: 0, name, d.int("color") ?: 0xFFB9A3FF.toInt(), updated)
                    if (id != null && dao.tag(id) != null) { dao.updateTag(e); return key }
                    val inserted = dao.insertTag(e.copy(id = 0))
                    return (if (inserted > 0) inserted else dao.tagByName(name)?.id)?.toString()
                }
                "stages" -> {
                    val e = StageEntity(id ?: 0, d.str("name") ?: "Column", d.int("sortOrder") ?: 0, updated)
                    val b = db.board()
                    return if (id != null && b.stage(id) != null) { b.updateStage(e); key } else b.insertStage(e.copy(id = 0)).toString()
                }
                "places" -> {
                    val lat = d.str("latitude")?.toDoubleOrNull() ?: return key
                    val lng = d.str("longitude")?.toDoubleOrNull() ?: return key
                    val e = PlaceEntity(id ?: 0, d.str("name") ?: "Place", lat, lng, d.int("radius") ?: 150, updated)
                    val b = db.board()
                    return if (id != null && b.place(id) != null) { b.updatePlace(e); key } else b.insertPlace(e.copy(id = 0)).toString()
                }
                "tasks" -> {
                    var list: Long? = null
                    var parent: Long? = null
                    var stage: Long? = null
                    var place: Long? = null
                    if (!ref("lists", d["list"], force) { list = it }) return null
                    if (!ref("tasks", d["parent"], force) { parent = it }) return null
                    if (!ref("stages", d["stage"], force) { stage = it }) return null
                    if (!ref("places", d["place"], force) { place = it }) return null
                    val e = TaskEntity(
                        id = id ?: 0, title = d.str("title") ?: "", notes = d.str("notes") ?: "", listId = list, parentId = parent,
                        dueDate = d.long("dueDate"), dueTime = d.int("dueTime"), priority = d.int("priority") ?: 0,
                        recurrence = d.str("recurrence"), reminder = d.int("reminder"), done = d.bool("done"),
                        completedAt = d.long("completedAt"), sortOrder = d.long("sortOrder") ?: 0, createdAt = d.long("createdAt") ?: updated,
                        updatedAt = updated, deleted = false, durationMinutes = d.int("durationMinutes"),
                        stageId = stage, startDate = d.long("startDate"), nag = d.bool("nag"), placeId = place, alarm = d.bool("alarm"),
                        doDay = d.long("doDay"), doTime = d.int("doTime"),
                    )
                    return if (id != null && dao.task(id) != null) { dao.updateTask(e); key } else dao.insertTask(e.copy(id = 0)).toString()
                }
                "checklist" -> {
                    var task: Long? = null
                    if (!ref("tasks", d["task"], force) { task = it }) return null
                    val t = task ?: return key
                    val e = ChecklistEntity(id ?: 0, t, d.str("text") ?: "", d.bool("done"), d.long("sortOrder") ?: 0, updated)
                    return if (id != null && dao.checklistItem(id) != null) { dao.updateChecklist(e); key } else dao.insertChecklist(e.copy(id = 0)).toString()
                }
                "taskTags" -> {
                    var task: Long? = null
                    var tag: Long? = null
                    if (!ref("tasks", d["task"], force) { task = it }) return null
                    if (!ref("tags", d["tag"], force) { tag = it }) return null
                    val t = task ?: return key
                    val g = tag ?: return key
                    dao.insertTaskTag(TaskTagEntity(t, g))
                    return "$t:$g"
                }
                "classes" -> {
                    var list: Long? = null
                    if (!ref("lists", d["list"], force) { list = it }) return null
                    val e = ClassEntity(
                        id ?: 0, d.str("name") ?: "Class", d.str("room") ?: "", d.int("color") ?: 0xFF8DB8FF.toInt(),
                        d.int("days") ?: 0, d.int("startMinute") ?: 0, d.int("endMinute") ?: 60, list, updated,
                    )
                    return if (id != null && dao.schoolClass(id) != null) { dao.updateClass(e); key } else dao.insertClass(e.copy(id = 0)).toString()
                }
                "habits" -> {
                    val e = HabitEntity(
                        id ?: 0, d.str("name") ?: "Habit", d.int("color") ?: 0xFF7FD6B5.toInt(), d.int("days") ?: 127,
                        d.int("goal") ?: 1, d.int("reminderMinute"), d.int("sortOrder") ?: 0, d.long("createdAt") ?: updated,
                        d.bool("archived"), updated,
                    )
                    return if (id != null && dao.habit(id) != null) { dao.updateHabit(e); key } else dao.insertHabit(e.copy(id = 0)).toString()
                }
                "habitChecks" -> {
                    var habit: Long? = null
                    if (!ref("habits", d["habit"], force) { habit = it }) return null
                    val h = habit ?: return key
                    val day = d.long("day") ?: return key
                    dao.putHabitCheck(HabitCheckEntity(h, day, d.int("count") ?: 1))
                    return "$h:$day"
                }
                "focus" -> {
                    var task: Long? = null
                    if (!ref("tasks", d["task"], force) { task = it }) return null
                    val e = FocusSessionEntity(id ?: 0, d.long("startedAt") ?: updated, d.int("minutes") ?: 0, task, d.str("kind") ?: "focus")
                    return if (id != null && dao.focus(id) != null) { dao.updateFocus(e); key } else dao.insertFocus(e.copy(id = 0)).toString()
                }
            }
            return key
        }
    }
}
