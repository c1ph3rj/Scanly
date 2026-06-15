package `in`.c1ph3rj.scanly.feature.home

import android.graphics.BitmapFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import java.util.Calendar
import java.util.Date

// ─── Routes ────────────────────────────────────────────────────────────────────

@Composable
fun HomeRoute(
    onOpenDocument: (String) -> Unit,
    onOpenScanSession: (String) -> Unit,
    onNavigateToLibrary: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // Track whether the next OpenDocument event should jump straight to scan
    var createForScan by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    remember(context) { ProcessCameraProvider.getInstance(context.applicationContext) }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HomeEvent.OpenDocument -> {
                    if (createForScan) {
                        onOpenScanSession(event.documentId)
                    } else {
                        onOpenDocument(event.documentId)
                    }
                    createForScan = false
                }
                is HomeEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    HomeDashboard(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onCreateDocument = viewModel::createDocument,
        onOpenDocument = onOpenDocument,
        onOpenScanSession = onOpenScanSession,
        onNavigateToLibrary = onNavigateToLibrary,
        onSetCreateForScan = { createForScan = it },
    )
}

@Composable
fun LibraryRoute(
    onOpenDocument: (String) -> Unit,
    onOpenScanSession: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    remember(context) { ProcessCameraProvider.getInstance(context.applicationContext) }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HomeEvent.OpenDocument -> onOpenDocument(event.documentId)
                is HomeEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LibraryScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onCreateDocument = viewModel::createDocument,
        onRenameDocument = viewModel::renameDocument,
        onDeleteDocument = viewModel::deleteDocument,
        onOpenDocument = onOpenDocument,
        onOpenScanSession = onOpenScanSession,
    )
}

// ─── Dashboard screen ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeDashboard(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onCreateDocument: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenScanSession: (String) -> Unit,
    onNavigateToLibrary: () -> Unit,
    onSetCreateForScan: (Boolean) -> Unit,
) {
    var createDialogVisible by rememberSaveable { mutableStateOf(false) }
    val latestDocument = uiState.documents.firstOrNull()
    val totalPages = remember(uiState.documents) { uiState.documents.sumOf { it.pageCount } }
    val recentDocs = remember(uiState.documents) { uiState.documents.take(3) }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (latestDocument == null) {
                        onSetCreateForScan(true)
                        createDialogVisible = true
                    } else {
                        onOpenScanSession(latestDocument.id)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(imageVector = Icons.Filled.CameraAlt, contentDescription = null) },
                text = { Text(text = if (latestDocument != null) "Scan" else "New scan") },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                DashboardHeader(greeting = greeting)
            }

            item {
                StatCardsRow(
                    documentCount = uiState.documents.size,
                    totalPages = totalPages,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 4.dp),
                )
            }

            when {
                uiState.isLoading -> item {
                    LoadingCard(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(top = 20.dp),
                    )
                }

                uiState.documents.isEmpty() -> item {
                    EmptyDashboardCard(
                        onCreateDocument = {
                            onSetCreateForScan(true)
                            createDialogVisible = true
                        },
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(top = 20.dp),
                    )
                }

                else -> {
                    item {
                        SectionHeader(
                            title = "Recent",
                            actionLabel = if (uiState.documents.size > 3) "See all" else null,
                            onAction = onNavigateToLibrary,
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(top = 24.dp, bottom = 8.dp),
                        )
                    }

                    items(items = recentDocs, key = { it.id }) { document ->
                        CompactDocumentCard(
                            document = document,
                            onOpen = { onOpenDocument(document.id) },
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 10.dp),
                        )
                    }

                    if (uiState.documents.size > 3) {
                        item {
                            SeeAllButton(
                                count = uiState.documents.size,
                                onClick = onNavigateToLibrary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .padding(top = 4.dp),
                            )
                        }
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
            onDismiss = {
                createDialogVisible = false
                onSetCreateForScan(false)
            },
            onConfirm = { value ->
                createDialogVisible = false
                onCreateDocument(value)
            },
        )
    }
}

// ─── Library screen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onCreateDocument: (String) -> Unit,
    onRenameDocument: (String, String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenScanSession: (String) -> Unit,
) {
    var createDialogVisible by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ScanDocument?>(null) }
    var deleteTarget by remember { mutableStateOf<ScanDocument?>(null) }
    val latestDocument = uiState.documents.firstOrNull()

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
                Icon(imageVector = Icons.Filled.CameraAlt, contentDescription = "Scan")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 20.dp, top = 20.dp, end = 20.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LibraryHeader(
                    documentCount = uiState.documents.size,
                    onCreateDocument = { createDialogVisible = true },
                )
            }

            when {
                uiState.isLoading -> item { LoadingCard() }

                uiState.documents.isEmpty() -> item {
                    EmptyDashboardCard(onCreateDocument = { createDialogVisible = true })
                }

                else -> items(
                    items = uiState.documents,
                    key = { it.id },
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
            text = { Text(text = "\"${document.title}\" and its pages will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        onDeleteDocument(document.id)
                    },
                ) { Text(text = "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(text = "Cancel") }
            },
        )
    }
}

// ─── Dashboard components ──────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(greeting: String) {
    val today = remember { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Scanly",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = today,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StatCardsRow(
    documentCount: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            icon = Icons.Filled.Folder,
            value = "$documentCount",
            label = "Documents",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            icon = Icons.Filled.Description,
            value = "$totalPages",
            label = "Pages",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CompactDocumentCard(
    document: ScanDocument,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactThumbnail(
                thumbnailPath = document.coverThumbnailPath,
                title = document.title,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(document.pageCount)
                        append(if (document.pageCount == 1) " page" else " pages")
                        append("  ·  ")
                        append(document.updatedAtMillis.toShortDate())
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CompactThumbnail(
    thumbnailPath: String?,
    title: String,
    size: Dp = 52.dp,
) {
    val imageBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = thumbnailPath,
    ) {
        value = withContext(Dispatchers.IO) {
            thumbnailPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
        }
    }

    Surface(
        modifier = Modifier.size(size),
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
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = DocumentPresentationFormatter.initials(title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SeeAllButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "View all $count documents",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EmptyDashboardCard(
    onCreateDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "No documents yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Create a document and start scanning.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onCreateDocument) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "New document")
            }
        }
    }
}

// ─── Library components ────────────────────────────────────────────────────────

@Composable
private fun LibraryHeader(
    documentCount: Int,
    onCreateDocument: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "$documentCount ${if (documentCount == 1) "document" else "documents"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ChromeIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "New document",
            onClick = onCreateDocument,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

// ─── Shared components ─────────────────────────────────────────────────────────

@Composable
private fun LoadingCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(text = "Loading documents…", style = MaterialTheme.typography.titleMedium)
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
                    overflow = TextOverflow.Ellipsis,
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
            thumbnailPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
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
            TextButton(onClick = { onConfirm(value) }) { Text(text = confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel") }
        },
    )
}

private fun Long.toShortDate(): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))
