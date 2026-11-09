package `in`.c1ph3rj.scanly.domain.model

import androidx.annotation.StringRes
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.R

enum class ThemeMode(
    val storageValue: String,
    @param:StringRes val labelRes: Int,
) {
    SYSTEM("system", R.string.settings_system),
    LIGHT("light", R.string.settings_light),
    DARK("dark", R.string.settings_dark);

    val label: String get() = appString(labelRes)


    companion object {
        fun fromStorage(value: String): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
