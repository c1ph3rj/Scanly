package `in`.c1ph3rj.scanly.core.common

import `in`.c1ph3rj.scanly.core.common.NamingTemplateRenderer.Problem
import `in`.c1ph3rj.scanly.domain.model.NamingTemplateRules
import `in`.c1ph3rj.scanly.domain.model.NamingToken
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class NamingTemplateRendererTest {
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun render_fillsNumericDateTokens() {
        assertEquals(
            "2026-03-05 / 2026 / 03 / 05",
            NamingTemplateRenderer.render("{iso} / {year} / {month} / {day}", AT, Locale.US, 1),
        )
    }

    @Test
    fun render_fillsLocaleTokensLikeDateFormat() {
        val date = Date(AT)
        assertEquals(
            "Scan ${DateFormat.getDateInstance(DateFormat.SHORT, Locale.US).format(date)}",
            NamingTemplateRenderer.render("Scan {date}", AT, Locale.US, 1),
        )
        assertEquals(
            DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US).format(date),
            NamingTemplateRenderer.render("{time}", AT, Locale.US, 1),
        )
        assertEquals(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.US).format(date),
            NamingTemplateRenderer.render("{datetime}", AT, Locale.US, 1),
        )
        assertEquals("Thursday", NamingTemplateRenderer.render("{weekday}", AT, Locale.US, 1))
    }

    @Test
    fun render_keysAreCaseInsensitive() {
        assertEquals(
            "Invoice 2026-03-05 7",
            NamingTemplateRenderer.render("Invoice {ISO} {N}", AT, Locale.US, 7),
        )
        assertEquals("2026", NamingTemplateRenderer.render("{Year}", AT, Locale.US, 1))
    }

    @Test
    fun render_leavesUnknownTokensAsWritten() {
        assertEquals("Note {foo} 2026", NamingTemplateRenderer.render("Note {foo} {year}", AT, Locale.US, 1))
        assertEquals("{} {12}", NamingTemplateRenderer.render("{} {12}", AT, Locale.US, 1))
    }

    @Test
    fun render_collapsesAndTrimsWhitespace() {
        assertEquals("Receipt 3", NamingTemplateRenderer.render("  Receipt \t  {n}  ", AT, Locale.US, 3))
    }

    @Test
    fun render_everyTokenIsKnown() {
        NamingToken.entries.forEach { token ->
            val rendered = NamingTemplateRenderer.render(token.placeholder, AT, Locale.US, 1)
            assertFalse("${token.placeholder} was left unfilled", rendered.contains("{"))
            assertTrue(NamingTemplateRenderer.unknownTokens(token.placeholder).isEmpty())
        }
    }

    @Test
    fun unknownTokens_listsEachDistinctUnknownAsWritten() {
        assertEquals(
            listOf("{foo}", "{Bar}"),
            NamingTemplateRenderer.unknownTokens("{foo} {date} {Bar} {foo} {N}"),
        )
    }

    @Test
    fun usesNumber_detectsNumberTokenIgnoringCase() {
        assertTrue(NamingTemplateRenderer.usesNumber("Scan {n}"))
        assertTrue(NamingTemplateRenderer.usesNumber("Scan {N}"))
        assertFalse(NamingTemplateRenderer.usesNumber("Scan {date} n"))
    }

    @Test
    fun renderUnique_withNumber_countsUpPastTakenNamesIgnoringCase() {
        val existing = listOf("receipt 1", "Receipt 2 ", "Other")
        assertEquals("Receipt 3", NamingTemplateRenderer.renderUnique("Receipt {n}", existing, AT, Locale.US))
        assertEquals("Receipt 1", NamingTemplateRenderer.renderUnique("Receipt {n}", emptyList(), AT, Locale.US))
    }

    @Test
    fun renderUnique_withoutNumber_returnsBaseNameEvenIfTaken() {
        assertEquals(
            "Invoice 2026-03-05",
            NamingTemplateRenderer.renderUnique("Invoice {iso}", listOf("Invoice 2026-03-05"), AT, Locale.US),
        )
    }

    @Test
    fun validate_acceptsGoodPatterns() {
        assertEquals(emptySet<Problem>(), NamingTemplateRenderer.validate("Receipt {date}"))
        assertEquals(emptySet<Problem>(), NamingTemplateRenderer.validate("  {n}  "))
        assertEquals(emptySet<Problem>(), NamingTemplateRenderer.validate("Plain name"))
    }

    @Test
    fun validate_reportsBlank() {
        assertEquals(setOf(Problem.BLANK), NamingTemplateRenderer.validate(""))
        assertEquals(setOf(Problem.BLANK), NamingTemplateRenderer.validate("   \t "))
    }

    @Test
    fun validate_reportsTooLongAfterTrimming() {
        val limit = NamingTemplateRules.MAX_PATTERN_LENGTH
        assertEquals(emptySet<Problem>(), NamingTemplateRenderer.validate("  " + "a".repeat(limit) + "  "))
        assertEquals(setOf(Problem.TOO_LONG), NamingTemplateRenderer.validate("a".repeat(limit + 1)))
    }

    @Test
    fun validate_reportsUnknownTokens() {
        assertEquals(setOf(Problem.UNKNOWN_TOKEN), NamingTemplateRenderer.validate("Scan {when}"))
        assertEquals(
            setOf(Problem.TOO_LONG, Problem.UNKNOWN_TOKEN),
            NamingTemplateRenderer.validate("{when}" + "a".repeat(NamingTemplateRules.MAX_PATTERN_LENGTH)),
        )
    }

    private companion object {
        /** 2026-03-05T14:07:00Z, a Thursday. */
        const val AT = 1_772_719_620_000L
    }
}
