package `in`.c1ph3rj.scanly.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.ArchiveOperation
import `in`.c1ph3rj.scanly.domain.model.ArchiveWorkPhase
import `in`.c1ph3rj.scanly.domain.model.ExportDestination
import `in`.c1ph3rj.scanly.domain.model.RestoreMode
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK }
        if (uri != null) {
            scope.launch {
                val fileName = withContext(Dispatchers.IO) {
                    queryDocumentName(context.contentResolver, uri)
                }
                if (fileName?.endsWith(SCANLY_BACKUP_EXTENSION, ignoreCase = true) != true) {
                    snackbarHostState.showSnackbar("Choose a .scanly backup file.")
                    return@launch
                }
                val permission = runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                if (permission.isFailure) {
                    snackbarHostState.showSnackbar("Scanly could not keep access to this backup file.")
                    return@launch
                }
                pendingRestoreUri = uri
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
            scope.launch {
                val initialUri = withContext(Dispatchers.IO) {
                    queryBackupDirectoryUri(context.contentResolver, uiState.exportDestination)
                }
                restoreLauncher.launch(createBackupPickerIntent(initialUri))
            }
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
                "not encrypted, so keep it private." +
                if (uiState.backupEstimate?.availableBytes == null) {
                    " This folder does not report free space; Scanly will stop safely if it fills up."
                } else {
                    ""
                },
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
                StorageSectionCard(
                    title = "App storage",
                    icon = Icons.Filled.Storage
                ) {
                    val usage = uiState.storageUsage
                    if (uiState.isLoadingStorage || usage == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                            Text("Calculating storage…")
                        }
                    } else {
                        val c1 = MaterialTheme.colorScheme.primary
                        val c2 = Color(0xFFFF9800)
                        val c3 = Color(0xFF9C27B0)
                        val c4 = MaterialTheme.colorScheme.error

                        MultiSegmentStorageBar(
                            libraryBytes = usage.documentsBytes,
                            exportCacheBytes = usage.exportCacheBytes,
                            databaseBytes = usage.databaseBytes,
                            archiveWorkingBytes = usage.archiveWorkingBytes,
                            totalBytes = usage.totalBytes,
                            color1 = c1,
                            color2 = c2,
                            color3 = c3,
                            color4 = c4,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )

                        StorageLegendItem("Library files", usage.documentsBytes, c1)
                        StorageLegendItem("Temporary exports", usage.exportCacheBytes, c2)
                        StorageLegendItem("Database", usage.databaseBytes, c3)
                        if (usage.archiveWorkingBytes > 0L) {
                            StorageLegendItem("Backup/restore workspace", usage.archiveWorkingBytes, c4)
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Total", fontWeight = FontWeight.SemiBold)
                            Text(StorageFormatter.formatBytes(usage.totalBytes), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item("backup_restore") {
                StorageSectionCard(
                    title = "Backup & Restore",
                    icon = Icons.Filled.Backup,
                ) {
                    val estimate = uiState.backupEstimate

                    Text(
                        "Safeguard your library by creating a backup snapshot, or restore an existing one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    if (uiState.isLoadingBackupEstimate) {
                        Text(
                            "Checking library size and free space…",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    } else if (estimate != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${estimate.documentCount} documents • ${estimate.pageCount} pages",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                IconButton(
                                    onClick = onRefreshEstimate,
                                    enabled = !uiState.isLoadingBackupEstimate &&
                                        !uiState.archiveWork.isRunning,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = "Recheck available space",
                                    )
                                }
                            }
                            Text(
                                "Required space: ${StorageFormatter.formatBytes(estimate.requiredBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Available space: ${estimate.availableBytes?.let(StorageFormatter::formatBytes) ?: "Not reported"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (estimate.availableBytes == null && estimate.canBackup) {
                                Text(
                                    "Your folder provider does not expose capacity. Backup can still run and will stop safely if the folder fills up.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            estimate.reason?.let {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onBackup,
                            enabled = estimate?.canBackup == true && !uiState.archiveWork.isRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Back up")
                        }
                        OutlinedButton(
                            onClick = onRestore,
                            enabled = !uiState.archiveWork.isRunning,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Restore")
                        }
                    }
                }
            }

            if (uiState.archiveWork.phase != ArchiveWorkPhase.IDLE) {
                item("work") {
                    ArchiveProgressCard(uiState = uiState, onCancel = onCancelWork)
                }
            }

            item("destination") {
                StorageSectionCard(
                    title = "Save location",
                    icon = Icons.Filled.Folder
                ) {
                    LocationLine("Exports", uiState.exportDestination.exportLabel)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    LocationLine("Backups", uiState.exportDestination.backupLabel)
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

            item("clear") {
                StorageSectionCard(
                    title = "Danger zone",
                    icon = Icons.Filled.Warning,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    iconTint = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(
                        "Permanently delete the local library, temporary exports, and archive workspace. This cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Button(
                        onClick = { showClearWarning = true },
                        enabled = !uiState.isClearingData && !uiState.archiveWork.isRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
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
fun MultiSegmentStorageBar(
    libraryBytes: Long,
    exportCacheBytes: Long,
    databaseBytes: Long,
    archiveWorkingBytes: Long,
    totalBytes: Long,
    color1: Color,
    color2: Color,
    color3: Color,
    color4: Color,
    modifier: Modifier = Modifier
) {
    val minRatio = 0.02f
    val libraryRatio = if (libraryBytes > 0 && totalBytes > 0) maxOf(libraryBytes.toFloat() / totalBytes, minRatio) else 0f
    val cacheRatio = if (exportCacheBytes > 0 && totalBytes > 0) maxOf(exportCacheBytes.toFloat() / totalBytes, minRatio) else 0f
    val dbRatio = if (databaseBytes > 0 && totalBytes > 0) maxOf(databaseBytes.toFloat() / totalBytes, minRatio) else 0f
    val archiveRatio = if (archiveWorkingBytes > 0 && totalBytes > 0) maxOf(archiveWorkingBytes.toFloat() / totalBytes, minRatio) else 0f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .padding(horizontal = 16.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (totalBytes > 0L) {
            if (libraryRatio > 0f) {
                Box(modifier = Modifier.weight(libraryRatio).fillMaxHeight().background(color1))
            }
            if (cacheRatio > 0f) {
                Box(modifier = Modifier.weight(cacheRatio).fillMaxHeight().background(color2))
            }
            if (dbRatio > 0f) {
                Box(modifier = Modifier.weight(dbRatio).fillMaxHeight().background(color3))
            }
            if (archiveRatio > 0f) {
                Box(modifier = Modifier.weight(archiveRatio).fillMaxHeight().background(color4))
            }
        }
    }
}

@Composable
private fun StorageLegendItem(label: String, bytes: Long, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(StorageFormatter.formatBytes(bytes), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LocationLine(label: String, path: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = path,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StorageSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, contentDescription = null, tint = iconTint)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ArchiveProgressCard(uiState: SettingsUiState, onCancel: () -> Unit) {
    val work = uiState.archiveWork
    val operationLabel = if (work.operation == ArchiveOperation.RESTORE) "Restore" else "Backup"
    val title = when (work.phase) {
        ArchiveWorkPhase.SUCCEEDED -> "$operationLabel complete"
        ArchiveWorkPhase.FAILED -> "$operationLabel failed"
        ArchiveWorkPhase.CANCELLED -> "$operationLabel cancelled"
        else -> "$operationLabel status"
    }
    val failed = work.phase == ArchiveWorkPhase.FAILED
    StorageSectionCard(
        title = title,
        icon = if (work.operation == ArchiveOperation.RESTORE) Icons.Filled.Restore else Icons.Filled.Backup,
        containerColor = if (failed) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (failed) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        iconTint = if (failed) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
    ) {
        Text(
            text = work.message ?: work.phase.name.lowercase().replaceFirstChar(Char::uppercase),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (work.isRunning) {
            if (work.total > 0) {
                LinearProgressIndicator(
                    progress = { work.current.toFloat() / work.total.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            if (work.canCancel) {
                TextButton(onClick = onCancel, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Cancel") }
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

private fun queryDocumentName(resolver: android.content.ContentResolver, uri: Uri): String? {
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

private fun createBackupPickerIntent(initialUri: Uri?): Intent =
    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/zip"
        putExtra(
            Intent.EXTRA_MIME_TYPES,
            arrayOf("application/zip", "application/octet-stream"),
        )
        initialUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
    }

private fun queryBackupDirectoryUri(
    resolver: android.content.ContentResolver,
    destination: ExportDestination,
): Uri? = runCatching {
    when (destination) {
        ExportDestination.DefaultDownloadsScanly -> DocumentsContract.buildDocumentUri(
            EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY,
            DEFAULT_BACKUP_DOCUMENT_ID,
        )

        is ExportDestination.CustomTree -> {
            val treeUri = Uri.parse(destination.uriString)
            val parentId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                )
                val nameColumn = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                )
                val mimeColumn = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumn) == BACKUP_DIRECTORY_NAME &&
                        cursor.getString(mimeColumn) == DocumentsContract.Document.MIME_TYPE_DIR
                    ) {
                        return@use DocumentsContract.buildDocumentUriUsingTree(
                            parentUri,
                            cursor.getString(idColumn),
                        )
                    }
                }
                null
            }
        }
    }
}.getOrNull()

private const val SCANLY_BACKUP_EXTENSION = ".scanly"
private const val BACKUP_DIRECTORY_NAME = "backup"
private const val EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY = "com.android.externalstorage.documents"
private const val DEFAULT_BACKUP_DOCUMENT_ID = "primary:Download/Scanly/backup"
