package io.github.ryr05.noctil.parse

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class BulkSplitTest {
    private val now = LocalDateTime.of(2026, 11, 10, 14, 0)

    @Test fun linesBulletsAndSemicolons() {
        assertEquals(
            listOf("Essay due fri", "Lab report thu 9am", "Read chapter 4", "Quiz mon"),
            BulkSplit.split("- Essay due fri\n• Lab report thu 9am\n\n1. Read chapter 4; Quiz mon", now),
        )
    }

    @Test fun commasOnlyWhenEveryPieceHasADate() {
        assertEquals(listOf("lab report fri", "essay mon", "quiz tomorrow"), BulkSplit.split("lab report fri, essay mon, quiz tomorrow", now))
        assertEquals(listOf("Email prof, ask about grade"), BulkSplit.split("Email prof, ask about grade", now))
    }

    @Test fun emptyLinesAreDropped() = assertEquals(emptyList<String>(), BulkSplit.split("\n \n-\n", now))
}
