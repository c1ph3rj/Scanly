package `in`.c1ph3rj.scanly.domain.model

import androidx.annotation.StringRes
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.R

/** What a document appears to be, inferred on-device from its recognized text. */
enum class DocumentKind(
    val storageValue: String,
    @param:StringRes val labelRes: Int,
) {
    INVOICE("invoice", R.string.document_kind_invoice),
    RECEIPT("receipt", R.string.document_kind_receipt),
    CONTRACT("contract", R.string.document_kind_contract),
    STATEMENT("statement", R.string.document_kind_statement),
    LETTER("letter", R.string.pdf_page_size_letter),
    CERTIFICATE("certificate", R.string.document_kind_certificate),
    ID_DOCUMENT("id", R.string.document_kind_id_document),
    FORM("form", R.string.document_kind_form),
    RESUME("resume", R.string.document_kind_r_sum),
    ;

    val label: String get() = appString(labelRes)


    companion object {
        fun fromStorage(value: String?): DocumentKind? =
            entries.firstOrNull { it.storageValue == value }
    }
}
