package `in`.c1ph3rj.scanly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfExportOptionsTest {

    @Test
    fun passwordProtectionIsOptional() {
        assertNull(PdfExportOptions().validationError())
    }

    @Test
    fun passwordProtectionRejectsBlankAndShortPasswords() {
        assertEquals(
            "Enter a PDF password.",
            PdfExportOptions(password = "   ").validationError(),
        )
        assertEquals(
            "Use at least 4 characters.",
            PdfExportOptions(password = "abc").validationError(),
        )
    }

    @Test
    fun passwordProtectionAcceptsValidPasswords() {
        assertNull(PdfExportOptions(password = "scanly-secret").validationError())
    }

    @Test
    fun passwordProtectionCapsInputLength() {
        assertEquals(
            "Use no more than 64 characters.",
            PdfExportOptions(password = "x".repeat(65)).validationError(),
        )
    }
}
