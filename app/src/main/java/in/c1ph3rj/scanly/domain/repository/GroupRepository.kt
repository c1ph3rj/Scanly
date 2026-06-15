package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun observeGroupsWithStats(): Flow<List<DocumentGroup>>

    fun observeRecentGroups(limit: Int): Flow<List<DocumentGroup>>

    fun observeGroupWithStats(groupId: String): Flow<DocumentGroup?>

    fun observeGroupDocuments(groupId: String): Flow<List<ScanDocument>>

    suspend fun createGroup(title: String): ScanlyResult<String>

    suspend fun renameGroup(groupId: String, title: String): ScanlyResult<Unit>

    suspend fun deleteGroup(groupId: String): ScanlyResult<Unit>

    suspend fun setDocumentGroup(documentId: String, groupId: String?): ScanlyResult<Unit>
}
