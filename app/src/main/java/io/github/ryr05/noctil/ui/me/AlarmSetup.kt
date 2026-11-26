package io.github.ryr05.noctil.ui.me

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.reminders.AlarmPrefs
import io.github.ryr05.noctil.reminders.AlarmService
import io.github.ryr05.noctil.reminders.Notifications
import io.github.ryr05.noctil.ui.components.Pill
import io.github.ryr05.noctil.ui.theme.Palette
import kotlinx.coroutines.delay

/** One thing Android (or the phone) must allow for alarms to ring full screen every time. */
private data class Step(val title: String, val detail: String, val ok: Boolean, val open: (Context) -> Intent)

private fun steps(context: Context): List<Step> = listOf(
    Step(
        "Notifications", "Alarms ring through a notification", Notifications.canNotify(context),
    ) { c -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName) },
    Step(
        "Full-screen alarms", "Fills the lockscreen when the phone is locked", AlarmPrefs.canShowFullScreen(context),
    ) { c -> AlarmPrefs.fullScreenSettings(c) },
    Step(
        "Show over other apps", "Takes over the screen while you're in another app", AlarmPrefs.canPopUp(context),
    ) { c -> AlarmPrefs.popUpSettings(c) },
    Step(
        "Ring while the phone sleeps", "Turns off battery optimization for Noctil, so alarms aren't delayed or skipped",
        AlarmPrefs.canRunAsleep(context),
    ) { c -> AlarmPrefs.runAsleepSettings(c) },
)

/** How many of the checkable steps are still off. */
fun alarmSetupMissing(context: Context): Int = steps(context).count { !it.ok }

/** Brands whose own battery manager also stops background apps, past Android's settings. */
private val strictBrand: String? = Build.MANUFACTURER.lowercase().let { m ->
    listOf("vivo", "iqoo", "oppo", "realme", "oneplus", "xiaomi", "redmi", "poco", "huawei", "honor", "samsung").firstOrNull { m.contains(it) }
}

private fun brandSteps(brand: String): String = when (brand) {
    "vivo", "iqoo" ->
        "Vivo has its own switches that no app can check (names differ a little between phones):\n\n" +
            "1. Settings > Battery > Background power consumption management: set Noctil to allow high background power use.\n" +
            "2. Settings > Apps > Autostart (or i Manager > App manager > Autostart): turn Noctil on.\n" +
            "3. Noctil's app settings > Permissions: allow \"Display pop-up windows while running in the background\" " +
            "and \"Display on lock screen\".\n\nThen use Test an alarm with the screen locked."
    "samsung" ->
        "Samsung can put apps to sleep on its own:\n\n" +
            "Settings > Battery > Background usage limits: make sure Noctil isn't in Sleeping or Deep sleeping apps, " +
            "or add it to Never sleeping apps.\n\nThen use Test an alarm with the screen locked."
    else ->
        "Your phone's battery manager can also stop alarms. In Noctil's app settings, allow autostart and " +
            "background activity, and pop-ups on the lockscreen or while in the background if your phone has them.\n\n" +
            "Then use Test an alarm with the screen locked."
}

private fun open(context: Context, intent: Intent) {
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
    for (i in listOf(intent, fallback)) {
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // Not on this phone: try the app's own settings page.
        }
    }
}

/** The alarm checklist: what's allowed, a button for what isn't, the phone's own extras and a real test. */
@Composable
fun AlarmSetupSteps(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Re-checked while visible, so a step ticks as soon as you come back from Android's settings.
    val list by produceState(steps(context)) {
        while (true) {
            value = steps(context)
            delay(1_500)
        }
    }
    var brandHelp by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    LaunchedEffect(testing) {
        if (testing) {
            delay(TEST_SECONDS * 1_000L)
            testing = false
        }
    }
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Palette.Surface).padding(horizontal = 16.dp, vertical = 6.dp)) {
        list.forEach { step ->
            StepRow(
                icon = if (step.ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                tint = if (step.ok) Palette.Lavender else Palette.PriorityMedium,
                title = step.title,
                detail = if (step.ok) "Allowed" else step.detail,
                action = if (step.ok) null else "Allow",
            ) { open(context, step.open(context)) }
        }
        strictBrand?.let { brand ->
            StepRow(
                icon = Icons.Rounded.PhoneAndroid, tint = Palette.Lavender,
                title = "${brand.replaceFirstChar { it.uppercase() }}'s own battery settings",
                detail = "Extra switches no app can check. Tap for the steps.",
                action = "Steps",
            ) { brandHelp = true }
        }
        StepRow(
            icon = Icons.Rounded.PlayCircle, tint = Palette.Lavender,
            title = "Test an alarm",
            detail = if (testing) "Ringing in about $TEST_SECONDS seconds. Lock the phone or open another app now."
            else "Rings in $TEST_SECONDS seconds, so you can lock the phone or open another app first",
            action = if (testing) null else "Test",
        ) {
            val app = context.applicationContext as NoctilApplication
            app.scheduler.scheduleAt(AlarmService.PREVIEW, System.currentTimeMillis() + TEST_SECONDS * 1_000L, alarmClock = true)
            testing = true
        }
    }
    if (brandHelp && strictBrand != null) {
        AlertDialog(
            onDismissRequest = { brandHelp = false },
            containerColor = Palette.SurfaceHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Phone settings for alarms", color = Palette.TextPrimary) },
            text = {
                Text(
                    brandSteps(strictBrand),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                }) { Text("Open Noctil's settings") }
            },
            dismissButton = { TextButton(onClick = { brandHelp = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun StepRow(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, detail: String, action: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Palette.TextPrimary)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
        }
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            Pill(action, selected = true, onClick = onClick)
        }
    }
}

/**
 * On Today while alarms are on but Android would stop them popping up: one tap opens the
 * checklist. Later hides it for a few days.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmSetupBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val missing by produceState(alarmSetupMissing(context)) {
        while (true) {
            value = alarmSetupMissing(context)
            delay(2_000)
        }
    }
    var hiddenUntil by remember { mutableStateOf(AlarmPrefs.setupLaterUntil(context)) }
    var open by remember { mutableStateOf(false) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Palette.SurfaceHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Set up alarms", color = Palette.TextPrimary) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { AlarmSetupSteps() } },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Done") } },
        )
    }
    val alarmsOn = AlarmPrefs.dueAlarms(context) || AlarmPrefs.startAlarms(context)
    if (!alarmsOn || missing == 0 || hiddenUntil > System.currentTimeMillis()) return
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Surface)
            .border(1.dp, Palette.PriorityMedium.copy(alpha = 0.4f), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = Palette.PriorityMedium, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Alarms might not pop up", style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
                Text(
                    "Allow ${if (missing == 1) "1 more setting" else "$missing more settings"} so alarms ring full screen, " +
                        "even while the phone sleeps or you're in another app.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 34.dp)) {
            Pill("Set up", selected = true) { open = true }
            Pill("Later", selected = false) {
                val until = System.currentTimeMillis() + LATER_DAYS * 24 * 60 * 60 * 1000L
                AlarmPrefs.setSetupLaterUntil(context, until)
                hiddenUntil = until
            }
        }
    }
}

private const val TEST_SECONDS = 15
private const val LATER_DAYS = 3
