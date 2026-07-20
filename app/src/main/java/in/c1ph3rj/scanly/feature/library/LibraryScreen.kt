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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
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
