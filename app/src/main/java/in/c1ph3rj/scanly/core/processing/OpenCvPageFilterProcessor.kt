package `in`.c1ph3rj.scanly.core.processing

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.processing.filter.PageFilterEngine
import `in`.c1ph3rj.scanly.core.processing.filter.PageImageAnalyzer
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat

object OpenCvPageFilterProcessor {

    data class AppliedFilter(
        val bitmap: Bitmap,
        /** Concrete preset that was rendered (Auto resolves to grayscale/clean/etc.). */
        val appliedPreset: PageFilterPreset,
    )

    @Volatile
    private var initialized = false

    fun apply(
        sourceBitmap: Bitmap,
        filterPreset: PageFilterPreset,
        profile: PageImageProfile? = null,
    ): Bitmap = applyWithResolvedPreset(sourceBitmap, filterPreset, profile).bitmap

    fun applyWithResolvedPreset(
        sourceBitmap: Bitmap,
        filterPreset: PageFilterPreset,
        profile: PageImageProfile? = null,
    ): AppliedFilter {
        ensureInitialized()
        if (filterPreset == PageFilterPreset.ORIGINAL) {
            return AppliedFilter(
                bitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, false),
                appliedPreset = PageFilterPreset.ORIGINAL,
            )
        }

        val sourceRgba = sourceBitmap.toMat()
        return try {
            val resolvedProfile = profile ?: runCatching {
                PageImageAnalyzer.analyze(
                    sourceRgba = sourceRgba,
                    sourceAspectRatio = aspectRatio(sourceBitmap.width, sourceBitmap.height),
                )
            }.getOrNull()
            val resolvedPreset = resolvePreset(filterPreset, resolvedProfile)
            val bitmap = runCatching {
                PageFilterEngine.render(
                    sourceRgba = sourceRgba,
                    filterPreset = resolvedPreset,
                    profile = resolvedProfile,
                    renderLongestEdge = maxOf(sourceBitmap.width, sourceBitmap.height),
                )
            }.getOrElse {
                sourceRgba.toBitmap()
            }
            AppliedFilter(bitmap = bitmap, appliedPreset = resolvedPreset)
        } finally {
            sourceRgba.release()
        }
    }

    fun analyze(sourceBitmap: Bitmap): PageImageProfile {
        ensureInitialized()
        val sourceRgba = sourceBitmap.toMat()
        return try {
            PageImageAnalyzer.analyze(
                sourceRgba = sourceRgba,
                sourceAspectRatio = aspectRatio(sourceBitmap.width, sourceBitmap.height),
            )
        } finally {
            sourceRgba.release()
        }
    }

    /** Resolves Auto to a concrete preset using the same analysis as rendering. */
    private fun resolvePreset(
        filterPreset: PageFilterPreset,
        profile: PageImageProfile?,
    ): PageFilterPreset =
        if (filterPreset == PageFilterPreset.AUTO) {
            AdaptivePageFilterTuning.automatic(profile)
        } else {
            filterPreset
        }

    internal fun applyAll(
        sourceBitmap: Bitmap,
        filterPresets: List<PageFilterPreset> = PageFilterPreset.entries,
        profile: PageImageProfile? = null,
    ): Map<PageFilterPreset, Bitmap> {
        ensureInitialized()
        val sourceRgba = sourceBitmap.toMat()
        return try {
            val resolvedProfile = profile ?: runCatching {
                PageImageAnalyzer.analyze(
                    sourceRgba = sourceRgba,
                    sourceAspectRatio = aspectRatio(sourceBitmap.width, sourceBitmap.height),
                )
            }.getOrNull()
            val renderLongestEdge = maxOf(sourceBitmap.width, sourceBitmap.height)
            filterPresets.associateWith { filterPreset ->
                if (filterPreset == PageFilterPreset.ORIGINAL) {
                    sourceRgba.toBitmap()
                } else {
                    val resolvedPreset = resolvePreset(filterPreset, resolvedProfile)
                    runCatching {
                        PageFilterEngine.render(
                            sourceRgba = sourceRgba,
                            filterPreset = resolvedPreset,
                            profile = resolvedProfile,
                            renderLongestEdge = renderLongestEdge,
                        )
                    }.getOrElse {
                        sourceRgba.toBitmap()
                    }
                }
            }
        } finally {
            sourceRgba.release()
        }
    }

    internal fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            check(OpenCVLoader.initLocal()) { "OpenCV could not be initialized." }
            initialized = true
        }
    }

    private fun Bitmap.toMat(): Mat {
        val mat = Mat(height, width, CvType.CV_8UC4)
        Utils.bitmapToMat(this, mat)
        return mat
    }

    private fun Mat.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(this, bitmap)
        return bitmap
    }

    private fun aspectRatio(width: Int, height: Int): Double {
        val shortEdge = minOf(width, height).coerceAtLeast(1)
        return maxOf(width, height).toDouble() / shortEdge.toDouble()
    }
}
