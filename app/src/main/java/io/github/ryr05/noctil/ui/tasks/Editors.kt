package io.github.ryr05.noctil.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.data.model.Folder
import io.github.ryr05.noctil.data.model.ItemColors
import io.github.ryr05.noctil.data.model.Tag
import io.github.ryr05.noctil.data.model.TaskList
import io.github.ryr05.noctil.ui.components.Pill
import io.github.ryr05.noctil.ui.theme.Palette

private val colorNames = listOf("Lavender", "Blue", "Mint", "Amber", "Rose", "Pink", "Cyan", "Lime")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListEditorDialog(
    initial: TaskList?,
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Color, folderId: Long?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var colorArgb by rememberSaveable { mutableStateOf((initial?.color ?: ItemColors.first()).toArgb()) }
    var folderId by rememberSaveable { mutableStateOf(initial?.folderId) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = "Delete \"${initial?.name}\"?",
            body = "The list is removed. Its tasks are kept and move to the Inbox.",
            confirm = "Delete list",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
        return
    }

    EditorDialog(
        title = if (initial == null) "New list" else "Edit list",
        canSave = name.isNotBlank(),
        onSave = { onSave(name, Color(colorArgb), folderId) },
        onDismiss = onDismiss,
        onDelete = onDelete?.let { { confirmDelete = true } },
    ) {
        NameField(name, "List name", "Calculus") { name = it }
        Spacer(Modifier.height(16.dp))
        FieldLabel("Color")
        ColorSwatches(Color(colorArgb)) { colorArgb = it.toArgb() }
        if (folders.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            FieldLabel("Folder")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("None", selected = folderId == null) { folderId = null }
                folders.forEach { folder ->
                    Pill(folder.name, selected = folderId == folder.id) { folderId = folder.id }
                }
            }
        }
    }
}

@Composable
fun FolderEditorDialog(
    initial: Folder?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = "Delete folder \"${initial?.name}\"?",
            body = "The folder is removed. Its lists and tasks are kept.",
            confirm = "Delete folder",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
        return
    }

    EditorDialog(
        title = if (initial == null) "New folder" else "Edit folder",
        canSave = name.isNotBlank(),
        onSave = { onSave(name) },
        onDismiss = onDismiss,
        onDelete = onDelete?.let { { confirmDelete = true } },
    ) {
        NameField(name, "Folder name", "Semester 1") { name = it }
    }
}

@Composable
fun TagEditorDialog(
    tag: Tag,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Color) -> Unit,
    onDelete: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(tag.name) }
    var colorArgb by rememberSaveable { mutableStateOf(tag.color.toArgb()) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete #${tag.name}?",
            body = "The tag is removed from every task. The tasks themselves are kept.",
            confirm = "Delete tag",
            onConfirm = onDelete,
            onDismiss = { confirmDelete = false },
        )
        return
    }

    EditorDialog(
        title = "Edit tag",
        canSave = name.isNotBlank(),
        onSave = { onSave(name, Color(colorArgb)) },
        onDismiss = onDismiss,
        onDelete = { confirmDelete = true },
    ) {
        NameField(name, "Tag name", "exam") { name = it.removePrefix("#").replace(" ", "") }
        Spacer(Modifier.height(16.dp))
        FieldLabel("Color")
        ColorSwatches(Color(colorArgb)) { colorArgb = it.toArgb() }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.SurfaceHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = Palette.TextPrimary) },
        text = { Text(body, color = Palette.TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = Palette.PriorityHigh) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.TextPrimary) }
        },
    )
}

@Composable
private fun EditorDialog(
    title: String,
    canSave: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.SurfaceHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = Palette.TextPrimary) },
        text = { Column { content() } },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = Palette.PriorityHigh) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.TextPrimary) }
            }
        },
    )
}

@Composable
private fun NameField(value: String, label: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSwatches(selected: Color, onSelect: (Color) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ItemColors.forEachIndexed { i, color ->
            val isSelected = color.toArgb() == selected.toArgb()
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(3.dp, if (isSelected) Palette.TextPrimary else Color.Transparent, CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(color) }
                    .semantics { contentDescription = colorNames[i] },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Palette.OnLavender, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
