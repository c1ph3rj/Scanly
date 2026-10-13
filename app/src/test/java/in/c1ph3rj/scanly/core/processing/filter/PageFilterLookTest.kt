package `in`.c1ph3rj.scanly.core.processing.filter

import `in`.c1ph3rj.scanly.core.processing.PageImageProfile
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageFilterLookTest {
    private val reference = 1_600

    @Test
    fun zeroIntensityGivesIdentityToneCurveForMagicColorAndGrayscale() {
        listOf(PageFilterPreset.MAGIC_COLOR, PageFilterPreset.GRAYSCALE).forEach { preset ->
            val params = resolve(preset, profile(), intensity = 0.0)

            assertEquals("$preset blackPoint", 0.0, params.blackPoint, 1e-9)
            assertEquals("$preset whitePoint", 255.0, params.whitePoint, 1e-9)
            assertEquals("$preset gamma", 1.0, params.gamma, 1e-9)
            assertEquals("$preset vibrance", 0.0, params.vibrance, 1e-9)
            assertEquals("$preset sharpenAmount", 0.0, params.sharpenAmount, 1e-9)
        }
    }

    @Test
    fun raisingIntensityStrengthensMagicColorMonotonically() {
        val looks = listOf(0.0, 0.5, 1.0, 1.5).map {
            resolve(PageFilterPreset.MAGIC_COLOR, profile(ink = 40.0, white = 235.0), intensity = it)
        }

        looks.zipWithNext().forEach { (weaker, stronger) ->
            assertTrue("gamma should rise", stronger.gamma > weaker.gamma)
            assertTrue("white point should drop", stronger.whitePoint < weaker.whitePoint)
            assertTrue("black point should rise", stronger.blackPoint > weaker.blackPoint)
            assertTrue("vibrance should rise", stronger.vibrance > weaker.vibrance)
        }
    }

    @Test
    fun intensityIsClampedToZeroThroughMaximum() {
        val profile = profile()

        listOf(PageFilterPreset.MAGIC_COLOR, PageFilterPreset.GRAYSCALE, PageFilterPreset.FADED).forEach { preset ->
            assertEquals(
                "$preset below zero",
                resolve(preset, profile, intensity = 0.0),
                resolve(preset, profile, intensity = -2.0),
            )
            assertEquals(
                "$preset above maximum",
                resolve(preset, profile, intensity = MAX_FILTER_INTENSITY),
                resolve(preset, profile, intensity = MAX_FILTER_INTENSITY + 3.0),
            )
        }
        assertEquals(1.5, MAX_FILTER_INTENSITY, 1e-9)
    }

    @Test
    fun tonalParamsDoNotDependOnRenderSize() {
        val profile = profile(ink = 60.0, white = 228.0, noise = 5.0)

        PageFilterPreset.entries.forEach { preset ->
            val small = resolve(preset, profile, intensity = 1.0, renderLongestEdge = 320)
            val medium = resolve(preset, profile, intensity = 1.0, renderLongestEdge = 1_600)
            val large = resolve(preset, profile, intensity = 1.0, renderLongestEdge = 2_800)

            listOf(small, large).forEach { other ->
                assertEquals("$preset blackPoint", medium.blackPoint, other.blackPoint, 1e-9)
                assertEquals("$preset whitePoint", medium.whitePoint, other.whitePoint, 1e-9)
                assertEquals("$preset gamma", medium.gamma, other.gamma, 1e-9)
                assertEquals("$preset vibrance", medium.vibrance, other.vibrance, 1e-9)
                assertEquals("$preset binaryThreshold", medium.binaryThreshold, other.binaryThreshold, 1e-9)
            }
        }
    }

    @Test
    fun pixelKernelsGrowWithRenderEdge() {
        val profile = profile()

        val sharpSmall = resolve(PageFilterPreset.MAGIC_COLOR, profile, renderLongestEdge = 320)
        val sharpMedium = resolve(PageFilterPreset.MAGIC_COLOR, profile, renderLongestEdge = 1_600)
        val sharpLarge = resolve(PageFilterPreset.MAGIC_COLOR, profile, renderLongestEdge = 2_800)
        assertTrue(sharpSmall.sharpenSigma < sharpMedium.sharpenSigma)
        assertTrue(sharpMedium.sharpenSigma < sharpLarge.sharpenSigma)
        assertTrue(sharpSmall.denoiseSigma < sharpMedium.denoiseSigma)
        assertTrue(sharpMedium.denoiseSigma < sharpLarge.denoiseSigma)

        val despeckleSmall = resolve(PageFilterPreset.BLACK_AND_WHITE, profile, renderLongestEdge = 320)
        val despeckleMedium = resolve(PageFilterPreset.BLACK_AND_WHITE, profile, renderLongestEdge = 1_600)
        val despeckleLarge = resolve(PageFilterPreset.BLACK_AND_WHITE, profile, renderLongestEdge = 2_800)
        assertTrue(despeckleSmall.despeckleMinArea < despeckleMedium.despeckleMinArea)
        assertTrue(despeckleMedium.despeckleMinArea < despeckleLarge.despeckleMinArea)
    }

    @Test
    fun whitePointStaysAtLeastSixteenLevelsAboveBlackPointForEveryPresetAndIntensity() {
        val profiles: List<PageImageProfile?> = listOf(
            null,
            profile(),
            profile(ink = 220.0, white = 175.0),
            profile(ink = 0.0, white = 252.0),
            profile(ink = 230.0, white = 170.0, paper = 150.0),
        )

        PageFilterPreset.entries.forEach { preset ->
            profiles.forEach { profile ->
                listOf(0.0, 0.5, 1.0, 1.5).forEach { intensity ->
                    listOf(320, 2_800).forEach { edge ->
                        val params = resolve(preset, profile, intensity, edge)
                        assertTrue(
                            "$preset intensity=$intensity edge=$edge: white ${params.whitePoint} black ${params.blackPoint}",
                            params.whitePoint - params.blackPoint >= 16.0,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun blackAndWhiteThresholdSitsBetweenInkAndPaperAndRisesWithIntensity() {
        val profile = profile(ink = 40.0, white = 235.0)

        val thresholds = listOf(0.0, 0.5, 1.0, 1.5).map {
            resolve(PageFilterPreset.BLACK_AND_WHITE, profile, intensity = it).binaryThreshold
        }

        thresholds.forEach { threshold ->
            assertTrue("threshold $threshold should be above ink", threshold > 40.0)
            assertTrue("threshold $threshold should be below paper", threshold < 235.0)
        }
        thresholds.zipWithNext().forEach { (weaker, stronger) ->
            assertTrue("threshold should rise with intensity", stronger > weaker)
        }
    }

    @Test
    fun fadedPullsFaintInkTowardBlack() {
        val faintInk = profile(ink = 140.0, white = 235.0)

        val faded = resolve(PageFilterPreset.FADED, faintInk, intensity = 1.0)
        val fadedCurve = PageFilterOperators.toneCurve(faded.blackPoint, faded.whitePoint, faded.gamma)
        val magicCurve = resolve(PageFilterPreset.MAGIC_COLOR, faintInk, intensity = 1.0).let {
            PageFilterOperators.toneCurve(it.blackPoint, it.whitePoint, it.gamma)
        }

        assertTrue(
            "FADED black point ${faded.blackPoint} should sit well above faint ink 140",
            faded.blackPoint >= 140.0 + 20.0,
        )
        assertEquals("faint ink should be pulled to black", 0, fadedCurve[140].toInt())
        assertTrue(
            "MAGIC_COLOR should keep faint ink above black on the same page",
            magicCurve[140].toInt() > 0,
        )
    }

    @Test
    fun noisyCapturesGetLessSharpeningThanCleanOnes() {
        val clean = profile(noise = 2.0)
        val noisy = profile(noise = 12.0)

        listOf(
            PageFilterPreset.MAGIC_COLOR,
            PageFilterPreset.WHITEBOARD,
            PageFilterPreset.NATURAL_COLOR,
            PageFilterPreset.GRAYSCALE,
            PageFilterPreset.FADED,
        ).forEach { preset ->
            val cleanSharpen = resolve(preset, clean, intensity = 1.0).sharpenAmount
            val noisySharpen = resolve(preset, noisy, intensity = 1.0).sharpenAmount

            assertTrue("$preset noisy sharpen $noisySharpen should be below clean $cleanSharpen", noisySharpen < cleanSharpen)
            assertTrue("$preset noisy sharpen should stay positive", noisySharpen > 0.0)
        }
    }

    @Test
    fun eachPresetMapsToExpectedRenderMode() {
        val profile = profile()

        assertEquals(PageFilterRenderMode.Color, resolve(PageFilterPreset.MAGIC_COLOR, profile).mode)
        assertEquals(PageFilterRenderMode.Color, resolve(PageFilterPreset.WHITEBOARD, profile).mode)
        assertEquals(PageFilterRenderMode.Natural, resolve(PageFilterPreset.NATURAL_COLOR, profile).mode)
        assertEquals(PageFilterRenderMode.Gray, resolve(PageFilterPreset.GRAYSCALE, profile).mode)
        assertEquals(PageFilterRenderMode.Gray, resolve(PageFilterPreset.FADED, profile).mode)
        assertEquals(PageFilterRenderMode.Binary, resolve(PageFilterPreset.BLACK_AND_WHITE, profile).mode)
    }

    private fun resolve(
        preset: PageFilterPreset,
        profile: PageImageProfile?,
        intensity: Double = DEFAULT_FILTER_INTENSITY,
        renderLongestEdge: Int = reference,
    ): PageFilterRenderParams = PageFilterLook.resolve(
        preset = preset,
        profile = profile,
        intensity = intensity,
        renderLongestEdge = renderLongestEdge,
    )

    private fun profile(
        ink: Double = 40.0,
        white: Double = 235.0,
        paper: Double = 246.0,
        noise: Double = 4.0,
    ): PageImageProfile = PageImageProfile(
        brightness = 188.0,
        contrast = 40.0,
        shadowRatio = 0.03,
        highlightRatio = 0.15,
        saturation = 12.0,
        edgeDensity = 0.07,
        sharpness = 48.0,
        longestEdge = 1_800,
        noise = noise,
        tonePaperLevel = paper,
        toneWhitePoint = white,
        toneInkLevel = ink,
    )
}
