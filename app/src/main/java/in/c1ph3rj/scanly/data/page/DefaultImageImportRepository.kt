package `in`.c1ph3rj.scanly.data.page

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ImportImagesProgress
import `in`.c1ph3rj.scanly.domain.model.ImportStage
import `in`.c1ph3rj.scanly.domain.repository.ImageImportRepository
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes gallery URIs to JPEG raw captures, then finalizes through the same
 * [PageRepository.finalizeCapture] path as camera captures.
 */
@Singleton
class DefaultImageImportRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pageRepository: PageRepository,
    private val dispatchers: ScanlyDispatchers,
) : ImageImportRepository {

    override suspend fun importImages(
        documentId: String,
        imageUriStrings: List<String>,
        onProgress: suspend (ImportImagesProgress) -> Unit,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            val total = imageUriStrings.size
            require(total > 0) { "No images selected." }

            imageUriStrings.forEachIndexed { index, uriString ->
                val current = index + 1
                reportProgress(onProgress, current, total, ImportStage.Preparing)

                val draftResult = pageRepository.prepareCapture(documentId)
                if (draftResult !is ScanlyResult.Success) {
                    error("Failed to prepare page capture for imported image.")
                }
                val draft = draftResult.value
                val rawFile = File(draft.rawImagePath)

                writeNormalizedJpeg(Uri.parse(uriString), rawFile)

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
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.isMutableRequired = false
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val sample = calculateInSampleSize(
                        sourceWidth = info.size.width,
                        sourceHeight = info.size.height,
                        maxDimension = IMPORT_MAX_LONGEST_EDGE,
                    )
                    if (sample > 1) {
                        decoder.setTargetSampleSize(sample)
                    }
                }.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                decodeBitmapPreP(uri)
            }
        }.getOrNull()
    }

    private fun decodeBitmapPreP(uri: Uri): Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, bounds)
        }
        val sampleSize = calculateInSampleSize(
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            maxDimension = IMPORT_MAX_LONGEST_EDGE,
        )
        val options = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, options)
        }?.copy(Bitmap.Config.ARGB_8888, false)
    }

    /**
     * Power-of-two sample size so longest edge stays ≤ [maxDimension] (matches still-process budget).
     */
    private fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        maxDimension: Int,
    ): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1
        var sampleSize = 1
        var width = sourceWidth
        var height = sourceHeight
        while (width > maxDimension || height > maxDimension) {
            width /= 2
            height /= 2
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private companion object {
        const val IMPORT_JPEG_QUALITY = 95
        /** Align with [DefaultPageImageProcessor] processing decode budget. */
        const val IMPORT_MAX_LONGEST_EDGE = 2_400
    }
}
