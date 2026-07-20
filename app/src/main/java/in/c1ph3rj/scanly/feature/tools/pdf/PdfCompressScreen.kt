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
fun PdfCompressRoute(
    onNavigateUp: () -> Unit,
    onPreviewPdf: (ExportArtifact) -> Unit,
) {
    val viewModel: PdfCompressViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPicker by rememberSaveable { mutableStateOf(true) }
    val phase = toolPhase(uiState.sources, uiState.result)

    val devicePicker = rememberSinglePdfPicker { uri ->
        showPicker = false
        viewModel.onSourcesChosen(
            listOf(PdfToolSource.DeviceUri(uri.toString(), uriDisplayName(context, uri))),
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest {
            if (it is PdfToolEvent.ShowMessage) snackbarHostState.showSnackbar(it.message)
        }
    }

    val before = uiState.originalSizeBytes
    val after = uiState.compressedSizeBytes
    val savedPercent = if (before != null && after != null && before > 0L) {
        ((before - after).toFloat() / before.toFloat() * 100f)
    } else null

    ToolDetailScaffold(
        title = "Compress PDF",
        onNavigateUp = onNavigateUp,
        snackbarHostState = snackbarHostState,
        isProcessing = uiState.isProcessing,
        progressMessage = uiState.progressMessage,
        primaryAction = if (phase == ToolPhase.Ready) {
            {
                Button(
                    onClick = viewModel::runCompress,
                    enabled = !uiState.isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Compress PDF") }
            }
        } else null,
    ) {
        when (phase) {
            ToolPhase.Empty -> ToolEmptyState(
                icon = Icons.Filled.Compress,
                title = "Compress a PDF",
                subtitle = "Reduce file size by re-encoding pages. Choose quality next.",
                onChooseSource = { showPicker = true },
            )
            ToolPhase.Done -> {
                uiState.result?.let { result ->
                    PdfToolCompleteScreen(
                        fileName = result.fileName,
                        statusTitle = "Compression complete",
                        statusSubtitle = "Your compressed PDF is ready.",
                        previewHint = "Preview the finished file before saving or sharing it.",
                        resultDetail = sizeComparisonLabel(before, after, savedPercent),
                        onPreview = { onPreviewPdf(result) },
                        onSave = viewModel::saveResult,
                        onShare = {
                            shareExportArtifact(context, result, "Compressed PDF")
                        },
                        onBack = onNavigateUp,
                    )
                }
            }
            ToolPhase.Ready -> {
                val windowSizeInfo = rememberWindowSizeInfo()
                val qualityOptions: @Composable () -> Unit = {
                    Text(
                        text = "Compression level",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Always vertical — side-by-side cards crush on tablet two-pane panes.
                    PdfCompressQuality.entries.forEachIndexed { index, quality ->
                        if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                        QualityOptionCard(
                            quality = quality,
                            selected = uiState.compressQuality == quality,
                            recommended = quality == PdfCompressQuality.MEDIUM,
                            onClick = { viewModel.setQuality(quality) },
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pages are re-encoded as images. Text may look slightly softer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val passwordFields: @Composable () -> Unit = {
                    if (uiState.needsPassword || uiState.info?.isEncrypted == true) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = uiState.currentPassword,
                            onValueChange = viewModel::setCurrentPassword,
                            label = { Text("PDF password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = viewModel::unlockOrRefresh) {
                            Text("Unlock / refresh preview")
                        }
                    }
                }
                if (windowSizeInfo.useToolTwoPaneLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        PdfSelectedDocumentPreview(
                            title = uiState.sources.first().label(),
                            supporting = buildString {
                                val info = uiState.info
                                if (info != null) {
                                    append("${info.pageCount} pages")
                                    (before ?: info.fileSizeBytes)?.let { bytes ->
                                        append(" · ${StorageFormatter.formatBytes(bytes)}")
                                    }
                                } else {
                                    before?.let { bytes -> append(StorageFormatter.formatBytes(bytes)) }
                                }
                            }.ifBlank { null },
                            preview = uiState.sourcePagePreview,
                            isLoading = uiState.isSourcePagePreviewLoading,
                            caption = "First page of the selected PDF",
                            onChange = { showPicker = true },
                            modifier = Modifier.weight(0.46f),
                        )
                        Column(modifier = Modifier.weight(0.54f)) {
                            passwordFields()
                            if (uiState.needsPassword || uiState.info?.isEncrypted == true) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            qualityOptions()
                        }
                    }
                } else {
                    PdfSelectedDocumentPreview(
                        title = uiState.sources.first().label(),
                        supporting = buildString {
                            val info = uiState.info
                            if (info != null) {
                                append("${info.pageCount} pages")
                                (before ?: info.fileSizeBytes)?.let { bytes ->
                                    append(" · ${StorageFormatter.formatBytes(bytes)}")
                                }
                            } else {
                                before?.let { bytes -> append(StorageFormatter.formatBytes(bytes)) }
                            }
                        }.ifBlank { null },
                        preview = uiState.sourcePagePreview,
                        isLoading = uiState.isSourcePagePreviewLoading,
                        caption = "First page of the selected PDF",
                        onChange = { showPicker = true },
                    )
                    passwordFields()
                    Spacer(modifier = Modifier.height(20.dp))
                    qualityOptions()
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
                viewModel.onSourcesChosen(it)
            },
        )
    }
}

@Composable
internal fun PdfSelectedDocumentPreview(
    title: String,
    supporting: String?,
    preview: Bitmap?,
    isLoading: Boolean,
    onChange: () -> Unit,
    caption: String = "First page of the selected PDF",
    modifier: Modifier = Modifier,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
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
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onChange) { Text("Change") }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(windowSizeInfo.toolPreviewHeight),
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
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
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun QualityOptionCard(
    quality: PdfCompressQuality,
    selected: Boolean,
    recommended: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.64f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = quality.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (recommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recommended",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    text = quality.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

internal fun sizeComparisonLabel(before: Long?, after: Long?, savedPercent: Float?): String? {
    if (before == null || after == null) return null
    return if (after < before && savedPercent != null) {
        "${StorageFormatter.formatBytes(before)} → ${StorageFormatter.formatBytes(after)} · saved ${savedPercent.toInt()}%"
    } else {
        "${StorageFormatter.formatBytes(before)} → ${StorageFormatter.formatBytes(after)}"
    }
}
