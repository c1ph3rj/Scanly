package `in`.c1ph3rj.scanly.data.text.indra

/**
 * The RTL recognizer reads glyphs left to right as they appear on the page (visual order).
 * [flip] converts that to logical order: reverse the line, then reverse runs of left-to-right
 * characters (Latin letters and digits, with the neutrals between them) back, and mirror brackets
 * that stay in the right-to-left flow.
 */
internal object IndraRtl {
    private enum class Kind { RTL, LTR, NEUTRAL }

    private val mirrored = mapOf(
        '('.code to ')'.code, ')'.code to '('.code,
        '['.code to ']'.code, ']'.code to '['.code,
        '{'.code to '}'.code, '}'.code to '{'.code,
        '<'.code to '>'.code, '>'.code to '<'.code,
        '«'.code to '»'.code, '»'.code to '«'.code,
    )

    fun isRtlLetter(codePoint: Int): Boolean {
        if (!Character.isLetter(codePoint)) return false
        val direction = Character.getDirectionality(codePoint)
        return direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
            direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }

    fun hasRtlLetters(text: String): Boolean = text.codePoints().anyMatch(::isRtlLetter)

    /** Counts of (RTL letters, other letters) over all [texts]. */
    fun letterCounts(texts: List<String>): Pair<Int, Int> {
        var rtl = 0
        var other = 0
        texts.forEach { text ->
            text.codePoints().forEach { cp ->
                if (isRtlLetter(cp)) rtl++ else if (Character.isLetter(cp)) other++
            }
        }
        return rtl to other
    }

    fun isPageRtl(texts: List<String>): Boolean {
        val (rtl, other) = letterCounts(texts)
        return rtl > other
    }

    /** [baseRtl] null means decide from the line: RTL when it has more RTL than left-to-right letters. */
    fun flip(text: String, baseRtl: Boolean? = null): String {
        if (text.isEmpty()) return text
        val cps = text.codePoints().toArray()
        if (cps.none(::isRtlLetter)) return text
        val rtl = baseRtl ?: letterCounts(listOf(text)).let { (r, o) -> r > o }
        val result = if (rtl) {
            flipSegment(cps)
        } else {
            flipRtlSegments(cps)
        }
        return String(result, 0, result.size)
    }

    private fun kind(cp: Int): Kind {
        if (isRtlLetter(cp)) return Kind.RTL
        return when (Character.getDirectionality(cp)) {
            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_EUROPEAN_NUMBER,
            Character.DIRECTIONALITY_ARABIC_NUMBER,
            -> Kind.LTR
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            -> Kind.RTL
            else -> Kind.NEUTRAL
        }
    }

    /** Left-to-right page: only stretches of RTL letters (with neutrals and numbers between) flip. */
    private fun flipRtlSegments(cps: IntArray): IntArray {
        val out = cps.copyOf()
        var i = 0
        while (i < cps.size) {
            if (!isRtlLetter(cps[i])) {
                i++
                continue
            }
            var end = i
            var j = i + 1
            while (j < cps.size && !(Character.getDirectionality(cps[j]) == Character.DIRECTIONALITY_LEFT_TO_RIGHT &&
                    !isRtlLetter(cps[j]))
            ) {
                if (isRtlLetter(cps[j])) end = j
                j++
            }
            val flipped = flipSegment(cps.copyOfRange(i, end + 1))
            flipped.copyInto(out, i)
            i = end + 1
        }
        return out
    }

    private fun flipSegment(visual: IntArray): IntArray {
        val reversed = visual.reversedArray()
        val kinds = Array(reversed.size) { kind(reversed[it]) }
        val inRun = BooleanArray(reversed.size)
        var i = 0
        while (i < reversed.size) {
            if (kinds[i] != Kind.LTR) {
                i++
                continue
            }
            var end = i
            var j = i + 1
            while (j < reversed.size && kinds[j] != Kind.RTL) {
                if (kinds[j] == Kind.LTR) end = j
                j++
            }
            reversed.reverse(i, end + 1)
            for (k in i..end) inRun[k] = true
            i = end + 1
        }
        for (k in reversed.indices) {
            if (!inRun[k]) reversed[k] = mirrored[reversed[k]] ?: reversed[k]
        }
        return reversed
    }
}
