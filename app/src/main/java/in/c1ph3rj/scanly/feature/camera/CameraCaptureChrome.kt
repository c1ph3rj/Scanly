package `in`.c1ph3rj.scanly.feature.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Bright brand teal: reads on the black camera chrome in both app themes. */
internal val CameraAccent = Color(0xFF2DD4BF)
private val CameraAccentInk = Color(0xFF00302A)
private val CameraAccentContainer = Color(0xFF00534A)

/** The live preview's rounded frame. Its size and 3:4 ratio are unchanged. */
internal val CameraPreviewShape = RoundedCornerShape(24.dp)

/** Auto-capture as a labelled pill: easier to read at a glance than an icon. */
@Composable
internal fun AutoCapturePill(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = if (enabled) "Turn auto-capture off" else "Turn auto-capture on"
            },
        shape = CircleShape,
        color = Color.Transparent,
    ) {
        // 48dp touch target around a 34dp visual pill.
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 2.dp)) {
            Surface(
                shape = CircleShape,
                color = if (enabled) CameraAccentContainer else Color.Transparent,
                border = if (enabled) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
            ) {
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "AUTO",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) CameraAccent else Color.White.copy(alpha = 0.78f),
                    )
                }
            }
        }
    }
}

/**
 * Finishes the session and opens the document. It appears once a page is captured: a teal pill
 * with a tick and the page count in portrait, a round tick in the landscape rail.
 */
@Composable
internal fun CaptureDoneButton(
    pageCount: Int,
    visible: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.8f),
        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.8f),
    ) {
        val label = if (pageCount == 1) "Done, review 1 page" else "Done, review $pageCount pages"
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label },
            shape = CircleShape,
            color = if (enabled) CameraAccent else CameraAccent.copy(alpha = 0.5f),
            contentColor = CameraAccentInk,
            shadowElevation = 0.dp,
        ) {
            if (compact) {
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(26.dp))
                }
            } else {
                Row(
                    modifier = Modifier
                        .height(52.dp)
                        .padding(start = 14.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
