package `in`.c1ph3rj.scanly.domain.search

import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.DocumentKind
import `in`.c1ph3rj.scanly.domain.model.DocumentSearchResult
import `in`.c1ph3rj.scanly.domain.model.FolderSearchResult
import `in`.c1ph3rj.scanly.domain.model.HighlightedText
import `in`.c1ph3rj.scanly.domain.model.LibrarySearchResults
import `in`.c1ph3rj.scanly.domain.model.PageTextHit
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.SearchScope
import kotlin.math.ln

/**
 * Turns name matches and page-text hits into the ordered result list.
 *
 * Order, by design:
 * 1. Documents whose pages contain the query — those whose name matches too float to the top,
 *    then more matches and more matching pages win. This is where users find "that letter
 *    which mentioned X", which names alone can't answer.
 * 2. Documents that match by name only, best name match first.
 * Folders are listed separately. While text hits are still loading ([textHits] == null),
 * name matches are returned on their own so results appear instantly as the user types.
 */
object LibrarySearchRanker {
    private const val NAME_ALSO_MATCHES_BOOST = 2.0
    private const val PAGE_SPREAD_WEIGHT = 0.35
    private const val RECENCY_WEIGHT = 0.15

    fun rank(
        query: SearchQuery,
        documents: List<ScanDocument>,
        groups: List<DocumentGroup>,
        documentKinds: Map<String, DocumentKind>,
        textHits: List<PageTextHit>?,
        scope: SearchScope,
        kindFilter: DocumentKind? = null,
    ): LibrarySearchResults {
        val groupTitles = groups.associate { it.id to it.title }
        if (query.isBlank) {
            // Blank query + a type chosen = browse every document of that type, newest first.
            if (kindFilter == null) return LibrarySearchResults(query.raw)
            return LibrarySearchResults(
                query = query.raw,
                nameMatches = documents
                    .filter { documentKinds[it.id] == kindFilter }
                    .sortedByDescending { it.updatedAtMillis }
                    .map { document ->
                        DocumentSearchResult(
                            document = document,
                            title = HighlightedText(document.title),
                            folderTitle = document.groupId?.let(groupTitles::get),
                            kind = kindFilter,
                            nameMatched = false,
                            textMatchCount = 0,
                            pageHits = emptyList(),
                        )
                    },
            )
        }
        val hitsByDocument = textHits.orEmpty()
            .takeIf { scope != SearchScope.NAMES }
            .orEmpty()
            .groupBy { it.documentId }
        val newest = documents.maxOfOrNull { it.updatedAtMillis } ?: 0L
        val oldest = documents.minOfOrNull { it.updatedAtMillis } ?: 0L

        val textMatches = mutableListOf<Pair<Double, DocumentSearchResult>>()
        val nameMatches = mutableListOf<Pair<Double, DocumentSearchResult>>()
        documents.forEach { document ->
            val kind = documentKinds[document.id]
            if (kindFilter != null && kind != kindFilter) return@forEach
            val nameMatch = if (scope != SearchScope.TEXT) NameMatcher.match(document.title, query) else null
            val pageHits = hitsByDocument[document.id].orEmpty().sortedBy { it.pageIndex }
            if (nameMatch == null && pageHits.isEmpty()) return@forEach

            val textMatchCount = pageHits.sumOf { it.matchCount }
            val result = DocumentSearchResult(
                document = document,
                title = nameMatch?.title ?: HighlightedText(document.title),
                folderTitle = document.groupId?.let(groupTitles::get),
                kind = kind,
                nameMatched = nameMatch != null,
                textMatchCount = textMatchCount,
                pageHits = pageHits,
            )
            val recency = recencyScore(document.updatedAtMillis, oldest, newest)
            if (pageHits.isNotEmpty()) {
                val pageSpread = pageHits.size.toDouble() / document.pageCount.coerceAtLeast(1)
                val score = (nameMatch?.let { NAME_ALSO_MATCHES_BOOST + it.score } ?: 0.0) +
                    ln(1.0 + textMatchCount) +
                    PAGE_SPREAD_WEIGHT * pageSpread +
                    RECENCY_WEIGHT * recency
                textMatches += score to result
            } else {
                nameMatches += (nameMatch!!.score + RECENCY_WEIGHT * recency) to result
            }
        }

        val folders = if (scope == SearchScope.TEXT || kindFilter != null) {
            emptyList()
        } else {
            groups.mapNotNull { group ->
                NameMatcher.match(group.title, query)?.let { match ->
                    match.score to FolderSearchResult(group, match.title)
                }
            }.sortedWith(compareByDescending<Pair<Double, FolderSearchResult>> { it.first }
                .thenByDescending { it.second.group.updatedAtMillis })
                .map { it.second }
        }

        return LibrarySearchResults(
            query = query.raw,
            textMatches = textMatches.sortedWith(resultOrder()).map { it.second },
            nameMatches = nameMatches.sortedWith(resultOrder()).map { it.second },
            folders = folders,
            isTextSearchComplete = textHits != null || scope == SearchScope.NAMES,
        )
    }

    private fun resultOrder(): Comparator<Pair<Double, DocumentSearchResult>> =
        compareByDescending<Pair<Double, DocumentSearchResult>> { it.first }
            .thenByDescending { it.second.document.updatedAtMillis }
            .thenBy { it.second.document.id }

    private fun recencyScore(updatedAt: Long, oldest: Long, newest: Long): Double =
        if (newest <= oldest) 1.0 else (updatedAt - oldest).toDouble() / (newest - oldest)
}
