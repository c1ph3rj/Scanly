package `in`.c1ph3rj.scanly.feature.sign.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.domain.model.SavedSignature
import `in`.c1ph3rj.scanly.domain.model.SignInk
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import `in`.c1ph3rj.scanly.feature.tools.ToolCompactHeader
import `in`.c1ph3rj.scanly.feature.tools.ToolContentPadding
import `in`.c1ph3rj.scanly.feature.tools.ToolFieldLabel
import `in`.c1ph3rj.scanly.feature.tools.ToolNote
import kotlinx.coroutines.flow.collect

private val SignatureAspectRatio = 2.2f
private val InitialsAspectRatio = 1.4f
private val CardCorner = 14.dp
private val MarkCardMinSize = 150.dp
private val DeleteIconColor = Color(0xFF6B757C)

/**
 * "Your signatures" screen: lists saved signatures and initials, lets the user delete them, and
 * opens the creator for a new one through [onCreate].
 *
 * The route wires [SignatureManageViewModel] into this screen and shows its one-shot messages in
 * a snackbar.
 *
 * @param onNavigateUp Called when the back button is pressed.
 * @param onCreate Called with the kind the user asked to add (signature or initials).
 * @param viewModel Source of the saved marks and the delete action.
 */
@Composable
fun SignatureManageRoute(
    onNavigateUp: () -> Unit,
    onCreate: (SignatureKind) -> Unit,
    viewModel: SignatureManageViewModel = hiltViewModel(),
) {
    val signatures by viewModel.signatures.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    SignatureManageScreen(
        signatures = signatures,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onCreate = onCreate,
        onDelete = viewModel::delete,
    )
}

/**
 * Stateless body of the "Your signatures" screen.
 *
 * Shows two sections, "Signatures" and "Initials", each ending with a dashed "+" tile. Deleting a
 * mark asks for confirmation first; the confirmed id is passed to [onDelete].
 *
 * @param signatures Every saved mark, both kinds.
 * @param snackbarHostState Host for one-shot messages from the ViewModel.
 * @param onNavigateUp Called when the back button is pressed.
 * @param onCreate Called with the kind the user asked to add.
 * @param onDelete Called with the id of a mark the user confirmed deleting.
 * @param modifier Applied to the screen root.
 */
@Composable
fun SignatureManageScreen(
    signatures: List<SavedSignature>,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onCreate: (SignatureKind) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDelete = signatures.firstOrNull { it.id == pendingDeleteId }
    val drawnSignatures = signatures.filter { it.kind == SignatureKind.SIGNATURE }
    val initials = signatures.filter { it.kind == SignatureKind.INITIALS }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ToolCompactHeader(title = "Your signatures", onBack = onNavigateUp)
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
                            text = "Signatures stay on this phone. Draw one or upload a photo of your signature.",
                        )
                    }
                }
                markSection(
                    title = "Signatures",
                    kind = SignatureKind.SIGNATURE,
                    marks = drawnSignatures,
                    aspectRatio = SignatureAspectRatio,
                    onCreate = onCreate,
                    onRequestDelete = { pendingDeleteId = it.id },
                )
                markSection(
                    title = "Initials",
                    kind = SignatureKind.INITIALS,
                    marks = initials,
                    aspectRatio = InitialsAspectRatio,
                    onCreate = onCreate,
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

    pendingDelete?.let { mark ->
        val noun = mark.kind.label.lowercase()
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete $noun?") },
            text = { Text("Pages you've already signed keep it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteId = null
                        onDelete(mark.id)
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Cancel")
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
    onRequestDelete: (SavedSignature) -> Unit,
) {
    item(key = "header-${kind.storageValue}", span = { GridItemSpan(maxLineSpan) }) {
        ToolFieldLabel(title, modifier = Modifier.padding(top = 4.dp))
    }
    items(marks, key = { it.id }) { mark ->
        SavedMarkCard(
            signature = mark,
            aspectRatio = aspectRatio,
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
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(CardCorner)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        SavedSignatureImage(
            imagePath = signature.imagePath,
            ink = SignInk.Default,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentDescription = "Saved ${signature.kind.label.lowercase()}",
        )
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
                contentDescription = "Delete ${signature.kind.label.lowercase()}",
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
    val label = "Add ${kind.label.lowercase()}"
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
