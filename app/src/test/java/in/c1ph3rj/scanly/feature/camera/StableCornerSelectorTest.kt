package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.CornerDetectionResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StableCornerSelectorTest {
    @Test
    fun initialCandidateRequiresConfirmationAndSingleJumpIsNotDisplayed() {
        val selector = StableCornerSelector()
        val paper = paperQuad()

        assertNull(selector.select(result(paper, DocumentCornerModel.ACCURATE)).quad)
        assertNotNull(selector.select(result(paper.shifted(0.005f), DocumentCornerModel.ACCURATE)).quad)
        assertNull(selector.select(result(mixedEdgeQuad(), DocumentCornerModel.STANDARD)).quad)
        assertNotNull(selector.select(result(paper.shifted(0.008f), DocumentCornerModel.ACCURATE)).quad)
    }

    @Test
    fun consistentlyBetterQuadReplacesLowerQualityQuadAfterTwoFrames() {
        val selector = StableCornerSelector()
        val mixed = mixedEdgeQuad()
        val paper = paperQuad()

        selector.select(result(mixed, DocumentCornerModel.STANDARD))
        assertNotNull(selector.select(result(mixed.shifted(0.003f), DocumentCornerModel.STANDARD)).quad)
        assertNull(selector.select(result(paper, DocumentCornerModel.ACCURATE)).quad)
        val replacement = selector.select(result(paper.shifted(0.003f), DocumentCornerModel.ACCURATE))

        assertNotNull(replacement.quad)
        assertEquals(DocumentCornerModel.ACCURATE, replacement.model)
    }

    private fun result(quad: DocumentCornerQuad, model: DocumentCornerModel) = CornerDetectionResult(
        quad = quad,
        confidence = 0.99f,
        inferenceTimeMillis = 10L,
        modelName = "test",
        model = model,
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
