package `in`.c1ph3rj.scanly.feature.onboarding

import android.graphics.ImageDecoder
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.c1ph3rj.scanly.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Each illustration plays once on arrival. Off-screen pages consume no animation frames. */
@Composable
private fun rememberScene(settled: Boolean, motion: Boolean): Animatable<Float, *> {
    val scene = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(settled, motion) {
        if (!motion) {
            scene.snapTo(1f)
        } else if (settled) {
            scene.animateTo(1f, tween(1800, delayMillis = 120, easing = LinearEasing))
        } else {
            scene.snapTo(0f)
        }
    }
    return scene
}

/** The approved artwork uses a shared 960 × 800 board, including native animation overlays. */
@Composable
private fun OnboardingArt(
    @DrawableRes image: Int,
    height: Dp,
    progress: Animatable<Float, *>,
    modifier: Modifier,
    overlay: @Composable BoxScope.(Dp, Dp) -> Unit = { _, _ -> },
) {
    BoxWithConstraints(modifier.height(height), contentAlignment = Alignment.Center) {
        val resources = LocalResources.current
        val bitmap by produceState<ImageBitmap?>(null, resources, image) {
            value = withContext(Dispatchers.Default) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resources, image)).asImageBitmap()
            }
        }
        val width = minOf(maxWidth, maxHeight * 1.2f)
        val artHeight = width / 1.2f
        Box(
            Modifier.size(width, artHeight).graphicsLayer {
                val arrive = EmphasizedEasing.transform((progress.value / 0.45f).coerceIn(0f, 1f))
                scaleX = 0.98f + 0.02f * arrive
                scaleY = scaleX
                translationY = (1f - arrive) * 8.dp.toPx()
            }.clearAndSetSemantics {},
        ) {
            bitmap?.let {
                Image(it, contentDescription = null,
                    modifier = Modifier.fillMaxSize().testTag("onboarding_illustration_ready"))
                overlay(width, artHeight)
            }
        }
    }
}

@Composable
internal fun CaptureScene(settled: Boolean, motion: Boolean, height: Dp, modifier: Modifier = Modifier) {
    val scene = rememberScene(settled, motion)
    OnboardingArt(
        R.drawable.onboarding_art_capture, height, scene,
        modifier.testTag("onboarding_art_capture"),
    ) { _, _ ->
        Canvas(Modifier.fillMaxSize().testTag("onboarding_paper")) {
            // Geometry is relative to the 960 × 800 artwork, including the phone's tilted page.
            val sweep = EmphasizedEasing.transform(((scene.value - 0.15f) / 0.65f).coerceIn(0f, 1f))
            val start = Offset(size.width * 0.15f, size.height * (0.30f + 0.25f * sweep))
            val length = size.width * 0.415f
            val thickness = size.width * 0.016f
            val page = Path().apply {
                moveTo(size.width * 166f / 960f, size.height * 226f / 800f)
                lineTo(size.width * 464f / 960f, size.height * 196f / 800f)
                lineTo(size.width * 518f / 960f, size.height * 675f / 800f)
                lineTo(size.width * 222f / 960f, size.height * 703f / 800f)
                close()
            }
            clipPath(page) {
                rotate(-3.45f, start) {
                    val trail = size.height * 0.045f
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color(0xFF00BEA3).copy(alpha = 0.20f), Color.Transparent),
                            startY = start.y, endY = start.y + trail,
                        ),
                        topLeft = start, size = Size(length, trail),
                    )
                }
            }
            rotate(-3.45f, start) {
                val end = start + Offset(length, 0f)
                val shadow = Offset(0f, thickness * 0.3f)
                drawLine(Color(0xFF008F7A).copy(alpha = 0.14f), start + shadow, end + shadow,
                    strokeWidth = thickness * 1.3f, cap = StrokeCap.Round)
                drawLine(
                    Brush.verticalGradient(
                        listOf(Color(0xFF6CFFE0), Color(0xFF29EBC7), Color(0xFF0AC5A8)),
                        startY = start.y - thickness / 2, endY = start.y + thickness / 2,
                    ),
                    start, end, strokeWidth = thickness, cap = StrokeCap.Round,
                )
                val highlight = Offset(thickness * 0.2f, -thickness * 0.25f)
                drawLine(Color.White.copy(alpha = 0.4f), start + highlight,
                    end + Offset(-highlight.x, highlight.y),
                    strokeWidth = thickness * 0.12f, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun FindScene(settled: Boolean, motion: Boolean, minHeight: Dp, modifier: Modifier = Modifier) {
    val scene = rememberScene(settled, motion)
    OnboardingArt(
        R.drawable.onboarding_art_find, minHeight, scene,
        modifier.testTag("onboarding_search_preview"),
    ) { width, artHeight ->
        // Sample document text is decorative. Keep it proportional to the illustration when
        // accessibility text scales; the native explanatory copy below remains fully scalable.
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
            val query = stringResource(R.string.onboarding_demo_query)
            Box(
                // Centre in the free area after the magnifier, along the bar's own centreline.
                Modifier.offset(width * 0.24f, artHeight * 0.44f)
                    .size(width * 0.325f, artHeight * 0.1025f)
                    .graphicsLayer {
                        rotationZ = -3.9f
                        alpha = ((scene.value - 0.12f) / 0.28f).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(query, maxLines = 1, style = TextStyle(
                    color = Color(0xFF101418), fontSize = (width.value * 0.052f).sp,
                    lineHeight = (width.value * 0.052f).sp, fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ))
            }
            Box(
                Modifier.offset(width * 0.30f, artHeight * 0.698f)
                    .size(width * 0.155f, artHeight * 0.08f)
                    .graphicsLayer {
                        rotationZ = -5.5f
                        alpha = ((scene.value - 0.48f) / 0.25f).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(query, maxLines = 1, style = TextStyle(
                    color = Color(0xFF101418), fontSize = (width.value * 0.0405f).sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ))
            }
        }
    }
}

@Composable
internal fun FinishScene(settled: Boolean, motion: Boolean, height: Dp, modifier: Modifier = Modifier) {
    val scene = rememberScene(settled, motion)
    OnboardingArt(R.drawable.onboarding_art_finish, height, scene,
        modifier.testTag("onboarding_art_finish").graphicsLayer {
            val arrive = EmphasizedEasing.transform((scene.value / 0.65f).coerceIn(0f, 1f))
            rotationZ = -1.5f * (1f - arrive)
        })
}
