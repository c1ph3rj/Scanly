package `in`.c1ph3rj.scanly.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ml.DetectionFrame
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.feature.components.PagePreview
import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize
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
        onRetakePageSelection = viewModel::onReplacementPageSelected,
        onClearRetakeSelection = viewModel::clearReplacementSelection,
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
    onRetakePageSelection: (String?) -> Unit,
    onClearRetakeSelection: () -> Unit,
    onAutoCaptureEnabledChange: (Boolean) -> Unit,
    onGridEnabledChange: (Boolean) -> Unit,
    onPreviewFrame: (() -> DetectionFrame?) -> Boolean,
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    onTorchToggle: () -> Unit,
    onCameraReady: (ImageCapture, PreviewView, Camera) -> Unit,
) {
    var pagesVisible by rememberSaveable { mutableStateOf(false) }
    var quickControlsVisible by rememberSaveable { mutableStateOf(false) }

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                ) {
                    // Instruction: The camera preview must always use the 3:4 default sensor size aspect ratio to prevent cropping.
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        liveDetection = uiState.liveDetection,
                        onCameraReady = onCameraReady,
                        onPreviewFrame = onPreviewFrame,
                    )
                }
                CameraTopBar(
                    uiState = uiState,
                    onNavigateUp = onNavigateUp,
                    quickControlsVisible = quickControlsVisible,
                    onQuickControlsToggle = { quickControlsVisible = !quickControlsVisible },
                    onClearRetakeSelection = onClearRetakeSelection,
                    onAutoCaptureEnabledChange = onAutoCaptureEnabledChange,
                    onGridEnabledChange = onGridEnabledChange,
                    torchEnabled = torchEnabled,
                    torchAvailable = torchAvailable,
                    onTorchToggle = onTorchToggle,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
                CameraBottomDock(
                    uiState = uiState,
                    onCapture = onCapture,
                    onRetakePageSelection = onRetakePageSelection,
                    pagesVisible = pagesVisible,
                    onPagesVisibilityToggle = { pagesVisible = !pagesVisible },
                    onOpenDocument = onOpenDocument,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraTopBar(
    uiState: ScanSessionUiState,
    onNavigateUp: () -> Unit,
    quickControlsVisible: Boolean,
    onQuickControlsToggle: () -> Unit,
    onClearRetakeSelection: () -> Unit,
    onAutoCaptureEnabledChange: (Boolean) -> Unit,
    onGridEnabledChange: (Boolean) -> Unit,
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    onTorchToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChromeIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onNavigateUp,
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White,
                )
                Text(
                    text = uiState.document?.title ?: "Scan session",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ChromeIconButton(
                icon = Icons.Filled.Tune,
                contentDescription = if (quickControlsVisible) "Hide quick controls" else "Show quick controls",
                onClick = onQuickControlsToggle,
                containerColor = if (quickControlsVisible) OverlayBlue.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.15f),
                contentColor = if (quickControlsVisible) Color.Black else Color.White,
            )
        }

        PreviewStatusHud(
            liveDetection = uiState.liveDetection,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        AnimatedVisibility(
            visible = quickControlsVisible,
            enter = slideInVertically(initialOffsetY = { fullHeight -> -fullHeight }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { fullHeight -> -fullHeight }) + fadeOut(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (uiState.isReplacementMode) {
                            CancelRetakeChip(onClick = onClearRetakeSelection)
                        }
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
private fun CancelRetakeChip(
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Text(
            text = "Cancel retake",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
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
        DetectionMeta(
            liveDetection = uiState.liveDetection,
            showStats = uiState.showDetectionStats,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = detectionBottomPadding),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = Color.Black.copy(alpha = 0.58f),
            shape = RoundedCornerShape(28.dp),
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
    replacement: Boolean,
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
                    replacement -> Color(0xFF1BC091)
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
                    } else if (replacement) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Replace page",
                            tint = Color.Black,
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
                cameraProvider.unbindAll()
                val boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    imageAnalysis,
                )
                onCameraReady(imageCapture, currentPreviewView, boundCamera)
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
            if (liveDetection.isGridEnabled) {
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

private val OverlayBlue = androidx.compose.ui.graphics.Color(0xFF00BFA5)
private val OverlayFill = Color(0x2EFFFFFF)
private val OverlayGrid = Color(0x22FFFFFF)
private val OverlayGuide = Color(0xC2FFFFFF)
private const val RgbaPixelStride = 4
private const val AnalysisFramesPerSecond = 8L
private const val AnalysisIntervalNanos = 1_000_000_000L / AnalysisFramesPerSecond
private val AnalysisResolution = Size(640, 480)


private fun LiveDetectionUiState.compactStatusLabel(): String = when (phase) {
    AutoCapturePhase.OFF -> "Manual"
    AutoCapturePhase.SEARCHING -> "Find document"
    AutoCapturePhase.HOLD_STEADY -> "Hold steady"
    AutoCapturePhase.COUNTDOWN -> countdownValue?.let { "Hold $it" } ?: "Hold"
    AutoCapturePhase.CAPTURING -> "Capturing"
    AutoCapturePhase.COOLDOWN -> "Next page"
}
