package io.github.ryr05.noctil.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** A calendar on the phone: Google, Samsung, Outlook, or iCloud through DAVx5. */
data class PhoneCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val color: Color,
    val writable: Boolean,
)

/** One occurrence of an event from a phone calendar. Times are epoch milliseconds. */
data class PhoneEvent(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Color,
    val location: String,
) {
    /** The local date this occurrence starts on. All-day events are stored in UTC. */
    fun startDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()
}

/** What an exported task looks like as an event. */
data class EventFields(
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val description: String = "",
)

/**
 * Talks to Android's shared calendar storage (CalendarContract). Every calendar account on the
 * phone shows up here, and Android syncs changes with Google, Samsung and others on its own.
 * All calls do disk work, so run them off the main thread.
 */
object DeviceCalendars {
    fun canRead(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun canWrite(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(context: Context): List<PhoneCalendar> {
        if (!canRead(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val result = mutableListOf<PhoneCalendar>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection,
                "${CalendarContract.Calendars.VISIBLE} = 1", null,
                "${CalendarContract.Calendars.ACCOUNT_NAME}, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}",
            )?.use { c ->
                while (c.moveToNext()) {
                    result += PhoneCalendar(
                        id = c.getLong(0),
                        name = c.getString(1) ?: "Calendar",
                        account = c.getString(2) ?: "",
                        color = Color(c.getInt(3) or 0xFF000000.toInt()),
                        writable = c.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                    )
                }
            }
        }
        return result
    }

    /** Event occurrences between [from] (inclusive) and [until] (exclusive), local time. */
    fun events(
        context: Context,
        from: LocalDate,
        until: LocalDate,
        calendarIds: Set<Long>,
        excludeEventIds: Set<Long>,
    ): List<PhoneEvent> {
        if (!canRead(context) || calendarIds.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        // Widen by a day each side so all-day events stored in UTC aren't missed.
        val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = until.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val ids = calendarIds.joinToString(",")
        val result = mutableListOf<PhoneEvent>()
        runCatching {
            context.contentResolver.query(
                uri, projection, "${CalendarContract.Instances.CALENDAR_ID} IN ($ids)", null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val eventId = c.getLong(0)
                    if (eventId in excludeEventIds) continue
                    val event = PhoneEvent(
                        eventId = eventId,
                        calendarId = c.getLong(1),
                        title = c.getString(2)?.ifBlank { null } ?: "(No title)",
                        begin = c.getLong(3),
                        end = c.getLong(4),
                        allDay = c.getInt(5) == 1,
                        color = Color(c.getInt(6) or 0xFF000000.toInt()),
                        location = c.getString(7) ?: "",
                    )
                    val day = event.startDate(zone)
                    if (!day.isBefore(from) && day.isBefore(until)) result += event
                }
            }
        }
        return result
    }

    fun readEvent(context: Context, eventId: Long): EventFields? {
        if (!canRead(context)) return null
        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.DELETED,
            CalendarContract.Events.DESCRIPTION,
        )
        return runCatching {
            context.contentResolver.query(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                projection, null, null, null,
            )?.use { c ->
                if (!c.moveToFirst() || c.getInt(4) == 1) return@use null
                val start = c.getLong(1)
                val end = if (c.isNull(2)) start + 30 * 60_000L else c.getLong(2)
                EventFields(
                    title = c.getString(0) ?: "",
                    start = start,
                    end = end,
                    allDay = c.getInt(3) == 1,
                    description = c.getString(5) ?: "",
                )
            }
        }.getOrNull()
    }

    fun insertEvent(context: Context, calendarId: Long, fields: EventFields): Long? {
        if (!canWrite(context)) return null
        val values = values(fields).apply { put(CalendarContract.Events.CALENDAR_ID, calendarId) }
        return runCatching {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let(ContentUris::parseId)
        }.getOrNull()
    }

    fun updateEvent(context: Context, eventId: Long, fields: EventFields): Boolean {
        if (!canWrite(context)) return false
        return runCatching {
            context.contentResolver.update(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), values(fields), null, null,
            ) > 0
        }.getOrDefault(false)
    }

    fun deleteEvent(context: Context, eventId: Long) {
        if (!canWrite(context) || eventId <= 0) return
        runCatching {
            context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null)
        }
    }

    private fun values(fields: EventFields) = ContentValues().apply {
        put(CalendarContract.Events.TITLE, fields.title)
        put(CalendarContract.Events.DESCRIPTION, fields.description)
        put(CalendarContract.Events.DTSTART, fields.start)
        put(CalendarContract.Events.DTEND, fields.end)
        put(CalendarContract.Events.ALL_DAY, if (fields.allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, if (fields.allDay) "UTC" else ZoneId.systemDefault().id)
    }
}
