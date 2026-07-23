package `in`.c1ph3rj.scanly.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdCardPairPdfLayoutTest {
    @Test
    fun landscapeIdPairFillsContentWidthWhenHeightAllows() {
        // A4-ish content box with typical landscape ID aspect (~1.6:1).
        val placement = IdCardPairPdfLayout.place(
            contentWidth = 547f,
            contentHeight = 794f,
            contentLeft = 24f,
            contentTop = 24f,
            frontWidth = 1600f,
            frontHeight = 1000f,
            backWidth = 1600f,
            backHeight = 1000f,
            gap = 16f,
        )

        assertEquals(547f, placement.frontWidth, 0.01f)
        assertEquals(547f, placement.backWidth, 0.01f)
        assertEquals(placement.frontWidth * (1000f / 1600f), placement.frontHeight, 0.01f)
        assertEquals(placement.backWidth * (1000f / 1600f), placement.backHeight, 0.01f)
        assertEquals(24f, placement.frontLeft, 0.01f)
        // Pair is vertically centered as a block.
        val blockHeight = placement.frontHeight + 16f + placement.backHeight
        assertEquals(24f + (794f - blockHeight) / 2f, placement.frontTop, 0.01f)
        assertEquals(
            placement.frontTop + placement.frontHeight + 16f,
            placement.backTop,
            0.01f,
        )
    }

    @Test
    fun tallCardsScaleDownToFitStackedHeight() {
        // Portrait scans that would overflow if full width were used.
        val placement = IdCardPairPdfLayout.place(
            contentWidth = 500f,
            contentHeight = 400f,
            contentLeft = 0f,
            contentTop = 0f,
            frontWidth = 100f,
            frontHeight = 200f,
            backWidth = 100f,
            backHeight = 200f,
            gap = 20f,
        )

        // heightPerWidth = 2 + 2 = 4; max width by height = (400-20)/4 = 95
        assertEquals(95f, placement.frontWidth, 0.01f)
        assertEquals(190f, placement.frontHeight, 0.01f)
        assertEquals(190f, placement.backHeight, 0.01f)
        val blockBottom = placement.backTop + placement.backHeight
        assertTrue(blockBottom <= 400f + 0.01f)
        assertTrue(placement.frontWidth < 500f)
    }

    @Test
    fun differentAspectsKeepSharedWidthAndSeparateHeights() {
        val placement = IdCardPairPdfLayout.place(
            contentWidth = 400f,
            contentHeight = 800f,
            contentLeft = 10f,
            contentTop = 10f,
            frontWidth = 200f,
            frontHeight = 100f,
            backWidth = 300f,
            backHeight = 100f,
            gap = 10f,
        )

        assertEquals(placement.frontWidth, placement.backWidth, 0.01f)
        assertEquals(400f, placement.frontWidth, 0.01f)
        assertEquals(200f, placement.frontHeight, 0.01f)
        assertEquals(400f * (100f / 300f), placement.backHeight, 0.01f)
        assertEquals(placement.frontLeft, placement.backLeft, 0.01f)
    }
}
