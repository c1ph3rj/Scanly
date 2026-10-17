package `in`.c1ph3rj.scanly.feature.sign.create

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.editing.InkStroke
import `in`.c1ph3rj.scanly.core.editing.InkStrokeBuilder
import `in`.c1ph3rj.scanly.domain.model.SignInk
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolCard
import `in`.c1ph3rj.scanly.feature.tools.ToolCompactHeader
import `in`.c1ph3rj.scanly.feature.tools.ToolFieldLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolIconWell
import `in`.c1ph3rj.scanly.feature.tools.ToolNote
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSecondaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSegmented
import `in`.c1ph3rj.scanly.feature.tools.ToolTextAction
import kotlin.math.ceil
import kotlinx.coroutines.flow.collect

private const val SignatureAspectRatio = 2.4f
private const val InitialsAspectRatio = 1.6f

/** Pen width on the pad, in dp. */
private val PenWidth = 3.2.dp

/** Height reserved under the pad for the Undo / Clear row, including its gap. */
private val ActionRowHeight = 64.dp

/** Smallest pad height, so the pad stays usable in very short windows. */
private val MinPadHeight = 60.dp

private val CheckerCell = 12.dp
private val CheckerTint = Color(0xFFE5E7EB)

/**
 * Navigation entry point for "New signature" / "New initials". Collects one-shot events:
 * [SignatureCreateEvent.Saved] closes the screen and [SignatureCreateEvent.Message] shows a snackbar.
 */
@Composable
fun SignatureCreateRoute(
    onNavigateUp: () -> Unit,
    viewModel: SignatureCreateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val style = remember(density) {
        InkStrokeBuilder.Style(baseWidth = with(density) { PenWidth.toPx() })
    }
    val currentOnNavigateUp by rememberUpdatedState(onNavigateUp)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SignatureCreateEvent.Saved -> currentOnNavigateUp()
                is SignatureCreateEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    SignatureCreateScreen(
        uiState = uiState,
        style = style,
        snackbarHostState = snackbarHostState,
        onBack = onNavigateUp,
        onKindChange = viewModel::setKind,
        onModeChange = viewModel::setMode,
        onStrokeFinished = viewModel::addStroke,
        onUndo = viewModel::undo,
        onClear = viewModel::clear,
        onImagePicked = viewModel::onImagePicked,
        onSensitivityChange = viewModel::setSensitivity,
        onSave = viewModel::save,
    )
}

/**
 * Stateless create screen. Draw mode shows the signing pad with Undo and Clear; Upload mode picks
 * a photo, previews the extracted ink and offers a sensitivity slider. Save renders the pad into an
 * ink mask first when drawing.
 *
 * @param onSave Receives the rendered ink mask in Draw mode, or null in Upload mode.
 */
@Composable
fun SignatureCreateScreen(
    uiState: SignatureCreateUiState,
    style: InkStrokeBuilder.Style,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onKindChange: (SignatureKind) -> Unit,
    onModeChange: (SignatureCreateMode) -> Unit,
    onStrokeFinished: (InkStroke) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onImagePicked: (String) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onSave: (Bitmap?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isInitials = uiState.kind == SignatureKind.INITIALS
    val inkColor = Color(SignInk.Default.argb)
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onImagePicked(uri.toString())
    }
    val choosePhoto: () -> Unit = {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val canSave = !uiState.isSaving && when (uiState.mode) {
        SignatureCreateMode.Draw -> uiState.strokes.isNotEmpty()
        SignatureCreateMode.Upload -> uiState.uploadPreview != null
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 96.dp),
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            ToolCompactHeader(
                title = if (isInitials) "New initials" else "New signature",
                onBack = onBack,
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToolSegmented(
                    options = SignatureKind.entries,
                    selected = uiState.kind,
                    onSelect = onKindChange,
                    label = { it.label },
                    modifier = Modifier.fillMaxWidth(),
                )
                ToolSegmented(
                    options = SignatureCreateMode.entries,
                    selected = uiState.mode,
                    onSelect = onModeChange,
                    label = { if (it == SignatureCreateMode.Draw) "Draw" else "Upload" },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (uiState.mode) {
                    SignatureCreateMode.Draw -> SignatureDrawArea(
                        uiState = uiState,
                        inkColor = inkColor,
                        style = style,
                        onStrokeFinished = onStrokeFinished,
                        onUndo = onUndo,
                        onClear = onClear,
                    )
                    SignatureCreateMode.Upload -> SignatureUploadArea(
                        uiState = uiState,
                        inkColor = inkColor,
                        onChoosePhoto = choosePhoto,
                        onSensitivityChange = onSensitivityChange,
                    )
                }
            }
            ToolBottomBar {
                ToolPrimaryButton(
                    text = "Save",
                    onClick = {
                        val mask = if (uiState.mode == SignatureCreateMode.Draw) {
                            renderInkMask(uiState.strokes, style)
                        } else {
                            null
                        }
                        onSave(mask)
                    },
                    enabled = canSave,
                    loading = uiState.isSaving,
                )
            }
        }
    }
}

/** Signing pad sized to the aspect ratio of the kind, fitted inside the free space. */
@Composable
private fun SignatureDrawArea(
    uiState: SignatureCreateUiState,
    inkColor: Color,
    style: InkStrokeBuilder.Style,
    onStrokeFinished: (InkStroke) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
) {
    val ratio = if (uiState.kind == SignatureKind.INITIALS) InitialsAspectRatio else SignatureAspectRatio
    val hasStrokes = uiState.strokes.isNotEmpty()
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val padHeightLimit = (maxHeight - ActionRowHeight).coerceAtLeast(MinPadHeight)
        val padWidth = minOf(maxWidth, padHeightLimit * ratio)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SignaturePad(
                strokes = uiState.strokes,
                onStrokeFinished = onStrokeFinished,
                inkColor = inkColor,
                style = style,
                modifier = Modifier.width(padWidth).aspectRatio(ratio),
            )
            Row(
                modifier = Modifier.width(padWidth),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolSecondaryButton(
                    text = "Undo",
                    onClick = onUndo,
                    enabled = hasStrokes,
                    icon = Icons.AutoMirrored.Outlined.Undo,
                    modifier = Modifier.weight(1f),
                )
                ToolSecondaryButton(
                    text = "Clear",
                    onClick = onClear,
                    enabled = hasStrokes,
                    icon = Icons.Outlined.Delete,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Upload flow: a picker card until a photo is chosen, then the extracted ink on a light
 * checkerboard tinted with the ink colour, plus sensitivity and a way to pick another photo.
 */
@Composable
private fun SignatureUploadArea(
    uiState: SignatureCreateUiState,
    inkColor: Color,
    onChoosePhoto: () -> Unit,
    onSensitivityChange: (Float) -> Unit,
) {
    if (uiState.uploadUri == null) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ToolCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ToolIconWell(
                        icon = Icons.Outlined.FileUpload,
                        size = 52.dp,
                        iconSize = 24.dp,
                        accent = true,
                    )
                    ToolFieldLabel("Choose a photo of your signature")
                }
            }
            ToolNote("Sign on white paper with a dark pen, then take a clear photo.")
            ToolPrimaryButton(
                text = "Choose photo",
                onClick = onChoosePhoto,
                icon = Icons.Outlined.Image,
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CheckerboardBackground(modifier = Modifier.matchParentSize())
                val preview = uiState.uploadPreview
                if (preview != null && !preview.isRecycled) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "Extracted signature preview",
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(inkColor),
                    )
                }
                if (uiState.isExtracting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            uiState.uploadError?.let { error ->
                ToolNote(text = error, warning = true)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolFieldLabel("Ink sensitivity")
                Slider(
                    value = uiState.sensitivity,
                    onValueChange = onSensitivityChange,
                    valueRange = SignatureCreateViewModel.MinSensitivity..SignatureCreateViewModel.MaxSensitivity,
                    enabled = !uiState.isSaving,
                )
            }
            ToolTextAction(
                label = "Choose another",
                onClick = onChoosePhoto,
                icon = Icons.Outlined.Image,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** Light checkerboard that shows through transparent ink masks. Always light, in every theme. */
@Composable
private fun CheckerboardBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(color = Color.White)
        val cell = CheckerCell.toPx()
        val columns = ceil(size.width / cell).toInt()
        val rows = ceil(size.height / cell).toInt()
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if ((row + column) % 2 == 1) {
                    drawRect(
                        color = CheckerTint,
                        topLeft = Offset(column * cell, row * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
    }
}
