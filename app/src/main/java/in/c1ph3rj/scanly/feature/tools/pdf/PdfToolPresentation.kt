package `in`.c1ph3rj.scanly.feature.tools.pdf

import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.domain.model.PdfPasswordMode
import `in`.c1ph3rj.scanly.domain.model.WatermarkLayout

fun formatPdfPageCount(pageCount: Int): String =
    if (pageCount == 1) "1 page" else "$pageCount pages"

fun formatMergeFileCount(fileCount: Int): String = when {
    fileCount <= 0 -> "No files"
    fileCount == 1 -> "1 PDF"
    else -> "$fileCount PDFs"
}

fun formatMergeFileOrdinal(index: Int): String = (index + 1).toString()

fun formatPdfProtectionLabel(isEncrypted: Boolean): String =
    if (isEncrypted) "Protected" else "Unlocked"

fun formatPdfDocumentMeta(
    pageCount: Int?,
    sizeBytes: Long?,
    isEncrypted: Boolean? = null,
): String {
    val parts = buildList {
        if (pageCount != null && pageCount > 0) add(formatPdfPageCount(pageCount))
        if (sizeBytes != null && sizeBytes > 0L) add(StorageFormatter.formatBytes(sizeBytes))
        if (isEncrypted != null) add(formatPdfProtectionLabel(isEncrypted))
    }
    return parts.joinToString("  ·  ")
}

fun formatCompressSizeComparison(
    beforeBytes: Long?,
    afterBytes: Long?,
    savedPercent: Float?,
): String? {
    if (beforeBytes == null || afterBytes == null) return null
    val before = StorageFormatter.formatBytes(beforeBytes)
    val after = StorageFormatter.formatBytes(afterBytes)
    return if (afterBytes < beforeBytes && savedPercent != null) {
        "$before → $after  ·  saved ${savedPercent.toInt()}%"
    } else {
        "$before → $after"
    }
}

fun formatPasswordModeActionLabel(mode: PdfPasswordMode): String = when (mode) {
    PdfPasswordMode.Protect -> "Protect PDF"
    PdfPasswordMode.Remove -> "Remove password"
}

fun formatWatermarkLayoutHint(layout: WatermarkLayout): String = when (layout) {
    WatermarkLayout.REPEATED -> "Repeats across the full page, including the edges."
    WatermarkLayout.CENTERED -> "One large stamp sized to dominate the page."
}

fun formatWatermarkOpacityPercent(opacity: Float): String {
    val percent = (opacity * 100f).toInt().coerceIn(0, 100)
    return "Opacity $percent%"
}

fun formatMergeReadyHint(fileCount: Int): String =
    if (fileCount < 2) {
        "Add at least one more PDF to combine."
    } else {
        "Files merge in this order. The first PDF starts the document."
    }
