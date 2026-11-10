package io.github.ryr05.noctil.ui.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.ui.components.RoundIconButton
import io.github.ryr05.noctil.ui.components.SectionLabel
import io.github.ryr05.noctil.ui.components.TaskRow
import io.github.ryr05.noctil.ui.components.bottomContentPadding
import io.github.ryr05.noctil.ui.components.topContentPadding
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val headerDate = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val shortDate = DateTimeFormatter.ofPattern("EEE, MMM d")

data class TaskActions(
    val onToggle: (TaskItem) -> Unit,
    val onOpen: (TaskItem) -> Unit,
    val onMoveToTomorrow: (TaskItem) -> Unit,
    /** Long-press or tap while picking: adds or removes a task from the selection. */
    val onSelect: ((TaskItem) -> Unit)? = null,
)

@Composable
fun TasksScreen(
    view: TaskView,
    snapshot: Snapshot,
    today: LocalDate,
    actions: TaskActions,
    onOpenLists: () -> Unit,
    onOpenSearch: () -> Unit,
    layout: TaskLayout = TaskLayout.List,
    onLayout: (TaskLayout) -> Unit = {},
    selection: Set<Long>? = null,
    boardActions: BoardActions? = null,
) {
    val sections = sectionsFor(view, snapshot, today)
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val showList = view !is TaskView.OfList

    if (layout != TaskLayout.List) {
        Column(Modifier.fillMaxSize().padding(top = topContentPadding())) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Header(view, snapshot, today, onOpenLists, onOpenSearch)
                LayoutSwitch(layout, onLayout, Modifier.padding(top = 14.dp, bottom = 12.dp))
            }
            val tasks = tasksInView(view, snapshot, today)
            when (layout) {
                TaskLayout.Board -> BoardView(
                    tasks = tasks,
                    stages = snapshot.stages,
                    today = today,
                    taskActions = actions,
                    actions = boardActions ?: BoardActions({ _, _, _ -> }, {}, {}, {}),
                    bottomPadding = bottomContentPadding(),
                )
                TaskLayout.Matrix -> MatrixView(tasks, today, actions, bottomContentPadding())
                TaskLayout.Timeline -> TimelineView(tasks, today, actions, bottomContentPadding())
                TaskLayout.List -> Unit
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding(),
            bottom = bottomContentPadding(),
        ),
    ) {
        item(key = "header") {
            Header(view, snapshot, today, onOpenLists, onOpenSearch)
        }
        item(key = "layouts") {
            LayoutSwitch(layout, onLayout, Modifier.padding(top = 14.dp))
        }
        if (view == TaskView.Today && selection == null) {
            nextExam(snapshot, today)?.let { exam ->
                item(key = "exam") {
                    ExamCard(exam, today, Modifier.padding(top = 20.dp)) { actions.onOpen(exam) }
                }
            }
        }

        sections.forEach { section ->
            val isOpen = !section.collapsible || section.key in expanded
            renderSection(
                section = section,
                isOpen = isOpen,
                onToggleOpen = {
                    expanded = if (section.key in expanded) expanded - section.key else expanded + section.key
                },
                today = today,
                showList = showList,
                actions = actions,
                selection = selection,
            )
        }

        if (snapshot.loaded && sections.none { !it.collapsible }) {
            item(key = "empty") { EmptyState(view) }
        }
    }
}

@Composable
private fun LayoutSwitch(layout: TaskLayout, onLayout: (TaskLayout) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Palette.Surface).padding(4.dp)) {
        TaskLayout.entries.forEach { l ->
            val selected = l == layout
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Palette.SurfaceHigh else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(onClickLabel = "Show as ${l.label}", role = Role.Tab) { onLayout(l) },
                contentAlignment = Alignment.Center,
            ) {
                Text(l.label, style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.TextPrimary else Palette.TextSecondary)
            }
        }
    }
}

@Composable
private fun Header(
    view: TaskView,
    snapshot: Snapshot,
    today: LocalDate,
    onOpenLists: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val count = openCount(view, snapshot, today)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (view == TaskView.Today) today.format(headerDate) else countText(count),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.TextSecondary,
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "Show lists", role = Role.Button, onClick = onOpenLists)
                    .semantics { contentDescription = "${viewTitle(view, snapshot)}. Show lists" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = viewTitle(view, snapshot),
                    style = MaterialTheme.typography.displaySmall,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = Palette.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp).size(28.dp),
                )
            }
            if (view == TaskView.Today) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = todaySummary(snapshot, today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                )
            }
        }
        RoundIconButton(Icons.Rounded.Search, "Search tasks", onClick = onOpenSearch)
    }
}

private fun countText(count: Int) = when (count) {
    0 -> "Nothing open"
    1 -> "1 open task"
    else -> "$count open tasks"
}

private fun todaySummary(snapshot: Snapshot, today: LocalDate): String {
    val open = snapshot.tasks.filter { it.parentId == null && !it.done && it.date != null }
    val dueToday = open.count { it.date == today }
    val overdue = open.count { it.date!!.isBefore(today) }
    val left = when (dueToday) {
        0 -> "Nothing else due today"
        1 -> "1 task left today"
        else -> "$dueToday tasks left today"
    }
    return if (overdue > 0) "$left, $overdue overdue" else left
}

private fun LazyListScope.renderSection(
    section: Section,
    isOpen: Boolean,
    onToggleOpen: () -> Unit,
    today: LocalDate,
    showList: Boolean,
    actions: TaskActions,
    selection: Set<Long>?,
) {
    item(key = "header-${section.key}") {
        val color = if (section.tone == SectionTone.Danger) Palette.PriorityHigh else Palette.TextSecondary
        if (section.collapsible) {
            val rotation by animateFloatAsState(if (isOpen) 0f else -90f, label = "chevron")
            Row(
                modifier = Modifier
                    .animateItem()
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = if (isOpen) "Hide" else "Show", onClick = onToggleOpen)
                    .padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${section.title}  ${section.tasks.size}", style = MaterialTheme.typography.labelLarge, color = color)
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.padding(start = 2.dp).size(18.dp).rotate(rotation),
                )
            }
        } else {
            SectionLabel(section.title, color = color, modifier = Modifier.animateItem())
        }
    }
    if (isOpen) {
        itemsIndexed(section.tasks, key = { _, t -> "${section.key}-${t.id}" }) { index, task ->
            TaskRow(
                task = task,
                index = index,
                count = section.tasks.size,
                today = today,
                showDate = section.showDate,
                showList = showList,
                onToggle = actions.onToggle,
                onOpen = actions.onOpen,
                onMoveToTomorrow = actions.onMoveToTomorrow,
                modifier = Modifier.animateItem(),
                selection = selection,
                onSelect = actions.onSelect,
            )
        }
    }
}

@Composable
private fun EmptyState(view: TaskView) {
    val (title, body) = when (view) {
        TaskView.Today -> "You're clear for today" to "Tap + to add a task. Try \"essay fri 5pm !1\"."
        TaskView.Next7 -> "A quiet week" to "Tasks with dates in the next 7 days show up here."
        TaskView.High -> "No high-priority tasks" to "Add !1 when you type a task to mark it high priority."
        TaskView.Completed -> "Nothing finished yet" to "Tasks you finish are kept here."
        TaskView.All -> "No open tasks" to "Tap + to add your first task."
        TaskView.Inbox -> "Inbox is empty" to "Tasks without a list land here."
        is TaskView.OfList -> "This list is empty" to "Tap + to add a task to this list."
        is TaskView.OfTag -> "No open tasks with this tag" to "Type #tagname when adding a task to tag it."
    }
    Column(Modifier.padding(top = 40.dp, start = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
    }
}

@Composable
private fun ExamCard(exam: TaskItem, today: LocalDate, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val date = exam.date ?: return
    val days = ChronoUnit.DAYS.between(today, date)
    val accent = exam.list?.color ?: Palette.Lavender
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface)
            .clickable(onClickLabel = "Open exam", onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.School, contentDescription = null, tint = accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Next exam", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
            Text(exam.title, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary, maxLines = 2)
            Text(date.format(shortDate), style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
            Text(
                text = if (days == 0L) "Today" else days.toString(),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Palette.Lavender,
            )
            if (days > 0) {
                Text(
                    text = if (days == 1L) "day left" else "days left",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                )
            }
        }
    }
}
