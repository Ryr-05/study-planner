package io.github.ryr05.noctil.ui.me

import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.R
import io.github.ryr05.noctil.reminders.LockscreenCard
import io.github.ryr05.noctil.tile.AddTaskTileService
import io.github.ryr05.noctil.ui.components.SectionLabel
import io.github.ryr05.noctil.ui.components.groupShape
import io.github.ryr05.noctil.ui.theme.Palette
import io.github.ryr05.noctil.widget.CalendarWidgetReceiver
import io.github.ryr05.noctil.widget.ExamWidgetReceiver
import io.github.ryr05.noctil.widget.QuickAddWidgetReceiver
import io.github.ryr05.noctil.widget.TodayWidgetReceiver

private data class WidgetOption(val icon: ImageVector, val title: String, val detail: String, val receiver: Class<*>)

private val widgetOptions = listOf(
    WidgetOption(Icons.Rounded.TaskAlt, "Today list", "Finish tasks right from your home screen", TodayWidgetReceiver::class.java),
    WidgetOption(Icons.Rounded.CalendarMonth, "Month calendar", "Dots on days with tasks", CalendarWidgetReceiver::class.java),
    WidgetOption(Icons.Rounded.School, "Exam countdown", "Days until your next #exam", ExamWidgetReceiver::class.java),
    WidgetOption(Icons.Rounded.Add, "Quick add bar", "One tap to type a new task", QuickAddWidgetReceiver::class.java),
)

/** Lockscreen card switch, one-tap widget adding, and the Quick Settings tile. */
fun LazyListScope.lockscreenAndWidgets(notificationsOn: Boolean) {
    item(key = "surfaces-label") { SectionLabel("Lockscreen and widgets") }
    item(key = "lockscreen-card") { LockscreenCardRow(notificationsOn) }
    widgetOptions.forEachIndexed { i, option ->
        item(key = "widget-${option.title}") {
            AddRow(option.icon, option.title, option.detail, index = i, count = widgetOptions.size + 1) { context ->
                pinWidget(context, option.receiver)
            }
        }
    }
    item(key = "qs-tile") {
        AddRow(
            Icons.Rounded.Tune,
            "Quick Settings tile",
            "Swipe down from the top and tap Add task",
            index = widgetOptions.size,
            count = widgetOptions.size + 1,
        ) { context -> addTile(context) }
    }
    item(key = "surfaces-note") {
        Text(
            "Tip: long-press the Noctil icon for shortcuts. On phones and tablets that allow lockscreen " +
                "widgets, these widgets can go on your lockscreen too.",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
        )
    }
}

@Composable
private fun LockscreenCardRow(notificationsOn: Boolean) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(LockscreenCard.isEnabled(context)) }
    fun set(value: Boolean) {
        enabled = value
        val app = context.applicationContext as NoctilApplication
        LockscreenCard.setEnabled(context, value, app.repository.snapshot.value)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface)
            .clickable(role = Role.Switch) { set(!enabled) }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = Palette.Lavender, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text("Lockscreen Today card", style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            Text(
                when {
                    !notificationsOn -> "Needs notifications. Turn on Reminders above first."
                    enabled -> "Today's tasks show on your lockscreen, silently."
                    else -> "Off"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Palette.TextSecondary,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { set(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.OnLavender,
                checkedTrackColor = Palette.Lavender,
                uncheckedThumbColor = Palette.TextSecondary,
                uncheckedTrackColor = Palette.SurfaceHigh,
                uncheckedBorderColor = Palette.Outline,
            ),
        )
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun AddRow(
    icon: ImageVector,
    title: String,
    detail: String,
    index: Int,
    count: Int,
    onAdd: (Context) -> Boolean,
) {
    val context = LocalContext.current
    var showManual by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(groupShape(index, count))
            .background(Palette.Surface)
            .clickable(onClickLabel = "Add $title") { if (!onAdd(context)) showManual = true }
            .drawBehind {
                if (index > 0) {
                    drawLine(Palette.Hairline, Offset(60.dp.toPx(), 0f), Offset(size.width - 16.dp.toPx(), 0f), 1.dp.toPx())
                }
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
        Text(
            "Add",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.Lavender,
            modifier = Modifier
                .background(Palette.Lavender.copy(alpha = 0.12f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
    if (showManual) {
        AlertDialog(
            onDismissRequest = { showManual = false },
            containerColor = Palette.SurfaceHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Add it by hand", color = Palette.TextPrimary) },
            text = {
                Text(
                    "Your phone doesn't support adding this from an app. Long-press an empty spot on your " +
                        "home screen, tap Widgets, find Noctil, then drag $title onto the screen.",
                    color = Palette.TextSecondary,
                )
            },
            confirmButton = { TextButton(onClick = { showManual = false }) { Text("Got it") } },
        )
    }
}

/** Asks the launcher to place the widget. Returns false when the launcher can't do that. */
private fun pinWidget(context: Context, receiver: Class<*>): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return false
    return manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
}

/** Android 13+ can add the tile with one tap. Older versions need it added from the Quick Settings editor. */
private fun addTile(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val manager = context.getSystemService(StatusBarManager::class.java) ?: return false
    manager.requestAddTileService(
        ComponentName(context, AddTaskTileService::class.java),
        context.getString(R.string.tile_add_task),
        Icon.createWithResource(context, R.drawable.ic_tile_add),
        context.mainExecutor,
    ) { }
    return true
}
