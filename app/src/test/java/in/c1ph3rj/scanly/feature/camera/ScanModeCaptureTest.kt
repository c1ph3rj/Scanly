package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.BookAnalysis
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.DocumentGateClass
import `in`.c1ph3rj.scanly.core.ml.DocumentGateResult
import `in`.c1ph3rj.scanly.core.ml.DocumentGateTiming
import `in`.c1ph3rj.scanly.core.ml.DocumentQuadPolicy
import `in`.c1ph3rj.scanly.core.ml.LiveQuadRules
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint
import `in`.c1ph3rj.scanly.domain.model.IdCardSide
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanModeCaptureTest {
    // ── ScanMode storage ────────────────────────────────────────────────────────

    @Test
    fun scanModeRoundTripsAndUnknownValuesFallBackToDocument() {
        ScanMode.entries.forEach { mode -> assertEquals(mode, ScanMode.fromStorage(mode.storageValue)) }
        assertEquals(ScanMode.DOCUMENT, ScanMode.fromStorage(null))
        assertEquals(ScanMode.DOCUMENT, ScanMode.fromStorage("passport"))
    }

    @Test
    fun idCardSideDecodingIsStrict() {
        assertEquals(IdCardSide.FRONT, IdCardSide.fromStorage("front"))
        assertEquals(IdCardSide.BACK, IdCardSide.fromStorage("back"))
        assertNull(IdCardSide.fromStorage(null))
        assertNull(IdCardSide.fromStorage("side"))
    }

    // ── Strategies ──────────────────────────────────────────────────────────────

    @Test
    fun documentStrategyKeepsTheOriginalCaptureRules() {
        val document = CaptureModeStrategy.forMode(ScanMode.DOCUMENT)
        assertEquals(LiveQuadRules.Default, document.quadRules)
        assertEquals(LiveGatePolicy.Default, document.gate)
        assertEquals(BookAnalysis.LIVE_ONLY, document.bookAnalysis)
        assertEquals(1_800L, document.stability.minStableDurationMillis)
        assertEquals(2_500L, document.stability.cooldownMillis)
        assertEquals(0.08f, document.stability.rearmDistanceThreshold)
        assertFalse(document.detectGlare)
        assertEquals(PageFilterPreset.AUTO, ScanMode.DOCUMENT.defaultFilterPreset)
    }

    @Test
    fun idCardStrategySkipsGutterAnalysisAndWatchesForGlare() {
        val id = CaptureModeStrategy.forMode(ScanMode.ID_CARD)
        assertEquals(BookAnalysis.OFF, id.bookAnalysis)
        assertTrue(id.detectGlare)
        assertTrue(id.quadRules.minArea < LiveQuadRules.Default.minArea)
    }

    @Test
    fun bookStrategyKeepsItsTrimOnSavedPagesAndOnlyScreensBlock() {
        val book = CaptureModeStrategy.forMode(ScanMode.BOOK)
        assertEquals(BookAnalysis.ALWAYS, book.bookAnalysis)
        assertTrue(book.gate.onlyScreensBlock)
    }

    // ── ID card live readiness ──────────────────────────────────────────────────

    @Test
    fun centredCardTooSmallForDocumentsIsReadyInIdMode() {
        // 0.32 x 0.269 of a 4:3 landscape frame: a 1.59 card covering ~8.6% of the frame.
        val card = rect(0.34f, 0.3655f, 0.66f, 0.6345f)
        val aspect = 4f / 3f
        assertFalse(DocumentQuadPolicy.isCaptureReady(card, aspect))
        assertTrue(DocumentQuadPolicy.isCaptureReady(card, aspect, idRules()))
        assertTrue(DocumentQuadPolicy.idCardGuideFitScore(card, aspect) > 0.72f)
    }

    @Test
    fun offCentreCardWaitsInIdMode() {
        val card = rect(0.02f, 0.05f, 0.52f, 0.33f)
        assertFalse(DocumentQuadPolicy.isCaptureReady(card, 4f / 3f, idRules()))
    }

    @Test
    fun squareSubjectIsNotAnIdCard() {
        // A square (sticky note, CD sleeve) centred in a 3:4 portrait frame.
        val square = rect(0.275f, 0.33125f, 0.725f, 0.66875f)
        assertTrue(DocumentQuadPolicy.isCaptureReady(square, 3f / 4f))
        assertFalse(DocumentQuadPolicy.isCaptureReady(square, 3f / 4f, idRules()))
    }

    // ── Glare ───────────────────────────────────────────────────────────────────

    @Test
    fun glareIsOnlyReportedWhenTheModeAsksAndASubjectIsThere() {
        val quality = CaptureFrameQuality(
            meanLuminance = 140f,
            luminanceDeviation = 40f,
            edgeStrength = 10f,
            edgeSharpness = 0.8f,
            centreHighlightFraction = 0.10f,
        )
        assertEquals(CaptureSceneIssue.GLARE, quality.sceneIssue(hasDocumentCandidate = true, checkGlare = true))
        assertNull(quality.sceneIssue(hasDocumentCandidate = true, checkGlare = false))
        assertNull(quality.sceneIssue(hasDocumentCandidate = false, checkGlare = true))
        assertNull(quality.copy(centreHighlightFraction = 0.01f).sceneIssue(hasDocumentCandidate = true, checkGlare = true))
    }

    // ── Gate policies ───────────────────────────────────────────────────────────

    @Test
    fun onlyScreensBlockAcceptsNeitherFramesButStillRejectsScreens() {
        val tracker = DocumentGateStabilityTracker(policy = LiveGatePolicy(onlyScreensBlock = true))
        assertFalse(tracker.evaluate(gate(DocumentGateClass.NEITHER, 0.95f)))
        assertTrue(tracker.evaluate(gate(DocumentGateClass.NEITHER, 0.95f)))
        assertFalse(tracker.evaluate(gate(DocumentGateClass.DIGITAL_SCREEN, 0.95f)))
    }

    @Test
    fun idGatePolicyRidesThroughOneConfidentNeitherFrame() {
        val tracker = CaptureModeStrategy.forMode(ScanMode.ID_CARD).newGateTracker()
        assertFalse(tracker.evaluate(gate(DocumentGateClass.PHYSICAL_DOCUMENT, 0.85f)))
        assertTrue(tracker.evaluate(gate(DocumentGateClass.PHYSICAL_DOCUMENT, 0.85f)))
        assertTrue(tracker.evaluate(gate(DocumentGateClass.NEITHER, 0.95f)))
        assertTrue(tracker.evaluate(gate(DocumentGateClass.PHYSICAL_DOCUMENT, 0.85f)))
    }

    // ── ID front/back resume ────────────────────────────────────────────────────

    @Test
    fun resumesTheBackOfACardWhoseFrontIsSaved() {
        val pages = listOf(
            idPage("a", 0, "pair-1", IdCardSide.FRONT, createdAt = 1),
            idPage("b", 1, "pair-1", IdCardSide.BACK, createdAt = 2),
            idPage("c", 2, "pair-2", IdCardSide.FRONT, createdAt = 3),
        )
        assertEquals(IdCardCaptureState("pair-2", IdCardSide.BACK), resolveIdCaptureState(pages))
    }

    @Test
    fun startsANewCardWhenTheNewestCardIsComplete() {
        val pages = listOf(
            idPage("a", 0, "pair-1", IdCardSide.FRONT, createdAt = 1),
            idPage("b", 1, "pair-1", IdCardSide.BACK, createdAt = 2),
        )
        assertEquals(IdCardCaptureState(), resolveIdCaptureState(pages))
        assertEquals(IdCardCaptureState(), resolveIdCaptureState(emptyList()))
    }

    private fun idRules(): LiveQuadRules = CaptureModeStrategy.forMode(ScanMode.ID_CARD).quadRules

    private fun rect(left: Float, top: Float, right: Float, bottom: Float) = DocumentCornerQuad(
        topLeft = NormalizedPoint(left, top),
        topRight = NormalizedPoint(right, top),
        bottomRight = NormalizedPoint(right, bottom),
        bottomLeft = NormalizedPoint(left, bottom),
    )

    private fun gate(prediction: DocumentGateClass, probability: Float): DocumentGateResult {
        val rest = (1f - probability) / 2f
        return DocumentGateResult(
            probabilities = DocumentGateClass.entries.associateWith { if (it == prediction) probability else rest },
            timing = DocumentGateTiming(0L, 0L, 0L, 0L),
        )
    }

    private fun idPage(id: String, index: Int, pairId: String, side: IdCardSide, createdAt: Long) = ScanPage(
        id = id,
        documentId = "doc",
        pageIndex = index,
        rawImagePath = null,
        processedImagePath = null,
        thumbnailPath = null,
        rotationDegrees = 0,
        cropQuad = null,
        filterPreset = PageFilterPreset.NATURAL_COLOR,
        processingState = PageProcessingState.PROCESSED,
        createdAtMillis = createdAt,
        updatedAtMillis = createdAt,
        scanMode = ScanMode.ID_CARD,
        idCardPairId = pairId,
        idCardSide = side,
    )
}
