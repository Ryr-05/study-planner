package io.github.ryr05.noctil.ui.me

import android.content.ActivityNotFoundException
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.reminders.AlarmPrefs
import io.github.ryr05.noctil.reminders.AlarmService
import io.github.ryr05.noctil.reminders.AlarmSound
import io.github.ryr05.noctil.reminders.AlarmSounds
import io.github.ryr05.noctil.ui.components.SectionLabel
import io.github.ryr05.noctil.ui.components.groupShape
import io.github.ryr05.noctil.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Alarm settings in Me: the sound, ringing every reminder, vibration, and a test ring. */
fun LazyListScope.alarmSection() {
    item(key = "alarm-label") { SectionLabel("Alarms") }
    item(key = "alarm-settings") { AlarmSettings() }
}

@Composable
private fun AlarmSettings() {
    val context = LocalContext.current
    var sound by remember { mutableStateOf(AlarmPrefs.sound(context)) }
    var all by remember { mutableStateOf(AlarmPrefs.allReminders(context)) }
    var vibrate by remember { mutableStateOf(AlarmPrefs.vibrate(context)) }
    var gentle by remember { mutableStateOf(AlarmPrefs.gentle(context)) }
    var snooze by remember { mutableStateOf(AlarmPrefs.snoozeMinutes(context)) }
    var picking by remember { mutableStateOf(false) }
    var fullScreenOk by remember { mutableStateOf(AlarmPrefs.canShowFullScreen(context)) }
    LaunchedEffect(Unit) {
        // Re-checked while open, so it updates after coming back from settings.
        while (true) {
            fullScreenOk = AlarmPrefs.canShowFullScreen(context)
            delay(1_500)
        }
    }
    val rows = 6 + if (fullScreenOk) 0 else 1
    var i = 0

    Column {
        Text(
            "Any task can ring like your phone's alarm clock: full screen, even on the lockscreen, until you stop it. " +
                "Turn it on in a task's Remind menu, or for every reminder below.",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
        )
        if (!fullScreenOk) {
            ActionRow(
                Icons.Rounded.Fullscreen, "Allow full-screen alarms",
                "Android has this turned off for Noctil, so alarms only show as a banner. Tap to allow.",
                i++, rows, accent = Palette.PriorityMedium,
            ) {
                try {
                    context.startActivity(AlarmPrefs.fullScreenSettings(context).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    // Nothing to open on this phone.
                }
            }
        }
        ActionRow(Icons.Rounded.MusicNote, "Alarm sound", sound.name, i++, rows) { picking = true }
        SwitchRow(Icons.Rounded.NotificationsActive, "Ring every reminder as an alarm", if (all) "On for all tasks" else "Only tasks you choose", all, i++, rows) {
            all = it
            AlarmPrefs.setAllReminders(context, it)
            (context.applicationContext as NoctilApplication).repository.rescheduleAll()
        }
        SwitchRow(Icons.Rounded.Vibration, "Vibrate", if (vibrate) "On" else "Off", vibrate, i++, rows) {
            vibrate = it
            AlarmPrefs.setVibrate(context, it)
        }
        SwitchRow(Icons.AutoMirrored.Rounded.VolumeUp, "Start soft, then louder", if (gentle) "Wakes you gently" else "Full volume right away", gentle, i++, rows) {
            gentle = it
            AlarmPrefs.setGentle(context, it)
        }
        ActionRow(Icons.Rounded.Snooze, "Snooze length", "$snooze minutes", i++, rows) {
            val options = listOf(5, 10, 15, 30)
            snooze = options[(options.indexOf(snooze) + 1).mod(options.size)]
            AlarmPrefs.setSnoozeMinutes(context, snooze)
        }
        ActionRow(Icons.Rounded.PlayCircle, "Try an alarm", "Rings now so you can see and hear it", i++, rows) {
            AlarmService.preview(context)
        }
    }

    if (picking) {
        AlarmSoundDialog(
            current = sound,
            onPick = {
                sound = it
                AlarmPrefs.setSound(context, it)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun AlarmSoundDialog(current: AlarmSound, onPick: (AlarmSound) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var system by remember { mutableStateOf<List<AlarmSound>>(emptyList()) }
    var custom by remember { mutableStateOf(AlarmSounds.custom(context)) }
    var selected by remember { mutableStateOf(current) }
    var note by remember { mutableStateOf<String?>(null) }
    val preview = remember { SoundPreview() }
    DisposableEffect(Unit) { onDispose { preview.stop() } }
    LaunchedEffect(Unit) { system = withContext(Dispatchers.IO) { AlarmSounds.system(context) } }

    fun choose(s: AlarmSound) {
        selected = s
        onPick(s)
        preview.play(context, AlarmSounds.playable(context, s).firstOrNull())
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { AlarmSounds.import(context, uri) }
                result.onSuccess {
                    custom = AlarmSounds.custom(context)
                    note = null
                    choose(it)
                }.onFailure { note = it.message ?: "Couldn't add that sound." }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.SurfaceHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Alarm sound", color = Palette.TextPrimary) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Palette.Surface)
                            .clickable(role = Role.Button) { importer.launch(arrayOf("audio/*")) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = Palette.Lavender)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Add your own sound", style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
                            Text("Pick an MP3 or other audio file. Noctil keeps a copy.", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
                        }
                    }
                    note?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.PriorityMedium, modifier = Modifier.padding(top = 8.dp)) }
                }
                if (custom.isNotEmpty()) {
                    item { DialogLabel("Your sounds") }
                    items(custom, key = { "c-${it.uri}" }) { s ->
                        SoundRow(s, selected.uri == s.uri, onClick = { choose(s) }, onDelete = {
                            preview.stop()
                            AlarmSounds.delete(context, s)
                            custom = AlarmSounds.custom(context)
                            if (selected.uri == s.uri) {
                                selected = AlarmPrefs.sound(context)
                                onPick(selected)
                            }
                        })
                    }
                }
                item { DialogLabel("On this phone") }
                if (system.isEmpty()) item { Text("Loading...", style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary) }
                items(system, key = { "s-${it.uri}-${it.name}" }) { s ->
                    SoundRow(s, selected.uri == s.uri, onClick = { choose(s) }, onDelete = null)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun DialogLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp))
}

@Composable
private fun SoundRow(sound: AlarmSound, selected: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp),
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
        Text(
            sound.name,
            style = MaterialTheme.typography.bodyLarge,
            color = Palette.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        if (onDelete != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = "Remove ${sound.name}", role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove ${sound.name}", tint = Palette.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Plays a few seconds of a sound when you pick it, on the alarm volume. */
private class SoundPreview {
    private var player: MediaPlayer? = null

    fun play(context: android.content.Context, uri: Uri?) {
        stop()
        uri ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(context, uri)
                prepare()
                start()
            }
        }.getOrNull()
        val p = player
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (player === p) stop() }, 6_000)
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    index: Int,
    count: Int,
    accent: Color = Palette.Lavender,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(groupShape(index, count))
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .divider(index)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    index: Int,
    count: Int,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(groupShape(index, count))
            .background(Palette.Surface)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .divider(index)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.OnLavender,
                checkedTrackColor = Palette.Lavender,
                uncheckedThumbColor = Palette.TextSecondary,
                uncheckedTrackColor = Palette.SurfaceHigh,
                uncheckedBorderColor = Palette.Outline,
            ),
        )
    }
}

private fun Modifier.divider(index: Int): Modifier = drawBehind {
    if (index > 0) drawLine(Palette.Hairline, Offset(60.dp.toPx(), 0f), Offset(size.width - 16.dp.toPx(), 0f), 1.dp.toPx())
}
