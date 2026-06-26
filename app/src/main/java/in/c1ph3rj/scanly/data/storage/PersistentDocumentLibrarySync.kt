package `in`.c1ph3rj.scanly.data.storage

import androidx.room.withTransaction
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentDocumentLibrarySync @Inject constructor(
    private val database: ScanlyDatabase,
    private val documentDao: DocumentDao,
    private val scanPageDao: ScanPageDao,
    private val storageManager: DocumentStorageManager,
    private val dispatchers: ScanlyDispatchers,
) {
    suspend fun sync() = withContext(dispatchers.io) {
        migrateLegacyPrivateDocuments()
        recoverMissingPersistentDocuments()
    }

    private suspend fun migrateLegacyPrivateDocuments() {
        documentDao.getDocuments()
            .filter { document -> storageManager.isLegacyPrivateDocumentPath(document.rootDirectoryPath) }
            .forEach { document -> migrateLegacyDocument(document) }
    }

    private suspend fun migrateLegacyDocument(document: DocumentEntity) {
        val pages = scanPageDao.getPages(document.id)
        val newLayout = storageManager.createDocumentScaffold(
            documentId = document.id,
            title = document.title,
        )
        val migratedPages = pages.map { page ->
            val draft = storageManager.createPageCaptureDraft(
                documentId = document.id,
                pageIndex = page.pageIndex,
            )
            val rawImagePath = copyIfPresent(page.rawImagePath, draft.rawImagePath)
            val processedImagePath = copyIfPresent(page.processedImagePath, draft.processedImagePath)
            val thumbnailPath = copyIfPresent(page.thumbnailPath, draft.thumbnailPath)

            page.copy(
                rawImagePath = rawImagePath,
                processedImagePath = processedImagePath,
                thumbnailPath = thumbnailPath,
            )
        }
        val migratedUpdatedAtMillis = migratedPages.maxOfOrNull { page -> page.updatedAtMillis }
            ?.coerceAtLeast(document.updatedAtMillis)
            ?: document.updatedAtMillis
        val coverThumbnailPath = migratedPages.firstOrNull()?.thumbnailPath ?: newLayout.coverThumbnailPath

        database.withTransaction {
            if (migratedPages.isNotEmpty()) {
                scanPageDao.updateAll(migratedPages)
            }
            documentDao.update(
                document.copy(
                    rootDirectoryPath = newLayout.rootDirectoryPath,
                    coverThumbnailPath = coverThumbnailPath,
                    updatedAtMillis = migratedUpdatedAtMillis,
                ),
            )
        }

        storageManager.deleteLegacyPrivateDocumentStorage(document.id)
    }

    private suspend fun recoverMissingPersistentDocuments() {
        val existingDocuments = documentDao.getDocuments()
        val knownDocumentIds = existingDocuments.mapTo(mutableSetOf()) { document -> document.id }
        val existingTitles = existingDocuments.mapTo(mutableSetOf()) { document -> document.title }

        storageManager.discoverStoredDocuments()
            .filter { snapshot -> snapshot.id !in knownDocumentIds }
            .forEach { snapshot ->
                val title = DocumentPresentationFormatter.resolveUniqueTitle(
                    baseTitle = "Recovered ${snapshot.id.take(8).ifBlank { "document" }}",
                    existingTitles = existingTitles,
                )
                recoverDocument(snapshot = snapshot, title = title)
                knownDocumentIds += snapshot.id
                existingTitles += title
            }
    }

    private suspend fun recoverDocument(
        snapshot: StoredDocumentSnapshot,
        title: String,
    ) {
        val pages = snapshot.pages
            .filter { page ->
                page.rawImagePath != null || page.processedImagePath != null || page.thumbnailPath != null
            }
            .sortedBy { page -> page.pageIndex }
        val pageEntities = pages.mapIndexed { recoveredIndex, page ->
            ScanPageEntity(
                id = UUID.randomUUID().toString(),
                documentId = snapshot.id,
                pageIndex = recoveredIndex,
                rawImagePath = page.rawImagePath,
                processedImagePath = page.processedImagePath,
                thumbnailPath = page.thumbnailPath,
                rotationDegrees = 0,
                cropTopLeftX = null,
                cropTopLeftY = null,
                cropTopRightX = null,
                cropTopRightY = null,
                cropBottomRightX = null,
                cropBottomRightY = null,
                cropBottomLeftX = null,
                cropBottomLeftY = null,
                filterPreset = PageFilterPreset.ORIGINAL.storageValue,
                processingState = if (page.processedImagePath != null) {
                    PageProcessingState.PROCESSED.storageValue
                } else {
                    PageProcessingState.CAPTURED.storageValue
                },
                createdAtMillis = page.updatedAtMillis,
                updatedAtMillis = page.updatedAtMillis,
            )
        }
        val coverThumbnailPath = snapshot.coverThumbnailPath
            ?: pageEntities.firstOrNull()?.thumbnailPath
        val document = DocumentEntity(
            id = snapshot.id,
            title = title,
            pageCount = pageEntities.size,
            coverThumbnailPath = coverThumbnailPath,
            preferredFilterPreset = null,
            rootDirectoryPath = snapshot.rootDirectoryPath,
            createdAtMillis = snapshot.createdAtMillis,
            updatedAtMillis = snapshot.updatedAtMillis,
            groupId = null,
        )

        database.withTransaction {
            documentDao.insert(document)
            if (pageEntities.isNotEmpty()) {
                scanPageDao.insertAll(pageEntities)
            }
        }
    }

    private fun copyIfPresent(
        sourcePath: String?,
        targetPath: String,
    ): String? {
        val source = sourcePath?.let(::File) ?: return null
        if (!source.exists() || !source.isFile || source.length() <= 0L) {
            return null
        }
        val target = File(targetPath)
        target.parentFile?.mkdirs()
        if (source.absolutePath != target.absolutePath) {
            source.inputStream().use { inputStream ->
                target.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return target.absolutePath
    }
}
