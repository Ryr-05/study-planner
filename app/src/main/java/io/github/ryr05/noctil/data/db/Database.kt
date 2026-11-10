package io.github.ryr05.noctil.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/*
 * The on-device database. Everything is stored here first, so the app works fully offline.
 * updatedAt and the soft-delete flag on tasks are there so cloud sync (Phase 5) can merge changes.
 */

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "lists", indices = [Index("folderId")])
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val folderId: Long? = null,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tasks", indices = [Index("listId"), Index("parentId"), Index("dueDate")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val listId: Long? = null,
    val parentId: Long? = null,
    /** Days since 1970-01-01, or null for no date. */
    val dueDate: Long? = null,
    /** Minutes after midnight, or null for no time. */
    val dueTime: Int? = null,
    val priority: Int = 0,
    val recurrence: String? = null,
    /** Minutes before the due time to remind, or null for no reminder. */
    val reminder: Int? = null,
    val done: Boolean = false,
    val completedAt: Long? = null,
    val sortOrder: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    /** How long the task blocks out on the calendar, in minutes. Null means the default (30). */
    val durationMinutes: Int? = null,
)

@Entity(tableName = "checklist", indices = [Index("taskId")])
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val text: String,
    val done: Boolean = false,
    val sortOrder: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A weekly class in the timetable. Entered once, shown every week on the calendar. */
@Entity(tableName = "classes")
data class ClassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val room: String = "",
    val color: Int,
    /** Which weekdays it meets. Bit 0 is Monday, bit 6 is Sunday. */
    val days: Int,
    val startMinute: Int,
    val endMinute: Int,
    val listId: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Links a task to the event it was copied to in the phone's calendar, with what the event looked
 * like at the last sync. That lets sync tell which side changed. eventId is -1 when the event was
 * deleted in the phone's calendar, so the task isn't added back.
 */
@Entity(tableName = "calendar_links")
data class CalendarLinkEntity(
    @PrimaryKey val taskId: Long,
    val calendarId: Long,
    val eventId: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val syncedAt: Long,
)

/** A finished focus session or stopwatch run, for statistics. */
@Entity(tableName = "focus_sessions", indices = [Index("startedAt")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val minutes: Int,
    val taskId: Long? = null,
    val kind: String = "focus",
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    /** Which weekdays it's due. Bit 0 is Monday, bit 6 is Sunday. 127 is every day. */
    val days: Int = 127,
    /** How many check-ins make a day count, like 8 glasses of water. */
    val goal: Int = 1,
    /** Minutes after midnight for a daily reminder, or null for none. */
    val reminderMinute: Int? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** How many times a habit was checked on a day (days since 1970-01-01). */
@Entity(tableName = "habit_checks", primaryKeys = ["habitId", "day"])
data class HabitCheckEntity(val habitId: Long, val day: Long, val count: Int)

/**
 * Pairs a local row with its copy in the cloud, plus a fingerprint of what was last synced,
 * so sync only sends rows that really changed. localKey is the row id, or "a:b" for two-part keys.
 */
@Entity(
    tableName = "sync_map",
    primaryKeys = ["tableName", "localKey"],
    indices = [Index(value = ["tableName", "remoteId"])],
)
data class SyncMapEntity(val tableName: String, val localKey: String, val remoteId: String, val hash: Int)

@Entity(tableName = "task_tags", primaryKeys = ["taskId", "tagId"], indices = [Index("tagId")])
data class TaskTagEntity(val taskId: Long, val tagId: Long)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deleted = 0")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE deleted = 0 AND done = 0 AND reminder IS NOT NULL AND dueDate IS NOT NULL")
    suspend fun withReminders(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deleted = 0 AND done = 0 AND dueDate IS NOT NULL")
    suspend fun withDates(): List<TaskEntity>

    @Query("SELECT id FROM tasks WHERE parentId = :parentId")
    suspend fun childIds(parentId: Long): List<Long>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM tasks")
    suspend fun maxSortOrder(): Long

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET deleted = :deleted, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setDeleted(ids: List<Long>, deleted: Boolean, now: Long)

    @Query("UPDATE tasks SET listId = NULL, updatedAt = :now WHERE listId = :listId")
    suspend fun moveListToInbox(listId: Long, now: Long)
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklist WHERE taskId = :taskId ORDER BY sortOrder, id")
    suspend fun forTask(taskId: Long): List<ChecklistEntity>

    @Query("SELECT * FROM checklist WHERE id = :id")
    suspend fun get(id: Long): ChecklistEntity?

    @Insert
    suspend fun insert(item: ChecklistEntity): Long

    @Update
    suspend fun update(item: ChecklistEntity)

    @Query("DELETE FROM checklist WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ListDao {
    @Query("SELECT * FROM lists ORDER BY sortOrder, id")
    fun observeLists(): Flow<List<ListEntity>>

    @Query("SELECT * FROM folders ORDER BY sortOrder, id")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM lists WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findListByName(name: String): ListEntity?

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun getList(id: Long): ListEntity?

    @Query("SELECT COUNT(*) FROM lists")
    suspend fun listCount(): Int

    @Insert
    suspend fun insertList(list: ListEntity): Long

    @Update
    suspend fun updateList(list: ListEntity)

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("UPDATE lists SET folderId = NULL WHERE folderId = :folderId")
    suspend fun ungroup(folderId: Long)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM task_tags")
    fun observeLinks(): Flow<List<TaskTagEntity>>

    @Query("SELECT * FROM tags WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun count(): Int

    @Query("SELECT tagId FROM task_tags WHERE taskId = :taskId")
    suspend fun tagIdsFor(taskId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM task_tags WHERE tagId = :tagId")
    suspend fun unlinkAll(tagId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(link: TaskTagEntity)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun unlink(taskId: Long, tagId: Long)
}

@Dao
interface ClassDao {
    @Query("SELECT * FROM classes ORDER BY startMinute, name")
    fun observeAll(): Flow<List<ClassEntity>>

    @Insert
    suspend fun insert(item: ClassEntity): Long

    @Update
    suspend fun update(item: ClassEntity)

    @Query("DELETE FROM classes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CalendarLinkDao {
    @Query("SELECT * FROM calendar_links")
    suspend fun all(): List<CalendarLinkEntity>

    @Query("SELECT eventId FROM calendar_links WHERE eventId > 0")
    fun observeEventIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: CalendarLinkEntity)

    @Query("DELETE FROM calendar_links WHERE taskId = :taskId")
    suspend fun delete(taskId: Long)

    @Query("DELETE FROM calendar_links")
    suspend fun clear()
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt")
    fun observeAll(): Flow<List<FocusSessionEntity>>

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_checks")
    fun observeChecks(): Flow<List<HabitCheckEntity>>

    @Query("SELECT * FROM habits WHERE archived = 0")
    suspend fun allHabits(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun get(id: Long): HabitEntity?

    @Query("SELECT COUNT(*) FROM habits")
    suspend fun count(): Int

    @Query("SELECT * FROM habit_checks WHERE habitId = :habitId AND day = :day")
    suspend fun check(habitId: Long, day: Long): HabitCheckEntity?

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM habit_checks WHERE habitId = :habitId")
    suspend fun deleteChecks(habitId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheck(check: HabitCheckEntity)

    @Query("DELETE FROM habit_checks WHERE habitId = :habitId AND day = :day")
    suspend fun deleteCheck(habitId: Long, day: Long)
}

/** Whole-database reads and writes for backup and restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM folders") suspend fun folders(): List<FolderEntity>
    @Query("SELECT * FROM lists") suspend fun lists(): List<ListEntity>
    @Query("SELECT * FROM tasks WHERE deleted = 0") suspend fun tasks(): List<TaskEntity>
    @Query("SELECT * FROM checklist") suspend fun checklist(): List<ChecklistEntity>
    @Query("SELECT * FROM tags") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM task_tags") suspend fun taskTags(): List<TaskTagEntity>
    @Query("SELECT * FROM classes") suspend fun classes(): List<ClassEntity>
    @Query("SELECT * FROM habits") suspend fun habits(): List<HabitEntity>
    @Query("SELECT * FROM habit_checks") suspend fun habitChecks(): List<HabitCheckEntity>
    @Query("SELECT * FROM focus_sessions") suspend fun focusSessions(): List<FocusSessionEntity>

    @Insert suspend fun insertFolders(items: List<FolderEntity>)
    @Insert suspend fun insertLists(items: List<ListEntity>)
    @Insert suspend fun insertTasks(items: List<TaskEntity>)
    @Insert suspend fun insertChecklist(items: List<ChecklistEntity>)
    @Insert suspend fun insertTags(items: List<TagEntity>)
    @Insert suspend fun insertTaskTags(items: List<TaskTagEntity>)
    @Insert suspend fun insertClasses(items: List<ClassEntity>)
    @Insert suspend fun insertHabits(items: List<HabitEntity>)
    @Insert suspend fun insertHabitChecks(items: List<HabitCheckEntity>)
    @Insert suspend fun insertFocusSessions(items: List<FocusSessionEntity>)

    @Query("DELETE FROM folders") suspend fun clearFolders()
    @Query("DELETE FROM lists") suspend fun clearLists()
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM checklist") suspend fun clearChecklist()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM task_tags") suspend fun clearTaskTags()
    @Query("DELETE FROM classes") suspend fun clearClasses()
    @Query("DELETE FROM habits") suspend fun clearHabits()
    @Query("DELETE FROM habit_checks") suspend fun clearHabitChecks()
    @Query("DELETE FROM focus_sessions") suspend fun clearFocusSessions()
    @Query("DELETE FROM calendar_links") suspend fun clearCalendarLinks()
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_map WHERE tableName = :table")
    suspend fun maps(table: String): List<SyncMapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMap(map: SyncMapEntity)

    @Query("DELETE FROM sync_map WHERE tableName = :table AND localKey = :localKey")
    suspend fun deleteMap(table: String, localKey: String)

    @Query("DELETE FROM sync_map")
    suspend fun clearMaps()

    @Query("SELECT * FROM tasks WHERE deleted = 0") suspend fun liveTasks(): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: Long): TaskEntity?
    @Query("SELECT * FROM lists WHERE id = :id") suspend fun list(id: Long): ListEntity?
    @Query("SELECT * FROM folders WHERE id = :id") suspend fun folder(id: Long): FolderEntity?
    @Query("SELECT * FROM tags WHERE id = :id") suspend fun tag(id: Long): TagEntity?
    @Query("SELECT * FROM checklist WHERE id = :id") suspend fun checklistItem(id: Long): ChecklistEntity?
    @Query("SELECT * FROM classes WHERE id = :id") suspend fun schoolClass(id: Long): ClassEntity?
    @Query("SELECT * FROM habits WHERE id = :id") suspend fun habit(id: Long): HabitEntity?
    @Query("SELECT * FROM focus_sessions WHERE id = :id") suspend fun focus(id: Long): FocusSessionEntity?

    @Insert suspend fun insertFolder(e: FolderEntity): Long
    @Update suspend fun updateFolder(e: FolderEntity)
    @Query("DELETE FROM folders WHERE id = :id") suspend fun deleteFolder(id: Long)

    @Insert suspend fun insertList(e: ListEntity): Long
    @Update suspend fun updateList(e: ListEntity)
    @Query("DELETE FROM lists WHERE id = :id") suspend fun deleteList(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTag(e: TagEntity): Long
    @Update suspend fun updateTag(e: TagEntity)
    @Query("DELETE FROM tags WHERE id = :id") suspend fun deleteTag(id: Long)
    @Query("SELECT * FROM tags WHERE LOWER(name) = LOWER(:name) LIMIT 1") suspend fun tagByName(name: String): TagEntity?

    @Insert suspend fun insertTask(e: TaskEntity): Long
    @Update suspend fun updateTask(e: TaskEntity)
    @Query("UPDATE tasks SET deleted = 1 WHERE id = :id") suspend fun softDeleteTask(id: Long)

    @Insert suspend fun insertChecklist(e: ChecklistEntity): Long
    @Update suspend fun updateChecklist(e: ChecklistEntity)
    @Query("DELETE FROM checklist WHERE id = :id") suspend fun deleteChecklist(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTaskTag(e: TaskTagEntity)
    @Query("DELETE FROM task_tags WHERE taskId = :taskId AND tagId = :tagId") suspend fun deleteTaskTag(taskId: Long, tagId: Long)

    @Insert suspend fun insertClass(e: ClassEntity): Long
    @Update suspend fun updateClass(e: ClassEntity)
    @Query("DELETE FROM classes WHERE id = :id") suspend fun deleteClass(id: Long)

    @Insert suspend fun insertHabit(e: HabitEntity): Long
    @Update suspend fun updateHabit(e: HabitEntity)
    @Query("DELETE FROM habits WHERE id = :id") suspend fun deleteHabit(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHabitCheck(e: HabitCheckEntity)
    @Query("DELETE FROM habit_checks WHERE habitId = :habitId AND day = :day") suspend fun deleteHabitCheck(habitId: Long, day: Long)

    @Insert suspend fun insertFocus(e: FocusSessionEntity): Long
    @Update suspend fun updateFocus(e: FocusSessionEntity)
    @Query("DELETE FROM focus_sessions WHERE id = :id") suspend fun deleteFocus(id: Long)
}

@Database(
    entities = [
        FolderEntity::class, ListEntity::class, TaskEntity::class,
        ChecklistEntity::class, TagEntity::class, TaskTagEntity::class,
        ClassEntity::class, CalendarLinkEntity::class,
        FocusSessionEntity::class, HabitEntity::class, HabitCheckEntity::class,
        SyncMapEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class NoctilDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun checklist(): ChecklistDao
    abstract fun lists(): ListDao
    abstract fun tags(): TagDao
    abstract fun classes(): ClassDao
    abstract fun calendarLinks(): CalendarLinkDao
    abstract fun focus(): FocusDao
    abstract fun habits(): HabitDao
    abstract fun backup(): BackupDao
    abstract fun sync(): SyncDao

    companion object {
        fun build(context: Context): NoctilDatabase =
            Room.databaseBuilder(context, NoctilDatabase::class.java, "noctil.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}

/**
 * Version 1 to 2 (Phase 3): task durations, the class timetable and phone-calendar links.
 * Existing tasks are kept. The SQL matches what Room generates for the entities above
 * (CI prints Room's own CREATE statements so they can be compared).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `durationMinutes` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `classes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `room` TEXT NOT NULL, `color` INTEGER NOT NULL, `days` INTEGER NOT NULL, " +
                "`startMinute` INTEGER NOT NULL, `endMinute` INTEGER NOT NULL, `listId` INTEGER, " +
                "`updatedAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `calendar_links` (`taskId` INTEGER NOT NULL, `calendarId` INTEGER NOT NULL, " +
                "`eventId` INTEGER NOT NULL, `title` TEXT NOT NULL, `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, " +
                "`allDay` INTEGER NOT NULL, `syncedAt` INTEGER NOT NULL, PRIMARY KEY(`taskId`))",
        )
    }
}

/** Version 2 to 3 (Phases 4 and 5): focus sessions, habits and habit check-ins. Existing data is kept. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `focus_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, `minutes` INTEGER NOT NULL, `taskId` INTEGER, `kind` TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_startedAt` ON `focus_sessions` (`startedAt`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`color` INTEGER NOT NULL, `days` INTEGER NOT NULL, `goal` INTEGER NOT NULL, `reminderMinute` INTEGER, " +
                "`sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `archived` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habit_checks` (`habitId` INTEGER NOT NULL, `day` INTEGER NOT NULL, " +
                "`count` INTEGER NOT NULL, PRIMARY KEY(`habitId`, `day`))",
        )
    }
}

/** Version 3 to 4: the table that pairs local rows with their cloud copies. Existing data is kept. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_map` (`tableName` TEXT NOT NULL, `localKey` TEXT NOT NULL, " +
                "`remoteId` TEXT NOT NULL, `hash` INTEGER NOT NULL, PRIMARY KEY(`tableName`, `localKey`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_map_tableName_remoteId` ON `sync_map` (`tableName`, `remoteId`)")
    }
}
