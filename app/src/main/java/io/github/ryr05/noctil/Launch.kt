package io.github.ryr05.noctil

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.ryr05.noctil.reminders.EXTRA_OPEN_TASK
import java.time.LocalDate

/** Intents that open the app at a specific place: from widgets, the lockscreen card, the tile and shortcuts. */
object Launch {
    const val ACTION_QUICK_ADD = "io.github.ryr05.noctil.action.QUICK_ADD"
    const val ACTION_FOCUS = "io.github.ryr05.noctil.action.FOCUS"
    const val ACTION_OPEN_TASK = "io.github.ryr05.noctil.action.OPEN_TASK"
    const val ACTION_OPEN_DATE = "io.github.ryr05.noctil.action.OPEN_DATE"
    const val ACTION_TODAY = "io.github.ryr05.noctil.action.TODAY"
    const val ACTION_HABITS = "io.github.ryr05.noctil.action.HABITS"
    const val EXTRA_OPEN_DATE = "io.github.ryr05.noctil.OPEN_DATE"
    const val EXTRA_START_FOCUS = "io.github.ryr05.noctil.START_FOCUS"

    private fun base(context: Context, action: String) =
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun quickAdd(context: Context): Intent = base(context, ACTION_QUICK_ADD)

    fun today(context: Context): Intent = base(context, ACTION_TODAY)

    fun habits(context: Context): Intent = base(context, ACTION_HABITS)

    fun focus(context: Context, startNext: Boolean = false): Intent =
        base(context, ACTION_FOCUS).putExtra(EXTRA_START_FOCUS, startNext)

    fun openTask(context: Context, id: Long): Intent = base(context, ACTION_OPEN_TASK).putExtra(EXTRA_OPEN_TASK, id)

    fun openDate(context: Context, date: LocalDate): Intent =
        base(context, ACTION_OPEN_DATE).putExtra(EXTRA_OPEN_DATE, date.toEpochDay())

    fun pending(context: Context, intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
