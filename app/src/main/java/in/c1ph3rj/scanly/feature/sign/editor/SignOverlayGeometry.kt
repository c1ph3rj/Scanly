package `in`.c1ph3rj.scanly.feature.sign.editor

import `in`.c1ph3rj.scanly.domain.model.NormRect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Smallest width a mark may be resized to, as a fraction of the page width. */
const val DefaultMinMarkWidth = 0.03f

/** A page fitted inside a container. [left] and [top] are the offset of its top-left corner. */
data class FitRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/** The page corner a resize drag moves. The diagonally opposite corner stays where it is. */
enum class Corner { TopLeft, TopRight, BottomLeft, BottomRight }

/**
 * Aspect-fits a page of [aspect] (width / height) into a container and centres it. The result uses
 * the same units as the container.
 */
fun fitPage(containerW: Float, containerH: Float, aspect: Float): FitRect {
    val cw = containerW.coerceAtLeast(0f)
    val ch = containerH.coerceAtLeast(0f)
    if (cw == 0f || ch == 0f || aspect <= 0f) return FitRect(left = 0f, top = 0f, width = cw, height = ch)
    return if (cw / ch > aspect) {
        val width = ch * aspect
        FitRect(left = (cw - width) / 2f, top = 0f, width = width, height = ch)
    } else {
        val height = cw / aspect
        FitRect(left = 0f, top = (ch - height) / 2f, width = cw, height = height)
    }
}

/**
 * Resizes [box] by dragging [corner] by ([dxNorm], [dyNorm]) in normalised page units. The corner
 * opposite the dragged one stays fixed.
 *
 * When [lockAspect] is true the visual aspect (width * [pageAspect] / height) is preserved and the
 * drag component that moved furthest decides the scale. The result is never narrower than
 * [minWidth] (its height never less than the visually square minimum) and always stays on the page.
 */
fun resizeFromCorner(
    box: NormRect,
    corner: Corner,
    dxNorm: Float,
    dyNorm: Float,
    lockAspect: Boolean,
    pageAspect: Float,
    minWidth: Float = DefaultMinMarkWidth,
): NormRect {
    val aspect = if (pageAspect > 0f) pageAspect else 1f
    val movesLeft = corner == Corner.TopLeft || corner == Corner.BottomLeft
    val movesTop = corner == Corner.TopLeft || corner == Corner.TopRight
    val signX = if (movesLeft) -1f else 1f
    val signY = if (movesTop) -1f else 1f

    // Room the box may grow into on each axis, measured away from the fixed corner.
    val roomX = if (movesLeft) box.right else 1f - box.left
    val roomY = if (movesTop) box.bottom else 1f - box.top

    val minW = minWidth.coerceIn(0f, 1f)
    val minH = (minW * aspect).coerceIn(0f, 1f)
    val boxW = max(box.width, NormRect.MinSide)
    val boxH = max(box.height, NormRect.MinSide)

    val result = if (lockAspect) {
        val scaleX = (boxW + signX * dxNorm) / boxW
        val scaleY = (boxH + signY * dyNorm) / boxH
        val drag = if (abs(scaleX - 1f) >= abs(scaleY - 1f)) scaleX else scaleY
        val low = max(minW / boxW, minH / boxH)
        val high = min(roomX / boxW, roomY / boxH)
        val scale = clampTo(drag, low, high)
        anchored(box, corner, boxW * scale, boxH * scale)
    } else {
        val w = clampTo(boxW + signX * dxNorm, minW, roomX)
        val h = clampTo(boxH + signY * dyNorm, minH, roomY)
        anchored(box, corner, w, h)
    }
    return result.clampedToPage()
}

/** Moves [box] by the given normalised offset, keeping it fully on the page. */
fun moveBox(box: NormRect, dxNorm: Float, dyNorm: Float): NormRect =
    box.offsetBy(dxNorm, dyNorm).clampedToPage()

private fun anchored(box: NormRect, corner: Corner, width: Float, height: Float): NormRect {
    val left = if (corner == Corner.TopLeft || corner == Corner.BottomLeft) box.right - width else box.left
    val top = if (corner == Corner.TopLeft || corner == Corner.TopRight) box.bottom - height else box.top
    return NormRect(left = left, top = top, width = width, height = height)
}

private fun clampTo(value: Float, low: Float, high: Float): Float =
    if (high < low) high else value.coerceIn(low, high)
