package `in`.c1ph3rj.scanly.core.common

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object StorageFormatter {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) {
            return "0 B"
        }

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }
}
