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
fun ScanSessionScreen(
    uiState: ScanSessionUiState,
    snackbarHostState: SnackbarHostState,
    hasCameraPermission: Boolean,
    isCameraPermissionPermanentlyDenied: Boolean,
    onRequestCameraPermission: () -> Unit,
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
            CameraPermissionPrompt(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                isPermanentlyDenied = isCameraPermissionPermanentlyDenied,
                onAllowCamera = onRequestCameraPermission,
                onNavigateUp = onNavigateUp,
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
