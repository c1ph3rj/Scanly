package `in`.c1ph3rj.scanly.domain.model

import androidx.annotation.StringRes
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.R

enum class DocumentTitleFormat(
    @param:StringRes val labelRes: Int,
    @param:StringRes val shortLabelRes: Int,
    val isDefault: Boolean = false,
) {
    ScanDateTime(
        labelRes = R.string.document_title_format_scan_with_date_and_time,
        shortLabelRes = R.string.document_title_format_scan_date_time,
        isDefault = true,
    ),
    DocumentDateTime(
        labelRes = R.string.document_title_format_document_with_date_and_time,
        shortLabelRes = R.string.document_title_format_document_date_time,
    ),
    ScanDate(
        labelRes = R.string.document_title_format_scan_with_date,
        shortLabelRes = R.string.document_title_format_scan_date,
    ),
    ScanIsoDate(
        labelRes = R.string.document_title_format_scan_with_iso_date,
        shortLabelRes = R.string.document_title_format_scan_yyyy_mm_dd,
    ),
    ;

    val label: String get() = appString(labelRes)
    val shortLabel: String get() = appString(shortLabelRes)


    fun next(): DocumentTitleFormat {
        val all = entries
        return all[(all.indexOf(this) + 1) % all.size]
    }

    companion object {
        val default: DocumentTitleFormat = entries.first { it.isDefault }
    }
}