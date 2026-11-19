package `in`.c1ph3rj.scanly.data.document

import androidx.room.withTransaction
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.data.common.LibraryOperationCoordinator
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.PageMarkupDao
import `in`.c1ph3rj.scanly.data.local.db.dao.PageTextDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.PageMarkupEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.PageTextEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity
import `in`.c1ph3rj.scanly.data.storage.DocumentStorageManager
import `in`.c1ph3rj.scanly.domain.repository.DocumentSplitRepository
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Splits by copying: each part gets its own document folder with copies of its pages' files,
 * and new page, text and markup rows. The source is never edited in place, so a failure part
 * way leaves it untouched (the parts made so far are removed), and "don't keep the original"
 * only moves it to Recently deleted.
 */
@Singleton
class DefaultDocumentSplitRepository @Inject constructor(
    private val database: ScanlyDatabase,
    private val documentDao: DocumentDao,
    private val scanPageDao: ScanPageDao,
    private val pageTextDao: PageTextDao,
    private val pageMarkupDao: PageMarkupDao,
    private val documentStorageManager: DocumentStorageManager,
    private val dispatchers: ScanlyDispatchers,
    private val operationCoordinator: LibraryOperationCoordinator,
) : DocumentSplitRepository {

    override suspend fun splitDocument(
        documentId: String,
        parts: List<List<Int>>,
        keepOriginal: Boolean,
    ): ScanlyResult<List<String>> = withContext(dispatchers.io) {
        operationCoordinator.withMutation {
            val created = mutableListOf<String>()
            runCatching {
                val source = checkNotNull(documentDao.getDocument(documentId)) { appString(R.string.error_document_not_found) }
                check(source.deletedAtMillis == null) { appString(R.string.error_document_not_found) }
                val pages = scanPageDao.getPages(documentId)
                require(parts.isNotEmpty() && parts.all { part -> part.isNotEmpty() && part.all { it in pages.indices } }) {
                    appString(R.string.document_split_failed)
                }
                val takenTitles = documentDao.getAllTitles().toMutableList()
                parts.forEachIndexed { partIndex, positions ->
                    val title = DocumentPresentationFormatter.resolveUniqueTitle(
                        baseTitle = appString(R.string.document_split_part_title, source.title, partIndex + 1),
                        existingTitles = takenTitles,
                    )
                    takenTitles += title
                    val newId = UUID.randomUUID().toString()
                    created += newId
                    copyPart(source, pages, positions, newId, title)
                }
                if (!keepOriginal) {
                    database.withTransaction {
                        documentDao.setDeletedAt(documentId, System.currentTimeMillis())
                    }
                }
                created.toList()
            }.fold(
                onSuccess = { ScanlyResult.Success(it) },
                onFailure = { throwable ->
                    created.forEach { id ->
                        runCatching { database.withTransaction { documentDao.deleteById(id) } }
                        runCatching { documentStorageManager.deleteDocumentStorage(id) }
                    }
                    ScanlyResult.Failure(
                        ScanlyError(message = throwable.message ?: appString(R.string.document_split_failed), cause = throwable),
                    )
                },
            )
        }
    }

    private suspend fun copyPart(
        source: DocumentEntity,
        pages: List<ScanPageEntity>,
        positions: List<Int>,
        newId: String,
        title: String,
    ) {
        val layout = documentStorageManager.createDocumentScaffold(documentId = newId, title = title)
        val root = File(layout.rootDirectoryPath)
        val newPages = mutableListOf<ScanPageEntity>()
        val texts = mutableListOf<PageTextEntity>()
        val markups = mutableListOf<PageMarkupEntity>()
        positions.forEachIndexed { newIndex, position ->
            val page = pages[position]
            val newPageId = UUID.randomUUID().toString()
            val processed = copyInto(page.processedImagePath, File(root, ProcessedDirectory))
            newPages += page.copy(
                id = newPageId,
                documentId = newId,
                pageIndex = newIndex,
                rawImagePath = copyInto(page.rawImagePath, File(root, RawDirectory)),
                processedImagePath = processed,
                thumbnailPath = copyInto(page.thumbnailPath, File(root, ThumbnailDirectory)),
                deletedAtMillis = null,
                deletedFromIndex = null,
                // updatedAtMillis is kept so the copied text still counts as up to date.
            )
            pageTextDao.getPageText(page.id)?.let { text ->
                texts += text.copy(id = 0L, pageId = newPageId, documentId = newId)
            }
            pageMarkupDao.getMarkup(page.id)?.let { markup ->
                // The markup's clean image sits beside the processed image as "<name>.base.jpg".
                val base = File(markup.baseImagePath)
                if (base.isFile && processed != null) {
                    val processedFile = File(processed)
                    val target = File(processedFile.parentFile, "${processedFile.nameWithoutExtension}.base.jpg")
                    base.copyTo(target, overwrite = true)
                    markups += markup.copy(pageId = newPageId, baseImagePath = target.absolutePath)
                }
            }
        }
        val now = System.currentTimeMillis()
        val document = DocumentEntity(
            id = newId,
            title = title,
            pageCount = newPages.size,
            coverThumbnailPath = resolveDocumentPreviewPath(newPages.firstOrNull()) ?: layout.coverThumbnailPath,
            preferredFilterPreset = source.preferredFilterPreset,
            rootDirectoryPath = layout.rootDirectoryPath,
            createdAtMillis = now,
            updatedAtMillis = now,
            groupId = source.groupId,
        )
        database.withTransaction {
            documentDao.insert(document)
            scanPageDao.insertAll(newPages)
            // Plain inserts so Room's FTS triggers index the copied text.
            texts.forEach { pageTextDao.insert(it) }
            markups.forEach { pageMarkupDao.upsertMarkup(it) }
        }
    }

    /** Copies [path] into [directory] under the same name (numbered if taken); null if missing. */
    private fun copyInto(path: String?, directory: File): String? {
        val file = path?.let(::File)?.takeIf { it.isFile } ?: return null
        directory.mkdirs()
        var target = File(directory, file.name)
        var counter = 2
        while (target.exists()) {
            target = File(directory, "${file.nameWithoutExtension}_$counter.${file.extension}")
            counter++
        }
        file.copyTo(target)
        return target.absolutePath
    }

    private companion object {
        const val RawDirectory = "raw"
        const val ProcessedDirectory = "processed"
        const val ThumbnailDirectory = "thumbs"
    }
}
