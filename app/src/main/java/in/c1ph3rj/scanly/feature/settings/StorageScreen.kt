package `in`.c1ph3rj.scanly.feature.settings

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.AppStorageUsage
import `in`.c1ph3rj.scanly.domain.model.ExportDestination
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun SettingsStorageRoute(
    onNavigateUp: () -> Unit,
    parentEntry: NavBackStackEntry,
    onOpenRecentlyDeleted: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(parentEntry),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                viewModel.setExportDestination(
                    uriString = uri.toString(),
                    displayName = queryDocumentName(context.contentResolver, uri) ?: "Selected folder",
                )
            }.onFailure {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        it.message ?: "Could not use the selected folder.",
                    )
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.loadStorageUsage()
    }

    SettingsStorageScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onChangeFolder = { folderLauncher.launch(null) },
        onResetFolder = viewModel::resetExportDestination,
        onClearExportCache = viewModel::clearExportCache,
        onDeleteEmptyDocuments = viewModel::deleteEmptyDocuments,
        onDeleteEmptyFolders = viewModel::deleteEmptyFolders,
        onClearAllData = viewModel::clearAllData,
        onOpenRecentlyDeleted = onOpenRecentlyDeleted,
    )
}

@Composable
private fun SettingsStorageScreen(
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onChangeFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onClearExportCache: () -> Unit,
    onDeleteEmptyDocuments: () -> Unit,
    onDeleteEmptyFolders: () -> Unit,
    onClearAllData: () -> Unit,
    onOpenRecentlyDeleted: () -> Unit,
) {
    val window = rememberWindowSizeInfo()
    var showEmptyDocumentsWarning by remember { mutableStateOf(false) }
    var showEmptyFoldersWarning by remember { mutableStateOf(false) }
    var showClearWarning by remember { mutableStateOf(false) }
    val cleanupBusy = uiState.isCleaningEmptyDocuments ||
        uiState.isCleaningEmptyFolders ||
        uiState.isClearingData
    val folderBusy = cleanupBusy || uiState.isClearingExportCache

    ScanlyDetailScaffold(
        title = "Storage",
        onNavigateUp = onNavigateUp,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = window.horizontalPadding,
                end = window.horizontalPadding,
                top = 8.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item("usage") {
                UsageHeroCard(
                    uiState = uiState,
                    onClearExportCache = onClearExportCache,
                )
            }

            item("location") {
                SettingsGroup(title = "Save location") {
                    SaveLocationRow(
                        label = uiState.exportDestination.exportLabel,
                        enabled = !folderBusy,
                        onClick = onChangeFolder,
                    )
                    Text(
                        text = "PDFs and images you export are saved here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                    if (uiState.exportDestination !is ExportDestination.DefaultDownloadsScanly) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        SettingsActionRow(
                            icon = Icons.Outlined.Restore,
                            title = "Use default folder",
                            subtitle = "Downloads/Scanly",
                            enabled = !folderBusy,
                            isBusy = false,
                            onClick = onResetFolder,
                        )
                    }
                }
            }

            item("tidy") {
                SettingsGroup(title = "Tidy up") {
                    SettingsActionRow(
                        icon = Icons.Outlined.RestoreFromTrash,
                        title = "Recently deleted",
                        subtitle = "Restore deleted folders, documents and pages",
                        enabled = true,
                        isBusy = false,
                        onClick = onOpenRecentlyDeleted,
                    )
                    SettingsActionRow(
                        icon = Icons.Filled.Description,
                        title = "Remove empty documents",
                        subtitle = "Documents with no pages",
                        enabled = !cleanupBusy,
                        isBusy = uiState.isCleaningEmptyDocuments,
                        onClick = { showEmptyDocumentsWarning = true },
                    )
                    SettingsActionRow(
                        icon = Icons.Filled.FolderOff,
                        title = "Remove empty folders",
                        subtitle = "Folders with no documents",
                        enabled = !cleanupBusy,
                        isBusy = uiState.isCleaningEmptyFolders,
                        onClick = { showEmptyFoldersWarning = true },
                    )
                }
            }

            item("danger") {
                SettingsGroup(title = "Danger zone") {
                    Row(
                        modifier = Modifier.settingsRowSurface(
                            onClick = if (cleanupBusy) null else ({ showClearWarning = true }),
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Clear all data",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = "Deletes every document, folder and page on this device",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (uiState.isClearingData) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEmptyDocumentsWarning) {
        ScanlyConfirmDialog(
            title = "Remove empty documents?",
            text = "Deletes every document that has no pages, including abandoned captures, " +
                "and frees their local storage. Documents that still have pages are kept.",
            confirmLabel = "Remove",
            confirmDestructive = true,
            onDismiss = { showEmptyDocumentsWarning = false },
            onConfirm = {
                showEmptyDocumentsWarning = false
                onDeleteEmptyDocuments()
            },
            confirmEnabled = !cleanupBusy,
            dismissEnabled = !cleanupBusy,
        )
    }

    if (showEmptyFoldersWarning) {
        ScanlyConfirmDialog(
            title = "Remove empty folders?",
            text = "Deletes every folder (group) that contains no documents. " +
                "Folders that still have documents are kept.",
            confirmLabel = "Remove",
            confirmDestructive = true,
            onDismiss = { showEmptyFoldersWarning = false },
            onConfirm = {
                showEmptyFoldersWarning = false
                onDeleteEmptyFolders()
            },
            confirmEnabled = !cleanupBusy,
            dismissEnabled = !cleanupBusy,
        )
    }

    if (showClearWarning) {
        ScanlyConfirmDialog(
            title = "Clear all data?",
            text = "This permanently deletes all documents, folders, pages and temporary exports on this " +
                "device. Files you already saved to your export folder are kept.",
            confirmLabel = "Delete",
            confirmDestructive = true,
            onDismiss = { showClearWarning = false },
            onConfirm = {
                showClearWarning = false
                onClearAllData()
            },
            confirmEnabled = !uiState.isClearingData,
            dismissEnabled = !uiState.isClearingData,
        )
    }
}

@Composable
private fun SaveLocationRow(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Exports save to",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onClick, enabled = enabled) {
            Text("Change")
        }
    }
}

@Composable
private fun UsageHeroCard(
    uiState: SettingsUiState,
    onClearExportCache: () -> Unit,
) {
    val usage = uiState.storageUsage
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        if (usage == null) {
            LoadingUsageContent()
        } else {
            UsageContent(
                usage = usage,
                isClearingExportCache = uiState.isClearingExportCache,
                onClearExportCache = onClearExportCache,
            )
        }
    }
}

@Composable
private fun LoadingUsageContent() {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Calculating…",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun UsageContent(
    usage: AppStorageUsage,
    isClearingExportCache: Boolean,
    onClearExportCache: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Scanly is using",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = StorageFormatter.formatBytes(usage.totalBytes),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Distinct by lightness, not hue: the brand teals are too close to tell apart in dark
        // theme. Library is the solid accent, the index a tint of it, exports neutral (disposable).
        val libraryColor = MaterialTheme.colorScheme.primary
        val indexColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        val exportsColor = MaterialTheme.colorScheme.outline
        MultiSegmentStorageBar(
            segments = listOf(
                usage.documentsBytes to libraryColor,
                usage.databaseBytes to indexColor,
                usage.exportCacheBytes to exportsColor,
            ),
            totalBytes = usage.totalBytes,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StorageLegendRow(
                label = "Library files",
                bytes = usage.documentsBytes,
                color = libraryColor,
            )
            StorageLegendRow(
                label = "Database & search index",
                bytes = usage.databaseBytes,
                color = indexColor,
            )
            StorageLegendRow(
                label = "Temporary exports",
                bytes = usage.exportCacheBytes,
                color = exportsColor,
                trailing = if (usage.exportCacheBytes > 0L) {
                    {
                        if (isClearingExportCache) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            TextButton(onClick = onClearExportCache) {
                                Text("Clear")
                            }
                        }
                    }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Horizontal storage bar split into proportional segments. Each entry is (bytes, color); segments
 * with bytes are given at least a small minimum width so they stay visible.
 */
@Composable
fun MultiSegmentStorageBar(
    segments: List<Pair<Long, Color>>,
    totalBytes: Long,
    modifier: Modifier = Modifier,
) {
    val minRatio = 0.02f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (totalBytes > 0L) {
            segments.forEach { (bytes, color) ->
                if (bytes > 0L) {
                    val ratio = maxOf(bytes.toFloat() / totalBytes, minRatio)
                    Box(modifier = Modifier.weight(ratio).fillMaxHeight().background(color))
                }
            }
        }
    }
}

@Composable
private fun StorageLegendRow(
    label: String,
    bytes: Long,
    color: Color,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = StorageFormatter.formatBytes(bytes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.width(4.dp))
                trailing()
            }
        }
    }
}

private fun queryDocumentName(resolver: ContentResolver, uri: Uri): String? {
    val documentUri = try {
        DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
    } catch (e: Exception) {
        uri
    }
    return runCatching {
        resolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}
