package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.feature.components.shareExportArtifact
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.ZoomableBitmapViewer
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageState
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.core.ui.rememberZoomableImageState
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfCompressQuality
import `in`.c1ph3rj.scanly.domain.model.PdfPasswordMode
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.WatermarkLayout
import `in`.c1ph3rj.scanly.domain.model.WatermarkOrientation
import `in`.c1ph3rj.scanly.domain.model.WatermarkPageRange
import `in`.c1ph3rj.scanly.domain.model.WatermarkSize
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun PdfPasswordRoute(
    onNavigateUp: () -> Unit,
    onPreviewPdf: (ExportArtifact) -> Unit,
) {
    val viewModel: PdfPasswordViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPicker by rememberSaveable { mutableStateOf(true) }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var showCurrentPassword by remember { mutableStateOf(false) }
    val phase = toolPhase(uiState.sources, uiState.result)

    val devicePicker = rememberSinglePdfPicker { uri ->
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

    val alreadyProtected = uiState.isPdfAlreadyProtected()
    val passwordsMatch = uiState.password.isNotEmpty() &&
        uiState.password == uiState.confirmPassword
    val protectEnabled = passwordsMatch &&
        !uiState.isProcessing &&
        (!alreadyProtected || uiState.currentPassword.isNotBlank())
    val removeEnabled = alreadyProtected &&
        uiState.currentPassword.isNotBlank() &&
        !uiState.isProcessing
    val isProtect = uiState.passwordMode == PdfPasswordMode.Protect

    ToolDetailScaffold(
        title = "PDF Password",
        onNavigateUp = onNavigateUp,
        snackbarHostState = snackbarHostState,
        isProcessing = uiState.isProcessing,
        progressMessage = uiState.progressMessage,
        primaryAction = if (phase == ToolPhase.Ready) {
            {
                Button(
                    onClick = viewModel::run,
                    enabled = if (isProtect) protectEnabled else removeEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isProtect) "Protect PDF" else "Remove password")
                }
            }
        } else null,
    ) {
        when (phase) {
            ToolPhase.Empty -> ToolEmptyState(
                icon = Icons.Filled.Lock,
                title = "Protect or unlock a PDF",
                subtitle = "Add an open password or create an unlocked copy. Your original file stays unchanged.",
                onChooseSource = { showPicker = true },
            )
            ToolPhase.Done -> {
                uiState.result?.let { result ->
                    val protected = uiState.passwordMode == PdfPasswordMode.Protect
                    PdfToolCompleteScreen(
                        fileName = result.fileName,
                        statusTitle = if (protected) "Password applied" else "Password removed",
                        statusSubtitle = if (protected) {
                            "Your protected PDF is ready."
                        } else {
                            "Your unlocked PDF is ready."
                        },
                        previewHint = "Preview the finished file before saving or sharing it.",
                        onPreview = { onPreviewPdf(result) },
                        onSave = viewModel::saveResult,
                        onShare = {
                            shareExportArtifact(
                                context,
                                result,
                                if (protected) "Protected PDF" else "Unlocked PDF",
                            )
                        },
                        onBack = onNavigateUp,
                    )
                }
            }
            ToolPhase.Ready -> {
                val windowSizeInfo = rememberWindowSizeInfo()
                val modeOptions: @Composable () -> Unit = {
                    Text(
                        text = "ACTION",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Vertical stack keeps copy readable on tablet two-pane panes.
                    PasswordModeOptionCard(
                        title = "Protect",
                        description = if (alreadyProtected) {
                            "Replace the open password on this locked PDF"
                        } else {
                            "Lock the PDF with an open password"
                        },
                        selected = isProtect,
                        onClick = { viewModel.setMode(PdfPasswordMode.Protect) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PasswordModeOptionCard(
                        title = "Remove",
                        description = if (alreadyProtected) {
                            "Create an unlocked copy of this protected file"
                        } else {
                            "Only available when the PDF is already protected"
                        },
                        selected = !isProtect,
                        onClick = { viewModel.setMode(PdfPasswordMode.Remove) },
                    )
                }
                val passwordForm: @Composable () -> Unit = {
                    Text(
                        text = when {
                            isProtect && alreadyProtected -> "PASSWORDS"
                            isProtect -> "NEW PASSWORD"
                            else -> "CURRENT PASSWORD"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (isProtect) {
                                if (alreadyProtected) {
                                    PasswordVisibilityField(
                                        value = uiState.currentPassword,
                                        onValueChange = viewModel::setCurrentPassword,
                                        label = "Current password",
                                        visible = showCurrentPassword,
                                        onToggleVisible = {
                                            showCurrentPassword = !showCurrentPassword
                                        },
                                        supportingText = "Required — this PDF is already protected.",
                                    )
                                    if (uiState.sourcePagePreview == null) {
                                        TextButton(
                                            onClick = viewModel::unlockPreview,
                                            enabled = uiState.currentPassword.isNotBlank() &&
                                                !uiState.isSourcePagePreviewLoading,
                                        ) {
                                            Text("Unlock preview")
                                        }
                                    }
                                }
                                PasswordVisibilityField(
                                    value = uiState.password,
                                    onValueChange = viewModel::setPassword,
                                    label = "New password",
                                    visible = showPassword,
                                    onToggleVisible = { showPassword = !showPassword },
                                )
                                PasswordVisibilityField(
                                    value = uiState.confirmPassword,
                                    onValueChange = viewModel::setConfirmPassword,
                                    label = "Confirm password",
                                    visible = showConfirm,
                                    onToggleVisible = { showConfirm = !showConfirm },
                                    isError = uiState.confirmPassword.isNotEmpty() &&
                                        uiState.password != uiState.confirmPassword,
                                    supportingText = if (
                                        uiState.confirmPassword.isNotEmpty() &&
                                        uiState.password != uiState.confirmPassword
                                    ) {
                                        "Passwords do not match"
                                    } else {
                                        null
                                    },
                                )
                            } else if (alreadyProtected) {
                                PasswordVisibilityField(
                                    value = uiState.currentPassword,
                                    onValueChange = viewModel::setCurrentPassword,
                                    label = "Current password",
                                    visible = showCurrentPassword,
                                    onToggleVisible = {
                                        showCurrentPassword = !showCurrentPassword
                                    },
                                    supportingText = "Required to create an unlocked copy.",
                                )
                                if (uiState.sourcePagePreview == null) {
                                    TextButton(
                                        onClick = viewModel::unlockPreview,
                                        enabled = uiState.currentPassword.isNotBlank() &&
                                            !uiState.isSourcePagePreviewLoading,
                                    ) {
                                        Text("Unlock preview")
                                    }
                                }
                            } else {
                                Text(
                                    text = "This PDF is not password protected. Choose Protect to add a password, or pick a different file.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when {
                            isProtect && alreadyProtected ->
                                "Creates a re-protected copy using the new password. Your original is unchanged."
                            isProtect ->
                                "Creates a password-protected copy. Your original file is unchanged."
                            alreadyProtected ->
                                "Creates an unlocked copy. Your original file is unchanged."
                            else ->
                                "Remove is only available for password-protected PDFs."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (windowSizeInfo.useToolTwoPaneLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        PdfSelectedDocumentPreview(
                            title = uiState.sources.first().label(),
                            supporting = buildString {
                                val info = uiState.info
                                if (info != null) {
                                    append("${info.pageCount} pages")
                                    info.fileSizeBytes?.let { bytes ->
                                        append(" · ${StorageFormatter.formatBytes(bytes)}")
                                    }
                                    append(if (info.isEncrypted) " · Protected" else " · Unlocked")
                                } else if (uiState.needsPassword) {
                                    append("Password required · Protected")
                                }
                            }.ifBlank { null },
                            preview = uiState.sourcePagePreview,
                            isLoading = uiState.isSourcePagePreviewLoading,
                            caption = "First page of the selected PDF",
                            onChange = { showPicker = true },
                            modifier = Modifier.weight(0.42f),
                        )
                        Column(modifier = Modifier.weight(0.58f)) {
                            modeOptions()
                            Spacer(modifier = Modifier.height(20.dp))
                            passwordForm()
                        }
                    }
                } else {
                    PdfSelectedDocumentPreview(
                        title = uiState.sources.first().label(),
                        supporting = buildString {
                            val info = uiState.info
                            if (info != null) {
                                append("${info.pageCount} pages")
                                info.fileSizeBytes?.let { bytes ->
                                    append(" · ${StorageFormatter.formatBytes(bytes)}")
                                }
                                append(if (info.isEncrypted) " · Protected" else " · Unlocked")
                            } else if (uiState.needsPassword) {
                                append("Password required · Protected")
                            }
                        }.ifBlank { null },
                        preview = uiState.sourcePagePreview,
                        isLoading = uiState.isSourcePagePreviewLoading,
                        caption = "First page of the selected PDF",
                        onChange = { showPicker = true },
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    modeOptions()
                    Spacer(modifier = Modifier.height(20.dp))
                    passwordForm()
                }
            }
        }
    }

    if (showPicker && phase != ToolPhase.Done) {
        PdfSourcePickerSheet(
            documents = uiState.libraryDocuments,
            multiSelect = false,
            onDismiss = { showPicker = false },
            onPickDevice = { devicePicker.launch(arrayOf("application/pdf")) },
            onConfirmLibrary = {
                showPicker = false
                viewModel.onSourcesChosen(it)
            },
        )
    }
}

@Composable
internal fun PasswordVisibilityField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "Toggle $label visibility",
                )
            }
        },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun PasswordModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.64f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
