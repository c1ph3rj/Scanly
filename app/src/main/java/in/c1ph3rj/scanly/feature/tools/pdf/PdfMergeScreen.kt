package `in`.c1ph3rj.scanly.feature.tools.pdf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.feature.tools.ToolBadge
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolCard
import `in`.c1ph3rj.scanly.feature.tools.ToolDetailRow
import `in`.c1ph3rj.scanly.feature.tools.ToolFileNameField
import `in`.c1ph3rj.scanly.feature.tools.ToolLargeTitle
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSectionLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolTextAction
import `in`.c1ph3rj.scanly.feature.tools.ToolTopBar
import `in`.c1ph3rj.scanly.feature.tools.ToolWorkingState
import kotlinx.coroutines.flow.collectLatest

/** Steps of the merge flow. Working and Done are derived from [PdfToolUiState], not requested. */
private enum class MergeStep { Pick, Order, Working, Done }

@Composable
fun PdfMergeRoute(
    onNavigateUp: () -> Unit,
    onPreviewPdf: (ExportArtifact) -> Unit,
    initialSource: PdfToolSource? = null,
) {
    val viewModel: PdfMergeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // The step the user asked for; Working and Done follow the ViewModel so they survive rotation.
    var requestedStep by rememberSaveable { mutableStateOf(MergeStep.Pick.name) }
    val step = when {
        uiState.result != null -> MergeStep.Done
        uiState.isProcessing -> MergeStep.Working
        requestedStep == MergeStep.Order.name && uiState.sources.isNotEmpty() -> MergeStep.Order
        else -> MergeStep.Pick
    }
    fun goTo(target: MergeStep) {
        requestedStep = target.name
    }

    // Opened from the reader's menu with that PDF: skip the picker, and back returns to the reader.
    val fromReader = initialSource != null
    var initialSourceApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialSource) {
        if (initialSource != null && !initialSourceApplied) {
            initialSourceApplied = true
            viewModel.addInitialSource(initialSource)
        }
    }

    val devicePicker = rememberMultiPdfPicker { uris ->
        viewModel.addDeviceSources(
            uris.map { uri -> PdfToolSource.DeviceUri(uri.toString(), uriDisplayName(context, uri)) },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest {
            if (it is PdfToolEvent.ShowMessage) snackbarHostState.showSnackbar(it.message)
        }
    }

    BackHandler(enabled = step == MergeStep.Order || step == MergeStep.Done) {
        if (step == MergeStep.Done) onNavigateUp() else goTo(MergeStep.Pick)
    }
    BackHandler(enabled = step == MergeStep.Working) {
        // The merge runs in this route's ViewModel and cannot be cancelled safely.
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (step) {
            MergeStep.Pick -> MergePickStep(
                uiState = uiState,
                onBack = onNavigateUp,
                onPickDevice = { devicePicker.launch(arrayOf("application/pdf")) },
                onToggleLibrary = viewModel::toggleLibraryDocument,
                onRemoveSource = viewModel::removeSource,
                onNext = {
                    viewModel.prepareOrder()
                    goTo(MergeStep.Order)
                },
            )
            MergeStep.Order -> MergeOrderStep(
                uiState = uiState,
                onBack = { goTo(MergeStep.Pick) },
                onAddMore = { goTo(MergeStep.Pick) },
                onMove = viewModel::moveSource,
                onRemove = viewModel::removeSource,
                onOutputNameChange = viewModel::setOutputName,
                onMerge = viewModel::runMerge,
            )
            MergeStep.Working -> MergeStepFrame {
                ToolWorkingState(
                    title = "Merging…",
                    caption = filesLabel(uiState.sources.size),
                )
            }
            MergeStep.Done -> MergeDoneStep(
                uiState = uiState,
                onClose = onNavigateUp,
                onOpenPreview = { uiState.namedResult()?.let(onPreviewPdf) },
                onOutputNameChange = viewModel::setOutputName,
                onSave = viewModel::saveResult,
                onShare = {
                    viewModel.prepareNamedShare { shareExportArtifact(context, it, "Merged PDF") }
                },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        )
    }
}

/** Keeps each step to a readable column on tablets (max 640dp, centred). */
@Composable
private fun MergeStepFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            content()
        }
    }
}

@Composable
private fun MergePickStep(
    uiState: PdfToolUiState,
    onBack: () -> Unit,
    onPickDevice: () -> Unit,
    onToggleLibrary: (ScanDocument) -> Unit,
    onRemoveSource: (Int) -> Unit,
    onNext: () -> Unit,
) {
    val selectedCount = uiState.sources.size
    val deviceFiles = uiState.sources.mapIndexedNotNull { index, source ->
        (source as? PdfToolSource.DeviceUri)?.let { index to it }
    }
    MergeStepFrame {
        PdfFilePickerScreen(
            title = "Merge PDFs",
            subtitle = "Pick files in the order you want them.",
            stepText = "Step 1 of 3 · Pick files",
            documents = uiState.libraryDocuments,
            onBack = onBack,
            onPickDevice = onPickDevice,
            onPickLibrary = onToggleLibrary,
            selectedOrder = uiState.sources.map { it.pickerKey() },
            headerExtra = { DeviceFileRows(files = deviceFiles, onRemove = onRemoveSource) },
            bottomBar = {
                if (selectedCount < 2) {
                    Text(
                        text = "Pick at least 2 files",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ToolPrimaryButton(
                    text = if (selectedCount >= 2) "Next · $selectedCount files" else "Next",
                    onClick = onNext,
                    enabled = selectedCount >= 2,
                )
            },
        )
    }
}

/** Phone-storage files are not library rows, so they are listed above the picker with a remove button. */
@Composable
private fun DeviceFileRows(
    files: List<Pair<Int, PdfToolSource.DeviceUri>>,
    onRemove: (Int) -> Unit,
) {
    if (files.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        files.forEach { (index, file) ->
            ToolCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "From phone: ${file.displayName}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove ${file.displayName}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeOrderStep(
    uiState: PdfToolUiState,
    onBack: () -> Unit,
    onAddMore: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onOutputNameChange: (String) -> Unit,
    onMerge: () -> Unit,
) {
    val sources = uiState.sources
    val count = sources.size
    val totalPages = mergedPageTotal(sources, uiState.libraryDocuments)
    val totalLine = totalPages?.let { "${formatPdfPageCount(it)} in total" } ?: filesLabel(count)
    MergeStepFrame {
        Column(Modifier.fillMaxSize()) {
            ToolTopBar(onBack = onBack, stepText = "Step 2 of 3 · Order & name")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ToolLargeTitle(title = "Order & name", subtitle = "Files are joined from top to bottom.")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolSectionLabel(
                            text = "Merge order · ${filesLabel(count)}",
                            modifier = Modifier.weight(1f),
                        )
                        ToolTextAction(label = "Add more", onClick = onAddMore)
                    }
                    sources.forEachIndexed { index, source ->
                        MergeOrderRow(
                            index = index,
                            count = count,
                            source = source,
                            document = (source as? PdfToolSource.LibraryDocument)?.let { ref ->
                                uiState.libraryDocuments.firstOrNull { it.id == ref.documentId }
                            },
                            onMoveUp = { onMove(index, index - 1) },
                            onMoveDown = { onMove(index, index + 1) },
                            onRemove = { onRemove(index) },
                        )
                    }
                }
                Text(
                    text = totalLine,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToolFileNameField(value = uiState.outputName, onValueChange = onOutputNameChange)
            }
            ToolBottomBar {
                ToolPrimaryButton(
                    text = if (count >= 2) "Merge $count files" else "Merge PDFs",
                    onClick = onMerge,
                    enabled = count >= 2 && !uiState.isProcessing,
                    icon = Icons.AutoMirrored.Filled.MergeType,
                )
            }
        }
    }
}

@Composable
private fun MergeOrderRow(
    index: Int,
    count: Int,
    source: PdfToolSource,
    document: ScanDocument?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val name = source.label()
    val meta = when (source) {
        is PdfToolSource.LibraryDocument ->
            document?.let { formatPdfPageCount(it.pageCount) } ?: "Page count unknown"
        is PdfToolSource.DeviceUri -> "PDF from phone"
        is PdfToolSource.AppFile -> "PDF from Scanly"
    }
    ToolCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box {
                if (document != null) {
                    PdfPaperThumbnail(document = document, width = 44.dp)
                } else {
                    PaperPlaceholder(width = 44.dp)
                }
                ToolBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-8).dp, y = (-8).dp),
                    size = 22.dp,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 19.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move $name up")
            }
            IconButton(
                onClick = onMoveDown,
                enabled = index < count - 1,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move $name down")
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Remove $name")
            }
        }
    }
}

/** Stand-in for a device file's first page: a white sheet with a PDF icon. */
@Composable
private fun PaperPlaceholder(width: Dp) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = Modifier
            .size(width = width, height = width * 1.41f)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.PictureAsPdf,
            contentDescription = null,
            tint = Color(0xFF6B757C),
            modifier = Modifier.size(width * 0.45f),
        )
    }
}

@Composable
private fun MergeDoneStep(
    uiState: PdfToolUiState,
    onClose: () -> Unit,
    onOpenPreview: () -> Unit,
    onOutputNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val count = uiState.sources.size
    val totalPages = mergedPageTotal(uiState.sources, uiState.libraryDocuments)
    val details = buildList {
        add(ToolDetailRow("Files", count.toString()))
        if (totalPages != null) add(ToolDetailRow("Pages", formatPdfPageCount(totalPages)))
        uiState.resultSizeBytes?.let { add(ToolDetailRow("Size", StorageFormatter.formatBytes(it))) }
        add(ToolDetailRow("Original files", "Kept, unchanged"))
    }
    MergeStepFrame {
        PdfToolDoneScreen(
            title = "Merged",
            subtitle = "${filesLabel(count)} combined into one PDF",
            preview = uiState.resultPreview,
            outputName = uiState.outputName,
            onOutputNameChange = onOutputNameChange,
            details = details,
            onClose = onClose,
            onOpenPreview = onOpenPreview,
            onSave = onSave,
            onShare = onShare,
            isSaving = uiState.isProcessing && uiState.result != null,
            savedDestinationLabel = uiState.savedDestinationLabel,
            footnote = "Your original files are unchanged",
        )
    }
}

/** Sum of the page counts when every file's count is known; null rather than an invented total. */
private fun mergedPageTotal(sources: List<PdfToolSource>, documents: List<ScanDocument>): Int? {
    val counts = sources.map { source ->
        (source as? PdfToolSource.LibraryDocument)
            ?.let { ref -> documents.firstOrNull { it.id == ref.documentId } }
            ?.pageCount
            ?.takeIf { it > 0 }
    }
    return if (counts.any { it == null }) null else counts.filterNotNull().sum()
}

private fun filesLabel(count: Int): String = if (count == 1) "1 file" else "$count files"

/** Key the picker uses to find a row's merge position. Device files never match a Scanly row. */
private fun PdfToolSource.pickerKey(): String = when (this) {
    is PdfToolSource.LibraryDocument -> documentId
    is PdfToolSource.DeviceUri -> "device:$uriString"
    is PdfToolSource.AppFile -> "file:$filePath"
}
