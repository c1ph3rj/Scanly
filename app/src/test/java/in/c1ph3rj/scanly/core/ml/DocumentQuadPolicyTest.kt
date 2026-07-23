package `in`.c1ph3rj.scanly.core.ml

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.domain.model.ScanMode

class DocumentQuadPolicyTest {
    @Test
    fun fullConvexDocumentIsCaptureReady() {
        assertTrue(DocumentQuadPolicy.isCaptureReady(quad()))
    }

    @Test
    fun edgeClippedDocumentIsRejectedForLiveCapture() {
        assertFalse(
            DocumentQuadPolicy.isCaptureReady(
                quad(topLeft = NormalizedPoint(0f, 0.10f)),
            ),
        )
    }

    @Test
    fun nearFullFrameScanIsStillProcessReadyButNotCaptureReady() {
        val fullBleed = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.005f, 0.005f),
            topRight = NormalizedPoint(0.995f, 0.008f),
            bottomRight = NormalizedPoint(0.992f, 0.995f),
            bottomLeft = NormalizedPoint(0.008f, 0.990f),
        )
        assertFalse(DocumentQuadPolicy.isCaptureReady(fullBleed))
        assertTrue(DocumentQuadPolicy.isStillProcessReady(fullBleed))
        assertTrue(DocumentQuadPolicy.isReady(fullBleed, QuadReadiness.STILL_PROCESS))
    }

    @Test
    fun wideIdCardIsStillProcessReady() {
        // ~credit-card / RC proportions in the center of a photo (like model benchmark).
        val idCard = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.12f, 0.32f),
            topRight = NormalizedPoint(0.88f, 0.30f),
            bottomRight = NormalizedPoint(0.90f, 0.68f),
            bottomLeft = NormalizedPoint(0.10f, 0.70f),
        )
        assertTrue(DocumentQuadPolicy.isStillProcessReady(idCard))
    }

    @Test
    fun idModeAcceptsWideCardForLiveCaptureWithoutChangingDocumentPolicy() {
        val idCard = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.12f, 0.30f),
            topRight = NormalizedPoint(0.88f, 0.30f),
            bottomRight = NormalizedPoint(0.88f, 0.70f),
            bottomLeft = NormalizedPoint(0.12f, 0.70f),
        )

        assertFalse(DocumentQuadPolicy.isReady(idCard, QuadReadiness.LIVE_CAPTURE))
        assertTrue(
            DocumentQuadPolicy.isReady(
                idCard,
                QuadReadiness.LIVE_CAPTURE,
                ScanMode.ID_CARD,
            ),
        )
    }

    @Test
    fun idModeWaitsUntilCardIsCenteredInGuide() {
        val centered = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.12f, 0.30f),
            topRight = NormalizedPoint(0.88f, 0.30f),
            bottomRight = NormalizedPoint(0.88f, 0.70f),
            bottomLeft = NormalizedPoint(0.12f, 0.70f),
        )
        val offCenter = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.03f, 0.12f),
            topRight = NormalizedPoint(0.69f, 0.12f),
            bottomRight = NormalizedPoint(0.69f, 0.46f),
            bottomLeft = NormalizedPoint(0.03f, 0.46f),
        )

        assertTrue(
            DocumentQuadPolicy.isReady(centered, QuadReadiness.LIVE_CAPTURE, ScanMode.ID_CARD),
        )
        assertFalse(
            DocumentQuadPolicy.isReady(offCenter, QuadReadiness.LIVE_CAPTURE, ScanMode.ID_CARD),
        )
        assertTrue(
            DocumentQuadPolicy.idCardGuideFitScore(centered) >
                DocumentQuadPolicy.idCardGuideFitScore(offCenter),
        )
    }

    @Test
    fun bookModeAcceptsACompleteWideSpread() {
        val spread = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.08f, 0.20f),
            topRight = NormalizedPoint(0.92f, 0.20f),
            bottomRight = NormalizedPoint(0.92f, 0.80f),
            bottomLeft = NormalizedPoint(0.08f, 0.80f),
        )

        assertTrue(
            DocumentQuadPolicy.isReady(
                spread,
                QuadReadiness.LIVE_CAPTURE,
                ScanMode.BOOK,
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
    fun tighterPageQuadScoresHigherThanLooseDeskGrab() {
        val tightPage = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.28f, 0.32f),
            topRight = NormalizedPoint(0.72f, 0.30f),
            bottomRight = NormalizedPoint(0.74f, 0.68f),
            bottomLeft = NormalizedPoint(0.26f, 0.70f),
        )
        val looseDesk = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.08f, 0.10f),
            topRight = NormalizedPoint(0.92f, 0.12f),
            bottomRight = NormalizedPoint(0.90f, 0.90f),
            bottomLeft = NormalizedPoint(0.10f, 0.88f),
        )
        assertTrue(DocumentQuadPolicy.areaFitScore(tightPage.area()) >
            DocumentQuadPolicy.areaFitScore(looseDesk.area()))
        assertTrue(
            DocumentQuadPolicy.selectionScore(result(tightPage, DocumentCornerModel.STANDARD)) >
                DocumentQuadPolicy.selectionScore(result(looseDesk, DocumentCornerModel.STANDARD)),
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
            result(paperQuad, DocumentCornerModel.HIGH),
        )
        assertEquals(DocumentCornerModel.HIGH, chosen.model)
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
