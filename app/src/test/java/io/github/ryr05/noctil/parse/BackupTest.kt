package io.github.ryr05.noctil.parse

import io.github.ryr05.noctil.data.Backup
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupTest {
    @Test fun plainCellsAreUnquoted() = assertEquals("Lab report", Backup.csvCell("Lab report"))

    @Test fun commasAndQuotesAreEscaped() {
        assertEquals("\"Read ch. 4, 5\"", Backup.csvCell("Read ch. 4, 5"))
        assertEquals("\"Say \"\"hi\"\"\"", Backup.csvCell("Say \"hi\""))
        assertEquals("\"line one\nline two\"", Backup.csvCell("line one\nline two"))
    }
}
