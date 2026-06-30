package `in`.c1ph3rj.scanly.core.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

@Composable
fun ZoomableImageDialog(
    imagePath: String?,
    title: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
        ),
    ) {
        val view = LocalView.current
        val isLightBackground = MaterialTheme.colorScheme.background.luminance() > 0.5f
        DisposableEffect(view, isLightBackground) {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = isLightBackground
                controller.isAppearanceLightNavigationBars = isLightBackground
            }
            onDispose {}
        }

        ZoomableImageViewer(
            imagePath = imagePath,
            title = title,
            onNavigateUp = onDismiss,
            closeContentDescription = "Close",
        )
    }
}

@Composable
fun ZoomableImageViewer(
    imagePath: String?,
    title: String,
    onNavigateUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    state: ZoomableImageState = rememberZoomableImageState(imagePath),
    closeContentDescription: String = "Back",
    allowParentHorizontalGestures: Boolean = false,
    showTopBar: Boolean = true,
    showZoomIndicator: Boolean = true,
    imageOverlay: @Composable BoxScope.(ZoomableImageOverlayInfo) -> Unit = {},
    onZoomActiveChange: (Boolean) -> Unit = {},
    trailingAction: @Composable (
        zoomActive: Boolean,
        onResetZoom: () -> Unit,
    ) -> Unit = { zoomActive, onResetZoom ->
        if (zoomActive) {
            ChromeIconButton(
                icon = Icons.Filled.Refresh,
                contentDescription = "Reset zoom",
                onClick = onResetZoom,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Box(modifier = Modifier.size(44.dp))
        }
    },
) {
    val zoomActive = state.isZoomActive
    val resetZoom = state::reset
    val currentOnZoomActiveChange by rememberUpdatedState(onZoomActiveChange)

    LaunchedEffect(imagePath) {
        state.reset()
    }

    LaunchedEffect(zoomActive) {
        currentOnZoomActiveChange(zoomActive)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            val imageBitmap by rememberZoomableImageBitmap(imagePath)
            when {
                imageBitmap == null && imagePath != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                        )
                    }
                }

                imageBitmap == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Image unavailable",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                else -> {
                    ZoomableImageCanvas(
                        imageBitmap = imageBitmap!!,
                        scale = state.scale,
                        offset = state.offset,
                        onScaleChange = { state.scale = it },
                        onOffsetChange = { state.offset = it },
                        allowParentHorizontalGestures = allowParentHorizontalGestures,
                        showZoomIndicator = showZoomIndicator,
                        imageOverlay = imageOverlay,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (showTopBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onNavigateUp != null) {
                            ChromeIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = closeContentDescription,
                                onClick = onNavigateUp,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        MetricChip(
                            label = title,
                            containerColor = Color.Black.copy(alpha = 0.42f),
                            contentColor = Color.White,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        )
                    }
                    trailingAction(zoomActive, resetZoom)
                }
            }
        }
    }
}

@Stable
class ZoomableImageState internal constructor() {
    var scale by mutableFloatStateOf(MIN_SCALE)
        internal set
    internal var offset by mutableStateOf(Offset.Zero)

    val isZoomActive: Boolean
        get() = scale > 1.02f

    fun reset() {
        scale = MIN_SCALE
        offset = Offset.Zero
    }
}

@Composable
fun rememberZoomableImageState(imageKey: Any?): ZoomableImageState =
    remember(imageKey) { ZoomableImageState() }

data class ZoomableImageOverlayInfo(
    val fittedWidthPx: Float,
    val fittedHeightPx: Float,
    val scale: Float,
)

@Composable
private fun ZoomableImageCanvas(
    imageBitmap: ImageBitmap,
    scale: Float,
    offset: Offset,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit,
    allowParentHorizontalGestures: Boolean,
    showZoomIndicator: Boolean,
    imageOverlay: @Composable BoxScope.(ZoomableImageOverlayInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val fittedImageSize = remember(containerSize, imageBitmap) {
        computeFittedImageSize(
            containerSize = containerSize,
            imageWidth = imageBitmap.width,
            imageHeight = imageBitmap.height,
        )
    }

    val currentScale by rememberUpdatedState(scale)
    val currentOffset by rememberUpdatedState(offset)
    val currentOnScaleChange by rememberUpdatedState(onScaleChange)
    val currentOnOffsetChange by rememberUpdatedState(onOffsetChange)
    val currentFittedImageSize by rememberUpdatedState(fittedImageSize)
    val currentContainerSize by rememberUpdatedState(containerSize)

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .background(Color.Black)
            .clipToBounds()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onDoubleClick = {
                    if (currentScale > MIN_SCALE) {
                        currentOnScaleChange(MIN_SCALE)
                        currentOnOffsetChange(Offset.Zero)
                    } else {
                        currentOnScaleChange(3f)
                        currentOnOffsetChange(Offset.Zero)
                    }
                },
            )
            .pointerInput(allowParentHorizontalGestures) {
                val updateTransform: (Offset, Offset, Float) -> Unit = { centroid, pan, zoom ->
                    val oldScale = currentScale
                    val newScale = (oldScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    val factor = newScale / oldScale

                    val focus = centroid - Offset(size.width / 2f, size.height / 2f)
                    val targetOffset = currentOffset * factor + focus * (1f - factor) + pan

                    currentOnScaleChange(newScale)
                    currentOnOffsetChange(
                        clampPanOffset(
                            offset = targetOffset,
                            scale = newScale,
                            fittedImageSize = currentFittedImageSize,
                            containerSize = currentContainerSize,
                        ),
                    )

                }

                if (!allowParentHorizontalGestures) {
                    detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                        updateTransform(centroid, pan, zoom)
                    }
                } else {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var ownsGesture = currentScale > MIN_SCALE
                        do {
                            val event = awaitPointerEvent()
                            val pressedPointers = event.changes.count { it.pressed }
                            if (pressedPointers >= 2) {
                                ownsGesture = true
                            } else if (event.changes.any { it.isConsumed }) {
                                ownsGesture = false
                            }
                            if (ownsGesture && pressedPointers > 0) {
                                updateTransform(
                                    event.calculateCentroid(useCurrent = true),
                                    event.calculatePan(),
                                    event.calculateZoom(),
                                )
                            }
                            if (ownsGesture) {
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (containerSize != IntSize.Zero) {
            Box(
                modifier = Modifier
                    .size(
                        width = with(density) { fittedImageSize.width.toDp() },
                        height = with(density) { fittedImageSize.height.toDp() },
                    )
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                        transformOrigin = TransformOrigin.Center
                    },
            ) {
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
                imageOverlay(
                    ZoomableImageOverlayInfo(
                        fittedWidthPx = fittedImageSize.width,
                        fittedHeightPx = fittedImageSize.height,
                        scale = scale,
                    ),
                )
            }
        }

        if (showZoomIndicator) {
            MetricChip(
                label = "${"%.1f".format(scale)}x",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp),
                containerColor = Color.Black.copy(alpha = 0.42f),
                contentColor = Color.White,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            )
        }
    }
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f

private fun computeFittedImageSize(
    containerSize: IntSize,
    imageWidth: Int,
    imageHeight: Int,
): FittedImageSize {
    if (containerSize == IntSize.Zero || imageWidth <= 0 || imageHeight <= 0) {
        return FittedImageSize(0f, 0f)
    }
    val scale = min(
        containerSize.width / imageWidth.toFloat(),
        containerSize.height / imageHeight.toFloat(),
    )
    return FittedImageSize(
        width = imageWidth * scale,
        height = imageHeight * scale,
    )
}

private fun clampPanOffset(
    offset: Offset,
    scale: Float,
    fittedImageSize: FittedImageSize,
    containerSize: IntSize,
): Offset {
    if (scale <= MIN_SCALE || containerSize == IntSize.Zero) {
        return Offset.Zero
    }
    val scaledWidth = fittedImageSize.width * scale
    val scaledHeight = fittedImageSize.height * scale
    return Offset(
        x = offset.x.coerceIn(
            minimumValue = -((scaledWidth - containerSize.width) / 2f).coerceAtLeast(0f),
            maximumValue = ((scaledWidth - containerSize.width) / 2f).coerceAtLeast(0f),
        ),
        y = offset.y.coerceIn(
            minimumValue = -((scaledHeight - containerSize.height) / 2f).coerceAtLeast(0f),
            maximumValue = ((scaledHeight - containerSize.height) / 2f).coerceAtLeast(0f),
        ),
    )
}

@Composable
private fun rememberZoomableImageBitmap(imagePath: String?) = produceState<ImageBitmap?>(
    initialValue = null,
    key1 = imagePath,
    key2 = imagePath?.let { path -> File(path).takeIf { it.exists() }?.lastModified() },
) {
    value = withContext(Dispatchers.IO) {
        imagePath?.let(::decodeZoomableBitmap)?.asImageBitmap()
    }
}

private fun decodeZoomableBitmap(path: String) =
    BitmapFactory.Options().run {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, this)
        val sampleSize = calculateSampleSize(
            width = outWidth,
            height = outHeight,
            maxDimension = 2_400,
        )
        BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            },
        )
    }

private fun calculateSampleSize(
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

private data class FittedImageSize(
    val width: Float,
    val height: Float,
)
