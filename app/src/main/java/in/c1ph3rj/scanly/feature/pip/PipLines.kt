package `in`.c1ph3rj.scanly.feature.pip

/**
 * Everything Pip says, in one place so the voice stays consistent: lowercase, one short line,
 * plain words, never cute at the cost of clarity. Pip never claims to read or upload anything.
 */
object PipLines {
    const val ONBOARDING = "hi, i'm pip! point me at any page and i'll straighten it up for you."
    const val ONBOARDING_CAMERA = "one last thing! can i borrow the camera? it's only on while you scan."

    const val HOME_EMPTY = "nothing here yet. let's scan your first page together."
    const val LIBRARY_EMPTY = "a clean shelf! start a folder, or scan something in."
    const val LIBRARY_NO_FOLDERS = "no folders yet. they're great for receipts, bills and school stuff."
    const val LIBRARY_NO_DOCUMENTS = "no documents yet. i'm ready when you are."
    const val FOLDER_EMPTY = "this folder is waiting for its first document."
    const val DOCUMENT_NO_PAGES = "no pages yet. point me at some paper!"
    const val PAGE_TEXT_EMPTY = "no pages means no words yet."
    const val BENCHMARK_EMPTY = "pick a few photos and i'll race the detectors."
    const val FAQ_EMPTY = "hmm, i don't have an answer for that one."
    const val QR_HISTORY_EMPTY = "no codes yet. scan or make one and i'll keep it here."
    const val QR_NO_MATCHES = "nothing matches that. try another word?"
    const val SEARCH_STILL_READING = "i'm still reading some pages. matches may turn up in a sec."

    const val CAMERA_PERMISSION = "i can't see a thing yet. can i borrow the camera?"
    const val CAMERA_BLOCKED = "the camera is switched off for scanly. a quick trip to settings fixes it."
    const val QR_PERMISSION = "lend me the camera and i'll read the code for you."
    const val DOCUMENT_MISSING = "huh, that document wandered off."

    fun searchNoResults(query: String): String {
        val shown = query.trim().let { if (it.length > 28) it.take(27) + "…" else it }
        return "i looked on every page. no “$shown” anywhere."
    }

    fun exportReady(pageCount: Int): String = when (pageCount) {
        0 -> "ready when you are."
        1 -> "all set! 1 page, nice and straight."
        else -> "all set! $pageCount pages, nice and straight."
    }

    /** Short, true tips about real features, one per day on Home. */
    val homeTips = listOf(
        "tip: search finds words inside your scans, not just their names.",
        "tip: long-press a page and drag it to reorder.",
        "tip: open a document's text to copy or share what it says.",
        "tip: the tools tab can merge, compress and sign pdfs.",
        "tip: add a scanly widget to your home screen for one-tap scans.",
        "tip: turn on the grid in the camera to line pages up.",
        "tip: folders keep related scans together. try one for receipts.",
    )

    fun homeTipFor(epochDay: Long): String = homeTips[Math.floorMod(epochDay, homeTips.size.toLong()).toInt()]

    /** What Pip says when poked. */
    val pokes = listOf(
        "hey! that tickles.",
        "i live on your phone. no servers to call home to.",
        "your original photos are kept, so edits can always be undone.",
        "i'm open source. you can read every line of me.",
        "still free. still no account. still no ads.",
        "okay okay, i felt that one.",
    )
}
