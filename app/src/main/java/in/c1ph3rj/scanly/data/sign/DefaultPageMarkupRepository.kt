package `in`.c1ph3rj.scanly.data.sign

import androidx.room.withTransaction
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.common.runCatchingCancellable
import `in`.c1ph3rj.scanly.data.common.LibraryOperationCoordinator
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.PageMarkupDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity
import `in`.c1ph3rj.scanly.domain.model.PageMarkup
import `in`.c1ph3rj.scanly.domain.model.SignMark
import `in`.c1ph3rj.scanly.domain.repository.PageMarkupRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPageMarkupRepository @Inject constructor(
    private val database: ScanlyDatabase,
    private val documentDao: DocumentDao,
    private val scanPageDao: ScanPageDao,
    private val markupDao: PageMarkupDao,
    private val markupStore: PageMarkupStore,
    private val operationCoordinator: LibraryOperationCoordinator,
    private val dispatchers: ScanlyDispatchers,
) : PageMarkupRepository {

    override suspend fun loadDocumentMarkups(documentId: String): ScanlyResult<Map<String, PageMarkup>> =
        withContext(dispatchers.io) {
            runCatchingCancellable {
                markupDao.getMarkupsForDocument(documentId).associate { row ->
                    row.pageId to PageMarkup(
                        pageId = row.pageId,
                        marks = SignMarkCodec.decode(row.marksJson),
                        baseImagePath = row.baseImagePath,
                        updatedAtMillis = row.updatedAtMillis,
                    )
                }
            }.toScanlyResult("Could not load signatures for this document.")
        }

    override suspend fun saveDocumentMarkups(
        documentId: String,
        marksByPageId: Map<String, List<SignMark>>,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        operationCoordinator.withMutation {
            runCatchingCancellable {
                val document = documentDao.getDocument(documentId) ?: error("Document not found.")
                val pages = scanPageDao.getPages(documentId).associateBy { it.id }
                val timestamp = System.currentTimeMillis()
                // Fail before touching any page, so a stale signature never leaves a half-saved document.
                // Only marks the document does not own yet are copied on save, so only those must exist.
                marksByPageId.forEach { (pageId, marks) ->
                    if (pageId !in pages) return@forEach
                    marks.filterIsInstance<SignMark.Image>()
                        .filterNot { mark -> PageMarkupStore.isOwnedMarkupImage(mark.imagePath) }
                        .forEach { mark ->
                            require(File(mark.imagePath).isFile) { "A signature image is missing. Place it again." }
                        }
                }
                val changedPages = mutableListOf<ScanPageEntity>()
                try {
                    marksByPageId.forEach { (pageId, marks) ->
                        val page = pages[pageId] ?: return@forEach
                        if (markupStore.apply(page, marks)) changedPages += page.copy(updatedAtMillis = timestamp)
                    }
                } finally {
                    // Pages stamped before a failure changed on disk too, so they are recorded either way.
                    if (changedPages.isNotEmpty()) {
                        withContext(NonCancellable) {
                            database.withTransaction {
                                // A new updatedAtMillis also makes the text indexer re-read the stamped pages.
                                scanPageDao.updateAll(changedPages)
                                documentDao.update(document.copy(updatedAtMillis = timestamp))
                            }
                        }
                    }
                }
            }.toScanlyResult("Could not save the signed pages.")
        }
    }

    private fun <T> Result<T>.toScanlyResult(fallbackMessage: String): ScanlyResult<T> =
        fold(
            onSuccess = { ScanlyResult.Success(it) },
            onFailure = { ScanlyResult.Failure(ScanlyError(it.message ?: fallbackMessage, it)) },
        )
}
