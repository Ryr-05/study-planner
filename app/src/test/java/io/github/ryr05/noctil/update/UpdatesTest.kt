package io.github.ryr05.noctil.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test
    fun newerVersionsAreSpotted() {
        assertTrue(Updates.isNewer("v0.1.48", "0.1.47"))
        assertTrue(Updates.isNewer("0.1.100", "0.1.99")) // numbers, not text
        assertTrue(Updates.isNewer("0.2.0", "0.1.99"))
        assertTrue(Updates.isNewer("v0.1.48", "0.1.0-local")) // local builds are older than any release
    }

    @Test
    fun sameOrOlderIsNotAnUpdate() {
        assertFalse(Updates.isNewer("v0.1.47", "0.1.47"))
        assertFalse(Updates.isNewer("0.1.46", "0.1.47"))
        assertFalse(Updates.isNewer("0.1", "0.1.0"))
        assertFalse(Updates.isNewer("", "0.1.47"))
        assertFalse(Updates.isNewer("latest", "0.1.47"))
    }

    @Test
    fun whatsNewIsTheCommitSubject() {
        val notes = "Big system text: priority chips wrap, task detail rows grow\n\nMore detail here.\n\nCo-Authored-By: Someone <x@example.com>"
        assertEquals("Big system text: priority chips wrap, task detail rows grow", Updates.whatsNew(notes))
        assertEquals("", Updates.whatsNew(""))
        assertEquals("", Updates.whatsNew("\n\nCo-Authored-By: Someone <x@example.com>\n"))
        assertEquals(140, Updates.whatsNew("x".repeat(300)).length)
    }
}
