package `in`.c1ph3rj.scanly.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import `in`.c1ph3rj.scanly.core.ml.DetectionFrame
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.feature.home.DocumentThumbnail
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

object ScanSessionDestination {
    const val documentIdArgument = "documentId"
    const val replacePageIdArgument = "replacePageId"
    const val routePattern = "camera/session/{$documentIdArgument}?$replacePageIdArgument={$replacePageIdArgument}"

    fun route(
        documentId: String,
        replacePageId: String? = null,
    ): String = buildString {
        append("camera/session/")
        append(documentId)
        if (replacePageId != null) {
            append("?")
            append(replacePageIdArgument)
            append("=")
            append(replacePageId)
        }
    }
}

@Composable
fun ScanSessionRoute(
    onNavigateUp: () -> Unit,
    viewModel: ScanSessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    var hasCameraPermission by remember(context) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(viewModel, imageCapture, previewView, hasCameraPermission) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ScanSessionEvent.PerformCapture -> {
                    val currentImageCapture = imageCapture
                    val currentPreviewView = previewView
                    if (!hasCameraPermission || currentImageCapture == null || currentPreviewView == null) {
                        viewModel.onCaptureFailed("Camera is not ready yet.")
                    } else {
                        capturePage(
                            draft = event.draft,
                            imageCapture = currentImageCapture,
                            previewView = currentPreviewView,
                            mainExecutor = mainExecutor,
                            onSaved = { viewModel.onCaptureSaved(event.draft) },
                            onFailure = { message -> viewModel.onCaptureFailed(message) },
                        )
                    }
                }

                is ScanSessionEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                ScanSessionEvent.NavigateUp -> onNavigateUp()
            }
        }
    }

    ScanSessionScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        hasCameraPermission = hasCameraPermission,
        onRequestPermission = {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onNavigateUp = onNavigateUp,
        onCapture = viewModel::requestCapture,
        onAutoCaptureEnabledChange = viewModel::onAutoCaptureEnabledChanged,
        onPreviewFrame = viewModel::onPreviewFrame,
        onCameraReady = { captureUseCase, cameraPreview ->
            imageCapture = captureUseCase
            previewView = cameraPreview
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ScanSessionScreen(
    uiState: ScanSessionUiState,
    snackbarHostState: SnackbarHostState,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigateUp: () -> Unit,
    onCapture: () -> Unit,
    onAutoCaptureEnabledChange: (Boolean) -> Unit,
    onPreviewFrame: (DetectionFrame) -> Unit,
    onCameraReady: (ImageCapture, PreviewView) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isReplacementMode) {
                            uiState.replacementPage?.let { page -> "Replace Page ${page.pageIndex + 1}" } ?: "Replace page"
                        } else {
                            uiState.document?.title ?: "Scan session"
                        },
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateUp) {
                        Text(text = "Back")
                    }
                },
                actions = {
                    if (uiState.isReplacementMode) {
                        Text(
                            text = "Retake mode",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    } else {
                        Text(
                            text = "${uiState.pages.size} pages",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        if (uiState.missingDocument) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                InfoCard(
                    title = "Document not found",
                    body = "Return to the library and reopen a valid document before capturing pages.",
                )
            }
        } else if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                InfoCard(
                    title = "Camera permission needed",
                    body = "Scanly needs camera access to capture document pages into the current document.",
                )
                Button(onClick = onRequestPermission) {
                    Text(text = "Grant camera access")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                CameraPreview(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    liveDetection = uiState.liveDetection,
                    onCameraReady = onCameraReady,
                    onPreviewFrame = onPreviewFrame,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (uiState.isReplacementMode) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = uiState.replacementPage?.let { page ->
                                            "Replacing Page ${page.pageIndex + 1}"
                                        } ?: "Replacing selected page",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = "This capture will overwrite the selected page and keep it in the same place in the document.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Auto-capture",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = uiState.liveDetection.statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = uiState.liveDetection.autoCaptureEnabled,
                                onCheckedChange = onAutoCaptureEnabledChange,
                            )
                        }
                        DetectionMeta(uiState.liveDetection)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Button(
                                onClick = onCapture,
                                enabled = !uiState.captureInProgress,
                            ) {
                                Text(
                                    text = when {
                                        uiState.captureInProgress -> "Saving..."
                                        uiState.isReplacementMode -> "Replace page"
                                        else -> "Capture page"
                                    },
                                )
                            }
                        }
                        if (uiState.pages.isEmpty()) {
                            Text(
                                text = "Captured pages will appear here as a quick review strip.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                items(
                                    items = uiState.pages,
                                    key = { page -> page.id },
                                ) { page ->
                                    Column(
                                        modifier = Modifier.size(width = 120.dp, height = 150.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        DocumentThumbnail(
                                            thumbnailPath = page.thumbnailPath,
                                            title = "Page ${page.pageIndex + 1}",
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Text(
                                            text = "Page ${page.pageIndex + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (uiState.replacementPageId == page.id) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
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
    }
}

@Composable
private fun DetectionMeta(
    liveDetection: LiveDetectionUiState,
) {
    if (liveDetection.confidence == null || liveDetection.inferenceTimeMillis == null) {
        return
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = "Detection ${(liveDetection.confidence * 100f).roundToInt()}% • ${liveDetection.inferenceTimeMillis} ms",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@Suppress("DEPRECATION")
private fun CameraPreview(
    modifier: Modifier = Modifier,
    liveDetection: LiveDetectionUiState,
    onCameraReady: (ImageCapture, PreviewView) -> Unit,
    onPreviewFrame: (DetectionFrame) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(analysisExecutor) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val currentPreviewView = previewView
        if (currentPreviewView == null) {
            onDispose { }
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val listener = Runnable {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also { useCase ->
                        useCase.surfaceProvider = currentPreviewView.surfaceProvider
                    }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                    .apply {
                        targetRotation = currentPreviewView.display.rotation
                    }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .apply {
                        targetRotation = currentPreviewView.display.rotation
                        setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                imageProxy.toDetectionFrame()?.let(onPreviewFrame)
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    imageAnalysis,
                )
                onCameraReady(imageCapture, currentPreviewView)
            }

            cameraProviderFuture.addListener(listener, mainExecutor)
            onDispose {
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
            }
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { androidContext ->
                PreviewView(androidContext).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                }
            },
            update = { view ->
                previewView = view
            },
        )
        DocumentDetectionOverlay(
            liveDetection = liveDetection,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DocumentDetectionOverlay(
    liveDetection: LiveDetectionUiState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridStrokeWidth = 1.dp.toPx()
            val thirdWidth = size.width / 3f
            val thirdHeight = size.height / 3f

            for (index in 1..2) {
                val x = thirdWidth * index
                drawLine(
                    color = OverlayGrid,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = gridStrokeWidth,
                )
            }

            for (index in 1..2) {
                val y = thirdHeight * index
                drawLine(
                    color = OverlayGrid,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = gridStrokeWidth,
                )
            }

            drawLine(
                color = OverlayGuide,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.5.dp.toPx(),
            )

            if (!liveDetection.hasOverlay) {
                return@Canvas
            }

            val mappedPoints = liveDetection.quad
                ?.mapToOverlay(
                    overlaySize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    sourceFrame = DetectionOverlayFrame(
                        width = liveDetection.frameWidth,
                        height = liveDetection.frameHeight,
                    ),
                )
                .orEmpty()

            if (mappedPoints.size < 4) {
                return@Canvas
            }

            val topMidpoint = Offset(
                x = (mappedPoints[0].x + mappedPoints[1].x) / 2f,
                y = (mappedPoints[0].y + mappedPoints[1].y) / 2f,
            )
            val bottomMidpoint = Offset(
                x = (mappedPoints[2].x + mappedPoints[3].x) / 2f,
                y = (mappedPoints[2].y + mappedPoints[3].y) / 2f,
            )
            val center = Offset(
                x = mappedPoints.map { it.x }.average().toFloat(),
                y = mappedPoints.map { it.y }.average().toFloat(),
            )

            val polygonPath = Path().apply {
                moveTo(mappedPoints[0].x, mappedPoints[0].y)
                for (index in 1 until mappedPoints.size) {
                    lineTo(mappedPoints[index].x, mappedPoints[index].y)
                }
                close()
            }

            drawPath(
                path = polygonPath,
                color = OverlayFill,
            )
            drawPath(
                path = polygonPath,
                color = OverlayBlue,
                style = Stroke(
                    width = 2.dp.toPx(),
                ),
            )
            drawLine(
                color = OverlayBlue.copy(alpha = 0.7f),
                start = topMidpoint,
                end = bottomMidpoint,
                strokeWidth = 1.dp.toPx(),
            )
            drawCircle(
                color = OverlayGuide.copy(alpha = 0.9f),
                radius = 10.dp.toPx(),
                center = center,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = 3.5.dp.toPx(),
                center = center,
            )
        }

        PreviewStatusHud(
            liveDetection = liveDetection,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp),
        )

        if (liveDetection.autoCaptureEnabled && liveDetection.countdownValue != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp)
                    .size(width = 104.dp, height = 52.dp),
                color = Color.Black.copy(alpha = 0.5f),
                shape = CircleShape,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = liveDetection.countdownValue.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = OverlayBlue,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewStatusHud(
    liveDetection: LiveDetectionUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.42f),
        shape = CircleShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(
                    color = when (liveDetection.phase) {
                        AutoCapturePhase.COUNTDOWN,
                        AutoCapturePhase.CAPTURING -> OverlayBlue
                        AutoCapturePhase.HOLD_STEADY -> OverlayGuide
                        AutoCapturePhase.OFF -> Color.White.copy(alpha = 0.7f)
                        else -> Color.White.copy(alpha = 0.45f)
                    },
                )
            }
            Text(
                text = liveDetection.statusMessage,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun capturePage(
    draft: PageCaptureDraft,
    imageCapture: ImageCapture,
    previewView: PreviewView,
    mainExecutor: Executor,
    onSaved: () -> Unit,
    onFailure: (String) -> Unit,
) {
    imageCapture.targetRotation = previewView.display.rotation
    val outputFile = File(draft.rawImagePath)
    outputFile.parentFile?.mkdirs()
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

    imageCapture.takePicture(
        outputOptions,
        mainExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSaved()
            }

            override fun onError(exception: ImageCaptureException) {
                onFailure(exception.message ?: "Capture failed.")
            }
        },
    )
}

private fun DocumentCornerQuad.mapToOverlay(
    overlaySize: IntSize,
    sourceFrame: DetectionOverlayFrame,
): List<Offset> {
    val scale = max(
        overlaySize.width / sourceFrame.width.toFloat(),
        overlaySize.height / sourceFrame.height.toFloat(),
    )
    val scaledWidth = sourceFrame.width * scale
    val scaledHeight = sourceFrame.height * scale
    val offsetX = (overlaySize.width - scaledWidth) / 2f
    val offsetY = (overlaySize.height - scaledHeight) / 2f

    return orderedPoints().map { (_, point) ->
        point.toOverlayOffset(
            sourceWidth = sourceFrame.width,
            sourceHeight = sourceFrame.height,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
        )
    }
}

private fun NormalizedPoint.toOverlayOffset(
    sourceWidth: Int,
    sourceHeight: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
): Offset = Offset(
    x = offsetX + (x * sourceWidth * scale),
    y = offsetY + (y * sourceHeight * scale),
)

private fun ImageProxy.toDetectionFrame(): DetectionFrame? {
    val rgbaPlane = planes.firstOrNull() ?: return null
    if (rgbaPlane.pixelStride < 4) {
        return null
    }

    val packedBytes = ByteArray(width * height * RgbaPixelStride)
    val buffer = rgbaPlane.buffer
    buffer.rewind()
    val rowBuffer = ByteArray(rgbaPlane.rowStride)

    for (rowIndex in 0 until height) {
        val readableBytes = minOf(rgbaPlane.rowStride, buffer.remaining())
        buffer.get(rowBuffer, 0, readableBytes)
        for (columnIndex in 0 until width) {
            val sourceIndex = columnIndex * rgbaPlane.pixelStride
            if (sourceIndex + 3 >= readableBytes) {
                break
            }

            val destinationIndex = ((rowIndex * width) + columnIndex) * RgbaPixelStride
            packedBytes[destinationIndex] = rowBuffer[sourceIndex]
            packedBytes[destinationIndex + 1] = rowBuffer[sourceIndex + 1]
            packedBytes[destinationIndex + 2] = rowBuffer[sourceIndex + 2]
            packedBytes[destinationIndex + 3] = rowBuffer[sourceIndex + 3]
        }
    }

    return DetectionFrame(
        width = width,
        height = height,
        rotationDegrees = imageInfo.rotationDegrees,
        bytes = packedBytes,
    )
}

private val OverlayBlue = Color(0xFF34E6F4)
private val OverlayFill = Color(0x2EFFFFFF)
private val OverlayGrid = Color(0x22FFFFFF)
private val OverlayGuide = Color(0xC2FFFFFF)
private const val RgbaPixelStride = 4
