package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.common.toShortDate
import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument

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
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
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
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
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
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
            shadowElevation = 0.dp,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                    if (onMove != null) {
                        LibraryCardIconButton(
                            icon = Icons.Filled.Folder,
                            contentDescription = "Move to folder",
                            onClick = onMove,
                            size = 38.dp,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                }
                LibraryCardIconButton(
                    icon = Icons.Filled.DeleteOutline,
                    contentDescription = deleteContentDescription,
                    onClick = onDelete,
                    size = 38.dp,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun DocumentMetaPill(
    label: String,
    icon: ImageVector? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
