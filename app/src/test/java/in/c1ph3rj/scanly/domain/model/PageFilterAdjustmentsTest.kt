package `in`.c1ph3rj.scanly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageFilterAdjustmentsTest {
    @Test
    fun defaultIsDefault() {
        assertTrue(PageFilterAdjustments.Default.isDefault)
    }

    @Test
    fun nonZeroIsNotDefault() {
        assertFalse(PageFilterAdjustments(brightness = 0.2f).isDefault)
    }

    @Test
    fun fromUiPercentsMapsAndClamps() {
        val adjustments = PageFilterAdjustments.fromUiPercents(
            brightness = 150,
            contrast = -200,
            saturation = 25,
            sharpness = 110,
            highlights = 75,
            shadows = -25,
            warmth = 40,
            vignette = 120,
        )
        assertEquals(1f, adjustments.brightness)
        assertEquals(-1f, adjustments.contrast)
        assertEquals(0.25f, adjustments.saturation)
        assertEquals(1f, adjustments.sharpness)
        assertEquals(0.75f, adjustments.highlights)
        assertEquals(-0.25f, adjustments.shadows)
        assertEquals(0.4f, adjustments.warmth)
        assertEquals(1f, adjustments.vignette)
    }

    @Test
    fun percentRoundTrip() {
        val adjustments = PageFilterAdjustments(
            brightness = 0.35f,
            contrast = -0.1f,
            saturation = 0f,
            sharpness = 0.5f,
            highlights = 0.2f,
            shadows = -0.3f,
            warmth = 0.15f,
            vignette = 0.4f,
        )
        assertEquals(35, adjustments.brightnessPercent())
        assertEquals(-10, adjustments.contrastPercent())
        assertEquals(0, adjustments.saturationPercent())
        assertEquals(50, adjustments.sharpnessPercent())
        assertEquals(20, adjustments.highlightsPercent())
        assertEquals(-30, adjustments.shadowsPercent())
        assertEquals(15, adjustments.warmthPercent())
        assertEquals(40, adjustments.vignettePercent())
    }
}
