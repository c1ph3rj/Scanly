package `in`.c1ph3rj.scanly.feature.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ImageImportSupport
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.feature.components.DocumentTitleDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyImportProgressOverlay
import `in`.c1ph3rj.scanly.feature.components.ScanlyTabScreenHeader
import `in`.c1ph3rj.scanly.navigation.ToolsQrDestination
import androidx.activity.compose.BackHandler
import kotlin.math.ceil
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ToolsRoute(
    onOpenDocument: (String) -> Unit,
    onOpenScanSession: (String) -> Unit,
    onOpenTool: (String) -> Unit,
) {
    val viewModel: ToolsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val importImagesLauncher = rememberLauncherForActivityResult(
        contract = ImageImportSupport.pickMultipleVisualMediaContract(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImagesAsDocument(uris)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ToolsEvent.OpenDocument -> onOpenDocument(event.documentId)
                is ToolsEvent.OpenScanSession -> onOpenScanSession(event.documentId)
                is ToolsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    ToolsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onScan = viewModel::createDocumentForScan,
        onImport = {
            importImagesLauncher.launch(ImageImportSupport.createPickRequest())
        },
        onOpenTool = onOpenTool,
        onSuggestTitle = viewModel::suggestDocumentTitle,
    )
}

@Composable
fun ToolsScreen(
    uiState: ToolsUiState,
    snackbarHostState: SnackbarHostState,
    onScan: (String) -> Unit,
    onImport: () -> Unit,
    onOpenTool: (String) -> Unit,
    onSuggestTitle: suspend (DocumentTitleFormat) -> String,
) {
    // Survive rotation — do not re-open closed sheets/dialogs after config change.
    var createDialogVisible by rememberSaveable { mutableStateOf(false) }
    var showQrModeSheet by rememberSaveable { mutableStateOf(false) }
    // Tallest tile measured across all grids on this page; every tile is stretched to this height.
    // Every tile takes the tallest tile's height; reset when the width (and so wrapping) changes.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    var tallestTilePx by remember(screenWidthDp) { mutableIntStateOf(0) }
    val onTileMeasured: (Int) -> Unit = { h -> if (h > tallestTilePx) tallestTilePx = h }
    val windowSizeInfo = rememberWindowSizeInfo()
    val gridColumns = if (windowSizeInfo.isTablet) {
        windowSizeInfo.toolGridColumns.coerceAtLeast(3)
    } else {
        2
    }

    BackHandler(enabled = uiState.isImporting) { /* block back while processing */ }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ScanlyTestTags.TOOLS_SCREEN),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = if (windowSizeInfo.isTablet) {
                    Modifier
                        .widthIn(max = windowSizeInfo.contentMaxWidth)
                        .fillMaxWidth()
                } else {
                    Modifier.fillMaxSize()
                },
                contentPadding = PaddingValues(
                    start = windowSizeInfo.horizontalPadding,
                    end = windowSizeInfo.horizontalPadding,
                    bottom = 24.dp,
                ),
            ) {
                item(key = "header") {
                    ScanlyTabScreenHeader(
                        title = "Tools",
                        subtitle = "Everything runs offline, on this phone",
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                item(key = "create_label") {
                    ToolsSectionLabel(text = "CREATE")
                }
                item(key = "create_grid") {
                    ToolsTileGrid(
                        tools = captureTools,
                        columns = gridColumns,
                        minTileHeightPx = tallestTilePx,
                        onTileMeasured = onTileMeasured,
                        onClick = { tool ->
                            when (tool.id) {
                                ToolActionId.Scan -> {
                                    createDialogVisible = true
                                }
                                ToolActionId.Import -> {
                                    onImport()
                                }
                                else -> Unit
                            }
                        },
                        enabled = { tool ->
                            tool.id != ToolActionId.Import || !uiState.isImporting
                        },
                    )
                }
                item(key = "pdf_label") {
                    ToolsSectionLabel(text = "PDF")
                }
                item(key = "pdf_grid") {
                    ToolsTileGrid(
                        tools = pdfTools,
                        columns = gridColumns,
                        minTileHeightPx = tallestTilePx,
                        onTileMeasured = onTileMeasured,
                        onClick = { tool -> tool.route?.let(onOpenTool) },
                    )
                }
                item(key = "utilities_label") {
                    ToolsSectionLabel(text = "UTILITIES")
                }
                item(key = "utilities_grid") {
                    ToolsTileGrid(
                        tools = utilityTools,
                        columns = gridColumns,
                        minTileHeightPx = tallestTilePx,
                        onTileMeasured = onTileMeasured,
                        onClick = { showQrModeSheet = true },
                    )
                }
            }

            if (uiState.isImporting) {
                ScanlyImportProgressOverlay(
                    current = uiState.importCurrent,
                    total = uiState.importTotal,
                    stageLabel = uiState.importStageLabel.ifBlank { "Working on your photos" },
                )
            }
        }
    }

    if (createDialogVisible && !uiState.isImporting) {
        DocumentTitleDialog(
            title = "New scan",
            initialValue = "",
            confirmLabel = "Scan",
            onDismiss = { createDialogVisible = false },
            onConfirm = { value ->
                createDialogVisible = false
                onScan(value)
            },
            onSuggestTitle = onSuggestTitle,
            autoFillSuggestedName = true,
        )
    }

    if (showQrModeSheet && !uiState.isImporting) {
        QrModePickerSheet(
            onDismiss = { showQrModeSheet = false },
            onChooseScan = {
                showQrModeSheet = false
                onOpenTool(ToolsQrDestination.route("scan"))
            },
            onChooseGenerate = {
                showQrModeSheet = false
                onOpenTool(ToolsQrDestination.route("generate"))
            },
        )
    }
}

@Composable
private fun ToolsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 20.dp, bottom = 10.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ToolsTileGrid(
    tools: List<ToolItem>,
    columns: Int,
    onClick: (ToolItem) -> Unit,
    enabled: (ToolItem) -> Boolean = { true },
    minTileHeightPx: Int,
    onTileMeasured: (Int) -> Unit,
) {
    val columnCount = columns.coerceAtLeast(1)
    Layout(
        content = {
            tools.forEach { tool ->
                ToolTile(
                    tool = tool,
                    onClick = { onClick(tool) },
                    enabled = enabled(tool),
                    modifier = Modifier,
                )
            }
        },
    ) { measurables, constraints ->
        val spacing = 10.dp.roundToPx()
        val cellWidth = (constraints.maxWidth - spacing * (columnCount - 1)) / columnCount
        val natural = measurables.maxOfOrNull { it.maxIntrinsicHeight(cellWidth) } ?: 0
        onTileMeasured(natural)
        val cellHeight = maxOf(natural, minTileHeightPx)
        val placeables = measurables.map { it.measure(Constraints.fixed(cellWidth, cellHeight)) }
        val rows = ceil(measurables.size / columnCount.toFloat()).toInt()
        val height = if (rows == 0) 0 else rows * cellHeight + spacing * (rows - 1)
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(
                    x = (index % columnCount) * (cellWidth + spacing),
                    y = (index / columnCount) * (cellHeight + spacing),
                )
            }
        }
    }
}

@Composable
private fun ToolTile(
    tool: ToolItem,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 72.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .alpha(if (enabled) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrModePickerSheet(
    onDismiss: () -> Unit,
    onChooseScan: () -> Unit,
    onChooseGenerate: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "QR Code",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "What would you like to do?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QrModeOption(
                title = "Scan a code",
                subtitle = "Use your camera to read a QR code or barcode.",
                icon = Icons.Filled.CameraAlt,
                highlighted = true,
                onClick = onChooseScan,
            )
            QrModeOption(
                title = "Generate a code",
                subtitle = "Turn a link or short message into a QR image.",
                icon = Icons.Filled.QrCode2,
                highlighted = false,
                onClick = onChooseGenerate,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun QrModeOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = MaterialTheme.shapes.large,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (highlighted) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
