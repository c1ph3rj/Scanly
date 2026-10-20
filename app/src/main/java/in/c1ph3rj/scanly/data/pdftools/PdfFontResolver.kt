package `in`.c1ph3rj.scanly.data.pdftools

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File

/**
 * Picks a font that can draw a string: a standard 14 font when it can encode every character,
 * otherwise a broad-coverage system font embedded as a subset (₹, accents, Greek, Cyrillic…).
 */
internal object PdfFontResolver {

    /** Bold system fonts tried in order, falling back to regular weights. */
    val BoldCandidates = listOf(
        "/system/fonts/Roboto-Bold.ttf",
        "/system/fonts/NotoSans-Bold.ttf",
        "/system/fonts/DroidSans-Bold.ttf",
        "/system/fonts/RobotoStatic-Regular.ttf",
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/DroidSans.ttf",
    )

    /** Regular-weight system fonts tried in order. */
    val RegularCandidates = listOf(
        "/system/fonts/RobotoStatic-Regular.ttf",
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/DroidSans.ttf",
    )

    fun resolve(
        document: PDDocument,
        text: String,
        standard: PDType1Font,
        candidates: List<String>,
    ): PDFont? {
        if (canEncode(standard, text)) return standard
        for (path in candidates) {
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
}
