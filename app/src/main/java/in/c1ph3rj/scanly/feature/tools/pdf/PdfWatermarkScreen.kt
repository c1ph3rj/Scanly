package `in`.c1ph3rj.scanly.feature.tools.pdf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TextRotationAngleup
import androidx.compose.material.icons.outlined.TextRotationNone
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.WatermarkColor
import `in`.c1ph3rj.scanly.domain.model.WatermarkLayout
import `in`.c1ph3rj.scanly.domain.model.WatermarkOrientation
import `in`.c1ph3rj.scanly.domain.model.WatermarkPageRange
import `in`.c1ph3rj.scanly.domain.model.WatermarkSize
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolChoiceChip
import `in`.c1ph3rj.scanly.feature.tools.ToolColorSwatch
import `in`.c1ph3rj.scanly.feature.tools.ToolCompactHeader
import `in`.c1ph3rj.scanly.feature.tools.ToolDetailRow
import `in`.c1ph3rj.scanly.feature.tools.ToolFieldLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolNote
import `in`.c1ph3rj.scanly.feature.tools.ToolPaperPage
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSegmented
import `in`.c1ph3rj.scanly.feature.tools.ToolShortcutTile
import `in`.c1ph3rj.scanly.feature.tools.ToolTextAction
import `in`.c1ph3rj.scanly.feature.tools.ToolTextField
import `in`.c1ph3rj.scanly.feature.tools.ToolWorkingState
import `in`.c1ph3rj.scanly.feature.tools.toolWarningColors
import kotlinx.coroutines.flow.collectLatest

private enum class WatermarkStep { Pick, Edit, Working, Done }

/**
 * Watermark tool (design: PdfPickerWatermark → WatermarkEdit → WatermarkDone).
 *
 * The editor keeps a live preview pinned above the controls; the preview is rendered by the
 * same engine that writes the file, so what you see is exactly what gets saved.
 */
@Composable
fun PdfWatermarkRoute(
    onNavigateUp: () -> Unit,
    onPreviewPdf: (ExportArtifact) -> Unit,
    initialSource: PdfToolSource? = null,
) {
    val viewModel: PdfWatermarkViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf(initialSource != null) }
    var returnToEditorOnPickerBack by rememberSaveable { mutableStateOf(false) }

    // Opened from the reader's menu with that PDF: skip the picker, and back returns to the reader.
    val fromReader = initialSource != null
    var initialSourceApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialSource) {
        if (initialSource != null && !initialSourceApplied) {
            initialSourceApplied = true
            viewModel.setSources(listOf(initialSource))
        }
    }

    val step = when {
        uiState.result != null -> WatermarkStep.Done
        !editing || uiState.sources.isEmpty() -> WatermarkStep.Pick
        uiState.isProcessing -> WatermarkStep.Working
        else -> WatermarkStep.Edit
    }

    val devicePicker = rememberSinglePdfPicker { uri ->
        returnToEditorOnPickerBack = false
        viewModel.setSources(listOf(PdfToolSource.DeviceUri(uri.toString(), uriDisplayName(context, uri))))
        editing = true
    }

    val onPickerBack = {
        if (shouldReturnFromPdfPickerToPreviousStep(uiState.sources.isNotEmpty(), returnToEditorOnPickerBack)) {
            returnToEditorOnPickerBack = false
            editing = true
        } else {
            onNavigateUp()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest {
            if (it is PdfToolEvent.ShowMessage) snackbarHostState.showSnackbar(it.message)
        }
    }

    BackHandler(enabled = step == WatermarkStep.Edit) { if (fromReader) onNavigateUp() else editing = false }
    BackHandler(
        enabled = step == WatermarkStep.Pick && uiState.sources.isNotEmpty() && returnToEditorOnPickerBack,
        onBack = onPickerBack,
    )
    BackHandler(enabled = step == WatermarkStep.Working) {
        // Keep the watermark job alive until it produces its result.
    }

    val pageCount = uiState.info?.pageCount
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (step) {
            WatermarkStep.Pick -> PdfFilePickerScreen(
                title = "Watermark",
                subtitle = "Stamp text like CONFIDENTIAL or \"For KYC use only\".",
                stepText = "Step 1 of 3 · Choose a file",
                documents = uiState.libraryDocuments,
                onBack = onPickerBack,
                onPickDevice = { devicePicker.launch(arrayOf("application/pdf")) },
                onPickLibrary = { document ->
                    returnToEditorOnPickerBack = false
                    viewModel.setSources(listOf(PdfToolSource.LibraryDocument(document.id, document.title)))
                    editing = true
                },
            )
            WatermarkStep.Edit -> WatermarkEditor(
                uiState = uiState,
                onBack = {
                    if (fromReader) {
                        onNavigateUp()
                    } else {
                        returnToEditorOnPickerBack = false
                        editing = false
                    }
                },
                onChange = {
                    returnToEditorOnPickerBack = true
                    editing = false
                },
                viewModel = viewModel,
            )
            WatermarkStep.Working -> ToolWorkingState(
                title = "Adding watermark…",
                caption = when {
                    uiState.watermarkPageRange == WatermarkPageRange.FIRST_PAGE -> "Stamping the first page"
                    pageCount != null -> "Stamping ${formatPdfPageCount(pageCount)}"
                    else -> "Stamping every page"
                },
                preview = {
                    ToolPaperPage(
                        bitmap = uiState.watermarkPreview,
                        modifier = Modifier.width(120.dp),
                    )
                },
            )
            WatermarkStep.Done -> PdfToolDoneScreen(
                title = "Watermark added",
                subtitle = when {
                    uiState.watermarkPageRange == WatermarkPageRange.FIRST_PAGE ->
                        "On the first page. Tap the page to check it."
                    pageCount == 1 -> "On the page. Tap it to check."
                    pageCount == 2 -> "On both pages. Tap the page to check it."
                    pageCount != null -> "On all $pageCount pages. Tap the page to check it."
                    else -> "On every page. Tap the page to check it."
                },
                preview = uiState.resultPreview,
                outputName = uiState.outputName,
                onOutputNameChange = viewModel::setOutputName,
                details = listOf(
                    ToolDetailRow("Stamp", uiState.watermarkLabel()),
                    ToolDetailRow("Style", uiState.watermarkStyleSummary()),
                    ToolDetailRow("Pages", applyToLabel(uiState.watermarkPageRange, pageCount, short = true)),
                    ToolDetailRow("Original", "Kept, unstamped"),
                ),
                onClose = onNavigateUp,
                onOpenPreview = { uiState.namedResult()?.let(onPreviewPdf) },
                onSave = viewModel::saveResult,
                onShare = {
                    viewModel.prepareNamedShare { shareExportArtifact(context, it, "Watermarked PDF") }
                },
                isSaving = uiState.isProcessing,
                savedDestinationLabel = uiState.savedDestinationLabel,
                extraContent = {
                    ToolShortcutTile(
                        icon = Icons.Outlined.Edit,
                        title = "Edit stamp",
                        caption = "Back to the editor",
                        onClick = {
                            viewModel.editAgain()
                            editing = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }
}

@Composable
private fun WatermarkEditor(
    uiState: PdfToolUiState,
    onBack: () -> Unit,
    onChange: () -> Unit,
    viewModel: PdfWatermarkViewModel,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    val source = uiState.sources.firstOrNull()
    val pageCount = uiState.info?.pageCount
    val locked = uiState.needsPassword
    val label = uiState.watermarkLabel()
    val canApply = label.isNotBlank() && uiState.watermarkPreviewError == null && !locked && !uiState.isProcessing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        ToolCompactHeader(
            title = "Watermark",
            subtitle = listOfNotNull(source?.label(), pageCount?.let(::formatPdfPageCount)).joinToString(" · "),
            onBack = onBack,
            actionLabel = "Change",
            onAction = onChange,
        )
        if (windowSizeInfo.useToolTwoPaneLayout) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                WatermarkPreviewPanel(
                    uiState = uiState,
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp),
                )
                WatermarkControls(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight(),
                )
            }
        } else {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Pinned preview: ~35% of the space (design: 292dp on an 844dp phone), never tiny.
                val previewHeight = (maxHeight * 0.4f).coerceIn(180.dp, 300.dp)
                Column(modifier = Modifier.fillMaxSize()) {
                    WatermarkPreviewPanel(
                        uiState = uiState,
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                            .fillMaxWidth()
                            .height(previewHeight),
                    )
                    WatermarkControls(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
        }
        ToolBottomBar {
            ToolPrimaryButton(
                text = when {
                    locked -> "Unlock the PDF first"
                    uiState.watermarkPageRange == WatermarkPageRange.FIRST_PAGE -> "Apply to first page"
                    pageCount == 1 -> "Apply to page"
                    pageCount != null -> "Apply to $pageCount pages"
                    else -> "Apply watermark"
                },
                onClick = viewModel::run,
                enabled = canApply,
            )
        }
    }
}

@Composable
private fun WatermarkPreviewPanel(
    uiState: PdfToolUiState,
    modifier: Modifier = Modifier,
) {
    val pageCount = uiState.info?.pageCount
    val error = uiState.watermarkPreviewError
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (uiState.needsPassword && uiState.watermarkPreview == null) {
                ToolPaperPage(
                    bitmap = null,
                    modifier = Modifier.fillMaxHeight(),
                    overlay = {
                        androidx.compose.material3.Icon(
                            Icons.Outlined.Lock,
                            contentDescription = "Locked PDF",
                            tint = Color(0xFF6B757C),
                            modifier = Modifier.size(28.dp),
                        )
                    },
                )
            } else {
                ToolPaperPage(
                    bitmap = uiState.watermarkPreview,
                    loading = uiState.watermarkPreview == null || uiState.isWatermarkPreviewLoading,
                    contentDescription = "Preview of page 1 with the watermark",
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (error != null) {
                val warn = toolWarningColors()
                androidx.compose.material3.Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = warn.content,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = warn.content,
                    textAlign = TextAlign.Center,
                )
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (pageCount != null) "1 / $pageCount" else "Page 1",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    text = "Live preview of the saved file",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatermarkControls(
    uiState: PdfToolUiState,
    viewModel: PdfWatermarkViewModel,
    modifier: Modifier = Modifier,
) {
    val pageCount = uiState.info?.pageCount
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (uiState.needsPassword) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolFieldLabel("This PDF is locked")
                ToolTextField(
                    value = uiState.currentPassword,
                    onValueChange = viewModel::setCurrentPassword,
                    placeholder = "PDF password",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    trailingIcon = { ToolTextAction(label = "Unlock", onClick = viewModel::unlock) },
                )
                ToolNote("The watermarked copy is saved without the password. You can lock it again with the Password tool.")
            }
        }

        // Text + presets.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolFieldLabel("Text")
            ToolTextField(
                value = uiState.watermarkText,
                onValueChange = viewModel::setWatermarkText,
                placeholder = "CONFIDENTIAL",
                emphasized = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics { contentDescription = "Presets" },
            ) {
                WatermarkPresets.forEach { preset ->
                    ToolChoiceChip(
                        label = preset,
                        selected = uiState.watermarkText == preset,
                        onClick = { viewModel.setWatermarkText(preset) },
                    )
                }
                ToolChoiceChip(
                    label = "Add date",
                    selected = uiState.watermarkAddDate,
                    onClick = viewModel::toggleDate,
                    leadingIcon = Icons.Outlined.CalendarToday,
                )
            }
            if (uiState.watermarkAddDate) {
                Text(
                    text = "Stamps \"${uiState.watermarkLabel()}\"",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Style block: layout + angle, size, opacity, colour.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolFieldLabel("Style")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolSegmented(
                    options = listOf(WatermarkLayout.REPEATED, WatermarkLayout.CENTERED),
                    selected = uiState.watermarkLayout,
                    onSelect = viewModel::setLayout,
                    label = { if (it == WatermarkLayout.REPEATED) "Tiled" else "Single" },
                    modifier = Modifier.weight(1f),
                )
                ToolSegmented(
                    options = listOf(WatermarkOrientation.DIAGONAL, WatermarkOrientation.HORIZONTAL),
                    selected = uiState.watermarkOrientation,
                    onSelect = viewModel::setOrientation,
                    label = { if (it == WatermarkOrientation.DIAGONAL) "Diagonal" else "Horizontal" },
                    icon = {
                        if (it == WatermarkOrientation.DIAGONAL) {
                            Icons.Outlined.TextRotationAngleup
                        } else {
                            Icons.Outlined.TextRotationNone
                        }
                    },
                    iconOnly = true,
                    modifier = Modifier.width(112.dp),
                )
            }
            val sizes = WatermarkSize.entries
            SliderRow(
                label = "Size",
                valueText = when (uiState.watermarkSize) {
                    WatermarkSize.SMALL -> "Small"
                    WatermarkSize.MEDIUM -> "Medium"
                    WatermarkSize.LARGE -> "Large"
                },
                value = sizes.indexOf(uiState.watermarkSize).toFloat(),
                onValueChange = { viewModel.setSize(sizes[it.toInt().coerceIn(0, sizes.lastIndex)]) },
                valueRange = 0f..sizes.lastIndex.toFloat(),
                steps = sizes.size - 2,
            )
            SliderRow(
                label = "Opacity",
                valueText = "${(uiState.watermarkOpacity * 100f).toInt()}%",
                value = uiState.watermarkOpacity,
                onValueChange = { viewModel.setOpacity((it * 50f).toInt() / 50f) },
                valueRange = 0.10f..0.60f,
                monospace = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Colour",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WatermarkColor.entries.forEach { color ->
                        ToolColorSwatch(
                            color = Color(color.argb),
                            name = color.label,
                            selected = uiState.watermarkColor == color,
                            onClick = { viewModel.setColor(color) },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolFieldLabel("Apply to")
            ToolSegmented(
                options = listOf(WatermarkPageRange.ALL_PAGES, WatermarkPageRange.FIRST_PAGE),
                selected = uiState.watermarkPageRange,
                onSelect = viewModel::setPageRange,
                label = { applyToLabel(it, pageCount, short = false) },
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    monospace: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .weight(1f)
                .semantics { stateDescription = valueText },
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
        )
    }
}

private fun applyToLabel(range: WatermarkPageRange, pageCount: Int?, short: Boolean): String = when (range) {
    WatermarkPageRange.FIRST_PAGE -> "First page only"
    WatermarkPageRange.ALL_PAGES -> when {
        pageCount == null -> "All pages"
        pageCount == 1 -> "The page"
        else -> "All $pageCount pages"
    }
}
