package `in`.c1ph3rj.scanly.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import `in`.c1ph3rj.scanly.navigation.ToolsPdfCompressDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfMergeDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfPasswordDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfReaderDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfWatermarkDestination
import `in`.c1ph3rj.scanly.navigation.ToolsQrDestination

/** Identifies what a hub tile does. Hub-local actions have no [ToolItem.route]. */
enum class ToolActionId {
    Scan,
    Import,
    Qr,
    PdfMerge,
    PdfCompress,
    PdfPassword,
    PdfWatermark,
    PdfReader,
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
val scanTools: List<ToolItem> = listOf(
    ToolItem(
        id = ToolActionId.Scan,
        title = "Scan a document",
        caption = "Camera with edge detection",
        icon = Icons.Outlined.DocumentScanner,
        accent = true,
    ),
)

/** "PDF": gallery import plus PDF tools. */
val pdfTools: List<ToolItem> = listOf(
    ToolItem(
        id = ToolActionId.Import,
        title = "Images to PDF",
        caption = "From gallery",
        icon = Icons.Outlined.PhotoLibrary,
    ),
    ToolItem(
        id = ToolActionId.PdfMerge,
        title = "Merge",
        caption = "Combine files",
        icon = Icons.AutoMirrored.Outlined.MergeType,
        route = ToolsPdfMergeDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfCompress,
        title = "Compress",
        caption = "Make it smaller",
        icon = Icons.Outlined.Compress,
        route = ToolsPdfCompressDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfPassword,
        title = "Password",
        caption = "Lock or unlock",
        icon = Icons.Outlined.Lock,
        route = ToolsPdfPasswordDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfWatermark,
        title = "Watermark",
        caption = "Stamp text",
        icon = Icons.Outlined.WaterDrop,
        route = ToolsPdfWatermarkDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfReader,
        title = "Read PDF",
        caption = "Open any PDF",
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        route = ToolsPdfReaderDestination.route,
    ),
)

/** "Utilities": the QR tile opens straight into scanning, where Create is a tab. */
val utilityTools: List<ToolItem> = listOf(
    ToolItem(
        id = ToolActionId.Qr,
        title = "QR code",
        caption = "Scan or create",
        icon = Icons.Outlined.QrCode2,
        route = ToolsQrDestination.route,
    ),
)
