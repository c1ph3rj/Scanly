package `in`.c1ph3rj.scanly.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PageEditorRoute(
    onNavigateUp: () -> Unit,
    onOpenCrop: (pageId: String) -> Unit,
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
        onOpenCrop = {
            uiState.page?.let { page ->
                onOpenCrop(page.id)
            }
        },
        onSelectFilter = viewModel::selectFilter,
        onApplyFilterToAllPagesChange = viewModel::setApplyFilterToAllPages,
        onFilterAdjustmentsChange = viewModel::updateFilterAdjustments,
        onResetFilterAdjustments = viewModel::resetFilterAdjustments,
        onSave = viewModel::saveEdits,
        onRetakePage = {
            uiState.page?.let { page ->
                onRetakePage(page.documentId, page.id)
            }
        },
        onDeletePage = viewModel::deletePage,
    )
}

@Composable
fun PageEditorScreen(
    uiState: PageEditorUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onOpenCrop: () -> Unit,
    onSelectFilter: (PageFilterPreset) -> Unit,
    onApplyFilterToAllPagesChange: (Boolean) -> Unit,
    onFilterAdjustmentsChange: (PageFilterAdjustments) -> Unit,
    onResetFilterAdjustments: () -> Unit,
    onSave: () -> Unit,
    onRetakePage: () -> Unit,
    onDeletePage: () -> Unit,
) {
    var filtersVisible by remember { mutableStateOf(false) }
    var customizeVisible by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    val showBulkApplyLoader = uiState.isSaving && uiState.applyFilterToAllPages
    val statusLabel = when {
        showBulkApplyLoader -> "Processing"
        uiState.isSaving -> "Processing"
        else -> "Editor"
    }

    if (filtersVisible && uiState.page != null && uiState.cropQuad != null) {
        FilterPickerScreen(
            page = uiState.page,
            cropQuad = uiState.cropQuad,
            rotationDegrees = uiState.rotationDegrees,
            selectedFilter = uiState.selectedFilter,
            filterAdjustments = uiState.filterAdjustments,
            applyFilterToAllPages = uiState.applyFilterToAllPages,
            isSaving = uiState.isSaving,
            onSelectFilter = onSelectFilter,
            onApplyFilterToAllPagesChange = onApplyFilterToAllPagesChange,
            onDone = { filtersVisible = false },
            onNavigateUp = { filtersVisible = false },
        )
        return
    }

    if (customizeVisible && uiState.page != null && uiState.cropQuad != null) {
        FilterCustomizeScreen(
            page = uiState.page,
            cropQuad = uiState.cropQuad,
            rotationDegrees = uiState.rotationDegrees,
            selectedFilter = uiState.selectedFilter,
            filterAdjustments = uiState.filterAdjustments,
            onAdjustmentsChange = onFilterAdjustmentsChange,
            onReset = onResetFilterAdjustments,
            onDone = { customizeVisible = false },
            onNavigateUp = { customizeVisible = false },
        )
        return
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
            val twoPane = rememberEditorTwoPaneSpec(windowSizeInfo)

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
            } else if (twoPane != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter,
                ) {
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
                            PageEditorPreview(
                                page = uiState.page,
                                cropQuad = uiState.cropQuad,
                                rotationDegrees = uiState.rotationDegrees,
                                selectedFilter = uiState.selectedFilter,
                                filterAdjustments = uiState.filterAdjustments,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        EditorToolPanel(
                            title = "Edit tools",
                            subtitle = "Crop, filter, and adjust this page. Save with Done.",
                            modifier = Modifier
                                .weight(twoPane.controlsWeight)
                                .fillMaxHeight()
                                .widthIn(max = twoPane.controlsMaxWidth),
                        ) {
                            EditorSideBadge(text = "Page ${uiState.page.pageIndex + 1}")
                            Spacer(modifier = Modifier.height(4.dp))
                            EditorRailAction(
                                icon = Icons.Filled.Crop,
                                label = "Crop",
                                subtitle = "AI detect, rotate, handles",
                                onClick = onOpenCrop,
                                enabled = !uiState.isSaving,
                            )
                            EditorRailAction(
                                icon = Icons.Filled.Tune,
                                label = "Filters",
                                subtitle = uiState.selectedFilter.toEditorLabel(),
                                onClick = { filtersVisible = true },
                                enabled = !uiState.isSaving,
                            )
                            EditorRailAction(
                                icon = Icons.Filled.Tonality,
                                label = if (uiState.filterAdjustments.isDefault) {
                                    "Adjust"
                                } else {
                                    "Adjusted"
                                },
                                subtitle = "Brightness, contrast, more",
                                onClick = { customizeVisible = true },
                                enabled = !uiState.isSaving,
                                emphasized = !uiState.filterAdjustments.isDefault,
                            )
                            EditorRailAction(
                                icon = Icons.Filled.Refresh,
                                label = "Retake",
                                subtitle = "Capture this page again",
                                onClick = onRetakePage,
                                enabled = !uiState.isSaving,
                            )
                            EditorRailAction(
                                icon = Icons.Filled.DeleteOutline,
                                label = "Delete page",
                                subtitle = "Remove from document",
                                onClick = { deleteDialogVisible = true },
                                enabled = !uiState.isSaving,
                                destructive = true,
                            )
                        }
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
                        PageEditorPreview(
                            page = uiState.page,
                            cropQuad = uiState.cropQuad,
                            rotationDegrees = uiState.rotationDegrees,
                            selectedFilter = uiState.selectedFilter,
                            filterAdjustments = uiState.filterAdjustments,
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
                            onOpenCrop = onOpenCrop,
                            onOpenFilters = { filtersVisible = true },
                            onOpenCustomize = { customizeVisible = true },
                            onRetake = onRetakePage,
                            onDelete = { deleteDialogVisible = true },
                            enabled = !uiState.isSaving,
                            customizeActive = !uiState.filterAdjustments.isDefault,
                        )
                    }
                }
            }
        }

        if (showBulkApplyLoader) {
            BulkFilterApplyOverlay()
        }
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
private fun PageEditorPreview(
    page: ScanPage,
    cropQuad: DocumentCornerQuad,
    rotationDegrees: Int,
    selectedFilter: PageFilterPreset,
    filterAdjustments: PageFilterAdjustments,
    modifier: Modifier = Modifier,
) {
    val previewBitmap by rememberEditorPreviewBitmap(
        rawImagePath = page.rawImagePath,
        fallbackImagePath = page.processedImagePath,
        rotationDegrees = rotationDegrees,
        selectedFilter = selectedFilter,
        cropQuad = cropQuad,
        filterAdjustments = filterAdjustments,
    )

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (previewBitmap == null) {
            Text(
                text = "Loading preview…",
                color = Color.White,
            )
        } else {
            Image(
                bitmap = previewBitmap!!,
                contentDescription = "Page editor preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
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
private fun EditorActionRow(
    onOpenCrop: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenCustomize: () -> Unit,
    onRetake: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
    customizeActive: Boolean,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        item {
            EditorActionButton(label = "Crop", icon = Icons.Filled.Crop, onClick = onOpenCrop, enabled = enabled)
        }
        item {
            EditorActionButton(label = "Filters", icon = Icons.Filled.Tune, onClick = onOpenFilters, enabled = enabled)
        }
        item {
            EditorActionButton(
                label = if (customizeActive) "Adjusted" else "Adjust",
                icon = Icons.Filled.Tonality,
                onClick = onOpenCustomize,
                enabled = enabled,
            )
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

private fun PageFilterPreset.toEditorLabel(): String = when (this) {
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
