package `in`.c1ph3rj.scanly.feature.document

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.ImageImportSupport
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageDialog
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageViewer
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.feature.components.PagePreview
import `in`.c1ph3rj.scanly.feature.components.ScanlyImportProgressOverlay
import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize
import `in`.c1ph3rj.scanly.feature.components.ExportActionRow
import `in`.c1ph3rj.scanly.feature.components.DocumentTitleDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import `in`.c1ph3rj.scanly.feature.components.FullScreenLoader
import `in`.c1ph3rj.scanly.feature.components.MoveToFolderSheet
import `in`.c1ph3rj.scanly.feature.components.PdfOptionsSheet
import `in`.c1ph3rj.scanly.feature.components.ScanlySheetContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import `in`.c1ph3rj.scanly.feature.components.sharePreparedFiles
import `in`.c1ph3rj.scanly.core.common.toRelativeDate
import `in`.c1ph3rj.scanly.core.common.toReadableDateTime

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DocumentDetailScreen(
    uiState: DocumentDetailUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenPageEditor: (String) -> Unit,
    onReplacePage: (String) -> Unit,
    onSelectPage: (String) -> Unit,
    onMovePage: (String, Int) -> Unit,
    onDeleteSelectedPage: () -> Unit,
    onExportPdf: (PdfExportOptions) -> Unit,
    onSharePdf: (PdfExportOptions) -> Unit,
    onExportImageArchive: () -> Unit,
    onShareImages: () -> Unit,
    onShareSelectedPage: () -> Unit,
    onMoveToGroup: (String?) -> Unit,
    onCreateFolderAndMove: (String) -> Unit,
    onSuggestGroupTitle: suspend (GroupTitleFormat) -> String,
    onImportImage: () -> Unit,
    onRenameDocument: (String) -> Unit,
    onDeleteDocument: () -> Unit,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    val useMasterDetailLayout = windowSizeInfo.useTabletLandscapeLayout
    var deleteDialogVisible by rememberSaveable(uiState.selectedPageId) { mutableStateOf(false) }
    var previewPageId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportSheetVisible by rememberSaveable { mutableStateOf(false) }
    var moveSheetVisible by rememberSaveable { mutableStateOf(false) }
    var renameDialogVisible by rememberSaveable { mutableStateOf(false) }
    var deleteDocumentDialogVisible by rememberSaveable { mutableStateOf(false) }
    var pdfActionMode by rememberSaveable { mutableStateOf<PdfActionMode?>(null) }
    var pdfOptions by remember { mutableStateOf(PdfExportOptions()) }
    var addPageSheetVisible by rememberSaveable { mutableStateOf(false) }
    val document = uiState.document
    val documentUpdatedDate = remember(document?.updatedAtMillis) {
        document?.updatedAtMillis?.toShortDate()
    }
    var isReviewingPage by rememberSaveable(document?.id) { mutableStateOf(false) }
    val selectedPage = uiState.selectedPage
    val listState = rememberLazyListState()
    val pageTileBounds = remember { mutableStateMapOf<String, Rect>() }
    var listBounds by remember { mutableStateOf<Rect?>(null) }
    var draggedPageId by remember { mutableStateOf<String?>(null) }
    var dragStartBounds by remember { mutableStateOf<Rect?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragCenterInRoot by remember { mutableStateOf<Offset?>(null) }
    var dragTargetPageId by remember { mutableStateOf<String?>(null) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
    var autoScrollDelta by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val autoScrollThresholdPx = with(density) { 96.dp.toPx() }
    val maxAutoScrollDeltaPx = with(density) { 22.dp.toPx() }
    val previewPage = previewPageId?.let { pageId ->
        uiState.pages.firstOrNull { page -> page.id == pageId }
    }
    LaunchedEffect(selectedPage, useMasterDetailLayout) {
        if (!useMasterDetailLayout && selectedPage == null) {
            isReviewingPage = false
        }
    }
    LaunchedEffect(uiState.pages, useMasterDetailLayout, uiState.selectedPageId) {
        if (
            useMasterDetailLayout &&
            uiState.pages.isNotEmpty() &&
            uiState.selectedPageId == null
        ) {
            onSelectPage(uiState.pages.first().id)
        }
    }
    LaunchedEffect(uiState.pages) {
        val pageIds = uiState.pages.map { page -> page.id }.toSet()
        pageTileBounds.keys.removeAll { pageId -> pageId !in pageIds }
    }
    val handleNavigateUp = {
        if (!useMasterDetailLayout && isReviewingPage) {
            isReviewingPage = false
        } else {
            onNavigateUp()
        }
    }
    BackHandler(enabled = uiState.isImporting || uiState.isExporting) {
        // Block back while import/export is running so the job isn't abandoned mid-pipeline.
    }
    BackHandler(enabled = !useMasterDetailLayout && isReviewingPage && !uiState.isImporting) {
        isReviewingPage = false
    }
    LaunchedEffect(draggedPageId, autoScrollDelta) {
        while (draggedPageId != null && autoScrollDelta != 0f) {
            listState.scrollBy(autoScrollDelta)
            val activePageId = draggedPageId
            val activeCenter = dragCenterInRoot
            if (activePageId != null && activeCenter != null) {
                val targetIndex = resolvePageReorderTargetIndex(
                    pageIds = uiState.pages.map { page -> page.id },
                    pageBounds = pageTileBounds,
                    draggedPageId = activePageId,
                    dragCenter = activeCenter,
                    visibleBounds = listBounds,
                )
                dragTargetIndex = targetIndex
                dragTargetPageId = resolvePageReorderTargetPageId(
                    pageIds = uiState.pages.map { page -> page.id },
                    draggedPageId = activePageId,
                    targetIndex = targetIndex,
                )
                autoScrollDelta = listBounds?.edgeScrollDelta(
                    pointerY = activeCenter.y,
                    thresholdPx = autoScrollThresholdPx,
                    maxScrollDeltaPx = maxAutoScrollDeltaPx,
                ) ?: 0f
            }
            delay(16L)
        }
    }

    val reorderEnabled = !uiState.isMutatingPage && uiState.pages.size > 1

    fun startPageDrag(pageId: String) {
        val startBounds = pageTileBounds[pageId] ?: return
        draggedPageId = pageId
        dragStartBounds = startBounds
        dragOffset = Offset.Zero
        dragCenterInRoot = startBounds.center
        dragTargetPageId = null
        dragTargetIndex = null
        autoScrollDelta = 0f
    }

    fun updatePageDrag(dragAmount: Offset) {
        val activePageId = draggedPageId ?: return
        val startBounds = dragStartBounds ?: return
        val updatedOffset = dragOffset + dragAmount
        val updatedCenter = startBounds.center + updatedOffset
        dragOffset = updatedOffset
        dragCenterInRoot = updatedCenter
        val targetIndex = resolvePageReorderTargetIndex(
            pageIds = uiState.pages.map { page -> page.id },
            pageBounds = pageTileBounds,
            draggedPageId = activePageId,
            dragCenter = updatedCenter,
            visibleBounds = listBounds,
        )
        dragTargetIndex = targetIndex
        dragTargetPageId = resolvePageReorderTargetPageId(
            pageIds = uiState.pages.map { page -> page.id },
            draggedPageId = activePageId,
            targetIndex = targetIndex,
        )
        autoScrollDelta = listBounds?.edgeScrollDelta(
            pointerY = updatedCenter.y,
            thresholdPx = autoScrollThresholdPx,
            maxScrollDeltaPx = maxAutoScrollDeltaPx,
        ) ?: 0f
    }

    fun endPageDrag() {
        val activePageId = draggedPageId
        val targetIndex = dragTargetIndex
        if (activePageId != null && targetIndex != null) {
            onMovePage(activePageId, targetIndex)
        }
        draggedPageId = null
        dragStartBounds = null
        dragOffset = Offset.Zero
        dragCenterInRoot = null
        dragTargetPageId = null
        dragTargetIndex = null
        autoScrollDelta = 0f
    }

    fun cancelPageDrag() {
        draggedPageId = null
        dragStartBounds = null
        dragOffset = Offset.Zero
        dragCenterInRoot = null
        dragTargetPageId = null
        dragTargetIndex = null
        autoScrollDelta = 0f
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            ReviewTopBar(
                title = document?.title ?: "Document",
                pageCount = uiState.pages.size,
                onNavigateUp = handleNavigateUp,
                onOpenExportSheet = { exportSheetVisible = true },
                onRename = { renameDialogVisible = true },
                onDelete = { deleteDocumentDialogVisible = true },
                exportEnabled = !uiState.isExporting && uiState.pages.isNotEmpty(),
                pageReviewActive = !useMasterDetailLayout && isReviewingPage && selectedPage != null,
                onSharePage = onShareSelectedPage,
                menuEnabled = document != null,
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            FullScreenLoader(modifier = Modifier.padding(innerPadding))
        } else if (useMasterDetailLayout) {
            DocumentMasterDetailLayout(
                innerPadding = innerPadding,
                document = document,
                uiState = uiState,
                documentUpdatedDate = documentUpdatedDate,
                selectedPage = selectedPage,
                listState = listState,
                listBounds = listBounds,
                onListBoundsChanged = { listBounds = it },
                pageTileBounds = pageTileBounds,
                draggedPageId = draggedPageId,
                dragStartBounds = dragStartBounds,
                dragOffset = dragOffset,
                dragTargetPageId = dragTargetPageId,
                reorderEnabled = reorderEnabled,
                onStartPageDrag = ::startPageDrag,
                onUpdatePageDrag = ::updatePageDrag,
                onEndPageDrag = ::endPageDrag,
                onCancelPageDrag = ::cancelPageDrag,
                onNavigateUp = onNavigateUp,
                onOpenCamera = onOpenCamera,
                onImportImage = onImportImage,
                onSelectPage = onSelectPage,
                onOpenPageEditor = onOpenPageEditor,
                onReplacePage = onReplacePage,
                onShareSelectedPage = onShareSelectedPage,
                onDeleteSelectedPage = { deleteDialogVisible = true },
                onAddPage = { addPageSheetVisible = true },
                onMoveToFolder = { moveSheetVisible = true },
                onPreviewPage = { pageId -> previewPageId = pageId },
                density = density,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = if (windowSizeInfo.isTablet) {
                    Alignment.TopCenter
                } else {
                    Alignment.TopStart
                },
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (windowSizeInfo.isTablet) {
                                Modifier
                                    .widthIn(max = windowSizeInfo.contentMaxWidth)
                                    .fillMaxHeight()
                            } else {
                                Modifier.fillMaxSize()
                            },
                        )
                        .background(MaterialTheme.colorScheme.background)
                        .onGloballyPositioned { coordinates ->
                            listBounds = coordinates.boundsInRoot()
                        }
                        .pointerInput(reorderEnabled) {
                            if (!reorderEnabled) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    val bounds = listBounds ?: return@detectDragGesturesAfterLongPress
                                    val touchInRoot = bounds.topLeft + offset
                                    val pressedPageId = pageTileBounds.entries.firstOrNull { it.value.contains(touchInRoot) }?.key
                                    if (pressedPageId != null) {
                                        startPageDrag(pressedPageId)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (draggedPageId != null) {
                                        change.consume()
                                        updatePageDrag(dragAmount)
                                    }
                                },
                                onDragEnd = {
                                    if (draggedPageId != null) {
                                        endPageDrag()
                                    }
                                },
                                onDragCancel = {
                                    if (draggedPageId != null) {
                                        cancelPageDrag()
                                    }
                                },
                            )
                        },
                ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = windowSizeInfo.horizontalPadding,
                        top = 16.dp,
                        end = windowSizeInfo.horizontalPadding,
                        bottom = 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    userScrollEnabled = draggedPageId == null,
                ) {
            if (document == null) {
                item(key = "missing_document", contentType = "state_card") {
                    MissingDocumentCard(onNavigateUp = onNavigateUp)
                }
                return@LazyColumn
            }

            item(key = "document_metrics", contentType = "metrics") {
                DocumentMetricsRow(
                    groupLabel = uiState.currentGroup?.title ?: "No folder",
                    pageCountLabel = uiState.pages.size.toPageCountLabel(),
                    updatedDate = documentUpdatedDate,
                    onMoveToFolder = { moveSheetVisible = true },
                )
            }

            if (uiState.pages.isEmpty()) {
                item(key = "empty_document", contentType = "state_card") {
                    EmptyDocumentCard(
                        actionsEnabled = !uiState.isMutatingPage,
                        onCapture = onOpenCamera,
                        onUploadImage = onImportImage,
                    )
                }
            } else if (!useMasterDetailLayout && isReviewingPage && selectedPage != null) {
                item(key = "selected_page", contentType = "selected_page") {
                    SelectedPageCard(
                        page = selectedPage,
                        pageCount = uiState.pages.size,
                        onPreview = { previewPageId = selectedPage.id },
                    )
                }
                item(key = "review_actions", contentType = "review_actions") {
                    ReviewActionDock(
                        enabled = !uiState.isMutatingPage,
                        onEdit = { onOpenPageEditor(selectedPage.id) },
                        onReplace = { onReplacePage(selectedPage.id) },
                        onShare = onShareSelectedPage,
                        onDelete = { deleteDialogVisible = true },
                    )
                }
            } else {
                item(key = "pages_header", contentType = "section_header") {
                    DocumentPagesHeader(
                        compact = false,
                        showReorderHint = reorderEnabled,
                        actionsEnabled = !uiState.isMutatingPage,
                        onAddPage = { addPageSheetVisible = true },
                    )
                }
                items(
                    items = uiState.pages.chunked(windowSizeInfo.pageColumns),
                    key = { rowPages -> rowPages.joinToString(separator = "-") { page -> page.id } },
                    contentType = { "page_row" },
                ) { rowPages ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowPages.forEach { page ->
                            DisposableEffect(page.id) {
                                onDispose {
                                    pageTileBounds.remove(page.id)
                                }
                            }
                            val isDragging = draggedPageId == page.id
                            PageOverviewTile(
                                page = page,
                                pageCount = uiState.pages.size,
                                isDragging = isDragging,
                                isDropTarget = dragTargetPageId == page.id,
                                isSelected = draggedPageId == null && page.id == selectedPage?.id,
                                compact = windowSizeInfo.pageColumns == 1,
                                reorderEnabled = reorderEnabled && !isReviewingPage,
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer {
                                        alpha = if (isDragging) 0.28f else 1f
                                    }
                                    .onGloballyPositioned { coordinates ->
                                        pageTileBounds[page.id] = coordinates.boundsInRoot()
                                    },
                                onClick = {
                                    if (draggedPageId == null) {
                                        onOpenPageEditor(page.id)
                                    }
                                },
                            )
                        }
                        val emptyCells = windowSizeInfo.pageColumns - rowPages.size
                        repeat(emptyCells) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
                val draggedPage = draggedPageId?.let { pageId ->
                    uiState.pages.firstOrNull { page -> page.id == pageId }
                }
                val startBounds = dragStartBounds
                val containerBounds = listBounds
                if (draggedPage != null && startBounds != null && containerBounds != null) {
                    val overlayHeight = startBounds.height
                    val containerHeight = containerBounds.height
                    PageOverviewTile(
                        page = draggedPage,
                        pageCount = uiState.pages.size,
                        isDragging = true,
                        isDropTarget = false,
                        compact = windowSizeInfo.pageColumns == 1,
                        modifier = Modifier
                            .width(with(density) { startBounds.width.toDp() })
                            .height(with(density) { overlayHeight.toDp() })
                            .offset {
                                val rawX = startBounds.left - containerBounds.left + dragOffset.x
                                val rawY = startBounds.top - containerBounds.top + dragOffset.y
                                IntOffset(
                                    x = rawX.roundToInt(),
                                    y = rawY
                                        .coerceIn(0f, (containerHeight - overlayHeight).coerceAtLeast(0f))
                                        .roundToInt(),
                                )
                            }
                            .zIndex(10f)
                            .graphicsLayer {
                                scaleX = 1.02f
                                scaleY = 1.02f
                            },
                        onClick = {},
                    )
                }
                }
            }
        }
    }

    if (deleteDialogVisible && selectedPage != null) {
        ScanlyConfirmDialog(
            title = "Delete page",
            text = "Page ${selectedPage.pageIndex + 1} will be removed from this document.",
            confirmLabel = "Delete",
            onDismiss = { deleteDialogVisible = false },
            onConfirm = {
                deleteDialogVisible = false
                isReviewingPage = false
                onDeleteSelectedPage()
            },
            confirmDestructive = true,
        )
    }

    if (previewPage != null) {
        ZoomableImageDialog(
            imagePath = previewPage.processedImagePath ?: previewPage.rawImagePath ?: previewPage.thumbnailPath,
            title = "Page ${previewPage.pageIndex + 1}",
            onDismiss = { previewPageId = null },
        )
    }

    if (exportSheetVisible && document != null) {
        ExportShareSheet(
            exportInProgress = uiState.isExporting,
            onDismiss = { exportSheetVisible = false },
            onSavePdf = {
                exportSheetVisible = false
                pdfActionMode = PdfActionMode.SAVE
            },
            onSharePdf = {
                exportSheetVisible = false
                pdfActionMode = PdfActionMode.SHARE
            },
            onSaveImageArchive = {
                exportSheetVisible = false
                onExportImageArchive()
            },
            onShareImages = {
                exportSheetVisible = false
                onShareImages()
            },
        )
    }

    if (moveSheetVisible && document != null) {
        MoveToFolderSheet(
            currentGroupId = document.groupId,
            groups = uiState.availableGroups,
            onDismiss = { moveSheetVisible = false },
            onSelectFolder = { groupId ->
                moveSheetVisible = false
                onMoveToGroup(groupId)
            },
            onCreateFolderAndMove = { name ->
                moveSheetVisible = false
                onCreateFolderAndMove(name)
            },
            onSuggestFolderName = onSuggestGroupTitle,
        )
    }

    if (addPageSheetVisible) {
        AddPageSheet(
            onDismiss = { addPageSheetVisible = false },
            onCapture = {
                addPageSheetVisible = false
                onOpenCamera()
            },
            onUploadImage = {
                addPageSheetVisible = false
                onImportImage()
            },
        )
    }

    if (renameDialogVisible && document != null) {
        DocumentTitleDialog(
            title = "Rename document",
            initialValue = document.title,
            confirmLabel = "Save",
            onDismiss = { renameDialogVisible = false },
            onConfirm = { value ->
                renameDialogVisible = false
                onRenameDocument(value)
            },
        )
    }

    if (deleteDocumentDialogVisible && document != null) {
        ScanlyConfirmDialog(
            title = "Delete document?",
            text = "\"${document.title}\" and all of its pages will be removed permanently.",
            confirmLabel = "Delete",
            onDismiss = { deleteDocumentDialogVisible = false },
            onConfirm = {
                deleteDocumentDialogVisible = false
                onDeleteDocument()
            },
            confirmDestructive = true,
        )
    }

    if (pdfActionMode != null) {
        PdfOptionsSheet(
            options = pdfOptions,
            confirmLabel = "Generate",
            onDismiss = { pdfActionMode = null },
            onOptionsChanged = { updatedOptions -> pdfOptions = updatedOptions },
            onConfirm = {
                val selectedOptions = pdfOptions
                pdfOptions = pdfOptions.copy(password = null)
                val selectedMode = pdfActionMode
                pdfActionMode = null
                when (selectedMode) {
                    PdfActionMode.SAVE -> onExportPdf(selectedOptions)
                    PdfActionMode.SHARE -> onSharePdf(selectedOptions)
                    null -> Unit
                }
            },
        )
    }

    if (uiState.isExporting) {
        ExportProgressOverlay(
            message = uiState.exportMessage ?: "Preparing export",
        )
    }

    if (uiState.isImporting) {
        ScanlyImportProgressOverlay(
            current = uiState.importCurrent,
            total = uiState.importTotal,
            stageLabel = uiState.importStageLabel.ifBlank { "Working on your photos" },
        )
    }
}
