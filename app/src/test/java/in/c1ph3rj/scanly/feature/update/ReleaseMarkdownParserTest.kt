package `in`.c1ph3rj.scanly.feature.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseMarkdownParserTest {
    @Test
    fun parse_groupsHeadingsBulletsAndParagraphs() {
        val blocks = ReleaseMarkdownParser.parse(
            """
            # Scanly v1.0.5

            ## Added
            - Faster export flow
            - Better **document** organization

            Existing scans stay available after update.
            """.trimIndent(),
        )

        assertEquals(4, blocks.size)
        assertEquals(MarkdownBlock.Heading(level = 1, text = "Scanly v1.0.5"), blocks[0])
        assertEquals(MarkdownBlock.Heading(level = 2, text = "Added"), blocks[1])
        assertTrue(blocks[2] is MarkdownBlock.BulletList)
        assertEquals(
            listOf("Faster export flow", "Better **document** organization"),
            (blocks[2] as MarkdownBlock.BulletList).items,
        )
        assertEquals(
            MarkdownBlock.Paragraph("Existing scans stay available after update."),
            blocks[3],
        )
    }
}
