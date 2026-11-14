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
import java.util.Locale

enum class TokenKind { Date, Time, Priority, Tag, List, Repeat, Duration, Alarm, Start }

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
    /** From "for 2 hours" or a time range like "3-5pm". */
    val durationMinutes: Int? = null,
    /** From a day range like "mon to fri" (the timeline shows a bar). */
    val startDate: LocalDate? = null,
    /** "!alarm" or "with alarm": ring like an alarm clock. */
    val alarm: Boolean = false,
    /** From "start thu 7pm": when to begin, with its own reminder apart from the deadline. */
    val startAt: LocalDateTime? = null,
)

/**
 * Turns "Math homework friday next week 5pm !1 #calc ~School" into task fields.
 * Works fully offline. Anything it doesn't understand stays in the title.
 *
 * Dates: today, tonight, tomorrow (and typos like tommorow, tmrw), day after tomorrow, mon..sun,
 * this/coming friday, friday next week, next week friday, friday this week, the friday after next,
 * this weekend, next weekend, end of week/month, start of next month, eod, in 3 days / 2 weeks /
 * a fortnight, next week/month/year, dec 4, 4 dec, dec 4 2027, the 15th, 11/20, 2026-11-20,
 * "mon to fri" (start and due).
 * Times: 5pm, 5:30 pm, at 5, @5, 17:00, noon, midnight, half past 4, quarter to 5, 5 o'clock,
 * this evening, tomorrow morning, in 30 minutes, in 2 hours, and ranges like 3-5pm.
 * Length: for 2 hours, for 45 min. Priority: !1 !2 !3, p1 p2 p3, !high !med !low, !urgent, !!! !!.
 * Repeat: daily, weekly, biweekly, monthly, yearly, every day/weekday/weekend/other week,
 * every 3 days, every monday, every mon and wed, mondays, each friday, every month on the 15th,
 * every morning. Tags: #exam. List: ~Chemistry. Alarm: !alarm or "with alarm".
 * Start: "start thu 7pm", "begin tomorrow", "and start tonight" (when to begin, apart from the deadline).
 */
object SmartParser {
    private const val WD =
        "(?:mon(?:day)?|tue(?:s|sday)?|wed(?:nesday)?|thu(?:r|rs|rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?" +
            "|wensday|wednsday|wedensday|thurday|thrusday|saterday|teusday|tuseday)"
    private const val WDS =
        "(?:mondays|tuesdays|wednesdays|thursdays|fridays|saturdays|sundays|weekdays|weekends)"
    private const val MONTH =
        "(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private const val NUM =
        "(?:\\d{1,3}|a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|a couple of|a few|couple of|few)"
    private const val B = "(?<![\\p{L}\\p{N}_])"
    private const val E = "(?![\\p{L}\\p{N}_])"
    private const val TOMORROW = "(?:tomorrow|tomorow|tommorow|tommorrow|tomorrw|tmorrow|tmrw|tmrrw|tmr|tmw|2moro|2morrow|tomoro)"

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    // ---- Repeat ----
    private val rEveryPartOfDay = rx("$B(?:every|each)\\s+(morning|afternoon|evening|night)$E")
    private val rEveryMonthOn = rx("$B(?:(?:every|each)\\s+month|monthly)\\s+on\\s+the\\s+(\\d{1,2})(?:st|nd|rd|th)?$E")
    private val rEveryNth = rx("$B(?:every|each)\\s+(\\d{1,2})(?:st|nd|rd|th)(?:\\s+of\\s+the\\s+month)?$E")
    private val rEveryN = rx("$B(?:every|each)\\s+(\\d{1,2})\\s+(days?|d|weeks?|wks?|w|months?|years?|yrs?)$E")
    private val rBiweekly = rx("$B(?:biweekly|bi-weekly|fortnightly|every\\s+fortnight)$E")
    private val rEveryUnit = rx("$B(?:every|each)\\s+(other\\s+)?(day|weekday|weekend|week|month|year)$E")
    private val rEveryDays = rx("$B(?:every|each)\\s+(other\\s+)?($WD(?:\\s*(?:,|and|&)?\\s*$WD)*)$E")
    private val rPluralDays = rx("$B(?:on\\s+)?($WDS(?:\\s*(?:,|and|&)?\\s*$WDS)*)$E")
    private val rRepeatWord = rx("$B(daily|weekly|monthly|yearly|annually)$E")
    private val rWeekdayWord = rx("$B$WD$E")
    private val rWeekdayPlural = rx("$B$WDS$E")

    // ---- Date ----
    private val rPartOfDay = rx("$B(today|tonight|$TOMORROW|$WD)\\s+(morning|afternoon|evening|night)$E")
    private val rEod = rx("$B(?:by\\s+)?(?:end\\s+of\\s+(?:the\\s+)?day|eod|close\\s+of\\s+business|cob)(?:\\s+($TOMORROW|today|$WD))?$E")
    private val rRelTime = rx("${B}in\\s+(half\\s+an|an|a|one|\\d{1,3}(?:\\.\\d)?|two|three|four|five|ten|fifteen|twenty|thirty|forty|forty-five|ninety)\\s*(minutes?|mins?|m|hours?|hrs?|h)$E")
    private val rDayAfter = rx("$B(?:the\\s+)?day\\s+after\\s+$TOMORROW$E")
    private val rAfterNext = rx("$B(?:the\\s+)?($WD)\\s+after\\s+next$E")
    private val rWdNextWeek = rx("$B(?:on\\s+)?(?:($WD)\\s+(?:of\\s+)?next\\s+week|next\\s+week(?:'s)?\\s+(?:on\\s+)?($WD))$E")
    private val rWdThisWeek = rx("$B(?:on\\s+)?(?:($WD)\\s+this\\s+week|this\\s+week(?:'s)?\\s+(?:on\\s+)?($WD))$E")
    private val rComing = rx("$B(?:this\\s+)?(?:coming|upcoming|following)\\s+($WD)$E")
    private val rWeekend = rx("$B(?:(this|next)\\s+)?weekend$E")
    private val rEndOf = rx("$B(?:by\\s+)?(?:(?:the\\s+)?end\\s+of\\s+(?:the\\s+)?(next\\s+)?(week|month|year)|(eow|eom|eoy))$E")
    private val rStartOf = rx("$B(?:the\\s+)?(?:start|beginning)\\s+of\\s+(?:the\\s+)?(next\\s+)?(week|month|year)$E")
    private val rTonight = rx("$B(?:tonight|tonite|2nite)$E")
    private val rToday = rx("$B(?:today|2day)$E")
    private val rTomorrow = rx("$B$TOMORROW$E")
    private val rTomorrowWord = rx(TOMORROW)
    private val rInN = rx("${B}in\\s+($NUM)\\s+(days?|d|weeks?|wks?|w|months?|mos?|years?|yrs?|fortnights?)$E")
    private val rInFortnight = rx("${B}in\\s+a\\s+fortnight$E")
    private val rNextUnit = rx("${B}next\\s+(week|month|year)$E")
    private val rIsoDate = rx("$B(\\d{4})-(\\d{1,2})-(\\d{1,2})$E")
    private val rSlashDate = rx("(?<![\\p{L}\\p{N}_/])(\\d{1,2})[/.](\\d{1,2})(?:[/.](\\d{2,4}))?(?![\\p{L}\\p{N}_/])")
    private val rMonthDay = rx("$B(?:on\\s+)?($MONTH)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?$E")
    private val rDayMonth = rx("$B(?:on\\s+)?(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?($MONTH)(?:,?\\s+(\\d{4}))?$E")
    private val rTheNth = rx("$B(?:on\\s+)?the\\s+(\\d{1,2})(?:st|nd|rd|th)$E")
    private val rWeekdayRange = rx("$B(?:from\\s+)?($WD)\\s*(?:-|to|thru|through|until|till)\\s*($WD)$E")
    private val rWeekday = rx("$B(?:(next|this|on|by)\\s+)?($WD)$E")

    // ---- Time ----
    private const val AMPM = "(am|pm|a\\.m\\.|p\\.m\\.)"
    private val rTimeRange = rx(
        "(?<![\\p{L}\\p{N}_:])(from\\s+|at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(?:$AMPM)?\\s*(?:-|to|until|till)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(?:$AMPM)?(?![\\p{L}\\p{N}_:])",
    )
    private val rHalfQuarter = rx("$B(?:at\\s+)?(half|quarter)\\s+(past|to|after|before)\\s+(\\d{1,2})(?:\\s*(am|pm))?$E")
    private val rOClock = rx("$B(?:at\\s+)?(\\d{1,2})\\s*o'?\\s?clock$E")
    private val rAmPm = rx("$B(?:at\\s+|@\\s*)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)(?![\\p{L}\\p{N}_])")
    private val rAtSign = rx("(?<![\\p{L}\\p{N}_@])@\\s*(\\d{1,2})(?::(\\d{2}))?(?![\\p{L}\\p{N}_:])")
    private val rNoon = rx("$B(?:at\\s+)?(noon|midday|midnight)$E")
    private val r24 = rx("(?<![\\p{L}\\p{N}_:])(?:at\\s+)?(\\d{1,2}):(\\d{2})(?![\\p{L}\\p{N}_:])")
    private val rAt = rx("${B}at\\s+(\\d{1,2})(?![\\p{L}\\p{N}_:])")
    private val rThisPart = rx("$B(?:this|in\\s+the)\\s+(morning|afternoon|evening)$E")

    // ---- Length ----
    private val rFor = rx("${B}for\\s+(half\\s+an|an|a|one|two|three|four|\\d{1,3}(?:\\.\\d)?)\\s*(hours?|hrs?|h|minutes?|mins?|m)(?:\\s+(\\d{1,2})\\s*(?:minutes?|mins?|m))?$E")

    // ---- Priority, tags, list ----
    private val rPriority = rx("(?<![\\p{L}\\p{N}_!])!(1|2|3|high|hi|urgent|important|medium|med|low|lo)$E")
    private val rP = rx("${B}p([123])$E")
    private val rBangs = rx("(?<![\\p{L}\\p{N}_!])(!!!|!!)(?![\\p{L}\\p{N}_!])")
    private val rTag = Regex("(?<![\\p{L}\\p{N}_#])#([\\p{L}\\p{N}_-]{1,32})")
    private val rList = Regex("(?<![\\p{L}\\p{N}_~])~([\\p{L}\\p{N}_-]{1,32})")

    private val rAlarm = rx("(?:(?<![\\p{L}\\p{N}_!])!alarm|${B}with\\s+(?:an\\s+)?alarm)$E")

    // ---- Start ----
    private val rStartWord = rx(
        "(?:(?:,\\s*|\\s+)(?:and|then)\\s+|,\\s*)?$B(?:start(?:ing)?|begin(?:ning)?)(?:\\s+(?:it|on\\s+it|working))?\\s+(?:on\\s+|from\\s+)?",
    )
    private val rGap = Regex("[\\s,]*")
    private val DATE_TIME_ONLY = TokenKind.entries.toSet() - TokenKind.Date - TokenKind.Time
    private val START_EVENING: LocalTime = LocalTime.of(19, 0)

    private val rDanglingEnd = rx("\\s+(?:on|at|by|due|until|till|from|before)$")
    private val rDanglingStart = rx("^(?:on|at|by|from)\\s+")

    fun parse(
        input: String,
        now: LocalDateTime,
        disabled: Set<TokenKind> = emptySet(),
        defaultDate: LocalDate? = null,
        locale: Locale = Locale.getDefault(),
    ): ParsedInput = parse(input, now, disabled, defaultDate, locale, findStart = true)

    private fun parse(
        input: String,
        now: LocalDateTime,
        disabled: Set<TokenKind>,
        defaultDate: LocalDate?,
        locale: Locale,
        findStart: Boolean,
    ): ParsedInput {
        val claimed = BooleanArray(input.length)
        // Text that is understood but kept in the title (a start phrase whose chip was removed).
        val reserved = BooleanArray(input.length)
        val tokens = mutableListOf<Token>()
        val today = now.toLocalDate()

        fun free(r: IntRange) = r.all { !claimed[it] && !reserved[it] }
        fun claim(kind: TokenKind, r: IntRange) {
            for (i in r) claimed[i] = true
            tokens += Token(kind, r.first, r.last + 1)
        }
        fun firstFree(regex: Regex): MatchResult? = regex.findAll(input).firstOrNull { free(it.range) }

        var recurrence: Recurrence? = null
        var date: LocalDate? = null
        var startDate: LocalDate? = null
        var time: LocalTime? = null
        var duration: Int? = null
        var impliedTime: LocalTime? = null // from "tonight" or "eod", used only if no time is typed
        var partHint: String? = null // "morning", "evening"... makes "tomorrow evening at 7" mean 7 PM
        var bareHour = false // the time was a plain number like "at 7", without am or pm
        var priority = Priority.None
        val tags = linkedSetOf<String>()
        var listName: String? = null

        // ---------- Start (first, so "start thu" isn't read as the deadline) ----------
        var startAt: LocalDateTime? = null
        if (findStart) {
            for (m in rStartWord.findAll(input)) {
                val after = m.range.last + 1
                val rest = input.substring(after)
                // The start is the date and time right after the word, nothing further along.
                var end = 0
                val seen = mutableSetOf<TokenKind>()
                for (t in parse(rest, now, DATE_TIME_ONLY, null, locale, findStart = false).tokens) {
                    // One date and one time at most: "start thu fri" means start Thu, due Fri.
                    if (!rGap.matches(rest.substring(end, t.start)) || !seen.add(t.kind)) break
                    end = t.end
                }
                if (end == 0) continue
                val phrase = parse(rest.substring(0, end), now, DATE_TIME_ONLY, null, locale, findStart = false)
                val day = phrase.date ?: continue
                val range = m.range.first until after + end
                if (TokenKind.Start in disabled) {
                    for (i in range) reserved[i] = true
                } else {
                    startAt = day.atTime(phrase.time ?: START_EVENING)
                    claim(TokenKind.Start, range)
                }
                break
            }
        }

        // ---------- Repeat (first, so "every monday" isn't read as one Monday) ----------
        if (TokenKind.Repeat !in disabled) {
            firstFree(rEveryPartOfDay)?.let { m ->
                recurrence = Recurrence(Freq.Daily)
                impliedTime = partOfDay(m.groupValues[1])
                partHint = m.groupValues[1].lowercase()
                claim(TokenKind.Repeat, m.range)
            }
            if (recurrence == null) (firstFree(rEveryMonthOn) ?: firstFree(rEveryNth))?.let { m ->
                val d = nthOfMonth(m.groupValues[1].toInt(), today)
                if (d != null) {
                    recurrence = Recurrence(Freq.Monthly)
                    if (TokenKind.Date !in disabled) date = d
                    claim(TokenKind.Repeat, m.range)
                }
            }
            if (recurrence == null) firstFree(rEveryN)?.let { m ->
                recurrence = Recurrence(freqOf(m.groupValues[2]), m.groupValues[1].toInt().coerceAtLeast(1))
                claim(TokenKind.Repeat, m.range)
            }
            if (recurrence == null) firstFree(rBiweekly)?.let { m ->
                recurrence = Recurrence(Freq.Weekly, 2)
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
                    recurrence = Recurrence(Freq.Weekly, if (m.groupValues[1].isNotBlank()) 2 else 1, days)
                    claim(TokenKind.Repeat, m.range)
                }
            }
            if (recurrence == null) firstFree(rPluralDays)?.let { m ->
                val days = rWeekdayPlural.findAll(m.groupValues[1]).flatMap { w ->
                    when (w.value.lowercase()) {
                        "weekdays" -> Recurrence.WEEKDAYS
                        "weekends" -> Recurrence.WEEKEND
                        else -> listOfNotNull(dayOf(w.value))
                    }
                }.toSet()
                if (days.isNotEmpty()) {
                    recurrence = Recurrence(Freq.Weekly, 1, days)
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

        // ---------- Date ----------
        if (TokenKind.Date !in disabled) {
            fun take(regex: Regex, resolve: (MatchResult) -> LocalDate?) {
                if (date != null) return
                val m = firstFree(regex) ?: return
                val d = resolve(m) ?: return
                date = d
                claim(TokenKind.Date, m.range)
            }
            if (TokenKind.Time !in disabled) take(rPartOfDay) { m ->
                resolveDayWord(m.groupValues[1], null, today)?.also {
                    impliedTime = partOfDay(m.groupValues[2])
                    partHint = m.groupValues[2].lowercase()
                }
            }
            take(rEod) { m ->
                impliedTime = LocalTime.of(17, 0)
                m.groupValues[1].takeIf { it.isNotBlank() }?.let { resolveDayWord(it, null, today) } ?: today
            }
            if (TokenKind.Time !in disabled && date == null) firstFree(rRelTime)?.let { m ->
                val minutes = relativeMinutes(m.groupValues[1], m.groupValues[2])
                if (minutes != null) {
                    val at = now.plusMinutes(minutes.toLong())
                    date = at.toLocalDate()
                    time = at.toLocalTime().withSecond(0).withNano(0)
                    claim(TokenKind.Date, m.range)
                }
            }
            take(rDayAfter) { today.plusDays(2) }
            take(rAfterNext) { m -> dayOf(m.groupValues[1])?.let { today.with(TemporalAdjusters.next(it)).plusWeeks(1) } }
            take(rWdNextWeek) { m ->
                dayOf(m.groupValues[1].ifEmpty { m.groupValues[2] })?.let { nextWeek(today, it) }
            }
            take(rWdThisWeek) { m ->
                dayOf(m.groupValues[1].ifEmpty { m.groupValues[2] })?.let {
                    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(it))
                }
            }
            take(rComing) { m -> dayOf(m.groupValues[1])?.let { today.with(TemporalAdjusters.nextOrSame(it)) } }
            take(rWeekend) { m ->
                if (m.groupValues[1].equals("next", true)) nextWeek(today, DayOfWeek.SATURDAY)
                else today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            }
            take(rEndOf) { m ->
                val next = m.groupValues[1].isNotBlank()
                when ((m.groupValues[2].ifEmpty { m.groupValues[3] }).lowercase()) {
                    // A school week ends on Friday.
                    "week", "eow" -> if (next) nextWeek(today, DayOfWeek.FRIDAY) else today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
                    "month", "eom" -> (if (next) today.plusMonths(1) else today).with(TemporalAdjusters.lastDayOfMonth())
                    else -> (if (next) today.plusYears(1) else today).with(TemporalAdjusters.lastDayOfYear())
                }
            }
            take(rStartOf) { m ->
                val next = m.groupValues[1].isNotBlank()
                when (m.groupValues[2].lowercase()) {
                    // "start of the week" means the coming Monday (or today if it is Monday).
                    "week" -> if (next) nextWeek(today, DayOfWeek.MONDAY) else today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                    "month" -> if (!next && today.dayOfMonth == 1) today else today.plusMonths(1).withDayOfMonth(1)
                    else -> if (!next && today.dayOfYear == 1) today else today.plusYears(1).withDayOfYear(1)
                }
            }
            take(rTonight) {
                impliedTime = LocalTime.of(20, 0)
                today
            }
            take(rToday) { today }
            take(rTomorrow) { today.plusDays(1) }
            take(rInFortnight) { today.plusWeeks(2) }
            take(rInN) { m ->
                numberOf(m.groupValues[1])?.let { n ->
                    val unit = m.groupValues[2].lowercase()
                    when {
                        unit.startsWith("fortnight") -> today.plusWeeks(2L * n)
                        unit.startsWith("d") -> today.plusDays(n.toLong())
                        unit.startsWith("w") -> today.plusWeeks(n.toLong())
                        unit.startsWith("m") -> today.plusMonths(n.toLong())
                        else -> today.plusYears(n.toLong())
                    }
                }
            }
            take(rNextUnit) { m ->
                when (m.groupValues[1].lowercase()) {
                    "week" -> today.plusWeeks(1)
                    "month" -> today.plusMonths(1)
                    else -> today.plusYears(1)
                }
            }
            take(rIsoDate) { m -> safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
            take(rSlashDate) { m -> slashDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3], today, locale) }
            take(rMonthDay) { m -> monthDay(monthOf(m.groupValues[1]), m.groupValues[2].toInt(), m.groupValues[3], today) }
            take(rDayMonth) { m -> monthDay(monthOf(m.groupValues[2]), m.groupValues[1].toInt(), m.groupValues[3], today) }
            take(rTheNth) { m -> nthOfMonth(m.groupValues[1].toInt(), today) }
            take(rWeekdayRange) { m ->
                val from = dayOf(m.groupValues[1]) ?: return@take null
                val to = dayOf(m.groupValues[2]) ?: return@take null
                val s = today.with(TemporalAdjusters.nextOrSame(from))
                startDate = s
                s.with(TemporalAdjusters.nextOrSame(to))
            }
            take(rWeekday) { m -> resolveDayWord(m.groupValues[2], m.groupValues[1], today) }
        }

        // ---------- Time ----------
        if (TokenKind.Time !in disabled) {
            fun takeTime(regex: Regex, resolve: (MatchResult) -> LocalTime?) {
                if (time != null) return
                val m = firstFree(regex) ?: return
                val t = resolve(m) ?: return
                time = t
                claim(TokenKind.Time, m.range)
            }
            // A range needs a clear sign it's a time ("from", am/pm or minutes), so "questions 1 to 12" stays text.
            rTimeRange.findAll(input).firstOrNull { m ->
                free(m.range) && (1..7).any { it != 2 && it != 5 && m.groupValues[it].isNotBlank() } && timeRange(m) != null
            }?.let { m ->
                val range = timeRange(m)!!
                time = range.first
                duration = range.second
                claim(TokenKind.Time, m.range)
            }
            takeTime(rHalfQuarter) { m ->
                val h = m.groupValues[3].toInt()
                if (h !in 1..12) return@takeTime null
                val base = hour12(h, m.groupValues[4])
                val minutes = if (m.groupValues[1].equals("half", true)) 30 else 15
                val forward = m.groupValues[2].lowercase().let { it == "past" || it == "after" }
                LocalTime.of(base, 0).plusMinutes(if (forward) minutes.toLong() else -minutes.toLong())
            }
            takeTime(rOClock) { m ->
                m.groupValues[1].toInt().takeIf { it in 1..12 }?.let {
                    bareHour = true
                    LocalTime.of(hour12(it, ""), 0)
                }
            }
            takeTime(rAmPm) { m ->
                val h = m.groupValues[1].toInt()
                val min = m.groupValues[2].ifEmpty { "0" }.toInt()
                if (h !in 1..12 || min !in 0..59) null
                else LocalTime.of((h % 12) + if (m.groupValues[3].lowercase().startsWith("p")) 12 else 0, min)
            }
            takeTime(rNoon) { m -> if (m.groupValues[1].equals("midnight", true)) LocalTime.of(23, 59) else LocalTime.NOON }
            takeTime(r24) { m -> bareTime(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
            takeTime(rAtSign) { m -> bareTime(m.groupValues[1].toInt(), m.groupValues[2].ifEmpty { "0" }.toInt())?.also { bareHour = true } }
            takeTime(rAt) { m -> bareTime(m.groupValues[1].toInt(), 0)?.also { bareHour = true } }
            takeTime(rThisPart) { m -> partOfDay(m.groupValues[1]) }
            val typed = time
            if (typed != null && bareHour && partHint != null) {
                // "tomorrow morning at 6" is 6 AM; "friday evening at 7" is 7 PM.
                time = when {
                    partHint == "morning" && typed.hour in 13..18 -> typed.minusHours(12)
                    partHint != "morning" && typed.hour in 1..11 -> typed.plusHours(12)
                    else -> typed
                }
            }
            if (time == null) time = impliedTime
        }

        // ---------- Length ----------
        if (TokenKind.Duration !in disabled && duration == null) {
            firstFree(rFor)?.let { m ->
                val main = relativeMinutes(m.groupValues[1], m.groupValues[2])
                val extra = m.groupValues[3].toIntOrNull() ?: 0
                if (main != null) {
                    duration = (main + extra).coerceIn(5, 24 * 60)
                    claim(TokenKind.Duration, m.range)
                }
            }
        }

        // ---------- Priority, tags, list ----------
        if (TokenKind.Priority !in disabled) {
            firstFree(rPriority)?.let { m ->
                priority = when (m.groupValues[1].lowercase()) {
                    "1", "high", "hi", "urgent", "important" -> Priority.High
                    "2", "medium", "med" -> Priority.Medium
                    else -> Priority.Low
                }
                claim(TokenKind.Priority, m.range)
            }
            if (priority == Priority.None) firstFree(rP)?.let { m ->
                priority = when (m.groupValues[1]) { "1" -> Priority.High; "2" -> Priority.Medium; else -> Priority.Low }
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

        var alarm = false
        if (TokenKind.Alarm !in disabled) {
            firstFree(rAlarm)?.let { m ->
                alarm = true
                claim(TokenKind.Alarm, m.range)
            }
        }

        // ---------- Fill in a date when the text implies one ----------
        var dateIsDefault = false
        val rec = recurrence
        if (date == null && rec != null) date = rec.firstOnOrAfter(today)
        val t = time
        if (date == null && t != null) date = if (t.isAfter(now.toLocalTime())) today else today.plusDays(1)
        if (date == null && defaultDate != null && TokenKind.Date !in disabled) {
            date = defaultDate
            dateIsDefault = true
        }

        return ParsedInput(
            title = buildTitle(input, claimed, anyClaimed = tokens.isNotEmpty()),
            date = date,
            time = time,
            priority = priority,
            tags = tags.toList(),
            listName = listName,
            recurrence = recurrence,
            tokens = tokens.sortedBy { it.start },
            dateIsDefault = dateIsDefault,
            durationMinutes = duration,
            startDate = startDate,
            alarm = alarm,
            startAt = startAt,
        )
    }

    private fun buildTitle(input: String, claimed: BooleanArray, anyClaimed: Boolean): String {
        val raw = buildString { input.forEachIndexed { i, c -> append(if (claimed[i]) ' ' else c) } }
        var title = raw.replace(Regex("\\s+"), " ").trim()
        if (anyClaimed) {
            repeat(2) { title = title.replace(rDanglingEnd, "").replace(rDanglingStart, "").trim() }
            title = title.trim(' ', ',', ';', '-')
        }
        return title.ifEmpty { input.trim() }
    }

    /** Friday of next calendar week, and so on. Weeks start on Monday. */
    private fun nextWeek(today: LocalDate, dow: DayOfWeek): LocalDate =
        today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(dow))

    private fun hour12(h: Int, ampm: String): Int = when {
        ampm.lowercase().startsWith("p") -> (h % 12) + 12
        ampm.lowercase().startsWith("a") -> h % 12
        h in 1..6 -> h + 12
        else -> h % 24
    }

    private fun bareTime(h: Int, m: Int): LocalTime? {
        if (h !in 0..23 || m !in 0..59) return null
        return LocalTime.of(if (h in 1..6) h + 12 else h, m)
    }

    /** "3-5pm", "9:30-11", "from 2 to 4" as a start time and a length in minutes. */
    private fun timeRange(m: MatchResult): Pair<LocalTime, Int>? {
        val h1 = m.groupValues[2].toInt()
        val m1 = m.groupValues[3].ifEmpty { "0" }.toInt()
        val a1 = m.groupValues[4]
        val h2 = m.groupValues[5].toInt()
        val m2 = m.groupValues[6].ifEmpty { "0" }.toInt()
        val a2 = m.groupValues[7]
        if (m1 !in 0..59 || m2 !in 0..59 || h1 !in 0..23 || h2 !in 0..23) return null
        fun toHour(h: Int, ampm: String, fallback: String): Int = when {
            h > 12 -> h
            ampm.isNotEmpty() -> hour12(h, ampm)
            fallback.isNotEmpty() -> hour12(h, fallback)
            else -> if (h in 1..6) h + 12 else h
        }
        val endHour = toHour(h2, a2, "")
        var startHour = toHour(h1, a1, a2)
        // "11-1pm" means 11 am to 1 pm.
        if (a1.isEmpty() && startHour * 60 + m1 >= endHour * 60 + m2 && startHour >= 12) startHour -= 12
        val start = LocalTime.of(startHour % 24, m1)
        val end = LocalTime.of(endHour % 24, m2)
        val minutes = java.time.Duration.between(start, end).toMinutes().toInt()
        if (minutes <= 0) return null
        return start to minutes
    }

    private fun relativeMinutes(amount: String, unit: String): Int? {
        val a = amount.lowercase().trim()
        val n: Double = when {
            a.startsWith("half") -> 0.5
            a == "a" || a == "an" || a == "one" -> 1.0
            else -> numberOf(a)?.toDouble() ?: a.toDoubleOrNull() ?: return null
        }
        val perUnit = if (unit.lowercase().startsWith("h")) 60 else 1
        return (n * perUnit).toInt().takeIf { it > 0 }
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
        if (w == "tonight") return today
        if (rTomorrowWord.matches(w)) return today.plusDays(1)
        val dow = dayOf(w) ?: return null
        return if (modifier.equals("next", ignoreCase = true)) nextWeek(today, dow) else today.with(TemporalAdjusters.nextOrSame(dow))
    }

    internal fun dayOf(word: String): DayOfWeek? = when (word.lowercase().take(2)) {
        "mo" -> DayOfWeek.MONDAY
        "tu", "te" -> DayOfWeek.TUESDAY
        "we" -> DayOfWeek.WEDNESDAY
        "th" -> DayOfWeek.THURSDAY
        "fr" -> DayOfWeek.FRIDAY
        "sa" -> DayOfWeek.SATURDAY
        "su" -> DayOfWeek.SUNDAY
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

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = try {
        LocalDate.of(y, m, d)
    } catch (e: DateTimeException) {
        null
    }

    private fun monthDay(month: Month, day: Int, year: String, today: LocalDate): LocalDate? {
        year.toIntOrNull()?.let { return safeDate(it, month.value, day) }
        val d = safeDate(today.year, month.value, day) ?: return null
        return if (d.isBefore(today)) safeDate(today.year + 1, month.value, day) else d
    }

    /** 11/20 is November 20 in the US and the Philippines; 20/11 elsewhere. Unambiguous ones work everywhere. */
    private fun slashDate(a: Int, b: Int, yearText: String, today: LocalDate, locale: Locale): LocalDate? {
        val monthFirst = locale.country.uppercase() in setOf("US", "PH", "FM", "MH", "PW", "BZ")
        val (month, day) = when {
            a > 12 && b <= 12 -> b to a
            b > 12 && a <= 12 -> a to b
            monthFirst -> a to b
            else -> b to a
        }
        if (month !in 1..12 || day !in 1..31) return null
        val year = yearText.toIntOrNull()?.let { if (it < 100) 2000 + it else it }
        if (year != null) return safeDate(year, month, day)
        val d = safeDate(today.year, month, day) ?: return null
        return if (d.isBefore(today)) safeDate(today.year + 1, month, day) else d
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
        unit.startsWith("d", true) -> Freq.Daily
        unit.startsWith("w", true) -> Freq.Weekly
        unit.startsWith("m", true) -> Freq.Monthly
        else -> Freq.Yearly
    }

    private fun numberOf(word: String): Int? = when (word.lowercase().trim()) {
        "a", "an", "one" -> 1
        "two", "a couple of", "couple of" -> 2
        "three", "a few", "few" -> 3
        "four" -> 4
        "five" -> 5
        "six" -> 6
        "seven" -> 7
        "eight" -> 8
        "nine" -> 9
        "ten" -> 10
        "eleven" -> 11
        "twelve" -> 12
        "fifteen" -> 15
        "twenty" -> 20
        "thirty" -> 30
        "forty" -> 40
        "forty-five" -> 45
        "ninety" -> 90
        else -> word.trim().toIntOrNull()
    }
}
