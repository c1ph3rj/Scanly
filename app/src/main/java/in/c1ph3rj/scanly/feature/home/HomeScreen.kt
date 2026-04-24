package `in`.c1ph3rj.scanly.feature.home

import android.graphics.BitmapFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.feature.groups.DocumentGroupPickerSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeRoute(
    onOpenDocument: (String) -> Unit,
    onOpenGroups: () -> Unit,
    onOpenSearch: () -> Unit,
    onNavigateUp: (() -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HomeEvent.OpenDocument -> onOpenDocument(event.documentId)
                is HomeEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    HomeScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onRenameDocument = viewModel::renameDocument,
        onDeleteDocument = viewModel::deleteDocument,
        onChangeDocumentGroup = viewModel::changeDocumentGroup,
        onCreateGroupForDocument = viewModel::createGroupForDocument,
        onOpenDocument = onOpenDocument,
        onOpenGroups = onOpenGroups,
        onOpenSearch = onOpenSearch,
        onSortModeChanged = viewModel::updateSortMode,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onRenameDocument: (String, String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onChangeDocumentGroup: (String, String?) -> Unit,
    onCreateGroupForDocument: (String, String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenGroups: () -> Unit,
    onOpenSearch: () -> Unit,
    onSortModeChanged: (DocumentSortMode) -> Unit,
    onNavigateUp: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var renameTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var deleteTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var groupTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var sortSheetVisible by rememberSaveable { mutableStateOf(false) }
    val totalPages = remember(uiState.documents) { uiState.documents.sumOf { it.pageCount } }

    // Warm up CameraX while the user is on home to reduce scan-session cold start latency.
    remember(context) {
        ProcessCameraProvider.getInstance(context.applicationContext)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HomeTopBar(
                    onNavigateUp = onNavigateUp,
                )
            }
            item {
                SearchField(
                    onClick = onOpenSearch,
                )
            }
            item {
                GroupsPreviewSection(
                    groups = uiState.groups,
                    ungroupedDocumentCount = uiState.documents.count { it.groupId == null },
                    totalPageCount = totalPages,
                    onOpenGroups = onOpenGroups,
                )
            }

            when {
                uiState.isLoading -> {
                    item {
                        LoadingCard()
                    }
                }

                uiState.documents.isEmpty() -> {
                    item {
                        EmptyLibraryCard()
                    }
                }

                else -> {
                    item {
                        AllDocumentsHeader(
                            count = uiState.documents.size,
                            sortMode = uiState.sortMode,
                            onOpenSort = { sortSheetVisible = true },
                        )
                    }
                    items(
                        items = uiState.documents,
                        key = { document -> document.id },
                    ) { document ->
                        DocumentCard(
                            document = document,
                            onOpen = { onOpenDocument(document.id) },
                            onChangeGroup = { groupTarget = document },
                            onRename = { renameTarget = document },
                            onDelete = { deleteTarget = document },
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { document ->
        DocumentTitleDialog(
            title = "Rename document",
            initialValue = document.title,
            confirmLabel = "Save",
            onDismiss = { renameTarget = null },
            onConfirm = { value ->
                renameTarget = null
                onRenameDocument(document.id, value)
            },
        )
    }

    deleteTarget?.let { document ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = "Delete document") },
            text = {
                Text(text = "\"${document.title}\" and its pages will be removed.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        onDeleteDocument(document.id)
                    },
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    if (sortSheetVisible) {
        SortModeSheet(
            selectedSortMode = uiState.sortMode,
            onDismiss = { sortSheetVisible = false },
            onSelect = { sortMode ->
                sortSheetVisible = false
                onSortModeChanged(sortMode)
            },
        )
    }

    groupTarget?.let { document ->
        DocumentGroupPickerSheet(
            document = document,
            groups = uiState.groups,
            onDismiss = { groupTarget = null },
            onSelectGroup = { groupId ->
                groupTarget = null
                onChangeDocumentGroup(document.id, groupId)
            },
            onCreateGroup = { name ->
                groupTarget = null
                onCreateGroupForDocument(document.id, name)
            },
        )
    }
}

@Composable
private fun HomeTopBar(
    onNavigateUp: (() -> Unit)?,
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
            if (onNavigateUp != null) {
                ChromeIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onNavigateUp,
                )
            }
            Column {
                Text(
                    text = "Scanly",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Documents",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Search documents or groups",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroupsPreviewSection(
    groups: List<DocumentGroup>,
    ungroupedDocumentCount: Int,
    totalPageCount: Int,
    onOpenGroups: () -> Unit,
) {
    val previewGroups = groups.take(2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Groups",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Manage",
                modifier = Modifier.clickable(onClick = onOpenGroups),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (previewGroups.isEmpty()) {
            GroupSummaryRow(
                name = "Ungrouped",
                documentCount = ungroupedDocumentCount,
                pageCount = totalPageCount,
                onClick = onOpenGroups,
            )
        } else {
            previewGroups.forEach { group ->
                GroupSummaryRow(
                    name = group.name,
                    documentCount = group.documentCount,
                    pageCount = group.pageCount,
                    onClick = onOpenGroups,
                )
            }
        }
    }
}

@Composable
private fun GroupSummaryRow(
    name: String,
    documentCount: Int,
    pageCount: Int,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$documentCount documents · $pageCount pages",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AllDocumentsHeader(
    count: Int,
    sortMode: DocumentSortMode,
    onOpenSort: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "All Documents",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (count == 1) "1 document" else "$count documents",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            modifier = Modifier.clickable(onClick = onOpenSort),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = sortMode.label,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = "Loading documents",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun EmptyLibraryCard(
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "No documents yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Use the scan action below to create the first document.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DocumentCard(
    document: ScanDocument,
    onOpen: () -> Unit,
    onChangeGroup: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentThumbnail(
                thumbnailPath = document.coverThumbnailPath,
                title = document.title,
                modifier = Modifier.weight(0.32f),
                minHeight = 106.dp,
            )
            Column(
                modifier = Modifier.weight(0.68f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricChip(
                        label = document.groupName ?: "Ungrouped",
                        icon = Icons.Filled.Folder,
                        modifier = Modifier.clickable(onClick = onChangeGroup),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    MetricChip(
                        label = "${document.pageCount}",
                        icon = Icons.Filled.Description,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
                Text(
                    text = "Modified ${document.updatedAtMillis.toShortDate()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChromeIconButton(
                        icon = Icons.Filled.FolderOpen,
                        contentDescription = "Open document",
                        onClick = onOpen,
                    )
                    ChromeIconButton(
                        icon = Icons.Filled.Edit,
                        contentDescription = "Rename document",
                        onClick = onRename,
                    )
                    ChromeIconButton(
                        icon = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete document",
                        onClick = onDelete,
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentThumbnail(
    thumbnailPath: String?,
    title: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 180.dp,
) {
    val imageBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = thumbnailPath,
    ) {
        value = withContext(Dispatchers.IO) {
            thumbnailPath?.let { path ->
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            }
        }
    }

    Surface(
        modifier = modifier
            .heightIn(min = minHeight),
        color = if (imageBitmap != null) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        shape = MaterialTheme.shapes.large,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = "$title cover thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = DocumentPresentationFormatter.initials(title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DocumentTitleDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(text = "Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SortModeSheet(
    selectedSortMode: DocumentSortMode,
    onDismiss: () -> Unit,
    onSelect: (DocumentSortMode) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Sort documents",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            DocumentSortMode.entries.forEach { sortMode ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(sortMode) },
                    color = if (sortMode == selectedSortMode) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = sortMode.label,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (sortMode == selectedSortMode) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

private fun Long.toShortDate(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))
