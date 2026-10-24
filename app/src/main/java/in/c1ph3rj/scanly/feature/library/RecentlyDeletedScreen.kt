package `in`.c1ph3rj.scanly.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
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
import kotlinx.coroutines.flow.collectLatest

/**
 * "Recently deleted" screen: lists folders, documents and pages in the bin with how long each has
 * left, lets the user restore them or delete them for good, one at a time or several at once, and
 * empties the whole bin.
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
        viewModel.events.collectLatest { event ->
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
        onRestoreAll = viewModel::restoreAll,
        onDeleteForeverAll = viewModel::deleteForeverAll,
    )
}

/**
 * Stateless body of the "Recently deleted" screen.
 *
 * Rows are grouped into Folders, Documents and Pages. Nothing is opened from here: each row has
 * Restore and Delete forever icon buttons, and tapping or long-pressing a row opens a quick menu
 * with the same actions plus Select. Once something is selected, taps toggle rows, the top bar
 * counts the selection and a bottom bar restores or deletes all of it. Every destructive action
 * asks for confirmation before [onDeleteForever], [onDeleteForeverAll] or [onEmptyAll] runs.
 *
 * @param uiState Trashed folders, documents and pages, loading flag and in-flight row keys.
 * @param onNavigateUp Called when the back button is pressed.
 * @param onRestore Called with a row the user asked to bring back.
 * @param onDeleteForever Called with a row the user confirmed deleting.
 * @param onEmptyAll Called when the user confirmed emptying the whole bin.
 * @param snackbarHostState Host for one-shot messages from the ViewModel.
 * @param onRestoreAll Called with the selected rows the user asked to bring back.
 * @param onDeleteForeverAll Called with the selected rows the user confirmed deleting.
 */
@Composable
fun RecentlyDeletedScreen(
    uiState: RecentlyDeletedUiState,
    onNavigateUp: () -> Unit,
    onRestore: (RecentlyDeletedRow) -> Unit,
    onDeleteForever: (RecentlyDeletedRow) -> Unit,
    onEmptyAll: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onRestoreAll: (List<RecentlyDeletedRow>) -> Unit = {},
    onDeleteForeverAll: (List<RecentlyDeletedRow>) -> Unit = {},
) {
    val window = rememberWindowSizeInfo()
    var showEmptyWarning by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteKey by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDeleteSelection by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by rememberSaveable { mutableStateOf(listOf<String>()) }
    val allRows = uiState.folders + uiState.documents + uiState.pages
    val pendingDelete = allRows.firstOrNull { it.key == pendingDeleteKey }
    val selectedRows = allRows.filter { it.key in selectedKeys }
    val selecting = selectedKeys.isNotEmpty()

    // Rows restored or deleted (here or by expiry) leave the selection with them.
    LaunchedEffect(allRows.map { it.key }) {
        val live = allRows.map { it.key }.toSet()
        if (selectedKeys.any { it !in live }) selectedKeys = selectedKeys.filter { it in live }
    }
    BackHandler(enabled = selecting) { selectedKeys = emptyList() }

    fun toggle(row: RecentlyDeletedRow) {
        selectedKeys = if (row.key in selectedKeys) selectedKeys - row.key else selectedKeys + row.key
    }

    ScanlyDetailScaffold(
        title = if (selecting) "${selectedKeys.size} selected" else "Recently deleted",
        onNavigateUp = if (selecting) ({ selectedKeys = emptyList() }) else onNavigateUp,
        navigationIcon = if (selecting) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
        navigationContentDescription = if (selecting) "Clear selection" else "Back",
        actions = {
            when {
                selecting -> TextButton(
                    onClick = {
                        selectedKeys = if (selectedKeys.size == allRows.size) {
                            emptyList()
                        } else {
                            allRows.map { it.key }
                        }
                    },
                    enabled = !uiState.isEmptying,
                ) {
                    Text(if (selectedKeys.size == allRows.size) "Select none" else "Select all")
                }
                uiState.itemCount > 0 -> TextButton(
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
                modifier = if (selecting) Modifier else Modifier.navigationBarsPadding(),
            )
        },
        bottomBar = {
            if (selecting) {
                SelectionActionBar(
                    count = selectedRows.size,
                    enabled = !uiState.isEmptying && selectedRows.none { it.key in uiState.busyKeys },
                    onRestore = { onRestoreAll(selectedRows) },
                    onDelete = { confirmDeleteSelection = true },
                )
            }
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
                    .then(if (selecting) Modifier else Modifier.navigationBarsPadding()),
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
                            text = recentlyDeletedRetentionNote + " Long-press an item for more options.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    listOf(
                        "Folders" to uiState.folders,
                        "Documents" to uiState.documents,
                        "Pages" to uiState.pages,
                    ).forEach { (title, rows) ->
                        trashSection(
                            title = title,
                            rows = rows,
                            busyKeys = uiState.busyKeys,
                            isEmptying = uiState.isEmptying,
                            selecting = selecting,
                            selectedKeys = selectedKeys,
                            onToggle = ::toggle,
                            onSelect = { row -> selectedKeys = selectedKeys + row.key },
                            onRestore = onRestore,
                            onRequestDelete = { pendingDeleteKey = it.key },
                        )
                    }
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

    if (confirmDeleteSelection && selectedRows.isNotEmpty()) {
        val count = selectedRows.size
        ScanlyConfirmDialog(
            title = if (count == 1) "Delete 1 item forever?" else "Delete $count items forever?",
            text = if (count == 1) {
                deleteForeverText(selectedRows.single().item)
            } else {
                "The $count selected items will be deleted permanently. This can't be undone."
            },
            onDismiss = { confirmDeleteSelection = false },
            onConfirm = {
                confirmDeleteSelection = false
                onDeleteForeverAll(selectedRows)
            },
            confirmLabel = "Delete",
            confirmDestructive = true,
        )
    }
}

/** A section header with its count, then one grouped list of rows. Emits nothing when [rows] is empty. */
private fun LazyListScope.trashSection(
    title: String,
    rows: List<RecentlyDeletedRow>,
    busyKeys: Set<String>,
    isEmptying: Boolean,
    selecting: Boolean,
    selectedKeys: List<String>,
    onToggle: (RecentlyDeletedRow) -> Unit,
    onSelect: (RecentlyDeletedRow) -> Unit,
    onRestore: (RecentlyDeletedRow) -> Unit,
    onRequestDelete: (RecentlyDeletedRow) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "$title-header") {
        Text(
            text = "$title · ${rows.size}",
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
                selecting = selecting,
                selected = row.key in selectedKeys,
                onToggle = { onToggle(row) },
                onSelect = { onSelect(row) },
                onRestore = { onRestore(row) },
                onRequestDelete = { onRequestDelete(row) },
            )
        }
    }
}

/**
 * One trashed folder, document or page: thumbnail, title, detail, a "days left" pill, and either
 * the Restore / Delete forever icon buttons or, while selecting, a selection mark.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentlyDeletedRowContent(
    row: RecentlyDeletedRow,
    actionsEnabled: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
    onRestore: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val item = row.item
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val label = deleteLabel(item)

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .combinedClickable(
                    enabled = actionsEnabled,
                    role = Role.Button,
                    onClickLabel = if (selecting) "Select" else "Show options",
                    onClick = { if (selecting) onToggle() else menuOpen = true },
                    onLongClickLabel = if (selecting) "Select" else "Show options",
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (selecting) onToggle() else menuOpen = true
                    },
                )
                .semantics { this.selected = selected }
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
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
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = rowTitle(item),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = rowDetail(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                PurgePill(daysUntilPurge = row.daysUntilPurge)
            }
            if (selecting) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp),
                )
            } else {
                FilledTonalIconButton(
                    onClick = onRestore,
                    enabled = actionsEnabled,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restore,
                        contentDescription = "Restore $label",
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(
                    onClick = onRequestDelete,
                    enabled = actionsEnabled,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = "Delete $label forever",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        RowQuickMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onRestore = {
                menuOpen = false
                onRestore()
            },
            onSelect = {
                menuOpen = false
                onSelect()
            },
            onDelete = {
                menuOpen = false
                onRequestDelete()
            },
        )
    }
}

/** Long-press menu: Restore, Select, then the destructive Delete forever set apart. */
@Composable
private fun RowQuickMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Restore") },
            leadingIcon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
            onClick = onRestore,
        )
        DropdownMenuItem(
            text = { Text("Select") },
            leadingIcon = { Icon(Icons.Outlined.Checklist, contentDescription = null) },
            onClick = onSelect,
        )
        // Kept apart so a tap meant for Restore can't land on Delete forever.
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Delete forever", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    Icons.Outlined.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = onDelete,
        )
    }
}

/** "5 days left" on a quiet pill; the last day turns to the error colour. */
@Composable
private fun PurgePill(daysUntilPurge: Int) {
    val urgent = daysUntilPurge <= 1
    Surface(
        shape = CircleShape,
        color = if (urgent) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (urgent) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = daysLeftLabel(daysUntilPurge),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Selection bar: restore or permanently delete everything selected. */
@Composable
private fun SelectionActionBar(
    count: Int,
    enabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            // Bar spans the screen; the two buttons keep the 640 dp action column on tablets.
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    enabled = enabled && count > 0,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(
                        Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.error else Color.Unspecified,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Delete",
                        color = if (enabled) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                }
                Button(
                    onClick = onRestore,
                    enabled = enabled && count > 0,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Restore")
                }
            }
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

/** "3 documents", "5 pages", "From \"Invoice\"", "Folder only · 2 documents kept in Library". */
private fun rowDetail(item: TrashedItem): String = when (item) {
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

/** "5 days left", "1 day left", "Last day". */
private fun daysLeftLabel(daysUntilPurge: Int): String = when {
    daysUntilPurge <= 0 -> "Last day"
    daysUntilPurge == 1 -> "1 day left"
    else -> "$daysUntilPurge days left"
}

/** Target of "Restore / Delete <label>" for screen readers. */
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
