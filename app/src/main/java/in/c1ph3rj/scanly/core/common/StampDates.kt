package `in`.c1ph3rj.scanly.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "13 Oct 2026" for dates drawn into PDFs and scans (signature dates, watermark dates).
 *
 * Follows the app language when its script can be embedded in a PDF (Latin, Cyrillic,
 * Vietnamese); other scripts fall back to English month names so the stamp always draws.
 */
fun formatStampDate(millis: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault()): String {
    val stampLocale = if (AppLanguage.from(locale)?.stampsInOwnScript == true) locale else Locale.ENGLISH
    return SimpleDateFormat("d MMM yyyy", stampLocale).format(Date(millis))
}
