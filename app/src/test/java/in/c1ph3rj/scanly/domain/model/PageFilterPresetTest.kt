package `in`.c1ph3rj.scanly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageFilterPresetTest {
    @Test
    fun everyPresetRoundTripsThroughStorage() {
        PageFilterPreset.entries.forEach { preset ->
            assertEquals(preset, PageFilterPreset.fromStorage(preset.storageValue))
        }
    }

    @Test
    fun storageValuesAreUnique() {
        val values = PageFilterPreset.entries.map { it.storageValue }
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun legacyPresetsMapToClosestReplacement() {
        assertEquals(PageFilterPreset.NATURAL_COLOR, PageFilterPreset.fromStorage("enhanced_color"))
        assertEquals(PageFilterPreset.MAGIC_COLOR, PageFilterPreset.fromStorage("shadow_reduction"))
        assertEquals(PageFilterPreset.GRAYSCALE, PageFilterPreset.fromStorage("clean"))
        assertEquals(PageFilterPreset.FADED, PageFilterPreset.fromStorage("soft_black_and_white"))
        assertEquals(PageFilterPreset.FADED, PageFilterPreset.fromStorage("receipt"))
    }

    @Test
    fun legacyStorageMigrationsCoverRemovedPresetsOnly() {
        val migrations = PageFilterPreset.legacyStorageMigrations
        assertEquals(
            mapOf(
                "enhanced_color" to "natural_color",
                "shadow_reduction" to "magic_color",
                "clean" to "grayscale",
                "soft_black_and_white" to "faded",
                "receipt" to "faded",
            ),
            migrations,
        )
        assertTrue(
            migrations.keys.none { legacy ->
                PageFilterPreset.entries.any { it.storageValue == legacy }
            },
        )
    }

    @Test
    fun unknownPresetStillFallsBackToOriginal() {
        assertEquals(PageFilterPreset.ORIGINAL, PageFilterPreset.fromStorage("not_a_filter"))
    }
}
