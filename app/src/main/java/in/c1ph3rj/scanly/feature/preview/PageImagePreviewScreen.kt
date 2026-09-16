package `in`.c1ph3rj.scanly.feature.preview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageState
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageViewer
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File

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
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
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
                    showZoomBadge = false,
                    onSingleTap = { chromeVisible = !chromeVisible },
                )
            }

            AnimatedVisibility(
                visible = chromeVisible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn() + slideInVertically { -it / 3 },
                exit = fadeOut() + slideOutVertically { -it / 3 },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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

                    if (visibleZoomState?.isZoomActive == true) {
                        PreviewActionButton(
                            icon = Icons.Filled.FitScreen,
                            contentDescription = "Reset zoom",
                            onClick = visibleZoomState::reset,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = chromeVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 },
            ) {
                PreviewBottomActionBar(
                    enabled = !uiState.isDeleting,
                    onShare = { onSharePage(visiblePage.id) },
                    onEdit = { onEditPage(visiblePage.id) },
                    onRetake = { onRetakePage(visiblePage.documentId, visiblePage.id) },
                    onDelete = { deleteTarget = visiblePage },
                )
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
private fun PreviewBottomActionBar(
    enabled: Boolean,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onRetake: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = Color.Black.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PreviewBottomAction(
                icon = Icons.Filled.IosShare,
                label = "Share",
                enabled = enabled,
                onClick = onShare,
                modifier = Modifier.weight(1f),
            )
            PreviewBottomAction(
                icon = Icons.Filled.Edit,
                label = "Edit",
                enabled = enabled,
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            )
            PreviewBottomAction(
                icon = Icons.Filled.Refresh,
                label = "Retake",
                enabled = enabled,
                onClick = onRetake,
                modifier = Modifier.weight(1f),
            )
            PreviewBottomAction(
                icon = Icons.Filled.DeleteOutline,
                label = "Delete",
                enabled = enabled,
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                contentColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PreviewBottomAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
) {
    val resolvedColor = if (enabled) {
        contentColor
    } else {
        contentColor.copy(alpha = 0.42f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        color = Color.Transparent,
        contentColor = resolvedColor,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = resolvedColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = resolvedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PreviewActionButton(
    icon: ImageVector,
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
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
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
