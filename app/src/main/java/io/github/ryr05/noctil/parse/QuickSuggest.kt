package io.github.ryr05.noctil.parse

import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.data.model.looksLikeExam
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One-tap suggestions while adding a task, for the parts you didn't type: a deadline (your next
 * class for that subject), a tag and a list you already have. Works offline, never needs AI.
 * Tapping one types the words for you ("wed 10am", "#chem"), so the field stays the one truth.
 */
object QuickSuggest {
    sealed interface Apply {
        /** Words added to the end of what you typed. */
        data class Insert(val text: String) : Apply
        /** A list whose name can't be typed with ~ (it has spaces). */
        data class SetList(val id: Long, val name: String) : Apply
        /** Opens the quick deadline menu. */
        data object PickDue : Apply
    }

    data class Suggestion(val kind: TokenKind, val label: String, val apply: Apply)

    data class NamedThing(val id: Long, val name: String)

    private const val MAX = 3
    private val stopWords = setOf(
        "the", "and", "for", "with", "from", "into", "about", "due", "start", "read", "write", "study",
        "finish", "work", "class", "homework", "notes", "this", "that", "next", "week", "day",
    )
    private val dayWords = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val timeWords = DateTimeFormatter.ofPattern("h:mma", Locale.US)

    fun suggest(
        parsed: ParsedInput,
        now: LocalDateTime,
        classes: List<SchoolClass>,
        lists: List<NamedThing>,
        tags: List<String>,
        hasListAlready: Boolean,
    ): List<Suggestion> {
        val words = wordsOf(parsed.title)
        if (parsed.title.count { it.isLetter() } < 3) return emptyList()
        val out = mutableListOf<Suggestion>()
        val typedDate = parsed.date != null && !parsed.dateIsDefault

        // Deadline: before the next class of the matching subject, else a quick menu.
        val course = classes.firstOrNull { c -> matches(words, wordsOf(c.name)) }
        if (!typedDate && parsed.recurrence == null) {
            val next = course?.let { nextSession(it, now) }
            if (course != null && next != null) {
                out += Suggestion(
                    TokenKind.Date,
                    "Before ${course.name}, ${shortDay(next.toLocalDate(), now.toLocalDate())} ${next.toLocalTime().format(timeWords).lowercase()}",
                    Apply.Insert(dateWords(next.toLocalDate(), now.toLocalDate()) + " " + next.toLocalTime().format(timeWords).lowercase()),
                )
            } else if (parsed.date == null) {
                out += Suggestion(TokenKind.Date, "Due date", Apply.PickDue)
            }
        }

        // Tag: one you already use that matches the title, or #exam for exam words.
        if (parsed.tags.isEmpty()) {
            val tag = tags.firstOrNull { matches(words, wordsOf(it)) }
                ?: "exam".takeIf { looksLikeExam(parsed.title, emptyList()) }
            if (tag != null) out += Suggestion(TokenKind.Tag, "#$tag", Apply.Insert("#$tag"))
        }

        // List: the class's own list, or one named like the subject.
        if (parsed.listName == null && !hasListAlready) {
            val list = course?.listId?.let { id -> lists.firstOrNull { it.id == id } }
                ?: lists.firstOrNull { matches(words, wordsOf(it.name)) }
            if (list != null) {
                val apply = if (list.name.matches(Regex("[\\p{L}\\p{N}_-]{1,32}"))) Apply.Insert("~${list.name}") else Apply.SetList(list.id, list.name)
                out += Suggestion(TokenKind.List, list.name, apply)
            }
        }
        return out.take(MAX)
    }

    /** "calc" matches "Calculus II"; "chem" matches #chemistry; "bio" alone is too short to guess. */
    fun matches(titleWords: List<String>, nameWords: List<String>): Boolean =
        titleWords.any { t ->
            nameWords.any { n ->
                t == n || (t.length >= 4 && n.startsWith(t)) || (n.length >= 4 && t.startsWith(n))
            }
        }

    private fun wordsOf(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in stopWords && it.any(Char::isLetter) }

    /** The next time this class starts, from now on, within two weeks. */
    fun nextSession(c: SchoolClass, now: LocalDateTime): LocalDateTime? =
        (0L..14L).asSequence()
            .map { now.toLocalDate().plusDays(it) }
            .filter { c.meetsOn(it) }
            .map { it.atTime(c.start) }
            .firstOrNull { it.isAfter(now) }

    private fun dateWords(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> date.format(dayWords).lowercase()
    }

    private fun shortDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
    }

    /** The quick deadline menu: words that the parser reads back. */
    fun dueChoices(today: LocalDate): List<Pair<String, String>> {
        val friday = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.FRIDAY))
        return buildList {
            add("Today" to "today")
            add("Tomorrow" to "tomorrow")
            if (friday != today && friday != today.plusDays(1)) add("Friday" to "fri")
            add("Next week" to "next week")
        }
    }

    /** Words for a date picked from the calendar, like "nov 20". */
    fun wordsFor(date: LocalDate, today: LocalDate): String =
        if (date.year == today.year) date.format(dayWords).lowercase()
        else date.format(DateTimeFormatter.ofPattern("MMM d yyyy", Locale.US)).lowercase()
}
