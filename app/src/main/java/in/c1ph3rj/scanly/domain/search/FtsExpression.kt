package `in`.c1ph3rj.scanly.domain.search

/**
 * An FTS4 MATCH expression kept as parts: a page must match every group, and a group matches
 * when any of its alternatives does. Alternatives are single FTS terms (`rent*`, `receipt`)
 * or quoted phrases, built only from folded letters and digits.
 */
class FtsExpression(
    val groups: List<List<String>>,
    /** Terms or phrases a page must not contain, each checked on its own. */
    val excluded: List<String> = emptyList(),
) {
    init {
        require(groups.isNotEmpty() && groups.all { it.isNotEmpty() })
    }

    fun withoutExclusions(): FtsExpression = if (excluded.isEmpty()) this else FtsExpression(groups)

    /** One group's alternatives OR-ed together. */
    fun group(index: Int): String = groups[index].joinToString(" OR ")

    /**
     * Plain implicit-AND form, valid only while no group has more than one alternative and
     * nothing is excluded (FTS4's NOT differs between its two query syntaxes).
     */
    val allOf: String?
        get() = if (excluded.isEmpty() && groups.all { it.size == 1 }) groups.joinToString(" ") { it.single() } else null

    /** Every alternative OR-ed: matches a superset of the expression, for snippets and matchinfo. */
    val anyOf: String get() = groups.flatten().joinToString(" OR ")

    /** The words the expression searches for, without FTS syntax (quotes, `*`, `column:`). */
    val words: List<String>
        get() = groups.flatten()
            .flatMap { alternative -> alternative.substringAfter(':').trim('"').split(' ') }
            .map { it.removeSuffix("*") }
            .filter { it.isNotEmpty() }
            .distinct()

    /**
     * Group of each term in the order FTS numbers them (`offsets()` term numbers); a quoted
     * phrase counts one term per word. The same order holds for [allOf] and [anyOf].
     */
    val termGroups: IntArray by lazy {
        buildList {
            groups.forEachIndexed { index, alternatives ->
                alternatives.forEach { alternative ->
                    repeat(alternative.trim('"').split(' ').count { it.isNotEmpty() }) { add(index) }
                }
            }
        }.toIntArray()
    }
}
