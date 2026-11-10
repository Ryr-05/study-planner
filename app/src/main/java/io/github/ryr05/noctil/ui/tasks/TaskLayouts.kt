package io.github.ryr05.noctil.ui.tasks

import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.runtime.key
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.Stage
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.ui.components.CheckCircle
import io.github.ryr05.noctil.ui.components.color
import io.github.ryr05.noctil.ui.components.relativeDay
import io.github.ryr05.noctil.ui.components.timeFormat
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

enum class TaskLayout(val label: String) { List("List"), Board("Board"), Matrix("Matrix"), Timeline("Timeline") }

/** Every top-level task a view shows (open and finished), for the board and timeline. */
fun tasksInView(view: TaskView, snap: Snapshot, today: LocalDate): List<TaskItem> =
    sectionsFor(view, snap, today).flatMap { it.tasks }.distinctBy { it.id }

// ---------------- Board ----------------

data class BoardActions(
    val onMove: (TaskItem, stageId: Long?, done: Boolean) -> Unit,
    val onNewStage: () -> Unit,
    val onRenameStage: (Stage) -> Unit,
    val onDeleteStage: (Stage) -> Unit,
)

private data class BoardColumn(val key: String, val title: String, val stageId: Long?, val done: Boolean, val stage: Stage?, val tasks: List<TaskItem>)

/** Kanban board: To Do, your own columns, Done. Long-press a card and drag it to another column. */
@Composable
fun BoardView(
    tasks: List<TaskItem>,
    stages: List<Stage>,
    today: LocalDate,
    taskActions: TaskActions,
    actions: BoardActions,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val known = stages.map { it.id }.toSet()
    val open = tasks.filter { !it.done }
    val columns = buildList {
        add(BoardColumn("todo", "To Do", null, false, null, open.filter { it.stageId == null || it.stageId !in known }.sortedWith(openTaskOrder)))
        stages.sortedBy { it.sortOrder }.forEach { st ->
            add(BoardColumn("s${st.id}", st.name, st.id, false, st, open.filter { it.stageId == st.id }.sortedWith(openTaskOrder)))
        }
        add(BoardColumn("done", "Done", null, true, null, tasks.filter { it.done }.sortedByDescending { it.completedAt ?: 0 }.take(30)))
    }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<TaskItem?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var rootOffset by remember { mutableStateOf(Offset.Zero) }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootOffset = it.boundsInRoot().topLeft }) {
        LazyRow(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(columns, key = { it.key }) { col ->
                val isTarget = dragging != null && bounds[col.key]?.let { pointer.x in it.left..it.right } == true
                Column(
                    Modifier
                        .width(276.dp)
                        .fillMaxHeight()
                        .onGloballyPositioned { bounds[col.key] = it.boundsInRoot() }
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isTarget) Palette.Lavender.copy(alpha = 0.10f) else Palette.Surface.copy(alpha = 0.55f))
                        .border(1.dp, if (isTarget) Palette.Lavender.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(24.dp))
                        .padding(10.dp),
                ) {
                    ColumnHeader(col, actions)
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        col.tasks.forEach { task ->
                            BoardCard(
                                task = task,
                                today = today,
                                hidden = dragging?.id == task.id,
                                onOpen = { taskActions.onOpen(task) },
                                onToggle = { taskActions.onToggle(task) },
                                onDragStart = { start ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragging = task
                                    pointer = start
                                },
                                onDrag = { delta -> pointer += delta },
                                onDragEnd = {
                                    val target = columns.firstOrNull { c -> bounds[c.key]?.let { pointer.x in it.left..it.right } == true }
                                    val moved = dragging
                                    dragging = null
                                    if (target != null && moved != null && !(target.done == moved.done && target.stageId == moved.stageId)) {
                                        actions.onMove(moved, target.stageId, target.done)
                                    }
                                },
                            )
                        }
                        if (col.tasks.isEmpty()) {
                            Text(
                                if (col.done) "Finished tasks land here" else "Drag a card here",
                                style = MaterialTheme.typography.labelMedium,
                                color = Palette.TextTertiary,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
            item(key = "add-column") {
                Box(
                    Modifier
                        .width(160.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, Palette.Outline, RoundedCornerShape(24.dp))
                        .clickable(onClickLabel = "Add a column", onClick = actions.onNewStage),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = Palette.Lavender)
                        Spacer(Modifier.width(6.dp))
                        Text("Add column", style = MaterialTheme.typography.labelLarge, color = Palette.Lavender)
                    }
                }
            }
        }
        dragging?.let { task ->
            val p = pointer - rootOffset
            Box(
                Modifier
                    .offset { IntOffset((p.x - with(density) { 120.dp.toPx() }).roundToInt(), (p.y - with(density) { 30.dp.toPx() }).roundToInt()) }
                    .width(240.dp)
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.SurfaceHigh)
                    .border(1.dp, Palette.Lavender.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
            ) {
                Text(task.title, style = MaterialTheme.typography.bodyMedium, color = Palette.TextPrimary, maxLines = 2)
            }
        }
    }
}

@Composable
private fun ColumnHeader(col: BoardColumn, actions: BoardActions) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(col.title, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1)
        Text("${col.tasks.size}", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
        if (col.stage != null) {
            Box {
                SmallIconButton(Icons.Rounded.MoreHoriz, "Column options") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Palette.SurfaceHigh) {
                    DropdownMenuItem(text = { Text("Rename", color = Palette.TextPrimary) }, onClick = { menu = false; actions.onRenameStage(col.stage) })
                    DropdownMenuItem(text = { Text("Delete column", color = Palette.PriorityHigh) }, onClick = { menu = false; actions.onDeleteStage(col.stage) })
                }
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun BoardCard(
    task: TaskItem,
    today: LocalDate,
    hidden: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val start by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val overdue = !task.done && task.date != null && task.date.isBefore(today)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .clip(RoundedCornerShape(16.dp))
            .background(if (hidden) Palette.Surface.copy(alpha = 0.3f) else Palette.Surface)
            .clickable(onClickLabel = "Open task", onClick = onOpen)
            .pointerInput(task.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> start(origin + offset) },
                    onDrag = { change, amount ->
                        change.consume()
                        drag(amount)
                    },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                )
            }
            .semantics { contentDescription = "${task.title}. Long-press and drag to move to another column." }
            .padding(start = 2.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            CheckCircle(task.done, task.priority.color(), task.title, onToggle, size = 18.dp)
        }
        Column(Modifier.weight(1f).padding(top = 10.dp, bottom = 4.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.done) Palette.TextTertiary else Palette.TextPrimary,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(task.date?.let { relativeDay(it, today) }, task.time?.format(timeFormat), task.list?.name).joinToString(", ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.labelSmall, color = if (overdue) Palette.PriorityHigh else Palette.TextSecondary, maxLines = 1)
            }
        }
        if (!task.done && (task.priority == Priority.High || task.priority == Priority.Medium)) {
            Icon(Icons.Rounded.Flag, contentDescription = null, tint = task.priority.color(), modifier = Modifier.padding(top = 10.dp).size(14.dp))
        }
    }
}

// ---------------- Eisenhower Matrix ----------------

private data class Quadrant(val title: String, val hint: String, val color: Color, val tasks: List<TaskItem>)

/** Sorts open tasks automatically. Urgent: due within 2 days or overdue. Important: high or medium priority. */
fun quadrantOf(task: TaskItem, today: LocalDate): Int {
    val urgent = task.date != null && !task.date.isAfter(today.plusDays(2))
    val important = task.priority == Priority.High || task.priority == Priority.Medium
    return when {
        urgent && important -> 0
        important -> 1
        urgent -> 2
        else -> 3
    }
}

@Composable
fun MatrixView(tasks: List<TaskItem>, today: LocalDate, taskActions: TaskActions, bottomPadding: androidx.compose.ui.unit.Dp) {
    val open = tasks.filter { !it.done }.sortedWith(openTaskOrder)
    val groups = open.groupBy { quadrantOf(it, today) }
    val quads = listOf(
        Quadrant("Do now", "Urgent and important", Palette.PriorityHigh, groups[0].orEmpty()),
        Quadrant("Plan it", "Important, not urgent", Palette.Lavender, groups[1].orEmpty()),
        Quadrant("Fit it in", "Urgent, less important", Palette.PriorityMedium, groups[2].orEmpty()),
        Quadrant("Later", "Neither, or no date", Palette.TextTertiary, groups[3].orEmpty()),
    )
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = bottomPadding)) {
        Text(
            "Urgent means due within 2 days. Important means high or medium priority.",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        if (LocalDensity.current.fontScale > 1.3f) {
            // Big text: one box per row, so titles have room. The whole page scrolls.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                quads.forEach { QuadrantBox(it, today, taskActions, Modifier.fillMaxWidth(), stacked = true) }
            }
        } else {
            for (row in 0..1) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (col in 0..1) {
                        QuadrantBox(quads[row * 2 + col], today, taskActions, Modifier.weight(1f).fillMaxHeight())
                    }
                }
                if (row == 0) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun QuadrantBox(q: Quadrant, today: LocalDate, taskActions: TaskActions, modifier: Modifier, stacked: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface.copy(alpha = 0.7f))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(q.color))
            Spacer(Modifier.width(8.dp))
            Text(q.title, style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
            Text("${q.tasks.size}", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
        }
        Text(q.hint, style = MaterialTheme.typography.labelSmall, color = Palette.TextTertiary, modifier = Modifier.padding(start = 22.dp, bottom = 6.dp))
        val row: @Composable (TaskItem) -> Unit = { task ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Open task") { taskActions.onOpen(task) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        CheckCircle(task.done, task.priority.color(), task.title, { taskActions.onToggle(task) }, size = 16.dp)
                    }
                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(task.title, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Palette.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        task.date?.let {
                            Text(
                                relativeDay(it, today),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (it.isBefore(today)) Palette.PriorityHigh else Palette.TextSecondary,
                            )
                        }
                    }
                }
        }
        if (stacked) {
            if (q.tasks.isEmpty()) {
                Text("Nothing here", style = MaterialTheme.typography.labelMedium, color = Palette.TextTertiary, modifier = Modifier.padding(start = 22.dp, bottom = 4.dp))
            }
            q.tasks.forEach { key(it.id) { row(it) } }
        } else {
            LazyColumn { items(q.tasks, key = { it.id }) { row(it) } }
        }
    }
}

// ---------------- Timeline ----------------

private val dayWidth = 40.dp
private val rowHeight = 44.dp
private val titleWidth = 132.dp

/** Gantt-style timeline: a bar from each task's start date to its due date, or a dot on the due day. */
@Composable
fun TimelineView(tasks: List<TaskItem>, today: LocalDate, taskActions: TaskActions, bottomPadding: androidx.compose.ui.unit.Dp) {
    io.github.ryr05.noctil.ui.CappedText(1.2f) { TimelineViewBody(tasks = tasks, today = today, taskActions = taskActions, bottomPadding = bottomPadding) }
}

@Composable
private fun TimelineViewBody(tasks: List<TaskItem>, today: LocalDate, taskActions: TaskActions, bottomPadding: androidx.compose.ui.unit.Dp) {
    val dated = tasks.filter { !it.done && it.date != null }
        .sortedWith(compareBy({ it.startDate ?: it.date }, { it.date }))
    if (dated.isEmpty()) {
        io.github.ryr05.noctil.ui.components.EmptyHint(
            icon = androidx.compose.material.icons.Icons.Rounded.ViewTimeline,
            title = "Nothing on the timeline",
            body = "Tasks with a due date show here. Type a day range to see one as a bar.",
            modifier = Modifier.padding(horizontal = 16.dp),
            examples = listOf("Group project mon to fri", "Essay draft friday next week"),
            onExample = taskActions.onQuickAdd,
        )
        return
    }
    val first = minOf(today.minusDays(2), dated.minOf { it.startDate ?: it.date!! })
    val last = maxOf(first.plusDays(20), dated.maxOf { it.date!! }.plusDays(3)).let { if (ChronoUnit.DAYS.between(first, it) > 120) first.plusDays(120) else it }
    val days = ChronoUnit.DAYS.between(first, last).toInt() + 1
    val hScroll = rememberScrollState(with(LocalDensity.current) { (dayWidth * ChronoUnit.DAYS.between(first, today).toInt().coerceAtLeast(0) - 8.dp).roundToPx().coerceAtLeast(0) })
    val locale = Locale.getDefault()
    val monthDay = DateTimeFormatter.ofPattern("MMM d")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomPadding)) {
        Row {
            Column(Modifier.width(titleWidth)) {
                Spacer(Modifier.height(40.dp))
                dated.forEach { task ->
                    Box(
                        Modifier
                            .height(rowHeight)
                            .fillMaxWidth()
                            .clickable(onClickLabel = "Open task") { taskActions.onOpen(task) }
                            .padding(start = 20.dp, end = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(task.title, style = MaterialTheme.typography.bodySmall, color = Palette.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(Modifier.horizontalScroll(hScroll)) {
                Column(Modifier.width(dayWidth * days)) {
                    Row(Modifier.height(40.dp)) {
                        for (i in 0 until days) {
                            val d = first.plusDays(i.toLong())
                            Column(Modifier.width(dayWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (d.dayOfMonth == 1 || i == 0) d.format(DateTimeFormatter.ofPattern("MMM")) else d.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, locale),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = Palette.TextTertiary,
                                )
                                Box(
                                    Modifier.size(24.dp).clip(CircleShape).background(if (d == today) Palette.Lavender else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        d.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (d == today) Palette.OnLavender else Palette.TextSecondary,
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        Modifier
                            .width(dayWidth * days)
                            .height(rowHeight * dated.size)
                            .drawBehind {
                                val w = dayWidth.toPx()
                                for (i in 0 until days) {
                                    val d = first.plusDays(i.toLong())
                                    if (d.dayOfWeek.value >= 6) drawRect(Palette.Surface.copy(alpha = 0.35f), Offset(i * w, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                                }
                                val todayX = ChronoUnit.DAYS.between(first, today) * w + w / 2
                                drawLine(Palette.PriorityHigh.copy(alpha = 0.8f), Offset(todayX, 0f), Offset(todayX, size.height), 2.dp.toPx())
                            },
                    ) {
                        dated.forEachIndexed { row, task ->
                            val due = task.date!!
                            val start = (task.startDate?.takeIf { !it.isAfter(due) }) ?: due
                            val startIdx = ChronoUnit.DAYS.between(first, start).toInt().coerceAtLeast(0)
                            val endIdx = ChronoUnit.DAYS.between(first, due).toInt().coerceAtMost(days - 1)
                            val color = if (due.isBefore(today)) Palette.PriorityHigh else task.list?.color ?: Palette.Lavender
                            val isBar = task.startDate != null && start.isBefore(due)
                            Box(
                                Modifier
                                    .offset(x = dayWidth * startIdx + 4.dp, y = rowHeight * row + 10.dp)
                                    .width(if (isBar) dayWidth * (endIdx - startIdx + 1) - 8.dp else 24.dp)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(color.copy(alpha = if (isBar) 0.35f else 1f))
                                    .border(1.dp, color, RoundedCornerShape(12.dp))
                                    .clickable(onClickLabel = "Open ${task.title}") { taskActions.onOpen(task) }
                                    .semantics {
                                        contentDescription = if (isBar) "${task.title}, ${start.format(monthDay)} to ${due.format(monthDay)}" else "${task.title}, due ${due.format(monthDay)}"
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
