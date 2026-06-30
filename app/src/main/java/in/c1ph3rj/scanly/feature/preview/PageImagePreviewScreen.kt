package `in`.c1ph3rj.scanly.feature.preview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageState
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PageImagePreviewEvent.ShowMessage -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is PageImagePreviewEvent.ShareFiles -> sharePreparedFiles(context, event.artifact)
                is PageImagePreviewEvent.CopyText -> {
                    context.copyRecognizedText(event.text)
                    snackbarHostState.showSnackbar("Text copied")
                }
            }
        }
    }

    PageImagePreviewScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEditPage = onEditPage,
        onSharePage = viewModel::sharePage,
        onSelectPage = viewModel::selectPage,
        onToggleTextMode = viewModel::toggleTextMode,
        onExitTextMode = viewModel::exitTextMode,
        onRetryTextRecognition = viewModel::retryTextRecognition,
        onSelectTextToken = viewModel::selectTextToken,
        onSelectTextRange = viewModel::selectTextRange,
        onClearTextSelection = viewModel::clearTextSelection,
        onSelectAllText = viewModel::selectAllText,
        onCopySelectedText = viewModel::copySelectedText,
        snackbarHostState = snackbarHostState,
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
    onToggleTextMode: (String) -> Unit,
    onExitTextMode: () -> Unit,
    onRetryTextRecognition: () -> Unit,
    onSelectTextToken: (Int) -> Unit,
    onSelectTextRange: (Int, Int) -> Unit,
    onClearTextSelection: () -> Unit,
    onSelectAllText: () -> Unit,
    onCopySelectedText: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
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
    val textModeActive = uiState.textMode !is PageTextModeState.Inactive
    var showPageMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = textModeActive) {
        onExitTextMode()
    }

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
                userScrollEnabled = !textModeActive && visibleZoomState?.isZoomActive != true,
            ) { pageIndex ->
                val previewPage = pages[pageIndex]
                val readyTextMode = uiState.textMode as? PageTextModeState.Ready
                ZoomableImageViewer(
                    imagePath = previewPage.processedImagePath
                        ?: previewPage.rawImagePath
                        ?: previewPage.thumbnailPath,
                    title = "",
                    state = checkNotNull(zoomStates[previewPage.id]),
                    allowParentHorizontalGestures = true,
                    showTopBar = false,
                    showZoomIndicator = !textModeActive,
                    imageOverlay = { overlayInfo ->
                        if (previewPage.id == uiState.selectedPageId && readyTextMode != null) {
                            PageTextOverlay(
                                recognizedText = readyTextMode.recognizedText,
                                selection = readyTextMode.selection,
                                zoomScale = overlayInfo.scale,
                                selectedColor = MaterialTheme.colorScheme.primary,
                                onSelectToken = onSelectTextToken,
                                onSelectRange = onSelectTextRange,
                                onClearSelection = onClearTextSelection,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
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
                        onClick = {
                            if (textModeActive) onExitTextMode() else onNavigateUp()
                        },
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
                            icon = Icons.Filled.Refresh,
                            contentDescription = "Reset zoom",
                            onClick = visibleZoomState::reset,
                        )
                    }
                    PreviewActionButton(
                        icon = Icons.Filled.TextFields,
                        contentDescription = if (textModeActive) "Close text selection" else "Select text",
                        onClick = { onToggleTextMode(visiblePage.id) },
                        selected = textModeActive,
                        loading = uiState.textMode is PageTextModeState.Loading,
                    )
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
                        }
                    }
                }
            }

            if (textModeActive) {
                TextModeActionBar(
                    textMode = uiState.textMode,
                    onRetry = onRetryTextRecognition,
                    onSelectAll = onSelectAllText,
                    onCopy = onCopySelectedText,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (textModeActive) 96.dp else 16.dp),
            )
        }
    }
}

@Composable
private fun TextModeActionBar(
    textMode: PageTextModeState,
    onRetry: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.78f),
        contentColor = Color.White,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        when (textMode) {
            PageTextModeState.Inactive -> Unit
            is PageTextModeState.Loading -> {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Text("Finding text…", style = MaterialTheme.typography.bodyLarge)
                }
            }
            is PageTextModeState.Ready -> {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${textMode.recognizedText.tokens.size} words found",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = if (textMode.selection == null) "Tap or drag over text"
                            else "${textMode.selection.count()} selected",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = onSelectAll) {
                        Text("Select all", color = Color.White)
                    }
                    Button(
                        onClick = onCopy,
                        enabled = textMode.selection != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("Copy")
                    }
                }
            }
            is PageTextModeState.Empty -> {
                TextModeMessage(
                    title = "No supported text found",
                    supportingText = "Text recognition currently supports Latin script.",
                    onRetry = onRetry,
                )
            }
            is PageTextModeState.Error -> {
                TextModeMessage(
                    title = "Couldn’t detect text",
                    supportingText = "Try again with a clearer page image.",
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun TextModeMessage(
    title: String,
    supportingText: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = supportingText,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onRetry) {
            Text("Retry", color = Color.White)
        }
    }
}

@Composable
private fun PreviewActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    loading: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.42f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(21.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                )
            }
        }
    }
}

private fun Context.copyRecognizedText(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Scanly recognized text", text))
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
