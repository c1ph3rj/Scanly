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

    @Test
    fun landscapeIdCardInPortraitFrameIsCaptureReady() {
        // 380x240 px card (w/h 1.583) centred in a 480x640 analysis frame.
        val card = pixelQuad(50f, 200f, 430f, 200f, 430f, 440f, 50f, 440f)
        assertEquals(1.583f, card.estimatedAspectRatio(PORTRAIT_FRAME), 0.01f)
        assertEquals(2.11f, card.estimatedAspectRatio(), 0.01f)
        assertTrue(DocumentQuadPolicy.isCaptureReady(card, PORTRAIT_FRAME))
    }

    @Test
    fun uprightA4InPortraitFrameIsCaptureReady() {
        // 354x500 px page (w/h 0.708) in a 480x640 frame.
        val a4 = pixelQuad(63f, 70f, 417f, 70f, 417f, 570f, 63f, 570f)
        assertEquals(0.708f, a4.estimatedAspectRatio(PORTRAIT_FRAME), 0.01f)
        assertTrue(DocumentQuadPolicy.isCaptureReady(a4, PORTRAIT_FRAME))
        assertTrue(DocumentQuadPolicy.isReady(a4, QuadReadiness.LIVE_CAPTURE, PORTRAIT_FRAME))
    }

    @Test
    fun liveAspectBandIsMeasuredInPixelSpace() {
        // Normalized w/h 2.0 is a true 3.0 strip in a 3:2 landscape frame: too wide for live.
        val strip = quad(
            topLeft = NormalizedPoint(0.10f, 0.30f),
            topRight = NormalizedPoint(0.90f, 0.30f),
            bottomRight = NormalizedPoint(0.90f, 0.70f),
            bottomLeft = NormalizedPoint(0.10f, 0.70f),
        )
        assertTrue(DocumentQuadPolicy.isCaptureReady(strip))
        assertFalse(DocumentQuadPolicy.isCaptureReady(strip, 1.5f))
    }

    @Test
    fun rotatedRectangleScoresLikeAxisAlignedOneWhenFrameAspectIsSupplied() {
        val axisAligned = rotatedPixelRectangle(degrees = 0f)
        val rotated = rotatedPixelRectangle(degrees = 30f)
        val axisScore = DocumentQuadPolicy.qualityScore(axisAligned, PORTRAIT_FRAME)
        val rotatedScore = DocumentQuadPolicy.qualityScore(rotated, PORTRAIT_FRAME)
        assertEquals(axisScore, rotatedScore, 0.01f)
        assertTrue(rotatedScore > 0.99f)
        // Square-space scoring turns the rotated rectangle into a parallelogram.
        assertTrue(DocumentQuadPolicy.qualityScore(rotated) < rotatedScore - 0.02f)
        assertTrue(
            DocumentQuadPolicy.selectionScore(result(rotated, DocumentCornerModel.STANDARD, PORTRAIT_FRAME)) >
                DocumentQuadPolicy.selectionScore(result(rotated, DocumentCornerModel.STANDARD)),
        )
    }

    @Test
    fun defaultAndInvalidFrameAspectKeepSquareSpaceResults() {
        val sample = quad(bottomRight = NormalizedPoint(0.80f, 0.86f))
        val square = DocumentQuadPolicy.qualityScore(sample)
        assertEquals(square, DocumentQuadPolicy.qualityScore(sample, 1f), 0f)
        assertEquals(square, DocumentQuadPolicy.qualityScore(sample, Float.NaN), 0f)
        assertEquals(square, DocumentQuadPolicy.qualityScore(sample, -2f), 0f)
        assertEquals(square, DocumentQuadPolicy.qualityScore(sample, Float.POSITIVE_INFINITY), 0f)
        assertEquals(sample.estimatedAspectRatio(), sample.estimatedAspectRatio(0f), 0f)
    }

    @Test
    fun borderTouchingQuadIsStillProcessReady() {
        // Decoders clamp corners to [0, 1]: a page running off the photo sits on the border.
        val borderTouching = DocumentCornerQuad(
            topLeft = NormalizedPoint(0f, 0.02f),
            topRight = NormalizedPoint(1f, 0f),
            bottomRight = NormalizedPoint(0.98f, 1f),
            bottomLeft = NormalizedPoint(0f, 0.97f),
        )
        assertFalse(DocumentQuadPolicy.isCaptureReady(borderTouching))
        assertTrue(DocumentQuadPolicy.isStillProcessReady(borderTouching))
        assertTrue(DocumentQuadPolicy.isStillProcessReady(borderTouching, PORTRAIT_FRAME))
    }

    @Test
    fun tinyOrSliverQuadsAreNotStillProcessReady() {
        // Valid and convex (the old last-resort fallback accepted these), but not a page.
        val tiny = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.40f, 0.40f),
            topRight = NormalizedPoint(0.50f, 0.40f),
            bottomRight = NormalizedPoint(0.50f, 0.55f),
            bottomLeft = NormalizedPoint(0.40f, 0.55f),
        )
        assertEquals(0.015f, tiny.area(), 0.0005f)
        assertTrue(tiny.isValid() && tiny.isConvex())
        assertFalse(DocumentQuadPolicy.isStillProcessReady(tiny))
        assertFalse(DocumentQuadPolicy.isStillProcessReady(tiny, PORTRAIT_FRAME))

        val sliver = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.05f, 0.48f),
            topRight = NormalizedPoint(0.95f, 0.48f),
            bottomRight = NormalizedPoint(0.95f, 0.52f),
            bottomLeft = NormalizedPoint(0.05f, 0.52f),
        )
        assertTrue(sliver.isValid() && sliver.isConvex())
        assertFalse(DocumentQuadPolicy.isStillProcessReady(sliver))
    }

    @Test
    fun stillLongShortLimitCoversTallReceiptInPortraitFrame() {
        // A long receipt (true long/short 4.0) in a portrait photo stays usable.
        val receipt = pixelQuad(180f, 40f, 300f, 40f, 300f, 520f, 180f, 520f)
        assertEquals(4f, 1f / receipt.estimatedAspectRatio(PORTRAIT_FRAME), 0.01f)
        assertTrue(DocumentQuadPolicy.isStillProcessReady(receipt, PORTRAIT_FRAME))
    }

    private fun quad(
        topLeft: NormalizedPoint = NormalizedPoint(0.12f, 0.10f),
        topRight: NormalizedPoint = NormalizedPoint(0.88f, 0.12f),
        bottomRight: NormalizedPoint = NormalizedPoint(0.86f, 0.90f),
        bottomLeft: NormalizedPoint = NormalizedPoint(0.14f, 0.88f),
    ) = DocumentCornerQuad(topLeft, topRight, bottomRight, bottomLeft)

    /** Corners given in pixels of a [FRAME_WIDTH] x [FRAME_HEIGHT] frame. */
    private fun pixelQuad(vararg xy: Float) = DocumentCornerQuad(
        NormalizedPoint(xy[0] / FRAME_WIDTH, xy[1] / FRAME_HEIGHT),
        NormalizedPoint(xy[2] / FRAME_WIDTH, xy[3] / FRAME_HEIGHT),
        NormalizedPoint(xy[4] / FRAME_WIDTH, xy[5] / FRAME_HEIGHT),
        NormalizedPoint(xy[6] / FRAME_WIDTH, xy[7] / FRAME_HEIGHT),
    )

    /** A true 300x200 px rectangle rotated clockwise about the frame centre. */
    private fun rotatedPixelRectangle(degrees: Float): DocumentCornerQuad {
        val radians = Math.toRadians(degrees.toDouble())
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        val corners = listOf(-150f to -100f, 150f to -100f, 150f to 100f, -150f to 100f).flatMap { (x, y) ->
            listOf(FRAME_WIDTH / 2 + x * cos - y * sin, FRAME_HEIGHT / 2 + x * sin + y * cos)
        }
        return pixelQuad(*corners.toFloatArray())
    }

    private fun result(
        quad: DocumentCornerQuad,
        model: DocumentCornerModel,
        frameAspectRatio: Float = 1f,
    ) = CornerDetectionResult(
        quad = quad,
        confidence = 0.99f,
        inferenceTimeMillis = 10L,
        modelName = model.displayName,
        model = model,
        frameAspectRatio = frameAspectRatio,
    )

    private companion object {
        const val FRAME_WIDTH = 480f
        const val FRAME_HEIGHT = 640f
        const val PORTRAIT_FRAME = FRAME_WIDTH / FRAME_HEIGHT
    }
}
