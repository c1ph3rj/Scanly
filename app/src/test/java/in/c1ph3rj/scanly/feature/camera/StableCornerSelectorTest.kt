package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.CornerDetectionResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StableCornerSelectorTest {
    @Test
    fun initialCandidateRequiresConfirmationAndSingleJumpIsNotDisplayed() {
        val selector = StableCornerSelector()
        val paper = paperQuad()

        assertNull(selector.select(result(paper, DocumentCornerModel.HIGH)).quad)
        assertNotNull(selector.select(result(paper.shifted(0.005f), DocumentCornerModel.HIGH)).quad)
        assertNull(selector.select(result(mixedEdgeQuad(), DocumentCornerModel.STANDARD)).quad)
        assertNotNull(selector.select(result(paper.shifted(0.008f), DocumentCornerModel.HIGH)).quad)
    }

    @Test
    fun consistentlyBetterQuadReplacesLowerQualityQuadAfterTwoFrames() {
        val selector = StableCornerSelector()
        val mixed = mixedEdgeQuad()
        val paper = paperQuad()

        selector.select(result(mixed, DocumentCornerModel.STANDARD))
        assertNotNull(selector.select(result(mixed.shifted(0.003f), DocumentCornerModel.STANDARD)).quad)
        assertNull(selector.select(result(paper, DocumentCornerModel.HIGH)).quad)
        val replacement = selector.select(result(paper.shifted(0.003f), DocumentCornerModel.HIGH))

        assertNotNull(replacement.quad)
        assertEquals(DocumentCornerModel.HIGH, replacement.model)
    }

    @Test
    fun shortDetectionGapKeepsTrackedQuadButMarksItHeld() {
        val selector = StableCornerSelector()
        val paper = paperQuad()
        selector.select(result(paper, DocumentCornerModel.HIGH))
        assertTrue(selector.selectTracked(result(paper, DocumentCornerModel.HIGH)).isFresh)

        val firstMiss = selector.selectTracked(missing())
        val secondMiss = selector.selectTracked(missing())
        val resumed = selector.selectTracked(result(paper.shifted(0.004f), DocumentCornerModel.HIGH))

        assertNotNull(firstMiss.result.quad)
        assertFalse(firstMiss.isFresh)
        assertNotNull(secondMiss.result.quad)
        assertFalse(secondMiss.isFresh)
        // Tracking resumes without a new confirmation.
        assertNotNull(resumed.result.quad)
        assertTrue(resumed.isFresh)
    }

    @Test
    fun trackedQuadClearsAfterMissLimitAndNeedsReconfirmation() {
        val selector = StableCornerSelector()
        val paper = paperQuad()
        selector.select(result(paper, DocumentCornerModel.HIGH))
        selector.select(result(paper, DocumentCornerModel.HIGH))

        selector.select(missing())
        selector.select(missing())
        val cleared = selector.selectTracked(missing())
        val reappeared = selector.selectTracked(result(paper, DocumentCornerModel.HIGH))

        assertNull(cleared.result.quad)
        assertFalse(cleared.isFresh)
        assertNull(reappeared.result.quad)
    }

    @Test
    fun pendingSwitchAfterGapIsNeverShown() {
        val selector = StableCornerSelector()
        val paper = paperQuad()
        selector.select(result(paper, DocumentCornerModel.HIGH))
        selector.select(result(paper, DocumentCornerModel.HIGH))
        selector.select(missing())

        val jump = selector.selectTracked(result(mixedEdgeQuad(), DocumentCornerModel.STANDARD))

        assertNull(jump.result.quad)
        assertFalse(jump.isFresh)
    }

    @Test
    fun captureReadinessUsesFrameAspectRatio() {
        val narrow = DocumentCornerQuad(
            NormalizedPoint(0.35f, 0.1f),
            NormalizedPoint(0.65f, 0.1f),
            NormalizedPoint(0.65f, 0.9f),
            NormalizedPoint(0.35f, 0.9f),
        )
        val square = StableCornerSelector()
        val portrait = StableCornerSelector()

        square.select(result(narrow, DocumentCornerModel.HIGH, aspect = 1f))
        portrait.select(result(narrow, DocumentCornerModel.HIGH, aspect = 0.5f))

        assertNotNull(square.select(result(narrow, DocumentCornerModel.HIGH, aspect = 1f)).quad)
        assertNull(portrait.select(result(narrow, DocumentCornerModel.HIGH, aspect = 0.5f)).quad)
    }

    private fun missing() = CornerDetectionResult(
        quad = null,
        confidence = 0f,
        inferenceTimeMillis = 10L,
        modelName = "test",
    )

    private fun result(quad: DocumentCornerQuad, model: DocumentCornerModel, aspect: Float = 1f) = CornerDetectionResult(
        quad = quad,
        confidence = 0.99f,
        inferenceTimeMillis = 10L,
        modelName = "test",
        model = model,
        frameAspectRatio = aspect,
    )

    private fun paperQuad() = DocumentCornerQuad(
        NormalizedPoint(0.17f, 0.54f),
        NormalizedPoint(0.67f, 0.54f),
        NormalizedPoint(0.70f, 0.83f),
        NormalizedPoint(0.13f, 0.83f),
    )

    private fun mixedEdgeQuad() = mixed

    private fun DocumentCornerQuad.shifted(delta: Float) = DocumentCornerQuad(
        NormalizedPoint(topLeft.x + delta, topLeft.y),
        NormalizedPoint(topRight.x + delta, topRight.y),
        NormalizedPoint(bottomRight.x + delta, bottomRight.y),
        NormalizedPoint(bottomLeft.x + delta, bottomLeft.y),
    )

    private companion object {
        val mixed = DocumentCornerQuad(
            NormalizedPoint(0.17f, 0.54f),
            NormalizedPoint(0.64f, 0.36f),
            NormalizedPoint(0.70f, 0.83f),
            NormalizedPoint(0.13f, 0.83f),
        )
    }
}
