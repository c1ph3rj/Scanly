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
fun PdfMergeRoute(
    onNavigateUp: () -> Unit,
    onPreviewPdf: (ExportArtifact) -> Unit,
) {
    val viewModel: PdfMergeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // rememberSaveable: rotation must not reset to true or the source sheet re-opens.
    var showPicker by rememberSaveable { mutableStateOf(true) }
    var appendMode by rememberSaveable { mutableStateOf(false) }
    val phase = toolPhase(uiState.sources, uiState.result)

    val devicePicker = rememberMultiPdfPicker { uris ->
        showPicker = false
        val sources = uris.map { uri ->
            PdfToolSource.DeviceUri(uri.toString(), uriDisplayName(context, uri))
        }
        if (appendMode) viewModel.appendDeviceUris(uris, sources.map { it.displayName })
        else viewModel.setSources(sources)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest {
            if (it is PdfToolEvent.ShowMessage) snackbarHostState.showSnackbar(it.message)
        }
    }

    ToolDetailScaffold(
        title = "Merge PDFs",
        onNavigateUp = onNavigateUp,
        snackbarHostState = snackbarHostState,
        isProcessing = uiState.isProcessing,
        progressMessage = uiState.progressMessage,
        primaryAction = if (phase == ToolPhase.Ready) {
            {
                Button(
                    onClick = viewModel::runMerge,
                    enabled = uiState.sources.size >= 2 && !uiState.isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Merge PDFs") }
            }
        } else null,
    ) {
        when (phase) {
            ToolPhase.Empty -> ToolEmptyState(
                icon = Icons.AutoMirrored.Filled.MergeType,
                title = "Merge PDFs",
                subtitle = "Select two or more PDFs from your device or Scanly library.",
                actionLabel = "Choose PDFs",
                onChooseSource = {
                    appendMode = false
                    showPicker = true
                },
            )
            ToolPhase.Done -> {
                uiState.result?.let { result ->
                    PdfToolCompleteScreen(
                        fileName = result.fileName,
                        statusTitle = "Merge complete",
                        statusSubtitle = "Your combined PDF is ready.",
                        previewHint = "Preview the finished file before saving or sharing it.",
                        onPreview = { onPreviewPdf(result) },
                        onSave = viewModel::saveResult,
                        onShare = {
                            shareExportArtifact(context, result, "Merged PDF")
                        },
                        onBack = onNavigateUp,
                    )
                }
            }
            ToolPhase.Ready -> {
                ToolSourceList(
                    sources = uiState.sources,
                    onAdd = {
                        appendMode = true
                        showPicker = true
                    },
                    onRemove = viewModel::removeSource,
                )
                if (uiState.sources.size < 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add at least one more PDF to merge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showPicker && phase != ToolPhase.Done) {
        PdfSourcePickerSheet(
            documents = uiState.libraryDocuments,
            multiSelect = true,
            onDismiss = { showPicker = false },
            onPickDevice = { devicePicker.launch(arrayOf("application/pdf")) },
            onConfirmLibrary = { docs ->
                showPicker = false
                if (appendMode) viewModel.appendLibrary(docs) else viewModel.setSources(docs)
            },
        )
    }
}
