package `in`.c1ph3rj.scanly.data.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.ml.AutomaticDocumentModelSelector
import `in`.c1ph3rj.scanly.core.ml.BookAwareCornerResolver
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerDetector
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.DocumentGateDetector
import `in`.c1ph3rj.scanly.core.ml.DocumentGatePolicy
import `in`.c1ph3rj.scanly.core.ml.DocumentQuadPolicy
import `in`.c1ph3rj.scanly.core.ml.IdCardFaceDetection
import `in`.c1ph3rj.scanly.core.ml.IdCardFaceDetector
import `in`.c1ph3rj.scanly.core.ml.FaceDetectionAvailability
import `in`.c1ph3rj.scanly.core.ml.QuadReadiness
import `in`.c1ph3rj.scanly.core.processing.OpenCvPageFilterProcessor
import `in`.c1ph3rj.scanly.core.processing.PageFilterAdjustmentsApplier
import `in`.c1ph3rj.scanly.core.processing.PerspectiveBitmapTransform
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import `in`.c1ph3rj.scanly.domain.processing.PageImageProcessor
import `in`.c1ph3rj.scanly.domain.processing.ProcessedPageArtifacts
import `in`.c1ph3rj.scanly.data.storage.DocumentStorageManager
import `in`.c1ph3rj.scanly.domain.repository.SettingsRepository
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPageImageProcessor @Inject constructor(
    private val bookAwareCornerResolver: BookAwareCornerResolver,
    private val documentCornerDetector: DocumentCornerDetector,
    private val documentGateDetector: DocumentGateDetector,
    private val automaticDocumentModelSelector: AutomaticDocumentModelSelector,
    private val idCardFaceDetector: IdCardFaceDetector,
    private val storageManager: DocumentStorageManager,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: ScanlyDispatchers,
) : PageImageProcessor {

    override suspend fun processCapture(
        rawImagePath: String,
        processedImagePath: String,
        thumbnailPath: String,
        filterPreset: PageFilterPreset,
        filterAdjustments: PageFilterAdjustments,
        scanMode: ScanMode,
    ): ProcessedPageArtifacts = reprocessPage(
        rawImagePath = rawImagePath,
        processedImagePath = processedImagePath,
        thumbnailPath = thumbnailPath,
        cropQuad = null,
        rotationDegrees = 0,
        filterPreset = filterPreset,
        filterAdjustments = filterAdjustments,
        detectDocumentWhenCropQuadMissing = true,
        scanMode = scanMode,
    )

    override suspend fun reprocessPage(
        rawImagePath: String,
        processedImagePath: String,
        thumbnailPath: String,
        cropQuad: DocumentCornerQuad?,
        rotationDegrees: Int,
        filterPreset: PageFilterPreset,
        filterAdjustments: PageFilterAdjustments,
        detectDocumentWhenCropQuadMissing: Boolean,
        scanMode: ScanMode,
    ): ProcessedPageArtifacts = withContext(dispatchers.default) {
        val exifRotationDegrees = ExifInterface(rawImagePath).rotationDegrees
        val userRotationDegrees = normalizeRotationDegrees(rotationDegrees)
        val decodedBitmap = decodeForProcessing(rawImagePath)
            ?: error("Could not decode captured image at $rawImagePath.")
        val orientedBitmap = rotateBitmapIfNeeded(decodedBitmap, exifRotationDegrees)
        if (orientedBitmap !== decodedBitmap) {
            decodedBitmap.recycle()
        }

        var editorOrientedBitmap: Bitmap? = null
        try {
            editorOrientedBitmap = rotateBitmapIfNeeded(orientedBitmap, userRotationDegrees)
            if (editorOrientedBitmap !== orientedBitmap) {
                orientedBitmap.recycle()
            }

            // Still-image finalize (camera capture + gallery import) always runs corner
            // detection. The semantic gate no longer skips detection — that blocked
            // imports of already-cropped scans and borderline physical pages.
            // Gate only influences review state when no usable quad is found.
            val gateAccepted = if (
                cropQuad == null &&
                detectDocumentWhenCropQuadMissing &&
                settingsRepository.getDocumentGateEnabled()
            ) {
                runCatching {
                    documentGateDetector.classify(editorOrientedBitmap)
                        .acceptsPhysicalDocument(DocumentGatePolicy.POST_PROCESSING_THRESHOLD)
                }.getOrDefault(true)
            } else {
                true
            }
            val effectiveCropQuad = if (cropQuad != null) {
                cropQuad
            } else if (detectDocumentWhenCropQuadMissing) {
                detectStillImageQuad(editorOrientedBitmap, scanMode)
            } else {
                null
            }
            val correctedBitmap = effectiveCropQuad?.let { quad ->
                PerspectiveBitmapTransform.correct(
                    sourceBitmap = editorOrientedBitmap,
                    quad = quad,
                )
            } ?: editorOrientedBitmap.copy(Bitmap.Config.ARGB_8888, false)
            val faceDetection = if (scanMode == ScanMode.ID_CARD) {
                runCatching {
                    idCardFaceDetector.detect(correctedBitmap)
                }.getOrDefault(IdCardFaceDetection.Unavailable)
            } else {
                IdCardFaceDetection()
            }
            val filtered = OpenCvPageFilterProcessor.applyWithResolvedPreset(
                sourceBitmap = correctedBitmap,
                filterPreset = filterPreset,
                scanMode = scanMode,
                faceRegions = faceDetection.regions,
                faceDetectionAvailable =
                    faceDetection.availability == FaceDetectionAvailability.AVAILABLE,
            )
            if (correctedBitmap !== filtered.bitmap) {
                correctedBitmap.recycle()
            }
            val filteredBitmap = filtered.bitmap
            val appliedFilterPreset = filtered.appliedPreset
            val enhancedBitmap = runCatching {
                PageFilterAdjustmentsApplier.apply(filteredBitmap, filterAdjustments)
            }.getOrElse {
                filteredBitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
            if (enhancedBitmap !== filteredBitmap) {
                filteredBitmap.recycle()
            }

            try {
                writeBitmap(
                    bitmap = enhancedBitmap,
                    outputPath = processedImagePath,
                )
            } finally {
                enhancedBitmap.recycle()
            }

            val thumbnailResult = storageManager.generatePageThumbnail(
                rawImagePath = processedImagePath,
                thumbnailPath = thumbnailPath,
            )

            val processingState = when {
                effectiveCropQuad != null -> PageProcessingState.PROCESSED
                // No quad and gate rejected: likely not a document page — review.
                !gateAccepted -> PageProcessingState.NEEDS_REVIEW
                else -> PageProcessingState.NEEDS_REVIEW
            }

            ProcessedPageArtifacts(
                processedImagePath = processedImagePath,
                thumbnailPath = thumbnailResult.thumbnailPath,
                cropQuad = effectiveCropQuad,
                rotationDegrees = userRotationDegrees,
                // Persist the concrete filter Auto chose so the editor and reprocess match.
                filterPreset = appliedFilterPreset,
                processingState = processingState,
            )
        } finally {
            editorOrientedBitmap?.takeIf { !it.isRecycled }?.recycle()
            if (!orientedBitmap.isRecycled) {
                orientedBitmap.recycle()
            }
        }
    }

    override suspend fun detectDocumentCorners(
        rawImagePath: String,
        rotationDegrees: Int,
        scanMode: ScanMode,
    ): DocumentCornerQuad? = withContext(dispatchers.default) {
        val exifRotationDegrees = ExifInterface(rawImagePath).rotationDegrees
        val userRotationDegrees = normalizeRotationDegrees(rotationDegrees)
        val decodedBitmap = decodeForProcessing(rawImagePath) ?: return@withContext null
        val orientedBitmap = rotateBitmapIfNeeded(decodedBitmap, exifRotationDegrees)
        if (orientedBitmap !== decodedBitmap) {
            decodedBitmap.recycle()
        }

        var editorOrientedBitmap: Bitmap? = null
        try {
            editorOrientedBitmap = rotateBitmapIfNeeded(orientedBitmap, userRotationDegrees)
            if (editorOrientedBitmap !== orientedBitmap) {
                orientedBitmap.recycle()
            }
            detectStillImageQuad(editorOrientedBitmap, scanMode)
        } finally {
            editorOrientedBitmap?.takeIf { !it.isRecycled }?.recycle()
            if (!orientedBitmap.isRecycled) {
                orientedBitmap.recycle()
            }
        }
    }

    /**
     * Still-image corner detection aligned with Model Benchmark behaviour:
     * 1) Book-aware resolver in STILL_PROCESS mode (no book-gutter damage, card-friendly aspect)
     * 2) Raw detector fallback if the resolver still drops a usable model quad
     */
    private suspend fun detectStillImageQuad(
        bitmap: Bitmap,
        scanMode: ScanMode,
    ): DocumentCornerQuad? {
        val selectedModel = if (settingsRepository.getAutomaticModelSelection()) {
            runCatching { automaticDocumentModelSelector.selection().postProcessingModel }
                .getOrDefault(settingsRepository.getPostProcessingModel())
        } else {
            settingsRepository.getPostProcessingModel()
        }

        val resolved = runCatching {
            bookAwareCornerResolver.detect(
                bitmap = bitmap,
                selectedModel = selectedModel,
                readiness = QuadReadiness.STILL_PROCESS,
                scanMode = scanMode,
            ).result.quad
        }.getOrNull()
        if (resolved != null) return resolved

        // Same path the benchmark visualizes: raw model corners.
        val raw = runCatching {
            documentCornerDetector.detect(bitmap, selectedModel).quad
        }.getOrNull()
        return raw?.takeIf(DocumentQuadPolicy::isStillProcessReady)
            ?: raw?.takeIf { it.isValid() && it.isConvex() }
    }

    private fun decodeForProcessing(path: String): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, boundsOptions)

        val sampleSize = calculateInSampleSize(
            sourceWidth = boundsOptions.outWidth,
            sourceHeight = boundsOptions.outHeight,
            maxDimension = MAX_PROCESSING_DIMENSION,
        )
        val bitmapOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, bitmapOptions)
    }

    private fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        maxDimension: Int,
    ): Int {
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

    private fun rotateBitmapIfNeeded(
        bitmap: Bitmap,
        rotationDegrees: Int,
    ): Bitmap {
        if (rotationDegrees == 0) {
            return bitmap
        }
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun writeBitmap(
        bitmap: Bitmap,
        outputPath: String,
    ) {
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, OUTPUT_JPEG_QUALITY, outputStream)
            outputStream.flush()
        }
    }

    private companion object {
        const val MAX_PROCESSING_DIMENSION = 2_400
        const val OUTPUT_JPEG_QUALITY = 94
    }

    private fun normalizeRotationDegrees(rotationDegrees: Int): Int {
        val normalized = rotationDegrees % 360
        return if (normalized < 0) normalized + 360 else normalized
    }

}
