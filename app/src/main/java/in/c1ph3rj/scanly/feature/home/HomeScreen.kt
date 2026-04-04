package `in`.c1ph3rj.scanly.feature.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Scanly") },
                navigationIcon = {
                    if (onNavigateUp != null) {
                        TextButton(onClick = onNavigateUp) {
                            Text(text = "Back")
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onOpenReadiness) {
                        Text(text = "Readiness")
                    }
                    TextButton(onClick = { createDialogVisible = true }) {
                        Text(text = "New")
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Your offline document library",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = if (uiState.documents.isEmpty()) {
                                "Create your first document now. Metadata and generated cover art stay in app-private storage until you export."
                            } else {
                                "${uiState.documents.size} documents ready. Open one to continue, or create a new document for the next scan session."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { createDialogVisible = true }) {
                                Text(text = "Create document")
                            }
                            TextButton(
                                onClick = {
                                    val latestDocument = uiState.documents.firstOrNull()
                                    if (latestDocument == null) {
                                        createDialogVisible = true
                                    } else {
                                        onOpenScanSession(latestDocument.id)
                                    }
                                },
                            ) {
                                Text(text = if (uiState.documents.isEmpty()) "Create first" else "Scan latest")
                            }
                        }
                    }
                }
            }
            if (uiState.isLoading) {
                item {
                    InfoCard(
                        title = "Loading documents",
                        body = "Preparing your local library.",
                    )
                }
            } else if (uiState.documents.isEmpty()) {
                item {
                    InfoCard(
                        title = "No documents yet",
                        body = "Create a document now so Sprint 3 can attach captured pages to a persistent home.",
                    )
                }
            } else {
                item {
                    Text(
                        text = "Documents",
                        style = MaterialTheme.typography.titleLarge,
                    )
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

    if (createDialogVisible) {
        DocumentTitleDialog(
            title = "Create document",
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
            confirmLabel = "Rename",
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
                Text(text = "Delete \"${document.title}\" and remove its local files?")
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
private fun DocumentCard(
    document: ScanDocument,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DocumentThumbnail(
                thumbnailPath = document.coverThumbnailPath,
                title = document.title,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"} · Updated ${document.updatedAtMillis.toReadableDateTime()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) {
                    Text(text = "Open")
                }
                TextButton(onClick = onRename) {
                    Text(text = "Rename")
                }
                TextButton(onClick = onDelete) {
                    Text(text = "Delete")
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
                    style = MaterialTheme.typography.displayMedium,
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
private fun InfoCard(
    title: String,
    body: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Long.toReadableDateTime(): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT,
).format(Date(this))
