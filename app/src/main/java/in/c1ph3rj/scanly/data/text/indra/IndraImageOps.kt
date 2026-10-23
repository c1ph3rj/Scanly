package `in`.c1ph3rj.scanly.data.text.indra

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Pixel work for the Indra pipeline. All images are CV_8UC1 grayscale unless noted. */
internal object IndraImageOps {
    private const val PREVIEW_LONG_SIDE = 1600
    private const val ORIENT_SIDE = 768
    private val inkTable = FloatArray(256) { 1f - it / 255f }

    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            check(OpenCVLoader.initLocal()) { "OpenCV could not be initialized." }
            initialized = true
        }
    }

    /** Y = 0.299R + 0.587G + 0.114B, rounded. */
    fun grayFromBitmap(bitmap: Bitmap): Mat {
        val rgba = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        } finally {
            rgba.release()
        }
        return gray
    }

    /** Applies an EXIF clockwise rotation of 0, 90, 180 or 270 degrees. */
    fun rotateClockwise(gray: Mat, degrees: Int): Mat {
        val code = when (((degrees % 360) + 360) % 360) {
            90 -> Core.ROTATE_90_CLOCKWISE
            180 -> Core.ROTATE_180
            270 -> Core.ROTATE_90_COUNTERCLOCKWISE
            else -> return gray
        }
        val out = Mat()
        Core.rotate(gray, out, code)
        gray.release()
        return out
    }

    /** `k` clockwise quarter turns. */
    fun turn(gray: Mat, quarterTurns: Int): Mat {
        val code = when (quarterTurns) {
            1 -> Core.ROTATE_90_CLOCKWISE
            2 -> Core.ROTATE_180
            3 -> Core.ROTATE_90_COUNTERCLOCKWISE
            else -> return gray.clone()
        }
        val out = Mat()
        Core.rotate(gray, out, code)
        return out
    }

    fun preview(gray: Mat): Mat {
        val longSide = max(gray.cols(), gray.rows())
        if (longSide <= PREVIEW_LONG_SIDE) return gray.clone()
        val scale = PREVIEW_LONG_SIDE.toDouble() / longSide
        return resize(gray, (gray.cols() * scale).roundToInt(), (gray.rows() * scale).roundToInt(), Imgproc.INTER_AREA)
    }

    fun resize(src: Mat, width: Int, height: Int, interpolation: Int): Mat {
        val out = Mat()
        Imgproc.resize(src, out, Size(max(1, width).toDouble(), max(1, height).toDouble()), 0.0, 0.0, interpolation)
        return out
    }

    // ---- skew -------------------------------------------------------------------------------

    /** Tilt (degrees, OpenCV rotation convention) whose horizontal ink profile changes most sharply. */
    fun estimateSkew(small: Mat): Double {
        val fine = inkThumbnail(small, 800)
        val coarse = inkThumbnail(small, 400)
        try {
            if (Core.mean(fine).`val`[0] < 0.003 * 255) return 0.0
            val coarseScores = ArrayList<Pair<Double, Double>>()
            var angle = -45.0
            while (angle <= 45.0 + 1e-9) {
                coarseScores += angle to profileScore(coarse, angle)
                angle += 2.0
            }
            val starts = LinkedHashSet<Double>()
            starts += coarseScores.sortedByDescending { it.second }.take(2).map { it.first }
            starts += 0.0
            var bestAngle = 0.0
            var bestScore = Double.NEGATIVE_INFINITY
            val tried = HashMap<Double, Double>()
            fun score(angle: Double): Double = tried.getOrPut(angle) { profileScore(fine, angle) }
            for (start in starts) {
                for (step in -4..4) {
                    val candidate = start + step * 0.5
                    val value = score(candidate)
                    if (value > bestScore) {
                        bestScore = value
                        bestAngle = candidate
                    }
                }
            }
            val center = bestAngle
            for (step in -5..5) {
                val candidate = center + step * 0.1
                val value = score(candidate)
                if (value > bestScore) {
                    bestScore = value
                    bestAngle = candidate
                }
            }
            return bestAngle
        } finally {
            fine.release()
            coarse.release()
        }
    }

    private fun inkThumbnail(small: Mat, side: Int): Mat {
        val longSide = max(small.cols(), small.rows())
        val scale = side.toDouble() / longSide
        val thumb = resize(
            small,
            (small.cols() * scale).roundToInt(),
            (small.rows() * scale).roundToInt(),
            Imgproc.INTER_AREA,
        )
        val ink = Mat()
        thumb.convertTo(ink, CvType.CV_32F, -1.0, 255.0)
        thumb.release()
        return ink
    }

    private fun profileScore(ink: Mat, angle: Double): Double {
        val rotated = if (angle == 0.0) ink else {
            val out = Mat()
            val matrix = Geometry.getRotationMatrix2D(Point(ink.cols() / 2.0, ink.rows() / 2.0), angle, 1.0)
            Imgproc.warpAffine(
                ink, out, matrix, ink.size(), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(0.0),
            )
            matrix.release()
            out
        }
        val sums = Mat()
        try {
            Core.reduce(rotated, sums, 1, Core.REDUCE_SUM, CvType.CV_64F)
            val rows = sums.rows()
            val profile = DoubleArray(rows)
            sums.get(0, 0, profile)
            var total = 0.0
            for (i in 0 until rows - 1) {
                val d = profile[i + 1] - profile[i]
                total += d * d
            }
            return total
        } finally {
            sums.release()
            if (rotated !== ink) rotated.release()
        }
    }

    /** Rotates the page about its centre by [angle] degrees, replicating the border. */
    fun rotatePage(gray: Mat, angle: Double): Mat {
        val out = Mat()
        val matrix = Geometry.getRotationMatrix2D(Point(gray.cols() / 2.0, gray.rows() / 2.0), angle, 1.0)
        Imgproc.warpAffine(
            gray, out, matrix, gray.size(), Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE,
        )
        matrix.release()
        return out
    }

    // ---- model inputs -----------------------------------------------------------------------

    /** 768 x 768 ink canvas with the page scaled to fit and pasted at the top-left. */
    fun orientInput(small: Mat): FloatArray {
        val longSide = max(small.cols(), small.rows())
        val scale = ORIENT_SIDE.toDouble() / longSide
        val scaled = resize(
            small,
            (small.cols() * scale).roundToInt().coerceIn(1, ORIENT_SIDE),
            (small.rows() * scale).roundToInt().coerceIn(1, ORIENT_SIDE),
            Imgproc.INTER_AREA,
        )
        try {
            val canvas = FloatArray(ORIENT_SIDE * ORIENT_SIDE)
            val w = scaled.cols()
            val h = scaled.rows()
            val bytes = bytesOf(scaled)
            for (y in 0 until h) {
                for (x in 0 until w) canvas[y * ORIENT_SIDE + x] = inkTable[bytes[y * w + x].toInt() and 0xFF]
            }
            return canvas
        } finally {
            scaled.release()
        }
    }

    class DetInput(val ink: FloatArray, val paddedHeight: Int, val paddedWidth: Int, val scale: Double)

    /** `page_to_input`: long side scaled to [longSide], bottom/right padded with paper to multiples of 32. */
    fun detInput(gray: Mat, longSide: Int): DetInput {
        val scale = longSide.toDouble() / max(gray.cols(), gray.rows())
        val width = (gray.cols() * scale).roundToInt().coerceAtLeast(1)
        val height = (gray.rows() * scale).roundToInt().coerceAtLeast(1)
        val resized = resize(gray, width, height, if (scale < 1.0) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR)
        try {
            val paddedW = ceil(width / 32.0).toInt() * 32
            val paddedH = ceil(height / 32.0).toInt() * 32
            val ink = FloatArray(paddedH * paddedW)
            val bytes = bytesOf(resized)
            for (y in 0 until height) {
                for (x in 0 until width) ink[y * paddedW + x] = inkTable[bytes[y * width + x].toInt() and 0xFF]
            }
            return DetInput(ink, paddedH, paddedW, scale)
        } finally {
            resized.release()
        }
    }

    /** 256-bin histogram of the page pixels under [box] (clipped to the page). */
    fun histogram(gray: Mat, box: IndraBox): IntArray {
        val x0 = Math.floor(box.x0.toDouble()).toInt().coerceIn(0, gray.cols() - 1)
        val y0 = Math.floor(box.y0.toDouble()).toInt().coerceIn(0, gray.rows() - 1)
        val x1 = ceil(box.x1.toDouble()).toInt().coerceIn(x0 + 1, gray.cols())
        val y1 = ceil(box.y1.toDouble()).toInt().coerceIn(y0 + 1, gray.rows())
        val region = gray.submat(y0, y1, x0, x1)
        val copy = region.clone()
        region.release()
        try {
            val bytes = bytesOf(copy)
            val histogram = IntArray(256)
            for (b in bytes) histogram[b.toInt() and 0xFF]++
            return histogram
        } finally {
            copy.release()
        }
    }

    /**
     * `crop_line`: the crop is [height] px tall ([IndraGeometry.LINE_HEIGHT] x window), outside the
     * page is paper (or black for a [dark] line), and the result is an ink map (row-major
     * `height x geometry.outWidth`), inverted for dark lines so text is always ink.
     */
    fun renderCrop(gray: Mat, geometry: CropGeometry, dark: Boolean, height: Int): FloatArray {
        val cw = geometry.width
        val ch = geometry.height
        val patch = Mat(ch, cw, CvType.CV_8UC1, Scalar(if (dark) 0.0 else 255.0))
        val ix0 = max(geometry.x0, 0)
        val iy0 = max(geometry.y0, 0)
        val ix1 = minOf(geometry.x1, gray.cols())
        val iy1 = minOf(geometry.y1, gray.rows())
        if (ix1 > ix0 && iy1 > iy0) {
            val source = gray.submat(iy0, iy1, ix0, ix1)
            val target = patch.submat(iy0 - geometry.y0, iy1 - geometry.y0, ix0 - geometry.x0, ix1 - geometry.x0)
            source.copyTo(target)
            source.release()
            target.release()
        }
        val resized = resize(
            patch,
            geometry.outWidth,
            height,
            if (ch > height) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR,
        )
        patch.release()
        try {
            val bytes = bytesOf(resized)
            val ink = FloatArray(bytes.size)
            for (i in bytes.indices) {
                val v = bytes[i].toInt() and 0xFF
                ink[i] = if (dark) v / 255f else inkTable[v]
            }
            return ink
        } finally {
            resized.release()
        }
    }

    private fun bytesOf(mat: Mat): ByteArray {
        val source = if (mat.isContinuous) mat else mat.clone()
        val bytes = ByteArray(source.rows() * source.cols())
        source.get(0, 0, bytes)
        if (source !== mat) source.release()
        return bytes
    }

    fun isTilted(angle: Double): Boolean = abs(angle) >= 0.3
}
