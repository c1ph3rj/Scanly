package `in`.c1ph3rj.scanly.feature.tools.pdf

import `in`.c1ph3rj.scanly.domain.model.PdfImageExport
import `in`.c1ph3rj.scanly.domain.model.PdfPageImage
import java.util.Locale

/** Compact size for estimates and totals: "220 KB", "1.3 MB". Whole KB below 1 MB. */
fun formatImageBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1000.0) return "${kb.toInt().coerceAtLeast(1)} KB"
    val mb = kb / 1024.0
    if (mb < 1000.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.1f GB", mb / 1024.0)
}

fun formatImageCount(count: Int): String = if (count == 1) "1 image" else "$count images"

/** Primary button on the pages step: "Export 6 images · ~1.3 MB". */
fun formatPdfToImagesExportLabel(selectedCount: Int, estimateEachBytes: Long?): String {
    if (selectedCount <= 0) return "Choose pages to export"
    val base = "Export ${formatImageCount(selectedCount)}"
    val total = estimateEachBytes?.takeIf { it > 0L }?.times(selectedCount) ?: return base
    return "$base · ~${formatImageBytes(total)}"
}

/** Section label above the page grid: "All 6 pages" or "3 of 6 pages". */
fun formatPdfToImagesPagesLabel(selectedCount: Int, pageCount: Int): String = when {
    pageCount <= 0 -> "Pages"
    selectedCount == pageCount -> if (pageCount == 1) "1 page" else "All $pageCount pages"
    else -> "$selectedCount of $pageCount pages"
}

fun formatPdfToImagesDoneTitle(count: Int): String = "${formatImageCount(count)} saved"

/** "In Pictures/Scanly · JPG · 1240 px · 1.3 MB in all". */
fun formatPdfToImagesDoneSubtitle(export: PdfImageExport): String = listOf(
    "In ${export.destinationLabel}",
    export.options.format.label,
    "${export.options.resolution.widthPx} px",
    "${formatImageBytes(export.totalBytes)} in all",
).joinToString(" · ")

/**
 * Share button on the Done step. An empty [selectedCount] means nothing is picked, which
 * shares everything.
 */
fun formatPdfToImagesShareLabel(selectedCount: Int, totalCount: Int): String = when {
    selectedCount in 1 until totalCount -> "Share $selectedCount"
    totalCount == 1 -> "Share image"
    else -> "Share all $totalCount"
}

/** Caption under a saved image on the Done grid: "Page 3 · 344 KB". */
fun formatPdfToImagesCellLabel(image: PdfPageImage): String =
    "Page ${image.pageIndex + 1} · ${formatImageBytes(image.sizeBytes)}"

/** Toggles [index] in a selection set. */
fun Set<Int>.toggled(index: Int): Set<Int> = if (index in this) this - index else this + index
