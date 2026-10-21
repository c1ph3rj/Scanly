package `in`.c1ph3rj.scanly.feature.tools.text

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ImageImportSupport
import `in`.c1ph3rj.scanly.feature.components.ScanlyImportProgressOverlay
import `in`.c1ph3rj.scanly.feature.tools.ToolNote
import `in`.c1ph3rj.scanly.feature.tools.pdf.PdfFilePickerScreen
import kotlinx.coroutines.flow.collectLatest

/**
 * "Image to text": choose photos or a Scanly scan, then read its text in the Extracted text
 * screen. Photos are imported as a new document first; [onOpenText] receives its id.
 */
@Composable
fun ImageTextRoute(
    onNavigateUp: () -> Unit,
    onOpenText: (documentId: String, replaceTool: Boolean) -> Unit,
    viewModel: ImageTextViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val documents by viewModel.libraryDocuments.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ImageImportSupport.pickImagesContract()) { uris ->
        if (uris.isNotEmpty()) viewModel.importPhotos(uris)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ImageTextEvent.OpenText -> {
                    // The text screen has no snackbar of ours, so a toast carries the note across.
                    event.notice?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                    onOpenText(event.documentId, true)
                }
                is ImageTextEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Leaving halfway would strand a half-imported document.
    BackHandler(enabled = uiState.isImporting) { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 640.dp)) {
            PdfFilePickerScreen(
                title = "Image to text",
                subtitle = "Copy text from photos or scans. Text is read on this phone.",
                stepText = null,
                documents = documents,
                onBack = onNavigateUp,
                onPickDevice = { if (!uiState.isImporting) photoPicker.launch(ImageImportSupport.createPickRequest()) },
                onPickLibrary = { document -> onOpenText(document.id, false) },
                deviceTitle = "Photos",
                deviceSubtitle = "Up to ${ImageImportSupport.MAX_IMAGES_PER_IMPORT} from your gallery or files",
                deviceIcon = Icons.Outlined.PhotoLibrary,
                itemNoun = "scans",
                countLabel = { count -> if (count == 1) "1 scan" else "$count scans" },
                emptyLibraryHint = "No scans yet. Pick photos to read their text.",
                noPagesHint = "Your scans have no pages yet. Pick photos to read their text.",
                headerExtra = {
                    ToolNote(text = "Photos are saved as a new document, so you can search their text later.")
                },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
        if (uiState.isImporting) {
            ScanlyImportProgressOverlay(
                current = uiState.importCurrent,
                total = uiState.importTotal,
                stageLabel = uiState.importStageLabel.ifBlank { "Working on your photos" },
            )
        }
    }
}
