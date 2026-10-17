package `in`.c1ph3rj.scanly.feature.sign.create

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.c1ph3rj.scanly.core.editing.InkPoint
import `in`.c1ph3rj.scanly.core.editing.InkStroke
import `in`.c1ph3rj.scanly.core.editing.InkStrokeBuilder

/** Height of the signing baseline as a fraction of the pad height. */
private const val BaselineFraction = 0.7f

/** Vertical centre of the "Draw here" hint, as a fraction of the pad height. */
private const val HintFraction = 0.4f

private val PadCornerRadius = 20.dp
private val BaselineInset = 28.dp
private val BaselineStroke = 1.5.dp
private val CaptionGap = 8.dp
private val CrossSize = 12.dp
private val CrossGap = 6.dp
private val CrossStroke = 1.5.dp
private val PadButtonSize = 36.dp
private val PadButtonIconSize = 18.dp
private val PadButtonInset = 8.dp

private val BaselineColor = Color(0xFFCBD5E1)
private val CrossColor = Color(0xFF94A3B8)
private val CaptionColor = 0xFF94A3B8.toInt()
private val HintColor = 0xFFCBD5E1.toInt()
private val PadButtonBackground = Color(0xFFF1F5F9)
private val PadButtonIconTint = Color(0xFF475569)

/**
 * Paper-like signing area in the style of an e-signature form. It stays white in every theme and
 * carries a baseline at 70% of the height, a small "x" just above the left end of the line, and a
 * caption centred under it. A faint "Draw here" hint sits in the upper area until the first stroke.
 * Undo and Clear appear as small circular buttons in the top-right corner once strokes exist.
 *
 * Pointer input is collected per gesture: each finished gesture reports one [InkStroke] through
 * [onStrokeFinished], while the stroke in progress is drawn live from a local list.
 *
 * @param strokes Finished strokes to draw, in order.
 * @param onStrokeFinished Called once when a gesture lifts, with the points it captured.
 * @param inkColor Colour used for the strokes.
 * @param style Pen style used to turn points into ink.
 * @param onUndo Called when the Undo button is pressed.
 * @param onClear Called when the Clear button is pressed.
 * @param caption Text centred under the baseline.
 */
@Composable
fun SignaturePad(
    strokes: List<InkStroke>,
    onStrokeFinished: (InkStroke) -> Unit,
    inkColor: Color,
    style: InkStrokeBuilder.Style,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String = "Sign above the line",
) {
    val livePoints = remember { mutableStateListOf<InkPoint>() }
    var liveIsStylus by remember { mutableStateOf(false) }
    val currentOnStrokeFinished by rememberUpdatedState(onStrokeFinished)
    val captionPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CaptionColor
            textAlign = Paint.Align.CENTER
        }
    }
    val hintPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = HintColor
            textAlign = Paint.Align.CENTER
        }
    }
    val shape = RoundedCornerShape(PadCornerRadius)

    Box(
        modifier = modifier
            .shadow(2.dp, shape, clip = false)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pointerId = down.id
                        val isStylus = down.type == PointerType.Stylus
                        liveIsStylus = isStylus
                        livePoints.add(InkPoint(down.position.x, down.position.y, down.uptimeMillis, down.pressure))
                        down.consume()
                        try {
                            var released = false
                            while (!released) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    if (change.id != pointerId) continue
                                    for (past in change.historical) {
                                        livePoints.add(
                                            InkPoint(past.position.x, past.position.y, past.uptimeMillis, change.pressure),
                                        )
                                    }
                                    livePoints.add(
                                        InkPoint(change.position.x, change.position.y, change.uptimeMillis, change.pressure),
                                    )
                                    change.consume()
                                    if (!change.pressed) released = true
                                }
                            }
                            if (livePoints.isNotEmpty()) {
                                currentOnStrokeFinished(InkStroke(livePoints.toList(), isStylus))
                            }
                        } finally {
                            livePoints.clear()
                        }
                    }
                },
        ) {
            val baselineY = size.height * BaselineFraction
            val inset = BaselineInset.toPx()

            drawLine(
                color = BaselineColor,
                start = Offset(inset, baselineY),
                end = Offset(size.width - inset, baselineY),
                strokeWidth = BaselineStroke.toPx(),
            )

            // The "x" sits just above the left end of the baseline: its left edge is on the line
            // start and its bottom is CrossGap above the line.
            val crossLeft = inset
            val crossRight = inset + CrossSize.toPx()
            val crossBottom = baselineY - CrossGap.toPx()
            val crossTop = crossBottom - CrossSize.toPx()
            drawLine(
                color = CrossColor,
                start = Offset(crossLeft, crossTop),
                end = Offset(crossRight, crossBottom),
                strokeWidth = CrossStroke.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = CrossColor,
                start = Offset(crossRight, crossTop),
                end = Offset(crossLeft, crossBottom),
                strokeWidth = CrossStroke.toPx(),
                cap = StrokeCap.Round,
            )

            captionPaint.textSize = 12.sp.toPx()
            drawContext.canvas.nativeCanvas.drawText(
                caption,
                size.width / 2f,
                baselineY + CaptionGap.toPx() - captionPaint.ascent(),
                captionPaint,
            )

            if (strokes.isEmpty() && livePoints.isEmpty()) {
                hintPaint.textSize = 14.sp.toPx()
                val hintY = size.height * HintFraction - (hintPaint.ascent() + hintPaint.descent()) / 2f
                drawContext.canvas.nativeCanvas.drawText(
                    "Draw here",
                    size.width / 2f,
                    hintY,
                    hintPaint,
                )
            }

            drawInkStrokes(strokes, style, inkColor)
            if (livePoints.isNotEmpty()) {
                drawInkStrokes(listOf(InkStroke(livePoints.toList(), liveIsStylus)), style, inkColor)
            }
        }

        if (strokes.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(PadButtonInset),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PadIconButton(icon = Icons.AutoMirrored.Outlined.Undo, label = "Undo", onClick = onUndo)
                PadIconButton(icon = Icons.Outlined.Delete, label = "Clear", onClick = onClear)
            }
        }
    }
}

/** Small light-grey circular button drawn on top of the pad. */
@Composable
private fun PadIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(PadButtonSize)
            .clip(CircleShape)
            .background(PadButtonBackground)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = PadButtonIconTint,
            modifier = Modifier.size(PadButtonIconSize),
        )
    }
}
