package io.github.ryr05.noctil.ui.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.data.model.Snapshot
import io.github.ryr05.noctil.focus.FocusEngine
import io.github.ryr05.noctil.focus.FocusKind
import io.github.ryr05.noctil.focus.FocusState
import io.github.ryr05.noctil.focus.Noise
import io.github.ryr05.noctil.ui.components.Pill
import io.github.ryr05.noctil.ui.components.ScreenTitle
import io.github.ryr05.noctil.ui.components.bottomContentPadding
import io.github.ryr05.noctil.ui.components.topContentPadding
import io.github.ryr05.noctil.ui.tasks.openTaskOrder
import io.github.ryr05.noctil.ui.theme.Palette
import io.github.ryr05.noctil.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Focus timer. It keeps running when you leave the app: the countdown shows on the lockscreen
 * (and as a Live Update on Android 16), and an alarm tells you when the session is done.
 */
@Composable
fun FocusScreen(snapshot: Snapshot, today: LocalDate) {
    val context = LocalContext.current
    val state by FocusEngine.state.collectAsState()
    val now by produceState(System.currentTimeMillis(), state.running) {
        while (true) {
            value = System.currentTimeMillis()
            delay(if (state.running) 250 else 1_000)
        }
    }
    val zone = ZoneId.systemDefault()
    val todayMinutes = snapshot.focus
        .filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == today }
        .sumOf { it.minutes }
    val todaySessions = snapshot.focus.count {
        it.kind == "focus" && Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == today
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topContentPadding(), bottom = bottomContentPadding()),
    ) {
        item(key = "title") { ScreenTitle(title = "Focus", overline = focusSummary(todaySessions, todayMinutes)) }
        item(key = "modes") {
            Spacer(Modifier.height(20.dp))
            ModePicker(state) { FocusEngine.select(context, it) }
        }
        item(key = "ring") {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                if (state.kind == FocusKind.Stopwatch) {
                    TimerRing(progress = if (state.running) 1f else 0f, clock = clock(state.elapsedAt(now)), caption = caption(state))
                } else {
                    val total = state.minutes * 60_000L
                    val remaining = state.remainingAt(now)
                    TimerRing(progress = if (total == 0L) 0f else remaining.toFloat() / total, clock = clock(remaining), caption = caption(state))
                }
            }
        }
        item(key = "controls") { Controls(state) }
        if (state.kind == FocusKind.Focus && !state.started) {
            item(key = "length") {
                Label("Length")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 45, 60, 90).forEach { m ->
                        item { Pill("$m min", selected = state.minutes == m) { FocusEngine.setMinutes(context, m) } }
                    }
                }
            }
        }
        item(key = "task") {
            Label("Focusing on")
            TaskPicker(state, snapshot, today)
        }
        item(key = "noise") {
            Label("Sound")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Noise.entries.forEach { n ->
                    item {
                        Pill(
                            n.label,
                            selected = state.noise == n,
                            leading = if (n == Noise.Off) null else Icons.Rounded.GraphicEq,
                        ) { FocusEngine.setNoise(context, n) }
                    }
                }
            }
            Text(
                "Sounds are made on your phone and keep playing with the screen off.",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
        }
    }
}

private fun focusSummary(sessions: Int, minutes: Int): String {
    val time = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    return when (sessions) {
        0 -> if (minutes > 0) "$time focused today" else "No focus time yet today"
        1 -> "1 session, $time today"
        else -> "$sessions sessions, $time today"
    }
}

private fun clock(ms: Long): String {
    val totalSeconds = ((ms + 999) / 1000).toInt()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun caption(s: FocusState): String = when {
    s.running && s.kind == FocusKind.Focus -> "Stay with one thing"
    s.running -> if (s.kind == FocusKind.Stopwatch) "Counting" else "Rest your eyes"
    s.started -> "Paused"
    else -> s.kind.label
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Palette.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 10.dp),
    )
}

@Composable
private fun ModePicker(state: FocusState, onSelect: (FocusKind) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(Palette.Surface).padding(4.dp)) {
        FocusKind.entries.forEach { kind ->
            val isSelected = kind == state.kind
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Palette.SurfaceHigh else Color.Transparent)
                    .selectable(selected = isSelected, enabled = !state.running || isSelected, role = Role.Tab) { onSelect(kind) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (kind) {
                        FocusKind.ShortBreak -> "Break"
                        FocusKind.LongBreak -> "Long break"
                        else -> kind.label
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Palette.TextPrimary else Palette.TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Controls(state: FocusState) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Palette.SurfaceHigh)
                .clickable(enabled = state.started, role = Role.Button) { FocusEngine.reset(context) }
                .semantics { contentDescription = if (state.kind == FocusKind.Stopwatch) "Stop and save" else "Stop timer" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Stop, contentDescription = null, tint = if (state.started) Palette.TextPrimary else Palette.TextTertiary)
        }
        Spacer(Modifier.width(16.dp))
        Row(
            modifier = Modifier
                .height(56.dp)
                .width(176.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Palette.LavenderLight, Palette.Lavender)))
                .clickable(role = Role.Button) { if (state.running) FocusEngine.pause(context) else FocusEngine.start(context) },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (state.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = Palette.OnLavender)
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    state.running -> "Pause"
                    state.started -> "Resume"
                    else -> "Start"
                },
                style = MaterialTheme.typography.titleMedium,
                color = Palette.OnLavender,
            )
        }
    }
}

@Composable
private fun TaskPicker(state: FocusState, snapshot: Snapshot, today: LocalDate) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val current = state.taskId?.let { snapshot.byId[it] }
    val choices = snapshot.tasks
        .filter { !it.done && it.parentId == null && (it.date == null || !it.date.isAfter(today.plusDays(2))) }
        .sortedWith(openTaskOrder)
        .take(25)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.Surface)
                .clickable(role = Role.Button) { open = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                current?.title ?: "Pick a task (optional)",
                style = MaterialTheme.typography.bodyLarge,
                color = if (current != null) Palette.TextPrimary else Palette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.SurfaceHigh) {
            DropdownMenuItem(
                text = { Text("No task", color = Palette.TextPrimary) },
                onClick = { open = false; FocusEngine.setTask(context, null) },
            )
            choices.forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.title, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { open = false; FocusEngine.setTask(context, t.id) },
                )
            }
        }
    }
}

@Composable
private fun TimerRing(progress: Float, clock: String, caption: String) {
    val reduceMotion = rememberReduceMotion()
    val animated by animateFloatAsState(progress, animationSpec = tween(if (reduceMotion) 0 else 250), label = "ring")
    Box(Modifier.size(264.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Palette.Hairline, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                brush = Brush.sweepGradient(listOf(Palette.Lavender, Palette.LavenderLight, Palette.Lavender)),
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                clock,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                color = Palette.TextPrimary,
                modifier = Modifier.semantics { contentDescription = "$clock" },
            )
            Text(caption, style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
        }
    }
}
