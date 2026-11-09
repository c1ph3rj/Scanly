package `in`.c1ph3rj.scanly.feature.tools.pdf

import `in`.c1ph3rj.scanly.core.common.appPluralString
import `in`.c1ph3rj.scanly.domain.model.PdfImageExport
import `in`.c1ph3rj.scanly.domain.model.PdfPageImage
import java.util.Locale
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString

/** Compact size for estimates and totals: "220 KB", "1.3 MB". Whole KB below 1 MB. */
fun formatImageBytes(bytes: Long): String {
    if (bytes <= 0L) return appString(R.string.pdf_to_images_presentation_0_kb)
    val kb = bytes / 1024.0
    if (kb < 1000.0) return appString(R.string.pdf_to_images_presentation_kb, kb.toInt().coerceAtLeast(1))
    val mb = kb / 1024.0
    if (mb < 1000.0) return String.format(Locale.getDefault(), appString(R.string.pdf_to_images_presentation_1f_mb), mb)
    return String.format(Locale.getDefault(), appString(R.string.pdf_to_images_presentation_1f_gb), mb / 1024.0)
}

fun formatImageCount(count: Int): String = appPluralString(R.plurals.common_images, count, count)

/** Primary button on the pages step: "Export 6 images · ~1.3 MB". */
fun formatPdfToImagesExportLabel(selectedCount: Int, estimateEachBytes: Long?): String {
    if (selectedCount <= 0) return appString(R.string.pdf_to_images_presentation_choose_pages_to_export)
    val base = appPluralString(R.plurals.pdf_to_images_export_count, selectedCount, selectedCount)
    val total = estimateEachBytes?.takeIf { it > 0L }?.times(selectedCount) ?: return base
    return "$base · ~${formatImageBytes(total)}"
}

/** Section label above the page grid: "All 6 pages" or "3 of 6 pages". */
fun formatPdfToImagesPagesLabel(selectedCount: Int, pageCount: Int): String = when {
    pageCount <= 0 -> appString(R.string.document_detail_pages)
    selectedCount == pageCount -> if (pageCount == 1) appString(R.string.library_card_presentation_1_page) else appString(R.string.pdf_to_images_presentation_all_pages, pageCount)
    else -> appString(R.string.pdf_to_images_presentation_of_pages, selectedCount, pageCount)
}

fun formatPdfToImagesDoneTitle(count: Int): String = appPluralString(R.plurals.pdf_to_images_saved_count, count, count)

/** "In Pictures/Scanly · JPG · 1240 px · 1.3 MB in all". */
fun formatPdfToImagesDoneSubtitle(export: PdfImageExport): String = listOf(
    appString(R.string.search_filters_in, export.destinationLabel),
    export.options.format.label,
    "${export.options.resolution.widthPx} px",
    appString(R.string.pdf_to_images_presentation_in_all, formatImageBytes(export.totalBytes)),
).joinToString(" · ")

/**
 * Share button on the Done step. An empty [selectedCount] means nothing is picked, which
 * shares everything.
 */
fun formatPdfToImagesShareLabel(selectedCount: Int, totalCount: Int): String = when {
    selectedCount in 1 until totalCount -> appString(R.string.document_detail_share, selectedCount)
    totalCount == 1 -> appString(R.string.pdf_to_images_presentation_share_image)
    else -> appString(R.string.pdf_to_images_presentation_share_all, totalCount)
}

/** Caption under a saved image on the Done grid: "Page 3 · 344 KB". */
fun formatPdfToImagesCellLabel(image: PdfPageImage): String =
    appString(R.string.pdf_to_images_presentation_page, image.pageIndex + 1, formatImageBytes(image.sizeBytes))

/** Toggles [index] in a selection set. */
fun Set<Int>.toggled(index: Int): Set<Int> = if (index in this) this - index else this + index
