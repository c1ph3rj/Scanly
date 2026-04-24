package `in`.c1ph3rj.scanly.data.document

import androidx.room.withTransaction
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentGroupDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupSummary
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentWithGroup
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.repository.DocumentGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultDocumentGroupRepository @Inject constructor(
    private val database: ScanlyDatabase,
    private val documentDao: DocumentDao,
    private val documentGroupDao: DocumentGroupDao,
    private val dispatchers: ScanlyDispatchers,
) : DocumentGroupRepository {

    override fun observeGroups(): Flow<List<DocumentGroup>> =
        documentGroupDao.observeGroupSummaries().map { groups ->
            groups.map { group -> group.toDomainModel() }
        }

    override fun observeDocumentsInGroup(groupId: String): Flow<List<ScanDocument>> =
        documentDao.observeDocumentsInGroup(groupId).map { documents ->
            documents.map { document -> document.toDomainModel() }
        }

    override suspend fun createGroup(name: String): ScanlyResult<String> =
        withContext(dispatchers.io) {
            val normalizedName = DocumentPresentationFormatter.normalizeTitle(name)
            val groupId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()

            runCatching {
                database.withTransaction {
                    documentGroupDao.insert(
                        DocumentGroupEntity(
                            id = groupId,
                            name = normalizedName,
                            createdAtMillis = timestamp,
                            updatedAtMillis = timestamp,
                        ),
                    )
                }
                groupId
            }.fold(
                onSuccess = { id -> ScanlyResult.Success(id) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not create the group.",
                            cause = throwable,
                        ),
                    )
                },
            )
        }

    override suspend fun renameGroup(
        groupId: String,
        name: String,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        val normalizedName = DocumentPresentationFormatter.normalizeTitle(name)
        runCatching {
            val group = documentGroupDao.getGroup(groupId) ?: error("Group not found.")
            documentGroupDao.update(
                group.copy(
                    name = normalizedName,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }.fold(
            onSuccess = { ScanlyResult.Success(Unit) },
            onFailure = { throwable ->
                ScanlyResult.Failure(
                    ScanlyError(
                        message = throwable.message ?: "Could not rename the group.",
                        cause = throwable,
                    ),
                )
            },
        )
    }

    override suspend fun deleteGroup(groupId: String): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                val timestamp = System.currentTimeMillis()
                database.withTransaction {
                    documentDao.clearGroupFromDocuments(
                        groupId = groupId,
                        updatedAtMillis = timestamp,
                    )
                    documentGroupDao.deleteById(groupId)
                }
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not delete the group.",
                            cause = throwable,
                        ),
                    )
                },
            )
        }

    override suspend fun assignDocumentToGroup(
        documentId: String,
        groupId: String?,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            if (groupId != null) {
                documentGroupDao.getGroup(groupId) ?: error("Group not found.")
            }
            val document = documentDao.getDocument(documentId) ?: error("Document not found.")
            val timestamp = System.currentTimeMillis()
            database.withTransaction {
                documentDao.updateDocumentGroup(
                    documentId = document.id,
                    groupId = groupId,
                    updatedAtMillis = timestamp,
                )
                if (groupId != null) {
                    documentGroupDao.getGroup(groupId)?.let { group ->
                        documentGroupDao.update(group.copy(updatedAtMillis = timestamp))
                    }
                }
            }
        }.fold(
            onSuccess = { ScanlyResult.Success(Unit) },
            onFailure = { throwable ->
                ScanlyResult.Failure(
                    ScanlyError(
                        message = throwable.message ?: "Could not move the document.",
                        cause = throwable,
                    ),
                )
            },
        )
    }

    private fun DocumentGroupSummary.toDomainModel(): DocumentGroup = DocumentGroup(
        id = id,
        name = name,
        documentCount = documentCount,
        pageCount = pageCount,
        coverThumbnailPaths = coverThumbnailPaths
            ?.split('|')
            ?.filter(String::isNotBlank)
            ?.take(3)
            .orEmpty(),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private fun DocumentWithGroup.toDomainModel(): ScanDocument = ScanDocument(
        id = id,
        title = title,
        pageCount = pageCount,
        coverThumbnailPath = coverThumbnailPath,
        groupId = groupId,
        groupName = groupName,
        rootDirectoryPath = rootDirectoryPath,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}
