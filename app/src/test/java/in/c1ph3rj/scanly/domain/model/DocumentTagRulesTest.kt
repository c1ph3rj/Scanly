package `in`.c1ph3rj.scanly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DocumentTagRulesTest {
    @Test
    fun normalizeName_trimsAndCollapsesWhitespace() {
        assertEquals("Travel Receipts", DocumentTagRules.normalizeName("  Travel \t\n  Receipts "))
        assertEquals("", DocumentTagRules.normalizeName("   "))
    }

    @Test
    fun normalizeName_capsLength() {
        val long = "x".repeat(DocumentTagRules.MAX_NAME_LENGTH + 10)
        assertEquals(DocumentTagRules.MAX_NAME_LENGTH, DocumentTagRules.normalizeName(long).length)
    }

    @Test
    fun key_ignoresCaseAndSpacing() {
        assertEquals(DocumentTagRules.key("Work  Stuff"), DocumentTagRules.key(" work stuff "))
        assertEquals("work stuff", DocumentTagRules.key("WORK Stuff"))
    }

    @Test
    fun key_isLocaleIndependent() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            // Turkish lowercase would turn "I" into dotless "ı".
            assertEquals("invoice", DocumentTagRules.key("INVOICE"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
