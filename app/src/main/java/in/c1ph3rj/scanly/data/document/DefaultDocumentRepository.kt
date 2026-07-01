package `in`.c1ph3rj.scanly.data.document

import androidx.room.withTransaction
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.data.library.LibraryMutationCoordinator
import `in`.c1ph3rj.scanly.data.library.LibraryRoomStateUpdater
import `in`.c1ph3rj.scanly.data.library.manifest.DocumentManifest
import `in`.c1ph3rj.scanly.data.library.toDomain
import `in`.c1ph3rj.scanly.data.library.toManifest
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
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
    private val scanPageDao: ScanPageDao,
    private val mutations: LibraryMutationCoordinator,
    private val roomStateUpdater: LibraryRoomStateUpdater,
    private val dispatchers: ScanlyDispatchers,
) : DocumentRepository {
    override fun observeDocuments(): Flow<List<ScanDocument>> =
        documentDao.observeDocuments().map { list -> list.map(DocumentEntity::toDomain) }

    override fun observeRecentDocuments(limit: Int): Flow<List<ScanDocument>> =
        documentDao.observeRecentDocuments(limit).map { list -> list.map(DocumentEntity::toDomain) }

    override fun observeUngroupedDocuments(): Flow<List<ScanDocument>> =
        documentDao.observeUngroupedDocuments().map { list -> list.map(DocumentEntity::toDomain) }

    override fun observeDocument(documentId: String): Flow<ScanDocument?> =
        documentDao.observeDocument(documentId).map { it?.toDomain() }

    override suspend fun getAllDocumentTitles(): List<String> = withContext(dispatchers.io) {
        documentDao.getAllTitles()
    }

    override suspend fun suggestDocumentTitle(format: DocumentTitleFormat): String = withContext(dispatchers.io) {
        DocumentPresentationFormatter.uniqueDocumentTitle(format, documentDao.getAllTitles())
    }

    override suspend fun createDocument(title: String, groupId: String?): ScanlyResult<String> = withContext(dispatchers.io) {
        val normalized = DocumentPresentationFormatter.resolveUniqueTitle(
            DocumentPresentationFormatter.normalizeTitle(title),
            documentDao.getAllTitles(),
        )
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val manifest = DocumentManifest(
            id = id,
            revision = 1L,
            title = normalized,
            groupId = groupId,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        resultOf("Could not create the document.") {
            mutations.commitDocument(manifest, "create_document") { checksum, generation ->
                database.withTransaction {
                    documentDao.insert(
                        DocumentEntity(
                            id = id,
                            title = normalized,
                            pageCount = 0,
                            coverThumbnail = null,
                            preferredFilterPreset = null,
                            createdAtMillis = now,
                            updatedAtMillis = now,
                            groupId = groupId,
                            revision = 1L,
                            manifestChecksum = checksum,
                        ),
                    )
                    roomStateUpdater.record("document", id, 1L, checksum, generation)
                }
                id
            }
        }
    }

    override suspend fun createImportedDocument(groupId: String?): ScanlyResult<String> = withContext(dispatchers.io) {
        createDocument(DocumentPresentationFormatter.uniqueImportedDocumentTitle(documentDao.getAllTitles()), groupId)
    }

    override suspend fun renameDocument(documentId: String, title: String): ScanlyResult<Unit> = withContext(dispatchers.io) {
        resultOf("Could not rename the document.") {
            val existing = documentDao.getDocument(documentId) ?: error("Document not found.")
            val normalized = DocumentPresentationFormatter.normalizeTitle(title)
            val now = System.currentTimeMillis()
            val manifest = existing.toManifest(scanPageDao.getPages(documentId), title = normalized, updatedAtMillis = now)
            mutations.commitDocument(manifest, "rename_document") { checksum, generation ->
                database.withTransaction {
                    documentDao.update(
                        existing.copy(
                            title = normalized,
                            updatedAtMillis = now,
                            revision = manifest.revision,
                            manifestChecksum = checksum,
                        ),
                    )
                    roomStateUpdater.record("document", documentId, manifest.revision, checksum, generation)
                }
            }
        }
    }

    override suspend fun deleteDocument(documentId: String): ScanlyResult<Unit> = withContext(dispatchers.io) {
        resultOf("Could not delete the document.") {
            requireNotNull(documentDao.getDocument(documentId)) { "Document not found." }
            mutations.deleteRecord("document", documentId) { generation ->
                database.withTransaction {
                    documentDao.deleteById(documentId)
                    roomStateUpdater.remove("document", documentId, generation)
                }
            }
        }
    }

    private suspend fun <T> resultOf(fallback: String, block: suspend () -> T): ScanlyResult<T> =
        runCatching { block() }.fold(
            onSuccess = { ScanlyResult.Success(it) },
            onFailure = { ScanlyResult.Failure(ScanlyError(it.message ?: fallback, it)) },
        )
}

internal fun resolveDocumentPreviewAsset(firstPage: `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity?):
    `in`.c1ph3rj.scanly.domain.model.LibraryAssetRef? =
    firstPage?.thumbnailAsset ?: firstPage?.processedAsset ?: firstPage?.rawAsset
