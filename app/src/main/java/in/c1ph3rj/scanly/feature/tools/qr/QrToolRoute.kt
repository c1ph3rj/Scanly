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
import `in`.c1ph3rj.scanly.feature.components.shareExportArtifact
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
    var initialModeReady by remember(viewModel, initialMode) { mutableStateOf(false) }

    // Seed mode from nav only once — do not re-apply on rotation/recomposition.
    LaunchedEffect(viewModel, initialMode) {
        viewModel.applyInitialMode(initialMode)
        initialModeReady = true
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
    val mode = if (initialModeReady) uiState.mode else initialMode
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
                        mode = mode,
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
                    contentAlignment = if (layout.twoPane && mode == QrToolMode.Generate) {
                        Alignment.Center
                    } else {
                        Alignment.TopCenter
                    },
                ) {
                    when (mode) {
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
