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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    val selectedPage = uiState.selectedPage

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = uiState.document?.title ?: "Document") },
                navigationIcon = {
                    TextButton(onClick = onNavigateUp) {
                        Text(text = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenCamera) {
                        Text(text = "Add page")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val document = uiState.document
            if (document == null) {
                item {
                    InfoCard(
                        title = "Document not found",
                        body = "This document may have been deleted. Return to the library to create or open another one.",
                    )
                }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = document.title,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                text = if (uiState.pages.isEmpty()) {
                                    "No pages yet. Add a page to start building this document."
                                } else {
                                    "${uiState.pages.size} pages ready. Review the selected page below, then reorder, replace, edit, or keep adding more."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = onOpenCamera,
                                ) {
                                    Text(
                                        text = if (uiState.pages.isEmpty()) {
                                            "Add first page"
                                        } else {
                                            "Add another page"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.pages.isEmpty()) {
                    item {
                        InfoCard(
                            title = "No pages captured yet",
                            body = "Tap Add page to open the scanner. Once pages exist, this screen becomes the full review and assembly flow.",
                        )
                    }
                } else if (selectedPage != null) {
                    item {
                        SelectedPageCard(
                            page = selectedPage,
                            pageCount = uiState.pages.size,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        ReviewActionRow(
                            primaryLabel = "Edit",
                            secondaryLabel = "Replace",
                            tertiaryLabel = "Delete",
                            enabled = !uiState.isMutatingPage,
                            onPrimary = { onOpenPageEditor(selectedPage.id) },
                            onSecondary = { onReplacePage(selectedPage.id) },
                            onTertiary = { deleteDialogVisible = true },
                        )
                    }
                    item {
                        PageOrderCard(
                            page = selectedPage,
                            pageCount = uiState.pages.size,
                            enabled = !uiState.isMutatingPage,
                            onMoveLeft = onMoveSelectedPageLeft,
                            onMoveRight = onMoveSelectedPageRight,
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Document pages",
                                style = MaterialTheme.typography.titleLarge,
                            )
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

                item {
                    DetailCard(
                        title = "Document metadata",
                        lines = listOf(
                            "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"}",
                            "Created ${document.createdAtMillis.toReadableDateTime()}",
                            "Updated ${document.updatedAtMillis.toReadableDateTime()}",
                        ),
                    )
                }
                item {
                    DetailCard(
                        title = "Storage",
                        lines = listOf(
                            "Root directory",
                            document.rootDirectoryPath,
                            "Cover thumbnail",
                            document.coverThumbnailPath ?: "Not available",
                        ),
                    )
                }
            }
        }
    }

    if (deleteDialogVisible && selectedPage != null) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text(text = "Delete page") },
            text = {
                Text(
                    text = "Delete Page ${selectedPage.pageIndex + 1} from this document? The raw, processed, and thumbnail files for that page will be removed.",
                )
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
}

@Composable
private fun SelectedPageCard(
    page: ScanPage,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Selected page",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DocumentThumbnail(
                thumbnailPath = page.thumbnailPath ?: page.processedImagePath,
                title = "Page ${page.pageIndex + 1}",
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Page ${page.pageIndex + 1} of $pageCount",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = page.processingState.toDisplayLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (page.processingState == PageProcessingState.NEEDS_REVIEW) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = "Captured ${page.createdAtMillis.toReadableDateTime()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewActionRow(
    primaryLabel: String,
    secondaryLabel: String,
    tertiaryLabel: String,
    enabled: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onTertiary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = onPrimary,
            enabled = enabled,
        ) {
            Text(text = primaryLabel)
        }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onSecondary,
            enabled = enabled,
        ) {
            Text(text = secondaryLabel)
        }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onTertiary,
            enabled = enabled,
        ) {
            Text(text = tertiaryLabel)
        }
    }
}

@Composable
private fun PageOrderCard(
    page: ScanPage,
    pageCount: Int,
    enabled: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Page order",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Move the selected page earlier or later in the final document order.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Currently ${page.pageIndex + 1} of $pageCount",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onMoveLeft,
                    enabled = enabled && page.pageIndex > 0,
                ) {
                    Text(text = "Move left")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onMoveRight,
                    enabled = enabled && page.pageIndex < pageCount - 1,
                ) {
                    Text(text = "Move right")
                }
            }
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
            .size(width = 148.dp, height = 204.dp)
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DocumentThumbnail(
                thumbnailPath = page.thumbnailPath,
                title = "Page ${page.pageIndex + 1}",
                modifier = Modifier.fillMaxWidth(),
                minHeight = 92.dp,
            )
            Text(
                text = "Page ${page.pageIndex + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = if (selected) {
                    "Selected · ${page.processingState.toDisplayLabel()}"
                } else {
                    page.processingState.toDisplayLabel()
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    lines: List<String>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Long.toReadableDateTime(): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT,
).format(Date(this))

private fun PageProcessingState.toDisplayLabel(): String = when (this) {
    PageProcessingState.CAPTURED -> "Captured"
    PageProcessingState.PROCESSED -> "Processed scan"
    PageProcessingState.NEEDS_REVIEW -> "Processed, review crop later"
}
