package io.github.ryr05.noctil.ui.me

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import io.github.ryr05.noctil.reminders.LockscreenCard
import io.github.ryr05.noctil.reminders.Notifications
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
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
import androidx.compose.material.icons.rounded.Gavel
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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

private val typingTips = listOf(
    "fri 5pm, tomorrow 9am" to "Due day and time",
    "tonight, tmrw, this weekend" to "Plain words work, even with typos",
    "friday next week, next fri" to "A day in next week",
    "dec 4, 11/20, the 15th" to "A specific date",
    "in 3 days, in 2 hours, end of month" to "Relative dates and times",
    "3-5pm, for 2 hours" to "Time with a length",
    "mon to fri" to "A start and due day (shows as a bar)",
    "half past 4, noon, 17:00" to "More ways to say a time",
    "every monday, mondays, weekdays" to "Repeating tasks",
    "every 3 days, biweekly, every 15th" to "More repeat patterns",
    "!1 !2 !3, p1, !urgent" to "High, medium, low priority",
    "#exam" to "Add a tag (exams get a countdown)",
    "~Chemistry" to "Put it in a list (made if new)",
)

@Composable
fun MeScreen(askedForNotificationsBefore: Boolean, onAskNotifications: () -> Unit, onOpenStats: () -> Unit, onBack: () -> Unit = {}) {
    val backup = rememberBackupController()
    val context = LocalContext.current
    val version = remember(context) { appVersion(context) }
    var showTips by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    // Re-checked while this screen is open, so it updates after you change it in settings.
    val notificationsOn by produceState(Notifications.canNotify(context)) {
        while (true) {
            value = Notifications.canNotify(context)
            delay(1_500)
        }
    }
    // If notifications were just turned on in system settings, bring the lockscreen card back.
    LaunchedEffect(notificationsOn) {
        if (notificationsOn) {
            val app = context.applicationContext as io.github.ryr05.noctil.NoctilApplication
            LockscreenCard.update(context, app.repository.snapshot.value)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding(),
            bottom = bottomContentPadding(),
        ),
    ) {
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                io.github.ryr05.noctil.ui.components.RoundIconButton(
                    androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack,
                )
                Spacer(Modifier.width(14.dp))
                ScreenTitle(title = "Me")
            }
        }
        item(key = "profile") { AccountCard(Modifier.padding(top = 20.dp)) }

        item(key = "settings-label") { SectionLabel("Settings") }
        item(key = "notifications") {
            SettingRow(
                icon = Icons.Rounded.Notifications,
                title = "Reminders",
                detail = if (notificationsOn) "On. Tasks with a date remind you." else "Off. Tap to turn on notifications.",
                trailing = if (notificationsOn) null else "Turn on",
                index = 0,
                count = 2,
                onClick = {
                    // The first time, Android shows its own prompt. After that it may stay
                    // silent, so open the app's notification settings instead.
                    if (!notificationsOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !askedForNotificationsBefore
                    ) {
                        onAskNotifications()
                    } else {
                        openNotificationSettings(context)
                    }
                },
            )
        }
        item(key = "tips") {
            SettingRow(
                icon = Icons.Rounded.Lightbulb,
                title = "How to type tasks",
                detail = "Dates, repeats, priority and tags in one line",
                trailing = null,
                index = 1,
                count = 2,
                onClick = { showTips = true },
            )
        }

        alarmSection()

        item(key = "stats-label") { SectionLabel("Progress") }
        item(key = "stats") {
            SettingRow(
                icon = Icons.Rounded.BarChart,
                title = "Statistics",
                detail = "Tasks finished, focus time and habit consistency",
                trailing = null,
                index = 0,
                count = 1,
                onClick = onOpenStats,
            )
        }

        lockscreenAndWidgets(notificationsOn)

        backupSection(backup)

        item(key = "about-label") { SectionLabel("About") }
        item(key = "version") {
            SettingRow(Icons.Rounded.Info, "Version", version, trailing = null, index = 0, count = 3)
        }
        item(key = "update") {
            SettingRow(
                icon = Icons.Rounded.SystemUpdate,
                title = "Check for updates",
                detail = "Opens the download page on GitHub",
                trailing = null,
                index = 1,
                count = 3,
                onClick = { openUrl(context, RELEASES_URL) },
            )
        }
        item(key = "terms") {
            SettingRow(
                icon = Icons.Rounded.Gavel,
                title = "Terms and privacy",
                detail = "For college students 18+. What's saved and what AI planning sends",
                trailing = null,
                index = 2,
                count = 3,
                onClick = { showTerms = true },
            )
        }
    }

    if (showTerms) io.github.ryr05.noctil.ui.onboarding.TermsDialog(onDismiss = { showTerms = false })

    backup.dialogs()

    if (showTips) {
        AlertDialog(
            onDismissRequest = { showTips = false },
            containerColor = Palette.SurfaceHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("How to type tasks", color = Palette.TextPrimary) },
            text = {
                Column {
                    Text(
                        "Type it all in one line when you tap +. For example: chem lab fri 5pm !1 #lab",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    typingTips.forEach { (example, meaning) ->
                        Column(Modifier.padding(vertical = 5.dp)) {
                            Text(example, style = MaterialTheme.typography.labelLarge, color = Palette.Lavender)
                            Text(meaning, style = MaterialTheme.typography.bodyMedium, color = Palette.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTips = false }) { Text("Got it") } },
        )
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Very old or unusual devices: nothing to open.
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
        // With big text the button goes under the words instead of squeezing them.
        val big = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
        val badge: @Composable (Modifier) -> Unit = { m ->
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Lavender,
                    modifier = m
                        .background(Palette.Lavender.copy(alpha = 0.12f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
            if (big) badge(Modifier.padding(top = 6.dp))
        }
        if (!big) badge(Modifier.padding(start = 8.dp))
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
