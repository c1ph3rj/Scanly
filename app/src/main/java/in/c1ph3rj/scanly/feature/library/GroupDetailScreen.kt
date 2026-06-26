package `in`.c1ph3rj.scanly.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.feature.components.DocumentCard
import `in`.c1ph3rj.scanly.feature.components.DocumentTitleDialog
import `in`.c1ph3rj.scanly.feature.components.ExportActionRow
import `in`.c1ph3rj.scanly.feature.components.GroupNameDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlySheetContent
import `in`.c1ph3rj.scanly.feature.components.FullScreenLoader
import `in`.c1ph3rj.scanly.feature.components.PdfOptionsSheet
import `in`.c1ph3rj.scanly.feature.components.ScanlyExtendedFab
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Route argument key — must match ScanlyNavHost composable declaration
const val GROUP_ID_ARG = "groupId"

@Composable
fun GroupDetailRoute(
    onNavigateUp: () -> Unit,
    onOpenDocument: (String) -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var pendingExport by remember { mutableStateOf<GroupDetailEvent.ExportReady?>(null) }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val artifact = pendingExport ?: return@rememberLauncherForActivityResult
        pendingExport = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val outputStream = context.contentResolver.openOutputStream(uri)
                        ?: error("Could not open the selected destination.")
                    outputStream.use { out ->
                        java.io.File(artifact.filePath).inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                }
            }.onFailure { error ->
                snackbarHostState.showSnackbar(
                    error.message ?: "Could not save the exported file.",
                )
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is GroupDetailEvent.ExportReady -> {
                    pendingExport = event
                    writeLauncher.launch(event.fileName)
                }
                is GroupDetailEvent.ShareFiles -> shareGroupArtifact(context, event.artifact)
                is GroupDetailEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is GroupDetailEvent.OpenDocument -> onOpenDocument(event.documentId)
                is GroupDetailEvent.GroupDeleted -> onNavigateUp()
            }
        }
    }

    GroupDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onOpenDocument = onOpenDocument,
        onRenameGroup = viewModel::renameGroup,
        onDeleteGroup = viewModel::deleteGroup,
        onAddDocument = viewModel::addDocumentToGroup,
        onCreateDocument = viewModel::createDocumentInGroup,
        onRemoveDocument = viewModel::removeDocumentFromGroup,
        onSaveMergedPdf = viewModel::saveMergedPdf,
        onShareMergedPdf = viewModel::shareMergedPdf,
        onSaveZippedPdfs = viewModel::saveZippedPdfs,
        onShareZippedPdfs = viewModel::shareZippedPdfs,
        onCancelExport = viewModel::cancelExport,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDetailScreen(
    uiState: GroupDetailUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onRenameGroup: (String) -> Unit,
    onDeleteGroup: () -> Unit,
    onAddDocument: (String) -> Unit,
    onCreateDocument: (String) -> Unit,
    onRemoveDocument: (String) -> Unit,
    onSaveMergedPdf: (PdfExportOptions) -> Unit,
    onShareMergedPdf: (PdfExportOptions) -> Unit,
    onSaveZippedPdfs: (PdfExportOptions) -> Unit,
    onShareZippedPdfs: (PdfExportOptions) -> Unit,
    onCancelExport: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var pendingExportAction by remember { mutableStateOf<GroupExportAction?>(null) }
    var pdfOptions by remember { mutableStateOf(PdfExportOptions()) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<ScanDocument?>(null) }

    val windowSizeInfo = rememberWindowSizeInfo()
    val group = uiState.group
    val hasDocuments = uiState.documents.isNotEmpty()
    val documentRows = remember(uiState.documents, windowSizeInfo.groupColumns) {
        if (windowSizeInfo.isTablet) uiState.documents.chunked(windowSizeInfo.groupColumns)
        else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = group?.title ?: "Group",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showExportSheet = true },
                        enabled = hasDocuments,
                    ) {
                        Icon(Icons.Filled.IosShare, contentDescription = "Export and share")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename group") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showRenameDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete group",
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
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                FullScreenLoader(modifier = Modifier.padding(innerPadding))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = windowSizeInfo.horizontalPadding,
                        end = windowSizeInfo.horizontalPadding,
                        top = 12.dp,
                        bottom = 100.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Group stats header
                    group?.let { g ->
                        item(key = "header") {
                            GroupStatsHeader(
                                documentCount = g.documentCount,
                                totalPageCount = g.totalPageCount,
                            )
                        }
                    }

                    if (uiState.documents.isEmpty()) {
                        item(key = "empty") {
                            EmptyGroupCard(onAddDocument = { showAddSheet = true })
                        }
                    } else if (documentRows != null) {
                        // Tablet: multi-column grid rows
                        items(
                            items = documentRows,
                            key = { rowItems -> "doc_row_${rowItems.first().id}" },
                            contentType = { "document_row" },
                        ) { rowItems ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                            ) {
                                rowItems.forEach { document ->
                                    DocumentCard(
                                        document = document,
                                        onOpen = { onOpenDocument(document.id) },
                                        onDelete = { removeTarget = document },
                                        onMove = null,
                                        showRename = false,
                                        deleteContentDescription = "Remove from group",
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                val emptyCells = windowSizeInfo.groupColumns - rowItems.size
                                repeat(emptyCells) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        // Phone: single-column list
                        items(
                            items = uiState.documents,
                            key = { it.id },
                            contentType = { "document_card" },
                        ) { document ->
                            DocumentCard(
                                document = document,
                                onOpen = { onOpenDocument(document.id) },
                                onDelete = { removeTarget = document },
                                onMove = null,
                                showRename = false,
                                deleteContentDescription = "Remove from group",
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            // Export progress overlay
            AnimatedVisibility(
                visible = uiState.exportProgress != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                uiState.exportProgress?.let { progress ->
                    ExportProgressBar(
                        progress = progress,
                        onCancel = onCancelExport,
                        modifier = Modifier.padding(innerPadding).padding(16.dp),
                    )
                }
            }

            ScanlyExtendedFab(
                text = "Add document",
                onClick = { showAddSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    ),
            )
        }
    }

    // Dialogs
    if (showRenameDialog) {
        GroupNameDialog(
            title = "Rename group",
            initialValue = group?.title.orEmpty(),
            confirmLabel = "Save",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle ->
                showRenameDialog = false
                onRenameGroup(newTitle)
            },
        )
    }

    if (showDeleteDialog) {
        ScanlyConfirmDialog(
            title = "Delete group?",
            text = "\"${group?.title.orEmpty()}\" will be deleted. " +
                "Documents inside will be moved to ungrouped.",
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDeleteGroup()
            },
            confirmLabel = "Delete",
            confirmDestructive = true,
        )
    }

    removeTarget?.let { doc ->
        ScanlyConfirmDialog(
            title = "Remove from group?",
            text = "\"${doc.title}\" will be moved to ungrouped documents.",
            onDismiss = { removeTarget = null },
            onConfirm = {
                removeTarget = null
                onRemoveDocument(doc.id)
            },
            confirmLabel = "Remove",
        )
    }

    // Add-document bottom sheet
    if (showAddSheet) {
        AddDocumentSheet(
            ungroupedDocuments = uiState.ungroupedDocuments,
            onCreateNew = {
                showAddSheet = false
                showCreateDialog = true
            },
            onAdd = { docId ->
                showAddSheet = false
                onAddDocument(docId)
            },
            onDismiss = { showAddSheet = false },
        )
    }

    if (showCreateDialog) {
        DocumentTitleDialog(
            title = "New document",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = { newTitle ->
                showCreateDialog = false
                onCreateDocument(newTitle)
            },
        )
    }

    if (showExportSheet) {
        GroupExportShareSheet(
            documentCount = uiState.documents.size,
            onDismiss = { showExportSheet = false },
            onSelectAction = { action ->
                showExportSheet = false
                pendingExportAction = action
            },
        )
    }

    pendingExportAction?.let { action ->
        PdfOptionsSheet(
            options = pdfOptions,
            confirmLabel = if (action.isShare) "Share" else "Save",
            titleText = action.optionsTitle,
            onDismiss = { pendingExportAction = null },
            onOptionsChanged = { updated -> pdfOptions = updated },
            onConfirm = {
                val selectedOptions = pdfOptions
                pendingExportAction = null
                when (action) {
                    GroupExportAction.SAVE_MERGED -> onSaveMergedPdf(selectedOptions)
                    GroupExportAction.SHARE_MERGED -> onShareMergedPdf(selectedOptions)
                    GroupExportAction.SAVE_ZIPPED -> onSaveZippedPdfs(selectedOptions)
                    GroupExportAction.SHARE_ZIPPED -> onShareZippedPdfs(selectedOptions)
                }
            },
        )
    }
}

private enum class GroupExportAction(
    val isShare: Boolean,
    val optionsTitle: String,
) {
    SAVE_MERGED(isShare = false, optionsTitle = "Merged PDF options"),
    SHARE_MERGED(isShare = true, optionsTitle = "Merged PDF options"),
    SAVE_ZIPPED(isShare = false, optionsTitle = "Separate PDFs options"),
    SHARE_ZIPPED(isShare = true, optionsTitle = "Separate PDFs options"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupExportShareSheet(
    documentCount: Int,
    onDismiss: () -> Unit,
    onSelectAction: (GroupExportAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val docLabel = if (documentCount == 1) "1 document" else "$documentCount documents"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ScanlySheetContent {
            Text(
                text = "Export & share",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            GroupExportSectionLabel(
                text = "Single PDF · $docLabel merged into one file",
            )
            ExportActionRow(
                icon = Icons.Filled.PictureAsPdf,
                title = "Save merged PDF",
                enabled = true,
                onClick = { onSelectAction(GroupExportAction.SAVE_MERGED) },
            )
            ExportActionRow(
                icon = Icons.Filled.Share,
                title = "Share merged PDF",
                enabled = true,
                onClick = { onSelectAction(GroupExportAction.SHARE_MERGED) },
            )

            GroupExportSectionLabel(
                text = "Separate PDFs · one PDF per document, bundled in a ZIP",
            )
            ExportActionRow(
                icon = Icons.Filled.FolderZip,
                title = "Save PDFs as ZIP",
                enabled = true,
                onClick = { onSelectAction(GroupExportAction.SAVE_ZIPPED) },
            )
            ExportActionRow(
                icon = Icons.Filled.Share,
                title = "Share PDFs as ZIP",
                enabled = true,
                onClick = { onSelectAction(GroupExportAction.SHARE_ZIPPED) },
            )
        }
    }
}

@Composable
private fun GroupExportSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private fun shareGroupArtifact(
    context: android.content.Context,
    artifact: `in`.c1ph3rj.scanly.domain.model.ShareArtifact,
) {
    val uris = artifact.filePaths.map { path ->
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            java.io.File(path),
        )
    }
    if (uris.isEmpty()) return
    val shareIntent = if (uris.size == 1) {
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = artifact.mimeType
            putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
            putExtra(android.content.Intent.EXTRA_TITLE, artifact.title)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = artifact.mimeType
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
            putExtra(android.content.Intent.EXTRA_TITLE, artifact.title)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(
        android.content.Intent.createChooser(shareIntent, "Share ${artifact.title}"),
    )
}

@Composable
private fun GroupStatsHeader(documentCount: Int, totalPageCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricChip(
            label = "$documentCount ${if (documentCount == 1) "document" else "documents"}",
            icon = Icons.Filled.FolderOpen,
        )
        MetricChip(
            label = "$totalPageCount ${if (totalPageCount == 1) "page" else "pages"}",
            icon = Icons.Filled.FileOpen,
        )
    }
}

@Composable
private fun EmptyGroupCard(onAddDocument: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "No documents in this group yet",
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onAddDocument) { Text("Add documents") }
        }
    }
}

@Composable
private fun ExportProgressBar(
    progress: ExportProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = progress.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            }
            LinearProgressIndicator(
                progress = { if (progress.total > 0) progress.current.toFloat() / progress.total else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDocumentSheet(
    ungroupedDocuments: List<ScanDocument>,
    onCreateNew: () -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        ScanlySheetContent {
            Text(
                text = "Add document to group",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Surface(
                onClick = onCreateNew,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Create new document",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Starts an empty document inside this folder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = "Or move an existing document",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )

            if (ungroupedDocuments.isEmpty()) {
                Text(
                    text = "No ungrouped documents available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = ungroupedDocuments,
                        key = { it.id },
                        contentType = { "ungrouped_doc" },
                    ) { doc ->
                        Surface(
                            onClick = { onAdd(doc.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column {
                                    Text(
                                        text = doc.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${doc.pageCount} ${if (doc.pageCount == 1) "page" else "pages"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
