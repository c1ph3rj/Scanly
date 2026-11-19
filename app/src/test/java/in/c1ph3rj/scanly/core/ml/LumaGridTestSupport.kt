package `in`.c1ph3rj.scanly.core.ml

/** Builds a synthetic luminance grid from a per-pixel function (0..255). */
internal fun lumaGrid(width: Int, height: Int, luma: (x: Int, y: Int) -> Float): LumaGrid {
    val values = FloatArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            values[y * width + x] = luma(x, y)
        }
    }
    return LumaGrid(width, height, values)
}

/** Normalized quad from pixel corners (TL, TR, BR, BL) using the grid's `x / (width - 1)` mapping. */
internal fun LumaGrid.quadFromPixels(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): DocumentCornerQuad = DocumentCornerQuad(
    topLeft = NormalizedPoint(toNormalizedX(left), toNormalizedY(top)),
    topRight = NormalizedPoint(toNormalizedX(right), toNormalizedY(top)),
    bottomRight = NormalizedPoint(toNormalizedX(right), toNormalizedY(bottom)),
    bottomLeft = NormalizedPoint(toNormalizedX(left), toNormalizedY(bottom)),
)
