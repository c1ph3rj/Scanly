package `in`.c1ph3rj.scanly.data.page

import androidx.room.withTransaction
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ui.ThumbnailCache
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity
import `in`.c1ph3rj.scanly.data.common.LibraryOperationCoordinator
import `in`.c1ph3rj.scanly.data.sign.PageMarkupStore
import `in`.c1ph3rj.scanly.data.storage.DocumentStorageManager
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.processing.PageImageProcessor
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.TrashedPage
import `in`.c1ph3rj.scanly.domain.repository.FlatPageSetup
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
    private val scanPageDao: ScanPageDao,
    private val storageManager: DocumentStorageManager,
    private val pageImageProcessor: PageImageProcessor,
    private val thumbnailCache: ThumbnailCache,
    private val dispatchers: ScanlyDispatchers,
    private val operationCoordinator: LibraryOperationCoordinator,
    private val markupStore: PageMarkupStore,
) : PageRepository {

    override fun observePages(documentId: String): Flow<List<ScanPage>> =
        scanPageDao.observePages(documentId).map { pages ->
            pages.map { page -> page.toDomain() }
        }

    override fun observePage(pageId: String): Flow<ScanPage?> =
        scanPageDao.observePage(pageId).map { page ->
            page?.toDomain()
        }

    override suspend fun prepareCapture(documentId: String): ScanlyResult<PageCaptureDraft> =
        withContext(dispatchers.io) {
            operationCoordinator.withMutation {
            runCatching {
                val document = documentDao.getDocument(documentId)
                    ?: error("Document not found.")
                val nextPageIndex = scanPageDao.countPages(documentId)
                val storageDraft = storageManager.createPageCaptureDraft(
                    documentId = document.id,
                    pageIndex = nextPageIndex,
                )

                PageCaptureDraft(
                    pageId = UUID.randomUUID().toString(),
                    documentId = document.id,
                    pageIndex = nextPageIndex,
                    rawImagePath = storageDraft.rawImagePath,
                    processedImagePath = storageDraft.processedImagePath,
                    thumbnailPath = storageDraft.thumbnailPath,
                    replacementPageId = null,
                )
            }.fold(
                onSuccess = { draft -> ScanlyResult.Success(draft) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not prepare page capture.",
                            cause = throwable,
                        ),
                    )
                },
            )
            }
        }

    override suspend fun prepareReplacementCapture(pageId: String): ScanlyResult<PageCaptureDraft> =
        withContext(dispatchers.io) {
            operationCoordinator.withMutation {
            runCatching {
                val page = scanPageDao.getPage(pageId)
                    ?: error("Page not found.")
                val anchorPath = page.rawImagePath ?: page.processedImagePath ?: page.thumbnailPath
                    ?: error("No existing file path found for page $pageId.")
                PageCaptureDraft(
                    pageId = page.id,
                    documentId = page.documentId,
                    pageIndex = page.pageIndex,
                    rawImagePath = page.rawImagePath ?: deriveSiblingAssetPath(anchorPath, RAW_DIRECTORY),
                    processedImagePath = page.processedImagePath ?: deriveSiblingAssetPath(anchorPath, PROCESSED_DIRECTORY),
                    thumbnailPath = page.thumbnailPath ?: deriveSiblingAssetPath(anchorPath, THUMBNAILS_DIRECTORY),
                    replacementPageId = page.id,
                )
            }.fold(
                onSuccess = { draft -> ScanlyResult.Success(draft) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not prepare page replacement.",
                            cause = throwable,
                        ),
                    )
                },
            )
            }
        }

    override suspend fun finalizeCapture(
        draft: PageCaptureDraft,
        flatPage: FlatPageSetup?,
    ): ScanlyResult<String> =
        withContext(dispatchers.io) {
            operationCoordinator.withMutation {
            runCatching {
                val document = documentDao.getDocument(draft.documentId)
                    ?: error("Document not found.")
                val existingPage = scanPageDao.getPage(draft.pageId)
                val rawFile = File(draft.rawImagePath)
                if (!rawFile.exists() || rawFile.length() <= 0L) {
                    error("Capture file missing at ${draft.rawImagePath}.")
                }

                val timestamp = System.currentTimeMillis()
                val captureFilterPreset = document.preferredFilterPreset
                    ?.let(PageFilterPreset::fromStorage)
                    ?: PageFilterPreset.AUTO
                val processedArtifacts = runCatching {
                    if (flatPage != null) {
                        pageImageProcessor.reprocessPage(
                            rawImagePath = draft.rawImagePath,
                            processedImagePath = draft.processedImagePath,
                            thumbnailPath = draft.thumbnailPath,
                            cropQuad = DocumentCornerQuad.FullFrame,
                            rotationDegrees = 0,
                            filterPreset = flatPage.filterPreset,
                            detectDocumentWhenCropQuadMissing = false,
                        )
                    } else {
                        pageImageProcessor.processCapture(
                            rawImagePath = draft.rawImagePath,
                            processedImagePath = draft.processedImagePath,
                            thumbnailPath = draft.thumbnailPath,
                            filterPreset = captureFilterPreset,
                        )
                    }.toPersistedArtifacts()
                }.getOrElse {
                    val fallbackThumbnail = storageManager.generatePageThumbnail(
                        rawImagePath = draft.rawImagePath,
                        thumbnailPath = draft.thumbnailPath,
                    )
                    FallbackProcessedArtifacts(
                        processedImagePath = null,
                        thumbnailPath = fallbackThumbnail.thumbnailPath,
                        rotationDegrees = fallbackThumbnail.rotationDegrees,
                    ).toPersistedArtifacts()
                }
                invalidateImageCache(
                    processedArtifacts.thumbnailPath,
                    processedArtifacts.processedImagePath,
                )
                if (existingPage != null) {
                    // Retake keeps the page's signatures: stamp them onto the new capture.
                    markupStore.reapplyAfterReprocess(
                        existingPage.copy(
                            processedImagePath = processedArtifacts.processedImagePath,
                            thumbnailPath = processedArtifacts.thumbnailPath,
                        ),
                    )
                }
                val page = ScanPageEntity(
                    id = draft.pageId,
                    documentId = draft.documentId,
                    pageIndex = draft.pageIndex,
                    rawImagePath = draft.rawImagePath,
                    processedImagePath = processedArtifacts.processedImagePath,
                    thumbnailPath = processedArtifacts.thumbnailPath,
                    rotationDegrees = processedArtifacts.rotationDegrees,
                    cropTopLeftX = processedArtifacts.cropTopLeftX,
                    cropTopLeftY = processedArtifacts.cropTopLeftY,
                    cropTopRightX = processedArtifacts.cropTopRightX,
                    cropTopRightY = processedArtifacts.cropTopRightY,
                    cropBottomRightX = processedArtifacts.cropBottomRightX,
                    cropBottomRightY = processedArtifacts.cropBottomRightY,
                    cropBottomLeftX = processedArtifacts.cropBottomLeftX,
                    cropBottomLeftY = processedArtifacts.cropBottomLeftY,
                    filterPreset = processedArtifacts.filterPreset,
                    processingState = processedArtifacts.processingState,
                    createdAtMillis = existingPage?.createdAtMillis ?: timestamp,
                    updatedAtMillis = timestamp,
                )

                database.withTransaction {
                    if (existingPage == null) {
                        scanPageDao.insert(page)
                    } else {
                        scanPageDao.update(page)
                    }
                    updateDocumentSnapshot(
                        document = document,
                        updatedAtMillis = timestamp,
                    )
                }

                draft.pageId
            }.fold(
                onSuccess = { pageId -> ScanlyResult.Success(pageId) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not save the captured page.",
                            cause = throwable,
                        ),
                    )
                },
            )
            }
        }

    override suspend fun movePage(
        pageId: String,
        targetIndex: Int,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        operationCoordinator.withMutation {
        runCatching {
            val page = scanPageDao.getPage(pageId)
                ?: error("Page not found.")
            val document = documentDao.getDocument(page.documentId)
                ?: error("Document not found.")
            val pages = scanPageDao.getPages(page.documentId)
            if (pages.isEmpty()) {
                return@runCatching
            }

            val currentIndex = pages.indexOfFirst { it.id == pageId }
            if (currentIndex == -1) {
                error("Page not found in document order.")
            }
            val clampedTargetIndex = targetIndex.coerceIn(0, pages.lastIndex)
            if (clampedTargetIndex == currentIndex) {
                return@runCatching
            }

            val reorderedPages = pages.toMutableList().apply {
                val movedPage = removeAt(currentIndex)
                add(clampedTargetIndex, movedPage)
            }
            val timestamp = System.currentTimeMillis()

            database.withTransaction {
                val temporaryOffset = reorderedPages.size + 1
                scanPageDao.updateAll(
                    reorderedPages.mapIndexed { index, existingPage ->
                        existingPage.copy(
                            pageIndex = index + temporaryOffset,
                            updatedAtMillis = timestamp,
                        )
                    },
                )
                scanPageDao.updateAll(
                    reorderedPages.mapIndexed { index, existingPage ->
                        existingPage.copy(
                            pageIndex = index,
                            updatedAtMillis = timestamp,
                        )
                    },
                )
                updateDocumentSnapshot(
                    document = document,
                    updatedAtMillis = timestamp,
                )
            }
        }.fold(
            onSuccess = { ScanlyResult.Success(Unit) },
            onFailure = { throwable ->
                ScanlyResult.Failure(
                    ScanlyError(
                        message = throwable.message ?: "Could not reorder the page.",
                        cause = throwable,
                    ),
                )
            },
        )
        }
    }

    override suspend fun deletePage(pageId: String): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            operationCoordinator.withMutation {
            runCatching {
                val page = scanPageDao.getPage(pageId)
                    ?: error("Page not found.")
                val document = documentDao.getDocument(page.documentId)
                    ?: error("Document not found.")
                val timestamp = System.currentTimeMillis()
                // The markup row cascades with the page; read it first to clean up its files.
                val markup = markupStore.markupFor(pageId)

                database.withTransaction {
                    scanPageDao.deleteById(pageId)
                    val remainingPages = scanPageDao.getPages(document.id)
                    if (remainingPages.isNotEmpty()) {
                        scanPageDao.updateAll(
                            remainingPages.mapIndexed { index, remainingPage ->
                                remainingPage.copy(
                                    pageIndex = index,
                                    updatedAtMillis = timestamp,
                                )
                            },
                        )
                    }
                    updateDocumentSnapshot(
                        document = document,
                        updatedAtMillis = timestamp,
                    )
                }

                deletePageAssets(page)
                markupStore.deleteAssets(markup, page.processedImagePath)
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not delete the page.",
                            cause = throwable,
                        ),
                    )
                },
            )
            }
        }

    override suspend fun movePageToTrash(pageId: String): ScanlyResult<Unit> =
        mutatePages("Could not delete the page.") {
            val page = scanPageDao.getPage(pageId) ?: error("Page not found.")
            check(page.deletedAtMillis == null) { "This page is already in Recently deleted." }
            val document = documentDao.getDocument(page.documentId) ?: error("Document not found.")
            val timestamp = System.currentTimeMillis()
            database.withTransaction {
                // Park below every other index so the unique (document, index) pair stays free.
                val parkedIndex = minOf(scanPageDao.minPageIndex(document.id) ?: 0, 0) - 1
                // updatedAtMillis is left alone so the page's text stays valid for when it comes back.
                scanPageDao.update(
                    page.copy(pageIndex = parkedIndex, deletedAtMillis = timestamp, deletedFromIndex = page.pageIndex),
                )
                renumberLivePages(document.id)
                updateDocumentSnapshot(document = document, updatedAtMillis = timestamp)
            }
        }

    override suspend fun restorePage(pageId: String): ScanlyResult<Unit> =
        mutatePages("Could not restore the page.") {
            val page = scanPageDao.getPage(pageId) ?: error("Page not found.")
            if (page.deletedAtMillis == null) return@mutatePages
            val document = documentDao.getDocument(page.documentId) ?: error("Document not found.")
            val timestamp = System.currentTimeMillis()
            database.withTransaction {
                val livePages = scanPageDao.getPages(document.id)
                val target = (page.deletedFromIndex ?: livePages.size).coerceIn(0, livePages.size)
                // Shift later pages up one, last first, so no two pages ever share an index.
                livePages.filter { it.pageIndex >= target }
                    .sortedByDescending { it.pageIndex }
                    .forEach { scanPageDao.update(it.copy(pageIndex = it.pageIndex + 1)) }
                scanPageDao.update(page.copy(pageIndex = target, deletedAtMillis = null, deletedFromIndex = null))
                updateDocumentSnapshot(document = document, updatedAtMillis = timestamp)
            }
        }

    override fun observeTrashedPages(): Flow<List<TrashedPage>> =
        scanPageDao.observeTrashedPages().map { rows ->
            rows.map { row ->
                TrashedPage(
                    id = row.id,
                    documentId = row.documentId,
                    documentTitle = row.documentTitle,
                    pageNumber = row.deletedFromIndex + 1,
                    thumbnailPath = row.thumbnailPath,
                    deletedAtMillis = row.deletedAtMillis,
                )
            }
        }

    override suspend fun deletePageForever(pageId: String): ScanlyResult<Unit> =
        mutatePages("Could not delete the page.") {
            val page = scanPageDao.getPage(pageId) ?: return@mutatePages
            check(page.deletedAtMillis != null) { "Only pages in Recently deleted can be deleted forever." }
            deleteTrashedPages(listOf(page))
        }

    override suspend fun emptyTrashedPages(): ScanlyResult<Int> =
        mutatePages("Could not delete the pages.") { deleteTrashedPages(scanPageDao.getTrashedPages()) }

    override suspend fun purgeTrashedPagesBefore(cutoffMillis: Long): ScanlyResult<Int> =
        mutatePages("Could not clear old deleted pages.") {
            deleteTrashedPages(scanPageDao.getTrashedPagesBefore(cutoffMillis))
        }

    /** Trashed pages sit outside the numbering, so removing them never renumbers live pages. */
    private suspend fun deleteTrashedPages(pages: List<ScanPageEntity>): Int {
        if (pages.isEmpty()) return 0
        // Markup rows cascade with their pages; read them first to clean up their files.
        val markups = pages.associate { it.id to markupStore.markupFor(it.id) }
        database.withTransaction {
            pages.forEach { scanPageDao.deleteById(it.id) }
        }
        pages.forEach { page ->
            runCatching {
                deletePageAssets(page)
                markupStore.deleteAssets(markups[page.id], page.processedImagePath)
            }
        }
        return pages.size
    }

    /** Closes the gap a trashed page left. Images are unchanged, so their text stays indexed. */
    private suspend fun renumberLivePages(documentId: String) {
        val livePages = scanPageDao.getPages(documentId)
        val renumbered = livePages.mapIndexedNotNull { index, livePage ->
            if (livePage.pageIndex == index) null else livePage.copy(pageIndex = index)
        }
        if (renumbered.isNotEmpty()) scanPageDao.updateAll(renumbered)
    }

    private suspend fun <T> mutatePages(failureMessage: String, block: suspend () -> T): ScanlyResult<T> =
        withContext(dispatchers.io) {
            operationCoordinator.withMutation {
                runCatching { block() }.fold(
                    onSuccess = { ScanlyResult.Success(it) },
                    onFailure = { throwable ->
                        ScanlyResult.Failure(
                            ScanlyError(message = throwable.message ?: failureMessage, cause = throwable),
                        )
                    },
                )
            }
        }

    override suspend fun updatePageEdits(
        pageId: String,
        cropQuad: `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad,
        rotationDegrees: Int,
        filterPreset: PageFilterPreset,
        filterAdjustments: PageFilterAdjustments,
        applyFilterToAllPages: Boolean,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        operationCoordinator.withMutation {
        runCatching {
            val page = scanPageDao.getPage(pageId)
                ?: error("Page not found.")
            val document = documentDao.getDocument(page.documentId)
                ?: error("Document not found.")
            val timestamp = System.currentTimeMillis()
            val sanitizedAdjustments = filterAdjustments.sanitized()
            val updatedDocument = if (applyFilterToAllPages) {
                document.copy(preferredFilterPreset = filterPreset.storageValue)
            } else {
                document
            }
            val pagesToUpdate = if (applyFilterToAllPages) {
                scanPageDao.getPages(page.documentId)
            } else {
                listOf(page)
            }
            val updatedPages = pagesToUpdate.map { existingPage ->
                val isEditedPage = existingPage.id == page.id
                val targetCropQuad = if (isEditedPage) {
                    cropQuad
                } else {
                    existingPage.toDomain().cropQuad
                }
                val targetRotation = if (isEditedPage) {
                    rotationDegrees
                } else {
                    existingPage.rotationDegrees
                }
                // Bulk filter applies preset only; per-page customizations stay on the edited page.
                val targetAdjustments = if (isEditedPage) {
                    sanitizedAdjustments
                } else {
                    existingPage.toDomain().filterAdjustments
                }
                val needsReprocess = isEditedPage ||
                    existingPage.filterPreset != filterPreset.storageValue
                if (!needsReprocess) {
                    existingPage.copy(updatedAtMillis = timestamp)
                } else {
                    existingPage.reprocessWith(
                        cropQuad = targetCropQuad,
                        rotationDegrees = targetRotation,
                        filterPreset = filterPreset,
                        filterAdjustments = targetAdjustments,
                        updatedAtMillis = timestamp,
                        detectDocumentWhenCropQuadMissing = isEditedPage || !applyFilterToAllPages,
                    )
                }
            }

            database.withTransaction {
                scanPageDao.updateAll(updatedPages)
                updateDocumentSnapshot(
                    document = updatedDocument,
                    updatedAtMillis = timestamp,
                )
            }
        }.fold(
            onSuccess = { ScanlyResult.Success(Unit) },
            onFailure = { throwable ->
                ScanlyResult.Failure(
                    ScanlyError(
                        message = throwable.message ?: "Could not save page edits.",
                        cause = throwable,
                    ),
                )
            },
        )
        }
    }

    private fun ScanPageEntity.toDomain(): ScanPage = ScanPage(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        rawImagePath = rawImagePath,
        processedImagePath = processedImagePath,
        thumbnailPath = thumbnailPath,
        rotationDegrees = rotationDegrees,
        cropQuad = cropTopLeftX?.let { topLeftX ->
            cropTopLeftY?.let { topLeftY ->
                cropTopRightX?.let { topRightX ->
                    cropTopRightY?.let { topRightY ->
                        cropBottomRightX?.let { bottomRightX ->
                            cropBottomRightY?.let { bottomRightY ->
                                cropBottomLeftX?.let { bottomLeftX ->
                                    cropBottomLeftY?.let { bottomLeftY ->
                                        `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad(
                                            topLeft = `in`.c1ph3rj.scanly.core.ml.NormalizedPoint(topLeftX, topLeftY),
                                            topRight = `in`.c1ph3rj.scanly.core.ml.NormalizedPoint(topRightX, topRightY),
                                            bottomRight = `in`.c1ph3rj.scanly.core.ml.NormalizedPoint(bottomRightX, bottomRightY),
                                            bottomLeft = `in`.c1ph3rj.scanly.core.ml.NormalizedPoint(bottomLeftX, bottomLeftY),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        filterPreset = PageFilterPreset.fromStorage(filterPreset),
        filterAdjustments = PageFilterAdjustments(
            brightness = filterBrightness,
            contrast = filterContrast,
            saturation = filterSaturation,
            sharpness = filterSharpness,
            intensity = filterIntensity,
        ).sanitized(),
        processingState = PageProcessingState.fromStorage(processingState),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private suspend fun ScanPageEntity.reprocessWith(
        cropQuad: `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad?,
        rotationDegrees: Int,
        filterPreset: PageFilterPreset,
        filterAdjustments: PageFilterAdjustments,
        updatedAtMillis: Long,
        detectDocumentWhenCropQuadMissing: Boolean,
    ): ScanPageEntity {
        val rawImagePath = rawImagePath ?: error("Raw image missing for page $id.")
        val resolvedProcessedImagePath = processedImagePath
            ?: deriveSiblingAssetPath(
                rawImagePath = rawImagePath,
                targetDirectoryName = PROCESSED_DIRECTORY,
            )
        val resolvedThumbnailPath = thumbnailPath
            ?: deriveSiblingAssetPath(
                rawImagePath = rawImagePath,
                targetDirectoryName = THUMBNAILS_DIRECTORY,
            )
        val sanitizedAdjustments = filterAdjustments.sanitized()

        val processedArtifacts = pageImageProcessor.reprocessPage(
            rawImagePath = rawImagePath,
            processedImagePath = resolvedProcessedImagePath,
            thumbnailPath = resolvedThumbnailPath,
            cropQuad = cropQuad,
            rotationDegrees = rotationDegrees,
            filterPreset = filterPreset,
            filterAdjustments = sanitizedAdjustments,
            detectDocumentWhenCropQuadMissing = detectDocumentWhenCropQuadMissing,
        ).toPersistedArtifacts()

        invalidateImageCache(
            processedArtifacts.thumbnailPath,
            processedArtifacts.processedImagePath,
        )
        // The pipeline rewrote a clean image; stamp any signatures onto it again.
        markupStore.reapplyAfterReprocess(
            copy(
                processedImagePath = processedArtifacts.processedImagePath,
                thumbnailPath = processedArtifacts.thumbnailPath,
            ),
        )

        return copy(
            processedImagePath = processedArtifacts.processedImagePath,
            thumbnailPath = processedArtifacts.thumbnailPath,
            rotationDegrees = processedArtifacts.rotationDegrees,
            cropTopLeftX = processedArtifacts.cropTopLeftX,
            cropTopLeftY = processedArtifacts.cropTopLeftY,
            cropTopRightX = processedArtifacts.cropTopRightX,
            cropTopRightY = processedArtifacts.cropTopRightY,
            cropBottomRightX = processedArtifacts.cropBottomRightX,
            cropBottomRightY = processedArtifacts.cropBottomRightY,
            cropBottomLeftX = processedArtifacts.cropBottomLeftX,
            cropBottomLeftY = processedArtifacts.cropBottomLeftY,
            filterPreset = processedArtifacts.filterPreset,
            filterBrightness = sanitizedAdjustments.brightness,
            filterContrast = sanitizedAdjustments.contrast,
            filterSaturation = sanitizedAdjustments.saturation,
            filterSharpness = sanitizedAdjustments.sharpness,
            filterIntensity = sanitizedAdjustments.intensity,
            processingState = processedArtifacts.processingState,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private data class PersistedProcessedArtifacts(
        val processedImagePath: String?,
        val thumbnailPath: String,
        val rotationDegrees: Int,
        val cropTopLeftX: Float?,
        val cropTopLeftY: Float?,
        val cropTopRightX: Float?,
        val cropTopRightY: Float?,
        val cropBottomRightX: Float?,
        val cropBottomRightY: Float?,
        val cropBottomLeftX: Float?,
        val cropBottomLeftY: Float?,
        val filterPreset: String,
        val processingState: String,
    )

    private data class FallbackProcessedArtifacts(
        val processedImagePath: String?,
        val thumbnailPath: String,
        val rotationDegrees: Int,
    )

    private fun `in`.c1ph3rj.scanly.domain.processing.ProcessedPageArtifacts.toPersistedArtifacts(): PersistedProcessedArtifacts =
        PersistedProcessedArtifacts(
            processedImagePath = processedImagePath,
            thumbnailPath = thumbnailPath,
            rotationDegrees = rotationDegrees,
            cropTopLeftX = cropQuad?.topLeft?.x,
            cropTopLeftY = cropQuad?.topLeft?.y,
            cropTopRightX = cropQuad?.topRight?.x,
            cropTopRightY = cropQuad?.topRight?.y,
            cropBottomRightX = cropQuad?.bottomRight?.x,
            cropBottomRightY = cropQuad?.bottomRight?.y,
            cropBottomLeftX = cropQuad?.bottomLeft?.x,
            cropBottomLeftY = cropQuad?.bottomLeft?.y,
            filterPreset = filterPreset.storageValue,
            processingState = processingState.storageValue,
        )

    private fun FallbackProcessedArtifacts.toPersistedArtifacts(): PersistedProcessedArtifacts =
        PersistedProcessedArtifacts(
            processedImagePath = processedImagePath,
            thumbnailPath = thumbnailPath,
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
            processingState = PageProcessingState.CAPTURED.storageValue,
        )

    private fun invalidateImageCache(vararg paths: String?) {
        paths.filterNotNull().forEach(thumbnailCache::invalidate)
    }

    private fun deriveSiblingAssetPath(
        rawImagePath: String,
        targetDirectoryName: String,
    ): String {
        val rawFile = File(rawImagePath)
        val rawDirectory = rawFile.parentFile ?: error("Invalid raw image path: $rawImagePath")
        val documentRoot = rawDirectory.parentFile ?: error("Could not resolve document root for $rawImagePath")
        val targetDirectory = File(documentRoot, targetDirectoryName)
        if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
            error("Could not create ${targetDirectory.absolutePath}.")
        }
        return File(targetDirectory, rawFile.name).absolutePath
    }

    private suspend fun updateDocumentSnapshot(
        document: `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity,
        updatedAtMillis: Long,
    ) {
        val pages = scanPageDao.getPages(document.id)
        val coverThumbnailPath = pages.firstOrNull()?.thumbnailPath
            ?: storageManager.refreshDocumentCover(
                documentId = document.id,
                title = document.title,
            )
        documentDao.update(
            document.copy(
                pageCount = pages.size,
                coverThumbnailPath = coverThumbnailPath,
                updatedAtMillis = updatedAtMillis,
            ),
        )
    }

    private fun deletePageAssets(page: ScanPageEntity) {
        listOf(page.rawImagePath, page.processedImagePath, page.thumbnailPath)
            .filterNotNull()
            .map(::File)
            .distinctBy { file -> file.absolutePath }
            .forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
    }

    private companion object {
        const val RAW_DIRECTORY = "raw"
        const val PROCESSED_DIRECTORY = "processed"
        const val THUMBNAILS_DIRECTORY = "thumbs"
    }
}
