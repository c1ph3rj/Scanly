package `in`.c1ph3rj.scanly.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.core.ml.DetectionFrame
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.feature.components.PagePreview
import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    onOpenDocument: (String) -> Unit,
    onReplacementCompleted: (String) -> Unit,
    viewModel: ScanSessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    var hasCameraPermission by remember(context) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

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
                is ScanSessionEvent.ReplacementCompleted -> onReplacementCompleted(event.pageId)
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
        onOpenDocument = {
            uiState.document?.id?.let(onOpenDocument)
        },
        onCapture = viewModel::requestCapture,
        onAutoCaptureEnabledChange = viewModel::onAutoCaptureEnabledChanged,
        onGridEnabledChange = viewModel::onGridEnabledChanged,
        onPreviewFrame = viewModel::onPreviewFrame,
        torchEnabled = torchEnabled,
        torchAvailable = torchAvailable,
        onTorchToggle = {
            val updatedState = !torchEnabled
            torchEnabled = updatedState
            cameraControl?.enableTorch(updatedState)
        },
        onTapToFocus = { tapOffset ->
            val currentPreviewView = previewView
            val currentCameraControl = cameraControl
            if (currentPreviewView == null || currentCameraControl == null) {
                false
            } else {
                runCatching {
                    val meteringPoint = currentPreviewView.meteringPointFactory.createPoint(
                        tapOffset.x,
                        tapOffset.y,
                    )
                    val focusAction = FocusMeteringAction.Builder(
                        meteringPoint,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                    )
                        .setAutoCancelDuration(TapFocusAutoCancelSeconds, TimeUnit.SECONDS)
                        .build()
                    currentCameraControl.startFocusAndMetering(focusAction)
                }.isSuccess
            }
        },
        onCameraReady = { captureUseCase, cameraPreview, boundCamera ->
            imageCapture = captureUseCase
            previewView = cameraPreview
            cameraControl = boundCamera.cameraControl
            torchAvailable = boundCamera.cameraInfo.hasFlashUnit()
            if (torchAvailable) {
                cameraControl?.enableTorch(torchEnabled)
            } else {
                torchEnabled = false
            }
        },
    )
}

@Composable
fun ScanSessionScreen(
    uiState: ScanSessionUiState,
    snackbarHostState: SnackbarHostState,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigateUp: () -> Unit,
    onOpenDocument: () -> Unit,
    onCapture: () -> Unit,
    onAutoCaptureEnabledChange: (Boolean) -> Unit,
    onGridEnabledChange: (Boolean) -> Unit,
    onPreviewFrame: (() -> DetectionFrame?) -> Boolean,
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    onTorchToggle: () -> Unit,
    onTapToFocus: (Offset) -> Boolean,
    onCameraReady: (ImageCapture, PreviewView, Camera) -> Unit,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    val previewAspectRatio = cameraPreviewAspectRatio(windowSizeInfo.isLandscape)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        if (uiState.missingDocument) {
            CameraStateCard(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                title = "Document missing",
                body = "Open a valid document first.",
                actionLabel = "Back",
                onAction = onNavigateUp,
            )
        } else if (!hasCameraPermission) {
            CameraStateCard(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                title = "Camera access needed",
                body = "Allow camera to keep scanning.",
                actionLabel = "Grant",
                onAction = onRequestPermission,
            )
        } else {
            CameraCaptureLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                uiState = uiState,
                previewAspectRatio = previewAspectRatio,
                isLandscape = windowSizeInfo.isLandscape,
                onNavigateUp = onNavigateUp,
                onCapture = onCapture,
                onOpenDocument = onOpenDocument,
                autoCaptureEnabled = uiState.liveDetection.autoCaptureEnabled,
                onAutoCaptureToggle = {
                    onAutoCaptureEnabledChange(!uiState.liveDetection.autoCaptureEnabled)
                },
                gridEnabled = uiState.liveDetection.isGridEnabled,
                onGridToggle = {
                    onGridEnabledChange(!uiState.liveDetection.isGridEnabled)
                },
                torchEnabled = torchEnabled,
                torchAvailable = torchAvailable,
                onTorchToggle = onTorchToggle,
                onCameraReady = onCameraReady,
                onPreviewFrame = onPreviewFrame,
                onTapToFocus = onTapToFocus,
            )
        }
    }
}

@Composable
private fun CameraCaptureLayout(
    uiState: ScanSessionUiState,
    previewAspectRatio: Float,
    isLandscape: Boolean,
    onNavigateUp: () -> Unit,
    onCapture: () -> Unit,
    onOpenDocument: () -> Unit,
    autoCaptureEnabled: Boolean,
    onAutoCaptureToggle: () -> Unit,
    gridEnabled: Boolean,
    onGridToggle: () -> Unit,
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    onTorchToggle: () -> Unit,
    onCameraReady: (ImageCapture, PreviewView, Camera) -> Unit,
    onPreviewFrame: (() -> DetectionFrame?) -> Boolean,
    onTapToFocus: (Offset) -> Boolean,
    modifier: Modifier = Modifier,
) {
    if (isLandscape) {
        Row(
            modifier = modifier.background(Color.Black).fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraLeftRail(
                onNavigateUp = onNavigateUp,
                autoCaptureEnabled = autoCaptureEnabled,
                onAutoCaptureToggle = onAutoCaptureToggle,
                gridEnabled = gridEnabled,
                onGridToggle = onGridToggle,
                torchEnabled = torchEnabled,
                torchAvailable = torchAvailable,
                onTorchToggle = onTorchToggle,
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                CameraPreviewViewport(
                    modifier = Modifier.fillMaxSize(),
                    aspectRatio = previewAspectRatio,
                    liveDetection = uiState.liveDetection,
                    onCameraReady = onCameraReady,
                    onPreviewFrame = onPreviewFrame,
                    onTapToFocus = onTapToFocus,
                )
            }
            
            CameraCaptureDock(
                uiState = uiState,
                onCapture = onCapture,
                onOpenDocument = onOpenDocument,
                compact = true,
                modifier = Modifier
                    .width(104.dp)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            )
        }
    } else {
        Column(
            modifier = modifier.background(Color.Black),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.78f)),
            ) {
                CameraTopBar(
                    onNavigateUp = onNavigateUp,
                    autoCaptureEnabled = autoCaptureEnabled,
                    onAutoCaptureToggle = onAutoCaptureToggle,
                    gridEnabled = gridEnabled,
                    onGridToggle = onGridToggle,
                    torchEnabled = torchEnabled,
                    torchAvailable = torchAvailable,
                    onTorchToggle = onTorchToggle,
                    modifier = Modifier
                        .statusBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            CameraPreviewViewport(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                aspectRatio = previewAspectRatio,
                liveDetection = uiState.liveDetection,
                onCameraReady = onCameraReady,
                onPreviewFrame = onPreviewFrame,
                onTapToFocus = onTapToFocus,
            )

            CameraCaptureDock(
                uiState = uiState,
                onCapture = onCapture,
                onOpenDocument = onOpenDocument,
                compact = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp)
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun CameraLeftRail(
    onNavigateUp: () -> Unit,
    autoCaptureEnabled: Boolean,
    onAutoCaptureToggle: () -> Unit,
    gridEnabled: Boolean,
    onGridToggle: () -> Unit,
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    onTorchToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.94f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CaptureControlStrip(
                horizontal = false,
                autoCaptureEnabled = autoCaptureEnabled,
                onAutoCaptureToggle = onAutoCaptureToggle,
                gridEnabled = gridEnabled,
                onGridToggle = onGridToggle,
                torchEnabled = torchEnabled,
                torchAvailable = torchAvailable,
                onTorchToggle = onTorchToggle,
            )
            ChromeIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateUp,
                containerColor = Color.Transparent,
                contentColor = Color.White,
            )
        }
    }
}

@Composable
private fun CameraCaptureDock(
    uiState: ScanSessionUiState,
    onCapture: () -> Unit,
    onOpenDocument: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = if (compact) 0f else 0.9f),
        shape = if (compact) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        if (compact) {
            LandscapeCaptureDockContent(
                uiState = uiState,
                onCapture = onCapture,
                onOpenDocument = onOpenDocument,
            )
        } else {
            PortraitCaptureDockContent(
                uiState = uiState,
                onCapture = onCapture,
                onOpenDocument = onOpenDocument,
            )
        }
    }
}

@Composable
private fun CameraPreviewViewport(
    modifier: Modifier = Modifier,
    aspectRatio: Float,
    liveDetection: LiveDetectionUiState,
    onCameraReady: (ImageCapture, PreviewView, Camera) -> Unit,
    onPreviewFrame: (() -> DetectionFrame?) -> Boolean,
    onTapToFocus: (Offset) -> Boolean,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val previewSize = constrainedCameraPreviewSize(
            maxWidth = maxWidth.value,
            maxHeight = maxHeight.value,
            aspectRatio = aspectRatio,
        )

        Box(
            modifier = Modifier
                .width(previewSize.width.dp)
                .height(previewSize.height.dp)
                .clipToBounds(),
        ) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                liveDetection = liveDetection,
                onCameraReady = onCameraReady,
                onPreviewFrame = onPreviewFrame,
                onTapToFocus = onTapToFocus,
            )
            ScanGridOverlay(
                visible = liveDetection.isGridEnabled,
                modifier = Modifier.fillMaxSize(),
            )
            CameraPreviewFeedback(
                liveDetection = liveDetection,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ScanGridOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) {
        return
    }

    Canvas(modifier = modifier) {
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
    }
}

@Composable
private fun CameraPreviewFeedback(
    liveDetection: LiveDetectionUiState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        PreviewStatusHud(
            liveDetection = liveDetection,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
        )
    }
}

@Composable
private fun PortraitCaptureDockContent(
    uiState: ScanSessionUiState,
    onCapture: () -> Unit,
    onOpenDocument: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        CaptureButton(
            busy = uiState.captureInProgress,
            replacement = uiState.isReplacementMode,
            onClick = onCapture,
            modifier = Modifier.align(Alignment.Center),
        )
        RecentCaptureStack(
            pages = uiState.pages,
            enabled = !uiState.captureInProgress,
            onOpenDocument = onOpenDocument,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun LandscapeCaptureDockContent(
    uiState: ScanSessionUiState,
    onCapture: () -> Unit,
    onOpenDocument: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        RecentCaptureStack(
            pages = uiState.pages,
            enabled = !uiState.captureInProgress,
            onOpenDocument = onOpenDocument,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        CaptureButton(
            busy = uiState.captureInProgress,
            replacement = uiState.isReplacementMode,
            onClick = onCapture,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun RecentCaptureStack(
    pages: List<ScanPage>,
    enabled: Boolean,
    onOpenDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recentPages = remember(pages) {
        pages.sortedWith(
            compareByDescending<ScanPage> { page -> page.updatedAtMillis }
                .thenByDescending { page -> page.pageIndex },
        ).take(2)
    }
    Surface(
        onClick = onOpenDocument,
        enabled = enabled && recentPages.isNotEmpty(),
        modifier = modifier.size(width = 78.dp, height = 82.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(2.dp),
    ) {
        if (recentPages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.48f),
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "No captures",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.58f),
                    maxLines = 1,
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                recentPages.asReversed().forEachIndexed { index, page ->
                    val isLatest = page.id == recentPages.first().id
                    Surface(
                        modifier = Modifier
                            .align(if (isLatest) Alignment.TopEnd else Alignment.BottomStart)
                            .width(54.dp)
                            .height(72.dp),
                        color = Color.Black,
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(
                            width = if (isLatest) 2.dp else 1.dp,
                            color = if (isLatest) Color.White else Color.White.copy(alpha = 0.45f),
                        ),
                        shadowElevation = if (index == recentPages.lastIndex) 4.dp else 0.dp,
                    ) {
                        PagePreview(
                            page = page,
                            displaySize = PreviewDisplaySize.COMPACT,
                            modifier = Modifier.fillMaxSize(),
                            minHeight = 72.dp,
                            aspectRatio = null,
                        )
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ) {
                    Text(
                        text = pages.size.toString(),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraTopBar(
    onNavigateUp: () -> Unit,
    autoCaptureEnabled: Boolean,
    onAutoCaptureToggle: () -> Unit,
    gridEnabled: Boolean,
    onGridToggle: () -> Unit,
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    onTorchToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ChromeIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onNavigateUp,
            containerColor = Color.Transparent,
            contentColor = Color.White,
        )
        CaptureControlStrip(
            horizontal = true,
            autoCaptureEnabled = autoCaptureEnabled,
            onAutoCaptureToggle = onAutoCaptureToggle,
            gridEnabled = gridEnabled,
            onGridToggle = onGridToggle,
            torchEnabled = torchEnabled,
            torchAvailable = torchAvailable,
            onTorchToggle = onTorchToggle,
        )
    }
}

@Composable
private fun CaptureControlStrip(
    horizontal: Boolean,
    autoCaptureEnabled: Boolean,
    onAutoCaptureToggle: () -> Unit,
    gridEnabled: Boolean,
    onGridToggle: () -> Unit,
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    onTorchToggle: () -> Unit,
) {
    val controls: @Composable () -> Unit = {
        TopControlButton(
            icon = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
            label = if (torchEnabled) "Turn flash off" else "Turn flash on",
            active = torchEnabled,
            enabled = torchAvailable,
            onClick = onTorchToggle,
        )
        TopControlButton(
            icon = Icons.Filled.AutoAwesome,
            label = if (autoCaptureEnabled) "Turn auto-capture off" else "Turn auto-capture on",
            active = autoCaptureEnabled,
            onClick = onAutoCaptureToggle,
        )
        TopControlButton(
            icon = Icons.Filled.Grid3x3,
            label = if (gridEnabled) "Hide grid" else "Show grid",
            active = gridEnabled,
            onClick = onGridToggle,
        )
    }

    if (horizontal) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            controls()
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            controls()
        }
    }
}

@Composable
private fun TopControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when {
        active -> colorScheme.primary
        enabled -> colorScheme.surfaceContainerHighest
        else -> colorScheme.surfaceContainer
    }
    val contentColor = when {
        active -> colorScheme.onPrimary
        enabled -> colorScheme.onSurface
        else -> colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp),
        color = containerColor,
        shape = CircleShape,
        border = BorderStroke(
            1.dp,
            if (active) Color.Transparent else colorScheme.outline.copy(alpha = 0.52f),
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun CaptureButton(
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    replacement: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.size(86.dp),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(3.dp, colorScheme.primary),
    ) {
        Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                onClick = onClick,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxSize(),
                shape = CircleShape,
                color = when {
                    busy -> colorScheme.primary
                    replacement -> colorScheme.tertiary
                    else -> Color.White
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = if (replacement) colorScheme.onTertiary else colorScheme.onPrimary,
                            trackColor = Color.Black.copy(alpha = 0.16f),
                            strokeWidth = 3.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraStateCard(
    modifier: Modifier = Modifier,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onAction) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun CameraPreview(
    modifier: Modifier = Modifier,
    liveDetection: LiveDetectionUiState,
    onCameraReady: (ImageCapture, PreviewView, Camera) -> Unit,
    onPreviewFrame: (() -> DetectionFrame?) -> Boolean,
    onTapToFocus: (Offset) -> Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var focusIndicatorOffset by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(focusIndicatorOffset) {
        if (focusIndicatorOffset != null) {
            delay(TapFocusIndicatorDurationMillis)
            focusIndicatorOffset = null
        }
    }

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
            var disposed = false
            val listener = Runnable {
                if (disposed) {
                    return@Runnable
                }
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .setTargetRotation(currentPreviewView.display.rotation)
                    .build()
                    .also { useCase ->
                        useCase.surfaceProvider = currentPreviewView.surfaceProvider
                    }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(CaptureJpegQuality)
                    .build()
                    .apply {
                        targetRotation = currentPreviewView.display.rotation
                    }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetResolution(AnalysisResolution)
                    .build()
                    .apply {
                        targetRotation = currentPreviewView.display.rotation
                        var lastAcceptedFrameNanos = 0L
                        setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                val nowNanos = System.nanoTime()
                                if (nowNanos - lastAcceptedFrameNanos >= AnalysisIntervalNanos) {
                                    val accepted = onPreviewFrame {
                                        imageProxy.toDetectionFrame()
                                    }
                                    if (accepted) {
                                        lastAcceptedFrameNanos = nowNanos
                                    }
                                }
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }
                val viewPort = currentPreviewView.viewPort ?: return@Runnable
                val useCaseGroup = UseCaseGroup.Builder()
                    .setViewPort(viewPort)
                    .addUseCase(preview)
                    .addUseCase(imageCapture)
                    .addUseCase(imageAnalysis)
                    .build()

                cameraProvider.unbindAll()
                val boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    useCaseGroup,
                )
                onCameraReady(imageCapture, currentPreviewView, boundCamera)
            }

            currentPreviewView.doOnLayout {
                if (!disposed) {
                    cameraProviderFuture.addListener(listener, mainExecutor)
                }
            }
            onDispose {
                disposed = true
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(onTapToFocus) {
                detectTapGestures { offset: Offset ->
                    if (onTapToFocus(offset)) {
                        focusIndicatorOffset = offset
                    }
                }
            },
    ) {
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
        TapFocusIndicator(
            focusOffset = focusIndicatorOffset,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TapFocusIndicator(
    focusOffset: Offset?,
    modifier: Modifier = Modifier,
) {
    if (focusOffset == null) {
        return
    }
    val accentColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        drawCircle(
            color = accentColor.copy(alpha = 0.16f),
            radius = 34.dp.toPx(),
            center = focusOffset,
        )
        drawCircle(
            color = accentColor,
            radius = 26.dp.toPx(),
            center = focusOffset,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.92f),
            radius = 4.dp.toPx(),
            center = focusOffset,
        )
    }
}

@Composable
private fun DocumentDetectionOverlay(
    liveDetection: LiveDetectionUiState,
    modifier: Modifier = Modifier,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!liveDetection.hasOverlay) {
                return@Canvas
            }

            val mappedPoints = liveDetection.overlayFrame
                ?.let { sourceFrame -> liveDetection.quad?.mapToPreview(sourceFrame) }
                ?.map { point ->
                    Offset(
                        x = point.x * size.width,
                        y = point.y * size.height,
                    )
                }
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
                x = (mappedPoints[0].x + mappedPoints[1].x + mappedPoints[2].x + mappedPoints[3].x) / 4f,
                y = (mappedPoints[0].y + mappedPoints[1].y + mappedPoints[2].y + mappedPoints[3].y) / 4f,
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
                color = accentColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                ),
            )
            drawLine(
                color = accentColor.copy(alpha = 0.7f),
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
                        color = accentColor,
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
    val accentColor = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.68f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (liveDetection.sceneIssue != null) {
                OverlayWarning.copy(alpha = 0.8f)
            } else {
                Color.White.copy(alpha = 0.12f)
            },
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(
                    color = if (liveDetection.sceneIssue != null) {
                        OverlayWarning
                    } else when (liveDetection.phase) {
                        AutoCapturePhase.COUNTDOWN,
                        AutoCapturePhase.CAPTURING -> accentColor
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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

internal fun cameraPreviewAspectRatio(isLandscape: Boolean): Float = if (isLandscape) 4f / 3f else 3f / 4f

internal data class CameraPreviewSize(
    val width: Float,
    val height: Float,
)

internal fun constrainedCameraPreviewSize(
    maxWidth: Float,
    maxHeight: Float,
    aspectRatio: Float,
): CameraPreviewSize {
    if (maxWidth <= 0f || maxHeight <= 0f || aspectRatio <= 0f) {
        return CameraPreviewSize(width = 0f, height = 0f)
    }
    return if ((maxWidth / maxHeight) > aspectRatio) {
        CameraPreviewSize(
            width = maxHeight * aspectRatio,
            height = maxHeight,
        )
    } else {
        CameraPreviewSize(
            width = maxWidth,
            height = maxWidth / aspectRatio,
        )
    }
}

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
        cropLeft = cropRect.left,
        cropTop = cropRect.top,
        cropRight = cropRect.right,
        cropBottom = cropRect.bottom,
    )
}

private val OverlayFill = Color(0x2EFFFFFF)
private val OverlayGrid = Color(0x22FFFFFF)
private val OverlayGuide = Color(0xC2FFFFFF)
private val OverlayWarning = Color(0xFFFFC857)
private const val RgbaPixelStride = 4
private const val AnalysisFramesPerSecond = 8L
private const val AnalysisIntervalNanos = 1_000_000_000L / AnalysisFramesPerSecond
private const val TapFocusAutoCancelSeconds = 3L
private const val TapFocusIndicatorDurationMillis = 850L
private val AnalysisResolution = Size(640, 480)
private const val CaptureJpegQuality = 95
