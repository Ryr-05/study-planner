package io.github.ryr05.noctil.ui.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Recent Noctil AI chats, saved on this phone only (never synced or sent anywhere). Chats older
 * than [KEEP_DAYS] days are removed. Rich cards are saved as plain text notes, so an old chat
 * reads back fine but its buttons don't come back.
 */
object ChatStore {
    const val KEEP_DAYS = 7L

    enum class Role { User, Bot, Note }

    data class Line(val role: Role, val text: String)

    data class Chat(val id: String, val title: String, val updatedAt: Long, val lines: List<Line>) {
        val preview: String get() = lines.lastOrNull { it.role != Role.User }?.text ?: lines.lastOrNull()?.text.orEmpty()
    }

    private fun dir(context: Context) = File(context.filesDir, "chats").apply { mkdirs() }

    private fun file(context: Context, id: String) = File(dir(context), "${id.filter { it.isLetterOrDigit() || it == '-' }}.json")

    /** Saved chats, newest first. Drops ones older than a week. */
    fun list(context: Context, now: Long = System.currentTimeMillis()): List<Chat> {
        val cutoff = now - KEEP_DAYS * 24 * 60 * 60 * 1000
        return dir(context).listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
            val chat = runCatching { decode(f.readText()) }.getOrNull()
            if (chat == null || chat.updatedAt < cutoff) {
                f.delete()
                null
            } else {
                chat
            }
        }.sortedByDescending { it.updatedAt }
    }

    fun load(context: Context, id: String): Chat? = runCatching { decode(file(context, id).readText()) }.getOrNull()

    fun save(context: Context, chat: Chat) {
        if (chat.lines.none { it.role == Role.User }) return
        runCatching { file(context, chat.id).writeText(encode(chat).toString()) }
    }

    fun delete(context: Context, id: String) {
        file(context, id).delete()
    }

    fun clear(context: Context) {
        dir(context).listFiles().orEmpty().forEach { it.delete() }
    }

    /** A short title from the first thing the student wrote. */
    fun titleFor(firstMessage: String): String {
        val clean = firstMessage.trim().replace(Regex("\\s+"), " ")
        return if (clean.length <= 40) clean else clean.take(40).substringBeforeLast(' ').ifBlank { clean.take(40) } + "..."
    }

    fun encode(chat: Chat): JSONObject = JSONObject()
        .put("id", chat.id)
        .put("title", chat.title)
        .put("updatedAt", chat.updatedAt)
        .put(
            "lines",
            JSONArray(chat.lines.map { JSONObject().put("role", it.role.name).put("text", it.text) }),
        )

    fun decode(text: String): Chat {
        val o = JSONObject(text)
        val arr = o.getJSONArray("lines")
        val lines = (0 until arr.length()).mapNotNull { i ->
            val l = arr.getJSONObject(i)
            val role = runCatching { Role.valueOf(l.getString("role")) }.getOrNull() ?: return@mapNotNull null
            Line(role, l.getString("text"))
        }
        return Chat(o.getString("id"), o.optString("title", "Chat"), o.getLong("updatedAt"), lines)
    }
}
