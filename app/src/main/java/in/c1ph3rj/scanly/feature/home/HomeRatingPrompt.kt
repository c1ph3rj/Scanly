package `in`.c1ph3rj.scanly.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
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
import `in`.c1ph3rj.scanly.feature.components.ScanlyModalBottomSheet
import android.widget.Toast
import `in`.c1ph3rj.scanly.feature.rating.launchRating
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import `in`.c1ph3rj.scanly.R
import androidx.compose.ui.res.stringResource
import `in`.c1ph3rj.scanly.core.common.appString

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
                    if (launchRating(context)) {
                        viewModel.onRatingPromptRated()
                    } else {
                        Toast.makeText(context, appString(R.string.rating_unavailable), Toast.LENGTH_LONG).show()
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
            text = stringResource(R.string.home_rating_prompt_rate_scanly),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.home_rating_prompt_scanly_is_free_private_and),
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
            Text(stringResource(R.string.home_rating_prompt_rate_on_google_play))
        }
        TextButton(onClick = onNotNow) {
            Text(stringResource(R.string.pip_camera_not_now))
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
        // Min height sits inside the clickable so the whole 48dp row is the target
        // (padding applied after clickable was not tappable).
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onRate)
            .semantics(mergeDescendants = true) {
                contentDescription = appString(R.string.home_rating_prompt_rate_scanly_opens_google_play)
            },
        verticalAlignment = Alignment.CenterVertically,
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
