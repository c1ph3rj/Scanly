package `in`.c1ph3rj.scanly.domain.model

import androidx.annotation.StringRes
import `in`.c1ph3rj.scanly.R

/**
 * What the camera is pointed at. Each mode tunes live detection, the on-screen guide and
 * auto-capture for its subject; [DOCUMENT] is the original, general-purpose flow.
 *
 * Stored per document (the mode new pages are captured in) and per page (the mode the page
 * was captured in, which retakes reuse).
 */
enum class ScanMode(
    val storageValue: String,
    @StringRes val labelRes: Int,
    /** Filter for new pages when the document has no preferred filter of its own. */
    val defaultFilterPreset: PageFilterPreset,
) {
    DOCUMENT("document", R.string.scan_mode_document, PageFilterPreset.AUTO),
    /** Cards carry photos and colour security print: keep natural colour. */
    ID_CARD("id_card", R.string.scan_mode_id_card, PageFilterPreset.NATURAL_COLOR),
    /** Lifts the gutter shadow and evens out page tone. */
    BOOK("book", R.string.scan_mode_book, PageFilterPreset.MAGIC_COLOR);

    companion object {
        fun fromStorage(value: String?): ScanMode =
            entries.firstOrNull { it.storageValue == value } ?: DOCUMENT
    }
}

/** Which side of an ID card a page holds. Pages of one card share an `idCardPairId`. */
enum class IdCardSide(
    val storageValue: String,
) {
    FRONT("front"),
    BACK("back");

    companion object {
        fun fromStorage(value: String?): IdCardSide? =
            entries.firstOrNull { it.storageValue == value }
    }
}
