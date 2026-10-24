package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.IndeterminateCheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import `in`.c1ph3rj.scanly.feature.tools.rememberToolSplitLayout
import `in`.c1ph3rj.scanly.feature.tools.ToolSplitPanes
import `in`.c1ph3rj.scanly.feature.tools.ToolSplitMaxWidth
import `in`.c1ph3rj.scanly.feature.tools.ToolColumnMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.domain.model.PdfImageFormat
import `in`.c1ph3rj.scanly.domain.model.PdfImageResolution
import `in`.c1ph3rj.scanly.domain.model.PdfPageImage
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolCard
import `in`.c1ph3rj.scanly.feature.tools.ToolFieldLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolLargeTitle
import `in`.c1ph3rj.scanly.feature.tools.ToolNote
import `in`.c1ph3rj.scanly.feature.tools.ToolPaperPage
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSecondaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSectionLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolShortcutTile
import `in`.c1ph3rj.scanly.feature.tools.ToolTextAction
import `in`.c1ph3rj.scanly.feature.tools.ToolTextField
import `in`.c1ph3rj.scanly.feature.tools.ToolTokens
import `in`.c1ph3rj.scanly.feature.tools.ToolTopBar
import `in`.c1ph3rj.scanly.feature.tools.ToolWorkingState
import kotlinx.coroutines.flow.collectLatest

/** The screens of PDF to JPG, in order: Choose a file, Pages and format, Saving, Done. */
private enum class PdfToImagesStep { Pick, Pages, Working, Done }

/** Grid cells grow on wide screens but stay three-up on phones, like the design. */
internal val PageCellMinWidth = 96.dp

@Composable
fun PdfToImagesRoute(onNavigateUp: () -> Unit, initialSource: PdfToolSource? = null) {
    val viewModel: PdfToImagesViewModel = hiltViewModel()
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
        uiState.result != null -> PdfToImagesStep.Done
        uiState.isExporting -> PdfToImagesStep.Working
        showPicker || uiState.source == null -> PdfToImagesStep.Pick
        else -> PdfToImagesStep.Pages
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
            if (it is PdfToImagesEvent.ShowMessage) snackbarHostState.showSnackbar(it.message)
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
    BackHandler(enabled = step == PdfToImagesStep.Pick && uiState.source != null, onBack = onPickerBack)
    BackHandler(enabled = step == PdfToImagesStep.Pages, onBack = if (fromReader) onNavigateUp else backToPicker)
    // An export can't be cancelled halfway (pages already in the gallery would be orphaned).
    BackHandler(enabled = step == PdfToImagesStep.Working) { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Pages and result split into two panes on wide landscape windows; the rest stay one column.
        val split = rememberToolSplitLayout() && (step == PdfToImagesStep.Pages || step == PdfToImagesStep.Done)
        Column(modifier = Modifier.widthIn(max = if (split) ToolSplitMaxWidth else ToolColumnMaxWidth).fillMaxSize()) {
            when (step) {
                PdfToImagesStep.Pick -> PdfFilePickerScreen(
                    title = "PDF to JPG",
                    subtitle = "Turn pages into images for chats, slides or your gallery.",
                    stepText = "Step 1 of 3 · Choose a file",
                    documents = uiState.libraryDocuments,
                    onBack = onPickerBack,
                    onPickDevice = { devicePicker.launch(arrayOf("application/pdf")) },
                    onPickLibrary = { document ->
                        onSourcePicked(PdfToolSource.LibraryDocument(document.id, document.title))
                    },
                )
                PdfToImagesStep.Pages -> PagesStep(
                    uiState = uiState,
                    onBack = if (fromReader) onNavigateUp else backToPicker,
                    onChange = openPicker,
                    onRequestThumbnail = viewModel::requestThumbnail,
                    onTogglePage = viewModel::togglePage,
                    onSelectionChange = viewModel::setSelectedPages,
                    onSelectAll = viewModel::selectAll,
                    onSelectNone = viewModel::selectNone,
                    onFormatSelected = viewModel::setFormat,
                    onResolutionSelected = viewModel::setResolution,
                    onPasswordChange = viewModel::setPassword,
                    onUnlock = viewModel::unlock,
                    onExport = viewModel::export,
                )
                PdfToImagesStep.Working -> WorkingStep(uiState = uiState)
                PdfToImagesStep.Done -> DoneStep(
                    uiState = uiState,
                    onClose = onNavigateUp,
                    onRequestThumbnail = viewModel::requestThumbnail,
                    onToggleShare = viewModel::toggleShare,
                    onShareSelectionChange = viewModel::setShareSelection,
                    onExportAgain = viewModel::exportAgain,
                    onConvertAnother = {
                        viewModel.exportAgain()
                        returnToPagesOnPickerBack = false
                        showPicker = true
                    },
                    onOpenGallery = {
                        val first = uiState.shareTargets.firstOrNull() ?: return@DoneStep
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(first.contentUri), uiState.result?.options?.format?.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            viewModel.showMessage("No gallery app found. The images are in Pictures/Scanly.")
                        }
                    },
                    onShare = {
                        val targets = uiState.shareTargets
                        val format = uiState.result?.options?.format ?: PdfImageFormat.JPG
                        if (targets.isNotEmpty()) {
                            sharePreparedFiles(
                                context = context,
                                artifact = ShareArtifact(
                                    mimeType = format.mimeType,
                                    title = if (targets.size == 1) "Image" else "${targets.size} images",
                                    filePaths = targets.map(PdfPageImage::filePath),
                                ),
                            )
                        }
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

/** Step 2: file summary, page grid with All/None, format, resolution, and "Export". */
@Composable
private fun ColumnScope.PagesStep(
    uiState: PdfToImagesUiState,
    onBack: () -> Unit,
    onChange: () -> Unit,
    onRequestThumbnail: (Int) -> Unit,
    onTogglePage: (Int) -> Unit,
    onSelectionChange: (Set<Int>) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onFormatSelected: (PdfImageFormat) -> Unit,
    onResolutionSelected: (PdfImageResolution) -> Unit,
    onPasswordChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onExport: () -> Unit,
) {
    val name = uiState.source?.label().orEmpty()
    val selectedCount = uiState.selectedPages.size
    val gridState = rememberLazyGridState()
    val pageOrder = remember(uiState.pageCount) { (0 until uiState.pageCount).toList() }

    val settings: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormatSection(selected = uiState.options.format, onSelected = onFormatSelected)
            ResolutionSection(
                selected = uiState.options.resolution,
                estimates = uiState.estimates,
                onSelected = onResolutionSelected,
            )
        }
    }

    ToolTopBar(onBack = onBack, stepText = "Step 2 of 3 · Pages and format")
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
                    ToolLargeTitle(title = "PDF to JPG")
                    FileSummaryLine(
                        name = name,
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
                        fullWidth("format") { settings() }
                    }
                }
            }
        }
    }
    if (split) {
        ToolSplitPanes(
            start = { grid(Modifier.fillMaxSize()) },
            end = { settings() },
            startWeight = 0.58f,
        )
    } else {
        grid(Modifier.weight(1f).fillMaxWidth())
    }

    ToolBottomBar {
        ToolPrimaryButton(
            text = formatPdfToImagesExportLabel(selectedCount, uiState.estimateEach),
            onClick = onExport,
            enabled = uiState.isReady && selectedCount > 0,
            icon = Icons.Outlined.Image,
        )
        Text(
            text = "Images show up in your gallery right away",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "Lease agreement · 6 pages · Change" — the name ellipsizes, "Change" always fits. */
@Composable
internal fun FileSummaryLine(name: String, pageCount: Int, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val meta = listOfNotNull(
            name.takeIf { it.isNotBlank() },
            pageCount.takeIf { it > 0 }?.let(::formatPdfPageCount),
        ).joinToString(" · ")
        Text(
            text = if (meta.isBlank()) "" else "$meta ·",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        ToolTextAction(label = "Change", onClick = onChange)
    }
}

@Composable
internal fun PasswordPanel(
    password: String,
    busy: Boolean,
    onPasswordChange: (String) -> Unit,
    onUnlock: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToolNote(text = "This PDF is password protected. Enter its password to see the pages.")
        ToolFieldLabel(text = "PDF password")
        ToolTextField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = "Enter the password",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
        )
        ToolSecondaryButton(
            text = "Unlock",
            onClick = onUnlock,
            enabled = password.isNotBlank(),
            loading = busy,
        )
    }
}

/**
 * Inline accent action whose checkbox mirrors the selection (empty, partial, full), the same
 * style as "Change". Nudged right so its text lines up with the grid's edge, like a trailing
 * action in a list header.
 */
@Composable
internal fun SelectAllAction(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
) {
    val allSelected = totalCount > 0 && selectedCount == totalCount
    ToolTextAction(
        label = if (allSelected) "Deselect all" else "Select all",
        onClick = if (allSelected) onSelectNone else onSelectAll,
        icon = when {
            allSelected -> Icons.Outlined.CheckBox
            selectedCount > 0 -> Icons.Outlined.IndeterminateCheckBox
            else -> Icons.Outlined.CheckBoxOutlineBlank
        },
        modifier = Modifier.offset(x = 10.dp),
    )
}

/**
 * A page as paper inside a frame. Selected cells get an accent ring and a filled check;
 * unselected ones fade back so the selection reads at a glance.
 */
@Composable
internal fun PageCell(
    bitmap: Bitmap?,
    label: String,
    selected: Boolean,
    contentDescription: String,
    onToggle: () -> Unit,
    /** False while nothing is picked on the Done grid: every image stays bright, no empty rings. */
    showUnselectedState: Boolean = true,
) {
    val frameShape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(frameShape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PageFrameRatio)
                .clip(frameShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = frameShape,
                )
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            ToolPaperPage(
                bitmap = bitmap,
                loading = bitmap == null,
                elevation = 0.dp,
                modifier = Modifier.alpha(if (selected || !showUnselectedState) 1f else 0.5f),
            )
            if (selected || showUnselectedState) {
                SelectionMark(selected = selected, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(2.dp)
            .size(26.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun FormatSection(selected: PdfImageFormat, onSelected: (PdfImageFormat) -> Unit) {
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToolFieldLabel(text = "Format")
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PdfImageFormat.entries.forEach { format ->
                val isSelected = format == selected
                // onClick on ToolCard (Surface) so the ripple respects the 14dp corners.
                ToolCard(
                    selected = isSelected,
                    onClick = { onSelected(format) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { this.selected = isSelected; role = Role.RadioButton },
                ) {
                    Column(
                        modifier = Modifier.heightIn(min = 84.dp).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = format.label,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = format.description,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolutionSection(
    selected: PdfImageResolution,
    estimates: Map<PdfImageResolution, Long>,
    onSelected: (PdfImageResolution) -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToolFieldLabel(text = "Resolution")
        ToolCard(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            Column {
                PdfImageResolution.entries.forEachIndexed { index, resolution ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ResolutionRow(
                        resolution = resolution,
                        selected = resolution == selected,
                        estimateBytes = estimates[resolution],
                        onClick = { onSelected(resolution) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResolutionRow(
    resolution: PdfImageResolution,
    selected: Boolean,
    estimateBytes: Long?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 68.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioDot(selected = selected)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = resolution.label,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = resolution.description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = estimateBytes?.let { "~${formatImageBytes(it)} each" } ?: "…",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(22.dp)
            .border(2.dp, if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).background(accent, CircleShape))
    }
}

/**
 * Saving: the page being saved right now, its page number, and determinate progress
 * ("12 of 100 · PNG · 2480 px"). Until the current page's thumbnail lands, the latest earlier
 * one stays up so the sheet never flashes blank.
 */
@Composable
private fun WorkingStep(uiState: PdfToImagesUiState) {
    val pages = uiState.exportPages
    val total = uiState.exportTotal.coerceAtLeast(1)
    val position = uiState.exportedCount.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val currentPage = pages.getOrNull(position)
    val shownBitmap = (position downTo 0).firstNotNullOfOrNull { i ->
        pages.getOrNull(i)?.let { uiState.thumbnails[it] }
    }
    ToolWorkingState(
        title = "Saving images…",
        caption = listOf(
            "${(uiState.exportedCount + 1).coerceAtMost(total)} of $total",
            uiState.options.format.label,
            "${uiState.options.resolution.widthPx} px",
        ).joinToString(" · "),
        progress = uiState.exportedCount.toFloat() / total,
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

/** Step 3: saved summary, tap-to-pick share grid, follow-up tiles, Open gallery + Share. */
@Composable
private fun ColumnScope.DoneStep(
    uiState: PdfToImagesUiState,
    onClose: () -> Unit,
    onRequestThumbnail: (Int) -> Unit,
    onToggleShare: (Int) -> Unit,
    onShareSelectionChange: (Set<Int>) -> Unit,
    onExportAgain: () -> Unit,
    onConvertAnother: () -> Unit,
    onOpenGallery: () -> Unit,
    onShare: () -> Unit,
) {
    val export = uiState.result ?: return
    // Images are already in the gallery, so back simply leaves the tool, like the close button.
    BackHandler(onBack = onClose)

    ToolTopBar(
        onBack = onClose,
        stepText = "Step 3 of 3 · Done",
        backIcon = Icons.Outlined.Close,
        backContentDescription = "Close",
        centerStep = true,
    )
    val imageCount = export.images.size
    val gridState = rememberLazyGridState()
    val imageOrder = remember(export) { export.images.map(PdfPageImage::pageIndex) }
    LazyVerticalGrid(
        state = gridState,
        // Few images get big previews so the screen isn't mostly empty; many fall back to the
        // compact three-up grid of the pages step.
        columns = when {
            imageCount <= 1 -> GridCells.Fixed(1)
            // Wide windows: steady 180 dp previews rather than two half-screen A4 pages.
            rememberToolSplitLayout() -> GridCells.Adaptive(180.dp)
            imageCount <= 4 -> GridCells.Fixed(2)
            else -> GridCells.Adaptive(PageCellMinWidth)
        },
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .gridDragSelect(
                state = gridState,
                order = imageOrder,
                selection = uiState.shareSelection,
                idForKey = { key -> selectableId(key, ImageKeyPrefix) },
                onSelectionChange = onShareSelectionChange,
                contentPaddingStart = ToolTokens.ScreenPadding,
                contentPaddingTop = GridTopPadding,
            ),
        contentPadding = PaddingValues(
            start = ToolTokens.ScreenPadding,
            end = ToolTokens.ScreenPadding,
            top = GridTopPadding,
            bottom = 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(if (imageCount <= 4) 16.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        fullWidth("title") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ToolLargeTitle(
                    title = formatPdfToImagesDoneTitle(export.images.size),
                    subtitle = formatPdfToImagesDoneSubtitle(export),
                )
                if (imageCount > 1) {
                    ShareSelectionLine(
                        selectedCount = uiState.shareSelection.size,
                        totalCount = imageCount,
                        onClear = { onShareSelectionChange(emptySet()) },
                    )
                }
            }
        }
        items(export.images, key = { image -> "$ImageKeyPrefix${image.pageIndex}" }) { image ->
            LaunchedEffect(image.pageIndex) { onRequestThumbnail(image.pageIndex) }
            // A lone image would fill the whole width at A4 height; cap it and centre it.
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = if (imageCount == 1) Modifier.widthIn(max = 240.dp) else Modifier) {
                    PageCell(
                        bitmap = uiState.thumbnails[image.pageIndex],
                        // File names all start with the same document name and truncate to
                        // nothing useful; page + size tells the images apart.
                        label = formatPdfToImagesCellLabel(image),
                        selected = image.pageIndex in uiState.shareSelection,
                        contentDescription = image.fileName,
                        onToggle = { onToggleShare(image.pageIndex) },
                        showUnselectedState = uiState.shareSelection.isNotEmpty(),
                    )
                }
            }
        }
        fullWidth("next") {
            // Intrinsic height so both tiles match the taller one when a caption wraps.
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ToolShortcutTile(
                    icon = Icons.Outlined.Tune,
                    title = "Export again",
                    caption = "PNG or print size",
                    onClick = onExportAgain,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                ToolShortcutTile(
                    icon = Icons.Outlined.PictureAsPdf,
                    title = "Another PDF",
                    caption = "Pick a different file",
                    onClick = onConvertAnother,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }

    ToolBottomBar {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolSecondaryButton(
                text = "Open gallery",
                onClick = onOpenGallery,
                icon = Icons.Outlined.PhotoLibrary,
                modifier = Modifier.weight(1f),
            )
            ToolPrimaryButton(
                text = formatPdfToImagesShareLabel(uiState.shareSelection.size, export.images.size),
                onClick = onShare,
                icon = Icons.Outlined.Share,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Under the Done title: how to pick a subset, or, once picking, "6 of 20 selected" with a
 * Clear action to go back to sharing everything.
 */
@Composable
private fun ShareSelectionLine(selectedCount: Int, totalCount: Int, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = ToolTokens.MinTouch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selectedCount == 0) {
                "Tap images, or long-press and drag, to share only some."
            } else {
                "$selectedCount of $totalCount selected"
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = if (selectedCount == 0) FontWeight.Normal else FontWeight.SemiBold,
            color = if (selectedCount == 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        if (selectedCount > 0) ToolTextAction(label = "Clear", onClick = onClear)
    }
}

@Composable
internal fun GridHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal const val PageKeyPrefix = "page-"
private const val ImageKeyPrefix = "image-"
internal val GridTopPadding = 2.dp

/** Grid keys look like "page-3"; anything else (headers, footers) is not selectable. */
internal fun selectableId(key: Any, prefix: String): Int? =
    (key as? String)?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toIntOrNull()

/** Page frames are a little wider than A4 so landscape pages still read at a glance. */
internal const val PageFrameRatio = 0.74f

internal fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}
