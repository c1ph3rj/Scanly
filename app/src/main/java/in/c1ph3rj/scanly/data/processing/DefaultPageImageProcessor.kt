package `in`.c1ph3rj.scanly.data.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerDetector
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.processing.OpenCvPageFilterProcessor
import `in`.c1ph3rj.scanly.core.processing.PerspectiveQuadMath
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.processing.PageImageProcessor
import `in`.c1ph3rj.scanly.domain.processing.ProcessedPageArtifacts
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPageImageProcessor @Inject constructor(
    private val documentCornerDetector: DocumentCornerDetector,
    private val dispatchers: ScanlyDispatchers,
) : PageImageProcessor {

    override suspend fun processCapture(
        rawImagePath: String,
        processedImagePath: String,
        thumbnailPath: String,
        filterPreset: PageFilterPreset,
        detectedCropQuad: DocumentCornerQuad?,
    ): ProcessedPageArtifacts = reprocessPage(
        rawImagePath = rawImagePath,
        processedImagePath = processedImagePath,
        thumbnailPath = thumbnailPath,
        cropQuad = detectedCropQuad,
        rotationDegrees = 0,
        filterPreset = filterPreset,
        detectDocumentWhenCropQuadMissing = detectedCropQuad == null,
    )

    override suspend fun warmUp() = withContext(dispatchers.default) {
        OpenCvPageFilterProcessor.warmUp()
    }

    override suspend fun reprocessPage(
        rawImagePath: String,
        processedImagePath: String,
        thumbnailPath: String,
        cropQuad: DocumentCornerQuad?,
        rotationDegrees: Int,
        filterPreset: PageFilterPreset,
        detectDocumentWhenCropQuadMissing: Boolean,
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

            val detectionResult = if (cropQuad == null && detectDocumentWhenCropQuadMissing) {
                runCatching {
                    documentCornerDetector.detect(editorOrientedBitmap)
                }.getOrElse { null }
            } else {
                null
            }
            val effectiveCropQuad = cropQuad ?: detectionResult?.quad
            val correctedBitmap = effectiveCropQuad?.let { quad ->
                perspectiveCorrect(
                    sourceBitmap = editorOrientedBitmap,
                    quad = quad,
                )
            } ?: editorOrientedBitmap.copy(Bitmap.Config.ARGB_8888, false)
            val enhancedBitmap = applyFilter(
                sourceBitmap = correctedBitmap,
                filterPreset = filterPreset,
            )
            if (correctedBitmap !== enhancedBitmap) {
                correctedBitmap.recycle()
            }

            try {
                writeBitmap(
                    bitmap = enhancedBitmap,
                    outputPath = processedImagePath,
                )
                writeThumbnailFromBitmap(enhancedBitmap, thumbnailPath)
            } finally {
                enhancedBitmap.recycle()
            }

            ProcessedPageArtifacts(
                processedImagePath = processedImagePath,
                thumbnailPath = thumbnailPath,
                cropQuad = effectiveCropQuad,
                rotationDegrees = userRotationDegrees,
                filterPreset = filterPreset,
                processingState = if (effectiveCropQuad != null) {
                    PageProcessingState.PROCESSED
                } else {
                    PageProcessingState.NEEDS_REVIEW
                },
            )
        } finally {
            editorOrientedBitmap?.takeIf { !it.isRecycled }?.recycle()
            if (!orientedBitmap.isRecycled) {
                orientedBitmap.recycle()
            }
        }
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

    private fun perspectiveCorrect(
        sourceBitmap: Bitmap,
        quad: DocumentCornerQuad,
    ): Bitmap {
        val outputSize = PerspectiveQuadMath.outputSize(
            quad = quad,
            sourceWidth = sourceBitmap.width,
            sourceHeight = sourceBitmap.height,
        )
        val destinationBitmap = Bitmap.createBitmap(
            outputSize.width,
            outputSize.height,
            Bitmap.Config.ARGB_8888,
        )
        val matrix = Matrix()
        matrix.setPolyToPoly(
            PerspectiveQuadMath.sourcePoints(quad, sourceBitmap.width, sourceBitmap.height),
            0,
            PerspectiveQuadMath.destinationPoints(outputSize.width - 1, outputSize.height - 1),
            0,
            4,
        )

        val canvas = Canvas(destinationBitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(
            sourceBitmap,
            matrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
        )
        return destinationBitmap
    }

    private fun applyFilter(
        sourceBitmap: Bitmap,
        filterPreset: PageFilterPreset,
    ): Bitmap = OpenCvPageFilterProcessor.apply(
        sourceBitmap = sourceBitmap,
        filterPreset = filterPreset,
    )

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

    private fun writeThumbnailFromBitmap(source: Bitmap, outputPath: String) {
        val largestDimension = maxOf(source.width, source.height)
        val scale = if (largestDimension > THUMBNAIL_MAX_DIMENSION) {
            THUMBNAIL_MAX_DIMENSION.toFloat() / largestDimension
        } else {
            1f
        }
        val thumbnail = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            source
        }
        try {
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { output ->
                thumbnail.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, output)
                output.flush()
            }
        } finally {
            if (thumbnail !== source) {
                thumbnail.recycle()
            }
        }
    }

    private companion object {
        const val MAX_PROCESSING_DIMENSION = 2_400
        const val OUTPUT_JPEG_QUALITY = 94
        const val THUMBNAIL_MAX_DIMENSION = 1_024
        const val THUMBNAIL_JPEG_QUALITY = 90
    }

    private fun normalizeRotationDegrees(rotationDegrees: Int): Int {
        val normalized = rotationDegrees % 360
        return if (normalized < 0) normalized + 360 else normalized
    }
}
