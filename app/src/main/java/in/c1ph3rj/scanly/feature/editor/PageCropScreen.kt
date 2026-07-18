package `in`.c1ph3rj.scanly.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.editing.CropHandle
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PageCropRoute(
    onNavigateUp: () -> Unit,
    viewModel: PageCropViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PageCropEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                PageCropEvent.Saved -> onNavigateUp()
            }
        }
    }

    PageCropScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onHandleMoved = viewModel::moveHandle,
        onRotateLeft = viewModel::rotateLeft,
        onRotateRight = viewModel::rotateRight,
        onResetCrop = viewModel::resetCrop,
        onDetectDocument = viewModel::detectDocument,
        onApply = viewModel::applyCrop,
    )
}

@Composable
fun PageCropScreen(
    uiState: PageCropUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onHandleMoved: (CropHandle, NormalizedPoint) -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onResetCrop: () -> Unit,
    onDetectDocument: () -> Unit,
    onApply: () -> Unit,
) {
    val actionsEnabled = !uiState.isSaving && !uiState.isDetecting
    val statusLabel = when {
        uiState.isDetecting -> "Detecting"
        uiState.isSaving -> "Processing"
        else -> "Crop"
    }
    val windowSizeInfo = rememberWindowSizeInfo()
    val twoPane = rememberEditorTwoPaneSpec(windowSizeInfo)
    val horizontalPadding = twoPane?.horizontalPadding
        ?: if (windowSizeInfo.isTablet) windowSizeInfo.horizontalPadding else 16.dp
    val contentMaxWidth = twoPane?.contentMaxWidth
        ?: if (windowSizeInfo.isTablet) windowSizeInfo.contentMaxWidth else Dp.Unspecified
    val chromeMaxWidth = twoPane?.chromeMaxWidth
        ?: if (windowSizeInfo.isTablet) 900.dp else Dp.Unspecified

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CropTopBar(
                statusLabel = statusLabel,
                onNavigateUp = onNavigateUp,
                onApply = onApply,
                applyEnabled = actionsEnabled,
                contentMaxWidth = chromeMaxWidth,
                horizontalPadding = horizontalPadding,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (uiState.missingPage || uiState.page == null || uiState.cropQuad == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Page not found.",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            } else if (twoPane != null) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = twoPane.contentMaxWidth)
                        .padding(horizontal = twoPane.horizontalPadding)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(twoPane.paneSpacing),
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(twoPane.previewWeight)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            PageCropEditor(
                                page = uiState.page,
                                cropQuad = uiState.cropQuad,
                                rotationDegrees = uiState.rotationDegrees,
                                selectedFilter = uiState.selectedFilter,
                                onHandleMoved = if (actionsEnabled) onHandleMoved else { _, _ -> },
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (uiState.isDetecting) {
                                DetectingOverlay()
                            }
                        }
                    }
                    EditorToolPanel(
                        title = "Crop tools",
                        subtitle = "Detect edges, rotate, then drag handles. Tap Done to apply.",
                        modifier = Modifier
                            .weight(twoPane.controlsWeight)
                            .fillMaxHeight()
                            .widthIn(max = twoPane.controlsMaxWidth),
                    ) {
                        EditorSideBadge(text = "Page ${uiState.page.pageIndex + 1}")
                        Spacer(modifier = Modifier.height(4.dp))
                        EditorRailAction(
                            icon = Icons.Filled.AutoAwesome,
                            label = if (uiState.isDetecting) "Detecting…" else "AI Detect",
                            subtitle = "Find the document corners",
                            onClick = onDetectDocument,
                            enabled = actionsEnabled || uiState.isDetecting,
                            emphasized = true,
                            leadingContent = if (uiState.isDetecting) {
                                {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        strokeWidth = 2.dp,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                        EditorRailAction(
                            icon = Icons.AutoMirrored.Filled.RotateLeft,
                            label = "Rotate left",
                            onClick = onRotateLeft,
                            enabled = actionsEnabled,
                        )
                        EditorRailAction(
                            icon = Icons.AutoMirrored.Filled.RotateRight,
                            label = "Rotate right",
                            onClick = onRotateRight,
                            enabled = actionsEnabled,
                        )
                        EditorRailAction(
                            icon = Icons.Filled.CropFree,
                            label = "Reset crop",
                            subtitle = "Full frame corners",
                            onClick = onResetCrop,
                            enabled = actionsEnabled,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = contentMaxWidth)
                        .navigationBarsPadding()
                        .padding(horizontal = horizontalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            PageCropEditor(
                                page = uiState.page,
                                cropQuad = uiState.cropQuad,
                                rotationDegrees = uiState.rotationDegrees,
                                selectedFilter = uiState.selectedFilter,
                                onHandleMoved = if (actionsEnabled) onHandleMoved else { _, _ -> },
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (uiState.isDetecting) {
                                DetectingOverlay()
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = if (windowSizeInfo.isTablet) 560.dp else Dp.Unspecified)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CropPageBadge(pageIndex = uiState.page.pageIndex)
                        CropActionRow(
                            onRotateLeft = onRotateLeft,
                            onRotateRight = onRotateRight,
                            onDetectDocument = onDetectDocument,
                            onResetCrop = onResetCrop,
                            enabled = actionsEnabled,
                            isDetecting = uiState.isDetecting,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeWidth = 2.5.dp,
                )
                Text(
                    text = "Detecting document…",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun CropTopBar(
    statusLabel: String,
    onNavigateUp: () -> Unit,
    onApply: () -> Unit,
    applyEnabled: Boolean,
    contentMaxWidth: Dp,
    horizontalPadding: Dp,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = contentMaxWidth)
                .padding(horizontal = horizontalPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ChromeIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateUp,
                containerColor = colorScheme.surfaceContainerHighest,
                contentColor = colorScheme.onSurface,
            )
            MetricChip(
                label = statusLabel,
                containerColor = colorScheme.surfaceContainerHighest,
                contentColor = colorScheme.onSurface,
            )
            ChromeIconButton(
                icon = Icons.Filled.Check,
                contentDescription = "Apply crop",
                onClick = onApply,
                enabled = applyEnabled,
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun CropPageBadge(
    pageIndex: Int,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = CircleShape,
        ) {
            Text(
                text = "Page ${pageIndex + 1}",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun CropActionRow(
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onDetectDocument: () -> Unit,
    onResetCrop: () -> Unit,
    enabled: Boolean,
    isDetecting: Boolean,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        item {
            CropActionButton(
                label = "Left",
                icon = Icons.AutoMirrored.Filled.RotateLeft,
                onClick = onRotateLeft,
                enabled = enabled,
            )
        }
        item {
            CropActionButton(
                label = "Right",
                icon = Icons.AutoMirrored.Filled.RotateRight,
                onClick = onRotateRight,
                enabled = enabled,
            )
        }
        item {
            CropActionButton(
                label = if (isDetecting) "Detecting" else "AI Detect",
                icon = Icons.Filled.AutoAwesome,
                onClick = onDetectDocument,
                enabled = enabled || isDetecting,
                forceDisabled = isDetecting,
            )
        }
        item {
            CropActionButton(
                label = "Reset",
                icon = Icons.Filled.CropFree,
                onClick = onResetCrop,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun CropActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    forceDisabled: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactive = enabled && !forceDisabled
    Surface(
        onClick = onClick,
        enabled = interactive,
        modifier = Modifier.width(104.dp),
        color = colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (forceDisabled && label == "Detecting") {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceContainerHighest,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (interactive) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.28f),
                )
            }
            Text(
                text = label,
                color = if (interactive) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.38f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
internal fun PageCropEditor(
    page: ScanPage,
    cropQuad: DocumentCornerQuad,
    rotationDegrees: Int,
    selectedFilter: PageFilterPreset,
    onHandleMoved: (CropHandle, NormalizedPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    // Full-frame preview so crop handles stay aligned to the raw rotated image.
    val previewBitmap by rememberCropCanvasPreviewBitmap(
        rawImagePath = page.rawImagePath,
        fallbackImagePath = page.processedImagePath,
        rotationDegrees = rotationDegrees,
        selectedFilter = selectedFilter,
    )
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
            contentDescription = "Page crop preview",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
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
                        Rect(
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
                    color = accentColor,
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
                        color = accentColor,
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
                        color = accentColor.copy(alpha = 0.28f),
                        start = lensCenter,
                        end = dragOffset!!,
                        strokeWidth = 1.4.dp.toPx(),
                    )
                }
                CropMagnifier(
                    imageBitmap = imageBitmap,
                    previewRect = previewRect,
                    focusOffset = dragOffset!!,
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
    activeHandle: CropHandle,
    lensSizeDp: androidx.compose.ui.unit.Dp = 96.dp,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary
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
            )
        }

        drawCircle(
            color = LensBorder,
            radius = lensRadius - 1.dp.toPx(),
            style = Stroke(width = 2.dp.toPx()),
        )
        val crosshairArm = 10.dp.toPx()
        drawLine(
            color = accentColor,
            start = Offset(focusIndicator.x - crosshairArm, focusIndicator.y),
            end = Offset(focusIndicator.x + crosshairArm, focusIndicator.y),
            strokeWidth = 1.8.dp.toPx(),
        )
        drawLine(
            color = accentColor,
            start = Offset(focusIndicator.x, focusIndicator.y - crosshairArm),
            end = Offset(focusIndicator.x, focusIndicator.y + crosshairArm),
            strokeWidth = 1.8.dp.toPx(),
        )
        drawCircle(
            color = accentColor.copy(alpha = 0.2f),
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
            color = accentColor,
            radius = 7.5.dp.toPx(),
            center = focusIndicator,
            style = Stroke(width = 2.dp.toPx()),
        )
        val guideLength = 16.dp.toPx()
        when (activeHandle) {
            CropHandle.TOP_LEFT -> drawCornerGuide(focusIndicator, guideLength, 1f, 1f, accentColor)
            CropHandle.TOP_RIGHT -> drawCornerGuide(focusIndicator, guideLength, -1f, 1f, accentColor)
            CropHandle.BOTTOM_RIGHT -> drawCornerGuide(focusIndicator, guideLength, -1f, -1f, accentColor)
            CropHandle.BOTTOM_LEFT -> drawCornerGuide(focusIndicator, guideLength, 1f, -1f, accentColor)
        }
    }
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCornerGuide(
    center: Offset,
    guideLength: Float,
    horizontalDirection: Float,
    verticalDirection: Float,
    accentColor: Color,
) {
    drawLine(
        color = accentColor.copy(alpha = 0.75f),
        start = center,
        end = Offset(
            x = center.x + (guideLength * horizontalDirection),
            y = center.y,
        ),
        strokeWidth = 1.6.dp.toPx(),
    )
    drawLine(
        color = accentColor.copy(alpha = 0.75f),
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

private val LensBackdrop = Color(0xF0121212)
private val LensBorder = Color.White.copy(alpha = 0.88f)
