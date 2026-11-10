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
    data object Focus : LaunchRequest
}

private data class UndoMessage(val id: Long, val text: String, val undo: () -> Unit)

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
            LaunchRequest.Focus -> {
                detailStack = emptyList()
                tab = Tab.Focus
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

    BackHandler(enabled = detailStack.isNotEmpty() || quickAddOpen || searchOpen || drawerOpen || tab != Tab.Tasks || view != TaskView.Today) {
        when {
            detailStack.isNotEmpty() -> detailStack = detailStack.dropLast(1)
            quickAddOpen -> quickAddOpen = false
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
                            onSelect = { calendarDay = it },
                            actions = taskActions,
                        )
                        Tab.Focus -> FocusScreen()
                        Tab.Me -> MeScreen(
                            askedForNotificationsBefore = repo.askedForNotifications,
                            onAskNotifications = { askForNotificationsOnce() },
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
                AddButton(onClick = { quickAddOpen = true })
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
                    if (tab == Tab.Focus || tab == Tab.Me) tab = Tab.Tasks
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
