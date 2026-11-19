package `in`.c1ph3rj.scanly.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import `in`.c1ph3rj.scanly.navigation.SignatureManageDestination
import `in`.c1ph3rj.scanly.navigation.ToolsImageTextDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfCompressDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfImportDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfMergeDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfPasswordDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfReaderDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfSignDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfSplitDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfToImagesDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfWatermarkDestination
import `in`.c1ph3rj.scanly.navigation.ToolsQrDestination
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.R

/** Identifies what a hub tile does. Hub-local actions have no [ToolItem.route]. */
enum class ToolActionId {
    Scan,
    Import,
    Qr,
    ImageText,
    Signatures,
    PdfMerge,
    PdfSplit,
    PdfCompress,
    PdfToImages,
    PdfPassword,
    PdfWatermark,
    PdfReader,
    PdfSign,
    PdfImport,
}

/**
 * One tile on the Tools hub.
 *
 * [accent] marks the hub's main action (accent icon well). [route] is the screen a tile opens;
 * Scan and Import are handled by the hub itself.
 */
data class ToolItem(
    val id: ToolActionId,
    val title: String,
    val caption: String,
    val icon: ImageVector,
    val accent: Boolean = false,
    val route: String? = null,
)

/** "Scan": the single full-width camera action. */
val scanTools: List<ToolItem> get() = listOf(
    ToolItem(
        id = ToolActionId.Scan,
        title = appString(R.string.tools_catalog_scan_a_document),
        caption = appString(R.string.tools_catalog_camera_with_edge_detection),
        icon = Icons.Outlined.DocumentScanner,
        accent = true,
    ),
)

/**
 * "PDF", in pairs that read as one idea per row of the two-column grid: open (Add to library,
 * Read), convert (Images to PDF, PDF to JPG), combine or cut (Merge, Split), shrink and protect
 * (Compress, Password), mark up (Sign, Watermark).
 */
val pdfTools: List<ToolItem> get() = listOf(
    ToolItem(
        id = ToolActionId.PdfImport,
        title = appString(R.string.pdf_import_add_to_library),
        caption = appString(R.string.tools_catalog_pdf_to_editable_scan),
        icon = Icons.Outlined.LibraryAdd,
        route = ToolsPdfImportDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfReader,
        title = appString(R.string.tools_catalog_read_pdf),
        caption = appString(R.string.tools_catalog_open_any_pdf),
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        route = ToolsPdfReaderDestination.route,
    ),
    ToolItem(
        id = ToolActionId.Import,
        title = appString(R.string.tools_catalog_images_to_pdf),
        caption = appString(R.string.tools_catalog_from_gallery),
        icon = Icons.Outlined.PhotoLibrary,
    ),
    ToolItem(
        id = ToolActionId.PdfToImages,
        title = appString(R.string.pdf_to_images_pdf_to_jpg),
        caption = appString(R.string.tools_catalog_pages_as_images),
        icon = Icons.Outlined.Image,
        route = ToolsPdfToImagesDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfMerge,
        title = appString(R.string.tools_catalog_merge),
        caption = appString(R.string.tools_catalog_combine_files),
        icon = Icons.AutoMirrored.Outlined.MergeType,
        route = ToolsPdfMergeDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfSplit,
        title = appString(R.string.tools_catalog_split),
        caption = appString(R.string.tools_catalog_cut_into_parts),
        icon = Icons.AutoMirrored.Outlined.CallSplit,
        route = ToolsPdfSplitDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfCompress,
        title = appString(R.string.pdf_compress_compress),
        caption = appString(R.string.tools_catalog_make_it_smaller),
        icon = Icons.Outlined.Compress,
        route = ToolsPdfCompressDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfPassword,
        title = appString(R.string.pdf_reader_password),
        caption = appString(R.string.tools_catalog_lock_or_unlock),
        icon = Icons.Outlined.Lock,
        route = ToolsPdfPasswordDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfSign,
        title = appString(R.string.page_image_preview_sign),
        caption = appString(R.string.tools_catalog_sign_any_pdf),
        icon = Icons.Outlined.Draw,
        route = ToolsPdfSignDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfWatermark,
        title = appString(R.string.pdf_watermark_watermark),
        caption = appString(R.string.tools_catalog_stamp_text),
        icon = Icons.Outlined.WaterDrop,
        route = ToolsPdfWatermarkDestination.route,
    ),
)

/**
 * "Utilities": the QR tile opens straight into scanning (it also reads product barcodes), where
 * Create is a tab. Image to text and Signatures reuse the Extracted text and Your signatures screens.
 */
val utilityTools: List<ToolItem> get() = listOf(
    ToolItem(
        id = ToolActionId.Qr,
        title = appString(R.string.tools_catalog_qr_barcode),
        caption = appString(R.string.tools_catalog_scan_or_create),
        icon = Icons.Outlined.QrCode2,
        route = ToolsQrDestination.route,
    ),
    ToolItem(
        id = ToolActionId.ImageText,
        title = appString(R.string.image_text_image_to_text),
        caption = appString(R.string.tools_catalog_photos_scans),
        icon = Icons.Outlined.TextFields,
        route = ToolsImageTextDestination.route,
    ),
    ToolItem(
        id = ToolActionId.Signatures,
        title = appString(R.string.tools_catalog_my_signatures),
        caption = appString(R.string.tools_catalog_create_manage),
        icon = Icons.Outlined.Gesture,
        route = SignatureManageDestination.route,
    ),
)
