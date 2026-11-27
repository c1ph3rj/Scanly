package `in`.c1ph3rj.scanly.feature.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.feature.components.ShimmerBlock
import `in`.c1ph3rj.scanly.feature.components.rememberShimmerProgress

@Composable
internal fun EditorPreviewContent(preview: EditorPreview, description: String) {
    when (preview) {
        EditorPreview.Loading -> {
            val loadingLabel = stringResource(R.string.filter_customize_loading_preview)
            ShimmerBlock(
                progress = rememberShimmerProgress(),
                shape = RectangleShape,
                modifier = Modifier.fillMaxSize().semantics { contentDescription = loadingLabel },
            )
        }

        EditorPreview.Unavailable -> Text(stringResource(R.string.zoomable_image_image_unavailable), color = Color.White)

        is EditorPreview.Ready -> {
            val opacity = remember { Animatable(0f) }
            LaunchedEffect(opacity) { opacity.animateTo(1f, tween(durationMillis = 140)) }
            Image(
                bitmap = preview.image,
                contentDescription = description,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = opacity.value },
                contentScale = ContentScale.Fit,
            )
        }
    }
}
