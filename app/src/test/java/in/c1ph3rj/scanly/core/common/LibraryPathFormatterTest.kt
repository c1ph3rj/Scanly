package `in`.c1ph3rj.scanly.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryPathFormatterTest {
    @Test
    fun primaryVolumeFormatsAsInternalStoragePath() {
        assertEquals(
            "Internal storage/Documents/Scanly",
            LibraryPathFormatter.formatTreeDocumentId("primary:Documents/Scanly"),
        )
    }

    @Test
    fun externalVolumeKeepsVolumeLabel() {
        assertEquals(
            "ABCD-1234/Download/Scanly",
            LibraryPathFormatter.formatTreeDocumentId("ABCD-1234:Download/Scanly"),
        )
    }

    @Test
    fun blankRelativePathReturnsVolumeOnly() {
        assertEquals(
            "Internal storage",
            LibraryPathFormatter.formatTreeDocumentId("primary:"),
        )
    }
}
