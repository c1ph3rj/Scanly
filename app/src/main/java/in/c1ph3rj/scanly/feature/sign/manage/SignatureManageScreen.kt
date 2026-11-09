package `in`.c1ph3rj.scanly.feature.sign.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.domain.model.SavedSignature
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.domain.model.SignInk
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import `in`.c1ph3rj.scanly.domain.model.SignatureSource
import `in`.c1ph3rj.scanly.feature.components.ExportActionRow
import `in`.c1ph3rj.scanly.feature.components.ScanlyModalBottomSheet
import `in`.c1ph3rj.scanly.feature.components.ScanlySheetContent
import `in`.c1ph3rj.scanly.feature.tools.ToolColorSwatch
import `in`.c1ph3rj.scanly.feature.tools.ToolCompactHeader
import `in`.c1ph3rj.scanly.feature.tools.ToolContentPadding
import `in`.c1ph3rj.scanly.feature.tools.ToolFieldLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolNote
import `in`.c1ph3rj.scanly.feature.tools.ToolPrimaryButton
import `in`.c1ph3rj.scanly.feature.tools.ToolSecondaryButton
import `in`.c1ph3rj.scanly.feature.tools.pdf.sharePreparedFiles
import java.text.DateFormat
import java.util.Date
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString
import androidx.compose.ui.res.stringResource

private val SignatureAspectRatio = 2.2f
private val InitialsAspectRatio = 1.4f
private val CardCorner = 14.dp
private val MarkCardMinSize = 150.dp
private val DeleteIconColor = Color(0xFF6B757C)

// Cards are always white paper, so the badge uses fixed light-surface colours in every theme.
private val UsedFirstBadgeColor = Color(0xFFE6F4F1)
private val UsedFirstBadgeTextColor = Color(0xFF0F5C52)

/**
 * "Your signatures" screen: lists saved signatures and initials, opens the creator for a new one
 * through [onCreate], and lets the user share, save, reorder or delete each saved mark.
 *
 * The route wires [SignatureManageViewModel] into this screen, shows its one-shot messages in a
 * snackbar and hands prepared images to the system share sheet.
 *
 * @param onNavigateUp Called when the back button is pressed.
 * @param onCreate Called with the kind the user asked to add (signature or initials).
 * @param onSignPdf Opens the Sign PDF tool.
 * @param viewModel Source of the saved marks and their actions.
 */
@Composable
fun SignatureManageRoute(
    onNavigateUp: () -> Unit,
    onCreate: (SignatureKind) -> Unit,
    onSignPdf: () -> Unit,
    viewModel: SignatureManageViewModel = hiltViewModel(),
) {
    val signatures by viewModel.signatures.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SignatureManageEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is SignatureManageEvent.ShareImage -> sharePreparedFiles(
                    context = context,
                    artifact = ShareArtifact(
                        mimeType = "image/png",
                        title = event.title,
                        filePaths = listOf(event.filePath),
                    ),
                )
            }
        }
    }

    SignatureManageScreen(
        signatures = signatures,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onCreate = onCreate,
        onDelete = viewModel::delete,
        busy = busy,
        onMoveToFront = viewModel::moveToFront,
        onShare = viewModel::share,
        onSaveToPhone = viewModel::saveToPhone,
        onSignPdf = onSignPdf,
    )
}

/**
 * Stateless body of the "Your signatures" screen.
 *
 * Shows two sections, "Signatures" and "Initials", each ending with a dashed "+" tile. The first
 * mark of each kind is the one the sign editor offers first, badged "Used first" when there is a
 * choice. Tapping a mark opens a sheet to preview it in each ink, share or save it as a
 * transparent PNG, move it to the front, start signing a PDF, or delete it. Deleting asks for
 * confirmation first; the confirmed id is passed to [onDelete].
 *
 * @param signatures Every saved mark, both kinds, in the order the sign editor offers them.
 * @param snackbarHostState Host for one-shot messages from the ViewModel.
 * @param onNavigateUp Called when the back button is pressed.
 * @param onCreate Called with the kind the user asked to add.
 * @param onDelete Called with the id of a mark the user confirmed deleting.
 * @param modifier Applied to the screen root.
 * @param busy True while an image is being prepared; share and save are disabled meanwhile.
 * @param onMoveToFront Called to make a mark the first of its kind.
 * @param onShare Called to share a mark as a PNG in the chosen ink.
 * @param onSaveToPhone Called to save a mark as a PNG in the chosen ink to the gallery.
 * @param onSignPdf Opens the Sign PDF tool.
 */
@Composable
fun SignatureManageScreen(
    signatures: List<SavedSignature>,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onCreate: (SignatureKind) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    onMoveToFront: (SavedSignature) -> Unit = {},
    onShare: (SavedSignature, SignInk) -> Unit = { _, _ -> },
    onSaveToPhone: (SavedSignature, SignInk) -> Unit = { _, _ -> },
    onSignPdf: () -> Unit = {},
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDelete = signatures.firstOrNull { it.id == pendingDeleteId }
    val opened = signatures.firstOrNull { it.id == openedId }
    val drawnSignatures = signatures.filter { it.kind == SignatureKind.SIGNATURE }
    val initials = signatures.filter { it.kind == SignatureKind.INITIALS }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        // Centred column on tablets, matching the other tool screens.
        Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            ToolCompactHeader(
                title = stringResource(R.string.sign_editor_your_signatures),
                onBack = onNavigateUp,
                subtitle = if (signatures.isEmpty()) null else stringResource(R.string.signature_manage_tap_one_to_share_save),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = MarkCardMinSize),
                modifier = Modifier.weight(1f),
                contentPadding = ToolContentPadding,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (signatures.isEmpty()) {
                    item(key = "empty-note", span = { GridItemSpan(maxLineSpan) }) {
                        ToolNote(
                            text = stringResource(R.string.signature_manage_signatures_stay_on_this_phone),
                        )
                    }
                }
                markSection(
                    title = appString(R.string.signature_manage_signatures),
                    kind = SignatureKind.SIGNATURE,
                    marks = drawnSignatures,
                    aspectRatio = SignatureAspectRatio,
                    onCreate = onCreate,
                    onOpen = { openedId = it.id },
                    onRequestDelete = { pendingDeleteId = it.id },
                )
                markSection(
                    title = appString(R.string.signature_manage_initials),
                    kind = SignatureKind.INITIALS,
                    marks = initials,
                    aspectRatio = InitialsAspectRatio,
                    onCreate = onCreate,
                    onOpen = { openedId = it.id },
                    onRequestDelete = { pendingDeleteId = it.id },
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        )
    }

    opened?.let { mark ->
        val sameKind = if (mark.kind == SignatureKind.SIGNATURE) drawnSignatures else initials
        SavedMarkSheet(
            signature = mark,
            isFirst = sameKind.firstOrNull()?.id == mark.id,
            busy = busy,
            onDismiss = { openedId = null },
            onShare = { ink -> onShare(mark, ink) },
            onSaveToPhone = { ink ->
                // Close the sheet so the "Saved to …" snackbar, drawn under it, is visible.
                openedId = null
                onSaveToPhone(mark, ink)
            },
            onMoveToFront = {
                openedId = null
                onMoveToFront(mark)
            },
            onSignPdf = {
                openedId = null
                onSignPdf()
            },
            onDelete = {
                openedId = null
                pendingDeleteId = mark.id
            },
        )
    }

    pendingDelete?.let { mark ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(mark.kind.deleteQuestion) },
            text = { Text(stringResource(R.string.signature_manage_pages_you_ve_already_signed_2)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteId = null
                        onDelete(mark.id)
                    },
                ) {
                    Text(stringResource(R.string.library_card_presentation_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(R.string.export_share_cancel))
                }
            },
        )
    }
}

/** One section of the grid: a full-width heading, the saved marks of [kind], then the add tile. */
private fun LazyGridScope.markSection(
    title: String,
    kind: SignatureKind,
    marks: List<SavedSignature>,
    aspectRatio: Float,
    onCreate: (SignatureKind) -> Unit,
    onOpen: (SavedSignature) -> Unit,
    onRequestDelete: (SavedSignature) -> Unit,
) {
    item(key = "header-${kind.storageValue}", span = { GridItemSpan(maxLineSpan) }) {
        ToolFieldLabel(title, modifier = Modifier.padding(top = 4.dp))
    }
    items(marks, key = { it.id }) { mark ->
        SavedMarkCard(
            signature = mark,
            aspectRatio = aspectRatio,
            // The badge only means something when there is a choice.
            usedFirst = marks.size > 1 && marks.first().id == mark.id,
            onOpen = { onOpen(mark) },
            onDelete = { onRequestDelete(mark) },
        )
    }
    item(key = "add-${kind.storageValue}") {
        AddMarkTile(
            kind = kind,
            aspectRatio = aspectRatio,
            onClick = { onCreate(kind) },
        )
    }
}

/** A saved mark on a white paper card. Paper stays white in dark mode so the ink reads clearly. */
@Composable
private fun SavedMarkCard(
    signature: SavedSignature,
    aspectRatio: Float,
    usedFirst: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(CardCorner)
    val kind = signature.kind
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClickLabel = kind.openOptionsLabel, onClick = onOpen),
    ) {
        SavedSignatureImage(
            imagePath = signature.imagePath,
            ink = SignInk.Default,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentDescription = kind.savedLabel + if (usedFirst) stringResource(R.string.signature_manage_used_first_2) else "",
        )
        if (usedFirst) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                shape = CircleShape,
                color = UsedFirstBadgeColor,
            ) {
                Text(
                    text = stringResource(R.string.signature_manage_used_first),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = UsedFirstBadgeTextColor,
                )
            }
        }
        // 48dp, inset from the card corner so the touch target is not clipped by the rounded edge.
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = kind.deleteLabel,
                tint = DeleteIconColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Dashed-outline "+" tile that starts creating a new mark of [kind]. */
@Composable
private fun AddMarkTile(
    kind: SignatureKind,
    aspectRatio: Float,
    onClick: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    val label = kind.addLabel
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(CardCorner))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .drawBehind {
                val strokePx = 1.5.dp.toPx()
                val inset = strokePx / 2f
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokePx, size.height - strokePx),
                    cornerRadius = CornerRadius(CardCorner.toPx() - inset),
                    style = Stroke(
                        width = strokePx,
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                        ),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * Options for one saved mark: a large preview in the chosen ink, share or save as a transparent
 * PNG, move to front, sign a PDF, and delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedMarkSheet(
    signature: SavedSignature,
    isFirst: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onShare: (SignInk) -> Unit,
    onSaveToPhone: (SignInk) -> Unit,
    onMoveToFront: () -> Unit,
    onSignPdf: () -> Unit,
    onDelete: () -> Unit,
) {
    var inkValue by rememberSaveable(signature.id) { mutableStateOf(SignInk.Default.storageValue) }
    val ink = SignInk.fromStorage(inkValue)
    val kind = signature.kind
    val shape = RoundedCornerShape(CardCorner)

    ScanlyModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = { MaterialTheme.colorScheme.surface },
    ) {
        ScanlySheetContent(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = signature.kind.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = savedMarkMeta(signature),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(SignatureAspectRatio)
                    .clip(shape)
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            ) {
                SavedSignatureImage(
                    imagePath = signature.imagePath,
                    ink = ink,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentDescription = stringResource(R.string.signature_manage_in_ink, signature.kind.label, ink.label),
                )
            }
            ToolFieldLabel(stringResource(R.string.signature_manage_ink_colour))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SignInk.entries.forEach { option ->
                    ToolColorSwatch(
                        color = Color(option.argb),
                        name = option.label,
                        selected = option == ink,
                        onClick = { inkValue = option.storageValue },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolPrimaryButton(
                    text = stringResource(R.string.document_detail_share_2),
                    onClick = { onShare(ink) },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    icon = Icons.Outlined.Share,
                )
                ToolSecondaryButton(
                    text = stringResource(R.string.signature_manage_save_to_phone),
                    onClick = { onSaveToPhone(ink) },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    icon = Icons.Outlined.Download,
                )
            }
            Text(
                text = stringResource(R.string.signature_manage_shared_and_saved_as_a),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isFirst) {
                ExportActionRow(
                    icon = Icons.Outlined.VerticalAlignTop,
                    title = stringResource(R.string.signature_manage_use_first_when_signing),
                    enabled = true,
                    onClick = onMoveToFront,
                    description = kind.showFirstLabel,
                )
            }
            ExportActionRow(
                icon = Icons.Outlined.Draw,
                title = stringResource(R.string.signature_manage_sign_a_pdf),
                enabled = true,
                onClick = onSignPdf,
                description = stringResource(R.string.signature_manage_place_it_on_a_pdf),
            )
            ExportActionRow(
                icon = Icons.Outlined.Delete,
                title = kind.deleteLabel,
                enabled = true,
                onClick = onDelete,
                description = stringResource(R.string.signature_manage_pages_you_ve_already_signed),
            )
        }
    }
}

/** "Drawn · Added 3 Oct 2026": how the mark was made and when. */
private fun savedMarkMeta(signature: SavedSignature): String {
    val source = when (signature.source) {
        SignatureSource.DRAWN -> appString(R.string.signature_manage_drawn)
        SignatureSource.IMPORTED -> appString(R.string.signature_manage_from_a_photo)
    }
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(signature.createdAtMillis))
    return appString(R.string.signature_manage_added, source, date)
}
