package `in`.c1ph3rj.scanly.feature.camera

import android.Manifest
import android.app.Activity
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

@Composable
internal fun CameraCaptureLayout(
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
internal fun CameraLeftRail(
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
internal fun CameraCaptureDock(
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
internal fun CameraPreviewViewport(
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
internal fun ScanGridOverlay(
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
                color = CameraSessionConstants.OverlayGrid,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = gridStrokeWidth,
            )
        }

        for (index in 1..2) {
            val y = thirdHeight * index
            drawLine(
                color = CameraSessionConstants.OverlayGrid,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = gridStrokeWidth,
            )
        }

        drawLine(
            color = CameraSessionConstants.OverlayGuide,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

@Composable
internal fun CameraPreviewFeedback(
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
internal fun PortraitCaptureDockContent(
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
internal fun LandscapeCaptureDockContent(
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
internal fun RecentCaptureStack(
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
