package io.github.ryr05.noctil.reminders

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri
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

    /** Every reminder rings as an alarm, not only tasks marked "Ring like an alarm". */
    fun allReminders(context: Context) = prefs(context).getBoolean("all_reminders", false)
    fun setAllReminders(context: Context, value: Boolean) = prefs(context).edit { putBoolean("all_reminders", value) }

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
