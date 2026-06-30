package `in`.c1ph3rj.scanly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognizedPageTextTest {
    @Test
    fun selectedTextPreservesSpacesAndLineBreaks() {
        val recognizedText = RecognizedPageText(
            tokens = listOf(
                token(index = 0, text = "Hello", lineIndex = 0),
                token(index = 1, text = "world", lineIndex = 0),
                token(index = 2, text = "Scanly", lineIndex = 1),
            ),
        )

        assertEquals("Hello world\nScanly", recognizedText.textForSelection(0..2))
        assertEquals("world\nScanly", recognizedText.textForSelection(1..2))
        assertEquals("", recognizedText.textForSelection(5..6))
    }

    private fun token(
        index: Int,
        text: String,
        lineIndex: Int,
    ) = RecognizedTextToken(
        index = index,
        text = text,
        blockIndex = 0,
        lineIndex = lineIndex,
        cornerPoints = listOf(
            NormalizedTextPoint(0f, 0f),
            NormalizedTextPoint(1f, 0f),
            NormalizedTextPoint(1f, 1f),
            NormalizedTextPoint(0f, 1f),
        ),
    )
}
