package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize
import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.feature.components.CachedThumbnail
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold

internal enum class PdfLibraryLayout { List, Grid }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSourcePickerSheet(
    documents: List<ScanDocument>,
    multiSelect: Boolean,
    onDismiss: () -> Unit,
    onPickDevice: () -> Unit,
    onConfirmLibrary: (List<PdfToolSource.LibraryDocument>) -> Unit,
    showDeviceOption: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var libraryLayout by remember { mutableStateOf(PdfLibraryLayout.Grid) }
    val windowSizeInfo = rememberWindowSizeInfo()
    val configuration = LocalConfiguration.current
    // Landscape / short windows: keep the library short so Confirm stays on-screen.
    val libraryHeight = when {
        configuration.screenHeightDp < 480 -> 140.dp
        configuration.screenHeightDp < 600 -> 180.dp
        configuration.screenHeightDp < 720 -> 240.dp
        windowSizeInfo.widthClass == WindowWidthClass.Expanded -> 320.dp
        windowSizeInfo.widthClass == WindowWidthClass.Medium -> 280.dp
        else -> 260.dp
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .then(
                        if (windowSizeInfo.isTablet || windowSizeInfo.widthClass != WindowWidthClass.Compact) {
                            Modifier.widthIn(max = windowSizeInfo.sheetMaxWidth)
                        } else {
                            Modifier
                        },
                    )
                    .fillMaxWidth()
                    .padding(horizontal = if (windowSizeInfo.isTablet) 24.dp else 20.dp),
            ) {
                Text(
                    text = if (multiSelect) "Add PDFs" else "Choose a PDF",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (showDeviceOption) {
                    Surface(
                        onClick = onPickDevice,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Filled.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column {
                                Text("Files on this device", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Pick a PDF from storage",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Choose by first-page preview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            libraryLayout = if (libraryLayout == PdfLibraryLayout.Grid) {
                                PdfLibraryLayout.List
                            } else {
                                PdfLibraryLayout.Grid
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (libraryLayout == PdfLibraryLayout.Grid) {
                                Icons.AutoMirrored.Filled.ViewList
                            } else {
                                Icons.Filled.GridView
                            },
                            contentDescription = if (libraryLayout == PdfLibraryLayout.Grid) {
                                "Show library as a list"
                            } else {
                                "Show library as a grid"
                            },
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (documents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(libraryHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No documents in your library yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val onDocumentClick: (ScanDocument) -> Unit = { doc ->
                        val selected = doc.id in selectedIds
                        if (multiSelect) {
                            selectedIds = if (selected) selectedIds - doc.id else selectedIds + doc.id
                        } else {
                            onConfirmLibrary(listOf(PdfToolSource.LibraryDocument(doc.id, doc.title)))
                        }
                    }
                    when (libraryLayout) {
                        PdfLibraryLayout.List -> PdfLibraryList(
                            documents = documents,
                            selectedIds = selectedIds,
                            onDocumentClick = onDocumentClick,
                            height = libraryHeight,
                        )
                        PdfLibraryLayout.Grid -> PdfLibraryGrid(
                            documents = documents,
                            selectedIds = selectedIds,
                            onDocumentClick = onDocumentClick,
                            height = libraryHeight,
                        )
                    }
                }

                // Sticky actions — always visible under the library (critical in landscape).
                Spacer(modifier = Modifier.height(12.dp))
                if (multiSelect && documents.isNotEmpty()) {
                    Button(
                        onClick = {
                            val chosen = documents
                                .filter { it.id in selectedIds }
                                .map { PdfToolSource.LibraryDocument(it.id, it.title) }
                            if (chosen.isNotEmpty()) onConfirmLibrary(chosen)
                        },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally),
                    ) {
                        Text("Add selected (${selectedIds.size})")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
internal fun PdfLibraryList(
    documents: List<ScanDocument>,
    selectedIds: Set<String>,
    onDocumentClick: (ScanDocument) -> Unit,
    height: androidx.compose.ui.unit.Dp,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(documents, key = { _, doc -> doc.id }) { _, doc ->
                PdfLibraryListItem(
                    document = doc,
                    selected = doc.id in selectedIds,
                    onClick = { onDocumentClick(doc) },
                )
            }
        }
        PdfLibraryScrollIndicator(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
internal fun PdfLibraryScrollIndicator(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty() || visibleItems.size >= layoutInfo.totalItemsCount) return

    val averageItemHeight = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
    val scrollableItemCount = (layoutInfo.totalItemsCount - visibleItems.size).coerceAtLeast(1)
    val scrollProgress = (
        listState.firstVisibleItemIndex +
            (listState.firstVisibleItemScrollOffset / averageItemHeight)
        ) / scrollableItemCount
    val indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f)
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)

    Canvas(
        modifier = Modifier
            .then(modifier)
            .fillMaxHeight()
            .width(12.dp)
            .padding(vertical = 8.dp, horizontal = 3.dp),
    ) {
        val thumbHeight = (size.height * (visibleItems.size.toFloat() / layoutInfo.totalItemsCount))
            .coerceIn(26.dp.toPx(), size.height)
        val top = (size.height - thumbHeight) * scrollProgress.coerceIn(0f, 1f)
        val trackWidth = 2.dp.toPx()
        val thumbWidth = 4.dp.toPx()
        val trackX = (size.width - trackWidth) / 2f
        val thumbX = (size.width - thumbWidth) / 2f
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(x = trackX, y = 0f),
            size = Size(width = trackWidth, height = size.height),
            cornerRadius = CornerRadius(trackWidth, trackWidth),
        )
        drawRoundRect(
            color = indicatorColor.copy(alpha = 0.92f),
            topLeft = Offset(x = thumbX, y = top),
            size = Size(width = thumbWidth, height = thumbHeight),
            cornerRadius = CornerRadius(thumbWidth, thumbWidth),
        )
    }
}

@Composable
internal fun PdfLibraryListItem(
    document: ScanDocument,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PdfLibraryPreview(
                document = document,
                modifier = Modifier.size(width = 54.dp, height = 68.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${document.pageCount} pages · PDF preview",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun PdfLibraryGrid(
    documents: List<ScanDocument>,
    selectedIds: Set<String>,
    onDocumentClick: (ScanDocument) -> Unit,
    height: androidx.compose.ui.unit.Dp,
) {
    val windowSizeInfo = rememberWindowSizeInfo()
    val columns = windowSizeInfo.pdfLibraryGridColumns
    val previewHeight = when {
        height < 160.dp -> 72.dp
        height < 220.dp -> 88.dp
        windowSizeInfo.widthClass == WindowWidthClass.Expanded -> 112.dp
        else -> 98.dp
    }
    val gridState = rememberLazyGridState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(documents, key = { it.id }) { document ->
                val selected = document.id in selectedIds
                Surface(
                    onClick = { onDocumentClick(document) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Box {
                            PdfLibraryPreview(
                                document = document,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(previewHeight),
                            )
                            if (selected) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = document.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${document.pageCount} pages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        PdfLibraryGridScrollIndicator(
            gridState = gridState,
            columns = columns,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
internal fun PdfLibraryGridScrollIndicator(
    gridState: LazyGridState,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val layoutInfo = gridState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val visibleRows = visibleItems.map { it.row }.distinct()
    val totalRows = (layoutInfo.totalItemsCount + columns - 1) / columns
    if (visibleRows.isEmpty() || visibleRows.size >= totalRows) return

    val averageItemHeight = visibleItems.sumOf { it.size.height }.toFloat() / visibleItems.size
    val scrollableRows = (totalRows - visibleRows.size).coerceAtLeast(1)
    val scrollProgress = (
        visibleRows.min() +
            (gridState.firstVisibleItemScrollOffset / averageItemHeight)
        ) / scrollableRows
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
    val indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)

    Canvas(
        modifier = Modifier
            .then(modifier)
            .fillMaxHeight()
            .width(12.dp)
            .padding(vertical = 8.dp, horizontal = 3.dp),
    ) {
        val thumbHeight = (size.height * (visibleRows.size.toFloat() / totalRows))
            .coerceIn(26.dp.toPx(), size.height)
        val top = (size.height - thumbHeight) * scrollProgress.coerceIn(0f, 1f)
        val trackWidth = 2.dp.toPx()
        val thumbWidth = 4.dp.toPx()
        val trackX = (size.width - trackWidth) / 2f
        val thumbX = (size.width - thumbWidth) / 2f
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(x = trackX, y = 0f),
            size = Size(width = trackWidth, height = size.height),
            cornerRadius = CornerRadius(trackWidth, trackWidth),
        )
        drawRoundRect(
            color = indicatorColor,
            topLeft = Offset(x = thumbX, y = top),
            size = Size(width = thumbWidth, height = thumbHeight),
            cornerRadius = CornerRadius(thumbWidth, thumbWidth),
        )
    }
}

@Composable
internal fun PdfLibraryPreview(
    document: ScanDocument,
    modifier: Modifier = Modifier,
) {
    CachedThumbnail(
        thumbnailPath = document.coverThumbnailPath,
        title = document.title,
        displaySize = PreviewDisplaySize.CARD,
        contentRevision = document.updatedAtMillis,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        placeholderIcon = {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    )
}
