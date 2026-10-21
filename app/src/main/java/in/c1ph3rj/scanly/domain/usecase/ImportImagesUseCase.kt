package `in`.c1ph3rj.scanly.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Progress emitted while each gallery image is decoded and run through the
 * document processing pipeline (corners + filter + thumbnail).
 */
data class ImportImagesProgress(
    val currentIndex: Int,
    val totalCount: Int,
    val stage: ImportStage,
) {
    /** Short, stable stage copy — pair with "X of Y" in the fixed-size loader. */
    val stageLabel: String
        get() = when (stage) {
            ImportStage.Preparing -> "Preparing page"
            ImportStage.Detecting -> "Detecting document"
            ImportStage.Finalizing -> "Saving page"
        }

    @Deprecated("Use stageLabel + index fields for fixed loader layout", ReplaceWith("stageLabel"))
    val displayMessage: String
        get() = stageLabel
}

/** Outcome of an import: pages added, and picked files that could not be read as images. */
data class ImportImagesSummary(
    val importedCount: Int,
    val skippedCount: Int,
)

enum class ImportStage {
    Preparing,
    Detecting,
    Finalizing,
}

/**
 * Imports gallery images as document pages.
 *
 * Each image is normalized to a JPEG raw capture, then finalized through the same
 * [PageRepository.finalizeCapture] path as camera captures — document gate (soft),
 * post-processing corner model, still-process quad readiness, Auto filter, and
 * thumbnail generation.
 *
 * Files that can't be decoded as images are skipped (no page is created for them) and
 * counted in [ImportImagesSummary.skippedCount]. An image whose page can't be saved is skipped
 * the same way; the import fails only when no page could be saved.
 */
class ImportImagesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pageRepository: PageRepository,
    private val dispatchers: ScanlyDispatchers,
) {
    suspend operator fun invoke(
        documentId: String,
        imageUris: List<Uri>,
        onProgress: suspend (ImportImagesProgress) -> Unit = {},
    ): ScanlyResult<ImportImagesSummary> = withContext(dispatchers.io) {
        runCatching {
            val total = imageUris.size
            require(total > 0) { "No images selected." }
            var imported = 0

            imageUris.forEachIndexed { index, uri ->
                val current = index + 1
                reportProgress(onProgress, current, total, ImportStage.Preparing)

                // Decode before creating the draft so an unreadable file leaves no empty page.
                val bitmap = decodeBitmap(uri) ?: return@forEachIndexed

                // A page that fails to save is skipped, so the pages already saved from this batch are kept.
                val saved = try {
                    savePage(documentId, bitmap, current, total, onProgress)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    false
                } finally {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
                if (saved) {
                    imported++
                }
            }

            check(imported > 0) {
                if (total == 1) {
                    "The selected file couldn't be opened as an image."
                } else {
                    "None of the selected files could be opened as images."
                }
            }
            ImportImagesSummary(importedCount = imported, skippedCount = total - imported)
        }.fold(
            onSuccess = { summary -> ScanlyResult.Success(summary) },
            onFailure = { throwable ->
                // Cancellation is not a failed import: rethrow so pages already saved are not treated as empty.
                if (throwable is CancellationException) throw throwable
                ScanlyResult.Failure(
                    ScanlyError(
                        message = throwable.message ?: "Failed to import images.",
                        cause = throwable,
                    ),
                )
            },
        )
    }

    /**
     * Saves one decoded image as a page through the same capture path as the camera.
     * @return false when the page could not be prepared or finalized.
     */
    private suspend fun savePage(
        documentId: String,
        bitmap: Bitmap,
        current: Int,
        total: Int,
        onProgress: suspend (ImportImagesProgress) -> Unit,
    ): Boolean {
        val draftResult = pageRepository.prepareCapture(documentId)
        if (draftResult !is ScanlyResult.Success) return false
        val draft = draftResult.value

        writeNormalizedJpeg(bitmap, File(draft.rawImagePath))

        reportProgress(onProgress, current, total, ImportStage.Detecting)
        if (pageRepository.finalizeCapture(draft) !is ScanlyResult.Success) return false

        reportProgress(onProgress, current, total, ImportStage.Finalizing)
        return true
    }

    private suspend fun reportProgress(
        onProgress: suspend (ImportImagesProgress) -> Unit,
        current: Int,
        total: Int,
        stage: ImportStage,
    ) {
        withContext(dispatchers.main) {
            onProgress(
                ImportImagesProgress(
                    currentIndex = current,
                    totalCount = total,
                    stage = stage,
                ),
            )
        }
    }

    /**
     * Writes the ImageDecoder output (HEIC/WEBP-friendly) as a consistent JPEG so EXIF orientation and format quirks don't skip the processing pipeline.
     */
    private fun writeNormalizedJpeg(bitmap: Bitmap, target: File) {
        try {
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { output ->
                val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, IMPORT_JPEG_QUALITY, output)
                if (!compressed) {
                    error("Could not write imported image.")
                }
                output.flush()
            }
            if (!target.exists() || target.length() <= 0L) {
                error("Imported image file is empty.")
            }
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    /**
     * Files from the document picker can be far larger than camera photos (scans, 50MP+
     * shots), so the decode is downscaled to [MAX_IMPORT_EDGE_PX] — still above the
     * processing size — to keep peak memory bounded on low-RAM devices.
     */
    private fun decodeBitmap(uri: Uri): Bitmap? {
        return runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longEdge = maxOf(info.size.width, info.size.height)
                if (longEdge > MAX_IMPORT_EDGE_PX) {
                    val scale = MAX_IMPORT_EDGE_PX.toFloat() / longEdge
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
            if (decoded.config == Bitmap.Config.ARGB_8888) {
                decoded
            } else {
                decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
            }
        }.getOrNull()
    }

    private companion object {
        const val IMPORT_JPEG_QUALITY = 95
        const val MAX_IMPORT_EDGE_PX = 4_096
    }
}
