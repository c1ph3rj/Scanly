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
    fun magicColorFlattensPaperAndPreservesColorMarks() {
        val source = shadedPaperBitmap(includeColorMarks = true)
        val filtered = OpenCvPageFilterProcessor.apply(source, PageFilterPreset.MAGIC_COLOR)

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
    fun magicColorDoesNotAmplifyBlankPaperNoise() {
        val source = shadedPaperBitmap(includeColorMarks = false)
        val filtered = OpenCvPageFilterProcessor.apply(source, PageFilterPreset.MAGIC_COLOR)

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
    fun naturalColorKeepsEvenPaperAndColorMarks() {
        val source = evenPaperBitmap()
        val filtered = OpenCvPageFilterProcessor.apply(source, PageFilterPreset.NATURAL_COLOR)

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
    fun grayscaleOutputIsNeutralAndKeepsSize() {
        val source = shadedPaperBitmap(includeColorMarks = true)
        val filtered = OpenCvPageFilterProcessor.apply(source, PageFilterPreset.GRAYSCALE)

        try {
            assertTrue(
                "Grayscale output must keep the source size.",
                filtered.width == source.width && filtered.height == source.height,
            )
            assertTrue(
                "Grayscale output must not pull capture chroma back in.",
                saturationAt(filtered, x = 132, y = 322) < 8.0,
            )
            assertAllPixelsNeutral(filtered)
        } finally {
            source.recycle()
            filtered.recycle()
        }
    }

    @Test
    fun fadedDoesNotReintroduceCaptureColor() {
        val source = shadedPaperBitmap(includeColorMarks = true)
        val filtered = OpenCvPageFilterProcessor.apply(source, PageFilterPreset.FADED)

        try {
            assertTrue(
                "Faded output must not pull capture chroma back in.",
                saturationAt(filtered, x = 132, y = 322) < 8.0,
            )
            assertAllPixelsNeutral(filtered)
        } finally {
            source.recycle()
            filtered.recycle()
        }
    }

    @Test
    fun blackAndWhiteOutputIsAlmostEntirelyInkOrPaper() {
        val source = shadedPaperBitmap(includeColorMarks = true)
        val filtered = OpenCvPageFilterProcessor.apply(source, PageFilterPreset.BLACK_AND_WHITE)

        try {
            assertAllPixelsNeutral(filtered)

            var midTonePixels = 0
            var sampled = 0
            for (y in 0 until filtered.height step 2) {
                for (x in 0 until filtered.width step 2) {
                    val value = Color.red(filtered.getPixel(x, y))
                    if (value in 32..223) midTonePixels++
                    sampled++
                }
            }
            val midToneFraction = midTonePixels.toDouble() / sampled.coerceAtLeast(1)

            assertTrue(
                "Black and white output should be near-binary; mid-tone fraction was $midToneFraction.",
                midToneFraction < 0.05,
            )
            assertTrue(
                "Paper should render as white in black and white output.",
                meanLuminance(filtered, left = 28, top = 36, right = 92, bottom = 92) > 250.0,
            )
        } finally {
            source.recycle()
            filtered.recycle()
        }
    }

    @Test
    fun magicColorFullIntensityDarkensInkMoreThanZeroIntensity() {
        val source = shadedPaperBitmap(includeColorMarks = false, shadowAmount = 0.0)
        val weak = OpenCvPageFilterProcessor.apply(
            source,
            PageFilterPreset.MAGIC_COLOR,
            intensity = 0f,
        )
        val strong = OpenCvPageFilterProcessor.apply(
            source,
            PageFilterPreset.MAGIC_COLOR,
            intensity = 1.5f,
        )

        try {
            // Title line only, so the paper and the lighter sub-line are not in the sample.
            val weakInk = minLuminance(weak, left = 54, top = 100, right = 360, bottom = 135)
            val strongInk = minLuminance(strong, left = 54, top = 100, right = 360, bottom = 135)

            assertTrue(
                "Full intensity should darken ink (weak min $weakInk, strong min $strongInk).",
                strongInk < weakInk - 10.0,
            )
        } finally {
            source.recycle()
            weak.recycle()
            strong.recycle()
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
            PageFilterPreset.MAGIC_COLOR,
            profile,
        )
        val filteredPreview = OpenCvPageFilterProcessor.apply(
            preview,
            PageFilterPreset.MAGIC_COLOR,
            profile,
        )
        val filteredThumb = OpenCvPageFilterProcessor.apply(
            thumb,
            PageFilterPreset.MAGIC_COLOR,
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

    @Test
    fun screenKeepsDarkModeBackgroundDarkAndTextBright() {
        val source = darkModeScreenBitmap()
        val filtered = OpenCvPageFilterProcessor.apply(source, PageFilterPreset.SCREEN)

        try {
            assertSameSize(source, filtered)
            val background = meanLuminance(filtered, left = 20, top = 400, right = 460, bottom = 600)
            val text = meanLuminance(filtered, left = 80, top = 122, right = 400, bottom = 138)

            assertTrue(
                "Dark-mode background should stay dark after SCREEN (mean $background).",
                background < 80.0,
            )
            assertTrue(
                "Light text bars should stay bright after SCREEN (mean $text).",
                text > 180.0,
            )
        } finally {
            source.recycle()
            filtered.recycle()
        }
    }

    @Test
    fun screenSuppressesSubPixelStripesOnFlatGray() {
        val source = flatGrayWithSubPixelStripesBitmap()
        val filtered = OpenCvPageFilterProcessor.apply(source, PageFilterPreset.SCREEN)

        try {
            assertSameSize(source, filtered)
            val sourceSpread = luminanceStandardDeviation(source, left = 300, top = 200, right = 900, bottom = 1400)
            val filteredSpread = luminanceStandardDeviation(filtered, left = 300, top = 200, right = 900, bottom = 1400)

            // Levels / S-curve / sharpening re-add a little contrast, so expect at least a halving.
            assertTrue(
                "SCREEN should at least halve sub-pixel stripe spread (source $sourceSpread, filtered $filteredSpread).",
                filteredSpread * 2.0 <= sourceSpread,
            )
        } finally {
            source.recycle()
            filtered.recycle()
        }
    }

    @Test
    fun vividColorRaisesMeanSaturationOfDullPage() {
        val source = dullColoredBitmap()
        val filtered = OpenCvPageFilterProcessor.apply(source, PageFilterPreset.VIVID_COLOR)

        try {
            assertSameSize(source, filtered)
            val sourceSaturation = meanSaturation(source)
            val filteredSaturation = meanSaturation(filtered)

            assertTrue(
                "VIVID_COLOR should raise mean saturation (source $sourceSaturation, filtered $filteredSaturation).",
                filteredSaturation > sourceSaturation * 1.15,
            )
        } finally {
            source.recycle()
            filtered.recycle()
        }
    }

    private fun darkModeScreenBitmap(width: Int = 480, height: Int = 640): Bitmap {
        // Dark-gray background with light text-like bars, both carrying an RGB sub-pixel stripe.
        val bars = listOf(
            intArrayOf(120, 140, 60, 420),
            intArrayOf(200, 212, 60, 300),
            intArrayOf(280, 296, 60, 380),
        )
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val inBar = bars.any { (top, bottom, left, right) ->
                    y in top until bottom && x in left until right
                }
                val base = if (inBar) 200 else 40
                pixels[(y * width) + x] = subPixelColor(base, boost = 20, phase = x % 3)
            }
        }
        return bitmapFromPixels(width, height, pixels)
    }

    private fun flatGrayWithSubPixelStripesBitmap(width: Int = 1_200, height: Int = 1_600): Bitmap {
        // Mid-gray with a 1-px red / green / blue column cycle (a display's sub-pixel grid),
        // framed by black and white UI bands so the page has a real screen's dynamic range.
        // 1200 px at the 1600 px reference keeps the de-screen kernel at its real-photo size.
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val phase = x % 3
                pixels[(y * width) + x] = when {
                    x < 180 -> Color.rgb(0, 0, 0)
                    x >= width - 180 -> Color.rgb(255, 255, 255)
                    else -> Color.rgb(
                        if (phase == 0) 168 else 88,
                        if (phase == 1) 168 else 88,
                        if (phase == 2) 168 else 88,
                    )
                }
            }
        }
        return bitmapFromPixels(width, height, pixels)
    }

    private fun dullColoredBitmap(width: Int = 480, height: Int = 640): Bitmap {
        // Muted stripes (low chroma) across the whole page.
        val palette = listOf(
            Color.rgb(150, 120, 110),
            Color.rgb(110, 135, 120),
            Color.rgb(120, 120, 150),
            Color.rgb(160, 150, 120),
        )
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[(y * width) + x] = palette[(x / 40) % palette.size]
            }
        }
        return bitmapFromPixels(width, height, pixels)
    }

    private fun subPixelColor(base: Int, boost: Int, phase: Int): Int = Color.rgb(
        (base + if (phase == 0) boost else 0).coerceIn(0, 255),
        (base + if (phase == 1) boost else 0).coerceIn(0, 255),
        (base + if (phase == 2) boost else 0).coerceIn(0, 255),
    )

    private fun bitmapFromPixels(width: Int, height: Int, pixels: IntArray): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        }

    private fun assertSameSize(expected: Bitmap, actual: Bitmap) {
        assertTrue(
            "Filtered output must keep the source size.",
            actual.width == expected.width && actual.height == expected.height,
        )
    }

    private fun meanSaturation(bitmap: Bitmap): Double {
        var total = 0.0
        var count = 0
        for (y in 0 until bitmap.height step 4) {
            for (x in 0 until bitmap.width step 4) {
                total += saturationAt(bitmap, x, y)
                count++
            }
        }
        return total / count.coerceAtLeast(1)
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

    private fun assertAllPixelsNeutral(bitmap: Bitmap) {
        for (y in 0 until bitmap.height step 3) {
            for (x in 0 until bitmap.width step 3) {
                val color = bitmap.getPixel(x, y)
                assertTrue(
                    "Pixel ($x, $y) is not neutral: ${Color.red(color)},${Color.green(color)},${Color.blue(color)}",
                    Color.red(color) == Color.green(color) && Color.green(color) == Color.blue(color),
                )
            }
        }
    }

    private fun minLuminance(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Double {
        var minimum = Double.MAX_VALUE
        for (y in top until bottom) {
            for (x in left until right) {
                minimum = minOf(minimum, luminance(bitmap.getPixel(x, y)))
            }
        }
        return minimum
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
