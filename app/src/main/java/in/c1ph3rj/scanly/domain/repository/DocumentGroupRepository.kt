package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import kotlinx.coroutines.flow.Flow

interface DocumentGroupRepository {
    fun observeGroups(): Flow<List<DocumentGroup>>

    fun observeDocumentsInGroup(groupId: String): Flow<List<ScanDocument>>

    suspend fun createGroup(name: String): ScanlyResult<String>

    suspend fun renameGroup(
        groupId: String,
        name: String,
    ): ScanlyResult<Unit>

    suspend fun deleteGroup(groupId: String): ScanlyResult<Unit>

    suspend fun assignDocumentToGroup(
        documentId: String,
        groupId: String?,
    ): ScanlyResult<Unit>
}
