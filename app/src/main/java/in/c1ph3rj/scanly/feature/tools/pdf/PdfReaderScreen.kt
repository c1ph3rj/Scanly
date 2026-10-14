package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.feature.tools.ForceLightSystemBarIcons
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.ZoomableBitmapViewer
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageState
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.core.ui.rememberZoomableImageState
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.feature.tools.ToolFieldLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolIconWell
import `in`.c1ph3rj.scanly.feature.tools.ToolLargeTitle
import `in`.c1ph3rj.scanly.feature.tools.ToolPaperPage
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolTextAction
import `in`.c1ph3rj.scanly.feature.tools.ToolTextField
import `in`.c1ph3rj.scanly.feature.tools.ToolTokens
import `in`.c1ph3rj.scanly.feature.tools.ToolTopBar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

private const val PDF_MIME_TYPE = "application/pdf"

// The reading surface stays dark in both themes so the page stands out.
private val ReaderBackground = Color(0xFF0B0E10)
private val ReaderChrome = Color(0xF00B0E10)
private val ReaderSheet = Color(0xFF15191C)
private val ReaderPill = Color(0xF015191C)
private val ReaderPlaceholder = Color(0xFF15191C)
private val ReaderLine = Color(0xFF262C31)
private val ReaderChromeText = Color(0xFFEEF1F2)
private val ReaderChromeMuted = Color(0xFF9AA5AB)

@Composable
fun PdfReaderRoute(
    onNavigateUp: () -> Unit,
    initialSource: PdfToolSource? = null,
) {
    val viewModel: PdfReaderViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Survives rotation. The picker opens first when no source was handed in.
    var showPicker by rememberSaveable { mutableStateOf(initialSource == null) }
    // Empty means nothing is open yet (hub entry) or the last open failed. A handed-in source is
    // about to open, so stay blank for that first frame instead of flashing the picker.
    val pickerVisible = showPicker ||
        (uiState.phase == PdfReaderPhase.Empty && (uiState.source != null || initialSource == null))

    // Back steps out one level: picker -> open document -> hub (or the Done screen that opened us).
    val onBack: () -> Unit = {
        when {
            pickerVisible && uiState.phase != PdfReaderPhase.Empty -> { showPicker = false }
            pickerVisible -> onNavigateUp()
            initialSource == null -> { showPicker = true }
            else -> onNavigateUp()
        }
    }
    BackHandler(onBack = onBack)

    val devicePicker = rememberSinglePdfPicker { uri ->
        showPicker = false
        viewModel.onSourcesChosen(
            listOf(PdfToolSource.DeviceUri(uri.toString(), uriDisplayName(context, uri))),
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest {
            if (it is PdfToolEvent.ShowMessage) snackbarHostState.showSnackbar(it.message)
        }
    }

    LaunchedEffect(initialSource) {
        if (initialSource != null && uiState.source != initialSource) {
            showPicker = false
            viewModel.onSourcesChosen(listOf(initialSource))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (pickerVisible) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                PdfFilePickerScreen(
                    title = "Read a PDF",
                    subtitle = "Open any PDF from Scanly or your phone.",
                    stepText = "Choose a file",
                    documents = uiState.libraryDocuments,
                    onBack = onBack,
                    onPickDevice = { devicePicker.launch(arrayOf(PDF_MIME_TYPE)) },
                    onPickLibrary = { document ->
                        showPicker = false
                        viewModel.onSourcesChosen(
                            listOf(PdfToolSource.LibraryDocument(document.id, document.title)),
                        )
                    },
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                )
            }
        } else {
            when (uiState.phase) {
                PdfReaderPhase.Empty -> Unit
                PdfReaderPhase.Opening -> PdfReaderOpeningState(onBack = onBack)
                PdfReaderPhase.Locked -> PdfReaderPasswordOverlay(
                    title = uiState.documentTitle.ifBlank { "Protected PDF" },
                    password = uiState.passwordDraft,
                    error = uiState.passwordError,
                    onPasswordChange = viewModel::setPasswordDraft,
                    onUnlock = viewModel::unlock,
                    onChooseAnother = { showPicker = true },
                    onBack = onBack,
                )
                PdfReaderPhase.Ready -> PdfReaderViewer(
                    uiState = uiState,
                    onBack = onBack,
                    onPageChange = viewModel::goToPage,
                    onToggleChrome = viewModel::toggleChrome,
                    onToggleReaderLayout = viewModel::toggleReaderLayout,
                    onRequestThumbnail = viewModel::requestThumbnail,
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun PdfReaderOpeningState(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ReaderBackground),
    ) {
        ReaderTopBar(
            title = "",
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center),
            color = ReaderChromeText,
        )
    }
}

@Composable
private fun PdfReaderPasswordOverlay(
    title: String,
    password: String,
    error: String?,
    onPasswordChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onChooseAnother: () -> Unit,
    onBack: () -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ToolTopBar(onBack = onBack)
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = ToolTokens.ScreenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ToolIconWell(
                    icon = Icons.Filled.Lock,
                    size = 56.dp,
                    iconSize = 26.dp,
                    accent = true,
                )
                ToolLargeTitle(
                    title = "Unlock PDF",
                    subtitle = "This PDF is password protected. Enter the password to read $title.",
                )
                ToolFieldLabel("Password")
                ToolTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                )
                ToolPrimaryButton(
                    text = "Unlock",
                    onClick = onUnlock,
                    enabled = password.isNotBlank(),
                )
                ToolTextAction(
                    label = "Choose another PDF",
                    onClick = onChooseAnother,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun PdfReaderViewer(
    uiState: PdfReaderUiState,
    onBack: () -> Unit,
    onPageChange: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    onToggleReaderLayout: () -> Unit,
    onRequestThumbnail: (Int) -> Unit,
) {
    ForceLightSystemBarIcons()
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = uiState.currentPageIndex.coerceAtLeast(0),
        pageCount = { uiState.pageCount.coerceAtLeast(1) },
    )
    var zoomActive by remember { mutableStateOf(false) }
    var showPages by rememberSaveable { mutableStateOf(false) }
    val zoomStates = remember(uiState.pageCount) {
        (0 until uiState.pageCount).associateWith { ZoomableImageState() }
    }
    val pageCounter = formatPdfReaderPageCounter(uiState.currentPageIndex, uiState.pageCount)
    val documentTitle = uiState.documentTitle.ifBlank { "PDF" }
    val source = uiState.source

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { onPageChange(it) }
    }
    LaunchedEffect(uiState.currentPageIndex) {
        if (pagerState.currentPage != uiState.currentPageIndex &&
            uiState.currentPageIndex in 0 until uiState.pageCount
        ) {
            pagerState.scrollToPage(uiState.currentPageIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ReaderBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = uiState.chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ReaderTopBar(title = documentTitle, onBack = onBack)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.pageCount > 0 && uiState.readerLayout == PdfReaderLayout.Paged) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = pdfReaderParentScrollerEnabled(zoomActive),
                        key = { it },
                    ) { pageIndex ->
                        val bitmap = uiState.pageBitmaps[pageIndex]
                        val zoomState = zoomStates[pageIndex] ?: rememberZoomableImageState(pageIndex)
                        if (bitmap != null) {
                            ZoomableBitmapViewer(
                                imageBitmap = bitmap.asImageBitmap(),
                                state = zoomState,
                                allowParentHorizontalGestures = pdfReaderAllowParentScrollGestures(),
                                onSingleTap = onToggleChrome,
                                onZoomActiveChange = {
                                    zoomActive = pdfReaderZoomActive(zoomStates)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = { onToggleChrome() })
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (pageIndex in uiState.loadingPages) {
                                    CircularProgressIndicator(color = ReaderChromeText)
                                } else {
                                    Text(
                                        "Loading page…",
                                        color = ReaderChromeMuted,
                                    )
                                }
                            }
                        }
                    }
                }
                if (uiState.pageCount > 0 && uiState.readerLayout == PdfReaderLayout.Continuous) {
                    PdfReaderContinuousViewer(
                        uiState = uiState,
                        zoomStates = zoomStates,
                        zoomActive = zoomActive,
                        onPageChange = onPageChange,
                        onToggleChrome = onToggleChrome,
                        onZoomActiveChange = { active -> zoomActive = active },
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ReaderBottomChrome(
                    pageCounter = pageCounter,
                    layout = uiState.readerLayout,
                    zoomActive = zoomActive,
                    canShare = pdfReaderCanShare(source),
                    onOpenPages = { showPages = true },
                    onToggleLayout = onToggleReaderLayout,
                    onResetZoom = {
                        resetPdfReaderZoom(zoomStates)
                        zoomActive = false
                    },
                    onShare = { source?.let { sharePdfSource(context, it) } },
                )
            }
        }

        // With the chrome hidden, the counter stays in the corner so the page is still located.
        AnimatedVisibility(
            visible = !uiState.chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp),
        ) {
            ReaderPageCounter(text = pageCounter)
        }
    }

    if (showPages) {
        PdfReaderPagesSheet(
            pageCount = uiState.pageCount,
            currentPageIndex = uiState.currentPageIndex,
            thumbnails = uiState.thumbnails,
            loadingThumbnails = uiState.loadingThumbnails,
            onRequestThumbnail = onRequestThumbnail,
            onPageSelected = { index ->
                onPageChange(index)
                showPages = false
            },
            onDismiss = { showPages = false },
        )
    }
}

@Composable
private fun PdfReaderContinuousViewer(
    uiState: PdfReaderUiState,
    zoomStates: Map<Int, ZoomableImageState>,
    zoomActive: Boolean,
    onPageChange: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    onZoomActiveChange: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect(onPageChange)
    }
    LaunchedEffect(uiState.currentPageIndex) {
        if (uiState.currentPageIndex in 0 until uiState.pageCount &&
            listState.firstVisibleItemIndex != uiState.currentPageIndex
        ) {
            listState.scrollToItem(uiState.currentPageIndex)
        }
    }

    val windowSizeInfo = rememberWindowSizeInfo()
    val pageMaxWidth = when (windowSizeInfo.widthClass) {
        WindowWidthClass.Compact -> Dp.Unspecified
        WindowWidthClass.Medium -> 720.dp
        WindowWidthClass.Expanded -> 840.dp
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = pdfReaderParentScrollerEnabled(zoomActive),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = 12.dp,
            start = windowSizeInfo.horizontalPadding,
            end = windowSizeInfo.horizontalPadding,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = uiState.pageCount,
            key = { it },
        ) { pageIndex ->
            val bitmap = uiState.pageBitmaps[pageIndex]
            val pageModifier = Modifier
                .then(
                    if (pageMaxWidth != Dp.Unspecified) {
                        Modifier.widthIn(max = pageMaxWidth)
                    } else {
                        Modifier
                    },
                )
                .fillMaxWidth()
            if (bitmap != null) {
                val zoomState = zoomStates[pageIndex] ?: rememberZoomableImageState("continuous-$pageIndex")
                ZoomableBitmapViewer(
                    imageBitmap = bitmap.asImageBitmap(),
                    state = zoomState,
                    allowParentHorizontalGestures = pdfReaderAllowParentScrollGestures(),
                    onSingleTap = onToggleChrome,
                    onZoomActiveChange = {
                        onZoomActiveChange(pdfReaderZoomActive(zoomStates))
                    },
                    modifier = pageModifier.aspectRatio(bitmap.width.toFloat() / bitmap.height),
                )
            } else {
                Box(
                    modifier = pageModifier
                        .height(280.dp)
                        .background(ReaderPlaceholder)
                        .pointerInput(pageIndex) {
                            detectTapGestures(onTap = { onToggleChrome() })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (pageIndex in uiState.loadingPages) {
                        CircularProgressIndicator(color = ReaderChromeText)
                    } else {
                        Text(
                            "Loading page ${pageIndex + 1}…",
                            color = ReaderChromeMuted,
                        )
                    }
                }
            }
        }
    }
}

/** Dark top chrome: back, the real file name on one line, and nothing on the right. */
@Composable
private fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ReaderChrome)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = ReaderChromeText,
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
            color = ReaderChromeText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Mirrors the back button so the file name stays centred. The right side is intentionally empty.
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun ReaderBottomChrome(
    pageCounter: String,
    layout: PdfReaderLayout,
    zoomActive: Boolean,
    canShare: Boolean,
    onOpenPages: () -> Unit,
    onToggleLayout: () -> Unit,
    onResetZoom: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReaderChrome),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            ReaderPageCounter(text = pageCounter)
        }
        HorizontalDivider(color = ReaderLine)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ReaderBottomAction(
                    icon = Icons.Filled.GridView,
                    label = "Pages",
                    onClick = onOpenPages,
                    modifier = Modifier.weight(1f),
                )
                ReaderBottomAction(
                    icon = if (layout == PdfReaderLayout.Paged) Icons.Filled.ViewAgenda else Icons.Filled.ViewCarousel,
                    label = pdfReaderLayoutActionLabel(layout),
                    onClick = onToggleLayout,
                    modifier = Modifier.weight(1f),
                )
                if (zoomActive) {
                    ReaderBottomAction(
                        icon = Icons.Filled.FitScreen,
                        label = "Reset zoom",
                        onClick = onResetZoom,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (canShare) {
                    ReaderBottomAction(
                        icon = Icons.Filled.Share,
                        label = "Share",
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ReaderChromeText,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = ReaderChromeText,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReaderPageCounter(
    text: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(ReaderPill)
            .border(1.dp, ReaderLine, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = ReaderChromeText,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfReaderPagesSheet(
    pageCount: Int,
    currentPageIndex: Int,
    thumbnails: Map<Int, Bitmap>,
    loadingThumbnails: Set<Int>,
    onRequestThumbnail: (Int) -> Unit,
    onPageSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ReaderSheet,
        contentColor = ReaderChromeText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = ToolTokens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Pages",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Tap a page to jump to it",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReaderChromeMuted,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(count = pageCount, key = { it }) { index ->
                    // Thumbnails are only rendered for cells that scroll into view.
                    LaunchedEffect(index) { onRequestThumbnail(index) }
                    ReaderPageThumbnail(
                        pageNumber = index + 1,
                        thumbnail = thumbnails[index],
                        loading = index in loadingThumbnails,
                        selected = index == currentPageIndex,
                        onClick = { onPageSelected(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderPageThumbnail(
    pageNumber: Int,
    thumbnail: Bitmap?,
    loading: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val highlightShape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToolPaperPage(
            bitmap = thumbnail,
            loading = loading,
            contentDescription = "Page $pageNumber",
            elevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 2.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = highlightShape,
                )
                .padding(3.dp),
        )
        Text(
            text = pageNumber.toString(),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else ReaderChromeText,
        )
    }
}

/**
 * Hands the open file to another app. Files Scanly owns go through its FileProvider; device
 * documents are shared as their own content URI with read access granted to the receiver.
 */
private fun sharePdfSource(context: Context, source: PdfToolSource) {
    when (source) {
        is PdfToolSource.AppFile -> shareExportArtifact(
            context = context,
            artifact = ExportArtifact(
                filePath = source.filePath,
                fileName = source.displayName,
                mimeType = PDF_MIME_TYPE,
            ),
            title = source.displayName,
        )
        is PdfToolSource.DeviceUri -> {
            val uri = Uri.parse(source.uriString)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = PDF_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, source.displayName)
                clipData = ClipData.newUri(context.contentResolver, source.displayName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${source.displayName}"))
        }
        is PdfToolSource.LibraryDocument -> Unit
    }
}
