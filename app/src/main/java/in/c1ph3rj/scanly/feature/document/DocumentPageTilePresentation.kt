package `in`.c1ph3rj.scanly.feature.document

/**
 * Document-detail page tile geometry and labels. Kept as pure functions so
 * tests can assert portrait paper tiles (not 1:1 thumbs) without Compose.
 */

/** Portrait paper (width / height) for page overview tiles. */
const val DOCUMENT_PAGE_TILE_ASPECT_RATIO = 3f / 4f

fun documentPageTileAspectRatio(): Float = DOCUMENT_PAGE_TILE_ASPECT_RATIO

fun formatDocumentPageNumberLabel(pageIndex: Int, pageCount: Int): String {
    if (pageCount <= 0) return "0"
    val number = (pageIndex + 1).coerceIn(1, pageCount)
    return number.toString()
}

fun formatDocumentPageIndexLabel(pageIndex: Int, pageCount: Int): String {
    val number = formatDocumentPageNumberLabel(pageIndex, pageCount)
    return "Page $number"
}
