package io.github.ryr05.noctil.parse

import androidx.compose.ui.graphics.Color
import io.github.ryr05.noctil.data.model.SchoolClass
import io.github.ryr05.noctil.parse.QuickSuggest.Apply
import io.github.ryr05.noctil.parse.QuickSuggest.NamedThing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class QuickSuggestTest {
    private val now = LocalDateTime.of(2026, 11, 10, 14, 0) // Tuesday 2 PM
    private val calc = SchoolClass(1, "Calculus II", "Hall B", Color.White, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), LocalTime.of(10, 0), LocalTime.of(11, 0), 7)
    private val lists = listOf(NamedThing(7, "Calculus II"), NamedThing(8, "Chemistry"))

    private fun suggest(text: String, hasList: Boolean = false) =
        QuickSuggest.suggest(SmartParser.parse(text, now), now, listOf(calc), lists, listOf("chem", "english"), hasList)

    @Test fun matchingClassSuggestsBeforeNextClassAndItsList() {
        val s = suggest("calc homework 5")
        val due = s.first { it.kind == TokenKind.Date }
        assertTrue(due.label.startsWith("Before Calculus II"))
        val words = (due.apply as Apply.Insert).text
        // Typed back in, it lands on Wednesday 10 AM.
        val reparsed = SmartParser.parse("calc homework 5 $words", now)
        assertEquals(now.toLocalDate().plusDays(1), reparsed.date)
        assertEquals(LocalTime.of(10, 0), reparsed.time)
        assertEquals(Apply.SetList(7, "Calculus II"), s.first { it.kind == TokenKind.List }.apply)
    }

    @Test fun tagAndListByName() {
        val s = suggest("chem lab writeup fri")
        assertEquals(Apply.Insert("#chem"), s.first { it.kind == TokenKind.Tag }.apply)
        assertEquals(Apply.Insert("~Chemistry"), s.first { it.kind == TokenKind.List }.apply)
        assertTrue("date was typed, so no date suggestion", s.none { it.kind == TokenKind.Date })
    }

    @Test fun examWordsSuggestExamTag() {
        assertEquals(Apply.Insert("#exam"), suggest("Bio midterm thu").first { it.kind == TokenKind.Tag }.apply)
    }

    @Test fun quietForShortOrFullyTypedTasks() {
        assertTrue(suggest("ca").isEmpty())
        assertTrue(suggest("Chem lab fri #chem ~Chemistry").isEmpty())
        assertEquals(listOf(TokenKind.Date), suggest("Laundry").map { it.kind })
        assertTrue(suggest("chem lab fri", hasList = true).none { it.kind == TokenKind.List })
    }

    @Test fun nextSessionSkipsOneThatAlreadyStarted() {
        val monday10 = LocalDateTime.of(2026, 11, 9, 10, 30)
        assertEquals(LocalDateTime.of(2026, 11, 11, 10, 0), QuickSuggest.nextSession(calc, monday10))
    }
}
