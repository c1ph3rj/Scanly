package `in`.c1ph3rj.scanly.domain.model

/**
 * Input for PDF toolkit operations. Device URIs are opaque content URIs;
 * library documents are resolved by exporting a temporary PDF first.
 */
sealed interface PdfToolSource {
    data class DeviceUri(val uriString: String, val displayName: String = "Document") : PdfToolSource
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
    val label: String,
    val description: String,
    val jpegQuality: Int,
    val maxDimension: Int,
) {
    HIGH("High quality", "Best clarity · less compression", 88, 2200),
    MEDIUM("Balanced", "Recommended · good balance", 72, 1600),
    SMALL("Smallest", "Maximum compression · softer detail", 52, 1100),
}

data class WatermarkOptions(
    val text: String,
    val opacity: Float = 0.24f,
    val angleDegrees: Float = -38f,
    val size: WatermarkSize = WatermarkSize.MEDIUM,
    val layout: WatermarkLayout = WatermarkLayout.CENTERED,
    val pageRange: WatermarkPageRange = WatermarkPageRange.ALL_PAGES,
)

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
