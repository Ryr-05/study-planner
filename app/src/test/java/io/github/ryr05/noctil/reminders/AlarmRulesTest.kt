package io.github.ryr05.noctil.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRulesTest {
    @Test
    fun timedTasksRingWhileDueAlarmsAreOn() {
        assertTrue(AlarmPrefs.ringsDue(marked = false, dueAlarmsOn = true, hasTime = true))
    }

    @Test
    fun dateOnlyTasksStayANotification() {
        assertFalse(AlarmPrefs.ringsDue(marked = false, dueAlarmsOn = true, hasTime = false))
    }

    @Test
    fun aTaskMarkedToRingAlwaysRings() {
        assertTrue(AlarmPrefs.ringsDue(marked = true, dueAlarmsOn = false, hasTime = false))
        assertTrue(AlarmPrefs.ringsDue(marked = true, dueAlarmsOn = true, hasTime = false))
    }

    @Test
    fun withDueAlarmsOffOnlyMarkedTasksRing() {
        assertFalse(AlarmPrefs.ringsDue(marked = false, dueAlarmsOn = false, hasTime = true))
    }
}
