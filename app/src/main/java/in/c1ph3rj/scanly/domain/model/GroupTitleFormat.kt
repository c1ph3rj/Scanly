package `in`.c1ph3rj.scanly.domain.model

import androidx.annotation.StringRes
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.R

enum class GroupTitleFormat(
    @param:StringRes val labelRes: Int,
    @param:StringRes val shortLabelRes: Int,
    val isDefault: Boolean = false,
) {
    FolderDateTime(
        labelRes = R.string.group_title_format_folder_with_date_and_time,
        shortLabelRes = R.string.group_title_format_folder_date_time,
        isDefault = true,
    ),
    NewFolderDateTime(
        labelRes = R.string.group_title_format_new_folder_with_date_and,
        shortLabelRes = R.string.group_title_format_new_folder_date_time,
    ),
    FolderDate(
        labelRes = R.string.group_title_format_folder_with_date,
        shortLabelRes = R.string.group_title_format_folder_date,
    ),
    FolderIsoDate(
        labelRes = R.string.group_title_format_folder_with_iso_date,
        shortLabelRes = R.string.group_title_format_folder_yyyy_mm_dd,
    ),
    ;

    val label: String get() = appString(labelRes)
    val shortLabel: String get() = appString(shortLabelRes)


    fun next(): GroupTitleFormat {
        val all = entries
        return all[(all.indexOf(this) + 1) % all.size]
    }

    companion object {
        val default: GroupTitleFormat = entries.first { it.isDefault }
    }
}