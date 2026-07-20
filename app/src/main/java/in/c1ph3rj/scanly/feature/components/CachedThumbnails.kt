package `in`.c1ph3rj.scanly.feature.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import `in`.c1ph3rj.scanly.core.ui.PreviewDisplaySize
import `in`.c1ph3rj.scanly.core.ui.PreviewImageSizer
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.previewImagePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─── Thumbnails ────────────────────────────────────────────────────────────────

@Composable
fun CachedThumbnail(
    thumbnailPath: String?,
    title: String,
    displaySize: PreviewDisplaySize = PreviewDisplaySize.CARD,
    contentRevision: Long = 0L,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIcon: (@Composable () -> Unit)?,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val cache = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            `in`.c1ph3rj.scanly.core.ui.ThumbnailCacheEntryPoint::class.java,
        ).thumbnailCache()
    }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val targetPx = remember(containerSize, displaySize, density) {
        PreviewImageSizer.targetPxForContainer(
            widthPx = containerSize.width,
            heightPx = containerSize.height,
            size = displaySize,
            density = density,
        )
    }
    val cachedImage = remember(thumbnailPath, targetPx, contentRevision) {
        thumbnailPath?.let { path ->
            cache.getIfCached(path, targetPx, contentRevision)?.asImageBitmap()
        }
    }

    val imageBitmap by produceState<ImageBitmap?>(
        initialValue = cachedImage,
        key1 = thumbnailPath,
        key2 = targetPx,
        key3 = contentRevision,
    ) {
        val path = thumbnailPath
        if (path == null) {
            value = null
            return@produceState
        }
        cache.getIfCached(path, targetPx, contentRevision)?.let { bitmap ->
            value = bitmap.asImageBitmap()
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            cache.decode(path, targetPx, contentRevision)?.asImageBitmap()
        }
    }

    Surface(
        modifier = modifier.onSizeChanged { size ->
            if (size != IntSize.Zero) {
                containerSize = size
            }
        },
        color = if (imageBitmap != null) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        shape = shape,
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                filterQuality = FilterQuality.High,
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (placeholderIcon != null) {
                    placeholderIcon()
                } else {
                    Text(
                        text = DocumentPresentationFormatter.initials(title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentThumbnail(
    thumbnailPath: String?,
    title: String,
    contentRevision: Long = 0L,
    displaySize: PreviewDisplaySize = PreviewDisplaySize.CARD,
    modifier: Modifier = Modifier,
    minHeight: Dp = 90.dp,
    aspectRatio: Float? = 3f / 4f,
    contentScale: ContentScale = if (displaySize == PreviewDisplaySize.DETAIL) {
        ContentScale.Fit
    } else {
        ContentScale.Crop
    },
) {
    CachedThumbnail(
        thumbnailPath = thumbnailPath,
        title = title,
        displaySize = displaySize,
        contentRevision = contentRevision,
        contentScale = contentScale,
        modifier = modifier
            .heightIn(min = minHeight)
            .let { if (aspectRatio != null) it.aspectRatio(aspectRatio) else it },
        placeholderIcon = {
            Text(
                text = DocumentPresentationFormatter.initials(title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    )
}

@Composable
fun PagePreview(
    page: ScanPage,
    displaySize: PreviewDisplaySize,
    modifier: Modifier = Modifier,
    minHeight: Dp = when (displaySize) {
        PreviewDisplaySize.COMPACT -> 56.dp
        PreviewDisplaySize.CARD -> 90.dp
        PreviewDisplaySize.DETAIL -> 120.dp
    },
    aspectRatio: Float? = if (displaySize == PreviewDisplaySize.DETAIL) null else 3f / 4f,
) {
    DocumentThumbnail(
        thumbnailPath = page.previewImagePath(displaySize),
        title = "Page ${page.pageIndex + 1}",
        contentRevision = page.updatedAtMillis,
        displaySize = displaySize,
        modifier = modifier,
        minHeight = minHeight,
        aspectRatio = aspectRatio,
    )
}
