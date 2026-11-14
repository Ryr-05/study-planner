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
import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.TaskItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * AI planning with Google's Gemini (through Firebase AI Logic, free tier). Off until the student
 * turns it on. Only the tasks they picked are sent: title, date, time, priority and length.
 */
object AiPlanning {
    /** Gemini model used for planning. Flash-Lite is fast and in the free tier. */
    const val MODEL = "gemini-3.1-flash-lite"
    const val DAILY_LIMIT = 40
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
) {
    companion object {
        fun parse(json: String): AiReply {
            val o = JSONObject(json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```"))
            fun strings(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }.orEmpty()
            val lengths = o.optJSONArray("lengths")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { it.optLong("id") to it.optInt("minutes") } }
                    .filter { it.second in 5..600 }
                    .toMap()
            }.orEmpty()
            val favored = o.optJSONArray("favored_ids")?.let { a -> (0 until a.length()).map { a.optLong(it) }.toSet() }.orEmpty()
            val minutes = if (o.has("minutes_available") && !o.isNull("minutes_available")) o.optInt("minutes_available") else null
            return AiReply(
                reply = o.optString("reply").ifBlank { "Okay." },
                action = o.optString("action", "chat"),
                options = strings("options").take(5),
                newTasks = strings("new_tasks").take(12),
                minutesAvailable = minutes?.takeIf { it == Planner.REST_OF_DAY || it in 5..16 * 60 },
                lengths = lengths,
                favored = favored,
            )
        }
    }
}

/** One conversation with Gemini about planning. Keeps a short history; nothing is stored. */
class GeminiPlanner {
    private val history = mutableListOf<Content>()

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = AiPlanning.MODEL,
            generationConfig = generationConfig {
                temperature = 0.3f
                responseMimeType = "application/json"
                responseSchema = SCHEMA
            },
            systemInstruction = content { text(SYSTEM) },
            requestOptions = RequestOptions(timeoutInMillis = 20_000),
        )
    }

    /**
     * Sends the student's message with the picked tasks and what's known so far. Throws if
     * Gemini can't be reached, so the caller can fall back to the offline planner.
     */
    suspend fun send(message: String, picked: List<TaskItem>, answers: Planner.Answers, now: LocalDateTime): AiReply {
        val ctx = contextBlock(picked, answers, now)
        val turn = content(role = "user") { text("$ctx\n\nStudent: $message") }
        val response = model.generateContent(history + turn)
        val raw = response.text ?: error("Empty reply")
        if (BuildConfig.DEBUG) android.util.Log.d("NoctilAI", "reply: $raw")
        val reply = AiReply.parse(raw)
        history += content(role = "user") { text("Student: $message") }
        history += content(role = "model") { text(raw) }
        while (history.size > 16) history.removeAt(0)
        return reply
    }

    private fun contextBlock(picked: List<TaskItem>, answers: Planner.Answers, now: LocalDateTime): String {
        val tasks = JSONArray()
        picked.forEach { t ->
            tasks.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("due_date", t.date?.toString() ?: JSONObject.NULL)
                    .put("due_time", t.time?.toString() ?: JSONObject.NULL)
                    .put("priority", if (t.priority == Priority.None) "none" else t.priority.label.lowercase())
                    .put("length_minutes", answers.lengths[t.id] ?: t.durationMinutes ?: JSONObject.NULL),
            )
        }
        val known = JSONObject()
            .put("minutes_available", answers.minutesAvailable ?: JSONObject.NULL)
            .put("favored_ids", JSONArray(answers.favored.toList()))
        return "CONTEXT\nnow: ${now.format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm"))}\n" +
            "picked_tasks: $tasks\nknown: $known"
    }

    companion object {
        private val SYSTEM = """
            You are the planning helper inside Noctil, a planner app for college students.
            Your only jobs: help the student decide what to work on now, in what order, for the time they have, and set reminders.
            You never do homework, write essays, solve problems or answer questions about course material, and you don't chat about other topics.
            If asked for anything else, say in one short sentence that you can only help plan and remind, then steer back to planning.

            Rules:
            - Use only the tasks in picked_tasks or tasks the student types. Never invent tasks, dates or facts.
            - Whenever the student mentions tasks that are not already in picked_tasks, put each one in new_tasks in that same reply,
              as a single line written like quick add: the title plus its date, time and priority words, for example
              "Lab report tomorrow 9am !1" or "Chem midterm tue #exam". Never repeat tasks already in picked_tasks.
            - The app puts tasks in order and builds the timed schedule itself, so don't ask which task to do first.
            - Ask at most one short question at a time, and only when you can't plan without it: how much time they have now,
              or how long a big task will take. With a question, use action "ask" and give 2 to 5 short options the student can tap.
            - As soon as you know how much time they have, use action "plan" (even in the same reply that adds new tasks) and fill
              minutes_available (use -1 for the rest of the day), lengths for tasks whose length you learned, and favored_ids
              for tasks the student said matter more.
            - Use action "add_tasks" only when you added tasks but still need to ask how much time they have; then ask it in reply
              and offer options.
            - Good order: overdue and due soon first, study time before exams, high priority first, quick wins when time is short.
            - If the student wants reminders for the plan, use action "remind".
            - The app carries out every action and confirms it itself. Never say you added tasks, made a schedule or set
              reminders; with "plan", briefly say what to start with and why, in one or two sentences.
            - Otherwise use action "chat".
            - Keep reply to 1 to 3 short, warm sentences in plain English. No emoji. Do not use em dashes.
            - Never ask for personal information.
        """.trimIndent()

        private val SCHEMA = Schema.obj(
            properties = mapOf(
                "reply" to Schema.string(),
                "action" to Schema.enumeration(listOf("chat", "ask", "add_tasks", "plan", "remind")),
                "options" to Schema.array(Schema.string()),
                "new_tasks" to Schema.array(Schema.string()),
                "minutes_available" to Schema.integer(),
                "lengths" to Schema.array(Schema.obj(mapOf("id" to Schema.long(), "minutes" to Schema.integer()))),
                "favored_ids" to Schema.array(Schema.long()),
            ),
            optionalProperties = listOf("options", "new_tasks", "minutes_available", "lengths", "favored_ids"),
        )
    }
}
