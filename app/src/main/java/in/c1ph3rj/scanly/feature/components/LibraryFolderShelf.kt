package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup

/** Inset between a folder tile's press bounds and its art/text; callers offset rows by it. */
val LibraryFolderTileInset = 6.dp
private val LibraryFolderPressShape = RoundedCornerShape(20.dp)

/**
 * Library folder tile: a paper sheet peeking out of a folder pocket.
 * Set the width via [modifier]; the art area keeps a 1:1 aspect ratio.
 */
@Composable
fun LibraryFolderTile(
    group: DocumentGroup,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val hasMenu = onRename != null || onDelete != null
    var menuExpanded by remember { mutableStateOf(false) }
    val openMenu: () -> Unit = { menuExpanded = true }
    val documentsLabel = if (group.documentCount == 1) {
        "1 document"
    } else {
        "${group.documentCount} documents"
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(LibraryFolderPressShape)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = if (hasMenu) openMenu else null,
                    role = Role.Button,
                )
                .semantics(mergeDescendants = true) {
                    customActions = listOfNotNull(
                        onRename?.let { callback ->
                            CustomAccessibilityAction("Rename") {
                                callback()
                                true
                            }
                        },
                        onDelete?.let { callback ->
                            CustomAccessibilityAction("Delete") {
                                callback()
                                true
                            }
                        },
                    )
                }
                .padding(LibraryFolderTileInset),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryFolderArt(
                group = group,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )

            // Text block: title + meta line
            Column(
                modifier = Modifier.padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = documentsLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (hasMenu) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (onRename != null) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                }
                if (onDelete != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Delete",
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Square folder illustration: a back panel with a tab, an optional extra sheet
 * for multi-document groups, the cover paper, and a front pocket. Transparent
 * background; every layer is sized from the box width so it scales cleanly.
 */
@Composable
fun LibraryFolderArt(
    group: DocumentGroup,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // primaryContainer is very pale on the light page; folders get a deeper teal there so
    // they read as solid objects. Dark themes keep primaryContainer.
    val folderColor = if (colors.background.luminance() > 0.5f) LightFolderTeal else colors.primaryContainer
    val backPanelColor = folderColor
        .copy(alpha = 0.55f)
        .compositeOver(colors.background)
    val coverPath = group.coverThumbnailPath
    val hasMultipleDocuments = group.documentCount > 1

    BoxWithConstraints(modifier = modifier) {
        val width = maxWidth
        val density = LocalDensity.current
        val sheetShiftPx = with(density) { (width * -0.05f).toPx() }
        val paperRotation = if (hasMultipleDocuments) 3f else 0f

        val paperModifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = width * 0.18f)
            .fillMaxWidth(0.64f)
            .height(width * 0.6f)
            .graphicsLayer { rotationZ = paperRotation }

        // 1. Back panel with a tab, drawn first.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    drawRoundRect(
                        color = backPanelColor,
                        topLeft = Offset(0f, w * 0.04f),
                        size = Size(w * 0.46f, w * 0.20f),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                    )
                    drawRoundRect(
                        color = backPanelColor,
                        topLeft = Offset(0f, w * 0.12f),
                        size = Size(w, w * 0.88f),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                    )
                },
        ) { }

        // 2. Extra sheet behind the cover, only for multi-document groups.
        if (hasMultipleDocuments) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = width * 0.20f)
                    .fillMaxWidth(0.60f)
                    .height(width * 0.6f)
                    .graphicsLayer {
                        rotationZ = -7f
                        translationX = sheetShiftPx
                    }
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surfaceContainerHighest),
            ) { }
        }

        // 3. Cover paper. Falls back to a plain sheet when no thumbnail exists yet.
        // Empty folders get no paper at all.
        if (group.documentCount > 0) {
            if (coverPath != null) {
                CachedThumbnail(
                    thumbnailPath = coverPath,
                    title = group.title,
                    contentRevision = group.coverUpdatedAtMillis,
                    modifier = paperModifier,
                    shape = RoundedCornerShape(4.dp),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    placeholderIcon = null,
                    containerColor = colors.surfaceContainerHighest,
                )
            } else {
                Box(
                    modifier = paperModifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.surfaceContainerHighest),
                ) { }
            }
        }

        // 4. Front pocket, overlapping the lower part of the paper.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .clip(
                    RoundedCornerShape(
                        topStart = 6.dp,
                        topEnd = 6.dp,
                        bottomStart = 14.dp,
                        bottomEnd = 14.dp,
                    ),
                )
                .background(folderColor),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.onPrimaryContainer.copy(alpha = 0.10f)),
            )
        }
    }
}

/**
 * "New folder" tile with a dashed outline. Shares the same outer geometry as
 * [LibraryFolderTile] so the two line up in a row.
 */
@Composable
fun LibraryNewFolderTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = LibraryFolderActionTile("New folder", "Organise scans", Icons.Outlined.CreateNewFolder, onClick, modifier)

@Composable
fun LibraryViewAllFoldersTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = LibraryFolderActionTile("View all", "All folders", Icons.Outlined.GridView, onClick, modifier, filled = true)

@Composable
private fun LibraryFolderActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
    filled: Boolean = false,
) {
    val dashedColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LibraryFolderPressShape)
            .clickable(
                onClick = onClick,
                role = Role.Button,
            )
            .semantics(mergeDescendants = true) {}
            .padding(LibraryFolderTileInset),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            val width = maxWidth

            if (filled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = width * 0.12f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(36.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val strokeWidth = 1.5.dp.toPx()
                            val inset = strokeWidth / 2f
                            val w = size.width
                            drawRoundRect(
                                color = dashedColor,
                                topLeft = Offset(inset, w * 0.12f + inset),
                                size = Size(w - strokeWidth, w * 0.88f - strokeWidth),
                                cornerRadius = CornerRadius(14.dp.toPx()),
                                style = Stroke(
                                    width = strokeWidth,
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                                    ),
                                ),
                            )
                        },
                ) { }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = width * 0.12f)
                        .size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Text block mirrors LibraryFolderTile's two-line structure.
        Column(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val LightFolderTeal = androidx.compose.ui.graphics.Color(0xFFA9DDD5)
