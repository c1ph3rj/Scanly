package `in`.c1ph3rj.scanly.testing

import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.TrashedFolder
import `in`.c1ph3rj.scanly.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class InMemoryGroupRepository(
    private val library: InMemoryScanlyLibrary,
) : GroupRepository {
    override fun observeGroupsWithStats(): Flow<List<DocumentGroup>> = library.groups

    override fun observeRecentGroups(limit: Int): Flow<List<DocumentGroup>> =
        library.groups.map { groups ->
            groups.sortedByDescending { it.updatedAtMillis }.take(limit)
        }

    override fun observeGroupWithStats(groupId: String): Flow<DocumentGroup?> =
        library.groups.map { groups -> groups.firstOrNull { it.id == groupId } }

    override fun observeGroupDocuments(groupId: String): Flow<List<ScanDocument>> =
        library.documents.map { documents ->
            documents.filter { it.groupId == groupId && it.deletedAtMillis == null }
        }

    override suspend fun getAllGroupTitles(): List<String> =
        library.groups.value.map { it.title }

    override suspend fun suggestGroupTitle(format: GroupTitleFormat): String =
        DocumentPresentationFormatter.uniqueGroupTitle(
            format = format,
            existingTitles = getAllGroupTitles(),
        )

    override suspend fun createGroup(title: String): ScanlyResult<String> =
        runLibraryResult("Could not create the group.") {
            val normalizedTitle = DocumentPresentationFormatter.resolveUniqueGroupTitle(
                baseTitle = DocumentPresentationFormatter.normalizeGroupTitle(title),
                existingTitles = getAllGroupTitles(),
            )
            val groupId = UUID.randomUUID().toString()
            val timestamp = library.now()
            library.upsertGroup(
                DocumentGroup(
                    id = groupId,
                    title = normalizedTitle,
                    documentCount = 0,
                    totalPageCount = 0,
                    coverThumbnailPath = null,
                    createdAtMillis = timestamp,
                    updatedAtMillis = timestamp,
                ),
            )
            groupId
        }

    override suspend fun renameGroup(groupId: String, title: String): ScanlyResult<Unit> =
        runLibraryResult("Could not rename the group.") {
            val existing = library.group(groupId) ?: error("Group not found.")
            library.upsertGroup(
                existing.copy(
                    title = DocumentPresentationFormatter.normalizeGroupTitle(title),
                    updatedAtMillis = library.now(),
                ),
            )
        }

    override suspend fun deleteGroup(groupId: String): ScanlyResult<Unit> =
        runLibraryResult("Could not delete the group.") {
            library.group(groupId) ?: error("Group not found.")
            library.deleteGroup(groupId)
        }

    override suspend fun moveGroupToTrash(groupId: String, includeDocuments: Boolean): ScanlyResult<Unit> =
        runLibraryResult("Could not move the folder to Recently deleted.") {
            library.group(groupId) ?: error("Folder not found.")
            library.trashGroup(groupId, library.now(), includeDocuments)
        }

    override suspend fun restoreGroup(groupId: String): ScanlyResult<Unit> =
        runLibraryResult("Could not restore the folder.") {
            library.group(groupId) ?: error("Folder not found.")
            library.restoreGroup(groupId)
        }

    override fun observeTrashedGroups(): Flow<List<TrashedFolder>> = library.trashedFolders

    override suspend fun deleteGroupForever(groupId: String): ScanlyResult<Unit> =
        runLibraryResult("Could not delete the folder.") {
            library.deleteTrashedGroups(listOf(groupId))
        }

    override suspend fun emptyTrashedGroups(): ScanlyResult<Int> =
        runLibraryResult("Could not delete the folders.") {
            val ids = library.trashedGroupTimes().keys.toList()
            library.deleteTrashedGroups(ids)
            ids.size
        }

    override suspend fun purgeTrashedGroupsBefore(cutoffMillis: Long): ScanlyResult<Int> =
        runLibraryResult("Could not clear old deleted folders.") {
            val dueIds = library.trashedGroupTimes()
                .filterValues { deletedAt -> deletedAt <= cutoffMillis }
                .keys
                .toList()
            library.deleteTrashedGroups(dueIds)
            dueIds.size
        }

    override suspend fun deleteEmptyGroups(): ScanlyResult<Int> =
        runLibraryResult("Could not remove empty folders.") {
            val emptyIds = library.groups.value.filter { it.documentCount == 0 }.map { it.id }
            emptyIds.forEach(library::deleteGroup)
            emptyIds.size
        }

    override suspend fun setDocumentGroup(
        documentId: String,
        groupId: String?,
    ): ScanlyResult<Unit> = runLibraryResult("Could not update document group.") {
        val document = library.document(documentId) ?: error("Document not found.")
        if (groupId != null) {
            library.group(groupId) ?: error("Group not found.")
        }
        library.upsertDocument(
            document.copy(groupId = groupId, updatedAtMillis = library.now()),
        )
        library.forgetRelease(documentId)
    }
}
