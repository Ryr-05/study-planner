package io.github.ryr05.noctil.ui.me

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.data.Backup
import io.github.ryr05.noctil.ui.components.SectionLabel
import io.github.ryr05.noctil.ui.components.groupShape
import io.github.ryr05.noctil.ui.tasks.ConfirmDialog
import io.github.ryr05.noctil.ui.theme.Palette
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Opens the system file picker for backups. Save to Google Drive to keep a copy in the cloud. */
class BackupController(
    val backUp: () -> Unit,
    val restore: () -> Unit,
    val exportCsv: () -> Unit,
    val dialogs: @Composable () -> Unit,
)

@Composable
fun rememberBackupController(): BackupController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = Backup.export(context, uri).fold(
                { "Backup saved with $it tasks. Keep it somewhere safe, like Google Drive." },
                { "Couldn't save the backup. ${it.message.orEmpty()}" },
            )
        }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            message = Backup.exportCsv(context, uri).fold({ "Exported $it tasks." }, { "Couldn't export. ${it.message.orEmpty()}" })
        }
    }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    val stamp = LocalDate.now().toString()

    return BackupController(
        backUp = { createJson.launch("noctil-backup-$stamp.json") },
        restore = { openJson.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) },
        exportCsv = { createCsv.launch("noctil-tasks-$stamp.csv") },
        dialogs = {
            pendingRestore?.let { uri ->
                ConfirmDialog(
                    title = "Restore this backup?",
                    body = "Everything in Noctil now is replaced by the backup. This can't be undone, so back up first if you're unsure.",
                    confirm = "Replace and restore",
                    onConfirm = {
                        pendingRestore = null
                        scope.launch {
                            message = Backup.restore(context, uri).fold(
                                { "Restored $it tasks." },
                                { "Couldn't restore. ${it.message ?: "The file may be damaged."}" },
                            )
                        }
                    },
                    onDismiss = { pendingRestore = null },
                )
            }
            message?.let { text ->
                AlertDialog(
                    onDismissRequest = { message = null },
                    containerColor = Palette.SurfaceHigh,
                    shape = RoundedCornerShape(28.dp),
                    text = { Text(text, color = Palette.TextPrimary) },
                    confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
                )
            }
        },
    )
}

fun LazyListScope.backupSection(controller: BackupController) {
    item(key = "backup-label") { SectionLabel("Backup") }
    item(key = "backup-save") {
        BackupRow(Icons.Rounded.CloudUpload, "Back up to a file", "Save to Google Drive or your phone", 0, 3, controller.backUp)
    }
    item(key = "backup-restore") {
        BackupRow(Icons.Rounded.CloudDownload, "Restore from a file", "Replaces what's in Noctil now", 1, 3, controller.restore)
    }
    item(key = "backup-csv") {
        BackupRow(Icons.Rounded.TableChart, "Export tasks as CSV", "Opens in Sheets or Excel", 2, 3, controller.exportCsv)
    }
    item(key = "backup-note") {
        Text(
            "Android also backs up Noctil automatically with your Google account, so a new phone can restore it during setup.",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
        )
    }
}

@Composable
private fun BackupRow(icon: ImageVector, title: String, detail: String, index: Int, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(groupShape(index, count))
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .drawBehind {
                if (index > 0) drawLine(Palette.Hairline, Offset(60.dp.toPx(), 0f), Offset(size.width - 16.dp.toPx(), 0f), 1.dp.toPx())
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
        }
    }
}
