package `in`.c1ph3rj.scanly.feature.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.domain.model.RecognizedPageText
import `in`.c1ph3rj.scanly.domain.model.RecognizedTextToken
import kotlin.math.pow

@Composable
internal fun PageTextOverlay(
    recognizedText: RecognizedPageText,
    selection: IntRange?,
    zoomScale: Float,
    selectedColor: Color,
    onSelectToken: (Int) -> Unit,
    onSelectRange: (Int, Int) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val outlineWidthPx = with(density) { 1.dp.toPx() }
    val handleRadiusPx = with(density) { 6.dp.toPx() }
    val handleTouchRadiusPx = with(density) { 24.dp.toPx() }
    val tokenTouchPaddingPx = with(density) { 5.dp.toPx() }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = "Detected text" }
            .pointerInput(recognizedText, selection, zoomScale) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val effectiveScale = zoomScale.coerceAtLeast(1f)
                    val startHandle = selection?.let { range ->
                        recognizedText.tokens.getOrNull(range.first)?.handlePoint(
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            isStart = true,
                        )
                    }
                    val endHandle = selection?.let { range ->
                        recognizedText.tokens.getOrNull(range.last)?.handlePoint(
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            isStart = false,
                        )
                    }
                    val handleRadius = handleTouchRadiusPx / effectiveScale
                    val draggingStartHandle = startHandle?.isNear(down.position, handleRadius) == true
                    val draggingEndHandle = endHandle?.isNear(down.position, handleRadius) == true
                    val touchedToken = findTextTokenAt(
                        tokens = recognizedText.tokens,
                        position = down.position,
                        width = size.width.toFloat(),
                        height = size.height.toFloat(),
                        padding = tokenTouchPaddingPx / effectiveScale,
                    )
                    val anchorIndex = when {
                        draggingStartHandle -> selection?.last
                        draggingEndHandle -> selection?.first
                        touchedToken != null -> touchedToken
                        else -> null
                    }

                    if (anchorIndex == null) {
                        onClearSelection()
                    } else {
                        if (!draggingStartHandle && !draggingEndHandle) {
                            onSelectToken(anchorIndex)
                        }
                        down.consume()

                        var selecting = true
                        while (selecting) {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } > 1) {
                                selecting = false
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                selecting = false
                                continue
                            }
                            val focusIndex = findTextTokenAt(
                                tokens = recognizedText.tokens,
                                position = change.position,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                padding = tokenTouchPaddingPx / effectiveScale,
                            ) ?: findNearestTextToken(
                                tokens = recognizedText.tokens,
                                position = change.position,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                maximumDistance = handleTouchRadiusPx * 2f / effectiveScale,
                            )
                            if (focusIndex != null) {
                                onSelectRange(anchorIndex, focusIndex)
                            }
                            change.consume()
                        }
                    }
                }
            },
    ) {
        val effectiveScale = zoomScale.coerceAtLeast(1f)
        recognizedText.tokens.forEach { token ->
            val selected = selection?.contains(token.index) == true
            drawTokenPolygon(
                token = token,
                fillColor = if (selected) selectedColor.copy(alpha = 0.34f)
                else Color.White.copy(alpha = 0.10f),
                outlineColor = if (selected) selectedColor.copy(alpha = 0.95f)
                else Color.White.copy(alpha = 0.38f),
                outlineWidth = outlineWidthPx / effectiveScale,
            )
        }

        selection?.let { range ->
            recognizedText.tokens.getOrNull(range.first)?.handlePoint(
                width = size.width,
                height = size.height,
                isStart = true,
            )?.let { point ->
                drawCircle(
                    color = selectedColor,
                    radius = handleRadiusPx / effectiveScale,
                    center = point,
                )
            }
            recognizedText.tokens.getOrNull(range.last)?.handlePoint(
                width = size.width,
                height = size.height,
                isStart = false,
            )?.let { point ->
                drawCircle(
                    color = selectedColor,
                    radius = handleRadiusPx / effectiveScale,
                    center = point,
                )
            }
        }
    }
}

private fun DrawScope.drawTokenPolygon(
    token: RecognizedTextToken,
    fillColor: Color,
    outlineColor: Color,
    outlineWidth: Float,
) {
    val points = token.cornerPoints.map { point ->
        Offset(point.x * size.width, point.y * size.height)
    }
    if (points.size < 3) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { point -> lineTo(point.x, point.y) }
        close()
    }
    drawPath(path = path, color = fillColor)
    drawPath(path = path, color = outlineColor, style = Stroke(width = outlineWidth))
}

internal fun findTextTokenAt(
    tokens: List<RecognizedTextToken>,
    position: Offset,
    width: Float,
    height: Float,
    padding: Float = 0f,
): Int? = tokens.lastOrNull { token ->
    token.bounds(width, height).contains(position, padding)
}?.index

internal fun findNearestTextToken(
    tokens: List<RecognizedTextToken>,
    position: Offset,
    width: Float,
    height: Float,
    maximumDistance: Float,
): Int? = tokens
    .map { token -> token.index to token.bounds(width, height).center }
    .minByOrNull { (_, center) -> center.distanceSquaredTo(position) }
    ?.takeIf { (_, center) -> center.distanceSquaredTo(position) <= maximumDistance.pow(2) }
    ?.first

private fun RecognizedTextToken.handlePoint(
    width: Float,
    height: Float,
    isStart: Boolean,
): Offset {
    val bounds = bounds(width, height)
    return if (isStart) Offset(bounds.left, bounds.bottom) else Offset(bounds.right, bounds.bottom)
}

private fun RecognizedTextToken.bounds(width: Float, height: Float): TextTokenBounds {
    val points = cornerPoints.map { point -> Offset(point.x * width, point.y * height) }
    return TextTokenBounds(
        left = points.minOfOrNull(Offset::x) ?: 0f,
        top = points.minOfOrNull(Offset::y) ?: 0f,
        right = points.maxOfOrNull(Offset::x) ?: 0f,
        bottom = points.maxOfOrNull(Offset::y) ?: 0f,
    )
}

private data class TextTokenBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val center: Offset
        get() = Offset((left + right) / 2f, (top + bottom) / 2f)

    fun contains(position: Offset, padding: Float): Boolean =
        position.x in (left - padding)..(right + padding) &&
            position.y in (top - padding)..(bottom + padding)
}

private fun Offset.isNear(other: Offset, radius: Float): Boolean =
    distanceSquaredTo(other) <= radius.pow(2)

private fun Offset.distanceSquaredTo(other: Offset): Float =
    (x - other.x).pow(2) + (y - other.y).pow(2)
