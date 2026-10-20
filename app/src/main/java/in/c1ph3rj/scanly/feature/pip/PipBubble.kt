package `in`.c1ph3rj.scanly.feature.pip

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Which edge of the bubble the tail sits on — always the side Pip is on. */
enum class PipBubbleTail {
    START,
    END,
    TOP,
    BOTTOM,
}

/**
 * Pip's voice: a notched paper bubble with a stepped pixel tail. This is the only pixel-styled
 * element in the app; everything around it stays Material.
 *
 * In light themes the bubble gets an ink outline and ink hard shadow (like the website); in dark
 * themes the paper stands out by itself and the shadow is a deep teal.
 */
@Composable
fun PipBubble(
    text: String,
    modifier: Modifier = Modifier,
    tail: PipBubbleTail = PipBubbleTail.BOTTOM,
    /** Where along the tail's edge it sits, 0 = start/top, 1 = end/bottom. */
    tailPosition: Float = if (tail == PipBubbleTail.START || tail == PipBubbleTail.END) 0.78f else 0.5f,
    textAlign: TextAlign = TextAlign.Start,
    /** Shows a close button inside the bubble. */
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String = "Dismiss",
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val outline = if (dark) PipColors.Paper else PipColors.Ink
    val shadow = if (dark) MaterialTheme.colorScheme.primaryContainer else PipColors.Ink
    Row(
        modifier = modifier
            // Room for the hard shadow so neighbours never sit on it.
            .padding(end = ShadowOffset, bottom = ShadowOffset)
            .drawBehind { drawBubble(outline = outline, shadow = shadow, tail = tail, tailPosition = tailPosition) }
            .padding(
                start = Border + 12.dp,
                end = if (onDismiss != null) Border + 2.dp else Border + 12.dp,
                top = Border + 9.dp,
                bottom = Border + 9.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = PipColors.Ink,
            textAlign = textAlign,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onDismiss != null) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = dismissLabel,
                    tint = PipColors.Ink.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private val Border: Dp = 2.dp
private val ShadowOffset: Dp = 4.dp
private val TailBlock: Dp = 8.dp
private val TailStep: Dp = 4.dp

private fun DrawScope.drawBubble(
    outline: Color,
    shadow: Color,
    tail: PipBubbleTail,
    tailPosition: Float,
) {
    val b = Border.toPx()
    val s = ShadowOffset.toPx()
    val notched = notchedRect(size, b)
    translate(left = s, top = s) { drawPath(notched, shadow) }
    drawPath(notched, outline)
    drawRect(PipColors.Paper, topLeft = Offset(b, b), size = Size(size.width - 2 * b, size.height - 2 * b))
    drawTail(outline = outline, tail = tail, tailPosition = tailPosition)
}

/** A rectangle with each corner pixel knocked out, the website's notched box. */
private fun notchedRect(size: Size, n: Float): Path = Path().apply {
    val w = size.width
    val h = size.height
    moveTo(n, 0f)
    lineTo(w - n, 0f)
    lineTo(w - n, n)
    lineTo(w, n)
    lineTo(w, h - n)
    lineTo(w - n, h - n)
    lineTo(w - n, h)
    lineTo(n, h)
    lineTo(n, h - n)
    lineTo(0f, h - n)
    lineTo(0f, n)
    lineTo(n, n)
    close()
}

/** A square block out of the bubble, a smaller step beyond it, and paper over the border seam. */
private fun DrawScope.drawTail(outline: Color, tail: PipBubbleTail, tailPosition: Float) {
    val block = TailBlock.toPx()
    val step = TailStep.toPx()
    val b = Border.toPx()
    val w = size.width
    val h = size.height
    when (tail) {
        PipBubbleTail.BOTTOM, PipBubbleTail.TOP -> {
            val x = ((w - block) * tailPosition).coerceIn(b * 2, w - block - b * 2)
            val outward = tail == PipBubbleTail.BOTTOM
            val blockY = if (outward) h else -block
            val stepY = if (outward) h + block - step else -block
            drawRect(outline, Offset(x, blockY), Size(block, block))
            // The step leans toward whichever side Pip is on.
            val stepX = if (tailPosition > 0.5f) x + block else x - step
            drawRect(outline, Offset(stepX, if (outward) stepY + step else stepY - step), Size(step, step))
            drawRect(PipColors.Paper, Offset(x + b, if (outward) h - b else 0f), Size(block - 2 * b, b + step / 2))
            if (outline != PipColors.Paper) {
                drawRect(PipColors.Paper, Offset(x + b, if (outward) h else -block + b), Size(block - 2 * b, block - b))
            }
        }
        PipBubbleTail.START, PipBubbleTail.END -> {
            val y = ((h - block) * tailPosition).coerceIn(b * 2, h - block - b * 2)
            val outward = tail == PipBubbleTail.END
            val blockX = if (outward) w else -block
            drawRect(outline, Offset(blockX, y), Size(block, block))
            drawRect(outline, Offset(if (outward) w + block else -block - step, y + block - step), Size(step, step))
            drawRect(PipColors.Paper, Offset(if (outward) w - b else 0f, y + b), Size(b + step / 2, block - 2 * b))
            if (outline != PipColors.Paper) {
                drawRect(PipColors.Paper, Offset(if (outward) w else -block + b, y + b), Size(block - b, block - 2 * b))
            }
        }
    }
}
