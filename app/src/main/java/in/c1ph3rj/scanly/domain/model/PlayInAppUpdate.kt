package `in`.c1ph3rj.scanly.domain.model

enum class PlayInAppUpdateType {
    FLEXIBLE,
    IMMEDIATE,
}

enum class PlayInstallStatus {
    UNKNOWN,
    PENDING,
    DOWNLOADING,
    DOWNLOADED,
    INSTALLING,
    INSTALLED,
    FAILED,
    CANCELED,
}

data class PlayInAppUpdateInstallState(
    val status: PlayInstallStatus,
    val bytesDownloaded: Long = 0L,
    val totalBytesToDownload: Long = 0L,
) {
    val downloadProgress: Float?
        get() = if (
            status == PlayInstallStatus.DOWNLOADING &&
            totalBytesToDownload > 0L
        ) {
            (bytesDownloaded.toFloat() / totalBytesToDownload.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
}

data class PlayInAppUpdateAvailability(
    val updateAvailable: Boolean,
    val availableVersionCode: Int?,
    val updatePriority: Int,
    val stalenessDays: Int?,
    val recommendedUpdateType: PlayInAppUpdateType?,
    val installStatus: PlayInstallStatus?,
    val developerTriggeredUpdateInProgress: Boolean,
)