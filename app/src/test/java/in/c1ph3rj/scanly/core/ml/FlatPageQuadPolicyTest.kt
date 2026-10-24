package `in`.c1ph3rj.scanly.core.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FlatPageQuadPolicyTest {
    @Test
    fun flatInvoiceCroppedToStampKeepsFullFrame() {
        val page = Canvas(WIDTH, HEIGHT, PAPER).apply {
            textLines(top = 0.08f, bottom = 0.92f)
            fillQuad(stampQuad, STAMP_RED)
        }

        assertEquals(
            FlatPageDecision.FULL_FRAME_CONTENT_OUTSIDE_QUAD,
            FlatPageQuadPolicy.decide(stampQuad, page.grid()),
        )
        assertEquals(DocumentCornerQuad.FullFrame, FlatPageQuadPolicy.resolve(stampQuad, page.grid()))
    }

    @Test
    fun flatLetterKeepsColouredLetterheadBand() {
        val page = Canvas(WIDTH, HEIGHT, PAPER).apply {
            fillRect(0f, 0f, 1f, 0.12f, LETTERHEAD_BLUE)
            textLines(top = 0.20f, bottom = 0.85f)
        }
        val belowBand = rect(0.01f, 0.12f, 0.99f, 0.99f)

        assertEquals(DocumentCornerQuad.FullFrame, FlatPageQuadPolicy.resolve(belowBand, page.grid()))
    }

    @Test
    fun flatFormKeepsLeftStripWithBoxes() {
        val page = Canvas(WIDTH, HEIGHT, PAPER).apply {
            for (row in 0 until 8) {
                val top = 0.10f + row * 0.10f
                strokeRect(0.04f, top, 0.96f, top + 0.07f, INK)
            }
        }
        val missingLeftStrip = rect(0.10f, 0.02f, 0.98f, 0.98f)

        assertEquals(
            DocumentCornerQuad.FullFrame,
            FlatPageQuadPolicy.resolve(missingLeftStrip, page.grid()),
        )
    }

    @Test
    fun deskPhotoKeepsDetectedQuad() {
        val photo = Canvas(WIDTH, HEIGHT, 0).apply {
            texturedDesk()
            fillQuad(photoPageQuad, PAPER)
        }

        assertFalse(FlatPageQuadPolicy.borderEvidence(photo.grid()).allSidesPlain)
        assertEquals(
            FlatPageDecision.KEEP_DETECTED_QUAD,
            FlatPageQuadPolicy.decide(photoPageQuad, photo.grid()),
        )
        assertEquals(photoPageQuad, FlatPageQuadPolicy.resolve(photoPageQuad, photo.grid()))
    }

    @Test
    fun deskPhotoWithStampStillKeepsDetectedQuad() {
        // A photo whose page carries a stamp: the frame edge is desk, so the quad stays.
        val photo = Canvas(WIDTH, HEIGHT, 0).apply {
            texturedDesk()
            fillQuad(photoPageQuad, PAPER)
            fillQuad(stampQuad, STAMP_RED)
        }

        assertEquals(stampQuad, FlatPageQuadPolicy.resolve(stampQuad, photo.grid()))
    }

    @Test
    fun receiptOnDarkScannerLidKeepsCrop() {
        val receipt = rect(0.30f, 0.10f, 0.70f, 0.90f)
        val scan = Canvas(WIDTH, HEIGHT, SCANNER_LID).apply {
            fillQuad(receipt, PAPER)
            textLines(top = 0.15f, bottom = 0.85f, left = 0.34f, right = 0.66f)
        }

        val border = FlatPageQuadPolicy.borderEvidence(scan.grid())
        assertTrue(border.allSidesPlain)
        assertEquals(0, border.paperSides)
        assertEquals(receipt, FlatPageQuadPolicy.resolve(receipt, scan.grid()))
    }

    @Test
    fun receiptOnWhiteScannerLidKeepsCrop() {
        val receipt = rect(0.30f, 0.10f, 0.70f, 0.90f)
        val scan = Canvas(WIDTH, HEIGHT, WHITE_LID).apply {
            fillQuad(receipt, PAPER)
            textLines(top = 0.15f, bottom = 0.85f, left = 0.34f, right = 0.66f)
        }

        assertEquals(
            FlatPageDecision.KEEP_QUAD_ON_PLAIN_BACKDROP,
            FlatPageQuadPolicy.decide(receipt, scan.grid()),
        )
    }

    @Test
    fun photoOnPlainDeskBelowWallKeepsDetectedQuad() {
        // Smooth two-tone background, as a photo looks after downscaling: plain but not paper.
        val photo = Canvas(WIDTH, HEIGHT, DESK_GREY).apply {
            fillRect(0f, 0f, 1f, 0.15f, WALL_BEIGE)
            fillQuad(photoPageQuad, PAPER)
            textLines(top = 0.25f, bottom = 0.75f, left = 0.25f, right = 0.75f)
        }

        assertEquals(
            FlatPageDecision.KEEP_DETECTED_QUAD,
            FlatPageQuadPolicy.decide(photoPageQuad, photo.grid()),
        )
    }

    @Test
    fun letterheadBandCrossingSideEdgesStillCountsAsPlain() {
        val page = Canvas(WIDTH, HEIGHT, PAPER).apply { fillRect(0f, 0f, 1f, 0.12f, LETTERHEAD_BLUE) }
        val border = FlatPageQuadPolicy.borderEvidence(page.grid())

        assertTrue(border.allSidesPlain)
        assertEquals(3, border.paperSides)
    }

    @Test
    fun plainBandOnPageWithBlankExteriorKeepsFullFrame() {
        // Band touches only the top edge (inset page art), so no ink is found outside the quad:
        // the non-paper top side alone marks the frame edge as printed page.
        val page = Canvas(WIDTH, HEIGHT, PAPER).apply { fillRect(0.06f, 0f, 0.94f, 0.12f, LETTERHEAD_BLUE) }
        val belowBand = rect(0.01f, 0.12f, 0.99f, 0.99f)

        assertEquals(
            FlatPageDecision.FULL_FRAME_NO_BACKGROUND,
            FlatPageQuadPolicy.decide(belowBand, page.grid()),
        )
    }

    @Test
    fun cleanFlatPageWithQuadOnItsMarginsKeepsQuad() {
        // Nothing but blank paper outside the quad: cropping only trims margin.
        val page = Canvas(WIDTH, HEIGHT, PAPER).apply { textLines(top = 0.10f, bottom = 0.90f) }
        val margins = rect(0.05f, 0.05f, 0.95f, 0.95f)

        assertEquals(margins, FlatPageQuadPolicy.resolve(margins, page.grid()))
    }

    @Test
    fun downsampleKeepsThinStrokesAsGreyCells() {
        val canvas = Canvas(40, 40, PAPER).apply { fillRect(0.5f, 0f, 0.525f, 1f, INK) }
        val reduced = canvas.grid().downsample(4)

        assertEquals(10, reduced.width)
        val strokeCell = PixelGrid.red(reduced.rgb(5, 5))
        assertTrue("stroke cell should be grey, was $strokeCell", strokeCell in 150..220)
        assertEquals(PixelGrid.red(PAPER), PixelGrid.red(reduced.rgb(1, 1)))
    }

    private val stampQuad = DocumentCornerQuad(
        topLeft = NormalizedPoint(0.55f, 0.52f),
        topRight = NormalizedPoint(0.86f, 0.46f),
        bottomRight = NormalizedPoint(0.89f, 0.58f),
        bottomLeft = NormalizedPoint(0.58f, 0.64f),
    )

    private val photoPageQuad = DocumentCornerQuad(
        topLeft = NormalizedPoint(0.18f, 0.12f),
        topRight = NormalizedPoint(0.80f, 0.15f),
        bottomRight = NormalizedPoint(0.86f, 0.88f),
        bottomLeft = NormalizedPoint(0.12f, 0.84f),
    )

    private fun rect(left: Float, top: Float, right: Float, bottom: Float) = DocumentCornerQuad(
        topLeft = NormalizedPoint(left, top),
        topRight = NormalizedPoint(right, top),
        bottomRight = NormalizedPoint(right, bottom),
        bottomLeft = NormalizedPoint(left, bottom),
    )

    /** Minimal raster painter for synthetic documents, at analysis-grid resolution. */
    private class Canvas(val width: Int, val height: Int, background: Int) {
        val pixels = IntArray(width * height) { background }

        fun grid() = PixelGrid(width, height, pixels.copyOf())

        fun fillRect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
            for (y in (top * height).toInt() until (bottom * height).toInt().coerceAtMost(height)) {
                for (x in (left * width).toInt() until (right * width).toInt().coerceAtMost(width)) {
                    pixels[y * width + x] = color
                }
            }
        }

        fun strokeRect(left: Float, top: Float, right: Float, bottom: Float, color: Int) {
            val stroke = 1f / width
            fillRect(left, top, right, top + 1f / height, color)
            fillRect(left, bottom - 1f / height, right, bottom, color)
            fillRect(left, top, left + stroke, bottom, color)
            fillRect(right - stroke, top, right, bottom, color)
        }

        /** Grey cells as text lines look after box-averaging. */
        fun textLines(top: Float, bottom: Float, left: Float = 0.08f, right: Float = 0.92f) {
            var y = top
            while (y < bottom) {
                fillRect(left, y, right, y + 0.012f, TEXT_GREY)
                y += 0.035f
            }
        }

        fun fillQuad(quad: DocumentCornerQuad, color: Int) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (contains(quad, (x + 0.5f) / width, (y + 0.5f) / height)) {
                        pixels[y * width + x] = color
                    }
                }
            }
        }

        /** Wood-like desk: vertical lighting falloff plus grain noise. */
        fun texturedDesk() {
            val random = Random(7)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val shade = 150 - (y * 60 / height) + random.nextInt(-18, 19)
                    pixels[y * width + x] = PixelGrid.packRgb(
                        shade.coerceIn(0, 255),
                        (shade * 0.75f).toInt().coerceIn(0, 255),
                        (shade * 0.5f).toInt().coerceIn(0, 255),
                    )
                }
            }
        }

        private fun contains(quad: DocumentCornerQuad, x: Float, y: Float): Boolean {
            val points = quad.orderedPoints().map { it.second }
            return points.indices.all { index ->
                val start = points[index]
                val end = points[(index + 1) % points.size]
                ((end.x - start.x) * (y - start.y)) - ((end.y - start.y) * (x - start.x)) >= 0f
            }
        }
    }

    private companion object {
        const val WIDTH = 120
        const val HEIGHT = 160
        val PAPER = PixelGrid.packRgb(250, 250, 248)
        val INK = PixelGrid.packRgb(20, 20, 20)
        val TEXT_GREY = PixelGrid.packRgb(170, 170, 170)
        val STAMP_RED = PixelGrid.packRgb(210, 40, 40)
        val LETTERHEAD_BLUE = PixelGrid.packRgb(30, 70, 160)
        val SCANNER_LID = PixelGrid.packRgb(24, 24, 26)
        val WHITE_LID = PixelGrid.packRgb(238, 238, 236)
        val DESK_GREY = PixelGrid.packRgb(120, 118, 112)
        val WALL_BEIGE = PixelGrid.packRgb(200, 180, 150)
    }
}
