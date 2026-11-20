package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

enum class CornerResolutionIssue {
    TWO_PAGES_AMBIGUOUS,
    MODELS_DISAGREE,
}

/** When the open-book gutter analysis runs. */
enum class BookAnalysis {
    /** Never: ID cards and other small, high-contrast subjects that it would cut in half. */
    OFF,
    /** Live frames only; saved pages trust the model corners. The general document flow. */
    LIVE_ONLY,
    /** Live frames and saved pages alike, so a trimmed book page stays trimmed once saved. */
    ALWAYS,
}

data class CornerResolution(
    val result: CornerDetectionResult,
    val issue: CornerResolutionIssue? = null,
)

@Singleton
class BookAwareCornerResolver @Inject constructor(
    private val detector: DocumentCornerDetector,
) {
    suspend fun detect(
        bitmap: Bitmap,
        selectedModel: DocumentCornerModel,
        readiness: QuadReadiness = QuadReadiness.LIVE_CAPTURE,
        bookAnalysis: BookAnalysis = BookAnalysis.LIVE_ONLY,
        liveRules: LiveQuadRules = LiveQuadRules.Default,
    ): CornerResolution {
        val startNanos = System.nanoTime()
        val resolution = resolve(bitmap, selectedModel, Policy(readiness, bookAnalysis, liveRules))
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
        policy: Policy,
    ): CornerResolution {
        val readiness = policy.readiness
        // Saved pages must come out the same on every import; live frames favour speed.
        val reproducible = readiness == QuadReadiness.STILL_PROCESS
        // One luma read per frame, shared by the gutter analysis and the tightener of both models.
        val frame = FrameContext(bitmap)
        val primaryRaw = runCatching { detector.detect(bitmap, selectedModel, reproducible) }
            .recoverCatching { error ->
                // Fall back to Accurate (YOLO-pose) for maximum compatibility.
                if (selectedModel == DocumentCornerModel.ACCURATE) throw error
                detector.detect(bitmap, DocumentCornerModel.ACCURATE, reproducible)
            }
            .getOrThrow()
        val primary = prepareCandidate(frame, primaryRaw, policy)
        val needsVerification = primary.refinement?.kind?.let { it != BookPageRefinementKind.NONE } == true ||
            primary.refinement?.evidence?.hasPossibleGutter() == true ||
            CornerCandidatePolicy.needsAccurateVerification(primary.result, readiness, policy.liveRules)
        // High (384 px regression) is the verification model for ambiguous quads.
        if (!needsVerification || primaryRaw.model == DocumentCornerModel.HIGH) {
            return primary.toResolution()
        }

        val highRaw = runCatching { detector.detect(bitmap, DocumentCornerModel.HIGH, reproducible) }
            .getOrNull() ?: return primary.toResolution()
        val high = prepareCandidate(frame, highRaw, policy)
        val combinedTiming = primaryRaw.timing + highRaw.timing
        val chosen = choose(primary, high, readiness)
        return chosen.copy(
            result = chosen.result.copy(
                inferenceTimeMillis = combinedTiming.inferenceMillis.toLong(),
                timing = combinedTiming,
            ),
        )
    }

    private fun prepareCandidate(
        frame: FrameContext,
        detected: CornerDetectionResult,
        policy: Policy,
    ): Candidate {
        val readiness = policy.readiness
        val rules = policy.liveRules
        // The resolver owns the bitmap, so its aspect is authoritative for every copy below.
        val aspect = frame.aspectRatio
        val raw = detected.copy(frameAspectRatio = aspect)
        val readyQuad = raw.quad?.takeIf { DocumentQuadPolicy.isReady(it, readiness, aspect, rules) }
            ?: return Candidate(raw.copy(quad = null), null)

        // Offline import/capture finalize should match Model Benchmark: trust model corners.
        // Book-page gutter analysis is for open-book frames and often wrecks ID/RC cards, so it
        // only runs on saved pages when the scan mode asks for it (Book).
        val analyzeBook = when (policy.bookAnalysis) {
            BookAnalysis.OFF -> false
            BookAnalysis.LIVE_ONLY -> readiness == QuadReadiness.LIVE_CAPTURE
            BookAnalysis.ALWAYS -> true
        }
        if (!analyzeBook) {
            return Candidate(raw.copy(quad = readyQuad), refinement = null)
        }

        val refinement = BookPageQuadAnalyzer.analyze(frame.grid, readyQuad, aspect)
        val refinedQuad = refinement.quad ?: return Candidate(raw.copy(quad = null), refinement)
        // Snap oversize model boxes inward onto stronger page edges (keyboard/desk overshoot).
        val tightened = DocumentQuadTightener.refine(
            grid = frame.grid,
            quad = refinedQuad,
            boundarySupport = refinement.evidence.boundarySupport,
        ).takeIf { DocumentQuadPolicy.isReady(it, readiness, aspect, rules) }
            ?: refinedQuad.takeIf { DocumentQuadPolicy.isReady(it, readiness, aspect, rules) }
        return Candidate(
            result = raw.copy(quad = tightened),
            refinement = refinement,
        )
    }

    private fun choose(
        primary: Candidate,
        accurate: Candidate,
        readiness: QuadReadiness,
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
        if (primaryQuad == null) return CornerResolution(accurate.result)
        if (accurateQuad == null) return CornerResolution(primary.result)

        val primaryScore = candidateScore(primary)
        val accurateScore = candidateScore(accurate)
        val candidatesDisagree =
            primaryQuad.maxCornerDistance(accurateQuad) >= MODEL_DISAGREEMENT_DISTANCE
        if (candidatesDisagree && abs(primaryScore - accurateScore) < MODEL_DISAGREEMENT_SCORE_MARGIN) {
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
        return CornerResolution(
            if (accurateScore >= primaryScore + ACCURATE_REPLACEMENT_MARGIN) {
                accurate.result
            } else {
                primary.result
            },
        )
    }

    private fun candidateScore(candidate: Candidate): Float {
        val edgeSupport = candidate.refinement?.evidence?.boundarySupport ?: 0f
        return (
            (DocumentQuadPolicy.selectionScore(candidate.result) * 0.78f) +
                (edgeSupport.coerceIn(0f, 1f) * 0.22f)
            ).coerceIn(0f, 1f)
    }

    private fun Candidate.toResolution(): CornerResolution {
        val issue = if (refinement?.kind == BookPageRefinementKind.AMBIGUOUS_TWO_PAGES) {
            CornerResolutionIssue.TWO_PAGES_AMBIGUOUS
        } else {
            null
        }
        return CornerResolution(result, issue)
    }

    private fun BookPageVisualEvidence.hasPossibleGutter(): Boolean =
        strongestInteriorSupport >= POSSIBLE_GUTTER_SUPPORT &&
            strongestInteriorSupport >= medianInteriorSupport + POSSIBLE_GUTTER_PROMINENCE

    /** Per-frame data; the luma grid is only read when the live path needs it. */
    private class FrameContext(bitmap: Bitmap) {
        val aspectRatio: Float = bitmap.frameAspectRatio
        val grid: LumaGrid by lazy(LazyThreadSafetyMode.NONE) { bitmap.toLumaGrid() }
    }

    private data class Policy(
        val readiness: QuadReadiness,
        val bookAnalysis: BookAnalysis,
        val liveRules: LiveQuadRules,
    )

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
