package `in`.c1ph3rj.scanly.core.processing

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Shared OpenCV init + Mat/Bitmap bridges used by page filters.
 * Keeps heavy filter presets free of I/O and lifecycle noise.
 */
internal object OpenCvMatSupport {
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

    const val ANALYSIS_MAX_DIMENSION = 720

    fun aspectRatio(width: Int, height: Int): Double {
        val shortEdge = minOf(width, height).coerceAtLeast(1)
        return maxOf(width, height).toDouble() / shortEdge.toDouble()
    }

    fun oddKernel(value: Int, min: Int, max: Int): Int {
        var candidate = value.coerceIn(min, max)
        if (candidate % 2 == 0) {
            candidate = if (candidate >= max) candidate - 1 else candidate + 1
        }
        return candidate.coerceAtLeast(3)
    }
}

internal fun Bitmap.toOpenCvMat(): Mat {
    val mat = Mat(height, width, CvType.CV_8UC4)
    Utils.bitmapToMat(this, mat)
    return mat
}

internal fun Mat.toAndroidBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(this, bitmap)
    return bitmap
}

/** Downsamples large mats for profile analysis only (does not own source when unscaled). */
internal fun Mat.forAnalysisDownsample(
    maxDimension: Int = OpenCvMatSupport.ANALYSIS_MAX_DIMENSION,
): Mat {
    val longestEdge = maxOf(cols(), rows())
    if (longestEdge <= maxDimension) {
        return this
    }

    val scale = maxDimension / longestEdge.toDouble()
    val resized = Mat()
    Imgproc.resize(this, resized, Size(), scale, scale, Imgproc.INTER_AREA)
    return resized
}
