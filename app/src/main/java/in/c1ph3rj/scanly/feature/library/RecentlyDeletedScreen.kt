package `in`.c1ph3rj.scanly.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.TrashedDocument
import `in`.c1ph3rj.scanly.domain.model.TrashedFolder
import `in`.c1ph3rj.scanly.domain.model.TrashedItem
import `in`.c1ph3rj.scanly.domain.model.TrashedPage
import `in`.c1ph3rj.scanly.domain.trash.TrashKind
import `in`.c1ph3rj.scanly.feature.components.CachedThumbnail
import `in`.c1ph3rj.scanly.feature.components.LibraryListItem
import `in`.c1ph3rj.scanly.feature.components.PipEmptyState
import `in`.c1ph3rj.scanly.feature.components.ScanlyConfirmDialog
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold
import `in`.c1ph3rj.scanly.feature.pip.PipMood
import kotlinx.coroutines.flow.collect

/**
 * "Recently deleted" screen: lists folders, documents and pages in the bin with how long each has
 * left, lets the user restore them or delete them for good, and empties the whole bin.
 *
 * The route wires [RecentlyDeletedViewModel] into [RecentlyDeletedScreen] and shows its one-shot
 * messages in a snackbar.
 *
 * @param onNavigateUp Called when the back button is pressed.
 * @param viewModel Source of the trashed items and the restore, delete and empty actions.
 */
@Composable
fun RecentlyDeletedRoute(
    onNavigateUp: () -> Unit,
    viewModel: RecentlyDeletedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RecentlyDeletedEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    RecentlyDeletedScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onRestore = viewModel::restore,
        onDeleteForever = viewModel::deleteForever,
        onEmptyAll = viewModel::emptyAll,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Stateless body of the "Recently deleted" screen.
 *
 * Rows are grouped into Folders, Documents and Pages. Nothing is opened from here: each row only
 * offers Restore and Delete forever, and both destructive actions ask for confirmation before
 * [onDeleteForever] or [onEmptyAll] runs.
 *
 * @param uiState Trashed folders, documents and pages, loading flag and in-flight row keys.
 * @param onNavigateUp Called when the back button is pressed.
 * @param onRestore Called with a row the user asked to bring back.
 * @param onDeleteForever Called with a row the user confirmed deleting.
 * @param onEmptyAll Called when the user confirmed emptying the whole bin.
 * @param snackbarHostState Host for one-shot messages from the ViewModel.
 */
@Composable
fun RecentlyDeletedScreen(
    uiState: RecentlyDeletedUiState,
    onNavigateUp: () -> Unit,
    onRestore: (RecentlyDeletedRow) -> Unit,
    onDeleteForever: (RecentlyDeletedRow) -> Unit,
    onEmptyAll: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val window = rememberWindowSizeInfo()
    var showEmptyWarning by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteKey by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDelete = (uiState.folders + uiState.documents + uiState.pages)
        .firstOrNull { it.key == pendingDeleteKey }

    ScanlyDetailScaffold(
        title = "Recently deleted",
        onNavigateUp = onNavigateUp,
        actions = {
            if (uiState.itemCount > 0) {
                TextButton(
                    onClick = { showEmptyWarning = true },
                    enabled = !uiState.isEmptying,
                ) {
                    Text("Empty")
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(
                    start = window.horizontalPadding,
                    end = window.horizontalPadding,
                    top = 8.dp,
                    bottom = 28.dp,
                ),
            ) {
                if (uiState.isEmpty) {
                    val pageRetention = TrashKind.PAGE.retentionLabel()
                    val documentRetention = TrashKind.DOCUMENT.retentionLabel()
                    item(key = "empty-state") {
                        PipEmptyState(
                            pipLine = "nothing in the bin. deleted things wait here for a few days.",
                            pipMood = PipMood.IDLE,
                            title = "Nothing here",
                            description = "Deleted pages stay here for $pageRetention, and documents " +
                                "and folders for $documentRetention, so you can bring them back.",
                        )
                    }
                } else {
                    item(key = "retention-note") {
                        Text(
                            text = recentlyDeletedRetentionNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    trashSection(
                        title = "Folders",
                        keyPrefix = "folders",
                        rows = uiState.folders,
                        busyKeys = uiState.busyKeys,
                        isEmptying = uiState.isEmptying,
                        onRestore = onRestore,
                        onRequestDelete = { pendingDeleteKey = it.key },
                    )
                    trashSection(
                        title = "Documents",
                        keyPrefix = "documents",
                        rows = uiState.documents,
                        busyKeys = uiState.busyKeys,
                        isEmptying = uiState.isEmptying,
                        onRestore = onRestore,
                        onRequestDelete = { pendingDeleteKey = it.key },
                    )
                    trashSection(
                        title = "Pages",
                        keyPrefix = "pages",
                        rows = uiState.pages,
                        busyKeys = uiState.busyKeys,
                        isEmptying = uiState.isEmptying,
                        onRestore = onRestore,
                        onRequestDelete = { pendingDeleteKey = it.key },
                    )
                }
            }
        }
    }

    if (showEmptyWarning) {
        val count = uiState.itemCount
        ScanlyConfirmDialog(
            title = "Empty Recently deleted?",
            text = if (count == 1) {
                "This item will be deleted permanently. This can't be undone."
            } else {
                "All $count items will be deleted permanently. This can't be undone."
            },
            onDismiss = { showEmptyWarning = false },
            onConfirm = {
                showEmptyWarning = false
                onEmptyAll()
            },
            confirmLabel = "Delete all",
            confirmDestructive = true,
            dismissEnabled = !uiState.isEmptying,
            confirmEnabled = !uiState.isEmptying,
        )
    }

    pendingDelete?.let { row ->
        ScanlyConfirmDialog(
            title = "Delete forever?",
            text = deleteForeverText(row.item),
            onDismiss = { pendingDeleteKey = null },
            onConfirm = {
                pendingDeleteKey = null
                onDeleteForever(row)
            },
            confirmLabel = "Delete",
            confirmDestructive = true,
        )
    }
}

/** A section header followed by one grouped list of rows. Emits nothing when [rows] is empty. */
private fun LazyListScope.trashSection(
    title: String,
    keyPrefix: String,
    rows: List<RecentlyDeletedRow>,
    busyKeys: Set<String>,
    isEmptying: Boolean,
    onRestore: (RecentlyDeletedRow) -> Unit,
    onRequestDelete: (RecentlyDeletedRow) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "$keyPrefix-header") {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 4.dp, top = 12.dp, bottom = 8.dp)
                .semantics { heading() },
        )
    }
    itemsIndexed(
        items = rows,
        key = { _, row -> row.key },
        contentType = { _, _ -> "recently_deleted_row" },
    ) { index, row ->
        LibraryListItem(
            first = index == 0,
            last = index == rows.lastIndex,
        ) {
            RecentlyDeletedRowContent(
                row = row,
                actionsEnabled = row.key !in busyKeys && !isEmptying,
                onRestore = { onRestore(row) },
                onRequestDelete = { onRequestDelete(row) },
            )
        }
    }
}

/** One trashed folder, document or page: thumbnail, title, days left, and the two row actions. */
@Composable
private fun RecentlyDeletedRowContent(
    row: RecentlyDeletedRow,
    actionsEnabled: Boolean,
    onRestore: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val item = row.item
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (item) {
            is TrashedFolder -> FolderThumbnail()
            is TrashedDocument -> TrashThumbnail(
                thumbnailPath = item.document.coverThumbnailPath,
                contentRevision = item.document.updatedAtMillis,
                title = item.document.title,
                placeholder = DocumentPresentationFormatter.initials(item.document.title),
            )
            is TrashedPage -> TrashThumbnail(
                thumbnailPath = item.thumbnailPath,
                title = "Page ${item.pageNumber}",
                placeholder = item.pageNumber.toString(),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = rowTitle(item),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = rowSubtitle(item, row.daysUntilPurge),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalButton(
            onClick = onRestore,
            enabled = actionsEnabled,
        ) {
            Text("Restore")
        }
        IconButton(
            onClick = onRequestDelete,
            enabled = actionsEnabled,
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete ${deleteLabel(item)} forever",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private val TrashThumbnailShape = RoundedCornerShape(8.dp)

/** Folders have no cover image, so they get a folder glyph in the same 48 x 64 slot as documents. */
@Composable
private fun FolderThumbnail() {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 64.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, TrashThumbnailShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), TrashThumbnailShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Cover or page thumbnail in the 48 x 64 slot; [placeholder] shows while no image is available. */
@Composable
private fun TrashThumbnail(
    thumbnailPath: String?,
    title: String,
    placeholder: String,
    contentRevision: Long = 0L,
) {
    CachedThumbnail(
        thumbnailPath = thumbnailPath,
        title = title,
        contentRevision = contentRevision,
        modifier = Modifier
            .size(width = 48.dp, height = 64.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), TrashThumbnailShape),
        shape = TrashThumbnailShape,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
        placeholderIcon = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

private fun rowTitle(item: TrashedItem): String = when (item) {
    is TrashedFolder -> item.title
    is TrashedDocument -> item.document.title
    is TrashedPage -> "Page ${item.pageNumber}"
}

/** "3 documents · Deleted permanently in 5 days", "5 pages · …", "From \"Invoice\" · …". */
private fun rowSubtitle(item: TrashedItem, daysUntilPurge: Int): String {
    val detail = when (item) {
        is TrashedFolder -> when {
            // "Delete folder only": the documents stayed in the library and rejoin it on restore.
            item.keptDocumentCount == 1 -> "Folder only · 1 document kept in Library"
            item.keptDocumentCount > 1 -> "Folder only · ${item.keptDocumentCount} documents kept in Library"
            item.documentCount == 0 -> "No documents"
            item.documentCount == 1 -> "1 document"
            else -> "${item.documentCount} documents"
        }
        is TrashedDocument -> if (item.document.pageCount == 1) {
            "1 page"
        } else {
            "${item.document.pageCount} pages"
        }
        is TrashedPage -> "From \"${item.documentTitle}\""
    }
    return "$detail · ${purgeLabel(daysUntilPurge)}"
}

/** Target of "Delete <label> forever" for screen readers. */
private fun deleteLabel(item: TrashedItem): String = when (item) {
    is TrashedFolder -> "\"${item.title}\""
    is TrashedDocument -> "\"${item.document.title}\""
    is TrashedPage -> "page ${item.pageNumber} of \"${item.documentTitle}\""
}

private fun deleteForeverText(item: TrashedItem): String = when (item) {
    is TrashedFolder -> if (item.documentCount == 0) {
        // Folder only (or empty): its documents, if any, are safe in the library.
        "The folder \"${item.title}\" will be deleted permanently. This can't be undone." +
            if (item.keptDocumentCount > 0) " Its documents stay in your Library." else ""
    } else {
        "\"${item.title}\" and the documents that were in it will be deleted permanently. This can't be undone."
    }
    is TrashedDocument ->
        "\"${item.document.title}\" and its pages will be deleted permanently. This can't be undone."
    is TrashedPage ->
        "Page ${item.pageNumber} of \"${item.documentTitle}\" will be deleted permanently. This can't be undone."
}
