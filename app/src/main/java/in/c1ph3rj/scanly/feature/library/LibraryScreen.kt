package `in`.c1ph3rj.scanly.feature.library

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import `in`.c1ph3rj.scanly.feature.components.ScanlyModalBottomSheet
import `in`.c1ph3rj.scanly.feature.pip.PipLines
import `in`.c1ph3rj.scanly.feature.pip.PipLook
import `in`.c1ph3rj.scanly.feature.pip.PipMood
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.feature.components.*
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LibraryRoute(
    onOpenDocument: (String) -> Unit,
    onOpenScanSession: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    tabRequest: LibraryTab? = null,
    onTabRequestHandled: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(tabRequest) {
        if (tabRequest != null) {
            viewModel.selectTab(tabRequest)
            onTabRequestHandled()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LibraryEvent.OpenDocument -> onOpenDocument(event.documentId)
                is LibraryEvent.OpenGroup -> onOpenGroup(event.groupId)
                is LibraryEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LibraryScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onCreateDocument = { title, groupId -> viewModel.createDocument(title, groupId) },
        onRenameDocument = viewModel::renameDocument,
        onDeleteDocument = viewModel::deleteDocument,
        onCreateGroup = viewModel::createGroup,
        onRenameGroup = viewModel::renameGroup,
        onDeleteGroup = viewModel::deleteGroup,
        onMoveDocumentToGroup = viewModel::moveDocumentToGroup,
        onMoveDocumentToNewGroup = viewModel::moveDocumentToNewGroup,
        onOpenDocument = onOpenDocument,
        onOpenScanSession = onOpenScanSession,
        onOpenGroup = onOpenGroup,
        onOpenSearch = onOpenSearch,
        onGroupSelected = viewModel::selectGroup,
        onTabSelected = viewModel::selectTab,
        onSortSelected = viewModel::selectSortOption,
        onSuggestTitle = viewModel::suggestDocumentTitle,
        onSuggestGroupTitle = viewModel::suggestGroupTitle,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    snackbarHostState: SnackbarHostState,
    onCreateDocument: (String, String?) -> Unit,
    onRenameDocument: (String, String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (groupId: String, includeDocuments: Boolean) -> Unit,
    onMoveDocumentToGroup: (String, String?) -> Unit,
    onMoveDocumentToNewGroup: (String, String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenScanSession: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onGroupSelected: (String) -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onSortSelected: (LibrarySortOption) -> Unit,
    onSuggestTitle: suspend (DocumentTitleFormat) -> String,
    onSuggestGroupTitle: suspend (GroupTitleFormat) -> String,
) {
    var createDocDialogVisible by rememberSaveable { mutableStateOf(false) }
    var createGroupDialogVisible by rememberSaveable { mutableStateOf(false) }
    var renameDocTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var deleteDocTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var renameGroupTarget by remember { mutableStateOf<DocumentGroup?>(null) }
    var deleteGroupTarget by remember { mutableStateOf<DocumentGroup?>(null) }
    var moveDocTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var gridLayout by rememberSaveable { mutableStateOf(true) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    val windowSizeInfo = rememberWindowSizeInfo()
    val showingFolders = uiState.selectedTab == LibraryTab.Folders && uiState.groups.isNotEmpty()
    val visibleGroups = remember(uiState) { uiState.visibleGroups }
    val previewGroups = remember(visibleGroups) {
        visibleGroups.sortedByDescending { it.updatedAtMillis }.take(3)
    }
    val visibleDocuments = remember(uiState) {
        if (showingFolders) emptyList() else uiState.visibleDocuments
    }
    val documentSections = remember(visibleDocuments, uiState.sortOption) {
        libraryDocumentSections(visibleDocuments, uiState.sortOption)
    }
    val documentColumns = windowSizeInfo.groupColumns
    val folderColumns = if (windowSizeInfo.isTablet) windowSizeInfo.groupColumns + 2 else 3
    val folderGridRows = remember(visibleGroups, folderColumns) {
        (listOf<DocumentGroup?>(null) + visibleGroups).chunked(folderColumns)
    }
    val inset = Modifier.padding(horizontal = windowSizeInfo.horizontalPadding)
    // Grid cards carry their own press inset; pull rows out so covers align with the labels.
    val gridRowInset = (windowSizeInfo.horizontalPadding - LibraryPreviewCardInset).coerceAtLeast(0.dp)
    val folderRowInset = (windowSizeInfo.horizontalPadding - LibraryFolderTileInset).coerceAtLeast(0.dp)

    BackHandler(enabled = showingFolders) { onTabSelected(LibraryTab.All) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ScanlyTestTags.LIBRARY_SCREEN),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (uiState.isLoading) {
                FullScreenLoader(modifier = Modifier.fillMaxSize())
            } else {
                // Items carry their own horizontal inset so the folder shelf can scroll edge to edge.
                LazyColumn(
                    modifier = if (windowSizeInfo.isTablet) {
                        Modifier.widthIn(max = windowSizeInfo.contentMaxWidth).fillMaxHeight()
                    } else {
                        Modifier.fillMaxSize()
                    },
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "header") {
                        LibraryHeader(
                            documentCount = uiState.allDocuments.size,
                            pageCount = uiState.allDocuments.sumOf { it.pageCount },
                            modifier = inset.padding(bottom = 16.dp),
                        )
                    }

                    item(key = "search") {
                        LibrarySearchLauncher(
                            onClick = onOpenSearch,
                            modifier = inset.padding(bottom = 16.dp),
                        )
                    }

                    if (!uiState.hasAnyItems) {
                        item(key = "empty_library") {
                            Box(inset) {
                                EmptyLibraryCard(
                                    onCreateDocument = { createDocDialogVisible = true },
                                    onCreateGroup = { createGroupDialogVisible = true },
                                )
                            }
                        }
                    } else {
                        item(key = "folders_header") {
                            if (showingFolders) {
                                LibraryFoldersExpandedHeader(
                                    folderCount = visibleGroups.size,
                                    sortOption = uiState.sortOption,
                                    onBack = { onTabSelected(LibraryTab.All) },
                                    onSort = { showSortSheet = true },
                                    modifier = inset,
                                )
                            } else {
                                LibrarySectionHeader(
                                    title = "Folders",
                                    count = visibleGroups.size,
                                    actionLabel = if (visibleGroups.isNotEmpty()) "See all" else null,
                                    onAction = { onTabSelected(LibraryTab.Folders) },
                                    modifier = inset,
                                )
                            }
                        }
                        if (showingFolders) {
                            // Expanded in place: the shelf becomes a grid and documents are hidden.
                            items(
                                items = folderGridRows,
                                key = { rowItems -> "folder_row_${rowItems.first()?.id ?: "new"}" },
                                contentType = { "folder_row" },
                            ) { rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .padding(horizontal = folderRowInset)
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .animateItem(),
                                ) {
                                    rowItems.forEach { group ->
                                        if (group == null) {
                                            LibraryNewFolderTile(
                                                onClick = { createGroupDialogVisible = true },
                                                modifier = Modifier.weight(1f),
                                            )
                                        } else {
                                            LibraryFolderTile(
                                                group = group,
                                                onOpen = { onOpenGroup(group.id) },
                                                onRename = { renameGroupTarget = group },
                                                onDelete = { deleteGroupTarget = group },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                    repeat(folderColumns - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            item(key = "folders_shelf") {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                                    contentPadding = PaddingValues(horizontal = folderRowInset),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    item(key = "new_folder") {
                                        LibraryNewFolderTile(
                                            onClick = { createGroupDialogVisible = true },
                                            modifier = Modifier.width(FolderShelfTileWidth),
                                        )
                                    }
                                    items(previewGroups, key = { it.id }) { group ->
                                        LibraryFolderTile(
                                            group = group,
                                            onOpen = { onOpenGroup(group.id) },
                                            onRename = { renameGroupTarget = group },
                                            onDelete = { deleteGroupTarget = group },
                                            modifier = Modifier.width(FolderShelfTileWidth),
                                        )
                                    }
                                    if (visibleGroups.size > 3) {
                                        item(key = "view_all_folders") {
                                            LibraryViewAllFoldersTile(
                                                onClick = { onTabSelected(LibraryTab.Folders) },
                                                modifier = Modifier.width(FolderShelfTileWidth),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!showingFolders) {
                            item(key = "documents_header") {
                                LibraryDocumentsHeader(
                                    documentCount = visibleDocuments.size,
                                    sortOption = uiState.sortOption,
                                    gridLayout = gridLayout,
                                    onSort = { showSortSheet = true },
                                    onLayoutChange = { gridLayout = it },
                                    modifier = inset.padding(bottom = 4.dp),
                                )
                            }

                            if (visibleDocuments.isEmpty()) {
                                item(key = "empty_documents") {
                                    Box(inset) {
                                        EmptyLibraryTabCard(
                                            selectedTab = LibraryTab.Documents,
                                            onCreateDocument = { createDocDialogVisible = true },
                                            onCreateGroup = { createGroupDialogVisible = true },
                                        )
                                    }
                                }
                            }

                            documentSections.forEach { section ->
                                section.title?.let { title ->
                                    item(key = "section_${section.key}", contentType = "section_label") {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = inset
                                                .padding(top = 8.dp, bottom = 8.dp)
                                                .semantics { heading() }
                                                .animateItem(),
                                        )
                                    }
                                }
                                if (gridLayout) {
                                    items(
                                        items = section.documents.chunked(documentColumns),
                                        key = { rowItems -> "document_row_${rowItems.first().id}" },
                                        contentType = { "document_row" },
                                    ) { rowItems ->
                                        Row(
                                            modifier = Modifier
                                                .padding(horizontal = gridRowInset)
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .animateItem(),
                                        ) {
                                            rowItems.forEach { document ->
                                                DocumentCard(
                                                    document = document,
                                                    onOpen = { onOpenDocument(document.id) },
                                                    onRename = { renameDocTarget = document },
                                                    onDelete = { deleteDocTarget = document },
                                                    onMove = { moveDocTarget = document },
                                                    style = LibraryCardStyle.Preview,
                                                    sectionedDate = section.title != null,
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            repeat(documentColumns - rowItems.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                } else {
                                    itemsIndexed(
                                        items = section.documents,
                                        key = { _, document -> "document_${document.id}" },
                                        contentType = { _, _ -> "document" },
                                    ) { index, document ->
                                        LibraryListItem(
                                            first = index == 0,
                                            last = index == section.documents.lastIndex,
                                            modifier = inset
                                                .padding(bottom = if (index == section.documents.lastIndex) 20.dp else 0.dp)
                                                .animateItem(),
                                        ) {
                                            DocumentCard(
                                                document = document,
                                                onOpen = { onOpenDocument(document.id) },
                                                onRename = { renameDocTarget = document },
                                                onDelete = { deleteDocTarget = document },
                                                onMove = { moveDocTarget = document },
                                                style = LibraryCardStyle.PreviewList,
                                                sectionedDate = section.title != null,
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
    }

    if (createDocDialogVisible) {
        NewDocumentDialog(
            groups = uiState.groups,
            initialGroupId = uiState.selectedGroupId,
            onDismiss = { createDocDialogVisible = false },
            onConfirm = { title, groupId ->
                // Ignore a second tap that lands before the dialog leaves composition.
                if (createDocDialogVisible) {
                    createDocDialogVisible = false
                    onCreateDocument(title, groupId)
                }
            },
            onSuggestTitle = onSuggestTitle,
        )
    }

    if (createGroupDialogVisible) {
        GroupNameDialog(
            title = "New folder",
            initialValue = "",
            onDismiss = { createGroupDialogVisible = false },
            onConfirm = { title ->
                if (createGroupDialogVisible) {
                    createGroupDialogVisible = false
                    onCreateGroup(title)
                }
            },
            onSuggestTitle = onSuggestGroupTitle,
            autoFillSuggestedName = true,
        )
    }

    renameDocTarget?.let { doc ->
        DocumentTitleDialog(
            title = "Rename document",
            initialValue = doc.title,
            confirmLabel = "Save",
            onDismiss = { renameDocTarget = null },
            onConfirm = { value ->
                renameDocTarget = null
                onRenameDocument(doc.id, value)
            },
        )
    }

    deleteDocTarget?.let { doc ->
        ScanlyConfirmDialog(
            title = "Delete document?",
            text = TrashCopy.document(doc.title),
            confirmLabel = "Delete",
            onDismiss = { deleteDocTarget = null },
            onConfirm = {
                deleteDocTarget = null
                onDeleteDocument(doc.id)
            },
            confirmDestructive = true,
        )
    }

    renameGroupTarget?.let { group ->
        GroupNameDialog(
            title = "Rename folder",
            initialValue = group.title,
            confirmLabel = "Save",
            onDismiss = { renameGroupTarget = null },
            onConfirm = { value ->
                renameGroupTarget = null
                onRenameGroup(group.id, value)
            },
        )
    }

    deleteGroupTarget?.let { group ->
        FolderDeleteDialog(
            title = group.title,
            documentCount = group.documentCount,
            onDismiss = { deleteGroupTarget = null },
            onDelete = { includeDocuments ->
                deleteGroupTarget = null
                onDeleteGroup(group.id, includeDocuments)
            },
        )
    }

    moveDocTarget?.let { doc ->
        MoveToFolderSheet(
            currentGroupId = doc.groupId,
            groups = uiState.groups,
            onDismiss = { moveDocTarget = null },
            onSelectFolder = { groupId ->
                moveDocTarget = null
                onMoveDocumentToGroup(doc.id, groupId)
            },
            onCreateFolderAndMove = { name ->
                moveDocTarget = null
                onMoveDocumentToNewGroup(doc.id, name)
            },
            onSuggestFolderName = onSuggestGroupTitle,
        )
    }

    if (showSortSheet) {
        LibrarySortSheet(
            selectedTab = uiState.selectedTab,
            selectedOption = uiState.sortOption,
            onSelect = { option ->
                onSortSelected(option)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false },
        )
    }
}

/** Includes the tile's press inset; leaves the next tile peeking on a 360 dp phone. */
private val FolderShelfTileWidth = 104.dp

@Composable
private fun LibraryHeader(
    documentCount: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "$documentCount ${if (documentCount == 1) "document" else "documents"} · " +
                "$pageCount ${if (pageCount == 1) "page" else "pages"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Looks like a search field but routes to the dedicated search screen. */
@Composable
private fun LibrarySearchLauncher(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag(ScanlyTestTags.LIBRARY_SEARCH)
            .semantics { contentDescription = "Search library" },
        shape = RoundedCornerShape(26.dp),
        // Same card-style pill as Home's search, so both entry points look identical.
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(
                "Search names and text in pages",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibraryDocumentsHeader(
    documentCount: Int,
    sortOption: LibrarySortOption,
    gridLayout: Boolean,
    onSort: () -> Unit,
    onLayoutChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LibrarySectionTitle("Documents", documentCount, modifier = Modifier.weight(1f))
        LibrarySortButton(sortOption, onSort)
        Spacer(Modifier.width(4.dp))
        LibraryLayoutToggle(gridLayout, onLayoutChange)
    }
}

/**
 * Header while folders are expanded in place. The back arrow sits beside "Folders" (not
 * "Library", which is a tab) because it only collapses this section, like system back.
 */
@Composable
private fun LibraryFoldersExpandedHeader(
    folderCount: Int,
    sortOption: LibrarySortOption,
    onBack: () -> Unit,
    onSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LibraryIconButton(
            onBack,
            Icons.AutoMirrored.Filled.ArrowBack,
            "Back to library",
            modifier = Modifier.offset(x = (-12).dp),
        )
        LibrarySectionTitle("Folders", folderCount, modifier = Modifier.weight(1f).offset(x = (-8).dp))
        LibrarySortButton(sortOption, onSort)
    }
}

@Composable
private fun LibrarySortButton(sortOption: LibrarySortOption, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = "Sort library" },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(sortOption.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Slim 40 dp segmented track; each segment keeps a 48 dp touch target. */
@Composable
private fun LibraryLayoutToggle(gridLayout: Boolean, onLayoutChange: (Boolean) -> Unit) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier = Modifier
            .testTag(ScanlyTestTags.LIBRARY_LAYOUT_SWITCH)
            .drawBehind {
                val inset = 4.dp.toPx()
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, inset),
                    size = Size(size.width, size.height - inset * 2),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                )
            }
            .selectableGroup(),
    ) {
        listOf(true, false).forEach { grid ->
            val selected = gridLayout == grid
            val interactionSource = remember { MutableInteractionSource() }
            // 48 dp hit area, but the ripple is drawn only on the visible 40 dp pill.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .selectable(
                        selected = selected,
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onLayoutChange(grid) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .indication(interactionSource, ripple())
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (grid) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.List,
                        if (grid) "Grid view" else "List view",
                        modifier = Modifier.size(20.dp),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryTabCard(
    selectedTab: LibraryTab,
    onCreateDocument: () -> Unit,
    onCreateGroup: () -> Unit,
) {
    val isFolders = selectedTab == LibraryTab.Folders
    PipEmptyState(
        pipLine = if (isFolders) PipLines.LIBRARY_NO_FOLDERS else PipLines.LIBRARY_NO_DOCUMENTS,
        pipMood = PipMood.IDLE,
        pipLook = if (isFolders) PipLook.RIGHT else PipLook.AHEAD,
        title = if (isFolders) "No folders yet" else "No documents yet",
        description = if (isFolders) {
            "Create a folder to keep related documents easy to find."
        } else {
            "Create a document, then scan pages or import them from your gallery."
        },
        actionLabel = if (isFolders) "New folder" else "New document",
        onAction = if (isFolders) onCreateGroup else onCreateDocument,
        actionIcon = if (isFolders) Icons.Outlined.CreateNewFolder else Icons.Outlined.Add,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySortSheet(
    selectedTab: LibraryTab,
    selectedOption: LibrarySortOption,
    onSelect: (LibrarySortOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = when (selectedTab) {
        LibraryTab.All -> "folders and documents"
        LibraryTab.Folders -> "folders"
        LibraryTab.Documents -> "documents"
    }
    ScanlyModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = { MaterialTheme.colorScheme.surfaceContainerLow },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Sort library",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Choose how $scope are ordered.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LibrarySortOption.entries.forEach { option ->
                val selected = option == selectedOption
                Surface(
                    onClick = { onSelect(option) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(
                        1.dp,
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = option.sortIcon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = option.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        RadioButton(
                            selected = selected,
                            onClick = null,
                        )
                    }
                }
            }
        }
    }
}

private fun LibrarySortOption.sortIcon(): ImageVector = when (this) {
    LibrarySortOption.RecentlyUpdated -> Icons.Filled.Update
    LibrarySortOption.OldestUpdated -> Icons.Filled.History
    LibrarySortOption.NameAscending,
    LibrarySortOption.NameDescending -> Icons.Filled.SortByAlpha
    LibrarySortOption.NewestCreated -> Icons.Filled.PostAdd
    LibrarySortOption.OldestCreated -> Icons.Filled.Inventory2
}

@Composable
fun NewDocumentDialog(
    groups: List<DocumentGroup>,
    initialGroupId: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (title: String, groupId: String?) -> Unit,
    onSuggestTitle: (suspend (DocumentTitleFormat) -> String)? = null,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var selectedGroupId by rememberSaveable { mutableStateOf(initialGroupId) }
    var isFillingName by remember {
        mutableStateOf(title.isBlank() && onSuggestTitle != null)
    }
    var suggestFormatIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (onSuggestTitle == null || title.isNotBlank()) {
            isFillingName = false
            return@LaunchedEffect
        }
        isFillingName = true
        val suggested = runCatching { onSuggestTitle(DocumentTitleFormat.default) }.getOrNull()
        if (title.isBlank() && !suggested.isNullOrBlank()) {
            title = suggested
            suggestFormatIndex = DocumentTitleFormat.entries.indexOf(
                DocumentTitleFormat.default.next(),
            )
        }
        isFillingName = false
    }

    var confirmed by remember { mutableStateOf(false) }
    val confirm = {
        val name = title.trim()
        if (name.isNotEmpty() && !confirmed) {
            confirmed = true
            onConfirm(name, selectedGroupId)
        }
    }

    ScanlyFormDialogShell(onDismiss = onDismiss) {
        Text(
            text = "New document",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        NameTextField(
            value = title,
            onValueChange = { title = it },
            label = "Title",
            isLoading = isFillingName,
            onKeyboardDone = confirm,
        )
        if (onSuggestTitle != null) {
            DocumentTitleSuggestRow(
                onSuggestTitle = onSuggestTitle,
                onSuggested = { title = it },
                initialFormatIndex = suggestFormatIndex,
                enabled = !isFillingName,
            )
        }

        if (groups.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Save to",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DestinationRow(
                    label = "No folder",
                    icon = Icons.Filled.FolderOff,
                    selected = selectedGroupId == null,
                    onClick = { selectedGroupId = null },
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(groups, key = { it.id }) { group ->
                        DestinationRow(
                            label = group.title,
                            icon = Icons.Filled.Folder,
                            selected = selectedGroupId == group.id,
                            onClick = { selectedGroupId = group.id },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = confirm,
                enabled = title.isNotBlank() && !isFillingName,
            ) { Text("Create") }
        }
    }
}

@Composable
private fun DestinationRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val selectedAccentColor = MaterialTheme.colorScheme.primary
    val rowShape = MaterialTheme.shapes.large
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = rowShape,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                selectedAccentColor.copy(alpha = 0.64f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (selected) {
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(4.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(selectedAccentColor),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (selected) 18.dp else 14.dp,
                        top = 12.dp,
                        end = 14.dp,
                        bottom = 12.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) {
                        selectedAccentColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = selectedAccentColor,
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyLibraryCard(
    onCreateDocument: () -> Unit,
    onCreateGroup: () -> Unit,
) {
    PipEmptyState(
        pipLine = PipLines.LIBRARY_EMPTY,
        pipMood = PipMood.HAPPY,
        title = "Your library is ready",
        description = "Create a folder to stay organised, or add a document and start capturing.",
        actionLabel = "New folder",
        onAction = onCreateGroup,
        actionIcon = Icons.Outlined.CreateNewFolder,
        secondaryActionLabel = "New document",
        onSecondaryAction = onCreateDocument,
    )
}
