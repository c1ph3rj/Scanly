package `in`.c1ph3rj.scanly.feature.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.FaceDetectionAvailability
import `in`.c1ph3rj.scanly.core.ml.IdCardFaceDetection
import `in`.c1ph3rj.scanly.core.ml.MlKitIdCardFaceDetector
import `in`.c1ph3rj.scanly.core.processing.OpenCvPageFilterProcessor
import `in`.c1ph3rj.scanly.core.processing.PageFilterAdjustmentsApplier
import `in`.c1ph3rj.scanly.core.processing.PerspectiveBitmapTransform
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Live editor preview: raw → rotation → perspective crop → selected filter → adjustments.
 * When only a processed path is available, shows that image as-is (already rendered).
 */
@Composable
internal fun rememberEditorPreviewBitmap(
    rawImagePath: String?,
    fallbackImagePath: String?,
    rotationDegrees: Int,
    selectedFilter: PageFilterPreset,
    cropQuad: DocumentCornerQuad? = null,
    filterAdjustments: PageFilterAdjustments = PageFilterAdjustments.Default,
    scanMode: ScanMode = ScanMode.DOCUMENT,
): State<ImageBitmap?> = produceState<ImageBitmap?>(
    initialValue = null,
    rawImagePath,
    fallbackImagePath,
    rotationDegrees,
    selectedFilter,
    cropQuad,
    filterAdjustments,
    scanMode,
) {
    value = withContext(Dispatchers.Default) {
        buildCroppedFilteredPreview(
            rawImagePath = rawImagePath,
            fallbackImagePath = fallbackImagePath,
            rotationDegrees = rotationDegrees,
            selectedFilter = selectedFilter,
            cropQuad = cropQuad,
            filterAdjustments = filterAdjustments,
            scanMode = scanMode,
        )?.asImageBitmap()
    }
}

/**
 * Uncropped rotated (+ optional filter) preview for the crop canvas, where the quad
 * overlay must sit on the full frame.
 */
@Composable
internal fun rememberCropCanvasPreviewBitmap(
    rawImagePath: String?,
    fallbackImagePath: String?,
    rotationDegrees: Int,
    selectedFilter: PageFilterPreset,
): State<ImageBitmap?> = produceState<ImageBitmap?>(
    initialValue = null,
    rawImagePath,
    fallbackImagePath,
    rotationDegrees,
    selectedFilter,
) {
    value = withContext(Dispatchers.Default) {
        val sourcePath = rawImagePath ?: fallbackImagePath ?: return@withContext null
        val rotatedBitmap = decodeEditorBitmap(
            path = sourcePath,
            userRotationDegrees = if (rawImagePath != null) rotationDegrees else 0,
        ) ?: return@withContext null
        val filteredBitmap = runCatching {
            OpenCvPageFilterProcessor.apply(rotatedBitmap, selectedFilter)
        }.getOrElse {
            rotatedBitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        if (filteredBitmap !== rotatedBitmap) {
            rotatedBitmap.recycle()
        }
        filteredBitmap.asImageBitmap()
    }
}

internal data class FilterPreviewState(
    val isLoading: Boolean,
    val previews: Map<PageFilterPreset, ImageBitmap>,
)

@Composable
internal fun rememberFilterPreviewBitmaps(
    rawImagePath: String?,
    fallbackImagePath: String?,
    rotationDegrees: Int,
    cropQuad: DocumentCornerQuad? = null,
    scanMode: ScanMode = ScanMode.DOCUMENT,
    filterPresets: List<PageFilterPreset> = PageFilterPreset.entries,
): State<FilterPreviewState> = produceState(
    initialValue = FilterPreviewState(
        isLoading = true,
        previews = emptyMap(),
    ),
    rawImagePath,
    fallbackImagePath,
    rotationDegrees,
    cropQuad,
    scanMode,
    filterPresets,
) {
    value = withContext(Dispatchers.Default) {
        val baseBitmap = buildCroppedUnfilteredPreview(
            rawImagePath = rawImagePath,
            fallbackImagePath = fallbackImagePath,
            rotationDegrees = rotationDegrees,
            cropQuad = cropQuad,
            maxDimension = if (scanMode == ScanMode.ID_CARD) 720 else 360,
        ) ?: return@withContext FilterPreviewState(
            isLoading = false,
            previews = emptyMap(),
        )
        val faceDetection = detectFacesForIdPreview(baseBitmap, scanMode)
        val previewBitmap = createFilterPreviewSource(baseBitmap)
        if (previewBitmap !== baseBitmap) {
            baseBitmap.recycle()
        }

        try {
            val previews = OpenCvPageFilterProcessor
                .applyAll(
                    sourceBitmap = previewBitmap,
                    filterPresets = filterPresets,
                    scanMode = scanMode,
                    faceRegions = faceDetection.regions,
                    faceDetectionAvailable =
                        faceDetection.availability == FaceDetectionAvailability.AVAILABLE,
                )
                .mapValues { (_, bitmap) -> bitmap.asImageBitmap() }
            FilterPreviewState(
                isLoading = false,
                previews = previews,
            )
        } finally {
            previewBitmap.recycle()
        }
    }
}

/**
 * Builds a cropped, filter-applied preview bitmap matching the reprocess pipeline.
 * Without a raw capture, falls back to the on-disk processed image as-is (already
 * cropped and filtered) to avoid double-filtering.
 */
internal suspend fun buildCroppedFilteredPreview(
    rawImagePath: String?,
    fallbackImagePath: String?,
    rotationDegrees: Int,
    selectedFilter: PageFilterPreset,
    cropQuad: DocumentCornerQuad?,
    filterAdjustments: PageFilterAdjustments = PageFilterAdjustments.Default,
    maxDimension: Int = 1_600,
    scanMode: ScanMode = ScanMode.DOCUMENT,
): Bitmap? {
    if (rawImagePath == null) {
        val fallbackPath = fallbackImagePath ?: return null
        // Processed file already includes filter + adjustments.
        return decodeEditorBitmap(
            path = fallbackPath,
            userRotationDegrees = 0,
            maxDimension = maxDimension,
        )
    }
    val cropped = buildCroppedUnfilteredPreview(
        rawImagePath = rawImagePath,
        fallbackImagePath = fallbackImagePath,
        rotationDegrees = rotationDegrees,
        cropQuad = cropQuad,
        maxDimension = maxDimension,
    ) ?: return null
    val faceDetection = detectFacesForIdPreview(cropped, scanMode)
    val filteredBitmap = runCatching {
        OpenCvPageFilterProcessor.apply(
            sourceBitmap = cropped,
            filterPreset = selectedFilter,
            scanMode = scanMode,
            faceRegions = faceDetection.regions,
            faceDetectionAvailable =
                faceDetection.availability == FaceDetectionAvailability.AVAILABLE,
        )
    }.getOrElse {
        cropped.copy(Bitmap.Config.ARGB_8888, false)
    }
    if (filteredBitmap !== cropped) {
        cropped.recycle()
    }
    val adjustedBitmap = runCatching {
        PageFilterAdjustmentsApplier.apply(filteredBitmap, filterAdjustments)
    }.getOrElse {
        filteredBitmap.copy(Bitmap.Config.ARGB_8888, false)
    }
    if (adjustedBitmap !== filteredBitmap) {
        filteredBitmap.recycle()
    }
    return adjustedBitmap
}

/**
 * Decodes raw (or processed fallback), applies user rotation and optional perspective crop.
 * Processed-only fallback is returned without re-cropping (pixels are already warped).
 */
internal fun buildCroppedUnfilteredPreview(
    rawImagePath: String?,
    fallbackImagePath: String?,
    rotationDegrees: Int,
    cropQuad: DocumentCornerQuad?,
    maxDimension: Int = 1_600,
): Bitmap? {
    if (rawImagePath != null) {
        val rotatedBitmap = decodeEditorBitmap(
            path = rawImagePath,
            userRotationDegrees = rotationDegrees,
            maxDimension = maxDimension,
        ) ?: return null
        if (cropQuad == null) {
            return rotatedBitmap
        }
        return runCatching {
            PerspectiveBitmapTransform.correct(rotatedBitmap, cropQuad)
        }.getOrElse {
            rotatedBitmap
        }.also { corrected ->
            if (corrected !== rotatedBitmap) {
                rotatedBitmap.recycle()
            }
        }
    }

    val fallbackPath = fallbackImagePath ?: return null
    // Processed images already include crop + rotation; do not re-apply either.
    return decodeEditorBitmap(
        path = fallbackPath,
        userRotationDegrees = 0,
        maxDimension = maxDimension,
    )
}

internal fun decodeEditorBitmap(
    path: String,
    userRotationDegrees: Int,
    maxDimension: Int = 1_600,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sampleSize = calculatePreviewSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeFile(path, options) ?: return null
    val exifRotation = runCatching { ExifInterface(path).rotationDegrees }.getOrDefault(0)
    val oriented = rotateBitmap(decoded, exifRotation)
    if (oriented !== decoded) {
        decoded.recycle()
    }
    return rotateBitmap(oriented, normalizeEditorRotation(userRotationDegrees))
}

internal fun normalizeEditorRotation(rotationDegrees: Int): Int {
    val normalized = rotationDegrees % 360
    return if (normalized < 0) normalized + 360 else normalized
}

private fun calculatePreviewSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int,
): Int {
    var sampleSize = 1
    var currentWidth = width
    var currentHeight = height
    while (currentWidth > maxDimension || currentHeight > maxDimension) {
        currentWidth /= 2
        currentHeight /= 2
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

private fun createFilterPreviewSource(bitmap: Bitmap): Bitmap {
    val longestEdge = maxOf(bitmap.width, bitmap.height)
    if (longestEdge <= 320) {
        return bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }
    val scale = 320f / longestEdge.toFloat()
    val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

private fun rotateBitmap(
    bitmap: Bitmap,
    rotationDegrees: Int,
): Bitmap {
    val normalizedRotation = normalizeEditorRotation(rotationDegrees)
    if (normalizedRotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(normalizedRotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private val editorIdCardFaceDetector by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    MlKitIdCardFaceDetector()
}

private suspend fun detectFacesForIdPreview(
    bitmap: Bitmap,
    scanMode: ScanMode,
): IdCardFaceDetection =
    if (scanMode == ScanMode.ID_CARD) {
        runCatching {
            editorIdCardFaceDetector.detect(bitmap)
        }.getOrDefault(IdCardFaceDetection.Unavailable)
    } else {
        IdCardFaceDetection()
    }
