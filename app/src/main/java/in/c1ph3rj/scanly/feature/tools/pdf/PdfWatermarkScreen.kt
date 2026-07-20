package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.feature.components.shareExportArtifact
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.ZoomableBitmapViewer
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageState
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.core.ui.rememberZoomableImageState
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfCompressQuality
import `in`.c1ph3rj.scanly.domain.model.PdfPasswordMode
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.WatermarkLayout
import `in`.c1ph3rj.scanly.domain.model.WatermarkOrientation
import `in`.c1ph3rj.scanly.domain.model.WatermarkPageRange
import `in`.c1ph3rj.scanly.domain.model.WatermarkSize
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun PdfWatermarkRoute(
    onNavigateUp: () -> Unit,
    onPreviewPdf: (ExportArtifact) -> Unit,
) {
    val viewModel: PdfWatermarkViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPicker by rememberSaveable { mutableStateOf(true) }
    var showPassword by remember { mutableStateOf(false) }
    val phase = toolPhase(uiState.sources, uiState.result)

    val devicePicker = rememberSinglePdfPicker { uri ->
        showPicker = false
        viewModel.setSources(
            listOf(PdfToolSource.DeviceUri(uri.toString(), uriDisplayName(context, uri))),
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest {
            if (it is PdfToolEvent.ShowMessage) snackbarHostState.showSnackbar(it.message)
        }
    }

    ToolDetailScaffold(
        title = "Watermark",
        onNavigateUp = onNavigateUp,
        snackbarHostState = snackbarHostState,
        isProcessing = uiState.isProcessing,
        progressMessage = uiState.progressMessage,
        primaryAction = if (phase == ToolPhase.Ready) {
            {
                Button(
                    onClick = viewModel::run,
                    enabled = uiState.watermarkText.isNotBlank() && !uiState.isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Apply watermark") }
            }
        } else null,
    ) {
        when (phase) {
            ToolPhase.Empty -> ToolEmptyState(
                icon = Icons.Filled.WaterDrop,
                title = "Stamp text on every page",
                subtitle = "Add a full-page watermark (tiled or single large mark) to a PDF from your device or library.",
                onChooseSource = { showPicker = true },
            )
            ToolPhase.Done -> {
                uiState.result?.let { result ->
                    PdfToolCompleteScreen(
                        fileName = result.fileName,
                        statusTitle = "Watermark applied",
                        statusSubtitle = "Your updated PDF is ready.",
                        previewHint = "Preview the stamped file before saving or sharing it.",
                        onPreview = { onPreviewPdf(result) },
                        onSave = viewModel::saveResult,
                        onShare = { shareExportArtifact(context, result, "Watermarked PDF") },
                        onBack = onNavigateUp,
                    )
                }
            }
            ToolPhase.Ready -> {
                val windowSizeInfo = rememberWindowSizeInfo()
                val watermarkControls: @Composable () -> Unit = {
                    Text(
                        text = "WATERMARK",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.watermarkText,
                        onValueChange = viewModel::setWatermarkText,
                        label = { Text("Text") },
                        placeholder = { Text("CONFIDENTIAL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    WatermarkChoiceRow(
                        title = "Layout",
                        options = listOf(
                            WatermarkLayout.REPEATED to "Tiled",
                            WatermarkLayout.CENTERED to "Single",
                        ),
                        selected = uiState.watermarkLayout,
                        onSelected = viewModel::setLayout,
                    )
                    Text(
                        text = when (uiState.watermarkLayout) {
                            WatermarkLayout.REPEATED ->
                                "Dense field that covers the full page, including edges."
                            WatermarkLayout.CENTERED ->
                                "One large stamp sized to dominate the page."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    WatermarkChoiceRow(
                        title = "Size",
                        options = listOf(
                            WatermarkSize.SMALL to "Small",
                            WatermarkSize.MEDIUM to "Medium",
                            WatermarkSize.LARGE to "Large",
                        ),
                        selected = uiState.watermarkSize,
                        onSelected = viewModel::setSize,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    WatermarkChoiceRow(
                        title = "Apply to",
                        options = listOf(
                            WatermarkPageRange.FIRST_PAGE to "First page",
                            WatermarkPageRange.ALL_PAGES to "All pages",
                        ),
                        selected = uiState.watermarkPageRange,
                        onSelected = viewModel::setPageRange,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    WatermarkChoiceRow(
                        title = "Orientation",
                        options = listOf(
                            WatermarkOrientation.DIAGONAL to "Diagonal",
                            WatermarkOrientation.HORIZONTAL to "Horizontal",
                        ),
                        selected = uiState.watermarkOrientation,
                        onSelected = viewModel::setOrientation,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Opacity ${(uiState.watermarkOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Slider(
                        value = uiState.watermarkOpacity,
                        onValueChange = viewModel::setOpacity,
                        valueRange = 0.08f..0.55f,
                    )
                    Text(
                        text = "Lower opacity keeps the document readable under the stamp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Hide password" else "PDF password (optional)")
                    }
                    if (showPassword) {
                        OutlinedTextField(
                            value = uiState.currentPassword,
                            onValueChange = viewModel::setCurrentPassword,
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (windowSizeInfo.useToolTwoPaneLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        WatermarkDocumentPreview(
                            title = uiState.sources.first().label(),
                            preview = uiState.watermarkPreview,
                            isLoading = uiState.isWatermarkPreviewLoading,
                            onChange = { showPicker = true },
                            modifier = Modifier.weight(0.46f),
                        )
                        Column(modifier = Modifier.weight(0.54f)) {
                            watermarkControls()
                        }
                    }
                } else {
                    WatermarkDocumentPreview(
                        title = uiState.sources.first().label(),
                        preview = uiState.watermarkPreview,
                        isLoading = uiState.isWatermarkPreviewLoading,
                        onChange = { showPicker = true },
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    watermarkControls()
                }
            }
        }
    }

    if (showPicker && phase != ToolPhase.Done) {
        PdfSourcePickerSheet(
            documents = uiState.libraryDocuments,
            multiSelect = false,
            onDismiss = { showPicker = false },
            onPickDevice = { devicePicker.launch(arrayOf("application/pdf")) },
            onConfirmLibrary = {
                showPicker = false
                viewModel.setSources(it)
            },
        )
    }
}

@Composable
internal fun WatermarkDocumentPreview(
    title: String,
    preview: Bitmap?,
    isLoading: Boolean,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    val previewHeight = if (windowSizeInfo.useToolTwoPaneLayout) {
        windowSizeInfo.toolPreviewHeight + 80.dp
    } else {
        windowSizeInfo.toolPreviewHeight.coerceAtLeast(220.dp)
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SELECTED PDF",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onChange) { Text("Change") }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    preview != null -> Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "First page preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    isLoading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    else -> Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    )
                }
                if (isLoading && preview != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
        Text(
            text = "Exact first-page preview of the exported PDF",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun <T> WatermarkChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(label) },
                )
            }
        }
    }
}
