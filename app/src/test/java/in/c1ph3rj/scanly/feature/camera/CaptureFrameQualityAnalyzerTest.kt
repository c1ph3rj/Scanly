package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.DetectionFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureFrameQualityAnalyzerTest {
    @Test
    fun coveredLens_returnsBlockedGuidanceWithoutDocument() {
        val quality = CaptureFrameQualityAnalyzer.analyze(solidFrame(value = 8))

        assertEquals(CaptureSceneIssue.LENS_BLOCKED, quality.sceneIssue(hasDocumentCandidate = false))
    }

    @Test
    fun darkScene_returnsLightingGuidance() {
        val quality = CaptureFrameQualityAnalyzer.analyze(checkerFrame(first = 24, second = 36))

        assertEquals(CaptureSceneIssue.TOO_DARK, quality.sceneIssue(hasDocumentCandidate = false))
    }

    @Test
    fun softDetectedDocument_returnsBlurGuidance() {
        val quality = CaptureFrameQualityAnalyzer.analyze(solidFrame(value = 132))

        assertEquals(CaptureSceneIssue.BLURRY, quality.sceneIssue(hasDocumentCandidate = true))
        assertNull(quality.sceneIssue(hasDocumentCandidate = false))
    }

    @Test
    fun sharpDetectedDocument_hasNoSceneIssue() {
        val quality = CaptureFrameQualityAnalyzer.analyze(checkerFrame(first = 32, second = 224))

        assertNull(quality.sceneIssue(hasDocumentCandidate = true))
    }

    private fun solidFrame(value: Int): DetectionFrame = frame { _, _ -> value }

    private fun checkerFrame(first: Int, second: Int): DetectionFrame = frame { x, y ->
        if (((x / 8) + (y / 8)) % 2 == 0) first else second
    }

    private fun frame(pixel: (x: Int, y: Int) -> Int): DetectionFrame {
        val width = 32
        val height = 32
        val bytes = ByteArray(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = pixel(x, y).coerceIn(0, 255).toByte()
                val index = ((y * width) + x) * 4
                bytes[index] = value
                bytes[index + 1] = value
                bytes[index + 2] = value
                bytes[index + 3] = 0xFF.toByte()
            }
        }
        return DetectionFrame(
            width = width,
            height = height,
            rotationDegrees = 0,
            bytes = bytes,
        )
    }
}
