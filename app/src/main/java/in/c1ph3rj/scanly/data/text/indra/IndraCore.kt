package `in`.c1ph3rj.scanly.data.text.indra

import java.text.Normalizer
import kotlin.math.exp

/** A text line box in page pixels (x right, y down). */
internal data class IndraBox(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val score: Float = 1f,
) {
    val width: Float get() = x1 - x0
    val height: Float get() = y1 - y0
}

/** Result of a greedy CTC decode of one line. [ids] are class ids (0 = blank is never kept). */
internal class CtcLine(val ids: IntArray, val steps: IntArray, val conf: Float)

internal object IndraCtc {
    const val MIN_CONF = 0.5f

    /**
     * Greedy CTC decode of [steps] frames of [classes] logits starting at [offset].
     * conf = mean top-softmax probability over the kept frames, or 0 if none were kept.
     */
    fun decode(logits: FloatArray, offset: Int, steps: Int, classes: Int): CtcLine {
        val argmax = IntArray(steps)
        val maxima = FloatArray(steps)
        for (t in 0 until steps) {
            val base = offset + t * classes
            var best = 0
            var bestValue = logits[base]
            for (c in 1 until classes) {
                val v = logits[base + c]
                if (v > bestValue) {
                    bestValue = v
                    best = c
                }
            }
            argmax[t] = best
            maxima[t] = bestValue
        }
        val keptIds = ArrayList<Int>()
        val keptSteps = ArrayList<Int>()
        var confSum = 0.0
        for (t in 0 until steps) {
            val id = argmax[t]
            if (id == 0 || (t > 0 && id == argmax[t - 1])) continue
            keptIds += id
            keptSteps += t
            val base = offset + t * classes
            var denominator = 0.0
            for (c in 0 until classes) denominator += exp((logits[base + c] - maxima[t]).toDouble())
            confSum += 1.0 / denominator
        }
        val conf = if (keptIds.isEmpty()) 0f else (confSum / keptIds.size).toFloat()
        return CtcLine(keptIds.toIntArray(), keptSteps.toIntArray(), conf)
    }
}

/** Recognizer charset: class 0 is the CTC blank, class i is chars[i - 1]. */
internal class IndraVocab(val chars: List<String>) {
    private val hasJamo = chars.any { token -> token.any { it == 'ᄀ' } }

    private fun token(id: Int): String? = chars.getOrNull(id - 1)

    /** Base vocabulary: joins tokens, spaces a LaTeX command from a following letter, NFC for jamo. */
    fun decodeBase(ids: IntArray): String {
        val tokens = ids.toList().mapNotNull(::token)
        val builder = StringBuilder()
        tokens.forEachIndexed { index, token ->
            builder.append(token)
            val next = tokens.getOrNull(index + 1)
            if (next != null && isLatexCommand(token) && next.isNotEmpty() && next[0].isLetter()) {
                builder.append(' ')
            }
        }
        val text = builder.toString()
        return if (hasJamo) Normalizer.normalize(text, Normalizer.Form.NFC) else text
    }

    /** Group vocabularies: characters joined, whitespace collapsed, NFC. */
    fun decodeGroup(ids: IntArray): String {
        val joined = ids.toList().mapNotNull(::token).joinToString("")
        val collapsed = joined.split(WHITESPACE).filter { it.isNotEmpty() }.joinToString(" ")
        return Normalizer.normalize(collapsed, Normalizer.Form.NFC)
    }

    private fun isLatexCommand(token: String): Boolean =
        token.length > 2 && token[0] == '\\' && token.substring(1).all { it.isLetter() }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        /** Parses `{"blank":0,"chars":[...]}`. */
        fun fromJson(json: String): IndraVocab {
            val array = org.json.JSONObject(json).getJSONArray("chars")
            return IndraVocab(List(array.length()) { array.getString(it) })
        }
    }
}
