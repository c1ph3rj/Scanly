package `in`.c1ph3rj.scanly.core.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

@Composable
fun ZoomableImageDialog(
    imagePath: String?,
    title: String,
    onDismiss: () -> Unit,
) {
    var scale by remember(imagePath) { mutableStateOf(1f) }
    var offset by remember(imagePath) { mutableStateOf(Offset.Zero) }
    val zoomActive = scale > 1.02f || offset != Offset.Zero

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
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
                                color = Color.White,
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
                                color = Color.White,
                            )
                        }
                    }

                    else -> {
                        ZoomableImageCanvas(
                            imageBitmap = imageBitmap!!,
                            scale = scale,
                            offset = offset,
                            onScaleChange = { scale = it },
                            onOffsetChange = { offset = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChromeIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        onClick = onDismiss,
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White,
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        MetricChip(
                            label = title,
                            containerColor = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White,
                        )
                    }
                    if (zoomActive) {
                        ChromeIconButton(
                            icon = Icons.Filled.Refresh,
                            contentDescription = "Reset zoom",
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White,
                        )
                    } else {
                        Box(modifier = Modifier.width(44.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImageCanvas(
    imageBitmap: ImageBitmap,
    scale: Float,
    offset: Offset,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Offset) -> Unit,
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

    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .pointerInput(imageBitmap, containerSize, fittedImageSize) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    val currentScale = scale
                    val newScale = (currentScale * gestureZoom).coerceIn(1f, 6f)
                    val unclampedOffset = if (newScale <= 1f) {
                        Offset.Zero
                    } else {
                        val containerCenter = Offset(
                            x = containerSize.width / 2f,
                            y = containerSize.height / 2f,
                        )
                        val centroidRelativeToCenter = centroid - containerCenter
                        val scaleFactor = newScale / currentScale
                        centroidRelativeToCenter + pan - ((centroidRelativeToCenter - offset) * scaleFactor)
                    }
                    onScaleChange(newScale)
                    onOffsetChange(
                        clampPanOffset(
                        offset = unclampedOffset,
                        scale = newScale,
                        fittedImageSize = fittedImageSize,
                        containerSize = containerSize,
                        ),
                    )
                }
            }
            .pointerInput(imageBitmap, containerSize, fittedImageSize) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1f) {
                            onScaleChange(1f)
                            onOffsetChange(Offset.Zero)
                        } else {
                            val targetScale = 2.4f
                            val containerCenter = Offset(
                                x = containerSize.width / 2f,
                                y = containerSize.height / 2f,
                            )
                            val tapRelativeToCenter = tapOffset - containerCenter
                            onScaleChange(targetScale)
                            onOffsetChange(
                                clampPanOffset(
                                offset = Offset(
                                    x = -(tapRelativeToCenter.x * 1.4f),
                                    y = -(tapRelativeToCenter.y * 1.4f),
                                ),
                                scale = targetScale,
                                fittedImageSize = fittedImageSize,
                                containerSize = containerSize,
                                ),
                            )
                        }
                    },
                )
            },
    ) {
        if (containerSize != IntSize.Zero) {
            androidx.compose.foundation.Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(
                        width = with(density) { fittedImageSize.width.toDp() },
                        height = with(density) { fittedImageSize.height.toDp() },
                    )
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }

        MetricChip(
            label = "${"%.1f".format(scale)}x",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
            containerColor = Color.White.copy(alpha = 0.1f),
            contentColor = Color.White,
        )
    }
}

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
    if (scale <= 1f || containerSize == IntSize.Zero) {
        return Offset.Zero
    }
    val scaledWidth = fittedImageSize.width * scale
    val scaledHeight = fittedImageSize.height * scale
    val maxX = ((scaledWidth - containerSize.width) / 2f).coerceAtLeast(0f)
    val maxY = ((scaledHeight - containerSize.height) / 2f).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

@Composable
private fun rememberZoomableImageBitmap(imagePath: String?) = produceState<ImageBitmap?>(
    initialValue = null,
    key1 = imagePath,
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
