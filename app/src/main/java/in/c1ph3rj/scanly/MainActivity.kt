package `in`.c1ph3rj.scanly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.feature.document.DocumentDestination
import `in`.c1ph3rj.scanly.feature.camera.ScanSessionDestination
import `in`.c1ph3rj.scanly.feature.home.HomeScreen
import `in`.c1ph3rj.scanly.feature.home.HomeUiState
import `in`.c1ph3rj.scanly.navigation.ScanlyNavHost
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanlyApp()
        }
    }
}

private enum class PendingScanAction {
    CAMERA,
    IMPORT,
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ScanlyApp() {
    val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
    val scanActionViewModel: RootScanActionViewModel = hiltViewModel()
    val themeMode by appSettingsViewModel.themeMode.collectAsState()
    val scanUiState by scanActionViewModel.uiState.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var scanSheetVisible by rememberSaveable { mutableStateOf(false) }
    var pendingAction by rememberSaveable { mutableStateOf<PendingScanAction?>(null) }
    var importTargetDocumentId by rememberSaveable { mutableStateOf<String?>(null) }

    val importImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        val documentId = importTargetDocumentId
        importTargetDocumentId = null
        if (documentId != null && uris.isNotEmpty()) {
            scanActionViewModel.importImages(documentId, uris)
        }
    }

    LaunchedEffect(scanActionViewModel, navController) {
        scanActionViewModel.events.collectLatest { event ->
            when (event) {
                is RootScanEvent.OpenScanSession -> {
                    navController.navigate(ScanSessionDestination.route(event.documentId))
                }

                is RootScanEvent.OpenDocument -> {
                    navController.navigate(DocumentDestination.route(event.documentId))
                }

                is RootScanEvent.LaunchImportPicker -> {
                    importTargetDocumentId = event.documentId
                    importImagesLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }

                is RootScanEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    ScanlyTheme(
        darkTheme = themeMode.resolveDarkTheme(systemDark),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                ScanlyNavHost(
                    navController = navController,
                    onScanClick = { scanSheetVisible = true },
                )
            }
        }

        if (scanSheetVisible) {
            ScanActionSheet(
                importEnabled = !scanUiState.isImporting,
                onDismiss = { scanSheetVisible = false },
                onOpenCamera = {
                    scanSheetVisible = false
                    pendingAction = PendingScanAction.CAMERA
                },
                onImportImages = {
                    scanSheetVisible = false
                    pendingAction = PendingScanAction.IMPORT
                },
            )
        }

        pendingAction?.let { action ->
            NewScanDocumentDialog(
                title = when (action) {
                    PendingScanAction.CAMERA -> "New scan document"
                    PendingScanAction.IMPORT -> "Import into new document"
                },
                onDismiss = { pendingAction = null },
                onConfirm = { title ->
                    pendingAction = null
                    when (action) {
                        PendingScanAction.CAMERA -> scanActionViewModel.startCamera(title)
                        PendingScanAction.IMPORT -> scanActionViewModel.startImport(title)
                    }
                },
            )
        }

        if (scanUiState.isImporting) {
            ImportProgressOverlay()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ScanActionSheet(
    importEnabled: Boolean,
    onDismiss: () -> Unit,
    onOpenCamera: () -> Unit,
    onImportImages: () -> Unit,
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
                text = "New document",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Choose how to create the next document.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScanActionRow(
                icon = Icons.Filled.CameraAlt,
                title = "Scan with camera",
                enabled = true,
                onClick = onOpenCamera,
            )
            ScanActionRow(
                icon = Icons.Filled.Image,
                title = "Import from gallery",
                enabled = importEnabled,
                onClick = onImportImages,
            )
        }
    }
}

@Composable
private fun ScanActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier else Modifier)
            .padding(0.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
        },
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun NewScanDocumentDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(text = "Document title") },
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }) {
                Text(text = "Continue")
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
private fun ImportProgressOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Importing images",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanlyAppPreview() {
    ScanlyTheme {
        HomeScreen(
            uiState = HomeUiState.initial(),
            snackbarHostState = SnackbarHostState(),
            onRenameDocument = { _, _ -> },
            onDeleteDocument = {},
            onChangeDocumentGroup = { _, _ -> },
            onCreateGroupForDocument = { _, _ -> },
            onOpenDocument = {},
            onOpenGroups = {},
            onOpenSearch = {},
            onSortModeChanged = {},
        )
    }
}


private fun ThemeMode.resolveDarkTheme(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
