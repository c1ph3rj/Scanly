package `in`.c1ph3rj.scanly.data.group

import androidx.room.withTransaction
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.data.library.LibraryMutationCoordinator
import `in`.c1ph3rj.scanly.data.library.LibraryRoomStateUpdater
import `in`.c1ph3rj.scanly.data.library.manifest.GroupManifest
import `in`.c1ph3rj.scanly.data.library.toDomain
import `in`.c1ph3rj.scanly.data.library.toManifest
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentGroupDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupStats
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultGroupRepository @Inject constructor(
    private val database: ScanlyDatabase,
    private val groupDao: DocumentGroupDao,
    private val documentDao: DocumentDao,
    private val pageDao: ScanPageDao,
    private val mutations: LibraryMutationCoordinator,
    private val roomStateUpdater: LibraryRoomStateUpdater,
    private val dispatchers: ScanlyDispatchers,
) : GroupRepository {
    override fun observeGroupsWithStats(): Flow<List<DocumentGroup>> =
        groupDao.observeGroupsWithStats().map { list -> list.map { it.toDomainModel() } }

    override fun observeRecentGroups(limit: Int): Flow<List<DocumentGroup>> =
        groupDao.observeRecentGroupsWithStats(limit).map { list -> list.map { it.toDomainModel() } }

    override fun observeGroupWithStats(groupId: String): Flow<DocumentGroup?> =
        groupDao.observeGroupWithStats(groupId).map { it?.toDomainModel() }

    override fun observeGroupDocuments(groupId: String): Flow<List<ScanDocument>> =
        documentDao.observeDocumentsByGroup(groupId).map { list -> list.map { it.toDomain() } }

    override suspend fun getAllGroupTitles(): List<String> = withContext(dispatchers.io) { groupDao.getAllTitles() }

    override suspend fun suggestGroupTitle(format: GroupTitleFormat): String = withContext(dispatchers.io) {
        DocumentPresentationFormatter.uniqueGroupTitle(format, groupDao.getAllTitles())
    }

    override suspend fun createGroup(title: String): ScanlyResult<String> = withContext(dispatchers.io) {
        val normalized = DocumentPresentationFormatter.resolveUniqueGroupTitle(
            DocumentPresentationFormatter.normalizeGroupTitle(title),
            groupDao.getAllTitles(),
        )
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val manifest = GroupManifest(id = id, revision = 1L, title = normalized, createdAtMillis = now, updatedAtMillis = now)
        resultOf("Could not create the group.") {
            mutations.commitGroup(manifest, "create_group") { checksum, generation ->
                database.withTransaction {
                    groupDao.insert(DocumentGroupEntity(id, normalized, now, now, 1L, checksum))
                    roomStateUpdater.record("group", id, 1L, checksum, generation)
                }
                id
            }
        }
    }

    override suspend fun renameGroup(groupId: String, title: String): ScanlyResult<Unit> = withContext(dispatchers.io) {
        resultOf("Could not rename the group.") {
            val existing = groupDao.getGroup(groupId) ?: error("Group not found.")
            val normalized = DocumentPresentationFormatter.normalizeGroupTitle(title)
            val now = System.currentTimeMillis()
            val manifest = GroupManifest(
                id = groupId,
                revision = existing.revision + 1L,
                title = normalized,
                createdAtMillis = existing.createdAtMillis,
                updatedAtMillis = now,
            )
            mutations.commitGroup(manifest, "rename_group") { checksum, generation ->
                database.withTransaction {
                    groupDao.update(existing.copy(title = normalized, updatedAtMillis = now, revision = manifest.revision, manifestChecksum = checksum))
                    roomStateUpdater.record("group", groupId, manifest.revision, checksum, generation)
                }
            }
        }
    }

    override suspend fun deleteGroup(groupId: String): ScanlyResult<Unit> = withContext(dispatchers.io) {
        resultOf("Could not delete the group.") {
            requireNotNull(groupDao.getGroup(groupId)) { "Group not found." }
            mutations.deleteRecord("group", groupId) { generation ->
                database.withTransaction {
                    groupDao.deleteById(groupId)
                    roomStateUpdater.remove("group", groupId, generation)
                }
            }
        }
    }

    override suspend fun setDocumentGroup(documentId: String, groupId: String?): ScanlyResult<Unit> = withContext(dispatchers.io) {
        resultOf("Could not update document group.") {
            if (groupId != null) requireNotNull(groupDao.getGroup(groupId)) { "Group not found." }
            val document = documentDao.getDocument(documentId) ?: error("Document not found.")
            val now = System.currentTimeMillis()
            val manifest = document.toManifest(pageDao.getPages(documentId), groupId = groupId, updatedAtMillis = now)
            mutations.commitDocument(manifest, "set_document_group") { checksum, generation ->
                database.withTransaction {
                    documentDao.update(document.copy(groupId = groupId, updatedAtMillis = now, revision = manifest.revision, manifestChecksum = checksum))
                    roomStateUpdater.record("document", documentId, manifest.revision, checksum, generation)
                }
            }
        }
    }

    private fun DocumentGroupStats.toDomainModel() = DocumentGroup(
        id = id,
        title = title,
        documentCount = documentCount,
        totalPageCount = totalPageCount,
        coverThumbnail = coverThumbnail,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        coverUpdatedAtMillis = coverUpdatedAtMillis ?: updatedAtMillis,
    )

    private suspend fun <T> resultOf(fallback: String, block: suspend () -> T): ScanlyResult<T> =
        runCatching { block() }.fold(
            onSuccess = { ScanlyResult.Success(it) },
            onFailure = { ScanlyResult.Failure(ScanlyError(it.message ?: fallback, it)) },
        )
}
