package `in`.c1ph3rj.scanly.feature.sign.manage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import `in`.c1ph3rj.scanly.domain.model.SignInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Longest edge, in pixels, that a saved signature is decoded to for display. */
private const val SignatureDisplayEdgePx = 600

/**
 * Shows a saved signature or initials PNG tinted with [ink].
 *
 * The file is an ink mask (black with alpha), so the same image renders in any [SignInk]. It is
 * decoded off the main thread, downsampled so its longest edge is about 600px, and nothing is
 * drawn until decoding finishes. Reload happens only when [imagePath] changes.
 *
 * @param imagePath Absolute path to the saved PNG ink mask.
 * @param ink Colour the mask is tinted with.
 * @param modifier Applied to the image container.
 * @param contentDescription Accessibility label for the signature image, or null when decorative.
 */
@Composable
fun SavedSignatureImage(
    imagePath: String,
    ink: SignInk,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = imagePath) {
        value = withContext(Dispatchers.IO) { decodeSavedSignature(imagePath)?.asImageBitmap() }
    }

    Box(modifier = modifier) {
        bitmap?.let { loaded ->
            Image(
                bitmap = loaded,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Color(ink.argb)),
            )
        }
    }
}

/**
 * Decodes the ink mask at [imagePath] keeping its alpha channel, downsampled so the longest edge
 * is at most about [SignatureDisplayEdgePx]. Returns null if the file cannot be read.
 */
private fun decodeSavedSignature(imagePath: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(imagePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    var width = bounds.outWidth
    var height = bounds.outHeight
    while (width / 2 >= SignatureDisplayEdgePx || height / 2 >= SignatureDisplayEdgePx) {
        width /= 2
        height /= 2
        sampleSize *= 2
    }

    return BitmapFactory.decodeFile(
        imagePath,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    )
}
