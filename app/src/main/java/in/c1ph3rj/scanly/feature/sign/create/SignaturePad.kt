package `in`.c1ph3rj.scanly.feature.sign.create

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.c1ph3rj.scanly.core.editing.InkPoint
import `in`.c1ph3rj.scanly.core.editing.InkStroke
import `in`.c1ph3rj.scanly.core.editing.InkStrokeBuilder

/** Height of the signing baseline as a fraction of the pad height. */
private const val BaselineFraction = 0.7f

private val PadCornerRadius = 16.dp
private val BaselineColor = Color(0xFFCBD5E1)
private val CrossColor = Color(0xFF94A3B8)
private val CaptionArgb = 0xFF94A3B8.toInt()

/**
 * Paper-like signing area. It stays white in every theme, with a light baseline at about 70% of
 * the height, a "Sign here" caption until the first stroke, and an "x" marking the signing point.
 *
 * Pointer input is collected per gesture: each finished gesture reports one [InkStroke] through
 * [onStrokeFinished], while the stroke in progress is drawn live from a local list.
 *
 * @param strokes Finished strokes to draw, in order.
 * @param onStrokeFinished Called once when a gesture lifts, with the points it captured.
 * @param inkColor Colour used for the strokes.
 * @param style Pen style used to turn points into ink.
 */
@Composable
fun SignaturePad(
    strokes: List<InkStroke>,
    onStrokeFinished: (InkStroke) -> Unit,
    inkColor: Color,
    style: InkStrokeBuilder.Style,
    modifier: Modifier = Modifier,
) {
    val livePoints = remember { mutableStateListOf<InkPoint>() }
    var liveIsStylus by remember { mutableStateOf(false) }
    val currentOnStrokeFinished by rememberUpdatedState(onStrokeFinished)
    val captionPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CaptionArgb }
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(PadCornerRadius))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(PadCornerRadius))
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
        val inset = 20.dp.toPx()

        drawLine(
            color = BaselineColor,
            start = Offset(inset, baselineY),
            end = Offset(size.width - inset, baselineY),
            strokeWidth = 1.dp.toPx(),
        )

        val crossCenterX = inset + 4.dp.toPx()
        val crossCenterY = baselineY - 6.dp.toPx()
        val arm = 4.dp.toPx()
        val crossStroke = 1.5.dp.toPx()
        drawLine(
            color = CrossColor,
            start = Offset(crossCenterX - arm, crossCenterY - arm),
            end = Offset(crossCenterX + arm, crossCenterY + arm),
            strokeWidth = crossStroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = CrossColor,
            start = Offset(crossCenterX + arm, crossCenterY - arm),
            end = Offset(crossCenterX - arm, crossCenterY + arm),
            strokeWidth = crossStroke,
            cap = StrokeCap.Round,
        )

        if (strokes.isEmpty() && livePoints.isEmpty()) {
            captionPaint.textSize = 13.sp.toPx()
            drawContext.canvas.nativeCanvas.drawText(
                "Sign here",
                inset + 14.dp.toPx(),
                baselineY - 8.dp.toPx(),
                captionPaint,
            )
        }

        drawInkStrokes(strokes, style, inkColor)
        if (livePoints.isNotEmpty()) {
            drawInkStrokes(listOf(InkStroke(livePoints.toList(), liveIsStylus)), style, inkColor)
        }
    }
}
