package `in`.c1ph3rj.scanly.feature.tools.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import `in`.c1ph3rj.scanly.core.ui.WindowSizeInfo
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.feature.camera.CameraPermissionStatus
import `in`.c1ph3rj.scanly.feature.camera.CameraPermissionSupport
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold
import `in`.c1ph3rj.scanly.feature.tools.pdf.shareExportArtifact
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrToolRoute(
    onNavigateUp: () -> Unit,
    initialMode: QrToolMode = QrToolMode.Scan,
) {
    val viewModel: QrToolViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Seed mode from nav only once — do not re-apply on rotation/recomposition.
    LaunchedEffect(viewModel) {
        viewModel.applyInitialMode(initialMode)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is QrToolEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val windowSizeInfo = rememberWindowSizeInfo()
    val layout = rememberQrToolLayout(windowSizeInfo)
    ScanlyDetailScaffold(
        title = "QR Code",
        onNavigateUp = onNavigateUp,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (layout.contentMaxWidth != Dp.Unspecified) {
                            Modifier.widthIn(max = layout.contentMaxWidth)
                        } else {
                            Modifier
                        },
                    )
                    .fillMaxWidth()
                    .padding(horizontal = layout.horizontalPadding)
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val modeSelector: @Composable () -> Unit = {
                    QrModeSelector(
                        mode = uiState.mode,
                        onModeChange = viewModel::setMode,
                        compact = layout.compactModeSelector,
                        maxWidth = layout.modeSelectorMaxWidth,
                    )
                }
                if (!layout.twoPane) {
                    modeSelector()
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = if (layout.twoPane && uiState.mode == QrToolMode.Generate) {
                        Alignment.Center
                    } else {
                        Alignment.TopCenter
                    },
                ) {
                    when (uiState.mode) {
                        QrToolMode.Scan -> QrScanPanel(
                            lastResult = uiState.scanResult,
                            layout = layout,
                            modeSelector = if (layout.twoPane) modeSelector else null,
                            onResult = viewModel::onScanResult,
                            onCopy = {
                                val text = uiState.scanResult ?: return@QrScanPanel
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("QR", text))
                                viewModel.emitCopied()
                            },
                            onOpen = {
                                val text = uiState.scanResult ?: return@QrScanPanel
                                val uri = runCatching { Uri.parse(text) }.getOrNull()
                                if (uri != null && (uri.scheme == "http" || uri.scheme == "https")) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                } else {
                                    viewModel.emitMessage("Not a web URL")
                                }
                            },
                            onClear = viewModel::clearScanResult,
                        )
                        QrToolMode.Generate -> QrGeneratePanel(
                            content = uiState.generateContent,
                            preview = uiState.previewBitmap,
                            isWorking = uiState.isWorking,
                            layout = layout,
                            modeSelector = if (layout.twoPane) modeSelector else null,
                            onContentChange = viewModel::setGenerateContent,
                            onSave = viewModel::saveGenerated,
                            onShare = {
                                viewModel.prepareShare { artifact ->
                                    shareExportArtifact(context, artifact, "QR Code")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private data class QrToolLayout(
    val twoPane: Boolean,
    val horizontalPadding: Dp,
    val contentMaxWidth: Dp,
    val sidePanelMaxWidth: Dp,
    val formMaxWidth: Dp,
    val previewWeight: Float,
    val sideWeight: Float,
    val paneSpacing: Dp,
    val compactModeSelector: Boolean,
    val modeSelectorMaxWidth: Dp,
    val generatePreviewSize: Dp,
)

@Composable
private fun rememberQrToolLayout(windowSizeInfo: WindowSizeInfo): QrToolLayout {
    val twoPane = windowSizeInfo.isLandscape

    return QrToolLayout(
        twoPane = twoPane,
        horizontalPadding = when {
            twoPane && windowSizeInfo.widthClass == WindowWidthClass.Expanded -> 32.dp
            twoPane || windowSizeInfo.isTablet -> windowSizeInfo.horizontalPadding
            else -> 16.dp
        },
        contentMaxWidth = when {
            twoPane && windowSizeInfo.widthClass == WindowWidthClass.Expanded -> 1100.dp
            twoPane -> 960.dp
            windowSizeInfo.isTablet -> windowSizeInfo.toolContentMaxWidth
            else -> Dp.Unspecified
        },
        sidePanelMaxWidth = when {
            windowSizeInfo.widthClass == WindowWidthClass.Expanded -> 360.dp
            else -> 320.dp
        },
        formMaxWidth = when {
            windowSizeInfo.widthClass == WindowWidthClass.Expanded -> 400.dp
            else -> 360.dp
        },
        previewWeight = if (windowSizeInfo.widthClass == WindowWidthClass.Expanded) 0.70f else 0.66f,
        sideWeight = if (windowSizeInfo.widthClass == WindowWidthClass.Expanded) 0.30f else 0.34f,
        paneSpacing = if (windowSizeInfo.widthClass == WindowWidthClass.Expanded) 24.dp else 20.dp,
        compactModeSelector = windowSizeInfo.useCompactLandscapeLayout,
        modeSelectorMaxWidth = if (twoPane) 480.dp else Dp.Unspecified,
        generatePreviewSize = when {
            windowSizeInfo.widthClass == WindowWidthClass.Expanded -> 360.dp
            windowSizeInfo.isTablet -> 320.dp
            else -> 272.dp
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrModeSelector(
    mode: QrToolMode,
    onModeChange: (QrToolMode) -> Unit,
    compact: Boolean,
    maxWidth: Dp,
) {
    val options = listOf(
        QrToolMode.Scan to "Scan",
        QrToolMode.Generate to "Create",
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    maxWidth != Dp.Unspecified -> Modifier.widthIn(max = maxWidth)
                    compact -> Modifier
                    else -> Modifier.widthIn(max = 420.dp)
                },
            ),
    ) {
        options.forEachIndexed { index, (value, label) ->
            val selected = mode == value
            SegmentedButton(
                selected = selected,
                onClick = { onModeChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {
                    Icon(
                        imageVector = if (value == QrToolMode.Scan) {
                            Icons.Filled.CameraAlt
                        } else {
                            Icons.Filled.QrCode2
                        },
                        contentDescription = null,
                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
private fun QrScanPanel(
    lastResult: String?,
    layout: QrToolLayout,
    modeSelector: (@Composable () -> Unit)?,
    onResult: (String) -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    var permissionStatus by remember {
        mutableStateOf(CameraPermissionSupport.resolveStatus(null, context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        CameraPermissionSupport.markRequested(context)
        permissionStatus = CameraPermissionSupport.resolveStatus(null, context)
    }

    if (permissionStatus != CameraPermissionStatus.Granted) {
        QrPermissionCard(
            permissionStatus = permissionStatus,
            onAllow = {
                if (CameraPermissionSupport.shouldOpenSettings(permissionStatus)) {
                    CameraPermissionSupport.openAppSettings(context)
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            maxWidth = if (layout.twoPane) 560.dp else Dp.Unspecified,
        )
        return
    }

    if (layout.twoPane) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(layout.paneSpacing),
        ) {
            QrCameraFrame(
                onResult = onResult,
                modifier = Modifier
                    .weight(layout.previewWeight)
                    .fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .weight(layout.sideWeight)
                    .widthIn(max = layout.sidePanelMaxWidth)
                    .fillMaxHeight(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    modeSelector?.invoke()
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (lastResult != null) {
                            QrScanResultCard(
                                result = lastResult,
                                onCopy = onCopy,
                                onOpen = onOpen,
                                onClear = onClear,
                            )
                        } else {
                            QrScanWaitingCard()
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QrCameraFrame(
                onResult = onResult,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .weight(1f),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .heightIn(min = 80.dp),
            ) {
                if (lastResult != null) {
                    QrScanResultCard(
                        result = lastResult,
                        onCopy = onCopy,
                        onOpen = onOpen,
                        onClear = onClear,
                    )
                } else {
                    QrScanWaitingCard()
                }
            }
        }
    }
}

@Composable
private fun QrPermissionCard(
    permissionStatus: CameraPermissionStatus,
    onAllow: () -> Unit,
    maxWidth: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier
                .then(if (maxWidth != Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Allow camera to scan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Scanly uses the camera only while this screen is open.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                )
                Button(onClick = onAllow) {
                    Text(
                        if (CameraPermissionSupport.shouldOpenSettings(permissionStatus)) {
                            "Open settings"
                        } else {
                            "Allow camera"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QrCameraFrame(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var torchEnabled by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            QrCameraPreview(
                onBarcode = onResult,
                torchEnabled = torchEnabled,
                onTorchAvailabilityChanged = { available ->
                    torchAvailable = available
                    if (!available) torchEnabled = false
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.extraLarge),
            )
            if (torchAvailable) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.large,
                    color = if (torchEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.58f)
                    },
                ) {
                    IconButton(onClick = { torchEnabled = !torchEnabled }) {
                        Icon(
                            imageVector = if (torchEnabled) {
                                Icons.Filled.FlashOn
                            } else {
                                Icons.Filled.FlashOff
                            },
                            contentDescription = if (torchEnabled) {
                                "Turn flashlight off"
                            } else {
                                "Turn flashlight on"
                            },
                            tint = if (torchEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                Color.White
                            },
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
            ) {
                Text(
                    text = "Point the camera at a code",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun QrScanWaitingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.QrCode2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Ready to scan",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "QR and barcodes scan automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QrScanResultCard(
    result: String,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onClear: () -> Unit,
) {
    val isWebLink = result.startsWith("https://", ignoreCase = true) ||
        result.startsWith("http://", ignoreCase = true)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isWebLink) "Link detected" else "Text detected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (isWebLink) "Ready to open or copy" else "Ready to copy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                result,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
            )
            if (isWebLink) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.padding(start = 6.dp))
                        Text("Open link")
                    }
                    FilledTonalButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.padding(start = 6.dp))
                        Text("Copy")
                    }
                }
            } else {
                FilledTonalButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text("Copy text")
                }
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text("Scan another")
            }
        }
    }
}

@Composable
private fun QrGeneratePanel(
    content: String,
    preview: android.graphics.Bitmap?,
    isWorking: Boolean,
    layout: QrToolLayout,
    modeSelector: (@Composable () -> Unit)?,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val formBody: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Create a QR code",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Add a link or short message. The preview updates as you type.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextField(
                value = content,
                onValueChange = onContentChange,
                label = { Text("Link or message") },
                placeholder = { Text("https://example.com") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            Text(
                "PNG · high contrast · ready to scan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onSave,
                    enabled = content.isNotBlank() && !isWorking,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isWorking) "Preparing…" else "Save PNG")
                }
                FilledTonalButton(
                    onClick = onShare,
                    enabled = content.isNotBlank() && !isWorking,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.padding(start = 6.dp))
                    Text("Share")
                }
            }
        }
    }

    @Composable
    fun PreviewCard(modifier: Modifier) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.White,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 1.dp,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "QR code preview",
                        modifier = Modifier
                            .padding(28.dp)
                            .fillMaxSize(),
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(20.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCode2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(44.dp),
                        )
                        Text(
                            if (content.isBlank()) {
                                "Preview appears as you type"
                            } else {
                                "Generating preview…"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }

    if (layout.twoPane) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(layout.paneSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(layout.previewWeight)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                PreviewCard(Modifier.size(layout.generatePreviewSize))
            }
            Box(
                modifier = Modifier
                    .weight(layout.sideWeight)
                    .widthIn(max = layout.formMaxWidth)
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    modeSelector?.invoke()
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp),
                    ) {
                        formBody()
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreviewCard(
                Modifier
                    .fillMaxWidth(0.74f)
                    .widthIn(max = layout.generatePreviewSize)
                    .aspectRatio(1f),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = layout.formMaxWidth),
            ) {
                formBody()
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
private fun QrCameraPreview(
    onBarcode: (String) -> Unit,
    torchEnabled: Boolean,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val handled = remember { AtomicBoolean(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_EAN_13,
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            boundCamera?.cameraControl?.enableTorch(false)
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    LaunchedEffect(boundCamera, torchEnabled) {
        val camera = boundCamera ?: return@LaunchedEffect
        if (camera.cameraInfo.hasFlashUnit()) {
            camera.cameraControl.enableTorch(torchEnabled)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(1280, 720),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                    ),
                                )
                                .build(),
                        )
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees,
                        )
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val value = barcodes.firstOrNull()?.rawValue
                                if (!value.isNullOrBlank() && handled.compareAndSet(false, true)) {
                                    onBarcode(value)
                                    previewView.postDelayed({ handled.set(false) }, 2_000L)
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                    try {
                        provider.unbindAll()
                        val camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                        boundCamera = camera
                        onTorchAvailabilityChanged(camera.cameraInfo.hasFlashUnit())
                    } catch (_: Exception) {
                        boundCamera = null
                        onTorchAvailabilityChanged(false)
                        // Camera may be unavailable; leave empty preview.
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
    )
}
