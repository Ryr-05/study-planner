package io.github.ryr05.noctil.plan

import android.content.Context
import android.telephony.TelephonyManager
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import io.github.ryr05.noctil.BuildConfig
import io.github.ryr05.noctil.data.model.Habit
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.data.model.TaskItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Noctil AI with Google's Gemini (through Firebase AI Logic, free tier). Off until the student
 * turns it on. Sent: what they type, their open tasks (title, dates, start, priority, list, tags,
 * length), classes (name, days, times) and habits (name, goal, streaks). Never notes, checklists,
 * rooms or places.
 */
object AiPlanning {
    /** Gemini model used for planning. Flash-Lite is fast and in the free tier. */
    const val MODEL = "gemini-3.1-flash-lite"
    const val DAILY_LIMIT = 100
    private const val PREFS = "ai"

    enum class Status { Ready, Off, NotInBuild, Region, Limit }

    /** EEA, Switzerland and the UK: Gemini's free tier can't be offered there. */
    private val blockedCountries = setOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU",
        "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "IS", "LI", "NO", "CH", "GB",
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)

    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("enabled", on).apply()

    fun inBlockedRegion(context: Context): Boolean {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val codes = listOfNotNull(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
            .map { it.uppercase() }
            .filter { it.isNotBlank() }
        return codes.any { it in blockedCountries }
    }

    fun status(context: Context): Status = when {
        !BuildConfig.CLOUD_SYNC -> Status.NotInBuild
        inBlockedRegion(context) -> Status.Region
        !enabled(context) -> Status.Off
        usedToday(context) >= DAILY_LIMIT -> Status.Limit
        else -> Status.Ready
    }

    fun usedToday(context: Context): Int {
        val p = prefs(context)
        return if (p.getString("day", "") == LocalDate.now().toString()) p.getInt("count", 0) else 0
    }

    fun countOne(context: Context) {
        val used = usedToday(context)
        prefs(context).edit().putString("day", LocalDate.now().toString()).putInt("count", used + 1).apply()
    }
}

/** What Gemini decided to do with a message. The app does the actual work. */
data class AiReply(
    val reply: String,
    val action: String,
    val options: List<String> = emptyList(),
    val newTasks: List<String> = emptyList(),
    val minutesAvailable: Int? = null,
    val lengths: Map<Long, Int> = emptyMap(),
    val favored: Set<Long> = emptySet(),
    val changes: List<AiChange> = emptyList(),
    val planTaskIds: List<Long> = emptyList(),
    val newHabits: List<AiHabit> = emptyList(),
) {
    companion object {
        fun parse(json: String): AiReply {
            val o = JSONObject(json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```"))
            fun strings(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }.orEmpty()
            fun longs(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optLong(it) }.filter { it > 0 } }.orEmpty()
            val lengths = o.optJSONArray("lengths")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { it.optLong("id") to it.optInt("minutes") } }
                    .filter { it.second in 5..600 }
                    .toMap()
            }.orEmpty()
            val minutes = if (o.has("minutes_available") && !o.isNull("minutes_available")) o.optInt("minutes_available") else null
            return AiReply(
                reply = o.optString("reply").ifBlank { "Okay." },
                action = o.optString("action", "chat"),
                options = strings("options").take(5),
                newTasks = strings("new_tasks").take(20),
                minutesAvailable = minutes?.takeIf { it == Planner.REST_OF_DAY || it in 5..16 * 60 },
                lengths = lengths,
                favored = longs("favored_ids").toSet(),
                changes = o.optJSONArray("changes")?.let { a ->
                    (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(AiChange::parse) }
                }.orEmpty().take(30),
                planTaskIds = longs("plan_task_ids").take(12),
                newHabits = o.optJSONArray("new_habits")?.let { a ->
                    (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(AiHabit::parse) }
                }.orEmpty().take(5),
            )
        }
    }
}

/** What the assistant is shown along with each message. Built fresh every time. */
data class AiContext(
    val tasks: List<TaskItem>,
    val classes: List<SchoolClass>,
    val habits: List<Habit>,
    val lists: List<String>,
    val answers: Planner.Answers,
)

/**
 * One Noctil AI conversation with Gemini. Keeps the last few turns; earlier turns (from a saved
 * chat) can be passed in so a reopened chat continues where it left off.
 */
class GeminiAssistant(earlier: List<Pair<Boolean, String>> = emptyList()) {
    private val history = mutableListOf<Content>()
    // The same conversation in plain (role, text) form, for the backup AI.
    private val plain = mutableListOf<Pair<String, String>>()

    /** "gemini" or "backup": which AI answered last. */
    var lastProvider = "gemini"
        private set

    init {
        // A saved chat comes back as plain lines; the model sees them as its own earlier replies.
        earlier.takeLast(MAX_HISTORY).forEach { (fromStudent, text) ->
            val asModel = JSONObject().put("reply", text).put("action", "chat").toString()
            history += if (fromStudent) content(role = "user") { text("Student: $text") } else content(role = "model") { text(asModel) }
            plain += if (fromStudent) "user" to "Student: $text" else "assistant" to asModel
        }
    }

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = AiPlanning.MODEL,
            generationConfig = generationConfig {
                temperature = 0.3f
                responseMimeType = "application/json"
                responseSchema = SCHEMA
            },
            systemInstruction = content { text(SYSTEM) },
            requestOptions = RequestOptions(timeoutInMillis = 45_000),
        )
    }

    /** Sends one message. Throws if Gemini can't be reached, so the caller can fall back to offline. */
    suspend fun send(message: String, ctx: AiContext, now: LocalDateTime): AiReply {
        val prompt = "${contextBlock(ctx, now)}\n\nStudent: $message"
        // Gemini first; if it's out of free messages or down, the backup AI on Noctil's relay.
        val raw = runCatching {
            if (BuildConfig.DEBUG && AiRelay.forceInDebug) error("Testing the backup AI")
            val turn = content(role = "user") { text(prompt) }
            model.generateContent(history + turn).text ?: error("Empty reply")
        }.onSuccess { lastProvider = "gemini" }.recoverCatching { e ->
            if (!AiRelay.available) throw e
            android.util.Log.w("NoctilAI", "Gemini failed (${e.javaClass.simpleName}), using the backup AI")
            AiRelay.chat(listOf("system" to SYSTEM + RELAY_FORMAT) + plain + ("user" to prompt)).also { lastProvider = "backup" }
        }.getOrThrow()
        if (BuildConfig.DEBUG) android.util.Log.d("NoctilAI", "reply: $raw")
        val reply = AiReply.parse(raw)
        history += content(role = "user") { text("Student: $message") }
        history += content(role = "model") { text(raw) }
        plain += "user" to "Student: $message"
        plain += "assistant" to raw
        while (history.size > MAX_HISTORY) history.removeAt(0)
        while (plain.size > MAX_HISTORY) plain.removeAt(0)
        return reply
    }

    private fun contextBlock(ctx: AiContext, now: LocalDateTime): String {
        val today = now.toLocalDate()
        val tasks = JSONArray()
        ctx.tasks.forEach { t ->
            tasks.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("due_date", t.date?.toString() ?: JSONObject.NULL)
                    .put("due_time", t.time?.toString() ?: JSONObject.NULL)
                    .put("start", t.startAt?.toString() ?: JSONObject.NULL)
                    .put("priority", if (t.priority == Priority.None) "none" else t.priority.label.lowercase())
                    .put("list", t.list?.name ?: JSONObject.NULL)
                    .put("tags", JSONArray(t.tags.map { it.name }))
                    .put("length_minutes", ctx.answers.lengths[t.id] ?: t.durationMinutes ?: JSONObject.NULL),
            )
        }
        val classes = JSONArray()
        ctx.classes.forEach { c ->
            classes.put(
                JSONObject()
                    .put("name", c.name)
                    .put("days", JSONArray(c.days.sortedBy { it.value }.map { it.name.take(3).lowercase() }))
                    .put("start", c.start.toString())
                    .put("end", c.end.toString()),
            )
        }
        val habits = JSONArray()
        ctx.habits.forEach { h ->
            habits.put(
                JSONObject()
                    .put("name", h.name)
                    .put("goal_per_day", h.goal)
                    .put("days", if (h.everyDay) JSONArray(listOf("daily")) else JSONArray(h.days.sortedBy { it.value }.map { it.name.take(3).lowercase() }))
                    .put("today", if (!h.isDue(today)) "not due" else "${h.count(today)} of ${h.goal}")
                    .put("streak_days", h.currentStreak(today))
                    .put("best_streak_days", h.bestStreak(today)),
            )
        }
        val known = JSONObject()
            .put("minutes_available", ctx.answers.minutesAvailable ?: JSONObject.NULL)
            .put("favored_ids", JSONArray(ctx.answers.favored.toList()))
        return "CONTEXT\nnow: ${now.format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm"))}\n" +
            "agenda:\n${agenda(ctx, today)}\n" +
            "tasks: $tasks\nclasses: $classes\nhabits: $habits\nlists: ${JSONArray(ctx.lists)}\nknown: $known"
    }

    /**
     * Overdue tasks, then the next 14 days, day by day, with each day's tasks and classes. The
     * free model answers "what's due" far more reliably reading this than scanning every task.
     */
    private fun agenda(ctx: AiContext, today: LocalDate): String = buildString {
        val fmt = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)
        val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        val overdue = ctx.tasks.filter { it.date?.isBefore(today) == true }
        if (overdue.isNotEmpty()) appendLine("Overdue: " + overdue.joinToString("; ") { "${it.title} (was due ${it.date!!.format(fmt)})" })
        for (offset in 0L..13L) {
            val day = today.plusDays(offset)
            val due = ctx.tasks.filter { it.date == day }.sortedBy { it.time ?: java.time.LocalTime.MAX }
            val classes = ctx.classes.filter { it.meetsOn(day) }.sortedBy { it.start }
            val label = when (offset) {
                0L -> "Today ${day.format(fmt)}"
                1L -> "Tomorrow ${day.format(fmt)}"
                else -> day.format(fmt)
            }
            val parts = buildList {
                add("due: " + if (due.isEmpty()) "nothing" else due.joinToString("; ") { t -> t.title + (t.time?.let { " at ${it.format(clock)}" } ?: "") })
                if (classes.isNotEmpty()) add("classes: " + classes.joinToString("; ") { "${it.name} ${it.start.format(clock)} to ${it.end.format(clock)}" })
            }
            appendLine("$label | " + parts.joinToString(" | "))
        }
        val later = ctx.tasks.count { it.date?.isAfter(today.plusDays(13)) == true }
        if (later > 0) appendLine("Later: $later more tasks, see tasks")
    }

    companion object {
        private const val MAX_HISTORY = 12

        /** Gemini gets the format as a schema; other models get it in words. */
        private val RELAY_FORMAT = """

            Reply with only one JSON object, no other text, with these fields:
            {"reply": string, "action": one of "chat","ask","add_tasks","plan","remind","change","plan_week","add_habits",
             "options": [string] (only for ask), "new_tasks": [string], "minutes_available": integer (optional),
             "lengths": [{"id": number, "minutes": integer}], "favored_ids": [number], "plan_task_ids": [number],
             "changes": [{"id": number, "start": "YYYY-MM-DDTHH:MM" or "none", "due_date": "YYYY-MM-DD", "due_time": "HH:MM",
                          "title": string, "priority": "high"|"medium"|"low"|"none", "tags_add": [string], "list": string, "done": boolean}],
             "new_habits": [{"name": string, "goal": integer, "days": [string], "reminder": "HH:MM" or ""}]}
            Leave out fields you don't need, but always include reply, action, lengths and favored_ids (empty lists are fine).
        """.trimIndent()

        private val SYSTEM = """
            You are Noctil AI, the assistant inside Noctil, a planner app for college students.
            You help only with the student's own schedule: answer questions about their tasks, deadlines, classes and
            habits; plan a study session or their week; add and change tasks; set up habits; and set reminders.
            You never do homework, write essays, solve problems or explain course material, and you don't chat about
            other topics. If asked, say in one short sentence that you only help with their schedule, then offer
            something you can do.

            CONTEXT lists: now; tasks (every open task, with its id); classes (weekly timetable); habits; lists; known.
            Use only what is in CONTEXT or what the student tells you. Never invent tasks, dates or facts.

            Questions ("what's due this week?", "when's my next exam?", "am I free thursday afternoon?", "how's my
            water streak?"): answer from CONTEXT with dates like "Thu, Nov 19". For anything about days or what's due,
            read agenda, which lists every day and everything due on it; include every task it lists for the days asked
            about, and mention overdue ones. "This week" means today and the next 6 days. Use action "chat".

            Adding tasks: whenever the student mentions tasks that aren't in tasks, put each one in new_tasks, written
            like quick add: "Lab report tomorrow 9am !1", "Chem midterm tue #exam". Add "start thu 7pm" only if they said
            when they'll start. Never repeat tasks that already exist.

            Changing tasks: use action "change" and put one item per task in changes, with its id:
            - start: "YYYY-MM-DDTHH:MM", or "none" to remove the start reminder. It must be before the deadline.
            - title, priority ("high", "medium", "low" or "none"), tags_add (names without #), list (one of lists),
              done (true to mark it finished).
            - Change only what they asked for. You can't delete tasks or habits; if asked, say they can do that in the app.
            - Deadlines: fill due_date or due_time ONLY when the student explicitly asks to change a deadline or due date,
              or says the class moved it ("my prof moved the quiz to friday"). "I'll do it later" means change the
              start, not the deadline.

            Planning a session (right now):
            - Ask at most one short question at a time (action "ask", with 2 to 5 short options), only when you can't
              plan without it: how much time they have, or how long a big task takes.
            - Once you know the time, use action "plan": fill minutes_available (-1 for the rest of the day) and
              plan_task_ids with the tasks to work on. The app orders them and builds the timed plan.
            - Whenever they say how long a task takes, put it in lengths as {id, minutes}. Whenever they say something
              matters more, put its id in favored_ids. Don't ask which task to do first.
            - If they want reminders for that plan, use action "remind".

            Planning the week: use action "plan_week" and give start times in changes as {id, start} for open tasks due
            in the next 7 days without an upcoming start. Spread them so no day has more than 2, keep clear of classes,
            prefer 19:00 unless they say otherwise. Exams, essays and projects start 2 to 3 days before; small tasks 1
            day before. The app shows the plan for them to approve.

            Habits: to set one up use action "add_habits" with new_habits items {name, goal (times a day, usually 1),
            days (["daily"], ["weekdays"] or like ["mon","wed"]), reminder ("HH:MM" or "")}.

            Always:
            - The app carries out every action and tells the student what it did. Never say you added, changed,
              planned or set anything. With "plan", say in one or two sentences what to start with and why.
            - Keep reply to 1 to 3 short, warm sentences in plain English (a list of what's due can be longer). Write
              times like "9 AM" or "11:59 PM". No emoji. Do not use em dashes.
            - Never ask for personal information.
        """.trimIndent()

        private val SCHEMA = Schema.obj(
            properties = mapOf(
                "reply" to Schema.string(description = "1 to 3 short sentences to the student"),
                "action" to Schema.enumeration(listOf("chat", "ask", "add_tasks", "plan", "remind", "change", "plan_week", "add_habits")),
                "options" to Schema.array(Schema.string(), description = "2 to 5 short tap-able answers when action is ask"),
                "new_tasks" to Schema.array(Schema.string(), description = "New tasks the student mentioned, quick-add style, one per item"),
                "minutes_available" to Schema.integer(description = "Free minutes right now, -1 for the rest of the day"),
                "lengths" to Schema.array(
                    Schema.obj(mapOf("id" to Schema.long(), "minutes" to Schema.integer())),
                    description = "How long tasks take, by task id, whenever the student said so",
                ),
                "favored_ids" to Schema.array(Schema.long(), description = "Ids of tasks the student said matter more or come first"),
                "plan_task_ids" to Schema.array(Schema.long(), description = "Ids of tasks to include in a session plan"),
                "changes" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "id" to Schema.long(),
                            "start" to Schema.string(description = "New start as YYYY-MM-DDTHH:MM, or none to remove it"),
                            "due_date" to Schema.string(description = "YYYY-MM-DD, only if the student asked to change the deadline"),
                            "due_time" to Schema.string(description = "HH:MM, only if the student asked to change the deadline"),
                            "title" to Schema.string(description = "New name, only if asked"),
                            "priority" to Schema.enumeration(listOf("high", "medium", "low", "none")),
                            "tags_add" to Schema.array(Schema.string(), description = "Tags to add, without #"),
                            "list" to Schema.string(description = "Move to this list (one of lists)"),
                            "done" to Schema.boolean(description = "true to mark the task finished"),
                        ),
                        optionalProperties = listOf("start", "due_date", "due_time", "title", "priority", "tags_add", "list", "done"),
                    ),
                    description = "Changes to existing tasks the student asked for, or start times for plan_week",
                ),
                "new_habits" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "name" to Schema.string(),
                            "goal" to Schema.integer(description = "Times a day, usually 1"),
                            "days" to Schema.array(Schema.string(), description = "daily, weekdays, weekends, or mon..sun"),
                            "reminder" to Schema.string(description = "HH:MM or empty"),
                        ),
                        optionalProperties = listOf("days", "reminder"),
                    ),
                    description = "Habits to set up when action is add_habits",
                ),
            ),
            optionalProperties = listOf("options", "new_tasks", "minutes_available", "changes", "plan_task_ids", "new_habits"),
        )
    }
}
