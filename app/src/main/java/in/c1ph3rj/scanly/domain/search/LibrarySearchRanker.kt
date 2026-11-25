package `in`.c1ph3rj.scanly.domain.search

import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.DocumentKind
import `in`.c1ph3rj.scanly.domain.model.DocumentSearchResult
import `in`.c1ph3rj.scanly.domain.model.FolderFilter
import `in`.c1ph3rj.scanly.domain.model.FolderSearchResult
import `in`.c1ph3rj.scanly.domain.model.HighlightedText
import `in`.c1ph3rj.scanly.domain.model.LibrarySearchResults
import `in`.c1ph3rj.scanly.domain.model.MatchTier
import `in`.c1ph3rj.scanly.domain.model.PageTextHit
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.SearchFilters
import `in`.c1ph3rj.scanly.domain.model.SearchScope
import `in`.c1ph3rj.scanly.domain.model.SearchSort
import java.time.ZoneId
import kotlin.math.ln

/**
 * Turns name matches and page-text hits into the ordered result list.
 *
 * Order, by design:
 * 1. Documents whose pages contain the query — those whose name matches too float to the top,
 *    then the most relevant pages win (BM25: rare words count more than common ones, a long
 *    page doesn't win by length alone), with a bonus for having the query's words close
 *    together and for matching on more pages. This is where users find "that letter
 *    which mentioned X", which names alone can't answer.
 *    Within these, pages that contain every typed word whole beat pages that only have words
 *    starting with it.
 *    Pages matching only through an OCR misread of a typed word ([MatchTier.OCR_VARIANT])
 *    count here too, at reduced weight.
 * 2. Documents that match by name only, best name match first.
 * 3. Documents whose pages only have words spelled close to the query ([MatchTier.CLOSE]),
 *    so a near miss never outranks what the user actually typed.
 * A document whose tag name matches counts as a name match (below the same match on its title).
 * Filters (type, date, folder, tags) narrow the documents first; a sort other than best match
 * reorders each section by date or name instead of score.
 * Folders are listed separately. While text hits are still loading ([textHits] == null),
 * name matches are returned on their own so results appear instantly as the user types.
 */
object LibrarySearchRanker {
    private const val NAME_ALSO_MATCHES_BOOST = 2.0
    private const val PAGE_SPREAD_WEIGHT = 0.35
    private const val RECENCY_WEIGHT = 0.15
    private const val EXACT_WORD_BOOST = 0.75

    /** Text relevance is scaled so the best page of the query scores this much. */
    private const val TEXT_RELEVANCE_WEIGHT = 2.0

    /** Further matching pages add with diminishing returns, far less than the best one. */
    private const val OTHER_PAGES_WEIGHT = 0.3
    private const val PROXIMITY_WEIGHT = 0.5
    private const val OCR_VARIANT_WEIGHT = 0.6
    private const val TRANSLITERATED_WEIGHT = 0.8

    /** A tag says what a document is about, but less surely than its own name. */
    private const val TAG_MATCH_WEIGHT = 0.9

    /** A word-start match in `NameMatcher` (its prefix score); infix and typo matches score lower. */
    private const val STRICT_PART_SCORE = 1.0

    fun rank(
        query: SearchQuery,
        documents: List<ScanDocument>,
        groups: List<DocumentGroup>,
        documentKinds: Map<String, DocumentKind>,
        textHits: List<PageTextHit>?,
        scope: SearchScope,
        filters: SearchFilters = SearchFilters(),
        closeTerms: List<String> = emptyList(),
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        /** Tag ids per document id, for the tag filter and tag name matches. */
        documentTagIds: Map<String, Set<String>> = emptyMap(),
        /** Tag names by tag id. */
        tagNames: Map<String, String> = emptyMap(),
    ): LibrarySearchResults {
        val groupTitles = groups.associate { it.id to it.title }
        val dateRange = filters.dateRange(nowMillis, zone)
        val kindFilter = filters.kind
        val allowed = documents.filter { document ->
            (kindFilter == null || documentKinds[document.id] == kindFilter) &&
                (dateRange == null || document.updatedAtMillis in dateRange) &&
                when (val folder = filters.folder) {
                    FolderFilter.Any -> true
                    FolderFilter.None -> document.groupId == null
                    is FolderFilter.In -> document.groupId == folder.groupId
                } &&
                (filters.tagIds.isEmpty() || documentTagIds[document.id].orEmpty().containsAll(filters.tagIds))
        }
        if (query.isBlank) {
            // Blank query + filters = browse every document they allow, newest first by default.
            if (!filters.narrows) return LibrarySearchResults(query.raw)
            val sort = filters.sort.takeIf { it != SearchSort.BEST_MATCH } ?: SearchSort.NEWEST
            return LibrarySearchResults(
                query = query.raw,
                nameMatches = allowed
                    .sortedWith(documentOrder(sort))
                    .map { document ->
                        DocumentSearchResult(
                            document = document,
                            title = HighlightedText(document.title),
                            folderTitle = document.groupId?.let(groupTitles::get),
                            kind = documentKinds[document.id],
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
        // BM25 values have no fixed scale; normalize so boosts below keep the same weight.
        val topRelevance = hitsByDocument.values.flatten().maxOfOrNull { it.relevance }?.takeIf { it > 0.0 } ?: 1.0
        val newest = allowed.maxOfOrNull { it.updatedAtMillis } ?: 0L
        val oldest = allowed.minOfOrNull { it.updatedAtMillis } ?: 0L

        val textMatches = mutableListOf<Pair<Double, DocumentSearchResult>>()
        val nameMatches = mutableListOf<Pair<Double, DocumentSearchResult>>()
        val closeMatches = mutableListOf<Pair<Double, DocumentSearchResult>>()
        allowed.forEach { document ->
            val kind = documentKinds[document.id]
            // "-draft" hides a document called "Draft lease" even if its pages never say draft.
            if (NameMatcher.mentions(document.title, query.excluded)) return@forEach
            val titleMatch = if (scope != SearchScope.TEXT && !excludedByTag(document, query, documentTagIds, tagNames)) {
                NameMatcher.match(document.title, query)
            } else {
                null
            }
            val viaTags = if (titleMatch == null && scope != SearchScope.TEXT && !excludedByTag(document, query, documentTagIds, tagNames)) {
                tagMatch(document, query, documentTagIds, tagNames)
            } else {
                null
            }
            val nameMatch = titleMatch ?: viaTags?.match
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
                matchedTags = viaTags?.tagNames.orEmpty(),
            )
            val recency = recencyScore(document.updatedAtMillis, oldest, newest)
            val pageSpread = pageHits.size.toDouble() / document.pageCount.coerceAtLeast(1)
            val textScore = textRelevance(pageHits, topRelevance) +
                PROXIMITY_WEIGHT * (pageHits.maxOfOrNull { it.proximity } ?: 0.0) +
                PAGE_SPREAD_WEIGHT * pageSpread +
                RECENCY_WEIGHT * recency
            val isCloseOnly = pageHits.isNotEmpty() && pageHits.all { it.tier == MatchTier.CLOSE }
            when {
                pageHits.isNotEmpty() && !isCloseOnly -> {
                    val exactBoost = if (pageHits.any { it.tier == MatchTier.EXACT }) EXACT_WORD_BOOST else 0.0
                    val score = (nameMatch?.let { NAME_ALSO_MATCHES_BOOST + it.score } ?: 0.0) + textScore + exactBoost
                    textMatches += score to result
                }
                // A name match is what the user typed; its near-miss pages still show under it.
                nameMatch != null -> nameMatches += (nameMatch.score + RECENCY_WEIGHT * recency) to result
                else -> closeMatches += textScore to result
            }
        }

        // Filters describe documents; a folder has no type or single date to match them.
        val folders = if (scope == SearchScope.TEXT || filters.narrows) {
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
            textMatches = textMatches.ordered(filters.sort),
            nameMatches = nameMatches.ordered(filters.sort),
            closeMatches = closeMatches.ordered(filters.sort),
            closeTerms = closeTerms.takeIf { closeMatches.isNotEmpty() }.orEmpty(),
            folders = folders,
            isTextSearchComplete = textHits != null || scope == SearchScope.NAMES,
        )
    }

    /** True when a `-word` the user typed appears in one of [document]'s tag names. */
    private fun excludedByTag(
        document: ScanDocument,
        query: SearchQuery,
        documentTagIds: Map<String, Set<String>>,
        tagNames: Map<String, String>,
    ): Boolean {
        if (query.excluded.isEmpty()) return false
        return documentTagIds[document.id].orEmpty()
            .mapNotNull(tagNames::get)
            .any { NameMatcher.mentions(it, query.excluded) }
    }

    /**
     * A match through [document]'s tags when its title alone doesn't match: either one tag name
     * matches the whole query, or every typed word starts a word in the title or in one of the tags
     * ("travel receipt" finds "Receipt" tagged Travel). The split case is strict (no typo tolerance,
     * phrases can't straddle the title and a tag) so loose matches don't creep in. An excluded word
     * found in the title or any tag rules the document out. Only the title's own words are highlighted.
     */
    private fun tagMatch(
        document: ScanDocument,
        query: SearchQuery,
        documentTagIds: Map<String, Set<String>>,
        tagNames: Map<String, String>,
    ): TagMatch? {
        val names = documentTagIds[document.id].orEmpty().mapNotNull(tagNames::get).sortedBy { it.lowercase() }
        if (names.isEmpty()) return null
        val fields = listOf(document.title) + names
        if (fields.any { NameMatcher.mentions(it, query.excluded) }) return null
        val wholeQueryMatches = names.mapNotNull { name -> NameMatcher.match(name, query)?.let { name to it } }
        val best = wholeQueryMatches.maxByOrNull { it.second.score }
        if (best != null) {
            return TagMatch(
                match = NameMatch(best.second.score * TAG_MATCH_WEIGHT, HighlightedText(document.title)),
                tagNames = wholeQueryMatches.map { it.first },
            )
        }

        // Each part of the query must start a word in a single field.
        val parts = query.terms.map { listOf(SearchQuery(raw = it, terms = listOf(it), phrases = emptyList())) } +
            query.phrases.map { phrase -> listOf(SearchQuery(raw = phrase.joinToString(" "), terms = emptyList(), phrases = listOf(phrase))) } +
            query.anyOf.map { alternatives ->
                alternatives.map { words ->
                    if (words.size == 1) {
                        SearchQuery(raw = words.single(), terms = words, phrases = emptyList())
                    } else {
                        SearchQuery(raw = words.joinToString(" "), terms = emptyList(), phrases = listOf(words))
                    }
                }
            }
        if (parts.isEmpty()) return null
        val titleHighlights = mutableListOf<IntRange>()
        val usedTags = linkedSetOf<String>()
        parts.forEach { alternatives ->
            var matched = false
            alternatives.forEach { part ->
                NameMatcher.match(document.title, part)?.takeIf { it.score >= STRICT_PART_SCORE }?.let {
                    titleHighlights += it.title.highlights
                    matched = true
                }
                if (!matched) {
                    names.firstOrNull { name ->
                        (NameMatcher.match(name, part)?.score ?: 0.0) >= STRICT_PART_SCORE
                    }?.let { name ->
                        usedTags += name
                        matched = true
                    }
                }
            }
            if (!matched) return null
        }
        return TagMatch(
            match = NameMatch(
                score = STRICT_PART_SCORE * TAG_MATCH_WEIGHT,
                title = HighlightedText(document.title, titleHighlights.sortedBy { it.first }),
            ),
            tagNames = usedTags.toList(),
        )
    }

    /** A name match found through tags, with the tag names that made it, for the result row. */
    private class TagMatch(val match: NameMatch, val tagNames: List<String>)

    /** Best page in full, then the rest log-damped, scaled against the query's best page. */
    private fun textRelevance(pageHits: List<PageTextHit>, topRelevance: Double): Double {
        if (pageHits.isEmpty()) return 0.0
        val relevances = pageHits.map { it.relevance * tierWeight(it.tier) / topRelevance }.sortedDescending()
        return TEXT_RELEVANCE_WEIGHT * (relevances.first() + OTHER_PAGES_WEIGHT * ln(1.0 + relevances.drop(1).sum()))
    }

    /** An OCR misread is probably the word the user typed, but less surely than the word itself. */
    private fun tierWeight(tier: MatchTier): Double = when (tier) {
        MatchTier.OCR_VARIANT -> OCR_VARIANT_WEIGHT
        // An English spelling of a word in another script is likely meant, but spellings vary.
        MatchTier.TRANSLITERATED -> TRANSLITERATED_WEIGHT
        else -> 1.0
    }

    private fun List<Pair<Double, DocumentSearchResult>>.ordered(sort: SearchSort): List<DocumentSearchResult> =
        if (sort == SearchSort.BEST_MATCH) {
            sortedWith(resultOrder()).map { it.second }
        } else {
            map { it.second }.sortedWith(compareBy<DocumentSearchResult, ScanDocument>(documentOrder(sort)) { it.document })
        }

    private fun documentOrder(sort: SearchSort): Comparator<ScanDocument> = when (sort) {
        SearchSort.OLDEST -> compareBy<ScanDocument> { it.updatedAtMillis }
        SearchSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        else -> compareByDescending<ScanDocument> { it.updatedAtMillis }
    }.thenBy { it.id }

    private fun resultOrder(): Comparator<Pair<Double, DocumentSearchResult>> =
        compareByDescending<Pair<Double, DocumentSearchResult>> { it.first }
            .thenByDescending { it.second.document.updatedAtMillis }
            .thenBy { it.second.document.id }

    private fun recencyScore(updatedAt: Long, oldest: Long, newest: Long): Double =
        if (newest <= oldest) 1.0 else (updatedAt - oldest).toDouble() / (newest - oldest)
}
