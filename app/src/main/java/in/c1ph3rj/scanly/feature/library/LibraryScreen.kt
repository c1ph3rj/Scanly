package `in`.c1ph3rj.scanly.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
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
    val uiState by viewModel.uiState.collectAsState()
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
    )
}

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
) {
    var createDocDialogVisible by rememberSaveable { mutableStateOf(false) }
    var createGroupDialogVisible by rememberSaveable { mutableStateOf(false) }
    var renameDocTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var deleteDocTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var renameGroupTarget by remember { mutableStateOf<DocumentGroup?>(null) }
    var deleteGroupTarget by remember { mutableStateOf<DocumentGroup?>(null) }
    var moveDocTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var showFabMenu by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                FullScreenLoader(modifier = Modifier.padding(innerPadding))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding()),
                    contentPadding = PaddingValues(
                        start = 20.dp, end = 20.dp, top = 0.dp, bottom = 120.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
            item(key = "header") {
                LibraryHeader(
                    groupCount = uiState.groups.size,
                    documentCount = uiState.ungroupedDocuments.size,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
            }

            item(key = "search") {
                LibrarySearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClear = onClearSearch,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }

            if (uiState.isSearchActive) {
                val filteredGroups = uiState.filteredGroups
                val filteredDocs = uiState.filteredDocuments

                if (filteredGroups.isEmpty() && filteredDocs.isEmpty()) {
                    item(key = "no_results") {
                        NoResultsCard(query = uiState.searchQuery)
                    }
                } else {
                    if (filteredGroups.isNotEmpty()) {
                        item(key = "search_groups_label") {
                            SearchResultLabel("Folders", modifier = Modifier.padding(bottom = 12.dp))
                        }
                        items(filteredGroups, key = { "sg_${it.id}" }) { group ->
                            GroupCard(
                                group = group,
                                onOpen = { onOpenGroup(group.id) },
                                onRename = { renameGroupTarget = group },
                                onDelete = { deleteGroupTarget = group },
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    }
                    if (filteredDocs.isNotEmpty()) {
                        item(key = "search_docs_label") {
                            SearchResultLabel("Documents", modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
                        }
                        items(filteredDocs, key = { "sd_${it.id}" }) { doc ->
                            DocumentCard(
                                document = doc,
                                onOpen = { onOpenDocument(doc.id) },
                                onRename = { renameDocTarget = doc },
                                onDelete = { deleteDocTarget = doc },
                                onMove = { moveDocTarget = doc },
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    }
                }
            } else {
                if (uiState.groups.isNotEmpty()) {
                    item(key = "groups_label") {
                        SectionLabel("Folders  ·  ${uiState.groups.size}", modifier = Modifier.padding(bottom = 12.dp))
                    }
                    item(key = "groups_grid") {
                        val rows = uiState.groups.chunked(2)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            rows.forEach { rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
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
                                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    item(key = "groups_spacer") { Spacer(modifier = Modifier.height(16.dp)) }
                }

                if (uiState.ungroupedDocuments.isNotEmpty()) {
                    item(key = "docs_label") {
                        SectionLabel(
                            text = "Documents  ·  ${uiState.ungroupedDocuments.size}",
                            modifier = Modifier.padding(bottom = 12.dp, top = 8.dp),
                        )
                    }
                    items(uiState.ungroupedDocuments, key = { it.id }) { doc ->
                        DocumentCard(
                            document = doc,
                            onOpen = { onOpenDocument(doc.id) },
                            onRename = { renameDocTarget = doc },
                            onDelete = { deleteDocTarget = doc },
                            onMove = { moveDocTarget = doc },
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }

                if (uiState.groups.isEmpty() && uiState.ungroupedDocuments.isEmpty() && !uiState.isLoading) {
                    item(key = "empty") {
                        EmptyLibraryCard(
                            onCreateDocument = { createDocDialogVisible = true },
                            onCreateGroup = { createGroupDialogVisible = true },
                        )
                    }
                }
            }
            }
            }

            FabMenuScrim(
                visible = showFabMenu,
                onDismiss = { showFabMenu = false },
            )

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

    if (createDocDialogVisible) {
        NewDocumentDialog(
            groups = uiState.groups,
            onDismiss = { createDocDialogVisible = false },
            onConfirm = { title, groupId ->
                createDocDialogVisible = false
                onCreateDocument(title, groupId)
            },
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
        AlertDialog(
            onDismissRequest = { deleteDocTarget = null },
            title = { Text("Delete document?") },
            text = { Text("\"${doc.title}\" and its pages will be removed permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDocTarget = null
                        onDeleteDocument(doc.id)
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDocTarget = null }) { Text("Cancel") }
            },
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
        AlertDialog(
            onDismissRequest = { deleteGroupTarget = null },
            title = { Text("Delete folder?") },
            text = {
                Text("\"${group.title}\" will be deleted. Documents inside will become ungrouped.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteGroupTarget = null
                        onDeleteGroup(group.id)
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteGroupTarget = null }) { Text("Cancel") }
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
        )
    }
}

@Composable
fun LibraryHeader(groupCount: Int, documentCount: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = buildString {
                if (groupCount > 0) {
                    append("$groupCount ${if (groupCount == 1) "folder" else "folders"}")
                    if (documentCount > 0) append("  ·  ")
                }
                if (documentCount > 0) {
                    append("$documentCount ungrouped")
                }
                if (groupCount == 0 && documentCount == 0) append("Empty")
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
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
                        text = "Search folders and documents…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
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
fun SearchResultLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
fun NoResultsCard(query: String) {
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
                text = "No results for \"$query\"",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Try a different name.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun NewDocumentDialog(
    groups: List<DocumentGroup>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, groupId: String?) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

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
                            modifier = Modifier.heightIn(max = 220.dp),
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onConfirm(title, selectedGroupId) },
                enabled = title.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DestinationRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
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
