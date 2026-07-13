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
    ): CornerResolution {
        val startNanos = System.nanoTime()
        val resolution = resolve(bitmap, selectedModel, readiness)
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
    ): CornerResolution {
        val primaryRaw = runCatching { detector.detect(bitmap, selectedModel) }
            .recoverCatching { error ->
                if (selectedModel == DocumentCornerModel.LEGACY) throw error
                detector.detect(bitmap, DocumentCornerModel.LEGACY)
            }
            .getOrThrow()
        val primary = prepareCandidate(bitmap, primaryRaw, readiness)
        val needsVerification = primary.refinement?.kind?.let { it != BookPageRefinementKind.NONE } == true ||
            primary.refinement?.evidence?.hasPossibleGutter() == true ||
            CornerCandidatePolicy.needsAccurateVerification(primary.result, readiness)
        if (!needsVerification || primaryRaw.model == DocumentCornerModel.ACCURATE) {
            return primary.toResolution()
        }

        val accurateRaw = runCatching { detector.detect(bitmap, DocumentCornerModel.ACCURATE) }
            .getOrNull() ?: return primary.toResolution()
        val accurate = prepareCandidate(bitmap, accurateRaw, readiness)
        val combinedTiming = primaryRaw.timing + accurateRaw.timing
        val chosen = choose(primary, accurate, readiness)
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
    ): Candidate {
        val readyQuad = raw.quad?.takeIf { DocumentQuadPolicy.isReady(it, readiness) }
            ?: return Candidate(raw.copy(quad = null), null)

        // Offline import/capture finalize should match Model Benchmark: trust model corners.
        // Book-page gutter analysis is for live open-book frames and often wrecks ID/RC cards.
        if (readiness == QuadReadiness.STILL_PROCESS) {
            return Candidate(raw.copy(quad = readyQuad), refinement = null)
        }

        val refinement = BookPageQuadAnalyzer.analyze(bitmap, readyQuad)
        return Candidate(
            result = raw.copy(
                quad = refinement.quad?.takeIf { DocumentQuadPolicy.isReady(it, readiness) },
            ),
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
