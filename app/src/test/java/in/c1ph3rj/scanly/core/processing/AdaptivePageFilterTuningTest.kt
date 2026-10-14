package `in`.c1ph3rj.scanly.core.processing

import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePageFilterTuningTest {
    @Test
    fun missingProfileFallsBackToMagicColor() {
        assertEquals(PageFilterPreset.MAGIC_COLOR, AdaptivePageFilterTuning.automatic(null))
    }

    @Test
    fun photoLikePageUsesNaturalColor() {
        val photo = profile(
            colorRatio = 0.30,
            saturation = 55.0,
            textDensity = 0.005,
        )

        assertEquals(PageFilterPreset.NATURAL_COLOR, AdaptivePageFilterTuning.automatic(photo))
    }

    @Test
    fun longMonochromeReceiptUsesFaded() {
        val receipt = profile(
            aspectRatio = 2.4,
            textDensity = 0.08,
            colorRatio = 0.004,
            saturation = 8.0,
        )

        assertEquals(PageFilterPreset.FADED, AdaptivePageFilterTuning.automatic(receipt))
    }

    @Test
    fun textPageWithFaintInkUsesFaded() {
        val faintText = profile(
            textDensity = 0.04,
            colorRatio = 0.004,
            saturation = 8.0,
            toneInkLevel = 130.0,
        )

        assertEquals(PageFilterPreset.FADED, AdaptivePageFilterTuning.automatic(faintText))
    }

    @Test
    fun colouredPageUsesMagicColor() {
        val coloredPage = profile(
            textDensity = 0.04,
            colorRatio = 0.05,
            saturation = 36.0,
        )

        assertEquals(PageFilterPreset.MAGIC_COLOR, AdaptivePageFilterTuning.automatic(coloredPage))
    }

    @Test
    fun plainMonochromeTextPageUsesGrayscale() {
        val plainText = profile(
            textDensity = 0.04,
            colorRatio = 0.002,
            saturation = 5.0,
            toneInkLevel = 30.0,
        )

        assertEquals(PageFilterPreset.GRAYSCALE, AdaptivePageFilterTuning.automatic(plainText))
    }

    @Test
    fun warmLightWithoutRealChromaIsNotTreatedAsColor() {
        val warmMonochromePage = profile(
            textDensity = 0.05,
            colorRatio = 0.004,
            saturation = 36.0,
        )

        assertEquals(PageFilterPreset.GRAYSCALE, AdaptivePageFilterTuning.automatic(warmMonochromePage))
    }

    @Test
    fun autoNeverResolvesToAutoOrOriginal() {
        val allowed = setOf(
            PageFilterPreset.MAGIC_COLOR,
            PageFilterPreset.NATURAL_COLOR,
            PageFilterPreset.GRAYSCALE,
            PageFilterPreset.FADED,
        )

        for (colorRatio in listOf(0.0, 0.004, 0.012, 0.03, 0.3)) {
            for (textDensity in listOf(0.0, 0.005, 0.02, 0.08)) {
                for (saturation in listOf(0.0, 10.0, 40.0)) {
                    for (aspectRatio in listOf(1.0, 1.4, 2.4)) {
                        for (ink in listOf(10.0, 60.0, 130.0, 220.0)) {
                            for (edgeDensity in listOf(0.005, 0.07)) {
                                val resolved = AdaptivePageFilterTuning.automatic(
                                    profile(
                                        colorRatio = colorRatio,
                                        textDensity = textDensity,
                                        saturation = saturation,
                                        aspectRatio = aspectRatio,
                                        toneInkLevel = ink,
                                        edgeDensity = edgeDensity,
                                    ),
                                )
                                assertTrue(
                                    "Auto resolved to $resolved for colorRatio=$colorRatio textDensity=$textDensity " +
                                        "saturation=$saturation aspect=$aspectRatio ink=$ink edge=$edgeDensity",
                                    resolved in allowed,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun screenLikeProfileAlwaysRoutesToScreen() {
        val screenLikeProfiles = listOf(
            profile(screenLikely = true),
            profile(screenLikely = true, colorRatio = 0.30, saturation = 55.0, textDensity = 0.005),
            profile(
                screenLikely = true,
                aspectRatio = 2.4,
                textDensity = 0.08,
                colorRatio = 0.004,
                saturation = 8.0,
            ),
            profile(screenLikely = true, textDensity = 0.04, toneInkLevel = 130.0),
            profile(screenLikely = true, textDensity = 0.04, colorRatio = 0.05, saturation = 36.0),
        )

        screenLikeProfiles.forEach { screenLike ->
            assertEquals(PageFilterPreset.SCREEN, AdaptivePageFilterTuning.automatic(screenLike))
        }
    }

    @Test
    fun profilesThatAreNotScreenLikeKeepExistingRouting() {
        assertEquals(
            PageFilterPreset.NATURAL_COLOR,
            AdaptivePageFilterTuning.automatic(
                profile(screenLikely = false, colorRatio = 0.30, saturation = 55.0, textDensity = 0.005),
            ),
        )
        assertEquals(
            PageFilterPreset.FADED,
            AdaptivePageFilterTuning.automatic(
                profile(screenLikely = false, aspectRatio = 2.4, textDensity = 0.08, colorRatio = 0.004, saturation = 8.0),
            ),
        )
        assertEquals(
            PageFilterPreset.MAGIC_COLOR,
            AdaptivePageFilterTuning.automatic(
                profile(screenLikely = false, textDensity = 0.04, colorRatio = 0.05, saturation = 36.0),
            ),
        )
        assertEquals(
            PageFilterPreset.GRAYSCALE,
            AdaptivePageFilterTuning.automatic(
                profile(screenLikely = false, textDensity = 0.04, colorRatio = 0.002, saturation = 5.0, toneInkLevel = 30.0),
            ),
        )
    }

    private fun profile(
        brightness: Double = 176.0,
        contrast: Double = 38.0,
        shadowRatio: Double = 0.04,
        saturation: Double = 14.0,
        edgeDensity: Double = 0.07,
        colorRatio: Double = 0.008,
        textDensity: Double = 0.04,
        aspectRatio: Double = 1.4,
        toneInkLevel: Double = 40.0,
        longestEdge: Int = 1_800,
        screenLikely: Boolean = false,
    ): PageImageProfile = PageImageProfile(
        brightness = brightness,
        contrast = contrast,
        shadowRatio = shadowRatio,
        highlightRatio = 0.18,
        saturation = saturation,
        edgeDensity = edgeDensity,
        sharpness = 48.0,
        longestEdge = longestEdge,
        textDensity = textDensity,
        colorRatio = colorRatio,
        aspectRatio = aspectRatio,
        toneInkLevel = toneInkLevel,
        screenLikely = screenLikely,
    )
}
