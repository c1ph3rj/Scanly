package `in`.c1ph3rj.scanly.feature.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.core.editing.CropHandle
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint
import `in`.c1ph3rj.scanly.core.processing.OpenCvPageFilterProcessor
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PageEditorRoute(
    onNavigateUp: () -> Unit,
    onRetakePage: (documentId: String, pageId: String) -> Unit,
    viewModel: PageEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PageEditorEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                PageEditorEvent.Saved -> onNavigateUp()
                PageEditorEvent.PageDeleted -> onNavigateUp()
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
        onApplyFilterToAllPagesChange = viewModel::setApplyFilterToAllPages,
        onSave = viewModel::saveEdits,
        onRetakePage = {
            uiState.page?.let { page ->
                onRetakePage(page.documentId, page.id)
            }
        },
        onDeletePage = viewModel::deletePage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onApplyFilterToAllPagesChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRetakePage: () -> Unit,
    onDeletePage: () -> Unit,
) {
    var filterSheetVisible by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    val showBulkApplyLoader = uiState.isSaving && uiState.applyFilterToAllPages
    val statusLabel = when {
        showBulkApplyLoader -> "Processing"
        uiState.isSaving -> "Processing"
        else -> "Editor"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                EditorTopBar(
                    statusLabel = statusLabel,
                    onNavigateUp = onNavigateUp,
                    onSave = onSave,
                    isSaving = uiState.isSaving,
                )
            },
        ) { innerPadding ->
            val windowSizeInfo = rememberWindowSizeInfo()

            if (uiState.missingPage || uiState.page == null || uiState.cropQuad == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Page not found.",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            } else if (windowSizeInfo.useTabletLandscapeLayout) {
                // Tablet landscape: side-by-side Row — crop canvas left, controls right
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PageCropEditor(
                        page = uiState.page,
                        cropQuad = uiState.cropQuad,
                        rotationDegrees = uiState.rotationDegrees,
                        selectedFilter = uiState.selectedFilter,
                        onHandleMoved = onHandleMoved,
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        EditorPageBadge(pageIndex = uiState.page.pageIndex)
                        EditorActionRow(
                            onRotateLeft = onRotateLeft,
                            onRotateRight = onRotateRight,
                            onResetCrop = onResetCrop,
                            onOpenFilters = { filterSheetVisible = true },
                            onRetake = onRetakePage,
                            onDelete = { deleteDialogVisible = true },
                            enabled = !uiState.isSaving,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .navigationBarsPadding(),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        PageCropEditor(
                            page = uiState.page,
                            cropQuad = uiState.cropQuad,
                            rotationDegrees = uiState.rotationDegrees,
                            selectedFilter = uiState.selectedFilter,
                            onHandleMoved = onHandleMoved,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        EditorPageBadge(pageIndex = uiState.page.pageIndex)
                        EditorActionRow(
                            onRotateLeft = onRotateLeft,
                            onRotateRight = onRotateRight,
                            onResetCrop = onResetCrop,
                            onOpenFilters = { filterSheetVisible = true },
                            onRetake = onRetakePage,
                            onDelete = { deleteDialogVisible = true },
                            enabled = !uiState.isSaving,
                        )
                    }
                }
            }
        }

        if (showBulkApplyLoader) {
            BulkFilterApplyOverlay()
        }
    }

    if (filterSheetVisible && uiState.page != null) {
        FilterOptionsSheet(
            uiState = uiState,
            onDismiss = { filterSheetVisible = false },
            onSelectFilter = onSelectFilter,
            onApplyFilterToAllPagesChange = onApplyFilterToAllPagesChange,
        )
    }

    if (deleteDialogVisible && uiState.page != null) {
        ScanlyConfirmDialog(
            title = "Delete page",
            text = "Page ${uiState.page.pageIndex + 1} will be removed from this document.",
            confirmLabel = "Delete",
            onDismiss = { deleteDialogVisible = false },
            onConfirm = {
                deleteDialogVisible = false
                onDeletePage()
            },
            confirmDestructive = true,
        )
    }
}

@Composable
private fun EditorTopBar(
    statusLabel: String,
    onNavigateUp: () -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
            contentDescription = "Done",
            onClick = onSave,
            enabled = !isSaving,
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary,
        )
    }
}

@Composable
private fun FilterScopeOption(
    applyToAllPages: Boolean,
    enabled: Boolean,
    onApplyToAllPagesChange: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                onApplyToAllPagesChange(!applyToAllPages)
            },
        color = colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(
            width = 1.dp,
            color = if (applyToAllPages) colorScheme.primary else colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Apply Filter To All Pages",
                    color = colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "Filter only. Crop and rotation stay per page.",
                    color = colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = applyToAllPages,
                onCheckedChange = onApplyToAllPagesChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorScheme.onPrimary,
                    checkedTrackColor = colorScheme.primary,
                    checkedBorderColor = colorScheme.primary,
                    uncheckedThumbColor = colorScheme.outline,
                    uncheckedTrackColor = colorScheme.surfaceContainerHighest,
                    uncheckedBorderColor = colorScheme.outline,
                    disabledCheckedThumbColor = colorScheme.onPrimary.copy(alpha = 0.72f),
                    disabledCheckedTrackColor = colorScheme.primary.copy(alpha = 0.42f),
                    disabledCheckedBorderColor = colorScheme.primary.copy(alpha = 0.42f),
                    disabledUncheckedThumbColor = colorScheme.onSurface.copy(alpha = 0.38f),
                    disabledUncheckedTrackColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                    disabledUncheckedBorderColor = colorScheme.outline.copy(alpha = 0.5f),
                ),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FilterOptionsSheet(
    uiState: PageEditorUiState,
    onDismiss: () -> Unit,
    onSelectFilter: (PageFilterPreset) -> Unit,
    onApplyFilterToAllPagesChange: (Boolean) -> Unit,
) {
    val page = uiState.page ?: return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Filters",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Choose the document enhancement without shrinking the crop canvas.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FilterScopeOption(
                applyToAllPages = uiState.applyFilterToAllPages,
                enabled = !uiState.isSaving,
                onApplyToAllPagesChange = onApplyFilterToAllPagesChange,
            )
            FilterSelector(
                selectedFilter = uiState.selectedFilter,
                rawImagePath = page.rawImagePath,
                fallbackImagePath = page.processedImagePath,
                rotationDegrees = uiState.rotationDegrees,
                onSelectFilter = onSelectFilter,
            )
        }
    }
}

@Composable
private fun BulkFilterApplyOverlay() {
    val interactionSource = remember { MutableInteractionSource() }
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceContainerHighest,
                )
                Text(
                    text = "Processing image data for all pages",
                    color = colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Please wait while Scanly adapts the filter and updates the whole document.",
                    color = colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
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
    val accentColor = MaterialTheme.colorScheme.primary
    val previewBitmap by rememberEditorPreviewBitmap(
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
            contentDescription = "Page editor preview",
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

@Composable
private fun EditorPageBadge(
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
private fun FilterSelector(
    selectedFilter: PageFilterPreset,
    rawImagePath: String?,
    fallbackImagePath: String?,
    rotationDegrees: Int,
    onSelectFilter: (PageFilterPreset) -> Unit,
    vertical: Boolean = false,
) {
    val previewState by rememberFilterPreviewBitmaps(
        rawImagePath = rawImagePath,
        fallbackImagePath = fallbackImagePath,
        rotationDegrees = rotationDegrees,
    )
    val listState = rememberLazyListState()

    LaunchedEffect(selectedFilter) {
        if (!vertical) {
            val targetIndex = PageFilterPreset.entries.indexOf(selectedFilter)
            if (targetIndex >= 0) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (previewState.isLoading) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Analyzing the page to tune each filter.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        if (vertical) {
            // Tablet sidebar: two-column grid using regular Column rows (parent is scrollable)
            val filterRows = PageFilterPreset.entries.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filterRows.forEach { rowFilters ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rowFilters.forEach { filter ->
                            FilterItem(
                                filter = filter,
                                isSelected = selectedFilter == filter,
                                preview = previewState.previews[filter],
                                onSelect = { onSelectFilter(filter) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowFilters.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            // Phone / portrait: horizontal scrolling LazyRow
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(PageFilterPreset.entries, key = { it.storageValue }) { filter ->
                    FilterItem(
                        filter = filter,
                        isSelected = selectedFilter == filter,
                        preview = previewState.previews[filter],
                        onSelect = { onSelectFilter(filter) },
                        modifier = Modifier.width(112.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterItem(
    filter: PageFilterPreset,
    isSelected: Boolean,
    preview: ImageBitmap?,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onSelect,
        modifier = modifier,
        color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (preview == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = filter.shortLabel(),
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    } else {
                        Image(
                            bitmap = preview,
                            contentDescription = "${filter.toDisplayLabel()} filter preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .background(colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected filter",
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Text(
                text = filter.toDisplayLabel(),
                color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun EditorActionRow(
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onResetCrop: () -> Unit,
    onOpenFilters: () -> Unit,
    onRetake: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        item {
            EditorActionButton(label = "Left", icon = Icons.AutoMirrored.Filled.RotateLeft, onClick = onRotateLeft, enabled = enabled)
        }
        item {
            EditorActionButton(label = "Right", icon = Icons.AutoMirrored.Filled.RotateRight, onClick = onRotateRight, enabled = enabled)
        }
        item {
            EditorActionButton(label = "Reset", icon = Icons.Filled.CropFree, onClick = onResetCrop, enabled = enabled)
        }
        item {
            EditorActionButton(label = "Filters", icon = Icons.Filled.Tune, onClick = onOpenFilters, enabled = enabled)
        }
        item {
            EditorActionButton(label = "Retake", icon = Icons.Filled.Refresh, onClick = onRetake, enabled = enabled)
        }
        item {
            EditorActionButton(
                label = "Delete",
                icon = Icons.Filled.DeleteOutline,
                onClick = onDelete,
                enabled = enabled,
                contentColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EditorActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentColor: Color? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val resolvedContentColor = contentColor ?: colorScheme.primary
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(104.dp),
        color = colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) resolvedContentColor else colorScheme.onSurface.copy(alpha = 0.28f),
            )
            Text(
                text = label,
                color = if (enabled) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.38f),
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
    selectedFilter: PageFilterPreset,
): androidx.compose.runtime.State<ImageBitmap?> = produceState<ImageBitmap?>(
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
            userRotationDegrees = rotationDegrees,
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

private fun decodeEditorBitmap(
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
return rotateBitmap(oriented, normalizeRotation(userRotationDegrees))
}

private data class FilterPreviewState(
    val isLoading: Boolean,
    val previews: Map<PageFilterPreset, ImageBitmap>,
)

@Composable
private fun rememberFilterPreviewBitmaps(
    rawImagePath: String?,
    fallbackImagePath: String?,
    rotationDegrees: Int,
): androidx.compose.runtime.State<FilterPreviewState> = produceState(
    initialValue = FilterPreviewState(
        isLoading = true,
        previews = emptyMap(),
    ),
    rawImagePath,
    fallbackImagePath,
    rotationDegrees,
) {
    value = withContext(Dispatchers.Default) {
        val sourcePath = rawImagePath ?: fallbackImagePath ?: return@withContext FilterPreviewState(
            isLoading = false,
            previews = emptyMap(),
        )
        val baseBitmap = decodeEditorBitmap(
            path = sourcePath,
            userRotationDegrees = rotationDegrees,
            maxDimension = 360,
        ) ?: return@withContext FilterPreviewState(
            isLoading = false,
            previews = emptyMap(),
        )
        val previewBitmap = createFilterPreviewSource(baseBitmap)
        if (previewBitmap !== baseBitmap) {
            baseBitmap.recycle()
        }

        try {
            val previews = OpenCvPageFilterProcessor
                .applyAll(previewBitmap)
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

private fun PageFilterPreset.toDisplayLabel(): String = when (this) {
    PageFilterPreset.ORIGINAL -> "Original"
    PageFilterPreset.AUTO -> "Auto"
    PageFilterPreset.ENHANCED_COLOR -> "Color"
    PageFilterPreset.GRAYSCALE -> "Grayscale"
    PageFilterPreset.BLACK_AND_WHITE -> "B&W"
    PageFilterPreset.CLEAN -> "Clean Paper"
    PageFilterPreset.SHADOW_REDUCTION -> "Shadow Reduce"
    PageFilterPreset.MAGIC_COLOR -> "Magic"
    PageFilterPreset.RECEIPT -> "Receipt"
    PageFilterPreset.SOFT_BLACK_AND_WHITE -> "Text Enhance"
}

private fun PageFilterPreset.shortLabel(): String = when (this) {
    PageFilterPreset.ORIGINAL -> "O"
    PageFilterPreset.AUTO -> "A"
    PageFilterPreset.ENHANCED_COLOR -> "C"
    PageFilterPreset.GRAYSCALE -> "G"
    PageFilterPreset.BLACK_AND_WHITE -> "B&W"
    PageFilterPreset.CLEAN -> "CP"
    PageFilterPreset.SHADOW_REDUCTION -> "SH"
    PageFilterPreset.MAGIC_COLOR -> "M"
    PageFilterPreset.RECEIPT -> "R"
    PageFilterPreset.SOFT_BLACK_AND_WHITE -> "TXT"
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
