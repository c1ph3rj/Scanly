package `in`.c1ph3rj.scanly.feature.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/**
 * Thin Material-styled vertical scrollbar for editor control panes.
 * Track is always drawn; thumb only when content overflows.
 */
@Composable
internal fun VerticalContentScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val trackColor = colorScheme.outlineVariant.copy(alpha = 0.55f)
    val thumbColor = colorScheme.primary.copy(alpha = 0.72f)
    val canScroll = scrollState.maxValue > 0

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth = size.width
            val corner = CornerRadius(trackWidth / 2f, trackWidth / 2f)

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, 0f),
                size = Size(trackWidth, trackHeightPx),
                cornerRadius = corner,
            )

            if (!canScroll) return@Canvas

            val contentHeight = trackHeightPx + scrollState.maxValue
            val minThumbPx = 28.dp.toPx()
            val thumbHeight = ((trackHeightPx / contentHeight) * trackHeightPx)
                .coerceIn(minThumbPx, trackHeightPx)
            val scrollFraction = scrollState.value / scrollState.maxValue.toFloat()
            val thumbTop = (trackHeightPx - thumbHeight) * scrollFraction

            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(0f, thumbTop),
                size = Size(trackWidth, thumbHeight),
                cornerRadius = corner,
            )
        }
    }
}
