package `in`.c1ph3rj.scanly.feature.sign.create

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.editing.InkStroke
import `in`.c1ph3rj.scanly.core.editing.InkStrokeBuilder
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.SignInk
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import `in`.c1ph3rj.scanly.feature.tools.ToolBottomBar
import `in`.c1ph3rj.scanly.feature.tools.ToolCompactHeader
import `in`.c1ph3rj.scanly.feature.tools.ToolFieldLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolNote
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSegmented
import `in`.c1ph3rj.scanly.feature.tools.ToolTextAction
import `in`.c1ph3rj.scanly.feature.tools.ToolCard
import kotlin.math.ceil
import kotlinx.coroutines.flow.collect

/** Pad width / height in portrait; the same for signatures and initials. */
private const val PadAspectRatio = 1.3f

/** Height kept free under the pad for the pen-weight row. */
private val PenRowAllowance = 72.dp

/** Smallest pad height, so the pad stays usable on a narrow phone. */
private val MinPadHeight = 200.dp

private val CheckerCell = 8.dp
private val CheckerBase = Color.White
private val CheckerTint = Color(0xFFF1F5F9)
private val PreviewShape = RoundedCornerShape(16.dp)
private val SourceIconCircle = 40.dp
private val SourceRowHeight = 64.dp
private val ImageMimeTypes = arrayOf("image/jpeg", "image/png", "image/webp")

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
    val scannedDocuments by viewModel.scannedDocuments.collectAsStateWithLifecycle()
    val scannedDocumentId by viewModel.scannedDocumentId.collectAsStateWithLifecycle()
    val scannedPages by viewModel.scannedPages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val style = remember(density, uiState.penWidth) {
        InkStrokeBuilder.Style(baseWidth = with(density) { uiState.penWidth.baseDp.dp.toPx() })
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
        scannedDocuments = scannedDocuments,
        scannedDocumentId = scannedDocumentId,
        scannedPages = scannedPages,
        onBack = onNavigateUp,
        onKindChange = viewModel::setKind,
        onModeChange = viewModel::setMode,
        onPenWidthChange = viewModel::setPenWidth,
        onStrokeFinished = viewModel::addStroke,
        onUndo = viewModel::undo,
        onClear = viewModel::clear,
        onImagePicked = viewModel::onImagePicked,
        onClearUpload = viewModel::clearUpload,
        onScannedDocumentSelect = viewModel::selectScannedDocument,
        onScannedPagePicked = viewModel::onScannedPagePicked,
        onSensitivityChange = viewModel::setSensitivity,
        onSave = viewModel::save,
    )
}

/**
 * Stateless create screen. A kind switch sits above two method tabs: Draw shows the signing pad
 * with a pen-width picker, and Upload picks a photo, a file or a scanned page, then previews the
 * extracted ink with a sensitivity slider. Save renders the pad into an ink mask first when drawing.
 *
 * @param onSave Receives the rendered ink mask in Draw mode, or null in Upload mode.
 * @param scannedDocuments Documents offered by the scanned-page picker.
 * @param scannedDocumentId Document whose pages the scanned-page picker is showing, if any.
 * @param scannedPages Pages of [scannedDocumentId] offered by the scanned-page picker.
 * @param onScannedDocumentSelect Opens a scanned document (its id) or goes back to the list (null).
 * @param onScannedPagePicked Uses a page already scanned in Scanly as the upload source.
 * @param onClearUpload Discards the picked image and returns to the upload source list.
 */
@Composable
fun SignatureCreateScreen(
    uiState: SignatureCreateUiState,
    style: InkStrokeBuilder.Style,
    snackbarHostState: SnackbarHostState,
    scannedDocuments: List<ScanDocument>,
    scannedDocumentId: String?,
    scannedPages: List<ScanPage>,
    onBack: () -> Unit,
    onKindChange: (SignatureKind) -> Unit,
    onModeChange: (SignatureCreateMode) -> Unit,
    onPenWidthChange: (SignaturePenWidth) -> Unit,
    onStrokeFinished: (InkStroke) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onImagePicked: (String) -> Unit,
    onClearUpload: () -> Unit,
    onScannedDocumentSelect: (String?) -> Unit,
    onScannedPagePicked: (ScanPage) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onSave: (Bitmap?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isInitials = uiState.kind == SignatureKind.INITIALS
    val inkColor = Color(SignInk.Default.argb)
    var showScannedSheet by rememberSaveable { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onImagePicked(uri.toString())
    }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImagePicked(uri.toString())
    }
    val choosePhoto: () -> Unit = {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val chooseFile: () -> Unit = {
        filePicker.launch(ImageMimeTypes)
    }
    val openScanned: () -> Unit = {
        showScannedSheet = true
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
        // Header and save bar span the screen; the pad and its controls keep a signing-sized
        // column on tablets instead of a 1000 dp strip.
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ToolCompactHeader(
                title = if (isInitials) "New initials" else "New signature",
                onBack = onBack,
            )
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            ) {
                ToolSegmented(
                    options = SignatureKind.entries,
                    selected = uiState.kind,
                    onSelect = onKindChange,
                    label = { it.label },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PrimaryTabRow(
                selectedTabIndex = uiState.mode.ordinal,
                modifier = Modifier.widthIn(max = MaxContentWidth),
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                SignatureCreateMode.entries.forEach { mode ->
                    Tab(
                        selected = uiState.mode == mode,
                        onClick = { onModeChange(mode) },
                        text = { Text(text = mode.tabLabel()) },
                        icon = { Icon(imageVector = mode.tabIcon(), contentDescription = null) },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                when (uiState.mode) {
                    SignatureCreateMode.Draw -> SignatureDrawArea(
                        uiState = uiState,
                        inkColor = inkColor,
                        style = style,
                        caption = if (isInitials) "Write your initials above the line" else "Sign above the line",
                        onStrokeFinished = onStrokeFinished,
                        onUndo = onUndo,
                        onClear = onClear,
                        onPenWidthChange = onPenWidthChange,
                    )
                    SignatureCreateMode.Upload -> SignatureUploadArea(
                        uiState = uiState,
                        inkColor = inkColor,
                        isInitials = isInitials,
                        onChoosePhoto = choosePhoto,
                        onChooseFile = chooseFile,
                        onOpenScanned = openScanned,
                        onClearUpload = onClearUpload,
                        onSensitivityChange = onSensitivityChange,
                    )
                }
            }
            ToolBottomBar {
                ToolPrimaryButton(
                    text = if (isInitials) "Save initials" else "Save signature",
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

    if (showScannedSheet) {
        ScannedPagePickerSheet(
            documents = scannedDocuments,
            selectedDocumentId = scannedDocumentId,
            pages = scannedPages,
            onSelectDocument = onScannedDocumentSelect,
            onPageClick = { page ->
                showScannedSheet = false
                onScannedPagePicked(page)
            },
            onDismiss = {
                showScannedSheet = false
                onScannedDocumentSelect(null)
            },
        )
    }
}

private val MaxContentWidth = 720.dp

private fun SignatureCreateMode.tabLabel(): String = when (this) {
    SignatureCreateMode.Draw -> "Draw"
    SignatureCreateMode.Upload -> "Upload"
}

private fun SignatureCreateMode.tabIcon(): ImageVector = when (this) {
    SignatureCreateMode.Draw -> Icons.Outlined.Draw
    SignatureCreateMode.Upload -> Icons.Outlined.FileUpload
}

/**
 * Signing pad anchored to the top of the free space. In portrait its height follows the kind's
 * aspect ratio (at least [MinPadHeight]); in landscape it takes all the height left after the pen
 * row. A pen-width picker sits under the pad, and a tip is shown in portrait only.
 */
@Composable
private fun SignatureDrawArea(
    uiState: SignatureCreateUiState,
    inkColor: Color,
    style: InkStrokeBuilder.Style,
    caption: String,
    onStrokeFinished: (InkStroke) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onPenWidthChange: (SignaturePenWidth) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        // One roomy pad for signatures and initials alike, leaving space for the pen row.
        val padHeight = minOf(maxWidth / PadAspectRatio, maxHeight - PenRowAllowance).coerceAtLeast(MinPadHeight)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SignaturePad(
                strokes = uiState.strokes,
                onStrokeFinished = onStrokeFinished,
                inkColor = inkColor,
                style = style,
                onUndo = onUndo,
                onClear = onClear,
                caption = caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (landscape) Modifier.weight(1f) else Modifier.height(padHeight)),
            )
            PenWidthRow(selected = uiState.penWidth, onSelect = onPenWidthChange)
        }
    }
}

/** "Pen" label followed by the three thickness choices, each with a dot that grows with the pen. */
@Composable
private fun PenWidthRow(
    selected: SignaturePenWidth,
    onSelect: (SignaturePenWidth) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolFieldLabel(text = "Pen", muted = true, modifier = Modifier.padding(end = 4.dp))
        SignaturePenWidth.entries.forEach { width ->
            PenChip(
                width = width,
                selected = width == selected,
                onClick = { onSelect(width) },
            )
        }
    }
}

/** Pill chip with a dot sized to its pen. Selected = accent-soft fill with an accent border. */
@Composable
private fun PenChip(
    width: SignaturePenWidth,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dotSize = when (width) {
        SignaturePenWidth.FINE -> 4.dp
        SignaturePenWidth.MEDIUM -> 7.dp
        SignaturePenWidth.BOLD -> 10.dp
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .background(MaterialTheme.colorScheme.onSurface, CircleShape),
            )
            Text(
                text = width.label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Upload flow: a source list until an image is chosen (Photos, Files or a scanned page), then the
 * extracted ink on a light checkerboard tinted with the ink colour, plus sensitivity and a way to
 * choose another image.
 */
@Composable
private fun SignatureUploadArea(
    uiState: SignatureCreateUiState,
    inkColor: Color,
    isInitials: Boolean,
    onChoosePhoto: () -> Unit,
    onChooseFile: () -> Unit,
    onOpenScanned: () -> Unit,
    onClearUpload: () -> Unit,
    onSensitivityChange: (Float) -> Unit,
) {
    if (uiState.uploadUri == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToolFieldLabel(
                text = if (isInitials) "Choose where your initials are" else "Choose where your signature is",
            )
            SignatureSourceRow(
                icon = Icons.Outlined.PhotoLibrary,
                title = "Photos",
                caption = "Pick a photo of your signature",
                onClick = onChoosePhoto,
            )
            SignatureSourceRow(
                icon = Icons.Outlined.FolderOpen,
                title = "Files",
                caption = "JPG or PNG from your phone",
                onClick = onChooseFile,
            )
            SignatureSourceRow(
                icon = Icons.Outlined.DocumentScanner,
                title = "Scanned page",
                caption = "Use a page you scanned in Scanly",
                onClick = onOpenScanned,
            )
            ToolNote(text = "Scanly removes the paper background and keeps just the ink.")
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
                    .clip(PreviewShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PreviewShape),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Fainter",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Bolder",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ToolTextAction(
                label = "Choose another",
                onClick = onClearUpload,
                icon = Icons.Outlined.Image,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** One upload source: a tinted circle with the icon, a title and caption, and a chevron. */
@Composable
private fun SignatureSourceRow(
    icon: ImageVector,
    title: String,
    caption: String,
    onClick: () -> Unit,
) {
    ToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(SourceRowHeight),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(SourceIconCircle)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Light checkerboard that shows through transparent ink masks. Always light, in every theme. */
@Composable
private fun CheckerboardBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(color = CheckerBase)
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
