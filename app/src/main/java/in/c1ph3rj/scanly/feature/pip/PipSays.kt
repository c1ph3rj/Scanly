package `in`.c1ph3rj.scanly.feature.pip

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class PipLayout {
    /** Bubble above, Pip below — for centred empty states. */
    STACKED,

    /** Pip on the left, bubble to its right — for rows and sheets. */
    BESIDE,
}

/**
 * Pip saying [line]. While the line is new Pip talks for a moment, then settles into [mood].
 * Tapping Pip (when [pokeable]) makes it hop and say something about itself for a few seconds.
 */
@Composable
fun PipSays(
    line: String,
    mood: PipMood,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    look: PipLook = PipLook.AHEAD,
    layout: PipLayout = PipLayout.STACKED,
    pokeable: Boolean = true,
    bubbleMaxWidth: Dp = 280.dp,
) {
    var pokeCount by remember { mutableIntStateOf(0) }
    var poked by remember { mutableStateOf(false) }
    var talking by remember { mutableStateOf(false) }

    LaunchedEffect(line) {
        talking = true
        delay(1_200L)
        talking = false
    }
    LaunchedEffect(pokeCount) {
        if (pokeCount == 0) return@LaunchedEffect
        poked = true
        delay(3_200L)
        poked = false
    }

    val shownLine = if (poked) PipLines.pokes[(pokeCount - 1) % PipLines.pokes.size] else line
    val shownMood = when {
        poked -> PipMood.HAPPY
        talking && mood != PipMood.SLEEP -> PipMood.TALK
        else -> mood
    }
    val onPoke: (() -> Unit)? = if (pokeable) ({ pokeCount++ }) else null

    when (layout) {
        PipLayout.STACKED -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedLine(shownLine) { text ->
                PipBubble(
                    text = text,
                    tail = PipBubbleTail.BOTTOM,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = bubbleMaxWidth),
                )
            }
            Spacer(Modifier.height(16.dp))
            Pip(mood = shownMood, size = size, look = look, onPoke = onPoke)
        }

        PipLayout.BESIDE -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Pip(mood = shownMood, size = size, look = look, onPoke = onPoke)
            AnimatedLine(shownLine) { text ->
                PipBubble(
                    text = text,
                    tail = PipBubbleTail.START,
                    modifier = Modifier.widthIn(max = bubbleMaxWidth),
                )
            }
        }
    }
}

@Composable
private fun AnimatedLine(line: String, content: @Composable (String) -> Unit) {
    AnimatedContent(
        targetState = line,
        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
        label = "pip_line",
    ) { text -> content(text) }
}
