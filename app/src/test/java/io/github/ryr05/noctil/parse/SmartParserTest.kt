package io.github.ryr05.noctil.parse

import io.github.ryr05.noctil.data.model.Priority
import io.github.ryr05.noctil.data.model.Recurrence
import io.github.ryr05.noctil.data.model.Recurrence.Freq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/** "Now" for every test: Tuesday, November 10 2026, 2:00 PM. */
class SmartParserTest {
    private val now = LocalDateTime.of(2026, 11, 10, 14, 0)
    private fun p(text: String, disabled: Set<TokenKind> = emptySet(), default: LocalDate? = null) =
        SmartParser.parse(text, now, disabled, default)
    private fun d(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test fun fullExample() {
        val r = p("Math homework fri 5pm !1 #calc")
        assertEquals("Math homework", r.title)
        assertEquals(d(11, 13), r.date)
        assertEquals(LocalTime.of(17, 0), r.time)
        assertEquals(Priority.High, r.priority)
        assertEquals(listOf("calc"), r.tags)
    }

    @Test fun plainTitlesStayUntouched() {
        listOf(
            "Read chapter 4 of The Great Gatsby",
            "Problem set 7, questions 1 to 12",
            "Email Prof. Okafor about office hours",
        ).forEach { text ->
            val r = p(text)
            assertEquals(text, r.title)
            assertNull(r.date)
            assertNull(r.time)
            assertEquals(Priority.None, r.priority)
        }
    }

    @Test fun tomorrowAtTime() {
        val r = p("call mom tomorrow at 2pm")
        assertEquals("call mom", r.title)
        assertEquals(d(11, 11), r.date)
        assertEquals(LocalTime.of(14, 0), r.time)
    }

    @Test fun shortTomorrow() = assertEquals(d(11, 11), p("pay fees tmrw").date)

    @Test fun today() {
        val r = p("lab report today")
        assertEquals("lab report", r.title)
        assertEquals(d(11, 10), r.date)
    }

    @Test fun dayAfterTomorrow() = assertEquals(d(11, 12), p("dentist day after tomorrow").date)

    @Test fun weekdayIsUpcoming() = assertEquals(d(11, 13), p("essay fri").date)

    @Test fun todaysWeekdayIsToday() = assertEquals(d(11, 10), p("club tuesday").date)

    @Test fun nextWeekdayIsNextWeek() = assertEquals(d(11, 20), p("essay next fri").date)

    @Test fun onWeekdayDropsTheWordOn() {
        val r = p("quiz on monday")
        assertEquals("quiz", r.title)
        assertEquals(d(11, 16), r.date)
    }

    @Test fun monthDay() {
        val r = p("Chem final dec 4")
        assertEquals("Chem final", r.title)
        assertEquals(d(12, 4), r.date)
    }

    @Test fun pastMonthDayRollsToNextYear() {
        assertEquals(d(1, 3, 2027), p("trip january 3").date)
        assertEquals(d(1, 3, 2027), p("trip 3rd of january").date)
    }

    @Test fun nthOfMonth() = assertEquals(d(11, 15), p("buy gift on the 15th").date)

    @Test fun inTwoWeeks() = assertEquals(d(11, 24), p("project in 2 weeks").date)

    @Test fun inAWeek() = assertEquals(d(11, 17), p("follow up in a week").date)

    @Test fun bareHourMeansAfternoon() {
        val r = p("meet at 5")
        assertEquals("meet", r.title)
        assertEquals(LocalTime.of(17, 0), r.time)
        assertEquals(d(11, 10), r.date)
    }

    @Test fun pastTimeMovesToTomorrow() {
        val r = p("breakfast at 8")
        assertEquals(LocalTime.of(8, 0), r.time)
        assertEquals(d(11, 11), r.date)
    }

    @Test fun twentyFourHourTime() {
        val r = p("deadline 23:59")
        assertEquals(LocalTime.of(23, 59), r.time)
        assertEquals(d(11, 10), r.date)
    }

    @Test fun amPmWithMinutes() = assertEquals(LocalTime.of(9, 30), p("lecture 9:30 am").time)

    @Test fun noon() {
        val r = p("noon lunch with Sam")
        assertEquals("lunch with Sam", r.title)
        assertEquals(LocalTime.NOON, r.time)
    }

    @Test fun tonight() {
        val r = p("movie tonight")
        assertEquals("movie", r.title)
        assertEquals(d(11, 10), r.date)
        assertEquals(LocalTime.of(20, 0), r.time)
    }

    @Test fun tomorrowMorning() {
        val r = p("run tomorrow morning")
        assertEquals("run", r.title)
        assertEquals(d(11, 11), r.date)
        assertEquals(LocalTime.of(9, 0), r.time)
    }

    @Test fun listTagsAndPriority() {
        val r = p("flashcards ~History #exam #week2 !2")
        assertEquals("flashcards", r.title)
        assertEquals("History", r.listName)
        assertEquals(listOf("exam", "week2"), r.tags)
        assertEquals(Priority.Medium, r.priority)
    }

    @Test fun priorityWords() {
        assertEquals(Priority.Low, p("email prof !low").priority)
        assertEquals(Priority.High, p("fix bike !!!").priority)
        assertEquals(Priority.Medium, p("wash car !!").priority)
    }

    @Test fun everyMonday() {
        val r = p("gym every monday")
        assertEquals("gym", r.title)
        assertEquals(Recurrence(Freq.Weekly, 1, setOf(DayOfWeek.MONDAY)), r.recurrence)
        assertEquals(d(11, 16), r.date)
    }

    @Test fun everyMonAndWed() {
        val r = p("swim every mon and wed")
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), r.recurrence?.days)
        assertEquals(d(11, 11), r.date)
    }

    @Test fun everyWeekdayAtNine() {
        val r = p("standup every weekday 9am")
        assertEquals("standup", r.title)
        assertEquals(Recurrence.WEEKDAYS, r.recurrence?.days)
        assertEquals(LocalTime.of(9, 0), r.time)
        assertEquals(d(11, 10), r.date)
    }

    @Test fun everyThreeDays() {
        val r = p("water plants every 3 days")
        assertEquals(Recurrence(Freq.Daily, 3), r.recurrence)
        assertEquals(d(11, 10), r.date)
    }

    @Test fun everyOtherWeek() {
        val r = p("every other week review notes")
        assertEquals("review notes", r.title)
        assertEquals(Recurrence(Freq.Weekly, 2), r.recurrence)
    }

    @Test fun monthlyWord() = assertEquals(Recurrence(Freq.Monthly), p("pay rent monthly").recurrence)

    @Test fun disabledDateKeepsWordsInTitle() {
        val r = p("Monday blues", disabled = setOf(TokenKind.Date))
        assertEquals("Monday blues", r.title)
        assertNull(r.date)
    }

    @Test fun defaultDateAppliesWhenNoneTyped() {
        val r = p("read notes", default = d(11, 10))
        assertEquals(d(11, 10), r.date)
        assertEquals(true, r.dateIsDefault)
    }

    @Test fun typedDateBeatsDefault() {
        val r = p("read notes fri", default = d(11, 10))
        assertEquals(d(11, 13), r.date)
        assertEquals(false, r.dateIsDefault)
    }

    @Test fun dueWordIsDropped() = assertEquals("essay", p("essay due fri").title)

    @Test fun onlyTokensKeepsOriginalTitle() = assertEquals("tomorrow", p("tomorrow").title)

    @Test fun tokensCoverTheRightText() {
        val text = "essay fri 5pm #english"
        val r = p(text)
        val pieces = r.tokens.associate { it.kind to text.substring(it.start, it.end) }
        assertEquals("fri", pieces[TokenKind.Date])
        assertEquals("5pm", pieces[TokenKind.Time])
        assertEquals("#english", pieces[TokenKind.Tag])
    }

    // ---------- More ways to say a day ----------

    @Test fun fridayNextWeek() {
        val r = p("essay friday next week")
        assertEquals("essay", r.title)
        assertEquals(d(11, 20), r.date)
        assertEquals(d(11, 20), p("essay next week friday").date)
        assertEquals(d(11, 16), p("essay monday next week").date)
    }

    @Test fun weekdayThisWeek() = assertEquals(d(11, 13), p("quiz fri this week").date)

    @Test fun weekdayAfterNext() {
        val r = p("meeting the friday after next")
        assertEquals("meeting", r.title)
        assertEquals(d(11, 20), r.date)
    }

    @Test fun comingWeekday() = assertEquals(d(11, 16), p("standup this coming monday").date)

    @Test fun weekends() {
        assertEquals(d(11, 14), p("trip this weekend").date)
        assertEquals(d(11, 21), p("trip next weekend").date)
    }

    @Test fun endAndStartOf() {
        assertEquals(d(11, 30), p("report end of month").date)
        assertEquals(d(11, 13), p("essay by eow").date)
        assertEquals(d(11, 20), p("essay end of next week").date)
        assertEquals(d(12, 1), p("budget start of next month").date)
        assertEquals("report", p("report end of month").title)
    }

    @Test fun endOfDay() {
        val r = p("submit form eod")
        assertEquals("submit form", r.title)
        assertEquals(d(11, 10), r.date)
        assertEquals(LocalTime.of(17, 0), r.time)
    }

    @Test fun typos() {
        assertEquals(d(11, 11), p("pay fees tommorow").date)
        assertEquals(d(11, 11), p("pay fees 2moro").date)
        assertEquals(d(11, 11), p("club wensday").date)
        assertEquals(d(11, 12), p("club thurday").date)
        val r = p("movie tonite")
        assertEquals(d(11, 10), r.date)
        assertEquals(LocalTime.of(20, 0), r.time)
    }

    @Test fun relativeWords() {
        assertEquals(d(11, 12), p("reply in a couple of days").date)
        assertEquals(d(11, 24), p("checkup in a fortnight").date)
        assertEquals(d(11, 13), p("reply in three days").date)
        assertEquals(d(12, 10), p("renew next month").date)
    }

    @Test fun inMinutesAndHours() {
        val r = p("call back in 30 minutes")
        assertEquals("call back", r.title)
        assertEquals(d(11, 10), r.date)
        assertEquals(LocalTime.of(14, 30), r.time)
        assertEquals(LocalTime.of(16, 0), p("leave in 2 hours").time)
        assertEquals(LocalTime.of(15, 0), p("leave in an hour").time)
        assertEquals(LocalTime.of(14, 30), p("leave in half an hour").time)
    }

    @Test fun numericDates() {
        fun at(text: String, locale: Locale) = SmartParser.parse(text, now, locale = locale).date
        assertEquals(d(11, 20), at("exam 11/20", Locale.US))
        assertEquals(d(11, 20), at("exam 20/11", Locale.UK))
        assertEquals(d(11, 20), at("exam 11/20", Locale.UK)) // 20 can't be a month
        assertEquals(d(3, 4, 2027), at("exam 3/4", Locale.US))
        assertEquals(d(4, 3, 2027), at("exam 3/4", Locale.UK))
        assertEquals(d(1, 15, 2027), at("exam 1/15/27", Locale.US))
        assertEquals(d(12, 1), p("exam 2026-12-01").date)
        assertEquals("exam", p("exam 2026-12-01").title)
    }

    @Test fun monthDayWithYear() = assertEquals(d(12, 4, 2027), p("final dec 4 2027").date)

    @Test fun dayRange() {
        val r = p("group project mon to fri")
        assertEquals("group project", r.title)
        assertEquals(d(11, 16), r.startDate)
        assertEquals(d(11, 20), r.date)
        assertEquals(d(11, 12), p("retreat thu-sat").startDate)
    }

    // ---------- More ways to say a time ----------

    @Test fun timeRanges() {
        val a = p("study 3-5pm")
        assertEquals("study", a.title)
        assertEquals(LocalTime.of(15, 0), a.time)
        assertEquals(120, a.durationMinutes)
        val b = p("lab 9:30-11")
        assertEquals(LocalTime.of(9, 30), b.time)
        assertEquals(90, b.durationMinutes)
        assertEquals(d(11, 11), b.date)
        val c = p("seminar from 2 to 4pm")
        assertEquals("seminar", c.title)
        assertEquals(LocalTime.of(14, 0), c.time)
        assertEquals(120, c.durationMinutes)
        val e = p("brunch 11-1pm")
        assertEquals(LocalTime.of(11, 0), e.time)
        assertEquals(120, e.durationMinutes)
    }

    @Test fun numberRangesWithoutTimeSignsStayText() {
        val r = p("read pages 10-20")
        assertEquals("read pages 10-20", r.title)
        assertNull(r.time)
    }

    @Test fun spokenTimes() {
        assertEquals(LocalTime.of(16, 30), p("call half past 4").time)
        assertEquals(LocalTime.of(16, 45), p("call quarter to 5").time)
        assertEquals(LocalTime.of(17, 15), p("call quarter past 5").time)
        assertEquals(LocalTime.of(17, 0), p("meet 5 o'clock").time)
        assertEquals(LocalTime.of(23, 59), p("submit by midnight").time)
    }

    @Test fun partOfDayWithHour() {
        val r = p("party tomorrow evening at 7")
        assertEquals("party", r.title)
        assertEquals(d(11, 11), r.date)
        assertEquals(LocalTime.of(19, 0), r.time)
        assertEquals(LocalTime.of(6, 0), p("run tomorrow morning at 6").time)
    }

    @Test fun lengths() {
        val r = p("study for 2 hours")
        assertEquals("study", r.title)
        assertEquals(120, r.durationMinutes)
        assertEquals(45, p("read for 45 min").durationMinutes)
        assertEquals(90, p("gym for 1h 30m").durationMinutes)
        assertEquals(30, p("nap for half an hour").durationMinutes)
    }

    // ---------- More repeats ----------

    @Test fun everyPartOfDay() {
        val r = p("journal every evening")
        assertEquals(Recurrence(Freq.Daily), r.recurrence)
        assertEquals(LocalTime.of(19, 0), r.time)
        assertEquals(LocalTime.of(20, 0), p("journal every evening at 8").time)
    }

    @Test fun monthlyOnADay() {
        val r = p("pay rent every month on the 15th")
        assertEquals("pay rent", r.title)
        assertEquals(Recurrence(Freq.Monthly), r.recurrence)
        assertEquals(d(11, 15), r.date)
        assertEquals(d(12, 1), p("budget every 1st").date)
    }

    @Test fun biweekly() {
        assertEquals(Recurrence(Freq.Weekly, 2), p("biweekly meeting").recurrence)
        assertEquals(Recurrence(Freq.Weekly, 2), p("meeting every fortnight").recurrence)
    }

    @Test fun pluralWeekdays() {
        val r = p("gym mondays")
        assertEquals("gym", r.title)
        assertEquals(Recurrence(Freq.Weekly, 1, setOf(DayOfWeek.MONDAY)), r.recurrence)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), p("club on mondays and wednesdays").recurrence?.days)
        assertEquals(Recurrence.WEEKDAYS, p("lunch weekdays").recurrence?.days)
        assertEquals(d(11, 13), p("quiz each friday").date)
    }

    @Test fun morePriorityWords() {
        assertEquals(Priority.High, p("finish essay p1").priority)
        assertEquals(Priority.Low, p("tidy desk p3").priority)
        assertEquals(Priority.High, p("pay fees !urgent").priority)
        assertEquals("pay fees", p("pay fees !urgent").title)
    }

    @Test fun beforeIsDropped() = assertEquals("submit", p("submit before fri").title)
}
