package `in`.c1ph3rj.scanly.feature.tools.pdf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
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
import `in`.c1ph3rj.scanly.feature.tools.rememberToolSplitLayout
import `in`.c1ph3rj.scanly.feature.tools.ToolSplitPanes
import `in`.c1ph3rj.scanly.feature.tools.ToolSplitMaxWidth
import `in`.c1ph3rj.scanly.feature.tools.ToolColumnMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.PdfImportLook
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolCard
import `in`.c1ph3rj.scanly.feature.tools.ToolFieldLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolLargeTitle
import `in`.c1ph3rj.scanly.feature.tools.ToolPaperPage
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSecondaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSectionLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolTextField
import `in`.c1ph3rj.scanly.feature.tools.ToolTokens
import `in`.c1ph3rj.scanly.feature.tools.ToolTopBar
import `in`.c1ph3rj.scanly.feature.tools.ToolWorkingState
import kotlinx.coroutines.flow.collectLatest

/** The screens of Add to library, in order: Choose a file, Pages and options, Adding, Done. */
private enum class PdfImportStep { Pick, Pages, Working, Done }

@Composable
fun PdfImportRoute(
    onNavigateUp: () -> Unit,
    onOpenDocument: (String) -> Unit,
    initialSource: PdfToolSource? = null,
) {
    val viewModel: PdfImportViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // rememberSaveable: rotation must keep the picker open after "Change" and closed after a pick.
    var showPicker by rememberSaveable { mutableStateOf(initialSource == null) }
    var returnToPagesOnPickerBack by rememberSaveable { mutableStateOf(false) }

    // Opened from the reader's menu with that PDF: skip the picker, and back returns to the reader.
    val fromReader = initialSource != null
    var initialSourceApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialSource) {
        if (initialSource != null && !initialSourceApplied) {
            initialSourceApplied = true
            viewModel.onSourceChosen(initialSource)
        }
    }
    val step = when {
        uiState.result != null -> PdfImportStep.Done
        uiState.isImporting -> PdfImportStep.Working
        showPicker || uiState.source == null -> PdfImportStep.Pick
        else -> PdfImportStep.Pages
    }

    val onSourcePicked = { source: PdfToolSource ->
        returnToPagesOnPickerBack = false
        showPicker = false
        viewModel.onSourceChosen(source)
    }
    val devicePicker = rememberSinglePdfPicker { uri ->
        onSourcePicked(PdfToolSource.DeviceUri(uri.toString(), uriDisplayName(context, uri)))
    }
    val openPicker = {
        returnToPagesOnPickerBack = true
        showPicker = true
    }
    // Back from the pages step is a step back, not "Change": back again on the picker leaves the
    // tool instead of bouncing to the pages step forever.
    val backToPicker = {
        returnToPagesOnPickerBack = false
        showPicker = true
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest {
            if (it is PdfImportEvent.ShowMessage) snackbarHostState.showSnackbar(it.message)
        }
    }

    val onPickerBack = {
        if (shouldReturnFromPdfPickerToPreviousStep(uiState.source != null, returnToPagesOnPickerBack)) {
            returnToPagesOnPickerBack = false
            showPicker = false
        } else {
            onNavigateUp()
        }
    }
    BackHandler(enabled = step == PdfImportStep.Pick && uiState.source != null, onBack = onPickerBack)
    BackHandler(enabled = step == PdfImportStep.Pages, onBack = if (fromReader) onNavigateUp else backToPicker)
    // Leaving halfway would throw the half-built document away; stay until it is done.
    BackHandler(enabled = step == PdfImportStep.Working) { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        // The pages step splits into two panes on wide landscape windows; the rest stay one column.
        val split = rememberToolSplitLayout() && step == PdfImportStep.Pages
        Column(modifier = Modifier.widthIn(max = if (split) ToolSplitMaxWidth else ToolColumnMaxWidth).fillMaxSize()) {
            when (step) {
                // Library documents are already in Scanly, so only files on the phone are offered.
                PdfImportStep.Pick -> PdfFilePickerScreen(
                    title = "Add PDF to library",
                    subtitle = "Turn a PDF into a Scanly document you can edit, sign and search.",
                    stepText = "Step 1 of 3 · Choose a file",
                    documents = emptyList(),
                    onBack = onPickerBack,
                    onPickDevice = { devicePicker.launch(arrayOf("application/pdf")) },
                    onPickLibrary = {},
                )
                PdfImportStep.Pages -> PagesStep(
                    uiState = uiState,
                    onBack = if (fromReader) onNavigateUp else backToPicker,
                    onChange = openPicker,
                    onRequestThumbnail = viewModel::requestThumbnail,
                    onTogglePage = viewModel::togglePage,
                    onSelectionChange = viewModel::setSelectedPages,
                    onSelectAll = viewModel::selectAll,
                    onSelectNone = viewModel::selectNone,
                    onTitleChange = viewModel::setTitle,
                    onLookSelected = viewModel::setLook,
                    onPasswordChange = viewModel::setPassword,
                    onUnlock = viewModel::unlock,
                    onImport = viewModel::import,
                )
                PdfImportStep.Working -> WorkingStep(uiState = uiState)
                PdfImportStep.Done -> DoneStep(
                    uiState = uiState,
                    onClose = onNavigateUp,
                    onOpenDocument = { uiState.result?.let { onOpenDocument(it.documentId) } },
                    onImportAnother = {
                        viewModel.reset()
                        returnToPagesOnPickerBack = false
                        showPicker = true
                    },
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
        )
    }
}

/** Step 2: file summary, page grid with All/None, name, look, and "Add to library". */
@Composable
private fun ColumnScope.PagesStep(
    uiState: PdfImportUiState,
    onBack: () -> Unit,
    onChange: () -> Unit,
    onRequestThumbnail: (Int) -> Unit,
    onTogglePage: (Int) -> Unit,
    onSelectionChange: (Set<Int>) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onTitleChange: (String) -> Unit,
    onLookSelected: (PdfImportLook) -> Unit,
    onPasswordChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onImport: () -> Unit,
) {
    val selectedCount = uiState.selectedPages.size
    val gridState = rememberLazyGridState()
    val pageOrder = remember(uiState.pageCount) { (0 until uiState.pageCount).toList() }

    val nameField: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ToolFieldLabel(text = "Document name")
            ToolTextField(
                value = uiState.title,
                onValueChange = onTitleChange,
                placeholder = "Name this document",
                isError = uiState.title.isBlank(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
            )
        }
    }

    ToolTopBar(onBack = onBack, stepText = "Step 2 of 3 · Pages and name")
    // Wide landscape: pages on the start side, the settings beside them instead of below.
    val split = rememberToolSplitLayout() && uiState.isReady && !uiState.needsPassword
    val grid: @Composable (Modifier) -> Unit = { gridModifier ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(PageCellMinWidth),
            state = gridState,
            modifier = gridModifier
                .gridDragSelect(
                    state = gridState,
                    order = pageOrder,
                    selection = uiState.selectedPages,
                    idForKey = { key -> selectableId(key, PageKeyPrefix) },
                    onSelectionChange = onSelectionChange,
                    contentPaddingStart = ToolTokens.ScreenPadding,
                    contentPaddingTop = GridTopPadding,
                ),
            contentPadding = PaddingValues(
                start = ToolTokens.ScreenPadding,
                end = ToolTokens.ScreenPadding,
                top = GridTopPadding,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            fullWidth("title") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ToolLargeTitle(title = "Add to library")
                    FileSummaryLine(
                        name = uiState.source?.label().orEmpty(),
                        pageCount = uiState.pageCount,
                        onChange = onChange,
                    )
                }
            }

            when {
                uiState.needsPassword -> fullWidth("password") {
                    PasswordPanel(
                        password = uiState.password,
                        busy = uiState.isOpening,
                        onPasswordChange = onPasswordChange,
                        onUnlock = onUnlock,
                    )
                }
                !uiState.isReady -> fullWidth("opening") {
                    Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
                        if (uiState.isOpening) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                }
                else -> {
                    if (!split) {
                        fullWidth("name") { nameField() }
                    }
                    fullWidth("pages-header") {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ToolSectionLabel(
                                    text = formatPdfToImagesPagesLabel(selectedCount, uiState.pageCount),
                                    modifier = Modifier.weight(1f),
                                )
                                SelectAllAction(
                                    selectedCount = selectedCount,
                                    totalCount = uiState.pageCount,
                                    onSelectAll = onSelectAll,
                                    onSelectNone = onSelectNone,
                                )
                            }
                            if (uiState.pageCount > 2) {
                                GridHint(text = "Tap a page, or long-press and drag across several.")
                            }
                        }
                    }
                    items(count = uiState.pageCount, key = { index -> "$PageKeyPrefix$index" }) { index ->
                        LaunchedEffect(index) { onRequestThumbnail(index) }
                        PageCell(
                            bitmap = uiState.thumbnails[index],
                            label = (index + 1).toString(),
                            selected = index in uiState.selectedPages,
                            contentDescription = "Page ${index + 1}",
                            onToggle = { onTogglePage(index) },
                        )
                    }
                    if (!split) {
                        fullWidth("look") {
                            LookSection(selected = uiState.look, onSelected = onLookSelected)
                        }
                    }
                }
            }
        }
    }
    if (split) {
        ToolSplitPanes(
            start = { grid(Modifier.fillMaxSize()) },
            end = {
                nameField()
                LookSection(selected = uiState.look, onSelected = onLookSelected)
            },
            startWeight = 0.58f,
            endSpacing = 8.dp,
        )
    } else {
        grid(Modifier.weight(1f).fillMaxWidth())
    }

    ToolBottomBar {
        ToolPrimaryButton(
            text = formatPdfImportButtonLabel(selectedCount),
            onClick = onImport,
            enabled = uiState.canImport,
            icon = Icons.Outlined.LibraryAdd,
        )
        Text(
            text = "Your PDF file stays as it is; Scanly keeps its own copy",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LookSection(selected: PdfImportLook, onSelected: (PdfImportLook) -> Unit) {
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToolFieldLabel(text = "Look")
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PdfImportLook.entries.forEach { look ->
                val isSelected = look == selected
                // onClick on ToolCard (Surface) so the ripple respects the rounded corners.
                ToolCard(
                    selected = isSelected,
                    onClick = { onSelected(look) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { this.selected = isSelected; role = Role.RadioButton },
                ) {
                    Column(
                        modifier = Modifier.heightIn(min = 84.dp).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = look.label,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = look.description,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Adding: the page being added right now and determinate progress ("3 of 12"). */
@Composable
private fun WorkingStep(uiState: PdfImportUiState) {
    val pages = uiState.importPages
    val total = pages.size.coerceAtLeast(1)
    val position = uiState.importedCount.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val currentPage = pages.getOrNull(position)
    // Until the current page's thumbnail lands, the latest earlier one stays up.
    val shownBitmap = (position downTo 0).firstNotNullOfOrNull { i ->
        pages.getOrNull(i)?.let { uiState.thumbnails[it] }
    }
    ToolWorkingState(
        title = "Adding to library…",
        caption = "${(uiState.importedCount + 1).coerceAtMost(total)} of $total",
        progress = uiState.importedCount.toFloat() / total,
        preview = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ToolPaperPage(
                    bitmap = shownBitmap,
                    loading = shownBitmap == null,
                    modifier = Modifier.size(width = 150.dp, height = 212.dp),
                )
                if (currentPage != null) {
                    Text(
                        text = "Page ${currentPage + 1}",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/** Step 3: what was added, then Open document (primary) or Add another PDF. */
@Composable
private fun ColumnScope.DoneStep(
    uiState: PdfImportUiState,
    onClose: () -> Unit,
    onOpenDocument: () -> Unit,
    onImportAnother: () -> Unit,
) {
    val result = uiState.result ?: return
    // The document is already saved, so back simply leaves the tool, like the close button.
    BackHandler(onBack = onClose)

    ToolTopBar(
        onBack = onClose,
        stepText = "Step 3 of 3 · Done",
        backIcon = Icons.Outlined.Close,
        backContentDescription = "Close",
        centerStep = true,
    )
    val firstPage = uiState.importPages.firstOrNull()
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = ToolTokens.ScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ToolLargeTitle(
            title = "Added to your library",
            subtitle = "${uiState.title.trim()} · ${formatPdfPageCount(result.pageCount)}. " +
                "Crop, filter, sign or search it like any scan.",
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val cover = firstPage?.let { uiState.thumbnails[it] }
            ToolPaperPage(
                bitmap = cover,
                loading = cover == null,
                modifier = Modifier.size(width = 170.dp, height = 240.dp),
            )
        }
    }

    ToolBottomBar {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolSecondaryButton(
                text = "Another PDF",
                onClick = onImportAnother,
                icon = Icons.Outlined.PictureAsPdf,
                modifier = Modifier.weight(1f),
            )
            ToolPrimaryButton(
                text = "Open document",
                onClick = onOpenDocument,
                icon = Icons.Outlined.Description,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** "Add 12 pages", "Add 1 page", or "Add to library" when nothing is picked yet. */
internal fun formatPdfImportButtonLabel(selectedCount: Int): String = when (selectedCount) {
    0 -> "Add to library"
    1 -> "Add 1 page"
    else -> "Add $selectedCount pages"
}
