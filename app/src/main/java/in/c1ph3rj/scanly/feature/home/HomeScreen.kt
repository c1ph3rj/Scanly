package `in`.c1ph3rj.scanly.feature.home

import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.outlined.Search
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import `in`.c1ph3rj.scanly.core.ui.ScanlyTestTags
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.ui.ImageImportSupport
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.adaptiveColumnCount
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.takeOrElse
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.feature.components.*
import `in`.c1ph3rj.scanly.feature.pip.LocalPipPreferences
import `in`.c1ph3rj.scanly.feature.pip.PipLines
import `in`.c1ph3rj.scanly.feature.pip.PipViewModel
import androidx.compose.ui.unit.Dp
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeRoute(
    onOpenDocument: (String) -> Unit,
    onOpenScanSession: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onNavigateToLibrary: () -> Unit,
    onSeeAllFolders: () -> Unit = onNavigateToLibrary,
    onOpenSearch: () -> Unit = {},
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val pipViewModel: PipViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var createForScan by rememberSaveable { mutableStateOf(false) }

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
                is HomeEvent.OpenDocument -> {
                    if (createForScan) onOpenScanSession(event.documentId)
                    else onOpenDocument(event.documentId)
                    createForScan = false
                }
                is HomeEvent.OpenGroup -> onOpenGroup(event.groupId)
                is HomeEvent.ShowMessage -> {
                    // A failed create must not leave the scan intent armed for the next document.
                    createForScan = false
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    HomeScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onCreateDocument = { title ->
            createForScan = true
            viewModel.createDocument(title)
        },
        onImportImages = {
            importImagesLauncher.launch(ImageImportSupport.createPickRequest())
        },
        onOpenDocument = onOpenDocument,
        onOpenScanSession = onOpenScanSession,
        onOpenGroup = onOpenGroup,
        onNavigateToLibrary = onNavigateToLibrary,
        onSeeAllFolders = onSeeAllFolders,
        onOpenSearch = onOpenSearch,
        onCreateGroup = viewModel::createGroup,
        onSuggestTitle = viewModel::suggestDocumentTitle,
        onSuggestGroupTitle = viewModel::suggestGroupTitle,
        onDismissPipTip = pipViewModel::dismissTodaysTip,
    )

    HomeRatingPromptHost(viewModel = viewModel)
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onCreateDocument: (String) -> Unit,
    onImportImages: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenScanSession: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onNavigateToLibrary: () -> Unit,
    onSeeAllFolders: () -> Unit = onNavigateToLibrary,
    onOpenSearch: () -> Unit = {},
    onCreateGroup: (String) -> Unit,
    onSuggestTitle: suspend (DocumentTitleFormat) -> String,
    onSuggestGroupTitle: suspend (GroupTitleFormat) -> String,
    onDismissPipTip: () -> Unit = {},
) {
    var createDialogVisible by rememberSaveable { mutableStateOf(false) }
    var createFolderDialogVisible by rememberSaveable { mutableStateOf(false) }
    val windowSizeInfo = rememberWindowSizeInfo()
    val pipPreferences = LocalPipPreferences.current
    val todayEpochDay = remember { java.time.LocalDate.now().toEpochDay() }
    val hasContent = uiState.recentDocuments.isNotEmpty() || uiState.recentGroups.isNotEmpty()
    var pokeCount by remember { mutableIntStateOf(0) }
    var pokeLine by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pokeCount) {
        if (pokeCount == 0) return@LaunchedEffect
        pokeLine = PipLines.pokes[(pokeCount - 1) % PipLines.pokes.size]
        kotlinx.coroutines.delay(3_600L)
        pokeLine = null
    }

    BackHandler(enabled = uiState.isImporting) { /* block back while processing */ }

    val showTip = pipPreferences.showHomeTips &&
        hasContent &&
        !uiState.isLoading &&
        pipPreferences.tipDismissedEpochDay != todayEpochDay
    val showPipNote = pipPreferences.enabled && (pokeLine != null || showTip)
    val headerPip: (@Composable () -> Unit)? = if (pipPreferences.enabled && hasContent) {
        { HomeHeaderPip(onPoke = { pokeCount++ }) }
    } else {
        null
    }
    val compactHeight = windowSizeInfo.isHeightConstrained
    val horizontalPadding = windowSizeInfo.horizontalPadding
    val recentsState = if (windowSizeInfo.widthClass == WindowWidthClass.Compact) {
        remember(uiState) { uiState.trimmedForCompactWidth() }
    } else {
        uiState
    }
    val folderTileWidth = if (windowSizeInfo.widthClass == WindowWidthClass.Compact) 104.dp else 132.dp

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ScanlyTestTags.HOME_SCREEN),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            contentAlignment = Alignment.TopCenter,
        ) {
            val recents: LazyListScope.(start: Dp, end: Dp, columns: Int) -> Unit = { start, end, columns ->
                homeRecents(
                    uiState = recentsState,
                    start = start,
                    end = end,
                    columns = columns,
                    folderTileWidth = folderTileWidth,
                    isTablet = windowSizeInfo.widthClass != WindowWidthClass.Compact,
                    onNewFolder = { createFolderDialogVisible = true },
                    onOpenGroup = onOpenGroup,
                    onSeeAllFolders = onSeeAllFolders,
                    onNavigateToLibrary = onNavigateToLibrary,
                    onOpenDocument = onOpenDocument,
                    onCreateDocument = { createDialogVisible = true },
                )
            }

            if (windowSizeInfo.useTwoPaneLayout) {
                // Launcher pane (greeting, search, actions) stays put while recents scroll beside it.
                val contentWidth = minOf(maxWidth, windowSizeInfo.wideContentMaxWidth)
                val launchPaneWidth = (contentWidth * 0.4f).coerceIn(300.dp, 400.dp)
                Row(
                    modifier = Modifier
                        .width(contentWidth)
                        .fillMaxHeight(),
                ) {
                    Column(
                        modifier = Modifier
                            .width(launchPaneWidth + horizontalPadding)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = horizontalPadding, bottom = 24.dp),
                    ) {
                        HomeHeader(
                            groupCount = uiState.recentGroups.size,
                            documentCount = uiState.recentDocuments.size,
                            compact = compactHeight,
                            modifier = Modifier.padding(bottom = 12.dp),
                            pip = headerPip,
                        )
                        if (showPipNote) {
                            HomePipNote(
                                line = pokeLine ?: PipLines.homeTipFor(todayEpochDay),
                                isTip = pokeLine == null,
                                onDismissTip = onDismissPipTip,
                                modifier = Modifier.padding(bottom = 16.dp),
                            )
                        }
                        HomeSearchPill(
                            onClick = onOpenSearch,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        QuickActionsRow(
                            onScan = { createDialogVisible = true },
                            onImport = onImportImages,
                            onNewFolder = { createFolderDialogVisible = true },
                            importEnabled = !uiState.isImporting,
                            horizontalPadding = 0.dp,
                        )
                    }
                    Spacer(Modifier.width(32.dp))
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        val columns = adaptiveColumnCount(
                            availableWidth = maxWidth - horizontalPadding,
                            minCellWidth = HomeRecentCellMinWidth,
                            minColumns = 1,
                            maxColumns = 2,
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp),
                        ) {
                            item(key = "recents_top_inset", contentType = "inset") {
                                Spacer(
                                    Modifier
                                        .statusBarsPadding()
                                        .height(if (compactHeight) 8.dp else 14.dp),
                                )
                            }
                            recents(0.dp, horizontalPadding, columns)
                        }
                    }
                }
            } else {
                val listModifier = if (windowSizeInfo.widthClass == WindowWidthClass.Compact) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.widthIn(max = windowSizeInfo.contentMaxWidth).fillMaxHeight()
                }
                val listWidth = minOf(maxWidth, windowSizeInfo.contentMaxWidth.takeOrElse { maxWidth })
                val columns = adaptiveColumnCount(
                    availableWidth = listWidth - horizontalPadding * 2,
                    minCellWidth = HomeRecentCellMinWidth,
                    minColumns = 1,
                    maxColumns = 2,
                )
                LazyColumn(
                    modifier = listModifier,
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    item(key = "home_header", contentType = "header") {
                        HomeHeader(
                            groupCount = uiState.recentGroups.size,
                            documentCount = uiState.recentDocuments.size,
                            compact = compactHeight,
                            modifier = Modifier
                                .padding(horizontal = horizontalPadding)
                                .padding(bottom = 12.dp),
                            pip = headerPip,
                        )
                    }

                    if (showPipNote) {
                        item(key = "home_pip_note", contentType = "pip_note") {
                            HomePipNote(
                                line = pokeLine ?: PipLines.homeTipFor(todayEpochDay),
                                isTip = pokeLine == null,
                                onDismissTip = onDismissPipTip,
                                modifier = Modifier
                                    .padding(horizontal = horizontalPadding)
                                    .padding(bottom = 16.dp)
                                    .animateItem(),
                            )
                        }
                    }

                    item(key = "home_search", contentType = "search") {
                        HomeSearchPill(
                            onClick = onOpenSearch,
                            modifier = Modifier
                                .padding(horizontal = horizontalPadding)
                                .padding(bottom = 16.dp),
                        )
                    }

                    item(key = "quick_actions", contentType = "quick_actions") {
                        QuickActionsRow(
                            onScan = { createDialogVisible = true },
                            onImport = onImportImages,
                            onNewFolder = { createFolderDialogVisible = true },
                            importEnabled = !uiState.isImporting,
                            wide = windowSizeInfo.widthClass != WindowWidthClass.Compact,
                            horizontalPadding = horizontalPadding,
                            modifier = Modifier.padding(bottom = 24.dp),
                        )
                    }

                    recents(horizontalPadding, horizontalPadding, columns)
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
            confirmLabel = "Create",
            onDismiss = { createDialogVisible = false },
            onConfirm = { value ->
                createDialogVisible = false
                onCreateDocument(value)
            },
            onSuggestTitle = onSuggestTitle,
            autoFillSuggestedName = true,
        )
    }

    if (createFolderDialogVisible && !uiState.isImporting) {
        GroupNameDialog(
            title = "New folder",
            initialValue = "",
            onDismiss = { createFolderDialogVisible = false },
            onConfirm = { title ->
                createFolderDialogVisible = false
                onCreateGroup(title)
            },
            onSuggestTitle = onSuggestGroupTitle,
            autoFillSuggestedName = true,
        )
    }
}

/** Recent rows stay readable down to this width; wider panes fit two side by side. */
private val HomeRecentCellMinWidth = 300.dp

/**
 * Loading skeleton, recent folders shelf and recent documents. Shared by the single-column
 * layout and the recents pane of the two-pane layout; [start] and [end] are the row insets.
 */
private fun LazyListScope.homeRecents(
    uiState: HomeUiState,
    start: Dp,
    end: Dp,
    columns: Int,
    folderTileWidth: Dp,
    isTablet: Boolean,
    onNewFolder: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onSeeAllFolders: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onCreateDocument: () -> Unit,
) {
    val inset = Modifier.padding(start = start, end = end)
    if (uiState.isLoading) {
        item(key = "home_loading", contentType = "loading") {
            HomeLoadingSkeleton(
                isTablet = isTablet,
                start = start,
                end = end,
            )
        }
        return
    }
    if (uiState.recentGroups.isNotEmpty()) {
        item(key = "groups_header", contentType = "section_header") {
            LibrarySectionHeader(
                title = "Recent folders",
                actionLabel = "See all",
                onAction = onSeeAllFolders,
                modifier = inset,
            )
        }
        item(key = "groups_row", contentType = "groups_row") {
            // Tiles carry their own press inset; pull the row out so the art aligns with the header.
            LazyRow(
                contentPadding = PaddingValues(
                    start = (start - LibraryFolderTileInset).coerceAtLeast(0.dp),
                    end = (end - LibraryFolderTileInset).coerceAtLeast(0.dp),
                ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 20.dp),
            ) {
                item(key = "new_folder", contentType = "new_folder") {
                    LibraryNewFolderTile(
                        onClick = onNewFolder,
                        modifier = Modifier.width(folderTileWidth),
                    )
                }
                items(
                    items = uiState.recentGroups,
                    key = { it.id },
                    contentType = { "recent_group" },
                ) { group ->
                    LibraryFolderTile(
                        group = group,
                        onOpen = { onOpenGroup(group.id) },
                        modifier = Modifier.width(folderTileWidth),
                    )
                }
                if (uiState.hasMoreGroups) {
                    item(key = "view_all_folders", contentType = "view_all_folders") {
                        LibraryViewAllFoldersTile(
                            onClick = onSeeAllFolders,
                            modifier = Modifier.width(folderTileWidth),
                        )
                    }
                }
            }
        }
    }

    if (uiState.recentDocuments.isNotEmpty()) {
        item(key = "files_header", contentType = "section_header") {
            LibrarySectionHeader(
                title = "Recent documents",
                actionLabel = "See all",
                onAction = onNavigateToLibrary,
                modifier = inset.padding(bottom = 4.dp),
            )
        }
        if (columns <= 1) {
            itemsIndexed(
                items = uiState.recentDocuments,
                key = { _, doc -> doc.id },
                contentType = { _, _ -> "recent_document" },
            ) { index, doc ->
                LibraryListItem(
                    first = index == 0,
                    last = index == uiState.recentDocuments.lastIndex,
                    modifier = inset.animateItem(),
                ) {
                    DocumentCard(
                        document = doc,
                        onOpen = { onOpenDocument(doc.id) },
                        style = LibraryCardStyle.PreviewList,
                    )
                }
            }
        } else {
            // Wide panes: separate rounded cards side by side instead of one long grouped list.
            items(
                items = uiState.recentDocuments.chunked(columns),
                key = { row -> "recent_row_${row.first().id}" },
                contentType = { "recent_document_row" },
            ) { row ->
                Row(
                    modifier = inset
                        .padding(bottom = 8.dp)
                        .animateItem(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { doc ->
                        LibraryListItem(
                            first = true,
                            last = true,
                            modifier = Modifier.weight(1f),
                        ) {
                            DocumentCard(
                                document = doc,
                                onOpen = { onOpenDocument(doc.id) },
                                style = LibraryCardStyle.PreviewList,
                            )
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    } else if (uiState.recentGroups.isEmpty()) {
        item(key = "empty_state", contentType = "empty") {
            EmptyHomeCard(
                onCreateDocument = onCreateDocument,
                modifier = inset.padding(top = 16.dp),
            )
        }
    }
}

@Composable
fun HomeHeader(
    groupCount: Int,
    documentCount: Int,
    modifier: Modifier = Modifier,
    /** Short window: smaller headline so the search and actions stay above the fold. */
    compact: Boolean = false,
    pip: (@Composable () -> Unit)? = null,
) {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = greetingForHour(hour)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = if (compact) 8.dp else 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        Text(
            text = greeting.salutation,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = buildAnnotatedString {
                val start = greeting.headline.indexOf(greeting.highlight)
                if (start < 0) {
                    append(greeting.headline)
                } else {
                    append(greeting.headline.substring(0, start))
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(greeting.highlight)
                    }
                    append(greeting.headline.substring(start + greeting.highlight.length))
                }
            },
            style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    if (pip != null) {
        Spacer(Modifier.width(8.dp))
        pip()
    }
    }
}

private data class HomeGreeting(
    val salutation: String,
    val headline: String,
    val highlight: String,
)

private fun greetingForHour(hour: Int): HomeGreeting = when (hour) {
    in 5..11 -> HomeGreeting(
        salutation = "Good morning,",
        headline = "What are we\nscanning today?",
        highlight = "scanning",
    )
    in 12..16 -> HomeGreeting(
        salutation = "Good afternoon,",
        headline = "Let's capture\nsome documents.",
        highlight = "documents",
    )
    in 17..20 -> HomeGreeting(
        salutation = "Good evening,",
        headline = "Wrap up and\ndigitize your day.",
        highlight = "digitize",
    )
    else -> HomeGreeting(
        salutation = "Still scanning?",
        headline = "Save it now,\nfind it tomorrow.",
        highlight = "Save it now",
    )
}

@Composable
fun QuickActionsRow(
    onScan: () -> Unit,
    onImport: () -> Unit,
    onNewFolder: () -> Unit,
    importEnabled: Boolean = true,
    /** Tablet widths: short horizontal cards spanning the column instead of big squares. */
    wide: Boolean = false,
    horizontalPadding: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionCard(
            title = "Scan",
            icon = Icons.Outlined.CameraAlt,
            onClick = onScan,
            accentContainerColor = MaterialTheme.colorScheme.primaryContainer,
            accentContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            wide = wide,
            modifier = Modifier.weight(1f)
        )
        QuickActionCard(
            title = "Import",
            icon = Icons.Outlined.PhotoLibrary,
            onClick = onImport,
            enabled = importEnabled,
            accentContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            accentContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            wide = wide,
            modifier = Modifier.weight(1f)
        )
        QuickActionCard(
            title = "Folder",
            icon = Icons.Outlined.CreateNewFolder,
            onClick = onNewFolder,
            accentContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            accentContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            wide = wide,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun QuickActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    accentContainerColor: androidx.compose.ui.graphics.Color,
    accentContentColor: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    wide: Boolean = false,
    modifier: Modifier = Modifier
) {
    val resolvedContentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    }
    val iconBadge: @Composable () -> Unit = {
        Surface(
            modifier = Modifier.size(46.dp),
            color = if (enabled) accentContainerColor else MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.large,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (enabled) accentContentColor else resolvedContentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = if (wide) modifier.height(80.dp) else modifier.aspectRatio(1f),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = MaterialTheme.shapes.extraLarge,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                iconBadge()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = resolvedContentColor,
                    maxLines = 1,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                iconBadge()
                Spacer(modifier = Modifier.height(9.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = resolvedContentColor
                )
            }
        }
    }
}

/**
 * Shimmering stand-in for the folders row and recent documents, shown until the library's
 * first load lands. Shapes mirror [LibraryFolderTile] and the compact document row so the
 * real content settles in without a jump.
 */
@Composable
private fun HomeLoadingSkeleton(
    isTablet: Boolean,
    start: Dp,
    end: Dp,
    modifier: Modifier = Modifier,
) {
    val progress = rememberShimmerProgress()
    val lineShape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    val tileWidth = if (isTablet) 132.dp else 104.dp
    val inset = Modifier.padding(start = start, end = end)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Loading your documents" },
    ) {
        SkeletonSectionHeader(progress, inset)
        // Not user-scrollable: the scroll container only lets tiles run off the edge like the real row.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState(), enabled = false)
                .padding(
                    start = (start - LibraryFolderTileInset).coerceAtLeast(0.dp),
                    end = (end - LibraryFolderTileInset).coerceAtLeast(0.dp),
                )
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(4) {
                Column(
                    modifier = Modifier
                        .width(tileWidth)
                        .padding(LibraryFolderTileInset),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ShimmerBlock(
                        progress = progress,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    ShimmerBlock(progress, lineShape, Modifier.fillMaxWidth(0.8f).height(14.dp))
                    ShimmerBlock(progress, lineShape, Modifier.fillMaxWidth(0.55f).height(10.dp))
                }
            }
        }

        SkeletonSectionHeader(
            progress,
            inset.padding(bottom = 4.dp),
        )
        val rowCount = 3
        repeat(rowCount) { index ->
            LibraryListItem(
                first = index == 0,
                last = index == rowCount - 1,
                modifier = inset,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShimmerBlock(
                        progress = progress,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        modifier = Modifier.size(width = 48.dp, height = 64.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ShimmerBlock(progress, lineShape, Modifier.fillMaxWidth(0.7f).height(16.dp))
                        ShimmerBlock(progress, lineShape, Modifier.fillMaxWidth(0.45f).height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonSectionHeader(
    progress: State<Float>,
    modifier: Modifier = Modifier,
) {
    val lineShape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ShimmerBlock(progress, lineShape, Modifier.width(140.dp).height(18.dp))
        ShimmerBlock(progress, lineShape, Modifier.width(52.dp).height(14.dp))
    }
}

@Composable
private fun EmptyHomeCard(
    onCreateDocument: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PipEmptyState(
        pipLine = `in`.c1ph3rj.scanly.feature.pip.PipLines.HOME_EMPTY,
        pipMood = `in`.c1ph3rj.scanly.feature.pip.PipMood.IDLE,
        title = "No documents yet",
        description = "Scan your first page and it will be ready here whenever you need it.",
        actionLabel = "Start scanning",
        onAction = onCreateDocument,
        actionIcon = Icons.Outlined.CameraAlt,
        modifier = modifier,
    )
}

/**
 * Entry point to library search, styled as a field but opening the dedicated search screen
 * so Home stays a launcher (no inline results pushing the quick actions around).
 */
@Composable
private fun HomeSearchPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = "Search names and text in pages" },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Search names and text in pages",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
