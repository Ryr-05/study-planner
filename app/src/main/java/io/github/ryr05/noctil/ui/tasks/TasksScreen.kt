package io.github.ryr05.noctil.ui.tasks

import io.github.ryr05.noctil.ui.components.EmptyHint
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.WbTwilight
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
import io.github.ryr05.noctil.ui.components.Segmented
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
    /** Opens quick add with this text already typed (from the examples on empty screens). */
    val onQuickAdd: (String) -> Unit = {},
)

@Composable
fun TasksScreen(
    view: TaskView,
    snapshot: Snapshot,
    today: LocalDate,
    actions: TaskActions,
    onOpenLists: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMe: () -> Unit = {},
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
                Header(view, snapshot, today, onOpenLists, onOpenSearch, onOpenMe)
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
            Header(view, snapshot, today, onOpenLists, onOpenSearch, onOpenMe)
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
            item(key = "empty") {
                val tagName = (view as? TaskView.OfTag)?.let { v -> snapshot.tags.firstOrNull { it.id == v.id }?.name } ?: "tag"
                EmptyState(view, tagName, actions.onQuickAdd)
            }
        }
    }
}

@Composable
private fun LayoutSwitch(layout: TaskLayout, onLayout: (TaskLayout) -> Unit, modifier: Modifier = Modifier) {
    Segmented(TaskLayout.entries.map { it.label }, layout.ordinal, { onLayout(TaskLayout.entries[it]) }, modifier, minHeight = 36.dp)
}

@Composable
private fun Header(
    view: TaskView,
    snapshot: Snapshot,
    today: LocalDate,
    onOpenLists: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMe: () -> Unit,
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
        Spacer(Modifier.width(8.dp))
        io.github.ryr05.noctil.ui.ProfileButton(onOpenMe)
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
private fun EmptyState(view: TaskView, tag: String, onExample: (String) -> Unit) {
    data class Empty(val icon: ImageVector, val title: String, val body: String, val examples: List<String>)
    val e = when (view) {
        TaskView.Today -> Empty(
            Icons.Rounded.WbTwilight, "You're clear for today",
            "Tap + and type a task the way you'd say it. Dates, times and priority are picked up for you.",
            listOf("Essay draft 5pm !1", "Study for quiz tonight", "Lab report tomorrow 9am"),
        )
        TaskView.Next7 -> Empty(
            Icons.Rounded.DateRange, "A quiet week",
            "Tasks due in the next 7 days show up here.",
            listOf("Quiz friday 9am #exam", "Group project mon to fri", "Return books in 3 days"),
        )
        TaskView.High -> Empty(
            Icons.Rounded.Flag, "No high-priority tasks",
            "Add !1 when you type a task to mark it high priority.",
            listOf("Finish essay fri !1", "Pay school fees !urgent"),
        )
        TaskView.Completed -> Empty(
            Icons.Rounded.TaskAlt, "Nothing finished yet",
            "Tap the circle next to a task to finish it. Finished tasks wait here.",
            emptyList(),
        )
        TaskView.All, TaskView.Inbox -> Empty(
            Icons.Rounded.Inbox, if (view == TaskView.All) "No open tasks" else "Inbox is empty",
            "Tap + to add your first task. You can type it all in one line.",
            listOf("Essay draft fri 5pm !1", "Gym every mon and wed 6pm", "Chem midterm dec 4 #exam"),
        )
        is TaskView.OfList -> Empty(
            Icons.Rounded.FolderOpen, "This list is empty",
            "Tasks you add here go straight into this list.",
            listOf("Read chapter 4 tomorrow", "Problem set friday next week 5pm"),
        )
        is TaskView.OfTag -> Empty(
            Icons.Rounded.Sell, "No open tasks with this tag",
            "Type #$tag when adding a task to tag it.",
            listOf("Revise notes fri #$tag"),
        )
    }
    EmptyHint(e.icon, e.title, e.body, examples = e.examples, onExample = onExample)
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
