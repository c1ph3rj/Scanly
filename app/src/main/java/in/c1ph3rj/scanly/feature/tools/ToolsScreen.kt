package `in`.c1ph3rj.scanly.feature.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Description
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ImageImportSupport
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import `in`.c1ph3rj.scanly.feature.components.DocumentTitleDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyImportProgressOverlay
import `in`.c1ph3rj.scanly.feature.components.ScanlyTabScreenHeader
import `in`.c1ph3rj.scanly.navigation.ToolsQrDestination
import androidx.activity.compose.BackHandler
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
    onScan: (String, ScanMode) -> Unit,
    onImport: () -> Unit,
    onOpenTool: (String) -> Unit,
    onSuggestTitle: suspend (DocumentTitleFormat) -> String,
) {
    // Survive rotation — do not re-open closed sheets/dialogs after config change.
    var createDialogVisible by rememberSaveable { mutableStateOf(false) }
    var showQrModeSheet by rememberSaveable { mutableStateOf(false) }
    var selectedScanMode by rememberSaveable { mutableStateOf(ScanMode.DOCUMENT) }
    val windowSizeInfo = rememberWindowSizeInfo()

    BackHandler(enabled = uiState.isImporting) { /* block back while processing */ }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                    bottom = 28.dp,
                ),
            ) {
                item(key = "header") {
                    ScanlyTabScreenHeader(
                        title = "Tools",
                        subtitle = "Create a document, share a code, or finish a PDF.",
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                }
                item(key = "capture") {
                    CaptureWorkspace(
                        onScan = { createDialogVisible = true },
                        onImport = onImport,
                        selectedScanMode = selectedScanMode,
                        onScanModeSelected = { selectedScanMode = it },
                        importEnabled = !uiState.isImporting,
                        modifier = Modifier.padding(bottom = 32.dp),
                    )
                }
                item(key = "utilities") {
                    ToolSectionHeader(
                        title = "Quick utility",
                        subtitle = "Scan a code or make one to share.",
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    if (windowSizeInfo.useToolTwoPaneLayout) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 32.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ToolFeaturedCard(
                                tool = utilityTools.single(),
                                eyebrow = "QR CODE",
                                onClick = { showQrModeSheet = true },
                                modifier = Modifier.weight(1f),
                            )
                            ToolFeaturedCard(
                                tool = pdfTools.first { it.id == ToolActionId.PdfReader },
                                eyebrow = "START HERE",
                                onClick = {
                                    pdfTools.first { it.id == ToolActionId.PdfReader }.route?.let(onOpenTool)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        ToolFeaturedCard(
                            tool = utilityTools.single(),
                            eyebrow = "QR CODE",
                            onClick = { showQrModeSheet = true },
                            modifier = Modifier.padding(bottom = 32.dp),
                        )
                    }
                }
                item(key = "pdf") {
                    ToolSectionHeader(
                        title = "PDF workspace",
                        subtitle = "Open, combine, reduce, protect, or stamp PDFs.",
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    if (!windowSizeInfo.useToolTwoPaneLayout) {
                        ToolFeaturedCard(
                            tool = pdfTools.first { it.id == ToolActionId.PdfReader },
                            eyebrow = "START HERE",
                            onClick = {
                                pdfTools.first { it.id == ToolActionId.PdfReader }.route?.let(onOpenTool)
                            },
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    ToolGrid(
                        tools = pdfTools.filter { it.id != ToolActionId.PdfReader },
                        columns = windowSizeInfo.toolGridColumns,
                        onToolClick = { tool -> tool.route?.let(onOpenTool) },
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
            title = when (selectedScanMode) {
                ScanMode.DOCUMENT -> "New document scan"
                ScanMode.ID_CARD -> "New ID scan"
                ScanMode.BOOK -> "New book scan"
            },
            initialValue = "",
            confirmLabel = "Start scanning",
            onDismiss = { createDialogVisible = false },
            onConfirm = { value ->
                createDialogVisible = false
                onScan(value, selectedScanMode)
            },
            onSuggestTitle = onSuggestTitle,
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

@Composable
private fun CaptureWorkspace(
    onScan: () -> Unit,
    onImport: () -> Unit,
    selectedScanMode: ScanMode,
    onScanModeSelected: (ScanMode) -> Unit,
    importEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "CREATE A DOCUMENT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp),
        )
        ScanModeGrid(
            selectedMode = selectedScanMode,
            onModeSelected = onScanModeSelected,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CaptureActionGridCard(
                title = "Scan",
                subtitle = when (selectedScanMode) {
                    ScanMode.DOCUMENT -> "Capture any page"
                    ScanMode.ID_CARD -> "Capture both sides"
                    ScanMode.BOOK -> "Capture one spread"
                },
                icon = Icons.Filled.CameraAlt,
                highlighted = true,
                onClick = onScan,
                modifier = Modifier.weight(1f),
            )
            CaptureActionGridCard(
                title = "Import",
                subtitle = "Choose up to 10 photos",
                icon = Icons.Filled.PhotoLibrary,
                highlighted = false,
                onClick = onImport,
                enabled = importEnabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ScanModeGrid(
    selectedMode: ScanMode,
    onModeSelected: (ScanMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScanMode.entries.forEach { mode ->
            ScanModeGridCard(
                mode = mode,
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ScanModeGridCard(
    mode: ScanMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(118.dp)
            .semantics { this.selected = selected },
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = MaterialTheme.shapes.large,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = mode.icon,
                        contentDescription = null,
                        modifier = Modifier.size(23.dp),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (mode) {
                    ScanMode.DOCUMENT -> "Document"
                    ScanMode.ID_CARD -> "ID card"
                    ScanMode.BOOK -> "Book"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (mode) {
                    ScanMode.DOCUMENT -> "Any page"
                    ScanMode.ID_CARD -> "Front & back"
                    ScanMode.BOOK -> "Open spread"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CaptureActionGridCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    highlighted: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(116.dp),
        color = if (highlighted && enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else if (enabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    color = if (highlighted && enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = MaterialTheme.shapes.large,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (highlighted && enabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.58f,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val ScanMode.icon: ImageVector
    get() = when (this) {
        ScanMode.DOCUMENT -> Icons.Outlined.Description
        ScanMode.ID_CARD -> Icons.Outlined.Badge
        ScanMode.BOOK -> Icons.AutoMirrored.Outlined.MenuBook
    }

@Composable
private fun ToolSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolFeaturedCard(
    tool: ToolItem,
    eyebrow: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (container, onContainer) = accentColors(tool.accent)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                color = container,
                shape = MaterialTheme.shapes.large,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = onContainer,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

@Composable
private fun ToolGrid(
    tools: List<ToolItem>,
    onToolClick: (ToolItem) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    val columnCount = columns.coerceAtLeast(1)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tools.chunked(columnCount).forEach { rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowTools.forEach { tool ->
                    ToolGridCard(
                        tool = tool,
                        onClick = { onToolClick(tool) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columnCount - rowTools.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ToolGridCard(
    tool: ToolItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (container, onContainer) = accentColors(tool.accent)
    Surface(
        onClick = onClick,
        modifier = modifier.height(142.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = container,
                shape = MaterialTheme.shapes.large,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = onContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
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

@Composable
private fun accentColors(accent: ToolAccent): Pair<Color, Color> =
    when (accent) {
        ToolAccent.Primary ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ToolAccent.Secondary ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ToolAccent.Tertiary ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
