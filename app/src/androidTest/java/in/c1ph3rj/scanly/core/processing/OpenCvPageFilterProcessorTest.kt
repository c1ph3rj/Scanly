package `in`.c1ph3rj.scanly.core.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenCvPageFilterProcessorTest {
    @Test
    fun shadowReductionFlattensPaperAndPreservesColorMarks() {
        val source = shadedPaperBitmap(includeColorMarks = true)
        val filtered = OpenCvPageFilterProcessor.apply(source, PageFilterPreset.SHADOW_REDUCTION)

        try {
            val sourceShadowDelta = luminanceDeltaAcrossPage(source)
            val filteredShadowDelta = luminanceDeltaAcrossPage(filtered)
            val sourcePaperCast = paperColorCast(source)
            val filteredPaperCast = paperColorCast(filtered)
            val sourceMarkSaturation = saturationAt(source, x = 132, y = 322)
            val filteredMarkSaturation = saturationAt(filtered, x = 132, y = 322)

            assertTrue(
                "Paper lighting should be flatter after shadow reduction.",
                filteredShadowDelta < sourceShadowDelta * 0.72,
            )
            assertTrue(
                "Warm lighting should be neutralized on likely paper pixels.",
                filteredPaperCast < sourcePaperCast * 0.82,
            )
            assertTrue(
                "Colored stamps and logos should retain useful saturation.",
                filteredMarkSaturation > sourceMarkSaturation * 0.70,
            )
        } finally {
            source.recycle()
            filtered.recycle()
        }
    }

    @Test
    fun shadowReductionDoesNotAmplifyBlankPaperNoise() {
        val source = shadedPaperBitmap(includeColorMarks = false)
        val filtered = OpenCvPageFilterProcessor.apply(source, PageFilterPreset.SHADOW_REDUCTION)

        try {
            val sourceNoise = luminanceStandardDeviation(source, left = 190, top = 220, right = 290, bottom = 420)
            val filteredNoise = luminanceStandardDeviation(filtered, left = 190, top = 220, right = 290, bottom = 420)

            assertTrue(
                "Local contrast must not turn low-detail paper into visible texture.",
                filteredNoise <= (sourceNoise * 1.15) + 1.0,
            )
        } finally {
            source.recycle()
            filtered.recycle()
        }
    }

    @Test
    fun enhancedColorKeepsEvenPaperAndColorMarks() {
        val source = evenPaperBitmap()
        val filtered = OpenCvPageFilterProcessor.apply(source, PageFilterPreset.ENHANCED_COLOR)

        try {
            val sourcePaper = meanLuminance(source, left = 28, top = 36, right = 92, bottom = 92)
            val filteredPaper = meanLuminance(filtered, left = 28, top = 36, right = 92, bottom = 92)
            val sourceMarkSaturation = saturationAt(source, x = 132, y = 322)
            val filteredMarkSaturation = saturationAt(filtered, x = 132, y = 322)
            val hueDelta = hueDistance(
                hueAt(source, x = 132, y = 322),
                hueAt(filtered, x = 132, y = 322),
            )

            assertTrue(
                "Well-lit even paper should stay close to the capture.",
                kotlin.math.abs(filteredPaper - sourcePaper) < 12.0,
            )
            assertTrue(
                "Color marks should keep their hue.",
                hueDelta < 18.0,
            )
            assertTrue(
                "Colored stamps and logos should retain useful saturation.",
                filteredMarkSaturation > sourceMarkSaturation * 0.70,
            )
        } finally {
            source.recycle()
            filtered.recycle()
        }
    }

    @Test
    fun sharedProfileKeepsPaperLookAcrossRenderSizes() {
        val master = shadedPaperBitmap(
            includeColorMarks = true,
            width = 1_800,
            height = 2_400,
        )
        val profile = OpenCvPageFilterProcessor.analyze(master)
        val preview = scaleToLongestEdge(master, 1_600)
        val thumb = scaleToLongestEdge(master, 320)
        val filteredSave = OpenCvPageFilterProcessor.apply(
            master,
            PageFilterPreset.SHADOW_REDUCTION,
            profile,
        )
        val filteredPreview = OpenCvPageFilterProcessor.apply(
            preview,
            PageFilterPreset.SHADOW_REDUCTION,
            profile,
        )
        val filteredThumb = OpenCvPageFilterProcessor.apply(
            thumb,
            PageFilterPreset.SHADOW_REDUCTION,
            profile,
        )

        try {
            val savePaper = fractionalPaperMean(filteredSave)
            val previewPaper = fractionalPaperMean(filteredPreview)
            val thumbPaper = fractionalPaperMean(filteredThumb)
            val saveContrast = fractionalPaperContrast(filteredSave)
            val previewContrast = fractionalPaperContrast(filteredPreview)
            val thumbContrast = fractionalPaperContrast(filteredThumb)

            assertTrue(
                "Preview and save paper means should stay aligned.",
                kotlin.math.abs(previewPaper - savePaper) < 8.0,
            )
            assertTrue(
                "Thumb and save paper means should stay aligned.",
                kotlin.math.abs(thumbPaper - savePaper) < 10.0,
            )
            assertTrue(
                "Preview and save paper contrast should stay aligned.",
                kotlin.math.abs(previewContrast - saveContrast) < 4.0,
            )
            assertTrue(
                "Thumb and save paper contrast should stay aligned.",
                kotlin.math.abs(thumbContrast - saveContrast) < 5.0,
            )
        } finally {
            master.recycle()
            if (preview !== master) preview.recycle()
            if (thumb !== master) thumb.recycle()
            filteredSave.recycle()
            filteredPreview.recycle()
            filteredThumb.recycle()
        }
    }

    private fun evenPaperBitmap(): Bitmap =
        shadedPaperBitmap(includeColorMarks = true, width = 480, height = 640, shadowAmount = 0.0)

    private fun shadedPaperBitmap(
        includeColorMarks: Boolean,
        width: Int = 480,
        height: Int = 640,
        shadowAmount: Double = 38.0,
    ): Bitmap {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val shadow = shadowAmount * (x / (width - 1.0))
                val noise = (((x * 17) + (y * 31)) % 5) - 2
                val base = (230.0 - shadow + noise).toInt()
                pixels[(y * width) + x] = Color.rgb(
                    (base + 10).coerceIn(0, 255),
                    (base + 2).coerceIn(0, 255),
                    (base - 9).coerceIn(0, 255),
                )
            }
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            val canvas = Canvas(bitmap)
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(35, 39, 43)
                textSize = 30f
                strokeWidth = 3f
            }
            val xScale = width / 480f
            val yScale = height / 640f
            canvas.drawText("SCANLY DOCUMENT", 54f * xScale, 128f * yScale, ink.apply {
                textSize = 30f * minOf(xScale, yScale)
            })
            canvas.drawText(
                "Faint text stays readable",
                54f * xScale,
                190f * yScale,
                ink.apply { alpha = 180 },
            )
            if (includeColorMarks) {
                canvas.drawRect(
                    90f * xScale,
                    286f * yScale,
                    176f * xScale,
                    356f * yScale,
                    Paint().apply { color = Color.rgb(190, 45, 48) },
                )
                canvas.drawRect(
                    204f * xScale,
                    286f * yScale,
                    290f * xScale,
                    356f * yScale,
                    Paint().apply { color = Color.rgb(34, 98, 184) },
                )
            }
        }
    }

    private fun luminanceDeltaAcrossPage(bitmap: Bitmap): Double {
        val left = meanLuminance(bitmap, left = 28, top = 36, right = 92, bottom = 92)
        val right = meanLuminance(bitmap, left = 388, top = 36, right = 452, bottom = 92)
        return kotlin.math.abs(left - right)
    }

    private fun paperColorCast(bitmap: Bitmap): Double {
        val color = meanColor(bitmap, left = 28, top = 36, right = 92, bottom = 92)
        val red = Color.red(color).toDouble()
        val green = Color.green(color).toDouble()
        val blue = Color.blue(color).toDouble()
        return maxOf(red, green, blue) - minOf(red, green, blue)
    }

    private fun saturationAt(bitmap: Bitmap, x: Int, y: Int): Double {
        val hsv = FloatArray(3)
        Color.colorToHSV(bitmap.getPixel(x, y), hsv)
        return hsv[1].toDouble()
    }

    private fun hueAt(bitmap: Bitmap, x: Int, y: Int): Double {
        val hsv = FloatArray(3)
        Color.colorToHSV(bitmap.getPixel(x, y), hsv)
        return hsv[0].toDouble()
    }

    private fun hueDistance(first: Double, second: Double): Double {
        val delta = kotlin.math.abs(first - second)
        return minOf(delta, 360.0 - delta)
    }

    private fun scaleToLongestEdge(bitmap: Bitmap, longestEdge: Int): Bitmap {
        val current = maxOf(bitmap.width, bitmap.height)
        if (current == longestEdge) {
            return bitmap
        }
        val scale = longestEdge / current.toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun fractionalPaperMean(bitmap: Bitmap): Double {
        val left = (bitmap.width * 0.06).toInt()
        val top = (bitmap.height * 0.06).toInt()
        val right = (bitmap.width * 0.19).toInt()
        val bottom = (bitmap.height * 0.14).toInt()
        return meanLuminance(bitmap, left, top, right, bottom)
    }

    private fun fractionalPaperContrast(bitmap: Bitmap): Double {
        val left = (bitmap.width * 0.40).toInt()
        val top = (bitmap.height * 0.34).toInt()
        val right = (bitmap.width * 0.60).toInt()
        val bottom = (bitmap.height * 0.66).toInt()
        return luminanceStandardDeviation(bitmap, left, top, right, bottom)
    }

    private fun meanLuminance(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Double {
        var total = 0.0
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                total += luminance(bitmap.getPixel(x, y))
                count++
            }
        }
        return total / count.coerceAtLeast(1)
    }

    private fun luminanceStandardDeviation(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Double {
        val mean = meanLuminance(bitmap, left, top, right, bottom)
        var squaredDifference = 0.0
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                squaredDifference += (luminance(bitmap.getPixel(x, y)) - mean).pow(2)
                count++
            }
        }
        return sqrt(squaredDifference / count.coerceAtLeast(1))
    }

    private fun meanColor(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        for (y in top until bottom) {
            for (x in left until right) {
                val color = bitmap.getPixel(x, y)
                red += Color.red(color)
                green += Color.green(color)
                blue += Color.blue(color)
                count++
            }
        }
        return Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }

    private fun luminance(color: Int): Double =
        (Color.red(color) * 0.2126) + (Color.green(color) * 0.7152) + (Color.blue(color) * 0.0722)
}
