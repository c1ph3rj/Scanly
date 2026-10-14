package `in`.c1ph3rj.scanly.domain.model

enum class QrOrigin { Scanned, Created }

/** One scanned or created code kept in the on-device QR history. */
data class QrHistoryEntry(
    val id: String,
    val raw: String,
    val origin: QrOrigin,
    val createdAtMillis: Long,
) {
    val payload: QrPayload get() = QrPayloadParser.parse(raw)
}
