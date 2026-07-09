package `in`.c1ph3rj.scanly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageFilterPresetTest {
    @Test
    fun fromStorageRoundTripsKnownValues() {
        assertEquals(PageFilterPreset.AUTO, PageFilterPreset.fromStorage(PageFilterPreset.AUTO.storageValue))
        assertEquals(
            PageFilterPreset.SHADOW_REDUCTION,
            PageFilterPreset.fromStorage(PageFilterPreset.SHADOW_REDUCTION.storageValue),
        )
        assertEquals(PageFilterPreset.PHOTO, PageFilterPreset.fromStorage(PageFilterPreset.PHOTO.storageValue))
        assertEquals(
            PageFilterPreset.HIGH_CONTRAST,
            PageFilterPreset.fromStorage(PageFilterPreset.HIGH_CONTRAST.storageValue),
        )
    }

    @Test
    fun fromStorageFallsBackToOriginalForUnknownValues() {
        assertEquals(PageFilterPreset.ORIGINAL, PageFilterPreset.fromStorage("not_a_filter"))
    }
}

class PageFilterAdjustmentsTest {
    @Test
    fun defaultIsMarkedAsDefault() {
        assertTrue(PageFilterAdjustments.Default.isDefault)
    }

    @Test
    fun sanitizeClampsOutOfRangeValues() {
        val sanitized = PageFilterAdjustments(
            intensity = 2.5f,
            brightness = -3f,
            contrast = 4f,
            shadows = -0.2f,
            details = 1.8f,
            threshold = -1f,
        ).sanitized()

        assertEquals(1.0f, sanitized.intensity)
        assertEquals(-1.0f, sanitized.brightness)
        assertEquals(1.0f, sanitized.contrast)
        assertEquals(0.0f, sanitized.shadows)
        assertEquals(1.0f, sanitized.details)
        assertEquals(0.0f, sanitized.threshold)
        assertFalse(sanitized.isDefault)
    }

    @Test
    fun supportsThresholdOnlyForBinaryLikePresets() {
        val adjustments = PageFilterAdjustments.Default
        assertTrue(adjustments.supportsThresholdControl(PageFilterPreset.BLACK_AND_WHITE))
        assertTrue(adjustments.supportsThresholdControl(PageFilterPreset.RECEIPT))
        assertTrue(adjustments.supportsThresholdControl(PageFilterPreset.HIGH_CONTRAST))
        assertFalse(adjustments.supportsThresholdControl(PageFilterPreset.ENHANCED_COLOR))
        assertFalse(adjustments.supportsThresholdControl(PageFilterPreset.GRAYSCALE))
    }
}
