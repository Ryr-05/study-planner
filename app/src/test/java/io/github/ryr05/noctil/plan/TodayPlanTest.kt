package io.github.ryr05.noctil.plan

import io.github.ryr05.noctil.plan.TodayPlan.Answer
import io.github.ryr05.noctil.plan.TodayPlan.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TodayPlanTest {
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private val plan = TodayPlan.Saved(
        day = LocalDate.of(2026, 11, 10),
        blocks = listOf(
            Block(1, "Problem set", t(19), t(19, 45)),
            Block(2, "Essay", t(19, 50), t(20, 50)),
            Block(3, "Reading", t(20, 55), t(21, 25)),
        ),
        endAt = t(21, 30),
        checkInTaskId = 1,
    )

    @Test fun doneOnTimeKeepsTheRest() {
        val s = TodayPlan.adjust(plan, 1, Answer.Done, t(19, 45))
        assertTrue(s.blocks.first { it.taskId == 1L }.done)
        assertEquals(t(19, 50), s.blocks.first { it.taskId == 2L }.start)
        assertEquals(null, s.checkInTaskId)
        assertTrue(s.movedLater.isEmpty())
    }

    @Test fun moreTimePushesTheRestAndDropsWhatNoLongerFits() {
        val s = TodayPlan.adjust(plan, 1, Answer.More(25), t(19, 45))
        val extra = s.blocks.first { it.taskId == 1L && !it.done }
        assertEquals(t(19, 45), extra.start)
        assertEquals(t(20, 10), extra.end)
        assertEquals(t(20, 10), s.blocks.first { it.taskId == 2L }.start)
        // Reading would now end after 9:30 PM, so it moves to later.
        assertEquals(listOf("Reading"), s.movedLater)
        assertTrue(s.blocks.none { it.taskId == 3L })
    }

    @Test fun notTodayDropsTheTaskAndPullsNothingEarlierThanPlanned() {
        val s = TodayPlan.adjust(plan, 1, Answer.NotToday, t(19, 10))
        assertTrue(s.blocks.none { it.taskId == 1L })
        assertEquals(t(19, 50), s.blocks.first { it.taskId == 2L }.start)
    }

    @Test fun runningLateShiftsEverything() {
        val s = TodayPlan.adjust(plan, 1, Answer.Done, t(20, 2))
        assertEquals(t(20, 5), s.blocks.first { it.taskId == 2L }.start)
        assertEquals(listOf("Reading"), s.movedLater)
    }
}
