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
    private val documentGroupDao: DocumentGroupDao,
    private val documentDao: DocumentDao,
    private val dispatchers: ScanlyDispatchers,
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
                        ScanlyError(message = it.message ?: "Could not create the group.", cause = it),
                    )
                },
            )
        }

    override suspend fun renameGroup(groupId: String, title: String): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            val normalizedTitle = DocumentPresentationFormatter.normalizeGroupTitle(title)
            runCatching {
                val existing = documentGroupDao.getGroup(groupId) ?: error("Group not found.")
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
                        ScanlyError(message = it.message ?: "Could not rename the group.", cause = it),
                    )
                },
            )
        }

    override suspend fun deleteGroup(groupId: String): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                // FK ON DELETE SET NULL will null out groupId on all documents automatically
                database.withTransaction {
                    documentGroupDao.deleteById(groupId)
                }
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = {
                    ScanlyResult.Failure(
                        ScanlyError(message = it.message ?: "Could not delete the group.", cause = it),
                    )
                },
            )
        }

    override suspend fun setDocumentGroup(documentId: String, groupId: String?): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                val doc = documentDao.getDocument(documentId) ?: error("Document not found.")
                database.withTransaction {
                    documentDao.update(
                        doc.copy(
                            groupId = groupId,
                            updatedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = {
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = it.message ?: "Could not update document group.",
                            cause = it,
                        ),
                    )
                },
            )
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
