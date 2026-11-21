package `in`.c1ph3rj.scanly.feature.launch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.feature.components.ScanlyModalBottomSheet
import `in`.c1ph3rj.scanly.feature.components.ScanlySheetContent
import `in`.c1ph3rj.scanly.feature.sign.create.ScannedDocumentRow
import `in`.c1ph3rj.scanly.feature.tools.ToolCard

private val DocumentListMaxHeight = 360.dp

/**
 * Where images shared from another app go: a new document, or more pages for an existing one.
 * Either way they run through the same import as the in-app picker.
 *
 * @param imageCount Images that will be imported (already capped and copied).
 * @param documents Live documents, most recently changed first; the list is hidden when empty.
 */
@Composable
fun SharedImagesSheet(
    imageCount: Int,
    documents: List<ScanDocument>,
    onNewDocument: () -> Unit,
    onAddToDocument: (ScanDocument) -> Unit,
    onDismiss: () -> Unit,
) {
    ScanlyModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("shared_images_sheet"),
        containerColor = { MaterialTheme.colorScheme.surface },
    ) {
        ScanlySheetContent {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.share_images_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = pluralStringResource(R.plurals.common_pages, imageCount, imageCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            NewDocumentRow(onClick = onNewDocument)

            if (documents.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.share_images_add_pages_to),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = DocumentListMaxHeight)
                        .testTag("shared_images_documents"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(documents, key = { it.id }) { document ->
                        ScannedDocumentRow(
                            document = document,
                            onClick = { onAddToDocument(document) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewDocumentRow(onClick: () -> Unit) {
    ToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shared_images_new_document"),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Same footprint as a document cover in the rows below, so the titles line up.
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 58.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PostAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.group_detail_new_document),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.share_images_new_document_description),
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
