package `in`.c1ph3rj.scanly.data.page

import androidx.room.withTransaction
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ui.ThumbnailCache
import `in`.c1ph3rj.scanly.data.library.DocumentAssetReader
import `in`.c1ph3rj.scanly.data.library.LibraryMutationCoordinator
import `in`.c1ph3rj.scanly.data.library.LibraryRoomStateUpdater
import `in`.c1ph3rj.scanly.data.library.WorkingFileStore
import `in`.c1ph3rj.scanly.data.library.toDomain
import `in`.c1ph3rj.scanly.data.library.toManifest
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity
import `in`.c1ph3rj.scanly.domain.model.LibraryAssetRef
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.processing.PageImageProcessor
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPageRepository @Inject constructor(
    private val database: ScanlyDatabase,
    private val documentDao: DocumentDao,
    private val pageDao: ScanPageDao,
    private val workingFiles: WorkingFileStore,
    private val assetReader: DocumentAssetReader,
    private val mutations: LibraryMutationCoordinator,
    private val roomStateUpdater: LibraryRoomStateUpdater,
    private val pageImageProcessor: PageImageProcessor,
    private val thumbnailCache: ThumbnailCache,
    private val dispatchers: ScanlyDispatchers,
) : PageRepository {
    override fun observePages(documentId: String): Flow<List<ScanPage>> =
        pageDao.observePages(documentId).map { list -> list.map(ScanPageEntity::toDomain) }

    override fun observePage(pageId: String): Flow<ScanPage?> =
        pageDao.observePage(pageId).map { it?.toDomain() }

    override suspend fun prepareCapture(documentId: String): ScanlyResult<PageCaptureDraft> = withContext(dispatchers.io) {
        resultOf("Could not prepare page capture.") {
            val document = documentDao.getDocument(documentId) ?: error("Document not found.")
            createDraft(document.id, UUID.randomUUID().toString(), pageDao.countPages(document.id), null)
        }
    }

    override suspend fun prepareReplacementCapture(pageId: String): ScanlyResult<PageCaptureDraft> = withContext(dispatchers.io) {
        resultOf("Could not prepare page replacement.") {
            val page = pageDao.getPage(pageId) ?: error("Page not found.")
            createDraft(page.documentId, page.id, page.pageIndex, page.id)
        }
    }

    override suspend fun finalizeCapture(draft: PageCaptureDraft): ScanlyResult<String> = withContext(dispatchers.io) {
        resultOf("Could not save the captured page.") {
            val document = documentDao.getDocument(draft.documentId) ?: error("Document not found.")
            val capture = File(draft.captureFilePath)
            require(capture.isFile && capture.length() > 0L) { "Captured image is missing." }
            val existing = pageDao.getPage(draft.pageId)
            val now = System.currentTimeMillis()
            val revision = document.revision + 1L
            val filter = document.preferredFilterPreset?.let(PageFilterPreset::fromStorage) ?: PageFilterPreset.AUTO
            val processed = runCatching {
                pageImageProcessor.processCapture(
                    rawImagePath = draft.captureFilePath,
                    processedImagePath = draft.processedWorkingPath,
                    thumbnailPath = draft.thumbnailWorkingPath,
                    filterPreset = filter,
                )
            }.getOrNull()

            val rawAsset = mutations.storeAsset(
                "documents/${document.id}/raw/${draft.pageId}-${draft.operationId}.jpg",
                capture,
                revision,
            )
            val processedAsset = processed?.let {
                mutations.storeAsset(
                    "documents/${document.id}/processed/${draft.pageId}-r$revision.jpg",
                    File(it.processedImagePath),
                    revision,
                )
            }
            val thumbnailAsset = processed?.let {
                mutations.storeAsset(
                    "documents/${document.id}/thumbs/${draft.pageId}-r$revision.jpg",
                    File(it.thumbnailPath),
                    revision,
                )
            } ?: rawAsset

            val page = ScanPageEntity(
                id = draft.pageId,
                documentId = draft.documentId,
                pageIndex = draft.pageIndex,
                rawAsset = rawAsset,
                processedAsset = processedAsset,
                thumbnailAsset = thumbnailAsset,
                rotationDegrees = processed?.rotationDegrees ?: 0,
                cropTopLeftX = processed?.cropQuad?.topLeft?.x,
                cropTopLeftY = processed?.cropQuad?.topLeft?.y,
                cropTopRightX = processed?.cropQuad?.topRight?.x,
                cropTopRightY = processed?.cropQuad?.topRight?.y,
                cropBottomRightX = processed?.cropQuad?.bottomRight?.x,
                cropBottomRightY = processed?.cropQuad?.bottomRight?.y,
                cropBottomLeftX = processed?.cropQuad?.bottomLeft?.x,
                cropBottomLeftY = processed?.cropQuad?.bottomLeft?.y,
                filterPreset = processed?.filterPreset?.storageValue ?: PageFilterPreset.ORIGINAL.storageValue,
                processingState = processed?.processingState?.storageValue ?: PageProcessingState.CAPTURED.storageValue,
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now,
            )
            val pages = pageDao.getPages(document.id).toMutableList().apply {
                removeAll { it.id == page.id }
                add(page)
            }.sortedBy(ScanPageEntity::pageIndex).mapIndexed { index, value -> value.copy(pageIndex = index) }
            val manifest = document.toManifest(pages, nextRevision = revision, updatedAtMillis = now)
            mutations.commitDocument(manifest, if (existing == null) "capture_page" else "retake_page") { checksum, generation ->
                database.withTransaction {
                    pageDao.deleteByDocumentId(document.id)
                    pageDao.upsertAll(pages)
                    documentDao.update(document.snapshot(pages, revision, checksum, now))
                    roomStateUpdater.record("document", document.id, revision, checksum, generation)
                }
            }
            invalidate(thumbnailAsset, processedAsset)
            if (existing != null) cleanupReplacedAssets(existing, page)
            workingFiles.clear(draft.operationId)
            page.id
        }
    }

    override suspend fun movePage(pageId: String, targetIndex: Int): ScanlyResult<Unit> = withContext(dispatchers.io) {
        resultOf("Could not reorder the page.") {
            val page = pageDao.getPage(pageId) ?: error("Page not found.")
            val document = documentDao.getDocument(page.documentId) ?: error("Document not found.")
            val current = pageDao.getPages(document.id)
            val from = current.indexOfFirst { it.id == pageId }
            require(from >= 0) { "Page not found in document order." }
            val to = targetIndex.coerceIn(0, current.lastIndex)
            if (from == to) return@resultOf Unit
            val now = System.currentTimeMillis()
            val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
                .mapIndexed { index, item -> item.copy(pageIndex = index, updatedAtMillis = now) }
            commitPages(document, reordered, "reorder_page", now)
        }
    }

    override suspend fun deletePage(pageId: String): ScanlyResult<Unit> = withContext(dispatchers.io) {
        resultOf("Could not delete the page.") {
            val page = pageDao.getPage(pageId) ?: error("Page not found.")
            val document = documentDao.getDocument(page.documentId) ?: error("Document not found.")
            val now = System.currentTimeMillis()
            val remaining = pageDao.getPages(document.id).filterNot { it.id == pageId }
                .mapIndexed { index, item -> item.copy(pageIndex = index, updatedAtMillis = now) }
            commitPages(document, remaining, "delete_page", now)
            cleanupAssets(page)
        }
    }

    override suspend fun updatePageEdits(
        pageId: String,
        cropQuad: DocumentCornerQuad,
        rotationDegrees: Int,
        filterPreset: PageFilterPreset,
        applyFilterToAllPages: Boolean,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        resultOf("Could not save page edits.") {
            val selected = pageDao.getPage(pageId) ?: error("Page not found.")
            val document = documentDao.getDocument(selected.documentId) ?: error("Document not found.")
            val pages = pageDao.getPages(document.id)
            val now = System.currentTimeMillis()
            val revision = document.revision + 1L
            val operationId = UUID.randomUUID().toString()
            val directory = workingFiles.createOperationDirectory(operationId)
            val changed = mutableListOf<Pair<ScanPageEntity, ScanPageEntity>>()
            try {
                val updated = pages.map { page ->
                    val mustProcess = page.id == pageId || (applyFilterToAllPages && page.filterPreset != filterPreset.storageValue)
                    if (!mustProcess) return@map page
                    val raw = page.rawAsset ?: error("Raw image missing for page ${page.id}.")
                    val localRaw = assetReader.materialize(raw)
                    val processedFile = File(directory, "${page.id}-processed.jpg")
                    val thumbFile = File(directory, "${page.id}-thumb.jpg")
                    val targetCrop = if (page.id == pageId) cropQuad else page.toDomain().cropQuad
                    val targetRotation = if (page.id == pageId) rotationDegrees else page.rotationDegrees
                    val artifacts = pageImageProcessor.reprocessPage(
                        rawImagePath = localRaw.absolutePath,
                        processedImagePath = processedFile.absolutePath,
                        thumbnailPath = thumbFile.absolutePath,
                        cropQuad = targetCrop,
                        rotationDegrees = targetRotation,
                        filterPreset = filterPreset,
                        detectDocumentWhenCropQuadMissing = page.id == pageId,
                    )
                    val next = page.copy(
                        processedAsset = mutations.storeAsset("documents/${document.id}/processed/${page.id}-r$revision.jpg", processedFile, revision),
                        thumbnailAsset = mutations.storeAsset("documents/${document.id}/thumbs/${page.id}-r$revision.jpg", thumbFile, revision),
                        rotationDegrees = artifacts.rotationDegrees,
                        cropTopLeftX = artifacts.cropQuad?.topLeft?.x,
                        cropTopLeftY = artifacts.cropQuad?.topLeft?.y,
                        cropTopRightX = artifacts.cropQuad?.topRight?.x,
                        cropTopRightY = artifacts.cropQuad?.topRight?.y,
                        cropBottomRightX = artifacts.cropQuad?.bottomRight?.x,
                        cropBottomRightY = artifacts.cropQuad?.bottomRight?.y,
                        cropBottomLeftX = artifacts.cropQuad?.bottomLeft?.x,
                        cropBottomLeftY = artifacts.cropQuad?.bottomLeft?.y,
                        filterPreset = artifacts.filterPreset.storageValue,
                        processingState = artifacts.processingState.storageValue,
                        updatedAtMillis = now,
                    )
                    changed += page to next
                    next
                }
                val preferred = if (applyFilterToAllPages) filterPreset.storageValue else document.preferredFilterPreset
                val manifest = document.toManifest(updated, nextRevision = revision, preferredFilterPreset = preferred, updatedAtMillis = now)
                mutations.commitDocument(manifest, "edit_page") { checksum, generation ->
                    database.withTransaction {
                        pageDao.updateAll(updated)
                        documentDao.update(document.snapshot(updated, revision, checksum, now).copy(preferredFilterPreset = preferred))
                        roomStateUpdater.record("document", document.id, revision, checksum, generation)
                    }
                }
                changed.forEach { (before, after) ->
                    invalidate(after.thumbnailAsset, after.processedAsset)
                    if (before.processedAsset != after.processedAsset) mutations.deleteAsset(before.processedAsset)
                    if (before.thumbnailAsset != after.thumbnailAsset) mutations.deleteAsset(before.thumbnailAsset)
                }
            } finally {
                workingFiles.clear(operationId)
            }
        }
    }

    private fun createDraft(documentId: String, pageId: String, pageIndex: Int, replacementId: String?): PageCaptureDraft {
        val operationId = UUID.randomUUID().toString()
        val directory = workingFiles.createOperationDirectory(operationId)
        return PageCaptureDraft(
            pageId = pageId,
            documentId = documentId,
            pageIndex = pageIndex,
            operationId = operationId,
            captureFilePath = File(directory, "capture.jpg").absolutePath,
            processedWorkingPath = File(directory, "processed.jpg").absolutePath,
            thumbnailWorkingPath = File(directory, "thumb.jpg").absolutePath,
            replacementPageId = replacementId,
        )
    }

    private suspend fun commitPages(document: DocumentEntity, pages: List<ScanPageEntity>, type: String, now: Long) {
        val revision = document.revision + 1L
        val manifest = document.toManifest(pages, nextRevision = revision, updatedAtMillis = now)
        mutations.commitDocument(manifest, type) { checksum, generation ->
            database.withTransaction {
                pageDao.deleteByDocumentId(document.id)
                pageDao.upsertAll(pages)
                documentDao.update(document.snapshot(pages, revision, checksum, now))
                roomStateUpdater.record("document", document.id, revision, checksum, generation)
            }
        }
    }

    private fun DocumentEntity.snapshot(pages: List<ScanPageEntity>, revision: Long, checksum: String, now: Long) = copy(
        pageCount = pages.size,
        coverThumbnail = pages.firstOrNull()?.thumbnailAsset ?: pages.firstOrNull()?.rawAsset,
        updatedAtMillis = now,
        revision = revision,
        manifestChecksum = checksum,
    )

    private fun invalidate(vararg assets: LibraryAssetRef?) {
        assets.filterNotNull().forEach { thumbnailCache.invalidate(it.relativePath) }
    }

    private suspend fun cleanupAssets(page: ScanPageEntity) {
        mutations.deleteAsset(page.rawAsset)
        mutations.deleteAsset(page.processedAsset)
        if (page.thumbnailAsset != page.rawAsset) mutations.deleteAsset(page.thumbnailAsset)
    }

    private suspend fun cleanupReplacedAssets(old: ScanPageEntity, new: ScanPageEntity) {
        if (old.rawAsset != new.rawAsset) mutations.deleteAsset(old.rawAsset)
        if (old.processedAsset != new.processedAsset) mutations.deleteAsset(old.processedAsset)
        if (old.thumbnailAsset != new.thumbnailAsset && old.thumbnailAsset != old.rawAsset) mutations.deleteAsset(old.thumbnailAsset)
    }

    private suspend fun <T> resultOf(fallback: String, block: suspend () -> T): ScanlyResult<T> =
        runCatching { block() }.fold(
            onSuccess = { ScanlyResult.Success(it) },
            onFailure = { ScanlyResult.Failure(ScanlyError(it.message ?: fallback, it)) },
        )
}
