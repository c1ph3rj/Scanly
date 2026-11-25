package `in`.c1ph3rj.scanly.domain.model

/**
 * A reusable document name pattern such as `Receipt {date}` or `{iso} Invoice {n}`.
 * Tokens are listed in [NamingToken]; see `NamingTemplateRenderer`.
 *
 * @property isDefault Used for new documents' suggested names instead of
 * [DocumentTitleFormat.default]. At most one template is the default; none means the built-in.
 */
data class NamingTemplate(
    val id: String,
    val pattern: String,
    val isDefault: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/** Placeholders a [NamingTemplate] pattern may contain, written `{key}` (case-insensitive). */
enum class NamingToken(val key: String) {
    /** Locale short date, as in "Scan <date>". */
    DATE("date"),

    /** Locale short time. */
    TIME("time"),

    /** Locale medium date + short time, as in the default "Scan <date time>". */
    DATE_TIME("datetime"),

    /** `yyyy-MM-dd`. */
    ISO_DATE("iso"),
    YEAR("year"),

    /** Two-digit month. */
    MONTH("month"),

    /** Two-digit day of month. */
    DAY("day"),

    /** Locale full weekday name. */
    WEEKDAY("weekday"),

    /** Smallest number from 1 that makes the name unique among existing documents. */
    NUMBER("n"),
    ;

    val placeholder: String get() = "{$key}"
}

/** One entry in the "Suggest name" cycle: a built-in date format or a saved template. */
sealed interface TitleSuggestionOption {
    data class BuiltIn(val format: DocumentTitleFormat) : TitleSuggestionOption

    data class Template(val template: NamingTemplate) : TitleSuggestionOption
}

object NamingTemplateRules {
    const val MAX_PATTERN_LENGTH = 80
}
