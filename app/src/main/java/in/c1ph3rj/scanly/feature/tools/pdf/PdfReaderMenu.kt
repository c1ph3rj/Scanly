package `in`.c1ph3rj.scanly.feature.tools.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import `in`.c1ph3rj.scanly.core.common.appString

/** PDF tools the reader can hand its open file to. Back from the tool returns to the reader. */
enum class PdfReaderTool(@param:StringRes val labelRes: Int, val icon: ImageVector) {
    SIGN(R.string.page_image_preview_sign, Icons.Outlined.Draw),
    COMPRESS(R.string.pdf_compress_compress, Icons.Outlined.Compress),
    MERGE(R.string.pdf_reader_tool_merge_with_other_pdfs, Icons.AutoMirrored.Outlined.MergeType),
    PASSWORD(R.string.pdf_reader_tool_password_protect, Icons.Outlined.Lock),
    WATERMARK(R.string.pdf_reader_tool_add_watermark, Icons.Outlined.WaterDrop),
    TO_IMAGES(R.string.pdf_reader_tool_save_pages_as_images, Icons.Outlined.Image),
    IMPORT(R.string.pdf_reader_tool_add_to_scanly_library, Icons.Outlined.LibraryAdd),
    ;

    val label: String get() = appString(labelRes)
}

/** The reader's ⋮ menu: share, jump to pages, the PDF tools, and opening another file. */
@Composable
internal fun PdfReaderOverflowMenu(
    canShare: Boolean,
    onShare: () -> Unit,
    onOpenPages: () -> Unit,
    onOpenTool: (PdfReaderTool) -> Unit,
    onOpenAnother: () -> Unit,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.shared_more_options), tint = iconTint)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
        ) {
            fun pick(action: () -> Unit): () -> Unit = {
                expanded = false
                action()
            }
            if (canShare) {
                MenuRow(stringResource(R.string.document_detail_share_2), Icons.Outlined.Share, pick(onShare))
            }
            MenuRow(stringResource(R.string.pdf_reader_menu_all_pages), Icons.Outlined.GridView, pick(onOpenPages))
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            PdfReaderTool.entries.forEach { tool ->
                MenuRow(tool.label, tool.icon, pick { onOpenTool(tool) })
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MenuRow(stringResource(R.string.pdf_reader_menu_open_another_pdf), Icons.Outlined.FileOpen, pick(onOpenAnother))
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

