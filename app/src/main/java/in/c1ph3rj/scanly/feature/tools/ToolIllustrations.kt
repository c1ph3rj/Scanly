package `in`.c1ph3rj.scanly.feature.tools

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import android.content.res.Resources
import android.graphics.ImageDecoder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.c1ph3rj.scanly.R

/**
 * Untinted tactile artwork inside a rounded outline. Transparent assets retain their material
 * colors in light, dark and pure-black themes; the parent card supplies the only background.
 *
 * The 4:3 art board and inset height leave air around each illustration, including the wider
 * document diagrams for Merge and Split. Decorative images have no separate semantics: the
 * card's localized title and caption already describe the action.
 */
@Composable
fun ToolArtStage(
    id: ToolActionId,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    artHeightFraction: Float = 0.78f,
) {
    Box(
        modifier = modifier.border(
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(cornerRadius),
        ),
        contentAlignment = Alignment.Center,
    ) {
        val resources = LocalResources.current
        val artwork = id.artwork()
        val bitmap by produceState(initialValue = ToolArtCache.get(artwork), artwork) {
            if (value == null) value = ToolArtCache.load(resources, artwork)
        }
        val artModifier = Modifier
            .fillMaxHeight(artHeightFraction)
            .aspectRatio(4f / 3f)
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = null,
                modifier = artModifier,
                contentScale = ContentScale.Fit,
            )
        } else {
            // Same footprint while the picture decodes, so the card does not resize.
            Box(artModifier)
        }
    }
}

/**
 * Decoded tool pictures, shared by every screen that shows them.
 *
 * `painterResource` decodes on the main thread during composition; with a picture on every card
 * that made the Tools tab's first frame slow. Here the first use decodes in the background and
 * later uses are a map lookup. The app drops the set when it leaves the screen
 * ([ToolArtCache.clear]), so the bitmaps are only held while they can be seen.
 */
object ToolArtCache {
    private val bitmaps = ConcurrentHashMap<Int, ImageBitmap>()

    fun get(@DrawableRes id: Int): ImageBitmap? = bitmaps[id]

    suspend fun load(resources: Resources, @DrawableRes id: Int): ImageBitmap? =
        bitmaps[id] ?: withContext(Dispatchers.Default) {
            runCatching {
                // Hardware bitmap: decoded straight into GPU memory, no upload on first draw.
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resources, id)).asImageBitmap()
            }.getOrNull()?.also { bitmaps[id] = it }
        }

    fun clear() = bitmaps.clear()
}

@DrawableRes
private fun ToolActionId.artwork(): Int = when (this) {
    ToolActionId.Scan -> R.drawable.tools_art_scan
    ToolActionId.Import -> R.drawable.tools_art_images_to_pdf
    ToolActionId.PdfSign -> R.drawable.tools_art_sign
    ToolActionId.PdfMerge -> R.drawable.tools_art_merge
    ToolActionId.PdfSplit -> R.drawable.tools_art_split
    ToolActionId.PdfCompress -> R.drawable.tools_art_compress
    ToolActionId.PdfToImages -> R.drawable.tools_art_pdf_to_jpg
    ToolActionId.PdfPassword -> R.drawable.tools_art_password
    ToolActionId.PdfWatermark -> R.drawable.tools_art_watermark
    ToolActionId.PdfReader -> R.drawable.tools_art_read_pdf
    ToolActionId.PdfImport -> R.drawable.tools_art_add_to_library
    ToolActionId.Qr -> R.drawable.tools_art_qr
    ToolActionId.ImageText -> R.drawable.tools_art_image_to_text
    ToolActionId.Signatures -> R.drawable.tools_art_signatures
}
