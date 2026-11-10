package io.github.ryr05.noctil.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.ui.theme.Palette
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

val timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d")

fun Priority.color(): Color = when (this) {
    Priority.High -> Palette.PriorityHigh
    Priority.Medium -> Palette.PriorityMedium
    Priority.Low -> Palette.PriorityLow
    Priority.None -> Palette.TextTertiary
}

fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(dayFormat)
}

/**
 * One task in a list. Tap to open, tap the circle to finish.
 * Swipe right to finish, swipe left to move it to tomorrow.
 */
@Composable
fun TaskRow(
    task: TaskItem,
    index: Int,
    count: Int,
    today: LocalDate,
    showDate: Boolean,
    onToggle: (TaskItem) -> Unit,
    onOpen: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
    showList: Boolean = true,
    onMoveToTomorrow: ((TaskItem) -> Unit)? = null,
    /** Non-null while picking several tasks. Tap toggles; long-press starts picking. */
    selection: Set<Long>? = null,
    onSelect: ((TaskItem) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val shape = groupShape(index, count)
    val swipeEnabled = !task.done && onMoveToTomorrow != null && selection == null

    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val currentTask by rememberUpdatedState(task)
    val currentToggle by rememberUpdatedState(onToggle)
    val currentTomorrow by rememberUpdatedState(onMoveToTomorrow)
    // Kept stable so the swipe action runs once per swipe, even if the row redraws mid-swipe.
    val onDismiss: (SwipeToDismissBoxValue) -> Unit = remember(task.id) {
        { value ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentToggle(currentTask)
                SwipeToDismissBoxValue.EndToStart -> currentTomorrow?.invoke(currentTask)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // Spring back into place; the list then moves the task where it belongs.
            scope.launch { state.reset() }
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier.clip(shape),
        enableDismissFromStartToEnd = swipeEnabled,
        enableDismissFromEndToStart = swipeEnabled,
        onDismiss = onDismiss,
        backgroundContent = {
            val direction = state.dismissDirection
            val (color, icon, label, alignEnd) = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> SwipeLook(Palette.PriorityMedium, Icons.Rounded.WbTwilight, "Tomorrow", true)
                else -> SwipeLook(Palette.Lavender, Icons.Rounded.Check, "Done", false)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color.copy(alpha = 0.22f))
                    .padding(horizontal = 20.dp),
                contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = color)
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
        },
    ) {
        RowContent(task, index, today, showDate, showList, onToggle, onOpen, onMoveToTomorrow, selection, onSelect)
    }
}

private data class SwipeLook(val color: Color, val icon: ImageVector, val label: String, val alignEnd: Boolean)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun RowContent(
    task: TaskItem,
    index: Int,
    today: LocalDate,
    showDate: Boolean,
    showList: Boolean,
    onToggle: (TaskItem) -> Unit,
    onOpen: (TaskItem) -> Unit,
    onMoveToTomorrow: ((TaskItem) -> Unit)?,
    selection: Set<Long>?,
    onSelect: ((TaskItem) -> Unit)?,
) {
    val selecting = selection != null
    val selected = selection?.contains(task.id) == true
    val haptics = LocalHapticFeedback.current
    val overdue = !task.done && task.date != null && task.date.isBefore(today)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Palette.Lavender.copy(alpha = 0.16f) else Palette.Surface)
            .drawBehind {
                if (index > 0) {
                    drawLine(
                        color = Palette.Hairline,
                        start = Offset(56.dp.toPx(), 0f),
                        end = Offset(size.width - 16.dp.toPx(), 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .combinedClickable(
                onClickLabel = if (selecting) "Select" else "Open task",
                onLongClickLabel = "Select several",
                onLongClick = onSelect?.let { pick -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); pick(task) } },
                onClick = { if (selecting) onSelect?.invoke(task) else onOpen(task) },
            )
            .semantics {
                if (onMoveToTomorrow != null && !task.done) {
                    customActions = listOf(
                        CustomAccessibilityAction("Move to tomorrow") { onMoveToTomorrow(task); true },
                    )
                }
            }
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                        .background(if (selected) Palette.Lavender else Color.Transparent)
                        .border(2.dp, if (selected) Palette.Lavender else Palette.TextTertiary, androidx.compose.foundation.shape.RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Palette.OnLavender, modifier = Modifier.size(15.dp))
                }
            }
        } else {
            CheckCircle(
                checked = task.done,
                ringColor = task.priority.color(),
                label = task.title,
                onToggle = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(task)
                },
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.done) Palette.TextTertiary else Palette.TextPrimary,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val whenText = buildList {
                if (showDate && task.date != null) add(relativeDay(task.date, today))
                task.time?.let { add(it.format(timeFormat)) }
            }.joinToString(", ")
            val hasMeta = whenText.isNotEmpty() || task.recurrence != null || (showList && task.list != null) ||
                task.tags.isNotEmpty() || task.subtaskTotal > 0 || task.checklistTotal > 0
            if (hasMeta) {
                FlowRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (whenText.isNotEmpty()) {
                        Meta(Icons.Rounded.Schedule, whenText, if (overdue) Palette.PriorityHigh else Palette.TextSecondary)
                    }
                    if (task.recurrence != null) Meta(Icons.Rounded.EventRepeat, null, Palette.TextSecondary)
                    if (task.subtaskTotal > 0) {
                        Meta(Icons.Rounded.AccountTree, "${task.subtaskDone}/${task.subtaskTotal}", Palette.TextSecondary)
                    }
                    if (task.checklistTotal > 0) {
                        Meta(Icons.Rounded.Checklist, "${task.checklistDone}/${task.checklistTotal}", Palette.TextSecondary)
                    }
                    if (showList) task.list?.let { ColorTag(it.name, it.color) }
                    task.tags.forEach { ColorTag("#${it.name}", it.color) }
                }
            }
        }
        if (!task.done && (task.priority == Priority.High || task.priority == Priority.Medium)) {
            Icon(
                Icons.Rounded.Flag,
                contentDescription = "${task.priority.label} priority",
                tint = task.priority.color(),
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
            )
        }
    }
}

@Composable
private fun Meta(icon: ImageVector, text: String?, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        if (text != null) {
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
fun CheckCircle(
    checked: Boolean,
    ringColor: Color,
    label: String,
    onToggle: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val fill by animateColorAsState(
        targetValue = if (checked) Palette.Lavender else Color.Transparent,
        label = "checkFill",
    )
    val pop by animateFloatAsState(
        targetValue = if (checked) 1f else 0.6f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "checkPop",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = if (checked) "Mark $label not done" else "Mark $label done" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, if (checked) Palette.Lavender else ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Palette.OnLavender,
                    modifier = Modifier.size(size * 0.66f).scale(pop),
                )
            }
        }
    }
}
