package `in`.c1ph3rj.scanly.navigation

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

    data object Readiness : ScanlyDestination(
        route = "readiness",
        title = "Readiness",
        summary = "On-device ML diagnostics, tensor contracts, and validation benchmarks stay accessible as a permanent tool.",
        sprintLabel = "Sprint 0",
    )
}
