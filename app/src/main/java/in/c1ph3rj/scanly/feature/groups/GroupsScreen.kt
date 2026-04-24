package `in`.c1ph3rj.scanly.feature.groups

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inventory2
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
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import kotlinx.coroutines.flow.collectLatest

@Composable
fun GroupsRoute(
    onOpenDocument: (String) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is GroupsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    GroupsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onCreateGroup = viewModel::createGroup,
        onRenameGroup = viewModel::renameGroup,
        onDeleteGroup = viewModel::deleteGroup,
        onAssignDocument = viewModel::assignDocument,
        onCreateGroupForDocument = viewModel::createGroupForDocument,
        onOpenDocument = onOpenDocument,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GroupsScreen(
    uiState: GroupsUiState,
    snackbarHostState: SnackbarHostState,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onAssignDocument: (String, String?) -> Unit,
    onCreateGroupForDocument: (String, String) -> Unit,
    onOpenDocument: (String) -> Unit,
) {
    var createDialogVisible by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DocumentGroup?>(null) }
    var deleteTarget by remember { mutableStateOf<DocumentGroup?>(null) }
    var manageTarget by remember { mutableStateOf<DocumentGroup?>(null) }
    var groupPickerTarget by remember { mutableStateOf<ScanDocument?>(null) }

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
                GroupsHeader(onCreateGroup = { createDialogVisible = true })
            }

            if (uiState.isLoading) {
                item { LoadingRow(label = "Loading groups") }
            } else {
                    item {
                        UngroupedCard(
                            documents = uiState.ungroupedDocuments,
                            onOpenDocument = onOpenDocument,
                            onChangeGroup = { document -> groupPickerTarget = document },
                        )
                    }

                if (uiState.groups.isEmpty()) {
                    item { EmptyGroupsCard() }
                } else {
                    items(
                        items = uiState.groups,
                        key = { group -> group.id },
                    ) { group ->
                        GroupCard(
                            group = group,
                            documents = uiState.documents.filter { document -> document.groupId == group.id },
                            onManageDocuments = { manageTarget = group },
                            onRename = { renameTarget = group },
                            onDelete = { deleteTarget = group },
                            onOpenDocument = onOpenDocument,
                            onChangeGroup = { document -> groupPickerTarget = document },
                        )
                    }
                }
            }
        }
    }

    if (createDialogVisible) {
        GroupNameDialog(
            title = "New group",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { createDialogVisible = false },
            onConfirm = { name ->
                createDialogVisible = false
                onCreateGroup(name)
            },
        )
    }

    renameTarget?.let { group ->
        GroupNameDialog(
            title = "Rename group",
            initialValue = group.name,
            confirmLabel = "Save",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                onRenameGroup(group.id, name)
            },
        )
    }

    deleteTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = "Delete group") },
            text = { Text(text = "\"${group.name}\" will be removed. Its documents will stay in Scanly.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        onDeleteGroup(group.id)
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

    manageTarget?.let { group ->
        DocumentAssignmentSheet(
            group = group,
            documents = uiState.documents,
            onDismiss = { manageTarget = null },
            onOpenDocument = onOpenDocument,
            onChangeGroup = { document ->
                manageTarget = null
                groupPickerTarget = document
            },
        )
    }

    groupPickerTarget?.let { document ->
        DocumentGroupPickerSheet(
            document = document,
            groups = uiState.groups,
            onDismiss = { groupPickerTarget = null },
            onSelectGroup = { groupId ->
                groupPickerTarget = null
                onAssignDocument(document.id, groupId)
            },
            onCreateGroup = { name ->
                groupPickerTarget = null
                onCreateGroupForDocument(document.id, name)
            },
        )
    }
}

@Composable
private fun GroupsHeader(
    onCreateGroup: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Groups",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Organize documents by purpose",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ChromeIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Create group",
            onClick = onCreateGroup,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun UngroupedCard(
    documents: List<ScanDocument>,
    onOpenDocument: (String) -> Unit,
    onChangeGroup: (ScanDocument) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GroupTitle(
                    icon = Icons.Filled.Inventory2,
                    title = "Ungrouped",
                    subtitle = if (documents.size == 1) "1 document" else "${documents.size} documents",
                )
                MetricChip(label = documents.sumOf { it.pageCount }.toString(), icon = Icons.Filled.Description)
            }
            documents.take(3).forEach { document ->
                DocumentMiniRow(
                    document = document,
                    actionLabel = "Group",
                    onAction = { onChangeGroup(document) },
                    onOpen = { onOpenDocument(document.id) },
                )
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: DocumentGroup,
    documents: List<ScanDocument>,
    onManageDocuments: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onChangeGroup: (ScanDocument) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GroupTitle(
                    icon = Icons.Filled.Folder,
                    title = group.name,
                    subtitle = "${group.documentCount} documents · ${group.pageCount} pages",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChromeIconButton(
                        icon = Icons.Filled.FolderOpen,
                        contentDescription = "Manage documents",
                        onClick = onManageDocuments,
                    )
                    ChromeIconButton(
                        icon = Icons.Filled.Edit,
                        contentDescription = "Rename group",
                        onClick = onRename,
                    )
                    ChromeIconButton(
                        icon = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete group",
                        onClick = onDelete,
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                }
            }
            documents.take(3).forEach { document ->
                DocumentMiniRow(
                    document = document,
                    actionLabel = "Group",
                    onAction = { onChangeGroup(document) },
                    onOpen = { onOpenDocument(document.id) },
                )
            }
        }
    }
}

@Composable
private fun GroupTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyGroupsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = "Create groups such as Invoices, Receipts, or Land records.",
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingRow(label: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DocumentAssignmentSheet(
    group: DocumentGroup,
    documents: List<ScanDocument>,
    onDismiss: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onChangeGroup: (ScanDocument) -> Unit,
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
                text = group.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            documents.forEach { document ->
                DocumentMiniRow(
                    document = document,
                    actionLabel = "Change",
                    onAction = { onChangeGroup(document) },
                    onOpen = { onOpenDocument(document.id) },
                )
            }
        }
    }
}

@Composable
private fun DocumentMiniRow(
    document: ScanDocument,
    actionLabel: String,
    onAction: () -> Unit,
    onOpen: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    text = "${document.pageCount} pages · ${document.groupName ?: "Ungrouped"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
private fun GroupNameDialog(
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
                label = { Text(text = "Name") },
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
