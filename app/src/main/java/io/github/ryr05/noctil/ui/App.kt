package io.github.ryr05.noctil.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.data.model.Folder
import io.github.ryr05.noctil.data.model.Habit
import io.github.ryr05.noctil.data.model.ItemColors
import io.github.ryr05.noctil.ui.habits.HabitActions
import io.github.ryr05.noctil.ui.habits.HabitDetailSheet
import io.github.ryr05.noctil.ui.habits.HabitEditorDialog
import io.github.ryr05.noctil.ui.habits.HabitsScreen
import io.github.ryr05.noctil.ui.stats.StatsSheet
import io.github.ryr05.noctil.focus.FocusEngine
import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.data.toMinutes
import io.github.ryr05.noctil.parse.SmartParser
import io.github.ryr05.noctil.ui.calendar.CalMode
import io.github.ryr05.noctil.ui.calendar.CalendarActions
import io.github.ryr05.noctil.ui.calendar.ClassEditorDialog
import io.github.ryr05.noctil.ui.calendar.SlotSheet
import io.github.ryr05.noctil.ui.calendar.SyncSheet
import io.github.ryr05.noctil.ui.calendar.TimetableSheet
import io.github.ryr05.noctil.ui.components.relativeDay
import io.github.ryr05.noctil.ui.components.timeFormat
import java.time.LocalTime
import io.github.ryr05.noctil.data.model.Tag
import io.github.ryr05.noctil.data.model.TaskItem
import io.github.ryr05.noctil.data.model.TaskList
import io.github.ryr05.noctil.reminders.LockscreenCard
import io.github.ryr05.noctil.reminders.Notifications
import io.github.ryr05.noctil.ui.calendar.CalendarScreen
import io.github.ryr05.noctil.ui.components.AuroraBackground
import io.github.ryr05.noctil.ui.detail.DetailActions
import io.github.ryr05.noctil.ui.detail.TaskDetailSheet
import io.github.ryr05.noctil.ui.focus.FocusScreen
import io.github.ryr05.noctil.ui.glass.GlassSurface
import io.github.ryr05.noctil.ui.glass.LocalHazeState
import io.github.ryr05.noctil.ui.me.MeScreen
import io.github.ryr05.noctil.ui.quickadd.QuickAddOverlay
import io.github.ryr05.noctil.ui.search.SearchOverlay
import io.github.ryr05.noctil.ui.tasks.DrawerActions
import io.github.ryr05.noctil.ui.tasks.FolderEditorDialog
import io.github.ryr05.noctil.ui.tasks.ListEditorDialog
import io.github.ryr05.noctil.ui.tasks.ListsDrawer
import io.github.ryr05.noctil.ui.tasks.TagEditorDialog
import io.github.ryr05.noctil.ui.tasks.TaskActions
import io.github.ryr05.noctil.ui.tasks.TaskView
import io.github.ryr05.noctil.ui.tasks.TasksScreen
import io.github.ryr05.noctil.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.LocalDate

/** A place to open the app at, from outside the app. */
sealed interface LaunchRequest {
    data class OpenTask(val id: Long) : LaunchRequest
    data class OpenDate(val date: LocalDate) : LaunchRequest
    data object QuickAdd : LaunchRequest
    data object Today : LaunchRequest
    data object Habits : LaunchRequest
    data class Focus(val startNext: Boolean = false) : LaunchRequest
}

private data class UndoMessage(val id: Long, val text: String, val undo: () -> Unit)

private data class ClassEdit(val cls: SchoolClass?)

private data class HabitEdit(val habit: Habit?)

private sealed interface Editor {
    data object NewList : Editor
    data class EditList(val list: TaskList) : Editor
    data object NewFolder : Editor
    data class EditFolder(val folder: Folder) : Editor
    data class EditTag(val tag: Tag) : Editor
}

@Composable
fun NoctilApp(launchRequest: LaunchRequest?, onLaunchHandled: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { (context.applicationContext as NoctilApplication).repository }
    val snapshot by repo.snapshot.collectAsState()
    val today by produceState(LocalDate.now()) {
        while (true) {
            delay(30_000)
            value = LocalDate.now()
        }
    }

    var tab by rememberSaveable { mutableStateOf(Tab.Tasks) }
    var viewKey by rememberSaveable { mutableStateOf(TaskView.Today.key) }
    val view = TaskView.fromKey(viewKey)
    var calendarDay by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var detailStack by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Editor?>(null) }
    var calMode by rememberSaveable { mutableStateOf(CalMode.Month) }
    var slot by remember { mutableStateOf<Pair<LocalDate, LocalTime>?>(null) }
    var timetableOpen by rememberSaveable { mutableStateOf(false) }
    var syncOpen by rememberSaveable { mutableStateOf(false) }
    var classEditing by remember { mutableStateOf<ClassEdit?>(null) }
    var habitOpenId by rememberSaveable { mutableStateOf<Long?>(null) }
    var habitEditing by remember { mutableStateOf<HabitEdit?>(null) }
    var statsOpen by rememberSaveable { mutableStateOf(false) }
    var undo by remember { mutableStateOf<UndoMessage?>(null) }
    val hazeState = rememberHazeState()

    fun showUndo(text: String, action: () -> Unit) {
        undo = UndoMessage(System.nanoTime(), text, action)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Show the lockscreen card right away once notifications are allowed.
        if (granted) LockscreenCard.update(context, repo.snapshot.value)
    }
    fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Notifications.canNotify(context) && !repo.askedForNotifications
        ) {
            repo.askedForNotifications = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // If a deleted list or tag was being shown, fall back to Today.
    LaunchedEffect(snapshot, viewKey) {
        if (!snapshot.loaded) return@LaunchedEffect
        val missing = when (val v = view) {
            is TaskView.OfList -> snapshot.lists.none { it.id == v.id }
            is TaskView.OfTag -> snapshot.tags.none { it.id == v.id }
            else -> false
        }
        if (missing) viewKey = TaskView.Today.key
    }

    // Opening from a reminder, widget, lockscreen card, Quick Settings tile or shortcut.
    LaunchedEffect(launchRequest) {
        val request = launchRequest ?: return@LaunchedEffect
        searchOpen = false
        drawerOpen = false
        when (request) {
            is LaunchRequest.OpenTask -> {
                tab = Tab.Tasks
                quickAddOpen = false
                detailStack = listOf(request.id)
            }
            is LaunchRequest.OpenDate -> {
                detailStack = emptyList()
                calendarDay = request.date
                tab = Tab.Calendar
            }
            LaunchRequest.QuickAdd -> {
                detailStack = emptyList()
                tab = Tab.Tasks
                viewKey = TaskView.Today.key
                quickAddOpen = true
            }
            LaunchRequest.Today -> {
                detailStack = emptyList()
                tab = Tab.Tasks
                viewKey = TaskView.Today.key
            }
            LaunchRequest.Habits -> {
                detailStack = emptyList()
                tab = Tab.Habits
            }
            is LaunchRequest.Focus -> {
                detailStack = emptyList()
                tab = Tab.Focus
                if (request.startNext) FocusEngine.start(context)
            }
        }
        onLaunchHandled()
    }

    // Close the editor if its task was deleted elsewhere.
    LaunchedEffect(snapshot, detailStack) {
        if (snapshot.loaded && detailStack.isNotEmpty() && snapshot.byId[detailStack.last()] == null) {
            detailStack = detailStack.dropLast(1).filter { snapshot.byId[it] != null }
        }
    }

    val taskActions = TaskActions(
        onToggle = { task: TaskItem ->
            if (!task.done) {
                repo.setDone(task.id, true) { completion ->
                    showUndo("Finished \"${task.title}\"") { repo.undoCompletion(completion) }
                }
            } else {
                repo.setDone(task.id, false)
            }
        },
        onOpen = { task -> detailStack = listOf(task.id) },
        onMoveToTomorrow = { task ->
            val previousDate = task.date
            val previousReminder = task.reminder
            repo.setDate(task.id, today.plusDays(1))
            showUndo("Moved to tomorrow") {
                repo.update(task.id) { it.copy(dueDate = previousDate?.toEpochDay(), reminder = previousReminder) }
            }
        },
    )

    BackHandler(
        enabled = detailStack.isNotEmpty() || quickAddOpen || searchOpen || drawerOpen || slot != null ||
            timetableOpen || syncOpen || tab != Tab.Tasks || view != TaskView.Today,
    ) {
        when {
            detailStack.isNotEmpty() -> detailStack = detailStack.dropLast(1)
            quickAddOpen -> quickAddOpen = false
            slot != null -> slot = null
            habitOpenId != null -> habitOpenId = null
            statsOpen -> statsOpen = false
            timetableOpen -> timetableOpen = false
            syncOpen -> syncOpen = false
            searchOpen -> searchOpen = false
            drawerOpen -> drawerOpen = false
            tab != Tab.Tasks -> tab = Tab.Tasks
            else -> viewKey = TaskView.Today.key
        }
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize().background(Palette.Night)) {
            // Everything inside this box is what the glass controls blur.
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                AuroraBackground()
                Crossfade(targetState = tab, animationSpec = tween(200), label = "tabs") { current ->
                    when (current) {
                        Tab.Tasks -> TasksScreen(
                            view = view,
                            snapshot = snapshot,
                            today = today,
                            actions = taskActions,
                            onOpenLists = { drawerOpen = true },
                            onOpenSearch = { searchOpen = true },
                        )
                        Tab.Calendar -> CalendarScreen(
                            snapshot = snapshot,
                            today = today,
                            selected = calendarDay,
                            mode = calMode,
                            taskActions = taskActions,
                            actions = CalendarActions(
                                onSelect = { calendarDay = it },
                                onMode = { calMode = it },
                                onSlot = { d, t -> slot = d to t },
                                onMoveTask = { task, d, t ->
                                    val previousDate = task.date
                                    val previousTime = task.time
                                    repo.schedule(task.id, d, t)
                                    showUndo("Moved to ${relativeDay(d, today)}, ${t.format(timeFormat)}") {
                                        repo.update(task.id) {
                                            it.copy(dueDate = previousDate?.toEpochDay(), dueTime = previousTime?.toMinutes())
                                        }
                                    }
                                },
                                onOpenClass = { classEditing = ClassEdit(it) },
                                onOpenTimetable = { timetableOpen = true },
                                onOpenSync = { syncOpen = true },
                            ),
                        )
                        Tab.Habits -> HabitsScreen(
                            snapshot = snapshot,
                            today = today,
                            actions = HabitActions(
                                onTap = { habit, day -> repo.tapHabit(habit.id, day) },
                                onOpen = { habitOpenId = it.id },
                                onNew = { habitEditing = HabitEdit(null) },
                                onQuickAdd = { name, goal ->
                                    repo.saveHabit(null, name, ItemColors[(snapshot.habits.size + 2) % ItemColors.size], java.time.DayOfWeek.entries.toSet(), goal, null)
                                },
                            ),
                        )
                        Tab.Focus -> FocusScreen(snapshot, today)
                        Tab.Me -> MeScreen(
                            askedForNotificationsBefore = repo.askedForNotifications,
                            onAskNotifications = { askForNotificationsOnce() },
                            onOpenStats = { statsOpen = true },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassTabBar(
                    selected = tab,
                    onSelect = {
                        if (it == Tab.Tasks && tab == Tab.Tasks) drawerOpen = true
                        tab = it
                    },
                    modifier = Modifier.weight(1f),
                )
                AddButton(onClick = { if (tab == Tab.Habits) habitEditing = HabitEdit(null) else quickAddOpen = true })
            }

            UndoBar(
                message = undo,
                onUndo = { undo?.undo?.invoke(); undo = null },
                onTimeout = { id -> if (undo?.id == id) undo = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            SearchOverlay(
                visible = searchOpen,
                snapshot = snapshot,
                today = today,
                actions = taskActions,
                onDismiss = { searchOpen = false },
            )

            ListsDrawer(
                visible = drawerOpen,
                snapshot = snapshot,
                today = today,
                current = view,
                actions = DrawerActions(
                    onSelect = {
                        viewKey = it.key
                        tab = Tab.Tasks
                        drawerOpen = false
                    },
                    onNewList = { editor = Editor.NewList },
                    onEditList = { editor = Editor.EditList(it) },
                    onNewFolder = { editor = Editor.NewFolder },
                    onEditFolder = { editor = Editor.EditFolder(it) },
                    onEditTag = { editor = Editor.EditTag(it) },
                ),
                onDismiss = { drawerOpen = false },
            )

            SlotSheet(
                slot = slot,
                snapshot = snapshot,
                today = today,
                onDismiss = { slot = null },
                onPlace = { task, d, t ->
                    slot = null
                    repo.schedule(task.id, d, t)
                    askForNotificationsOnce()
                },
                onCreate = { text, d, t ->
                    slot = null
                    val parsed = SmartParser.parse(text, java.time.LocalDateTime.now(), defaultDate = d)
                    repo.addTask(parsed.copy(time = parsed.time ?: t), null) { id ->
                        showUndo("Added \"${parsed.title}\"") { repo.delete(id) }
                    }
                    askForNotificationsOnce()
                },
            )

            TimetableSheet(
                visible = timetableOpen,
                snapshot = snapshot,
                onDismiss = { timetableOpen = false },
                onEdit = { classEditing = ClassEdit(it) },
            )

            SyncSheet(visible = syncOpen, onDismiss = { syncOpen = false })

            HabitDetailSheet(
                habit = habitOpenId?.let { id -> snapshot.habits.firstOrNull { it.id == id } },
                today = today,
                onDismiss = { habitOpenId = null },
                onEdit = { habitEditing = HabitEdit(it) },
                onTap = { habit, day -> repo.tapHabit(habit.id, day) },
            )

            StatsSheet(visible = statsOpen, snapshot = snapshot, today = today, onDismiss = { statsOpen = false })

            val quickAddDefaultDate = when {
                tab == Tab.Calendar -> calendarDay
                tab != Tab.Tasks -> today
                view == TaskView.Today || view == TaskView.Next7 -> today
                else -> null
            }
            val quickAddList = if (tab == Tab.Tasks) (view as? TaskView.OfList)?.id else null
            QuickAddOverlay(
                visible = quickAddOpen,
                defaultDate = quickAddDefaultDate,
                defaultListId = quickAddList,
                snapshot = snapshot,
                today = today,
                onDismiss = { quickAddOpen = false },
                onSubmit = { parsed, listId ->
                    quickAddOpen = false
                    if (tab == Tab.Focus || tab == Tab.Me || tab == Tab.Habits) tab = Tab.Tasks
                    repo.addTask(parsed, listId) { id ->
                        showUndo("Added \"${parsed.title}\"") { repo.delete(id) }
                    }
                    if (parsed.date != null) askForNotificationsOnce()
                },
            )

            TaskDetailSheet(
                taskId = detailStack.lastOrNull(),
                hasParentInStack = detailStack.size > 1,
                snapshot = snapshot,
                today = today,
                repo = repo,
                actions = DetailActions(
                    onClose = { detailStack = emptyList() },
                    onBack = { detailStack = detailStack.dropLast(1) },
                    onOpenSubtask = { id -> detailStack = detailStack + id },
                    onToggle = taskActions.onToggle,
                    onDelete = { task ->
                        detailStack = detailStack.dropLast(1)
                        repo.delete(task.id) { ids ->
                            showUndo("Deleted \"${task.title}\"") { repo.restore(ids) }
                        }
                    },
                    onReminderSet = { askForNotificationsOnce() },
                ),
            )
        }
    }

    habitEditing?.let { edit ->
        HabitEditorDialog(
            initial = edit.habit,
            onDismiss = { habitEditing = null },
            onSave = { name, color, days, goal, reminder ->
                habitEditing = null
                repo.saveHabit(edit.habit?.id, name, color, days, goal, reminder)
                if (reminder != null) askForNotificationsOnce()
            },
            onDelete = edit.habit?.let { h -> { habitEditing = null; habitOpenId = null; repo.deleteHabit(h.id) } },
        )
    }

    classEditing?.let { edit ->
        ClassEditorDialog(
            initial = edit.cls,
            lists = snapshot.lists,
            onDismiss = { classEditing = null },
            onSave = { name, room, color, days, start, end, listId ->
                classEditing = null
                repo.saveClass(edit.cls?.id, name, room, color, days, start, end, listId)
            },
            onDelete = edit.cls?.let { cls -> { classEditing = null; repo.deleteClass(cls.id) } },
        )
    }

    when (val e = editor) {
        Editor.NewList -> ListEditorDialog(
            initial = null,
            folders = snapshot.folders,
            onDismiss = { editor = null },
            onSave = { name, color, folderId ->
                editor = null
                repo.createList(name, color, folderId) { id ->
                    viewKey = TaskView.OfList(id).key
                    tab = Tab.Tasks
                    drawerOpen = false
                }
            },
            onDelete = null,
        )
        is Editor.EditList -> ListEditorDialog(
            initial = e.list,
            folders = snapshot.folders,
            onDismiss = { editor = null },
            onSave = { name, color, folderId ->
                editor = null
                repo.updateList(e.list.id, name, color, folderId)
            },
            onDelete = {
                editor = null
                repo.deleteList(e.list.id)
            },
        )
        Editor.NewFolder -> FolderEditorDialog(
            initial = null,
            onDismiss = { editor = null },
            onSave = { name -> editor = null; repo.createFolder(name) },
            onDelete = null,
        )
        is Editor.EditFolder -> FolderEditorDialog(
            initial = e.folder,
            onDismiss = { editor = null },
            onSave = { name -> editor = null; repo.renameFolder(e.folder.id, name, e.folder.sortOrder) },
            onDelete = { editor = null; repo.deleteFolder(e.folder.id) },
        )
        is Editor.EditTag -> TagEditorDialog(
            tag = e.tag,
            onDismiss = { editor = null },
            onSave = { name, color -> editor = null; repo.updateTag(e.tag.id, name, color) },
            onDelete = { editor = null; repo.deleteTag(e.tag.id) },
        )
        null -> Unit
    }
}

/** Short glass toast above the tab bar with an Undo button. Disappears after a few seconds. */
@Composable
private fun UndoBar(
    message: UndoMessage?,
    onUndo: () -> Unit,
    onTimeout: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var last by remember { mutableStateOf(message) }
    if (message != null) last = message
    LaunchedEffect(message?.id) {
        val id = message?.id ?: return@LaunchedEffect
        delay(4_500)
        onTimeout(id)
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it },
        modifier = modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 92.dp),
    ) {
        GlassSurface(modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape, tintAlpha = 0.8f) {
            Row(
                Modifier.fillMaxSize().padding(start = 20.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    last?.text.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Undo",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Lavender,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onUndo)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
