package `in`.c1ph3rj.scanly.feature.preview

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import `in`.c1ph3rj.scanly.domain.model.NormalizedTextPoint
import `in`.c1ph3rj.scanly.domain.model.RecognizedTextToken

class PageTextOverlayTest {
    private val tokens = listOf(
        token(index = 0, left = 0.1f, top = 0.2f, right = 0.3f, bottom = 0.4f),
        token(index = 1, left = 0.6f, top = 0.2f, right = 0.8f, bottom = 0.4f),
    )

    @Test
    fun hitTestMapsNormalizedBoundsToTheFittedImage() {
        assertEquals(
            0,
            findTextTokenAt(
                tokens = tokens,
                position = Offset(200f, 150f),
                width = 1_000f,
                height = 500f,
            ),
        )
        assertEquals(
            1,
            findTextTokenAt(
                tokens = tokens,
                position = Offset(700f, 150f),
                width = 1_000f,
                height = 500f,
            ),
        )
        assertNull(
            findTextTokenAt(
                tokens = tokens,
                position = Offset(450f, 150f),
                width = 1_000f,
                height = 500f,
            ),
        )
    }

    @Test
    fun nearestTokenRespectsMaximumDragDistance() {
        assertEquals(
            0,
            findNearestTextToken(
                tokens = tokens,
                position = Offset(320f, 150f),
                width = 1_000f,
                height = 500f,
                maximumDistance = 150f,
            ),
        )
        assertNull(
            findNearestTextToken(
                tokens = tokens,
                position = Offset(500f, 450f),
                width = 1_000f,
                height = 500f,
                maximumDistance = 100f,
            ),
        )
    }

    private fun token(
        index: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) = RecognizedTextToken(
        index = index,
        text = "word-$index",
        blockIndex = 0,
        lineIndex = 0,
        cornerPoints = listOf(
            NormalizedTextPoint(left, top),
            NormalizedTextPoint(right, top),
            NormalizedTextPoint(right, bottom),
            NormalizedTextPoint(left, bottom),
        ),
    )
}
