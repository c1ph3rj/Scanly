package `in`.c1ph3rj.scanly.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import `in`.c1ph3rj.scanly.core.ui.rememberWindowSizeInfo
import `in`.c1ph3rj.scanly.domain.model.PipPreference
import `in`.c1ph3rj.scanly.domain.model.PipPreferences
import `in`.c1ph3rj.scanly.feature.components.ScanlyDetailScaffold
import `in`.c1ph3rj.scanly.feature.pip.LocalPipPreferences
import `in`.c1ph3rj.scanly.feature.pip.Pip
import `in`.c1ph3rj.scanly.feature.pip.PipMood
import `in`.c1ph3rj.scanly.feature.pip.PipSays
import `in`.c1ph3rj.scanly.feature.pip.PipViewModel

@Composable
fun SettingsPipRoute(
    onNavigateUp: () -> Unit,
    viewModel: PipViewModel = hiltViewModel(),
) {
    SettingsPipScreen(
        preferences = LocalPipPreferences.current,
        onNavigateUp = onNavigateUp,
        onPreferenceChange = viewModel::setPreference,
    )
}

/**
 * Pip's own settings. A live Pip at the top answers every change, so the switches explain
 * themselves; the master switch sits right under it, and the finer switches step back while
 * Pip is napping.
 */
@Composable
fun SettingsPipScreen(
    preferences: PipPreferences,
    onNavigateUp: () -> Unit,
    onPreferenceChange: (PipPreference, Boolean) -> Unit,
) {
    val window = rememberWindowSizeInfo()
    ScanlyDetailScaffold(
        title = "Pip",
        onNavigateUp = onNavigateUp,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = window.horizontalPadding,
                end = window.horizontalPadding,
                top = 8.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("hero") {
                PipHeroCard(
                    preferences = preferences,
                    onEnabledChange = { onPreferenceChange(PipPreference.ENABLED, it) },
                )
            }
            item("where") {
                SettingsGroup(title = "Where Pip helps") {
                    SettingsToggleRow(
                        title = "Tips on Home",
                        subtitle = "One short tip a day under the greeting.",
                        checked = preferences.homeTips,
                        enabled = preferences.enabled,
                        onCheckedChange = { onPreferenceChange(PipPreference.HOME_TIPS, it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleRow(
                        title = "Camera coaching",
                        subtitle = "Pip helps you frame pages. Off shows the plain guide.",
                        checked = preferences.cameraCoaching,
                        enabled = preferences.enabled,
                        onCheckedChange = { onPreferenceChange(PipPreference.CAMERA_COACHING, it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleRow(
                        title = "Cheer on exports",
                        subtitle = "A happy hello when a document is ready to share.",
                        checked = preferences.celebrations,
                        enabled = preferences.enabled,
                        onCheckedChange = { onPreferenceChange(PipPreference.CELEBRATIONS, it) },
                    )
                }
            }
            item("motion") {
                SettingsGroup(title = "Motion") {
                    SettingsToggleRow(
                        title = "Animate Pip",
                        subtitle = "Blinks, talks and hops. Pip stays still when system animations are off.",
                        checked = preferences.motion,
                        enabled = preferences.enabled,
                        onCheckedChange = { onPreferenceChange(PipPreference.MOTION, it) },
                    )
                }
            }
            item("privacy") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp),
                    )
                    Text(
                        text = "Pip lives on this phone and never looks at your documents. " +
                            "Everything it says is built into Scanly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PipHeroCard(
    preferences: PipPreferences,
    onEnabledChange: (Boolean) -> Unit,
) {
    val (line, mood) = when {
        !preferences.enabled -> "okay, i'll nap. switch me back on anytime." to PipMood.SLEEP
        !preferences.motion -> "i'll keep still. still here, though." to PipMood.IDLE
        else -> "hi! this is where you choose how much i help." to PipMood.HAPPY
    }
    val switchContainer by animateColorAsState(
        targetValue = if (preferences.enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "pip_master_container",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PipSays(line = line, mood = mood, size = 112.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Pip",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Scanly's little helper. Pip turns up on empty screens, in the camera and " +
                    "when something's ready, says one short thing, then gets out of the way.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(20.dp))
            Surface(
                checked = preferences.enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { role = Role.Switch },
                color = switchContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Pip",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (preferences.enabled) {
                                "On around the app"
                            } else {
                                "Napping · screens show plain text"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = preferences.enabled, onCheckedChange = null)
                }
            }
        }
    }
}

/** Settings hub summary of what Pip is doing. */
internal fun PipPreferences.settingsSummary(): String {
    if (!enabled) return "Napping"
    val parts = buildList {
        if (homeTips) add("tips")
        if (cameraCoaching) add("coaching")
        if (celebrations) add("cheers")
    }
    return if (parts.isEmpty()) "On" else "On · " + parts.joinToString(", ")
}

/** A small Pip that stands in for a settings icon. */
@Composable
internal fun PipSettingsIcon(enabled: Boolean) {
    // Occupies an icon's 22dp slot so row text lines up with its neighbours; Pip draws a bit larger.
    Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        Pip(
            mood = if (enabled) PipMood.IDLE else PipMood.SLEEP,
            size = 32.dp,
            modifier = Modifier.requiredSize(32.dp),
        )
    }
}
