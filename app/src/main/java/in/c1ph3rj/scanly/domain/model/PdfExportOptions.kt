package `in`.c1ph3rj.scanly.domain.model

enum class PdfPageOrientation(
    val label: String,
) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
}

enum class PdfPageSize(
    val label: String,
    val dimensionsLabel: String,
    internal val portraitWidthPoints: Int?,
    internal val portraitHeightPoints: Int?,
) {
    FIT("Auto fit", "Match each scan", null, null),
    A3("A3", "29.7 × 42.0 cm", 842, 1_191),
    A4("A4", "21.0 × 29.7 cm", 595, 842),
    A5("A5", "14.8 × 21.0 cm", 420, 595),
    B4("B4", "25.0 × 35.3 cm", 709, 1_001),
    B5("B5", "17.6 × 25.0 cm", 499, 709),
    US_LETTER("Letter", "21.6 × 27.9 cm", 612, 792),
    TABLOID("Tabloid", "27.9 × 43.2 cm", 792, 1_224),
    LEGAL("Legal", "21.6 × 35.6 cm", 612, 1_008),
    EXECUTIVE("Executive", "18.4 × 26.7 cm", 522, 756),
    POSTCARD("Postcard", "10.0 × 14.8 cm", 283, 420),
    AMERICAN_FOOLSCAP("American foolscap", "21.6 × 33.0 cm", 612, 936),
    EUROPEAN_FOOLSCAP("European foolscap", "22.9 × 33.0 cm", 649, 936),
}

enum class PdfPageMargin(
    val label: String,
) {
    NONE("No margin"),
    SMALL("Small"),
    LARGE("Big"),
}

enum class PdfPageNumber(
    val label: String,
) {
    NONE("None"),
    BOTTOM_LEFT("Left"),
    BOTTOM_CENTER("Center"),
    BOTTOM_RIGHT("Right"),
}

data class PdfExportOptions(
    val orientation: PdfPageOrientation = PdfPageOrientation.AUTO,
    val pageSize: PdfPageSize = PdfPageSize.FIT,
    val margin: PdfPageMargin = PdfPageMargin.NONE,
    val pageNumber: PdfPageNumber = PdfPageNumber.NONE,
    /** Null disables protection; a non-null value is used as the PDF open password. */
    val password: String? = null,
)

fun PdfExportOptions.validationError(): String? = when {
    password == null -> null
    password.isBlank() -> "Enter a PDF password."
    password.length < MinimumPdfPasswordLength ->
        "Use at least $MinimumPdfPasswordLength characters."
    password.length > MaximumPdfPasswordLength ->
        "Use no more than $MaximumPdfPasswordLength characters."
    else -> null
}

const val MinimumPdfPasswordLength = 4
const val MaximumPdfPasswordLength = 64
