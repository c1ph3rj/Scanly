package `in`.c1ph3rj.scanly.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextDeriverTest {
    private fun values(text: String) = SearchTextDeriver.derive(text).map { it.value }

    @Test
    fun joinsWordsBrokenAcrossLinesOrByHyphens() {
        val tokens = SearchTextDeriver.derive("the agree-\nment and e-mail, INV-2041")
        assertEquals(listOf("agreement", "email", "inv2041"), tokens.map { it.value })
        assertEquals("agree-\nment", "the agree-\nment and e-mail, INV-2041".substring(tokens.first().sourceRange))
    }

    @Test
    fun groupedNumbersGetDigitOnlyForms() {
        assertEquals(listOf("125000", "1250"), values("Total 1,250.00"))
        assertEquals(listOf("1250"), values("Rs 1,250 due"))
        assertEquals(listOf("125000"), values("₹1,25,000"))
        assertEquals(listOf("125000", "1250"), values("1.250,00 EUR"))
    }

    @Test
    fun plainDecimalsAreLeftAlone() {
        assertTrue(values("pi is 3.14 and 12.5").isEmpty())
    }

    @Test
    fun phoneLikeDigitRunsAreJoined() {
        assertEquals(listOf("9840012345"), values("Call 98 400 12345"))
        assertTrue(values("Qty 12 34").isEmpty())
    }

    @Test
    fun datesInAnyFormatShareOneForm() {
        assertTrue("d20261102" in values("Dated 02/11/2026"))
        assertTrue("d20261102" in values("Dated 2026-11-02"))
        assertTrue("d20261102" in values("Dated 2 Nov 2026"))
        assertTrue("d20261102" in values("Dated 2nd November, 2026"))
        assertTrue("d20261102" in values("Dated November 2, 2026"))
        assertTrue("d20261102" in values("Dated 02-11-26"))
    }

    @Test
    fun ambiguousNumericDatesGetBothReadingsAndInvalidOnesNone() {
        val both = values("02/11/2026")
        assertTrue("d20261102" in both && "d20260211" in both)
        val dayFirstOnly = values("25/11/2026")
        assertTrue("d20261125" in dayFirstOnly && dayFirstOnly.none { it.startsWith("d2026") && it != "d20261125" })
        assertTrue(values("31/02/2026").none { it.startsWith("d") })
    }

    @Test
    fun derivedTextIsSpaceSeparatedValues() {
        assertEquals("1250", SearchTextDeriver.derivedText("Rs 1,250"))
        assertEquals("", SearchTextDeriver.derivedText("nothing to see"))
    }
}
