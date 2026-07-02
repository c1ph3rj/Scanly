package `in`.c1ph3rj.scanly.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.compose.ui.window.Dialog
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.ArchiveOperation
import `in`.c1ph3rj.scanly.domain.model.ArchiveWorkPhase
import `in`.c1ph3rj.scanly.domain.model.ExportDestination
import `in`.c1ph3rj.scanly.domain.model.RestoreMode
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun SettingsStorageRoute(
    onNavigateUp: () -> Unit,
    parentEntry: NavBackStackEntry,
    viewModel: SettingsViewModel = hiltViewModel(parentEntry),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showBackupWarning by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingNotificationAction?.invoke()
        pendingNotificationAction = null
    }
    fun runWithNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingNotificationAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            pendingRestoreUri = uri
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
        viewModel.loadBackupEstimate()
    }

    SettingsStorageScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onChangeFolder = { folderLauncher.launch(null) },
        onResetFolder = viewModel::resetExportDestination,
        onRefreshEstimate = viewModel::loadBackupEstimate,
        onBackup = { showBackupWarning = true },
        onRestore = {
            restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/*"))
        },
        onCancelWork = viewModel::cancelArchiveWork,
        onClearAllData = viewModel::clearAllData,
    )

    if (showBackupWarning) {
        ScanlyConfirmDialog(
            title = "Back up entire library?",
            text = "Scanly will compress every document, original capture, processed image, and thumbnail " +
                "into ${uiState.exportDestination.backupLabel}. This can take several minutes and use " +
                "extra battery. Library changes are paused while the snapshot is created. The backup is " +
                "not encrypted, so keep it private.",
            confirmLabel = "Start backup",
            onDismiss = { showBackupWarning = false },
            onConfirm = {
                showBackupWarning = false
                runWithNotificationPermission(viewModel::startBackup)
            },
            confirmEnabled = uiState.backupEstimate?.canBackup == true && !uiState.archiveWork.isRunning,
        )
    }

    pendingRestoreUri?.let { uri ->
        RestoreModeDialog(
            onDismiss = { pendingRestoreUri = null },
            onSelect = { mode ->
                pendingRestoreUri = null
                runWithNotificationPermission {
                    viewModel.startRestore(uri.toString(), mode)
                }
            },
        )
    }
}

@Composable
private fun SettingsStorageScreen(
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onChangeFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onRefreshEstimate: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onCancelWork: () -> Unit,
    onClearAllData: () -> Unit,
) {
    val window = rememberWindowSizeInfo()
    var showClearWarning by remember { mutableStateOf(false) }
    ScanlyDetailScaffold(
        title = "Storage & backup",
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("usage") {
                StorageSectionCard("App storage", Icons.Filled.Storage) {
                    val usage = uiState.storageUsage
                    if (uiState.isLoadingStorage || usage == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                            Text("Calculating storage…")
                        }
                    } else {
                        StorageMetric("Library files", usage.documentsBytes)
                        StorageMetric("Temporary exports", usage.exportCacheBytes)
                        StorageMetric("Database", usage.databaseBytes)
                        StorageMetric("Backup/restore workspace", usage.archiveWorkingBytes)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        StorageMetric("Total", usage.totalBytes, emphasized = true)
                    }
                }
            }

            item("destination") {
                StorageSectionCard("Save location", Icons.Filled.Folder) {
                    LocationLine("Exports", uiState.exportDestination.exportLabel)
                    LocationLine("Backups", uiState.exportDestination.backupLabel)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onChangeFolder, enabled = !uiState.archiveWork.isRunning) {
                            Text("Change folder")
                        }
                        if (uiState.exportDestination !is ExportDestination.DefaultDownloadsScanly) {
                            OutlinedButton(onClick = onResetFolder, enabled = !uiState.archiveWork.isRunning) {
                                Text("Reset default")
                            }
                        }
                    }
                }
            }

            if (uiState.archiveWork.phase != ArchiveWorkPhase.IDLE) {
                item("work") {
                    ArchiveProgressCard(uiState = uiState, onCancel = onCancelWork)
                }
            }

            item("backup") {
                StorageSectionCard("Library backup", Icons.Filled.Backup) {
                    val estimate = uiState.backupEstimate
                    when {
                        uiState.isLoadingBackupEstimate -> Text("Checking library size and free space…")
                        estimate == null -> Text("Backup availability could not be calculated.")
                        else -> {
                            Text(
                                "${estimate.documentCount} documents • ${estimate.pageCount} pages",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "Required: ${StorageFormatter.formatBytes(estimate.requiredBytes)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Available: ${estimate.availableBytes?.let(StorageFormatter::formatBytes) ?: "Unknown"}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            estimate.reason?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onBackup,
                            enabled = estimate?.canBackup == true && !uiState.archiveWork.isRunning,
                        ) {
                            Text("Back up library")
                        }
                        OutlinedButton(
                            onClick = onRefreshEstimate,
                            enabled = !uiState.archiveWork.isRunning,
                        ) {
                            Text("Recheck space")
                        }
                    }
                }
            }

            item("restore") {
                StorageSectionCard("Restore", Icons.Filled.Restore) {
                    Text(
                        "Select a .scanly file. Scanly validates and stages it before changing the library.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onRestore, enabled = !uiState.archiveWork.isRunning) {
                        Text("Choose backup")
                    }
                }
            }

            item("clear") {
                StorageSectionCard("Data controls", Icons.Filled.DeleteOutline) {
                    Text(
                        "Permanently delete the local library, temporary exports, and archive workspace.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { showClearWarning = true },
                        enabled = !uiState.isClearingData && !uiState.archiveWork.isRunning,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Clear all data")
                    }
                }
            }
        }
    }

    if (showClearWarning) {
        ScanlyConfirmDialog(
            title = "Clear all data?",
            text = "This permanently deletes all documents, folders, pages, temporary exports, and " +
                "backup/restore workspace files. Backups already saved in the backup folder are kept.",
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
private fun StorageSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun StorageMetric(label: String, bytes: Long, emphasized: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal)
        Text(StorageFormatter.formatBytes(bytes), fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun LocationLine(label: String, path: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(path, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ArchiveProgressCard(uiState: SettingsUiState, onCancel: () -> Unit) {
    val work = uiState.archiveWork
    StorageSectionCard(
        title = if (work.operation == ArchiveOperation.RESTORE) "Restore status" else "Backup status",
        icon = if (work.operation == ArchiveOperation.RESTORE) Icons.Filled.Restore else Icons.Filled.Backup,
    ) {
        Text(work.message ?: work.phase.name.lowercase().replaceFirstChar(Char::uppercase))
        if (work.isRunning) {
            if (work.total > 0) {
                LinearProgressIndicator(
                    progress = { work.current.toFloat() / work.total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (work.canCancel) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun RestoreModeDialog(onDismiss: () -> Unit, onSelect: (RestoreMode) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = AlertDialogDefaults.containerColor,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Restore library?", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Validation and restoration can take several minutes. Library changes are paused while " +
                        "the restore runs. Choose how existing data should be handled.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { onSelect(RestoreMode.MERGE) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Merge as copies")
                }
                Button(
                    onClick = { onSelect(RestoreMode.REPLACE) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Replace current library")
                }
                Text(
                    "Replace deletes the current library only after the backup validates and stages successfully.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}

private fun queryDocumentName(resolver: android.content.ContentResolver, uri: Uri): String? =
    resolver.query(
        uri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
