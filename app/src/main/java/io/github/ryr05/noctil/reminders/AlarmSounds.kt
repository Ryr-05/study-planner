package io.github.ryr05.noctil.reminders

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri
import io.github.ryr05.noctil.data.db.TaskEntity
import java.io.File

/** A sound an alarm can play. [uri] null means the phone's default alarm sound. */
data class AlarmSound(val name: String, val uri: String?, val custom: Boolean = false)

/** Alarm settings, kept on this phone only. */
object AlarmPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("alarm", Context.MODE_PRIVATE)

    fun sound(context: Context): AlarmSound {
        val p = prefs(context)
        val uri = p.getString("sound_uri", null)
        return AlarmSound(p.getString("sound_name", null) ?: DEFAULT_NAME, uri, uri?.startsWith("file:") == true)
    }

    fun setSound(context: Context, sound: AlarmSound) = prefs(context).edit {
        putString("sound_uri", sound.uri)
        putString("sound_name", sound.name)
    }

    /** Due reminders of tasks with a time ring as full alarms. On unless turned off in Me > Alarms. */
    fun dueAlarms(context: Context) = prefs(context).getBoolean("due_alarms", true)
    fun setDueAlarms(context: Context, value: Boolean) = prefs(context).edit { putBoolean("due_alarms", value) }

    /** "Time to start" rings as a full alarm. On unless turned off in Me > Alarms. */
    fun startAlarms(context: Context) = prefs(context).getBoolean("start_alarms", true)
    fun setStartAlarms(context: Context, value: Boolean) = prefs(context).edit { putBoolean("start_alarms", value) }

    /**
     * A due reminder rings as a full alarm when the task is marked to ring, or when it has a time
     * and due alarms are on. Tasks with only a date (reminded at 9 AM) stay a notification.
     */
    fun ringsDue(marked: Boolean, dueAlarmsOn: Boolean, hasTime: Boolean) = marked || (dueAlarmsOn && hasTime)
    fun ringsDue(context: Context, task: TaskEntity) = ringsDue(task.alarm, dueAlarms(context), task.dueTime != null)

    /** "Display over other apps": lets the alarm page open while you're using another app. */
    fun canPopUp(context: Context) = Settings.canDrawOverlays(context)
    fun popUpSettings(context: Context) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())

    /** Out of battery optimization, so Android (and the phone's own battery saver) lets alarms wake Noctil. */
    fun canRunAsleep(context: Context) =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    @SuppressLint("BatteryLife") // an alarm app is the documented case for asking
    fun runAsleepSettings(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

    /** When the Today banner about alarm permissions may show again after Later. */
    fun setupLaterUntil(context: Context) = prefs(context).getLong("setup_later_until", 0L)
    fun setSetupLaterUntil(context: Context, millis: Long) = prefs(context).edit { putLong("setup_later_until", millis) }

    fun vibrate(context: Context) = prefs(context).getBoolean("vibrate", true)
    fun setVibrate(context: Context, value: Boolean) = prefs(context).edit { putBoolean("vibrate", value) }

    /** Starts quiet and gets louder over about 20 seconds, so it wakes you gently. */
    fun gentle(context: Context) = prefs(context).getBoolean("gentle", true)
    fun setGentle(context: Context, value: Boolean) = prefs(context).edit { putBoolean("gentle", value) }

    /** Minutes to snooze from the alarm screen. */
    fun snoozeMinutes(context: Context) = prefs(context).getInt("snooze", 10)
    fun setSnoozeMinutes(context: Context, value: Int) = prefs(context).edit { putInt("snooze", value) }

    /** Android 14+ lets people turn off full-screen alarms for an app. Then they show as a banner. */
    fun canShowFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            runCatching { context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() }.getOrDefault(true)

    fun fullScreenSettings(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
        }

    const val DEFAULT_NAME = "Phone's default alarm"
}

/** The alarm sounds on this phone, plus ones you added yourself. */
object AlarmSounds {
    private const val MAX_BYTES = 25L * 1024 * 1024

    private fun folder(context: Context) = File(context.filesDir, "alarm_sounds").apply { mkdirs() }

    fun system(context: Context): List<AlarmSound> {
        val list = mutableListOf(AlarmSound(AlarmPrefs.DEFAULT_NAME, null))
        runCatching {
            val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
            val cursor = manager.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
                list += AlarmSound(title, manager.getRingtoneUri(cursor.position).toString())
            }
        }
        return list.distinctBy { it.name }
    }

    fun custom(context: Context): List<AlarmSound> =
        folder(context).listFiles().orEmpty()
            .sortedBy { it.lastModified() }
            .map { AlarmSound(it.nameWithoutExtension, Uri.fromFile(it).toString(), custom = true) }

    /** Copies a picked audio file into Noctil, so it keeps working even if the original is moved. */
    fun import(context: Context, source: Uri): Result<AlarmSound> = runCatching {
        val resolver = context.contentResolver
        var name = "My sound"
        var size = -1L
        resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        require(size <= MAX_BYTES) { "That file is too big. Pick a sound under 25 MB." }
        val base = name.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().ifEmpty { "My sound" }.take(60)
        val ext = name.substringAfterLast('.', "mp3").lowercase().take(5)
        var file = File(folder(context), "$base.$ext")
        var n = 2
        while (file.exists()) file = File(folder(context), "$base ($n).$ext").also { n++ }
        resolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Couldn't open that file." }
            file.outputStream().use { out ->
                val copied = input.copyTo(out)
                if (copied > MAX_BYTES) {
                    file.delete()
                    error("That file is too big. Pick a sound under 25 MB.")
                }
            }
        }
        AlarmSound(file.nameWithoutExtension, Uri.fromFile(file).toString(), custom = true)
    }

    fun delete(context: Context, sound: AlarmSound) {
        val path = sound.uri?.toUri()?.path ?: return
        val file = File(path)
        if (file.parentFile == folder(context)) file.delete()
        if (AlarmPrefs.sound(context).uri == sound.uri) AlarmPrefs.setSound(context, AlarmSound(AlarmPrefs.DEFAULT_NAME, null))
    }

    /** What to actually play: the chosen sound, falling back to whatever the phone has. */
    fun playable(context: Context, sound: AlarmSound): List<Uri> = listOfNotNull(
        sound.uri?.toUri(),
        runCatching { RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM) }.getOrNull(),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
    ).distinct()
}
