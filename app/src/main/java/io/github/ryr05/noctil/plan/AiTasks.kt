package io.github.ryr05.noctil.plan

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import io.github.ryr05.noctil.BuildConfig
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** What Gemini read out of the text: quick-add lines, and a note about anything it skipped. */
data class AiRead(val tasks: List<String>, val note: String) {
    companion object {
        fun parse(json: String): AiRead {
            val o = JSONObject(json.trim().removePrefix("```json").removePrefix("```").removeSuffix("```"))
            val tasks = o.optJSONArray("tasks")?.let { a ->
                (0 until a.length()).mapNotNull { a.optString(it).trim().takeIf { s -> s.count(Char::isLetter) >= 2 } }
            }.orEmpty()
            return AiRead(tasks.take(MAX_TASKS), o.optString("note").trim())
        }

        const val MAX_TASKS = 40
    }
}

/**
 * Turns a messy message or a pasted syllabus into tasks with Gemini. One request per tap; the
 * student reviews every task before anything is added.
 */
class GeminiTaskReader {
    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = AiPlanning.MODEL,
            generationConfig = generationConfig {
                temperature = 0.1f
                responseMimeType = "application/json"
                responseSchema = SCHEMA
            },
            systemInstruction = content { text(SYSTEM) },
            requestOptions = RequestOptions(timeoutInMillis = 45_000),
        )
    }

    suspend fun read(text: String, now: LocalDateTime, tags: List<String>): AiRead {
        val prompt = "now: ${now.format(DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm"))}\n" +
            "existing_tags: ${tags.take(40).joinToString(", ")}\n\nTEXT:\n${text.take(MAX_CHARS)}"
        val raw = model.generateContent(prompt).text ?: error("Empty reply")
        if (BuildConfig.DEBUG) android.util.Log.d("NoctilAI", "read: $raw")
        return AiRead.parse(raw)
    }

    companion object {
        const val MAX_CHARS = 12_000

        private val SYSTEM = """
            You turn a college student's text into tasks for the Noctil planner app. The text may be a list, a message
            or a pasted course syllabus. You only extract tasks; you never do the work or answer questions about it.

            Write each task as one line in Noctil's quick add style:
            - A short title (at most 8 words), like "Lab report 3" or "Read chapter 4".
            - Then the due date as month and day, like "nov 20" (add the year only if it isn't this year).
              Turn weekdays and words like "tomorrow" into month and day using "now".
            - Then the due time if the text gives one, like "5pm" or "11:59pm".
            - Then "start" with a date and time only if the student says when they will start, like "start nov 19 7pm".
            - Then one tag for the course, like "#chem", only if the text names a course or subject. Reuse an existing tag
              when one fits. No spaces in tags. Never make up tags like #admin or #personal.
            - Then "!1" only if the text says it is important or urgent.
            Example: "Lab report 3 nov 20 11:59pm #chem"

            Rules:
            - Never invent tasks, dates or times. If a task has no date, write just its title (and tag).
            - Skip items whose dates have already passed, and things that aren't tasks: lecture topics, office hours,
              grading policies, readings with no due date in a syllabus schedule unless the student listed them as tasks.
            - Exams and quizzes are tasks too: "Chem midterm nov 24 10am #chem".
            - At most 40 tasks, soonest first.
            - note: one short sentence if you skipped something or a date was unclear, otherwise empty. No emoji, no em dashes.
        """.trimIndent()

        private val SCHEMA = Schema.obj(
            properties = mapOf(
                "tasks" to Schema.array(Schema.string(), description = "One quick-add line per task"),
                "note" to Schema.string(description = "One short sentence about anything skipped or unclear, or empty"),
            ),
        )
    }
}
