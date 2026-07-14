package `in`.c1ph3rj.scanly.feature.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
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
 * Full-screen filter customization with adaptive large-screen layouts.
 *
 * - Hold **Compare** to preview the selected filter without custom adjustments
 * - Controls scroll with a thin Material scrollbar when content overflows
 * - Preview stays fixed; only the controls region scrolls when needed
 */
@Composable
fun FilterCustomizeScreen(
    page: ScanPage,
    cropQuad: DocumentCornerQuad,
    rotationDegrees: Int,
    selectedFilter: PageFilterPreset,
    filterAdjustments: PageFilterAdjustments,
    onAdjustmentsChange: (PageFilterAdjustments) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    var comparing by remember { mutableStateOf(false) }

    val adjustedPreview by rememberEditorPreviewBitmap(
        rawImagePath = page.rawImagePath,
        fallbackImagePath = page.processedImagePath,
        rotationDegrees = rotationDegrees,
        selectedFilter = selectedFilter,
        cropQuad = cropQuad,
        filterAdjustments = filterAdjustments,
    )
    val baseFilterPreview by rememberEditorPreviewBitmap(
        rawImagePath = page.rawImagePath,
        fallbackImagePath = page.processedImagePath,
        rotationDegrees = rotationDegrees,
        selectedFilter = selectedFilter,
        cropQuad = cropQuad,
        filterAdjustments = PageFilterAdjustments.Default,
    )

    val showCompare = comparing && !filterAdjustments.isDefault
    val displayedPreview = if (showCompare) baseFilterPreview else adjustedPreview
    val layout = rememberCustomizeLayout(windowSizeInfo)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CustomizeTopBar(
                onNavigateUp = onNavigateUp,
                onReset = onReset,
                onDone = onDone,
                resetEnabled = !filterAdjustments.isDefault,
                compareEnabled = !filterAdjustments.isDefault,
                comparing = showCompare,
                onComparePressedChange = { pressed -> comparing = pressed },
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
                CustomizeLayoutMode.TwoPane -> {
                    TwoPaneCustomizeBody(
                        previewBitmap = displayedPreview,
                        comparing = showCompare,
                        selectedFilter = selectedFilter,
                        filterAdjustments = filterAdjustments,
                        onAdjustmentsChange = onAdjustmentsChange,
                        layout = layout,
                    )
                }

                CustomizeLayoutMode.Stacked -> {
                    StackedCustomizeBody(
                        previewBitmap = displayedPreview,
                        comparing = showCompare,
                        selectedFilter = selectedFilter,
                        filterAdjustments = filterAdjustments,
                        onAdjustmentsChange = onAdjustmentsChange,
                        layout = layout,
                    )
                }
            }
        }
    }
}

private enum class CustomizeLayoutMode {
    TwoPane,
    Stacked,
}

private data class CustomizeLayoutSpec(
    val mode: CustomizeLayoutMode,
    val horizontalPadding: Dp,
    val contentMaxWidth: Dp,
    val chromeMaxWidth: Dp,
    val controlsMaxWidth: Dp,
    val controlsMaxHeight: Dp,
    val useTwoColumnSliders: Boolean,
    val previewWeight: Float,
    val controlsWeight: Float,
    val paneSpacing: Dp,
)

@Composable
private fun rememberCustomizeLayout(windowSizeInfo: WindowSizeInfo): CustomizeLayoutSpec {
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
        twoPane && windowSizeInfo.widthClass == WindowWidthClass.Expanded -> 440.dp
        twoPane -> 400.dp
        windowSizeInfo.isTablet -> windowSizeInfo.toolFormMaxWidth
        else -> Dp.Unspecified
    }

    val controlsMaxHeight = when {
        twoPane -> Dp.Unspecified
        windowSizeInfo.useCompactLandscapeLayout -> 200.dp
        windowSizeInfo.isTablet && !windowSizeInfo.isLandscape -> 420.dp
        windowSizeInfo.widthClass == WindowWidthClass.Medium -> 360.dp
        else -> 320.dp
    }

    val useTwoColumnSliders = twoPane &&
        windowSizeInfo.widthClass == WindowWidthClass.Expanded

    return CustomizeLayoutSpec(
        mode = if (twoPane) CustomizeLayoutMode.TwoPane else CustomizeLayoutMode.Stacked,
        horizontalPadding = horizontalPadding,
        contentMaxWidth = contentMaxWidth,
        chromeMaxWidth = chromeMaxWidth,
        controlsMaxWidth = controlsMaxWidth,
        controlsMaxHeight = controlsMaxHeight,
        useTwoColumnSliders = useTwoColumnSliders,
        previewWeight = if (windowSizeInfo.widthClass == WindowWidthClass.Expanded) 0.62f else 0.58f,
        controlsWeight = if (windowSizeInfo.widthClass == WindowWidthClass.Expanded) 0.38f else 0.42f,
        paneSpacing = if (windowSizeInfo.widthClass == WindowWidthClass.Expanded) 24.dp else 16.dp,
    )
}

@Composable
private fun TwoPaneCustomizeBody(
    previewBitmap: ImageBitmap?,
    comparing: Boolean,
    selectedFilter: PageFilterPreset,
    filterAdjustments: PageFilterAdjustments,
    onAdjustmentsChange: (PageFilterAdjustments) -> Unit,
    layout: CustomizeLayoutSpec,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = layout.contentMaxWidth)
            .padding(horizontal = layout.horizontalPadding)
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(layout.paneSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CustomizePreviewPane(
            previewBitmap = previewBitmap,
            comparing = comparing,
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
            CustomizeControlsPane(
                selectedFilter = selectedFilter,
                filterAdjustments = filterAdjustments,
                onAdjustmentsChange = onAdjustmentsChange,
                useTwoColumnSliders = layout.useTwoColumnSliders,
                fillHeight = true,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .widthIn(max = layout.controlsMaxWidth),
            )
        }
    }
}

@Composable
private fun StackedCustomizeBody(
    previewBitmap: ImageBitmap?,
    comparing: Boolean,
    selectedFilter: PageFilterPreset,
    filterAdjustments: PageFilterAdjustments,
    onAdjustmentsChange: (PageFilterAdjustments) -> Unit,
    layout: CustomizeLayoutSpec,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = layout.contentMaxWidth)
            .navigationBarsPadding()
            .padding(horizontal = layout.horizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CustomizePreviewPane(
            previewBitmap = previewBitmap,
            comparing = comparing,
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
        CustomizeControlsPane(
            selectedFilter = selectedFilter,
            filterAdjustments = filterAdjustments,
            onAdjustmentsChange = onAdjustmentsChange,
            useTwoColumnSliders = false,
            fillHeight = false,
            modifier = controlsModifier,
        )
    }
}

@Composable
private fun CustomizeTopBar(
    onNavigateUp: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    resetEnabled: Boolean,
    compareEnabled: Boolean,
    comparing: Boolean,
    onComparePressedChange: (Boolean) -> Unit,
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
                label = if (comparing) "Compare" else "Adjust",
                containerColor = if (comparing) {
                    colorScheme.primaryContainer
                } else {
                    colorScheme.surfaceContainerHighest
                },
                contentColor = if (comparing) {
                    colorScheme.onPrimaryContainer
                } else {
                    colorScheme.onSurface
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompareIconButton(
                    enabled = compareEnabled,
                    active = comparing,
                    onPressedChange = onComparePressedChange,
                )
                ChromeIconButton(
                    icon = Icons.Filled.RestartAlt,
                    contentDescription = "Reset adjustments",
                    onClick = onReset,
                    enabled = resetEnabled,
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
}

/**
 * Hold to compare the selected filter without custom adjustments.
 * Releases back to the adjusted preview.
 */
@Composable
private fun CompareIconButton(
    enabled: Boolean,
    active: Boolean,
    onPressedChange: (Boolean) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when {
        !enabled -> colorScheme.surfaceContainer
        active -> colorScheme.primary
        else -> colorScheme.surfaceContainerHighest
    }
    val contentColor = when {
        !enabled -> colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
        active -> colorScheme.onPrimary
        else -> colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .size(44.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPressedChange(true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        onPressedChange(false)
                    }
                }
            },
        color = containerColor,
        shape = MaterialTheme.shapes.large,
        border = if (active) {
            BorderStroke(1.dp, colorScheme.primary)
        } else {
            null
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Compare,
                contentDescription = "Hold to compare with filter only",
                tint = contentColor,
            )
        }
    }
}

@Composable
private fun CustomizePreviewPane(
    previewBitmap: ImageBitmap?,
    comparing: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = colorScheme.surfaceContainerLow,
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
                    contentDescription = if (comparing) {
                        "Selected filter without adjustments"
                    } else {
                        "Filter adjustment preview"
                    },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                color = if (comparing) {
                    colorScheme.primary.copy(alpha = 0.92f)
                } else {
                    Color.Black.copy(alpha = 0.55f)
                },
                shape = CircleShape,
            ) {
                Text(
                    text = if (comparing) "Filter only" else "Adjusted",
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
private fun CustomizeControlsPane(
    selectedFilter: PageFilterPreset,
    filterAdjustments: PageFilterAdjustments,
    onAdjustmentsChange: (PageFilterAdjustments) -> Unit,
    useTwoColumnSliders: Boolean,
    fillHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
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
                .padding(top = 16.dp, bottom = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Customize filter",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = "Fine-tune ${selectedFilter.toDisplayLabel()} on this page. Hold Compare to preview the filter alone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable sliders + trailing scrollbar in the leftover space.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fillHeight) Modifier.weight(1f) else Modifier)
                    .padding(start = 18.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(scrollState)
                        .padding(end = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (useTwoColumnSliders) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                AdjustmentSlider(
                                    label = "Brightness",
                                    valuePercent = filterAdjustments.brightnessPercent(),
                                    valueRange = -100f..100f,
                                    onValueChange = { percent ->
                                        onAdjustmentsChange(
                                            filterAdjustments.copy(brightness = percent / 100f).sanitized(),
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                AdjustmentSlider(
                                    label = "Contrast",
                                    valuePercent = filterAdjustments.contrastPercent(),
                                    valueRange = -100f..100f,
                                    onValueChange = { percent ->
                                        onAdjustmentsChange(
                                            filterAdjustments.copy(contrast = percent / 100f).sanitized(),
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                AdjustmentSlider(
                                    label = "Saturation",
                                    valuePercent = filterAdjustments.saturationPercent(),
                                    valueRange = -100f..100f,
                                    onValueChange = { percent ->
                                        onAdjustmentsChange(
                                            filterAdjustments.copy(saturation = percent / 100f).sanitized(),
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                AdjustmentSlider(
                                    label = "Sharpness",
                                    valuePercent = filterAdjustments.sharpnessPercent(),
                                    valueRange = 0f..100f,
                                    onValueChange = { percent ->
                                        onAdjustmentsChange(
                                            filterAdjustments.copy(sharpness = percent / 100f).sanitized(),
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    } else {
                        AdjustmentSlider(
                            label = "Brightness",
                            valuePercent = filterAdjustments.brightnessPercent(),
                            valueRange = -100f..100f,
                            onValueChange = { percent ->
                                onAdjustmentsChange(
                                    filterAdjustments.copy(brightness = percent / 100f).sanitized(),
                                )
                            },
                        )
                        AdjustmentSlider(
                            label = "Contrast",
                            valuePercent = filterAdjustments.contrastPercent(),
                            valueRange = -100f..100f,
                            onValueChange = { percent ->
                                onAdjustmentsChange(
                                    filterAdjustments.copy(contrast = percent / 100f).sanitized(),
                                )
                            },
                        )
                        AdjustmentSlider(
                            label = "Saturation",
                            valuePercent = filterAdjustments.saturationPercent(),
                            valueRange = -100f..100f,
                            onValueChange = { percent ->
                                onAdjustmentsChange(
                                    filterAdjustments.copy(saturation = percent / 100f).sanitized(),
                                )
                            },
                        )
                        AdjustmentSlider(
                            label = "Sharpness",
                            valuePercent = filterAdjustments.sharpnessPercent(),
                            valueRange = 0f..100f,
                            onValueChange = { percent ->
                                onAdjustmentsChange(
                                    filterAdjustments.copy(sharpness = percent / 100f).sanitized(),
                                )
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                VerticalContentScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                        .width(6.dp),
                )
            }
        }
    }
}

/**
 * Thin Material-styled scrollbar for scrollable adjust controls.
 * Hidden when content fits; uses primary-tinted thumb on outline track.
 */
@Composable
private fun VerticalContentScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val trackColor = colorScheme.outlineVariant.copy(alpha = 0.55f)
    val thumbColor = colorScheme.primary.copy(alpha = 0.72f)
    val canScroll = scrollState.maxValue > 0

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackWidth = size.width
            val corner = CornerRadius(trackWidth / 2f, trackWidth / 2f)

            // Always draw a subtle track so the gutter matches chrome spacing.
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, 0f),
                size = Size(trackWidth, trackHeightPx),
                cornerRadius = corner,
            )

            if (!canScroll) return@Canvas

            val contentHeight = trackHeightPx + scrollState.maxValue
            val minThumbPx = 28.dp.toPx()
            val thumbHeight = ((trackHeightPx / contentHeight) * trackHeightPx)
                .coerceIn(minThumbPx, trackHeightPx)
            val scrollFraction = scrollState.value / scrollState.maxValue.toFloat()
            val thumbTop = (trackHeightPx - thumbHeight) * scrollFraction

            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(0f, thumbTop),
                size = Size(trackWidth, thumbHeight),
                cornerRadius = corner,
            )
        }
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    valuePercent: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onSurface,
            )
            Surface(
                color = colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = if (valuePercent > 0) "+$valuePercent" else "$valuePercent",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
        Slider(
            value = valuePercent.toFloat(),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = colorScheme.primary,
                activeTrackColor = colorScheme.primary,
                inactiveTrackColor = colorScheme.surfaceContainerHighest,
            ),
        )
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
