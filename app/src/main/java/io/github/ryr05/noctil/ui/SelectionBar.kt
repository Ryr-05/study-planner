package io.github.ryr05.noctil.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.TaskList
import io.github.ryr05.noctil.ui.components.color
import io.github.ryr05.noctil.ui.glass.GlassDefaults
import io.github.ryr05.noctil.ui.glass.GlassSurface
import io.github.ryr05.noctil.ui.theme.Palette
import java.time.LocalDate

data class SelectionActions(
    val onDone: () -> Unit,
    val onDate: (LocalDate?) -> Unit,
    val onMove: (Long?) -> Unit,
    val onPriority: (Priority) -> Unit,
    val onTag: (String) -> Unit,
    val onDelete: () -> Unit,
    val onSelectAll: () -> Unit,
    val onClose: () -> Unit,
)

/** Shown instead of the tab bar while several tasks are picked. */
@Composable
fun SelectionBar(count: Int, lists: List<TaskList>, today: LocalDate, actions: SelectionActions, modifier: Modifier = Modifier) {
    var tagging by remember { mutableStateOf(false) }
    GlassSurface(
        modifier = modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tintAlpha = GlassDefaults.THICK,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    if (count == 1) "1 selected" else "$count selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Select all",
                    style = MaterialTheme.typography.labelLarge,
                    color = Palette.Lavender,
                    modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = actions.onSelectAll).padding(10.dp),
                )
                BarButton(Icons.Rounded.Close, "Stop selecting", onClick = actions.onClose)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BarButton(Icons.Rounded.Check, "Finish", enabled = count > 0, onClick = actions.onDone)
                MenuButton(Icons.Rounded.CalendarMonth, "Set date", count > 0) { close ->
                    listOf("Today" to today, "Tomorrow" to today.plusDays(1), "Next week" to today.plusWeeks(1), "No date" to null)
                        .forEach { (label, date) -> Item(label) { close(); actions.onDate(date) } }
                }
                MenuButton(Icons.Rounded.FolderOpen, "Move to list", count > 0) { close ->
                    Item("Inbox") { close(); actions.onMove(null) }
                    lists.forEach { l -> Item(l.name) { close(); actions.onMove(l.id) } }
                }
                MenuButton(Icons.Rounded.Flag, "Set priority", count > 0) { close ->
                    listOf(Priority.High, Priority.Medium, Priority.Low, Priority.None).forEach { p ->
                        Item(p.label, if (p == Priority.None) null else p.color()) { close(); actions.onPriority(p) }
                    }
                }
                BarButton(Icons.Rounded.Sell, "Add a tag", enabled = count > 0) { tagging = true }
                BarButton(Icons.Rounded.DeleteOutline, "Delete", enabled = count > 0, tint = Palette.PriorityHigh, onClick = actions.onDelete)
            }
        }
    }
    if (tagging) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { tagging = false },
            containerColor = Palette.SurfaceHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Add a tag", color = Palette.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.replace(" ", "") }, singleLine = true,
                    label = { Text("Tag name") }, placeholder = { Text("exam") }, shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = { tagging = false; actions.onTag(text) }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { tagging = false }) { Text("Cancel", color = Palette.TextPrimary) } },
        )
    }
}

@Composable
private fun Item(label: String, dot: Color? = null, onClick: () -> Unit) {
    DropdownMenuItem(
        leadingIcon = dot?.let { c -> { Icon(Icons.Rounded.Flag, contentDescription = null, tint = c, modifier = Modifier.size(18.dp)) } },
        text = { Text(label, color = Palette.TextPrimary) },
        onClick = onClick,
    )
}

@Composable
private fun MenuButton(icon: ImageVector, label: String, enabled: Boolean, content: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarButton(icon, label, enabled = enabled) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.SurfaceHigh) {
            content { open = false }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = Palette.TextPrimary, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else Palette.TextTertiary)
    }
}
