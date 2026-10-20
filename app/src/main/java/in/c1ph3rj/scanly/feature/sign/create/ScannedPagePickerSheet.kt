package `in`.c1ph3rj.scanly.feature.sign.create

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.previewImagePath
import `in`.c1ph3rj.scanly.feature.components.CachedThumbnail
import `in`.c1ph3rj.scanly.feature.components.ScanlyModalBottomSheet
import `in`.c1ph3rj.scanly.feature.components.ScanlySheetContent
import `in`.c1ph3rj.scanly.feature.tools.ToolCard
import `in`.c1ph3rj.scanly.feature.tools.ToolNote

private val ListMaxHeight = 420.dp
private val GridMaxHeight = 460.dp
private val PageTileShape = RoundedCornerShape(10.dp)
private val DocumentThumbShape = RoundedCornerShape(6.dp)

/**
 * Bottom sheet for picking a page already scanned in Scanly. It first lists documents; tapping
 * one shows its pages as a three-column grid, and tapping a page reports it through [onPageClick].
 *
 * @param documents Documents with at least one page, in the order the repository gives them.
 * @param selectedDocumentId Document whose pages are shown, or null to show the document list.
 * @param pages Pages of the selected document that have an image.
 * @param onSelectDocument Called with a document id to open it, or null to go back to the list.
 * @param onPageClick Called with the page the user picked.
 * @param onDismiss Called when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannedPagePickerSheet(
    documents: List<ScanDocument>,
    selectedDocumentId: String?,
    pages: List<ScanPage>,
    onSelectDocument: (String?) -> Unit,
    onPageClick: (ScanPage) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedDocument = documents.firstOrNull { it.id == selectedDocumentId }
    ScanlyModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = { MaterialTheme.colorScheme.surface },
    ) {
        ScanlySheetContent {
            if (selectedDocument == null) {
                Text(
                    text = "Scanned pages",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (documents.isEmpty()) {
                    ToolNote(text = "No scanned documents yet. Scan a page in Scanly and it will show up here.")
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = ListMaxHeight),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(documents, key = { it.id }) { document ->
                            ScannedDocumentRow(
                                document = document,
                                onClick = { onSelectDocument(document.id) },
                            )
                        }
                    }
                }
            } else {
                ScannedPageGrid(
                    document = selectedDocument,
                    pages = pages,
                    onBack = { onSelectDocument(null) },
                    onPageClick = onPageClick,
                )
            }
        }
    }
}

/** One scanned document: cover thumbnail, title, page count and a chevron. */
@Composable
private fun ScannedDocumentRow(
    document: ScanDocument,
    onClick: () -> Unit,
) {
    val pageLabel = if (document.pageCount == 1) "1 page" else "${document.pageCount} pages"
    ToolCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CachedThumbnail(
                thumbnailPath = document.coverThumbnailPath,
                title = document.title,
                displaySize = PreviewDisplaySize.COMPACT,
                contentRevision = document.updatedAtMillis,
                shape = DocumentThumbShape,
                contentScale = ContentScale.Fit,
                containerColor = Color.White,
                modifier = Modifier.size(width = 44.dp, height = 58.dp),
                placeholderIcon = {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pageLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The pages of one document as a three-column grid, with a back button to the document list. */
@Composable
private fun ScannedPageGrid(
    document: ScanDocument,
    pages: List<ScanPage>,
    onBack: () -> Unit,
    onPageClick: (ScanPage) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to documents",
            )
        }
        Text(
            text = document.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
    if (pages.isEmpty()) {
        ToolNote(text = "No pages in this document can be used.")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.heightIn(max = GridMaxHeight),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(pages, key = { it.id }) { page ->
                ScannedPageTile(page = page, onClick = { onPageClick(page) })
            }
        }
    }
}

/** A page thumbnail on a white sheet with its page number underneath. */
@Composable
private fun ScannedPageTile(
    page: ScanPage,
    onClick: () -> Unit,
) {
    val title = "Page ${page.pageIndex + 1}"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(PageTileShape)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PageTileShape),
        ) {
            CachedThumbnail(
                thumbnailPath = page.previewImagePath(PreviewDisplaySize.CARD),
                title = title,
                displaySize = PreviewDisplaySize.CARD,
                contentRevision = page.updatedAtMillis,
                shape = PageTileShape,
                contentScale = ContentScale.Fit,
                containerColor = Color.White,
                modifier = Modifier.fillMaxSize(),
                placeholderIcon = null,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
