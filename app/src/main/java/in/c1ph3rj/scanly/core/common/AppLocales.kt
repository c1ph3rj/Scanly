package `in`.c1ph3rj.scanly.core.common

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Per-app language ("app language") support.
 *
 * - Android 13+: the platform owns the choice through [LocaleManager]. It survives reinstalls via
 *   backup, appears in system Settings › Apps › Scanly › Language (see `locales_config.xml`), and
 *   the system recreates activities when it changes.
 * - Android 10–12: the choice is kept in a private preference and applied by wrapping the
 *   activity's base context ([wrap]) and the app-wide default [Locale].
 *
 * A null language means "follow the device language".
 */
object AppLocales {
    private const val PREFS = "scanly_app_locale"
    private const val KEY_TAG = "language_tag"

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

    /**
     * Switches the app language. On Android 13+ the system recreates the activity; on older
     * versions [activity] is recreated here so every screen picks up the new resources.
     */
    fun setLanguage(activity: Activity, language: AppLanguage?) {
        prefs(activity).edit().putString(KEY_TAG, language?.tag).commit()
        if (usesPlatformLocales) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (language == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
        } else {
            applyDefaultLocale(activity.applicationContext)
            AppStrings.onLocaleChanged()
            activity.recreate()
        }
    }

    /** Android 10–12: makes [Locale.getDefault] (used by date and number formatting) follow the choice. */
    fun applyDefaultLocale(context: Context) {
        if (usesPlatformLocales) return
        val locale = AppLanguage.fromTag(storedTag(context))?.locale ?: deviceLocale(context)
        Locale.setDefault(locale)
    }

    /** Android 10–12: [base] with the chosen language applied. Returns [base] when nothing is chosen. */
    fun wrap(base: Context): Context {
        if (usesPlatformLocales) return base
        val language = AppLanguage.fromTag(storedTag(base)) ?: return base
        val locale = language.locale
        Locale.setDefault(locale)
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
