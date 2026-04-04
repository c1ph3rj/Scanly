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
}
