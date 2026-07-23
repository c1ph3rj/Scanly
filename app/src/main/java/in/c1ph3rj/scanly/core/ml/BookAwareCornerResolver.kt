package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

enum class CornerResolutionIssue {
    TWO_PAGES_AMBIGUOUS,
    MODELS_DISAGREE,
}

data class CornerResolution(
    val result: CornerDetectionResult,
    val issue: CornerResolutionIssue? = null,
    val bookGutterFraction: Float? = null,
)

@Singleton
class BookAwareCornerResolver @Inject constructor(
    private val detector: DocumentCornerDetector,
) {
    suspend fun detect(
        bitmap: Bitmap,
        selectedModel: DocumentCornerModel,
        readiness: QuadReadiness = QuadReadiness.LIVE_CAPTURE,
        scanMode: ScanMode = ScanMode.DOCUMENT,
    ): CornerResolution {
        val startNanos = System.nanoTime()
        val resolution = resolve(bitmap, selectedModel, readiness, scanMode)
        val totalNanos = System.nanoTime() - startNanos
        val previousTiming = resolution.result.timing
        val resolverOverhead = (totalNanos - previousTiming.totalNanos).coerceAtLeast(0L)
        return resolution.copy(
            result = resolution.result.copy(
                timing = previousTiming.copy(
                    postprocessingNanos = previousTiming.postprocessingNanos + resolverOverhead,
                    totalNanos = totalNanos,
                ),
            ),
        )
    }

    private suspend fun resolve(
        bitmap: Bitmap,
        selectedModel: DocumentCornerModel,
        readiness: QuadReadiness,
        scanMode: ScanMode,
    ): CornerResolution {
        val primaryRaw = runCatching { detector.detect(bitmap, selectedModel) }
            .recoverCatching { error ->
                // Fall back to Accurate (YOLO-pose) for maximum compatibility.
                if (selectedModel == DocumentCornerModel.ACCURATE) throw error
                detector.detect(bitmap, DocumentCornerModel.ACCURATE)
            }
            .getOrThrow()
        val primary = prepareCandidate(bitmap, primaryRaw, readiness, scanMode)
        val needsVerification = primary.refinement?.kind?.let { it != BookPageRefinementKind.NONE } == true ||
            primary.refinement?.evidence?.hasPossibleGutter() == true ||
            CornerCandidatePolicy.needsAccurateVerification(primary.result, readiness)
        // High (384 px regression) is the verification model for ambiguous quads.
        if (!needsVerification || primaryRaw.model == DocumentCornerModel.HIGH) {
            return primary.toResolution(scanMode)
        }

        val highRaw = runCatching { detector.detect(bitmap, DocumentCornerModel.HIGH) }
            .getOrNull() ?: return primary.toResolution(scanMode)
        val high = prepareCandidate(bitmap, highRaw, readiness, scanMode)
        val combinedTiming = primaryRaw.timing + highRaw.timing
        val chosen = choose(primary, high, readiness, scanMode)
        return chosen.copy(
            result = chosen.result.copy(
                inferenceTimeMillis = combinedTiming.inferenceMillis.toLong(),
                timing = combinedTiming,
            ),
        )
    }

    private fun prepareCandidate(
        bitmap: Bitmap,
        raw: CornerDetectionResult,
        readiness: QuadReadiness,
        scanMode: ScanMode,
    ): Candidate {
        val readyQuad = raw.quad?.takeIf { DocumentQuadPolicy.isReady(it, readiness, scanMode) }
            ?: return Candidate(raw.copy(quad = null), null)

        // Offline import/capture finalize should match Model Benchmark: trust model corners.
        // Book-page gutter analysis is for live open-book frames and often wrecks ID/RC cards.
        if (readiness == QuadReadiness.STILL_PROCESS || scanMode == ScanMode.ID_CARD) {
            return Candidate(raw.copy(quad = readyQuad), refinement = null)
        }

        val refinement = BookPageQuadAnalyzer.analyze(bitmap, readyQuad)
        if (scanMode == ScanMode.BOOK) {
            // Book mode intentionally keeps the complete open spread. The gutter
            // is guidance/validation metadata, never a request to split the image.
            return Candidate(raw.copy(quad = readyQuad), refinement)
        }
        val refinedQuad = refinement.quad ?: return Candidate(raw.copy(quad = null), refinement)
        // Snap oversize model boxes inward onto stronger page edges (keyboard/desk overshoot).
        val tightened = DocumentQuadTightener.refine(
            bitmap = bitmap,
            quad = refinedQuad,
            boundarySupport = refinement.evidence.boundarySupport,
        ).takeIf { DocumentQuadPolicy.isReady(it, readiness, scanMode) }
            ?: refinedQuad.takeIf { DocumentQuadPolicy.isReady(it, readiness, scanMode) }
        return Candidate(
            result = raw.copy(quad = tightened),
            refinement = refinement,
        )
    }

    private fun choose(
        primary: Candidate,
        accurate: Candidate,
        readiness: QuadReadiness,
        scanMode: ScanMode,
    ): CornerResolution {
        val primaryQuad = primary.result.quad
        val accurateQuad = accurate.result.quad
        if (primaryQuad == null && accurateQuad == null) {
            val issue = if (
                primary.refinement?.kind == BookPageRefinementKind.AMBIGUOUS_TWO_PAGES ||
                accurate.refinement?.kind == BookPageRefinementKind.AMBIGUOUS_TWO_PAGES
            ) {
                CornerResolutionIssue.TWO_PAGES_AMBIGUOUS
            } else {
                null
            }
            return CornerResolution(primary.result.copy(quad = null), issue)
        }
        if (primaryQuad == null) return accurate.toResolution(scanMode)
        if (accurateQuad == null) return primary.toResolution(scanMode)

        val primaryScore = candidateScore(primary)
        val accurateScore = candidateScore(accurate)
        val candidatesDisagree =
            primaryQuad.maxCornerDistance(accurateQuad) >= MODEL_DISAGREEMENT_DISTANCE
        if (
            scanMode != ScanMode.BOOK &&
            candidatesDisagree &&
            abs(primaryScore - accurateScore) < MODEL_DISAGREEMENT_SCORE_MARGIN
        ) {
            // Live capture: suppress ambiguous overlays. Still process: keep the better warp
            // so imports never lose a good detection (benchmark-equivalent behaviour).
            return if (readiness == QuadReadiness.STILL_PROCESS) {
                CornerResolution(
                    if (accurateScore >= primaryScore) accurate.result else primary.result,
                )
            } else {
                CornerResolution(
                    primary.result.copy(quad = null),
                    CornerResolutionIssue.MODELS_DISAGREE,
                )
            }
        }
        val selected = if (accurateScore >= primaryScore + ACCURATE_REPLACEMENT_MARGIN) {
            accurate.result
        } else {
            primary.result
        }
        val selectedCandidate = if (selected === accurate.result) accurate else primary
        return CornerResolution(
            result = selected,
            bookGutterFraction = selectedCandidate.bookGutterFraction(scanMode),
        )
    }

    private fun candidateScore(candidate: Candidate): Float {
        val edgeSupport = candidate.refinement?.evidence?.boundarySupport ?: 0f
        return (
            (DocumentQuadPolicy.selectionScore(candidate.result) * 0.78f) +
                (edgeSupport.coerceIn(0f, 1f) * 0.22f)
            ).coerceIn(0f, 1f)
    }

    private fun Candidate.toResolution(scanMode: ScanMode): CornerResolution {
        val issue = if (refinement?.kind == BookPageRefinementKind.AMBIGUOUS_TWO_PAGES) {
            CornerResolutionIssue.TWO_PAGES_AMBIGUOUS
        } else {
            null
        }
        return CornerResolution(
            result = result,
            issue = if (scanMode == ScanMode.BOOK) null else issue,
            bookGutterFraction = bookGutterFraction(scanMode),
        )
    }

    private fun Candidate.bookGutterFraction(scanMode: ScanMode): Float? =
        refinement
            ?.takeIf {
                scanMode == ScanMode.BOOK &&
                    it.kind == BookPageRefinementKind.AMBIGUOUS_TWO_PAGES
            }
            ?.evidence
            ?.strongestInteriorFraction

    private fun BookPageVisualEvidence.hasPossibleGutter(): Boolean =
        strongestInteriorSupport >= POSSIBLE_GUTTER_SUPPORT &&
            strongestInteriorSupport >= medianInteriorSupport + POSSIBLE_GUTTER_PROMINENCE

    private data class Candidate(
        val result: CornerDetectionResult,
        val refinement: BookPageRefinement?,
    )

    private companion object {
        const val MODEL_DISAGREEMENT_DISTANCE = 0.14f
        const val MODEL_DISAGREEMENT_SCORE_MARGIN = 0.035f
        const val ACCURATE_REPLACEMENT_MARGIN = 0.012f
        const val POSSIBLE_GUTTER_SUPPORT = 0.085f
        const val POSSIBLE_GUTTER_PROMINENCE = 0.025f
    }
}
