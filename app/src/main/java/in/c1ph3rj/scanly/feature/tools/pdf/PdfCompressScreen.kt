package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfCompressQuality
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolCard
import `in`.c1ph3rj.scanly.feature.tools.ToolCompactHeader
import `in`.c1ph3rj.scanly.feature.tools.ToolDetailRow
import `in`.c1ph3rj.scanly.feature.tools.ToolFieldLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolNote
import `in`.c1ph3rj.scanly.feature.tools.ToolPaperPage
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolShortcutTile
import `in`.c1ph3rj.scanly.feature.tools.ToolTextAction
import `in`.c1ph3rj.scanly.feature.tools.ToolTextField
import `in`.c1ph3rj.scanly.feature.tools.ToolTokens
import `in`.c1ph3rj.scanly.feature.tools.ToolWorkingState
import kotlinx.coroutines.flow.collectLatest

/** The four screens of the Compress flow, in order: Choose a file, Choose quality, Compressing, Done. */
private enum class CompressStep { Pick, Options, Working, Done }

@Composable
fun PdfCompressRoute(
    onNavigateUp: () -> Unit,
    onPreviewPdf: (ExportArtifact) -> Unit,
    initialSource: PdfToolSource? = null,
) {
    val viewModel: PdfCompressViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // rememberSaveable: rotation must keep the picker open after "Change" and closed after a pick.
    var showPicker by rememberSaveable { mutableStateOf(initialSource == null) }
    var returnToOptionsOnPickerBack by rememberSaveable { mutableStateOf(false) }

    // Opened from the reader's menu with that PDF: skip the picker, and back returns to the reader.
    val fromReader = initialSource != null
    var initialSourceApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialSource) {
        if (initialSource != null && !initialSourceApplied) {
            initialSourceApplied = true
            viewModel.onSourcesChosen(listOf(initialSource))
        }
    }
    val step = when {
        uiState.result != null -> CompressStep.Done
        uiState.isProcessing -> CompressStep.Working
        showPicker || uiState.sources.isEmpty() -> CompressStep.Pick
        else -> CompressStep.Options
    }

    val devicePicker = rememberSinglePdfPicker { uri ->
        returnToOptionsOnPickerBack = false
        showPicker = false
        viewModel.onSourcesChosen(
            listOf(PdfToolSource.DeviceUri(uri.toString(), uriDisplayName(context, uri))),
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest {
            if (it is PdfToolEvent.ShowMessage) snackbarHostState.showSnackbar(it.message)
        }
    }

    val onPickerBack = {
        if (shouldReturnFromPdfPickerToPreviousStep(uiState.sources.isNotEmpty(), returnToOptionsOnPickerBack)) {
            returnToOptionsOnPickerBack = false
            showPicker = false
        } else {
            onNavigateUp()
        }
    }
    BackHandler(enabled = step == CompressStep.Pick && uiState.sources.isNotEmpty(), onBack = onPickerBack)
    BackHandler(enabled = step == CompressStep.Options) {
        if (fromReader) {
            onNavigateUp()
        } else {
            returnToOptionsOnPickerBack = false
            showPicker = true
        }
    }
    BackHandler(enabled = step == CompressStep.Done) { viewModel.clearResult() }
    // A compression run cannot be cancelled, so back does nothing until it finishes.
    BackHandler(enabled = step == CompressStep.Working) { }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 640.dp)) {
            when (step) {
                CompressStep.Pick -> PdfFilePickerScreen(
                    title = "Compress PDF",
                    subtitle = "Shrink a PDF for email or upload limits.",
                    stepText = "Step 1 of 3 · Choose a file",
                    documents = uiState.libraryDocuments,
                    onBack = onPickerBack,
                    onPickDevice = { devicePicker.launch(arrayOf("application/pdf")) },
                    onPickLibrary = { document ->
                        returnToOptionsOnPickerBack = false
                        showPicker = false
                        viewModel.onSourcesChosen(
                            listOf(PdfToolSource.LibraryDocument(document.id, document.title)),
                        )
                    },
                    defaultSort = PdfPickerSort.MostPages,
                )
                CompressStep.Options -> CompressOptionsStep(
                    uiState = uiState,
                    onChange = {
                        returnToOptionsOnPickerBack = true
                        showPicker = true
                    },
                    onQualitySelected = viewModel::setQuality,
                    onPasswordChange = viewModel::setCurrentPassword,
                    onUnlock = viewModel::unlockOrRefresh,
                    onCompress = viewModel::runCompress,
                )
                CompressStep.Working -> CompressWorkingStep(uiState = uiState)
                CompressStep.Done -> CompressDoneStep(
                    uiState = uiState,
                    onClose = onNavigateUp,
                    onOpenPreview = { uiState.namedResult()?.let(onPreviewPdf) },
                    onOutputNameChange = viewModel::setOutputName,
                    onSave = viewModel::saveResult,
                    onShare = {
                        viewModel.prepareNamedShare { shareExportArtifact(context, it, "Compressed PDF") }
                    },
                    onTryAnotherQuality = viewModel::clearResult,
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

/** Step 2: header with the file summary, the quality choice, an optional password, and "Compress". */
@Composable
private fun ColumnScope.CompressOptionsStep(
    uiState: PdfToolUiState,
    onChange: () -> Unit,
    onQualitySelected: (PdfCompressQuality) -> Unit,
    onPasswordChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onCompress: () -> Unit,
) {
    val name = uiState.sources.firstOrNull()?.label().orEmpty()
    val meta = formatPdfDocumentMeta(
        pageCount = uiState.info?.pageCount,
        sizeBytes = uiState.originalSizeBytes ?: uiState.info?.fileSizeBytes,
    )
    val subtitle = listOf(name, meta).filter { it.isNotBlank() }.joinToString(" · ")
    val locked = uiState.needsPassword || uiState.info?.isEncrypted == true
    var showPassword by rememberSaveable { mutableStateOf(false) }

    ToolCompactHeader(
        title = "Compress",
        onBack = onChange,
        subtitle = subtitle.takeIf { it.isNotBlank() },
        actionLabel = "Change",
        onAction = onChange,
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ToolTokens.ScreenPadding)
            .padding(top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ToolPaperPage(
            bitmap = uiState.sourcePagePreview,
            loading = uiState.isSourcePagePreviewLoading,
            contentDescription = "First page of ${name.ifBlank { "the PDF" }}",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(120.dp),
        )

        if (locked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolFieldLabel(text = "PDF password")
                ToolTextField(
                    value = uiState.currentPassword,
                    onValueChange = onPasswordChange,
                    placeholder = "Enter the password",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                            )
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    ToolTextAction(label = "Unlock", onClick = onUnlock)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolFieldLabel(text = "Quality")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PdfCompressQuality.entries.forEach { quality ->
                    QualityOptionCard(
                        quality = quality,
                        selected = uiState.compressQuality == quality,
                        onClick = { onQualitySelected(quality) },
                    )
                }
            }
        }

        ToolNote(text = "Pages are re-encoded as images, so text in the PDF can no longer be selected or searched.")
    }

    ToolBottomBar {
        ToolPrimaryButton(
            text = "Compress",
            onClick = onCompress,
            enabled = !uiState.isProcessing && (!locked || uiState.currentPassword.isNotBlank()),
            icon = Icons.Outlined.Compress,
        )
    }
}

/** Step 3: the file's first page with a progress caption while the run is in flight. */
@Composable
private fun CompressWorkingStep(uiState: PdfToolUiState) {
    val pages = uiState.info?.pageCount?.takeIf { it > 0 }?.let(::formatPdfPageCount)
    ToolWorkingState(
        title = "Compressing…",
        caption = listOfNotNull(uiState.compressQuality.label, pages).joinToString(" · "),
        preview = {
            ToolPaperPage(
                bitmap = uiState.sourcePagePreview,
                loading = uiState.isSourcePagePreviewLoading,
                modifier = Modifier.width(140.dp),
            )
        },
    )
}

/** Step 4: the result, the saved size change, an editable file name and "Try another quality". */
@Composable
private fun CompressDoneStep(
    uiState: PdfToolUiState,
    onClose: () -> Unit,
    onOpenPreview: () -> Unit,
    onOutputNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onTryAnotherQuality: () -> Unit,
) {
    val before = uiState.originalSizeBytes
    val after = uiState.compressedSizeBytes ?: uiState.resultSizeBytes
    val savedPercent = if (before != null && after != null && before > 0L && after < before) {
        ((before - after) * 100L / before).toInt()
    } else {
        null
    }
    val sizeLine = if (before != null && after != null) {
        "${StorageFormatter.formatBytes(before)} → ${StorageFormatter.formatBytes(after)}"
    } else {
        null
    }
    val title = when {
        savedPercent != null -> "Saved $savedPercent%"
        sizeLine != null -> "Already compact"
        else -> "Compressed"
    }
    val subtitle = when {
        savedPercent != null -> sizeLine.orEmpty()
        sizeLine != null -> "This quality didn't make the file smaller. Try another one."
        else -> ""
    }
    val details = buildList {
        add(ToolDetailRow("Quality", uiState.compressQuality.label))
        if (before != null) add(ToolDetailRow("Before", StorageFormatter.formatBytes(before)))
        if (after != null) add(ToolDetailRow("After", StorageFormatter.formatBytes(after)))
        uiState.info?.pageCount?.takeIf { it > 0 }?.let { add(ToolDetailRow("Pages", it.toString())) }
    }

    PdfToolDoneScreen(
        title = title,
        subtitle = subtitle,
        preview = uiState.resultPreview,
        outputName = uiState.outputName,
        onOutputNameChange = onOutputNameChange,
        details = details,
        onClose = onClose,
        onOpenPreview = onOpenPreview,
        onSave = onSave,
        onShare = onShare,
        savedDestinationLabel = uiState.savedDestinationLabel,
        isSaving = uiState.isProcessing,
        extraContent = {
            ToolShortcutTile(
                icon = Icons.Outlined.Tune,
                title = "Try another quality",
                caption = "Compress this PDF again with a different setting",
                onClick = onTryAnotherQuality,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun QualityOptionCard(
    quality: PdfCompressQuality,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // onClick goes to ToolCard (Surface) so the ripple is clipped to the 14dp corners.
    ToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected; role = Role.RadioButton },
        onClick = onClick,
        selected = selected,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = quality.label,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (quality == PdfCompressQuality.MEDIUM) {
                        RecommendedChip()
                    }
                }
                Text(
                    // The "Recommended" flag is shown as a chip, so drop it from the description.
                    text = quality.description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QualityRadio(selected = selected)
        }
    }
}

@Composable
private fun RecommendedChip() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = "Recommended",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
        )
    }
}

@Composable
private fun QualityRadio(selected: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(22.dp)
            .border(
                width = 2.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(modifier = Modifier.size(10.dp).background(accent, CircleShape))
        }
    }
}
