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

/** How closely a page's words match what the user typed, best first. */
enum class MatchTier {
    /** Every typed word appears whole on the page. */
    EXACT,

    /** The page has words starting with what was typed ("lea" → "lease"). */
    PREFIX,

    /** Only words spelled close to the typed ones ("reciept" → "receipt"). */
    CLOSE,
}

/** One page that contains the query, as returned by the full-text index. */
data class PageTextHit(
    val pageId: String,
    val documentId: String,
    val pageIndex: Int,
    val snippet: HighlightedText,
    val matchCount: Int,
    val tier: MatchTier = MatchTier.PREFIX,
)

/** Page-text hits for one query. */
data class PageTextSearch(
    val hits: List<PageTextHit> = emptyList(),
    /**
     * Words from the library searched in place of typed words nothing matched, for the
     * [MatchTier.CLOSE] hits. Empty when every hit matched the typed words themselves.
     */
    val closeTerms: List<String> = emptyList(),
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
    /**
     * Documents whose pages only have words spelled close to the query, shown last so exact
     * matches always come first.
     */
    val closeMatches: List<DocumentSearchResult> = emptyList(),
    /** The similar words behind [closeMatches]. */
    val closeTerms: List<String> = emptyList(),
    val folders: List<FolderSearchResult> = emptyList(),
    /** False while the page-text query is still running; name matches are already final. */
    val isTextSearchComplete: Boolean = true,
) {
    val documentCount: Int get() = textMatches.size + nameMatches.size + closeMatches.size
    val totalTextMatches: Int get() = textMatches.sumOf { it.textMatchCount }
    val isEmpty: Boolean
        get() = textMatches.isEmpty() && nameMatches.isEmpty() && closeMatches.isEmpty() && folders.isEmpty()
}
