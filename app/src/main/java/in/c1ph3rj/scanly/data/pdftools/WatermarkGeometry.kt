package `in`.c1ph3rj.scanly.data.pdftools

import `in`.c1ph3rj.scanly.domain.model.WatermarkLayout
import `in`.c1ph3rj.scanly.domain.model.WatermarkSize
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure layout maths for text watermarks, in *visual* page space: the page as a reader sees it
 * (after /Rotate), origin bottom-left, y up. [PdfWatermarkStamper] maps the result into PDF
 * user space. Kept free of PDFBox/Android so it is unit-testable on the JVM.
 */
internal object WatermarkGeometry {

    /** A stamp instance: text centre in visual space; the text runs along [WatermarkPlan.angleDegrees]. */
    data class Placement(val x: Float, val y: Float)

    data class Plan(
        val fontSize: Float,
        val characterSpacing: Float,
        /** Advance width of the whole string at [fontSize], including tracking between glyphs. */
        val textWidth: Float,
        val angleDegrees: Float,
        val placements: List<Placement>,
    )

    /**
     * @param unitWidth string advance width at 1pt with no tracking (font units / 1000).
     * @param glyphCount number of glyphs, for tracking.
     * @param capHeight cap height as a fraction of the font size (≈0.72 for Helvetica).
     */
    fun plan(
        pageWidth: Float,
        pageHeight: Float,
        unitWidth: Float,
        glyphCount: Int,
        capHeight: Float,
        layout: WatermarkLayout,
        size: WatermarkSize,
        angleDegrees: Float,
    ): Plan {
        val width = pageWidth.coerceAtLeast(1f)
        val height = pageHeight.coerceAtLeast(1f)
        val trackingPerPoint = trackingFactor(layout)
        // Advance width per point of font size, tracking included.
        val perPoint = unitWidth.coerceAtLeast(0.01f) + trackingPerPoint * (glyphCount - 1).coerceAtLeast(0)
        val radians = Math.toRadians(angleDegrees.toDouble())
        val absCos = abs(cos(radians)).toFloat()
        val absSin = abs(sin(radians)).toFloat()
        val boxHeight = capHeight.coerceIn(0.5f, 1f)

        return when (layout) {
            WatermarkLayout.CENTERED -> {
                // Largest size whose rotated text box fits inside the page with a margin, then
                // scaled by the chosen size so "Large" nearly fills and "Small" is a modest stamp.
                val usableW = width * CenteredFill
                val usableH = height * CenteredFill
                val fitByWidth = usableW / (perPoint * absCos + boxHeight * absSin)
                val fitByHeight = usableH / (perPoint * absSin + boxHeight * absCos)
                val maxFit = minOf(fitByWidth, fitByHeight)
                val fontSize = (maxFit * centeredScale(size)).coerceIn(MinFontSize, MaxCenteredFontSize)
                    .coerceAtMost(maxFit.coerceAtLeast(MinFontSize))
                Plan(
                    fontSize = fontSize,
                    characterSpacing = fontSize * trackingPerPoint,
                    textWidth = perPoint * fontSize,
                    angleDegrees = angleDegrees,
                    placements = listOf(Placement(width / 2f, height / 2f)),
                )
            }
            WatermarkLayout.REPEATED -> {
                val shortEdge = minOf(width, height)
                // Size from the paper, capped so long stamps still repeat across each row.
                val byPaper = shortEdge * tiledScale(size)
                val byLength = (shortEdge * 0.62f) / perPoint
                val fontSize = minOf(byPaper, byLength).coerceIn(MinFontSize, MaxTiledFontSize)
                val textWidth = perPoint * fontSize
                Plan(
                    fontSize = fontSize,
                    characterSpacing = fontSize * trackingPerPoint,
                    textWidth = textWidth,
                    angleDegrees = angleDegrees,
                    placements = tile(width, height, textWidth, fontSize, angleDegrees),
                )
            }
        }
    }

    /**
     * Lays tiles on a grid aligned with the text direction (rows run parallel to the text),
     * alternate rows offset by half a step, centred on the page so the pattern is symmetric.
     * Only tiles whose box touches the page are kept.
     */
    internal fun tile(
        width: Float,
        height: Float,
        textWidth: Float,
        fontSize: Float,
        angleDegrees: Float,
    ): List<Placement> {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val dirX = cos(radians).toFloat()
        val dirY = sin(radians).toFloat()
        // Perpendicular (rotated +90°).
        val perpX = -dirY
        val perpY = dirX

        var stepAlong = textWidth + fontSize * TileGapEm
        var stepAcross = fontSize * TileRowPitchEm
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = hypot(width, height) / 2f

        // Grow spacing if a pathological input would exceed the tile budget.
        while (true) {
            val cols = ceil((radius + textWidth) / stepAlong).toInt() + 1
            val rows = ceil((radius + fontSize) / stepAcross).toInt() + 1
            if ((2 * cols + 1) * (2 * rows + 1) <= MaxCandidateTiles) break
            stepAlong *= 1.25f
            stepAcross *= 1.25f
        }

        val cols = ceil((radius + textWidth) / stepAlong).toInt() + 1
        val rows = ceil((radius + fontSize) / stepAcross).toInt() + 1
        val halfW = textWidth / 2f
        val halfH = fontSize / 2f
        // Half-extent of a tile's axis-aligned box, used for a cheap "touches the page" test.
        val extentX = halfW * abs(dirX) + halfH * abs(perpX)
        val extentY = halfW * abs(dirY) + halfH * abs(perpY)

        return buildList {
            for (row in -rows..rows) {
                val offset = if (row % 2 == 0) 0f else stepAlong / 2f
                for (col in -cols..cols) {
                    val along = col * stepAlong + offset
                    val across = row * stepAcross
                    val x = centerX + along * dirX + across * perpX
                    val y = centerY + along * dirY + across * perpY
                    val touches = x + extentX >= 0f && x - extentX <= width &&
                        y + extentY >= 0f && y - extentY <= height
                    if (touches) add(Placement(x, y))
                }
            }
        }.take(MaxTiles)
    }

    /**
     * Maps a visual-space point/angle to PDF user space for a page whose crop box starts at
     * ([lowerX], [lowerY]) with unrotated size [boxWidth]×[boxHeight] and /Rotate [rotation].
     * Viewers rotate the page clockwise by [rotation]; we apply the inverse.
     */
    fun toUserSpace(
        x: Float,
        y: Float,
        lowerX: Float,
        lowerY: Float,
        boxWidth: Float,
        boxHeight: Float,
        rotation: Int,
    ): Pair<Float, Float> = when (normalizeRotation(rotation)) {
        90 -> (lowerX + boxWidth - y) to (lowerY + x)
        180 -> (lowerX + boxWidth - x) to (lowerY + boxHeight - y)
        270 -> (lowerX + y) to (lowerY + boxHeight - x)
        else -> (lowerX + x) to (lowerY + y)
    }

    /** Visual angle → user-space angle for a page rotated clockwise by [rotation] degrees. */
    fun toUserAngle(angleDegrees: Float, rotation: Int): Float =
        angleDegrees + normalizeRotation(rotation)

    /** Visual (as displayed) size of a page box. */
    fun visualSize(boxWidth: Float, boxHeight: Float, rotation: Int): Pair<Float, Float> =
        when (normalizeRotation(rotation)) {
            90, 270 -> boxHeight to boxWidth
            else -> boxWidth to boxHeight
        }

    fun normalizeRotation(rotation: Int): Int {
        val r = ((rotation % 360) + 360) % 360
        // /Rotate must be a multiple of 90; snap anything else to the nearest quarter turn.
        return ((r + 45) / 90 % 4) * 90
    }

    private fun trackingFactor(layout: WatermarkLayout): Float = when (layout) {
        WatermarkLayout.CENTERED -> 0.04f
        WatermarkLayout.REPEATED -> 0.02f
    }

    private fun centeredScale(size: WatermarkSize): Float = when (size) {
        WatermarkSize.SMALL -> 0.6f
        WatermarkSize.MEDIUM -> 0.82f
        WatermarkSize.LARGE -> 1.0f
    }

    private fun tiledScale(size: WatermarkSize): Float = when (size) {
        WatermarkSize.SMALL -> 0.036f
        WatermarkSize.MEDIUM -> 0.05f
        WatermarkSize.LARGE -> 0.068f
    }

    private const val CenteredFill = 0.86f
    private const val MinFontSize = 8f
    private const val MaxCenteredFontSize = 260f
    private const val MaxTiledFontSize = 64f
    /** Gap between tiles along a row, in ems. */
    private const val TileGapEm = 2.2f
    /** Distance between rows, in ems. */
    private const val TileRowPitchEm = 4.2f
    private const val MaxCandidateTiles = 900
    private const val MaxTiles = 160
}
