package io.github.ryr05.noctil.ui.focus

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.ui.components.ScreenTitle
import io.github.ryr05.noctil.ui.components.bottomContentPadding
import io.github.ryr05.noctil.ui.components.topContentPadding
import io.github.ryr05.noctil.ui.theme.Palette
import io.github.ryr05.noctil.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

enum class FocusMode(val label: String, val minutes: Int) {
    Focus("Focus", 25),
    ShortBreak("Short break", 5),
    LongBreak("Long break", 15),
}

/**
 * Working Pomodoro timer. It counts from a wall-clock end time, so it stays accurate even
 * if the screen skips frames. Phase 4 moves it into a background service with a lockscreen
 * countdown so it keeps running when the app is closed.
 */
@Composable
fun FocusScreen() {
    var mode by rememberSaveable { mutableStateOf(FocusMode.Focus) }
    var remainingMs by rememberSaveable { mutableLongStateOf(FocusMode.Focus.minutes * 60_000L) }
    var running by rememberSaveable { mutableStateOf(false) }
    var completedSessions by rememberSaveable { mutableIntStateOf(0) }
    val totalMs = mode.minutes * 60_000L

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val endAt = SystemClock.elapsedRealtime() + remainingMs
        while (remainingMs > 0) {
            delay(200)
            remainingMs = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        }
        running = false
        if (mode == FocusMode.Focus) completedSessions++
    }

    fun selectMode(newMode: FocusMode) {
        mode = newMode
        running = false
        remainingMs = newMode.minutes * 60_000L
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = topContentPadding(), bottom = bottomContentPadding()),
    ) {
        ScreenTitle(title = "Focus")
        Spacer(Modifier.height(20.dp))
        ModePicker(selected = mode, onSelect = ::selectMode)

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimerRing(
                progress = if (totalMs == 0L) 0f else remainingMs.toFloat() / totalMs,
                remainingMs = remainingMs,
                caption = when {
                    remainingMs == 0L -> "Session complete"
                    running -> "Stay with one thing"
                    remainingMs < totalMs -> "Paused"
                    else -> mode.label
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Palette.SurfaceHigh)
                    .selectable(selected = false, role = Role.Button, onClick = { selectMode(mode) })
                    .semantics { contentDescription = "Reset timer" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Palette.TextPrimary)
            }
            Spacer(Modifier.width(16.dp))
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .width(168.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Palette.LavenderLight, Palette.Lavender)))
                    .selectable(selected = running, role = Role.Button, onClick = {
                        if (remainingMs == 0L) remainingMs = totalMs
                        running = !running
                    }),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Palette.OnLavender,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        running -> "Pause"
                        remainingMs in 1 until totalMs -> "Resume"
                        else -> "Start"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.OnLavender,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = when (completedSessions) {
                0 -> "No focus sessions yet today"
                1 -> "1 focus session done today"
                else -> "$completedSessions focus sessions done today"
            },
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun ModePicker(selected: FocusMode, onSelect: (FocusMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Palette.Surface)
            .padding(4.dp),
    ) {
        FocusMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Palette.SurfaceHigh else Palette.Surface)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(mode) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${mode.label} ${mode.minutes}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Palette.TextPrimary else Palette.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun TimerRing(progress: Float, remainingMs: Long, caption: String) {
    val reduceMotion = rememberReduceMotion()
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(if (reduceMotion) 0 else 250),
        label = "ring",
    )
    val totalSeconds = ((remainingMs + 999) / 1000).toInt()
    val clock = "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

    Box(Modifier.size(264.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = Palette.Hairline,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(Palette.Lavender, Palette.LavenderLight, Palette.Lavender)),
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = clock,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
                color = Palette.TextPrimary,
                modifier = Modifier.semantics { contentDescription = "$clock remaining" },
            )
            Text(text = caption, style = MaterialTheme.typography.labelLarge, color = Palette.TextSecondary)
        }
    }
}
