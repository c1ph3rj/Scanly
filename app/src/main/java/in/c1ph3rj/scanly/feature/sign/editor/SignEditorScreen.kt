package `in`.c1ph3rj.scanly.feature.sign.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import androidx.compose.ui.Modifier
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.domain.model.SignInk
import `in`.c1ph3rj.scanly.domain.model.SignMark
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import `in`.c1ph3rj.scanly.domain.model.textContent
import `in`.c1ph3rj.scanly.feature.sign.manage.SavedSignatureImage
import kotlinx.coroutines.flow.distinctUntilChanged

/** Gap between the editor edges and the page sheet. */
private val PageMargin = 16.dp

/** Corner radius of the paper sheet, matching the document previews. */
private val PageShape = RoundedCornerShape(3.dp)

private val SavedCardHeight = 72.dp
private val SignatureCardWidth = 180.dp
private val InitialsCardWidth = 96.dp

private val SignToolEntries: List<Pair<SignTool, ImageVector>> = listOf(
    SignTool.Signature to Icons.Outlined.Draw,
    SignTool.Initials to Icons.Outlined.TextFields,
    SignTool.Date to Icons.Outlined.CalendarToday,
    SignTool.Text to Icons.Outlined.Title,
    SignTool.Checkbox to Icons.Outlined.CheckBox,
)

/**
 * Shared Sign editor: a swipeable page sheet with placed marks on top, the page's title and
 * "Page x of y" in the header, and a bottom panel with the saved-signature tray and the tool row.
 *
 * The screen keeps no edit state of its own; every change goes through [onAction]. Cancel asks
 * before discarding when [SignEditorUiState.isDirty] is true, and system back does the same.
 *
 * @param state Pages, marks, selection and tool state to draw.
 * @param pageBitmaps Rendered page images keyed by page index. A page without one shows a spinner.
 * @param onAction Sink for every edit the user makes.
 * @param onRequestPage Asked for a page's bitmap when that page is composed.
 * @param onCancel Called to leave without saving, after confirmation when there are changes.
 * @param onDone Called when the Done pill is tapped.
 * @param onManageSignatures Opens the saved-signature manager.
 * @param onCreateSignature Opens the creator for a new signature or initials.
 * @param modifier Applied to the whole editor.
 * @param title Title shown in the header.
 * @param doneEnabled Whether the Done pill can be tapped.
 */
@Composable
fun SignEditorScreen(
    state: SignEditorUiState,
    pageBitmaps: Map<Int, Bitmap>,
    onAction: (SignEditorAction) -> Unit,
    onRequestPage: (Int) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onManageSignatures: () -> Unit,
    onCreateSignature: (SignatureKind) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Sign",
    doneEnabled: Boolean = true,
) {
    val pageCount = state.pages.size
    val pagerState = rememberPagerState(
        initialPage = state.currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        pageCount = { pageCount },
    )
    val latestOnAction = rememberUpdatedState(onAction)
    val latestCurrentPage = rememberUpdatedState(state.currentPage)

    // Pager to state: report a page once its swipe has settled.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page != latestCurrentPage.value) {
                    latestOnAction.value(SignEditorAction.ChangePage(page))
                }
            }
    }

    // State to pager: follow changes made elsewhere, such as placing a mark on another page.
    LaunchedEffect(state.currentPage) {
        val target = state.currentPage
        if (target in 0 until pagerState.pageCount && pagerState.settledPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestCancel: () -> Unit = {
        if (state.isDirty) confirmDiscard = true else onCancel()
    }
    BackHandler(onBack = requestCancel)

    val editingMark = state.editingTextMarkId?.let { id -> state.marks.firstOrNull { it.id == id } }
    if (editingMark != null) {
        SignTextEditDialog(
            mark = editingMark,
            onConfirm = { text -> onAction(SignEditorAction.CommitText(editingMark.id, text)) },
            onDismiss = { onAction(SignEditorAction.CancelEditText) },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Signatures and marks you added or changed on this document won't be kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onCancel()
                    },
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            },
        )
    }

    // Landscape windows with room: the page keeps the full height and the tools sit beside it.
    // A bottom tray there would leave the page a thumbnail.
    val window = rememberWindowSizeInfo()
    val useSidePanel = window.isLandscape && window.widthClass != WindowWidthClass.Compact
    val pageArea: @Composable (Modifier) -> Unit = { areaModifier ->
        Box(
            modifier = areaModifier.background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            if (pageCount == 0) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    // Leave room under the sheet for the page switcher so it never covers the page.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = if (pageCount > 1) 60.dp else 0.dp),
                    // Marks claim only drags that start on them, so empty paper always swipes pages.
                    userScrollEnabled = true,
                ) { pageIndex ->
                    SignPageSheet(
                        pageIndex = pageIndex,
                        page = state.pages[pageIndex],
                        bitmap = pageBitmaps[pageIndex],
                        marks = state.marks,
                        selectedMarkId = state.selectedMarkId,
                        onAction = onAction,
                        onRequestPage = onRequestPage,
                    )
                }
                if (pageCount > 1) {
                    PageSwitcher(
                        current = state.currentPage,
                        count = pageCount,
                        onChange = { onAction(SignEditorAction.ChangePage(it)) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SignTopBar(
            title = title,
            subtitle = if (pageCount == 0) "" else "Page ${state.currentPage + 1} of $pageCount",
            onCancel = requestCancel,
            onDone = onDone,
            doneEnabled = doneEnabled,
        )
        if (useSidePanel) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                pageArea(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                SignBottomPanel(
                    state = state,
                    onAction = onAction,
                    onManageSignatures = onManageSignatures,
                    onCreateSignature = onCreateSignature,
                    sidePanel = true,
                    modifier = Modifier
                        .width(SidePanelWidth)
                        .fillMaxHeight(),
                )
            }
        } else {
            pageArea(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            SignBottomPanel(
                state = state,
                onAction = onAction,
                onManageSignatures = onManageSignatures,
                onCreateSignature = onCreateSignature,
            )
        }
    }
}

private val SidePanelWidth = 360.dp

@Composable
private fun SignTopBar(
    title: String,
    subtitle: String,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    doneEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = onDone,
            enabled = doneEnabled,
            shape = RoundedCornerShape(percent = 50),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Done",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * One page: a white paper sheet aspect-fitted with a margin, the rendered bitmap (or a spinner
 * until it arrives), and the mark layer on top.
 */
@Composable
private fun SignPageSheet(
    pageIndex: Int,
    page: SignEditorPage,
    bitmap: Bitmap?,
    marks: List<SignMark>,
    selectedMarkId: String?,
    onAction: (SignEditorAction) -> Unit,
    onRequestPage: (Int) -> Unit,
) {
    LaunchedEffect(pageIndex) { onRequestPage(pageIndex) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(PageMargin),
    ) {
        val fit = fitPage(maxWidth.value, maxHeight.value, page.aspectRatio)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(fit.width.dp, fit.height.dp)
                .shadow(elevation = 6.dp, shape = PageShape, clip = false)
                .clip(PageShape)
                .background(Color.White),
        ) {
            if (bitmap != null && !bitmap.isRecycled) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            SignMarkLayer(
                pageIndex = pageIndex,
                pageAspect = page.aspectRatio,
                marks = marks,
                selectedMarkId = selectedMarkId,
                onAction = onAction,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Sheet-style panel: saved items for Signature/Initials (or a hint), a divider, then the tools. */
@Composable
private fun SignBottomPanel(
    state: SignEditorUiState,
    onAction: (SignEditorAction) -> Unit,
    onManageSignatures: () -> Unit,
    onCreateSignature: (SignatureKind) -> Unit,
    modifier: Modifier = Modifier,
    /** Docked beside the page (landscape): full height, tools pinned to the bottom. */
    sidePanel: Boolean = false,
) {
    val kind: SignatureKind? = when (state.activeTool) {
        SignTool.Signature -> SignatureKind.SIGNATURE
        SignTool.Initials -> SignatureKind.INITIALS
        SignTool.Date, SignTool.Text, SignTool.Checkbox -> null
    }

    Surface(
        modifier = modifier.then(if (sidePanel) Modifier else Modifier.fillMaxWidth()),
        shape = if (sidePanel) {
            RoundedCornerShape(topStart = 24.dp)
        } else {
            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        },
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (sidePanel) Modifier.fillMaxHeight() else Modifier)
                .navigationBarsPadding()
                .padding(top = 16.dp, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Your signatures",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onManageSignatures) {
                    Text(
                        text = "Manage",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (kind != null) {
                SignatureTray(
                    kind = kind,
                    state = state,
                    onAction = onAction,
                    onCreateSignature = onCreateSignature,
                )
            } else {
                ToolHint(tool = state.activeTool)
            }
            Spacer(Modifier.height(8.dp))
            if (sidePanel) Spacer(Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ToolRow(
                activeTool = state.activeTool,
                onTool = { tool ->
                    onAction(SignEditorAction.SelectTool(tool))
                    if (tool == SignTool.Date || tool == SignTool.Text || tool == SignTool.Checkbox) {
                        onAction(SignEditorAction.PlaceTool(tool, state.currentPage))
                    }
                },
            )
        }
    }
}

/** Horizontal row of saved signatures or initials, then the dashed "+" card. */
@Composable
private fun SignatureTray(
    kind: SignatureKind,
    state: SignEditorUiState,
    onAction: (SignEditorAction) -> Unit,
    onCreateSignature: (SignatureKind) -> Unit,
) {
    val cardWidth = if (kind == SignatureKind.SIGNATURE) SignatureCardWidth else InitialsCardWidth
    val savedOfKind = state.signatures.filter { it.kind == kind }
    // The selected mark wins; otherwise the most recently placed mark of this kind is accented.
    val highlightPath = (state.selectedMark as? SignMark.Image)?.imagePath
        ?: state.marks.filterIsInstance<SignMark.Image>().lastOrNull { it.kind == kind }?.imagePath

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(SavedCardHeight),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(savedOfKind, key = { it.id }) { signature ->
            SavedItemCard(
                width = cardWidth,
                selected = signature.imagePath == highlightPath,
                onClick = { onAction(SignEditorAction.PlaceSignature(signature, state.currentPage)) },
            ) {
                SavedSignatureImage(
                    imagePath = signature.imagePath,
                    ink = SignInk.Default,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    contentDescription = kind.label,
                )
            }
        }
        item(key = "add-${kind.storageValue}") {
            AddSavedItemCard(
                width = cardWidth,
                kind = kind,
                showLabel = savedOfKind.isEmpty(),
                onClick = { onCreateSignature(kind) },
            )
        }
    }
}

@Composable
private fun SavedItemCard(
    width: Dp,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .height(SavedCardHeight),
        shape = RoundedCornerShape(14.dp),
        // Paper-white in every theme so dark ink stays readable, like the page itself.
        color = Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
private fun AddSavedItemCard(
    width: Dp,
    kind: SignatureKind,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val label = "Add ${kind.label.lowercase()}"
    Box(
        modifier = Modifier
            .width(width)
            .height(SavedCardHeight)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    size = size,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                        ),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = if (showLabel) null else label,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
            if (showLabel) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** One-line hint shown in place of the tray for Date, Text and Checkbox. */
@Composable
private fun ToolHint(tool: SignTool) {
    val hint = when (tool) {
        SignTool.Date -> "Tap Date again for another stamp · double-tap a date to edit it"
        SignTool.Text -> "Tap Text again for another line · double-tap text to edit it"
        SignTool.Checkbox -> "Tap Checkbox again for another box · use the toolbar to tick or untick"
        SignTool.Signature, SignTool.Initials -> ""
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SavedCardHeight)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ToolRow(
    activeTool: SignTool,
    onTool: (SignTool) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        SignToolEntries.forEach { (tool, icon) ->
            val active = tool == activeTool
            val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = { onTool(tool) })
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tool.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = tint,
                )
            }
        }
    }
}

/** Text dialog for Text and Date marks. Confirm commits the text; dismiss cancels the edit. */
@Composable
private fun SignTextEditDialog(
    mark: SignMark,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(mark.id) { mutableStateOf(mark.textContent.orEmpty()) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(mark.id) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mark is SignMark.Date) "Edit date" else "Add text") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** "‹  2 / 6  ›" pill so multi-page documents are obviously navigable (swiping works too). */
@Composable
private fun PageSwitcher(
    current: Int,
    count: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 4.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(current - 1) }, enabled = current > 0) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous page",
                    tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = if (current > 0) 1f else 0.35f),
                )
            }
            Text(
                text = "${current + 1} / $count",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { onChange(current + 1) }, enabled = current < count - 1) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next page",
                    tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = if (current < count - 1) 1f else 0.35f),
                )
            }
        }
    }
}
