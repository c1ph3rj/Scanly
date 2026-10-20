package `in`.c1ph3rj.scanly.feature.pip

/** What Pip is feeling. Each mood is a small edit to the same 16×16 bitmap. */
enum class PipMood {
    IDLE,
    TALK,
    HAPPY,
    WOW,
    SLEEP,
    DIZZY,
}

/** Where Pip's eyes point. */
enum class PipLook(internal val shift: Int) {
    LEFT(-1),
    AHEAD(0),
    RIGHT(1),
}

/**
 * Pip — the little page from scanly's website — drawn from a 16×16 bitmap so it stays crisp at
 * any integer scale.
 *   k = ink outline · w = paper · E = eye · M = mouth · C = cheek · L = text line · . = empty
 */
internal object PipBitmap {
    const val SIZE = 16

    private val BASE = listOf(
        "..kkkkkkkkk.....",
        "..kwwwwwwwkk....",
        "..kwwwwwwwkwk...",
        "..kwwwwwwwkkkk..",
        "..kwwwwwwwwwwk..",
        "..kwwwwwwwwwwk..",
        "..kwwEwwwwEwwk..",
        "..kwwEwwwwEwwk..",
        "..kwwwwwwwwwwk..",
        "..kwCwwMMwwCwk..",
        "..kwwwwwwwwwwk..",
        "..kwwLLLLLLwwk..",
        "..kwwLLLLwwwwk..",
        "..kwwwwwwwwwwk..",
        "..kkkkkkkkkkkk..",
        "....kk....kk....",
    )

    /**
     * Rows for one animation frame. [blink] only shows on an idle Pip; [mouthOpen] only while
     * talking. Happy, sleepy and dizzy faces ignore [look] — their eyes are already drawn.
     */
    fun frame(
        mood: PipMood,
        look: PipLook = PipLook.AHEAD,
        blink: Boolean = false,
        mouthOpen: Boolean = false,
    ): List<String> {
        val rows = BASE.toMutableList()
        when (mood) {
            PipMood.IDLE -> if (blink) rows[6] = "..kwwwwwwwwwwk.."
            PipMood.SLEEP -> {
                rows[6] = "..kwwwwwwwwwwk.."
                rows[7] = "..kwEEwwwEEwwk.."
                rows[9] = "..kwCwwwwwwCwk.."
                rows[10] = "..kwwwwMMwwwwk.."
            }
            PipMood.DIZZY -> {
                rows[6] = "..kwEwEwwEwEwk.."
                rows[7] = "..kwwEwwwwEwwk.."
                rows[8] = "..kwEwEwwEwEwk.."
                rows[9] = "..kwCwwwwwwCwk.."
                rows[10] = "..kwwwMwMwwwwk.."
            }
            PipMood.HAPPY -> {
                rows[6] = "..kwwEwwwwEwwk.."
                rows[7] = "..kwEwEwwEwEwk.."
                rows[9] = "..kwCwMwwMwCwk.."
                rows[10] = "..kwwwwMMwwwwk.."
            }
            PipMood.WOW -> {
                rows[5] = "..kwwEwwwwEwwk.."
                rows[9] = "..kwCwwMMwwCwk.."
                rows[10] = "..kwwwwMMwwwwk.."
            }
            PipMood.TALK -> if (mouthOpen) rows[10] = "..kwwwwMMwwwwk.."
        }
        if (mood == PipMood.SLEEP || mood == PipMood.HAPPY || mood == PipMood.DIZZY) return rows
        return rows.map { row -> shiftEyes(row, look.shift) }
    }

    /** Horizontal runs of one colour, so a frame draws as a handful of rectangles. */
    fun runs(rows: List<String>): List<PipRun> = buildList {
        rows.forEachIndexed { y, row ->
            var x = 0
            while (x < row.length) {
                val cell = row[x]
                var length = 1
                while (x + length < row.length && row[x + length] == cell) length++
                if (cell != '.') add(PipRun(cell = cell, x = x, y = y, length = length))
                x += length
            }
        }
    }

    private fun shiftEyes(row: String, shift: Int): String {
        if (shift == 0 || 'E' !in row) return row
        val cells = row.map { if (it == 'E') 'w' else it }.toCharArray()
        row.forEachIndexed { index, cell ->
            if (cell == 'E') cells[index + shift] = 'E'
        }
        return String(cells)
    }
}

internal data class PipRun(
    val cell: Char,
    val x: Int,
    val y: Int,
    val length: Int,
)
