package `in`.c1ph3rj.scanly.data.pdftools

import `in`.c1ph3rj.scanly.domain.model.NormRect

/**
 * Maps sign marks from normalised visual page space (fractions of the page as displayed, origin
 * top-left, y down) into PDF user space. Kept free of PDFBox/Android so it is unit-testable.
 */
internal object SignGeometry {

    /** A PDF affine transform: x' = a·x + c·y + e, y' = b·x + d·y + f. */
    data class Affine(
        val a: Float,
        val b: Float,
        val c: Float,
        val d: Float,
        val e: Float,
        val f: Float,
    ) {
        fun apply(x: Float, y: Float): Pair<Float, Float> = (a * x + c * y + e) to (b * x + d * y + f)
    }

    /** Page geometry: crop box origin and unrotated size in points, plus /Rotate. */
    data class PageFrame(
        val lowerX: Float,
        val lowerY: Float,
        val boxWidth: Float,
        val boxHeight: Float,
        val rotation: Int,
    ) {
        val visualWidth: Float get() = WatermarkGeometry.visualSize(boxWidth, boxHeight, rotation).first
        val visualHeight: Float get() = WatermarkGeometry.visualSize(boxWidth, boxHeight, rotation).second
    }

    /** Visual space (origin bottom-left, y up, points) → user space; matches [WatermarkGeometry.toUserSpace]. */
    fun visualToUser(frame: PageFrame): Affine = with(frame) {
        when (WatermarkGeometry.normalizeRotation(rotation)) {
            90 -> Affine(a = 0f, b = 1f, c = -1f, d = 0f, e = lowerX + boxWidth, f = lowerY)
            180 -> Affine(a = -1f, b = 0f, c = 0f, d = -1f, e = lowerX + boxWidth, f = lowerY + boxHeight)
            270 -> Affine(a = 0f, b = -1f, c = 1f, d = 0f, e = lowerX, f = lowerY + boxHeight)
            else -> Affine(a = 1f, b = 0f, c = 0f, d = 1f, e = lowerX, f = lowerY)
        }
    }

    /** The mark box in visual space as (left, bottom, width, height) in points. */
    fun visualRect(box: NormRect, frame: PageFrame): FloatArray {
        val w = frame.visualWidth
        val h = frame.visualHeight
        return floatArrayOf(box.left * w, (1f - box.bottom) * h, box.width * w, box.height * h)
    }

    /**
     * Maps the unit square (u right, v up) onto the mark box in user space, so an image drawn
     * with this matrix appears upright in the box on screen whatever the page rotation.
     */
    fun unitSquareToUser(box: NormRect, frame: PageFrame): Affine {
        val (vx, vy, vw, vh) = visualRect(box, frame)
        val t = visualToUser(frame)
        return Affine(
            a = t.a * vw,
            b = t.b * vw,
            c = t.c * vh,
            d = t.d * vh,
            e = t.a * vx + t.c * vy + t.e,
            f = t.b * vx + t.d * vy + t.f,
        )
    }

    /**
     * A point inside the box given as fractions (fx right, fy down from the top), in user space.
     */
    fun boxPointToUser(box: NormRect, frame: PageFrame, fx: Float, fy: Float): Pair<Float, Float> =
        unitSquareToUser(box, frame).apply(fx, 1f - fy)

    private operator fun FloatArray.component1() = this[0]
    private operator fun FloatArray.component2() = this[1]
    private operator fun FloatArray.component3() = this[2]
    private operator fun FloatArray.component4() = this[3]
}
