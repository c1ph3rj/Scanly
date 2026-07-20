package `in`.c1ph3rj.scanly.feature.preview

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageState
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageViewer
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import `in`.c1ph3rj.scanly.feature.components.sharePreparedFiles
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow

@Composable
fun PageImagePreviewRoute(
    onNavigateUp: () -> Unit,
    onEditPage: (String) -> Unit,
    onRetakePage: (documentId: String, pageId: String) -> Unit,
    viewModel: PageImagePreviewViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PageImagePreviewEvent.ShowMessage -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is PageImagePreviewEvent.ShareFiles -> sharePreparedFiles(context, event.artifact)
                is PageImagePreviewEvent.PageDeleted -> {
                    if (event.wasLastPage) onNavigateUp()
                }
            }
        }
    }

    PageImagePreviewScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEditPage = onEditPage,
        onRetakePage = onRetakePage,
        onSharePage = viewModel::sharePage,
        onDeletePage = viewModel::deletePage,
        onSelectPage = viewModel::selectPage,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageImagePreviewScreen(
    uiState: PageImagePreviewUiState,
    onNavigateUp: () -> Unit,
    onEditPage: (String) -> Unit,
    onRetakePage: (documentId: String, pageId: String) -> Unit,
    onSharePage: (String) -> Unit,
    onDeletePage: (String) -> Unit,
    onSelectPage: (String) -> Unit,
) {
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp,
            )
        }
        return
    }

    val page = uiState.page
    if (uiState.missingPage || page == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Page not found.",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        return
    }

    val pages = uiState.pages
    val initialPage = remember(pages, uiState.selectedPageId) {
        pages.indexOfFirst { it.id == uiState.selectedPageId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pages.size },
    )
    val pageIds = pages.map { it.id }
    val zoomStates = remember(pageIds) {
        pageIds.associateWith { ZoomableImageState() }
    }
    val visiblePage = pages.getOrNull(pagerState.settledPage) ?: page
    val visibleZoomState = zoomStates[visiblePage.id]
    var showPageMenu by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ScanPage?>(null) }

    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }
            .map(pages::getOrNull)
            .distinctUntilChanged()
            .collectLatest { selectedPage ->
                selectedPage?.let { onSelectPage(it.id) }
            }
    }

    LaunchedEffect(uiState.selectedPageId, pages) {
        val selectedIndex = pages.indexOfFirst { it.id == uiState.selectedPageId }
        if (selectedIndex >= 0 && selectedIndex != pagerState.currentPage) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    LaunchedEffect(visiblePage.id) {
        showPageMenu = false
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { index -> pages[index].id },
                userScrollEnabled = visibleZoomState?.isZoomActive != true,
            ) { pageIndex ->
                val previewPage = pages[pageIndex]
                ZoomableImageViewer(
                    imagePath = previewPage.processedImagePath
                        ?: previewPage.rawImagePath
                        ?: previewPage.thumbnailPath,
                    title = "",
                    state = checkNotNull(zoomStates[previewPage.id]),
                    allowParentHorizontalGestures = true,
                    showTopBar = false,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChromeIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onNavigateUp,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    MetricChip(
                        label = "Page ${visiblePage.pageIndex + 1} of ${pages.size}",
                        containerColor = Color.Black.copy(alpha = 0.42f),
                        contentColor = Color.White,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (visibleZoomState?.isZoomActive == true) {
                        PreviewActionButton(
                            icon = Icons.Filled.FitScreen,
                            contentDescription = "Reset zoom",
                            onClick = visibleZoomState::reset,
                        )
                    }
                    Box {
                        PreviewActionButton(
                            icon = Icons.Filled.MoreVert,
                            contentDescription = "Page options",
                            onClick = { showPageMenu = true },
                        )
                        DropdownMenu(
                            expanded = showPageMenu,
                            onDismissRequest = { showPageMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share page") },
                                leadingIcon = {
                                    Icon(Icons.Filled.IosShare, contentDescription = null)
                                },
                                onClick = {
                                    showPageMenu = false
                                    onSharePage(visiblePage.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Edit page") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Edit, contentDescription = null)
                                },
                                onClick = {
                                    showPageMenu = false
                                    onEditPage(visiblePage.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Retake page") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Refresh, contentDescription = null)
                                },
                                enabled = !uiState.isDeleting,
                                onClick = {
                                    showPageMenu = false
                                    onRetakePage(visiblePage.documentId, visiblePage.id)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete page",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                enabled = !uiState.isDeleting,
                                onClick = {
                                    showPageMenu = false
                                    deleteTarget = visiblePage
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ScanlyConfirmDialog(
            title = "Delete page?",
            text = "Page ${target.pageIndex + 1} will be permanently deleted from this document.",
            confirmLabel = "Delete",
            confirmDestructive = true,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                onDeletePage(target.id)
            },
            confirmEnabled = !uiState.isDeleting,
            dismissEnabled = !uiState.isDeleting,
        )
    }
}

@Composable
private fun PreviewActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
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

