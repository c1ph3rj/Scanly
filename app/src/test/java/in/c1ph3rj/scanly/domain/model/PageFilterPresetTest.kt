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
    }

    @Test
    fun unknownPresetStillFallsBackToOriginal() {
        assertEquals(PageFilterPreset.ORIGINAL, PageFilterPreset.fromStorage("not_a_filter"))
    }
}
