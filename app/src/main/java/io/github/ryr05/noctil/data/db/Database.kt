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

@Database(
    entities = [
        FolderEntity::class, ListEntity::class, TaskEntity::class,
        ChecklistEntity::class, TagEntity::class, TaskTagEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class NoctilDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun checklist(): ChecklistDao
    abstract fun lists(): ListDao
    abstract fun tags(): TagDao

    companion object {
        fun build(context: Context): NoctilDatabase =
            Room.databaseBuilder(context, NoctilDatabase::class.java, "noctil.db").build()
    }
}
