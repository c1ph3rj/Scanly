package `in`.c1ph3rj.scanly.feature.pip

import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString

/**
 * Everything Pip says, in one place so the voice stays consistent: lowercase, one short line,
 * plain words, never cute at the cost of clarity. Pip never claims to read or upload anything.
 */
object PipLines {
    val ONBOARDING: String get() = appString(R.string.pip_lines_hi_i_m_pip_point)
    val ONBOARDING_CAMERA: String get() = appString(R.string.pip_lines_one_last_thing_can_i)

    val HOME_EMPTY: String get() = appString(R.string.pip_lines_nothing_here_yet_let_s)
    val LIBRARY_EMPTY: String get() = appString(R.string.pip_lines_a_clean_shelf_start_a)
    val LIBRARY_NO_FOLDERS: String get() = appString(R.string.pip_lines_no_folders_yet_they_re)
    val LIBRARY_NO_DOCUMENTS: String get() = appString(R.string.pip_lines_no_documents_yet_i_m)
    val FOLDER_EMPTY: String get() = appString(R.string.pip_lines_this_folder_is_waiting_for)
    val DOCUMENT_NO_PAGES: String get() = appString(R.string.pip_lines_no_pages_yet_point_me)
    val PAGE_TEXT_EMPTY: String get() = appString(R.string.pip_lines_no_pages_means_no_words)
    val BENCHMARK_EMPTY: String get() = appString(R.string.pip_lines_pick_a_few_photos_and)
    val FAQ_EMPTY: String get() = appString(R.string.pip_lines_hmm_i_don_t_have)
    val QR_HISTORY_EMPTY: String get() = appString(R.string.pip_lines_no_codes_yet_scan_or)
    val QR_NO_MATCHES: String get() = appString(R.string.pip_lines_nothing_matches_that_try_another)
    val SEARCH_STILL_READING: String get() = appString(R.string.pip_lines_i_m_still_reading_some)

    val CAMERA_PERMISSION: String get() = appString(R.string.pip_lines_i_can_t_see_a)
    val CAMERA_BLOCKED: String get() = appString(R.string.pip_lines_the_camera_is_switched_off)
    val QR_PERMISSION: String get() = appString(R.string.pip_lines_lend_me_the_camera_and)
    val DOCUMENT_MISSING: String get() = appString(R.string.pip_lines_huh_that_document_wandered_off)

    fun searchNoResults(query: String): String {
        val shown = query.trim().let { if (it.length > 28) it.take(27) + "…" else it }
        return appString(R.string.pip_lines_i_looked_on_every_page, shown)
    }

    fun exportReady(pageCount: Int): String = when (pageCount) {
        0 -> appString(R.string.pip_lines_ready_when_you_are)
        1 -> appString(R.string.pip_lines_all_set_1_page_nice)
        else -> appString(R.string.pip_lines_all_set_pages_nice_and, pageCount)
    }

    /** Short, true tips about real features, one per day on Home. */
    val homeTips get() = listOf(
        appString(R.string.pip_lines_tip_search_finds_words_inside),
        appString(R.string.pip_lines_tip_long_press_a_page),
        appString(R.string.pip_lines_tip_open_a_document_s),
        appString(R.string.pip_lines_tip_the_tools_tab_can),
        appString(R.string.pip_lines_tip_add_a_scanly_widget),
        appString(R.string.pip_lines_tip_turn_on_the_grid),
        appString(R.string.pip_lines_tip_folders_keep_related_scans),
    )

    fun homeTipFor(epochDay: Long): String = homeTips[Math.floorMod(epochDay, homeTips.size.toLong()).toInt()]

    /** What Pip says when poked. */
    val pokes get() = listOf(
        appString(R.string.pip_lines_hey_that_tickles),
        appString(R.string.pip_lines_i_live_on_your_phone),
        appString(R.string.pip_lines_your_original_photos_are_kept),
        appString(R.string.pip_lines_i_m_open_source_you),
        appString(R.string.pip_lines_still_free_still_no_account),
        appString(R.string.pip_lines_okay_okay_i_felt_that),
    )
}
