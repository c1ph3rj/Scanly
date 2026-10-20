package `in`.c1ph3rj.scanly.data.pdftools

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.util.Matrix
import `in`.c1ph3rj.scanly.data.sign.SignInkBitmaps
import `in`.c1ph3rj.scanly.domain.model.SignInk
import `in`.c1ph3rj.scanly.domain.model.SignMark
import `in`.c1ph3rj.scanly.domain.model.SignMarkStyle
import `in`.c1ph3rj.scanly.domain.model.textContent
import kotlin.math.hypot

/**
 * Flattens sign marks into PDF page content. Positions come from [SignGeometry], so marks land
 * where they were placed in the editor whatever the page's /Rotate or crop box. Images keep
 * their transparency (LosslessFactory writes an SMask); text uses Helvetica or an embedded
 * system font, like the watermark tool.
 */
internal class PdfSignatureStamper(private val document: PDDocument) {

    private val images = mutableMapOf<Pair<String, SignInk>, PDImageXObject>()
    private val fonts = mutableMapOf<String, PDFont>()

    /** Validates text marks up front so the user gets a clear message before anything is written. */
    fun validate(marks: List<SignMark>) {
        marks.forEach { mark ->
            val text = mark.textContent?.let(::normalizeWatermarkText) ?: return@forEach
            require(text.isNotEmpty()) { "A text box is empty. Type something or delete it." }
            require(!needsComplexShaping(text)) {
                "Text and dates support Latin, Greek and Cyrillic text, digits and symbols."
            }
        }
    }

    fun stamp(page: PDPage, marks: List<SignMark>) {
        if (marks.isEmpty()) return
        val box = page.cropBox ?: page.mediaBox
        val frame = SignGeometry.PageFrame(
            lowerX = box.lowerLeftX,
            lowerY = box.lowerLeftY,
            boxWidth = box.width,
            boxHeight = box.height,
            rotation = page.rotation,
        )
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            marks.forEach { mark ->
                stream.saveGraphicsState()
                when (mark) {
                    is SignMark.Image -> drawImage(stream, mark, frame)
                    is SignMark.Text -> drawText(stream, mark.text, mark, frame)
                    is SignMark.Date -> drawText(stream, mark.text, mark, frame)
                    is SignMark.Checkbox -> drawCheck(stream, mark, frame)
                }
                stream.restoreGraphicsState()
            }
        }
    }

    private fun drawImage(stream: PDPageContentStream, mark: SignMark.Image, frame: SignGeometry.PageFrame) {
        val image = images.getOrPut(mark.imagePath to mark.ink) {
            val mask = SignInkBitmaps.decodeMask(mark.imagePath)
                ?: throw IllegalArgumentException("A signature image is missing. Remove it and place it again.")
            val tinted = SignInkBitmaps.tinted(mask, mark.ink)
            mask.recycle()
            try {
                LosslessFactory.createFromImage(document, tinted)
            } finally {
                tinted.recycle()
            }
        }
        stream.drawImage(image, SignGeometry.unitSquareToUser(mark.box, frame).toMatrix())
    }

    private fun drawText(
        stream: PDPageContentStream,
        rawText: String,
        mark: SignMark,
        frame: SignGeometry.PageFrame,
    ) {
        val text = normalizeWatermarkText(rawText)
        if (text.isEmpty()) return
        val font = fonts.getOrPut(text) {
            PdfFontResolver.resolve(document, text, PDType1Font.HELVETICA, PdfFontResolver.RegularCandidates)
                ?: throw IllegalArgumentException("Some characters can't be drawn. Remove emoji or unusual symbols.")
        }
        val unit = SignGeometry.unitSquareToUser(mark.box, frame)
        val boxWidth = hypot(unit.a, unit.b)
        val boxHeight = hypot(unit.c, unit.d)
        val fontSize = SignMarkStyle.fontSizeFor(boxWidth, boxHeight, font.getStringWidth(text) / 1000f)
        // Text direction follows the box's u-axis; the baseline sits at the shared fraction.
        val (originX, originY) = unit.apply(0f, 1f - SignMarkStyle.TextBaselineFraction)
        val dirX = unit.a / boxWidth
        val dirY = unit.b / boxWidth
        stream.setNonStrokingColor(mark.ink.red, mark.ink.green, mark.ink.blue)
        stream.beginText()
        stream.setFont(font, fontSize)
        stream.setTextMatrix(Matrix(dirX, dirY, -dirY, dirX, originX, originY))
        stream.showText(text)
        stream.endText()
    }

    private fun drawCheck(stream: PDPageContentStream, mark: SignMark.Checkbox, frame: SignGeometry.PageFrame) {
        val unit = SignGeometry.unitSquareToUser(mark.box, frame)
        val side = minOf(hypot(unit.a, unit.b), hypot(unit.c, unit.d))
        stream.setStrokingColor(mark.ink.red, mark.ink.green, mark.ink.blue)
        drawCheckboxBorder(stream, unit, side)
        if (!mark.checked) return
        stream.setLineWidth(side * SignMarkStyle.CheckStrokeFraction)
        stream.setLineCapStyle(1)
        stream.setLineJoinStyle(1)
        SignMarkStyle.CheckPoints.forEachIndexed { index, (fx, fy) ->
            val (x, y) = unit.apply(fx, 1f - fy)
            if (index == 0) stream.moveTo(x, y) else stream.lineTo(x, y)
        }
        stream.stroke()
    }

    /** Rounded square inset by half the border width, built from the box's unit square. */
    private fun drawCheckboxBorder(stream: PDPageContentStream, unit: SignGeometry.Affine, side: Float) {
        val border = side * SignMarkStyle.CheckboxBorderFraction
        val inset = border / 2f / side
        val r = SignMarkStyle.CheckboxCornerFraction
        val k = 0.5523f * r // Bezier circle approximation.
        val lo = inset
        val hi = 1f - inset
        fun p(x: Float, y: Float) = unit.apply(x, y)
        stream.setLineWidth(border)
        val (sx, sy) = p(lo + r, lo)
        stream.moveTo(sx, sy)
        p(hi - r, lo).let { (x, y) -> stream.lineTo(x, y) }
        curve(stream, p(hi - r + k, lo), p(hi, lo + r - k), p(hi, lo + r))
        p(hi, hi - r).let { (x, y) -> stream.lineTo(x, y) }
        curve(stream, p(hi, hi - r + k), p(hi - r + k, hi), p(hi - r, hi))
        p(lo + r, hi).let { (x, y) -> stream.lineTo(x, y) }
        curve(stream, p(lo + r - k, hi), p(lo, hi - r + k), p(lo, hi - r))
        p(lo, lo + r).let { (x, y) -> stream.lineTo(x, y) }
        curve(stream, p(lo, lo + r - k), p(lo + r - k, lo), p(lo + r, lo))
        stream.closePath()
        stream.stroke()
    }

    private fun curve(
        stream: PDPageContentStream,
        c1: Pair<Float, Float>,
        c2: Pair<Float, Float>,
        end: Pair<Float, Float>,
    ) = stream.curveTo(c1.first, c1.second, c2.first, c2.second, end.first, end.second)

    private fun SignGeometry.Affine.toMatrix() = Matrix(a, b, c, d, e, f)
}
