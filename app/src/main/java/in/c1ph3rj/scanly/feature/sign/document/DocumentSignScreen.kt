package `in`.c1ph3rj.scanly.feature.sign.document

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import `in`.c1ph3rj.scanly.feature.sign.editor.SignEditorScreen

/** Space kept between the snackbar and the bottom edge, clearing the editor's bottom panel. */
private val SnackbarBottomInset = 160.dp

private val SavingScrim = Color.Black.copy(alpha = 0.45f)

/**
 * Route for signing a library document in place. Hosts the shared [SignEditorScreen] with the
 * document's pages, shows progress while loading, and blocks back navigation while saving.
 *
 * @param onNavigateUp Called on cancel, and after a successful save.
 * @param onManageSignatures Opens the saved signatures screen.
 * @param onCreateSignature Opens the signature creator for the given kind.
 */
@Composable
fun DocumentSignRoute(
    onNavigateUp: () -> Unit,
    onManageSignatures: () -> Unit,
    onCreateSignature: (SignatureKind) -> Unit,
    viewModel: DocumentSignViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editorState by viewModel.editor.state.collectAsStateWithLifecycle()
    val pageBitmaps by viewModel.pageBitmaps.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnNavigateUp by rememberUpdatedState(onNavigateUp)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DocumentSignEvent.Saved -> currentOnNavigateUp()
                is DocumentSignEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    BackHandler(enabled = uiState.isSaving) {}

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            SignEditorScreen(
                state = editorState,
                pageBitmaps = pageBitmaps,
                onAction = viewModel::onEditorAction,
                onRequestPage = viewModel::requestPage,
                onCancel = onNavigateUp,
                onDone = viewModel::save,
                onManageSignatures = onManageSignatures,
                onCreateSignature = onCreateSignature,
                title = "Sign",
                doneEnabled = !uiState.isSaving,
            )
        }

        if (uiState.isSaving) {
            SavingOverlay()
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = SnackbarBottomInset),
        )
    }
}

/** Dims the editor and blocks touches while the markups are being stamped and saved. */
@Composable
private fun SavingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SavingScrim)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Stamping signatures…",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}
