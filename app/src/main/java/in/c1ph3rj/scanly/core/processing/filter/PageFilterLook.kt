package `in`.c1ph3rj.scanly.core.processing.filter

import `in`.c1ph3rj.scanly.core.processing.PageImageProfile
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import kotlin.math.roundToInt

/** Edge length that kernel sizes are expressed against (sigma 0.9 px at 1600 px). */
internal const val FILTER_REFERENCE_EDGE = 1_600

internal const val DEFAULT_FILTER_INTENSITY = 1.0
internal const val MAX_FILTER_INTENSITY = 1.5

internal enum class PageFilterRenderMode {
    /** Per-channel background division, white paper, vivid ink. */
    Color,

    /** Even lighting toward the page's own (lifted) paper tone; light white balance. */
    Natural,

    /** Gray background division + tone curve. */
    Gray,

    /** Gray background division + global anti-aliased threshold + despeckle. */
    Binary,

    /**
     * Photos of displays: de-screen (pixel grid / color moiré), remove reflection veil,
     * neutralize the screen white and stretch levels. Global — no paper model, so dark
     * mode and full-screen images survive.
     */
    Screen,

    /** Punchy color for posters, covers and photos: global white balance, levels, local contrast. */
    Vivid,
}

/**
 * Concrete parameters for one render. Everything image-dependent comes from the
 * [PageImageProfile] tone stats (measured on the background-normalized page), so the
 * same profile yields the same look at chip, preview and save sizes; only pixel
 * kernels ([sharpenSigma], [denoiseSigma], [despeckleMinArea]) scale with the edge.
 */
internal data class PageFilterRenderParams(
    val mode: PageFilterRenderMode,
    /** Close kernel as a fraction of the background-estimate edge; removes strokes up to this width. */
    val strokeFraction: Double,
    /** Max shading gain (paper / local background) before the background is floored. */
    val maxGain: Double,
    val blackPoint: Double,
    val whitePoint: Double,
    val gamma: Double,
    val vibrance: Double,
    val sharpenAmount: Double,
    val sharpenSigma: Double,
    val denoiseSigma: Double,
    /** Natural only: 0 keeps paper luminance, 1 lifts it toward ~238. */
    val naturalLift: Double = 0.0,
    /** Natural only: fraction of the paper cast removed. */
    val naturalWhiteBalance: Double = 0.0,
    val binaryThreshold: Double = 0.0,
    val binaryRamp: Double = 0.0,
    val despeckleMinArea: Int = 0,
    /** Screen / Vivid: mid-tone contrast S-curve strength. */
    val contrastCurve: Double = 0.0,
    /** Screen / Vivid: fraction of the white-reference cast removed (gain limited to ±15%). */
    val whiteBalance: Double = 0.0,
    /** Screen / Vivid: fraction of the darkest-luminance level mapped to black (haze / veil lift). */
    val blackClip: Double = 0.0,
    /** Screen: luma blur that suppresses the display's pixel grid. */
    val descreenSigma: Double = 0.0,
    /** Screen: chroma blur (at quarter resolution) that removes color moiré. */
    val chromaSigma: Double = 0.0,
    /** Screen: fraction of the spatial reflection veil subtracted on dark-background screens. */
    val veilRemoval: Double = 0.0,
    /** Vivid: CLAHE blend on lightness. */
    val localContrast: Double = 0.0,
)

internal object PageFilterLook {
    /**
     * Resolves render parameters for a concrete preset ([PageFilterPreset.AUTO] must be
     * resolved and [PageFilterPreset.ORIGINAL] skipped by the caller).
     *
     * [intensity] scales the look: 0 only evens out lighting, 1 is the tuned default,
     * 1.5 is the strongest.
     */
    fun resolve(
        preset: PageFilterPreset,
        profile: PageImageProfile?,
        intensity: Double,
        renderLongestEdge: Int,
    ): PageFilterRenderParams {
        val t = intensity.coerceIn(0.0, MAX_FILTER_INTENSITY)
        val scale = (renderLongestEdge.coerceAtLeast(1).toDouble() / FILTER_REFERENCE_EDGE)
            .coerceIn(0.05, 3.0)
        val ink = (profile?.toneInkLevel ?: DEFAULT_INK_LEVEL).coerceIn(0.0, 230.0)
        val measuredWhite = (profile?.toneWhitePoint ?: DEFAULT_WHITE_POINT).coerceIn(170.0, 252.0)
        val paper = (profile?.tonePaperLevel ?: DEFAULT_PAPER_LEVEL).coerceIn(150.0, 255.0)
        // Noisy (dim / high-ISO) captures get less sharpening so grain is not amplified.
        val sharpenDamp = 1.0 - normalized(profile?.noise ?: 4.0, 3.0, 11.0) * 0.6
        val sharpenSigma = (0.9 * scale).coerceAtLeast(0.5)
        val denoiseSigma = (1.1 * scale).coerceAtLeast(0.6)

        fun whiteAt(white: Double) = 255.0 - (255.0 - white) * t

        val params = when (preset) {
            PageFilterPreset.ORIGINAL,
            PageFilterPreset.AUTO,
            PageFilterPreset.MAGIC_COLOR,
            -> PageFilterRenderParams(
                mode = PageFilterRenderMode.Color,
                strokeFraction = 0.03,
                maxGain = 4.0,
                blackPoint = minOf(ink, 70.0) * t,
                whitePoint = whiteAt(measuredWhite),
                gamma = 1.0 + 0.35 * t,
                vibrance = 0.35 * t,
                sharpenAmount = 0.6 * t * sharpenDamp,
                sharpenSigma = sharpenSigma,
                denoiseSigma = denoiseSigma,
            )

            PageFilterPreset.WHITEBOARD -> PageFilterRenderParams(
                mode = PageFilterRenderMode.Color,
                strokeFraction = 0.045,
                maxGain = 4.5,
                blackPoint = minOf(ink, 90.0) * t,
                whitePoint = whiteAt(minOf(measuredWhite, paper - 25.0)),
                gamma = 1.0 + 0.6 * t,
                vibrance = 0.7 * t,
                sharpenAmount = 0.5 * t * sharpenDamp,
                sharpenSigma = sharpenSigma,
                denoiseSigma = denoiseSigma,
            )

            PageFilterPreset.NATURAL_COLOR -> PageFilterRenderParams(
                mode = PageFilterRenderMode.Natural,
                strokeFraction = 0.03,
                maxGain = 2.5,
                blackPoint = minOf(ink, 35.0) * t,
                whitePoint = 255.0,
                gamma = 1.0 + 0.08 * t,
                vibrance = 0.0,
                sharpenAmount = 0.35 * t * sharpenDamp,
                sharpenSigma = sharpenSigma,
                denoiseSigma = denoiseSigma,
                naturalLift = t,
                naturalWhiteBalance = 0.6 * t.coerceAtMost(1.0),
            )

            PageFilterPreset.VIVID_COLOR -> PageFilterRenderParams(
                mode = PageFilterRenderMode.Vivid,
                strokeFraction = 0.0,
                maxGain = 1.0,
                blackPoint = 0.0,
                whitePoint = 255.0,
                gamma = 1.0,
                vibrance = 0.55 * t,
                sharpenAmount = 0.4 * t * sharpenDamp,
                sharpenSigma = sharpenSigma,
                denoiseSigma = 0.0,
                contrastCurve = 0.2 * t,
                whiteBalance = 0.6 * t.coerceAtMost(1.0),
                blackClip = 0.8 * t,
                localContrast = (0.45 * t).coerceAtMost(0.65),
            )

            PageFilterPreset.SCREEN -> PageFilterRenderParams(
                mode = PageFilterRenderMode.Screen,
                strokeFraction = 0.0,
                maxGain = 1.0,
                blackPoint = 0.0,
                whitePoint = 255.0,
                gamma = 1.0,
                vibrance = 0.22 * t,
                sharpenAmount = 0.35 * t,
                sharpenSigma = sharpenSigma,
                denoiseSigma = 0.0,
                contrastCurve = 0.15 * t,
                whiteBalance = 0.9 * t.coerceAtMost(1.0),
                blackClip = 0.9 * t.coerceAtMost(1.0),
                // De-screening stays on even at low intensity: the grid is never wanted.
                descreenSigma = (0.8 * scale * (0.6 + 0.4 * t.coerceAtMost(1.0))).coerceAtLeast(0.5),
                chromaSigma = (2.0 * scale).coerceAtLeast(0.8),
                veilRemoval = t.coerceAtMost(1.0),
            )

            PageFilterPreset.GRAYSCALE -> PageFilterRenderParams(
                mode = PageFilterRenderMode.Gray,
                strokeFraction = 0.03,
                maxGain = 4.0,
                blackPoint = minOf(ink, 60.0) * t,
                whitePoint = whiteAt(measuredWhite),
                gamma = 1.0 + 0.25 * t,
                vibrance = 0.0,
                sharpenAmount = 0.6 * t * sharpenDamp,
                sharpenSigma = sharpenSigma,
                denoiseSigma = denoiseSigma,
            )

            PageFilterPreset.FADED -> {
                val white = whiteAt(minOf(measuredWhite, paper - 8.0))
                PageFilterRenderParams(
                    mode = PageFilterRenderMode.Gray,
                    strokeFraction = 0.03,
                    maxGain = 4.0,
                    // Pull faint ink toward black: the black point sits 30% of the way to paper.
                    blackPoint = (ink + (white - ink) * 0.30) * t,
                    whitePoint = white,
                    gamma = 1.0 + 0.8 * t,
                    vibrance = 0.0,
                    sharpenAmount = 0.9 * t * sharpenDamp,
                    sharpenSigma = sharpenSigma,
                    denoiseSigma = denoiseSigma,
                )
            }

            PageFilterPreset.BLACK_AND_WHITE -> {
                val k = (0.70 + 0.15 * (t - 1.0)).coerceIn(0.45, 0.88)
                PageFilterRenderParams(
                    mode = PageFilterRenderMode.Binary,
                    strokeFraction = 0.03,
                    maxGain = 4.0,
                    blackPoint = 0.0,
                    whitePoint = 255.0,
                    gamma = 1.0,
                    vibrance = 0.0,
                    sharpenAmount = 0.0,
                    sharpenSigma = sharpenSigma,
                    denoiseSigma = denoiseSigma,
                    binaryThreshold = ink + (measuredWhite - ink) * k,
                    binaryRamp = 8.0,
                    despeckleMinArea = (4.0 * scale * scale).roundToInt().coerceAtLeast(2),
                )
            }
        }
        return params.withSafeTonalRange()
    }

    private fun PageFilterRenderParams.withSafeTonalRange(): PageFilterRenderParams {
        val white = whitePoint.coerceIn(MIN_TONAL_RANGE, 255.0)
        val black = blackPoint.coerceIn(0.0, white - MIN_TONAL_RANGE)
        return copy(blackPoint = black, whitePoint = white)
    }

    internal const val DEFAULT_INK_LEVEL = 40.0
    internal const val DEFAULT_WHITE_POINT = 235.0
    internal const val DEFAULT_PAPER_LEVEL = 246.0
    private const val MIN_TONAL_RANGE = 16.0
}

internal fun normalized(value: Double, min: Double, max: Double): Double {
    if (max <= min) return 0.0
    return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
}

internal fun toOddWithin(value: Int, min: Int, max: Int): Int {
    var candidate = value.coerceIn(min, max)
    if (candidate % 2 == 0) {
        candidate = if (candidate >= max) candidate - 1 else candidate + 1
    }
    return candidate.coerceIn(min, max)
}
