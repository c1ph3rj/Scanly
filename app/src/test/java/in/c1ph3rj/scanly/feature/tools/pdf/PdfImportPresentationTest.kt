package `in`.c1ph3rj.scanly.feature.tools.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfImportPresentationTest {
    @Test
    fun buttonLabel_withNothingSelected_saysAddToLibrary() {
        assertEquals("Add to library", formatPdfImportButtonLabel(0))
    }

    @Test
    fun buttonLabel_withOnePage_isSingular() {
        assertEquals("Add 1 page", formatPdfImportButtonLabel(1))
    }

    @Test
    fun buttonLabel_withManyPages_isPlural() {
        assertEquals("Add 12 pages", formatPdfImportButtonLabel(12))
    }
}
