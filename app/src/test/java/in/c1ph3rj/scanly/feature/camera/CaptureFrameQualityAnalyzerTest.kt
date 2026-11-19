package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.DetectionFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureFrameQualityAnalyzerTest {
    @Test
    fun coveredLens_returnsBlockedGuidanceWithoutDocument() {
        val quality = CaptureFrameQualityAnalyzer.analyze(solidFrame(value = 8))

        assertEquals(CaptureSceneIssue.LENS_BLOCKED, quality.sceneIssue(hasDocumentCandidate = false))
        assertEquals(CaptureSceneIssue.LENS_BLOCKED, quality.sceneIssue(hasDocumentCandidate = true))
    }

    @Test
    fun darkScene_returnsLightingGuidance() {
        val quality = CaptureFrameQualityAnalyzer.analyze(checkerFrame(first = 24, second = 36))

        assertEquals(CaptureSceneIssue.TOO_DARK, quality.sceneIssue(hasDocumentCandidate = false))
        assertEquals(CaptureSceneIssue.TOO_DARK, quality.sceneIssue(hasDocumentCandidate = true))
    }

    @Test
    fun darkBlurredScene_reportsDarkBeforeBlur() {
        val quality = analyzeScene(blurRadius = 5) { x, y -> if (inPage(x, y)) 44 else 4 }

        assertTrue(quality.edgeSharpness < 0.2f)
        assertEquals(CaptureSceneIssue.TOO_DARK, quality.sceneIssue(hasDocumentCandidate = true))
    }

    // Previously expected BLURRY: a flat frame was read as "no texture = blurry". Blur cannot be
    // judged without any edge, so the analyzer now stays quiet rather than block auto-capture.
    @Test
    fun uniformFrameWithDocument_isNotCalledBlurry() {
        val quality = CaptureFrameQualityAnalyzer.analyze(solidFrame(value = 132))

        assertEquals(CaptureFrameQuality.UNMEASURED_EDGE_SHARPNESS, quality.edgeSharpness)
        assertNull(quality.sceneIssue(hasDocumentCandidate = true))
        assertNull(quality.sceneIssue(hasDocumentCandidate = false))
    }

    @Test
    fun sharpDetectedDocument_hasNoSceneIssue() {
        val quality = CaptureFrameQualityAnalyzer.analyze(checkerFrame(first = 32, second = 224))

        assertNull(quality.sceneIssue(hasDocumentCandidate = true))
    }

    @Test
    fun blankSharpPageOnPlainDesk_hasNoSceneIssue() {
        val quality = analyzeScene(blurRadius = 0, pixel = ::blankPage)

        // The old texture mean is low here, which used to report BLURRY forever.
        assertTrue("edgeStrength=${quality.edgeStrength}", quality.edgeStrength < 5f)
        assertTrue("edgeSharpness=${quality.edgeSharpness}", quality.edgeSharpness > 0.5f)
        assertNull(quality.sceneIssue(hasDocumentCandidate = true))
    }

    @Test
    fun blankPageBlurred_isBlurry() {
        for (radius in intArrayOf(4, 6)) {
            val quality = analyzeScene(blurRadius = radius, pixel = ::blankPage)

            assertTrue("r=$radius edgeSharpness=${quality.edgeSharpness}", quality.edgeSharpness < 0.15f)
            assertEquals(CaptureSceneIssue.BLURRY, quality.sceneIssue(hasDocumentCandidate = true))
            assertNull(quality.sceneIssue(hasDocumentCandidate = false))
        }
    }

    @Test
    fun denseSharpText_hasNoSceneIssue() {
        val quality = analyzeScene(blurRadius = 0, pixel = ::textPage)

        assertTrue("edgeSharpness=${quality.edgeSharpness}", quality.edgeSharpness > 0.5f)
        assertNull(quality.sceneIssue(hasDocumentCandidate = true))
    }

    @Test
    fun denseTextBlurred_isBlurry() {
        for (radius in intArrayOf(4, 6)) {
            val quality = analyzeScene(blurRadius = radius, pixel = ::textPage)

            assertTrue("r=$radius edgeSharpness=${quality.edgeSharpness}", quality.edgeSharpness < 0.15f)
            assertEquals(CaptureSceneIssue.BLURRY, quality.sceneIssue(hasDocumentCandidate = true))
            assertNull(quality.sceneIssue(hasDocumentCandidate = false))
        }
    }

    @Test
    fun lowContrastSharpPage_hasNoSceneIssue() {
        val quality = analyzeScene(blurRadius = 0) { x, y -> if (inPage(x, y)) 190 else 150 }

        assertTrue("edgeSharpness=${quality.edgeSharpness}", quality.edgeSharpness > 0.5f)
        assertNull(quality.sceneIssue(hasDocumentCandidate = true))
    }

    @Test
    fun lowContrastPageBlurred_isBlurry() {
        val quality = analyzeScene(blurRadius = 5) { x, y -> if (inPage(x, y)) 190 else 150 }

        assertTrue("edgeSharpness=${quality.edgeSharpness}", quality.edgeSharpness < 0.15f)
        assertEquals(CaptureSceneIssue.BLURRY, quality.sceneIssue(hasDocumentCandidate = true))
    }

    @Test
    fun noDocumentCandidate_neverReportsBlur() {
        val quality = CaptureFrameQuality(
            meanLuminance = 128f,
            luminanceDeviation = 40f,
            edgeStrength = 0f,
            edgeSharpness = 0f,
        )

        assertNull(quality.sceneIssue(hasDocumentCandidate = false))
        assertEquals(CaptureSceneIssue.BLURRY, quality.sceneIssue(hasDocumentCandidate = true))
    }

    @Test
    fun legacyConstructor_defaultsToUnmeasuredSharpness() {
        val quality = CaptureFrameQuality(meanLuminance = 128f, luminanceDeviation = 40f, edgeStrength = 0f)

        assertNull(quality.sceneIssue(hasDocumentCandidate = true))
    }

    @Test
    fun degenerateInput_doesNotCrashOrReportBlur() {
        val frames = listOf(
            DetectionFrame(width = 0, height = 0, rotationDegrees = 0, bytes = ByteArray(0)),
            DetectionFrame(width = 640, height = 480, rotationDegrees = 0, bytes = ByteArray(0)),
            DetectionFrame(width = 640, height = 480, rotationDegrees = 0, bytes = ByteArray(3)),
            DetectionFrame(width = 640, height = 480, rotationDegrees = 0, bytes = ByteArray(640 * 4 + 7) { 0x7F }),
            DetectionFrame(width = 1, height = 1, rotationDegrees = 0, bytes = ByteArray(4) { 0x7F }),
        )

        for (frame in frames) {
            val quality = CaptureFrameQualityAnalyzer.analyze(frame)
            assertTrue(quality.edgeSharpness in 0f..1f)
            assertTrue(quality.sceneIssue(hasDocumentCandidate = true) != CaptureSceneIssue.BLURRY)
        }
    }

    @Test
    fun truncatedSharpFrame_stillMeasuresAvailableRows() {
        val full = sceneFrame(blurRadius = 0, pixel = ::blankPage)
        val truncated = full.copy(bytes = full.bytes.copyOf(full.bytes.size * 3 / 4 + 5))

        val quality = CaptureFrameQualityAnalyzer.analyze(truncated)

        assertTrue("edgeSharpness=${quality.edgeSharpness}", quality.edgeSharpness > 0.5f)
    }

    private fun blankPage(x: Int, y: Int): Int = if (inPage(x, y)) 220 else 40

    private fun textPage(x: Int, y: Int): Int {
        if (!inPage(x, y)) return 40
        val inTextBlock = x in (PageLeft + 20) until (PageRight - 20) && y in (PageTop + 20) until (PageBottom - 20)
        val onLine = (y - PageTop) % 10 < 3
        val onGlyph = (x - PageLeft) % 10 < 6
        return if (inTextBlock && onLine && onGlyph) 30 else 220
    }

    private fun inPage(x: Int, y: Int): Boolean = x in PageLeft until PageRight && y in PageTop until PageBottom

    private fun analyzeScene(blurRadius: Int, pixel: (x: Int, y: Int) -> Int): CaptureFrameQuality =
        CaptureFrameQualityAnalyzer.analyze(sceneFrame(blurRadius, pixel))

    private fun sceneFrame(blurRadius: Int, pixel: (x: Int, y: Int) -> Int): DetectionFrame {
        val values = IntArray(SceneWidth * SceneHeight) { index -> pixel(index % SceneWidth, index / SceneWidth) }
        val blurred = if (blurRadius > 0) boxBlur(values, SceneWidth, SceneHeight, blurRadius) else values
        return frameOf(SceneWidth, SceneHeight) { x, y -> blurred[(y * SceneWidth) + x] }
    }

    /** Separable box blur with edge clamping. */
    private fun boxBlur(values: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val window = (2 * radius) + 1
        val horizontal = IntArray(values.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                for (dx in -radius..radius) {
                    sum += values[(y * width) + (x + dx).coerceIn(0, width - 1)]
                }
                horizontal[(y * width) + x] = (sum + window / 2) / window
            }
        }
        val result = IntArray(values.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                for (dy in -radius..radius) {
                    sum += horizontal[((y + dy).coerceIn(0, height - 1) * width) + x]
                }
                result[(y * width) + x] = (sum + window / 2) / window
            }
        }
        return result
    }

    private fun solidFrame(value: Int): DetectionFrame = frameOf(32, 32) { _, _ -> value }

    private fun checkerFrame(first: Int, second: Int): DetectionFrame = frameOf(32, 32) { x, y ->
        if (((x / 8) + (y / 8)) % 2 == 0) first else second
    }

    private fun frameOf(width: Int, height: Int, pixel: (x: Int, y: Int) -> Int): DetectionFrame {
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

    private companion object {
        const val SceneWidth = 640
        const val SceneHeight = 480
        const val PageLeft = 170
        const val PageTop = 110
        const val PageRight = 470
        const val PageBottom = 370
    }
}
