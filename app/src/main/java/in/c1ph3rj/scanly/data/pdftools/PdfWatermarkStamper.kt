package `in`.c1ph3rj.scanly.data.pdftools

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import `in`.c1ph3rj.scanly.domain.model.WatermarkOptions
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws a text watermark onto PDF pages.
 *
 * - Layout is computed in the page's *visual* space (after /Rotate) by [WatermarkGeometry], so
 *   stamps on rotated scans read upright and stay centred.
 * - Text uses Helvetica Bold when it can encode every character; otherwise a bold system font
 *   is embedded (subset) so symbols like ₹ and accented / Greek / Cyrillic text still work.
 *   Scripts that need complex shaping are rejected up front with a clear message, because
 *   PDFBox cannot shape them and would draw broken glyph sequences.
 * - The mark is tagged as an /Artifact so screen readers and text extraction skip it.
 */
internal class PdfWatermarkStamper private constructor(
    private val font: PDFont,
    private val text: String,
    private val options: WatermarkOptions,
) {
    private val unitWidth: Float = font.getStringWidth(text) / 1000f
    private val glyphCount: Int = text.codePointCount(0, text.length)
    private val capHeight: Float = resolveCapHeight(font)

    fun stamp(document: PDDocument, page: PDPage) {
        val box = page.cropBox ?: page.mediaBox
        val rotation = page.rotation
        val (visualWidth, visualHeight) = WatermarkGeometry.visualSize(box.width, box.height, rotation)
        val plan = WatermarkGeometry.plan(
            pageWidth = visualWidth,
            pageHeight = visualHeight,
            unitWidth = unitWidth,
            glyphCount = glyphCount,
            capHeight = capHeight,
            layout = options.layout,
            size = options.size,
            angleDegrees = options.angleDegrees,
        )
        val userAngle = Math.toRadians(WatermarkGeometry.toUserAngle(plan.angleDegrees, rotation).toDouble())
        val cos = cos(userAngle).toFloat()
        val sin = sin(userAngle).toFloat()
        // Centre the glyph box on the placement: half the advance back, half the cap height down.
        val halfWidth = plan.textWidth / 2f
        val halfCap = plan.fontSize * capHeight / 2f

        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.beginMarkedContent(COSName.getPDFName("Artifact"))
            stream.saveGraphicsState()
            stream.setGraphicsStateParameters(
                PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = options.opacity.coerceIn(MinOpacity, MaxOpacity)
                },
            )
            stream.setNonStrokingColor(options.color.red, options.color.green, options.color.blue)
            stream.beginText()
            stream.setFont(font, plan.fontSize)
            stream.setCharacterSpacing(plan.characterSpacing)
            plan.placements.forEach { placement ->
                val (ux, uy) = WatermarkGeometry.toUserSpace(
                    x = placement.x,
                    y = placement.y,
                    lowerX = box.lowerLeftX,
                    lowerY = box.lowerLeftY,
                    boxWidth = box.width,
                    boxHeight = box.height,
                    rotation = rotation,
                )
                // Origin = centre − rotate(halfWidth, halfCap): start of the baseline.
                val originX = ux - (halfWidth * cos - halfCap * sin)
                val originY = uy - (halfWidth * sin + halfCap * cos)
                stream.setTextMatrix(Matrix(cos, sin, -sin, cos, originX, originY))
                stream.showText(text)
            }
            stream.endText()
            stream.restoreGraphicsState()
            stream.endMarkedContent()
        }
    }

    companion object {
        private const val MinOpacity = 0.05f
        private const val MaxOpacity = 0.85f

        /** Bold, broad-coverage system fonts tried in order when Helvetica can't encode the text. */
        private val SystemFontCandidates = listOf(
            "/system/fonts/Roboto-Bold.ttf",
            "/system/fonts/NotoSans-Bold.ttf",
            "/system/fonts/DroidSans-Bold.ttf",
            "/system/fonts/RobotoStatic-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/DroidSans.ttf",
        )

        /**
         * Prepares a stamper for [document]. Throws [IllegalArgumentException] with a user-facing
         * message when the text can't be drawn faithfully.
         */
        fun create(document: PDDocument, options: WatermarkOptions): PdfWatermarkStamper {
            val text = normalizeWatermarkText(options.text)
            require(text.isNotEmpty()) { "Enter watermark text." }
            require(!needsComplexShaping(text)) {
                "Watermarks support Latin, Greek and Cyrillic text, digits and symbols. " +
                    "Try the text in English."
            }
            val font = resolveFont(document, text)
                ?: throw IllegalArgumentException(
                    "Some characters in the watermark can't be drawn. Remove emoji or unusual symbols.",
                )
            return PdfWatermarkStamper(font, text, options)
        }

        private fun resolveFont(document: PDDocument, text: String): PDFont? {
            val helvetica = PDType1Font.HELVETICA_BOLD
            if (canEncode(helvetica, text)) return helvetica
            for (path in SystemFontCandidates) {
                val file = File(path)
                if (!file.isFile) continue
                val font = runCatching { PDType0Font.load(document, file) }.getOrNull() ?: continue
                if (canEncode(font, text)) return font
            }
            return null
        }

        private fun canEncode(font: PDFont, text: String): Boolean =
            runCatching {
                font.encode(text)
                font.getStringWidth(text)
            }.isSuccess

        private fun resolveCapHeight(font: PDFont): Float {
            val fromDescriptor = font.fontDescriptor?.capHeight?.takeIf { it > 0f }?.div(1000f)
            return (fromDescriptor ?: 0.72f).coerceIn(0.5f, 0.9f)
        }
    }
}

/** Collapses whitespace/newlines (PDF text has no line breaks) and swaps typographic quirks. */
internal fun normalizeWatermarkText(raw: String): String =
    raw.replace(Regex("\\s+"), " ").trim()

/**
 * True for scripts that need OpenType shaping (Indic, Arabic, Hebrew, Thai, Khmer, Myanmar…).
 * PDFBox draws code points in logical order without shaping, which renders these incorrectly.
 */
internal fun needsComplexShaping(text: String): Boolean =
    text.codePoints().anyMatch { cp ->
        cp in 0x0590..0x08FF || // Hebrew, Arabic, Syriac, Thaana, NKo, Samaritan, Arabic Ext
            cp in 0x0900..0x0DFF || // Devanagari … Sinhala (all Indic)
            cp in 0x0E00..0x0FFF || // Thai, Lao, Tibetan
            cp in 0x1000..0x109F || // Myanmar
            cp in 0x1780..0x17FF || // Khmer
            cp in 0xA8E0..0xA8FF || // Devanagari Extended
            cp in 0xFB1D..0xFDFF || // Hebrew/Arabic presentation forms
            cp in 0xFE70..0xFEFF // Arabic presentation forms-B
    }
