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
    fun intensityDefaultsToOneAndIsPartOfDefault() {
        assertEquals(1f, PageFilterAdjustments.Default.intensity)
        assertEquals(1f, PageFilterAdjustments().intensity)
        assertFalse(PageFilterAdjustments(intensity = 0.5f).isDefault)
        assertFalse(PageFilterAdjustments(intensity = 1.2f).isDefault)
    }

    @Test
    fun sanitizedClampsIntensityToRange() {
        assertEquals(1.5f, PageFilterAdjustments(intensity = 3f).sanitized().intensity)
        assertEquals(0f, PageFilterAdjustments(intensity = -0.4f).sanitized().intensity)
        assertEquals(0.8f, PageFilterAdjustments(intensity = 0.8f).sanitized().intensity)
    }

    @Test
    fun fromUiPercentsMapsAndClamps() {
        val adjustments = PageFilterAdjustments.fromUiPercents(
            brightness = 150,
            contrast = -200,
            saturation = 25,
            sharpness = 110,
        )
        assertEquals(1f, adjustments.brightness)
        assertEquals(-1f, adjustments.contrast)
        assertEquals(0.25f, adjustments.saturation)
        assertEquals(1f, adjustments.sharpness)
        assertEquals(1f, adjustments.intensity)
    }

    @Test
    fun fromUiPercentsMapsAndClampsIntensity() {
        assertEquals(0.75f, PageFilterAdjustments.fromUiPercents(0, 0, 0, 0, intensity = 75).intensity)
        assertEquals(1.5f, PageFilterAdjustments.fromUiPercents(0, 0, 0, 0, intensity = 200).intensity)
        assertEquals(0f, PageFilterAdjustments.fromUiPercents(0, 0, 0, 0, intensity = -10).intensity)
    }

    @Test
    fun percentRoundTrip() {
        val adjustments = PageFilterAdjustments(
            brightness = 0.35f,
            contrast = -0.1f,
            saturation = 0f,
            sharpness = 0.5f,
            intensity = 0.75f,
        )
        assertEquals(35, adjustments.brightnessPercent())
        assertEquals(-10, adjustments.contrastPercent())
        assertEquals(0, adjustments.saturationPercent())
        assertEquals(50, adjustments.sharpnessPercent())
        assertEquals(75, adjustments.intensityPercent())
    }

    @Test
    fun intensityPercentIsClampedToUiRange() {
        assertEquals(150, PageFilterAdjustments(intensity = 1.5f).intensityPercent())
        assertEquals(150, PageFilterAdjustments(intensity = 2f).intensityPercent())
        assertEquals(0, PageFilterAdjustments(intensity = -1f).intensityPercent())
        assertEquals(33, PageFilterAdjustments(intensity = 0.333f).intensityPercent())
    }
}
