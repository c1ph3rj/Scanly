package `in`.c1ph3rj.scanly.core.common

import android.content.res.Configuration
import android.os.LocaleList
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import `in`.c1ph3rj.scanly.R

/** Replaces localized resources, never the activity, navigation graph or remembered screen state. */
@Composable
fun AppLocaleProvider(content: @Composable () -> Unit) {
    val base = LocalContext.current
    val systemConfiguration = LocalConfiguration.current
    val revision by AppLocales.changes.collectAsState()
    val context = remember(base, systemConfiguration, revision) {
        val locale = AppLocales.selectedLanguage(base)?.locale ?: AppLocales.deviceLocale(base)
        val configuration = Configuration(systemConfiguration).apply { setLocales(LocaleList(locale)) }
        // Keep the Activity in the ContextWrapper chain for permissions, Hilt and Android views.
        ContextThemeWrapper(base, R.style.Theme_Scanly).apply {
            applyOverrideConfiguration(configuration)
        }
    }
    val configuration = context.resources.configuration
    CompositionLocalProvider(
        LocalContext provides context,
        LocalResources provides context.resources,
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides if (configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        },
        content = content,
    )
}
