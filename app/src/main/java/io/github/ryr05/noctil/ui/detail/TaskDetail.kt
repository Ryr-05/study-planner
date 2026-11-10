package io.github.ryr05.noctil.ui.detail

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.data.Repository
import io.github.ryr05.noctil.data.model.ChecklistItem
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.Recurrence
import io.github.ryr05.noctil.data.model.Recurrence.Freq
import io.github.ryr05.noctil.data.model.Reminders
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.data.toMinutes
import io.github.ryr05.noctil.parse.SmartParser
import io.github.ryr05.noctil.ui.components.CheckCircle
import io.github.ryr05.noctil.ui.components.ColorTag
import io.github.ryr05.noctil.ui.components.Pill
import io.github.ryr05.noctil.ui.components.color
import io.github.ryr05.noctil.ui.components.relativeDay
import io.github.ryr05.noctil.ui.components.timeFormat
import io.github.ryr05.noctil.ui.glass.GlassDefaults
import io.github.ryr05.noctil.ui.glass.GlassSurface
import io.github.ryr05.noctil.ui.tasks.SmallIconButton
import io.github.ryr05.noctil.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val createdFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

data class DetailActions(
    val onClose: () -> Unit,
    val onBack: () -> Unit,
    val onOpenSubtask: (Long) -> Unit,
    val onToggle: (TaskItem) -> Unit,
    val onDelete: (TaskItem) -> Unit,
    val onReminderSet: () -> Unit,
)

/**
 * Full task editor in a sheet. Every change saves straight away, so there is no Save button.
 * Subtasks open in the same sheet, with a back arrow to return to the parent.
 */
@Composable
fun TaskDetailSheet(
    taskId: Long?,
    hasParentInStack: Boolean,
    snapshot: Snapshot,
    today: LocalDate,
    repo: Repository,
    actions: DetailActions,
) {
    val visible = taskId != null
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Palette.Shadow.copy(alpha = 0.6f))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = actions.onClose),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 420f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // Remember the last task so the sheet can animate out after closing.
            var lastId by remember { mutableStateOf(taskId) }
            if (taskId != null) lastId = taskId
            val task = lastId?.let { snapshot.byId[it] }
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.94f)
                    .statusBarsPadding()
                    .imePadding(),
            ) {
                GlassSurface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    tintAlpha = GlassDefaults.THICK,
                ) {
                    if (task != null) {
                        DetailContent(task, hasParentInStack, snapshot, today, repo, actions)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    task: TaskItem,
    hasParentInStack: Boolean,
    snapshot: Snapshot,
    today: LocalDate,
    repo: Repository,
    actions: DetailActions,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val parent = task.parentId?.let { snapshot.byId[it] }

    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
    ) {
        item(key = "grabber") {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Palette.Outline))
            }
        }
        item(key = "topbar") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasParentInStack || parent != null) {
                    SmallIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to ${parent?.title ?: "parent task"}") {
                        if (hasParentInStack) actions.onBack() else parent?.let { actions.onOpenSubtask(it.id) }
                    }
                    Text(
                        text = parent?.let { "Subtask of ${it.title}" } ?: "Subtask",
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                SmallIconButton(Icons.Rounded.Close, "Close", onClick = actions.onClose)
            }
        }

        item(key = "title-${task.id}") {
            Row(verticalAlignment = Alignment.Top) {
                CheckCircle(
                    checked = task.done,
                    ringColor = task.priority.color(),
                    label = task.title,
                    onToggle = { actions.onToggle(task) },
                    size = 26.dp,
                )
                Spacer(Modifier.width(4.dp))
                AutoSaveField(
                    initial = task.title,
                    key = task.id,
                    placeholder = "Task name",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = if (task.done) Palette.TextTertiary else Palette.TextPrimary,
                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    ),
                    singleLine = false,
                    modifier = Modifier.weight(1f).padding(top = 10.dp),
                ) { value -> if (value.isNotBlank()) repo.update(task.id) { it.copy(title = value.trim()) } }
            }
        }

        item(key = "props") {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PropertyRow(
                    icon = Icons.Rounded.CalendarMonth,
                    label = "Date",
                    value = task.date?.let { relativeDay(it, today) } ?: "No date",
                    active = task.date != null,
                    onClick = { showDate = true },
                    onClear = if (task.date != null) ({ repo.setDate(task.id, null) }) else null,
                )
                PropertyRow(
                    icon = Icons.Rounded.Schedule,
                    label = "Time",
                    value = task.time?.format(timeFormat) ?: "No time",
                    active = task.time != null,
                    onClick = { showTime = true },
                    onClear = if (task.time != null) ({ repo.update(task.id) { it.copy(dueTime = null) } }) else null,
                )
                if (task.time != null) DurationRow(task, repo)
                RepeatRow(task, repo, today)
                ReminderRow(task, repo, actions.onReminderSet)
                ListRow(task, snapshot, repo)
            }
        }

        item(key = "priority") {
            SectionTitle("Priority")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Priority.None, Priority.Low, Priority.Medium, Priority.High).forEach { p ->
                    Pill(
                        text = p.label,
                        selected = task.priority == p,
                        color = if (p == Priority.None) Palette.Lavender else p.color(),
                        leading = if (p == Priority.None) null else Icons.Rounded.Flag,
                    ) { repo.update(task.id) { it.copy(priority = p.value) } }
                }
            }
        }

        item(key = "tags") {
            SectionTitle("Tags")
            TagEditor(task, snapshot, repo)
        }

        item(key = "notes") {
            SectionTitle("Notes")
            AutoSaveField(
                initial = task.notes,
                key = task.id,
                placeholder = "Add notes, links or details",
                style = MaterialTheme.typography.bodyLarge.copy(color = Palette.TextPrimary),
                singleLine = false,
                boxed = true,
                modifier = Modifier.fillMaxWidth(),
            ) { value -> repo.update(task.id) { it.copy(notes = value) } }
        }

        item(key = "checklist") {
            SectionTitle("Checklist")
            ChecklistEditor(task.id, snapshot.checklist[task.id].orEmpty(), repo)
        }

        item(key = "subtasks") {
            SectionTitle("Subtasks")
            SubtaskEditor(task, snapshot, today, repo, actions)
        }

        item(key = "footer") {
            Row(Modifier.padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Created ${Instant.ofEpochMilli(task.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(createdFormat)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextTertiary,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { actions.onDelete(task) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Palette.PriorityHigh, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete task", style = MaterialTheme.typography.labelLarge, color = Palette.PriorityHigh)
                }
            }
        }
    }

    if (showDate) {
        DateDialog(
            initial = task.date ?: today,
            onDismiss = { showDate = false },
            onPick = { date ->
                showDate = false
                repo.setDate(task.id, date)
                actions.onReminderSet()
            },
        )
    }
    if (showTime) {
        TimeDialog(
            initial = task.time ?: LocalTime.of(17, 0),
            onDismiss = { showTime = false },
            onPick = { time ->
                showTime = false
                repo.update(task.id) { t ->
                    val date = t.dueDate ?: today.toEpochDay()
                    t.copy(dueTime = time.toMinutes(), dueDate = date, reminder = t.reminder ?: 0)
                }
                actions.onReminderSet()
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Palette.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 10.dp),
    )
}

@Composable
private fun PropertyRow(
    icon: ImageVector,
    label: String,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface.copy(alpha = 0.7f))
            .clickable(onClickLabel = "Change $label", role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 4.dp)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Palette.Lavender else Palette.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary, modifier = Modifier.width(84.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (active) Palette.TextPrimary else Palette.TextTertiary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (onClear != null) {
            SmallIconButton(Icons.Rounded.Close, "Clear $label", onClick = onClear)
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun DurationRow(task: TaskItem, repo: Repository) {
    var open by remember { mutableStateOf(false) }
    val options = listOf(15, 30, 45, 60, 90, 120, 180)
    fun label(m: Int) = when {
        m < 60 -> "$m min"
        m % 60 == 0 -> if (m == 60) "1 hour" else "${m / 60} hours"
        else -> "${m / 60} h ${m % 60} min"
    }
    Box {
        PropertyRow(
            icon = Icons.Rounded.HourglassEmpty,
            label = "Length",
            value = label(task.blockMinutes),
            active = task.durationMinutes != null,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.SurfaceHigh) {
            options.forEach { m ->
                DropdownMenuItem(
                    text = { Text(label(m), color = Palette.TextPrimary) },
                    onClick = {
                        open = false
                        repo.setDuration(task.id, m)
                    },
                )
            }
        }
    }
}

@Composable
private fun RepeatRow(task: TaskItem, repo: Repository, today: LocalDate) {
    var open by remember { mutableStateOf(false) }
    val base = task.date ?: today
    val dayName = base.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.ENGLISH)
    val options = listOf(
        "Never" to null,
        "Every day" to Recurrence(Freq.Daily),
        "Every weekday" to Recurrence(Freq.Weekly, 1, Recurrence.WEEKDAYS),
        "Every $dayName" to Recurrence(Freq.Weekly, 1, setOf(base.dayOfWeek)),
        "Every other $dayName" to Recurrence(Freq.Weekly, 2, setOf(base.dayOfWeek)),
        "Every month" to Recurrence(Freq.Monthly),
        "Every year" to Recurrence(Freq.Yearly),
    )
    Box {
        PropertyRow(
            icon = Icons.Rounded.EventRepeat,
            label = "Repeat",
            value = task.recurrence?.label() ?: "Never",
            active = task.recurrence != null,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.SurfaceHigh) {
            options.forEach { (label, rec) ->
                DropdownMenuItem(
                    text = { Text(label, color = Palette.TextPrimary) },
                    onClick = {
                        open = false
                        repo.update(task.id) { t ->
                            val date = when {
                                rec == null -> t.dueDate
                                t.dueDate != null -> t.dueDate
                                else -> rec.firstOnOrAfter(today).toEpochDay()
                            }
                            t.copy(recurrence = rec?.encode(), dueDate = date, reminder = t.reminder ?: if (date != null) 0 else null)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ReminderRow(task: TaskItem, repo: Repository, onReminderSet: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val hasTime = task.time != null
    Box {
        PropertyRow(
            icon = Icons.Rounded.Notifications,
            label = "Remind",
            value = if (task.date == null) "Add a date first" else Reminders.label(task.reminder, hasTime),
            active = task.date != null && task.reminder != null,
            onClick = { if (task.date != null) open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.SurfaceHigh) {
            val choices = listOf<Int?>(null) + Reminders.options.filter { hasTime || it == 0 || it == 1440 }
            choices.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(Reminders.label(minutes, hasTime), color = Palette.TextPrimary) },
                    onClick = {
                        open = false
                        repo.update(task.id) { it.copy(reminder = minutes) }
                        if (minutes != null) onReminderSet()
                    },
                )
            }
        }
    }
}

@Composable
private fun ListRow(task: TaskItem, snapshot: Snapshot, repo: Repository) {
    var open by remember { mutableStateOf(false) }
    Box {
        PropertyRow(
            icon = Icons.Rounded.FolderOpen,
            label = "List",
            value = task.list?.name ?: "Inbox",
            active = task.list != null,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.SurfaceHigh) {
            DropdownMenuItem(
                text = { Text("Inbox", color = Palette.TextPrimary) },
                onClick = { open = false; repo.update(task.id) { it.copy(listId = null) } },
            )
            snapshot.lists.forEach { list ->
                DropdownMenuItem(
                    leadingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(list.color)) },
                    text = { Text(list.name, color = Palette.TextPrimary) },
                    onClick = { open = false; repo.update(task.id) { it.copy(listId = list.id) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditor(task: TaskItem, snapshot: Snapshot, repo: Repository) {
    var adding by remember(task.id) { mutableStateOf(false) }
    var text by remember(task.id) { mutableStateOf("") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        task.tags.forEach { tag ->
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(tag.color.copy(alpha = 0.16f))
                    .padding(start = 12.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#${tag.name}", style = MaterialTheme.typography.labelLarge, color = tag.color)
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Remove tag ${tag.name}") { repo.removeTag(task.id, tag.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove ${tag.name}", tint = tag.color, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (!adding) {
            Pill("Add tag", selected = false, leading = Icons.Rounded.Sell) { adding = true }
        }
    }
    if (adding) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        val submit = {
            if (text.isNotBlank()) repo.addTag(task.id, text)
            text = ""
            adding = false
        }
        InlineInput(
            value = text,
            onValueChange = { text = it.replace(" ", "") },
            placeholder = "Tag name, like exam",
            onSubmit = submit,
            modifier = Modifier.padding(top = 10.dp).focusRequester(focus),
        )
        val suggestions = snapshot.tags
            .filter { t -> task.tags.none { it.id == t.id } && t.name.contains(text.removePrefix("#"), ignoreCase = true) }
            .take(8)
        if (suggestions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { tag ->
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .clickable { repo.addTag(task.id, tag.name); text = ""; adding = false },
                    ) { ColorTag("#${tag.name}", tag.color, Modifier.padding(vertical = 6.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ChecklistEditor(taskId: Long, items: List<ChecklistItem>, repo: Repository) {
    var newText by remember(taskId) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = if (item.done) "Uncheck" else "Check", role = Role.Checkbox) {
                            repo.toggleChecklistItem(item.id)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (item.done) Palette.Lavender else Color.Transparent)
                            .border(2.dp, if (item.done) Palette.Lavender else Palette.TextTertiary, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.done) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Palette.OnLavender, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                AutoSaveField(
                    initial = item.text,
                    key = item.id,
                    placeholder = "Item",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (item.done) Palette.TextTertiary else Palette.TextPrimary,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                ) { value -> repo.editChecklistItem(item.id, value) }
                SmallIconButton(Icons.Rounded.Close, "Remove ${item.text}") { repo.deleteChecklistItem(item.id) }
            }
        }
        val submit = {
            repo.addChecklistItem(taskId, newText)
            newText = ""
        }
        InlineInput(
            value = newText,
            onValueChange = { newText = it },
            placeholder = "Add an item",
            onSubmit = submit,
            leading = Icons.Rounded.Add,
        )
    }
}

@Composable
private fun SubtaskEditor(
    task: TaskItem,
    snapshot: Snapshot,
    today: LocalDate,
    repo: Repository,
    actions: DetailActions,
) {
    var newText by remember(task.id) { mutableStateOf("") }
    val children = snapshot.childrenOf[task.id].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        children.forEach { child ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Open subtask") { actions.onOpenSubtask(child.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckCircle(
                    checked = child.done,
                    ringColor = child.priority.color(),
                    label = child.title,
                    onToggle = { actions.onToggle(child) },
                    size = 20.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        child.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (child.done) Palette.TextTertiary else Palette.TextPrimary,
                        textDecoration = if (child.done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                    )
                    val meta = listOfNotNull(
                        child.date?.let { relativeDay(it, today) },
                        child.time?.format(timeFormat),
                        if (child.subtaskTotal > 0) "${child.subtaskDone}/${child.subtaskTotal} subtasks" else null,
                    ).joinToString(", ")
                    if (meta.isNotEmpty()) {
                        Text(meta, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
                    }
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Palette.TextTertiary, modifier = Modifier.padding(end = 8.dp))
            }
        }
        val submit = {
            if (newText.isNotBlank()) {
                val parsed = SmartParser.parse(newText, LocalDateTime.now())
                repo.addTask(parsed, listId = task.list?.id, parentId = task.id)
            }
            newText = ""
        }
        InlineInput(
            value = newText,
            onValueChange = { newText = it },
            placeholder = "Add a subtask",
            onSubmit = submit,
            leading = Icons.Rounded.Add,
        )
    }
}

/** Text that saves itself a moment after you stop typing, and again when you leave. */
@Composable
private fun AutoSaveField(
    initial: String,
    key: Long,
    placeholder: String,
    style: TextStyle,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
    boxed: Boolean = false,
    onSave: (String) -> Unit,
) {
    var value by remember(key) { mutableStateOf(initial) }
    var lastSaved by remember(key) { mutableStateOf(initial) }
    val save by rememberUpdatedState(onSave)
    LaunchedEffect(key, value) {
        delay(500)
        if (value != lastSaved) {
            save(value)
            lastSaved = value
        }
    }
    DisposableEffect(key) {
        onDispose { if (value != lastSaved) save(value) }
    }
    BasicTextField(
        value = value,
        onValueChange = { value = it },
        textStyle = style,
        singleLine = singleLine,
        cursorBrush = SolidColor(Palette.Lavender),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Box(
                if (boxed) {
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.Surface.copy(alpha = 0.7f))
                        .padding(14.dp)
                } else {
                    Modifier.padding(vertical = 10.dp)
                },
            ) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = Palette.TextTertiary, textDecoration = null))
                inner()
            }
        },
    )
}

@Composable
private fun InlineInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.TextPrimary),
        cursorBrush = SolidColor(Palette.Lavender),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.Surface.copy(alpha = 0.7f))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Icon(leading, contentDescription = null, tint = Palette.TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Palette.TextSecondary)
                    inner()
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) else onDismiss()
            }) { Text("Set date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.TextPrimary) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a time", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("Set time") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.TextPrimary) } },
    ) {
        TimePicker(state = state)
    }
}
