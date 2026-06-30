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

    suspend fun deleteDocument(documentId: String): ScanlyResult<Unit>
}
