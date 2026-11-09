package `in`.c1ph3rj.scanly.feature.tools.pdf

import `in`.c1ph3rj.scanly.core.common.appPluralString
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.domain.model.PdfPasswordMode
import `in`.c1ph3rj.scanly.domain.model.WatermarkLayout
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString

fun shouldReturnFromPdfPickerToPreviousStep(
    hasSelectedSource: Boolean,
    pickerOpenedForChange: Boolean,
): Boolean = hasSelectedSource && pickerOpenedForChange

fun formatPdfPageCount(pageCount: Int): String =
    appPluralString(R.plurals.common_pages, pageCount, pageCount)

fun formatMergeFileCount(fileCount: Int): String = when {
    fileCount <= 0 -> appString(R.string.pdf_tool_presentation_no_files)
    fileCount == 1 -> appString(R.string.pdf_tool_presentation_1_pdf)
    else -> appString(R.string.pdf_tool_presentation_pdfs, fileCount)
}

fun formatMergeFileOrdinal(index: Int): String = (index + 1).toString()

fun formatPdfProtectionLabel(isEncrypted: Boolean): String =
    if (isEncrypted) appString(R.string.pdf_tool_presentation_protected) else appString(R.string.pdf_tool_presentation_unlocked)

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
        appString(R.string.pdf_tool_presentation_saved, before, after, savedPercent.toInt())
    } else {
        "$before → $after"
    }
}

fun formatPasswordModeActionLabel(mode: PdfPasswordMode): String = when (mode) {
    PdfPasswordMode.Protect -> appString(R.string.pdf_tool_presentation_protect_pdf)
    PdfPasswordMode.Remove -> appString(R.string.pdf_password_remove_password)
}

fun formatWatermarkLayoutHint(layout: WatermarkLayout): String = when (layout) {
    WatermarkLayout.REPEATED -> appString(R.string.pdf_tool_presentation_repeats_across_the_full_page)
    WatermarkLayout.CENTERED -> appString(R.string.pdf_tool_presentation_one_large_stamp_sized_to)
}

fun formatWatermarkOpacityPercent(opacity: Float): String {
    val percent = (opacity * 100f).toInt().coerceIn(0, 100)
    return appString(R.string.pdf_tool_presentation_opacity, percent)
}

fun formatMergeReadyHint(fileCount: Int): String =
    if (fileCount < 2) {
        appString(R.string.pdf_tool_presentation_add_at_least_one_more)
    } else {
        appString(R.string.pdf_tool_presentation_files_merge_in_this_order)
    }
