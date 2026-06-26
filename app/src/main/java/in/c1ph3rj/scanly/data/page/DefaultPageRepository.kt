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
import `in`.c1ph3rj.scanly.data.storage.DocumentStorageManager
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
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
    private val scanPageDao: ScanPageDao,
    private val storageManager: DocumentStorageManager,
    private val pageImageProcessor: PageImageProcessor,
    private val thumbnailCache: ThumbnailCache,
    private val dispatchers: ScanlyDispatchers,
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

    override suspend fun prepareReplacementCapture(pageId: String): ScanlyResult<PageCaptureDraft> =
        withContext(dispatchers.io) {
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

    override suspend fun finalizeCapture(draft: PageCaptureDraft): ScanlyResult<String> =
        withContext(dispatchers.io) {
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
                    ?: PageFilterPreset.ENHANCED_COLOR
                val processedArtifacts = runCatching {
                    pageImageProcessor.processCapture(
                        rawImagePath = draft.rawImagePath,
                        processedImagePath = draft.processedImagePath,
                        thumbnailPath = draft.thumbnailPath,
                        filterPreset = captureFilterPreset,
                    ).toPersistedArtifacts()
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

    override suspend fun movePage(
        pageId: String,
        targetIndex: Int,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
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

    override suspend fun deletePage(pageId: String): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                val page = scanPageDao.getPage(pageId)
                    ?: error("Page not found.")
                val document = documentDao.getDocument(page.documentId)
                    ?: error("Document not found.")
                val timestamp = System.currentTimeMillis()

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

                storageManager.deletePageAssets(
                    rawImagePath = page.rawImagePath,
                    processedImagePath = page.processedImagePath,
                    thumbnailPath = page.thumbnailPath,
                )
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

    override suspend fun updatePageEdits(
        pageId: String,
        cropQuad: `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad,
        rotationDegrees: Int,
        filterPreset: PageFilterPreset,
        applyFilterToAllPages: Boolean,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            val page = scanPageDao.getPage(pageId)
                ?: error("Page not found.")
            val document = documentDao.getDocument(page.documentId)
                ?: error("Document not found.")
            val timestamp = System.currentTimeMillis()
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
                val targetCropQuad = if (existingPage.id == page.id) {
                    cropQuad
                } else {
                    existingPage.toDomain().cropQuad
                }
                val targetRotation = if (existingPage.id == page.id) {
                    rotationDegrees
                } else {
                    existingPage.rotationDegrees
                }
                val needsReprocess = existingPage.id == page.id || existingPage.filterPreset != filterPreset.storageValue
                if (!needsReprocess) {
                    existingPage.copy(updatedAtMillis = timestamp)
                } else {
                    existingPage.reprocessWith(
                        cropQuad = targetCropQuad,
                        rotationDegrees = targetRotation,
                        filterPreset = filterPreset,
                        updatedAtMillis = timestamp,
                        detectDocumentWhenCropQuadMissing = existingPage.id == page.id || !applyFilterToAllPages,
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
        processingState = PageProcessingState.fromStorage(processingState),
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private suspend fun ScanPageEntity.reprocessWith(
        cropQuad: `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad?,
        rotationDegrees: Int,
        filterPreset: PageFilterPreset,
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

        val processedArtifacts = pageImageProcessor.reprocessPage(
            rawImagePath = rawImagePath,
            processedImagePath = resolvedProcessedImagePath,
            thumbnailPath = resolvedThumbnailPath,
            cropQuad = cropQuad,
            rotationDegrees = rotationDegrees,
            filterPreset = filterPreset,
            detectDocumentWhenCropQuadMissing = detectDocumentWhenCropQuadMissing,
        ).toPersistedArtifacts()

        invalidateImageCache(
            processedArtifacts.thumbnailPath,
            processedArtifacts.processedImagePath,
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

    private companion object {
        const val RAW_DIRECTORY = "raw"
        const val PROCESSED_DIRECTORY = "processed"
        const val THUMBNAILS_DIRECTORY = "thumbs"
    }
}
