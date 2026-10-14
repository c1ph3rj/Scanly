package `in`.c1ph3rj.scanly.feature.tools.qr

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.feature.tools.pdf.shareExportArtifact
import kotlinx.coroutines.flow.collectLatest

/**
 * QR tool: Scan (the default when opened from Tools), Create and History, with the result
 * screen above them. [initialMode] is only read the first time the route is composed.
 */
@Composable
fun QrToolRoute(
    onNavigateUp: () -> Unit,
    initialMode: QrToolMode = QrToolMode.Scan,
) {
    val viewModel: QrToolViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var initialModeReady by remember(viewModel, initialMode) { mutableStateOf(false) }

    LaunchedEffect(viewModel, initialMode) {
        viewModel.applyInitialMode(initialMode)
        initialModeReady = true
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is QrToolEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Until the seed is applied, show the screen the nav argument asks for, not the default.
    val screen = when {
        initialModeReady -> uiState.screen
        initialMode == QrToolMode.Generate -> QrScreen.Create
        else -> QrScreen.Scan
    }

    // Result and History step back inside the tool; Scan and Create leave it (system back).
    BackHandler(enabled = screen is QrScreen.Result || screen == QrScreen.History) {
        viewModel.navigateBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (screen) {
            QrScreen.Scan -> QrScanScreen(
                found = uiState.found,
                isReadingPhoto = uiState.isReadingPhoto,
                onCodeFound = viewModel::onCodeFound,
                onPhotoPicked = viewModel::onPhotoPicked,
                onDismissFound = viewModel::dismissFound,
                onOpenDetails = viewModel::openFoundResult,
                onClose = onNavigateUp,
                onSelectCreate = viewModel::showCreate,
                onOpenHistory = viewModel::showHistory,
                onMessage = viewModel::emitMessage,
            )
            QrScreen.Create -> QrCreateScreen(
                draft = uiState.draft,
                preview = uiState.previewBitmap,
                isSaving = uiState.isSaving,
                onBack = onNavigateUp,
                onSelectScan = viewModel::showScanner,
                onUpdate = viewModel::updateDraft,
                onSave = viewModel::saveCreatedCode,
                onShare = {
                    viewModel.shareCreatedCode { artifact ->
                        shareExportArtifact(context, artifact, "QR code")
                    }
                },
            )
            QrScreen.History -> QrHistoryScreen(
                entries = history,
                query = uiState.historyQuery,
                filter = uiState.historyFilter,
                onQueryChange = viewModel::setHistoryQuery,
                onFilterChange = viewModel::setHistoryFilter,
                onBack = viewModel::navigateBack,
                onOpen = viewModel::openHistoryEntry,
                onClear = viewModel::clearHistory,
            )
            is QrScreen.Result -> QrResultScreen(
                item = screen.item,
                onBack = viewModel::navigateBack,
                onDelete = viewModel::deleteResultEntry,
                onMessage = viewModel::emitMessage,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }
}
