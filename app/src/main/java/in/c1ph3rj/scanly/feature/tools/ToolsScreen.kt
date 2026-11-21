package `in`.c1ph3rj.scanly.feature.tools

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import `in`.c1ph3rj.scanly.core.ui.adaptiveColumnCount
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalDensity
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
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.TitleSuggestionOption
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import `in`.c1ph3rj.scanly.feature.components.DocumentTitleDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyImportProgressOverlay
import kotlinx.coroutines.flow.collectLatest
import `in`.c1ph3rj.scanly.R
import androidx.compose.ui.res.stringResource

@Composable
fun ToolsRoute(
    onOpenDocument: (String) -> Unit,
    onOpenScanSession: (String) -> Unit,
    onOpenTool: (String) -> Unit,
) {
    val viewModel: ToolsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val titleSuggestionOptions by viewModel.titleSuggestionOptions.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val importImagesLauncher = rememberLauncherForActivityResult(
        contract = ImageImportSupport.pickImagesContract(),
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
        titleSuggestionOptions = titleSuggestionOptions,
    )
}

@Composable
fun ToolsScreen(
    uiState: ToolsUiState,
    snackbarHostState: SnackbarHostState,
    onScan: (String, ScanMode) -> Unit,
    onImport: () -> Unit,
    onOpenTool: (String) -> Unit,
    onSuggestTitle: suspend (TitleSuggestionOption) -> String,
    titleSuggestionOptions: List<TitleSuggestionOption> = emptyList(),
) {
    // The mode of the scan whose title dialog is open; null when closed. Survives rotation.
    var pendingScanMode by rememberSaveable { mutableStateOf<ScanMode?>(null) }
    val windowSizeInfo = rememberWindowSizeInfo()
    val isCompactWidth = windowSizeInfo.widthClass == WindowWidthClass.Compact
    // Illustrated cards (picture over title and caption) wherever there is room; short windows
    // keep the compact row tiles so everything stays above the fold.
    val richCards = !windowSizeInfo.isHeightConstrained
    val onTool: (ToolItem) -> Unit = { tool ->
        onHubTool(tool, onScan = { mode -> pendingScanMode = mode }, onImport = onImport, onOpenTool = onOpenTool)
    }
    val pdfEnabled: (ToolItem) -> Boolean = { tool -> tool.id != ToolActionId.Import || !uiState.isImporting }

    BackHandler(enabled = uiState.isImporting) { /* block back while processing */ }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ScanlyTestTags.TOOLS_SCREEN),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            contentAlignment = Alignment.TopCenter,
        ) {
            val horizontalPadding = windowSizeInfo.horizontalPadding
            if (windowSizeInfo.useTwoPaneLayout) {
                // Wide: Scan and Utilities on the start side, the PDF toolkit grid beside them.
                val contentWidth = minOf(maxWidth, windowSizeInfo.wideContentMaxWidth)
                val rowWidth = contentWidth - horizontalPadding * 2
                val startPaneWidth = (rowWidth * 0.32f).coerceIn(240.dp, 380.dp)
                val gridWidth = rowWidth - startPaneWidth - ToolsPaneSpacing
                val pdfColumns = adaptiveColumnCount(
                    availableWidth = gridWidth,
                    minCellWidth = if (richCards) 150.dp else 190.dp,
                    minColumns = 2,
                    maxColumns = 3,
                )
                Column(
                    modifier = Modifier
                        .width(contentWidth)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = horizontalPadding)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ToolsHeader()
                    Row(horizontalArrangement = Arrangement.spacedBy(ToolsPaneSpacing)) {
                        Column(
                            modifier = Modifier.width(startPaneWidth),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            ToolsSection(label = stringResource(R.string.home_scan)) {
                                ToolsScanHero(
                                    tool = scanTools.first(),
                                    tall = richCards,
                                    onClick = { onTool(scanTools.first()) },
                                )
                                ToolsTileGrid(
                                    tools = scanModeTools,
                                    columns = 1,
                                    enabled = { true },
                                    onClick = onTool,
                                    showFullCaption = true,
                                )
                            }
                            ToolsSection(label = stringResource(R.string.tools_utilities)) {
                                ToolsTileGrid(tools = utilityTools, columns = 1, enabled = { true }, onClick = onTool)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            ToolsSection(label = "PDF") {
                                ToolsTileGrid(
                                    tools = pdfTools,
                                    columns = pdfColumns,
                                    enabled = pdfEnabled,
                                    onClick = onTool,
                                    rich = richCards,
                                )
                            }
                        }
                    }
                }
            } else {
                // Phones: two columns of row tiles. Tablet and foldable portrait: three columns of
                // taller cards under a prominent Scan card, filling the column.
                val gridColumns = if (isCompactWidth) 2 else 3
                val contentWidth = if (isCompactWidth) maxWidth else minOf(maxWidth, windowSizeInfo.contentMaxWidth)
                val scanModeColumns = if (richCards) {
                    adaptiveColumnCount(
                        availableWidth = contentWidth - horizontalPadding * 2,
                        minCellWidth = 144.dp * LocalDensity.current.fontScale.coerceAtLeast(1f),
                        maxColumns = 2,
                    )
                } else {
                    1
                }
                Column(
                    modifier = Modifier
                        .then(
                            if (isCompactWidth) {
                                Modifier
                            } else {
                                Modifier.widthIn(max = windowSizeInfo.contentMaxWidth)
                            },
                        )
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = horizontalPadding)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ToolsHeader()
                    ToolsSection(label = stringResource(R.string.home_scan)) {
                        if (!richCards) {
                            ToolsTileGrid(tools = scanTools, columns = 1, enabled = { true }, onClick = onTool)
                        } else {
                            ToolsScanHero(
                                tool = scanTools.first(),
                                tall = false,
                                onClick = { onTool(scanTools.first()) },
                            )
                        }
                        ToolsTileGrid(
                            tools = scanModeTools,
                            columns = scanModeColumns,
                            enabled = { true },
                            onClick = onTool,
                            rich = scanModeColumns == 2,
                            showFullCaption = true,
                        )
                    }
                    ToolsSection(label = "PDF") {
                        ToolsTileGrid(
                            tools = pdfTools,
                            columns = gridColumns,
                            enabled = pdfEnabled,
                            onClick = onTool,
                            rich = richCards,
                        )
                    }
                    ToolsSection(label = stringResource(R.string.tools_utilities)) {
                        ToolsTileGrid(
                            tools = utilityTools,
                            columns = gridColumns,
                            enabled = { true },
                            onClick = onTool,
                            rich = richCards,
                        )
                    }
                }
            }

            if (uiState.isImporting) {
                ScanlyImportProgressOverlay(
                    current = uiState.importCurrent,
                    total = uiState.importTotal,
                    stageLabel = uiState.importStageLabel.ifBlank { stringResource(R.string.document_detail_working_on_your_photos) },
                )
            }
        }
    }

    val dialogScanMode = pendingScanMode
    if (dialogScanMode != null && !uiState.isImporting) {
        DocumentTitleDialog(
            title = when (dialogScanMode) {
                ScanMode.DOCUMENT -> stringResource(R.string.home_new_scan)
                ScanMode.ID_CARD -> stringResource(R.string.tools_catalog_scan_id_card)
                ScanMode.BOOK -> stringResource(R.string.tools_catalog_scan_book)
            },
            initialValue = "",
            confirmLabel = stringResource(R.string.home_scan),
            onDismiss = { pendingScanMode = null },
            onConfirm = { value ->
                pendingScanMode = null
                onScan(value, dialogScanMode)
            },
            onSuggestTitle = onSuggestTitle,
            suggestionOptions = titleSuggestionOptions,
            autoFillSuggestedName = true,
        )
    }
}

/** Routes a tapped hub tile: Scan opens the title dialog, Import the gallery, others a route. */
private fun onHubTool(
    tool: ToolItem,
    onScan: (ScanMode) -> Unit,
    onImport: () -> Unit,
    onOpenTool: (String) -> Unit,
) {
    when (tool.id) {
        ToolActionId.Scan -> onScan(ScanMode.DOCUMENT)
        ToolActionId.ScanIdCard -> onScan(ScanMode.ID_CARD)
        ToolActionId.ScanBook -> onScan(ScanMode.BOOK)
        ToolActionId.Import -> onImport()
        else -> tool.route?.let(onOpenTool)
    }
}

@Composable
private fun ToolsHeader() {
    val compact = rememberWindowSizeInfo().isHeightConstrained
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = if (compact) 8.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_hub_sub_screens_tools),
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
            text = stringResource(R.string.tools_everything_runs_offline_on_this),
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
    /** Stacked art, title and caption cards instead of compact row tiles. */
    rich: Boolean = false,
    showFullCaption: Boolean = false,
) {
    val gap = if (rich) 12.dp else 10.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        tools.chunked(columns.coerceAtLeast(1)).forEach { rowTools ->
            Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                rowTools.forEach { tool ->
                    if (rich) {
                        ToolsFeatureCard(
                            tool = tool,
                            enabled = enabled(tool),
                            onClick = { onClick(tool) },
                            modifier = Modifier.weight(1f),
                            showFullCaption = showFullCaption,
                        )
                    } else {
                        ToolsHubTile(
                            tool = tool,
                            enabled = enabled(tool),
                            onClick = { onClick(tool) },
                            modifier = Modifier.weight(1f),
                            showFullCaption = showFullCaption,
                        )
                    }
                }
                repeat(columns - rowTools.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Hub tile grows with translated titles and keeps both rows aligned. */
@Composable
private fun ToolsHubTile(
    tool: ToolItem,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showFullCaption: Boolean = false,
) {
    ToolCard(
        modifier = modifier
            .heightIn(min = 76.dp)
            .fillMaxHeight()
            .alpha(if (enabled) 1f else 0.5f),
        onClick = if (enabled) onClick else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToolArtStage(id = tool.id, modifier = Modifier.size(width = 64.dp, height = 52.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp, lineHeight = 22.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = tool.caption,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (showFullCaption) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val ToolsPaneSpacing = 28.dp

/**
 * Illustrated tool card: inset tactile artwork, then the title and caption on one neutral surface.
 * Used on phones and tablets; cards in a row share one height.
 */
@Composable
private fun ToolsFeatureCard(
    tool: ToolItem,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showFullCaption: Boolean = false,
) {
    ToolCard(
        modifier = modifier
            .fillMaxHeight()
            .alpha(if (enabled) 1f else 0.5f),
        onClick = if (enabled) onClick else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            ToolArtStage(
                id = tool.id,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
            )
            Column(
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = tool.caption,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (showFullCaption) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The camera action as a highlighted card on tablets: it is the tool people come here for most.
 * [tall] stacks the icon over the text for the narrow start pane, so the title never truncates.
 */
@Composable
private fun ToolsScanHero(
    tool: ToolItem,
    tall: Boolean,
    onClick: () -> Unit,
) {
    val title: @Composable () -> Unit = {
        Text(
            text = tool.title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = if (tall) 20.sp else 17.sp, lineHeight = 22.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val caption: @Composable () -> Unit = {
        Text(
            text = tool.caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val arrow: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    ToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (tall) 184.dp else 104.dp),
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (tall) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    ToolArtStage(
                        id = tool.id,
                        modifier = Modifier.size(width = 112.dp, height = 84.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    arrow()
                }
                Spacer(Modifier.weight(1f))
                title()
                caption()
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ToolArtStage(
                    id = tool.id,
                    modifier = Modifier.size(width = 104.dp, height = 80.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    title()
                    caption()
                }
                arrow()
            }
        }
    }
}
