package `in`.c1ph3rj.scanly.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewImageSizerTest {

    @Test
    fun estimatedTargetsUseSharedDecodeBuckets() {
        assertEquals(384, PreviewImageSizer.estimateTargetPx(PreviewDisplaySize.COMPACT, density = 3f))
        assertEquals(768, PreviewImageSizer.estimateTargetPx(PreviewDisplaySize.CARD, density = 3f))
        assertEquals(1_536, PreviewImageSizer.estimateTargetPx(PreviewDisplaySize.DETAIL, density = 3f))
    }

    @Test
    fun measuredTargetsRoundUpWithoutExceedingDisplayLimit() {
        assertEquals(
            192,
            PreviewImageSizer.targetPxForContainer(
                widthPx = 100,
                heightPx = 80,
                size = PreviewDisplaySize.CARD,
                density = 3f,
            ),
        )
        assertEquals(
            768,
            PreviewImageSizer.targetPxForContainer(
                widthPx = 400,
                heightPx = 300,
                size = PreviewDisplaySize.CARD,
                density = 3f,
            ),
        )
        assertEquals(
            768,
            PreviewImageSizer.targetPxForContainer(
                widthPx = 1_200,
                heightPx = 900,
                size = PreviewDisplaySize.CARD,
                density = 3f,
            ),
        )
    }
}
