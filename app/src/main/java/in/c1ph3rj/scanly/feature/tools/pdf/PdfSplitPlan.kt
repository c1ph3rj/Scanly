package `in`.c1ph3rj.scanly.feature.tools.pdf

/** How the user tells Split where to cut. Every mode ends up as a list of page lists. */
enum class PdfSplitMode { Custom, EveryN, Extract }

/**
 * Pages [from]..[to] as the user sees them (1-based, inclusive). A range may be invalid while
 * the user is typing (0, past the end, or reversed); [isValid] says whether it can be split.
 */
data class PdfPageRange(val from: Int, val to: Int) {
    fun isValid(pageCount: Int): Boolean = from in 1..to && to <= pageCount

    val size: Int get() = (to - from + 1).coerceAtLeast(0)

    /** 0-based page indexes, for the repository. */
    fun pageIndexes(): List<Int> = (from..to).map { it - 1 }
}

/** "1-3, 6, 8-9" for [ranges]; single pages print without a dash. */
fun formatPageRanges(ranges: List<PdfPageRange>): String =
    ranges.joinToString(", ") { if (it.from == it.to) "${it.from}" else "${it.from}-${it.to}" }

/** Display form for a label: "1–3, 6" with en dashes. */
fun formatPageRangesForDisplay(ranges: List<PdfPageRange>): String =
    ranges.joinToString(", ") { if (it.from == it.to) "${it.from}" else "${it.from}–${it.to}" }

/**
 * Parses "1-3, 6-8" (also en/em dashes and spaces) into ranges. Returns null when any part is
 * not a page or range, or falls outside 1..[pageCount], so the caller can keep the last good
 * value while the user is mid-typing ("1-").
 */
fun parsePageRanges(text: String, pageCount: Int): List<PdfPageRange>? {
    val parts = text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val ranges = parts.map { part ->
        val match = RangePattern.matchEntire(part) ?: return null
        val from = match.groupValues[1].toIntOrNull() ?: return null
        val to = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: from
        PdfPageRange(from, to)
    }
    return ranges.takeIf { list -> list.all { it.isValid(pageCount) } }
}

private val RangePattern = Regex("""^(\d{1,5})\s*(?:[-–—]\s*(\d{1,5}))?$""")

/** Groups sorted 1-based [pages] into runs: [1,2,3,6] → 1–3, 6. */
fun compressPages(pages: Collection<Int>): List<PdfPageRange> {
    val sorted = pages.distinct().sorted()
    val runs = mutableListOf<PdfPageRange>()
    for (page in sorted) {
        val last = runs.lastOrNull()
        if (last != null && last.to == page - 1) {
            runs[runs.lastIndex] = last.copy(to = page)
        } else {
            runs += PdfPageRange(page, page)
        }
    }
    return runs
}

/** 1-based pages that no valid range covers. */
fun uncoveredPages(ranges: List<PdfPageRange>, pageCount: Int): List<Int> {
    val covered = BooleanArray(pageCount + 1)
    ranges.filter { it.isValid(pageCount) }.forEach { range ->
        for (page in range.from..range.to) covered[page] = true
    }
    return (1..pageCount).filterNot { covered[it] }
}

/** Index of the first valid range holding 1-based [page], or -1. */
fun rangeIndexForPage(ranges: List<PdfPageRange>, page: Int, pageCount: Int): Int =
    ranges.indexOfFirst { it.isValid(pageCount) && page in it.from..it.to }

/** Consecutive chunks of [pagesPerPart] pages; the last one may be shorter. */
fun everyNRanges(pageCount: Int, pagesPerPart: Int): List<PdfPageRange> {
    if (pageCount <= 0) return emptyList()
    val n = pagesPerPart.coerceIn(1, pageCount)
    return (1..pageCount step n).map { start -> PdfPageRange(start, minOf(pageCount, start + n - 1)) }
}

/** Largest useful "pages per PDF": one less than the page count, so there are always two parts. */
fun maxPagesPerPart(pageCount: Int): Int = (pageCount - 1).coerceAtLeast(1)

/** The next range offered by "Add range": right after the last one, to the end of the file. */
fun nextRange(ranges: List<PdfPageRange>, pageCount: Int): PdfPageRange {
    val start = ((ranges.lastOrNull()?.to ?: 0) + 1).coerceIn(1, pageCount.coerceAtLeast(1))
    return PdfPageRange(start, pageCount.coerceAtLeast(start))
}

/** A suggested first range for a fresh file: the first half, the rest as a second range. */
fun defaultCustomRanges(pageCount: Int): List<PdfPageRange> = when {
    pageCount < 2 -> listOf(PdfPageRange(1, pageCount.coerceAtLeast(1)))
    else -> {
        val half = pageCount / 2
        listOf(PdfPageRange(1, half), PdfPageRange(half + 1, pageCount))
    }
}

/** Default "pages per PDF": one page for short files, else a quarter-ish of the file. */
fun defaultPagesPerPart(pageCount: Int): Int = when {
    pageCount <= 3 -> 1
    else -> (pageCount / 4).coerceIn(1, maxPagesPerPart(pageCount))
}

/** Everything the plan needs to turn the user's choices into output page lists. */
data class PdfSplitChoices(
    val pageCount: Int,
    val mode: PdfSplitMode,
    val customRanges: List<PdfPageRange>,
    val mergeRanges: Boolean,
    val pagesPerPart: Int,
    /** 0-based picked pages for Extract. */
    val selectedPages: Set<Int>,
    val extractSeparately: Boolean,
    val keepRest: Boolean,
)

/** One output PDF: its 0-based page indexes, in order. */
data class PdfSplitOutputPlan(val pageIndexes: List<Int>) {
    /** 1-based page runs, for names and labels. */
    val runs: List<PdfPageRange> get() = compressPages(pageIndexes.map { it + 1 })
}

/**
 * The PDFs a split will write, or an empty list when the current choices can't be split
 * (an invalid range, nothing picked, everything picked with nothing left to cut, …).
 */
fun planSplitOutputs(choices: PdfSplitChoices): List<PdfSplitOutputPlan> {
    val count = choices.pageCount
    if (count < 2) return emptyList()
    return when (choices.mode) {
        PdfSplitMode.Custom -> {
            val ranges = choices.customRanges
            if (ranges.isEmpty() || ranges.any { !it.isValid(count) }) return emptyList()
            if (choices.mergeRanges) {
                listOf(PdfSplitOutputPlan(ranges.flatMap(PdfPageRange::pageIndexes)))
            } else {
                ranges.map { PdfSplitOutputPlan(it.pageIndexes()) }
            }
        }
        PdfSplitMode.EveryN -> everyNRanges(count, choices.pagesPerPart.coerceAtMost(maxPagesPerPart(count)))
            .map { PdfSplitOutputPlan(it.pageIndexes()) }
        PdfSplitMode.Extract -> {
            val picked = choices.selectedPages.filter { it in 0 until count }.sorted()
            if (picked.isEmpty()) return emptyList()
            val rest = (0 until count).filterNot { it in choices.selectedPages }
            // Extracting every page as one PDF would just copy the file.
            if (rest.isEmpty() && !choices.extractSeparately) return emptyList()
            buildList {
                if (choices.extractSeparately) {
                    picked.forEach { add(PdfSplitOutputPlan(listOf(it))) }
                } else {
                    add(PdfSplitOutputPlan(picked))
                }
                if (choices.keepRest && rest.isNotEmpty()) add(PdfSplitOutputPlan(rest))
            }
        }
    }
}
