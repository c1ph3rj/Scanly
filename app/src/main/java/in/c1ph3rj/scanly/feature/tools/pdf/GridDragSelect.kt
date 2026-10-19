package `in`.c1ph3rj.scanly.feature.tools.pdf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Selection after dragging from [anchor] to [current] (both item ids from [order]).
 *
 * Everything between the two, in grid order, is added when [selecting] or removed otherwise;
 * items outside the range keep their state from [initial], so dragging back over cells
 * restores them — the Google Photos behaviour.
 */
fun applyDragSelection(
    initial: Set<Int>,
    order: List<Int>,
    anchor: Int,
    current: Int,
    selecting: Boolean,
): Set<Int> {
    val from = order.indexOf(anchor)
    val to = order.indexOf(current)
    if (from < 0 || to < 0) return initial
    val range = order.subList(minOf(from, to), maxOf(from, to) + 1)
    return if (selecting) initial + range else initial - range.toSet()
}

/** Speed for edge auto-scroll: 0 in the middle, ramping to ±[maxSpeed] at the very edge. */
fun dragAutoScrollSpeed(pointerY: Float, viewportHeight: Float, edge: Float, maxSpeed: Float): Float = when {
    edge <= 0f || viewportHeight <= 0f -> 0f
    pointerY < edge -> -maxSpeed * ((edge - pointerY) / edge).coerceIn(0f, 1f)
    pointerY > viewportHeight - edge -> maxSpeed * ((pointerY - (viewportHeight - edge)) / edge).coerceIn(0f, 1f)
    else -> 0f
}

private class DragSession(
    val anchor: Int,
    val initial: Set<Int>,
    val selecting: Boolean,
    var pointer: Offset,
)

/**
 * Long-press a cell, then drag across the grid to select (or, if the first cell was already
 * selected, deselect) every cell in between. Dragging near the top or bottom edge scrolls.
 * Plain taps still reach the cells and plain swipes still scroll.
 *
 * [idForKey] maps a grid item key to its selectable id, or null for headers and footers.
 * [contentPaddingStart] / [contentPaddingTop] must match the grid's content padding:
 * lazy-grid item offsets are measured inside it.
 */
@Composable
fun Modifier.gridDragSelect(
    state: LazyGridState,
    order: List<Int>,
    selection: Set<Int>,
    idForKey: (Any) -> Int?,
    onSelectionChange: (Set<Int>) -> Unit,
    contentPaddingStart: Dp,
    contentPaddingTop: Dp,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val paddingStartPx = with(density) { contentPaddingStart.toPx() }
    val paddingTopPx = with(density) { contentPaddingTop.toPx() }
    val edgePx = with(density) { 72.dp.toPx() }
    val maxSpeedPx = with(density) { 18.dp.toPx() }

    val currentOrder by rememberUpdatedState(order)
    val currentSelection by rememberUpdatedState(selection)
    val currentIdForKey by rememberUpdatedState(idForKey)
    val currentOnChange by rememberUpdatedState(onSelectionChange)

    var session by remember { mutableStateOf<DragSession?>(null) }
    var autoScrollSpeed by remember { mutableFloatStateOf(0f) }

    fun idAt(pointer: Offset): Int? {
        val x = pointer.x - paddingStartPx
        val y = pointer.y - paddingTopPx
        val hit = state.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            x >= item.offset.x && x < item.offset.x + item.size.width &&
                y >= item.offset.y && y < item.offset.y + item.size.height
        } ?: return null
        return currentIdForKey(hit.key)
    }

    fun extendTo(pointer: Offset) {
        val active = session ?: return
        active.pointer = pointer
        val id = idAt(pointer) ?: return
        val next = applyDragSelection(active.initial, currentOrder, active.anchor, id, active.selecting)
        if (next != currentSelection) currentOnChange(next)
    }

    LaunchedEffect(autoScrollSpeed != 0f) {
        while (autoScrollSpeed != 0f) {
            state.scrollBy(autoScrollSpeed)
            session?.let { extendTo(it.pointer) }
            withFrameNanos { }
        }
    }

    return pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Moving before the timeout lets the grid scroll and cancels this, as usual.
            val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            val anchor = idAt(longPress.position) ?: return@awaitEachGesture
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            session = DragSession(
                anchor = anchor,
                initial = currentSelection,
                selecting = anchor !in currentSelection,
                pointer = longPress.position,
            )
            extendTo(longPress.position)
            longPress.consume()
            try {
                // Initial pass: see each move before the grid's scroller and the cells do, and
                // consume it, so the drag neither scrolls the grid nor ends in a cell's tap.
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    extendTo(change.position)
                    autoScrollSpeed = dragAutoScrollSpeed(
                        pointerY = change.position.y,
                        viewportHeight = size.height.toFloat(),
                        edge = edgePx,
                        maxSpeed = maxSpeedPx,
                    )
                }
            } finally {
                session = null
                autoScrollSpeed = 0f
            }
        }
    }
}
