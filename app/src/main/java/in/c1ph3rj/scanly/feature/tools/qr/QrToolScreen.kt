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
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

    LaunchedEffect(initialMode) {
        viewModel.setMode(initialMode)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is QrToolEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    ScanlyDetailScaffold(
        title = "QR Code",
        onNavigateUp = onNavigateUp,
        actions = {
            IconButton(
                onClick = {
                    viewModel.setMode(
                        if (uiState.mode == QrToolMode.Scan) QrToolMode.Generate else QrToolMode.Scan,
                    )
                },
            ) {
                Icon(
                    imageVector = if (uiState.mode == QrToolMode.Scan) {
                        Icons.Filled.QrCode2
                    } else {
                        Icons.Filled.CameraAlt
                    },
                    contentDescription = if (uiState.mode == QrToolMode.Scan) {
                        "Generate QR code"
                    } else {
                        "Scan a code"
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            QrFlowHeading(
                mode = uiState.mode,
            )
            Spacer(modifier = Modifier.height(20.dp))
            when (uiState.mode) {
                QrToolMode.Scan -> QrScanPanel(
                    lastResult = uiState.scanResult,
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

@Composable
private fun QrFlowHeading(
    mode: QrToolMode,
) {
    val isScanning = mode == QrToolMode.Scan
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isScanning) Icons.Filled.CameraAlt else Icons.Filled.QrCode2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.padding(start = 12.dp))
            Column {
            Text(
                text = if (isScanning) "SCAN A CODE" else "CREATE A CODE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isScanning) {
                    "Point the camera at a QR code or barcode."
                } else {
                    "Turn a link or short message into a shareable QR image."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
        }
    }
}

@Composable
private fun QrScanPanel(
    lastResult: String?,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (permissionStatus != CameraPermissionStatus.Granted) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                    Button(
                        onClick = {
                            if (CameraPermissionSupport.shouldOpenSettings(permissionStatus)) {
                                CameraPermissionSupport.openAppSettings(context)
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                    ) {
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
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    QrCameraPreview(
                        onBarcode = onResult,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.extraLarge),
                    )
                    Text(
                        text = "ALIGN THE CODE INSIDE THE FRAME",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    )
                }
            }
            if (lastResult != null) {
                QrScanResultCard(
                    result = lastResult,
                    onCopy = onCopy,
                    onOpen = onOpen,
                    onClear = onClear,
                )
            } else {
                Text(
                    "Supports QR, Aztec, Data Matrix, PDF417, Code 128, and EAN-13.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
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
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
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
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "CONTENT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
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
                minLines = 1,
                maxLines = 2,
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .aspectRatio(1f),
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.White,
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (preview != null) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "QR code preview",
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxSize(),
                    )
                } else {
                    Text(
                        "Your QR code will appear here",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        Text(
            "PNG · high contrast · ready to share",
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
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
private fun QrCameraPreview(
    onBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val handled = remember { AtomicBoolean(false) }
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
            analysisExecutor.shutdown()
            scanner.close()
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
                    val cameraProvider = cameraProviderFuture.get()
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
                                    // Allow rescans after a short debounce via clear
                                    previewView.postDelayed({ handled.set(false) }, 2_000L)
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    } catch (_: Exception) {
                        // Camera bind failures surface as empty preview.
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
    )
}
