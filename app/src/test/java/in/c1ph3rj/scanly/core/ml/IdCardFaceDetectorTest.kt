package `in`.c1ph3rj.scanly.core.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdCardFaceDetectorTest {
    @Test
    fun paddingExpandsAndClampsFaceRegion() {
        val padded = NormalizedFaceRegion(
            left = 0.02f,
            top = 0.05f,
            right = 0.32f,
            bottom = 0.55f,
        ).padded(
            horizontalFraction = 0.25f,
            verticalFraction = 0.30f,
        )

        assertEquals(0f, padded.left)
        assertEquals(0f, padded.top)
        assertTrue(padded.right > 0.32f)
        assertTrue(padded.bottom > 0.55f)
        assertTrue(padded.isUsable)
    }

    @Test
    fun tinyOrInvertedRegionsAreRejected() {
        assertFalse(
            NormalizedFaceRegion(0.2f, 0.2f, 0.21f, 0.21f).isUsable,
        )
        assertFalse(
            NormalizedFaceRegion(0.6f, 0.2f, 0.4f, 0.5f).isUsable,
        )
    }
}
