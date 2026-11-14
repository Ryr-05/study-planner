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
import androidx.compose.ui.Alignment
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

private val TIMES = listOf(
    "Morning" to LocalTime.of(9, 0),
    "Afternoon" to LocalTime.of(14, 0),
    "Evening" to LocalTime.of(19, 0),
    "Night" to LocalTime.of(21, 0),
)

/**
 * Pick when to start on one screen: tap a day, tap a time, done. "Other" opens the full
 * calendar or clock only for the rare odd time.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StartPickerDialog(
    initial: LocalDateTime?,
    dueDate: LocalDate?,
    dueTime: LocalTime?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDateTime) -> Unit,
) {
    val context = LocalContext.current
    val deadline = dueDate?.atTime(dueTime ?: LocalTime.MAX)
    var day by remember {
        mutableStateOf(initial?.toLocalDate() ?: dueDate?.minusDays(1)?.takeIf { !it.isBefore(today) } ?: today)
    }
    var time by remember { mutableStateOf(initial?.toLocalTime() ?: StartReminders.EVENING) }
    var otherDay by remember { mutableStateOf(false) }
    var otherTime by remember { mutableStateOf(false) }
    val at = day.atTime(time)
    val now = LocalDateTime.now()
    val problem = when {
        !at.isAfter(now) -> "That time has passed"
        deadline != null && !at.isBefore(deadline) -> "That's after the deadline"
        else -> null
    }
    // Today up to the deadline (at most a week), so every button is a real option.
    val lastDay = listOfNotNull(today.plusDays(6), dueDate).min()
    val days = generateSequence(today) { it.plusDays(1) }.takeWhile { !it.isAfter(lastDay) }.toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.SurfaceHigh,
        title = { Text("When will you start?", color = Palette.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dueDate != null) {
                    val due = relativeDay(dueDate, today) + (dueTime?.let { ", ${it.format(timeFormat)}" } ?: "")
                    Text("Due $due", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
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
                    PickPill("Other day", selected = day !in days) { otherDay = true }
                }
                Text("Time", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TIMES.forEach { (name, t) ->
                        PickPill("$name ${t.format(timeFormat)}", selected = t == time) { time = t }
                    }
                    PickPill(
                        if (TIMES.none { it.second == time }) time.format(timeFormat) else "Other time",
                        selected = TIMES.none { it.second == time },
                    ) { otherTime = true }
                }
                val ahead = dueDate?.let { ChronoUnit.DAYS.between(day, it) }
                val summary = problem ?: buildString {
                    append("Start ").append(StartReminders.label(at, today))
                    when (ahead) {
                        null -> Unit
                        0L -> append(", the day it's due")
                        1L -> append(", 1 day before it's due")
                        else -> append(", $ahead days before it's due")
                    }
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (problem != null) Palette.PriorityHigh else Palette.TextPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(at) }, enabled = problem == null) { Text("Set start") }
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
        val state = rememberTimePickerState(time.hour, time.minute, DateFormat.is24HourFormat(context))
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
