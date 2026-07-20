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
internal fun DocumentMasterDetailLayout(
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
