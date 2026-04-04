package `in`.c1ph3rj.scanly.core.ml

data class NormalizedPoint(
    val x: Float,
    val y: Float,
) {
    fun isNormalized(): Boolean = x in 0f..1f && y in 0f..1f
}
