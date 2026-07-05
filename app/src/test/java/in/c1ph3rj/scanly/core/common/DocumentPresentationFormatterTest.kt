package `in`.c1ph3rj.scanly.core.common

import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPresentationFormatterTest {
    @Test
    fun normalizeTitle_collapsesWhitespaceAndFallsBackWhenBlank() {
        assertEquals(
            "Invoice April",
            DocumentPresentationFormatter.normalizeTitle("  Invoice   April  "),
        )
        assertEquals(
            "Untitled document",
            DocumentPresentationFormatter.normalizeTitle("   "),
        )
    }

    @Test
    fun initials_useFirstTwoWordsOrTwoLettersFromSingleWord() {
        assertEquals("IA", DocumentPresentationFormatter.initials("Invoice April"))
        assertEquals("RE", DocumentPresentationFormatter.initials("Receipt"))
        assertEquals("UD", DocumentPresentationFormatter.initials("   "))
    }

    @Test
    fun resolveUniqueTitle_appendsSuffixWhenTitleAlreadyExists() {
        val existing = listOf("Invoice", "Invoice (2)")

        assertEquals(
            "Invoice (3)",
            DocumentPresentationFormatter.resolveUniqueTitle(
                baseTitle = "Invoice",
                existingTitles = existing,
            ),
        )
    }

    @Test
    fun resolveUniqueTitle_returnsBaseWhenUnused() {
        assertEquals(
            "Invoice",
            DocumentPresentationFormatter.resolveUniqueTitle(
                baseTitle = "Invoice",
                existingTitles = listOf("Receipt"),
            ),
        )
    }

    @Test
    fun formatTitle_scanDateTime_startsWithScanPrefix() {
        val title = DocumentPresentationFormatter.formatTitle(
            format = DocumentTitleFormat.ScanDateTime,
            atMillis = 1_751_289_300_000L,
        )

        assertTrue(title.startsWith("Scan "))
    }

    @Test
    fun formatTitle_scanIsoDate_usesIsoPattern() {
        val title = DocumentPresentationFormatter.formatTitle(
            format = DocumentTitleFormat.ScanIsoDate,
            atMillis = 1_751_289_300_000L,
        )

        assertEquals("Scan 2025-06-30", title)
    }

    @Test
    fun documentTitleFormat_next_cyclesThroughAllFormats() {
        assertEquals(
            DocumentTitleFormat.DocumentDateTime,
            DocumentTitleFormat.ScanDateTime.next(),
        )
        assertEquals(
            DocumentTitleFormat.ScanDateTime,
            DocumentTitleFormat.ScanIsoDate.next(),
        )
    }

    @Test
    fun normalizeGroupTitle_fallsBackWhenBlank() {
        assertEquals(
            "Untitled folder",
            DocumentPresentationFormatter.normalizeGroupTitle("   "),
        )
    }

    @Test
    fun formatGroupTitle_folderIsoDate_usesIsoPattern() {
        val title = DocumentPresentationFormatter.formatGroupTitle(
            format = GroupTitleFormat.FolderIsoDate,
            atMillis = 1_751_289_300_000L,
        )

        assertEquals("Folder 2025-06-30", title)
    }

    @Test
    fun uniqueGroupTitle_appendsSuffixWhenFormatAlreadyExists() {
        val existingTitle = DocumentPresentationFormatter.formatGroupTitle(
            format = GroupTitleFormat.FolderDateTime,
            atMillis = 1_751_289_300_000L,
        )

        val uniqueTitle = DocumentPresentationFormatter.uniqueGroupTitle(
            format = GroupTitleFormat.FolderDateTime,
            existingTitles = listOf(existingTitle),
            atMillis = 1_751_289_300_000L,
        )

        assertEquals("$existingTitle (2)", uniqueTitle)
    }

    @Test
    fun uniqueDocumentTitle_appendsSuffixWhenFormatAlreadyExists() {
        val existingTitle = DocumentPresentationFormatter.formatTitle(
            format = DocumentTitleFormat.ScanIsoDate,
            atMillis = 1_751_289_300_000L,
        )

        val uniqueTitle = DocumentPresentationFormatter.uniqueDocumentTitle(
            format = DocumentTitleFormat.ScanIsoDate,
            existingTitles = listOf(existingTitle),
            atMillis = 1_751_289_300_000L,
        )

        assertEquals("$existingTitle (2)", uniqueTitle)
    }
}
