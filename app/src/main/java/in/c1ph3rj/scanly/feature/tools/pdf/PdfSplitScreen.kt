package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolCard
import `in`.c1ph3rj.scanly.feature.tools.ToolColumnMaxWidth
import `in`.c1ph3rj.scanly.feature.tools.ToolFieldLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolLargeTitle
import `in`.c1ph3rj.scanly.feature.tools.ToolNote
import `in`.c1ph3rj.scanly.feature.tools.ToolPaperPage
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSecondaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSectionLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolSegmented
import `in`.c1ph3rj.scanly.feature.tools.ToolTextAction
import `in`.c1ph3rj.scanly.feature.tools.ToolTextField
import `in`.c1ph3rj.scanly.feature.tools.ToolTokens
import `in`.c1ph3rj.scanly.feature.tools.ToolTopBar
import `in`.c1ph3rj.scanly.feature.tools.ToolWorkingState
import `in`.c1ph3rj.scanly.feature.tools.isToolDarkTheme
import kotlinx.coroutines.flow.collectLatest

/** Steps of Split PDF. Working and Done follow the ViewModel so they survive rotation. */
private enum class SplitStep { Pick, Ranges, Working, Done }

@Composable
fun PdfSplitRoute(
    onNavigateUp: () -> Unit,
    onPreviewPdf: (ExportArtifact) -> Unit,
    initialSource: PdfToolSource? = null,
) {
    val viewModel: PdfSplitViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPicker by rememberSaveable { mutableStateOf(initialSource == null) }
    var returnToRangesOnPickerBack by rememberSaveable { mutableStateOf(false) }

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
        uiState.hasResult -> SplitStep.Done
        uiState.isSplitting -> SplitStep.Working
        showPicker || uiState.source == null -> SplitStep.Pick
        else -> SplitStep.Ranges
    }

    val onSourcePicked = { source: PdfToolSource ->
        returnToRangesOnPickerBack = false
        showPicker = false
        viewModel.onSourceChosen(source)
    }
    val devicePicker = rememberSinglePdfPicker { uri ->
        onSourcePicked(PdfToolSource.DeviceUri(uri.toString(), uriDisplayName(context, uri)))
    }
    val openPicker = {
        returnToRangesOnPickerBack = true
        showPicker = true
    }
    val backToPicker = {
        returnToRangesOnPickerBack = false
        showPicker = true
    }
    val onPickerBack = {
        if (shouldReturnFromPdfPickerToPreviousStep(uiState.source != null, returnToRangesOnPickerBack)) {
            returnToRangesOnPickerBack = false
            showPicker = false
        } else {
            onNavigateUp()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PdfSplitEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is PdfSplitEvent.Share -> sharePreparedFiles(context, event.artifact)
            }
        }
    }

    BackHandler(enabled = step == SplitStep.Pick && uiState.source != null, onBack = onPickerBack)
    BackHandler(enabled = step == SplitStep.Ranges, onBack = if (fromReader) onNavigateUp else backToPicker)
    // Parts already written would be orphaned halfway, like Merge: the split runs to the end.
    BackHandler(enabled = step == SplitStep.Working) { }
    BackHandler(enabled = step == SplitStep.Done, onBack = onNavigateUp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.widthIn(max = ToolColumnMaxWidth).fillMaxSize()) {
            when (step) {
                SplitStep.Pick -> PdfFilePickerScreen(
                    title = stringResource(R.string.pdf_split_title),
                    subtitle = stringResource(R.string.pdf_split_subtitle),
                    stepText = stringResource(R.string.pdf_compress_step_1_of_3_choose),
                    documents = uiState.libraryDocuments,
                    onBack = onPickerBack,
                    onPickDevice = { devicePicker.launch(arrayOf("application/pdf")) },
                    onPickLibrary = { document ->
                        onSourcePicked(PdfToolSource.LibraryDocument(document.id, document.title))
                    },
                )
                SplitStep.Ranges -> RangesStep(
                    uiState = uiState,
                    onBack = if (fromReader) onNavigateUp else backToPicker,
                    onChange = openPicker,
                    viewModel = viewModel,
                )
                SplitStep.Working -> ToolWorkingState(
                    title = stringResource(R.string.pdf_split_splitting),
                    caption = stringResource(
                        R.string.pdf_split_writing_part,
                        (uiState.splitDone + 1).coerceAtMost(uiState.splitTotal.coerceAtLeast(1)),
                        uiState.splitTotal.coerceAtLeast(1),
                    ),
                    progress = if (uiState.splitTotal > 0) uiState.splitDone.toFloat() / uiState.splitTotal else null,
                )
                SplitStep.Done -> DoneStep(
                    uiState = uiState,
                    onClose = onNavigateUp,
                    onRename = viewModel::renameOutput,
                    onOpen = { index -> viewModel.outputForPreview(index)?.let(onPreviewPdf) },
                    onRequestThumbnail = viewModel::requestThumbnail,
                    onSaveAsZip = viewModel::setSaveAsZip,
                    onSaveAll = viewModel::saveAll,
                    onShareAll = viewModel::shareAll,
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

// ─── Step 2 · Where to split ─────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.RangesStep(
    uiState: PdfSplitUiState,
    onBack: () -> Unit,
    onChange: () -> Unit,
    viewModel: PdfSplitViewModel,
) {
    val gridState = rememberLazyGridState()
    val pageCount = uiState.pageCount
    val pageOrder = remember(pageCount) { (0 until pageCount).toList() }
    val extract = uiState.mode == PdfSplitMode.Extract && uiState.isReady && pageCount >= 2
    val plan = uiState.plan

    ToolTopBar(onBack = onBack, stepText = stringResource(R.string.pdf_split_step_2))
    LazyVerticalGrid(
        columns = GridCells.Adaptive(PageCellMinWidth),
        state = gridState,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .then(
                if (extract) {
                    Modifier.gridDragSelect(
                        state = gridState,
                        order = pageOrder,
                        selection = uiState.selectedPages,
                        idForKey = { key -> selectableId(key, PageKeyPrefix) },
                        onSelectionChange = viewModel::setSelectedPages,
                        contentPaddingStart = ToolTokens.ScreenPadding,
                        contentPaddingTop = GridTopPadding,
                    )
                } else {
                    Modifier
                },
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
                ToolLargeTitle(title = stringResource(R.string.pdf_split_title))
                FileSummaryLine(name = uiState.source?.label().orEmpty(), pageCount = pageCount, onChange = onChange)
            }
        }
        when {
            uiState.needsPassword -> fullWidth("password") {
                PasswordPanel(
                    password = uiState.password,
                    busy = uiState.isOpening,
                    onPasswordChange = viewModel::setPassword,
                    onUnlock = viewModel::unlock,
                )
            }
            !uiState.isReady -> fullWidth("opening") {
                Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
                    if (uiState.isOpening) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            }
            pageCount < 2 -> fullWidth("single") {
                ToolNote(
                    text = stringResource(R.string.pdf_split_single_page),
                    icon = Icons.Outlined.Warning,
                    warning = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            else -> {
                fullWidth("modes") {
                    val labels = PdfSplitMode.entries.associateWith { splitModeLabel(it) }
                    ToolSegmented(
                        options = PdfSplitMode.entries,
                        selected = uiState.mode,
                        onSelect = viewModel::setMode,
                        label = { labels.getValue(it) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
                when (uiState.mode) {
                    PdfSplitMode.Custom -> customContent(uiState, viewModel)
                    PdfSplitMode.EveryN -> everyNContent(uiState, viewModel)
                    PdfSplitMode.Extract -> extractContent(uiState, viewModel)
                }
                if (uiState.wasLocked) {
                    fullWidth("unlocked-note") { ToolNote(text = stringResource(R.string.pdf_split_no_password)) }
                }
            }
        }
    }

    ToolBottomBar {
        ToolPrimaryButton(
            text = splitButtonLabel(uiState, plan),
            onClick = viewModel::split,
            enabled = uiState.isReady && plan.isNotEmpty(),
            icon = Icons.AutoMirrored.Outlined.CallSplit,
        )
    }
}

@Composable
private fun splitModeLabel(mode: PdfSplitMode): String = when (mode) {
    PdfSplitMode.Custom -> stringResource(R.string.pdf_split_mode_custom)
    PdfSplitMode.EveryN -> stringResource(R.string.pdf_split_mode_every_n)
    PdfSplitMode.Extract -> stringResource(R.string.pdf_split_mode_extract)
}

@Composable
private fun splitButtonLabel(uiState: PdfSplitUiState, plan: List<PdfSplitOutputPlan>): String {
    if (!uiState.isReady || uiState.pageCount < 2) return stringResource(R.string.pdf_split_title)
    if (plan.isEmpty()) {
        return when (uiState.mode) {
            PdfSplitMode.Extract -> stringResource(R.string.pdf_split_choose_pages)
            else -> stringResource(R.string.pdf_split_fix_ranges)
        }
    }
    return when {
        uiState.mode == PdfSplitMode.Extract -> stringResource(
            R.string.pdf_split_extract_button,
            formatPdfPageCount(uiState.selectedPages.size),
            pluralStringResource(R.plurals.pdf_split_pdf_count, plan.size, plan.size),
        )
        uiState.mode == PdfSplitMode.Custom && uiState.mergeRanges -> stringResource(
            R.string.pdf_split_create_one,
            formatPdfPageCount(plan.first().pageIndexes.size),
        )
        else -> pluralStringResource(R.plurals.pdf_split_split_into, plan.size, plan.size)
    }
}

// ─── Custom ranges ───────────────────────────────────────────────────────────────────────────

private fun LazyGridScope.customContent(uiState: PdfSplitUiState, viewModel: PdfSplitViewModel) {
    val pageCount = uiState.pageCount
    val ranges = uiState.customRanges
    fullWidth("custom-text") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            ToolFieldLabel(text = stringResource(R.string.pdf_split_ranges_label), muted = true)
            ToolTextField(
                value = uiState.customText ?: formatPageRanges(ranges),
                onValueChange = viewModel::setCustomText,
                placeholder = stringResource(R.string.pdf_split_ranges_placeholder),
                isError = uiState.customTextError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                supportingText = {
                    Text(
                        text = if (uiState.customTextError) {
                            stringResource(R.string.pdf_split_ranges_error)
                        } else {
                            stringResource(R.string.pdf_split_ranges_hint)
                        },
                    )
                },
            )
        }
    }
    fullWidth("custom-label") {
        ToolSectionLabel(text = pluralStringResource(R.plurals.pdf_split_range_count, ranges.size, ranges.size))
    }
    ranges.forEachIndexed { index, range ->
        fullWidth("range-$index") {
            RangeCard(
                index = index,
                range = range,
                pageCount = pageCount,
                thumbnails = uiState.thumbnails,
                canRemove = ranges.size > 1,
                onRequestThumbnail = viewModel::requestThumbnail,
                onFrom = { viewModel.setRangeFrom(index, it) },
                onTo = { viewModel.setRangeTo(index, it) },
                onRemove = { viewModel.removeRange(index) },
            )
        }
    }
    fullWidth("add-range") {
        ToolSecondaryButton(
            text = stringResource(R.string.pdf_split_add_range),
            onClick = viewModel::addRange,
            icon = Icons.Outlined.Add,
            contentColor = MaterialTheme.colorScheme.primary,
        )
    }
    fullWidth("coverage") { CoverageStrip(ranges = ranges, pageCount = pageCount) }
    fullWidth("merge") {
        SwitchRow(
            title = stringResource(R.string.pdf_split_merge_ranges),
            caption = stringResource(R.string.pdf_split_merge_ranges_caption),
            checked = uiState.mergeRanges,
            onCheckedChange = viewModel::setMergeRanges,
        )
    }
}

@Composable
private fun RangeCard(
    index: Int,
    range: PdfPageRange,
    pageCount: Int,
    thumbnails: Map<Int, Bitmap>,
    canRemove: Boolean,
    onRequestThumbnail: (Int) -> Unit,
    onFrom: (Int) -> Unit,
    onTo: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val valid = range.isValid(pageCount)
    val color = splitPartColor(index)
    val title = stringResource(R.string.pdf_split_range_title, index + 1)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ToolTokens.CardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (valid) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        },
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RangeThumbs(
                    first = range.from - 1,
                    last = range.to - 1,
                    show = valid,
                    color = color,
                    thumbnails = thumbnails,
                    onRequestThumbnail = onRequestThumbnail,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = if (valid) rangeMeta(range) else stringResource(R.string.pdf_split_range_error, pageCount),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.pdf_split_remove_range, index + 1))
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val fromLabel = stringResource(R.string.pdf_split_range_from)
                val toLabel = stringResource(R.string.pdf_split_range_to)
                Text(fromLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PageNumberField(value = range.from, onValue = onFrom, description = "$title, $fromLabel")
                Text(toLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PageNumberField(value = range.to, onValue = onTo, description = "$title, $toLabel")
            }
        }
    }
}

/** First … last page of a part, with its colour underneath. */
@Composable
private fun RangeThumbs(
    first: Int,
    last: Int,
    show: Boolean,
    color: Color,
    thumbnails: Map<Int, Bitmap>,
    onRequestThumbnail: (Int) -> Unit,
) {
    if (show) {
        LaunchedEffect(first, last) {
            onRequestThumbnail(first)
            if (last != first) onRequestThumbnail(last)
        }
    }
    Row(
        modifier = Modifier.clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MiniPage(bitmap = if (show) thumbnails[first] else null, color = color)
        if (show && last != first) {
            Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            MiniPage(bitmap = thumbnails[last], color = color)
        }
    }
}

@Composable
private fun MiniPage(bitmap: Bitmap?, color: Color, width: Dp = 32.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ToolPaperPage(
            bitmap = bitmap,
            aspectRatio = 1f / 1.414f,
            elevation = 1.dp,
            modifier = Modifier.width(width),
        )
        Box(
            Modifier
                .width(width)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
    }
}

@Composable
private fun PageNumberField(value: Int, onValue: (Int) -> Unit, description: String) {
    ToolTextField(
        value = if (value > 0) value.toString() else "",
        onValueChange = { text -> onValue(text.filter(Char::isDigit).take(5).toIntOrNull() ?: 0) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        textAlign = TextAlign.Center,
        emphasized = true,
        modifier = Modifier
            .width(76.dp)
            .semantics { contentDescription = description },
    )
}

@Composable
private fun rangeMeta(range: PdfPageRange): String =
    if (range.from == range.to) {
        stringResource(R.string.pdf_split_page_meta, range.from, formatPdfPageCount(1))
    } else {
        stringResource(R.string.pdf_split_pages_meta, "${range.from}–${range.to}", formatPdfPageCount(range.size))
    }

/** Every page as a numbered square in its range's colour; pages in no range are struck through. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverageStrip(ranges: List<PdfPageRange>, pageCount: Int) {
    val missing = uncoveredPages(ranges, pageCount)
    val missingText = formatPageRangesForDisplay(compressPages(missing))
    val notIncluded = stringResource(R.string.pdf_split_not_included, missingText)
    val heading = stringResource(R.string.pdf_split_pages_included)
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolSectionLabel(text = heading)
        // Long files would turn the strip into a wall of squares; the sentence below says it all.
        if (pageCount <= MaxCoverageSquares) {
            val dark = isToolDarkTheme()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clearAndSetSemantics { },
            ) {
                for (page in 1..pageCount) {
                    val rangeIndex = rangeIndexForPage(ranges, page, pageCount)
                    val included = rangeIndex >= 0
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (included) splitPartColor(rangeIndex) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = page.toString(),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                !included -> MaterialTheme.colorScheme.onSurfaceVariant
                                dark -> Color(0xFF0C0F0E)
                                else -> Color.White
                            },
                            textDecoration = if (included) null else TextDecoration.LineThrough,
                        )
                    }
                }
            }
        }
        if (missing.isNotEmpty()) {
            Text(
                text = notIncluded,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Every N pages ───────────────────────────────────────────────────────────────────────────

private fun LazyGridScope.everyNContent(uiState: PdfSplitUiState, viewModel: PdfSplitViewModel) {
    val pageCount = uiState.pageCount
    val n = uiState.pagesPerPart.coerceIn(1, maxPagesPerPart(pageCount))
    val parts = everyNRanges(pageCount, n)
    val shown = if (parts.size > MaxListedParts + 1) parts.take(MaxListedParts) else parts
    fullWidth("every-n-control") {
        ToolCard(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.pdf_split_pages_in_each),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    RoundStepButton(
                        icon = Icons.Outlined.Remove,
                        description = stringResource(R.string.pdf_split_fewer_pages),
                        enabled = n > 1,
                        onClick = { viewModel.setPagesPerPart(n - 1) },
                    )
                    Text(
                        text = n.toString(),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 64.dp),
                    )
                    RoundStepButton(
                        icon = Icons.Outlined.Add,
                        description = stringResource(R.string.pdf_split_more_pages),
                        enabled = n < maxPagesPerPart(pageCount),
                        onClick = { viewModel.setPagesPerPart(n + 1) },
                    )
                }
                val last = parts.lastOrNull()?.size ?: n
                Text(
                    text = stringResource(
                        R.string.pdf_split_you_get,
                        pluralStringResource(R.plurals.pdf_split_pdf_count, parts.size, parts.size),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                if (last < n) {
                    Text(
                        text = stringResource(R.string.pdf_split_last_shorter, formatPdfPageCount(last)),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (n != 1) {
                    ToolTextAction(label = stringResource(R.string.pdf_split_one_per_page), onClick = { viewModel.setPagesPerPart(1) })
                } else {
                    Spacer(Modifier.height(ToolTokens.MinTouch))
                }
            }
        }
    }
    fullWidth("every-n-list") {
        ToolCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                shown.forEachIndexed { index, range ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PartRow(
                        index = index,
                        range = range,
                        thumbnails = uiState.thumbnails,
                        onRequestThumbnail = viewModel::requestThumbnail,
                    )
                }
                if (shown.size < parts.size) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = stringResource(R.string.pdf_split_more_parts, parts.size - shown.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundStepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(56.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun PartRow(
    index: Int,
    range: PdfPageRange,
    thumbnails: Map<Int, Bitmap>,
    onRequestThumbnail: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RangeThumbs(
            first = range.from - 1,
            last = range.to - 1,
            show = true,
            color = splitPartColor(index),
            thumbnails = thumbnails,
            onRequestThumbnail = onRequestThumbnail,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.pdf_split_pdf_title, index + 1),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = rangeMeta(range),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Extract ─────────────────────────────────────────────────────────────────────────────────

private fun LazyGridScope.extractContent(uiState: PdfSplitUiState, viewModel: PdfSplitViewModel) {
    val pageCount = uiState.pageCount
    val selectedCount = uiState.selectedPages.size
    fullWidth("extract-header") {
        Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolSectionLabel(
                    text = formatPdfToImagesPagesLabel(selectedCount, pageCount),
                    modifier = Modifier.weight(1f),
                )
                SelectAllAction(
                    selectedCount = selectedCount,
                    totalCount = pageCount,
                    onSelectAll = viewModel::selectAll,
                    onSelectNone = viewModel::selectNone,
                )
            }
            if (pageCount > 2) GridHint(text = stringResource(R.string.pdf_import_tap_a_page_or_long))
        }
    }
    fullWidth("extract-text") {
        val typed = uiState.extractText
            ?: formatPageRanges(compressPages(uiState.selectedPages.map { it + 1 }))
        ToolTextField(
            value = typed,
            onValueChange = viewModel::setExtractText,
            placeholder = stringResource(R.string.pdf_split_ranges_placeholder),
            isError = uiState.extractTextError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            leadingIcon = {
                Text(
                    text = stringResource(R.string.pdf_split_pages_label),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp, end = 4.dp),
                )
            },
            supportingText = if (uiState.extractTextError) {
                { Text(stringResource(R.string.pdf_split_ranges_error)) }
            } else {
                null
            },
        )
    }
    items(count = pageCount, key = { index -> "$PageKeyPrefix$index" }) { index ->
        LaunchedEffect(index) { viewModel.requestThumbnail(index) }
        PageCell(
            bitmap = uiState.thumbnails[index],
            label = (index + 1).toString(),
            selected = index in uiState.selectedPages,
            contentDescription = stringResource(R.string.trash_use_cases_page, index + 1),
            onToggle = { viewModel.togglePage(index) },
        )
    }
    fullWidth("extract-save-as") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            ToolFieldLabel(text = stringResource(R.string.pdf_split_save_as))
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OptionCard(
                    title = stringResource(R.string.pdf_split_one_pdf),
                    caption = stringResource(R.string.pdf_split_one_pdf_caption),
                    selected = !uiState.extractSeparately,
                    onClick = { viewModel.setExtractSeparately(false) },
                    modifier = Modifier.weight(1f),
                )
                OptionCard(
                    title = stringResource(R.string.pdf_split_separate_pdfs),
                    caption = stringResource(R.string.pdf_split_separate_caption),
                    selected = uiState.extractSeparately,
                    onClick = { viewModel.setExtractSeparately(true) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    val restCount = pageCount - selectedCount
    if (restCount > 0) {
        fullWidth("extract-rest") {
            SwitchRow(
                title = pluralStringResource(R.plurals.pdf_split_keep_rest, restCount, restCount),
                caption = stringResource(R.string.pdf_split_keep_rest_caption),
                checked = uiState.keepRest,
                onCheckedChange = viewModel::setKeepRest,
            )
        }
    }
}

@Composable
private fun OptionCard(title: String, caption: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(ToolTokens.CardRadius)
    Column(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainer,
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), fontWeight = FontWeight.Bold)
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(title: String, caption: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ToolCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

// ─── Step 3 · Done ───────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.DoneStep(
    uiState: PdfSplitUiState,
    onClose: () -> Unit,
    onRename: (Int, String) -> Unit,
    onOpen: (Int) -> Unit,
    onRequestThumbnail: (Int) -> Unit,
    onSaveAsZip: (Boolean) -> Unit,
    onSaveAll: () -> Unit,
    onShareAll: () -> Unit,
) {
    val outputs = uiState.outputs
    ToolTopBar(
        onBack = onClose,
        stepText = stringResource(R.string.pdf_import_step_3_of_3_done),
        backIcon = Icons.Outlined.Close,
        backContentDescription = stringResource(R.string.zoomable_image_close),
        centerStep = true,
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = ToolTokens.ScreenPadding, end = ToolTokens.ScreenPadding, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutputFan(outputs = outputs, thumbnails = uiState.thumbnails, onRequestThumbnail = onRequestThumbnail)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = pluralStringResource(R.plurals.pdf_split_split_into, outputs.size, outputs.size),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(
                    R.string.pdf_split_from_file,
                    uiState.source?.label().orEmpty(),
                    formatPdfPageCount(uiState.pageCount),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolSectionLabel(text = stringResource(R.string.pdf_split_tap_to_rename))
            ToolCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    outputs.forEachIndexed { index, output ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        OutputRow(
                            index = index,
                            output = output,
                            thumbnails = uiState.thumbnails,
                            onRequestThumbnail = onRequestThumbnail,
                            onRename = { onRename(index, it) },
                            onOpen = { onOpen(index) },
                        )
                    }
                }
            }
        }
        if (outputs.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolFieldLabel(text = stringResource(R.string.pdf_split_save_as))
                ToolSegmented(
                    options = listOf(false, true),
                    selected = uiState.saveAsZip,
                    onSelect = onSaveAsZip,
                    label = { zip ->
                        if (zip) appStringSafe(R.string.pdf_split_one_zip) else appStringSafe(R.string.pdf_split_separate_pdfs)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (uiState.wasLocked) ToolNote(text = stringResource(R.string.pdf_split_no_password))
    }
    ToolBottomBar {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolSecondaryButton(
                text = stringResource(R.string.pdf_split_save_all),
                onClick = onSaveAll,
                icon = Icons.Outlined.Download,
                loading = uiState.isSaving,
                modifier = Modifier.weight(1f),
            )
            ToolPrimaryButton(
                text = stringResource(R.string.pdf_split_share_all),
                onClick = onShareAll,
                icon = Icons.Outlined.Share,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.pdf_tool_done_your_original_file_is_unchanged),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** ToolSegmented's label lambda is not composable, so resolve through the app's string helper. */
private fun appStringSafe(id: Int): String = `in`.c1ph3rj.scanly.core.common.appString(id)

/** Up to three parts' first pages, fanned like a hand of cards. */
@Composable
private fun OutputFan(outputs: List<PdfSplitOutput>, thumbnails: Map<Int, Bitmap>, onRequestThumbnail: (Int) -> Unit) {
    val shown = outputs.take(3)
    LaunchedEffect(shown.map { it.pageIndexes.firstOrNull() }) {
        shown.forEach { output -> output.pageIndexes.firstOrNull()?.let(onRequestThumbnail) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        shown.forEachIndexed { index, output ->
            val rotation = when {
                shown.size == 1 -> 0f
                index == 0 -> -8f
                index == shown.lastIndex -> 8f
                else -> 0f
            }
            Box(Modifier.width(if (index == 1) 92.dp else 84.dp).graphicsRotation(rotation)) {
                MiniPage(
                    bitmap = output.pageIndexes.firstOrNull()?.let { thumbnails[it] },
                    color = splitPartColor(index),
                    width = if (index == 1) 92.dp else 84.dp,
                )
            }
        }
    }
}

private fun Modifier.graphicsRotation(degrees: Float): Modifier =
    if (degrees == 0f) this else this.rotate(degrees)

@Composable
private fun OutputRow(
    index: Int,
    output: PdfSplitOutput,
    thumbnails: Map<Int, Bitmap>,
    onRequestThumbnail: (Int) -> Unit,
    onRename: (String) -> Unit,
    onOpen: () -> Unit,
) {
    val first = output.pageIndexes.firstOrNull()
    LaunchedEffect(first) { first?.let(onRequestThumbnail) }
    val runs = compressPages(output.pageIndexes.map { it + 1 })
    val pagesText = formatPageRangesForDisplay(runs)
    val size = output.sizeBytes?.let(StorageFormatter::formatBytes)
    val meta = if (output.pageIndexes.size == 1) {
        stringResource(R.string.pdf_split_page_meta, output.pageIndexes.first() + 1, size ?: formatPdfPageCount(1))
    } else {
        stringResource(R.string.pdf_split_pages_meta, pagesText, size ?: formatPdfPageCount(output.pageIndexes.size))
    }
    Row(
        modifier = Modifier.padding(start = 12.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.clickable(onClick = onOpen).clearAndSetSemantics { }) {
            MiniPage(bitmap = first?.let { thumbnails[it] }, color = splitPartColor(index), width = 38.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Names differ only at the end ("… – pages 1–3"), so they wrap instead of ellipsizing.
            ToolTextField(
                value = output.name,
                onValueChange = onRename,
                singleLine = false,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                suffix = { Text(".pdf", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        IconButton(onClick = onOpen, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Outlined.Visibility,
                contentDescription = stringResource(R.string.pdf_split_open_part, output.name),
            )
        }
    }
}

// ─── Colours ─────────────────────────────────────────────────────────────────────────────────

/** Distinct part colours that also differ in lightness; brighter set on dark surfaces. */
@Composable
private fun splitPartColor(index: Int): Color {
    val palette = if (isToolDarkTheme()) DarkPartColors else LightPartColors
    return palette[index.mod(palette.size)]
}

private val LightPartColors = listOf(
    Color(0xFF0F766E), Color(0xFFB45309), Color(0xFF4338CA),
    Color(0xFF9D174D), Color(0xFF166534), Color(0xFF1D4ED8),
)
private val DarkPartColors = listOf(
    Color(0xFF2DD4BF), Color(0xFFFBBF24), Color(0xFFA5B4FC),
    Color(0xFFF9A8D4), Color(0xFF86EFAC), Color(0xFF93C5FD),
)

private const val MaxCoverageSquares = 60
private const val MaxListedParts = 5
