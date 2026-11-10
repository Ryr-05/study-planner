package io.github.ryr05.noctil.ui.habits

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Remove
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ryr05.noctil.data.model.Habit
import io.github.ryr05.noctil.data.model.ItemColors
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.ui.components.GlassSheet
import io.github.ryr05.noctil.ui.components.Pill
import io.github.ryr05.noctil.ui.components.RoundIconButton
import io.github.ryr05.noctil.ui.components.ScreenTitle
import io.github.ryr05.noctil.ui.components.SectionLabel
import io.github.ryr05.noctil.ui.components.bottomContentPadding
import io.github.ryr05.noctil.ui.components.groupShape
import io.github.ryr05.noctil.ui.components.timeFormat
import io.github.ryr05.noctil.ui.components.topContentPadding
import io.github.ryr05.noctil.ui.tasks.ColorSwatches
import io.github.ryr05.noctil.ui.tasks.ConfirmDialog
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

data class HabitActions(
    val onTap: (Habit, LocalDate) -> Unit,
    val onOpen: (Habit) -> Unit,
    val onNew: () -> Unit,
    val onQuickAdd: (name: String, goal: Int) -> Unit,
)

private val suggestions = listOf("Read 20 minutes" to 1, "Drink water" to 8, "Exercise" to 1, "Review notes" to 1, "Sleep by 11" to 1)

@Composable
fun HabitsScreen(snapshot: Snapshot, today: LocalDate, actions: HabitActions) {
    val due = snapshot.habits.filter { it.isDue(today) }
    val doneToday = due.count { it.isDone(today) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topContentPadding(), bottom = bottomContentPadding()),
    ) {
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle(
                    title = "Habits",
                    overline = when {
                        snapshot.habits.isEmpty() -> "Small things, every day"
                        due.isEmpty() -> "Nothing due today"
                        doneToday == due.size -> "All $doneToday done today"
                        else -> "$doneToday of ${due.size} done today"
                    },
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(Icons.Rounded.Add, "New habit", onClick = actions.onNew)
            }
        }
        if (snapshot.loaded && snapshot.habits.isEmpty()) {
            item(key = "empty") { EmptyHabits(actions) }
        }
        val todayList = snapshot.habits.filter { it.isDue(today) }
        val other = snapshot.habits.filter { !it.isDue(today) }
        if (todayList.isNotEmpty()) {
            item(key = "today-label") { SectionLabel("Today") }
            todayList.forEachIndexed { i, h ->
                item(key = "h${h.id}") { HabitRow(h, today, i, todayList.size, actions, Modifier.animateItem()) }
            }
        }
        if (other.isNotEmpty()) {
            item(key = "other-label") { SectionLabel("Not due today") }
            other.forEachIndexed { i, h ->
                item(key = "o${h.id}") { HabitRow(h, today, i, other.size, actions, Modifier.animateItem()) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyHabits(actions: HabitActions) {
    Column(Modifier.padding(top = 32.dp, start = 4.dp)) {
        Text("Start a habit", style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Check it off each day to build a streak. Tap one to add it, or make your own with +.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { (name, goal) ->
                Pill(if (goal > 1) "$name ($goal a day)" else name, selected = false, leading = Icons.Rounded.Add) {
                    actions.onQuickAdd(name, goal)
                }
            }
        }
    }
}

@Composable
private fun HabitRow(habit: Habit, today: LocalDate, index: Int, count: Int, actions: HabitActions, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val streak = habit.currentStreak(today)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(groupShape(index, count))
            .background(Palette.Surface)
            .clickable(onClickLabel = "Open ${habit.name}") { actions.onOpen(habit) }
            .drawBehind {
                if (index > 0) drawLine(Palette.Hairline, Offset(72.dp.toPx(), 0f), Offset(size.width - 16.dp.toPx(), 0f), 1.dp.toPx())
            }
            .padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckButton(habit, today) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            actions.onTap(habit, today)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(
                    Icons.Rounded.LocalFireDepartment, contentDescription = null,
                    tint = if (streak > 0) Palette.PriorityMedium else Palette.TextTertiary, modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    when (streak) {
                        0 -> "No streak yet"
                        1 -> "1 day streak"
                        else -> "$streak day streak"
                    } + if (habit.goal > 1) ", ${habit.count(today)} of ${habit.goal} today" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            WeekStrip(habit, today) { day -> actions.onTap(habit, day) }
        }
    }
}

/** A round button that fills up as you check in. One tap per check-in; tap again at the goal to clear. */
@Composable
fun CheckButton(habit: Habit, day: LocalDate, size: androidx.compose.ui.unit.Dp = 40.dp, onTap: () -> Unit) {
    val count = habit.count(day)
    val done = count >= habit.goal
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClickLabel = if (done) "Clear" else "Check in") { onTap() }
            .semantics { contentDescription = "${habit.name}, $count of ${habit.goal}" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            if (done) {
                drawCircle(habit.color)
            } else {
                drawArc(Palette.Hairline, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                if (count > 0) {
                    drawArc(
                        habit.color, -90f, 360f * count / habit.goal, false, Offset(inset, inset), arc,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        if (done) Icon(Icons.Rounded.Check, contentDescription = null, tint = Palette.OnLavender, modifier = Modifier.size(size * 0.5f))
        else if (habit.goal > 1) Text("$count", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
    }
}

@Composable
private fun WeekStrip(habit: Habit, today: LocalDate, onTap: (LocalDate) -> Unit) {
    val locale = Locale.getDefault()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (6 downTo 0).forEach { back ->
            val day = today.minusDays(back.toLong())
            val due = habit.isDue(day)
            val done = habit.isDone(day)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    day.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (day == today) Palette.TextPrimary else Palette.TextTertiary,
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (done) habit.color else Color.Transparent)
                        .border(1.5.dp, if (done) habit.color else if (due) habit.color.copy(alpha = 0.45f) else Palette.Hairline, CircleShape)
                        .clickable(enabled = !day.isBefore(habit.createdOn), role = Role.Checkbox) { onTap(day) }
                        .semantics { contentDescription = "${day.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)}, ${if (done) "done" else "not done"}" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Rounded.Check, contentDescription = null, tint = Palette.OnLavender, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

// ---------------- Detail ----------------

@Composable
fun HabitDetailSheet(habit: Habit?, today: LocalDate, onDismiss: () -> Unit, onEdit: (Habit) -> Unit, onTap: (Habit, LocalDate) -> Unit) {
    var last by remember { mutableStateOf(habit) }
    if (habit != null) last = habit
    val h = last
    GlassSheet(visible = habit != null, title = h?.name ?: "", onDismiss = onDismiss) {
        if (h == null) return@GlassSheet
        val rate30 = h.rate(today.minusDays(29), today)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Current streak", "${h.currentStreak(today)}", "days", Modifier.weight(1f))
                StatTile("Best streak", "${h.bestStreak(today)}", "days", Modifier.weight(1f))
                StatTile("Last 30 days", rate30?.let { "${(it * 100).toInt()}" } ?: "-", if (rate30 != null) "% done" else "nothing due", Modifier.weight(1f))
            }
            Text(
                "Last 20 weeks",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 10.dp),
            )
            Heatmap(h, today)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Less", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
                listOf(0.25f, 0.5f, 0.75f, 1f).forEach { a ->
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(h.color.copy(alpha = a)))
                }
                Spacer(Modifier.width(4.dp))
                Text("More", style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary)
            }
            val schedule = if (h.everyDay) "Every day" else DayOfWeek.entries.filter { it in h.days }
                .joinToString(", ") { it.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()) }
            Text(
                listOfNotNull(
                    schedule,
                    if (h.goal > 1) "${h.goal} times a day" else null,
                    h.reminder?.let { "Reminder at ${it.format(timeFormat)}" },
                ).joinToString(". "),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 18.dp),
            )
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (h.isDone(today)) "Done today" else "Check in today", selected = h.isDone(today), leading = Icons.Rounded.Check) { onTap(h, today) }
                Pill("Edit", selected = false, leading = Icons.Rounded.Edit) { onEdit(h) }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(Palette.Surface.copy(alpha = 0.7f)).padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary, maxLines = 1)
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = Palette.TextPrimary)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary, maxLines = 1)
    }
}

/** Twenty weeks of check-ins, one square per day, darker for days you hit the goal. */
@Composable
private fun Heatmap(habit: Habit, today: LocalDate) {
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val weeks = 20
    val start = today.with(TemporalAdjusters.previousOrSame(firstDay)).minusWeeks((weeks - 1).toLong())
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 3.dp
        val cell = (maxWidth - gap * (weeks - 1)) / weeks
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(cell * 7 + gap * 6)
                .semantics { contentDescription = "Check-in history for the last 20 weeks" },
        ) {
            val c = cell.toPx()
            val g = gap.toPx()
            for (w in 0 until weeks) {
                for (d in 0 until 7) {
                    val day = start.plusWeeks(w.toLong()).plusDays(d.toLong())
                    if (day.isAfter(today)) continue
                    val ratio = (habit.count(day).toFloat() / habit.goal).coerceIn(0f, 1f)
                    val color = when {
                        ratio > 0f -> habit.color.copy(alpha = 0.25f + 0.75f * ratio)
                        habit.isDue(day) -> Palette.SurfaceHigh
                        else -> Palette.Hairline.copy(alpha = 0.5f)
                    }
                    drawRoundRect(color, Offset(w * (c + g), d * (c + g)), Size(c, c), CornerRadius(c * 0.25f))
                }
            }
        }
    }
}

// ---------------- Editor ----------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HabitEditorDialog(
    initial: Habit?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Color, days: Set<DayOfWeek>, goal: Int, reminder: LocalTime?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var colorArgb by rememberSaveable { mutableStateOf((initial?.color ?: ItemColors[2]).toArgb()) }
    var days by remember { mutableStateOf(initial?.days ?: DayOfWeek.entries.toSet()) }
    var goal by rememberSaveable { mutableStateOf(initial?.goal ?: 1) }
    var reminder by remember { mutableStateOf(initial?.reminder) }
    var pickingTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = "Delete \"${initial?.name}\"?",
            body = "Its streaks and check-in history are deleted too.",
            confirm = "Delete habit",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.SurfaceHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text(if (initial == null) "New habit" else "Edit habit", color = Palette.TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Habit") },
                    placeholder = { Text("Read 20 minutes") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                EditorLabel("Days")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Every day", selected = days.size == 7) { days = DayOfWeek.entries.toSet() }
                    DayOfWeek.entries.forEach { d ->
                        Pill(d.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()), selected = d in days && days.size < 7) {
                            days = when {
                                days.size == 7 -> setOf(d)
                                d in days -> (days - d).ifEmpty { setOf(d) }
                                else -> days + d
                            }
                        }
                    }
                }
                EditorLabel("Times a day")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton(Icons.Rounded.Remove, "Fewer") { goal = (goal - 1).coerceAtLeast(1) }
                    Text(
                        "$goal",
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(48.dp),
                    )
                    StepButton(Icons.Rounded.Add, "More") { goal = (goal + 1).coerceAtMost(50) }
                    Spacer(Modifier.width(12.dp))
                    Text(if (goal == 1) "Once is enough" else "Like $goal glasses of water", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
                }
                EditorLabel("Reminder")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(reminder?.format(timeFormat) ?: "Add a reminder", selected = reminder != null, leading = Icons.Rounded.Notifications) { pickingTime = true }
                    if (reminder != null) Pill("Remove", selected = false, leading = Icons.Rounded.Close) { reminder = null }
                }
                EditorLabel("Color")
                ColorSwatches(Color(colorArgb)) { colorArgb = it.toArgb() }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, Color(colorArgb), days, goal, reminder) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = Palette.PriorityHigh) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.TextPrimary) }
            }
        },
    )

    if (pickingTime) {
        val state = rememberTimePickerState(reminder?.hour ?: 20, reminder?.minute ?: 0, DateFormat.is24HourFormat(context))
        TimePickerDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text("Remind me at", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { reminder = LocalTime.of(state.hour, state.minute); pickingTime = false }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel", color = Palette.TextPrimary) } },
        ) { TimePicker(state = state) }
    }
}

@Composable
private fun EditorLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Palette.Surface)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Palette.TextPrimary) }
}
