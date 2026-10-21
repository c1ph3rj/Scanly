package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeDocuments(): Flow<List<ScanDocument>>

    fun observeRecentDocuments(limit: Int): Flow<List<ScanDocument>>

    fun observeUngroupedDocuments(): Flow<List<ScanDocument>>

    fun observeDocument(documentId: String): Flow<ScanDocument?>

    suspend fun getAllDocumentTitles(): List<String>

    suspend fun suggestDocumentTitle(format: DocumentTitleFormat): String

    suspend fun createDocument(
        title: String,
        groupId: String? = null,
    ): ScanlyResult<String>

    suspend fun createImportedDocument(
        groupId: String? = null,
    ): ScanlyResult<String>

    suspend fun renameDocument(
        documentId: String,
        title: String,
    ): ScanlyResult<Unit>

    /** Hides the document from the library; it stays restorable in Recently deleted. */
    suspend fun moveDocumentToTrash(documentId: String): ScanlyResult<Unit>

    /** Puts a trashed document back in the library (and its folder, if that still exists). */
    suspend fun restoreDocument(documentId: String): ScanlyResult<Unit>

    fun observeTrashedDocuments(): Flow<List<ScanDocument>>

    /** Permanently deletes the document and its files. */
    suspend fun deleteDocument(documentId: String): ScanlyResult<Unit>

    /**
     * Permanently deletes every trashed document.
     * @return number of documents removed
     */
    suspend fun emptyTrash(): ScanlyResult<Int>

    /**
     * Permanently deletes trashed documents deleted at or before [cutoffMillis].
     * @return number of documents removed
     */
    suspend fun purgeTrashDeletedBefore(cutoffMillis: Long): ScanlyResult<Int>

    /**
     * Deletes documents that have no pages and removes their on-disk storage.
     * @return number of documents removed
     */
    suspend fun deleteEmptyDocuments(): ScanlyResult<Int>
}
