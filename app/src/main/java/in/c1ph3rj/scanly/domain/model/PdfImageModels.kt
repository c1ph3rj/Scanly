package `in`.c1ph3rj.scanly.domain.model

import androidx.annotation.StringRes
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.R

/** Image encoding for PDF → image export. */
enum class PdfImageFormat(
    val label: String,
    @param:StringRes val descriptionRes: Int,
    val extension: String,
    val mimeType: String,
) {
    JPG("JPG", R.string.pdf_image_format_smaller_files_best_for_photos, "jpg", "image/jpeg"),
    PNG("PNG", R.string.pdf_image_format_crisper_text_files_about_3, "png", "image/png"),
    ;

    val description: String get() = appString(descriptionRes)
}

/** Output width for PDF → image export. Height follows each page's own aspect ratio. */
enum class PdfImageResolution(
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
    val widthPx: Int,
) {
    SHARING(R.string.pdf_image_resolution_for_sharing, R.string.pdf_image_resolution_1240_px_wide_sharp_on, 1240),
    PRINTING(R.string.pdf_image_resolution_for_printing, R.string.pdf_image_resolution_2480_px_wide_300_dpi, 2480),
    ;

    val label: String get() = appString(labelRes)
    val description: String get() = appString(descriptionRes)
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
