package `in`.c1ph3rj.scanly.feature.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeRoute(
    onOpenDocument: (String) -> Unit,
    onOpenReadiness: () -> Unit,
    onOpenScanSession: (String) -> Unit,
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
        onCreateDocument = viewModel::createDocument,
        onRenameDocument = viewModel::renameDocument,
        onDeleteDocument = viewModel::deleteDocument,
        onOpenDocument = onOpenDocument,
        onOpenReadiness = onOpenReadiness,
        onOpenScanSession = onOpenScanSession,
        onNavigateUp = onNavigateUp,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onCreateDocument: (String) -> Unit,
    onRenameDocument: (String, String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenReadiness: () -> Unit,
    onOpenScanSession: (String) -> Unit,
    onNavigateUp: (() -> Unit)? = null,
) {
    var createDialogVisible by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var deleteTarget by remember { mutableStateOf<ScanDocument?>(null) }
    val latestDocument = uiState.documents.firstOrNull()
    val totalPages = remember(uiState.documents) { uiState.documents.sumOf { it.pageCount } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (latestDocument == null) {
                        createDialogVisible = true
                    } else {
                        onOpenScanSession(latestDocument.id)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                ImageActionIcon()
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                LibraryTopBar(
                    onNavigateUp = onNavigateUp,
                    onOpenReadiness = onOpenReadiness,
                    onCreateDocument = { createDialogVisible = true },
                )
            }

            item {
                LibraryHero(
                    documentCount = uiState.documents.size,
                    totalPages = totalPages,
                    hasDocuments = latestDocument != null,
                    onPrimaryAction = {
                        if (latestDocument == null) {
                            createDialogVisible = true
                        } else {
                            onOpenScanSession(latestDocument.id)
                        }
                    },
                    onSecondaryAction = { createDialogVisible = true },
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
                        EmptyLibraryCard(
                            onCreateDocument = { createDialogVisible = true },
                        )
                    }
                }

                else -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Documents",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            MetricChip(
                                label = "${uiState.documents.size}",
                                icon = Icons.Filled.Description,
                            )
                        }
                    }
                    items(
                        items = uiState.documents,
                        key = { document -> document.id },
                    ) { document ->
                        DocumentCard(
                            document = document,
                            onOpen = { onOpenDocument(document.id) },
                            onRename = { renameTarget = document },
                            onDelete = { deleteTarget = document },
                        )
                    }
                }
            }
        }
    }

    if (createDialogVisible) {
        DocumentTitleDialog(
            title = "New document",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { createDialogVisible = false },
            onConfirm = { value ->
                createDialogVisible = false
                onCreateDocument(value)
            },
        )
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
}

@Composable
private fun LibraryTopBar(
    onNavigateUp: (() -> Unit)?,
    onOpenReadiness: () -> Unit,
    onCreateDocument: () -> Unit,
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
                    text = "Library",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChromeIconButton(
                icon = Icons.Filled.Tune,
                contentDescription = "Readiness",
                onClick = onOpenReadiness,
            )
            ChromeIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "New document",
                onClick = onCreateDocument,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun LibraryHero(
    documentCount: Int,
    totalPages: Int,
    hasDocuments: Boolean,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (hasDocuments) "Ready for the next scan" else "Start a new document",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (hasDocuments) "Capture, review, and assemble pages fast." else "Create one document and the camera flow is ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricChip(
                    label = "$documentCount docs",
                    icon = Icons.Filled.Description,
                )
                MetricChip(
                    label = "$totalPages pages",
                    icon = Icons.Filled.FolderOpen,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onPrimaryAction,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ImageActionIcon()
                        Text(text = if (hasDocuments) "Scan" else "Create")
                    }
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onSecondaryAction,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                        )
                        Text(text = "New doc")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
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
    onCreateDocument: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Text(
                text = "No documents yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Create one and start scanning.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onCreateDocument) {
                Text(text = "New document")
            }
        }
    }
}

@Composable
private fun DocumentCard(
    document: ScanDocument,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentThumbnail(
                thumbnailPath = document.coverThumbnailPath,
                title = document.title,
                modifier = Modifier.weight(0.34f),
                minHeight = 116.dp,
            )
            Column(
                modifier = Modifier.weight(0.66f),
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
                        label = "${document.pageCount}",
                        icon = Icons.Filled.Description,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    MetricChip(
                        label = document.updatedAtMillis.toShortDate(),
                        icon = Icons.Filled.FolderOpen,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
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
            .heightIn(min = minHeight)
            .aspectRatio(4f / 3f),
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
                    style = MaterialTheme.typography.displaySmall,
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
private fun ImageActionIcon() {
    androidx.compose.material3.Icon(
        imageVector = Icons.Filled.CameraAlt,
        contentDescription = null,
    )
}

private fun Long.toShortDate(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))
