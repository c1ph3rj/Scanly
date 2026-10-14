package `in`.c1ph3rj.scanly.domain.model

/** What a document appears to be, inferred on-device from its recognized text. */
enum class DocumentKind(
    val storageValue: String,
    val label: String,
) {
    INVOICE("invoice", "Invoice"),
    RECEIPT("receipt", "Receipt"),
    CONTRACT("contract", "Contract"),
    STATEMENT("statement", "Statement"),
    LETTER("letter", "Letter"),
    CERTIFICATE("certificate", "Certificate"),
    ID_DOCUMENT("id", "ID document"),
    FORM("form", "Form"),
    RESUME("resume", "Résumé"),
    ;

    companion object {
        fun fromStorage(value: String?): DocumentKind? =
            entries.firstOrNull { it.storageValue == value }
    }
}
