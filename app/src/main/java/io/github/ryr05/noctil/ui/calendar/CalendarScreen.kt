package io.github.ryr05.noctil.ui.calendar

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.calendar.CalendarPrefs
import io.github.ryr05.noctil.calendar.DeviceCalendars
import io.github.ryr05.noctil.calendar.PhoneEvent
import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.ui.components.CheckCircle
import io.github.ryr05.noctil.ui.components.Pill
import io.github.ryr05.noctil.ui.components.RoundIconButton
import io.github.ryr05.noctil.ui.components.SectionLabel
import io.github.ryr05.noctil.ui.components.TaskRow
import io.github.ryr05.noctil.ui.components.bottomContentPadding
import io.github.ryr05.noctil.ui.components.color
import io.github.ryr05.noctil.ui.components.groupShape
import io.github.ryr05.noctil.ui.components.relativeDay
import io.github.ryr05.noctil.ui.components.timeFormat
import io.github.ryr05.noctil.ui.components.topContentPadding
import io.github.ryr05.noctil.ui.tasks.TaskActions
import io.github.ryr05.noctil.ui.tasks.openTaskOrder
import io.github.ryr05.noctil.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class CalMode(val label: String) { Day("Day"), Week("Week"), Month("Month"), Year("Year") }

data class CalendarActions(
    val onSelect: (LocalDate) -> Unit,
    val onMode: (CalMode) -> Unit,
    val onSlot: (LocalDate, LocalTime) -> Unit,
    val onMoveTask: (TaskItem, LocalDate, LocalTime) -> Unit,
    val onOpenClass: (SchoolClass) -> Unit,
    val onOpenTimetable: () -> Unit,
    val onOpenSync: () -> Unit,
)

private val monthFormat = DateTimeFormatter.ofPattern("MMMM")
private val dayTitleFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val a11yDayFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val shortMonthDay = DateTimeFormatter.ofPattern("MMM d")

fun weekStartOf(date: LocalDate, locale: Locale = Locale.getDefault()): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))

@Composable
fun CalendarScreen(
    snapshot: Snapshot,
    today: LocalDate,
    selected: LocalDate,
    mode: CalMode,
    taskActions: TaskActions,
    actions: CalendarActions,
) {
    val context = LocalContext.current
    val events = rememberPhoneEvents(context, mode, selected)

    Column(Modifier.fillMaxSize().padding(top = topContentPadding())) {
        Header(mode, selected, today, actions, Modifier.padding(horizontal = 20.dp))
        when (mode) {
            CalMode.Month -> MonthView(snapshot, today, selected, events, taskActions, actions)
            CalMode.Year -> YearView(snapshot, today, selected, actions)
            CalMode.Day -> DayView(snapshot, today, selected, events, taskActions, actions)
            CalMode.Week -> WeekView(snapshot, today, selected, events, taskActions, actions)
        }
    }
}

/** Loads phone calendar events for what's on screen, and reloads when calendars change. */
@Composable
private fun rememberPhoneEvents(context: Context, mode: CalMode, selected: LocalDate): List<PhoneEvent> {
    val version by CalendarPrefs.version.collectAsState()
    val linkFlow = remember { (context.applicationContext as NoctilApplication).repository.db.calendarLinks().observeEventIds() }
    val exported by linkFlow.collectAsState(emptyList())
    val (from, until) = when (mode) {
        CalMode.Day -> selected to selected.plusDays(1)
        CalMode.Week -> weekStartOf(selected).let { it to it.plusDays(7) }
        CalMode.Month -> YearMonth.from(selected).let { it.atDay(1) to it.atEndOfMonth().plusDays(1) }
        CalMode.Year -> selected to selected
    }
    val events by produceState(emptyList<PhoneEvent>(), from, until, version, exported) {
        value = if (from == until) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                if (!DeviceCalendars.canRead(context)) return@withContext emptyList()
                val shown = CalendarPrefs.shownIds(context) ?: DeviceCalendars.calendars(context).map { it.id }.toSet()
                DeviceCalendars.events(context, from, until, shown, exported.toSet())
            }
        }
    }
    return events
}

@Composable
private fun Header(mode: CalMode, selected: LocalDate, today: LocalDate, actions: CalendarActions, modifier: Modifier) {
    val (overline, title) = when (mode) {
        CalMode.Day -> (if (selected == today) "Today" else selected.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())) to
            selected.format(DateTimeFormatter.ofPattern("MMMM d"))
        CalMode.Week -> {
            val start = weekStartOf(selected)
            val end = start.plusDays(6)
            val endText = if (end.month == start.month) end.dayOfMonth.toString() else end.format(shortMonthDay)
            start.year.toString() to "${start.format(shortMonthDay)} - $endText"
        }
        CalMode.Month -> selected.year.toString() to selected.format(monthFormat)
        CalMode.Year -> "Year" to selected.year.toString()
    }
    fun step(forward: Boolean) {
        val n = if (forward) 1L else -1L
        actions.onSelect(
            when (mode) {
                CalMode.Day -> selected.plusDays(n)
                CalMode.Week -> selected.plusWeeks(n)
                CalMode.Month -> selected.plusMonths(n)
                CalMode.Year -> selected.plusYears(n)
            },
        )
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(overline, style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
                Text(title, style = MaterialTheme.typography.displaySmall, color = Palette.TextPrimary, maxLines = 1)
            }
            RoundIconButton(Icons.Rounded.ChevronLeft, "Previous ${mode.label.lowercase()}") { step(false) }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Rounded.ChevronRight, "Next ${mode.label.lowercase()}") { step(true) }
        }
        Spacer(Modifier.height(14.dp))
        ModeSwitch(mode, actions.onMode)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selected != today) {
                item { Pill("Today", selected = false, leading = Icons.Rounded.Today) { actions.onSelect(today) } }
            }
            item { Pill("Timetable", selected = false, leading = Icons.Rounded.School, onClick = actions.onOpenTimetable) }
            item { Pill("Phone calendars", selected = false, leading = Icons.Rounded.Sync, onClick = actions.onOpenSync) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ModeSwitch(mode: CalMode, onMode: (CalMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(CircleShape).background(Palette.Surface).padding(4.dp),
    ) {
        CalMode.entries.forEach { m ->
            val isSelected = m == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Palette.SurfaceHigh else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab) { onMode(m) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    m.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Palette.TextPrimary else Palette.TextSecondary,
                )
            }
        }
    }
}

// ---------------- Month ----------------

@Composable
private fun MonthView(
    snapshot: Snapshot,
    today: LocalDate,
    selected: LocalDate,
    events: List<PhoneEvent>,
    taskActions: TaskActions,
    actions: CalendarActions,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val busy = remember(snapshot, events) {
        snapshot.tasks.filter { !it.done && it.date != null }.mapNotNull { it.date }.toSet() + events.map { it.startDate(zone) }
    }
    val dayTasks = snapshot.tasks
        .filter { it.date == selected && (it.parentId == null || !it.done) }
        .sortedWith(compareBy<TaskItem>({ it.done }).then(openTaskOrder))
    val dayClasses = snapshot.classes.filter { it.meetsOn(selected) }.sortedBy { it.start }
    val dayEvents = events.filter { it.startDate(zone) == selected }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomContentPadding()),
    ) {
        item(key = "grid") {
            MonthGrid(YearMonth.from(selected), today, selected, busy, onSelect = actions.onSelect)
        }
        item(key = "day-title") {
            SectionLabel(if (selected == today) "Today" else selected.format(dayTitleFormat))
        }
        val agenda = dayClasses.size + dayEvents.size
        if (agenda > 0) {
            var i = 0
            dayClasses.forEach { cls ->
                val index = i++
                item(key = "class-${cls.id}") {
                    AgendaRow(
                        color = cls.color, title = cls.name,
                        detail = listOf(timeRange(cls.start, cls.end), cls.room).filter { it.isNotBlank() }.joinToString(", "),
                        index = index, count = agenda,
                    ) { actions.onOpenClass(cls) }
                }
            }
            dayEvents.forEach { ev ->
                val index = i++
                item(key = "event-${ev.eventId}-${ev.begin}") {
                    AgendaRow(
                        color = ev.color, title = ev.title, detail = eventTime(ev),
                        index = index, count = agenda, outlined = true,
                    ) { openEvent(context, ev) }
                }
            }
            item(key = "agenda-gap") { Spacer(Modifier.height(12.dp)) }
        }
        if (dayTasks.isEmpty() && agenda == 0) {
            item(key = "empty") {
                Text(
                    "Nothing planned. Tap + to add a task on this day.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        } else {
            items(dayTasks.size, key = { "cal-${dayTasks[it].id}" }) { index ->
                TaskRow(
                    task = dayTasks[index],
                    index = index,
                    count = dayTasks.size,
                    today = today,
                    showDate = false,
                    onToggle = taskActions.onToggle,
                    onOpen = taskActions.onOpen,
                    onMoveToTomorrow = taskActions.onMoveToTomorrow,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun AgendaRow(
    color: Color,
    title: String,
    detail: String,
    index: Int,
    count: Int,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(groupShape(index, count))
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .drawBehind {
                if (index > 0) {
                    drawLine(Palette.Hairline, Offset(44.dp.toPx(), 0f), Offset(size.width - 16.dp.toPx(), 0f), 1.dp.toPx())
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .then(if (outlined) Modifier.border(2.dp, color, CircleShape) else Modifier.background(color)),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
        }
    }
}

private fun timeRange(start: LocalTime, end: LocalTime) = "${start.format(timeFormat)} - ${end.format(timeFormat)}"

private fun eventTime(ev: PhoneEvent): String {
    if (ev.allDay) return "All day"
    val zone = ZoneId.systemDefault()
    val s = Instant.ofEpochMilli(ev.begin).atZone(zone).toLocalTime()
    val e = Instant.ofEpochMilli(ev.end).atZone(zone).toLocalTime()
    return listOf(timeRange(s, e), ev.location).filter { it.isNotBlank() }.joinToString(", ")
}

/** Opens a phone calendar event in the phone's own calendar app. */
fun openEvent(context: Context, ev: PhoneEvent) {
    val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, ev.eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ev.begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ev.end)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No calendar app to show it in.
    }
}

@Composable
fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    busyDays: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val weekDays = remember(firstDayOfWeek) { (0L until 7L).map { firstDayOfWeek.plus(it) } }
    val leading = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val rows = (leading + month.lengthOfMonth() + 6) / 7

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface)
            .padding(horizontal = 10.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            weekDays.forEach { day: DayOfWeek ->
                Text(
                    text = day.getDisplayName(JavaTextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextTertiary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNumber = row * 7 + col - leading + 1
                    if (dayNumber < 1 || dayNumber > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(dayNumber)
                        DayCell(date, date == today, date == selected, date in busyDays, { onSelect(date) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    hasTasks: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = when {
        isSelected -> Palette.OnLavender
        isToday -> Palette.Lavender
        else -> Palette.TextPrimary
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .background(if (isSelected) Palette.Lavender else Color.Transparent)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, Palette.Lavender.copy(alpha = 0.6f), CircleShape) else Modifier)
            .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = buildString {
                    append(date.format(a11yDayFormat))
                    if (isToday) append(", today")
                    if (hasTasks) append(", has plans")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal),
            color = textColor,
        )
        if (hasTasks) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Palette.OnLavender else Palette.Lavender),
            )
        }
    }
}

// ---------------- Year ----------------

@Composable
private fun YearView(snapshot: Snapshot, today: LocalDate, selected: LocalDate, actions: CalendarActions) {
    val busy = remember(snapshot) { snapshot.tasks.filter { !it.done && it.date != null }.mapNotNull { it.date }.toSet() }
    val months = (1..12).map { YearMonth.of(selected.year, it) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomContentPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(months.chunked(2), key = { it.first().toString() }) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { month ->
                    MiniMonth(month, today, busy, Modifier.weight(1f)) {
                        actions.onSelect(if (YearMonth.from(today) == month) today else month.atDay(1))
                        actions.onMode(CalMode.Month)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMonth(month: YearMonth, today: LocalDate, busy: Set<LocalDate>, modifier: Modifier, onClick: () -> Unit) {
    val locale = Locale.getDefault()
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val leading = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val rows = (leading + month.lengthOfMonth() + 6) / 7
    val isCurrent = YearMonth.from(today) == month
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Surface)
            .clickable(onClickLabel = "Open ${month.month.getDisplayName(JavaTextStyle.FULL, locale)}", onClick = onClick)
            .padding(10.dp),
    ) {
        Text(
            month.month.getDisplayName(JavaTextStyle.FULL, locale),
            style = MaterialTheme.typography.labelLarge,
            color = if (isCurrent) Palette.Lavender else Palette.TextPrimary,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val day = row * 7 + col - leading + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            val isToday = date == today
                            if (isToday) Box(Modifier.fillMaxSize().padding(1.dp).clip(CircleShape).background(Palette.Lavender))
                            Text(
                                day.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = when {
                                    isToday -> Palette.OnLavender
                                    date in busy -> Palette.Lavender
                                    else -> Palette.TextSecondary
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Day and week ----------------

@Composable
private fun DayView(
    snapshot: Snapshot,
    today: LocalDate,
    selected: LocalDate,
    events: List<PhoneEvent>,
    taskActions: TaskActions,
    actions: CalendarActions,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val scroll = rememberScrollState()
    val days = listOf(selected)
    val blocks = remember(snapshot, events, selected) { timedBlocks(days, snapshot, events) }
    val untimed = snapshot.tasks
        .filter { it.date == selected && it.time == null && (it.parentId == null || !it.done) }
        .sortedWith(compareBy<TaskItem>({ it.done }).then(openTaskOrder))
    val allDayEvents = events.filter { it.allDay && it.startDate(zone) == selected }

    Column(Modifier.fillMaxSize()) {
        if (untimed.isNotEmpty() || allDayEvents.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(untimed, key = { "u${it.id}" }) { task -> UntimedChip(task, taskActions) }
                items(allDayEvents, key = { "a${it.eventId}" }) { ev ->
                    EventChip(ev) { openEvent(context, ev) }
                }
            }
        } else {
            Text(
                "Tap a time to plan something. Long-press a task to drag it.",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
            )
        }
        TimeGrid(
            days = days,
            today = today,
            blocks = blocks,
            scroll = scroll,
            bottomPadding = bottomContentPadding(),
            onSlotTap = actions.onSlot,
            onMoveTask = actions.onMoveTask,
            onOpenTask = taskActions.onOpen,
            onOpenEvent = { openEvent(context, it) },
            onOpenClass = actions.onOpenClass,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
    }
}

@Composable
private fun WeekView(
    snapshot: Snapshot,
    today: LocalDate,
    selected: LocalDate,
    events: List<PhoneEvent>,
    taskActions: TaskActions,
    actions: CalendarActions,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val scroll = rememberScrollState()
    val start = weekStartOf(selected)
    val days = (0L until 7L).map { start.plusDays(it) }
    val blocks = remember(snapshot, events, start) { timedBlocks(days, snapshot, events) }
    val untimedDays = remember(snapshot, events, start) {
        days.associateWith { d ->
            snapshot.tasks.count { it.date == d && it.time == null && !it.done && it.parentId == null } +
                events.count { it.allDay && it.startDate(zone) == d }
        }
    }
    val locale = Locale.getDefault()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 12.dp, bottom = 6.dp)) {
            days.forEach { d ->
                val isToday = d == today
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Open ${d.format(a11yDayFormat)}") {
                            actions.onSelect(d)
                            actions.onMode(CalMode.Day)
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        d.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextTertiary,
                    )
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isToday) Palette.Lavender else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isToday) Palette.OnLavender else Palette.TextPrimary,
                        )
                    }
                    val n = untimedDays[d] ?: 0
                    Text(
                        if (n > 0) "+$n" else " ",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Palette.Lavender,
                    )
                }
            }
        }
        TimeGrid(
            days = days,
            today = today,
            blocks = blocks,
            scroll = scroll,
            bottomPadding = bottomContentPadding(),
            onSlotTap = actions.onSlot,
            onMoveTask = actions.onMoveTask,
            onOpenTask = taskActions.onOpen,
            onOpenEvent = { openEvent(context, it) },
            onOpenClass = actions.onOpenClass,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
    }
}

@Composable
private fun UntimedChip(task: TaskItem, actions: TaskActions) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(Palette.Surface)
            .clickable(onClickLabel = "Open task") { actions.onOpen(task) }
            .padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            CheckCircle(task.done, task.priority.color(), task.title, { actions.onToggle(task) }, size = 18.dp)
        }
        Text(
            task.title,
            style = MaterialTheme.typography.labelLarge,
            color = if (task.done) Palette.TextTertiary else Palette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(150.dp),
        )
    }
}

@Composable
private fun EventChip(ev: PhoneEvent, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .border(1.dp, ev.color.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(ev.color))
        Spacer(Modifier.width(8.dp))
        Text(ev.title, style = MaterialTheme.typography.labelLarge, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
    }
}

/** Short label for a day used elsewhere in calendar sheets. */
fun dayLabel(date: LocalDate, today: LocalDate): String = relativeDay(date, today)
