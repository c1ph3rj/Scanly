package `in`.c1ph3rj.scanly.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ImageImportSupport
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.feature.components.*
import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeRoute(
    onOpenDocument: (String) -> Unit,
    onOpenScanSession: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onNavigateToLibrary: () -> Unit,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var createForScan by rememberSaveable { mutableStateOf(false) }

    val importImagesLauncher = rememberLauncherForActivityResult(
        contract = ImageImportSupport.pickMultipleVisualMediaContract(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImagesAsDocument(uris)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HomeEvent.OpenDocument -> {
                    if (createForScan) onOpenScanSession(event.documentId)
                    else onOpenDocument(event.documentId)
                    createForScan = false
                }
                is HomeEvent.OpenGroup -> onOpenGroup(event.groupId)
                is HomeEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    HomeScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onCreateDocument = { title ->
            createForScan = true
            viewModel.createDocument(title)
        },
        onImportImages = {
            importImagesLauncher.launch(ImageImportSupport.createPickRequest())
        },
        onOpenDocument = onOpenDocument,
        onOpenScanSession = onOpenScanSession,
        onOpenGroup = onOpenGroup,
        onNavigateToLibrary = onNavigateToLibrary,
        onCreateGroup = viewModel::createGroup,
        onSuggestTitle = viewModel::suggestDocumentTitle,
        onSuggestGroupTitle = viewModel::suggestGroupTitle,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onCreateDocument: (String) -> Unit,
    onImportImages: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenScanSession: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onNavigateToLibrary: () -> Unit,
    onCreateGroup: (String) -> Unit,
    onSuggestTitle: suspend (DocumentTitleFormat) -> String,
    onSuggestGroupTitle: suspend (GroupTitleFormat) -> String,
) {
    var createDialogVisible by rememberSaveable { mutableStateOf(false) }
    var createFolderDialogVisible by rememberSaveable { mutableStateOf(false) }
    val windowSizeInfo = rememberWindowSizeInfo()

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
        LazyColumn(
            modifier = if (windowSizeInfo.isTablet) {
                Modifier.widthIn(max = windowSizeInfo.contentMaxWidth).fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            },
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item(key = "home_header", contentType = "header") {
                HomeHeader(
                    groupCount = uiState.recentGroups.size,
                    documentCount = uiState.recentDocuments.size,
                    modifier = Modifier
                        .padding(horizontal = windowSizeInfo.horizontalPadding)
                        .padding(bottom = 24.dp),
                )
            }

            if (uiState.isLoading) {
                item(key = "home_loading", contentType = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
            item(key = "quick_actions", contentType = "quick_actions") {
                QuickActionsRow(
                    onScan = { createDialogVisible = true },
                    onImport = onImportImages,
                    onNewFolder = { createFolderDialogVisible = true },
                    importEnabled = !uiState.isImporting,
                    isTablet = windowSizeInfo.isTablet,
                    horizontalPadding = windowSizeInfo.horizontalPadding,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }

            if (uiState.recentGroups.isNotEmpty()) {
                item(key = "groups_header", contentType = "section_header") {
                    SectionHeader(
                        title = "Recent Folders",
                        modifier = Modifier
                            .padding(horizontal = windowSizeInfo.horizontalPadding)
                            .padding(bottom = 16.dp),
                    )
                }
                item(key = "groups_row", contentType = "groups_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = windowSizeInfo.horizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 32.dp)
                    ) {
                        items(
                            items = uiState.recentGroups,
                            key = { it.id },
                            contentType = { "recent_group" },
                        ) { group ->
                            RecentGroupChip(
                                group = group,
                                onClick = { onOpenGroup(group.id) },
                                chipWidth = if (windowSizeInfo.isTablet) 200.dp else 160.dp,
                            )
                        }
                    }
                }
            }

            if (uiState.recentDocuments.isNotEmpty()) {
                item(key = "files_header", contentType = "section_header") {
                    SectionHeader(
                        title = "Recent Files",
                        actionLabel = "See all in Library",
                        onAction = onNavigateToLibrary,
                        modifier = Modifier
                            .padding(horizontal = windowSizeInfo.horizontalPadding)
                            .padding(bottom = 16.dp),
                    )
                }
                items(
                    items = uiState.recentDocuments,
                    key = { it.id },
                    contentType = { "recent_document" },
                ) { doc ->
                    CompactDocumentCard(
                        document = doc,
                        onOpen = { onOpenDocument(doc.id) },
                        modifier = Modifier
                            .padding(horizontal = windowSizeInfo.horizontalPadding)
                            .padding(bottom = 12.dp)
                            .animateItem(),
                    )
                }
            } else if (uiState.recentGroups.isEmpty()) {
                item(key = "empty_state", contentType = "empty") {
                    EmptyHomeCard(
                        onCreateDocument = { createDialogVisible = true },
                        modifier = Modifier
                            .padding(horizontal = windowSizeInfo.horizontalPadding)
                            .padding(top = 16.dp),
                    )
                }
            }
            }
        }
        } // end outer Box
    }

    if (createDialogVisible) {
        DocumentTitleDialog(
            title = "New scan",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { createDialogVisible = false },
            onConfirm = { value ->
                createDialogVisible = false
                onCreateDocument(value)
            },
            onSuggestTitle = onSuggestTitle,
        )
    }

    if (createFolderDialogVisible) {
        GroupNameDialog(
            title = "New folder",
            initialValue = "",
            onDismiss = { createFolderDialogVisible = false },
            onConfirm = { title ->
                createFolderDialogVisible = false
                onCreateGroup(title)
            },
            onSuggestTitle = onSuggestGroupTitle,
        )
    }
}

@Composable
fun HomeHeader(
    groupCount: Int,
    documentCount: Int,
    modifier: Modifier = Modifier,
) {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = greetingForHour(hour)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = greeting.salutation,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = greeting.headline,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

private data class HomeGreeting(
    val salutation: String,
    val headline: String,
)

private fun greetingForHour(hour: Int): HomeGreeting = when (hour) {
    in 5..11 -> HomeGreeting(
        salutation = "Good morning,",
        headline = "What are we scanning today?",
    )
    in 12..16 -> HomeGreeting(
        salutation = "Good afternoon,",
        headline = "Let's capture some documents.",
    )
    in 17..20 -> HomeGreeting(
        salutation = "Good evening,",
        headline = "Wrap up and digitize your day.",
    )
    else -> HomeGreeting(
        salutation = "Still scanning?",
        headline = "Save it now, find it tomorrow.",
    )
}

@Composable
fun QuickActionsRow(
    onScan: () -> Unit,
    onImport: () -> Unit,
    onNewFolder: () -> Unit,
    importEnabled: Boolean = true,
    isTablet: Boolean = false,
    horizontalPadding: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    val rowModifier = if (isTablet) {
        modifier
            .widthIn(max = 480.dp)
            .padding(horizontal = horizontalPadding)
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
    }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        QuickActionCard(
            title = "Scan",
            icon = Icons.Filled.CameraAlt,
            onClick = onScan,
            accentContainerColor = MaterialTheme.colorScheme.primaryContainer,
            accentContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        QuickActionCard(
            title = "Import",
            icon = Icons.Filled.PhotoLibrary,
            onClick = onImport,
            enabled = importEnabled,
            accentContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            accentContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
        QuickActionCard(
            title = "Folder",
            icon = Icons.Filled.CreateNewFolder,
            onClick = onNewFolder,
            accentContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            accentContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun QuickActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    accentContainerColor: androidx.compose.ui.graphics.Color,
    accentContentColor: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val resolvedContentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.aspectRatio(1f),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = MaterialTheme.shapes.extraLarge,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                color = if (enabled) accentContainerColor else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.large,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (enabled) accentContentColor else resolvedContentColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = resolvedContentColor
            )
        }
    }
}

@Composable
private fun RecentGroupChip(
    group: DocumentGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chipWidth: Dp = 160.dp,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(chipWidth),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            CachedThumbnail(
                thumbnailPath = group.coverThumbnailPath,
                title = group.title,
                displaySize = PreviewDisplaySize.CARD,
                contentRevision = group.coverUpdatedAtMillis,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f),
                placeholderIcon = {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp),
                    )
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${group.documentCount} docs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactDocumentCard(
    document: ScanDocument,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val updatedDate = remember(document.updatedAtMillis) {
        document.updatedAtMillis.toRelativeDate()
    }
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CachedThumbnail(
                thumbnailPath = document.coverThumbnailPath,
                title = document.title,
                displaySize = PreviewDisplaySize.CARD,
                contentRevision = document.updatedAtMillis,
                modifier = Modifier.size(64.dp),
                shape = MaterialTheme.shapes.large,
                placeholderIcon = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                        append(updatedDate)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EmptyHomeCard(
    onCreateDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Text(
                "Start scanning",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Your recently captured files and folders will appear here.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCreateDocument, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New scan")
            }
        }
    }
}
