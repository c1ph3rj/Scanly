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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.WindowSizeInfo
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.core.editing.CropHandle
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint
import `in`.c1ph3rj.scanly.core.processing.OpenCvPageFilterProcessor
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private enum class FilterPanelTab {
    LOOKS,
    ADJUST,
}

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
        onFilterAdjustmentsChange = viewModel::updateFilterAdjustments,
        onResetFilterAdjustments = viewModel::resetFilterAdjustments,
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
    onFilterAdjustmentsChange: (PageFilterAdjustments) -> Unit,
    onResetFilterAdjustments: () -> Unit,
    onApplyFilterToAllPagesChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRetakePage: () -> Unit,
    onDeletePage: () -> Unit,
) {
    var filterPanelVisible by remember { mutableStateOf(false) }
    var filterPanelTab by remember { mutableStateOf(FilterPanelTab.LOOKS) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    val showBulkApplyLoader = uiState.isSaving && uiState.applyFilterToAllPages
    val statusLabel = when {
        showBulkApplyLoader -> "Processing"
        uiState.isSaving -> "Processing"
        filterPanelVisible -> "Filters"
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
                    onNavigateUp = {
                        if (filterPanelVisible) {
                            filterPanelVisible = false
                        } else {
                            onNavigateUp()
                        }
                    },
                    onSave = onSave,
                    isSaving = uiState.isSaving,
                )
            },
        ) { innerPadding ->
            val windowSizeInfo = rememberWindowSizeInfo()
            // Side-by-side: tablets (any orientation) and wide landscape windows.
            val useSideBySideEditor = windowSizeInfo.isTablet ||
                (windowSizeInfo.isLandscape && windowSizeInfo.widthClass != WindowWidthClass.Compact)

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
            } else if (useSideBySideEditor) {
                val horizontalPad = when (windowSizeInfo.widthClass) {
                    WindowWidthClass.Compact -> 12.dp
                    WindowWidthClass.Medium -> 20.dp
                    WindowWidthClass.Expanded -> 28.dp
                }
                val previewWeight = when {
                    !filterPanelVisible -> 0.64f
                    windowSizeInfo.widthClass == WindowWidthClass.Expanded -> 0.62f
                    windowSizeInfo.widthClass == WindowWidthClass.Medium -> 0.56f
                    else -> 0.54f
                }
                val sideWeight = 1f - previewWeight

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = horizontalPad),
                    horizontalArrangement = Arrangement.spacedBy(
                        if (windowSizeInfo.widthClass == WindowWidthClass.Expanded) 20.dp else 14.dp,
                    ),
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(previewWeight)
                            .fillMaxHeight()
                            .padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        PageCropEditor(
                            page = uiState.page,
                            cropQuad = uiState.cropQuad,
                            rotationDegrees = uiState.rotationDegrees,
                            selectedFilter = uiState.selectedFilter,
                            filterAdjustments = uiState.filterAdjustments,
                            showCropHandles = !filterPanelVisible,
                            onHandleMoved = onHandleMoved,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(sideWeight)
                            .fillMaxHeight()
                            .navigationBarsPadding()
                            .padding(vertical = 12.dp)
                            .widthIn(
                                max = when (windowSizeInfo.widthClass) {
                                    WindowWidthClass.Expanded -> 440.dp
                                    WindowWidthClass.Medium -> 400.dp
                                    WindowWidthClass.Compact -> Dp.Unspecified
                                },
                            ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        EditorPageBadge(pageIndex = uiState.page.pageIndex)
                        if (filterPanelVisible) {
                            FilterEditorDock(
                                uiState = uiState,
                                windowSizeInfo = windowSizeInfo,
                                selectedTab = filterPanelTab,
                                onTabChange = { filterPanelTab = it },
                                onClose = { filterPanelVisible = false },
                                onSelectFilter = onSelectFilter,
                                onFilterAdjustmentsChange = onFilterAdjustmentsChange,
                                onResetFilterAdjustments = onResetFilterAdjustments,
                                onApplyFilterToAllPagesChange = onApplyFilterToAllPagesChange,
                                layoutMode = FilterDockLayoutMode.SIDE_PANEL,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            )
                        } else {
                            EditorActionRow(
                                onRotateLeft = onRotateLeft,
                                onRotateRight = onRotateRight,
                                onResetCrop = onResetCrop,
                                onOpenFilters = {
                                    filterPanelTab = FilterPanelTab.LOOKS
                                    filterPanelVisible = true
                                },
                                onRetake = onRetakePage,
                                onDelete = { deleteDialogVisible = true },
                                enabled = !uiState.isSaving,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                // Phone portrait: live page keeps the top weight; filters dock below it.
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
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp, bottom = if (filterPanelVisible) 8.dp else 12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        PageCropEditor(
                            page = uiState.page,
                            cropQuad = uiState.cropQuad,
                            rotationDegrees = uiState.rotationDegrees,
                            selectedFilter = uiState.selectedFilter,
                            filterAdjustments = uiState.filterAdjustments,
                            showCropHandles = !filterPanelVisible,
                            onHandleMoved = onHandleMoved,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (filterPanelVisible) {
                        FilterEditorDock(
                            uiState = uiState,
                            windowSizeInfo = windowSizeInfo,
                            selectedTab = filterPanelTab,
                            onTabChange = { filterPanelTab = it },
                            onClose = { filterPanelVisible = false },
                            onSelectFilter = onSelectFilter,
                            onFilterAdjustmentsChange = onFilterAdjustmentsChange,
                            onResetFilterAdjustments = onResetFilterAdjustments,
                            onApplyFilterToAllPagesChange = onApplyFilterToAllPagesChange,
                            layoutMode = FilterDockLayoutMode.BOTTOM_DOCK,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                        )
                    } else {
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
                                onOpenFilters = {
                                    filterPanelTab = FilterPanelTab.LOOKS
                                    filterPanelVisible = true
                                },
                                onRetake = onRetakePage,
                                onDelete = { deleteDialogVisible = true },
                                enabled = !uiState.isSaving,
                            )
                        }
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
private fun FilterScopeOption(
    applyToAllPages: Boolean,
    enabled: Boolean,
    onApplyToAllPagesChange: (Boolean) -> Unit,
    compact: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                onApplyToAllPagesChange(!applyToAllPages)
            },
        color = colorScheme.surfaceContainer,
        shape = if (compact) RoundedCornerShape(16.dp) else MaterialTheme.shapes.extraLarge,
        border = BorderStroke(
            width = 1.dp,
            color = if (applyToAllPages) colorScheme.primary else colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 16.dp,
                vertical = if (compact) 8.dp else 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
            ) {
                Text(
                    text = if (compact) "Apply to all pages" else "Apply Filter To All Pages",
                    color = colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (!compact) {
                    Text(
                        text = "Filter only. Crop and rotation stay per page.",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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

private enum class FilterDockLayoutMode {
    /** Phone portrait: capped height under the live preview. */
    BOTTOM_DOCK,

    /** Tablet / wide: full-height side panel next to the live preview. */
    SIDE_PANEL,
}

/**
 * Bottom/side filter dock that never covers the live page preview.
 * Looks and Adjust are split into tabs so slider mode keeps the image large.
 */
@Composable
private fun FilterEditorDock(
    uiState: PageEditorUiState,
    windowSizeInfo: WindowSizeInfo,
    selectedTab: FilterPanelTab,
    onTabChange: (FilterPanelTab) -> Unit,
    onClose: () -> Unit,
    onSelectFilter: (PageFilterPreset) -> Unit,
    onFilterAdjustmentsChange: (PageFilterAdjustments) -> Unit,
    onResetFilterAdjustments: () -> Unit,
    onApplyFilterToAllPagesChange: (Boolean) -> Unit,
    layoutMode: FilterDockLayoutMode,
    modifier: Modifier = Modifier,
) {
    val page = uiState.page ?: return
    val colorScheme = MaterialTheme.colorScheme
    val configuration = LocalConfiguration.current
    val isSidePanel = layoutMode == FilterDockLayoutMode.SIDE_PANEL
    val useTwoColumnAdjust = isSidePanel &&
        windowSizeInfo.widthClass != WindowWidthClass.Compact
    val useVerticalLooks = isSidePanel
    // Cap dock height on phones so the live preview always keeps most of the screen.
    val maxDockHeight = when (windowSizeInfo.widthClass) {
        WindowWidthClass.Compact -> (configuration.screenHeightDp * 0.46f).dp.coerceIn(240.dp, 400.dp)
        WindowWidthClass.Medium -> (configuration.screenHeightDp * 0.42f).dp.coerceIn(260.dp, 440.dp)
        WindowWidthClass.Expanded -> (configuration.screenHeightDp * 0.40f).dp.coerceIn(280.dp, 480.dp)
    }
    val looksScroll = rememberScrollState()
    val adjustScroll = rememberScrollState()
    val contentPadding = when {
        isSidePanel && windowSizeInfo.widthClass == WindowWidthClass.Expanded -> 16.dp
        isSidePanel -> 14.dp
        else -> 12.dp
    }
    val previewHint = if (isSidePanel) {
        "Live preview is on the left — scroll Adjust for more controls."
    } else {
        "Preview stays above — scroll Adjust for more controls."
    }

    Surface(
        modifier = modifier.then(
            if (isSidePanel) {
                Modifier.fillMaxHeight()
            } else {
                Modifier.heightIn(max = maxDockHeight)
            },
        ),
        color = colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isSidePanel) Modifier.fillMaxHeight() else Modifier)
                .padding(horizontal = contentPadding, vertical = contentPadding - 2.dp),
            verticalArrangement = Arrangement.spacedBy(if (isSidePanel) 12.dp else 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Filters",
                        color = colorScheme.onSurface,
                        style = if (isSidePanel) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = previewHint,
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ChromeIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close filters",
                    onClick = onClose,
                    containerColor = colorScheme.surfaceContainerHighest,
                    contentColor = colorScheme.onSurface,
                )
            }

            FilterPanelTabRow(
                selectedTab = selectedTab,
                onTabChange = onTabChange,
                adjustmentsActive = !uiState.filterAdjustments.isDefault,
            )

            FilterScopeOption(
                applyToAllPages = uiState.applyFilterToAllPages,
                enabled = !uiState.isSaving,
                onApplyToAllPagesChange = onApplyFilterToAllPagesChange,
                compact = true,
            )

            // Side panel fills remaining height; bottom dock gets a bounded scroll viewport
            // so the right-edge scroller is always meaningful when sliders overflow.
            val scrollAreaModifier = if (isSidePanel) {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 148.dp, max = 248.dp)
            }

            when (selectedTab) {
                FilterPanelTab.LOOKS -> {
                    ScrollableColumnWithScrollbar(
                        scrollState = looksScroll,
                        modifier = scrollAreaModifier,
                        contentSpacing = 10.dp,
                    ) {
                        FilterSelector(
                            selectedFilter = uiState.selectedFilter,
                            rawImagePath = page.rawImagePath,
                            fallbackImagePath = page.processedImagePath,
                            rotationDegrees = uiState.rotationDegrees,
                            onSelectFilter = onSelectFilter,
                            vertical = useVerticalLooks,
                            compact = !isSidePanel,
                        )
                    }
                }

                FilterPanelTab.ADJUST -> {
                    ScrollableColumnWithScrollbar(
                        scrollState = adjustScroll,
                        modifier = scrollAreaModifier,
                        contentSpacing = if (useTwoColumnAdjust) 8.dp else 4.dp,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = uiState.selectedFilter.toDisplayLabel(),
                                color = colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            TextButton(
                                onClick = onResetFilterAdjustments,
                                enabled = !uiState.isSaving && !uiState.filterAdjustments.isDefault,
                            ) {
                                Text("Reset")
                            }
                        }
                        FilterAdjustmentsPanel(
                            selectedFilter = uiState.selectedFilter,
                            adjustments = uiState.filterAdjustments,
                            enabled = !uiState.isSaving,
                            onAdjustmentsChange = onFilterAdjustmentsChange,
                            twoColumn = useTwoColumnAdjust,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Scrollable column with a thin always-visible track/thumb on the right when content overflows,
 * plus a small down-arrow cue while more content remains below.
 *
 * The scrollbar sits in a dedicated gutter away from slider thumbs so it is not mistaken
 * for another vertical control.
 */
@Composable
private fun ScrollableColumnWithScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    contentSpacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val canScroll = scrollState.maxValue > 0
    val scrolledToEnd = !canScroll || scrollState.value >= scrollState.maxValue - 2
    // Keep content clear of the scroller: gap + track column (not flush against slider ends).
    val contentEndPadding = 8.dp
    val scrollbarColumnWidth = 14.dp
    val scrollbarGutter = contentEndPadding + scrollbarColumnWidth

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                // Always reserve gutter so layout does not jump when scroll becomes available.
                .padding(end = scrollbarGutter),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = content,
        )

        if (canScroll) {
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(scrollbarColumnWidth)
                    .padding(vertical = 6.dp),
            ) {
                val trackWidth = 2.5.dp
                val thumbWidth = 3.dp
                val minThumbHeight = 32.dp
                val viewportPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val maxScroll = scrollState.maxValue.toFloat().coerceAtLeast(1f)
                val contentHeightPx = viewportPx + maxScroll
                val thumbFraction = (viewportPx / contentHeightPx).coerceIn(0.18f, 0.72f)
                val thumbHeight = (maxHeight * thumbFraction).coerceAtLeast(minThumbHeight)
                val travel = (maxHeight - thumbHeight).coerceAtLeast(0.dp)
                val scrollFraction = (scrollState.value.toFloat() / maxScroll).coerceIn(0f, 1f)
                val thumbOffset = travel * scrollFraction

                // Muted track — distinct from primary-colored slider tracks.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight()
                        .width(trackWidth)
                        .background(
                            color = colorScheme.onSurface.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50),
                        ),
                )
                // Subtle thumb (outline tone, not primary) so it does not look like a slider.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = thumbOffset)
                        .width(thumbWidth)
                        .height(thumbHeight)
                        .background(
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(50),
                        ),
                )
            }

            if (!scrolledToEnd) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(scrollbarColumnWidth)
                        .padding(bottom = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "More controls below",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPanelTabRow(
    selectedTab: FilterPanelTab,
    onTabChange: (FilterPanelTab) -> Unit,
    adjustmentsActive: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterPanelTabChip(
            label = "Looks",
            selected = selectedTab == FilterPanelTab.LOOKS,
            onClick = { onTabChange(FilterPanelTab.LOOKS) },
            modifier = Modifier.weight(1f),
        )
        FilterPanelTabChip(
            label = if (adjustmentsActive) "Adjust · custom" else "Adjust",
            selected = selectedTab == FilterPanelTab.ADJUST,
            onClick = { onTabChange(FilterPanelTab.ADJUST) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FilterPanelTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) colorScheme.primaryContainer else colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) colorScheme.primary else colorScheme.outlineVariant,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

private data class FilterSliderSpec(
    val label: String,
    val valueLabel: String,
    val value: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    val enabled: Boolean,
    val onValueChange: (Float) -> Unit,
)

@Composable
private fun FilterAdjustmentsPanel(
    selectedFilter: PageFilterPreset,
    adjustments: PageFilterAdjustments,
    enabled: Boolean,
    onAdjustmentsChange: (PageFilterAdjustments) -> Unit,
    twoColumn: Boolean = false,
) {
    val showThreshold = adjustments.supportsThresholdControl(selectedFilter)
    val sliders = buildList {
        add(
            FilterSliderSpec(
                label = "Strength",
                valueLabel = percentLabel(adjustments.intensity),
                value = adjustments.intensity,
                valueRange = PageFilterAdjustments.INTENSITY_MIN..PageFilterAdjustments.INTENSITY_MAX,
                enabled = enabled && selectedFilter != PageFilterPreset.ORIGINAL,
                onValueChange = { onAdjustmentsChange(adjustments.copy(intensity = it)) },
            ),
        )
        add(
            FilterSliderSpec(
                label = "Brightness",
                valueLabel = signedPercentLabel(adjustments.brightness),
                value = adjustments.brightness,
                valueRange = PageFilterAdjustments.BRIGHTNESS_MIN..PageFilterAdjustments.BRIGHTNESS_MAX,
                enabled = enabled,
                onValueChange = { onAdjustmentsChange(adjustments.copy(brightness = it)) },
            ),
        )
        add(
            FilterSliderSpec(
                label = "Contrast",
                valueLabel = signedPercentLabel(adjustments.contrast),
                value = adjustments.contrast,
                valueRange = PageFilterAdjustments.CONTRAST_MIN..PageFilterAdjustments.CONTRAST_MAX,
                enabled = enabled,
                onValueChange = { onAdjustmentsChange(adjustments.copy(contrast = it)) },
            ),
        )
        add(
            FilterSliderSpec(
                label = "Shadows",
                valueLabel = percentLabel(adjustments.shadows),
                value = adjustments.shadows,
                valueRange = PageFilterAdjustments.SHADOWS_MIN..PageFilterAdjustments.SHADOWS_MAX,
                enabled = enabled && selectedFilter != PageFilterPreset.ORIGINAL,
                onValueChange = { onAdjustmentsChange(adjustments.copy(shadows = it)) },
            ),
        )
        add(
            FilterSliderSpec(
                label = "Details",
                valueLabel = percentLabel(adjustments.details),
                value = adjustments.details,
                valueRange = PageFilterAdjustments.DETAILS_MIN..PageFilterAdjustments.DETAILS_MAX,
                enabled = enabled,
                onValueChange = { onAdjustmentsChange(adjustments.copy(details = it)) },
            ),
        )
        if (showThreshold) {
            add(
                FilterSliderSpec(
                    label = "Ink",
                    valueLabel = percentLabel(adjustments.threshold),
                    value = adjustments.threshold,
                    valueRange = PageFilterAdjustments.THRESHOLD_MIN..PageFilterAdjustments.THRESHOLD_MAX,
                    enabled = enabled,
                    onValueChange = { onAdjustmentsChange(adjustments.copy(threshold = it)) },
                ),
            )
        }
    }

    if (twoColumn) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sliders.chunked(2).forEach { rowSliders ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowSliders.forEach { spec ->
                        FilterAdjustmentSlider(
                            label = spec.label,
                            valueLabel = spec.valueLabel,
                            value = spec.value,
                            valueRange = spec.valueRange,
                            enabled = spec.enabled,
                            onValueChange = spec.onValueChange,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowSliders.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            sliders.forEach { spec ->
                FilterAdjustmentSlider(
                    label = spec.label,
                    valueLabel = spec.valueLabel,
                    value = spec.value,
                    valueRange = spec.valueRange,
                    enabled = spec.enabled,
                    onValueChange = spec.onValueChange,
                )
            }
        }
    }
}

@Composable
private fun FilterAdjustmentSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (enabled) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.38f),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = valueLabel,
                color = if (enabled) colorScheme.onSurfaceVariant else colorScheme.onSurface.copy(alpha = 0.38f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = colorScheme.primary,
                activeTrackColor = colorScheme.primary,
                inactiveTrackColor = colorScheme.surfaceContainerHighest,
                disabledThumbColor = colorScheme.onSurface.copy(alpha = 0.28f),
                disabledActiveTrackColor = colorScheme.onSurface.copy(alpha = 0.18f),
                disabledInactiveTrackColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
            ),
        )
    }
}

private fun percentLabel(value: Float): String =
    "${(value * 100f).roundToInt()}%"

private fun signedPercentLabel(value: Float): String {
    val percent = (value * 100f).roundToInt()
    return if (percent > 0) "+$percent%" else "$percent%"
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
    filterAdjustments: PageFilterAdjustments,
    showCropHandles: Boolean = true,
    onHandleMoved: (CropHandle, NormalizedPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val previewBitmap by rememberEditorPreviewBitmap(
        rawImagePath = page.rawImagePath,
        fallbackImagePath = page.processedImagePath,
        rotationDegrees = rotationDegrees,
        selectedFilter = selectedFilter,
        filterAdjustments = filterAdjustments,
    )
    val handleSelectionRadiusPx = with(LocalDensity.current) { 24.dp.toPx() }
    val magnifierSizePx = with(LocalDensity.current) { 96.dp.toPx() }
    val magnifierPaddingPx = with(LocalDensity.current) { 12.dp.toPx() }
    val magnifierProtectionRadiusPx = with(LocalDensity.current) { 64.dp.toPx() }
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableStateOf<CropHandle?>(null) }
    var dragOffset by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(showCropHandles) {
        if (!showCropHandles) {
            activeHandle = null
            dragOffset = null
        }
    }

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
                .then(
                    if (showCropHandles) {
                        Modifier.pointerInput(Unit) {
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
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (!showCropHandles) {
                return@Box
            }

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
    compact: Boolean = false,
) {
    val previewState by rememberFilterPreviewBitmaps(
        rawImagePath = rawImagePath,
        fallbackImagePath = fallbackImagePath,
        rotationDegrees = rotationDegrees,
    )
    val listState = rememberLazyListState()
    // Fixed tile sizes so every look card is identical (no growing on select / wrap).
    val previewHeight = when {
        compact -> 80.dp
        vertical -> 92.dp
        else -> 100.dp
    }
    val itemWidth = if (compact) 100.dp else 108.dp
    // preview + padding(10*2) + gap(6) + 2-line label(32) + border room
    val itemHeight = previewHeight + 10.dp + 10.dp + 6.dp + 32.dp
    val gridColumns = if (vertical) 2 else 1

    LaunchedEffect(selectedFilter) {
        if (!vertical) {
            val targetIndex = PageFilterPreset.entries.indexOf(selectedFilter)
            if (targetIndex >= 0) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        if (previewState.isLoading) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Tuning filter previews…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        if (vertical) {
            // Side panel / tablet: multi-column grid (parent provides the scrollbar).
            val filterRows = PageFilterPreset.entries.chunked(gridColumns)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filterRows.forEach { rowFilters ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                    ) {
                        rowFilters.forEach { filter ->
                            FilterItem(
                                filter = filter,
                                isSelected = selectedFilter == filter,
                                preview = previewState.previews[filter],
                                previewHeight = previewHeight,
                                onSelect = { onSelectFilter(filter) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                        if (rowFilters.size < gridColumns) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            // Phone portrait: fixed-size tiles in a horizontal row.
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(PageFilterPreset.entries, key = { it.storageValue }) { filter ->
                    FilterItem(
                        filter = filter,
                        isSelected = selectedFilter == filter,
                        preview = previewState.previews[filter],
                        previewHeight = previewHeight,
                        onSelect = { onSelectFilter(filter) },
                        modifier = Modifier
                            .width(itemWidth)
                            .height(itemHeight),
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
    previewHeight: Dp = 100.dp,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    // Same border width always so selected cards do not grow larger than neighbors.
    Surface(
        onClick = onSelect,
        modifier = modifier,
        color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(14.dp),
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
                            .padding(4.dp)
                            .size(22.dp)
                            .background(colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected filter",
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            // Fixed two-line label band so long names never resize the tile.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = filter.toDisplayLabel(),
                    color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    minLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
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
    filterAdjustments: PageFilterAdjustments,
): androidx.compose.runtime.State<ImageBitmap?> = produceState<ImageBitmap?>(
    initialValue = null,
    rawImagePath,
    fallbackImagePath,
    rotationDegrees,
    selectedFilter,
    filterAdjustments,
) {
    // Short debounce keeps live preview responsive while dragging sliders.
    delay(45)
    value = withContext(Dispatchers.Default) {
        val sourcePath = rawImagePath ?: fallbackImagePath ?: return@withContext null
        val rotatedBitmap = decodeEditorBitmap(
            path = sourcePath,
            userRotationDegrees = rotationDegrees,
        ) ?: return@withContext null
        val filteredBitmap = runCatching {
            OpenCvPageFilterProcessor.apply(
                sourceBitmap = rotatedBitmap,
                filterPreset = selectedFilter,
                adjustments = filterAdjustments,
            )
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
    PageFilterPreset.PHOTO -> "Photo"
    PageFilterPreset.GRAYSCALE -> "Grayscale"
    PageFilterPreset.BLACK_AND_WHITE -> "B&W"
    PageFilterPreset.CLEAN -> "Clean Paper"
    PageFilterPreset.SHADOW_REDUCTION -> "Shadow Reduce"
    PageFilterPreset.MAGIC_COLOR -> "Magic"
    PageFilterPreset.RECEIPT -> "Receipt"
    PageFilterPreset.SOFT_BLACK_AND_WHITE -> "Text Enhance"
    PageFilterPreset.HIGH_CONTRAST -> "High Contrast"
}

private fun PageFilterPreset.shortLabel(): String = when (this) {
    PageFilterPreset.ORIGINAL -> "O"
    PageFilterPreset.AUTO -> "A"
    PageFilterPreset.ENHANCED_COLOR -> "C"
    PageFilterPreset.PHOTO -> "P"
    PageFilterPreset.GRAYSCALE -> "G"
    PageFilterPreset.BLACK_AND_WHITE -> "B&W"
    PageFilterPreset.CLEAN -> "CP"
    PageFilterPreset.SHADOW_REDUCTION -> "SH"
    PageFilterPreset.MAGIC_COLOR -> "M"
    PageFilterPreset.RECEIPT -> "R"
    PageFilterPreset.SOFT_BLACK_AND_WHITE -> "TXT"
    PageFilterPreset.HIGH_CONTRAST -> "HC"
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
