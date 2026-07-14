package `in`.c1ph3rj.scanly.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import `in`.c1ph3rj.scanly.navigation.ToolsPdfCompressDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfMergeDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfPasswordDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfReaderDestination
import `in`.c1ph3rj.scanly.navigation.ToolsPdfWatermarkDestination
import `in`.c1ph3rj.scanly.navigation.ToolsQrDestination

enum class ToolActionId {
    Scan,
    Import,
    Qr,
    PdfReader,
    PdfMerge,
    PdfCompress,
    PdfPassword,
    PdfWatermark,
}

enum class ToolAccent {
    Primary,
    Secondary,
    Tertiary,
}

data class ToolItem(
    val id: ToolActionId,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: ToolAccent,
    /** Null for hub-local actions (Scan / Import). */
    val route: String? = null,
)

val captureTools: List<ToolItem> = listOf(
    ToolItem(
        id = ToolActionId.Scan,
        title = "Scan",
        subtitle = "Capture with camera",
        icon = Icons.Filled.CameraAlt,
        accent = ToolAccent.Primary,
    ),
    ToolItem(
        id = ToolActionId.Import,
        title = "Import",
        subtitle = "Photos from gallery",
        icon = Icons.Filled.PhotoLibrary,
        accent = ToolAccent.Secondary,
    ),
)

val utilityTools: List<ToolItem> = listOf(
    ToolItem(
        id = ToolActionId.Qr,
        title = "QR Code",
        subtitle = "Scan or generate",
        icon = Icons.Filled.QrCode2,
        accent = ToolAccent.Tertiary,
        route = ToolsQrDestination.route,
    ),
)

val pdfTools: List<ToolItem> = listOf(
    ToolItem(
        id = ToolActionId.PdfReader,
        title = "Reader",
        subtitle = "View PDF pages",
        icon = Icons.AutoMirrored.Filled.MenuBook,
        accent = ToolAccent.Primary,
        route = ToolsPdfReaderDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfMerge,
        title = "Merge",
        subtitle = "Combine PDFs",
        icon = Icons.AutoMirrored.Filled.MergeType,
        accent = ToolAccent.Secondary,
        route = ToolsPdfMergeDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfCompress,
        title = "Compress",
        subtitle = "Reduce file size",
        icon = Icons.Filled.Compress,
        accent = ToolAccent.Tertiary,
        route = ToolsPdfCompressDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfPassword,
        title = "Password",
        subtitle = "Protect or unlock",
        icon = Icons.Filled.Lock,
        accent = ToolAccent.Primary,
        route = ToolsPdfPasswordDestination.route,
    ),
    ToolItem(
        id = ToolActionId.PdfWatermark,
        title = "Watermark",
        subtitle = "Stamp text on pages",
        icon = Icons.Filled.WaterDrop,
        accent = ToolAccent.Secondary,
        route = ToolsPdfWatermarkDestination.route,
    ),
)
