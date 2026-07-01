package `in`.c1ph3rj.scanly.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
        onSearchQueryChange = viewModel::setSearchQuery,
        onClearSearch = viewModel::clearSearch,
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
    onDeleteGroup: (String) -> Unit,
    onMoveDocumentToGroup: (String, String?) -> Unit,
    onMoveDocumentToNewGroup: (String, String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenScanSession: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
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
    var showFabMenu by rememberSaveable { mutableStateOf(false) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    val windowSizeInfo = rememberWindowSizeInfo()
    val visibleGroups = remember(uiState) { uiState.visibleGroups }
    val visibleDocuments = remember(uiState) { uiState.visibleDocuments }
    val groupRows = remember(visibleGroups, windowSizeInfo.groupColumns) {
        visibleGroups.chunked(windowSizeInfo.groupColumns)
    }
    val documentRows = remember(visibleDocuments, windowSizeInfo.groupColumns) {
        visibleDocuments.chunked(windowSizeInfo.groupColumns)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                LazyColumn(
                    modifier = if (windowSizeInfo.isTablet) {
                        Modifier.widthIn(max = windowSizeInfo.contentMaxWidth).fillMaxHeight()
                    } else {
                        Modifier.fillMaxSize()
                    },
                    contentPadding = PaddingValues(
                        start = windowSizeInfo.horizontalPadding,
                        end = windowSizeInfo.horizontalPadding,
                        top = 0.dp,
                        bottom = 120.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    item(key = "header") {
                        LibraryHeader(
                            groupCount = uiState.groups.size,
                            documentCount = uiState.allDocuments.size,
                            modifier = Modifier.padding(bottom = 18.dp),
                        )
                    }

                    item(key = "search_and_sort") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LibrarySearchBar(
                                query = uiState.searchQuery,
                                onQueryChange = onSearchQueryChange,
                                onClear = onClearSearch,
                                selectedTab = uiState.selectedTab,
                                modifier = Modifier.weight(1f),
                            )
                            LibrarySortButton(
                                selectedOption = uiState.sortOption,
                                onClick = { showSortSheet = true },
                            )
                        }
                    }

                    item(key = "tabs") {
                        LibraryTabs(
                            selectedTab = uiState.selectedTab,
                            onTabSelected = onTabSelected,
                            modifier = Modifier.padding(bottom = 18.dp),
                        )
                    }

                    if (visibleGroups.isEmpty() && visibleDocuments.isEmpty()) {
                        item(key = "empty_${uiState.selectedTab}") {
                            when {
                                uiState.isSearchActive -> NoResultsCard(
                                    query = uiState.searchQuery,
                                    selectedTab = uiState.selectedTab,
                                )
                                !uiState.hasAnyItems || uiState.selectedTab == LibraryTab.All -> EmptyLibraryCard(
                                    onCreateDocument = { createDocDialogVisible = true },
                                    onCreateGroup = { createGroupDialogVisible = true },
                                )
                                else -> EmptyLibraryTabCard(
                                    selectedTab = uiState.selectedTab,
                                    onCreateDocument = { createDocDialogVisible = true },
                                    onCreateGroup = { createGroupDialogVisible = true },
                                )
                            }
                        }
                    } else {
                        item(key = "result_summary") {
                            LibraryResultSummary(
                                itemCount = visibleGroups.size + visibleDocuments.size,
                                sortOption = uiState.sortOption,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }

                        if (visibleGroups.isNotEmpty()) {
                            if (uiState.selectedTab == LibraryTab.All) {
                                item(key = "groups_label") {
                                    LibrarySectionHeader(
                                        title = "Folders",
                                        count = visibleGroups.size,
                                        modifier = Modifier.padding(bottom = 12.dp),
                                    )
                                }
                            }
                            items(
                                items = groupRows,
                                key = { rowItems -> "group_row_${rowItems.first().id}" },
                                contentType = { "group_row" },
                            ) { rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .animateItem(),
                                ) {
                                    rowItems.forEach { group ->
                                        GroupCard(
                                            group = group,
                                            onOpen = { onOpenGroup(group.id) },
                                            onRename = { renameGroupTarget = group },
                                            onDelete = { deleteGroupTarget = group },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(windowSizeInfo.groupColumns - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        if (visibleGroups.isNotEmpty() && visibleDocuments.isNotEmpty()) {
                            item(key = "section_spacer") { Spacer(modifier = Modifier.height(12.dp)) }
                        }

                        if (visibleDocuments.isNotEmpty()) {
                            if (uiState.selectedTab == LibraryTab.All) {
                                item(key = "documents_label") {
                                    LibrarySectionHeader(
                                        title = "Documents",
                                        count = visibleDocuments.size,
                                        modifier = Modifier.padding(bottom = 12.dp),
                                    )
                                }
                            }
                            if (windowSizeInfo.isTablet) {
                                items(
                                    items = documentRows,
                                    key = { rowItems -> "document_row_${rowItems.first().id}" },
                                    contentType = { "document_row" },
                                ) { rowItems ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp)
                                            .animateItem(),
                                    ) {
                                        rowItems.forEach { document ->
                                            DocumentCard(
                                                document = document,
                                                onOpen = { onOpenDocument(document.id) },
                                                onRename = { renameDocTarget = document },
                                                onDelete = { deleteDocTarget = document },
                                                onMove = { moveDocTarget = document },
                                                style = LibraryCardStyle.Grid,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        repeat(windowSizeInfo.groupColumns - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = visibleDocuments,
                                    key = { "document_${it.id}" },
                                    contentType = { "document" },
                                ) { document ->
                                    DocumentCard(
                                        document = document,
                                        onOpen = { onOpenDocument(document.id) },
                                        onRename = { renameDocTarget = document },
                                        onDelete = { deleteDocTarget = document },
                                        onMove = { moveDocTarget = document },
                                        modifier = Modifier
                                            .padding(bottom = 12.dp)
                                            .animateItem(),
                                    )
                                }
                            }
                        }
                    }
            }
            }

            FabMenuScrim(
                visible = showFabMenu,
                onDismiss = { showFabMenu = false },
            )

            if (!uiState.isLoading) {
                ScanlyExpandableFabMenu(
                    expanded = showFabMenu,
                    onExpandedChange = { showFabMenu = it },
                    onNewFolder = { createGroupDialogVisible = true },
                    onNewDocument = { createDocDialogVisible = true },
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

    if (createDocDialogVisible) {
        NewDocumentDialog(
            groups = uiState.groups,
            onDismiss = { createDocDialogVisible = false },
            onConfirm = { title, groupId ->
                createDocDialogVisible = false
                onCreateDocument(title, groupId)
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
                createGroupDialogVisible = false
                onCreateGroup(title)
            },
            onSuggestTitle = onSuggestGroupTitle,
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
            text = "\"${doc.title}\" and its pages will be removed permanently.",
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
            onDismiss = { renameGroupTarget = null },
            onConfirm = { value ->
                renameGroupTarget = null
                onRenameGroup(group.id, value)
            },
        )
    }

    deleteGroupTarget?.let { group ->
        ScanlyConfirmDialog(
            title = "Delete folder?",
            text = "\"${group.title}\" will be deleted. Documents inside will become ungrouped.",
            confirmLabel = "Delete",
            onDismiss = { deleteGroupTarget = null },
            onConfirm = {
                deleteGroupTarget = null
                onDeleteGroup(group.id)
            },
            confirmDestructive = true,
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

@Composable
fun LibraryHeader(groupCount: Int, documentCount: Int, modifier: Modifier = Modifier) {
    ScanlyTabScreenHeader(
        title = "Library",
        subtitle = buildString {
            if (groupCount > 0) {
                append("$groupCount ${if (groupCount == 1) "folder" else "folders"}")
                if (documentCount > 0) append("  ·  ")
            }
            if (documentCount > 0) {
                append("$documentCount ${if (documentCount == 1) "document" else "documents"}")
            }
            if (groupCount == 0 && documentCount == 0) append("Empty")
        },
        modifier = modifier,
    )
}

@Composable
fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    selectedTab: LibraryTab,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val searchPlaceholder = when (selectedTab) {
        LibraryTab.All -> "Search folders and documents…"
        LibraryTab.Folders -> "Search folders…"
        LibraryTab.Documents -> "Search documents…"
    }
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = searchPlaceholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = searchPlaceholder },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    singleLine = true,
                    interactionSource = interactionSource,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryTabs(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = MaterialTheme.shapes.extraLarge
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(pillShape)
                    .selectable(
                        selected = selected,
                        onClick = { onTabSelected(tab) },
                        role = Role.Tab,
                    ),
                shape = pillShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = tab.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySortButton(
    selectedOption: LibrarySortOption,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort: ${selectedOption.title}",
                tint = MaterialTheme.colorScheme.onSurface,
            )
            if (selectedOption != LibrarySortOption.RecentlyUpdated) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(9.dp)
                        .size(7.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun LibraryResultSummary(
    itemCount: Int,
    sortOption: LibrarySortOption,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "$itemCount ${if (itemCount == 1) "item" else "items"}  ·  ${sortOption.title}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun LibrarySectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "$title  ·  $count",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@Composable
fun NoResultsCard(
    query: String,
    selectedTab: LibraryTab,
) {
    val resultType = when (selectedTab) {
        LibraryTab.All -> "folders or documents"
        LibraryTab.Folders -> "folders"
        LibraryTab.Documents -> "documents"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "No $resultType found",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Nothing matches \"${query.trim()}\". Try a different name.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (isFolders) Icons.Filled.FolderOff else Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (isFolders) "No folders yet" else "No documents yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isFolders) {
                    "Create a folder to keep related documents together."
                } else {
                    "Create a document to start scanning or importing pages."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(onClick = if (isFolders) onCreateGroup else onCreateDocument) {
                Icon(
                    imageVector = if (isFolders) Icons.Filled.CreateNewFolder else Icons.Filled.Add,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isFolders) "New folder" else "New document")
            }
        }
    }
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
    onDismiss: () -> Unit,
    onConfirm: (title: String, groupId: String?) -> Unit,
    onSuggestTitle: (suspend (DocumentTitleFormat) -> String)? = null,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }

    ScanlyFormDialogShell(onDismiss = onDismiss) {
        Text(
            text = "New document",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onSuggestTitle != null) {
            DocumentTitleSuggestRow(
                onSuggestTitle = onSuggestTitle,
                onSuggested = { title = it },
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
                onClick = { if (title.isNotBlank()) onConfirm(title, selectedGroupId) },
                enabled = title.isNotBlank(),
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Nothing here yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Create a folder to organise documents, or add a document directly.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onCreateGroup, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New Folder")
                }
                TextButton(onClick = onCreateDocument, modifier = Modifier.height(48.dp)) {
                    Text("New Document")
                }
            }
        }
    }
}
