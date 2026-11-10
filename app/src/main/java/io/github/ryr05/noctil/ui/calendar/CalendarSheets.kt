package io.github.ryr05.noctil.ui.calendar

import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.calendar.CalendarPrefs
import io.github.ryr05.noctil.calendar.CalendarSync
import io.github.ryr05.noctil.calendar.DeviceCalendars
import io.github.ryr05.noctil.calendar.PhoneCalendar
import io.github.ryr05.noctil.data.model.ItemColors
import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.data.model.TaskList
import io.github.ryr05.noctil.ui.components.GlassSheet
import io.github.ryr05.noctil.ui.components.Pill
import io.github.ryr05.noctil.ui.components.relativeDay
import io.github.ryr05.noctil.ui.components.timeFormat
import io.github.ryr05.noctil.ui.tasks.ColorSwatches
import io.github.ryr05.noctil.ui.tasks.ConfirmDialog
import io.github.ryr05.noctil.ui.tasks.openTaskOrder
import io.github.ryr05.noctil.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

// ---------------- Plan a time slot ----------------

/**
 * Time blocking: tap an empty slot on the calendar, then pick a task to put there or type a new one.
 */
@Composable
fun SlotSheet(
    slot: Pair<LocalDate, LocalTime>?,
    snapshot: Snapshot,
    today: LocalDate,
    onDismiss: () -> Unit,
    onPlace: (TaskItem, LocalDate, LocalTime) -> Unit,
    onCreate: (String, LocalDate, LocalTime) -> Unit,
) {
    var last by remember { mutableStateOf(slot) }
    if (slot != null) last = slot
    val (date, time) = last ?: (today to LocalTime.NOON)
    GlassSheet(
        visible = slot != null,
        title = "${relativeDay(date, today)} at ${time.format(timeFormat)}",
        onDismiss = onDismiss,
    ) {
        var text by rememberSaveable(slot) { mutableStateOf("") }
        val submit = {
            if (text.isNotBlank()) onCreate(text, date, time)
            text = ""
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.TextPrimary),
                cursorBrush = SolidColor(Palette.Lavender),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f).semantics { contentDescription = "New task at ${time.format(timeFormat)}" },
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Palette.Surface.copy(alpha = 0.7f))
                            .padding(14.dp),
                    ) {
                        if (text.isEmpty()) Text("New task at ${time.format(timeFormat)}", style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (text.isNotBlank()) Palette.Lavender else Palette.SurfaceHigh)
                    .clickable(enabled = text.isNotBlank(), role = Role.Button) { submit() }
                    .semantics { contentDescription = "Add task" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = if (text.isNotBlank()) Palette.OnLavender else Palette.TextTertiary)
            }
        }

        val sameDay = snapshot.tasks.filter { it.parentId == null && !it.done && it.date == date && it.time == null }.sortedWith(openTaskOrder)
        val noDate = snapshot.tasks.filter { it.parentId == null && !it.done && it.date == null }.sortedWith(openTaskOrder).take(20)
        val overdue = snapshot.tasks.filter { it.parentId == null && !it.done && it.date != null && it.date.isBefore(today) }.sortedWith(openTaskOrder).take(10)
        LazyColumn(Modifier.padding(top = 8.dp)) {
            fun group(title: String, tasks: List<TaskItem>) {
                if (tasks.isEmpty()) return
                item(key = "h-$title") {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp))
                }
                tasks.forEach { task ->
                    item(key = "$title-${task.id}") { PickRow(task, today) { onPlace(task, date, time) } }
                }
            }
            group("Unscheduled on this day", sameDay)
            if (date == today) group("Overdue", overdue)
            group("No date yet", noDate)
            if (sameDay.isEmpty() && noDate.isEmpty() && (date != today || overdue.isEmpty())) {
                item(key = "none") {
                    Text(
                        "Every task already has a time. Type a new one above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(top = 16.dp, start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickRow(task: TaskItem, today: LocalDate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Plan it here", onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(task.date?.let { relativeDay(it, today) }, task.list?.name).joinToString(", ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
        }
        Text("Place", style = MaterialTheme.typography.labelLarge, color = Palette.Lavender, modifier = Modifier.padding(end = 4.dp))
    }
}

// ---------------- Timetable ----------------

@Composable
fun TimetableSheet(
    visible: Boolean,
    snapshot: Snapshot,
    onDismiss: () -> Unit,
    onEdit: (SchoolClass?) -> Unit,
) {
    GlassSheet(visible = visible, title = "Timetable", onDismiss = onDismiss) {
        Text(
            "Add each class once. It shows on your calendar every week.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        val classes = snapshot.classes.sortedWith(compareBy({ c -> c.days.minOfOrNull { it.value } ?: 8 }, { it.start }))
        LazyColumn(Modifier.weight(1f, fill = false)) {
            classes.forEach { cls ->
                item(key = cls.id) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClickLabel = "Edit ${cls.name}") { onEdit(cls) }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(cls.color))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cls.name, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
                            Text(
                                listOf(daysText(cls.days), "${cls.start.format(timeFormat)} - ${cls.end.format(timeFormat)}", cls.room)
                                    .filter { it.isNotBlank() }.joinToString(", "),
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Pill("Add a class", selected = true, leading = Icons.Rounded.Add) { onEdit(null) }
    }
}

private fun daysText(days: Set<DayOfWeek>): String = DayOfWeek.entries.filter { it in days }
    .joinToString(" ") { it.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassEditorDialog(
    initial: SchoolClass?,
    lists: List<TaskList>,
    onDismiss: () -> Unit,
    onSave: (name: String, room: String, color: Color, days: Set<DayOfWeek>, start: LocalTime, end: LocalTime, listId: Long?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var room by rememberSaveable { mutableStateOf(initial?.room ?: "") }
    var colorArgb by rememberSaveable { mutableStateOf((initial?.color ?: ItemColors[1]).toArgb()) }
    var days by remember { mutableStateOf(initial?.days ?: setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)) }
    var start by remember { mutableStateOf(initial?.start ?: LocalTime.of(9, 0)) }
    var end by remember { mutableStateOf(initial?.end ?: LocalTime.of(10, 0)) }
    var listId by rememberSaveable { mutableStateOf(initial?.listId) }
    var picking by remember { mutableStateOf<String?>(null) } // "start" or "end"
    var confirmDelete by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && days.isNotEmpty() && end.isAfter(start)

    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = "Delete \"${initial?.name}\"?",
            body = "It's removed from your timetable. Tasks are not affected.",
            confirm = "Delete class",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.SurfaceHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text(if (initial == null) "New class" else "Edit class", color = Palette.TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Class name") },
                    placeholder = { Text("Calculus II") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = room, onValueChange = { room = it }, label = { Text("Room (optional)") },
                    placeholder = { Text("Hall B, room 204") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Label("Days")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        Pill(d.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()), selected = d in days) {
                            days = if (d in days) days - d else days + d
                        }
                    }
                }
                Label("Time")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(start.format(timeFormat), selected = false, leading = Icons.Rounded.Schedule) { picking = "start" }
                    Text("to", color = Palette.TextSecondary)
                    Pill(end.format(timeFormat), selected = false, leading = Icons.Rounded.Schedule) { picking = "end" }
                }
                if (!end.isAfter(start)) {
                    Text("End time must be after the start.", style = MaterialTheme.typography.labelMedium, color = Palette.PriorityHigh, modifier = Modifier.padding(top = 6.dp))
                }
                Label("Color")
                ColorSwatches(Color(colorArgb)) { colorArgb = it.toArgb() }
                if (lists.isNotEmpty()) {
                    Label("List for its homework")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("None", selected = listId == null) { listId = null }
                        lists.forEach { l -> Pill(l.name, selected = listId == l.id) { listId = l.id } }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, room, Color(colorArgb), days, start, end, listId) }, enabled = valid) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = Palette.PriorityHigh) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.TextPrimary) }
            }
        },
    )

    picking?.let { which ->
        ClockDialog(
            initial = if (which == "start") start else end,
            onDismiss = { picking = null },
            onPick = { t ->
                if (which == "start") {
                    val length = java.time.Duration.between(start, end).toMinutes().coerceAtLeast(30)
                    start = t
                    if (!end.isAfter(t)) end = t.plusMinutes(length).let { if (it.isBefore(t)) LocalTime.of(23, 59) else it }
                } else {
                    end = t
                }
                picking = null
            },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a time", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("Set time") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.TextPrimary) } },
    ) { TimePicker(state = state) }
}

// ---------------- Phone calendars ----------------

/**
 * Pick which phone calendars show up in Noctil, and optionally one to copy dated tasks into.
 * Copying is two-way: move or rename the event in Google Calendar and the task follows.
 */
@Composable
fun SyncSheet(visible: Boolean, onDismiss: () -> Unit) {
    GlassSheet(visible = visible, title = "Phone calendars", onDismiss = onDismiss) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val version by CalendarPrefs.version.collectAsState()
        var permissionTick by remember { mutableStateOf(0) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionTick++
            CalendarPrefs.bump()
        }
        val canRead = remember(permissionTick, visible) { DeviceCalendars.canRead(context) }
        val canWrite = remember(permissionTick, visible) { DeviceCalendars.canWrite(context) }
        val calendars by produceState(emptyList<PhoneCalendar>(), canRead, visible) {
            value = withContext(Dispatchers.IO) { DeviceCalendars.calendars(context) }
        }
        var confirmStop by remember { mutableStateOf<Long?>(null) }

        if (!canRead) {
            Text(
                "Allow calendar access to see your Google, Samsung or Outlook events here. You can also " +
                    "choose a calendar to copy your dated tasks into.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            Pill("Allow calendar access", selected = true) {
                launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Using iCloud? Install the free DAVx5 app to add your iCloud calendar to this phone first.",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
            )
            return@GlassSheet
        }

        val shown = remember(version, calendars) { CalendarPrefs.shownIds(context) ?: calendars.map { it.id }.toSet() }
        val exportId = remember(version) { CalendarPrefs.exportCalendarId(context) }

        LazyColumn(Modifier.weight(1f, fill = false)) {
            item(key = "show-label") { SheetLabel("Show in Noctil") }
            if (calendars.isEmpty()) {
                item(key = "none") {
                    Text("No calendars found on this phone.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                }
            }
            calendars.forEach { cal ->
                item(key = "show-${cal.id}") {
                    ChoiceRow(cal.name, cal.account, cal.color, checked = cal.id in shown, radio = false) {
                        CalendarPrefs.setShownIds(context, if (cal.id in shown) shown - cal.id else shown + cal.id)
                    }
                }
            }
            item(key = "export-label") {
                SheetLabel("Add my dated tasks to")
                Text(
                    "Tasks show up as events. Move or rename them there and Noctil updates the task. Finishing a task removes its event.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item(key = "export-none") {
                ChoiceRow("Don't add tasks", "Keep tasks only in Noctil", null, checked = exportId < 0, radio = true) {
                    if (exportId >= 0) confirmStop = exportId
                }
            }
            calendars.filter { it.writable }.forEach { cal ->
                item(key = "export-${cal.id}") {
                    ChoiceRow(cal.name, cal.account, cal.color, checked = exportId == cal.id, radio = true, enabled = canWrite) {
                        CalendarPrefs.setExportCalendarId(context, cal.id)
                        scope.launch { CalendarSync.sync(context) }
                    }
                }
            }
            item(key = "icloud") {
                Text(
                    "Using iCloud? Install the free DAVx5 app to add your iCloud calendar to this phone, then it shows up here.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        confirmStop?.let {
            ConfirmDialog(
                title = "Stop adding tasks?",
                body = "The events Noctil added to your calendar are removed. Your tasks stay in Noctil.",
                confirm = "Stop and remove",
                onConfirm = {
                    confirmStop = null
                    scope.launch { CalendarSync.stopExport(context) }
                },
                onDismiss = { confirmStop = null },
            )
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp))
}

@Composable
private fun ChoiceRow(
    title: String,
    detail: String,
    color: Color?,
    checked: Boolean,
    radio: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = if (radio) Role.RadioButton else Role.Checkbox, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(if (radio) CircleShape else RoundedCornerShape(6.dp))
                .background(if (checked) Palette.Lavender else Color.Transparent)
                .border(2.dp, if (checked) Palette.Lavender else Palette.TextTertiary, if (radio) CircleShape else RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = Palette.OnLavender, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(14.dp))
        if (color != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Palette.TextPrimary else Palette.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
