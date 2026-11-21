package `in`.c1ph3rj.scanly.feature.naming

import `in`.c1ph3rj.scanly.domain.model.NamingTemplate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NamingTemplateDuplicateTest {
    private val templates = listOf(
        NamingTemplate("template-1", "Receipt {date}", isDefault = false, createdAtMillis = 0L, updatedAtMillis = 0L),
        NamingTemplate("template-2", "{iso} Invoice {n}", isDefault = true, createdAtMillis = 1L, updatedAtMillis = 1L),
    )

    @Test
    fun samePatternIgnoringCaseAndOuterSpaces_isDuplicate() {
        assertTrue(isDuplicatePattern("  receipt {DATE} ", templates, editingId = null))
    }

    @Test
    fun editingTheSameTemplate_isNotDuplicate() {
        assertFalse(isDuplicatePattern("Receipt {date}", templates, editingId = "template-1"))
        assertTrue(isDuplicatePattern("{ISO} invoice {n}", templates, editingId = "template-1"))
    }

    @Test
    fun newOrBlankPattern_isNotDuplicate() {
        assertFalse(isDuplicatePattern("Notes {weekday}", templates, editingId = null))
        assertFalse(isDuplicatePattern("   ", templates, editingId = null))
    }
}
