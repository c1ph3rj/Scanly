package `in`.c1ph3rj.scanly.data.document

import androidx.room.withTransaction
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentWithGroup
import `in`.c1ph3rj.scanly.data.storage.DocumentStorageManager
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultDocumentRepository @Inject constructor(
    private val database: ScanlyDatabase,
    private val documentDao: DocumentDao,
    private val documentStorageManager: DocumentStorageManager,
    private val dispatchers: ScanlyDispatchers,
) : DocumentRepository {

    override fun observeDocuments(): Flow<List<ScanDocument>> =
        documentDao.observeDocumentsWithGroup().map { documents ->
            documents.map { document -> document.toDomain() }
        }

    override fun observeDocument(documentId: String): Flow<ScanDocument?> =
        documentDao.observeDocumentWithGroup(documentId).map { document ->
            document?.toDomain()
        }

    override suspend fun createDocument(title: String): ScanlyResult<String> =
        withContext(dispatchers.io) {
            val normalizedTitle = DocumentPresentationFormatter.normalizeTitle(title)
            val documentId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()

            runCatching {
                val fileLayout = documentStorageManager.createDocumentScaffold(
                    documentId = documentId,
                    title = normalizedTitle,
                )
                val document = DocumentEntity(
                    id = documentId,
                    title = normalizedTitle,
                    pageCount = 0,
                    coverThumbnailPath = fileLayout.coverThumbnailPath,
                    preferredFilterPreset = null,
                    groupId = null,
                    rootDirectoryPath = fileLayout.rootDirectoryPath,
                    createdAtMillis = timestamp,
                    updatedAtMillis = timestamp,
                )
                database.withTransaction {
                    documentDao.insert(document)
                }
                documentId
            }.fold(
                onSuccess = { id -> ScanlyResult.Success(id) },
                onFailure = { throwable ->
                    runCatching { documentStorageManager.deleteDocumentStorage(documentId) }
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not create the document.",
                            cause = throwable,
                        ),
                    )
                },
            )
        }

    override suspend fun renameDocument(
        documentId: String,
        title: String,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        val normalizedTitle = DocumentPresentationFormatter.normalizeTitle(title)

        runCatching {
            val existingDocument = documentDao.getDocument(documentId)
                ?: error("Document not found.")
            val updatedCoverThumbnail = documentStorageManager.refreshDocumentCover(
                documentId = documentId,
                title = normalizedTitle,
            )
            database.withTransaction {
                documentDao.update(
                    existingDocument.copy(
                        title = normalizedTitle,
                        coverThumbnailPath = updatedCoverThumbnail,
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }.fold(
            onSuccess = { ScanlyResult.Success(Unit) },
            onFailure = { throwable ->
                ScanlyResult.Failure(
                    ScanlyError(
                        message = throwable.message ?: "Could not rename the document.",
                        cause = throwable,
                    ),
                )
            },
        )
    }

    override suspend fun deleteDocument(documentId: String): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                database.withTransaction {
                    documentDao.deleteById(documentId)
                }
                documentStorageManager.deleteDocumentStorage(documentId)
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not delete the document.",
                            cause = throwable,
                        ),
                    )
                },
            )
        }

    private fun DocumentWithGroup.toDomain(): ScanDocument = ScanDocument(
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
