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

    private fun shadedPaperBitmap(includeColorMarks: Boolean): Bitmap {
        val width = 480
        val height = 640
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val shadow = 38.0 * (x / (width - 1.0))
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
            canvas.drawText("SCANLY DOCUMENT", 54f, 128f, ink)
            canvas.drawText("Faint text stays readable", 54f, 190f, ink.apply { alpha = 180 })
            if (includeColorMarks) {
                canvas.drawRect(90f, 286f, 176f, 356f, Paint().apply { color = Color.rgb(190, 45, 48) })
                canvas.drawRect(204f, 286f, 290f, 356f, Paint().apply { color = Color.rgb(34, 98, 184) })
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
