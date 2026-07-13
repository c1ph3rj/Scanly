package `in`.c1ph3rj.scanly.core.ml

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel

class DocumentQuadPolicyTest {
    @Test
    fun fullConvexDocumentIsCaptureReady() {
        assertTrue(DocumentQuadPolicy.isCaptureReady(quad()))
    }

    @Test
    fun edgeClippedDocumentIsRejected() {
        assertFalse(
            DocumentQuadPolicy.isCaptureReady(
                quad(topLeft = NormalizedPoint(0f, 0.10f)),
            ),
        )
    }

    @Test
    fun concaveFourPointShapeIsRejected() {
        assertFalse(
            DocumentQuadPolicy.isCaptureReady(
                quad(bottomRight = NormalizedPoint(0.48f, 0.42f)),
            ),
        )
    }

    @Test
    fun cleanPaperQuadScoresHigherThanMixedMonitorEdgeQuad() {
        val mixedEdgeQuad = quad(
            topLeft = NormalizedPoint(0.17f, 0.54f),
            topRight = NormalizedPoint(0.64f, 0.36f),
            bottomRight = NormalizedPoint(0.70f, 0.83f),
            bottomLeft = NormalizedPoint(0.13f, 0.83f),
        )
        val paperQuad = quad(
            topLeft = NormalizedPoint(0.17f, 0.54f),
            topRight = NormalizedPoint(0.67f, 0.54f),
            bottomRight = NormalizedPoint(0.70f, 0.83f),
            bottomLeft = NormalizedPoint(0.13f, 0.83f),
        )

        assertTrue(DocumentQuadPolicy.qualityScore(paperQuad) > DocumentQuadPolicy.qualityScore(mixedEdgeQuad))
        val chosen = CornerCandidatePolicy.choosePreferred(
            result(mixedEdgeQuad, DocumentCornerModel.STANDARD),
            result(paperQuad, DocumentCornerModel.ACCURATE),
        )
        assertEquals(DocumentCornerModel.ACCURATE, chosen.model)
    }

    private fun quad(
        topLeft: NormalizedPoint = NormalizedPoint(0.12f, 0.10f),
        topRight: NormalizedPoint = NormalizedPoint(0.88f, 0.12f),
        bottomRight: NormalizedPoint = NormalizedPoint(0.86f, 0.90f),
        bottomLeft: NormalizedPoint = NormalizedPoint(0.14f, 0.88f),
    ) = DocumentCornerQuad(topLeft, topRight, bottomRight, bottomLeft)

    private fun result(quad: DocumentCornerQuad, model: DocumentCornerModel) = CornerDetectionResult(
        quad = quad,
        confidence = 0.99f,
        inferenceTimeMillis = 10L,
        modelName = model.displayName,
        model = model,
    )
}
