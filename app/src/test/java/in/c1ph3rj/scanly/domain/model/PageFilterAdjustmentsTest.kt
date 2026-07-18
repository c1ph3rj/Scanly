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
        )
        assertEquals(1f, adjustments.brightness)
        assertEquals(-1f, adjustments.contrast)
        assertEquals(0.25f, adjustments.saturation)
        assertEquals(1f, adjustments.sharpness)
    }

    @Test
    fun percentRoundTrip() {
        val adjustments = PageFilterAdjustments(
            brightness = 0.35f,
            contrast = -0.1f,
            saturation = 0f,
            sharpness = 0.5f,
        )
        assertEquals(35, adjustments.brightnessPercent())
        assertEquals(-10, adjustments.contrastPercent())
        assertEquals(0, adjustments.saturationPercent())
        assertEquals(50, adjustments.sharpnessPercent())
    }
}
