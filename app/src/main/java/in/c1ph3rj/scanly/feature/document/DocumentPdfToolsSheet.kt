package `in`.c1ph3rj.scanly.feature.document

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.feature.components.ScanlyModalBottomSheet
import `in`.c1ph3rj.scanly.feature.components.ScanlySheetContent
import `in`.c1ph3rj.scanly.feature.tools.ToolCard
import `in`.c1ph3rj.scanly.feature.tools.pdf.PdfReaderTool

private val ToolListMaxHeight = 480.dp

/**
 * The PDF tools for a library document, gathered in one sheet instead of the ⋮ menu. Each tool
 * works on an exported copy of the document, so the scan itself is never changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentPdfToolsSheet(
    documentTitle: String,
    tools: List<PdfReaderTool>,
    onDismiss: () -> Unit,
    onOpenTool: (PdfReaderTool) -> Unit,
) {
    ScanlyModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("document_pdf_tools_sheet"),
        containerColor = { MaterialTheme.colorScheme.surface },
    ) {
        ScanlySheetContent {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.document_menu_pdf_tools),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.document_pdf_tools_sheet_subtitle, documentTitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LazyColumn(
                modifier = Modifier.heightIn(max = ToolListMaxHeight),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tools, key = { it.name }) { tool ->
                    PdfToolRow(tool = tool, onClick = { onOpenTool(tool) })
                }
            }
        }
    }
}

@Composable
private fun PdfToolRow(
    tool: PdfReaderTool,
    onClick: () -> Unit,
) {
    ToolCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("document_pdf_tool_${tool.name.lowercase()}"),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(tool.documentTitleRes()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                tool.documentCaptionRes()?.let { captionRes ->
                    Text(
                        text = stringResource(captionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

/** "Split" alone reads like Split into documents here, so the sheet says "Split into PDFs". */
private fun PdfReaderTool.documentTitleRes(): Int =
    if (this == PdfReaderTool.SPLIT) R.string.document_menu_split_into_pdfs else labelRes

/** One-line captions, shared with the Tools tab where they fit a document. */
private fun PdfReaderTool.documentCaptionRes(): Int? = when (this) {
    PdfReaderTool.COMPRESS -> R.string.tools_catalog_make_it_smaller
    PdfReaderTool.MERGE -> R.string.tools_catalog_combine_files
    PdfReaderTool.SPLIT -> R.string.tools_catalog_cut_into_parts
    PdfReaderTool.PASSWORD -> R.string.document_pdf_tools_password_caption
    PdfReaderTool.WATERMARK -> R.string.tools_catalog_stamp_text
    PdfReaderTool.TO_IMAGES -> R.string.document_pdf_tools_images_caption
    PdfReaderTool.SIGN, PdfReaderTool.IMPORT -> null
}
