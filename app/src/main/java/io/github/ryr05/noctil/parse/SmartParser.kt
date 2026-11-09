package io.github.ryr05.noctil.parse

import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.Recurrence
import io.github.ryr05.noctil.data.model.Recurrence.Freq
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.temporal.TemporalAdjusters

enum class TokenKind { Date, Time, Priority, Tag, List, Repeat }

/** A piece of the typed text that was understood. [start] inclusive, [end] exclusive. */
data class Token(val kind: TokenKind, val start: Int, val end: Int)

data class ParsedInput(
    val title: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val priority: Priority,
    val tags: List<String>,
    val listName: String?,
    val recurrence: Recurrence?,
    val tokens: List<Token>,
    /** True when [date] came from the screen you were on, not from the text. */
    val dateIsDefault: Boolean = false,
)

/**
 * Turns "Math homework fri 5pm !1 #calc every monday ~School" into structured task fields.
 * Works fully offline. Anything it doesn't understand stays in the title.
 *
 * Supported:
 * - Dates: today, tonight, tomorrow (tmr, tmrw), day after tomorrow, mon..sun, "next fri"
 *   (Friday of next week), "in 3 days", "in 2 weeks", "next week", "dec 4", "4 dec",
 *   "the 15th".
 * - Times: 5pm, 5:30 pm, at 5, @5, 17:00, noon, midnight, "this evening", "tomorrow morning".
 *   A bare hour from 1 to 6 means afternoon ("at 5" is 5 PM).
 * - Priority: !1 (high), !2 (medium), !3 (low), !high, !med, !low, !!! and !!.
 * - Tags: #exam. List: ~Chemistry.
 * - Repeat: daily, weekly, monthly, yearly, every day, every weekday, every other week,
 *   every 3 days, every monday, every mon and wed.
 */
object SmartParser {
    private const val WD =
        "(?:mon(?:day)?|tue(?:s|sday)?|wed(?:nesday)?|thu(?:r|rs|rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)"
    private const val MONTH =
        "(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private const val NUM = "(?:\\d{1,3}|a|an|one|two|three|four|five|six|seven|eight|nine|ten)"
    private const val B = "(?<![\\p{L}\\p{N}_])"
    private const val E = "(?![\\p{L}\\p{N}_])"

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    // Repeat
    private val rEveryN = rx("${B}every\\s+(\\d{1,2})\\s+(days?|weeks?|months?|years?)$E")
    private val rEveryUnit = rx("${B}every\\s+(other\\s+)?(day|weekday|weekend|week|month|year)$E")
    private val rEveryDays = rx("${B}every\\s+(other\\s+)?($WD(?:\\s*(?:,|and|&)?\\s*$WD)*)$E")
    private val rRepeatWord = rx("${B}(daily|weekly|monthly|yearly|annually)$E")
    private val rWeekdayWord = rx("$B$WD$E")

    // Date (some also carry a time)
    private val rPartOfDay = rx("$B(today|tomorrow|tmrw|tmr|$WD)\\s+(morning|afternoon|evening|night)$E")
    private val rDayAfter = rx("$B(?:the\\s+)?day\\s+after\\s+tomorrow$E")
    private val rTonight = rx("${B}tonight$E")
    private val rToday = rx("${B}today$E")
    private val rTomorrow = rx("$B(?:tomorrow|tomorow|tmrw|tmrrw|tmr)$E")
    private val rInN = rx("${B}in\\s+($NUM)\\s+(days?|weeks?|months?)$E")
    private val rNextUnit = rx("${B}next\\s+(week|month|year)$E")
    private val rMonthDay = rx("$B(?:on\\s+)?($MONTH)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?$E")
    private val rDayMonth = rx("$B(?:on\\s+)?(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?($MONTH)$E")
    private val rTheNth = rx("$B(?:on\\s+)?the\\s+(\\d{1,2})(?:st|nd|rd|th)$E")
    private val rWeekday = rx("$B(?:(next|this|on)\\s+)?($WD)$E")

    // Time
    private val rAmPm = rx("$B(?:at\\s+|@\\s*)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)(?![\\p{L}\\p{N}_])")
    private val rAtSign = rx("(?<![\\p{L}\\p{N}_@])@\\s*(\\d{1,2})(?::(\\d{2}))?(?![\\p{L}\\p{N}_:])")
    private val rNoon = rx("$B(?:at\\s+)?(noon|midday|midnight)$E")
    private val r24 = rx("(?<![\\p{L}\\p{N}_:])(?:at\\s+)?(\\d{1,2}):(\\d{2})(?![\\p{L}\\p{N}_:])")
    private val rAt = rx("${B}at\\s+(\\d{1,2})(?![\\p{L}\\p{N}_:])")
    private val rThisPart = rx("$B(?:this|in\\s+the)\\s+(morning|afternoon|evening)$E")

    // Priority, tags, list
    private val rPriority = rx("(?<![\\p{L}\\p{N}_!])!(1|2|3|high|hi|medium|med|low|lo)$E")
    private val rBangs = rx("(?<![\\p{L}\\p{N}_!])(!!!|!!)(?![\\p{L}\\p{N}_!])")
    private val rTag = Regex("(?<![\\p{L}\\p{N}_#])#([\\p{L}\\p{N}_-]{1,32})")
    private val rList = Regex("(?<![\\p{L}\\p{N}_~])~([\\p{L}\\p{N}_-]{1,32})")

    private val rDanglingEnd = rx("\\s+(?:on|at|by|due)$")
    private val rDanglingStart = rx("^(?:on|at|by)\\s+")

    fun parse(
        input: String,
        now: LocalDateTime,
        disabled: Set<TokenKind> = emptySet(),
        defaultDate: LocalDate? = null,
    ): ParsedInput {
        val claimed = BooleanArray(input.length)
        val tokens = mutableListOf<Token>()
        val today = now.toLocalDate()

        fun free(r: IntRange) = r.all { !claimed[it] }
        fun claim(kind: TokenKind, r: IntRange) {
            for (i in r) claimed[i] = true
            tokens += Token(kind, r.first, r.last + 1)
        }
        fun firstFree(regex: Regex): MatchResult? = regex.findAll(input).firstOrNull { free(it.range) }

        var recurrence: Recurrence? = null
        var date: LocalDate? = null
        var time: LocalTime? = null
        var tonight = false
        var priority = Priority.None
        val tags = linkedSetOf<String>()
        var listName: String? = null

        // Repeat first, so "every monday" is not read as a single Monday.
        if (TokenKind.Repeat !in disabled) {
            firstFree(rEveryN)?.let { m ->
                val n = m.groupValues[1].toInt().coerceAtLeast(1)
                recurrence = Recurrence(freqOf(m.groupValues[2]), n)
                claim(TokenKind.Repeat, m.range)
            }
            if (recurrence == null) firstFree(rEveryUnit)?.let { m ->
                val interval = if (m.groupValues[1].isNotBlank()) 2 else 1
                recurrence = when (m.groupValues[2].lowercase()) {
                    "day" -> Recurrence(Freq.Daily, interval)
                    "weekday" -> Recurrence(Freq.Weekly, 1, Recurrence.WEEKDAYS)
                    "weekend" -> Recurrence(Freq.Weekly, 1, Recurrence.WEEKEND)
                    "week" -> Recurrence(Freq.Weekly, interval)
                    "month" -> Recurrence(Freq.Monthly, interval)
                    else -> Recurrence(Freq.Yearly, interval)
                }
                claim(TokenKind.Repeat, m.range)
            }
            if (recurrence == null) firstFree(rEveryDays)?.let { m ->
                val days = rWeekdayWord.findAll(m.groupValues[2]).mapNotNull { dayOf(it.value) }.toSet()
                if (days.isNotEmpty()) {
                    val interval = if (m.groupValues[1].isNotBlank()) 2 else 1
                    recurrence = Recurrence(Freq.Weekly, interval, days)
                    claim(TokenKind.Repeat, m.range)
                }
            }
            if (recurrence == null) firstFree(rRepeatWord)?.let { m ->
                recurrence = when (m.groupValues[1].lowercase()) {
                    "daily" -> Recurrence(Freq.Daily)
                    "weekly" -> Recurrence(Freq.Weekly)
                    "monthly" -> Recurrence(Freq.Monthly)
                    else -> Recurrence(Freq.Yearly)
                }
                claim(TokenKind.Repeat, m.range)
            }
        }

        if (TokenKind.Date !in disabled) {
            if (TokenKind.Time !in disabled) firstFree(rPartOfDay)?.let { m ->
                val d = resolveDayWord(m.groupValues[1], null, today)
                if (d != null) {
                    date = d
                    time = partOfDay(m.groupValues[2])
                    claim(TokenKind.Date, m.range)
                }
            }
            if (date == null) firstFree(rDayAfter)?.let { m ->
                date = today.plusDays(2); claim(TokenKind.Date, m.range)
            }
            if (date == null) firstFree(rTonight)?.let { m ->
                date = today; tonight = true; claim(TokenKind.Date, m.range)
            }
            if (date == null) firstFree(rToday)?.let { m ->
                date = today; claim(TokenKind.Date, m.range)
            }
            if (date == null) firstFree(rTomorrow)?.let { m ->
                date = today.plusDays(1); claim(TokenKind.Date, m.range)
            }
            if (date == null) firstFree(rInN)?.let { m ->
                val n = numberOf(m.groupValues[1])
                if (n != null) {
                    val unit = m.groupValues[2].lowercase()
                    date = when {
                        unit.startsWith("day") -> today.plusDays(n.toLong())
                        unit.startsWith("week") -> today.plusWeeks(n.toLong())
                        else -> today.plusMonths(n.toLong())
                    }
                    claim(TokenKind.Date, m.range)
                }
            }
            if (date == null) firstFree(rNextUnit)?.let { m ->
                date = when (m.groupValues[1].lowercase()) {
                    "week" -> today.plusWeeks(1)
                    "month" -> today.plusMonths(1)
                    else -> today.plusYears(1)
                }
                claim(TokenKind.Date, m.range)
            }
            if (date == null) firstFree(rMonthDay)?.let { m ->
                val d = monthDay(monthOf(m.groupValues[1]), m.groupValues[2].toInt(), today)
                if (d != null) { date = d; claim(TokenKind.Date, m.range) }
            }
            if (date == null) firstFree(rDayMonth)?.let { m ->
                val d = monthDay(monthOf(m.groupValues[2]), m.groupValues[1].toInt(), today)
                if (d != null) { date = d; claim(TokenKind.Date, m.range) }
            }
            if (date == null) firstFree(rTheNth)?.let { m ->
                val d = nthOfMonth(m.groupValues[1].toInt(), today)
                if (d != null) { date = d; claim(TokenKind.Date, m.range) }
            }
            if (date == null) firstFree(rWeekday)?.let { m ->
                val d = resolveDayWord(m.groupValues[2], m.groupValues[1], today)
                if (d != null) { date = d; claim(TokenKind.Date, m.range) }
            }
        }

        if (TokenKind.Time !in disabled) {
            if (time == null) firstFree(rAmPm)?.let { m ->
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].ifEmpty { "0" }.toInt()
                if (h in 1..12 && min in 0..59) {
                    val pm = m.groupValues[3].lowercase().startsWith("p")
                    time = LocalTime.of((h % 12) + if (pm) 12 else 0, min)
                    claim(TokenKind.Time, m.range)
                }
            }
            if (time == null) firstFree(rNoon)?.let { m ->
                time = if (m.groupValues[1].equals("midnight", true)) LocalTime.of(23, 59) else LocalTime.NOON
                claim(TokenKind.Time, m.range)
            }
            if (time == null) firstFree(r24)?.let { m ->
                val t = bareTime(m.groupValues[1].toInt(), m.groupValues[2].toInt())
                if (t != null) { time = t; claim(TokenKind.Time, m.range) }
            }
            if (time == null) firstFree(rAtSign)?.let { m ->
                val t = bareTime(m.groupValues[1].toInt(), m.groupValues[2].ifEmpty { "0" }.toInt())
                if (t != null) { time = t; claim(TokenKind.Time, m.range) }
            }
            if (time == null) firstFree(rAt)?.let { m ->
                val t = bareTime(m.groupValues[1].toInt(), 0)
                if (t != null) { time = t; claim(TokenKind.Time, m.range) }
            }
            if (time == null) firstFree(rThisPart)?.let { m ->
                time = partOfDay(m.groupValues[1]); claim(TokenKind.Time, m.range)
            }
            if (time == null && tonight) time = LocalTime.of(20, 0)
        }

        if (TokenKind.Priority !in disabled) {
            firstFree(rPriority)?.let { m ->
                priority = when (m.groupValues[1].lowercase()) {
                    "1", "high", "hi" -> Priority.High
                    "2", "medium", "med" -> Priority.Medium
                    else -> Priority.Low
                }
                claim(TokenKind.Priority, m.range)
            }
            if (priority == Priority.None) firstFree(rBangs)?.let { m ->
                priority = if (m.groupValues[1].length == 3) Priority.High else Priority.Medium
                claim(TokenKind.Priority, m.range)
            }
        }

        if (TokenKind.Tag !in disabled) {
            rTag.findAll(input).filter { free(it.range) }.forEach { m ->
                tags += m.groupValues[1]
                claim(TokenKind.Tag, m.range)
            }
        }

        if (TokenKind.List !in disabled) {
            firstFree(rList)?.let { m ->
                listName = m.groupValues[1]
                claim(TokenKind.List, m.range)
            }
        }

        // Fill in a date when the text implies one.
        var dateIsDefault = false
        val rec = recurrence
        if (date == null && rec != null) date = rec.firstOnOrAfter(today)
        val t = time
        if (date == null && t != null) date = if (t.isAfter(now.toLocalTime())) today else today.plusDays(1)
        if (date == null && defaultDate != null && TokenKind.Date !in disabled) {
            date = defaultDate
            dateIsDefault = true
        }

        val title = buildTitle(input, claimed, anyClaimed = tokens.isNotEmpty())
        return ParsedInput(
            title = title,
            date = date,
            time = time,
            priority = priority,
            tags = tags.toList(),
            listName = listName,
            recurrence = recurrence,
            tokens = tokens.sortedBy { it.start },
            dateIsDefault = dateIsDefault,
        )
    }

    private fun buildTitle(input: String, claimed: BooleanArray, anyClaimed: Boolean): String {
        val raw = buildString { input.forEachIndexed { i, c -> append(if (claimed[i]) ' ' else c) } }
        var title = raw.replace(Regex("\\s+"), " ").trim()
        if (anyClaimed) {
            repeat(2) {
                title = title.replace(rDanglingEnd, "").replace(rDanglingStart, "").trim()
            }
            title = title.trim(' ', ',', ';', '-')
        }
        return title.ifEmpty { input.trim() }
    }

    private fun bareTime(h: Int, m: Int): LocalTime? {
        if (h !in 0..23 || m !in 0..59) return null
        val hour = if (h in 1..6) h + 12 else h
        return LocalTime.of(hour, m)
    }

    private fun partOfDay(word: String): LocalTime = when (word.lowercase()) {
        "morning" -> LocalTime.of(9, 0)
        "afternoon" -> LocalTime.of(14, 0)
        "evening" -> LocalTime.of(19, 0)
        else -> LocalTime.of(20, 0)
    }

    private fun resolveDayWord(word: String, modifier: String?, today: LocalDate): LocalDate? {
        val w = word.lowercase()
        if (w == "today") return today
        if (w == "tomorrow" || w == "tmrw" || w == "tmr") return today.plusDays(1)
        val dow = dayOf(w) ?: return null
        return if (modifier.equals("next", ignoreCase = true)) {
            today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(dow))
        } else {
            today.with(TemporalAdjusters.nextOrSame(dow))
        }
    }

    internal fun dayOf(word: String): DayOfWeek? = when (word.lowercase().take(3)) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        "sun" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun monthOf(word: String): Month = when (word.lowercase().take(3)) {
        "jan" -> Month.JANUARY
        "feb" -> Month.FEBRUARY
        "mar" -> Month.MARCH
        "apr" -> Month.APRIL
        "may" -> Month.MAY
        "jun" -> Month.JUNE
        "jul" -> Month.JULY
        "aug" -> Month.AUGUST
        "sep" -> Month.SEPTEMBER
        "oct" -> Month.OCTOBER
        "nov" -> Month.NOVEMBER
        else -> Month.DECEMBER
    }

    private fun monthDay(month: Month, day: Int, today: LocalDate): LocalDate? = try {
        val d = LocalDate.of(today.year, month, day)
        if (d.isBefore(today)) LocalDate.of(today.year + 1, month, day) else d
    } catch (e: DateTimeException) {
        null
    }

    private fun nthOfMonth(day: Int, today: LocalDate): LocalDate? {
        if (day !in 1..31) return null
        var month = today.withDayOfMonth(1)
        repeat(13) {
            if (day <= month.lengthOfMonth()) {
                val candidate = month.withDayOfMonth(day)
                if (!candidate.isBefore(today)) return candidate
            }
            month = month.plusMonths(1)
        }
        return null
    }

    private fun freqOf(unit: String): Freq = when {
        unit.startsWith("day", true) -> Freq.Daily
        unit.startsWith("week", true) -> Freq.Weekly
        unit.startsWith("month", true) -> Freq.Monthly
        else -> Freq.Yearly
    }

    private fun numberOf(word: String): Int? = when (word.lowercase()) {
        "a", "an", "one" -> 1
        "two" -> 2
        "three" -> 3
        "four" -> 4
        "five" -> 5
        "six" -> 6
        "seven" -> 7
        "eight" -> 8
        "nine" -> 9
        "ten" -> 10
        else -> word.toIntOrNull()
    }
}
