package `in`.c1ph3rj.scanly.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.feature.pip.Pip
import `in`.c1ph3rj.scanly.feature.pip.PipBubble
import `in`.c1ph3rj.scanly.feature.pip.PipBubbleTail
import `in`.c1ph3rj.scanly.feature.pip.PipLook
import `in`.c1ph3rj.scanly.feature.pip.PipMood
import kotlinx.coroutines.delay

/** Pip beside the greeting, glancing at the actions below. Poke it for a line about itself. */
@Composable
internal fun HomeHeaderPip(onPoke: () -> Unit) {
    var hopping by remember { mutableStateOf(false) }
    var pokes by remember { mutableIntStateOf(0) }
    LaunchedEffect(pokes) {
        if (pokes == 0) return@LaunchedEffect
        hopping = true
        delay(3_600L)
        hopping = false
    }
    Pip(
        mood = if (hopping) PipMood.HAPPY else PipMood.IDLE,
        look = PipLook.LEFT,
        size = 64.dp,
        onPoke = {
            pokes++
            onPoke()
        },
    )
}

/**
 * Pip's note under the greeting, its tail pointing up at Pip: today's tip (closable until
 * tomorrow) or, right after a poke, what Pip had to say.
 */
@Composable
internal fun HomePipNote(
    line: String,
    isTip: Boolean,
    onDismissTip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopEnd,
    ) {
        PipBubble(
            text = line,
            tail = PipBubbleTail.TOP,
            tailPosition = 0.9f,
            onDismiss = if (isTip) onDismissTip else null,
            dismissLabel = "Hide today's tip",
            modifier = Modifier.widthIn(max = 340.dp),
        )
    }
}
