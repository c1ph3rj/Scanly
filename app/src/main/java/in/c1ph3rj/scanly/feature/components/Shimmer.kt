package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Progress of one shimmer sweep, shared by every block in a skeleton so the highlight
 * travels across them together instead of each block pulsing on its own clock.
 */
@Composable
fun rememberShimmerProgress(): State<Float> {
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_progress",
    )
}

/**
 * Placeholder block with a diagonal highlight sweeping across it. The sweep reads [progress]
 * only in the draw phase, so the animation never recomposes the skeleton.
 */
@Composable
fun ShimmerBlock(
    progress: State<Float>,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerLow
    val sweepWidth = with(LocalDensity.current) { 240.dp.toPx() }
    val colors = remember(base, highlight) { listOf(base, highlight, base) }
    Box(
        modifier = modifier
            .background(base, shape)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val travel = size.width + sweepWidth * 2
                onDrawBehind {
                    val start = -sweepWidth + travel * progress.value - sweepWidth
                    drawOutline(
                        outline = outline,
                        brush = Brush.linearGradient(
                            colors = colors,
                            start = Offset(start, 0f),
                            end = Offset(start + sweepWidth, size.height),
                        ),
                    )
                }
            },
    )
}
