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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.ZoomableBitmapViewer
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageState
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
fun PdfMergeRoute(
    onNavigateUp: () -> Unit,
    onPreviewPdf: (ExportArtifact) -> Unit,
) {
    val viewModel: PdfMergeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(true) }
    var appendMode by remember { mutableStateOf(false) }
    val phase = toolPhase(uiState.sources, uiState.result)

    val devicePicker = rememberMultiPdfPicker { uris ->
        showPicker = false
        val sources = uris.map { uri ->
            PdfToolSource.DeviceUri(uri.toString(), uriDisplayName(context, uri))
        }
        if (appendMode) viewModel.appendDeviceUris(uris, sources.map { it.displayName })
        else viewModel.setSources(sources)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest {
            if (it is PdfToolEvent.ShowMessage) snackbarHostState.showSnackbar(it.message)
        }
    }

    ToolDetailScaffold(
        title = "Merge PDFs",
        onNavigateUp = onNavigateUp,
        snackbarHostState = snackbarHostState,
        isProcessing = uiState.isProcessing,
        progressMessage = uiState.progressMessage,
        primaryAction = if (phase == ToolPhase.Ready) {
            {
                Button(
                    onClick = viewModel::runMerge,
                    enabled = uiState.sources.size >= 2 && !uiState.isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Merge PDFs") }
            }
        } else null,
    ) {
        when (phase) {
            ToolPhase.Empty -> ToolEmptyState(
                icon = Icons.AutoMirrored.Filled.MergeType,
                title = "Merge PDFs",
                subtitle = "Select two or more PDFs from your device or Scanly library.",
                actionLabel = "Choose PDFs",
                onChooseSource = {
                    appendMode = false
                    showPicker = true
                },
            )
            ToolPhase.Done -> {
                uiState.result?.let { result ->
                    PdfToolCompleteScreen(
                        fileName = result.fileName,
                        statusTitle = "Merge complete",
                        statusSubtitle = "Your combined PDF is ready.",
                        previewHint = "Preview the finished file before saving or sharing it.",
                        onPreview = { onPreviewPdf(result) },
                        onSave = viewModel::saveResult,
                        onShare = {
                            shareExportArtifact(context, result, "Merged PDF")
                        },
                        onBack = onNavigateUp,
                    )
                }
            }
            ToolPhase.Ready -> {
                ToolSourceList(
                    sources = uiState.sources,
                    onAdd = {
                        appendMode = true
                        showPicker = true
                    },
                    onRemove = viewModel::removeSource,
                )
                if (uiState.sources.size < 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add at least one more PDF to merge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showPicker && phase != ToolPhase.Done) {
        PdfSourcePickerSheet(
            documents = uiState.libraryDocuments,
            multiSelect = true,
            onDismiss = { showPicker = false },
            onPickDevice = { devicePicker.launch(arrayOf("application/pdf")) },
            onConfirmLibrary = { docs ->
                showPicker = false
                if (appendMode) viewModel.appendLibrary(docs) else viewModel.setSources(docs)
            },
        )
    }
}

@Composable
fun PdfCompressRoute(
    onNavigateUp: () -> Unit,
    onPreviewPdf: (ExportArtifact) -> Unit,
) {
    val viewModel: PdfCompressViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(true) }
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

    val before = uiState.originalSizeBytes
    val after = uiState.compressedSizeBytes
    val savedPercent = if (before != null && after != null && before > 0L) {
        ((before - after).toFloat() / before.toFloat() * 100f)
    } else null

    ToolDetailScaffold(
        title = "Compress PDF",
        onNavigateUp = onNavigateUp,
        snackbarHostState = snackbarHostState,
        isProcessing = uiState.isProcessing,
        progressMessage = uiState.progressMessage,
        primaryAction = if (phase == ToolPhase.Ready) {
            {
                Button(
                    onClick = viewModel::runCompress,
                    enabled = !uiState.isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Compress PDF") }
            }
        } else null,
    ) {
        when (phase) {
            ToolPhase.Empty -> ToolEmptyState(
                icon = Icons.Filled.Compress,
                title = "Compress a PDF",
                subtitle = "Reduce file size by re-encoding pages. Choose quality next.",
                onChooseSource = { showPicker = true },
            )
            ToolPhase.Done -> {
                uiState.result?.let { result ->
                    PdfToolCompleteScreen(
                        fileName = result.fileName,
                        statusTitle = "Compression complete",
                        statusSubtitle = "Your compressed PDF is ready.",
                        previewHint = "Preview the finished file before saving or sharing it.",
                        resultDetail = sizeComparisonLabel(before, after, savedPercent),
                        onPreview = { onPreviewPdf(result) },
                        onSave = viewModel::saveResult,
                        onShare = {
                            shareExportArtifact(context, result, "Compressed PDF")
                        },
                        onBack = onNavigateUp,
                    )
                }
            }
            ToolPhase.Ready -> {
                PdfSelectedDocumentPreview(
                    title = uiState.sources.first().label(),
                    supporting = buildString {
                        val info = uiState.info
                        if (info != null) {
                            append("${info.pageCount} pages")
                            (before ?: info.fileSizeBytes)?.let { bytes ->
                                append(" · ${StorageFormatter.formatBytes(bytes)}")
                            }
                        } else {
                            before?.let { bytes -> append(StorageFormatter.formatBytes(bytes)) }
                        }
                    }.ifBlank { null },
                    preview = uiState.sourcePagePreview,
                    isLoading = uiState.isSourcePagePreviewLoading,
                    caption = "First page of the selected PDF",
                    onChange = { showPicker = true },
                )
                if (uiState.needsPassword || uiState.info?.isEncrypted == true) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = uiState.currentPassword,
                        onValueChange = viewModel::setCurrentPassword,
                        label = { Text("PDF password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = viewModel::unlockOrRefresh) {
                        Text("Unlock / refresh preview")
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Compression level",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                PdfCompressQuality.entries.forEach { quality ->
                    QualityOptionCard(
                        quality = quality,
                        selected = uiState.compressQuality == quality,
                        recommended = quality == PdfCompressQuality.MEDIUM,
                        onClick = { viewModel.setQuality(quality) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = "Pages are re-encoded as images. Text may look slightly softer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun PdfSelectedDocumentPreview(
    title: String,
    supporting: String?,
    preview: Bitmap?,
    isLoading: Boolean,
    onChange: () -> Unit,
    caption: String = "First page of the selected PDF",
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SELECTED PDF",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onChange) { Text("Change") }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    preview != null -> Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "First page preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                    )
                    isLoading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    else -> Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    )
                }
                if (isLoading && preview != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QualityOptionCard(
    quality: PdfCompressQuality,
    selected: Boolean,
    recommended: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(quality.label, fontWeight = FontWeight.SemiBold)
                    if (recommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recommended",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = quality.description,
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

private fun sizeComparisonLabel(before: Long?, after: Long?, savedPercent: Float?): String? {
    if (before == null || after == null) return null
    return if (after < before && savedPercent != null) {
        "${StorageFormatter.formatBytes(before)} → ${StorageFormatter.formatBytes(after)} · saved ${savedPercent.toInt()}%"
    } else {
        "${StorageFormatter.formatBytes(before)} → ${StorageFormatter.formatBytes(after)}"
    }
}

@Composable
fun PdfPasswordRoute(
    onNavigateUp: () -> Unit,
    onPreviewPdf: (ExportArtifact) -> Unit,
) {
    val viewModel: PdfPasswordViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(true) }
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
                Text(
                    text = "ACTION",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
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

                Spacer(modifier = Modifier.height(20.dp))
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
private fun PasswordVisibilityField(
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
private fun PasswordModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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

@Composable
fun PdfWatermarkRoute(
    onNavigateUp: () -> Unit,
    onPreviewPdf: (ExportArtifact) -> Unit,
) {
    val viewModel: PdfWatermarkViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(true) }
    var showPassword by remember { mutableStateOf(false) }
    val phase = toolPhase(uiState.sources, uiState.result)

    val devicePicker = rememberSinglePdfPicker { uri ->
        showPicker = false
        viewModel.setSources(
            listOf(PdfToolSource.DeviceUri(uri.toString(), uriDisplayName(context, uri))),
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest {
            if (it is PdfToolEvent.ShowMessage) snackbarHostState.showSnackbar(it.message)
        }
    }

    ToolDetailScaffold(
        title = "Watermark",
        onNavigateUp = onNavigateUp,
        snackbarHostState = snackbarHostState,
        isProcessing = uiState.isProcessing,
        progressMessage = uiState.progressMessage,
        primaryAction = if (phase == ToolPhase.Ready) {
            {
                Button(
                    onClick = viewModel::run,
                    enabled = uiState.watermarkText.isNotBlank() && !uiState.isProcessing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Apply watermark") }
            }
        } else null,
    ) {
        when (phase) {
            ToolPhase.Empty -> ToolEmptyState(
                icon = Icons.Filled.WaterDrop,
                title = "Stamp text on every page",
                subtitle = "Add a full-page watermark (tiled or single large mark) to a PDF from your device or library.",
                onChooseSource = { showPicker = true },
            )
            ToolPhase.Done -> {
                uiState.result?.let { result ->
                    PdfToolCompleteScreen(
                        fileName = result.fileName,
                        statusTitle = "Watermark applied",
                        statusSubtitle = "Your updated PDF is ready.",
                        previewHint = "Preview the stamped file before saving or sharing it.",
                        onPreview = { onPreviewPdf(result) },
                        onSave = viewModel::saveResult,
                        onShare = { shareExportArtifact(context, result, "Watermarked PDF") },
                        onBack = onNavigateUp,
                    )
                }
            }
            ToolPhase.Ready -> {
                WatermarkDocumentPreview(
                    title = uiState.sources.first().label(),
                    preview = uiState.watermarkPreview,
                    isLoading = uiState.isWatermarkPreviewLoading,
                    onChange = { showPicker = true },
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "WATERMARK",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.watermarkText,
                    onValueChange = viewModel::setWatermarkText,
                    label = { Text("Text") },
                    placeholder = { Text("CONFIDENTIAL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(20.dp))
                WatermarkChoiceRow(
                    title = "Layout",
                    options = listOf(
                        WatermarkLayout.REPEATED to "Tiled",
                        WatermarkLayout.CENTERED to "Single",
                    ),
                    selected = uiState.watermarkLayout,
                    onSelected = viewModel::setLayout,
                )
                Text(
                    text = when (uiState.watermarkLayout) {
                        WatermarkLayout.REPEATED ->
                            "Dense field that covers the full page, including edges."
                        WatermarkLayout.CENTERED ->
                            "One large stamp sized to dominate the page."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                WatermarkChoiceRow(
                    title = "Size",
                    options = listOf(
                        WatermarkSize.SMALL to "Small",
                        WatermarkSize.MEDIUM to "Medium",
                        WatermarkSize.LARGE to "Large",
                    ),
                    selected = uiState.watermarkSize,
                    onSelected = viewModel::setSize,
                )
                Spacer(modifier = Modifier.height(16.dp))
                WatermarkChoiceRow(
                    title = "Apply to",
                    options = listOf(
                        WatermarkPageRange.FIRST_PAGE to "First page",
                        WatermarkPageRange.ALL_PAGES to "All pages",
                    ),
                    selected = uiState.watermarkPageRange,
                    onSelected = viewModel::setPageRange,
                )
                Spacer(modifier = Modifier.height(16.dp))
                WatermarkChoiceRow(
                    title = "Orientation",
                    options = listOf(
                        WatermarkOrientation.DIAGONAL to "Diagonal",
                        WatermarkOrientation.HORIZONTAL to "Horizontal",
                    ),
                    selected = uiState.watermarkOrientation,
                    onSelected = viewModel::setOrientation,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Opacity ${(uiState.watermarkOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Slider(
                    value = uiState.watermarkOpacity,
                    onValueChange = viewModel::setOpacity,
                    valueRange = 0.08f..0.55f,
                )
                Text(
                    text = "Lower opacity keeps the document readable under the stamp.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "Hide password" else "PDF password (optional)")
                }
                if (showPassword) {
                    OutlinedTextField(
                        value = uiState.currentPassword,
                        onValueChange = viewModel::setCurrentPassword,
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                viewModel.setSources(it)
            },
        )
    }
}

@Composable
private fun WatermarkDocumentPreview(
    title: String,
    preview: Bitmap?,
    isLoading: Boolean,
    onChange: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SELECTED PDF",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onChange) { Text("Change") }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    preview != null -> Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "First page preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    isLoading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    else -> Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    )
                }
                if (isLoading && preview != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
        Text(
            text = "Exact first-page preview of the exported PDF",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun <T> WatermarkChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
fun PdfReaderRoute(
    onNavigateUp: () -> Unit,
    initialSource: PdfToolSource? = null,
) {
    val viewModel: PdfReaderViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPicker by remember(initialSource) { mutableStateOf(initialSource == null) }
    var showMenu by remember { mutableStateOf(false) }

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

    LaunchedEffect(initialSource) {
        if (initialSource != null && uiState.source != initialSource) {
            showPicker = false
            viewModel.onSourcesChosen(listOf(initialSource))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (uiState.phase) {
            PdfReaderPhase.Empty -> PdfReaderEmptyState(
                onChooseSource = { showPicker = true },
                onNavigateUp = onNavigateUp,
            )
            PdfReaderPhase.Opening -> PdfReaderOpeningState()
            PdfReaderPhase.Locked -> PdfReaderPasswordOverlay(
                title = uiState.documentTitle.ifBlank { "Protected PDF" },
                password = uiState.passwordDraft,
                error = uiState.passwordError,
                onPasswordChange = viewModel::setPasswordDraft,
                onUnlock = viewModel::unlock,
                onChooseAnother = {
                    viewModel.clearDocument()
                    showPicker = true
                },
                onNavigateUp = onNavigateUp,
            )
            PdfReaderPhase.Ready -> PdfReaderViewer(
                uiState = uiState,
                onNavigateUp = onNavigateUp,
                onPageChange = viewModel::goToPage,
                onToggleChrome = viewModel::toggleChrome,
                onToggleReaderLayout = viewModel::toggleReaderLayout,
                onOpenAnother = { showPicker = true },
                showMenu = showMenu,
                onShowMenuChange = { showMenu = it },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showPicker) {
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
private fun PdfReaderOpeningState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text("Opening PDF…")
        }
    }
}

@Composable
private fun PdfReaderEmptyState(
    onChooseSource: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            ChromeIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateUp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Text(
                text = "Read a PDF",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Open a file from your device or turn a Scanly document into a PDF here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onChooseSource, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose PDF")
            }
        }
    }
}

@Composable
private fun PdfReaderPasswordOverlay(
    title: String,
    password: String,
    error: String?,
    onPasswordChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onChooseAnother: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            ChromeIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateUp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "PROTECTED PDF",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Enter the password to unlock this PDF.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onChooseAnother) { Text("Choose another") }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onUnlock, enabled = password.isNotBlank()) {
                        Text("Unlock")
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfReaderViewer(
    uiState: PdfReaderUiState,
    onNavigateUp: () -> Unit,
    onPageChange: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    onToggleReaderLayout: () -> Unit,
    onOpenAnother: () -> Unit,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = uiState.currentPageIndex.coerceAtLeast(0),
        pageCount = { uiState.pageCount.coerceAtLeast(1) },
    )
    var zoomActive by remember { mutableStateOf(false) }
    val zoomStates = remember(uiState.pageCount) {
        (0 until uiState.pageCount).associateWith { ZoomableImageState() }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { onPageChange(it) }
    }
    LaunchedEffect(uiState.currentPageIndex) {
        if (pagerState.currentPage != uiState.currentPageIndex &&
            uiState.currentPageIndex in 0 until uiState.pageCount
        ) {
            pagerState.scrollToPage(uiState.currentPageIndex)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.pageCount > 0 && uiState.readerLayout == PdfReaderLayout.Paged) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !zoomActive,
                    key = { it },
                ) { pageIndex ->
                    val bitmap = uiState.pageBitmaps[pageIndex]
                    val zoomState = zoomStates[pageIndex] ?: rememberZoomableImageState(pageIndex)
                    if (bitmap != null) {
                        ZoomableBitmapViewer(
                            imageBitmap = bitmap.asImageBitmap(),
                            state = zoomState,
                            allowParentHorizontalGestures = true,
                            onSingleTap = onToggleChrome,
                            onZoomActiveChange = { active ->
                                if (pageIndex == pagerState.settledPage) {
                                    zoomActive = active
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { onToggleChrome() })
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (pageIndex in uiState.loadingPages) {
                                CircularProgressIndicator()
                            } else {
                                Text(
                                    "Loading page…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (uiState.pageCount > 0 && uiState.readerLayout == PdfReaderLayout.Continuous) {
                PdfReaderContinuousViewer(
                    uiState = uiState,
                    onPageChange = onPageChange,
                    onToggleChrome = onToggleChrome,
                )
            }

            AnimatedVisibility(
                visible = uiState.chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        ChromeIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            onClick = onNavigateUp,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        MetricChip(
                            label = buildString {
                                val title = uiState.documentTitle
                                if (title.isNotBlank()) {
                                    append(title.take(22))
                                    if (title.length > 22) append('…')
                                    append(" · ")
                                }
                                append("${uiState.currentPageIndex + 1}/${uiState.pageCount}")
                            },
                            containerColor = Color.Black.copy(alpha = 0.42f),
                            contentColor = Color.White,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        )
                    }
                    Row {
                        if (zoomActive) {
                            ChromeIconButton(
                                icon = Icons.Filled.FitScreen,
                                contentDescription = "Reset zoom",
                                onClick = {
                                    zoomStates[pagerState.settledPage]?.reset()
                                    zoomActive = false
                                },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Box {
                            ChromeIconButton(
                                icon = Icons.Filled.MoreVert,
                                contentDescription = "More",
                                onClick = { onShowMenuChange(true) },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            )
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { onShowMenuChange(false) },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.readerLayout == PdfReaderLayout.Paged) {
                                                "Continuous scroll"
                                            } else {
                                                "Page by page"
                                            },
                                        )
                                    },
                                    onClick = {
                                        onShowMenuChange(false)
                                        onToggleReaderLayout()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Open another PDF") },
                                    onClick = {
                                        onShowMenuChange(false)
                                        onOpenAnother()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun PdfReaderContinuousViewer(
    uiState: PdfReaderUiState,
    onPageChange: (Int) -> Unit,
    onToggleChrome: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect(onPageChange)
    }
    LaunchedEffect(uiState.currentPageIndex) {
        if (uiState.currentPageIndex in 0 until uiState.pageCount &&
            listState.firstVisibleItemIndex != uiState.currentPageIndex
        ) {
            listState.scrollToItem(uiState.currentPageIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 96.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = uiState.pageCount,
            key = { it },
        ) { pageIndex ->
            val bitmap = uiState.pageBitmaps[pageIndex]
            if (bitmap != null) {
                val zoomState = rememberZoomableImageState("continuous-$pageIndex")
                ZoomableBitmapViewer(
                    imageBitmap = bitmap.asImageBitmap(),
                    state = zoomState,
                    allowParentHorizontalGestures = true,
                    onSingleTap = onToggleChrome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .pointerInput(pageIndex) {
                            detectTapGestures(onTap = { onToggleChrome() })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (pageIndex in uiState.loadingPages) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            "Loading page ${pageIndex + 1}…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
