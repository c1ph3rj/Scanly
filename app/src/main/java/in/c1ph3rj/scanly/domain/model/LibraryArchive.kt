package `in`.c1ph3rj.scanly.domain.model

enum class RestoreMode {
    REPLACE,
    MERGE,
}

data class BackupEstimate(
    val sourceBytes: Long,
    val requiredBytes: Long,
    val availableBytes: Long?,
    val documentCount: Int,
    val pageCount: Int,
    val destinationLabel: String,
    val canBackup: Boolean,
    val reason: String? = null,
)

enum class ArchiveOperation {
    BACKUP,
    RESTORE,
}

enum class ArchiveWorkPhase {
    IDLE,
    QUEUED,
    VALIDATING,
    ARCHIVING,
    RESTORING,
    FINALIZING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class ArchiveWorkState(
    val operation: ArchiveOperation? = null,
    val phase: ArchiveWorkPhase = ArchiveWorkPhase.IDLE,
    val current: Int = 0,
    val total: Int = 0,
    val message: String? = null,
    val isRunning: Boolean = false,
    val canCancel: Boolean = false,
)
