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
import `in`.c1ph3rj.scanly.core.processing.PerspectiveQuadMath
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.processing.PageImageProcessor
import `in`.c1ph3rj.scanly.domain.processing.ProcessedPageArtifacts
import `in`.c1ph3rj.scanly.data.storage.DocumentStorageManager
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPageImageProcessor @Inject constructor(
    private val documentCornerDetector: DocumentCornerDetector,
    private val storageManager: DocumentStorageManager,
    private val dispatchers: ScanlyDispatchers,
) : PageImageProcessor {

    override suspend fun processCapture(
        rawImagePath: String,
        processedImagePath: String,
        thumbnailPath: String,
    ): ProcessedPageArtifacts = reprocessPage(
        rawImagePath = rawImagePath,
        processedImagePath = processedImagePath,
        thumbnailPath = thumbnailPath,
        cropQuad = null,
        rotationDegrees = 0,
        filterPreset = PageFilterPreset.ENHANCED_COLOR,
    )

    override suspend fun reprocessPage(
        rawImagePath: String,
        processedImagePath: String,
        thumbnailPath: String,
        cropQuad: DocumentCornerQuad?,
        rotationDegrees: Int,
        filterPreset: PageFilterPreset,
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

            val detectionResult = if (cropQuad == null) {
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
            } finally {
                enhancedBitmap.recycle()
            }

            val thumbnailResult = storageManager.generatePageThumbnail(
                rawImagePath = processedImagePath,
                thumbnailPath = thumbnailPath,
            )

            ProcessedPageArtifacts(
                processedImagePath = processedImagePath,
                thumbnailPath = thumbnailResult.thumbnailPath,
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
    ): Bitmap = when (filterPreset) {
        PageFilterPreset.ORIGINAL -> sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
        PageFilterPreset.ENHANCED_COLOR -> enhanceDocumentBitmap(sourceBitmap)
        PageFilterPreset.GRAYSCALE -> grayscaleBitmap(sourceBitmap)
        PageFilterPreset.BLACK_AND_WHITE -> blackAndWhiteBitmap(sourceBitmap)
    }

    private fun enhanceDocumentBitmap(sourceBitmap: Bitmap): Bitmap {
        val workingBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(workingBitmap.width * workingBitmap.height)
        workingBitmap.getPixels(pixels, 0, workingBitmap.width, 0, 0, workingBitmap.width, workingBitmap.height)

        for (index in pixels.indices) {
            val color = pixels[index]
            val alpha = Color.alpha(color)
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            val maxChannel = maxOf(red, green, blue)
            val minChannel = minOf(red, green, blue)
            val luminance = (0.2126f * red) + (0.7152f * green) + (0.0722f * blue)
            val saturation = if (maxChannel == 0) 0f else (maxChannel - minChannel) / maxChannel.toFloat()

            val boostedRed = enhanceChannel(red)
            val boostedGreen = enhanceChannel(green)
            val boostedBlue = enhanceChannel(blue)

            val paperBoost = ((luminance - PAPER_WHITENING_START) / (255f - PAPER_WHITENING_START))
                .coerceIn(0f, 1f)
            val neutralBoost = if (saturation < LOW_SATURATION_THRESHOLD) paperBoost else paperBoost * 0.4f

            val finalRed = blendToWhite(boostedRed, neutralBoost)
            val finalGreen = blendToWhite(boostedGreen, neutralBoost)
            val finalBlue = blendToWhite(boostedBlue, neutralBoost)

            pixels[index] = Color.argb(alpha, finalRed, finalGreen, finalBlue)
        }

        workingBitmap.setPixels(pixels, 0, workingBitmap.width, 0, 0, workingBitmap.width, workingBitmap.height)
        return workingBitmap
    }

    private fun grayscaleBitmap(sourceBitmap: Bitmap): Bitmap {
        val workingBitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(workingBitmap.width * workingBitmap.height)
        workingBitmap.getPixels(pixels, 0, workingBitmap.width, 0, 0, workingBitmap.width, workingBitmap.height)

        for (index in pixels.indices) {
            val color = pixels[index]
            val luminance = ((0.299f * Color.red(color)) + (0.587f * Color.green(color)) + (0.114f * Color.blue(color))).toInt()
            pixels[index] = Color.argb(
                Color.alpha(color),
                luminance,
                luminance,
                luminance,
            )
        }
        workingBitmap.setPixels(pixels, 0, workingBitmap.width, 0, 0, workingBitmap.width, workingBitmap.height)
        return workingBitmap
    }

    private fun blackAndWhiteBitmap(sourceBitmap: Bitmap): Bitmap {
        val grayscale = grayscaleBitmap(sourceBitmap)
        val pixels = IntArray(grayscale.width * grayscale.height)
        grayscale.getPixels(pixels, 0, grayscale.width, 0, 0, grayscale.width, grayscale.height)

        for (index in pixels.indices) {
            val color = pixels[index]
            val luminance = Color.red(color)
            val threshold = if (luminance >= 160) 255 else 0
            pixels[index] = Color.argb(Color.alpha(color), threshold, threshold, threshold)
        }
        grayscale.setPixels(pixels, 0, grayscale.width, 0, 0, grayscale.width, grayscale.height)
        return grayscale
    }

    private fun enhanceChannel(channel: Int): Int {
        val normalized = channel / 255f
        val contrasted = ((normalized - 0.5f) * CONTRAST) + 0.5f + BRIGHTNESS_BIAS
        return (contrasted.coerceIn(0f, 1f) * 255f).toInt()
    }

    private fun blendToWhite(channel: Int, amount: Float): Int {
        return (channel + ((255 - channel) * amount)).toInt().coerceIn(0, 255)
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
        const val CONTRAST = 1.12f
        const val BRIGHTNESS_BIAS = 0.04f
        const val PAPER_WHITENING_START = 168f
        const val LOW_SATURATION_THRESHOLD = 0.18f
    }

    private fun normalizeRotationDegrees(rotationDegrees: Int): Int {
        val normalized = rotationDegrees % 360
        return if (normalized < 0) normalized + 360 else normalized
    }
}
