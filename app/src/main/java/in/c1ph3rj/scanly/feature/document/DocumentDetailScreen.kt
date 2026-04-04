package `in`.c1ph3rj.scanly.feature.document

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageDialog
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.feature.home.DocumentThumbnail
import kotlinx.coroutines.flow.collectLatest
import java.text.DateFormat
import java.util.Date

object DocumentDestination {
    const val documentIdArgument = "documentId"
    const val routePattern = "document/{$documentIdArgument}"

    fun route(documentId: String): String = "document/$documentId"
}

@Composable
fun DocumentDetailRoute(
    onNavigateUp: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenPageEditor: (String) -> Unit,
    onReplacePage: (String) -> Unit,
    viewModel: DocumentDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is DocumentDetailEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
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
        onMoveSelectedPageLeft = viewModel::moveSelectedPageLeft,
        onMoveSelectedPageRight = viewModel::moveSelectedPageRight,
        onDeleteSelectedPage = viewModel::deleteSelectedPage,
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
    onMoveSelectedPageLeft: () -> Unit,
    onMoveSelectedPageRight: () -> Unit,
    onDeleteSelectedPage: () -> Unit,
) {
    var deleteDialogVisible by rememberSaveable(uiState.selectedPageId) { mutableStateOf(false) }
    var previewPageId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPage = uiState.selectedPage
    val document = uiState.document
    val previewPage = previewPageId?.let { pageId ->
        uiState.pages.firstOrNull { page -> page.id == pageId }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                ReviewTopBar(
                    title = document?.title ?: "Document",
                    pageCount = uiState.pages.size,
                    onNavigateUp = onNavigateUp,
                    onAddPage = onOpenCamera,
                )
            }

            if (document == null) {
                item {
                    MissingDocumentCard(onNavigateUp = onNavigateUp)
                }
                return@LazyColumn
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricChip(label = "${uiState.pages.size} pages")
                    MetricChip(label = document.updatedAtMillis.toShortDate())
                }
            }

            if (selectedPage == null) {
                item {
                    EmptyDocumentCard(onOpenCamera = onOpenCamera)
                }
            } else {
                item {
                    SelectedPageCard(
                        page = selectedPage,
                        pageCount = uiState.pages.size,
                        onPreview = { previewPageId = selectedPage.id },
                    )
                }
                item {
                    ReviewActionDock(
                        page = selectedPage,
                        pageCount = uiState.pages.size,
                        enabled = !uiState.isMutatingPage,
                        onEdit = { onOpenPageEditor(selectedPage.id) },
                        onReplace = { onReplacePage(selectedPage.id) },
                        onDelete = { deleteDialogVisible = true },
                        onMoveLeft = onMoveSelectedPageLeft,
                        onMoveRight = onMoveSelectedPageRight,
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Pages",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Tap to switch",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) {
                            items(
                                items = uiState.pages,
                                key = { page -> page.id },
                            ) { page ->
                                PageReviewTile(
                                    page = page,
                                    selected = page.id == selectedPage.id,
                                    onClick = { onSelectPage(page.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteDialogVisible && selectedPage != null) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text(text = "Delete page") },
            text = {
                Text(text = "Page ${selectedPage.pageIndex + 1} will be removed from this document.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialogVisible = false
                        onDeleteSelectedPage()
                    },
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogVisible = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    if (previewPage != null) {
        ZoomableImageDialog(
            imagePath = previewPage.processedImagePath ?: previewPage.rawImagePath ?: previewPage.thumbnailPath,
            title = "Page ${previewPage.pageIndex + 1}",
            onDismiss = { previewPageId = null },
        )
    }
}

@Composable
private fun ReviewTopBar(
    title: String,
    pageCount: Int,
    onNavigateUp: () -> Unit,
    onAddPage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChromeIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateUp,
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = if (pageCount == 1) "1 page" else "$pageCount pages",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ChromeIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Add page",
            onClick = onAddPage,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
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
    onOpenCamera: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "No pages yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Add the first page to start the review flow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenCamera) {
                Text(text = "Open camera")
            }
        }
    }
}

@Composable
private fun SelectedPageCard(
    page: ScanPage,
    pageCount: Int,
    onPreview: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPreview),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box {
                DocumentThumbnail(
                    thumbnailPath = page.thumbnailPath ?: page.processedImagePath,
                    title = "Page ${page.pageIndex + 1}",
                    modifier = Modifier.fillMaxWidth(),
                )
                MetricChip(
                    label = "P${page.pageIndex + 1}/$pageCount",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                )
                MetricChip(
                    label = page.processingState.toDisplayLabel(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    containerColor = page.processingState.toContainerColor(),
                    contentColor = page.processingState.toContentColor(),
                )
                ChromeIconButton(
                    icon = Icons.Filled.OpenInFull,
                    contentDescription = "Open zoom view",
                    onClick = onPreview,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Captured ${page.createdAtMillis.toReadableDateTime()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewActionDock(
    page: ScanPage,
    pageCount: Int,
    enabled: Boolean,
    onEdit: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    icon = Icons.Filled.DeleteOutline,
                    label = "Delete",
                    enabled = enabled,
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ReviewToolButton(
                    icon = Icons.Filled.KeyboardArrowLeft,
                    label = "Earlier",
                    enabled = enabled && page.pageIndex > 0,
                    onClick = onMoveLeft,
                    modifier = Modifier.weight(1f),
                )
                ReviewToolButton(
                    icon = Icons.Filled.KeyboardArrowRight,
                    label = "Later",
                    enabled = enabled && page.pageIndex < pageCount - 1,
                    onClick = onMoveRight,
                    modifier = Modifier.weight(1f),
                )
            }
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
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun PageReviewTile(
    page: ScanPage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(width = 122.dp, height = 176.dp)
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DocumentThumbnail(
                thumbnailPath = page.thumbnailPath,
                title = "Page ${page.pageIndex + 1}",
                modifier = Modifier.fillMaxWidth(),
                minHeight = 90.dp,
            )
            Text(
                text = "P${page.pageIndex + 1}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = page.processingState.toShortLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.84f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

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
