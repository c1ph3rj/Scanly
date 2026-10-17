package `in`.c1ph3rj.scanly.feature.tools.pdf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import `in`.c1ph3rj.scanly.feature.sign.editor.SignEditorScreen
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolCompactHeader
import `in`.c1ph3rj.scanly.feature.tools.ToolDetailRow
import `in`.c1ph3rj.scanly.feature.tools.ToolFieldLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolNote
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolShortcutTile
import `in`.c1ph3rj.scanly.feature.tools.ToolTextAction
import `in`.c1ph3rj.scanly.feature.tools.ToolTextField
import `in`.c1ph3rj.scanly.feature.tools.ToolWorkingState
import kotlinx.coroutines.flow.collectLatest

private enum class SignStep { Pick, Unlock, Edit, Working, Done }

/**
 * Sign PDF tool. Flow: Pick a PDF, unlock it if needed, place marks in the shared sign editor,
 * wait while the signed copy is written, then save or share it from the Done screen.
 */
@Composable
fun PdfSignRoute(
    onNavigateUp: () -> Unit,
    onPreviewPdf: (ExportArtifact) -> Unit,
    onManageSignatures: () -> Unit,
    onCreateSignature: (SignatureKind) -> Unit,
    viewModel: PdfSignViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val pageBitmaps by viewModel.pageBitmaps.collectAsStateWithLifecycle()
    val isSessionReady by viewModel.isSessionReady.collectAsStateWithLifecycle()
    val isOpening by viewModel.isOpening.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var editing by rememberSaveable { mutableStateOf(false) }
    var returnToEditorOnPickerBack by rememberSaveable { mutableStateOf(false) }

    val step = when {
        uiState.result != null -> SignStep.Done
        !editing || uiState.sources.isEmpty() -> SignStep.Pick
        uiState.isProcessing -> SignStep.Working
        uiState.needsPassword -> SignStep.Unlock
        else -> SignStep.Edit
    }

    val devicePicker = rememberSinglePdfPicker { uri ->
        returnToEditorOnPickerBack = false
        viewModel.setSources(listOf(PdfToolSource.DeviceUri(uri.toString(), uriDisplayName(context, uri))))
        editing = true
    }

    val onPickerBack = {
        if (shouldReturnFromPdfPickerToPreviousStep(uiState.sources.isNotEmpty(), returnToEditorOnPickerBack)) {
            returnToEditorOnPickerBack = false
            editing = true
        } else {
            onNavigateUp()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest {
            if (it is PdfToolEvent.ShowMessage) snackbarHostState.showSnackbar(it.message)
        }
    }

    BackHandler(enabled = step == SignStep.Unlock) { editing = false }
    BackHandler(
        enabled = step == SignStep.Pick && uiState.sources.isNotEmpty() && returnToEditorOnPickerBack,
        onBack = onPickerBack,
    )
    BackHandler(enabled = step == SignStep.Working) {
        // Keep the signing job alive until it produces its result.
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (step) {
            SignStep.Pick -> PdfFilePickerScreen(
                title = "Sign PDF",
                subtitle = "Choose a PDF to sign",
                stepText = "Step 1 of 3 · Choose",
                documents = uiState.libraryDocuments,
                onBack = onPickerBack,
                onPickDevice = { devicePicker.launch(arrayOf("application/pdf")) },
                onPickLibrary = { document ->
                    returnToEditorOnPickerBack = false
                    viewModel.setSources(listOf(PdfToolSource.LibraryDocument(document.id, document.title)))
                    editing = true
                },
            )
            SignStep.Unlock -> SignUnlockStep(
                uiState = uiState,
                viewModel = viewModel,
                onBack = { editing = false },
            )
            SignStep.Edit -> if (isOpening || !isSessionReady) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                SignEditorScreen(
                    state = editorState,
                    pageBitmaps = pageBitmaps,
                    onAction = viewModel::onEditorAction,
                    onRequestPage = viewModel::requestPage,
                    onCancel = {
                        returnToEditorOnPickerBack = false
                        editing = false
                    },
                    onDone = viewModel::run,
                    onManageSignatures = onManageSignatures,
                    onCreateSignature = onCreateSignature,
                    modifier = Modifier.fillMaxSize(),
                    title = "Sign",
                    doneEnabled = editorState.marks.isNotEmpty(),
                )
            }
            SignStep.Working -> ToolWorkingState(
                title = "Signing your PDF",
                caption = "Placing signatures on each page…",
                progress = null,
            )
            SignStep.Done -> {
                val markCount = editorState.marks.size
                val pageCount = editorState.marks.map { it.pageIndex }.distinct().size
                PdfToolDoneScreen(
                    title = "Signed",
                    subtitle = signSummary(markCount = markCount, pageCount = pageCount),
                    preview = uiState.resultPreview,
                    outputName = uiState.outputName,
                    onOutputNameChange = viewModel::setOutputName,
                    details = listOf(
                        ToolDetailRow("Pages signed", formatPdfPageCount(pageCount)),
                        ToolDetailRow("Marks placed", markCount.toString()),
                        ToolDetailRow("Saved as", "PDF"),
                    ),
                    onClose = onNavigateUp,
                    onOpenPreview = { uiState.namedResult()?.let(onPreviewPdf) },
                    onSave = viewModel::saveResult,
                    onShare = {
                        viewModel.prepareNamedShare { shareExportArtifact(context, it, "Signed PDF") }
                    },
                    isSaving = uiState.isProcessing,
                    savedDestinationLabel = uiState.savedDestinationLabel,
                    stepText = "Step 3 of 3 · Done",
                    extraContent = {
                        ToolShortcutTile(
                            icon = Icons.Outlined.Edit,
                            title = "Edit signatures",
                            caption = "Back to the editor",
                            onClick = {
                                viewModel.editAgain()
                                editing = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }
}

/** Password prompt for a locked PDF, in the same layout as the watermark tool's unlock step. */
@Composable
private fun SignUnlockStep(
    uiState: PdfToolUiState,
    viewModel: PdfSignViewModel,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        ToolCompactHeader(
            title = "Sign",
            subtitle = uiState.sources.firstOrNull()?.label(),
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ToolFieldLabel("This PDF is locked")
            ToolTextField(
                value = uiState.currentPassword,
                onValueChange = viewModel::setCurrentPassword,
                placeholder = "PDF password",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                trailingIcon = { ToolTextAction(label = "Unlock", onClick = viewModel::unlock) },
            )
            ToolNote("The signed copy is saved without the password.")
        }
        ToolBottomBar {
            ToolPrimaryButton(
                text = "Unlock",
                onClick = viewModel::unlock,
                enabled = uiState.currentPassword.isNotBlank(),
            )
        }
    }
}

/** "3 marks on 2 pages", the summary line on the Done screen. */
private fun signSummary(markCount: Int, pageCount: Int): String {
    val marks = if (markCount == 1) "1 mark" else "$markCount marks"
    return "$marks on ${formatPdfPageCount(pageCount)}"
}
