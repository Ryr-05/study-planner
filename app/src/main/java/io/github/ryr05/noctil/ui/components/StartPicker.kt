package io.github.ryr05.noctil.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.reminders.StartReminders
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** One quick choice in a Start menu: "Thu 7:00 PM" with "1 day before" under it. */
@Composable
fun StartChoiceText(choice: StartReminders.Choice, today: LocalDate) {
    Column {
        Text(StartReminders.label(choice.at, today), color = Palette.TextPrimary)
        choice.hint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary) }
    }
}

/** Which date the picker sets. Both use the same screen, so they feel like one system. */
enum class WhenKind { Due, Start }

private val START_TIMES = listOf(
    "Morning" to LocalTime.of(9, 0),
    "Afternoon" to LocalTime.of(14, 0),
    "Evening" to LocalTime.of(19, 0),
    "Night" to LocalTime.of(21, 0),
)

// The times deadlines usually are: start of class, noon, end of the work day, midnight.
private val DUE_TIMES = listOf(
    LocalTime.of(9, 0),
    LocalTime.of(12, 0),
    LocalTime.of(17, 0),
    LocalTime.of(23, 59),
)

/**
 * Pick a due date or a start on one screen: tap a day, tap a time, done. "Other" opens the
 * full calendar or clock only for the rare odd day or time. For a start, quick picks like
 * "1 day before" set it in one tap.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WhenPickerDialog(
    kind: WhenKind,
    initialDay: LocalDate?,
    initialTime: LocalTime?,
    today: LocalDate,
    dueDate: LocalDate?,
    dueTime: LocalTime?,
    onDismiss: () -> Unit,
    onPick: (LocalDate, LocalTime?) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val start = kind == WhenKind.Start
    val deadline = dueDate?.atTime(dueTime ?: LocalTime.MAX)
    var day by remember {
        mutableStateOf(
            initialDay ?: if (start) dueDate?.minusDays(1)?.takeIf { !it.isBefore(today) } ?: today else today,
        )
    }
    var time by remember { mutableStateOf(initialTime ?: if (start) StartReminders.EVENING else null) }
    var otherDay by remember { mutableStateOf(false) }
    var otherTime by remember { mutableStateOf(false) }
    val now = LocalDateTime.now()
    val problem = if (!start) null else {
        val at = day.atTime(time ?: StartReminders.EVENING)
        when {
            !at.isAfter(now) -> "That time has passed"
            deadline != null && !at.isBefore(deadline) -> "That's after the deadline"
            else -> null
        }
    }
    // A start can only be up to the deadline; a deadline can be any day.
    val lastDay = if (start) listOfNotNull(today.plusDays(6), dueDate).min() else today.plusDays(6)
    val days = generateSequence(today) { it.plusDays(1) }.takeWhile { !it.isAfter(lastDay) }.toList()
    val quick = if (start) StartReminders.choices(dueDate, dueTime, now).filter { it.hint != null }.take(3) else emptyList()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.SurfaceHigh,
        title = { Text(if (start) "When will you start?" else "When is it due?", color = Palette.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (start && dueDate != null) {
                    Text("Due ${dueText(dueDate, dueTime, today)}", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
                }
                if (quick.isNotEmpty()) {
                    Text("Quick picks", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        quick.forEach { c ->
                            PickPill("${c.hint}, ${c.at.toLocalTime().format(timeFormat)}", selected = false) {
                                onPick(c.at.toLocalDate(), c.at.toLocalTime())
                            }
                        }
                    }
                }
                Text("Day", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    days.forEach { d ->
                        val label = when (d) {
                            today -> "Today"
                            today.plusDays(1) -> "Tomorrow"
                            else -> d.format(DateTimeFormatter.ofPattern("EEE d"))
                        }
                        PickPill(label, selected = d == day) { day = d }
                    }
                    PickPill(if (day in days) "Other day" else day.format(DateTimeFormatter.ofPattern("MMM d")), selected = day !in days) { otherDay = true }
                }
                Text("Time", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (start) {
                        START_TIMES.forEach { (name, t) -> PickPill("$name ${t.format(timeFormat)}", selected = t == time) { time = t } }
                    } else {
                        PickPill("No time", selected = time == null) { time = null }
                        DUE_TIMES.forEach { t -> PickPill(t.format(timeFormat), selected = t == time) { time = t } }
                    }
                    val presets = if (start) START_TIMES.map { it.second } else DUE_TIMES
                    val custom = time != null && time !in presets
                    PickPill(if (custom) time!!.format(timeFormat) else "Other time", selected = custom) { otherTime = true }
                }
                val summary = problem ?: if (start) {
                    val ahead = dueDate?.let { ChronoUnit.DAYS.between(day, it) }
                    "Start " + StartReminders.label(day.atTime(time ?: StartReminders.EVENING), today) + when (ahead) {
                        null -> ""
                        0L -> ", the day it's due"
                        1L -> ", 1 day before it's due"
                        else -> ", $ahead days before it's due"
                    }
                } else {
                    "Due " + dueText(day, time, today) + if (time == null) " (any time that day)" else ""
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (problem != null) Palette.PriorityHigh else Palette.TextPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (onRemove != null) {
                    Text(
                        if (start) "Remove start" else "Remove deadline",
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.PriorityHigh,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onRemove)
                            .padding(vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(day, time) }, enabled = problem == null) { Text(if (start) "Set start" else "Set due") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.TextPrimary) } },
    )

    if (otherDay) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { otherDay = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    otherDay = false
                }) { Text("Set day") }
            },
            dismissButton = { TextButton(onClick = { otherDay = false }) { Text("Cancel", color = Palette.TextPrimary) } },
        ) { DatePicker(state = state) }
    }
    if (otherTime) {
        val shown = time ?: LocalTime.of(17, 0)
        val state = rememberTimePickerState(shown.hour, shown.minute, DateFormat.is24HourFormat(context))
        TimePickerDialog(
            onDismissRequest = { otherTime = false },
            title = { Text("Pick a time", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    otherTime = false
                }) { Text("Set time") }
            },
            dismissButton = { TextButton(onClick = { otherTime = false }) { Text("Cancel", color = Palette.TextPrimary) } },
        ) { TimePicker(state = state) }
    }
}

/** The start picker, as quick add uses it. */
@Composable
fun StartPickerDialog(
    initial: LocalDateTime?,
    dueDate: LocalDate?,
    dueTime: LocalTime?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDateTime) -> Unit,
) = WhenPickerDialog(
    kind = WhenKind.Start,
    initialDay = initial?.toLocalDate(),
    initialTime = initial?.toLocalTime(),
    today = today,
    dueDate = dueDate,
    dueTime = dueTime,
    onDismiss = onDismiss,
    onPick = { d, t -> onPick(d.atTime(t ?: StartReminders.EVENING)) },
)

/** "Fri, Nov 20, 11:59 PM", or "Tomorrow" when it's close. */
fun dueText(date: LocalDate, time: LocalTime?, today: LocalDate): String =
    relativeDay(date, today) + (time?.let { ", ${it.format(timeFormat)}" } ?: "")

@Composable
private fun PickPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Palette.OnLavender else Palette.TextPrimary,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(if (selected) Palette.Lavender else Palette.Surface)
            .border(1.dp, if (selected) Palette.Lavender else Palette.Outline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
