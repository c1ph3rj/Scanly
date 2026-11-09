package `in`.c1ph3rj.scanly.domain.model

import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString
import androidx.annotation.StringRes

enum class PdfPageOrientation(
    @param:StringRes val labelRes: Int,
) {
    AUTO(R.string.filter_customize_auto),
    PORTRAIT(R.string.pdf_page_orientation_portrait),
    LANDSCAPE(R.string.pdf_page_orientation_landscape),
    ;

    val label: String get() = appString(labelRes)
}

enum class PdfPageSize(
    @param:StringRes val labelRes: Int,
    @param:StringRes val dimensionsLabelRes: Int,
    internal val portraitWidthPoints: Int?,
    internal val portraitHeightPoints: Int?,
) {
    FIT(R.string.pdf_page_size_auto_fit, R.string.pdf_page_size_match_each_scan, null, null),
    A3(R.string.pdf_page_size_a3, R.string.pdf_page_size_29_7_42_0_cm, 842, 1_191),
    A4(R.string.pdf_page_size_a4, R.string.pdf_page_size_21_0_29_7_cm, 595, 842),
    A5(R.string.pdf_page_size_a5, R.string.pdf_page_size_14_8_21_0_cm, 420, 595),
    B4(R.string.pdf_page_size_b4, R.string.pdf_page_size_25_0_35_3_cm, 709, 1_001),
    B5(R.string.pdf_page_size_b5, R.string.pdf_page_size_17_6_25_0_cm, 499, 709),
    US_LETTER(R.string.pdf_page_size_letter, R.string.pdf_page_size_21_6_27_9_cm, 612, 792),
    TABLOID(R.string.pdf_page_size_tabloid, R.string.pdf_page_size_27_9_43_2_cm, 792, 1_224),
    LEGAL(R.string.settings_hub_sub_screens_legal, R.string.pdf_page_size_21_6_35_6_cm, 612, 1_008),
    EXECUTIVE(R.string.pdf_page_size_executive, R.string.pdf_page_size_18_4_26_7_cm, 522, 756),
    POSTCARD(R.string.pdf_page_size_postcard, R.string.pdf_page_size_10_0_14_8_cm, 283, 420),
    AMERICAN_FOOLSCAP(R.string.pdf_page_size_american_foolscap, R.string.pdf_page_size_21_6_33_0_cm, 612, 936),
    EUROPEAN_FOOLSCAP(R.string.pdf_page_size_european_foolscap, R.string.pdf_page_size_22_9_33_0_cm, 649, 936),
    ;

    val dimensionsLabel: String get() = appString(dimensionsLabelRes)


    val label: String get() = appString(labelRes)
}

enum class PdfPageMargin(
    @param:StringRes val labelRes: Int,
) {
    NONE(R.string.pdf_page_margin_no_margin),
    SMALL(R.string.pdf_watermark_small),
    LARGE(R.string.pdf_page_margin_big),
    ;

    val label: String get() = appString(labelRes)
}

enum class PdfPageNumber(
    @param:StringRes val labelRes: Int,
) {
    NONE(R.string.model_benchmark_none),
    BOTTOM_LEFT(R.string.page_crop_left),
    BOTTOM_CENTER(R.string.pdf_page_number_center),
    BOTTOM_RIGHT(R.string.page_crop_right),
    ;

    val label: String get() = appString(labelRes)
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
    password.isBlank() -> appString(R.string.pdf_export_options_enter_a_pdf_password)
    password.length < MinimumPdfPasswordLength ->
        appString(R.string.pdf_export_options_use_at_least_characters, MinimumPdfPasswordLength)
    password.length > MaximumPdfPasswordLength ->
        appString(R.string.pdf_export_options_use_no_more_than_characters, MaximumPdfPasswordLength)
    else -> null
}

const val MinimumPdfPasswordLength = 4
const val MaximumPdfPasswordLength = 64
