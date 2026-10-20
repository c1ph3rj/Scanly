package `in`.c1ph3rj.scanly.data.storage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates the cover thumbnail for documents without a scanned page yet.
 *
 * Layout: a dark brand-tinted field with a faint dot grid, a stack of two tilted "sheets" with
 * a scan beam crossing them, an initials badge + wordmark header, and the title set large at the
 * bottom. The palette is picked from the title so each document keeps a stable look.
 */
@Singleton
class DocumentCoverArtFactory @Inject constructor() {
    fun create(title: String): Bitmap {
        val bitmap = Bitmap.createBitmap(COVER_WIDTH, COVER_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = palettes[Math.floorMod(title.hashCode(), palettes.size)]
        val displayTitle = DocumentPresentationFormatter.normalizeTitle(title)

        drawBackground(canvas, palette)
        drawDotGrid(canvas)
        drawSheetStack(canvas, palette)
        drawHeader(canvas, palette, DocumentPresentationFormatter.initials(title))
        drawTitle(canvas, displayTitle)
        drawFooter(canvas)

        return bitmap
    }

    private fun drawBackground(canvas: Canvas, palette: CoverPalette) {
        val width = COVER_WIDTH.toFloat()
        val height = COVER_HEIGHT.toFloat()

        canvas.drawRect(
            0f,
            0f,
            width,
            height,
            Paint().apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    0f,
                    height,
                    palette.backgroundTop,
                    palette.backgroundBottom,
                    Shader.TileMode.CLAMP,
                )
            },
        )

        // Two soft glows: a strong one top-right and a weaker one bottom-left, for depth.
        canvas.drawRect(0f, 0f, width, height, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.92f,
                height * 0.04f,
                width * 0.85f,
                withAlpha(palette.glow, 120),
                withAlpha(palette.glow, 0),
                Shader.TileMode.CLAMP,
            )
        })
        canvas.drawRect(0f, 0f, width, height, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.02f,
                height * 0.98f,
                width * 0.7f,
                withAlpha(palette.glow, 56),
                withAlpha(palette.glow, 0),
                Shader.TileMode.CLAMP,
            )
        })
    }

    private fun drawDotGrid(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(Color.WHITE, 24) }
        var y = DOT_SPACING / 2f
        while (y < COVER_HEIGHT) {
            var x = DOT_SPACING / 2f
            while (x < COVER_WIDTH) {
                canvas.drawCircle(x, y, DOT_RADIUS, paint)
                x += DOT_SPACING
            }
            y += DOT_SPACING
        }
    }

    private fun drawSheetStack(canvas: Canvas, palette: CoverPalette) {
        val centerX = COVER_WIDTH / 2f
        val sheetWidth = 210f
        val sheetHeight = 200f
        val sheetTop = 120f
        val sheetCenterY = sheetTop + sheetHeight / 2f

        val backSheet = RectF(
            centerX - sheetWidth / 2f,
            sheetTop,
            centerX + sheetWidth / 2f,
            sheetTop + sheetHeight,
        )
        canvas.save()
        canvas.rotate(-7f, centerX, sheetCenterY)
        drawGlassSheet(canvas, backSheet)
        canvas.restore()

        val frontSheet = RectF(backSheet).apply { offset(6f, 4f) }
        canvas.save()
        canvas.rotate(4f, centerX, sheetCenterY)
        drawPaperSheet(canvas, palette, frontSheet)
        canvas.restore()

        drawScanBeam(canvas, palette, centerY = sheetCenterY)
    }

    private fun drawGlassSheet(canvas: Canvas, rect: RectF) {
        canvas.drawRoundRect(
            rect,
            SHEET_RADIUS,
            SHEET_RADIUS,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(Color.WHITE, 30) },
        )
        canvas.drawRoundRect(
            rect,
            SHEET_RADIUS,
            SHEET_RADIUS,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = withAlpha(Color.WHITE, 80)
            },
        )
    }

    private fun drawPaperSheet(canvas: Canvas, palette: CoverPalette, rect: RectF) {
        canvas.drawRoundRect(
            rect,
            SHEET_RADIUS,
            SHEET_RADIUS,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER_COLOR },
        )

        val left = rect.left + 26f
        val top = rect.top + 26f
        canvas.drawRoundRect(
            RectF(left, top, left + 28f, top + 28f),
            8f,
            8f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent },
        )

        // Placeholder "text" lines. The last one uses the accent, like a signature or stamp.
        val lineWidths = floatArrayOf(1f, 0.82f, 0.92f, 0.55f, 0.7f)
        val availableWidth = rect.width() - 52f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(palette.sheetInk, 70) }
        var lineTop = rect.top + 74f
        lineWidths.forEachIndexed { index, fraction ->
            val isLast = index == lineWidths.lastIndex
            if (isLast) {
                linePaint.color = palette.accent
            }
            canvas.drawRoundRect(
                RectF(left, lineTop, left + availableWidth * fraction, lineTop + 12f),
                6f,
                6f,
                linePaint,
            )
            lineTop += 22f
        }
    }

    private fun drawScanBeam(canvas: Canvas, palette: CoverPalette, centerY: Float) {
        val halfBand = 20f
        val width = COVER_WIDTH.toFloat()

        canvas.drawRect(
            0f,
            centerY - halfBand,
            width,
            centerY + halfBand,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    centerY - halfBand,
                    0f,
                    centerY + halfBand,
                    intArrayOf(
                        withAlpha(palette.accent, 0),
                        withAlpha(palette.accent, 110),
                        withAlpha(palette.accent, 0),
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
        canvas.drawLine(
            0f,
            centerY,
            width,
            centerY,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.accent
                strokeWidth = 2f
            },
        )
    }

    private fun drawHeader(canvas: Canvas, palette: CoverPalette, initials: String) {
        val badge = RectF(PADDING, PADDING, PADDING + 52f, PADDING + 52f)
        canvas.drawRoundRect(
            badge,
            16f,
            16f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent },
        )

        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.onAccent
            textSize = 22f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, 700, false)
        }
        canvas.drawText(
            initials,
            badge.centerX(),
            centeredBaseline(badge.centerY(), badgePaint),
            badgePaint,
        )

        val wordmarkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(Color.WHITE, 180)
            textSize = 18f
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0.22f
            typeface = Typeface.create(Typeface.DEFAULT, 600, false)
        }
        canvas.drawText(
            WORDMARK,
            COVER_WIDTH - PADDING,
            centeredBaseline(badge.centerY(), wordmarkPaint),
            wordmarkPaint,
        )
    }

    private fun drawTitle(canvas: Canvas, title: String) {
        val textWidth = (COVER_WIDTH - PADDING * 2).toInt()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = TITLE_TEXT_SIZE
            letterSpacing = -0.02f
            typeface = Typeface.create(Typeface.DEFAULT, 600, false)
        }
        val layout = StaticLayout.Builder
            .obtain(title, 0, title.length, titlePaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 0.98f)
            .setIncludePad(false)
            .setMaxLines(TITLE_MAX_LINES)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setEllipsizedWidth(textWidth)
            .build()

        val footerTop = COVER_HEIGHT - PADDING - 56f
        val titleTop = footerTop - layout.height

        // Short accent bar above the title, a small editorial detail.
        val accentBarTop = titleTop - 22f
        canvas.drawRoundRect(
            RectF(PADDING, accentBarTop, PADDING + 44f, accentBarTop + 4f),
            2f,
            2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE },
        )

        canvas.save()
        canvas.translate(PADDING, titleTop)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawFooter(canvas: Canvas) {
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(Color.WHITE, 150)
            textSize = 18f
            letterSpacing = 0.04f
            typeface = Typeface.create(Typeface.DEFAULT, 400, false)
        }
        canvas.drawText(FOOTER_TEXT, PADDING, COVER_HEIGHT - PADDING, footerPaint)
    }

    /** Baseline that visually centres a line of text on [centerY]. */
    private fun centeredBaseline(centerY: Float, paint: Paint): Float {
        val metrics = paint.fontMetrics
        return centerY - (metrics.ascent + metrics.descent) / 2f
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private data class CoverPalette(
        val backgroundTop: Int,
        val backgroundBottom: Int,
        val glow: Int,
        val accent: Int,
        val onAccent: Int,
        val sheetInk: Int,
    )

    private companion object {
        const val COVER_WIDTH = 480
        const val COVER_HEIGHT = 640
        const val PADDING = 44f
        const val SHEET_RADIUS = 22f
        const val DOT_SPACING = 32f
        const val DOT_RADIUS = 1.6f
        const val TITLE_TEXT_SIZE = 46f
        const val TITLE_MAX_LINES = 3
        const val WORDMARK = "SCANLY"
        const val FOOTER_TEXT = "Scanned document"
        val PAPER_COLOR = Color.argb(245, 244, 247, 246)

        // Dark, brand-tinted fields. The accent drives the badge, beam and signature line.
        val palettes = listOf(
            CoverPalette(
                backgroundTop = Color.parseColor("#0F2B28"),
                backgroundBottom = Color.parseColor("#05100F"),
                glow = Color.parseColor("#2DD4BF"),
                accent = Color.parseColor("#2DD4BF"),
                onAccent = Color.parseColor("#04201C"),
                sheetInk = Color.parseColor("#0F3D38"),
            ),
            CoverPalette(
                backgroundTop = Color.parseColor("#14213D"),
                backgroundBottom = Color.parseColor("#070B1A"),
                glow = Color.parseColor("#60A5FA"),
                accent = Color.parseColor("#93C5FD"),
                onAccent = Color.parseColor("#0B1A3A"),
                sheetInk = Color.parseColor("#1E3A8A"),
            ),
            CoverPalette(
                backgroundTop = Color.parseColor("#2A1A17"),
                backgroundBottom = Color.parseColor("#0D0807"),
                glow = Color.parseColor("#FB923C"),
                accent = Color.parseColor("#FDBA74"),
                onAccent = Color.parseColor("#3B1A05"),
                sheetInk = Color.parseColor("#7C2D12"),
            ),
            CoverPalette(
                backgroundTop = Color.parseColor("#1E2420"),
                backgroundBottom = Color.parseColor("#080B09"),
                glow = Color.parseColor("#A3E635"),
                accent = Color.parseColor("#BEF264"),
                onAccent = Color.parseColor("#1A2E05"),
                sheetInk = Color.parseColor("#3F6212"),
            ),
        )
    }
}
