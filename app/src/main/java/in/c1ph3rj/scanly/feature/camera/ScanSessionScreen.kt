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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Brush
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
import `in`.c1ph3rj.scanly.core.ui.MetricChip
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
    onOpenDocument: (String) -> Unit,
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
@OptIn(ExperimentalMaterial3Api::class)
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
    var settingsSheetVisible by rememberSaveable { mutableStateOf(false) }
    val windowSizeInfo = rememberWindowSizeInfo()
    val previewAspectRatio = cameraPreviewAspectRatio()

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
                settingsSheetVisible = settingsSheetVisible,
                onSettingsClick = { settingsSheetVisible = true },
                onCapture = onCapture,
                onOpenDocument = onOpenDocument,
                onCameraReady = onCameraReady,
                onPreviewFrame = onPreviewFrame,
                onTapToFocus = onTapToFocus,
            )
        }
    }

    if (settingsSheetVisible) {
        CameraSettingsSheet(
            uiState = uiState,
            torchEnabled = torchEnabled,
            torchAvailable = torchAvailable,
            onDismiss = { settingsSheetVisible = false },
            onAutoCaptureEnabledChange = onAutoCaptureEnabledChange,
            onGridEnabledChange = onGridEnabledChange,
            onTorchToggle = onTorchToggle,
        )
    }
}

@Composable
private fun CameraCaptureLayout(
    uiState: ScanSessionUiState,
    previewAspectRatio: Float,
    isLandscape: Boolean,
    onNavigateUp: () -> Unit,
    settingsSheetVisible: Boolean,
    onSettingsClick: () -> Unit,
    onCapture: () -> Unit,
    onOpenDocument: () -> Unit,
    onCameraReady: (ImageCapture, PreviewView, Camera) -> Unit,
    onPreviewFrame: (() -> DetectionFrame?) -> Boolean,
    onTapToFocus: (Offset) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        CameraPreviewViewport(
            modifier = if (isLandscape) {
                Modifier.fillMaxHeight()
            } else {
                Modifier.fillMaxWidth()
            },
            aspectRatio = previewAspectRatio,
            liveDetection = uiState.liveDetection,
            showStats = uiState.showDetectionStats,
            onCameraReady = onCameraReady,
            onPreviewFrame = onPreviewFrame,
            onTapToFocus = onTapToFocus,
        )
        ScanGridOverlay(
            visible = uiState.liveDetection.isGridEnabled,
            modifier = Modifier.fillMaxSize(),
        )

        Surface(
            color = Color.Black.copy(alpha = 0.78f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(if (isLandscape) 0.62f else 1f),
            shape = if (isLandscape) {
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            } else {
                RoundedCornerShape(0.dp)
            },
        ) {
            CameraTopBar(
                uiState = uiState,
                onNavigateUp = onNavigateUp,
                settingsSheetVisible = settingsSheetVisible,
                onSettingsClick = onSettingsClick,
                compact = false,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        CameraCaptureDock(
            uiState = uiState,
            onCapture = onCapture,
            onOpenDocument = onOpenDocument,
            compact = isLandscape,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(
                    if (isLandscape) {
                        Modifier
                            .widthIn(max = 420.dp)
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.9f))
                    },
                )
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun CameraLandscapeControlPanel(
    uiState: ScanSessionUiState,
    onNavigateUp: () -> Unit,
    settingsSheetVisible: Boolean,
    onSettingsClick: () -> Unit,
    onAutoCaptureEnabledChange: (Boolean) -> Unit,
    onGridEnabledChange: (Boolean) -> Unit,
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    onTorchToggle: () -> Unit,
    onCapture: () -> Unit,
    onOpenDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.94f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CameraTopBar(
                uiState = uiState,
                onNavigateUp = onNavigateUp,
                settingsSheetVisible = settingsSheetVisible,
                onSettingsClick = onSettingsClick,
                compact = true,
            )
            CameraCaptureDock(
                uiState = uiState,
                onCapture = onCapture,
                onOpenDocument = onOpenDocument,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 0.dp else 18.dp, vertical = if (compact) 12.dp else 16.dp),
        ) {
            CaptureButton(
                busy = uiState.captureInProgress,
                onClick = onCapture,
                modifier = Modifier.align(Alignment.Center),
            )
            TopControlButton(
                icon = Icons.Filled.Description,
                label = "Review document",
                active = false,
                enabled = uiState.document != null && !uiState.captureInProgress,
                onClick = onOpenDocument,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 92.dp),
            )
        }
    }
}

@Composable
private fun CameraPreviewViewport(
    modifier: Modifier = Modifier,
    aspectRatio: Float,
    liveDetection: LiveDetectionUiState,
    showStats: Boolean,
    onCameraReady: (ImageCapture, PreviewView, Camera) -> Unit,
    onPreviewFrame: (() -> DetectionFrame?) -> Boolean,
    onTapToFocus: (Offset) -> Boolean,
) {
    Box(
        modifier = modifier.aspectRatio(aspectRatio),
    ) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            liveDetection = liveDetection,
            onCameraReady = onCameraReady,
            onPreviewFrame = onPreviewFrame,
            onTapToFocus = onTapToFocus,
        )
        CameraPreviewFeedback(
            liveDetection = liveDetection,
            showStats = showStats,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun CameraPreviewScrims(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(170.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.62f),
                            Color.Black.copy(alpha = 0f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(190.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0f),
                            Color.Black.copy(alpha = 0.58f),
                        ),
                    ),
                ),
        )
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
    showStats: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewStatusHud(liveDetection = liveDetection)
        DetectionMeta(
            liveDetection = liveDetection,
            showStats = showStats,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CameraSettingsSheet(
    uiState: ScanSessionUiState,
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    onDismiss: () -> Unit,
    onAutoCaptureEnabledChange: (Boolean) -> Unit,
    onGridEnabledChange: (Boolean) -> Unit,
    onTorchToggle: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Scan settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    text = uiState.liveDetection.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.68f),
                )
            }

            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Capture controls",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AutoCaptureChip(
                            enabled = uiState.liveDetection.autoCaptureEnabled,
                            onClick = {
                                onAutoCaptureEnabledChange(!uiState.liveDetection.autoCaptureEnabled)
                            },
                        )
                        GridChip(
                            enabled = uiState.liveDetection.isGridEnabled,
                            onClick = {
                                onGridEnabledChange(!uiState.liveDetection.isGridEnabled)
                            },
                        )
                        FlashChip(
                            enabled = torchEnabled,
                            available = torchAvailable,
                            onClick = onTorchToggle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraTopBar(
    uiState: ScanSessionUiState,
    onNavigateUp: () -> Unit,
    settingsSheetVisible: Boolean,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraSessionTitlePill(
                title = uiState.document?.title ?: "Scan session",
                onNavigateUp = onNavigateUp,
                modifier = Modifier.weight(1f, fill = false),
            )
            TopControlButton(
                icon = Icons.Filled.Tune,
                label = "Scan settings",
                active = settingsSheetVisible,
                onClick = onSettingsClick,
            )
        }
    }
}

@Composable
private fun CameraSessionTitlePill(
    title: String,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.36f),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChromeIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateUp,
                containerColor = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TopControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        active -> OverlayBlue.copy(alpha = 0.92f)
        enabled -> Color.Black.copy(alpha = 0.36f)
        else -> Color.Black.copy(alpha = 0.22f)
    }
    val contentColor = when {
        active -> Color.Black
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.38f)
    }
    Surface(
        modifier = modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        color = containerColor,
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (active) 0f else 0.1f)),
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
private fun AutoCaptureChip(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (enabled) OverlayBlue.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) OverlayBlue else Color.White.copy(alpha = 0.12f),
        ),
    ) {
        TextButton(onClick = onClick) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = if (enabled) Color.Black else Color.White,
                )
                Text(
                    text = "Auto",
                    color = if (enabled) Color.Black else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun GridChip(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (enabled) OverlayBlue.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) OverlayBlue else Color.White.copy(alpha = 0.12f),
        ),
    ) {
        TextButton(onClick = onClick) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Grid3x3,
                    contentDescription = null,
                    tint = if (enabled) Color.Black else Color.White,
                )
                Text(
                    text = "Grid",
                    color = if (enabled) Color.Black else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun FlashChip(
    enabled: Boolean,
    available: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(
            enabled = available,
            onClick = onClick,
        ),
        color = if (enabled) OverlayBlue.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) OverlayBlue else Color.White.copy(alpha = 0.12f),
        ),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                imageVector = if (enabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = if (enabled) "Flashlight on" else "Flashlight off",
                tint = if (enabled) Color.Black else if (available) Color.White else Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun CameraBottomDock(
    uiState: ScanSessionUiState,
    onCapture: () -> Unit,
    onRetakePageSelection: (String?) -> Unit,
    pagesVisible: Boolean,
    onPagesVisibilityToggle: () -> Unit,
    onOpenDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        val trayVisible = uiState.pages.isNotEmpty() && pagesVisible
        val detectionBottomPadding = if (trayVisible) 246.dp else 132.dp

        AnimatedVisibility(
            visible = trayVisible,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(bottom = 132.dp),
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(
                    items = uiState.pages,
                    key = { page -> page.id },
                ) { page ->
                    val selectedForReplacement = uiState.replacementPageId == page.id
                    Surface(
                        modifier = Modifier
                            .size(width = 76.dp, height = 102.dp)
                            .clickable {
                                onRetakePageSelection(
                                    if (selectedForReplacement) null else page.id,
                                )
                            },
                        color = Color.Black.copy(alpha = 0.46f),
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(
                            width = if (selectedForReplacement) 2.dp else 1.dp,
                            color = if (selectedForReplacement) {
                                OverlayBlue
                            } else {
                                Color.White.copy(alpha = 0.12f)
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PagePreview(
                                page = page,
                                displaySize = PreviewDisplaySize.COMPACT,
                                modifier = Modifier.fillMaxWidth(),
                                minHeight = 56.dp,
                            )
                            Text(
                                text = "P${page.pageIndex + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = Color.Transparent,
            shape = androidx.compose.ui.graphics.RectangleShape,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DockActionButton(
                    icon = if (pagesVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    label = "Pages",
                    onClick = onPagesVisibilityToggle,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.pages.isNotEmpty(),
                    active = pagesVisible && uiState.pages.isNotEmpty(),
                )
                CaptureButton(
                    busy = uiState.captureInProgress,
                    replacement = uiState.isReplacementMode,
                    onClick = onCapture,
                )
                val latestPage = uiState.latestCapturedPage
                if (latestPage != null) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier
                                .width(48.dp)
                                .height(64.dp)
                                .clickable(
                                    enabled = !uiState.captureInProgress,
                                    onClick = onOpenDocument
                                ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.48f),
                            border = BorderStroke(2.dp, Color.White),
                            shadowElevation = 4.dp
                        ) {
                            PagePreview(
                                page = latestPage,
                                displaySize = PreviewDisplaySize.DETAIL,
                                modifier = Modifier.fillMaxSize(),
                                minHeight = 120.dp,
                                aspectRatio = null,
                            )
                        }
                    }
                } else {
                    DockActionButton(
                        icon = Icons.Filled.Description,
                        label = "Preview",
                        onClick = onOpenDocument,
                        modifier = Modifier.weight(1f),
                        enabled = uiState.document != null && !uiState.captureInProgress,
                        emphasized = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraSideDock(
    uiState: ScanSessionUiState,
    onCapture: () -> Unit,
    onRetakePageSelection: (String?) -> Unit,
    pagesVisible: Boolean,
    onPagesVisibilityToggle: () -> Unit,
    onOpenDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Transparent,
        shape = androidx.compose.ui.graphics.RectangleShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CaptureRailHeader(
                pageCount = uiState.pages.size,
                autoCaptureEnabled = uiState.liveDetection.autoCaptureEnabled,
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
                if (pagesVisible && uiState.pages.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = uiState.pages,
                        key = { page -> page.id },
                    ) { page ->
                        val selectedForReplacement = uiState.replacementPageId == page.id
                        PageRailCard(
                            page = page,
                            selectedForReplacement = selectedForReplacement,
                            latest = uiState.latestCapturedPage?.id == page.id,
                            onClick = {
                                onRetakePageSelection(
                                    if (selectedForReplacement) null else page.id,
                                )
                            },
                        )
                    }
                }
                } else {
                    EmptyPageRail(
                        hasPages = uiState.pages.isNotEmpty(),
                        pagesVisible = pagesVisible,
                        onPagesVisibilityToggle = onPagesVisibilityToggle,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            CaptureRailActions(
                uiState = uiState,
                pagesVisible = pagesVisible,
                onPagesVisibilityToggle = onPagesVisibilityToggle,
                onCapture = onCapture,
                onOpenDocument = onOpenDocument,
            )
        }
    }
}

@Composable
private fun CaptureRailHeader(
    pageCount: Int,
    autoCaptureEnabled: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Captured pages",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    text = if (pageCount == 1) "1 page in this document" else "$pageCount pages in this document",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                color = if (autoCaptureEnabled) OverlayBlue.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.1f),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = if (autoCaptureEnabled) Icons.Filled.AutoAwesome else Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = if (autoCaptureEnabled) Color.Black else Color.White,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(18.dp),
                )
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    }
}

@Composable
private fun PageRailCard(
    page: ScanPage,
    selectedForReplacement: Boolean,
    latest: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = when {
        selectedForReplacement -> OverlayBlue
        latest -> Color.White.copy(alpha = 0.42f)
        else -> Color.White.copy(alpha = 0.1f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clickable(onClick = onClick),
        color = if (selectedForReplacement) {
            OverlayBlue.copy(alpha = 0.16f)
        } else {
            Color.Black.copy(alpha = 0.42f)
        },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (selectedForReplacement) 2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .width(48.dp)
                    .height(62.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            ) {
                PagePreview(
                    page = page,
                    displaySize = PreviewDisplaySize.COMPACT,
                    modifier = Modifier.fillMaxSize(),
                    minHeight = 56.dp,
                    aspectRatio = null,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Page ${page.pageIndex + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        selectedForReplacement -> "Retake target"
                        latest -> "Latest capture"
                        else -> "Tap to retake"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selectedForReplacement) OverlayBlue else Color.White.copy(alpha = 0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selectedForReplacement) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = OverlayBlue,
                    modifier = Modifier.size(18.dp),
                )
            } else if (latest) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyPageRail(
    hasPages: Boolean,
    pagesVisible: Boolean,
    onPagesVisibilityToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (hasPages && !pagesVisible) Icons.Filled.VisibilityOff else Icons.Filled.Description,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.48f),
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = if (hasPages && !pagesVisible) "Pages hidden" else "No pages yet",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            Text(
                text = if (hasPages && !pagesVisible) {
                    "Show pages to choose one for retake."
                } else {
                    "Capture your first page to build the stack."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.58f),
                maxLines = 2,
            )
            if (hasPages && !pagesVisible) {
                DockActionButton(
                    icon = Icons.Filled.Visibility,
                    label = "Show pages",
                    onClick = onPagesVisibilityToggle,
                    active = true,
                )
            }
        }
    }
}

@Composable
private fun CaptureRailActions(
    uiState: ScanSessionUiState,
    pagesVisible: Boolean,
    onPagesVisibilityToggle: () -> Unit,
    onCapture: () -> Unit,
    onOpenDocument: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaptureTargetCard(
                uiState = uiState,
                onOpenDocument = onOpenDocument,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SideDockIconButton(
                    icon = if (pagesVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    label = if (pagesVisible) "Hide" else "Pages",
                    onClick = onPagesVisibilityToggle,
                    enabled = uiState.pages.isNotEmpty(),
                    active = pagesVisible && uiState.pages.isNotEmpty(),
                )
                CaptureButton(
                    busy = uiState.captureInProgress,
                    replacement = uiState.isReplacementMode,
                    onClick = onCapture,
                )
                SideDockIconButton(
                    icon = Icons.Filled.Description,
                    label = "Review",
                    onClick = onOpenDocument,
                    enabled = uiState.document != null && !uiState.captureInProgress,
                    emphasized = uiState.pages.isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun CaptureTargetCard(
    uiState: ScanSessionUiState,
    onOpenDocument: () -> Unit,
) {
    val targetPage = uiState.replacementPage ?: uiState.latestCapturedPage
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = targetPage != null && !uiState.captureInProgress,
                onClick = onOpenDocument,
            ),
        color = Color.Black.copy(alpha = 0.42f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (uiState.isReplacementMode) OverlayBlue else Color.White.copy(alpha = 0.1f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (targetPage != null) {
                Surface(
                    modifier = Modifier
                        .width(52.dp)
                        .height(70.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                ) {
                    PagePreview(
                        page = targetPage,
                        displaySize = PreviewDisplaySize.COMPACT,
                        modifier = Modifier.fillMaxSize(),
                        minHeight = 70.dp,
                        aspectRatio = null,
                    )
                }
            } else {
                Surface(
                    modifier = Modifier
                        .width(52.dp)
                        .height(70.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.62f),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = if (uiState.isReplacementMode) "Retake target" else "Next capture",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (uiState.isReplacementMode) OverlayBlue else Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                )
                Text(
                    text = targetPage?.let { "Page ${it.pageIndex + 1}" } ?: "Ready for page ${uiState.pages.size + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (uiState.isReplacementMode) "Shutter will replace this page" else "Tap Review after capture",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.52f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SideDockIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
    emphasized: Boolean = false,
) {
    val containerColor = when {
        emphasized -> if (enabled) OverlayBlue.copy(alpha = 0.92f) else OverlayBlue.copy(alpha = 0.26f)
        active -> OverlayBlue.copy(alpha = 0.18f)
        else -> Color.Black.copy(alpha = 0.48f)
    }
    val contentColor = when {
        emphasized -> if (enabled) Color.Black else Color.Black.copy(alpha = 0.45f)
        active -> OverlayBlue
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.45f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier
                .size(52.dp)
                .clickable(enabled = enabled, onClick = onClick),
            color = containerColor,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = if (enabled) 0.85f else 0.45f),
        )
    }
}

@Composable
private fun DockActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    emphasized: Boolean = false,
) {
    val containerColor = when {
        emphasized -> if (enabled) OverlayBlue.copy(alpha = 0.92f) else OverlayBlue.copy(alpha = 0.26f)
        active -> OverlayBlue.copy(alpha = 0.18f)
        else -> Color.Black.copy(alpha = 0.48f)
    }
    val borderColor = when {
        emphasized -> OverlayBlue.copy(alpha = 0.95f)
        active -> OverlayBlue.copy(alpha = 0.95f)
        enabled -> Color.White.copy(alpha = 0.12f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    val contentColor = when {
        emphasized -> if (enabled) Color.Black else Color.Black.copy(alpha = 0.45f)
        active -> OverlayBlue
        enabled -> Color.White
        else -> Color.White.copy(alpha = 0.45f)
    }

    Surface(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clickable(enabled = enabled, onClick = onClick),
        color = containerColor,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CaptureButton(
    busy: Boolean,
    replacement: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(86.dp),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(3.dp, Color.White),
    ) {
        Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = !busy, onClick = onClick),
                shape = CircleShape,
                color = when {
                    busy -> OverlayBlue
                    else -> Color.White
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color.Black,
                            trackColor = Color.Black.copy(alpha = 0.2f),
                            strokeWidth = 3.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectionMeta(
    liveDetection: LiveDetectionUiState,
    showStats: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!showStats) {
        return
    }
    if (liveDetection.confidence == null || liveDetection.inferenceTimeMillis == null) {
        return
    }

    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = "${(liveDetection.confidence * 100f).roundToInt()}% • ${liveDetection.inferenceTimeMillis} ms",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
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
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
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

    Canvas(modifier = modifier) {
        drawCircle(
            color = OverlayBlue.copy(alpha = 0.16f),
            radius = 34.dp.toPx(),
            center = focusOffset,
        )
        drawCircle(
            color = OverlayBlue,
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
                text = liveDetection.compactStatusLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
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

internal fun cameraPreviewAspectRatio(): Float = 1f

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

private val OverlayBlue = androidx.compose.ui.graphics.Color(0xFF00BFA5)
private val OverlayFill = Color(0x2EFFFFFF)
private val OverlayGrid = Color(0x22FFFFFF)
private val OverlayGuide = Color(0xC2FFFFFF)
private const val RgbaPixelStride = 4
private const val AnalysisFramesPerSecond = 8L
private const val AnalysisIntervalNanos = 1_000_000_000L / AnalysisFramesPerSecond
private const val TapFocusAutoCancelSeconds = 3L
private const val TapFocusIndicatorDurationMillis = 850L
private val AnalysisResolution = Size(640, 480)


private fun LiveDetectionUiState.compactStatusLabel(): String = when (phase) {
    AutoCapturePhase.OFF -> "Manual"
    AutoCapturePhase.SEARCHING -> "Find document"
    AutoCapturePhase.HOLD_STEADY -> "Hold steady"
    AutoCapturePhase.COUNTDOWN -> countdownValue?.let { "Hold $it" } ?: "Hold"
    AutoCapturePhase.CAPTURING -> "Capturing"
    AutoCapturePhase.COOLDOWN -> "Next page"
}
