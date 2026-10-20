package `in`.c1ph3rj.scanly.feature.pip

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import `in`.c1ph3rj.scanly.domain.model.PipPreferences

/**
 * Pip's preferences for the whole app, provided once in `MainActivity`. Previews and tests get
 * the defaults (Pip on, everything on).
 */
val LocalPipPreferences = compositionLocalOf { PipPreferences() }

/** True when Pip may animate: the user allows it and system animations are not switched off. */
@Composable
fun pipMotionEnabled(): Boolean =
    LocalPipPreferences.current.motion && rememberSystemAnimationsEnabled()

@Composable
private fun rememberSystemAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) > 0f
    }
}
