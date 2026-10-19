package `in`.c1ph3rj.scanly.domain.model

/** Image encoding for PDF → image export. */
enum class PdfImageFormat(
    val label: String,
    val description: String,
    val extension: String,
    val mimeType: String,
) {
    JPG("JPG", "Smaller files, best for photos and sharing", "jpg", "image/jpeg"),
    PNG("PNG", "Crisper text, files about 3× bigger", "png", "image/png"),
}

/** Output width for PDF → image export. Height follows each page's own aspect ratio. */
enum class PdfImageResolution(
    val label: String,
    val description: String,
    val widthPx: Int,
) {
    SHARING("For sharing", "1240 px wide · sharp on phones and WhatsApp", 1240),
    PRINTING("For printing", "2480 px wide · 300 dpi on A4", 2480),
}

data class PdfImageOptions(
    val format: PdfImageFormat = PdfImageFormat.JPG,
    val resolution: PdfImageResolution = PdfImageResolution.SHARING,
)

/**
 * A decrypted local copy of a PDF that a multi-step tool reads in place, so page thumbnails
 * and exports never re-copy a device file or re-export a library scan.
 */
data class PdfWorkingCopy(
    val file: PdfToolSource.AppFile,
    val pageCount: Int,
)

/** One exported page image: a private cache copy for sharing plus its gallery entry. */
data class PdfPageImage(
    val pageIndex: Int,
    val fileName: String,
    val filePath: String,
    val contentUri: String,
    val sizeBytes: Long,
)

data class PdfImageExport(
    val images: List<PdfPageImage>,
    /** Where the gallery copies live, e.g. "Pictures/Scanly". */
    val destinationLabel: String,
    val options: PdfImageOptions,
) {
    val totalBytes: Long get() = images.sumOf { it.sizeBytes }
}
