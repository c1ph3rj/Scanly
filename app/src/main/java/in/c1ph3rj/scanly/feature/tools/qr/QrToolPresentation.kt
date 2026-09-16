package `in`.c1ph3rj.scanly.feature.tools.qr

fun isQrWebLink(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("http://", ignoreCase = true)
}

/** Trimmed HTTP(S) URL to open, or null if [value] is not a web link. */
fun qrWebLinkToOpen(value: String): String? =
    value.trim().takeIf(::isQrWebLink)

fun qrUriIsOpenableWebLink(scheme: String?): Boolean {
    val normalized = scheme?.lowercase() ?: return false
    return normalized == "http" || normalized == "https"
}

fun formatQrScanResultTitle(value: String): String =
    if (isQrWebLink(value)) "Link" else "Text"

fun formatQrScanResultSubtitle(value: String): String =
    if (isQrWebLink(value)) "Open in a browser or copy" else "Copy to the clipboard"

fun formatQrGeneratePlaceholderHint(contentBlank: Boolean): String =
    if (contentBlank) "Preview appears as you type" else "Generating preview…"

/**
 * Landscape hosts Scan/Create inside the scan panel. A blocked camera must still
 * show that control so Generate stays reachable without granting Camera.
 */
fun qrPermissionGateShowsModeSelector(twoPane: Boolean): Boolean = twoPane
