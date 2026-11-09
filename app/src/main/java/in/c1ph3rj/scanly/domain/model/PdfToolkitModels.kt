package `in`.c1ph3rj.scanly.domain.model

import androidx.annotation.StringRes
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.R

/**
 * Input for PDF toolkit operations. Device URIs are opaque content URIs;
 * library documents are resolved by exporting a temporary PDF first.
 */
sealed interface PdfToolSource {
    data class DeviceUri(val uriString: String, val displayName: String = appString(R.string.document_detail_document)) : PdfToolSource
    data class LibraryDocument(val documentId: String, val title: String) : PdfToolSource
    data class AppFile(val filePath: String, val displayName: String) : PdfToolSource
}

data class PdfDocumentInfo(
    val pageCount: Int,
    val isEncrypted: Boolean,
    val fileSizeBytes: Long?,
    val displayName: String,
)

enum class PdfCompressQuality(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
    val jpegQuality: Int,
    val maxDimension: Int,
) {
    HIGH(R.string.pdf_compress_quality_high_quality, R.string.pdf_compress_quality_best_clarity_less_compression, 88, 2200),
    MEDIUM(R.string.pdf_compress_quality_balanced, R.string.pdf_compress_quality_good_balance_of_size_and, 72, 1600),
    SMALL(R.string.pdf_compress_quality_smallest, R.string.pdf_compress_quality_maximum_compression_softer_detail, 52, 1100),
    ;

    val label: String get() = appString(labelRes)
    val description: String get() = appString(descriptionRes)
}

data class WatermarkOptions(
    val text: String,
    val opacity: Float = 0.24f,
    val angleDegrees: Float = -38f,
    val size: WatermarkSize = WatermarkSize.MEDIUM,
    val layout: WatermarkLayout = WatermarkLayout.CENTERED,
    val pageRange: WatermarkPageRange = WatermarkPageRange.ALL_PAGES,
    val color: WatermarkColor = WatermarkColor.GREY,
)

/**
 * Stamp inks. Kept dark and saturated so the mark stays legible at low opacity on white
 * paper and survives photocopying.
 */
enum class WatermarkColor(
    @param:StringRes val labelRes: Int,
    val argb: Long,
) {
    GREY(R.string.watermark_color_grey, 0xFF4B5563),
    RED(R.string.watermark_color_red, 0xFFB91C1C),
    BLUE(R.string.watermark_color_blue, 0xFF1D4ED8),
    TEAL(R.string.watermark_color_teal, 0xFF0F766E),
    ;

    val label: String get() = appString(labelRes)


    val red: Float get() = ((argb shr 16) and 0xFF) / 255f
    val green: Float get() = ((argb shr 8) and 0xFF) / 255f
    val blue: Float get() = (argb and 0xFF) / 255f
}

/**
 * Relative stamp scale. Absolute point size is derived from the page box so the
 * same preset stays usable on A4, letter, and phone-captured page sizes.
 */
enum class WatermarkSize(
    val scale: Float,
) {
    SMALL(0.72f),
    MEDIUM(1.0f),
    LARGE(1.38f),
}

enum class WatermarkLayout {
    /** One large mark, sized to dominate the page like a classic DRAFT stamp. */
    CENTERED,
    /** Dense tiled field that covers the full page, including rotated corners. */
    REPEATED,
}

enum class WatermarkPageRange {
    FIRST_PAGE,
    ALL_PAGES,
}

enum class WatermarkOrientation(
    val angleDegrees: Float,
) {
    DIAGONAL(-38f),
    HORIZONTAL(0f),
}

enum class PdfPasswordMode {
    Protect,
    Remove,
}
