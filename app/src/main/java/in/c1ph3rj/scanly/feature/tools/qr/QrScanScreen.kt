package `in`.c1ph3rj.scanly.feature.tools.qr

import android.Manifest
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import android.view.ViewGroup
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import `in`.c1ph3rj.scanly.feature.tools.ForceLightSystemBarIcons
import `in`.c1ph3rj.scanly.domain.model.QrPayload
import `in`.c1ph3rj.scanly.domain.model.QrPayloadParser
import `in`.c1ph3rj.scanly.domain.model.headline
import `in`.c1ph3rj.scanly.domain.model.summary
import `in`.c1ph3rj.scanly.feature.camera.CameraPermissionStatus
import `in`.c1ph3rj.scanly.feature.camera.CameraPermissionSupport
import `in`.c1ph3rj.scanly.feature.tools.ToolIconWell
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSecondaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolTextAction
import java.util.concurrent.atomic.AtomicBoolean

private val ScanTeal = Color(0xFF2DD4BF)

/**
 * Always-dark scanner: full-bleed camera with a viewfinder. A found code slides a result card
 * up over the camera; the camera stops detecting until the card is dismissed.
 */
@Composable
fun QrScanScreen(
    found: QrResultItem?,
    isReadingPhoto: Boolean,
    onCodeFound: (String, QrCapturePreview?) -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    onDismissFound: () -> Unit,
    onOpenDetails: () -> Unit,
    onClose: () -> Unit,
    onSelectCreate: () -> Unit,
    onOpenHistory: () -> Unit,
    onMessage: (String) -> Unit,
) {
    ForceLightSystemBarIcons()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = CameraPermissionSupport.findActivity(context)
    var permissionStatus by remember {
        mutableStateOf(CameraPermissionSupport.resolveStatus(activity, context))
    }
    var hasAutoRequestedPermission by rememberSaveable { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var cameraUnavailable by remember { mutableStateOf(false) }
    var hasTarget by remember { mutableStateOf(false) }
    var pickingPhoto by remember { mutableStateOf(false) }

    fun refreshCameraPermissionStatus() {
        permissionStatus = CameraPermissionSupport.resolveStatus(activity, context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        CameraPermissionSupport.markRequested(context)
        permissionStatus = CameraPermissionSupport.resolveStatus(activity, context)
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        pickingPhoto = false
        if (uri != null) onPhotoPicked(uri)
    }

    LaunchedEffect(permissionStatus) {
        if (
            CameraPermissionSupport.shouldAutoRequestSystemPermission(permissionStatus) &&
            !hasAutoRequestedPermission
        ) {
            hasAutoRequestedPermission = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(lifecycleOwner, context, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshCameraPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val granted = permissionStatus == CameraPermissionStatus.Granted
    val onAllow: () -> Unit = {
        if (CameraPermissionSupport.shouldOpenSettings(permissionStatus)) {
            CameraPermissionSupport.openAppSettings(context)
        } else if (CameraPermissionSupport.shouldRequestSystemPermission(permissionStatus)) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        QrScanHeader(
            torchAvailable = torchAvailable,
            torchEnabled = torchEnabled,
            onToggleTorch = { torchEnabled = !torchEnabled },
            onClose = onClose,
            onSelectCreate = onSelectCreate,
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (granted) {
                QrCameraPreview(
                    onBarcode = onCodeFound,
                    detecting = found == null && !pickingPhoto && !isReadingPhoto,
                    capture = found?.capture,
                    onTargetChanged = { hasTarget = it },
                    torchEnabled = torchEnabled,
                    onTorchAvailabilityChanged = { available ->
                        torchAvailable = available
                        if (!available) torchEnabled = false
                    },
                    onCameraError = {
                        torchAvailable = false
                        torchEnabled = false
                        cameraUnavailable = true
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (cameraUnavailable) {
                    Text(
                        text = "The camera isn't available right now. Try \"From photo\" instead.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp),
                    )
                }
                if (found == null && !hasTarget && !cameraUnavailable) {
                    QrViewfinder(found = false, modifier = Modifier.align(Alignment.Center))
                }
                QrStatusPill(
                    text = found?.let { formatQrFoundStatus(QrPayloadParser.parse(it.raw)) }
                        ?: if (isReadingPhoto) "Reading photo…" else if (hasTarget) "QR found · hold steady" else "Point at a QR code",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                )
            } else {
                QrPermissionPrompt(
                    status = permissionStatus,
                    onAllow = onAllow,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // Fully qualified: the plain overload, not the ColumnScope one from the outer Column.
            androidx.compose.animation.AnimatedVisibility(
                visible = found != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = fadeOut(),
            ) {
                found?.let { item ->
                    QrFoundCard(
                        item = item,
                        onDetails = onOpenDetails,
                        onScanAgain = onDismissFound,
                        onMessage = onMessage,
                    )
                }
            }
        }

        QrScanBottomBar(
            onPickPhoto = {
                pickingPhoto = true
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onOpenHistory = onOpenHistory,
        )
    }
}

@Composable
private fun QrScanHeader(
    torchAvailable: Boolean,
    torchEnabled: Boolean,
    onToggleTorch: () -> Unit,
    onClose: () -> Unit,
    onSelectCreate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Close",
                tint = Color.White,
            )
        }
        Text(
            text = "Scan QR code",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (torchAvailable) {
            Surface(
                onClick = onToggleTorch,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        stateDescription = if (torchEnabled) "On" else "Off"
                    },
                shape = CircleShape,
                color = if (torchEnabled) ScanTeal else Color.White.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                        contentDescription = "Torch on/off",
                        tint = if (torchEnabled) Color(0xFF042F2A) else Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        QrPillButton(
            label = "Create",
            icon = Icons.Outlined.QrCode2,
            onClick = onSelectCreate,
            dark = true,
        )
    }
}

/** Corner brackets (white, teal once a code is found) and a sweeping line while searching. */
@Composable
private fun QrViewfinder(
    found: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "qr-scanline")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "qr-sweep",
    )
    val cornerColor = if (found) ScanTeal else Color.White
    Box(
        modifier = modifier
            .size(240.dp)
            .background(
                color = if (found) ScanTeal.copy(alpha = 0.12f) else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f
            val length = 34.dp.toPx()
            val radius = 16.dp.toPx()
            val right = this.size.width - inset
            val bottom = this.size.height - inset
            val cornerStroke = Stroke(width = stroke, cap = StrokeCap.Round)

            // Each bracket is drawn in full: nothing clips the Canvas, so the tips at the edge survive.
            // dx and dy are the directions the horizontal and vertical arms run from the corner.
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                val path = Path().apply {
                    moveTo(x, y + dy * length)
                    lineTo(x, y + dy * radius)
                    arcTo(
                        rect = Rect(center = Offset(x + dx * radius, y + dy * radius), radius = radius),
                        startAngleDegrees = if (dx > 0f) 180f else 0f,
                        sweepAngleDegrees = 90f * dx * dy,
                        forceMoveTo = false,
                    )
                    lineTo(x + dx * length, y)
                }
                drawPath(path = path, color = cornerColor, style = cornerStroke)
            }
            corner(inset, inset, dx = 1f, dy = 1f)
            corner(right, inset, dx = -1f, dy = 1f)
            corner(right, bottom, dx = -1f, dy = -1f)
            corner(inset, bottom, dx = 1f, dy = -1f)

            if (!found) {
                val y = this.size.height * (0.08f + 0.80f * sweep)
                drawLine(
                    color = ScanTeal,
                    start = Offset(12.dp.toPx(), y),
                    end = Offset(this.size.width - 12.dp.toPx(), y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun QrStatusPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(ScanTeal),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun QrPermissionPrompt(
    status: CameraPermissionStatus,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocked = CameraPermissionSupport.shouldOpenSettings(status)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.CameraAlt,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = if (blocked) "Camera permission is blocked" else "Allow camera to scan",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (blocked) {
                "Open Settings, tap Permissions, and turn Camera on for Scanly."
            } else {
                "The camera is used only while this screen is open. \"From photo\" works without it."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        ToolPrimaryButton(
            text = if (blocked) "Open settings" else "Allow camera",
            onClick = onAllow,
        )
    }
}

/** Card over the camera for a found code: type, headline, primary action, copy and share. */
@Composable
private fun QrFoundCard(
    item: QrResultItem,
    onDetails: () -> Unit,
    onScanAgain: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val payload: QrPayload = remember(item.raw) { QrPayloadParser.parse(item.raw) }
    val primary = qrPrimaryActionFor(payload)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToolIconWell(
                    icon = qrKindIcon(payload),
                    size = 44.dp,
                    iconSize = 22.dp,
                    accent = true,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = payload.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = payload.headline,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDetails, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Details",
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolPrimaryButton(
                    text = primary.cardLabel,
                    onClick = { runQrPrimaryAction(context, payload)?.let(onMessage) },
                    icon = qrPrimaryActionIcon(primary),
                    modifier = Modifier.weight(1.5f),
                )
                if (primary != QrPrimaryAction.CopyText) {
                    ToolSecondaryButton(
                        text = "Copy",
                        onClick = { onMessage(copyQrText(context, "QR code", item.raw)) },
                        icon = Icons.Outlined.ContentCopy,
                        modifier = Modifier.weight(1f),
                    )
                }
                ToolSecondaryButton(
                    text = "Share",
                    onClick = { shareQrText(context, item.raw)?.let(onMessage) },
                    icon = Icons.Outlined.Share,
                    modifier = Modifier.weight(1f),
                )
            }

            ToolTextAction(
                label = "Scan again",
                onClick = onScanAgain,
                icon = Icons.Outlined.QrCodeScanner,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun QrScanBottomBar(
    onPickPhoto: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QrPillButton(
            label = "From photo",
            icon = Icons.Outlined.Image,
            onClick = onPickPhoto,
            dark = true,
        )
        Spacer(modifier = Modifier.weight(1f))
        QrPillButton(
            label = "History",
            icon = Icons.Outlined.History,
            onClick = onOpenHistory,
            dark = true,
        )
    }
}

/**
 * CameraX preview with ML Kit detection. Detection only runs while [detecting] is true, and
 * a visible lock precedes capture; stale callbacks cannot publish into a new scan.
 */
@Composable
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
private fun QrCameraPreview(
    onBarcode: (String, QrCapturePreview?) -> Unit,
    detecting: Boolean,
    capture: QrCapturePreview?,
    onTargetChanged: (Boolean) -> Unit,
    torchEnabled: Boolean,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    onCameraError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentDetecting by rememberUpdatedState(detecting)
    val currentOnTargetChanged by rememberUpdatedState(onTargetChanged)
    val currentOnTorchAvailability by rememberUpdatedState(onTorchAvailabilityChanged)
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    val armed = remember { AtomicBoolean(detecting) }
    val disposed = remember { AtomicBoolean(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val scanner = remember { createQrBarcodeScanner() }
    val tracker = remember { QrCaptureTracker() }
    var corners by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var generation by remember { mutableStateOf(0) }
    var active by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val currentActive by rememberUpdatedState(active)
    val previewUseCase = remember { Preview.Builder().build() }
    var analysisUseCase by remember { mutableStateOf<ImageAnalysis?>(null) }


    LaunchedEffect(detecting, active) {
        generation++
        tracker.reset()
        armed.set(detecting && active)
        if (detecting) {
            corners = emptyList()
            currentOnTargetChanged(false)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_PAUSE) {
                active = event == Lifecycle.Event.ON_RESUME
                generation++
                armed.set(active && currentDetecting)
                tracker.reset()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(scanner) {
        if (scanner == null) currentOnCameraError()
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            boundCamera?.cameraControl?.enableTorch(false)
            analysisUseCase?.clearAnalyzer()
            analysisUseCase?.let { cameraProvider?.unbind(previewUseCase, it) }
            scanner?.close()
        }
    }

    LaunchedEffect(boundCamera, torchEnabled, detecting, active) {
        val camera = boundCamera ?: return@LaunchedEffect
        if (camera.cameraInfo.hasFlashUnit()) {
            camera.cameraControl.enableTorch(torchEnabled && detecting && active)
        }
    }

    Box(modifier = modifier.clip(RoundedCornerShape(0.dp))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    setOnTouchListener { view, event ->
                        if (event.action == MotionEvent.ACTION_UP && currentDetecting) {
                            view.performClick()
                            val point = meteringPointFactory.createPoint(event.x, event.y)
                            boundCamera?.cameraControl?.startFocusAndMetering(
                                FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).build(),
                            )
                        }
                        true
                    }
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener(
                    {
                        if (disposed.get()) return@addListener
                        val provider = runCatching { cameraProviderFuture.get() }
                            .getOrElse {
                                currentOnCameraError()
                                return@addListener
                            }
                        cameraProvider = provider
                        val preview = previewUseCase.also {
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
                        analysisUseCase = analysis
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                            if (disposed.get() || !armed.get() || !currentDetecting || !currentActive) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val barcodeScanner = scanner ?: run {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val frameGeneration = generation
                            val sourceTransform = ImageProxyTransformFactory().apply {
                                isUsingRotationDegrees = true
                            }.getOutputTransform(imageProxy)
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees,
                            )
                            runCatching { barcodeScanner.process(image) }
                                .getOrElse {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                .addOnSuccessListener { barcodes ->
                                    if (disposed.get() || !armed.get() || !currentDetecting ||
                                        !currentActive || frameGeneration != generation
                                    ) return@addOnSuccessListener
                                    val targetTransform = previewView.outputTransform
                                    val targets = if (targetTransform == null) emptyList() else barcodes.mapNotNull { barcode ->
                                        val value = barcode.rawValue?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                                        val points = barcode.cornerPoints?.takeIf { it.size == 4 } ?: return@mapNotNull null
                                        val mapped = points.flatMap { listOf(it.x.toFloat(), it.y.toFloat()) }.toFloatArray()
                                        CoordinateTransform(sourceTransform, targetTransform).mapPoints(mapped)
                                        val positions = mapped.toList().chunked(2).map { Offset(it[0], it[1]) }
                                        if (positions.any { !it.x.isFinite() || !it.y.isFinite() ||
                                                it.x < 0 || it.y < 0 || it.x > previewView.width || it.y > previewView.height }) {
                                            return@mapNotNull null
                                        }
                                        value to positions
                                    }
                                    val target = targets.minByOrNull { (_, points) ->
                                        val center = points.reduce { a, b -> a + b } / 4f
                                        (center - Offset(previewView.width / 2f, previewView.height / 2f)).getDistanceSquared()
                                    }
                                    corners = target?.second.orEmpty()
                                    currentOnTargetChanged(target != null)
                                    if (tracker.update(target?.first, SystemClock.elapsedRealtime()) && target != null) {
                                        // Freeze the analyzed frame, not a newer preview frame that may have moved.
                                        val frame = runCatching {
                                            val source = imageProxy.toBitmap()
                                            try {
                                                val matrix = Matrix()
                                                CoordinateTransform(
                                                    ImageProxyTransformFactory().getOutputTransform(imageProxy),
                                                    requireNotNull(targetTransform),
                                                ).transform(matrix)
                                                Bitmap.createBitmap(previewView.width, previewView.height, Bitmap.Config.ARGB_8888).also {
                                                    android.graphics.Canvas(it).drawBitmap(source, matrix, null)
                                                }
                                            } finally {
                                                source.recycle()
                                            }
                                        }.getOrNull()
                                        if (frame != null && armed.compareAndSet(true, false)) {
                                            currentOnBarcode(target.first, QrCapturePreview(frame, corners))
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    if (!disposed.get() && frameGeneration == generation && armed.get()) {
                                        tracker.reset()
                                        corners = emptyList()
                                        currentOnTargetChanged(false)
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                        previewView.doOnLayout {
                            if (disposed.get()) return@doOnLayout
                            try {
                                val camera = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    UseCaseGroup.Builder()
                                        .setViewPort(requireNotNull(previewView.viewPort))
                                        .addUseCase(preview)
                                        .addUseCase(analysis)
                                        .build(),
                                )
                                boundCamera = camera
                                currentOnTorchAvailability(camera.cameraInfo.hasFlashUnit())
                            } catch (_: Exception) {
                                boundCamera = null
                                currentOnTorchAvailability(false)
                                currentOnCameraError()
                            }
                        }
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
                previewView
            },
        )
        if (!detecting && capture == null) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
        capture?.bitmap?.let { bitmap ->
            Image(bitmap.asImageBitmap(), contentDescription = "Captured QR code",
                modifier = Modifier.fillMaxSize().background(Color.Black), contentScale = ContentScale.Fit)
        }
        val displayedCorners = capture?.corners ?: corners
        if (displayedCorners.size == 4 && (detecting || capture != null)) {
            Canvas(Modifier.fillMaxSize()) {
                val corners = capture?.let { frozen ->
                    val scale = minOf(size.width / frozen.bitmap.width, size.height / frozen.bitmap.height)
                    val origin = Offset((size.width - frozen.bitmap.width * scale) / 2f, (size.height - frozen.bitmap.height * scale) / 2f)
                    displayedCorners.map { origin + it * scale }
                } ?: displayedCorners
                val outline = Path().apply {
                    moveTo(corners[0].x, corners[0].y)
                    corners.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(outline, ScanTeal.copy(alpha = 0.12f))
                corners.forEachIndexed { index, corner ->
                    val previous = corners[(index + 3) % 4]
                    val next = corners[(index + 1) % 4]
                    drawLine(ScanTeal, corner, corner + (previous - corner) * 0.24f, 3.dp.toPx(), StrokeCap.Round)
                    drawLine(ScanTeal, corner, corner + (next - corner) * 0.24f, 3.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}
