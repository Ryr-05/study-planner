package io.github.ryr05.noctil.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.data.Priority
import io.github.ryr05.noctil.data.Task
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
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

/** A titled inset group of tasks. Rows animate when they move between groups. */
fun LazyListScope.taskSection(
    key: String,
    title: String,
    tasks: List<Task>,
    today: LocalDate,
    showDate: Boolean,
    onToggle: (Int) -> Unit,
    titleColor: Color = Palette.TextSecondary,
) {
    if (tasks.isEmpty()) return
    item(key = "header-$key") {
        SectionLabel(title, color = titleColor, modifier = Modifier.animateItem())
    }
    itemsIndexed(tasks, key = { _, task -> "task-${task.id}" }) { index, task ->
        TaskRow(
            task = task,
            index = index,
            count = tasks.size,
            today = today,
            showDate = showDate,
            onToggle = onToggle,
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
fun TaskRow(
    task: Task,
    index: Int,
    count: Int,
    today: LocalDate,
    showDate: Boolean,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(groupShape(index, count))
            .background(Palette.Surface)
            .drawBehind {
                if (index > 0) {
                    drawLine(
                        color = Palette.Hairline,
                        start = Offset(64.dp.toPx(), 0f),
                        end = Offset(size.width - 16.dp.toPx(), 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(
            checked = task.done,
            ringColor = task.priority.color(),
            label = task.title,
            onToggle = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle(task.id)
            },
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.done) Palette.TextTertiary else Palette.TextPrimary,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val whenText = buildList {
                if (showDate) add(relativeDay(task.date, today))
                task.time?.let { add(it.format(timeFormat)) }
            }.joinToString(", ")
            if (whenText.isNotEmpty() || task.subject != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (whenText.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = Palette.TextSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = whenText,
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.TextSecondary,
                            )
                        }
                    }
                    task.subject?.let { SubjectTag(it) }
                }
            }
        }
        if (!task.done && (task.priority == Priority.High || task.priority == Priority.Medium)) {
            Icon(
                Icons.Rounded.Flag,
                contentDescription = "${task.priority.name} priority",
                tint = task.priority.color(),
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
            )
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean, ringColor: Color, label: String, onToggle: () -> Unit) {
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
                .size(24.dp)
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
                    modifier = Modifier.size(16.dp).scale(pop),
                )
            }
        }
    }
}
