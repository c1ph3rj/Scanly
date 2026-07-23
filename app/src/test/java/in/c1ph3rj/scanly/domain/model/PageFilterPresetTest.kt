package `in`.c1ph3rj.scanly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PageFilterPresetTest {
    @Test
    fun autoPresetRoundTripsThroughStorage() {
        assertEquals(PageFilterPreset.AUTO, PageFilterPreset.fromStorage(PageFilterPreset.AUTO.storageValue))
        assertEquals(
            PageFilterPreset.SHADOW_REDUCTION,
            PageFilterPreset.fromStorage(PageFilterPreset.SHADOW_REDUCTION.storageValue),
        )
        assertEquals(
            PageFilterPreset.ID_NATURAL,
            PageFilterPreset.fromStorage(PageFilterPreset.ID_NATURAL.storageValue),
        )
        assertEquals(
            PageFilterPreset.ID_CLEAR,
            PageFilterPreset.fromStorage(PageFilterPreset.ID_CLEAR.storageValue),
        )
        assertEquals(
            PageFilterPreset.ID_PORTRAIT,
            PageFilterPreset.fromStorage(PageFilterPreset.ID_PORTRAIT.storageValue),
        )
        assertEquals(
            PageFilterPreset.ID_TEXT,
            PageFilterPreset.fromStorage(PageFilterPreset.ID_TEXT.storageValue),
        )
    }

    @Test
    fun unknownPresetStillFallsBackToOriginal() {
        assertEquals(PageFilterPreset.ORIGINAL, PageFilterPreset.fromStorage("not_a_filter"))
    }
}
