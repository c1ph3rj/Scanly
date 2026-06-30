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
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.feature.components.PagePreview
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

object DocumentDestination {
    const val documentIdArgument = "documentId"
    const val routePattern = "document/{$documentIdArgument}"

    fun route(documentId: String): String = "document/$documentId"
}

private enum class PdfActionMode {
    SAVE,
    SHARE,
}

private fun Int.toPageCountLabel(): String = if (this == 1) "1 page" else "$this pages"

@Composable
fun DocumentDetailRoute(
    onNavigateUp: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenPageEditor: (String) -> Unit,
    onReplacePage: (String) -> Unit,
    viewModel: DocumentDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPdfExport by remember { mutableStateOf<ExportArtifact?>(null) }
    var pendingArchiveExport by remember { mutableStateOf<ExportArtifact?>(null) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(PdfMimeType),
    ) { uri ->
        val artifact = pendingPdfExport
        pendingPdfExport = null
        if (uri == null || artifact == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            saveExportedFile(
                context = context,
                artifact = artifact,
                destinationUri = uri,
                snackbarHostState = snackbarHostState,
            )
        }
    }

    val saveArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ZipMimeType),
    ) { uri ->
        val artifact = pendingArchiveExport
        pendingArchiveExport = null
        if (uri == null || artifact == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            saveExportedFile(
                context = context,
                artifact = artifact,
                destinationUri = uri,
                snackbarHostState = snackbarHostState,
            )
        }
    }

    val importImagesLauncher = rememberLauncherForActivityResult(
        contract = ImageImportSupport.pickMultipleVisualMediaContract(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is DocumentDetailEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is DocumentDetailEvent.SaveExportedFile -> {
                    when (event.artifact.mimeType) {
                        PdfMimeType -> {
                            pendingPdfExport = event.artifact
                            savePdfLauncher.launch(event.artifact.fileName)
                        }

                        ZipMimeType -> {
                            pendingArchiveExport = event.artifact
                            saveArchiveLauncher.launch(event.artifact.fileName)
                        }

                        else -> snackbarHostState.showSnackbar("Unsupported export format.")
                    }
                }

                is DocumentDetailEvent.ShareFiles -> {
                    sharePreparedFiles(
                        context = context,
                        artifact = event.artifact,
                    )
                }

                DocumentDetailEvent.DocumentDeleted -> onNavigateUp()
            }
        }
    }

    DocumentDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onOpenCamera = onOpenCamera,
        onOpenPageEditor = onOpenPageEditor,
        onReplacePage = onReplacePage,
        onSelectPage = viewModel::selectPage,
        onMovePage = viewModel::movePage,
        onDeleteSelectedPage = viewModel::deleteSelectedPage,
        onExportPdf = viewModel::exportPdf,
        onSharePdf = viewModel::sharePdf,
        onExportImageArchive = viewModel::exportImageArchive,
        onShareImages = viewModel::shareImages,
        onShareSelectedPage = viewModel::shareSelectedPage,
        onMoveToGroup = viewModel::moveToGroup,
        onCreateFolderAndMove = viewModel::createFolderAndMove,
        onImportImage = {
            importImagesLauncher.launch(ImageImportSupport.createPickRequest())
        },
        onRenameDocument = viewModel::renameDocument,
        onDeleteDocument = viewModel::deleteDocument,
    )
}

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
    BackHandler(enabled = !useMasterDetailLayout && isReviewingPage) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewTopBar(
    title: String,
    pageCount: Int,
    onNavigateUp: () -> Unit,
    onOpenExportSheet: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    exportEnabled: Boolean,
    pageReviewActive: Boolean,
    onSharePage: () -> Unit,
    menuEnabled: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }
    val topBarColor = MaterialTheme.colorScheme.surface

    Surface(color = topBarColor) {
        TopAppBar(
            title = {
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pageCount.toPageCountLabel(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                ChromeIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onNavigateUp,
                )
            },
            actions = {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        enabled = menuEnabled,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Document options",
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        if (pageReviewActive) {
                            DropdownMenuItem(
                                text = { Text("Share page") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onSharePage()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Export & share") },
                            leadingIcon = {
                                Icon(Icons.Filled.IosShare, contentDescription = null)
                            },
                            enabled = exportEnabled,
                            onClick = {
                                showMenu = false
                                onOpenExportSheet()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Rename document") },
                            leadingIcon = {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Delete document",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AddPageSheet(
    onDismiss: () -> Unit,
    onCapture: () -> Unit,
    onUploadImage: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ScanlySheetContent {
            Text(
                text = "Add page",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Capture a new scan or choose up to ${ImageImportSupport.MAX_IMAGES_PER_IMPORT} images from your gallery.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExportActionRow(
                icon = Icons.Filled.CameraAlt,
                title = "Capture",
                description = "Open the camera to scan a new page",
                enabled = true,
                onClick = onCapture,
            )
            ExportActionRow(
                icon = Icons.Filled.PhotoLibrary,
                title = "Choose images",
                description = "Pick up to ${ImageImportSupport.MAX_IMAGES_PER_IMPORT} photos from your device",
                enabled = true,
                onClick = onUploadImage,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExportShareSheet(
    exportInProgress: Boolean,
    onDismiss: () -> Unit,
    onSavePdf: () -> Unit,
    onSharePdf: () -> Unit,
    onSaveImageArchive: () -> Unit,
    onShareImages: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ScanlySheetContent {
            Text(
                text = "Export & share",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            ExportActionRow(
                icon = Icons.Filled.PictureAsPdf,
                title = "Save PDF",
                enabled = !exportInProgress,
                onClick = onSavePdf,
            )
            ExportActionRow(
                icon = Icons.Filled.Share,
                title = "Share PDF",
                enabled = !exportInProgress,
                onClick = onSharePdf,
            )
            ExportActionRow(
                icon = Icons.Filled.FileDownload,
                title = "Save images ZIP",
                enabled = !exportInProgress,
                onClick = onSaveImageArchive,
            )
            ExportActionRow(
                icon = Icons.Filled.PhotoLibrary,
                title = "Share pages",
                enabled = !exportInProgress,
                onClick = onShareImages,
            )
        }
    }
}

@Composable
private fun ExportProgressOverlay(
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.44f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Please wait while the file is prepared.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DocumentMasterDetailLayout(
    innerPadding: PaddingValues,
    document: ScanDocument?,
    uiState: DocumentDetailUiState,
    documentUpdatedDate: String?,
    selectedPage: ScanPage?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    listBounds: Rect?,
    onListBoundsChanged: (Rect) -> Unit,
    pageTileBounds: MutableMap<String, Rect>,
    draggedPageId: String?,
    dragStartBounds: Rect?,
    dragOffset: Offset,
    dragTargetPageId: String?,
    reorderEnabled: Boolean,
    onStartPageDrag: (String) -> Unit,
    onUpdatePageDrag: (Offset) -> Unit,
    onEndPageDrag: () -> Unit,
    onCancelPageDrag: () -> Unit,
    onNavigateUp: () -> Unit,
    onOpenCamera: () -> Unit,
    onImportImage: () -> Unit,
    onSelectPage: (String) -> Unit,
    onOpenPageEditor: (String) -> Unit,
    onReplacePage: (String) -> Unit,
    onShareSelectedPage: () -> Unit,
    onDeleteSelectedPage: () -> Unit,
    onAddPage: () -> Unit,
    onMoveToFolder: () -> Unit,
    onPreviewPage: (String) -> Unit,
    density: Density,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .weight(0.36f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
                .onGloballyPositioned { coordinates ->
                    onListBoundsChanged(coordinates.boundsInRoot())
                }
                .pointerInput(reorderEnabled) {
                    if (!reorderEnabled) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val bounds = listBounds ?: return@detectDragGesturesAfterLongPress
                            val touchInRoot = bounds.topLeft + offset
                            val pressedPageId = pageTileBounds.entries.firstOrNull { it.value.contains(touchInRoot) }?.key
                            if (pressedPageId != null) {
                                onStartPageDrag(pressedPageId)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (draggedPageId != null) {
                                change.consume()
                                onUpdatePageDrag(dragAmount)
                            }
                        },
                        onDragEnd = {
                            if (draggedPageId != null) {
                                onEndPageDrag()
                            }
                        },
                        onDragCancel = {
                            if (draggedPageId != null) {
                                onCancelPageDrag()
                            }
                        },
                    )
                },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 16.dp,
                    end = 12.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = draggedPageId == null,
            ) {
                if (document == null) {
                    item(key = "missing_document") {
                        MissingDocumentCard(onNavigateUp = onNavigateUp)
                    }
                    return@LazyColumn
                }

                item(key = "document_metrics") {
                    DocumentMetricsRow(
                        groupLabel = uiState.currentGroup?.title ?: "No folder",
                        pageCountLabel = uiState.pages.size.toPageCountLabel(),
                        updatedDate = documentUpdatedDate,
                        onMoveToFolder = onMoveToFolder,
                    )
                }

                item(key = "pages_header") {
                    DocumentPagesHeader(
                        compact = true,
                        showReorderHint = reorderEnabled,
                        actionsEnabled = !uiState.isMutatingPage,
                        onAddPage = onAddPage,
                    )
                }

                if (uiState.pages.isEmpty()) {
                    item(key = "empty_document_hint") {
                        Text(
                            text = "Use Scan or Import to add your first page.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                } else {
                    items(
                        items = uiState.pages,
                        key = { it.id },
                        contentType = { "page_tile" },
                    ) { page ->
                        DisposableEffect(page.id) {
                            onDispose { pageTileBounds.remove(page.id) }
                        }
                        val isDragging = draggedPageId == page.id
                        PageOverviewTile(
                            page = page,
                            pageCount = uiState.pages.size,
                            isDragging = isDragging,
                            isDropTarget = dragTargetPageId == page.id,
                            isSelected = draggedPageId == null && page.id == selectedPage?.id,
                            compact = true,
                            reorderEnabled = reorderEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = if (isDragging) 0.28f else 1f }
                                .onGloballyPositioned { coordinates ->
                                    pageTileBounds[page.id] = coordinates.boundsInRoot()
                                },
                            onClick = {
                                if (draggedPageId == null) {
                                    onSelectPage(page.id)
                                }
                            },
                        )
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
                    compact = true,
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

        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )

        Column(
            modifier = Modifier
                .weight(0.64f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                document == null -> {
                    MissingDocumentCard(onNavigateUp = onNavigateUp)
                }
                uiState.pages.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyDocumentCard(
                            actionsEnabled = !uiState.isMutatingPage,
                            onCapture = onOpenCamera,
                            onUploadImage = onImportImage,
                            modifier = Modifier.widthIn(max = 420.dp),
                        )
                    }
                }
                selectedPage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraLarge),
                    ) {
                        ZoomableImageViewer(
                            imagePath = selectedPage.processedImagePath ?: selectedPage.rawImagePath ?: selectedPage.thumbnailPath,
                            title = "Page ${selectedPage.pageIndex + 1} of ${uiState.pages.size}",
                            onNavigateUp = null,
                            trailingAction = { zoomActive, onResetZoom ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (zoomActive) {
                                        PreviewActionButton(
                                            icon = Icons.Filled.Refresh,
                                            contentDescription = "Reset zoom",
                                            onClick = onResetZoom,
                                        )
                                    }
                                    PreviewActionButton(
                                        icon = Icons.Filled.Crop,
                                        contentDescription = "Edit page",
                                        onClick = { onOpenPageEditor(selectedPage.id) },
                                    )
                                    PreviewActionButton(
                                        icon = Icons.Filled.CameraAlt,
                                        contentDescription = "Retake page",
                                        onClick = { onReplacePage(selectedPage.id) },
                                    )
                                    PreviewActionButton(
                                        icon = Icons.Filled.IosShare,
                                        contentDescription = "Share page",
                                        onClick = onShareSelectedPage,
                                    )
                                    PreviewActionButton(
                                        icon = Icons.Filled.DeleteOutline,
                                        contentDescription = "Delete page",
                                        onClick = onDeleteSelectedPage,
                                    )
                                }
                            },
                        )
                    }
                }
                else -> {
                    DocumentDetailPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentMetricsRow(
    groupLabel: String,
    pageCountLabel: String,
    updatedDate: String?,
    onMoveToFolder: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetricChip(
            label = groupLabel,
            icon = Icons.Filled.Folder,
            modifier = Modifier.clickable(onClick = onMoveToFolder),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
        )
        MetricChip(label = pageCountLabel)
        updatedDate?.let { date ->
            MetricChip(label = date)
        }
    }
}

@Composable
private fun DocumentPagesHeader(
    compact: Boolean,
    showReorderHint: Boolean,
    actionsEnabled: Boolean,
    onAddPage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
        ) {
            Text(
                text = "Pages",
                style = if (compact) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.titleLarge
                },
                fontWeight = FontWeight.SemiBold,
            )
            if (showReorderHint) {
                Text(
                    text = if (compact) {
                        "Long-press and drag to reorder"
                    } else {
                        "Tap to review. Long-press and drag to reorder."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ChromeIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Add page",
            onClick = onAddPage,
            enabled = actionsEnabled,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun DocumentDetailPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "Select a page to preview",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MissingDocumentCard(
    onNavigateUp: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Document not found",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Return to the library and open another one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onNavigateUp) {
                Text(text = "Back")
            }
        }
    }
}

@Composable
private fun EmptyDocumentCard(
    actionsEnabled: Boolean,
    onCapture: () -> Unit,
    onUploadImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Text(
                text = "No pages yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Capture a page or choose up to ${ImageImportSupport.MAX_IMAGES_PER_IMPORT} images to start this document.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onCapture,
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scan",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onUploadImage,
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Import",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedPageCard(
    page: ScanPage,
    pageCount: Int,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    val capturedDate = remember(page.createdAtMillis) {
        page.createdAtMillis.toReadableDateTime()
    }
    val previewMaxHeight = 280.dp
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPreview),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expanded) Modifier.fillMaxHeight() else Modifier)
                .padding(if (expanded) 14.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (expanded) 12.dp else 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Page ${page.pageIndex + 1} of $pageCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = capturedDate,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MetricChip(
                    label = page.processingState.toDisplayLabel(),
                    containerColor = page.processingState.toContainerColor(),
                    contentColor = page.processingState.toContentColor(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (expanded) {
                            Modifier.weight(1f)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                PagePreview(
                    page = page,
                    displaySize = PreviewDisplaySize.DETAIL,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (expanded) {
                                Modifier.fillMaxHeight()
                            } else {
                                Modifier
                            },
                        ),
                    minHeight = if (expanded) 140.dp else 100.dp,
                )
                ChromeIconButton(
                    icon = Icons.Filled.OpenInFull,
                    contentDescription = "Open zoom view",
                    onClick = onPreview,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ReviewActionDock(
    enabled: Boolean,
    onEdit: () -> Unit,
    onReplace: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReviewToolButton(
                icon = Icons.Filled.Crop,
                label = "Edit",
                enabled = enabled,
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            )
            ReviewToolButton(
                icon = Icons.Filled.Refresh,
                label = "Retake",
                enabled = enabled,
                onClick = onReplace,
                modifier = Modifier.weight(1f),
            )
            ReviewToolButton(
                icon = Icons.Filled.IosShare,
                label = "Share",
                enabled = enabled,
                onClick = onShare,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary,
            )
            ReviewToolButton(
                icon = Icons.Filled.DeleteOutline,
                label = "Delete",
                enabled = enabled,
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ReviewToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    val resolvedContainerColor = if (enabled) {
        containerColor
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val resolvedContentColor = if (enabled) {
        contentColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    }
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        color = resolvedContainerColor,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = label,
                tint = resolvedContentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = resolvedContentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PageOverviewTile(
    page: ScanPage,
    pageCount: Int,
    isDragging: Boolean,
    isDropTarget: Boolean,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    compact: Boolean = false,
    reorderEnabled: Boolean = false,
    onClick: () -> Unit,
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tileShape = MaterialTheme.shapes.extraLarge
    val tileColor = when {
        isDropTarget -> MaterialTheme.colorScheme.surfaceContainer
        isDragging -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val tileBorderWidth = if (isSelected || isDragging || isDropTarget) 1.5.dp else 1.dp
    val tileBorderColor = when {
        isDragging -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.42f else 0.34f)
        isDropTarget -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.72f else 0.64f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.72f else 0.64f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.86f else 0.78f)
    val showLeadingAccent = compact && (isSelected || isDragging)
    val showDropIndicator = isDropTarget

    Surface(
        onClick = onClick,
        modifier = modifier.clip(tileShape),
        color = tileColor,
        border = BorderStroke(
            width = tileBorderWidth,
            color = tileBorderColor,
        ),
        shape = tileShape,
        shadowElevation = when {
            isDragging -> 4.dp
            else -> 0.dp
        },
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (compact) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (showLeadingAccent) 16.dp else 12.dp,
                            top = 12.dp,
                            end = 12.dp,
                            bottom = 12.dp,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PagePreview(
                        page = page,
                        displaySize = PreviewDisplaySize.COMPACT,
                        modifier = Modifier.size(72.dp),
                        minHeight = 72.dp,
                        aspectRatio = 1f,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Page ${page.pageIndex + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = page.processingState.toShortLabel(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = page.updatedAtMillis.toReadableDateTime(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (reorderEnabled) {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth()) {
                    PagePreview(
                        page = page,
                        displaySize = PreviewDisplaySize.CARD,
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = 88.dp,
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.6f),
                                        Color.Black.copy(alpha = 0.9f)
                                    )
                                )
                            )
                    )
                    MetricChip(
                        label = "P${page.pageIndex + 1}/$pageCount",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                    )
                    if (reorderEnabled) {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(24.dp),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Page ${page.pageIndex + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Text(
                            text = page.updatedAtMillis.toReadableDateTime(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (showLeadingAccent) {
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(4.dp)
                            .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                            .background(accentColor),
                    )
                }
            }
            if (showDropIndicator) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(accentColor),
                )
            }
        }
    }
}

internal fun resolvePageReorderTargetIndex(
    pageIds: List<String>,
    pageBounds: Map<String, Rect>,
    draggedPageId: String,
    dragCenter: Offset,
    visibleBounds: Rect?,
): Int? {
    if (draggedPageId !in pageIds || pageIds.size < 2) {
        return null
    }

    val remainingPageIds = pageIds.filter { pageId -> pageId != draggedPageId }

    // Keep each candidate's index within the *full* remaining list so the result
    // stays correct even when auto-scroll has pushed some pages off-screen and
    // they were filtered out below. The repository inserts at this same index in
    // the post-removal list, so the two coordinate spaces must match exactly.
    val visibleTargets = remainingPageIds.mapIndexedNotNull { remainingIndex, pageId ->
        val bounds = pageBounds[pageId] ?: return@mapIndexedNotNull null
        if (visibleBounds != null && !bounds.intersects(visibleBounds)) {
            return@mapIndexedNotNull null
        }
        remainingIndex to bounds
    }

    if (visibleTargets.isEmpty()) {
        if (visibleBounds != null) {
            return if (dragCenter.y < visibleBounds.center.y) 0 else remainingPageIds.size
        }
        return null
    }

    // Insert before the first visible page whose center is past the drag point.
    // If the drag point is below every visible page, append right after the last
    // visible one (any pages below it are further down and keep higher indices).
    val match = visibleTargets.firstOrNull { (_, bounds) ->
        dragCenter.isBeforePageCenter(bounds)
    }
    val targetInRemaining = match?.first ?: (visibleTargets.last().first + 1)

    return targetInRemaining.coerceIn(0, remainingPageIds.size)
}

private fun resolvePageReorderTargetPageId(
    pageIds: List<String>,
    draggedPageId: String,
    targetIndex: Int?,
): String? {
    if (targetIndex == null) {
        return null
    }
    val remainingPageIds = pageIds.filter { pageId -> pageId != draggedPageId }
    return remainingPageIds.getOrNull(targetIndex) ?: remainingPageIds.lastOrNull()
}

private fun Offset.isBeforePageCenter(bounds: Rect): Boolean {
    val center = bounds.center
    if (y < center.y) {
        return true
    }
    if (y > center.y) {
        return false
    }
    return x < center.x
}

private fun Rect.edgeScrollDelta(
    pointerY: Float,
    thresholdPx: Float,
    maxScrollDeltaPx: Float,
): Float {
    val topRatio = ((top + thresholdPx - pointerY) / thresholdPx).coerceIn(0f, 1f)
    if (topRatio > 0f) {
        return -maxScrollDeltaPx * topRatio
    }

    val bottomRatio = ((pointerY - (bottom - thresholdPx)) / thresholdPx).coerceIn(0f, 1f)
    if (bottomRatio > 0f) {
        return maxScrollDeltaPx * bottomRatio
    }

    return 0f
}

private fun Rect.intersects(other: Rect): Boolean =
    left < other.right &&
        right > other.left &&
        top < other.bottom &&
        bottom > other.top

private fun Long.toReadableDateTime(): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT,
).format(Date(this))

private fun Long.toShortDate(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))

private fun PageProcessingState.toDisplayLabel(): String = when (this) {
    PageProcessingState.CAPTURED -> "Captured"
    PageProcessingState.PROCESSED -> "Ready"
    PageProcessingState.NEEDS_REVIEW -> "Review"
}

private fun PageProcessingState.toShortLabel(): String = when (this) {
    PageProcessingState.CAPTURED -> "New"
    PageProcessingState.PROCESSED -> "Ready"
    PageProcessingState.NEEDS_REVIEW -> "Review"
}

@Composable
private fun PageProcessingState.toContainerColor() = when (this) {
    PageProcessingState.CAPTURED -> MaterialTheme.colorScheme.secondaryContainer
    PageProcessingState.PROCESSED -> MaterialTheme.colorScheme.primaryContainer
    PageProcessingState.NEEDS_REVIEW -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun PageProcessingState.toContentColor() = when (this) {
    PageProcessingState.CAPTURED -> MaterialTheme.colorScheme.onSecondaryContainer
    PageProcessingState.PROCESSED -> MaterialTheme.colorScheme.onPrimaryContainer
    PageProcessingState.NEEDS_REVIEW -> MaterialTheme.colorScheme.onTertiaryContainer
}

private suspend fun saveExportedFile(
    context: Context,
    artifact: ExportArtifact,
    destinationUri: Uri,
    snackbarHostState: SnackbarHostState,
) {
    val result = runCatching {
        withContext(Dispatchers.IO) {
            val sourceFile = File(artifact.filePath)
            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: error("Could not open the selected destination.")
        }
    }
    if (result.isSuccess) {
        snackbarHostState.showSnackbar("Saved ${artifact.fileName}")
    } else {
        snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Could not save export.")
    }
}

private fun sharePreparedFiles(
    context: Context,
    artifact: ShareArtifact,
) {
    val uris = artifact.filePaths.map(context::exportUriFor)
    val shareIntent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = artifact.mimeType
            putExtra(Intent.EXTRA_STREAM, uris.first())
            putExtra(Intent.EXTRA_TITLE, artifact.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = artifact.mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            putExtra(Intent.EXTRA_TITLE, artifact.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share ${artifact.title}"))
}

private fun Context.exportUriFor(path: String): Uri = FileProvider.getUriForFile(
    this,
    "$packageName.fileprovider",
    File(path),
)

private const val PdfMimeType = "application/pdf"
private const val ZipMimeType = "application/zip"

@Composable
private fun PreviewActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        color = Color.Black.copy(alpha = 0.42f),
        contentColor = Color.White,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
            )
        }
    }
}
