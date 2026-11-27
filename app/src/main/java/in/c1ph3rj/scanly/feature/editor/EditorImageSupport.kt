package `in`.c1ph3rj.scanly.feature.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import `in`.c1ph3rj.scanly.core.common.runCatchingCancellable
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.processing.OpenCvPageFilterProcessor
import `in`.c1ph3rj.scanly.core.processing.PageFilterAdjustmentsApplier
import `in`.c1ph3rj.scanly.core.processing.PerspectiveBitmapTransform
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Live editor preview: raw → rotation → perspective crop → selected filter → adjustments.
 * When only a processed path is available, shows that image as-is (already rendered).
 */
@Composable
internal fun rememberEditorPreview(
    rawImagePath: String?,
    fallbackImagePath: String?,
    rotationDegrees: Int,
    selectedFilter: PageFilterPreset,
    cropQuad: DocumentCornerQuad? = null,
    filterAdjustments: PageFilterAdjustments = PageFilterAdjustments.Default,
): EditorPreview {
    val source by rememberEditorPreviewSource(rawImagePath, fallbackImagePath, rotationDegrees, cropQuad)
    val filtered = rememberEditorFilteredBitmap(source, selectedFilter, filterAdjustments.intensity)
    return rememberEditorAdjustedPreview(source, selectedFilter, filtered, filterAdjustments)
}

internal sealed interface EditorPreview {
    data object Loading : EditorPreview
    data object Unavailable : EditorPreview
    data class Ready(val image: ImageBitmap) : EditorPreview
}

internal class EditorPreviewSource(val bitmap: Bitmap?, val isProcessedFallback: Boolean) {
    val profile by lazy {
        bitmap?.let { runCatchingCancellable { OpenCvPageFilterProcessor.analyze(it) }.getOrNull() }
    }
}

@Composable
internal fun rememberEditorPreviewSource(
    rawImagePath: String?,
    fallbackImagePath: String?,
    rotationDegrees: Int,
    cropQuad: DocumentCornerQuad?,
): State<EditorPreviewSource?> = key(rawImagePath, fallbackImagePath, rotationDegrees, cropQuad) {
    produceState<EditorPreviewSource?>(initialValue = null) {
        value = withContext(Dispatchers.Default) {
            val bitmap = buildCroppedUnfilteredPreview(
                rawImagePath = rawImagePath,
                fallbackImagePath = fallbackImagePath,
                rotationDegrees = rotationDegrees,
                cropQuad = cropQuad,
            )
            EditorPreviewSource(bitmap, isProcessedFallback = rawImagePath == null)
        }
    }
}

private data class FilterRenderRequest(
    val source: EditorPreviewSource,
    val preset: PageFilterPreset,
    val intensity: Float,
)

@Composable
internal fun rememberEditorFilteredBitmap(
    source: EditorPreviewSource?,
    selectedFilter: PageFilterPreset,
    intensity: Float,
): Bitmap? {
    val bitmap = source?.bitmap ?: return null
    if (source.isProcessedFallback || selectedFilter == PageFilterPreset.ORIGINAL) return bitmap
    return rememberRenderedPreview(FilterRenderRequest(source, selectedFilter, intensity)) { (input, preset, strength) ->
        val image = checkNotNull(input.bitmap)
        runCatchingCancellable {
            OpenCvPageFilterProcessor.apply(image, preset, input.profile, strength)
        }.getOrElse { image }
    }
}

/**
 * Final preview for [selectedFilter] on [source]. While a slider moves, the last finished image
 * stays on screen until the next render lands; a new page, crop or preset starts from Loading.
 */
@Composable
internal fun rememberEditorAdjustedPreview(
    source: EditorPreviewSource?,
    selectedFilter: PageFilterPreset,
    filtered: Bitmap?,
    adjustments: PageFilterAdjustments,
): EditorPreview {
    val preview = rememberEditorAdjustedPreviewNow(source, filtered, adjustments)
    val held = remember { HeldEditorPreview() }
    val scope = source to selectedFilter
    SideEffect {
        if (preview is EditorPreview.Ready) held.update(scope, preview)
    }
    if (preview == EditorPreview.Loading) held.readyFor(scope)?.let { return it }
    return preview
}

private class HeldEditorPreview {
    private var scope: Any? = null
    private var ready: EditorPreview.Ready? = null

    fun update(scope: Any, ready: EditorPreview.Ready) {
        this.scope = scope
        this.ready = ready
    }

    fun readyFor(scope: Any): EditorPreview.Ready? = ready?.takeIf { this.scope == scope }
}

@Composable
private fun rememberEditorAdjustedPreviewNow(
    source: EditorPreviewSource?,
    filtered: Bitmap?,
    adjustments: PageFilterAdjustments,
): EditorPreview {
    if (source == null) return EditorPreview.Loading
    if (source.bitmap == null) return EditorPreview.Unavailable
    if (filtered == null) return EditorPreview.Loading

    val tonalAdjustments = adjustments.copy(intensity = 1f)
    val adjusted = if (source.isProcessedFallback || tonalAdjustments.isDefault) filtered else {
        rememberRenderedPreview(filtered to tonalAdjustments) { (bitmap, tonal) ->
            runCatchingCancellable { PageFilterAdjustmentsApplier.apply(bitmap, tonal) }.getOrElse { bitmap }
        } ?: return EditorPreview.Loading
    }
    return remember(adjusted) { EditorPreview.Ready(adjusted.asImageBitmap()) }
}

@Composable
private fun <Request, Output> rememberRenderedPreview(request: Request, render: (Request) -> Output): Output? {
    val latestRequest by rememberUpdatedState(request)
    val latestRender by rememberUpdatedState(render)
    val result by produceState<Pair<Request, Output>?>(initialValue = null) {
        // Wait for a cancelled native render to finish before starting the newest request.
        snapshotFlow { latestRequest }.collectLatest { input ->
            val renderInput = latestRender
            value = input to withContext(Dispatchers.Default) { renderInput(input) }
        }
    }
    return result?.takeIf { it.first == request }?.second
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
        val filteredBitmap = if (selectedFilter == PageFilterPreset.ORIGINAL) rotatedBitmap else
            runCatchingCancellable { OpenCvPageFilterProcessor.apply(rotatedBitmap, selectedFilter) }
                .getOrElse { rotatedBitmap }
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
    source: EditorPreviewSource?,
): State<FilterPreviewState> = key(source) {
    produceState(initialValue = FilterPreviewState(isLoading = true, previews = emptyMap())) {
        if (source == null) return@produceState
        val bitmap = source.bitmap
        if (bitmap == null) {
            value = FilterPreviewState(isLoading = false, previews = emptyMap())
            return@produceState
        }
        val previews = withContext(Dispatchers.Default) {
            val previewBitmap = createFilterPreviewSource(bitmap)
            try {
                runCatchingCancellable { OpenCvPageFilterProcessor.applyAll(previewBitmap, profile = source.profile) }
                    .getOrDefault(emptyMap())
                    .mapValues { (_, image) -> image.asImageBitmap() }
            } finally {
                if (previewBitmap !== bitmap) previewBitmap.recycle()
            }
        }
        value = FilterPreviewState(isLoading = false, previews = previews)
    }
}

/**
 * Decodes raw (or processed fallback), applies user rotation and optional perspective crop.
 * Processed-only fallback is returned without re-cropping (pixels are already warped).
 */
private fun buildCroppedUnfilteredPreview(
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
    val oriented = rotateBitmap(decoded, exifRotation + normalizeEditorRotation(userRotationDegrees))
    if (oriented !== decoded) {
        decoded.recycle()
    }
    return oriented
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
        return bitmap
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
