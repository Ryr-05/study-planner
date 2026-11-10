package io.github.ryr05.noctil.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import io.github.ryr05.noctil.NoctilApplication
import io.github.ryr05.noctil.reminders.LockscreenCard
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything outside the app that shows tasks: home screen widgets and the lockscreen card.
 * Refreshed whenever the data changes, and once just after midnight so "today" rolls over.
 */
object Surfaces {
    private const val ACTION_MIDNIGHT = "io.github.ryr05.noctil.MIDNIGHT_REFRESH"

    suspend fun refreshAll(context: Context) {
        val app = context.applicationContext as NoctilApplication
        runCatching { TodayWidget().updateAll(context) }
        runCatching { CalendarWidget().updateAll(context) }
        runCatching { ExamWidget().updateAll(context) }
        runCatching { HabitWidget().updateAll(context) }
        LockscreenCard.update(context, app.repository.snapshot.value)
    }

    fun scheduleMidnightRefresh(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).plusMinutes(1).toInstant().toEpochMilli()
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, MidnightReceiver::class.java).setAction(ACTION_MIDNIGHT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Inexact is fine here: a minute or two late doesn't matter for a date rollover.
        alarms.set(AlarmManager.RTC, at, pending)
    }
}

class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NoctilApplication
        val pending = goAsync()
        app.repository.scope.launch {
            try {
                Surfaces.refreshAll(context)
                Surfaces.scheduleMidnightRefresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
