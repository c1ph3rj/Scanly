package `in`.c1ph3rj.scanly.feature.tools

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ImageImportSupport
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.feature.components.DocumentTitleDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyImportProgressOverlay
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
    // Survive rotation: do not re-open the title dialog after a config change.
    var createDialogVisible by rememberSaveable { mutableStateOf(false) }
    val windowSizeInfo = rememberWindowSizeInfo()
    val gridColumns = if (windowSizeInfo.isTablet) 3 else 2

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
            Column(
                modifier = Modifier
                    .then(
                        if (windowSizeInfo.isTablet) {
                            Modifier.widthIn(max = windowSizeInfo.contentMaxWidth)
                        } else {
                            Modifier
                        },
                    )
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = windowSizeInfo.horizontalPadding)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ToolsHeader()
                ToolsSection(label = "Scan") {
                    ToolsTileGrid(
                        tools = scanTools,
                        columns = 1,
                        enabled = { true },
                        onClick = { tool -> onHubTool(tool, onScan = { createDialogVisible = true }, onImport = onImport, onOpenTool = onOpenTool) },
                    )
                }
                ToolsSection(label = "PDF") {
                    ToolsTileGrid(
                        tools = pdfTools,
                        columns = gridColumns,
                        enabled = { tool -> tool.id != ToolActionId.Import || !uiState.isImporting },
                        onClick = { tool -> onHubTool(tool, onScan = {}, onImport = onImport, onOpenTool = onOpenTool) },
                    )
                }
                ToolsSection(label = "Utilities") {
                    ToolsTileGrid(
                        tools = utilityTools,
                        columns = gridColumns,
                        enabled = { true },
                        onClick = { tool -> onHubTool(tool, onScan = {}, onImport = onImport, onOpenTool = onOpenTool) },
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
}

/** Routes a tapped hub tile: Scan opens the title dialog, Import the gallery, others a route. */
private fun onHubTool(
    tool: ToolItem,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onOpenTool: (String) -> Unit,
) {
    when (tool.id) {
        ToolActionId.Scan -> onScan()
        ToolActionId.Import -> onImport()
        else -> tool.route?.let(onOpenTool)
    }
}

@Composable
private fun ToolsHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "Tools",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.6).sp,
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Everything runs offline, on this phone",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolsSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolSectionLabel(
            text = label,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/** Lays [tools] out in rows of [columns] equal-width tiles; a short last row keeps its widths. */
@Composable
private fun ToolsTileGrid(
    tools: List<ToolItem>,
    columns: Int,
    enabled: (ToolItem) -> Boolean,
    onClick: (ToolItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tools.chunked(columns.coerceAtLeast(1)).forEach { rowTools ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowTools.forEach { tool ->
                    ToolsHubTile(
                        tool = tool,
                        enabled = enabled(tool),
                        onClick = { onClick(tool) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowTools.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 64dp hub tile: 38dp icon well, 14.5sp title and 12sp caption. */
@Composable
private fun ToolsHubTile(
    tool: ToolItem,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolCard(
        modifier = modifier
            .height(64.dp)
            .alpha(if (enabled) 1f else 0.5f),
        onClick = if (enabled) onClick else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToolIconWell(icon = tool.icon, accent = tool.accent)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp, lineHeight = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tool.caption,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
