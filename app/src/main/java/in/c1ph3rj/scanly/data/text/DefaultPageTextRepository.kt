package `in`.c1ph3rj.scanly.data.text

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.util.Log
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.PageTextDao
import `in`.c1ph3rj.scanly.data.local.db.dao.MAX_SQL_ARGUMENTS
import `in`.c1ph3rj.scanly.data.local.db.dao.PageTextCandidateRow
import `in`.c1ph3rj.scanly.data.local.db.dao.PendingTextPageRow
import `in`.c1ph3rj.scanly.data.local.db.dao.groupedMatchQuery
import `in`.c1ph3rj.scanly.data.local.db.dao.snippetQuery
import `in`.c1ph3rj.scanly.data.local.db.entity.PageTextEntity
import `in`.c1ph3rj.scanly.domain.model.DocumentKind
import `in`.c1ph3rj.scanly.domain.model.MatchTier
import `in`.c1ph3rj.scanly.domain.model.OcrEngine
import `in`.c1ph3rj.scanly.domain.model.OcrFallbackReason
import `in`.c1ph3rj.scanly.domain.model.PageText
import `in`.c1ph3rj.scanly.domain.model.PageTextHit
import `in`.c1ph3rj.scanly.domain.model.PageTextSearch
import `in`.c1ph3rj.scanly.domain.model.TextIndexProgress
import `in`.c1ph3rj.scanly.domain.repository.PageTextRepository
import `in`.c1ph3rj.scanly.domain.search.Bm25
import `in`.c1ph3rj.scanly.domain.search.DerivedSnippet
import `in`.c1ph3rj.scanly.domain.search.FtsExpression
import `in`.c1ph3rj.scanly.domain.search.LooseRomanization
import `in`.c1ph3rj.scanly.domain.search.OcrConfusions
import `in`.c1ph3rj.scanly.domain.search.Proximity
import `in`.c1ph3rj.scanly.domain.search.SearchQuery
import `in`.c1ph3rj.scanly.domain.search.SearchTextDeriver
import `in`.c1ph3rj.scanly.domain.search.SnippetParser
import `in`.c1ph3rj.scanly.domain.search.SpellingSuggester
import `in`.c1ph3rj.scanly.domain.search.VocabularyTerm
import `in`.c1ph3rj.scanly.domain.text.DocumentKindClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPageTextRepository @Inject constructor(
    private val dao: PageTextDao,
    private val database: ScanlyDatabase,
    private val recognizer: PageTextRecognizer,
    private val scheduler: TextIndexScheduler,
    private val dispatchers: ScanlyDispatchers,
) : PageTextRepository {

    /**
     * Guards check-then-recognize-then-write per page, so the background worker and an open
     * Text screen never read the same page twice or overwrite each other's result.
     */
    private val recognitionMutex = Mutex()

    private val vocabularyCache = SearchVocabularyCache()

    override fun observeDocumentTexts(documentId: String): Flow<List<PageText>> =
        dao.observeDocumentTexts(documentId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.default)

    override fun observeIndexProgress(): Flow<TextIndexProgress> =
        dao.observeIndexProgress()
            .map { TextIndexProgress(totalPages = it.totalPages, pendingPages = it.pendingPages) }
            .distinctUntilChanged()

    override fun observeDocumentKinds(): Flow<Map<String, DocumentKind>> =
        dao.observeDocumentKinds()
            .map { rows ->
                buildMap {
                    // Rows arrive ordered by page; the first kind seen for a document wins.
                    rows.forEach { row ->
                        if (row.documentId !in this) {
                            DocumentKind.fromStorage(row.kind)?.let { put(row.documentId, it) }
                        }
                    }
                }
            }
            .distinctUntilChanged()

    override suspend fun recognizeDocument(
        documentId: String,
        force: Boolean,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        resultOf("Could not read text from this document.") {
            val pages = if (force) {
                dao.getDocumentPageSources(documentId)
            } else {
                dao.getPendingPagesForDocument(documentId)
            }
            onProgress(0, pages.size)
            pages.forEachIndexed { index, page ->
                recognizeSource(page, force)
                onProgress(index + 1, pages.size)
            }
        }
    }

    override suspend fun recognizePage(pageId: String, force: Boolean): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            resultOf("Could not read text from this page.") {
                val source = dao.getPageSource(pageId) ?: error("This page no longer exists.")
                recognizeSource(source, force)
                Unit
            }
        }

    override suspend fun recognizePendingPages(limit: Int): ScanlyResult<Int> =
        withContext(dispatchers.io) {
            resultOf("Could not update the text index.") {
                val pages = dao.getPendingPages(limit)
                pages.count { page -> recognizeSource(page, force = false) }
            }
        }

    override suspend fun updateUserText(pageId: String, text: String?): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            resultOf("Could not save your changes.") {
                recognitionMutex.withLock {
                    val existing = dao.getPageText(pageId) ?: error("Read this page's text first.")
                    val userText = text?.takeIf { it != existing.recognizedText }
                    val searchText = indexable(userText ?: existing.recognizedText)
                    vocabularyCache.clear()
                    dao.replace(
                        existing.copy(
                            userText = userText,
                            searchText = searchText,
                            derivedText = SearchTextDeriver.derivedText(searchText),
                            derivedVersion = SearchTextDeriver.VERSION,
                            kind = DocumentKindClassifier.classify(searchText)?.storageValue,
                        ),
                    )
                }
            }
        }

    /**
     * Tiered page-text search, best matches first:
     * 1. pages with words starting with what was typed, marked [MatchTier.EXACT] when every
     *    typed word also appears whole;
     * 2. only when nothing matched and some typed word appears nowhere in the library: the same
     *    search with that word swapped for similar words that do appear ([MatchTier.CLOSE]).
     *
     * Each tier fetches up to [MAX_CANDIDATES] pages without snippets, ranks them by BM25, and
     * builds snippets for the best [limit] only, so very common words never crowd out the best
     * pages.
     */
    override suspend fun searchPages(query: SearchQuery, limit: Int): ScanlyResult<PageTextSearch> =
        withContext(dispatchers.io) {
            val expression = query.ftsGroups { null }
                ?: return@withContext ScanlyResult.Success(PageTextSearch())
            // Derived alternatives ("1,250" or 1250) need the grouped form; plain words don't.
            val match = expression.allOf ?: expression.anyOf
            try {
                val candidates = if (expression.allOf != null) {
                    dao.searchCandidates(match, MAX_CANDIDATES)
                } else {
                    dao.searchCandidatesRaw(groupedMatchQuery(expression, MAX_CANDIDATES))
                }
                if (candidates.isNotEmpty()) {
                    // Quoted phrases alone are already exact; no second query needed.
                    val exactMatch = query.toExactFtsMatch()
                    val exact = exactMatch?.takeIf { it != match }?.let { dao.searchPageIds(it, MAX_CANDIDATES).toSet() }
                    val hits = rankedHits(query, expression, match, candidates, limit) { pageId ->
                        if (exact?.contains(pageId) ?: (exactMatch != null)) MatchTier.EXACT else MatchTier.PREFIX
                    }
                    val found = candidates.mapTo(HashSet()) { it.pageId }
                    val variantHits = searchOcrVariants(query, found, limit / OCR_VARIANT_SHARE)
                    val transliteratedHits = searchTransliterated(query, found, limit / OCR_VARIANT_SHARE)
                    return@withContext ScanlyResult.Success(PageTextSearch(hits = hits + variantHits + transliteratedHits))
                }
                // Nothing typed matched; an English spelling may still name text in another script.
                val transliterated = searchTransliterated(query, emptySet(), limit)
                if (transliterated.isNotEmpty()) return@withContext ScanlyResult.Success(PageTextSearch(hits = transliterated))
                ScanlyResult.Success(searchCloseMatches(query, limit))
            } catch (exception: SQLiteException) {
                // A query the FTS parser rejects simply has no text matches.
                Log.w(TAG, "Text search rejected query", exception)
                ScanlyResult.Success(PageTextSearch())
            }
        }

    /**
     * Pages the typed search missed because OCR misread a word ("lnvoice" for "invoice"), as
     * [MatchTier.OCR_VARIANT]: each term is searched together with its look-alikes from the
     * library, and pages already [found] are left out.
     */
    private suspend fun searchOcrVariants(query: SearchQuery, found: Set<String>, limit: Int): List<PageTextHit> {
        var hasVariants = false
        val expression = query.ftsGroups { term ->
            SpellingSuggester.ocrVariants(term, vocabularyNear(term), MAX_OCR_VARIANTS)
                .takeIf { it.isNotEmpty() }
                ?.let { variants -> hasVariants = true; listOf("$term*") + variants }
        }
        if (expression == null || !hasVariants) return emptyList()
        val candidates = dao.searchCandidatesRaw(groupedMatchQuery(expression, MAX_CANDIDATES))
            .filter { it.pageId !in found }
        return rankedHits(query, expression, expression.anyOf, candidates, limit) { MatchTier.OCR_VARIANT }
    }

    /**
     * Pages with a word in another script spelled like an English-typed term ("chennai" for
     * சென்னை), as [MatchTier.TRANSLITERATED], leaving out pages already [found]. Skipped unless
     * the library has such text at all.
     */
    private suspend fun searchTransliterated(query: SearchQuery, found: Set<String>, limit: Int): List<PageTextHit> {
        if (!query.hasTransliterations || !hasTransliterationKeys()) return emptyList()
        val expression = query.ftsGroups(withTransliteration = true) { null } ?: return emptyList()
        val candidates = dao.searchCandidatesRaw(groupedMatchQuery(expression, MAX_CANDIDATES))
            .filter { it.pageId !in found }
        return rankedHits(query, expression, expression.anyOf, candidates, limit) { MatchTier.TRANSLITERATED }
    }

    /** Whether any page has English-spelling keys (derived column 1, see [SearchTextDeriver]). */
    private fun hasTransliterationKeys(): Boolean {
        val prefix = LooseRomanization.TOKEN_PREFIX
        return database.openHelper.readableDatabase.query(
            "SELECT 1 FROM ${ScanlyDatabase.PAGE_TEXT_TERMS_TABLE} WHERE col = 1 AND term >= ? AND term < ? LIMIT 1",
            arrayOf<Any>(prefix, prefix + Char.MAX_VALUE),
        ).use { it.moveToFirst() }
    }

    private suspend fun searchCloseMatches(query: SearchQuery, limit: Int): PageTextSearch {
        val closeTerms = mutableListOf<String>()
        // The vocabulary still lists words from pages in Recently deleted, so a lone word that
        // matched nothing may "exist" only there; look for close words anyway.
        val isSingleTerm = query.terms.size == 1 && query.phrases.isEmpty()
        val expression = query.ftsGroups { term ->
            if (term.length < SpellingSuggester.MIN_TERM_LENGTH || (!isSingleTerm && vocabularyHasPrefix(term))) {
                null
            } else {
                SpellingSuggester.closeMatches(term, vocabularyNear(term), MAX_CLOSE_TERMS)
                    .also { closeTerms += it }
            }
        }
        // Nothing to swap in: every word occurs somewhere, they just never share a page.
        if (expression == null || closeTerms.isEmpty()) return PageTextSearch()
        val candidates = dao.searchCandidatesRaw(groupedMatchQuery(expression, MAX_CANDIDATES))
        val hits = rankedHits(query, expression, expression.anyOf, candidates, limit) { MatchTier.CLOSE }
        return PageTextSearch(hits = hits, closeTerms = closeTerms.distinct().takeIf { hits.isNotEmpty() }.orEmpty())
    }

    /**
     * Ranks [candidates] (better tier first, then BM25) and builds snippets for the top [limit],
     * highlighting [snippetMatch]: the same expression the candidates were matched with, so
     * `offsets()` term numbers line up with [FtsExpression.termGroups]. Pages matched only
     * through derived forms get a snippet built here instead (see [DerivedSnippet]).
     */
    private suspend fun rankedHits(
        query: SearchQuery,
        expression: FtsExpression,
        snippetMatch: String,
        candidates: List<PageTextCandidateRow>,
        limit: Int,
        tierOf: (pageId: String) -> MatchTier,
    ): List<PageTextHit> {
        val ranked = candidates
            .map { row ->
                RankedCandidate(row, tierOf(row.pageId), Bm25.score(Bm25.decode(row.matchinfo), COLUMN_WEIGHTS))
            }
            .sortedWith(compareBy<RankedCandidate> { it.tier.ordinal }.thenByDescending { it.score.relevance })
            .take(limit)
        val snippets = ranked.map { it.row.pageId }
            .chunked(MAX_SQL_ARGUMENTS - 1)
            .flatMap { pageIds -> dao.snippetsRaw(snippetQuery(snippetMatch, pageIds)) }
            .associateBy { it.pageId }
        val parsedSnippets = snippets.mapValues { (_, row) -> SnippetParser.parse(row.snippet) }
        val derivedSnippets = parsedSnippets.filterValues { it.highlights.isEmpty() }.keys.toList()
            .chunked(MAX_SQL_ARGUMENTS - 1)
            .flatMap { pageIds -> dao.getSearchTexts(pageIds) }
            .associate { row -> row.pageId to DerivedSnippet.build(row.searchText, query.allWords + expression.words) }
        return ranked.mapNotNull { (row, tier, score) ->
            val snippet = snippets[row.pageId] ?: return@mapNotNull null
            PageTextHit(
                pageId = row.pageId,
                documentId = row.documentId,
                pageIndex = row.pageIndex,
                snippet = derivedSnippets[row.pageId] ?: parsedSnippets.getValue(row.pageId),
                matchCount = score.hits.coerceAtLeast(1),
                tier = tier,
                relevance = score.relevance,
                proximity = Proximity.bonus(snippet.offsets, expression.termGroups, expression.groups.size),
            )
        }
    }

    private data class RankedCandidate(val row: PageTextCandidateRow, val tier: MatchTier, val score: Bm25.Score)

    override suspend fun suggestCorrection(query: SearchQuery): String? = withContext(dispatchers.io) {
        try {
            var changed = false
            val corrected = query.terms.map { term ->
                if (term.length < SpellingSuggester.MIN_TERM_LENGTH || vocabularyHasPrefix(term)) {
                    term
                } else {
                    SpellingSuggester.bestMatch(term, vocabularyNear(term))?.also { changed = true } ?: term
                }
            }
            if (!changed) return@withContext null
            (query.phrases.map { words -> words.joinToString(" ", "\"", "\"") } + corrected).joinToString(" ")
        } catch (exception: SQLiteException) {
            Log.w(TAG, "Spelling suggestion failed", exception)
            null
        }
    }

    override suspend fun completeWord(prefix: String, limit: Int): List<VocabularyTerm> = withContext(dispatchers.io) {
        try {
            database.openHelper.readableDatabase.query(
                "SELECT term, documents FROM ${ScanlyDatabase.PAGE_TEXT_TERMS_TABLE} " +
                    "WHERE col = 0 AND term >= ? AND term < ? ORDER BY documents DESC, term LIMIT ?",
                arrayOf<Any>(prefix, prefix + Char.MAX_VALUE, limit),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(VocabularyTerm(cursor.getString(0), cursor.getInt(1)))
                }
            }
        } catch (exception: SQLiteException) {
            Log.w(TAG, "Word completion failed", exception)
            emptyList()
        }
    }

    /** Vocabulary lookups read column 0 (the text) only: derived forms like d20261102 are no words. */
    private fun vocabularyHasPrefix(term: String): Boolean =
        database.openHelper.readableDatabase.query(
            "SELECT 1 FROM ${ScanlyDatabase.PAGE_TEXT_TERMS_TABLE} WHERE col = 0 AND term >= ? AND term < ? LIMIT 1",
            arrayOf<Any>(term, term + Char.MAX_VALUE),
        ).use { it.moveToFirst() }

    /**
     * Words of similar length starting with the term's first letter, or a letter OCR confuses
     * it with ("lnvoice" for "invoice"); typed typos rarely hit the first letter.
     */
    private fun vocabularyNear(term: String): List<VocabularyTerm> {
        val maxEdits = SpellingSuggester.maxEdits(term)
        val minLength = term.length - maxEdits
        val maxLength = term.length + maxEdits
        return OcrConfusions.firstCharsFor(term.first()).flatMap { first ->
            vocabularyCache.getOrPut(SearchVocabularyCache.Key(first, minLength, maxLength)) {
                database.openHelper.readableDatabase.query(
                    "SELECT term, documents FROM ${ScanlyDatabase.PAGE_TEXT_TERMS_TABLE} " +
                        "WHERE col = 0 AND term >= ? AND term < ? AND length(term) BETWEEN ? AND ? " +
                        "LIMIT $MAX_VOCABULARY_CANDIDATES",
                    arrayOf<Any>(first.toString(), (first + 1).toString(), minLength, maxLength),
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(VocabularyTerm(cursor.getString(0), cursor.getInt(1)))
                    }
                }
            }
        }
    }

    override suspend fun refreshDerivedText(limit: Int): ScanlyResult<Int> =
        withContext(dispatchers.io) {
            resultOf("Could not update the text index.") {
                recognitionMutex.withLock {
                    val rows = dao.getRowsWithDerivedVersionBelow(SearchTextDeriver.VERSION, limit)
                    rows.forEach { row ->
                        // Older rows also predate NFC-normalized text; fix both in one write.
                        val searchText = indexable(row.searchText)
                        dao.updateDerived(
                            id = row.id,
                            searchText = searchText,
                            derivedText = SearchTextDeriver.derivedText(searchText),
                            version = SearchTextDeriver.VERSION,
                        )
                    }
                    if (rows.isNotEmpty()) vocabularyCache.clear()
                    rows.size
                }
            }
        }

    override fun requestBackgroundIndexing() {
        scheduler.enqueue()
    }

    override fun observePagesNotReadWith(engine: OcrEngine): Flow<Int> =
        dao.observePagesNotReadWith(engine.name).distinctUntilChanged()

    override suspend fun rereadPagesNotReadWith(engine: OcrEngine): ScanlyResult<Int> =
        withContext(dispatchers.io) {
            resultOf("Could not queue pages to be read again.") {
                // Stale rows go through the normal indexer, which keeps the user's own edits.
                val marked = dao.markPagesNotReadWithStale(engine.name)
                scheduler.enqueue()
                marked
            }
        }

    /** Returns true when the page was (re)read. */
    private suspend fun recognizeSource(source: PendingTextPageRow, force: Boolean): Boolean =
        recognitionMutex.withLock {
            val existing = dao.getPageText(source.pageId)
            if (!force && existing?.sourceUpdatedAtMillis == source.updatedAtMillis) return@withLock false

            val imagePath = source.processedImagePath?.takeIf { File(it).exists() } ?: source.rawImagePath
            val recognized = try {
                imagePath?.let { recognizer.recognize(it) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Throwable) {
                // An unreadable image (or an Error such as OutOfMemoryError) is stored as "no
                // text" so the indexer doesn't retry it forever; the Text screen offers "Read
                // again" for a manual retry.
                Log.w(TAG, "Text recognition failed for page ${source.pageId}", exception)
                null
            }
            val recognizedText = recognized?.text.orEmpty()
            val userText = existing?.userText
            val searchText = indexable(userText ?: recognizedText)
            vocabularyCache.clear()
            try {
                dao.replace(
                    PageTextEntity(
                        pageId = source.pageId,
                        documentId = source.documentId,
                        recognizedText = recognizedText,
                        userText = userText,
                        searchText = searchText,
                        derivedText = SearchTextDeriver.derivedText(searchText),
                        derivedVersion = SearchTextDeriver.VERSION,
                        blocksJson = PageTextLayoutCodec.encode(recognized?.blocks.orEmpty()),
                        imageWidth = recognized?.imageWidth ?: 0,
                        imageHeight = recognized?.imageHeight ?: 0,
                        sourceUpdatedAtMillis = source.updatedAtMillis,
                        recognizedAtMillis = System.currentTimeMillis(),
                        kind = DocumentKindClassifier.classify(searchText)?.storageValue,
                        engine = recognized?.engine?.name,
                        requestedEngine = recognized?.requestedEngine?.name,
                        fallbackReason = recognized?.fallbackReason?.name,
                    ),
                )
            } catch (_: SQLiteConstraintException) {
                // The page was deleted while it was being read; nothing to store.
                return@withLock false
            }
            true
        }

    private inline fun <T> resultOf(fallbackMessage: String, block: () -> T): ScanlyResult<T> =
        try {
            ScanlyResult.Success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            // Errors (e.g. OutOfMemoryError from a recognizer) become a failed result, not a crash.
            ScanlyResult.Failure(ScanlyError(error.message ?: fallbackMessage, error))
        }

    private fun PageTextEntity.toDomain(): PageText = PageText(
        pageId = pageId,
        documentId = documentId,
        recognizedText = recognizedText,
        userText = userText,
        blocks = PageTextLayoutCodec.decode(blocksJson),
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        sourceUpdatedAtMillis = sourceUpdatedAtMillis,
        recognizedAtMillis = recognizedAtMillis,
        kind = DocumentKind.fromStorage(kind),
        engine = ocrEngineFromStorage(engine),
        requestedEngine = ocrEngineFromStorage(requestedEngine),
        fallbackReason = OcrFallbackReason.entries.firstOrNull { it.name == fallbackReason },
    )

    /** Unknown or missing values read as "not recorded" rather than guessing an engine. */
    private fun ocrEngineFromStorage(value: String?): OcrEngine? =
        OcrEngine.entries.firstOrNull { it.name == value }

    private companion object {
        const val TAG = "PageTextRepository"
        const val MAX_VOCABULARY_CANDIDATES = 5_000

        /** BM25 weight of the text column and of derived forms, which are less sure matches. */
        val COLUMN_WEIGHTS = doubleArrayOf(1.0, 0.5)

        /** Pages ranked per tier before snippets are built for the best few hundred. */
        const val MAX_CANDIDATES = 2_000

        /** Similar words tried per typed word; more only adds noise below the best few. */
        const val MAX_CLOSE_TERMS = 4

        /** OCR look-alikes searched per typed word. */
        const val MAX_OCR_VARIANTS = 3

        /** OCR-variant pages get at most this fraction (1/n) of the page budget. */
        const val OCR_VARIANT_SHARE = 4

        /**
         * Indexed text in NFC, the form queries are folded to, so "क़" read as one code point
         * and typed as क + ़ is the same word to the index.
         */
        fun indexable(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
    }
}
