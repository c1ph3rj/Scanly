package `in`.c1ph3rj.scanly.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextReflowTest {
    @Test
    fun proseLinesFlowIntoOneParagraphAndRejoinHyphenation() {
        val text = TextReflow.paragraph(
            listOf(
                "This Lease Agreement is entered into on 1 October",
                "2026 by and between the Landlord and the Ten-",
                "ant named in Schedule A for the premises.",
            ),
        )
        assertEquals(
            "This Lease Agreement is entered into on 1 October 2026 by and between the Landlord and " +
                "the Tenant named in Schedule A for the premises.",
            text,
        )
    }

    @Test
    fun shortLineLayoutsKeepTheirBreaks() {
        assertEquals(
            "Unit 4B\nMain Street\nPune 411001",
            TextReflow.paragraph(listOf("Unit 4B", "Main Street", "Pune 411001")),
        )
    }

    @Test
    fun listItemsInsideProseStartNewLines() {
        val text = TextReflow.paragraph(
            listOf(
                "The Tenant agrees to the following conditions of use:",
                "1. The premises are used only as a private residence",
                "2. No structural changes are made without consent",
            ),
        )
        assertEquals(3, text.lines().size)
    }

    @Test
    fun pageSeparatesBlocksWithBlankLine() {
        assertEquals("A\n\nB", TextReflow.page(listOf("A", " ", "B")))
    }
}
