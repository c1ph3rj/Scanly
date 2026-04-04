package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeDocuments(): Flow<List<ScanDocument>>

    fun observeDocument(documentId: String): Flow<ScanDocument?>

    suspend fun createDocument(title: String): ScanlyResult<String>

    suspend fun renameDocument(
        documentId: String,
        title: String,
    ): ScanlyResult<Unit>

    suspend fun deleteDocument(documentId: String): ScanlyResult<Unit>
}
