package `in`.c1ph3rj.scanly.feature.library

import `in`.c1ph3rj.scanly.core.common.appPluralString
import `in`.c1ph3rj.scanly.feature.components.LibraryCardStyle
import `in`.c1ph3rj.scanly.feature.components.ScanlyModalBottomSheet
import `in`.c1ph3rj.scanly.feature.components.rememberScanlySheetState
import `in`.c1ph3rj.scanly.feature.components.LibraryListItem
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.feature.components.CachedThumbnail
import `in`.c1ph3rj.scanly.feature.components.formatLibraryModifiedDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.adaptiveColumnCount
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import androidx.compose.ui.unit.takeOrElse
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.feature.components.DocumentCard
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailTopBar
import `in`.c1ph3rj.scanly.feature.components.DocumentTitleDialog
import `in`.c1ph3rj.scanly.feature.components.ExportActionRow
import `in`.c1ph3rj.scanly.feature.components.GroupNameDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlySheetContent
import `in`.c1ph3rj.scanly.feature.components.FullScreenLoader
import `in`.c1ph3rj.scanly.feature.components.PdfOptionsSheet
import `in`.c1ph3rj.scanly.feature.components.ScanlyExtendedFab
import kotlinx.coroutines.flow.collectLatest
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource

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
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
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
        onAddDocuments = viewModel::addDocumentsToGroup,
        onCreateDocument = viewModel::createDocumentInGroup,
        onSuggestTitle = viewModel::suggestDocumentTitle,
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
    onDeleteGroup: (includeDocuments: Boolean) -> Unit,
    onAddDocuments: (Collection<String>) -> Unit,
    onCreateDocument: (String) -> Unit,
    onSuggestTitle: suspend (DocumentTitleFormat) -> String,
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
    val isCompactWidth = windowSizeInfo.widthClass == WindowWidthClass.Compact

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScanlyDetailTopBar(
                title = group?.title ?: stringResource(R.string.home_folder),
                onNavigateUp = onNavigateUp,
                actions = {
                    IconButton(
                        onClick = { showExportSheet = true },
                        enabled = hasDocuments,
                    ) {
                        Icon(Icons.Filled.IosShare, contentDescription = stringResource(R.string.group_detail_export_and_share))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.shared_more_options))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.group_detail_rename_folder)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showRenameDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.group_detail_delete_folder),
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
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            // Wide windows: a cover grid sized like the Library's, so covers never balloon.
            val gridWidth = minOf(maxWidth, windowSizeInfo.wideContentMaxWidth.takeOrElse { maxWidth }) -
                windowSizeInfo.horizontalPadding * 2
            val gridColumns = adaptiveColumnCount(gridWidth, 148.dp, minColumns = 2)
            val documentRows = remember(uiState.documents, gridColumns, isCompactWidth) {
                if (isCompactWidth) null else uiState.documents.chunked(gridColumns)
            }
            if (uiState.isLoading) {
                FullScreenLoader(modifier = Modifier.padding(innerPadding))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = windowSizeInfo.wideContentMaxWidth)
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = windowSizeInfo.horizontalPadding,
                        end = windowSizeInfo.horizontalPadding,
                        top = 12.dp,
                        bottom = 100.dp,
                    ),
                ) {
                    // Spacing is per item: the phone list is segmented and must not get extra gaps.
                    group?.let { g ->
                        item(key = "header") {
                            Box(modifier = Modifier.padding(bottom = 12.dp)) {
                                GroupStatsHeader(
                                    documentCount = g.documentCount,
                                    totalPageCount = g.totalPageCount,
                                )
                            }
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
                                    .padding(bottom = 10.dp)
                                    .animateItem(),
                            ) {
                                rowItems.forEach { document ->
                                    DocumentCard(
                                        document = document,
                                        onOpen = { onOpenDocument(document.id) },
                                        onDelete = { removeTarget = document },
                                        showRename = false,
                                        deleteContentDescription = stringResource(R.string.library_card_presentation_remove_from_folder),
                                        deleteIsRemoveFromFolder = true,
                                        style = LibraryCardStyle.Preview,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                val emptyCells = gridColumns - rowItems.size
                                repeat(emptyCells) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        // Phone: segmented list, same rows and relative dates as the Library.
                        itemsIndexed(
                            items = uiState.documents,
                            key = { _, document -> document.id },
                            contentType = { _, _ -> "document_row" },
                        ) { index, document ->
                            LibraryListItem(
                                first = index == 0,
                                last = index == uiState.documents.lastIndex,
                                modifier = Modifier.animateItem(),
                            ) {
                                DocumentCard(
                                    document = document,
                                    onOpen = { onOpenDocument(document.id) },
                                    onDelete = { removeTarget = document },
                                    showRename = false,
                                    deleteContentDescription = stringResource(R.string.library_card_presentation_remove_from_folder),
                                    deleteIsRemoveFromFolder = true,
                                    style = LibraryCardStyle.PreviewList,
                                )
                            }
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

            // Hidden while exporting: it sat on top of the progress bar's Cancel button.
            if (uiState.exportProgress == null) {
                ScanlyExtendedFab(
                    text = stringResource(R.string.group_detail_add_documents_2),
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
    }

    // Dialogs
    if (showRenameDialog) {
        GroupNameDialog(
            title = stringResource(R.string.group_detail_rename_folder),
            initialValue = group?.title.orEmpty(),
            confirmLabel = stringResource(R.string.document_detail_save),
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle ->
                showRenameDialog = false
                onRenameGroup(newTitle)
            },
        )
    }

    if (showDeleteDialog) {
        FolderDeleteDialog(
            title = group?.title.orEmpty(),
            documentCount = group?.documentCount ?: 0,
            onDismiss = { showDeleteDialog = false },
            onDelete = { includeDocuments ->
                showDeleteDialog = false
                onDeleteGroup(includeDocuments)
            },
        )
    }

    removeTarget?.let { doc ->
        ScanlyConfirmDialog(
            title = stringResource(R.string.group_detail_remove_from_folder),
            text = stringResource(R.string.group_detail_will_be_moved_out_of, doc.title),
            onDismiss = { removeTarget = null },
            onConfirm = {
                removeTarget = null
                onRemoveDocument(doc.id)
            },
            confirmLabel = stringResource(R.string.group_detail_remove),
        )
    }

    // Add-document bottom sheet (single or multi select)
    if (showAddSheet) {
        AddDocumentSheet(
            ungroupedDocuments = uiState.ungroupedDocuments,
            onCreateNew = {
                showAddSheet = false
                showCreateDialog = true
            },
            onAdd = { docIds ->
                showAddSheet = false
                onAddDocuments(docIds)
            },
            onDismiss = { showAddSheet = false },
        )
    }

    if (showCreateDialog) {
        DocumentTitleDialog(
            title = stringResource(R.string.group_detail_new_document),
            initialValue = "",
            confirmLabel = stringResource(R.string.shared_create),
            onDismiss = { showCreateDialog = false },
            onConfirm = { newTitle ->
                if (showCreateDialog) {
                    showCreateDialog = false
                    onCreateDocument(newTitle)
                }
            },
            onSuggestTitle = onSuggestTitle,
            autoFillSuggestedName = true,
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
            confirmLabel = if (action.isShare) stringResource(R.string.document_detail_share_2) else stringResource(R.string.document_detail_save),
            titleText = action.optionsTitle,
            onDismiss = { pendingExportAction = null },
            onOptionsChanged = { updated -> pdfOptions = updated },
            onConfirm = {
                val selectedOptions = pdfOptions
                pdfOptions = pdfOptions.copy(password = null)
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
    @param:StringRes val optionsTitleRes: Int,
) {
    SAVE_MERGED(isShare = false, optionsTitleRes = R.string.group_export_action_merged_pdf_options),
    SHARE_MERGED(isShare = true, optionsTitleRes = R.string.group_export_action_merged_pdf_options),
    SAVE_ZIPPED(isShare = false, optionsTitleRes = R.string.group_export_action_separate_pdfs_options),
    SHARE_ZIPPED(isShare = true, optionsTitleRes = R.string.group_export_action_separate_pdfs_options),
    ;

    val optionsTitle: String get() = appString(optionsTitleRes)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupExportShareSheet(
    documentCount: Int,
    onDismiss: () -> Unit,
    onSelectAction: (GroupExportAction) -> Unit,
) {
    val sheetState = rememberScanlySheetState()
    val docLabel = pluralStringResource(R.plurals.common_documents, documentCount, documentCount)

    ScanlyModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = { MaterialTheme.colorScheme.surface },
    ) {
        ScanlySheetContent {
            Text(
                text = stringResource(R.string.document_detail_export_share),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            GroupExportSectionLabel(
                text = stringResource(R.string.group_detail_single_pdf_merged_into_one, docLabel),
            )
            ExportActionRow(
                icon = Icons.Filled.PictureAsPdf,
                title = stringResource(R.string.group_detail_save_merged_pdf),
                enabled = true,
                onClick = { onSelectAction(GroupExportAction.SAVE_MERGED) },
            )
            ExportActionRow(
                icon = Icons.Filled.Share,
                title = stringResource(R.string.group_detail_share_merged_pdf),
                enabled = true,
                onClick = { onSelectAction(GroupExportAction.SHARE_MERGED) },
            )

            GroupExportSectionLabel(
                text = stringResource(R.string.group_detail_separate_pdfs_one_pdf_per),
            )
            ExportActionRow(
                icon = Icons.Filled.FolderZip,
                title = stringResource(R.string.group_detail_save_pdfs_as_zip),
                enabled = true,
                onClick = { onSelectAction(GroupExportAction.SAVE_ZIPPED) },
            )
            ExportActionRow(
                icon = Icons.Filled.Share,
                title = stringResource(R.string.group_detail_share_pdfs_as_zip),
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
        android.content.Intent.createChooser(shareIntent, appString(R.string.document_detail_share, artifact.title)),
    )
}

@Composable
private fun GroupStatsHeader(documentCount: Int, totalPageCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricChip(
            label = pluralStringResource(R.plurals.common_documents, documentCount, documentCount),
            icon = Icons.Filled.FolderOpen,
        )
        MetricChip(
            label = pluralStringResource(R.plurals.common_pages, totalPageCount, totalPageCount),
            icon = Icons.Filled.FileOpen,
        )
    }
}

@Composable
private fun EmptyGroupCard(onAddDocument: () -> Unit) {
    `in`.c1ph3rj.scanly.feature.components.PipEmptyState(
        pipLine = `in`.c1ph3rj.scanly.feature.pip.PipLines.FOLDER_EMPTY,
        pipMood = `in`.c1ph3rj.scanly.feature.pip.PipMood.IDLE,
        title = stringResource(R.string.group_detail_this_folder_is_empty),
        description = stringResource(R.string.group_detail_add_a_document_here_to),
        actionLabel = stringResource(R.string.group_detail_add_documents_2),
        onAction = onAddDocument,
        actionIcon = Icons.Filled.Add,
        compact = true,
    )
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
                    Text(stringResource(R.string.export_share_cancel), color = MaterialTheme.colorScheme.error)
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
    onAdd: (Collection<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    val configuration = LocalConfiguration.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val selectedCount = selectedIds.size
    val selectedAccent = MaterialTheme.colorScheme.primary
    val availableCount = ungroupedDocuments.size
    val allSelected = availableCount > 0 && selectedCount == availableCount

    // Scale list height to screen: shorter on landscape phones, taller on tablets.
    val listMaxHeight = remember(configuration.screenHeightDp, windowSizeInfo) {
        val screenH = configuration.screenHeightDp.toFloat()
        when {
            windowSizeInfo.useCompactLandscapeLayout -> (screenH * 0.32f).coerceIn(140f, 220f)
            windowSizeInfo.isTablet -> (screenH * 0.46f).coerceIn(280f, 520f)
            else -> (screenH * 0.40f).coerceIn(200f, 380f)
        }.dp
    }
    val useTwoColumnList = windowSizeInfo.isTablet &&
        windowSizeInfo.widthClass != WindowWidthClass.Compact &&
        availableCount >= 4
    val documentRows = remember(ungroupedDocuments, useTwoColumnList) {
        if (useTwoColumnList) ungroupedDocuments.chunked(2) else null
    }

    ScanlyModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = { MaterialTheme.colorScheme.surface },
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        ScanlySheetContent {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.group_detail_add_documents_to_folder),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (availableCount == 0) {
                        stringResource(R.string.group_detail_create_a_new_document_or_2)
                    } else {
                        stringResource(R.string.group_detail_create_a_new_document_or, availableCount)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                onClick = onCreateNew,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.extraLarge,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                ),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.large,
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.group_detail_create_new_document),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.group_detail_starts_empty_inside_this_folder),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.group_detail_move_existing),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (availableCount == 0) {
                            stringResource(R.string.group_detail_nothing_available_to_move)
                        } else {
                            stringResource(R.string.group_detail_select_one_or_more_documents)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (availableCount > 1) {
                    TextButton(
                        onClick = {
                            selectedIds = if (allSelected) {
                                emptySet()
                            } else {
                                ungroupedDocuments.map { it.id }.toSet()
                            }
                        },
                    ) {
                        Text(if (allSelected) stringResource(R.string.group_detail_clear) else stringResource(R.string.group_detail_select_all))
                    }
                }
            }

            if (availableCount == 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = stringResource(R.string.group_detail_no_loose_documents),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.group_detail_documents_already_in_folders_won),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (documentRows != null) {
                        items(
                            items = documentRows,
                            key = { row -> row.joinToString("-") { it.id } },
                            contentType = { "ungrouped_doc_row" },
                        ) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { doc ->
                                    AddDocumentPickerRow(
                                        document = doc,
                                        isSelected = doc.id in selectedIds,
                                        selectedAccent = selectedAccent,
                                        onToggle = {
                                            selectedIds = if (doc.id in selectedIds) {
                                                selectedIds - doc.id
                                            } else {
                                                selectedIds + doc.id
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(
                            items = ungroupedDocuments,
                            key = { it.id },
                            contentType = { "ungrouped_doc" },
                        ) { doc ->
                            AddDocumentPickerRow(
                                document = doc,
                                isSelected = doc.id in selectedIds,
                                selectedAccent = selectedAccent,
                                onToggle = {
                                    selectedIds = if (doc.id in selectedIds) {
                                        selectedIds - doc.id
                                    } else {
                                        selectedIds + doc.id
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                            )
                        }
                    }
                }
            }

            // Action row stays visible for empty + selection states.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (windowSizeInfo.toolPrimaryActionMaxWidth != Dp.Unspecified) {
                                Modifier.widthIn(max = windowSizeInfo.toolPrimaryActionMaxWidth)
                            } else {
                                Modifier
                            },
                        ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.export_share_cancel))
                    }
                    Button(
                        onClick = { onAdd(selectedIds) },
                        enabled = selectedCount > 0,
                        modifier = Modifier.weight(1.35f),
                    ) {
                        Text(
                            text = when (selectedCount) {
                                0 -> stringResource(R.string.group_detail_add_selected)
                                1 -> stringResource(R.string.group_detail_add_1_document)
                                else -> stringResource(R.string.group_detail_add_documents, selectedCount)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddDocumentPickerRow(
    document: ScanDocument,
    isSelected: Boolean,
    selectedAccent: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Names alone are often near-identical ("Scan 20-Oct…"), so pair the cover with when it was last edited.
    val meta = remember(document.pageCount, document.updatedAtMillis) {
        val pages = when (document.pageCount) {
            0 -> appString(R.string.document_detail_no_pages_yet)
            else -> appPluralString(R.plurals.common_pages, document.pageCount, document.pageCount)
        }
        val time = android.text.format.DateFormat.getTimeFormat(context)
            .format(java.util.Date(document.updatedAtMillis))
        "$pages  ·  ${formatLibraryModifiedDate(document.updatedAtMillis)}, $time"
    }
    val thumbShape = RoundedCornerShape(8.dp)

    Surface(
        onClick = onToggle,
        modifier = modifier.semantics { selected = isSelected },
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) {
                selectedAccent.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CachedThumbnail(
                thumbnailPath = document.coverThumbnailPath,
                title = document.title,
                contentRevision = document.updatedAtMillis,
                modifier = Modifier
                    .size(width = 48.dp, height = 64.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), thumbShape),
                shape = thumbShape,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                placeholderIcon = {
                    Text(
                        text = DocumentPresentationFormatter.initials(document.title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (isSelected) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Outlined.Circle
                },
                contentDescription = if (isSelected) stringResource(R.string.shared_selected) else stringResource(R.string.group_detail_not_selected),
                tint = if (isSelected) {
                    selectedAccent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
