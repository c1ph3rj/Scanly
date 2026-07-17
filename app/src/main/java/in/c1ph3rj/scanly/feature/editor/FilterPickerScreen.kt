package `in`.c1ph3rj.scanly.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.WindowSizeInfo
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.ScanPage

/**
 * Full-screen filter picker with a large live preview so users can see how each
 * preset affects the cropped page (same pattern as [FilterCustomizeScreen]).
 */
@Composable
fun FilterPickerScreen(
    page: ScanPage,
    cropQuad: DocumentCornerQuad,
    rotationDegrees: Int,
    selectedFilter: PageFilterPreset,
    filterAdjustments: PageFilterAdjustments,
    applyFilterToAllPages: Boolean,
    isSaving: Boolean,
    onSelectFilter: (PageFilterPreset) -> Unit,
    onApplyFilterToAllPagesChange: (Boolean) -> Unit,
    onDone: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    val layout = rememberFilterPickerLayout(windowSizeInfo)

    // Large live preview: crop + selected filter + current adjustments.
    val livePreview by rememberEditorPreviewBitmap(
        rawImagePath = page.rawImagePath,
        fallbackImagePath = page.processedImagePath,
        rotationDegrees = rotationDegrees,
        selectedFilter = selectedFilter,
        cropQuad = cropQuad,
        filterAdjustments = filterAdjustments,
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FilterPickerTopBar(
                selectedFilter = selectedFilter,
                onNavigateUp = onNavigateUp,
                onDone = onDone,
                contentMaxWidth = layout.chromeMaxWidth,
                horizontalPadding = layout.horizontalPadding,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (layout.mode) {
                FilterPickerLayoutMode.TwoPane -> {
                    TwoPaneFilterPickerBody(
                        livePreview = livePreview,
                        page = page,
                        cropQuad = cropQuad,
                        rotationDegrees = rotationDegrees,
                        selectedFilter = selectedFilter,
                        applyFilterToAllPages = applyFilterToAllPages,
                        isSaving = isSaving,
                        onSelectFilter = onSelectFilter,
                        onApplyFilterToAllPagesChange = onApplyFilterToAllPagesChange,
                        layout = layout,
                    )
                }

                FilterPickerLayoutMode.Stacked -> {
                    StackedFilterPickerBody(
                        livePreview = livePreview,
                        page = page,
                        cropQuad = cropQuad,
                        rotationDegrees = rotationDegrees,
                        selectedFilter = selectedFilter,
                        applyFilterToAllPages = applyFilterToAllPages,
                        isSaving = isSaving,
                        onSelectFilter = onSelectFilter,
                        onApplyFilterToAllPagesChange = onApplyFilterToAllPagesChange,
                        layout = layout,
                    )
                }
            }
        }
    }
}

private enum class FilterPickerLayoutMode {
    TwoPane,
    Stacked,
}

private data class FilterPickerLayoutSpec(
    val mode: FilterPickerLayoutMode,
    val horizontalPadding: Dp,
    val contentMaxWidth: Dp,
    val chromeMaxWidth: Dp,
    val controlsMaxWidth: Dp,
    val controlsMaxHeight: Dp,
    val previewWeight: Float,
    val controlsWeight: Float,
    val paneSpacing: Dp,
)

@Composable
private fun rememberFilterPickerLayout(windowSizeInfo: WindowSizeInfo): FilterPickerLayoutSpec {
    val twoPane = windowSizeInfo.useTabletLandscapeLayout ||
        (windowSizeInfo.useToolTwoPaneLayout && windowSizeInfo.isLandscape)

    val horizontalPadding = when {
        twoPane && windowSizeInfo.widthClass == WindowWidthClass.Expanded -> 32.dp
        twoPane -> 24.dp
        windowSizeInfo.isTablet -> windowSizeInfo.horizontalPadding
        windowSizeInfo.useCompactLandscapeLayout -> 16.dp
        else -> 16.dp
    }

    val contentMaxWidth = when {
        twoPane && windowSizeInfo.widthClass == WindowWidthClass.Expanded -> 1200.dp
        twoPane -> 1040.dp
        windowSizeInfo.isTablet -> windowSizeInfo.contentMaxWidth
        else -> Dp.Unspecified
    }

    val chromeMaxWidth = when {
        windowSizeInfo.widthClass == WindowWidthClass.Expanded -> 1200.dp
        windowSizeInfo.isTablet -> 900.dp
        else -> Dp.Unspecified
    }

    val controlsMaxWidth = when {
        twoPane && windowSizeInfo.widthClass == WindowWidthClass.Expanded -> 460.dp
        twoPane -> 420.dp
        windowSizeInfo.isTablet -> windowSizeInfo.toolFormMaxWidth
        else -> Dp.Unspecified
    }

    val controlsMaxHeight = when {
        twoPane -> Dp.Unspecified
        windowSizeInfo.useCompactLandscapeLayout -> 210.dp
        windowSizeInfo.isTablet && !windowSizeInfo.isLandscape -> 400.dp
        windowSizeInfo.widthClass == WindowWidthClass.Medium -> 340.dp
        else -> 300.dp
    }

    return FilterPickerLayoutSpec(
        mode = if (twoPane) FilterPickerLayoutMode.TwoPane else FilterPickerLayoutMode.Stacked,
        horizontalPadding = horizontalPadding,
        contentMaxWidth = contentMaxWidth,
        chromeMaxWidth = chromeMaxWidth,
        controlsMaxWidth = controlsMaxWidth,
        controlsMaxHeight = controlsMaxHeight,
        previewWeight = if (windowSizeInfo.widthClass == WindowWidthClass.Expanded) 0.58f else 0.55f,
        controlsWeight = if (windowSizeInfo.widthClass == WindowWidthClass.Expanded) 0.42f else 0.45f,
        paneSpacing = if (windowSizeInfo.widthClass == WindowWidthClass.Expanded) 24.dp else 16.dp,
    )
}

@Composable
private fun TwoPaneFilterPickerBody(
    livePreview: ImageBitmap?,
    page: ScanPage,
    cropQuad: DocumentCornerQuad,
    rotationDegrees: Int,
    selectedFilter: PageFilterPreset,
    applyFilterToAllPages: Boolean,
    isSaving: Boolean,
    onSelectFilter: (PageFilterPreset) -> Unit,
    onApplyFilterToAllPagesChange: (Boolean) -> Unit,
    layout: FilterPickerLayoutSpec,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = layout.contentMaxWidth)
            .padding(horizontal = layout.horizontalPadding)
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(layout.paneSpacing),
    ) {
        FilterLivePreviewPane(
            previewBitmap = livePreview,
            selectedFilter = selectedFilter,
            modifier = Modifier
                .weight(layout.previewWeight)
                .fillMaxHeight()
                .padding(vertical = 4.dp),
        )
        Box(
            modifier = Modifier
                .weight(layout.controlsWeight)
                .fillMaxHeight()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            FilterControlsPane(
                page = page,
                cropQuad = cropQuad,
                rotationDegrees = rotationDegrees,
                selectedFilter = selectedFilter,
                applyFilterToAllPages = applyFilterToAllPages,
                isSaving = isSaving,
                fillHeight = true,
                onSelectFilter = onSelectFilter,
                onApplyFilterToAllPagesChange = onApplyFilterToAllPagesChange,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .widthIn(max = layout.controlsMaxWidth),
            )
        }
    }
}

@Composable
private fun StackedFilterPickerBody(
    livePreview: ImageBitmap?,
    page: ScanPage,
    cropQuad: DocumentCornerQuad,
    rotationDegrees: Int,
    selectedFilter: PageFilterPreset,
    applyFilterToAllPages: Boolean,
    isSaving: Boolean,
    onSelectFilter: (PageFilterPreset) -> Unit,
    onApplyFilterToAllPagesChange: (Boolean) -> Unit,
    layout: FilterPickerLayoutSpec,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = layout.contentMaxWidth)
            .navigationBarsPadding()
            .padding(horizontal = layout.horizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilterLivePreviewPane(
            previewBitmap = livePreview,
            selectedFilter = selectedFilter,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
        )

        var controlsModifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
        if (layout.controlsMaxWidth != Dp.Unspecified) {
            controlsModifier = controlsModifier.widthIn(max = layout.controlsMaxWidth)
        }
        if (layout.controlsMaxHeight != Dp.Unspecified) {
            controlsModifier = controlsModifier.heightIn(max = layout.controlsMaxHeight)
        }

        FilterControlsPane(
            page = page,
            cropQuad = cropQuad,
            rotationDegrees = rotationDegrees,
            selectedFilter = selectedFilter,
            applyFilterToAllPages = applyFilterToAllPages,
            isSaving = isSaving,
            fillHeight = false,
            onSelectFilter = onSelectFilter,
            onApplyFilterToAllPagesChange = onApplyFilterToAllPagesChange,
            modifier = controlsModifier,
        )
    }
}

@Composable
private fun FilterPickerTopBar(
    selectedFilter: PageFilterPreset,
    onNavigateUp: () -> Unit,
    onDone: () -> Unit,
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
                label = selectedFilter.toDisplayLabel(),
                containerColor = colorScheme.surfaceContainerHighest,
                contentColor = colorScheme.onSurface,
            )
            ChromeIconButton(
                icon = Icons.Filled.Check,
                contentDescription = "Done",
                onClick = onDone,
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun FilterLivePreviewPane(
    previewBitmap: ImageBitmap?,
    selectedFilter: PageFilterPreset,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (previewBitmap == null) {
                Text(text = "Loading preview…", color = Color.White)
            } else {
                Image(
                    bitmap = previewBitmap,
                    contentDescription = "${selectedFilter.toDisplayLabel()} filter preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                color = Color.Black.copy(alpha = 0.55f),
                shape = CircleShape,
            ) {
                Text(
                    text = selectedFilter.toDisplayLabel(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun FilterControlsPane(
    page: ScanPage,
    cropQuad: DocumentCornerQuad,
    rotationDegrees: Int,
    selectedFilter: PageFilterPreset,
    applyFilterToAllPages: Boolean,
    isSaving: Boolean,
    fillHeight: Boolean,
    onSelectFilter: (PageFilterPreset) -> Unit,
    onApplyFilterToAllPagesChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                .padding(top = 14.dp, bottom = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = "Tap a preset to update the live preview. Save from the editor when finished.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fillHeight) Modifier.weight(1f) else Modifier)
                    .padding(horizontal = 16.dp)
                    .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FilterScopeOption(
                    applyToAllPages = applyFilterToAllPages,
                    enabled = !isSaving,
                    onApplyToAllPagesChange = onApplyFilterToAllPagesChange,
                )

                FilterSelector(
                    selectedFilter = selectedFilter,
                    rawImagePath = page.rawImagePath,
                    fallbackImagePath = page.processedImagePath,
                    rotationDegrees = rotationDegrees,
                    cropQuad = cropQuad,
                    onSelectFilter = onSelectFilter,
                )

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
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
        color = colorScheme.surfaceContainerHigh,
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Apply Filter To All Pages",
                    color = colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "Filter only. Crop, rotation, and adjust stay per page.",
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
private fun FilterSelector(
    selectedFilter: PageFilterPreset,
    rawImagePath: String?,
    fallbackImagePath: String?,
    rotationDegrees: Int,
    cropQuad: DocumentCornerQuad?,
    onSelectFilter: (PageFilterPreset) -> Unit,
) {
    val previewState by rememberFilterPreviewBitmaps(
        rawImagePath = rawImagePath,
        fallbackImagePath = fallbackImagePath,
        rotationDegrees = rotationDegrees,
        cropQuad = cropQuad,
    )
    val listState = rememberLazyListState()

    LaunchedEffect(selectedFilter) {
        val targetIndex = PageFilterPreset.entries.indexOf(selectedFilter)
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (previewState.isLoading) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
        color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
        ),
    ) {
        Box(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .height(120.dp),
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
                        contentDescription = "${filter.toDisplayLabel()} filter thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = filter.toDisplayLabel(),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
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
    }
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
