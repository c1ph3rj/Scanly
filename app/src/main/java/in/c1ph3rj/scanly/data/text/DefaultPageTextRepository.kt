package `in`.c1ph3rj.scanly.data.text

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.util.Log
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.PageTextDao
import `in`.c1ph3rj.scanly.data.local.db.dao.PendingTextPageRow
import `in`.c1ph3rj.scanly.data.local.db.entity.PageTextEntity
import `in`.c1ph3rj.scanly.domain.model.DocumentKind
import `in`.c1ph3rj.scanly.domain.model.PageText
import `in`.c1ph3rj.scanly.domain.model.PageTextHit
import `in`.c1ph3rj.scanly.domain.model.TextIndexProgress
import `in`.c1ph3rj.scanly.domain.repository.PageTextRepository
import `in`.c1ph3rj.scanly.domain.search.SearchQuery
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPageTextRepository @Inject constructor(
    private val dao: PageTextDao,
    private val database: ScanlyDatabase,
    private val recognizer: MlKitPageTextRecognizer,
    private val scheduler: TextIndexScheduler,
    private val dispatchers: ScanlyDispatchers,
) : PageTextRepository {

    /**
     * Guards check-then-recognize-then-write per page, so the background worker and an open
     * Text screen never read the same page twice or overwrite each other's result.
     */
    private val recognitionMutex = Mutex()

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
                    val searchText = userText ?: existing.recognizedText
                    dao.replace(
                        existing.copy(
                            userText = userText,
                            searchText = searchText,
                            kind = DocumentKindClassifier.classify(searchText)?.storageValue,
                        ),
                    )
                }
            }
        }

    override suspend fun searchPages(query: SearchQuery, limit: Int): ScanlyResult<List<PageTextHit>> =
        withContext(dispatchers.io) {
            val match = query.toFtsMatch() ?: return@withContext ScanlyResult.Success(emptyList())
            try {
                ScanlyResult.Success(
                    dao.search(match, limit).map { row ->
                        PageTextHit(
                            pageId = row.pageId,
                            documentId = row.documentId,
                            pageIndex = row.pageIndex,
                            snippet = SnippetParser.parse(row.snippet),
                            matchCount = SnippetParser.matchCount(row.offsets),
                        )
                    },
                )
            } catch (exception: SQLiteException) {
                // A query the FTS parser rejects simply has no text matches.
                Log.w(TAG, "Text search rejected query", exception)
                ScanlyResult.Success(emptyList())
            }
        }

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

    private fun vocabularyHasPrefix(term: String): Boolean =
        database.openHelper.readableDatabase.query(
            "SELECT 1 FROM ${ScanlyDatabase.PAGE_TEXT_TERMS_TABLE} WHERE col = '*' AND term >= ? AND term < ? LIMIT 1",
            arrayOf<Any>(term, term + Char.MAX_VALUE),
        ).use { it.moveToFirst() }

    /** Words sharing the first letter and of similar length; typos rarely hit the first letter. */
    private fun vocabularyNear(term: String): List<VocabularyTerm> {
        val maxEdits = SpellingSuggester.maxEdits(term)
        val first = term.first()
        return database.openHelper.readableDatabase.query(
            "SELECT term, documents FROM ${ScanlyDatabase.PAGE_TEXT_TERMS_TABLE} " +
                "WHERE col = '*' AND term >= ? AND term < ? AND length(term) BETWEEN ? AND ? " +
                "LIMIT $MAX_VOCABULARY_CANDIDATES",
            arrayOf<Any>(first.toString(), (first + 1).toString(), term.length - maxEdits, term.length + maxEdits),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(VocabularyTerm(cursor.getString(0), cursor.getInt(1)))
            }
        }
    }

    override fun requestBackgroundIndexing() {
        scheduler.enqueue()
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
            } catch (exception: Exception) {
                // An unreadable image is stored as "no text" so the indexer doesn't retry it
                // forever; the Text screen offers "Read again" for a manual retry.
                Log.w(TAG, "Text recognition failed for page ${source.pageId}", exception)
                null
            }
            val recognizedText = recognized?.text.orEmpty()
            val userText = existing?.userText
            val searchText = userText ?: recognizedText
            try {
                dao.replace(
                    PageTextEntity(
                        pageId = source.pageId,
                        documentId = source.documentId,
                        recognizedText = recognizedText,
                        userText = userText,
                        searchText = searchText,
                        blocksJson = PageTextLayoutCodec.encode(recognized?.blocks.orEmpty()),
                        imageWidth = recognized?.imageWidth ?: 0,
                        imageHeight = recognized?.imageHeight ?: 0,
                        sourceUpdatedAtMillis = source.updatedAtMillis,
                        recognizedAtMillis = System.currentTimeMillis(),
                        kind = DocumentKindClassifier.classify(searchText)?.storageValue,
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
        } catch (exception: Exception) {
            ScanlyResult.Failure(ScanlyError(exception.message ?: fallbackMessage, exception))
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
    )

    private companion object {
        const val TAG = "PageTextRepository"
        const val MAX_VOCABULARY_CANDIDATES = 5_000
    }
}
