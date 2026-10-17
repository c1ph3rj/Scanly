package `in`.c1ph3rj.scanly.feature.text

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.PageTextBlock
import `in`.c1ph3rj.scanly.domain.model.previewImagePath
import `in`.c1ph3rj.scanly.domain.search.FoldedText
import `in`.c1ph3rj.scanly.feature.components.CachedThumbnail
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class TextViewMode(val label: String) { TEXT("Text"), PAGE("On page") }

@Composable
fun PageTextRoute(
    onNavigateUp: () -> Unit,
    viewModel: PageTextViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PageTextEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    PageTextScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onReadDocument = viewModel::readDocument,
        onReadPage = viewModel::readPage,
        onSaveEdit = viewModel::saveEdit,
        onRestoreRecognized = viewModel::restoreRecognized,
        onClearQuery = viewModel::clearQuery,
        onTextUsed = viewModel::onTextUsed,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageTextScreen(
    uiState: PageTextUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onReadDocument: (force: Boolean) -> Unit,
    onReadPage: (pageId: String) -> Unit,
    onSaveEdit: (pageId: String, text: String) -> Unit,
    onRestoreRecognized: (pageId: String) -> Unit,
    onClearQuery: () -> Unit,
    onTextUsed: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val window = rememberWindowSizeInfo()
    val pageCount = uiState.pages.size
    val pagerState = rememberPagerState(
        initialPage = uiState.initialPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        pageCount = { pageCount },
    )
    var mode by rememberSaveable { mutableStateOf(TextViewMode.TEXT) }
    var editingPageId by rememberSaveable { mutableStateOf<String?>(null) }
    var editValue by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val currentItem = uiState.pages.getOrNull(pagerState.currentPage)
    val currentText = currentItem?.text?.text.orEmpty()
    val documentTitle = uiState.document?.title.orEmpty()

    // Jump to the requested page once pages are known (the pager starts at 0 while loading).
    LaunchedEffect(pageCount) {
        if (pageCount > 0 && pagerState.currentPage == 0 && uiState.initialPageIndex in 1 until pageCount) {
            pagerState.scrollToPage(uiState.initialPageIndex)
        }
    }

    fun share(subject: String, text: String) {
        shareText(context, subject, text)
        onTextUsed()
    }

    fun copy(text: String, what: String) {
        onTextUsed()
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(documentTitle, text)))
            // Android 13+ shows its own clipboard confirmation.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                snackbarHostState.showSnackbar("Copied $what")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Extracted text",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (documentTitle.isNotEmpty()) {
                            Text(
                                text = documentTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }, enabled = editingPageId == null) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Copy all pages") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                enabled = uiState.hasAnyText,
                                onClick = {
                                    menuOpen = false
                                    copy(uiState.documentText, "all pages")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share all pages") },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                enabled = uiState.hasAnyText,
                                onClick = {
                                    menuOpen = false
                                    share(documentTitle, uiState.documentText)
                                },
                            )
                            if (currentItem?.text?.isEdited == true) {
                                DropdownMenuItem(
                                    text = { Text("Restore original text") },
                                    leadingIcon = { Icon(Icons.Outlined.Restore, null) },
                                    onClick = {
                                        menuOpen = false
                                        onRestoreRecognized(currentItem.page.id)
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Read all pages again") },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                enabled = uiState.recognition !is RecognitionStatus.Running && pageCount > 0,
                                onClick = {
                                    menuOpen = false
                                    onReadDocument(true)
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (pageCount > 0) {
                PageTextActionBar(
                    editing = editingPageId != null,
                    hasText = currentText.isNotBlank(),
                    canEdit = currentItem?.text != null,
                    onCopy = { copy(currentText, "page ${pagerState.currentPage + 1}") },
                    onShare = { share("$documentTitle — page ${pagerState.currentPage + 1}", currentText) },
                    onEdit = {
                        currentItem?.let {
                            editValue = currentText
                            editingPageId = it.page.id
                            mode = TextViewMode.TEXT
                        }
                    },
                    onCancelEdit = { editingPageId = null },
                    onSaveEdit = {
                        editingPageId?.let { onSaveEdit(it, editValue) }
                        editingPageId = null
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val running = uiState.recognition as? RecognitionStatus.Running
            AnimatedVisibility(running != null) {
                RecognitionProgress(running)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = window.contentMaxWidth)
                    .padding(horizontal = window.horizontalPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    TextViewMode.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = mode == option,
                            onClick = { mode = option },
                            enabled = editingPageId == null,
                            shape = SegmentedButtonDefaults.itemShape(index, TextViewMode.entries.size),
                            icon = {},
                            label = { Text(option.label, maxLines = 1, softWrap = false) },
                        )
                    }
                }
                if (pageCount > 1) {
                    PageStepper(
                        current = pagerState.currentPage,
                        count = pageCount,
                        enabled = editingPageId == null,
                        onStep = { delta ->
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + delta) }
                        },
                    )
                }
            }
            if (uiState.highlightWords.isNotEmpty()) {
                QueryPill(
                    query = uiState.query,
                    matchesOnPage = countMatches(currentText, uiState.highlightWords),
                    onClear = onClearQuery,
                    modifier = Modifier
                        .widthIn(max = window.contentMaxWidth)
                        .padding(horizontal = window.horizontalPadding),
                )
            }
            when {
                uiState.isLoading -> Unit
                pageCount == 0 -> EmptyDocumentText()
                else -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = editingPageId == null,
                    beyondViewportPageCount = 1,
                    key = { index -> uiState.pages[index].page.id },
                ) { index ->
                    val item = uiState.pages[index]
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        val pageModifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = window.contentMaxWidth)
                        AnimatedContent(
                            targetState = mode,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "text-mode",
                        ) { targetMode ->
                            when (targetMode) {
                                TextViewMode.TEXT -> TextModePage(
                                    item = item,
                                    pageCount = pageCount,
                                    highlightWords = uiState.highlightWords,
                                    recognition = uiState.recognition,
                                    editing = editingPageId == item.page.id,
                                    editValue = editValue,
                                    onEditValueChange = { editValue = it },
                                    onReadPage = { onReadPage(item.page.id) },
                                    horizontalPadding = window.horizontalPadding,
                                    modifier = pageModifier,
                                )
                                TextViewMode.PAGE -> OnPageModePage(
                                    item = item,
                                    highlightWords = uiState.highlightWords,
                                    onCopyBlock = { copy(it, "selection") },
                                    horizontalPadding = window.horizontalPadding,
                                    modifier = pageModifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionProgress(running: RecognitionStatus.Running?) {
    Column(Modifier.fillMaxWidth()) {
        if (running != null && running.total > 0) {
            LinearProgressIndicator(
                progress = { running.done.toFloat() / running.total },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Text(
            text = if (running != null && running.total > 1) {
                "Reading page ${(running.done + 1).coerceAtMost(running.total)} of ${running.total}…"
            } else {
                "Reading text…"
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageStepper(current: Int, count: Int, enabled: Boolean, onStep: (Int) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onStep(-1) }, enabled = enabled && current > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
            }
            Text(
                text = "${current + 1} / $count",
                modifier = Modifier.semantics { contentDescription = "Page ${current + 1} of $count" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { onStep(1) }, enabled = enabled && current < count - 1) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
            }
        }
    }
}

@Composable
private fun QueryPill(query: String, matchesOnPage: Int, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Surface(shape = CircleShape, color = matchHighlightStyle().background) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "“${query.trim()}” · " +
                        if (matchesOnPage == 0) "not on this page" else "$matchesOnPage on this page",
                    style = MaterialTheme.typography.labelLarge,
                    color = matchHighlightStyle().color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 260.dp),
                )
                IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Stop highlighting",
                        modifier = Modifier.size(18.dp),
                        tint = matchHighlightStyle().color,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextModePage(
    item: PageTextItem,
    pageCount: Int,
    highlightWords: List<String>,
    recognition: RecognitionStatus,
    editing: Boolean,
    editValue: String,
    onEditValueChange: (String) -> Unit,
    onReadPage: () -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val text = item.text
    val highlight = matchHighlightStyle()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            PageHeader(item, pageCount)
        }
        if (item.isStale && !editing) {
            item(key = "stale") {
                InlineNotice(
                    icon = Icons.Outlined.Update,
                    text = "This page changed after its text was read.",
                    actionLabel = "Read again",
                    onAction = onReadPage,
                    enabled = recognition !is RecognitionStatus.Running,
                )
            }
        }
        when {
            editing -> item(key = "editor") {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = onEditValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    supportingText = { Text("Your corrections are used for copy, share and search.") },
                )
            }
            text == null -> item(key = "pending") {
                when (recognition) {
                    is RecognitionStatus.Running -> ReadingPlaceholder()
                    is RecognitionStatus.Failed -> PageMessage(
                        icon = Icons.Outlined.ErrorOutline,
                        title = "Couldn't read this page",
                        body = recognition.message,
                        actionLabel = "Try again",
                        onAction = onReadPage,
                    )
                    RecognitionStatus.Idle -> PageMessage(
                        icon = Icons.Outlined.TextFields,
                        title = "Text not read yet",
                        body = "Scanly reads page text on this device.",
                        actionLabel = "Read this page",
                        onAction = onReadPage,
                    )
                }
            }
            !text.hasText -> item(key = "empty") {
                PageMessage(
                    icon = Icons.Outlined.ImageSearch,
                    title = "No text found on this page",
                    body = "Photos and handwriting may not contain readable printed text. " +
                        "A sharper, well-lit scan usually helps.",
                    actionLabel = "Read again",
                    onAction = onReadPage,
                    actionEnabled = recognition !is RecognitionStatus.Running,
                )
            }
            else -> {
                val paragraphs = if (text.isEdited) {
                    text.text.split(Regex("\\n\\s*\\n")).filter(String::isNotBlank)
                } else {
                    text.blocks.map(PageTextBlock::text).filter(String::isNotBlank)
                }
                item(key = "body") {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            paragraphs.forEach { paragraph ->
                                Text(
                                    text = highlightWordPrefixes(paragraph, highlightWords, highlight),
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageHeader(item: PageTextItem, pageCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Page ${item.page.pageIndex + 1}" + if (pageCount > 1) " of $pageCount" else "",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        item.text?.takeIf { it.hasText }?.let { text ->
            Text(
                text = "${text.wordCount} ${if (text.wordCount == 1) "word" else "words"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (text.isEdited) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        text = "Edited",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnPageModePage(
    item: PageTextItem,
    highlightWords: List<String>,
    onCopyBlock: (String) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val text = item.text
    val blocks = text?.blocks.orEmpty()
    var selectedIndex by remember(item.page.id, text?.recognizedAtMillis) { mutableStateOf<Int?>(null) }
    // The overlay is drawn in normalized coordinates, so the frame must match the read image exactly.
    val aspect = text?.imageAspectRatio ?: DEFAULT_PAGE_ASPECT
    val primary = MaterialTheme.colorScheme.primary
    val matchFill = matchHighlightStyle().background
    val matchingLines = remember(blocks, highlightWords) {
        blocks.flatMap { it.lines }.filter { line -> countMatches(line.text, highlightWords) > 0 }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                CachedThumbnail(
                    thumbnailPath = item.page.previewImagePath(PreviewDisplaySize.DETAIL),
                    title = "Page ${item.page.pageIndex + 1}",
                    displaySize = PreviewDisplaySize.DETAIL,
                    contentRevision = item.page.updatedAtMillis,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(0.dp),
                    contentScale = ContentScale.FillBounds,
                    placeholderIcon = null,
                )
                if (!item.isStale) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "Page image with ${blocks.size} text regions" }
                            .pointerInput(blocks) {
                                detectTapGestures { offset ->
                                    val x = offset.x / size.width
                                    val y = offset.y / size.height
                                    val hit = blocks.indexOfFirst { block ->
                                        x in block.box.left - TAP_SLOP..block.box.right + TAP_SLOP &&
                                            y in block.box.top - TAP_SLOP..block.box.bottom + TAP_SLOP
                                    }
                                    selectedIndex = hit.takeIf { it >= 0 && it != selectedIndex }
                                }
                            },
                    ) {
                        val radius = CornerRadius(4.dp.toPx())
                        matchingLines.forEach { line ->
                            drawRoundRect(
                                color = matchFill.copy(alpha = 0.55f),
                                topLeft = Offset(line.box.left * size.width, line.box.top * size.height),
                                size = Size(line.box.width * size.width, line.box.height * size.height),
                                cornerRadius = radius,
                            )
                        }
                        blocks.forEachIndexed { index, block ->
                            val selected = index == selectedIndex
                            val pad = 3.dp.toPx()
                            val topLeft = Offset(block.box.left * size.width - pad, block.box.top * size.height - pad)
                            val blockSize = Size(block.box.width * size.width + pad * 2, block.box.height * size.height + pad * 2)
                            drawRoundRect(
                                color = primary.copy(alpha = if (selected) 0.16f else 0.05f),
                                topLeft = topLeft,
                                size = blockSize,
                                cornerRadius = radius,
                            )
                            drawRoundRect(
                                color = primary.copy(alpha = if (selected) 0.9f else 0.35f),
                                topLeft = topLeft,
                                size = blockSize,
                                cornerRadius = radius,
                                style = Stroke(width = if (selected) 2.dp.toPx() else 1.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }
        val selected = selectedIndex?.let(blocks::getOrNull)
        when {
            item.isStale -> InlineNotice(
                icon = Icons.Outlined.Update,
                text = "Regions are hidden because this page changed after its text was read.",
            )
            selected != null -> SelectedBlockCard(
                text = selected.text,
                highlightWords = highlightWords,
                onCopy = { onCopyBlock(selected.text) },
                onDismiss = { selectedIndex = null },
            )
            blocks.isNotEmpty() -> Text(
                text = "Tap an outlined area to copy just that part.",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SelectedBlockCard(
    text: String,
    highlightWords: List<String>,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectionContainer {
                Text(
                    text = highlightWordPrefixes(text, highlightWords, matchHighlightStyle()),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCopy) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

@Composable
private fun PageTextActionBar(
    editing: Boolean,
    hasText: Boolean,
    canEdit: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (editing) {
                    OutlinedButton(onClick = onCancelEdit, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Cancel")
                    }
                    Button(onClick = onSaveEdit, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Save changes")
                    }
                } else {
                    Button(
                        onClick = onCopy,
                        enabled = hasText,
                        modifier = Modifier.weight(1.25f).height(52.dp),
                        contentPadding = ActionButtonPadding,
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy page", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onShare,
                        enabled = hasText,
                        modifier = Modifier.weight(1f).height(52.dp),
                        contentPadding = ActionButtonPadding,
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onEdit,
                        enabled = canEdit,
                        modifier = Modifier.weight(1f).height(52.dp),
                        contentPadding = ActionButtonPadding,
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineNotice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    enabled: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (actionLabel != null) {
                TextButton(onClick = onAction, enabled = enabled) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun PageMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onAction, enabled = actionEnabled) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(actionLabel)
        }
    }
}

/** Shimmering line placeholders while a page is being read. */
@Composable
private fun ReadingPlaceholder() {
    val transition = rememberInfiniteTransition(label = "reading")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "reading-alpha",
    )
    val widths = listOf(1f, 0.94f, 0.98f, 0.62f, 0f, 1f, 0.9f, 0.96f, 0.4f)
    Column(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Reading text" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        widths.forEach { fraction ->
            if (fraction == 0f) {
                Spacer(Modifier.height(6.dp))
            } else {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)),
                )
            }
        }
    }
}

@Composable
private fun EmptyDocumentText() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Notes, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("This document has no pages yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Scan or import a page and its text will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Counts words in [text] that start with any of the folded [words]. */
internal fun countMatches(text: String, words: List<String>): Int {
    if (words.isEmpty() || text.isEmpty()) return 0
    val folded = FoldedText.of(text).text
    return Regex("[\\p{L}\\p{N}]+").findAll(folded).count { match -> words.any { match.value.startsWith(it) } }
}

private fun shareText(context: Context, subject: String, text: String) {
    if (text.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share text"))
}

private const val TAP_SLOP = 0.012f
private val ActionButtonPadding = PaddingValues(horizontal = 10.dp)
/** A4 portrait, used until the page has been read. */
private const val DEFAULT_PAGE_ASPECT = 0.707f
