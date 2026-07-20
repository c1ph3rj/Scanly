package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.feature.components.shareExportArtifact
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.ZoomableBitmapViewer
import `in`.c1ph3rj.scanly.core.ui.ZoomableImageState
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.core.ui.rememberZoomableImageState
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfCompressQuality
import `in`.c1ph3rj.scanly.domain.model.PdfPasswordMode
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.WatermarkLayout
import `in`.c1ph3rj.scanly.domain.model.WatermarkOrientation
import `in`.c1ph3rj.scanly.domain.model.WatermarkPageRange
import `in`.c1ph3rj.scanly.domain.model.WatermarkSize
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun PdfReaderRoute(
    onNavigateUp: () -> Unit,
    initialSource: PdfToolSource? = null,
) {
    val viewModel: PdfReaderViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Survives rotation; initial open only when no cold-start source was provided.
    var showPicker by rememberSaveable { mutableStateOf(initialSource == null) }
    // Menus should not re-open after rotation.
    var showMenu by remember { mutableStateOf(false) }

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
        when (uiState.phase) {
            PdfReaderPhase.Empty -> PdfReaderEmptyState(
                onChooseSource = { showPicker = true },
                onNavigateUp = onNavigateUp,
            )
            PdfReaderPhase.Opening -> PdfReaderOpeningState()
            PdfReaderPhase.Locked -> PdfReaderPasswordOverlay(
                title = uiState.documentTitle.ifBlank { "Protected PDF" },
                password = uiState.passwordDraft,
                error = uiState.passwordError,
                onPasswordChange = viewModel::setPasswordDraft,
                onUnlock = viewModel::unlock,
                onChooseAnother = {
                    viewModel.clearDocument()
                    showPicker = true
                },
                onNavigateUp = onNavigateUp,
            )
            PdfReaderPhase.Ready -> PdfReaderViewer(
                uiState = uiState,
                onNavigateUp = onNavigateUp,
                onPageChange = viewModel::goToPage,
                onToggleChrome = viewModel::toggleChrome,
                onToggleReaderLayout = viewModel::toggleReaderLayout,
                onOpenAnother = { showPicker = true },
                showMenu = showMenu,
                onShowMenuChange = { showMenu = it },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    // Only present the sheet for Empty (first pick) or an explicit re-open while
    // viewing — never as a surprise overlay after rotation when a PDF is open.
    val allowPicker =
        showPicker &&
            (
                uiState.phase == PdfReaderPhase.Empty ||
                    uiState.phase == PdfReaderPhase.Ready ||
                    uiState.phase == PdfReaderPhase.Locked
                )
    if (allowPicker) {
        PdfSourcePickerSheet(
            documents = uiState.libraryDocuments,
            multiSelect = false,
            onDismiss = { showPicker = false },
            onPickDevice = { devicePicker.launch(arrayOf("application/pdf")) },
            onConfirmLibrary = {
                showPicker = false
                viewModel.onSourcesChosen(it)
            },
        )
    }
}

@Composable
internal fun PdfReaderOpeningState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text("Opening PDF…")
        }
    }
}

@Composable
internal fun PdfReaderEmptyState(
    onChooseSource: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            ChromeIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateUp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Text(
                text = "Read a PDF",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Open a file from your device or turn a Scanly document into a PDF here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onChooseSource, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose PDF")
            }
        }
    }
}

@Composable
internal fun PdfReaderPasswordOverlay(
    title: String,
    password: String,
    error: String?,
    onPasswordChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onChooseAnother: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            ChromeIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateUp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "PROTECTED PDF",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Enter the password to unlock this PDF.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onChooseAnother) { Text("Choose another") }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onUnlock, enabled = password.isNotBlank()) {
                        Text("Unlock")
                    }
                }
            }
        }
    }
}

@Composable
internal fun PdfReaderViewer(
    uiState: PdfReaderUiState,
    onNavigateUp: () -> Unit,
    onPageChange: (Int) -> Unit,
    onToggleChrome: () -> Unit,
    onToggleReaderLayout: () -> Unit,
    onOpenAnother: () -> Unit,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = uiState.currentPageIndex.coerceAtLeast(0),
        pageCount = { uiState.pageCount.coerceAtLeast(1) },
    )
    var zoomActive by remember { mutableStateOf(false) }
    val zoomStates = remember(uiState.pageCount) {
        (0 until uiState.pageCount).associateWith { ZoomableImageState() }
    }

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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.pageCount > 0 && uiState.readerLayout == PdfReaderLayout.Paged) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !zoomActive,
                    key = { it },
                ) { pageIndex ->
                    val bitmap = uiState.pageBitmaps[pageIndex]
                    val zoomState = zoomStates[pageIndex] ?: rememberZoomableImageState(pageIndex)
                    if (bitmap != null) {
                        ZoomableBitmapViewer(
                            imageBitmap = bitmap.asImageBitmap(),
                            state = zoomState,
                            allowParentHorizontalGestures = true,
                            onSingleTap = onToggleChrome,
                            onZoomActiveChange = { active ->
                                if (pageIndex == pagerState.settledPage) {
                                    zoomActive = active
                                }
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
                                CircularProgressIndicator()
                            } else {
                                Text(
                                    "Loading page…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (uiState.pageCount > 0 && uiState.readerLayout == PdfReaderLayout.Continuous) {
                PdfReaderContinuousViewer(
                    uiState = uiState,
                    onPageChange = onPageChange,
                    onToggleChrome = onToggleChrome,
                )
            }

            AnimatedVisibility(
                visible = uiState.chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        ChromeIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            onClick = onNavigateUp,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        MetricChip(
                            label = buildString {
                                val title = uiState.documentTitle
                                if (title.isNotBlank()) {
                                    append(title.take(22))
                                    if (title.length > 22) append('…')
                                    append(" · ")
                                }
                                append("${uiState.currentPageIndex + 1}/${uiState.pageCount}")
                            },
                            containerColor = Color.Black.copy(alpha = 0.42f),
                            contentColor = Color.White,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        )
                    }
                    Row {
                        if (zoomActive) {
                            ChromeIconButton(
                                icon = Icons.Filled.FitScreen,
                                contentDescription = "Reset zoom",
                                onClick = {
                                    zoomStates[pagerState.settledPage]?.reset()
                                    zoomActive = false
                                },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Box {
                            ChromeIconButton(
                                icon = Icons.Filled.MoreVert,
                                contentDescription = "More",
                                onClick = { onShowMenuChange(true) },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            )
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { onShowMenuChange(false) },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.readerLayout == PdfReaderLayout.Paged) {
                                                "Continuous scroll"
                                            } else {
                                                "Page by page"
                                            },
                                        )
                                    },
                                    onClick = {
                                        onShowMenuChange(false)
                                        onToggleReaderLayout()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Open another PDF") },
                                    onClick = {
                                        onShowMenuChange(false)
                                        onOpenAnother()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
internal fun PdfReaderContinuousViewer(
    uiState: PdfReaderUiState,
    onPageChange: (Int) -> Unit,
    onToggleChrome: () -> Unit,
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 96.dp,
            bottom = 32.dp,
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
                val zoomState = rememberZoomableImageState("continuous-$pageIndex")
                ZoomableBitmapViewer(
                    imageBitmap = bitmap.asImageBitmap(),
                    state = zoomState,
                    allowParentHorizontalGestures = true,
                    onSingleTap = onToggleChrome,
                    modifier = pageModifier.aspectRatio(bitmap.width.toFloat() / bitmap.height),
                )
            } else {
                Box(
                    modifier = pageModifier
                        .height(280.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .pointerInput(pageIndex) {
                            detectTapGestures(onTap = { onToggleChrome() })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (pageIndex in uiState.loadingPages) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            "Loading page ${pageIndex + 1}…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
