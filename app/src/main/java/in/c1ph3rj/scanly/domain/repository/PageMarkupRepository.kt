package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PageMarkup
import `in`.c1ph3rj.scanly.domain.model.SignMark

/** Signature marks stamped onto Scanly pages, kept editable. */
interface PageMarkupRepository {
    /** Existing markups for a document, keyed by page id. */
    suspend fun loadDocumentMarkups(documentId: String): ScanlyResult<Map<String, PageMarkup>>

    /**
     * Replaces the marks on each listed page and re-stamps its image. A page mapped to an empty
     * list goes back to its clean image. Pages not in [marksByPageId] are left alone.
     */
    suspend fun saveDocumentMarkups(
        documentId: String,
        marksByPageId: Map<String, List<SignMark>>,
    ): ScanlyResult<Unit>
}
