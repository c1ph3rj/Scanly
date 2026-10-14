package `in`.c1ph3rj.scanly.domain.model

/** Text plus the character ranges to emphasise. */
data class HighlightedText(
    val text: String,
    val highlights: List<IntRange> = emptyList(),
)

enum class SearchScope(val label: String) {
    EVERYWHERE("Everywhere"),
    NAMES("Names"),
    TEXT("Text in pages"),
}

/** One page that contains the query, as returned by the full-text index. */
data class PageTextHit(
    val pageId: String,
    val documentId: String,
    val pageIndex: Int,
    val snippet: HighlightedText,
    val matchCount: Int,
)

data class DocumentSearchResult(
    val document: ScanDocument,
    val title: HighlightedText,
    val folderTitle: String?,
    val kind: DocumentKind?,
    val nameMatched: Boolean,
    val textMatchCount: Int,
    /** One entry per matching page, in page order. */
    val pageHits: List<PageTextHit>,
)

data class FolderSearchResult(
    val group: DocumentGroup,
    val title: HighlightedText,
)

data class LibrarySearchResults(
    val query: String,
    /** Documents whose pages contain the query; those that also match by name rank first. */
    val textMatches: List<DocumentSearchResult> = emptyList(),
    /** Documents that match only by name — shown after text matches. */
    val nameMatches: List<DocumentSearchResult> = emptyList(),
    val folders: List<FolderSearchResult> = emptyList(),
    /** False while the page-text query is still running; name matches are already final. */
    val isTextSearchComplete: Boolean = true,
) {
    val documentCount: Int get() = textMatches.size + nameMatches.size
    val totalTextMatches: Int get() = textMatches.sumOf { it.textMatchCount }
    val isEmpty: Boolean get() = textMatches.isEmpty() && nameMatches.isEmpty() && folders.isEmpty()
}
