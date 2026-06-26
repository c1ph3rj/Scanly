package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dagger.hilt.android.EntryPointAccessors
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.ui.ChromeIconButton
import `in`.c1ph3rj.scanly.core.ui.MetricChip
import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.core.ui.PreviewImageSizer
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.previewImagePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

// ─── Formatting Helpers ────────────────────────────────────────────────────────

fun Long.toShortDate(): String =
    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(this))

fun Long.toRelativeDate(): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))


// ─── Headers ───────────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

// ─── Thumbnails ────────────────────────────────────────────────────────────────

@Composable
fun CachedThumbnail(
    thumbnailPath: String?,
    title: String,
    displaySize: PreviewDisplaySize = PreviewDisplaySize.CARD,
    contentRevision: Long = 0L,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIcon: (@Composable () -> Unit)?,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val cache = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            `in`.c1ph3rj.scanly.core.ui.ThumbnailCacheEntryPoint::class.java,
        ).thumbnailCache()
    }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val targetPx = remember(containerSize, displaySize, density) {
        PreviewImageSizer.targetPxForContainer(
            widthPx = containerSize.width,
            heightPx = containerSize.height,
            size = displaySize,
            density = density,
        )
    }
    val cachedImage = remember(thumbnailPath, targetPx, contentRevision) {
        thumbnailPath?.let { path ->
            cache.getIfCached(path, targetPx, contentRevision)?.asImageBitmap()
        }
    }

    val imageBitmap by produceState<ImageBitmap?>(
        initialValue = cachedImage,
        key1 = thumbnailPath,
        key2 = targetPx,
        key3 = contentRevision,
    ) {
        val path = thumbnailPath
        if (path == null) {
            value = null
            return@produceState
        }
        cache.getIfCached(path, targetPx, contentRevision)?.let { bitmap ->
            value = bitmap.asImageBitmap()
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            cache.decode(path, targetPx, contentRevision)?.asImageBitmap()
        }
    }

    Surface(
        modifier = modifier.onSizeChanged { size ->
            if (size != IntSize.Zero) {
                containerSize = size
            }
        },
        color = if (imageBitmap != null) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        shape = shape,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                filterQuality = FilterQuality.High,
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (placeholderIcon != null) {
                    placeholderIcon()
                } else {
                    Text(
                        text = DocumentPresentationFormatter.initials(title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentThumbnail(
    thumbnailPath: String?,
    title: String,
    contentRevision: Long = 0L,
    displaySize: PreviewDisplaySize = PreviewDisplaySize.CARD,
    modifier: Modifier = Modifier,
    minHeight: Dp = 90.dp,
    aspectRatio: Float? = 3f / 4f,
    contentScale: ContentScale = if (displaySize == PreviewDisplaySize.DETAIL) {
        ContentScale.Fit
    } else {
        ContentScale.Crop
    },
) {
    CachedThumbnail(
        thumbnailPath = thumbnailPath,
        title = title,
        displaySize = displaySize,
        contentRevision = contentRevision,
        contentScale = contentScale,
        modifier = modifier
            .heightIn(min = minHeight)
            .let { if (aspectRatio != null) it.aspectRatio(aspectRatio) else it },
        placeholderIcon = {
            Text(
                text = DocumentPresentationFormatter.initials(title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    )
}

@Composable
fun PagePreview(
    page: ScanPage,
    displaySize: PreviewDisplaySize,
    modifier: Modifier = Modifier,
    minHeight: Dp = when (displaySize) {
        PreviewDisplaySize.COMPACT -> 56.dp
        PreviewDisplaySize.CARD -> 90.dp
        PreviewDisplaySize.DETAIL -> 120.dp
    },
    aspectRatio: Float? = if (displaySize == PreviewDisplaySize.DETAIL) null else 3f / 4f,
) {
    DocumentThumbnail(
        thumbnailPath = page.previewImagePath(displaySize),
        title = "Page ${page.pageIndex + 1}",
        contentRevision = page.updatedAtMillis,
        displaySize = displaySize,
        modifier = modifier,
        minHeight = minHeight,
        aspectRatio = aspectRatio,
    )
}

// ─── Branding ──────────────────────────────────────────────────────────────────

@Composable
fun ScanlyAppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { (size * 1.35f).roundToPx().coerceAtLeast(1) }
    val painter = remember(context.packageName, sizePx) {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        BitmapPainter(drawable.toBitmap(sizePx, sizePx).asImageBitmap())
    }
    Image(
        painter = painter,
        contentDescription = "Scanly",
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f)),
        contentScale = ContentScale.Crop,
    )
}

// ─── Dialogs ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanlyFormDialogShell(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalMargin: Dp = 24.dp,
    maxWidth: Dp = 560.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    val adaptiveMaxWidth = if (windowSizeInfo.isTablet) windowSizeInfo.dialogMaxWidth else maxWidth
    val horizontalPadding = if (windowSizeInfo.isTablet) 32.dp else horizontalMargin

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .widthIn(max = adaptiveMaxWidth)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun ScanlyConfirmDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "Confirm",
    dismissLabel: String = "Cancel",
    confirmDestructive: Boolean = false,
    dismissEnabled: Boolean = true,
    confirmEnabled: Boolean = true,
) {
    ScanlyFormDialogShell(onDismiss = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDismiss,
                enabled = dismissEnabled,
            ) {
                Text(dismissLabel)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
            ) {
                Text(
                    text = confirmLabel,
                    color = if (confirmDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

@Composable
fun ScanlySheetContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = modifier
                .then(
                    if (windowSizeInfo.isTablet) {
                        Modifier.widthIn(max = windowSizeInfo.sheetMaxWidth)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                )
                .padding(horizontal = if (windowSizeInfo.isTablet) 24.dp else 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ScanlyDialogActions(
    onDismiss: () -> Unit,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = onConfirm,
            enabled = confirmEnabled,
        ) { Text(confirmLabel) }
    }
}

@Composable
fun DocumentTitleDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    ScanlyFormDialogShell(onDismiss = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ScanlyDialogActions(
            onDismiss = onDismiss,
            confirmLabel = confirmLabel,
            confirmEnabled = value.isNotBlank(),
            onConfirm = { if (value.isNotBlank()) onConfirm(value) },
        )
    }
}

@Composable
fun GroupNameDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    confirmLabel: String = "Create",
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    ScanlyFormDialogShell(onDismiss = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text("Folder name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ScanlyDialogActions(
            onDismiss = onDismiss,
            confirmLabel = confirmLabel,
            confirmEnabled = value.isNotBlank(),
            onConfirm = { if (value.isNotBlank()) onConfirm(value) },
        )
    }
}

/**
 * Unified folder picker used wherever a document can be moved between folders
 * (Library cards and the document detail screen). It clearly shows the document's
 * current folder, lets the user switch to another folder, remove it from its
 * current folder ("No folder"), or create a new folder and move into it in one step.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MoveToFolderSheet(
    currentGroupId: String?,
    groups: List<DocumentGroup>,
    onDismiss: () -> Unit,
    onSelectFolder: (String?) -> Unit,
    onCreateFolderAndMove: (String) -> Unit,
) {
    var creatingFolder by rememberSaveable { mutableStateOf(false) }
    var newFolderName by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ScanlySheetContent {
            Text(
                text = "Move to folder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            FolderPickerRow(
                label = "No folder",
                icon = Icons.Filled.FolderOff,
                selected = currentGroupId == null,
                onClick = { onSelectFolder(null) },
            )

            if (groups.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(groups, key = { it.id }) { group ->
                        FolderPickerRow(
                            label = group.title,
                            icon = Icons.Filled.Folder,
                            selected = currentGroupId == group.id,
                            onClick = { onSelectFolder(group.id) },
                        )
                    }
                }
            }

            if (creatingFolder) {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("New folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = {
                            creatingFolder = false
                            newFolderName = ""
                        },
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            val name = newFolderName.trim()
                            if (name.isNotEmpty()) onCreateFolderAndMove(name)
                        },
                        enabled = newFolderName.isNotBlank(),
                    ) { Text("Create & move") }
                }
            } else {
                FolderPickerRow(
                    label = "Create new folder",
                    icon = Icons.Filled.CreateNewFolder,
                    selected = false,
                    onClick = { creatingFolder = true },
                )
            }
        }
    }
}

@Composable
fun FolderPickerRow(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ─── Cards ─────────────────────────────────────────────────────────────────────

/** Controls list vs. grid presentation for library cards. */
enum class LibraryCardStyle {
    /** Pick grid when the card is narrower than [GridCardMaxWidth]. */
    Auto,
    /** Full-width horizontal list row. */
    List,
    /** Vertical tile for multi-column grids. */
    Grid,
}

private val GridCardMaxWidth = 280.dp

@Composable
fun GroupCard(
    group: DocumentGroup,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    style: LibraryCardStyle = LibraryCardStyle.Auto,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
        ),
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useGrid = when (style) {
                LibraryCardStyle.Grid -> true
                LibraryCardStyle.List -> false
                LibraryCardStyle.Auto -> maxWidth < GridCardMaxWidth
            }
            GroupCardContent(
                group = group,
                onRename = onRename,
                onDelete = onDelete,
                compact = useGrid,
            )
        }
    }
}

@Composable
private fun GroupCardContent(
    group: DocumentGroup,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    compact: Boolean,
) {
    val padding = if (compact) 10.dp else 14.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding),
    ) {
        CachedThumbnail(
            thumbnailPath = group.coverThumbnailPath,
            title = group.title,
            displaySize = PreviewDisplaySize.CARD,
            contentRevision = group.coverUpdatedAtMillis,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
            placeholderIcon = {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(if (compact) 28.dp else 36.dp),
                )
            },
        )
        Spacer(modifier = Modifier.height(if (compact) 8.dp else 10.dp))
        Text(
            text = group.title,
            style = if (compact) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 1,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = buildString {
                append("${group.documentCount} docs")
                if (group.totalPageCount > 0) append("  ·  ${group.totalPageCount} pg")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LibraryCardActions(
            onRename = onRename,
            onDelete = onDelete,
            compact = compact,
        )
    }
}

@Composable
fun DocumentCard(
    document: ScanDocument,
    onOpen: () -> Unit,
    onRename: () -> Unit = {},
    onDelete: () -> Unit,
    onMove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    style: LibraryCardStyle = LibraryCardStyle.Auto,
    showRename: Boolean = true,
    deleteContentDescription: String = "Delete",
) {
    val updatedDate = remember(document.updatedAtMillis) {
        document.updatedAtMillis.toShortDate()
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 1.dp,
        tonalElevation = 1.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useGrid = when (style) {
                LibraryCardStyle.Grid -> true
                LibraryCardStyle.List -> false
                LibraryCardStyle.Auto -> maxWidth < GridCardMaxWidth
            }
            if (useGrid) {
                DocumentCardGridContent(
                    document = document,
                    updatedDate = updatedDate,
                    onRename = onRename,
                    onDelete = onDelete,
                    onMove = onMove,
                    showRename = showRename,
                    deleteContentDescription = deleteContentDescription,
                )
            } else {
                DocumentCardListContent(
                    document = document,
                    updatedDate = updatedDate,
                    onRename = onRename,
                    onDelete = onDelete,
                    onMove = onMove,
                    showRename = showRename,
                    deleteContentDescription = deleteContentDescription,
                )
            }
        }
    }
}

@Composable
private fun DocumentCardGridContent(
    document: ScanDocument,
    updatedDate: String,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: (() -> Unit)?,
    showRename: Boolean,
    deleteContentDescription: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .padding(10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            CachedThumbnail(
                thumbnailPath = document.coverThumbnailPath,
                title = document.title,
                displaySize = PreviewDisplaySize.CARD,
                contentRevision = document.updatedAtMillis,
                modifier = Modifier.fillMaxSize(),
                placeholderIcon = {
                    Text(
                        text = DocumentPresentationFormatter.initials(document.title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DocumentMetaPill(
                    label = "${document.pageCount} pg",
                    icon = Icons.Filled.Description,
                )
                DocumentMetaPill(label = updatedDate)
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showRename) {
                LibraryCardIconButton(
                    icon = Icons.Filled.Edit,
                    contentDescription = "Rename",
                    onClick = onRename,
                    size = 32.dp,
                    containerColor = Color.Black.copy(alpha = 0.42f),
                    contentColor = Color.White,
                )
            }
            if (onMove != null) {
                LibraryCardIconButton(
                    icon = Icons.Filled.Folder,
                    contentDescription = "Move to folder",
                    onClick = onMove,
                    size = 32.dp,
                    containerColor = Color.Black.copy(alpha = 0.42f),
                    contentColor = Color.White,
                )
            }
            LibraryCardIconButton(
                icon = Icons.Filled.DeleteOutline,
                contentDescription = deleteContentDescription,
                onClick = onDelete,
                size = 32.dp,
                containerColor = Color.Black.copy(alpha = 0.42f),
                contentColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DocumentCardListContent(
    document: ScanDocument,
    updatedDate: String,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: (() -> Unit)?,
    showRename: Boolean,
    deleteContentDescription: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .width(82.dp)
                .aspectRatio(3f / 4f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
            ),
            shadowElevation = 2.dp,
        ) {
            CachedThumbnail(
                thumbnailPath = document.coverThumbnailPath,
                title = document.title,
                displaySize = PreviewDisplaySize.CARD,
                contentRevision = document.updatedAtMillis,
                modifier = Modifier.fillMaxSize(),
                placeholderIcon = {
                    Text(
                        text = DocumentPresentationFormatter.initials(document.title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DocumentMetaPill(
                    label = "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"}",
                    icon = Icons.Filled.Description,
                )
                DocumentMetaPill(label = updatedDate)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showRename) {
                LibraryCardIconButton(
                    icon = Icons.Filled.Edit,
                    contentDescription = "Rename",
                    onClick = onRename,
                    size = 38.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                )
            }
            if (onMove != null) {
                LibraryCardIconButton(
                    icon = Icons.Filled.Folder,
                    contentDescription = "Move to folder",
                    onClick = onMove,
                    size = 38.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                )
            }
            LibraryCardIconButton(
                icon = Icons.Filled.DeleteOutline,
                contentDescription = deleteContentDescription,
                onClick = onDelete,
                size = 38.dp,
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DocumentMetaPill(
    label: String,
    icon: ImageVector? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.76f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibraryCardActions(
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: (() -> Unit)? = null,
    showRename: Boolean = true,
    deleteContentDescription: String = "Delete",
    compact: Boolean,
) {
    val buttonSize = if (compact) 34.dp else 40.dp
    val spacing = if (compact) 6.dp else 8.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showRename) {
            LibraryCardIconButton(
                icon = Icons.Filled.Edit,
                contentDescription = "Rename",
                onClick = onRename,
                size = buttonSize,
            )
        }
        if (onMove != null) {
            LibraryCardIconButton(
                icon = Icons.Filled.Folder,
                contentDescription = "Move to folder",
                onClick = onMove,
                size = buttonSize,
            )
        }
        LibraryCardIconButton(
            icon = Icons.Filled.DeleteOutline,
            contentDescription = deleteContentDescription,
            onClick = onDelete,
            size = buttonSize,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun LibraryCardIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = Modifier.size(size),
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

// ─── Loaders ───────────────────────────────────────────────────────────────────

@Composable
fun FullScreenLoader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
