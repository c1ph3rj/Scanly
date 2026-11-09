package `in`.c1ph3rj.scanly.feature.tools.text

import `in`.c1ph3rj.scanly.core.common.appPluralString
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
import `in`.c1ph3rj.scanly.R
import androidx.compose.ui.res.stringResource
import `in`.c1ph3rj.scanly.core.common.appString

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
        Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            PdfFilePickerScreen(
                title = stringResource(R.string.image_text_image_to_text),
                subtitle = stringResource(R.string.image_text_copy_text_from_photos_or),
                stepText = null,
                documents = documents,
                onBack = onNavigateUp,
                onPickDevice = { if (!uiState.isImporting) photoPicker.launch(ImageImportSupport.createPickRequest()) },
                onPickLibrary = { document -> onOpenText(document.id, false) },
                deviceTitle = stringResource(R.string.signature_create_photos),
                deviceSubtitle = stringResource(R.string.image_text_up_to_from_your_gallery, ImageImportSupport.MAX_IMAGES_PER_IMPORT),
                deviceIcon = Icons.Outlined.PhotoLibrary,
                itemNoun = "scans",
                countLabel = { count -> appPluralString(R.plurals.common_scans, count, count) },
                emptyLibraryHint = stringResource(R.string.image_text_no_scans_yet_pick_photos),
                noPagesHint = stringResource(R.string.image_text_your_scans_have_no_pages),
                headerExtra = {
                    ToolNote(text = stringResource(R.string.image_text_photos_are_saved_as_a))
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
                stageLabel = uiState.importStageLabel.ifBlank { stringResource(R.string.document_detail_working_on_your_photos) },
            )
        }
    }
}
