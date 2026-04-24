package `in`.c1ph3rj.scanly.feature.document

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageDialog
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.PdfPageMargin
import `in`.c1ph3rj.scanly.domain.model.PdfPageOrientation
import `in`.c1ph3rj.scanly.domain.model.PdfPageSize
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.feature.groups.DocumentGroupPickerSheet
import `in`.c1ph3rj.scanly.feature.home.DocumentThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

object DocumentDestination {
    const val documentIdArgument = "documentId"
    const val routePattern = "document/{$documentIdArgument}"

    fun route(documentId: String): String = "document/$documentId"
}

private enum class PdfActionMode {
    SAVE,
    SHARE,
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPdfExport by remember { mutableStateOf<ExportArtifact?>(null) }
    var pendingArchiveExport by remember { mutableStateOf<ExportArtifact?>(null) }

    val importImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris)
        }
    }

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
            }
        }
    }

    DocumentDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onOpenCamera = onOpenCamera,
        onImportImages = {
            importImagesLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onOpenPageEditor = onOpenPageEditor,
        onReplacePage = onReplacePage,
        onSelectPage = viewModel::selectPage,
        onMoveSelectedPageLeft = viewModel::moveSelectedPageLeft,
        onMoveSelectedPageRight = viewModel::moveSelectedPageRight,
        onDeleteSelectedPage = viewModel::deleteSelectedPage,
        onExportPdf = viewModel::exportPdf,
        onSharePdf = viewModel::sharePdf,
        onExportImageArchive = viewModel::exportImageArchive,
        onShareImages = viewModel::shareImages,
        onChangeDocumentGroup = viewModel::changeDocumentGroup,
        onCreateGroupForDocument = viewModel::createGroupForDocument,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DocumentDetailScreen(
    uiState: DocumentDetailUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onOpenCamera: () -> Unit,
    onImportImages: () -> Unit,
    onOpenPageEditor: (String) -> Unit,
    onReplacePage: (String) -> Unit,
    onSelectPage: (String) -> Unit,
    onMoveSelectedPageLeft: () -> Unit,
    onMoveSelectedPageRight: () -> Unit,
    onDeleteSelectedPage: () -> Unit,
    onExportPdf: (PdfExportOptions) -> Unit,
    onSharePdf: (PdfExportOptions) -> Unit,
    onExportImageArchive: () -> Unit,
    onShareImages: () -> Unit,
    onChangeDocumentGroup: (String?) -> Unit,
    onCreateGroupForDocument: (String) -> Unit,
) {
    var deleteDialogVisible by rememberSaveable(uiState.selectedPageId) { mutableStateOf(false) }
    var previewPageId by rememberSaveable { mutableStateOf<String?>(null) }
    var addPageSheetVisible by rememberSaveable { mutableStateOf(false) }
    var exportSheetVisible by rememberSaveable { mutableStateOf(false) }
    var groupSheetVisible by rememberSaveable { mutableStateOf(false) }
    var pdfActionMode by rememberSaveable { mutableStateOf<PdfActionMode?>(null) }
    var pdfOptions by remember { mutableStateOf(PdfExportOptions()) }
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
                    onOpenExportSheet = { exportSheetVisible = true },
                    onAddPage = { addPageSheetVisible = true },
                    exportEnabled = !uiState.isExporting && uiState.pages.isNotEmpty(),
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
                    GroupChip(
                        label = document.groupName ?: "Ungrouped",
                        onClick = { groupSheetVisible = true },
                    )
                }
            }

            if (selectedPage == null) {
                item {
                    EmptyDocumentCard(
                        onOpenCamera = onOpenCamera,
                        onImportImages = onImportImages,
                        importEnabled = !uiState.isImporting,
                    )
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

    if (addPageSheetVisible && document != null) {
        AddPageSheet(
            importEnabled = !uiState.isImporting,
            onDismiss = { addPageSheetVisible = false },
            onOpenCamera = {
                addPageSheetVisible = false
                onOpenCamera()
            },
            onImportImages = {
                addPageSheetVisible = false
                onImportImages()
            },
        )
    }

    if (pdfActionMode != null) {
        PdfOptionsSheet(
            options = pdfOptions,
            onDismiss = { pdfActionMode = null },
            onOptionsChanged = { updatedOptions -> pdfOptions = updatedOptions },
            onConfirm = {
                val selectedOptions = pdfOptions
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

    if (groupSheetVisible && document != null) {
        DocumentGroupPickerSheet(
            document = document,
            groups = uiState.groups,
            onDismiss = { groupSheetVisible = false },
            onSelectGroup = { groupId ->
                groupSheetVisible = false
                onChangeDocumentGroup(groupId)
            },
            onCreateGroup = { name ->
                groupSheetVisible = false
                onCreateGroupForDocument(name)
            },
        )
    }

    if (uiState.isExporting || uiState.isImporting) {
        ExportProgressOverlay(
            message = uiState.exportMessage ?: "Importing images",
        )
    }
}

@Composable
private fun GroupChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewTopBar(
    title: String,
    pageCount: Int,
    onNavigateUp: () -> Unit,
    onOpenExportSheet: () -> Unit,
    onAddPage: () -> Unit,
    exportEnabled: Boolean,
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChromeIconButton(
                icon = Icons.Filled.Share,
                contentDescription = "Export and share",
                onClick = onOpenExportSheet,
                enabled = exportEnabled,
            )
            ChromeIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "Add page",
                onClick = onAddPage,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
private fun ExportActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
        },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AddPageSheet(
    importEnabled: Boolean,
    onDismiss: () -> Unit,
    onOpenCamera: () -> Unit,
    onImportImages: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Add pages",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Capture new pages or upload images into this document.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AddPageActionRow(
                icon = Icons.Filled.CameraAlt,
                title = "Capture with camera",
                enabled = true,
                onClick = onOpenCamera,
            )
            AddPageActionRow(
                icon = Icons.Filled.PhotoLibrary,
                title = "Upload from gallery",
                enabled = importEnabled,
                onClick = onImportImages,
            )
        }
    }
}

@Composable
private fun AddPageActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
        },
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PdfOptionsSheet(
    options: PdfExportOptions,
    onDismiss: () -> Unit,
    onOptionsChanged: (PdfExportOptions) -> Unit,
    onConfirm: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "PDF options",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            PdfOptionSection(title = "Orientation") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PdfChoiceTile(
                        label = PdfPageOrientation.PORTRAIT.label,
                        selected = options.orientation == PdfPageOrientation.PORTRAIT,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onOptionsChanged(options.copy(orientation = PdfPageOrientation.PORTRAIT))
                        },
                    )
                    PdfChoiceTile(
                        label = PdfPageOrientation.LANDSCAPE.label,
                        selected = options.orientation == PdfPageOrientation.LANDSCAPE,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onOptionsChanged(options.copy(orientation = PdfPageOrientation.LANDSCAPE))
                        },
                    )
                }
            }

            PdfOptionSection(title = "Page size") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PdfPageSize.entries.forEach { pageSize ->
                        PdfChoiceTile(
                            label = pageSize.label,
                            selected = options.pageSize == pageSize,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onOptionsChanged(options.copy(pageSize = pageSize))
                            },
                        )
                    }
                }
            }

            PdfOptionSection(title = "Margin") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PdfPageMargin.entries.forEach { margin ->
                        PdfChoiceTile(
                            label = margin.label,
                            selected = options.margin == margin,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onOptionsChanged(options.copy(margin = margin))
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                ) {
                    Text(text = "Cancel")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                ) {
                    Text(text = "Generate")
                }
            }
        }
    }
}

@Composable
private fun PdfOptionSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun PdfChoiceTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Box(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
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
    onImportImages: () -> Unit,
    importEnabled: Boolean,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AddPageCompactButton(
                    icon = Icons.Filled.CameraAlt,
                    label = "Capture",
                    enabled = true,
                    onClick = onOpenCamera,
                    modifier = Modifier.weight(1f),
                )
                AddPageCompactButton(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Upload",
                    enabled = importEnabled,
                    onClick = onImportImages,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AddPageCompactButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
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
    context.startActivity(Intent.createChooser(shareIntent, "Share pages"))
}

private fun Context.exportUriFor(path: String): Uri = FileProvider.getUriForFile(
    this,
    "$packageName.fileprovider",
    File(path),
)

private const val PdfMimeType = "application/pdf"
private const val ZipMimeType = "application/zip"
