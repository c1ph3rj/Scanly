package `in`.c1ph3rj.scanly.core.processing

internal data class PageImageProfile(
    val brightness: Double,
    val contrast: Double,
    val shadowRatio: Double,
    val highlightRatio: Double,
    val saturation: Double,
    val edgeDensity: Double,
    val sharpness: Double,
    val longestEdge: Int,
)
