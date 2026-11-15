package io.github.ryr05.noctil.update

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import io.github.ryr05.noctil.BuildConfig
import io.github.ryr05.noctil.R
import io.github.ryr05.noctil.reminders.Notifications
import io.github.ryr05.noctil.reminders.Notifications.ownGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "A new version is out." Noctil is shared as an APK on GitHub, so it looks at the latest GitHub
 * release about twice a day in the background and whenever the app comes to the front. A newer
 * version gets one notification (tap to download it), a banner on Today until you tap Later, and
 * a dot on the profile button until it's installed.
 *
 * The request only reads the public release info: it carries nothing about you or your tasks.
 */
object Updates {
    const val RELEASES_URL = "https://github.com/Ryr-05/study-planner/releases/latest"
    const val APK_URL = "https://github.com/Ryr-05/study-planner/releases/latest/download/Noctil.apk"
    private const val API_URL = "https://api.github.com/repos/Ryr-05/study-planner/releases/latest"

    const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 73_001
    private const val JOB_ID = 73_000
    private const val EVERY_MS = 12 * 60 * 60 * 1000L
    /** When the app opens, check again only if the last check is older than this. */
    private const val STALE_MS = 6 * 60 * 60 * 1000L

    private const val PREFS = "updates"
    private const val KEY_LATEST = "latest"
    private const val KEY_NOTES = "notes"
    private const val KEY_CHECKED_AT = "checked_at"
    private const val KEY_NOTIFIED = "notified"
    private const val KEY_DISMISSED = "dismissed"

    data class Release(val version: String, val whatsNew: String)

    enum class Result { UpToDate, Available, Failed }

    private val _available = MutableStateFlow<Release?>(null)
    /** The newer version waiting to be installed, or null when this one is the latest we know of. */
    val available: StateFlow<Release?> = _available.asStateFlow()

    private val _dismissed = MutableStateFlow<String?>(null)
    /** The version whose Today banner was closed with Later. The profile dot stays until you update. */
    val dismissed: StateFlow<String?> = _dismissed.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checking = AtomicBoolean(false)

    /** At app start: restore what the last check found and make sure the background check is scheduled. */
    fun init(context: Context) {
        createChannel(context)
        val p = prefs(context)
        _dismissed.value = p.getString(KEY_DISMISSED, null)
        val latest = p.getString(KEY_LATEST, null)
        if (latest != null && isNewer(latest, BuildConfig.VERSION_NAME)) {
            _available.value = Release(latest, p.getString(KEY_NOTES, null).orEmpty())
        }
        schedule(context)
    }

    /** When the app comes to the front: checks, unless it already did in the last few hours. */
    fun checkIfStale(context: Context) {
        val app = context.applicationContext
        if (System.currentTimeMillis() - prefs(app).getLong(KEY_CHECKED_AT, 0L) < STALE_MS) return
        scope.launch { check(app, notify = false) }
    }

    /**
     * Asks GitHub for the latest release. With [notify], a version you haven't been told about yet
     * gets a notification (once per version).
     */
    suspend fun check(context: Context, notify: Boolean): Result = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!checking.compareAndSet(false, true)) {
            return@withContext if (_available.value != null) Result.Available else Result.UpToDate
        }
        try {
            val release = fetch() ?: return@withContext Result.Failed
            val p = prefs(app)
            p.edit()
                .putString(KEY_LATEST, release.version)
                .putString(KEY_NOTES, release.whatsNew)
                .putLong(KEY_CHECKED_AT, System.currentTimeMillis())
                .apply()
            if (isNewer(release.version, BuildConfig.VERSION_NAME)) {
                _available.value = release
                if (notify && p.getString(KEY_NOTIFIED, null) != release.version && showNotification(app, release)) {
                    p.edit().putString(KEY_NOTIFIED, release.version).apply()
                }
                Result.Available
            } else {
                _available.value = null
                NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
                Result.UpToDate
            }
        } finally {
            checking.set(false)
        }
    }

    /** Hides the Today banner for this version (a newer one shows it again). */
    fun dismissBanner(context: Context) {
        val version = _available.value?.version ?: return
        prefs(context).edit().putString(KEY_DISMISSED, version).apply()
        _dismissed.value = version
    }

    /** Downloads the new APK in the browser; opening it installs over this one and keeps everything. */
    fun download(context: Context) = open(context, APK_URL)

    fun openReleasePage(context: Context) = open(context, RELEASES_URL)

    /** True if [remote] ("v0.1.48", "0.1.48") is a higher version than [current] ("0.1.47", "0.1.0-local"). */
    fun isNewer(remote: String, current: String): Boolean {
        val a = numbers(remote)
        val b = numbers(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String): List<Int> =
        version.trim().removePrefix("v").substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }

    /** Release notes are the commit message; its first line says what changed. */
    fun whatsNew(notes: String): String =
        notes.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("Co-Authored-By", ignoreCase = true) }
            ?.take(140)
            .orEmpty()

    private fun fetch(): Release? = try {
        val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Noctil-Android")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val version = o.optString("tag_name").removePrefix("v")
                if (version.isBlank() || o.optBoolean("draft") || o.optBoolean("prerelease")) null
                else Release(version, whatsNew(o.optString("body")))
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null // offline, GitHub busy or rate limited: try again next time
    }

    @SuppressLint("MissingPermission") // checked by Notifications.canNotify
    private fun showNotification(context: Context, release: Release): Boolean {
        if (!Notifications.canNotify(context)) return false
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val download = PendingIntent.getActivity(context, 73_002, Intent(Intent.ACTION_VIEW, APK_URL.toUri()), flags)
        val page = PendingIntent.getActivity(context, 73_003, Intent(Intent.ACTION_VIEW, RELEASES_URL.toUri()), flags)
        val text = if (release.whatsNew.isBlank()) "Tap to download it." else "New: ${release.whatsNew}"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB9A3FF.toInt())
            .setContentTitle("Noctil ${release.version} is out")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nTap to download. Opening it installs the update; your tasks stay."))
            .setContentIntent(download)
            .setAutoCancel(true)
            .ownGroup("update") // its own card, not bundled with the Today card
            .addAction(0, "Download", download)
            .addAction(0, "What's new", page)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When a new version of Noctil is out"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun schedule(context: Context) {
        val jobs = context.getSystemService(JobScheduler::class.java) ?: return
        if (jobs.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckJob::class.java))
            .setPeriodic(EVERY_MS, 2 * 60 * 60 * 1000L)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        runCatching { jobs.schedule(job) }
    }

    private fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // No browser installed: nothing to open.
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
