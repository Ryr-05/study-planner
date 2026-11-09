package io.github.ryr05.noctil.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInbox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.data.model.Folder
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.data.model.Tag
import io.github.ryr05.noctil.data.model.TaskList
import io.github.ryr05.noctil.ui.glass.GlassDefaults
import io.github.ryr05.noctil.ui.glass.GlassSurface
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.LocalDate

data class DrawerActions(
    val onSelect: (TaskView) -> Unit,
    val onNewList: () -> Unit,
    val onEditList: (TaskList) -> Unit,
    val onNewFolder: () -> Unit,
    val onEditFolder: (Folder) -> Unit,
    val onEditTag: (Tag) -> Unit,
)

/** Side panel with smart lists, folders, lists and tags. */
@Composable
fun ListsDrawer(
    visible: Boolean,
    snapshot: Snapshot,
    today: LocalDate,
    current: TaskView,
    actions: DrawerActions,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(140))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Palette.Shadow.copy(alpha = 0.55f))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f)) { -it } + fadeIn(),
            exit = slideOutHorizontally(tween(180)) { -it } + fadeOut(tween(180)),
        ) {
            BoxWithConstraints(Modifier.fillMaxHeight()) {
                val width = minOf(maxWidth * 0.86f, 360.dp)
                GlassSurface(
                    modifier = Modifier.width(width).fillMaxHeight(),
                    shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                    tintAlpha = GlassDefaults.THICK,
                ) {
                    DrawerContent(snapshot, today, current, actions, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    snapshot: Snapshot,
    today: LocalDate,
    current: TaskView,
    actions: DrawerActions,
    onDismiss: () -> Unit,
) {
    var collapsed by rememberSaveable { mutableStateOf(setOf<Long>()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 24.dp),
    ) {
        item(key = "title") {
            Row(Modifier.padding(start = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Lists", style = MaterialTheme.typography.headlineSmall, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
                SmallIconButton(Icons.Rounded.Close, "Close lists", onClick = onDismiss)
            }
        }

        TaskView.smart.forEach { view ->
            item(key = view.key) {
                DrawerRow(
                    title = viewTitle(view, snapshot),
                    count = openCount(view, snapshot, today),
                    selected = current == view,
                    leading = { LeadingIcon(smartIcon(view)) },
                    onClick = { actions.onSelect(view) },
                )
            }
        }
        item(key = TaskView.Inbox.key) {
            DrawerRow(
                title = "Inbox",
                count = openCount(TaskView.Inbox, snapshot, today),
                selected = current == TaskView.Inbox,
                leading = { LeadingIcon(Icons.Rounded.Inbox) },
                onClick = { actions.onSelect(TaskView.Inbox) },
            )
        }

        item(key = "lists-header") {
            Row(Modifier.padding(start = 12.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Your lists", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
                SmallIconButton(Icons.Rounded.CreateNewFolder, "New folder", onClick = actions.onNewFolder)
                SmallIconButton(Icons.Rounded.PlaylistAdd, "New list", onClick = actions.onNewList)
            }
        }

        snapshot.folders.forEach { folder ->
            val isCollapsed = folder.id in collapsed
            item(key = "folder-${folder.id}") {
                val rotation by animateFloatAsState(if (isCollapsed) -90f else 0f, label = "folder")
                DrawerRow(
                    title = folder.name,
                    count = null,
                    selected = false,
                    leading = { LeadingIcon(Icons.Rounded.Folder) },
                    trailing = {
                        Icon(
                            Icons.Rounded.ExpandMore, contentDescription = null, tint = Palette.TextSecondary,
                            modifier = Modifier.size(20.dp).rotate(rotation),
                        )
                    },
                    onClick = { collapsed = if (isCollapsed) collapsed - folder.id else collapsed + folder.id },
                    onMore = { actions.onEditFolder(folder) },
                    moreLabel = "Edit folder ${folder.name}",
                )
            }
            if (!isCollapsed) {
                listRows(snapshot.lists.filter { it.folderId == folder.id }, snapshot, today, current, actions, indent = true)
            }
        }
        listRows(snapshot.lists.filter { l -> l.folderId == null || snapshot.folders.none { it.id == l.folderId } }, snapshot, today, current, actions, indent = false)

        if (snapshot.lists.isEmpty()) {
            item(key = "no-lists") {
                Text(
                    "Make a list for each class, like Calculus or Chemistry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (snapshot.tags.isNotEmpty()) {
            item(key = "tags-header") {
                Text(
                    "Tags", style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary,
                    modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp),
                )
            }
            snapshot.tags.forEach { tag ->
                val view = TaskView.OfTag(tag.id)
                item(key = "tag-${tag.id}") {
                    DrawerRow(
                        title = "#${tag.name}",
                        count = openCount(view, snapshot, today),
                        selected = current == view,
                        leading = { Dot(tag.color) },
                        onClick = { actions.onSelect(view) },
                        onMore = { actions.onEditTag(tag) },
                        moreLabel = "Edit tag ${tag.name}",
                    )
                }
            }
        }
    }
}

private fun LazyListScope.listRows(
    lists: List<TaskList>,
    snapshot: Snapshot,
    today: LocalDate,
    current: TaskView,
    actions: DrawerActions,
    indent: Boolean,
) {
    lists.forEach { list ->
        val view = TaskView.OfList(list.id)
        item(key = "list-${list.id}") {
            DrawerRow(
                title = list.name,
                count = openCount(view, snapshot, today),
                selected = current == view,
                leading = { Dot(list.color) },
                onClick = { actions.onSelect(view) },
                onMore = { actions.onEditList(list) },
                moreLabel = "Edit list ${list.name}",
                indent = indent,
            )
        }
    }
}

private fun smartIcon(view: TaskView): ImageVector = when (view) {
    TaskView.Today -> Icons.Rounded.Today
    TaskView.Next7 -> Icons.Rounded.DateRange
    TaskView.High -> Icons.Rounded.Flag
    TaskView.All -> Icons.Rounded.AllInbox
    else -> Icons.Rounded.TaskAlt
}

@Composable
private fun DrawerRow(
    title: String,
    count: Int?,
    selected: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    moreLabel: String = "More",
    indent: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 20.dp else 0.dp)
            .clip(CircleShape)
            .background(if (selected) Palette.Lavender.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 4.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Palette.TextPrimary else Palette.TextPrimary.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        if (count != null && count > 0) {
            Text(count.toString(), style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
        }
        trailing?.let {
            Spacer(Modifier.width(4.dp))
            it()
        }
        if (onMore != null) {
            SmallIconButton(Icons.Rounded.MoreHoriz, moreLabel, onClick = onMore)
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun LeadingIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(22.dp))
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
}

@Composable
fun SmallIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.TextSecondary, modifier = Modifier.size(22.dp))
    }
}
