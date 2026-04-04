package `in`.c1ph3rj.scanly.domain.model

enum class PdfPageOrientation(
    val label: String,
) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
}

enum class PdfPageSize(
    val label: String,
) {
    FIT("Fit"),
    A4("A4"),
    US_LETTER("US Letter"),
}

enum class PdfPageMargin(
    val label: String,
) {
    NONE("No margin"),
    SMALL("Small"),
    LARGE("Big"),
}

data class PdfExportOptions(
    val orientation: PdfPageOrientation = PdfPageOrientation.PORTRAIT,
    val pageSize: PdfPageSize = PdfPageSize.FIT,
    val margin: PdfPageMargin = PdfPageMargin.NONE,
)
