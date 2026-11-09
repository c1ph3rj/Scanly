package `in`.c1ph3rj.scanly.data.group

import androidx.room.withTransaction
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentGroupDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupStats
import `in`.c1ph3rj.scanly.data.common.LibraryOperationCoordinator
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.TrashedFolder
import `in`.c1ph3rj.scanly.data.storage.DocumentStorageManager
import `in`.c1ph3rj.scanly.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString

@Singleton
class DefaultGroupRepository @Inject constructor(
    private val database: ScanlyDatabase,
    private val documentGroupDao: DocumentGroupDao,
    private val documentDao: DocumentDao,
    private val documentStorageManager: DocumentStorageManager,
    private val dispatchers: ScanlyDispatchers,
    private val operationCoordinator: LibraryOperationCoordinator,
) : GroupRepository {

    override fun observeGroupsWithStats(): Flow<List<DocumentGroup>> =
        documentGroupDao.observeGroupsWithStats().map { list ->
            list.map { it.toDomain() }
        }

    override fun observeRecentGroups(limit: Int): Flow<List<DocumentGroup>> =
        documentGroupDao.observeRecentGroupsWithStats(limit).map { list ->
            list.map { it.toDomain() }
        }

    override fun observeGroupWithStats(groupId: String): Flow<DocumentGroup?> =
        documentGroupDao.observeGroupWithStats(groupId).map { it?.toDomain() }

    override fun observeGroupDocuments(groupId: String): Flow<List<ScanDocument>> =
        documentDao.observeDocumentsByGroup(groupId).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getAllGroupTitles(): List<String> =
        withContext(dispatchers.io) {
            documentGroupDao.getAllTitles()
        }

    override suspend fun suggestGroupTitle(format: GroupTitleFormat): String =
        withContext(dispatchers.io) {
            DocumentPresentationFormatter.uniqueGroupTitle(
                format = format,
                existingTitles = documentGroupDao.getAllTitles(),
            )
        }

    override suspend fun createGroup(title: String): ScanlyResult<String> =
        withContext(dispatchers.io) {
            operationCoordinator.withMutation {
            val normalizedTitle = DocumentPresentationFormatter.resolveUniqueGroupTitle(
                baseTitle = DocumentPresentationFormatter.normalizeGroupTitle(title),
                existingTitles = documentGroupDao.getAllTitles(),
            )
            val groupId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            runCatching {
                database.withTransaction {
                    documentGroupDao.insert(
                        DocumentGroupEntity(
                            id = groupId,
                            title = normalizedTitle,
                            createdAtMillis = timestamp,
                            updatedAtMillis = timestamp,
                        ),
                    )
                }
                groupId
            }.fold(
                onSuccess = { ScanlyResult.Success(it) },
                onFailure = {
                    ScanlyResult.Failure(
                        ScanlyError(message = it.message ?: appString(R.string.error_could_not_create_the_group), cause = it),
                    )
                },
            )
            }
        }

    override suspend fun renameGroup(groupId: String, title: String): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            operationCoordinator.withMutation {
            val normalizedTitle = DocumentPresentationFormatter.normalizeGroupTitle(title)
            runCatching {
                val existing = documentGroupDao.getGroup(groupId) ?: error(appString(R.string.error_group_not_found))
                database.withTransaction {
                    documentGroupDao.update(
                        existing.copy(
                            title = normalizedTitle,
                            updatedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = {
                    ScanlyResult.Failure(
                        ScanlyError(message = it.message ?: appString(R.string.error_could_not_rename_the_group), cause = it),
                    )
                },
            )
            }
        }

    override suspend fun deleteGroup(groupId: String): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            operationCoordinator.withMutation {
            runCatching {
                // FK ON DELETE SET NULL will null out groupId on all documents automatically
                database.withTransaction {
                    documentDao.forgetReleasedGroup(groupId)
                    documentGroupDao.deleteById(groupId)
                }
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = {
                    ScanlyResult.Failure(
                        ScanlyError(message = it.message ?: appString(R.string.error_could_not_delete_the_group), cause = it),
                    )
                },
            )
            }
        }

    override suspend fun moveGroupToTrash(groupId: String, includeDocuments: Boolean): ScanlyResult<Unit> =
        mutateGroup(appString(R.string.default_group_repository_could_not_move_the_folder)) {
            checkNotNull(documentGroupDao.getGroup(groupId)) { appString(R.string.error_folder_not_found) }
            val timestamp = System.currentTimeMillis()
            database.withTransaction {
                documentGroupDao.setDeletedAt(groupId, timestamp)
                if (includeDocuments) {
                    documentDao.trashWithGroup(groupId, timestamp)
                } else {
                    documentDao.releaseFromGroup(groupId)
                }
            }
        }

    override suspend fun restoreGroup(groupId: String): ScanlyResult<Unit> =
        mutateGroup(appString(R.string.default_group_repository_could_not_restore_the_folder)) {
            checkNotNull(documentGroupDao.getGroup(groupId)) { appString(R.string.error_folder_not_found) }
            database.withTransaction {
                documentGroupDao.setDeletedAt(groupId, null)
                documentDao.restoreWithGroup(groupId)
                documentDao.returnReleasedToGroup(groupId)
                documentDao.forgetReleasedGroup(groupId)
            }
        }

    override fun observeTrashedGroups(): Flow<List<TrashedFolder>> =
        documentGroupDao.observeTrashedGroups().map { rows ->
            rows.map { row ->
                TrashedFolder(
                    id = row.id,
                    title = row.title,
                    documentCount = row.documentCount,
                    pageCount = row.totalPageCount,
                    coverThumbnailPath = row.coverThumbnailPath,
                    deletedAtMillis = row.deletedAtMillis,
                    keptDocumentCount = row.keptDocumentCount,
                )
            }
        }

    override suspend fun deleteGroupForever(groupId: String): ScanlyResult<Unit> =
        mutateGroup(appString(R.string.default_group_repository_could_not_delete_the_folder)) { deleteTrashedGroups(listOf(groupId)) }

    override suspend fun emptyTrashedGroups(): ScanlyResult<Int> =
        mutateGroup(appString(R.string.default_group_repository_could_not_delete_the_folders)) {
            deleteTrashedGroups(documentGroupDao.getTrashedGroupIds())
        }

    override suspend fun purgeTrashedGroupsBefore(cutoffMillis: Long): ScanlyResult<Int> =
        mutateGroup(appString(R.string.default_group_repository_could_not_clear_old_deleted)) {
            deleteTrashedGroups(documentGroupDao.getTrashedGroupIdsBefore(cutoffMillis))
        }

    /** Removes the folders and the documents that went to the bin with them, files included. */
    private suspend fun deleteTrashedGroups(groupIds: List<String>): Int {
        if (groupIds.isEmpty()) return 0
        val documentIds = groupIds.flatMap { documentDao.getIdsDeletedWithGroup(it) }
        database.withTransaction {
            documentIds.forEach { documentDao.deleteById(it) }
            // Documents deleted on their own earlier keep their bin entry; the FK moves them out.
            groupIds.forEach {
                documentDao.forgetReleasedGroup(it)
                documentGroupDao.deleteById(it)
            }
        }
        documentIds.forEach { id -> runCatching { documentStorageManager.deleteDocumentStorage(id) } }
        return groupIds.size
    }

    private suspend fun <T> mutateGroup(failureMessage: String, block: suspend () -> T): ScanlyResult<T> =
        withContext(dispatchers.io) {
            operationCoordinator.withMutation {
                runCatching { block() }.fold(
                    onSuccess = { ScanlyResult.Success(it) },
                    onFailure = {
                        ScanlyResult.Failure(ScanlyError(message = it.message ?: failureMessage, cause = it))
                    },
                )
            }
        }

    override suspend fun deleteEmptyGroups(): ScanlyResult<Int> =
        withContext(dispatchers.io) {
            operationCoordinator.withMutation {
                runCatching {
                    val emptyIds = documentGroupDao.getEmptyGroupIds()
                    if (emptyIds.isEmpty()) return@runCatching 0
                    database.withTransaction {
                        emptyIds.forEach { documentGroupDao.deleteById(it) }
                    }
                    emptyIds.size
                }.fold(
                    onSuccess = { ScanlyResult.Success(it) },
                    onFailure = {
                        ScanlyResult.Failure(
                            ScanlyError(
                                message = it.message ?: appString(R.string.error_could_not_remove_empty_folders),
                                cause = it,
                            ),
                        )
                    },
                )
            }
        }

    override suspend fun setDocumentGroup(documentId: String, groupId: String?): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            operationCoordinator.withMutation {
            runCatching {
                val doc = documentDao.getDocument(documentId) ?: error(appString(R.string.error_document_not_found))
                database.withTransaction {
                    documentDao.update(
                        doc.copy(
                            groupId = groupId,
                            // A move by hand overrides "rejoin the folder if it is restored".
                            releasedFromGroupId = null,
                            updatedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = {
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = it.message ?: appString(R.string.error_could_not_update_document_group),
                            cause = it,
                        ),
                    )
                },
            )
            }
        }

    private fun DocumentGroupStats.toDomain(): DocumentGroup = DocumentGroup(
        id = id,
        title = title,
        documentCount = documentCount,
        totalPageCount = totalPageCount,
        coverThumbnailPath = coverThumbnailPath,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        coverUpdatedAtMillis = coverUpdatedAtMillis ?: updatedAtMillis,
    )

    private fun `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity.toDomain(): ScanDocument =
        ScanDocument(
            id = id,
            title = title,
            pageCount = pageCount,
            coverThumbnailPath = coverThumbnailPath,
            rootDirectoryPath = rootDirectoryPath,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
            groupId = groupId,
        )
}
