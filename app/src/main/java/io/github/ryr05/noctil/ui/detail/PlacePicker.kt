package io.github.ryr05.noctil.ui.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.data.Repository
import io.github.ryr05.noctil.data.model.Place
import io.github.ryr05.noctil.reminders.Geofences
import io.github.ryr05.noctil.ui.tasks.SmallIconButton
import io.github.ryr05.noctil.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Choose a saved place, or save where you are now ("Campus", "Library", "Home").
 * No map and no account: it uses the phone's own location.
 */
@Composable
fun PlacePickerDialog(
    current: Long?,
    places: List<Place>,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
    repo: Repository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var askBackground by remember { mutableStateOf(false) }
    var pendingPick by remember { mutableStateOf<Long?>(null) }

    fun finishPick(id: Long?) {
        if (id != null && !Geofences.hasBackground(context)) {
            pendingPick = id
            askBackground = true
        } else {
            onPick(id)
        }
    }

    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askBackground = false
        val id = pendingPick
        pendingPick = null
        scope.launch { Geofences.refresh(context) }
        if (!granted) note = "Without \"Allow all the time\", Noctil can't notice when you arrive while it's closed. You can change this in the app's location settings."
        onPick(id)
    }

    val saveHere: () -> Unit = {
        working = true
        scope.launch {
            val spot = Geofences.currentLocation(context)
            working = false
            if (spot == null) {
                note = "Couldn't find your location. Check that location is on, then try again."
            } else {
                repo.savePlace(name.ifBlank { "Saved place" }, spot.first, spot.second) { id ->
                    naming = false
                    name = ""
                    finishPick(id)
                }
            }
        }
    }

    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) saveHere() else note = "Location wasn't allowed, so this spot can't be saved."
    }

    if (askBackground) {
        AlertDialog(
            onDismissRequest = { askBackground = false; onPick(pendingPick) },
            containerColor = Palette.SurfaceHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("One more step", color = Palette.TextPrimary) },
            text = {
                Text(
                    "To remind you when you arrive, even with Noctil closed, choose \"Allow all the time\" on the next screen. " +
                        "Your location stays on your phone.",
                    color = Palette.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    else { askBackground = false; onPick(pendingPick) }
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { askBackground = false; onPick(pendingPick) }) { Text("Not now", color = Palette.TextPrimary) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.SurfaceHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Remind me when I get to", color = Palette.TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PlaceRow("No place", selected = current == null, onClick = { onPick(null) }, onDelete = null)
                places.forEach { p ->
                    PlaceRow(p.name, selected = current == p.id, onClick = { finishPick(p.id) }, onDelete = { repo.deletePlace(p.id) })
                }
                Spacer(Modifier.height(12.dp))
                if (!naming) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Palette.Surface)
                            .clickable(role = Role.Button) { naming = true }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = Palette.Lavender)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Save where I am now", style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
                            Text("Stand at the place, like your campus, then save it", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text("Name this place") }, placeholder = { Text("Campus") },
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(enabled = !working, onClick = {
                        if (Geofences.hasForeground(context)) saveHere()
                        else foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }) { Text(if (working) "Finding you..." else "Save this spot") }
                }
                note?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.PriorityMedium, modifier = Modifier.padding(top = 10.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun PlaceRow(name: String, selected: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) Palette.Lavender else Color.Transparent)
                .border(2.dp, if (selected) Palette.Lavender else Palette.TextTertiary, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Rounded.Place, contentDescription = null, tint = Palette.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
        if (onDelete != null) SmallIconButton(Icons.Rounded.Close, "Forget $name", onClick = onDelete)
    }
}
