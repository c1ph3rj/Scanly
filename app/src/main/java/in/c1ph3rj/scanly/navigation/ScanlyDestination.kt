package `in`.c1ph3rj.scanly.navigation

import `in`.c1ph3rj.scanly.feature.settings.LegalDocumentType

sealed class ScanlyDestination(
    val route: String,
    val title: String,
    val summary: String,
    val sprintLabel: String,
) {
    data object Home : ScanlyDestination(
        route = "home",
        title = "Home",
        summary = "Your local document library and scanning workspace.",
        sprintLabel = "Sprint 2",
    )

    data object Library : ScanlyDestination(
        route = "library",
        title = "Library",
        summary = "Document collections, persistence, and generated thumbnails live here.",
        sprintLabel = "Sprint 2",
    )

    data object Camera : ScanlyDestination(
        route = "camera",
        title = "Camera",
        summary = "Manual capture becomes the first truly useful scanning workflow after the shell.",
        sprintLabel = "Sprint 3",
    )

    data object Review : ScanlyDestination(
        route = "review",
        title = "Review",
        summary = "Multi-page review and document assembly are planned after scanning and editing mature.",
        sprintLabel = "Sprint 7",
    )

    data object Editor : ScanlyDestination(
        route = "editor",
        title = "Editor",
        summary = "The 4-point cropper and page adjustments live here once the processing pipeline is ready.",
        sprintLabel = "Sprint 6",
    )

    data object Settings : ScanlyDestination(
        route = "settings",
        title = "Settings",
        summary = "Appearance, FAQs, licensing, and developer links live here.",
        sprintLabel = "Sprint 8",
    )
}

/** Route helpers for screens that accept typed arguments. */
object LegalDocumentDestination {
    private const val base = "legal"
    const val typeArgument = "documentType"
    const val routePattern = "$base/{$typeArgument}"

    fun route(documentType: LegalDocumentType): String =
        "$base/${documentType.name}"
}

object GroupDetailDestination {
    private const val base = "group"
    const val groupIdArgument = "groupId"
    const val routePattern = "$base/{$groupIdArgument}"

    fun route(groupId: String): String = "$base/$groupId"
}
