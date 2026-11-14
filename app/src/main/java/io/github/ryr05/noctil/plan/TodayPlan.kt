package io.github.ryr05.noctil.plan

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Today's plan, saved on the phone so it outlives the chat it was made in. It shows in Focus
 * and on the Noctil AI tab, and when a planned focus block ends it asks "Done, or more time?"
 * and shifts the rest of the plan to match.
 */
object TodayPlan {
    data class Block(val taskId: Long, val title: String, val start: LocalTime, val end: LocalTime, val done: Boolean = false) {
        val minutes: Int get() = ChronoUnit.MINUTES.between(start, end).toInt()
    }

    data class Saved(
        val day: LocalDate,
        val blocks: List<Block>,
        /** When the student's free time ends; blocks pushed past it move to later. */
        val endAt: LocalTime,
        /** A planned focus block just ended and is waiting for "Done, or more time?". */
        val checkInTaskId: Long? = null,
        /** Titles moved off today's plan by the last change, to tell the student. */
        val movedLater: List<String> = emptyList(),
    ) {
        val next: Block? get() = blocks.firstOrNull { !it.done }
    }

    sealed interface Answer {
        data object Done : Answer
        data class More(val minutes: Int) : Answer
        data object NotToday : Answer
    }

    private const val PREFS = "today_plan"
    private val _state = MutableStateFlow<Saved?>(null)
    val state: StateFlow<Saved?> = _state

    fun init(context: Context) {
        _state.value = runCatching { decode(prefs(context).getString("plan", null) ?: return) }.getOrNull()
            ?.takeIf { it.day == LocalDate.now() }
    }

    fun save(context: Context, plan: Planner.Plan, day: LocalDate = LocalDate.now()) {
        val blocks = plan.work.map { Block(it.taskId, it.title + if (it.parts > 1) " (${it.part}/${it.parts})" else "", it.start, it.end) }
        if (blocks.isEmpty()) return
        set(context, Saved(day, blocks, plan.steps.maxOf { it.end }))
    }

    fun clear(context: Context) = set(context, null)

    /** A focus session on [taskId] finished: ask about it if it's in today's plan. */
    fun onFocusFinished(context: Context, taskId: Long) {
        val s = _state.value?.takeIf { it.day == LocalDate.now() } ?: return
        if (s.blocks.any { it.taskId == taskId && !it.done }) set(context, s.copy(checkInTaskId = taskId))
    }

    fun answer(context: Context, answer: Answer, now: LocalTime = LocalTime.now()) {
        val s = _state.value ?: return
        val id = s.checkInTaskId ?: s.next?.taskId ?: return
        val updated = adjust(s, id, answer, now)
        set(context, updated)
        // Keep the "time for" nudges in step with the new times.
        PlanReminders.schedule(context, toPlan(updated))
    }

    /**
     * The re-plan, kept pure for tests. Done or Not today: that task leaves the plan and the rest
     * starts no earlier than now. More time: the task gets [Answer.More] minutes from now and
     * everything after waits for it. Blocks that no longer end before [Saved.endAt] move to later.
     */
    fun adjust(s: Saved, taskId: Long, answer: Answer, now: LocalTime): Saved {
        val fromNow = roundUp(now)
        val blocks = s.blocks.toMutableList()
        when (answer) {
            Answer.Done -> blocks.replaceAll { if (it.taskId == taskId) it.copy(done = true) else it }
            Answer.NotToday -> blocks.removeAll { it.taskId == taskId && !it.done }
            is Answer.More -> {
                val index = blocks.indexOfFirst { it.taskId == taskId && !it.done }.coerceAtLeast(0)
                // The block that just ended counts as done; the extra time is the new current block.
                if (index < blocks.size && blocks[index].taskId == taskId) blocks[index] = blocks[index].copy(done = true)
                val title = s.blocks.firstOrNull { it.taskId == taskId }?.title?.substringBefore(" (") ?: ""
                blocks.add(index, Block(taskId, title, fromNow, fromNow.plusMinutes(answer.minutes.toLong())))
            }
        }
        // Shift what's left so nothing starts before the block in front of it ends.
        var cursor = fromNow
        var keep = answer is Answer.More // the extra time itself always stays, even past the planned end
        val moved = mutableListOf<String>()
        val shifted = blocks.mapNotNull { b ->
            if (b.done) return@mapNotNull b
            val start = maxOf(b.start, cursor)
            val end = start.plusMinutes(b.minutes.toLong())
            val fits = !end.isBefore(start) && !end.isAfter(s.endAt)
            if (!fits && !keep) {
                moved += b.title
                null
            } else {
                keep = false
                cursor = end
                b.copy(start = start, end = end)
            }
        }
        return s.copy(blocks = shifted, checkInTaskId = null, movedLater = moved)
    }

    fun toPlan(s: Saved): Planner.Plan = Planner.Plan(
        s.blocks.filter { !it.done }.map { Planner.Step.Work(it.taskId, it.title, it.start, it.end, "In today's plan") },
        emptyList(),
    )

    private fun roundUp(t: LocalTime): LocalTime {
        val m = t.hour * 60 + t.minute
        val up = ((m + 4) / 5) * 5
        return if (up >= 24 * 60) LocalTime.of(23, 55) else LocalTime.of(up / 60, up % 60)
    }

    private fun set(context: Context, s: Saved?) {
        _state.value = s
        prefs(context).edit().putString("plan", s?.let { encode(it).toString() }).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun encode(s: Saved): JSONObject = JSONObject()
        .put("day", s.day.toString())
        .put("end", s.endAt.toString())
        .put("checkIn", s.checkInTaskId ?: -1L)
        .put("blocks", JSONArray(s.blocks.map { JSONObject().put("id", it.taskId).put("title", it.title).put("start", it.start.toString()).put("end", it.end.toString()).put("done", it.done) }))

    fun decode(text: String): Saved {
        val o = JSONObject(text)
        val arr = o.getJSONArray("blocks")
        val blocks = (0 until arr.length()).map { i ->
            val b = arr.getJSONObject(i)
            Block(b.getLong("id"), b.getString("title"), LocalTime.parse(b.getString("start")), LocalTime.parse(b.getString("end")), b.optBoolean("done"))
        }
        return Saved(LocalDate.parse(o.getString("day")), blocks, LocalTime.parse(o.getString("end")), o.optLong("checkIn", -1L).takeIf { it > 0 })
    }
}
