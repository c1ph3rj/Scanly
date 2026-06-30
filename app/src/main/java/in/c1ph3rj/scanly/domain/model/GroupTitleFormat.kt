package `in`.c1ph3rj.scanly.domain.model

enum class GroupTitleFormat(
    val label: String,
    val shortLabel: String,
    val isDefault: Boolean = false,
) {
    FolderDateTime(
        label = "Folder with date and time",
        shortLabel = "Folder · date & time",
        isDefault = true,
    ),
    NewFolderDateTime(
        label = "New folder with date and time",
        shortLabel = "New folder · date & time",
    ),
    FolderDate(
        label = "Folder with date",
        shortLabel = "Folder · date",
    ),
    FolderIsoDate(
        label = "Folder with ISO date",
        shortLabel = "Folder · YYYY-MM-DD",
    ),
    ;

    fun next(): GroupTitleFormat {
        val all = entries
        return all[(all.indexOf(this) + 1) % all.size]
    }

    companion object {
        val default: GroupTitleFormat = entries.first { it.isDefault }
    }
}