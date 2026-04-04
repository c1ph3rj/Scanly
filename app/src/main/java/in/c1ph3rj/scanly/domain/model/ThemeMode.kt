package `in`.c1ph3rj.scanly.domain.model

enum class ThemeMode(
    val storageValue: String,
    val label: String,
) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromStorage(value: String): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
