package `in`.c1ph3rj.scanly.feature.pip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.random.Random

/** Pip's paper palette. It stays paper-on-ink in every theme so it reads as the same character. */
internal object PipColors {
    val Ink = Color(0xFF171613)
    val Paper = Color(0xFFFBFAF5)
    val Cheek = Color(0xFF12C9A8)
    val Line = Color(0xFFD3CDBD)
}

/**
 * Pip, Scanly's helper. Keep [size] a multiple of 16dp so every bitmap pixel lands on whole
 * device pixels as often as possible.
 *
 * Pip blinks while idle, moves its mouth while talking, hops when happy or surprised, and wobbles
 * when dizzy — unless motion is off in Pip's settings or the system has animations disabled.
 * With [onPoke] set Pip is a button; otherwise it is decorative and hidden from accessibility
 * unless [contentDescription] is given.
 */
@Composable
fun Pip(
    mood: PipMood,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    look: PipLook = PipLook.AHEAD,
    contentDescription: String? = null,
    onPoke: (() -> Unit)? = null,
) {
    val motion = pipMotionEnabled()
    var blink by remember { mutableStateOf(false) }
    var mouthOpen by remember { mutableStateOf(false) }
    var beat by remember { mutableIntStateOf(0) }

    LaunchedEffect(mood, motion) {
        blink = false
        if (!motion || mood != PipMood.IDLE) return@LaunchedEffect
        // An irregular rhythm feels alive; a fixed loop reads as a machine.
        while (true) {
            delay(Random.nextLong(2_200L, 4_800L))
            blink = true
            delay(140L)
            blink = false
        }
    }
    LaunchedEffect(mood, motion) {
        mouthOpen = mood == PipMood.TALK
        if (!motion || mood != PipMood.TALK) return@LaunchedEffect
        while (true) {
            delay(150L)
            mouthOpen = !mouthOpen
        }
    }
    LaunchedEffect(mood, motion) {
        beat = 0
        if (!motion || mood == PipMood.IDLE) return@LaunchedEffect
        val period = when (mood) {
            PipMood.DIZZY -> 180L
            PipMood.SLEEP -> 700L
            else -> 300L
        }
        while (true) {
            delay(period)
            beat++
        }
    }

    val rows = remember(mood, look, blink, mouthOpen) {
        PipBitmap.runs(PipBitmap.frame(mood = mood, look = look, blink = blink, mouthOpen = mouthOpen))
    }
    // Hops and wobbles move in whole bitmap pixels, so Pip never smears between steps.
    val hop = motion && beat % 2 == 1 && (mood == PipMood.TALK || mood == PipMood.HAPPY || mood == PipMood.WOW)
    val wobble = if (motion && mood == PipMood.DIZZY) (if (beat % 2 == 0) -6f else 6f) else 0f
    val zColor = MaterialTheme.colorScheme.onSurfaceVariant

    val semanticsModifier = when {
        onPoke != null -> Modifier.semantics {
            this.contentDescription = contentDescription ?: "Poke Pip, Scanly's helper"
        }
        contentDescription != null -> Modifier.clearAndSetSemantics {
            this.contentDescription = contentDescription
        }
        else -> Modifier.clearAndSetSemantics {}
    }
    val clickModifier = if (onPoke != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onPoke,
        )
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .size(size)
            .then(clickModifier)
            .then(semanticsModifier)
            .graphicsLayer { rotationZ = wobble },
    ) {
        val cell = floor(this.size.minDimension / PipBitmap.SIZE).coerceAtLeast(1f)
        // Whole-pixel origin: a fractional one anti-aliases every row edge into visible seams.
        val origin = Offset(
            x = floor((this.size.width - cell * PipBitmap.SIZE) / 2f),
            y = floor((this.size.height - cell * PipBitmap.SIZE) / 2f) + if (hop) -cell else 0f,
        )
        // One path per colour, like the website's SVG: no internal edges, so no hairline seams.
        rows.groupBy { it.cell }.forEach { (cellType, runs) ->
            val path = Path()
            runs.forEach { run ->
                path.addRect(
                    Rect(
                        offset = Offset(origin.x + run.x * cell, origin.y + run.y * cell),
                        size = Size(run.length * cell, cell),
                    ),
                )
            }
            drawPath(path = path, color = cellType.pipColor())
        }
        if (mood == PipMood.SLEEP) {
            // A pixel "z" drifting above the head.
            val lift = if (beat % 2 == 1) cell else 0f
            listOf("kkk", "..k", ".k.", "kkk").forEachIndexed { y, row ->
                row.forEachIndexed { x, c ->
                    if (c == 'k') {
                        drawRect(
                            color = zColor,
                            topLeft = Offset(origin.x + (13 + x) * cell, origin.y + (y - 2) * cell - lift),
                            size = Size(cell, cell),
                        )
                    }
                }
            }
        }
    }
}

private fun Char.pipColor(): Color = when (this) {
    'w' -> PipColors.Paper
    'C' -> PipColors.Cheek
    'L' -> PipColors.Line
    else -> PipColors.Ink
}
