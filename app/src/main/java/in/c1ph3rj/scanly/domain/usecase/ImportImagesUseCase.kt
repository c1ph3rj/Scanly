package `in`.c1ph3rj.scanly.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import kotlinx.coroutines.withContext
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
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            val total = imageUris.size
            require(total > 0) { "No images selected." }

            imageUris.forEachIndexed { index, uri ->
                val current = index + 1
                reportProgress(onProgress, current, total, ImportStage.Preparing)

                val draftResult = pageRepository.prepareCapture(documentId)
                if (draftResult !is ScanlyResult.Success) {
                    error("Failed to prepare page capture for imported image.")
                }
                val draft = draftResult.value
                val rawFile = File(draft.rawImagePath)

                writeNormalizedJpeg(uri, rawFile)

                reportProgress(onProgress, current, total, ImportStage.Detecting)
                val finalizeResult = pageRepository.finalizeCapture(draft)
                if (finalizeResult !is ScanlyResult.Success) {
                    error("Failed to finalize captured page for imported image.")
                }

                reportProgress(onProgress, current, total, ImportStage.Finalizing)
            }
        }.fold(
            onSuccess = { ScanlyResult.Success(Unit) },
            onFailure = { throwable ->
                ScanlyResult.Failure(
                    ScanlyError(
                        message = throwable.message ?: "Failed to import images.",
                        cause = throwable,
                    ),
                )
            },
        )
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
     * Decode via ImageDecoder (HEIC/WEBP-friendly on API 28+) and write a consistent
     * JPEG so EXIF orientation and format quirks don't skip the processing pipeline.
     */
    private fun writeNormalizedJpeg(uri: Uri, target: File) {
        val bitmap = decodeBitmap(uri)
            ?: error("Could not decode imported image.")
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

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = false
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    android.graphics.BitmapFactory.decodeStream(input)
                }?.copy(Bitmap.Config.ARGB_8888, false)
            }
        }.getOrNull()
    }

    private companion object {
        const val IMPORT_JPEG_QUALITY = 95
    }
}
