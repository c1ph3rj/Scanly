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
@Suppress("DEPRECATION")
internal fun CameraPreview(
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
            delay(CameraSessionConstants.TapFocusIndicatorDurationMillis)
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
                    .setJpegQuality(CameraSessionConstants.CaptureJpegQuality)
                    .build()
                    .apply {
                        targetRotation = currentPreviewView.display.rotation
                    }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetResolution(CameraSessionConstants.AnalysisResolution)
                    .build()
                    .apply {
                        targetRotation = currentPreviewView.display.rotation
                        var lastAcceptedFrameNanos = 0L
                        setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                val nowNanos = System.nanoTime()
                                if (nowNanos - lastAcceptedFrameNanos >= CameraSessionConstants.AnalysisIntervalNanos) {
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
internal fun TapFocusIndicator(
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
internal fun DocumentDetectionOverlay(
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
                color = CameraSessionConstants.OverlayFill,
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
                color = CameraSessionConstants.OverlayGuide.copy(alpha = 0.9f),
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
internal fun PreviewStatusHud(
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
                CameraSessionConstants.OverlayWarning.copy(alpha = 0.8f)
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
                        CameraSessionConstants.OverlayWarning
                    } else when (liveDetection.phase) {
                        AutoCapturePhase.COUNTDOWN,
                        AutoCapturePhase.CAPTURING -> accentColor
                        AutoCapturePhase.HOLD_STEADY -> CameraSessionConstants.OverlayGuide
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
