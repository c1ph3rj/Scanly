package `in`.c1ph3rj.scanly.core.common

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * Per-app language ("app language") support.
 *
 * - Android 13+: the platform owns the choice through [LocaleManager]. It survives reinstalls via
 *   backup, appears in system Settings › Apps › Scanly › Language (see `locales_config.xml`), and
 *   MainActivity handles locale configuration changes without replacing its window.
 * - Android 10–12: the choice is kept in a private preference. AppLocaleProvider updates the
 *   existing composition's resources; [wrap] supplies resources to non-Compose callers.
 *
 * A null language means "follow the device language".
 */
object AppLocales {
    private const val PREFS = "scanly_app_locale"
    private const val KEY_TAG = "language_tag"
    private val configurationRevision = MutableStateFlow(0)
    internal val changes = configurationRevision.asStateFlow()

    private val usesPlatformLocales: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** The language the user picked in Scanly, or null for the device language. */
    fun selectedLanguage(context: Context): AppLanguage? {
        if (usesPlatformLocales) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            return if (locales == null || locales.isEmpty) null else AppLanguage.from(locales[0])
        }
        return AppLanguage.fromTag(storedTag(context))
    }

    /** The device's own language, ignoring any per-app choice. */
    fun deviceLocale(context: Context): Locale {
        if (usesPlatformLocales) {
            val system = context.getSystemService(LocaleManager::class.java)?.systemLocales
            if (system != null && !system.isEmpty) return system[0]
        }
        return Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
    }

    /** Switches resources in place, retaining navigation, scroll position and the current theme. */
    fun setLanguage(activity: Activity, language: AppLanguage?) {
        prefs(activity).edit().putString(KEY_TAG, language?.tag).apply()
        if (usesPlatformLocales) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (language == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
        }
        onConfigurationChanged(activity.applicationContext)
    }

    /** Also called for language changes made in Android Settings, including follow-phone mode. */
    fun onConfigurationChanged(context: Context) {
        applyDefaultLocale(context)
        AppStrings.onLocaleChanged()
        configurationRevision.update { it + 1 }
    }

    /** Android 10–12: makes [Locale.getDefault] (used by date and number formatting) follow the choice. */
    fun applyDefaultLocale(context: Context) {
        if (usesPlatformLocales) return
        val locale = AppLanguage.fromTag(storedTag(context))?.locale ?: deviceLocale(context)
        Locale.setDefault(locale)
    }

    /** A resource context for the chosen language, including follow-phone mode. */
    fun wrap(base: Context): Context {
        val locale = selectedLanguage(base)?.locale ?: deviceLocale(base)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** The locale UI text is currently shown in. */
    fun currentLocale(context: Context): Locale =
        context.resources.configuration.locales[0] ?: Locale.getDefault()

    private fun storedTag(context: Context): String? =
        prefs(context).getString(KEY_TAG, null)?.takeIf { it.isNotBlank() }

    private fun prefs(context: Context) =
        (context.applicationContext ?: context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
