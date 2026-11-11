package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult

/** Turns one library document into several. */
interface DocumentSplitRepository {
    /**
     * Copies each entry of [parts] (0-based positions of the document's live pages, in order)
     * into a new document in the same folder, with the pages' images, text and signature
     * layers. When [keepOriginal] is false the original then moves to Recently deleted, so it
     * can still be restored. Returns the new document ids in [parts] order.
     */
    suspend fun splitDocument(
        documentId: String,
        parts: List<List<Int>>,
        keepOriginal: Boolean,
    ): ScanlyResult<List<String>>
}
