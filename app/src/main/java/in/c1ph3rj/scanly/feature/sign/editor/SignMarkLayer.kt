package `in`.c1ph3rj.scanly.feature.sign.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.c1ph3rj.scanly.domain.model.NormRect
import `in`.c1ph3rj.scanly.domain.model.SignInk
import `in`.c1ph3rj.scanly.domain.model.SignMark
import `in`.c1ph3rj.scanly.domain.model.SignMarkStyle
import `in`.c1ph3rj.scanly.feature.sign.manage.SavedSignatureImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Visible corner handle radius (12dp circle). */
private val HandleVisualRadius = 6.dp

/** Radius around a corner that still grabs the handle (24dp touch target). */
private val HandleTouchRadius = 12.dp

private val ToolbarGap = 8.dp
private val SelectionDashOn = 6.dp
private val SelectionDashOff = 4.dp
private val SelectionStroke = 1.5.dp
private val HandleRingStroke = 2.dp
private val PillShape = RoundedCornerShape(percent = 50)
private val DeleteTint = Color(0xFFFF8A80)
private const val TextProbeSp = 100f

/** A drag in progress. Each keeps the box it started from, so repeated updates never drift. */
private sealed interface LayerDrag {
    val id: String

    data class Move(override val id: String, val start: NormRect) : LayerDrag

    data class Resize(
        override val id: String,
        val corner: Corner,
        val start: NormRect,
        val lockAspect: Boolean,
    ) : LayerDrag
}

/**
 * Draws the marks placed on one page and handles editing them: tap to select, drag to move or
 * resize (corner handles), double-tap a text mark to edit it, and a floating toolbar above the
 * selected mark for ink, duplicate and delete. Every change is reported through [onAction].
 *
 * The layer is sized by [modifier]; place it over the page image at exactly the page's size so
 * the normalised boxes line up with the page.
 *
 * @param pageIndex Page whose marks are drawn.
 * @param pageAspect Page width / height, used to keep aspect-locked marks square-ish on screen.
 * @param marks All marks in the document; only those on [pageIndex] are drawn.
 * @param selectedMarkId Id of the selected mark, or null.
 * @param onAction Sink for select, move, resize, ink, duplicate, delete and text-edit actions.
 * @param modifier Sizes the layer; pass the page's exact size.
 */
@Composable
internal fun SignMarkLayer(
    pageIndex: Int,
    pageAspect: Float,
    marks: List<SignMark>,
    selectedMarkId: String?,
    onAction: (SignEditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val pageMarks = marks.filter { it.pageIndex == pageIndex }
    val selected = pageMarks.firstOrNull { it.id == selectedMarkId }
    val gapPx = with(density) { ToolbarGap.toPx() }

    val latestMarks = rememberUpdatedState(pageMarks)
    val latestSelectedId = rememberUpdatedState(selectedMarkId)
    val latestOnAction = rememberUpdatedState(onAction)
    val latestAspect = rememberUpdatedState(pageAspect)
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }

    BoxWithConstraints(modifier = modifier) {
        val pageWidthPx = with(density) { maxWidth.toPx() }
        val pageHeightPx = with(density) { maxHeight.toPx() }
        val handleTouchPx = with(density) { HandleTouchRadius.toPx() }
        val latestWidth = rememberUpdatedState(pageWidthPx)
        val latestHeight = rememberUpdatedState(pageHeightPx)

        // Gesture surface sits below the marks so children never block it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val w = latestWidth.value
                            val h = latestHeight.value
                            val current = latestMarks.value
                            val selectedMark = current.firstOrNull { it.id == latestSelectedId.value }
                            val onHandle = selectedMark != null &&
                                cornerNear(selectedMark.box.toRect(w, h), offset, handleTouchPx) != null
                            if (!onHandle) {
                                val hit = hitMark(current, offset, w, h)
                                latestOnAction.value(SignEditorAction.SelectMark(hit?.id))
                            }
                        },
                        onDoubleTap = { offset ->
                            val hit = hitMark(latestMarks.value, offset, latestWidth.value, latestHeight.value)
                            if (hit is SignMark.Text || hit is SignMark.Date) {
                                latestOnAction.value(SignEditorAction.StartEditText(hit.id))
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    var drag: LayerDrag? = null
                    var totalX = 0f
                    var totalY = 0f
                    detectDragGestures(
                        onDragStart = { start ->
                            val w = latestWidth.value
                            val h = latestHeight.value
                            val current = latestMarks.value
                            val selectedMark = current.firstOrNull { it.id == latestSelectedId.value }
                            val corner = selectedMark?.let { mark ->
                                val rect = mark.box.toRect(w, h)
                                // On a small box the handles cover its body; a grab inside it moves.
                                val bodyGrab = rect.contains(start) && minOf(rect.width, rect.height) < handleTouchPx * 3f
                                cornerNear(rect, start, handleTouchPx).takeUnless { bodyGrab }
                            }
                            totalX = 0f
                            totalY = 0f
                            drag = if (selectedMark != null && corner != null) {
                                LayerDrag.Resize(
                                    id = selectedMark.id,
                                    corner = corner,
                                    start = selectedMark.box,
                                    lockAspect = lockAspectFor(selectedMark),
                                )
                            } else {
                                val hit = hitMark(current, start, w, h)
                                if (hit == null) {
                                    null
                                } else {
                                    if (hit.id != latestSelectedId.value) {
                                        latestOnAction.value(SignEditorAction.SelectMark(hit.id))
                                    }
                                    LayerDrag.Move(id = hit.id, start = hit.box)
                                }
                            }
                        },
                        onDrag = { change, amount ->
                            val active = drag ?: return@detectDragGestures
                            change.consume()
                            totalX += amount.x
                            totalY += amount.y
                            val dx = totalX / latestWidth.value
                            val dy = totalY / latestHeight.value
                            val next = when (active) {
                                is LayerDrag.Move -> moveBox(active.start, dx, dy)
                                is LayerDrag.Resize -> resizeFromCorner(
                                    box = active.start,
                                    corner = active.corner,
                                    dxNorm = dx,
                                    dyNorm = dy,
                                    lockAspect = active.lockAspect,
                                    pageAspect = latestAspect.value,
                                )
                            }
                            latestOnAction.value(SignEditorAction.UpdateBox(active.id, next))
                        },
                        onDragEnd = { drag = null },
                        onDragCancel = { drag = null },
                    )
                },
        ) {
            // Placeholder so the gesture surface is laid out; the marks draw above it.
        }

        pageMarks.forEach { mark ->
            key(mark.id) {
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * mark.box.left, y = maxHeight * mark.box.top)
                        .size(width = maxWidth * mark.box.width, height = maxHeight * mark.box.height),
                ) {
                    MarkContent(mark)
                }
            }
        }

        selected?.let { mark ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rect = mark.box.toRect(size.width, size.height)
                drawRect(
                    color = accent.copy(alpha = 0.08f),
                    topLeft = rect.topLeft,
                    size = rect.size,
                )
                drawRect(
                    color = accent,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(
                        width = SelectionStroke.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(SelectionDashOn.toPx(), SelectionDashOff.toPx()),
                        ),
                    ),
                )
                val radius = HandleVisualRadius.toPx()
                cornerCenters(rect).values.forEach { center ->
                    drawCircle(color = Color.White, radius = radius, center = center)
                    drawCircle(
                        color = accent,
                        radius = radius,
                        center = center,
                        style = Stroke(width = HandleRingStroke.toPx()),
                    )
                }
            }
        }

        if (selected != null) {
            val box = selected.box
            val boxTop = box.top * pageHeightPx
            val boxBottom = box.bottom * pageHeightPx
            val anchorX = box.centerX * pageWidthPx
            val fitsAbove = boxTop - gapPx - toolbarSize.height >= 0f
            val rawY = if (fitsAbove) boxTop - gapPx - toolbarSize.height else boxBottom + gapPx
            val x = (anchorX - toolbarSize.width / 2f)
                .coerceIn(0f, max(0f, pageWidthPx - toolbarSize.width))
            val y = rawY.coerceIn(0f, max(0f, pageHeightPx - toolbarSize.height))
            SelectedMarkToolbar(
                mark = selected,
                onAction = onAction,
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .onSizeChanged { toolbarSize = it },
            )
        }
    }
}

@Composable
private fun MarkContent(mark: SignMark) {
    when (mark) {
        is SignMark.Image -> SavedSignatureImage(
            imagePath = mark.imagePath,
            ink = mark.ink,
            modifier = Modifier.fillMaxSize(),
        )
        is SignMark.Text -> TextMarkContent(text = mark.text, ink = mark.ink)
        is SignMark.Date -> TextMarkContent(text = mark.text, ink = mark.ink)
        is SignMark.Checkbox -> CheckboxMarkContent(ink = mark.ink)
    }
}

/**
 * Single line of text sized to the box height (shrunk if it would overflow the width) and set on
 * the shared baseline from [SignMarkStyle], so it matches the PDF output.
 */
@Composable
private fun TextMarkContent(text: String, ink: SignInk) {
    val measurer = rememberTextMeasurer()
    val color = Color(ink.argb)
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val probe = measurer.measure(text, TextStyle(fontSize = TextProbeSp.sp))
                val probePx = TextProbeSp.sp.toPx()
                val widthAtSizeOne = if (probePx > 0f) probe.size.width / probePx else 0f
                val fontPx = SignMarkStyle.fontSizeFor(size.width, size.height, widthAtSizeOne)
                val layout = measurer.measure(
                    text = text,
                    style = TextStyle(color = color, fontSize = fontPx.toSp()),
                )
                val baselineY = size.height * SignMarkStyle.TextBaselineFraction
                onDrawBehind {
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(0f, baselineY - layout.firstBaseline),
                    )
                }
            },
    )
}

/** Tick polyline from [SignMarkStyle.CheckPoints] with round caps, sized like the PDF output. */
@Composable
private fun CheckboxMarkContent(ink: SignInk) {
    val color = Color(ink.argb)
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val strokePx = SignMarkStyle.CheckStrokeFraction * min(size.width, size.height)
                val path = Path()
                SignMarkStyle.CheckPoints.forEachIndexed { index, (fx, fy) ->
                    val x = fx * size.width
                    val y = fy * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                onDrawBehind {
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = strokePx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            },
    )
}

/** Dark pill with the ink dot, a duplicate icon and a red delete icon. */
@Composable
private fun SelectedMarkToolbar(
    mark: SignMark,
    onAction: (SignEditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var inkMenuOpen by remember(mark.id) { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = colors.inverseSurface,
        contentColor = colors.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                IconButton(onClick = { inkMenuOpen = true }, modifier = Modifier.size(40.dp)) {
                    InkDot(color = Color(mark.ink.argb))
                }
                DropdownMenu(
                    expanded = inkMenuOpen,
                    onDismissRequest = { inkMenuOpen = false },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SignInk.entries.forEach { ink ->
                            InkSwatch(
                                ink = ink,
                                selected = ink == mark.ink,
                                onClick = {
                                    inkMenuOpen = false
                                    onAction(SignEditorAction.SetInk(mark.id, ink))
                                },
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = { onAction(SignEditorAction.Duplicate(mark.id)) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Duplicate",
                    tint = colors.inverseOnSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = { onAction(SignEditorAction.Delete(mark.id)) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = DeleteTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Filled ink dot with a white ring, used as the toolbar's colour button. */
@Composable
private fun InkDot(color: Color) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .border(width = 2.dp, color = Color.White, shape = CircleShape)
            .padding(3.dp)
            .background(color = color, shape = CircleShape),
    )
}

@Composable
private fun InkSwatch(
    ink: SignInk,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            )
            .padding(4.dp)
            .background(color = Color(ink.argb), shape = CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = ink.label },
    )
}

private fun lockAspectFor(mark: SignMark): Boolean = mark is SignMark.Image || mark is SignMark.Checkbox

private fun NormRect.toRect(width: Float, height: Float): Rect =
    Rect(left * width, top * height, right * width, bottom * height)

private fun cornerCenters(rect: Rect): Map<Corner, Offset> = mapOf(
    Corner.TopLeft to rect.topLeft,
    Corner.TopRight to rect.topRight,
    Corner.BottomLeft to rect.bottomLeft,
    Corner.BottomRight to rect.bottomRight,
)

private fun cornerNear(rect: Rect, point: Offset, radiusPx: Float): Corner? =
    cornerCenters(rect).entries
        .map { entry -> entry.key to (entry.value - point).getDistance() }
        .filter { (_, distance) -> distance <= radiusPx }
        .minByOrNull { (_, distance) -> distance }
        ?.first

/** Topmost mark on the page whose box contains [point], or null. */
private fun hitMark(marks: List<SignMark>, point: Offset, width: Float, height: Float): SignMark? =
    marks.asReversed().firstOrNull { it.box.toRect(width, height).contains(point) }
