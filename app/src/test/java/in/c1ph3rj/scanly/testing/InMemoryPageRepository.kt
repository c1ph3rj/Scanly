package `in`.c1ph3rj.scanly.testing

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.TrashedPage
import `in`.c1ph3rj.scanly.domain.repository.FlatPageSetup
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class InMemoryPageRepository(
    private val library: InMemoryScanlyLibrary,
    /** Root for page image paths; point it at a temp folder when a test writes real files. */
    private val storageRoot: String = "/documents",
) : PageRepository {
    override fun observePages(documentId: String): Flow<List<ScanPage>> =
        library.pages.map { pages ->
            pages.filter { it.documentId == documentId }.sortedBy { it.pageIndex }
        }

    override fun observePage(pageId: String): Flow<ScanPage?> =
        library.pages.map { pages -> pages.firstOrNull { it.id == pageId } }

    override suspend fun prepareCapture(documentId: String): ScanlyResult<PageCaptureDraft> =
        runLibraryResult("Could not prepare page capture.") {
            val document = library.document(documentId) ?: error("Document not found.")
            val nextPageIndex = library.pagesFor(documentId).size
            val pageId = UUID.randomUUID().toString()
            pageDraft(
                pageId = pageId,
                documentId = document.id,
                pageIndex = nextPageIndex,
                replacementPageId = null,
            )
        }

    override suspend fun prepareReplacementCapture(pageId: String): ScanlyResult<PageCaptureDraft> =
        runLibraryResult("Could not prepare page replacement.") {
            val page = library.page(pageId) ?: error("Page not found.")
            pageDraft(
                pageId = page.id,
                documentId = page.documentId,
                pageIndex = page.pageIndex,
                replacementPageId = page.id,
            )
        }

    /** Every [FlatPageSetup] passed to [finalizeCapture], in call order (null for camera captures). */
    val receivedFlatPages: MutableList<FlatPageSetup?> = mutableListOf()

    /** The most recent [FlatPageSetup] passed to [finalizeCapture]. */
    var lastFlatPage: FlatPageSetup? = null
        private set

    override suspend fun finalizeCapture(
        draft: PageCaptureDraft,
        flatPage: FlatPageSetup?,
    ): ScanlyResult<String> =
        runLibraryResult("Could not save the captured page.") {
            lastFlatPage = flatPage
            receivedFlatPages += flatPage
            library.document(draft.documentId) ?: error("Document not found.")
            val existing = library.page(draft.pageId)
            val timestamp = library.now()
            library.upsertPage(
                ScanPage(
                    id = draft.pageId,
                    documentId = draft.documentId,
                    pageIndex = draft.pageIndex,
                    rawImagePath = draft.rawImagePath,
                    processedImagePath = draft.processedImagePath,
                    thumbnailPath = draft.thumbnailPath,
                    rotationDegrees = existing?.rotationDegrees ?: 0,
                    cropQuad = existing?.cropQuad,
                    filterPreset = flatPage?.filterPreset ?: existing?.filterPreset ?: PageFilterPreset.AUTO,
                    filterAdjustments = existing?.filterAdjustments ?: PageFilterAdjustments.Default,
                    processingState = PageProcessingState.PROCESSED,
                    createdAtMillis = existing?.createdAtMillis ?: timestamp,
                    updatedAtMillis = timestamp,
                ),
            )
            draft.pageId
        }

    override suspend fun movePage(pageId: String, targetIndex: Int): ScanlyResult<Unit> =
        runLibraryResult("Could not reorder the page.") {
            val page = library.page(pageId) ?: error("Page not found.")
            val pages = library.pagesFor(page.documentId).toMutableList()
            if (pages.isEmpty()) return@runLibraryResult
            val currentIndex = pages.indexOfFirst { it.id == pageId }
            if (currentIndex == -1) error("Page not found in document order.")
            val clampedTargetIndex = targetIndex.coerceIn(0, pages.lastIndex)
            if (clampedTargetIndex == currentIndex) return@runLibraryResult
            val moved = pages.removeAt(currentIndex)
            pages.add(clampedTargetIndex, moved)
            val timestamp = library.now()
            library.replacePages(
                page.documentId,
                pages.mapIndexed { index, existing ->
                    existing.copy(pageIndex = index, updatedAtMillis = timestamp)
                },
            )
        }

    override suspend fun deletePage(pageId: String): ScanlyResult<Unit> =
        runLibraryResult("Could not delete the page.") {
            library.page(pageId) ?: error("Page not found.")
            library.deletePage(pageId)
        }

    override suspend fun movePageToTrash(pageId: String): ScanlyResult<Unit> =
        runLibraryResult("Could not delete the page.") {
            library.page(pageId) ?: error("Page not found.")
            library.trashPage(pageId, library.now())
        }

    override suspend fun restorePage(pageId: String): ScanlyResult<Unit> =
        runLibraryResult("Could not restore the page.") {
            // A live page is a no-op, as in the DB repository.
            if (library.page(pageId) != null) return@runLibraryResult
            library.trashedPageRecord(pageId) ?: error("Page not found.")
            library.restorePage(pageId)
        }

    override fun observeTrashedPages(): Flow<List<TrashedPage>> = library.trashedPages

    override suspend fun deletePageForever(pageId: String): ScanlyResult<Unit> =
        runLibraryResult("Could not delete the page.") {
            check(library.page(pageId) == null) { "Only pages in Recently deleted can be deleted forever." }
            // Unknown ids are a no-op, as in the DB repository.
            library.removeTrashedPages(listOf(pageId))
        }

    override suspend fun emptyTrashedPages(): ScanlyResult<Int> =
        runLibraryResult("Could not delete the pages.") {
            val ids = library.trashedPageRecords().map { it.page.id }
            library.removeTrashedPages(ids)
            ids.size
        }

    override suspend fun purgeTrashedPagesBefore(cutoffMillis: Long): ScanlyResult<Int> =
        runLibraryResult("Could not clear old deleted pages.") {
            val dueIds = library.trashedPageRecords()
                .filter { it.deletedAtMillis <= cutoffMillis }
                .map { it.page.id }
            library.removeTrashedPages(dueIds)
            dueIds.size
        }

    override suspend fun updatePageEdits(
        pageId: String,
        cropQuad: DocumentCornerQuad,
        rotationDegrees: Int,
        filterPreset: PageFilterPreset,
        filterAdjustments: PageFilterAdjustments,
        applyFilterToAllPages: Boolean,
    ): ScanlyResult<Unit> = runLibraryResult("Could not save page edits.") {
        val page = library.page(pageId) ?: error("Page not found.")
        val timestamp = library.now()
        val sanitizedAdjustments = filterAdjustments.sanitized()
        val pagesToUpdate = if (applyFilterToAllPages) {
            library.pagesFor(page.documentId)
        } else {
            listOf(page)
        }
        pagesToUpdate.forEach { existing ->
            val isEditedPage = existing.id == page.id
            library.upsertPage(
                existing.copy(
                    cropQuad = if (isEditedPage) cropQuad else existing.cropQuad,
                    rotationDegrees = if (isEditedPage) rotationDegrees else existing.rotationDegrees,
                    filterPreset = filterPreset,
                    filterAdjustments = if (isEditedPage) {
                        sanitizedAdjustments
                    } else {
                        existing.filterAdjustments
                    },
                    updatedAtMillis = timestamp,
                ),
            )
        }
    }

    private fun pageDraft(
        pageId: String,
        documentId: String,
        pageIndex: Int,
        replacementPageId: String?,
    ): PageCaptureDraft = PageCaptureDraft(
        pageId = pageId,
        documentId = documentId,
        pageIndex = pageIndex,
        rawImagePath = "$storageRoot/$documentId/raw/$pageId.jpg",
        processedImagePath = "$storageRoot/$documentId/processed/$pageId.jpg",
        thumbnailPath = "$storageRoot/$documentId/thumbs/$pageId.jpg",
        replacementPageId = replacementPageId,
    )
}
