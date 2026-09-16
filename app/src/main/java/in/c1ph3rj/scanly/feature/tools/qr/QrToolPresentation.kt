package `in`.c1ph3rj.scanly.feature.tools.qr

fun isQrWebLink(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("http://", ignoreCase = true)
}

fun formatQrScanResultTitle(value: String): String =
    if (isQrWebLink(value)) "Link" else "Text"

fun formatQrScanResultSubtitle(value: String): String =
    if (isQrWebLink(value)) "Open in a browser or copy" else "Copy to the clipboard"

fun formatQrGeneratePlaceholderHint(contentBlank: Boolean): String =
    if (contentBlank) "Preview appears as you type" else "Generating preview…"
