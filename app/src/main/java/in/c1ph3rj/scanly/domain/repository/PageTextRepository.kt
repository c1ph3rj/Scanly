package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentKind
import `in`.c1ph3rj.scanly.domain.model.PageText
import `in`.c1ph3rj.scanly.domain.model.PageTextHit
import `in`.c1ph3rj.scanly.domain.model.TextIndexProgress
import `in`.c1ph3rj.scanly.domain.search.SearchQuery
import kotlinx.coroutines.flow.Flow

/** On-device page text: recognition, user corrections, and the full-text index. */
interface PageTextRepository {
    fun observeDocumentTexts(documentId: String): Flow<List<PageText>>

    fun observeIndexProgress(): Flow<TextIndexProgress>

    fun observeDocumentKinds(): Flow<Map<String, DocumentKind>>

    /**
     * Reads every page of [documentId] that has no up-to-date text, reporting
     * (pagesDone, pagesTotal). With [force], re-reads pages that are already current.
     */
    suspend fun recognizeDocument(
        documentId: String,
        force: Boolean = false,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ScanlyResult<Unit>

    suspend fun recognizePage(pageId: String, force: Boolean = false): ScanlyResult<Unit>

    /** Reads up to [limit] stale pages library-wide, newest documents first. Returns pages read. */
    suspend fun recognizePendingPages(limit: Int): ScanlyResult<Int>

    /** Saves the user's correction; null restores the recognized text. */
    suspend fun updateUserText(pageId: String, text: String?): ScanlyResult<Unit>

    suspend fun searchPages(query: SearchQuery, limit: Int): ScanlyResult<List<PageTextHit>>

    /**
     * A corrected query built from words that occur in the user's pages, or null when every
     * word already appears somewhere (or nothing close enough exists).
     */
    suspend fun suggestCorrection(query: SearchQuery): String?

    /** Asks the background indexer to catch up (no-op when nothing is pending). */
    fun requestBackgroundIndexing()
}
