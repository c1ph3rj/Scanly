package `in`.c1ph3rj.scanly.navigation

import android.net.Uri
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource

/**
 * Optional "start with this PDF" argument for the PDF tools, so the reader's menu can hand its
 * open file straight to Compress, Sign, etc. Plain tool routes (no argument) still match.
 */
internal const val PdfToolSourceArgument = "source"

internal fun pdfToolRoutePattern(route: String): String = "$route?$PdfToolSourceArgument={$PdfToolSourceArgument}"

internal fun pdfToolRoute(route: String, source: PdfToolSource): String =
    "$route?$PdfToolSourceArgument=${Uri.encode(encodePdfToolSource(source))}"

internal fun pdfToolSourceNavArgument(): NamedNavArgument = navArgument(PdfToolSourceArgument) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

internal fun NavBackStackEntry.pdfToolSource(): PdfToolSource? =
    arguments?.getString(PdfToolSourceArgument)?.let(::decodePdfToolSource)

/** `kind \u001F id \u001F label`: the unit separator never appears in paths, URIs or titles. */
internal fun encodePdfToolSource(source: PdfToolSource): String = when (source) {
    is PdfToolSource.AppFile -> listOf("app", source.filePath, source.displayName)
    is PdfToolSource.DeviceUri -> listOf("uri", source.uriString, source.displayName)
    is PdfToolSource.LibraryDocument -> listOf("lib", source.documentId, source.title)
}.joinToString(SEPARATOR)

internal fun decodePdfToolSource(value: String): PdfToolSource? {
    val parts = value.split(SEPARATOR)
    if (parts.size != 3 || parts[1].isBlank()) return null
    return when (parts[0]) {
        "app" -> PdfToolSource.AppFile(parts[1], parts[2])
        "uri" -> PdfToolSource.DeviceUri(parts[1], parts[2])
        "lib" -> PdfToolSource.LibraryDocument(parts[1], parts[2])
        else -> null
    }
}

private const val SEPARATOR = "\u001F"
