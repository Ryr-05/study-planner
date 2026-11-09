package io.github.ryr05.noctil.ui.me

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.ryr05.noctil.ui.components.ScreenTitle
import io.github.ryr05.noctil.ui.components.SectionLabel
import io.github.ryr05.noctil.ui.components.bottomContentPadding
import io.github.ryr05.noctil.ui.components.groupShape
import io.github.ryr05.noctil.ui.components.topContentPadding
import io.github.ryr05.noctil.ui.theme.Palette

private const val RELEASES_URL = "https://github.com/Ryr-05/study-planner/releases/latest"

private data class Upcoming(val icon: ImageVector, val title: String, val detail: String)

private val upcoming = listOf(
    Upcoming(Icons.Rounded.Notifications, "Reminders", "Alerts with Done and Snooze buttons"),
    Upcoming(Icons.Rounded.Widgets, "Home screen widgets", "Today list, calendar, quick add"),
    Upcoming(Icons.Rounded.Lock, "Lockscreen card", "See today's tasks without unlocking"),
    Upcoming(Icons.Rounded.LocalFireDepartment, "Habits", "Streaks and a yearly heatmap"),
    Upcoming(Icons.Rounded.BarChart, "Statistics", "Tasks done and hours focused"),
    Upcoming(Icons.Rounded.Cloud, "Backup and sync", "Optional sign-in, works offline too"),
)

@Composable
fun MeScreen() {
    val context = LocalContext.current
    val version = remember(context) { appVersion(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding(),
            bottom = bottomContentPadding(),
        ),
    ) {
        item(key = "title") { ScreenTitle(title = "Me") }
        item(key = "profile") { ProfileCard(Modifier.padding(top = 20.dp)) }

        item(key = "soon-label") { SectionLabel("On the way") }
        items(count = upcoming.size, key = { "soon-$it" }) { index ->
            val entry = upcoming[index]
            SettingRow(
                icon = entry.icon,
                title = entry.title,
                detail = entry.detail,
                trailing = "Soon",
                index = index,
                count = upcoming.size,
            )
        }

        item(key = "about-label") { SectionLabel("About") }
        item(key = "version") {
            SettingRow(Icons.Rounded.Info, "Version", version, trailing = null, index = 0, count = 2)
        }
        item(key = "update") {
            SettingRow(
                icon = Icons.Rounded.SystemUpdate,
                title = "Check for updates",
                detail = "Opens the download page on GitHub",
                trailing = null,
                index = 1,
                count = 2,
                onClick = { openUrl(context, RELEASES_URL) },
            )
        }
    }
}

@Composable
private fun ProfileCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(Palette.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = Palette.Lavender)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Guest", style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
            Text(
                text = "Everything stays on this phone. No account needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
            )
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    detail: String,
    trailing: String?,
    index: Int,
    count: Int,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(groupShape(index, count))
            .background(Palette.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind {
                if (index > 0) {
                    drawLine(
                        color = Palette.Hairline,
                        start = Offset(60.dp.toPx(), 0f),
                        end = Offset(size.width - 16.dp.toPx(), 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
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
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Lavender,
                modifier = Modifier
                    .background(Palette.Lavender.copy(alpha = 0.12f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
} catch (e: Exception) {
    "Unknown"
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No browser installed: nothing to open.
    }
}
