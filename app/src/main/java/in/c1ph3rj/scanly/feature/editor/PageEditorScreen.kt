package `in`.c1ph3rj.scanly.feature.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.editing.CropHandle
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PageEditorRoute(
    onNavigateUp: () -> Unit,
    viewModel: PageEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PageEditorEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                PageEditorEvent.Saved -> onNavigateUp()
            }
        }
    }

    PageEditorScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onHandleMoved = viewModel::moveHandle,
        onRotateLeft = viewModel::rotateLeft,
        onRotateRight = viewModel::rotateRight,
        onResetCrop = viewModel::resetCrop,
        onSelectFilter = viewModel::selectFilter,
        onSave = viewModel::saveEdits,
    )
}

@Composable
fun PageEditorScreen(
    uiState: PageEditorUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onHandleMoved: (CropHandle, NormalizedPoint) -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onResetCrop: () -> Unit,
    onSelectFilter: (PageFilterPreset) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = EditorBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            EditorTopBar(
                isSaving = uiState.isSaving,
                onNavigateUp = onNavigateUp,
                onSave = onSave,
            )
        },
    ) { innerPadding ->
        if (uiState.missingPage || uiState.page == null || uiState.cropQuad == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Page not found.",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PageCropEditor(
                    page = uiState.page,
                    cropQuad = uiState.cropQuad,
                    rotationDegrees = uiState.rotationDegrees,
                    selectedFilter = uiState.selectedFilter,
                    onHandleMoved = onHandleMoved,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                EditorPageBadge(
                    pageIndex = uiState.page.pageIndex,
                )
                FilterSelector(
                    selectedFilter = uiState.selectedFilter,
                    onSelectFilter = onSelectFilter,
                )
                EditorActionRow(
                    onRotateLeft = onRotateLeft,
                    onRotateRight = onRotateRight,
                    onResetCrop = onResetCrop,
                )
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    isSaving: Boolean,
    onNavigateUp: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EditorBackground)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ChromeIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onNavigateUp,
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White,
        )
        MetricChip(
            label = if (isSaving) "Saving" else "Editor",
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White,
        )
        ChromeIconButton(
            icon = Icons.Filled.Check,
            contentDescription = "Done",
            onClick = onSave,
            enabled = !isSaving,
            containerColor = AccentGreen,
            contentColor = Color.White,
        )
    }
}

@Composable
private fun PageCropEditor(
    page: ScanPage,
    cropQuad: DocumentCornerQuad,
    rotationDegrees: Int,
    selectedFilter: PageFilterPreset,
    onHandleMoved: (CropHandle, NormalizedPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewBitmap by rememberEditorPreviewBitmap(
        rawImagePath = page.rawImagePath,
        fallbackImagePath = page.processedImagePath,
        rotationDegrees = rotationDegrees,
    )
    val colorFilter = remember(selectedFilter) { selectedFilter.toPreviewColorFilter() }
    val handleSelectionRadiusPx = with(LocalDensity.current) { 24.dp.toPx() }
    val magnifierSizePx = with(LocalDensity.current) { 96.dp.toPx() }
    val magnifierPaddingPx = with(LocalDensity.current) { 12.dp.toPx() }
    val magnifierProtectionRadiusPx = with(LocalDensity.current) { 64.dp.toPx() }
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableStateOf<CropHandle?>(null) }
    var dragOffset by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { editorSize = it },
    ) {
        if (previewBitmap == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Loading preview…",
                    color = Color.White,
                )
            }
            return@Box
        }

        val imageBitmap = previewBitmap!!
        val previewRect = remember(editorSize, imageBitmap) {
            computePreviewRect(
                containerSize = editorSize,
                imageWidth = imageBitmap.width,
                imageHeight = imageBitmap.height,
            )
        }
        val handlePositions = remember(cropQuad, previewRect) {
            cropQuad.toHandleOffsets(previewRect)
        }
        val magnifierPlacement = remember(
            activeHandle,
            dragOffset,
            editorSize,
        ) {
            val handle = activeHandle ?: return@remember null
            val focusPoint = dragOffset ?: handlePositions[handle] ?: return@remember null
            resolveMagnifierPlacement(
                containerSize = editorSize,
                lensSizePx = magnifierSizePx,
                paddingPx = magnifierPaddingPx,
                protectedRadiusPx = magnifierProtectionRadiusPx,
                activeHandle = handle,
                focusOffset = focusPoint,
            )
        }
        val latestPreviewRect = rememberUpdatedState(previewRect)
        val latestHandlePositions = rememberUpdatedState(handlePositions)
        val latestHandleSelectionRadius = rememberUpdatedState(handleSelectionRadiusPx)

        Image(
            bitmap = imageBitmap,
            contentDescription = "Page editor preview",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var draggingHandle: CropHandle? = null
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            draggingHandle = nearestHandle(
                                startOffset = startOffset,
                                handles = latestHandlePositions.value,
                                maxDistance = latestHandleSelectionRadius.value,
                            )
                            activeHandle = draggingHandle
                            dragOffset = draggingHandle?.let { handle ->
                                latestHandlePositions.value[handle]
                            } ?: latestPreviewRect.value.clampOffset(startOffset)
                        },
                        onDragEnd = {
                            draggingHandle = null
                            activeHandle = null
                            dragOffset = null
                        },
                        onDragCancel = {
                            draggingHandle = null
                            activeHandle = null
                            dragOffset = null
                        },
                        onDrag = { change, _ ->
                            val handle = draggingHandle ?: return@detectDragGestures
                            change.consume()
                            val clampedOffset = latestPreviewRect.value.clampOffset(change.position)
                            dragOffset = clampedOffset
                            onHandleMoved(
                                handle,
                                latestPreviewRect.value.toNormalizedPointClamped(clampedOffset),
                            )
                        },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val polygonPath = Path().apply {
                    moveTo(handlePositions[CropHandle.TOP_LEFT]!!.x, handlePositions[CropHandle.TOP_LEFT]!!.y)
                    lineTo(handlePositions[CropHandle.TOP_RIGHT]!!.x, handlePositions[CropHandle.TOP_RIGHT]!!.y)
                    lineTo(handlePositions[CropHandle.BOTTOM_RIGHT]!!.x, handlePositions[CropHandle.BOTTOM_RIGHT]!!.y)
                    lineTo(handlePositions[CropHandle.BOTTOM_LEFT]!!.x, handlePositions[CropHandle.BOTTOM_LEFT]!!.y)
                    close()
                }

                val dimPath = Path().apply {
                    addRect(
                        androidx.compose.ui.geometry.Rect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = size.height,
                        ),
                    )
                    addPath(polygonPath)
                    fillType = PathFillType.EvenOdd
                }
                drawPath(
                    path = dimPath,
                    color = Color.Black.copy(alpha = 0.48f),
                    style = Fill,
                )
                drawPath(
                    path = polygonPath,
                    color = AccentGreen,
                    style = Stroke(width = 2.5.dp.toPx()),
                )

                handlePositions.forEach { (handle, point) ->
                    val isActiveHandle = handle == activeHandle
                    drawCircle(
                        color = Color.White,
                        radius = if (isActiveHandle) 8.5.dp.toPx() else 7.dp.toPx(),
                        center = point,
                    )
                    drawCircle(
                        color = AccentGreen,
                        radius = if (isActiveHandle) 12.dp.toPx() else 10.dp.toPx(),
                        center = point,
                        style = Stroke(width = if (isActiveHandle) 2.5.dp.toPx() else 2.dp.toPx()),
                    )
                }
            }

            if (activeHandle != null && dragOffset != null && magnifierPlacement != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lensCenter = Offset(
                        x = magnifierPlacement.left + (magnifierSizePx / 2f),
                        y = magnifierPlacement.top + (magnifierSizePx / 2f),
                    )
                    drawLine(
                        color = AccentGreen.copy(alpha = 0.28f),
                        start = lensCenter,
                        end = dragOffset!!,
                        strokeWidth = 1.4.dp.toPx(),
                    )
                }
                CropMagnifier(
                    imageBitmap = imageBitmap,
                    previewRect = previewRect,
                    focusOffset = dragOffset!!,
                    colorFilter = colorFilter,
                    activeHandle = activeHandle!!,
                    lensSizeDp = 96.dp,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = magnifierPlacement.left.roundToInt(),
                                y = magnifierPlacement.top.roundToInt(),
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun CropMagnifier(
    imageBitmap: ImageBitmap,
    previewRect: PreviewRect,
    focusOffset: Offset,
    colorFilter: ColorFilter?,
    activeHandle: CropHandle,
    lensSizeDp: androidx.compose.ui.unit.Dp = 96.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.size(lensSizeDp),
    ) {
        val lensRadius = size.minDimension / 2f
        val clippedPath = Path().apply {
            addOval(Rect(0f, 0f, size.width, size.height))
        }
        val normalizedPoint = previewRect.toNormalizedPointClamped(focusOffset)
        val zoom = 3.2f
        val sourceWidth = (imageBitmap.width / zoom).roundToInt().coerceIn(1, imageBitmap.width)
        val sourceHeight = (imageBitmap.height / zoom).roundToInt().coerceIn(1, imageBitmap.height)
        val focusPixelX = (normalizedPoint.x * (imageBitmap.width - 1)).roundToInt()
            .coerceIn(0, imageBitmap.width - 1)
        val focusPixelY = (normalizedPoint.y * (imageBitmap.height - 1)).roundToInt()
            .coerceIn(0, imageBitmap.height - 1)
        val sourceLeft = (focusPixelX - (sourceWidth / 2)).coerceIn(0, imageBitmap.width - sourceWidth)
        val sourceTop = (focusPixelY - (sourceHeight / 2)).coerceIn(0, imageBitmap.height - sourceHeight)
        val focusIndicator = Offset(
            x = (((focusPixelX - sourceLeft).toFloat() / sourceWidth.toFloat()) * size.width)
                .coerceIn(0f, size.width),
            y = (((focusPixelY - sourceTop).toFloat() / sourceHeight.toFloat()) * size.height)
                .coerceIn(0f, size.height),
        )

        clipPath(clippedPath) {
            drawCircle(
                color = LensBackdrop,
                radius = lensRadius,
            )
            drawImage(
                image = imageBitmap,
                srcOffset = IntOffset(sourceLeft, sourceTop),
                srcSize = IntSize(sourceWidth, sourceHeight),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                colorFilter = colorFilter,
            )
        }

        drawCircle(
            color = LensBorder,
            radius = lensRadius - 1.dp.toPx(),
            style = Stroke(width = 2.dp.toPx()),
        )
        val crosshairArm = 10.dp.toPx()
        drawLine(
            color = AccentGreen,
            start = Offset(focusIndicator.x - crosshairArm, focusIndicator.y),
            end = Offset(focusIndicator.x + crosshairArm, focusIndicator.y),
            strokeWidth = 1.8.dp.toPx(),
        )
        drawLine(
            color = AccentGreen,
            start = Offset(focusIndicator.x, focusIndicator.y - crosshairArm),
            end = Offset(focusIndicator.x, focusIndicator.y + crosshairArm),
            strokeWidth = 1.8.dp.toPx(),
        )
        drawCircle(
            color = AccentGreen.copy(alpha = 0.2f),
            radius = 14.dp.toPx(),
            center = focusIndicator,
            style = Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            color = Color.White,
            radius = 5.5.dp.toPx(),
            center = focusIndicator,
        )
        drawCircle(
            color = AccentGreen,
            radius = 7.5.dp.toPx(),
            center = focusIndicator,
            style = Stroke(width = 2.dp.toPx()),
        )
        val guideLength = 16.dp.toPx()
        when (activeHandle) {
            CropHandle.TOP_LEFT -> drawCornerGuide(focusIndicator, guideLength, 1f, 1f)
            CropHandle.TOP_RIGHT -> drawCornerGuide(focusIndicator, guideLength, -1f, 1f)
            CropHandle.BOTTOM_RIGHT -> drawCornerGuide(focusIndicator, guideLength, -1f, -1f)
            CropHandle.BOTTOM_LEFT -> drawCornerGuide(focusIndicator, guideLength, 1f, -1f)
        }
    }
}

@Composable
private fun EditorPageBadge(
    pageIndex: Int,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            color = Color(0xFF1E1E1E),
            shape = CircleShape,
        ) {
            Text(
                text = "Page ${pageIndex + 1}",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun FilterSelector(
    selectedFilter: PageFilterPreset,
    onSelectFilter: (PageFilterPreset) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(PageFilterPreset.entries) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onSelectFilter(filter) },
                label = {
                    Text(text = filter.toDisplayLabel())
                },
            )
        }
    }
}

@Composable
private fun EditorActionRow(
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onResetCrop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        EditorActionButton(label = "Left", icon = Icons.Filled.RotateLeft, onClick = onRotateLeft)
        EditorActionButton(label = "Right", icon = Icons.Filled.RotateRight, onClick = onRotateRight)
        EditorActionButton(label = "Reset", icon = Icons.Filled.CropFree, onClick = onResetCrop)
    }
}

@Composable
private fun EditorActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(104.dp)
            .clickable(onClick = onClick),
        color = Color(0xFF181818),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentGreen,
            )
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun rememberEditorPreviewBitmap(
    rawImagePath: String?,
    fallbackImagePath: String?,
    rotationDegrees: Int,
): androidx.compose.runtime.State<ImageBitmap?> = produceState<ImageBitmap?>(
    initialValue = null,
    key1 = rawImagePath,
    key2 = fallbackImagePath,
    key3 = rotationDegrees,
) {
    value = withContext(Dispatchers.IO) {
        val sourcePath = rawImagePath ?: fallbackImagePath ?: return@withContext null
        decodeEditorBitmap(
            path = sourcePath,
            userRotationDegrees = rotationDegrees,
        )?.asImageBitmap()
    }
}

private fun decodeEditorBitmap(
    path: String,
    userRotationDegrees: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sampleSize = calculatePreviewSampleSize(bounds.outWidth, bounds.outHeight, 1_600)
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
    return rotateBitmap(oriented, normalizeRotation(userRotationDegrees))
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

private fun rotateBitmap(
    bitmap: Bitmap,
    rotationDegrees: Int,
): Bitmap {
    val normalizedRotation = normalizeRotation(rotationDegrees)
    if (normalizedRotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(normalizedRotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun computePreviewRect(
    containerSize: IntSize,
    imageWidth: Int,
    imageHeight: Int,
): PreviewRect {
    val scale = min(
        containerSize.width / imageWidth.toFloat(),
        containerSize.height / imageHeight.toFloat(),
    )
    val previewWidth = imageWidth * scale
    val previewHeight = imageHeight * scale
    return PreviewRect(
        left = (containerSize.width - previewWidth) / 2f,
        top = (containerSize.height - previewHeight) / 2f,
        width = previewWidth,
        height = previewHeight,
    )
}

private fun DocumentCornerQuad.toHandleOffsets(previewRect: PreviewRect): Map<CropHandle, Offset> = mapOf(
    CropHandle.TOP_LEFT to previewRect.toOffset(topLeft),
    CropHandle.TOP_RIGHT to previewRect.toOffset(topRight),
    CropHandle.BOTTOM_RIGHT to previewRect.toOffset(bottomRight),
    CropHandle.BOTTOM_LEFT to previewRect.toOffset(bottomLeft),
)

private fun PreviewRect.toOffset(point: NormalizedPoint): Offset = Offset(
    x = left + (point.x * width),
    y = top + (point.y * height),
)

private fun PreviewRect.clampOffset(offset: Offset): Offset = Offset(
    x = offset.x.coerceIn(left, left + width),
    y = offset.y.coerceIn(top, top + height),
)

private fun PreviewRect.toNormalizedPointClamped(offset: Offset): NormalizedPoint {
    val clampedOffset = clampOffset(offset)
    return NormalizedPoint(
        x = ((clampedOffset.x - left) / width).coerceIn(0f, 1f),
        y = ((clampedOffset.y - top) / height).coerceIn(0f, 1f),
    )
}

private fun nearestHandle(
    startOffset: Offset,
    handles: Map<CropHandle, Offset>,
    maxDistance: Float,
): CropHandle? = handles.minByOrNull { (_, offset) ->
    distance(startOffset, offset)
}?.takeIf { (_, offset) ->
    distance(startOffset, offset) <= maxDistance
}?.key

private fun distance(first: Offset, second: Offset): Float {
    val deltaX = first.x - second.x
    val deltaY = first.y - second.y
    return kotlin.math.sqrt((deltaX * deltaX) + (deltaY * deltaY))
}

private fun PageFilterPreset.toPreviewColorFilter(): ColorFilter? = when (this) {
    PageFilterPreset.ORIGINAL -> null
    PageFilterPreset.ENHANCED_COLOR -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                1.1f, 0f, 0f, 0f, 8f,
                0f, 1.1f, 0f, 0f, 8f,
                0f, 0f, 1.1f, 0f, 8f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )

    PageFilterPreset.GRAYSCALE -> {
        val matrix = ColorMatrix()
        matrix.setToSaturation(0f)
        ColorFilter.colorMatrix(matrix)
    }

    PageFilterPreset.BLACK_AND_WHITE -> {
        val matrix = ColorMatrix().apply {
            setToSaturation(0f)
            values[0] = 1.7f
            values[6] = 1.7f
            values[12] = 1.7f
            values[4] = -120f
            values[9] = -120f
            values[14] = -120f
        }
        ColorFilter.colorMatrix(matrix)
    }
}

private fun PageFilterPreset.toDisplayLabel(): String = when (this) {
    PageFilterPreset.ORIGINAL -> "Original"
    PageFilterPreset.ENHANCED_COLOR -> "Enhanced"
    PageFilterPreset.GRAYSCALE -> "Grayscale"
    PageFilterPreset.BLACK_AND_WHITE -> "B&W"
}

private fun normalizeRotation(rotationDegrees: Int): Int {
    val normalized = rotationDegrees % 360
    return if (normalized < 0) normalized + 360 else normalized
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCornerGuide(
    center: Offset,
    guideLength: Float,
    horizontalDirection: Float,
    verticalDirection: Float,
) {
    drawLine(
        color = AccentGreen.copy(alpha = 0.75f),
        start = center,
        end = Offset(
            x = center.x + (guideLength * horizontalDirection),
            y = center.y,
        ),
        strokeWidth = 1.6.dp.toPx(),
    )
    drawLine(
        color = AccentGreen.copy(alpha = 0.75f),
        start = center,
        end = Offset(
            x = center.x,
            y = center.y + (guideLength * verticalDirection),
        ),
        strokeWidth = 1.6.dp.toPx(),
    )
}

private fun resolveMagnifierPlacement(
    containerSize: IntSize,
    lensSizePx: Float,
    paddingPx: Float,
    protectedRadiusPx: Float,
    activeHandle: CropHandle,
    focusOffset: Offset,
): MagnifierPlacement {
    val left = paddingPx
    val top = paddingPx
    val right = (containerSize.width - lensSizePx - paddingPx).coerceAtLeast(paddingPx)
    val bottom = (containerSize.height - lensSizePx - paddingPx).coerceAtLeast(paddingPx)

    val topLeft = MagnifierPlacement(left = left, top = top)
    val topRight = MagnifierPlacement(left = right, top = top)
    val bottomLeft = MagnifierPlacement(left = left, top = bottom)
    val bottomRight = MagnifierPlacement(left = right, top = bottom)

    val candidates = when (activeHandle) {
        CropHandle.TOP_LEFT -> listOf(bottomRight, topRight, bottomLeft, topLeft)
        CropHandle.TOP_RIGHT -> listOf(bottomLeft, topLeft, bottomRight, topRight)
        CropHandle.BOTTOM_RIGHT -> listOf(topLeft, bottomLeft, topRight, bottomRight)
        CropHandle.BOTTOM_LEFT -> listOf(topRight, bottomRight, topLeft, bottomLeft)
    }

    val protectedRect = Rect(
        left = focusOffset.x - protectedRadiusPx,
        top = focusOffset.y - protectedRadiusPx,
        right = focusOffset.x + protectedRadiusPx,
        bottom = focusOffset.y + protectedRadiusPx,
    )

    return candidates.firstOrNull { placement ->
        !placement.toRect(lensSizePx).overlaps(protectedRect)
    } ?: candidates.first()
}

private data class PreviewRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

private data class MagnifierPlacement(
    val left: Float,
    val top: Float,
) {
    fun toRect(sizePx: Float): Rect = Rect(
        left = left,
        top = top,
        right = left + sizePx,
        bottom = top + sizePx,
    )
}

private val EditorBackground = Color(0xFF050505)
private val AccentGreen = Color(0xFF0AAE78)
private val LensBackdrop = Color(0xF0121212)
private val LensBorder = Color.White.copy(alpha = 0.88f)
