package io.github.ryr05.noctil.reminders

import android.content.Context
import io.github.ryr05.noctil.reminders.StartReminders.Lead
import io.github.ryr05.noctil.reminders.StartReminders.Size

/**
 * Learns when you like to start, with nothing to set up. Every task added with a deadline
 * records the start you kept (or that you removed it), per task size. When two of your last
 * three choices agree, that becomes the suggestion. Stays on this phone.
 */
object StartHabits {
    private const val PREFS = "start_habits"
    private const val KEEP = 3
    private const val OFF = "off"

    /** What your recent choices say, or the built-in default when they don't agree yet. */
    fun leadFor(context: Context, size: Size): Lead? = when (val habit = learned(history(context, size))) {
        Habit.None -> StartReminders.defaultLead(size)
        Habit.Off -> null
        is Habit.At -> habit.lead
    }

    sealed interface Habit {
        data object None : Habit
        data object Off : Habit
        data class At(val lead: Lead) : Habit
    }

    /** Records what was kept for a task of this size: a start [lead], or null when you removed it. */
    fun record(context: Context, size: Size, lead: Lead?) {
        val list = (history(context, size) + encode(lead)).takeLast(KEEP)
        prefs(context).edit().putString(size.name, list.joinToString(",")).apply()
    }

    /** Pure, for tests: the choice made at least twice in the last three, if any. */
    fun learned(history: List<String>): Habit {
        val agreed = history.takeLast(KEEP).groupingBy { it }.eachCount().entries.firstOrNull { it.value >= 2 }?.key
            ?: return Habit.None
        if (agreed == OFF) return Habit.Off
        return Lead.decode(agreed)?.let { Habit.At(it) } ?: Habit.None
    }

    /** The stored form of one choice, for tests. */
    fun encode(lead: Lead?): String = lead?.encode() ?: OFF

    private fun history(context: Context, size: Size): List<String> =
        prefs(context).getString(size.name, null)?.split(",")?.filter { it.isNotBlank() }.orEmpty()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
