package `in`.c1ph3rj.scanly.feature.preview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageViewer
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow
import java.io.File

@Composable
fun PageImagePreviewRoute(
    onNavigateUp: () -> Unit,
    onEditPage: (String) -> Unit,
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
            }
        }
    }

    PageImagePreviewScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEditPage = onEditPage,
        onSharePage = viewModel::sharePage,
        onSelectPage = viewModel::selectPage,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageImagePreviewScreen(
    uiState: PageImagePreviewUiState,
    onNavigateUp: () -> Unit,
    onEditPage: (String) -> Unit,
    onSharePage: (String) -> Unit,
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
    var zoomedPageId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.currentPage }
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

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        key = { index -> pages[index].id },
        userScrollEnabled = zoomedPageId == null,
    ) { pageIndex ->
        val previewPage = pages[pageIndex]
        ZoomableImageViewer(
            imagePath = previewPage.processedImagePath
                ?: previewPage.rawImagePath
                ?: previewPage.thumbnailPath,
            title = "Page ${previewPage.pageIndex + 1} of ${pages.size}",
            onNavigateUp = onNavigateUp,
            allowParentHorizontalGestures = true,
            onZoomActiveChange = { zoomActive ->
                zoomedPageId = when {
                    zoomActive -> previewPage.id
                    zoomedPageId == previewPage.id -> null
                    else -> zoomedPageId
                }
            },
            trailingAction = { zoomActive, onResetZoom ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (zoomActive) {
                        ChromeIconButton(
                            icon = Icons.Filled.Refresh,
                            contentDescription = "Reset zoom",
                            onClick = onResetZoom,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    PreviewActionButton(
                        icon = Icons.Filled.IosShare,
                        contentDescription = "Share page",
                        onClick = { onSharePage(previewPage.id) },
                    )
                    PreviewActionButton(
                        icon = Icons.Filled.Edit,
                        contentDescription = "Edit page",
                        onClick = { onEditPage(previewPage.id) },
                    )
                }
            },
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
