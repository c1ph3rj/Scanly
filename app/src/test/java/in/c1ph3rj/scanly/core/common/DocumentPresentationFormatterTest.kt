package `in`.c1ph3rj.scanly.core.common

import org.junit.Assert.assertEquals
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
}
