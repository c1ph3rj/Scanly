package `in`.c1ph3rj.scanly.feature.tools.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GridDragSelectTest {
    private val order = (0..9).toList()

    @Test
    fun forwardDragSelectsAnchorThroughCurrentInclusive() {
        val result = applyDragSelection(
            initial = emptySet(),
            order = order,
            anchor = 2,
            current = 5,
            selecting = true,
        )

        assertEquals(setOf(2, 3, 4, 5), result)
    }

    @Test
    fun backwardDragSelectsSameRangeAsForwardDrag() {
        val result = applyDragSelection(
            initial = emptySet(),
            order = order,
            anchor = 5,
            current = 2,
            selecting = true,
        )

        assertEquals(setOf(2, 3, 4, 5), result)
    }

    @Test
    fun deselectingRemovesRangeAndKeepsOthersFromInitial() {
        val result = applyDragSelection(
            initial = setOf(1, 2, 3, 4, 5, 6),
            order = order,
            anchor = 2,
            current = 4,
            selecting = false,
        )

        assertEquals(setOf(1, 5, 6), result)
    }

    @Test
    fun itemsOutsideRangeKeepTheirInitialState() {
        val result = applyDragSelection(
            initial = setOf(0, 9),
            order = order,
            anchor = 3,
            current = 5,
            selecting = true,
        )

        assertEquals(setOf(0, 3, 4, 5, 9), result)
    }

    @Test
    fun draggingBackTowardAnchorShrinksSelectionFromSameInitial() {
        val initial = emptySet<Int>()

        val wide = applyDragSelection(initial, order, anchor = 3, current = 5, selecting = true)
        val narrow = applyDragSelection(initial, order, anchor = 3, current = 4, selecting = true)

        assertEquals(setOf(3, 4, 5), wide)
        assertEquals(setOf(3, 4), narrow)
        assertFalse(narrow.contains(5))
    }

    @Test
    fun anchorEqualToCurrentSelectsOnlyThatItem() {
        val result = applyDragSelection(
            initial = emptySet(),
            order = order,
            anchor = 4,
            current = 4,
            selecting = true,
        )

        assertEquals(setOf(4), result)
    }

    @Test
    fun anchorEqualToCurrentDeselectsOnlyThatItem() {
        val result = applyDragSelection(
            initial = setOf(4, 7),
            order = order,
            anchor = 4,
            current = 4,
            selecting = false,
        )

        assertEquals(setOf(7), result)
    }

    @Test
    fun rangeUsesPositionsInOrderWhenOrderIsNotZeroBased() {
        val sparseOrder = listOf(4, 7, 9, 12)

        val result = applyDragSelection(
            initial = emptySet(),
            order = sparseOrder,
            anchor = 7,
            current = 12,
            selecting = true,
        )

        assertEquals(setOf(7, 9, 12), result)
    }

    @Test
    fun anchorMissingFromOrderReturnsInitialUnchanged() {
        val initial = setOf(1)

        val result = applyDragSelection(
            initial = initial,
            order = order,
            anchor = 99,
            current = 5,
            selecting = true,
        )

        assertEquals(initial, result)
    }

    @Test
    fun currentMissingFromOrderReturnsInitialUnchanged() {
        val initial = setOf(1)

        val result = applyDragSelection(
            initial = initial,
            order = order,
            anchor = 2,
            current = 99,
            selecting = true,
        )

        assertEquals(initial, result)
    }

    @Test
    fun autoScrollIsZeroInMiddleOfViewport() {
        assertEquals(0f, dragAutoScrollSpeed(pointerY = 500f, viewportHeight = 1000f, edge = 100f, maxSpeed = 18f), 0.001f)
    }

    @Test
    fun autoScrollAtTopEdgeIsFullNegativeSpeed() {
        assertEquals(-18f, dragAutoScrollSpeed(pointerY = 0f, viewportHeight = 1000f, edge = 100f, maxSpeed = 18f), 0.001f)
    }

    @Test
    fun autoScrollHalfwayIntoTopZoneIsHalfNegativeSpeed() {
        assertEquals(-9f, dragAutoScrollSpeed(pointerY = 50f, viewportHeight = 1000f, edge = 100f, maxSpeed = 18f), 0.001f)
    }

    @Test
    fun autoScrollAtBottomEdgeIsFullPositiveSpeed() {
        assertEquals(18f, dragAutoScrollSpeed(pointerY = 1000f, viewportHeight = 1000f, edge = 100f, maxSpeed = 18f), 0.001f)
    }

    @Test
    fun autoScrollHalfwayIntoBottomZoneIsHalfPositiveSpeed() {
        assertEquals(9f, dragAutoScrollSpeed(pointerY = 950f, viewportHeight = 1000f, edge = 100f, maxSpeed = 18f), 0.001f)
    }

    @Test
    fun autoScrollClampsWhenPointerAboveViewport() {
        assertEquals(-18f, dragAutoScrollSpeed(pointerY = -200f, viewportHeight = 1000f, edge = 100f, maxSpeed = 18f), 0.001f)
    }

    @Test
    fun autoScrollClampsWhenPointerBelowViewport() {
        assertEquals(18f, dragAutoScrollSpeed(pointerY = 1500f, viewportHeight = 1000f, edge = 100f, maxSpeed = 18f), 0.001f)
    }

    @Test
    fun autoScrollIsZeroWhenEdgeIsNotPositive() {
        assertEquals(0f, dragAutoScrollSpeed(pointerY = 0f, viewportHeight = 1000f, edge = 0f, maxSpeed = 18f), 0.001f)
        assertEquals(0f, dragAutoScrollSpeed(pointerY = 1000f, viewportHeight = 1000f, edge = -5f, maxSpeed = 18f), 0.001f)
    }

    @Test
    fun autoScrollIsZeroWhenViewportHeightIsNotPositive() {
        assertEquals(0f, dragAutoScrollSpeed(pointerY = 0f, viewportHeight = 0f, edge = 100f, maxSpeed = 18f), 0.001f)
    }
}
