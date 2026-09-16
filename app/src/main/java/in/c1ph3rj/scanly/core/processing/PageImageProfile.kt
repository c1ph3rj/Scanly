package `in`.c1ph3rj.scanly.core.processing

/**
 * Resolution-independent page analysis. Metrics come from a canonical 720px view.
 * [longestEdge] is informational (analysis or source size) and must not drive filter strength.
 */
data class PageImageProfile(
    val brightness: Double,
    val contrast: Double,
    val shadowRatio: Double,
    val highlightRatio: Double,
    val saturation: Double,
    val edgeDensity: Double,
    val sharpness: Double,
    val longestEdge: Int,
    val backgroundUnevenness: Double = 0.0,
    val textDensity: Double = 0.0,
    val colorRatio: Double = 0.0,
    val aspectRatio: Double = 1.0,
    val paperL: Double = brightness,
    val paperA: Double = 128.0,
    val paperB: Double = 128.0,
    val glareRatio: Double = highlightRatio,
    val noise: Double = 0.0,
)
