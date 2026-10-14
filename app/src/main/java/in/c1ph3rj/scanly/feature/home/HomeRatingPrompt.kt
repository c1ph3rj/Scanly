package `in`.c1ph3rj.scanly.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.c1ph3rj.scanly.feature.camera.CameraPermissionSupport
import `in`.c1ph3rj.scanly.feature.components.ScanlyModalBottomSheet
import `in`.c1ph3rj.scanly.feature.rating.currentRatingChannel
import `in`.c1ph3rj.scanly.feature.rating.launchRating
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RATING_PROMPT_DELAY_MILLIS = 1_500L
private const val RATING_STAR_COUNT = 5
private const val RATING_STAR_STAGGER_MILLIS = 40L
private const val RATING_STAR_START_SCALE = 0.6f

/**
 * Occasional, gentle rating nudge on Home. It only appears after the user has stayed on Home for a
 * moment, and every rating action (stars or the primary button) leads to the same store flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRatingPromptHost(viewModel: HomeViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val promptEligible by viewModel.showRatingPrompt.collectAsStateWithLifecycle()
    var sheetVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(promptEligible) {
        if (promptEligible) {
            delay(RATING_PROMPT_DELAY_MILLIS)
            viewModel.onRatingPromptPresented()
            sheetVisible = true
        }
    }

    if (sheetVisible) {
        val activity = remember(context) { CameraPermissionSupport.findActivity(context) }
        val channel = remember { currentRatingChannel() }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ScanlyModalBottomSheet(
            onDismissRequest = {
                sheetVisible = false
                viewModel.onRatingPromptDismissed()
            },
            sheetState = sheetState,
        ) {
            RatingPromptContent(
                onRate = {
                    sheetVisible = false
                    viewModel.onRatingPromptRated()
                    if (activity != null) {
                        scope.launch { launchRating(activity, channel) }
                    }
                },
                onNotNow = {
                    sheetVisible = false
                    viewModel.onRatingPromptDismissed()
                },
            )
        }
    }
}

@Composable
private fun RatingPromptContent(
    onRate: () -> Unit,
    onNotNow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedRatingStars(onRate = onRate)
        Text(
            text = "Rate Scanly",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = "Scanly is free, private and ad-free. If it saves you time, a quick rating on " +
                "Google Play really helps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Rate on Google Play")
        }
        TextButton(onClick = onNotNow) {
            Text("Not now")
        }
    }
}

@Composable
private fun AnimatedRatingStars(onRate: () -> Unit) {
    val scales = remember { List(RATING_STAR_COUNT) { Animatable(RATING_STAR_START_SCALE) } }

    LaunchedEffect(Unit) {
        coroutineScope {
            scales.forEachIndexed { index, scale ->
                launch {
                    delay(index * RATING_STAR_STAGGER_MILLIS)
                    scale.animateTo(1f, animationSpec = tween(durationMillis = 260))
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onRate)
            .semantics(mergeDescendants = true) {
                contentDescription = "Rate Scanly, opens Google Play"
            }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        scales.forEach { scale ->
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    },
            )
        }
    }
}
