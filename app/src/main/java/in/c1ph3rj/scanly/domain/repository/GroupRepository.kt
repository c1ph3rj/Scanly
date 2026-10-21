package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.TrashedFolder
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun observeGroupsWithStats(): Flow<List<DocumentGroup>>

    fun observeRecentGroups(limit: Int): Flow<List<DocumentGroup>>

    fun observeGroupWithStats(groupId: String): Flow<DocumentGroup?>

    fun observeGroupDocuments(groupId: String): Flow<List<ScanDocument>>

    suspend fun getAllGroupTitles(): List<String>

    suspend fun suggestGroupTitle(format: GroupTitleFormat): String

    suspend fun createGroup(title: String): ScanlyResult<String>

    suspend fun renameGroup(groupId: String, title: String): ScanlyResult<Unit>

    /** Deletes the folder only; its documents move out of it. */
    suspend fun deleteGroup(groupId: String): ScanlyResult<Unit>

    /**
     * Moves the folder to Recently deleted. With [includeDocuments] its documents go with it;
     * otherwise they move to the library and rejoin the folder if it is restored.
     */
    suspend fun moveGroupToTrash(groupId: String, includeDocuments: Boolean = true): ScanlyResult<Unit>

    /** Brings a trashed folder back with the documents that went with it (or were kept out of it). */
    suspend fun restoreGroup(groupId: String): ScanlyResult<Unit>

    fun observeTrashedGroups(): Flow<List<TrashedFolder>>

    /** Permanently deletes a trashed folder and the documents that went with it. */
    suspend fun deleteGroupForever(groupId: String): ScanlyResult<Unit>

    /** Permanently deletes every trashed folder. @return number of folders removed */
    suspend fun emptyTrashedGroups(): ScanlyResult<Int>

    /** Permanently deletes folders trashed at or before [cutoffMillis]. @return number removed */
    suspend fun purgeTrashedGroupsBefore(cutoffMillis: Long): ScanlyResult<Int>

    /**
     * Deletes folders/groups that contain no documents.
     * @return number of groups removed
     */
    suspend fun deleteEmptyGroups(): ScanlyResult<Int>

    suspend fun setDocumentGroup(documentId: String, groupId: String?): ScanlyResult<Unit>
}
