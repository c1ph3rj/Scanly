package `in`.c1ph3rj.scanly.domain.model

/**
 * A user-made label. Tags complement folders: a document lives in at most one folder but can carry
 * any number of tags (a receipt can be both "Travel" and "Reimbursable").
 *
 * @property documentCount Live (not trashed) documents carrying the tag.
 */
data class DocumentTag(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val documentCount: Int = 0,
)

object DocumentTagRules {
    const val MAX_NAME_LENGTH = 40

    /** Trimmed, inner whitespace collapsed; what is stored and shown. */
    fun normalizeName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH)

    /** Key two names must not share (case-insensitive, locale-independent). */
    fun key(name: String): String = normalizeName(name).lowercase(java.util.Locale.ROOT)
}
