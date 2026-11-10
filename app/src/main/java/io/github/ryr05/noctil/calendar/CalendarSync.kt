package io.github.ryr05.noctil.calendar

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.data.db.CalendarLinkEntity
import io.github.ryr05.noctil.data.db.TaskEntity
import io.github.ryr05.noctil.data.localDate
import io.github.ryr05.noctil.data.localTime
import io.github.ryr05.noctil.data.model.DEFAULT_TASK_MINUTES
import io.github.ryr05.noctil.data.toMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Which phone calendars to show, and which one (if any) to copy tasks into. Stored on the phone. */
object CalendarPrefs {
    private const val FILE = "noctil"
    private const val SHOWN = "calendars_shown"
    private const val EXPORT = "calendar_export_id"

    private val _version = MutableStateFlow(0)

    /** Bumps whenever these settings change, so screens can reload events. */
    val version: StateFlow<Int> = _version

    /** Null means "show every calendar" (the default once access is allowed). */
    fun shownIds(context: Context): Set<Long>? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet(SHOWN, null)
            ?.mapNotNull { it.toLongOrNull() }?.toSet()

    fun setShownIds(context: Context, ids: Set<Long>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putStringSet(SHOWN, ids.map { it.toString() }.toSet()).apply()
        _version.value++
    }

    fun exportCalendarId(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(EXPORT, -1L)

    fun setExportCalendarId(context: Context, id: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong(EXPORT, id).apply()
        _version.value++
    }

    fun bump() {
        _version.value++
    }
}

/**
 * Two-way sync between dated tasks and one phone calendar of your choice.
 *
 * - Every open task with a date gets a matching event (all-day if it has no time).
 * - Change the task here and the event follows. Move or rename the event there and the task follows.
 * - Finishing or deleting a task removes its event. Deleting the event there just stops copying
 *   that task, it doesn't delete the task.
 *
 * Each link remembers how the event looked at the last sync, so sync can tell which side changed.
 * If both changed, the task wins.
 */
object CalendarSync {
    private val mutex = Mutex()
    private var observerJob: Job? = null
    private var watching = false

    suspend fun sync(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock { runCatching { syncLocked(context) } }
    }

    /** Stop copying tasks: removes the events Noctil created. */
    suspend fun stopExport(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val db = (context.applicationContext as NoctilApplication).repository.db
            db.calendarLinks().all().forEach { DeviceCalendars.deleteEvent(context, it.eventId) }
            db.calendarLinks().clear()
            CalendarPrefs.setExportCalendarId(context, -1L)
        }
    }

    /**
     * Re-sync when anything changes in the phone's calendars, for example after Google syncs.
     * Safe to call again (it only starts watching once), such as right after access is granted.
     */
    fun watch(context: Context) {
        if (watching || !DeviceCalendars.canRead(context)) return
        val app = context.applicationContext as NoctilApplication
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                observerJob?.cancel()
                observerJob = app.repository.scope.launch {
                    delay(1_500)
                    CalendarPrefs.bump()
                    sync(context)
                }
            }
        }
        runCatching {
            context.applicationContext.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
            watching = true
        }
    }

    private suspend fun syncLocked(context: Context) {
        val calendarId = CalendarPrefs.exportCalendarId(context)
        if (calendarId < 0 || !DeviceCalendars.canWrite(context) || !DeviceCalendars.canRead(context)) return
        val repo = (context.applicationContext as NoctilApplication).repository
        val links = repo.db.calendarLinks()
        val tasks = repo.db.tasks().withDates().associateBy { it.id }
        val existing = links.all().associateBy { it.taskId }

        for (link in existing.values) {
            val task = tasks[link.taskId]
            if (link.eventId <= 0) {
                // Removed in the phone's calendar earlier. Forget it once the task is finished or gone.
                if (task == null) links.delete(link.taskId)
                continue
            }
            if (link.calendarId != calendarId) {
                DeviceCalendars.deleteEvent(context, link.eventId)
                links.delete(link.taskId)
                continue
            }
            val event = DeviceCalendars.readEvent(context, link.eventId)
            if (task == null) {
                if (event != null) DeviceCalendars.deleteEvent(context, link.eventId)
                links.delete(link.taskId)
                continue
            }
            if (event == null) {
                links.upsert(link.copy(eventId = -1, syncedAt = System.currentTimeMillis()))
                continue
            }
            val eventChanged = event.title != link.title || event.start != link.start ||
                event.end != link.end || event.allDay != link.allDay
            val taskChanged = task.updatedAt > link.syncedAt
            when {
                taskChanged -> {
                    val fields = fieldsFor(task)
                    if (fields.title != event.title || fields.start != event.start || fields.end != event.end ||
                        fields.allDay != event.allDay || fields.description != event.description
                    ) {
                        DeviceCalendars.updateEvent(context, link.eventId, fields)
                    }
                    links.upsert(linkFor(task.id, calendarId, link.eventId, fields))
                }
                eventChanged -> {
                    repo.updateNow(task.id) { applyEvent(it, event) }
                    links.upsert(linkFor(task.id, calendarId, link.eventId, event))
                }
            }
        }

        for (task in tasks.values) {
            if (task.id in existing) continue
            val fields = fieldsFor(task)
            val eventId = DeviceCalendars.insertEvent(context, calendarId, fields) ?: continue
            links.upsert(linkFor(task.id, calendarId, eventId, fields))
        }
    }

    private fun linkFor(taskId: Long, calendarId: Long, eventId: Long, f: EventFields) = CalendarLinkEntity(
        taskId = taskId, calendarId = calendarId, eventId = eventId,
        title = f.title, start = f.start, end = f.end, allDay = f.allDay,
        syncedAt = System.currentTimeMillis(),
    )

    internal fun fieldsFor(task: TaskEntity): EventFields {
        val date = task.localDate()!!
        val time = task.localTime()
        val description = listOf(task.notes.trim(), "Added by Noctil").filter { it.isNotEmpty() }.joinToString("\n\n")
        return if (time != null) {
            val start = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val minutes = task.durationMinutes ?: DEFAULT_TASK_MINUTES
            EventFields(task.title, start, start + minutes * 60_000L, allDay = false, description = description)
        } else {
            val start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            EventFields(task.title, start, start + 86_400_000L, allDay = true, description = description)
        }
    }

    internal fun applyEvent(task: TaskEntity, event: EventFields): TaskEntity {
        val title = event.title.ifBlank { task.title }
        return if (event.allDay) {
            val date = Instant.ofEpochMilli(event.start).atZone(ZoneOffset.UTC).toLocalDate()
            task.copy(title = title, dueDate = date.toEpochDay(), dueTime = null)
        } else {
            val start = Instant.ofEpochMilli(event.start).atZone(ZoneId.systemDefault()).toLocalDateTime()
            val minutes = ((event.end - event.start) / 60_000L).toInt().coerceIn(5, 24 * 60)
            task.copy(
                title = title,
                dueDate = start.toLocalDate().toEpochDay(),
                dueTime = start.toLocalTime().toMinutes(),
                durationMinutes = if (minutes == DEFAULT_TASK_MINUTES && task.durationMinutes == null) null else minutes,
            )
        }
    }
}
