package io.github.ryr05.noctil.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.github.ryr05.noctil.NoctilApplication
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * "Remind me when I get to campus." Uses the phone's own geofencing (free, no maps account).
 * Locations never leave the phone except in your own cloud copy if you sign in.
 */
object Geofences {
    private var lastSignature: String? = null

    fun hasForeground(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Needed so arriving somewhere is noticed while Noctil is closed (Android 10 and newer). */
    fun hasBackground(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The phone's current position, for "Save this spot". Null if location is off or not allowed. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Pair<Double, Double>? {
        if (!hasForeground(context)) return null
        return runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val loc = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
                ?: client.lastLocation.await()
            loc?.let { it.latitude to it.longitude }
        }.getOrNull()
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        7_200,
        Intent(context, GeofenceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, // Play services fills in which fence fired
    )

    /** Registers one fence per open task that has a place. Skips the work when nothing changed. */
    @SuppressLint("MissingPermission")
    suspend fun refresh(context: Context) {
        val app = context.applicationContext as NoctilApplication
        val board = app.repository.db.board()
        val places = board.places().associateBy { it.id }
        val tasks = board.tasksWithPlaces().filter { places.containsKey(it.placeId) }
        val signature = tasks.joinToString { t -> places[t.placeId]!!.let { "${t.id}@${it.latitude},${it.longitude},${it.radius}" } }
        if (signature == lastSignature) return
        val client = LocationServices.getGeofencingClient(context)
        runCatching { client.removeGeofences(pendingIntent(context)).await() }
        lastSignature = signature
        if (tasks.isEmpty() || !hasForeground(context) || !hasBackground(context)) return
        val fences = tasks.take(95).map { t ->
            val p = places.getValue(t.placeId!!)
            Geofence.Builder()
                .setRequestId("task:${t.id}")
                .setCircularRegion(p.latitude, p.longitude, p.radius.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // only on arriving, not if you're already there
            .addGeofences(fences)
            .build()
        runCatching { client.addGeofences(request, pendingIntent(context)).await() }
            .onFailure { lastSignature = null }
    }
}

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val ids = event.triggeringGeofences.orEmpty().mapNotNull { it.requestId.removePrefix("task:").toLongOrNull() }
        if (ids.isEmpty()) return
        val app = context.applicationContext as NoctilApplication
        val pending = goAsync()
        app.repository.scope.launch {
            try {
                ids.forEach { id ->
                    val task = app.repository.task(id) ?: return@forEach
                    if (task.done || task.deleted) return@forEach
                    val place = task.placeId?.let { app.repository.db.board().place(it) }
                    Notifications.showReminder(context, task, app.repository.listName(task.listId), place?.name)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
