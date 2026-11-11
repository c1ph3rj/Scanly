package `in`.c1ph3rj.scanly.feature.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Small explanatory illustrations for the Tools hub. Each one shows what a tool does (two pages
 * becoming one for Merge, a page with a padlock for Password) instead of a lone glyph, in the
 * tool's own hue so the grid is easy to scan. They are drawn on a 64 x 48 unit board and scale to
 * any size; colours follow the light, dark and pure black themes. Purely decorative: the tile's
 * title and caption carry the meaning for screen readers.
 */

/** Hue for each tool. Related tools share one (Sign and My signatures). */
private fun ToolActionId.hue(): Color = when (this) {
    ToolActionId.Scan -> Color(0xFF0F766E)
    ToolActionId.Import -> Color(0xFFCA8A04)
    ToolActionId.PdfSign, ToolActionId.Signatures -> Color(0xFF2563EB)
    ToolActionId.PdfMerge -> Color(0xFF7C3AED)
    ToolActionId.PdfSplit -> Color(0xFFDB2777)
    ToolActionId.PdfCompress -> Color(0xFF16A34A)
    ToolActionId.PdfToImages -> Color(0xFFEA580C)
    ToolActionId.PdfPassword -> Color(0xFFDC2626)
    ToolActionId.PdfWatermark -> Color(0xFF0891B2)
    ToolActionId.PdfReader -> Color(0xFF4F46E5)
    ToolActionId.PdfImport -> Color(0xFF0D9488)
    ToolActionId.Qr -> Color(0xFF5B6B82)
    ToolActionId.ImageText -> Color(0xFFC026D3)
}

@Immutable
private data class ToolArtColors(
    val paper: Color,
    val edge: Color,
    val line: Color,
    val accent: Color,
    val accentSoft: Color,
    val stage: Color,
    /** Soft halo of the tool's hue behind the drawing; transparent when the stage is tinted. */
    val glow: Color = Color.Transparent,
)

@Composable
private fun rememberToolArtColors(id: ToolActionId): ToolArtColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    return remember(id, dark, scheme.surfaceContainerHigh) {
        val hue = id.hue()
        if (dark) {
            // Paper stays paper-like (soft off-white, dimmed so it does not glare) so pages read
            // clearly. The stage stays neutral like every other dark surface: solid dark fills of
            // each hue went brown, maroon and navy and fought the app's grey-teal palette. Colour
            // comes from the drawing itself plus a faint halo of the hue behind it.
            val accent = lerp(hue, Color.White, 0.28f)
            ToolArtColors(
                paper = Color(0xFFDCE3E0),
                edge = Color(0xFF8F9B97),
                line = Color(0xFFA9B4B0),
                accent = accent,
                accentSoft = lerp(hue, Color(0xFFDCE3E0), 0.68f),
                stage = lerp(scheme.surfaceContainerHigh, hue, 0.05f),
                glow = accent.copy(alpha = 0.16f),
            )
        } else {
            ToolArtColors(
                paper = Color.White,
                edge = Color(0xFF8A96A0),
                line = Color(0xFFC9D1D8),
                accent = hue,
                accentSoft = hue.copy(alpha = 0.18f),
                stage = hue.copy(alpha = 0.10f),
            )
        }
    }
}

/**
 * A tinted rounded "stage" with the tool's illustration centred in it. Give it a size; the drawing
 * keeps its 4:3 shape and fills [artHeightFraction] of the stage height.
 */
@Composable
fun ToolArtStage(
    id: ToolActionId,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    artHeightFraction: Float = 0.84f,
) {
    val colors = rememberToolArtColors(id)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.stage)
            .drawBehind {
                if (colors.glow.alpha > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(colors.glow, Color.Transparent),
                            center = center,
                            radius = size.minDimension * 0.75f,
                        ),
                        radius = size.minDimension * 0.75f,
                        center = center,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight(artHeightFraction)
                .aspectRatio(4f / 3f),
        ) {
            val art = ToolArt(this, size.width / 64f, colors)
            art.draw(id)
        }
    }
}

/** Draws on a 64 x 48 unit board; [u] is one unit in pixels. */
private class ToolArt(
    private val scope: DrawScope,
    private val u: Float,
    private val c: ToolArtColors,
) {
    private fun o(x: Float, y: Float) = Offset(x * u, y * u)
    private fun s(w: Float, h: Float) = Size(w * u, h * u)
    private fun r(radius: Float) = CornerRadius(radius * u, radius * u)
    private fun stroke(width: Float) = Stroke(width = width * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun draw(id: ToolActionId) = when (id) {
        ToolActionId.Scan -> scan()
        ToolActionId.Import -> imagesToPdf()
        ToolActionId.PdfSign -> sign()
        ToolActionId.PdfMerge -> merge()
        ToolActionId.PdfSplit -> split()
        ToolActionId.PdfCompress -> compress()
        ToolActionId.PdfToImages -> pdfToImages()
        ToolActionId.PdfPassword -> password()
        ToolActionId.PdfWatermark -> watermark()
        ToolActionId.PdfReader -> read()
        ToolActionId.PdfImport -> addToLibrary()
        ToolActionId.Qr -> qr()
        ToolActionId.ImageText -> imageToText()
        ToolActionId.Signatures -> signatures()
    }

    // -----------------------------------------------------------------------------------------
    // Building blocks
    // -----------------------------------------------------------------------------------------

    /** A sheet of paper with a folded corner and [lines] rows of grey "text". */
    private fun page(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        lines: Int = 0,
        lineColor: Color = c.line,
        badge: Boolean = false,
    ) = with(scope) {
        val f = minOf(w, h) * 0.3f
        val body = Path().apply {
            moveTo(x * u, y * u)
            lineTo((x + w - f) * u, y * u)
            lineTo((x + w) * u, (y + f) * u)
            lineTo((x + w) * u, (y + h) * u)
            lineTo(x * u, (y + h) * u)
            close()
        }
        drawPath(body, c.paper)
        drawPath(body, c.edge, style = stroke(1.1f))
        val fold = Path().apply {
            moveTo((x + w - f) * u, y * u)
            lineTo((x + w - f) * u, (y + f) * u)
            lineTo((x + w) * u, (y + f) * u)
            close()
        }
        drawPath(fold, c.line)
        drawPath(fold, c.edge, style = stroke(1.1f))
        if (badge) {
            // A "PDF" label strip: a filled tab on the page's top-left.
            drawRoundRect(c.accent, o(x + w * 0.16f, y + h * 0.16f), s(w * 0.4f, 3.2f), r(1.2f))
        }
        val first = y + h * (if (badge) 0.42f else 0.3f)
        val gap = (h * 0.62f) / (lines + 1).coerceAtLeast(3)
        for (i in 0 until lines) {
            val ly = first + i * gap
            if (ly > y + h - 3f) break
            val end = if (i == lines - 1 && lines > 1) 0.6f else 0.82f
            drawLine(lineColor, o(x + w * 0.18f, ly), o(x + w * end, ly), strokeWidth = 1.5f * u, cap = StrokeCap.Round)
        }
    }

    /** A photo: framed card with a sun and hills. */
    private fun photo(x: Float, y: Float, w: Float, h: Float) = with(scope) {
        drawRoundRect(c.paper, o(x, y), s(w, h), r(2f))
        drawRoundRect(c.edge, o(x, y), s(w, h), r(2f), style = stroke(1.1f))
        val i = 2f
        drawRoundRect(c.accentSoft, o(x + i, y + i), s(w - 2 * i, h - 2 * i), r(1f))
        drawCircle(c.accent, radius = h * 0.11f * u, center = o(x + w * 0.72f, y + h * 0.34f))
        val hills = Path().apply {
            moveTo((x + i) * u, (y + h - i) * u)
            lineTo((x + w * 0.36f) * u, (y + h * 0.46f) * u)
            lineTo((x + w * 0.56f) * u, (y + h * 0.72f) * u)
            lineTo((x + w * 0.7f) * u, (y + h * 0.58f) * u)
            lineTo((x + w - i) * u, (y + h - i) * u)
            close()
        }
        drawPath(hills, c.accent)
    }

    /** Right-pointing arrow from [x1] to [x2] at height [y]. */
    private fun arrow(x1: Float, x2: Float, y: Float, color: Color = c.accent) = with(scope) {
        drawLine(color, o(x1, y), o(x2, y), strokeWidth = 2f * u, cap = StrokeCap.Round)
        val head = Path().apply {
            moveTo((x2 - 3f) * u, (y - 3f) * u)
            lineTo(x2 * u, y * u)
            lineTo((x2 - 3f) * u, (y + 3f) * u)
        }
        drawPath(head, color, style = stroke(2f))
    }

    private fun arrowHead(x: Float, y: Float, angleDeg: Float) = with(scope) {
        rotate(angleDeg, pivot = o(x, y)) {
            val head = Path().apply {
                moveTo((x - 3f) * u, (y - 3f) * u)
                lineTo(x * u, y * u)
                lineTo((x - 3f) * u, (y + 3f) * u)
            }
            drawPath(head, c.accent, style = stroke(2f))
        }
    }

    /** A handwritten signature stroke inside the box. */
    private fun squiggle(x: Float, y: Float, w: Float, h: Float, width: Float = 1.9f) = with(scope) {
        val p = Path().apply {
            moveTo(x * u, (y + h * 0.7f) * u)
            cubicTo((x + w * 0.08f) * u, y * u, (x + w * 0.22f) * u, y * u, (x + w * 0.2f) * u, (y + h * 0.55f) * u)
            cubicTo((x + w * 0.18f) * u, (y + h) * u, (x + w * 0.34f) * u, (y + h) * u, (x + w * 0.42f) * u, (y + h * 0.4f) * u)
            cubicTo((x + w * 0.48f) * u, (y + h * 0.05f) * u, (x + w * 0.56f) * u, (y + h * 0.9f) * u, (x + w * 0.64f) * u, (y + h * 0.6f) * u)
            cubicTo((x + w * 0.72f) * u, (y + h * 0.3f) * u, (x + w * 0.8f) * u, (y + h * 0.75f) * u, (x + w) * u, (y + h * 0.35f) * u)
        }
        drawPath(p, c.accent, style = stroke(width))
    }

    private fun corner(x: Float, y: Float, dx: Float, dy: Float, color: Color, width: Float) = with(scope) {
        val p = Path().apply {
            moveTo(x * u, (y + dy) * u)
            lineTo(x * u, y * u)
            lineTo((x + dx) * u, y * u)
        }
        drawPath(p, color, style = stroke(width))
    }

    private fun brackets(l: Float, t: Float, rgt: Float, b: Float, arm: Float, color: Color, width: Float) {
        corner(l, t, arm, arm, color, width)
        corner(rgt, t, -arm, arm, color, width)
        corner(l, b, arm, -arm, color, width)
        corner(rgt, b, -arm, -arm, color, width)
    }

    // -----------------------------------------------------------------------------------------
    // Tools
    // -----------------------------------------------------------------------------------------

    /** Camera framing: a page inside detection corners with a scan line sweeping over it. */
    private fun scan() = with(scope) {
        page(23f, 9f, 18f, 29f, lines = 4)
        brackets(16f, 3f, 48f, 45f, arm = 6f, color = c.accent, width = 2.4f)
        drawRect(c.accentSoft, o(19f, 18f), s(26f, 8f))
        drawLine(c.accent, o(18.5f, 26f), o(45.5f, 26f), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
    }

    /** Photos turn into a PDF page. */
    private fun imagesToPdf() = with(scope) {
        rotate(-10f, pivot = o(16f, 20f)) { photo(5f, 11f, 21f, 16f) }
        photo(9f, 21f, 21f, 16f)
        arrow(33f, 39f, 24f)
        page(42f, 9f, 18f, 29f, lines = 3, badge = true)
    }

    /** A page with a signature on the line and a pen. */
    private fun sign() = with(scope) {
        page(14f, 4f, 30f, 40f, lines = 3)
        drawLine(c.line, o(18f, 38f), o(40f, 38f), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
        squiggle(18f, 29f, 20f, 8f)
        // Pen
        drawLine(c.accent, o(55f, 14f), o(44f, 29f), strokeWidth = 3.6f * u, cap = StrokeCap.Round)
        drawLine(c.edge, o(44f, 29f), o(41.6f, 32.4f), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
    }

    /** Two pages flow into one. */
    private fun merge() = with(scope) {
        page(3f, 3f, 15f, 19f, lines = 2)
        page(3f, 26f, 15f, 19f, lines = 2)
        val top = Path().apply {
            moveTo(20.5f * u, 12.5f * u)
            cubicTo(28f * u, 12.5f * u, 27f * u, 24f * u, 35f * u, 24f * u)
        }
        val bottom = Path().apply {
            moveTo(20.5f * u, 35.5f * u)
            cubicTo(28f * u, 35.5f * u, 27f * u, 24f * u, 35f * u, 24f * u)
        }
        drawPath(top, c.accent, style = stroke(2f))
        drawPath(bottom, c.accent, style = stroke(2f))
        arrowHead(36f, 24f, 0f)
        page(40f, 6f, 21f, 36f, lines = 5)
    }

    /** One page cut into two. */
    private fun split() = with(scope) {
        page(3f, 6f, 21f, 36f, lines = 5)
        drawLine(
            c.accent,
            o(1.5f, 24f),
            o(25.5f, 24f),
            strokeWidth = 1.8f * u,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.6f * u, 2.4f * u)),
        )
        val top = Path().apply {
            moveTo(28f * u, 24f * u)
            cubicTo(35f * u, 24f * u, 34f * u, 12.5f * u, 41f * u, 12.5f * u)
        }
        val bottom = Path().apply {
            moveTo(28f * u, 24f * u)
            cubicTo(35f * u, 24f * u, 34f * u, 35.5f * u, 41f * u, 35.5f * u)
        }
        drawPath(top, c.accent, style = stroke(2f))
        drawPath(bottom, c.accent, style = stroke(2f))
        arrowHead(42f, 12.5f, 0f)
        arrowHead(42f, 35.5f, 0f)
        page(46f, 3f, 15f, 19f, lines = 2)
        page(46f, 26f, 15f, 19f, lines = 2)
    }

    /** A page squeezed from both sides. */
    private fun compress() = with(scope) {
        page(23f, 8f, 18f, 31f, lines = 4)
        // Left arrow pointing in, with an end stop.
        drawLine(c.accent, o(5f, 17f), o(5f, 31f), strokeWidth = 2f * u, cap = StrokeCap.Round)
        arrow(7f, 18.5f, 24f)
        drawLine(c.accent, o(59f, 17f), o(59f, 31f), strokeWidth = 2f * u, cap = StrokeCap.Round)
        drawLine(c.accent, o(57f, 24f), o(45.5f, 24f), strokeWidth = 2f * u, cap = StrokeCap.Round)
        arrowHead(45.5f, 24f, 180f)
    }

    /** A page becomes pictures. */
    private fun pdfToImages() = with(scope) {
        page(4f, 8f, 20f, 31f, lines = 4, badge = true)
        arrow(27f, 33f, 24f)
        photo(40f, 9f, 20f, 16f)
        photo(36f, 18f, 21f, 17f)
    }

    /** A page with a padlock. */
    private fun password() = with(scope) {
        page(12f, 4f, 28f, 40f, lines = 5)
        drawArc(
            color = c.accent,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = o(39f, 19f),
            size = s(12f, 12f),
            style = stroke(2.6f),
        )
        drawLine(c.accent, o(39f, 25f), o(39f, 28f), strokeWidth = 2.6f * u)
        drawLine(c.accent, o(51f, 25f), o(51f, 28f), strokeWidth = 2.6f * u)
        drawRoundRect(c.accent, o(35.5f, 27f), s(19f, 15f), r(3f))
        drawCircle(c.paper, radius = 1.9f * u, center = o(45f, 33f))
        drawLine(c.paper, o(45f, 33f), o(45f, 37.5f), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
    }

    /** A page with a diagonal stamp across it. */
    private fun watermark() = with(scope) {
        page(16f, 4f, 32f, 40f, lines = 5)
        rotate(-30f, pivot = o(32f, 25f)) {
            drawRoundRect(c.accentSoft, o(19f, 20.5f), s(26f, 9f), r(2f))
            drawRoundRect(c.accent, o(19f, 20.5f), s(26f, 9f), r(2f), style = stroke(1.4f))
            for (i in 0 until 4) {
                val lx = 22.5f + i * 5.2f
                drawLine(c.accent, o(lx, 25f), o(lx + 3f, 25f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
            }
        }
    }

    /** An open book with a bookmark. */
    private fun read() = with(scope) {
        val cover = Path().apply {
            moveTo(5f * u, 13f * u)
            lineTo(5f * u, 40f * u)
            lineTo(32f * u, 43.5f * u)
            lineTo(59f * u, 40f * u)
            lineTo(59f * u, 13f * u)
        }
        drawPath(cover, c.accent, style = stroke(2.2f))
        val left = Path().apply {
            moveTo(8f * u, 8f * u)
            cubicTo(16f * u, 7f * u, 26f * u, 8f * u, 31f * u, 11f * u)
            lineTo(31f * u, 40f * u)
            cubicTo(26f * u, 37f * u, 16f * u, 36f * u, 8f * u, 37f * u)
            close()
        }
        val right = Path().apply {
            moveTo(56f * u, 8f * u)
            cubicTo(48f * u, 7f * u, 38f * u, 8f * u, 33f * u, 11f * u)
            lineTo(33f * u, 40f * u)
            cubicTo(38f * u, 37f * u, 48f * u, 36f * u, 56f * u, 37f * u)
            close()
        }
        drawPath(left, c.paper)
        drawPath(left, c.edge, style = stroke(1.1f))
        drawPath(right, c.paper)
        drawPath(right, c.edge, style = stroke(1.1f))
        for (i in 0 until 4) {
            val ly = 16f + i * 5f
            drawLine(c.line, o(12f, ly), o(if (i == 3) 21f else 27f, ly + 0.8f), strokeWidth = 1.5f * u, cap = StrokeCap.Round)
            drawLine(c.line, o(37f, ly + 0.8f), o(if (i == 3) 44f else 52f, ly), strokeWidth = 1.5f * u, cap = StrokeCap.Round)
        }
        val ribbon = Path().apply {
            moveTo(47f * u, 7.5f * u)
            lineTo(52f * u, 7.6f * u)
            lineTo(52f * u, 18f * u)
            lineTo(49.5f * u, 15.6f * u)
            lineTo(47f * u, 18f * u)
            close()
        }
        drawPath(ribbon, c.accent)
    }

    /** A page dropping into a folder. */
    private fun addToLibrary() = with(scope) {
        val back = c.accent.copy(alpha = 0.45f)
        drawRoundRect(back, o(8f, 9f), s(18f, 9f), r(2.5f))
        drawRoundRect(back, o(8f, 13f), s(48f, 31f), r(3f))
        page(19f, 3f, 26f, 30f, lines = 3)
        drawRoundRect(c.accent, o(6f, 22f), s(52f, 23f), r(3.5f))
        drawLine(c.paper, o(32f, 28.5f), o(32f, 38.5f), strokeWidth = 2.6f * u, cap = StrokeCap.Round)
        drawLine(c.paper, o(27f, 33.5f), o(37f, 33.5f), strokeWidth = 2.6f * u, cap = StrokeCap.Round)
    }

    /** A QR code inside scanner corners. */
    private fun qr() = with(scope) {
        val x = 18f
        val y = 10f
        val n = 28f
        drawRoundRect(c.paper, o(x - 2f, y - 2f), s(n + 4f, n + 4f), r(2f))
        fun finder(fx: Float, fy: Float) {
            drawRoundRect(c.accent, o(fx + 0.9f, fy + 0.9f), s(8.2f, 8.2f), r(1.6f), style = stroke(1.8f))
            drawRoundRect(c.accent, o(fx + 3f, fy + 3f), s(4f, 4f), r(0.8f))
        }
        finder(x, y)
        finder(x + n - 10f, y)
        finder(x, y + n - 10f)
        val modules = listOf(
            13f to 1f, 13f to 5f, 16f to 3f,
            1f to 13f, 5f to 13f, 3f to 16f,
            13f to 13f, 16f to 16f, 20f to 13f, 24f to 13f,
            13f to 20f, 20f to 20f, 24f to 24f, 16f to 24f, 20f to 24f, 13f to 24f, 24f to 17f,
        )
        modules.forEach { (mx, my) ->
            drawRoundRect(c.accent, o(x + mx, y + my), s(2.8f, 2.8f), r(0.6f))
        }
        brackets(11f, 3f, 53f, 45f, arm = 6f, color = c.edge, width = 2f)
    }

    /** A photo turns into selectable text. */
    private fun imageToText() = with(scope) {
        photo(3f, 13f, 24f, 20f)
        arrow(30f, 36f, 23f)
        val x = 39f
        val y = 7f
        val w = 22f
        val h = 33f
        drawRoundRect(c.paper, o(x, y), s(w, h), r(2.5f))
        drawRoundRect(c.edge, o(x, y), s(w, h), r(2.5f), style = stroke(1.1f))
        // "T" glyph
        drawLine(c.accent, o(x + 4f, y + 6f), o(x + 12f, y + 6f), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
        drawLine(c.accent, o(x + 8f, y + 6f), o(x + 8f, y + 13f), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
        // Selected line
        drawRoundRect(c.accentSoft, o(x + 2.5f, y + 16.5f), s(w - 5f, 4f), r(1f))
        listOf(18.5f, 24.5f, 29.5f).forEachIndexed { i, ly ->
            val end = if (i == 2) x + 13f else x + w - 4f
            drawLine(if (i == 0) c.accent else c.line, o(x + 4f, y + ly), o(end, y + ly), strokeWidth = 1.5f * u, cap = StrokeCap.Round)
        }
    }

    /** A small stack of saved signature cards. */
    private fun signatures() = with(scope) {
        drawRoundRect(c.paper.copy(alpha = 0.7f), o(15f, 5f), s(40f, 22f), r(3f))
        drawRoundRect(c.edge, o(15f, 5f), s(40f, 22f), r(3f), style = stroke(1.1f))
        drawRoundRect(c.paper, o(8f, 14f), s(44f, 28f), r(3f))
        drawRoundRect(c.edge, o(8f, 14f), s(44f, 28f), r(3f), style = stroke(1.1f))
        squiggle(13f, 21f, 30f, 11f, width = 2.1f)
        drawLine(c.line, o(13f, 36.5f), o(47f, 36.5f), strokeWidth = 1.2f * u, cap = StrokeCap.Round)
        drawCircle(c.accent, radius = 5f * u, center = o(53f, 38f))
        drawLine(c.paper, o(53f, 35.2f), o(53f, 40.8f), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
        drawLine(c.paper, o(50.2f, 38f), o(55.8f, 38f), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
    }
}
