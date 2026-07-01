package `in`.c1ph3rj.scanly.domain.model

enum class LibraryStartupStatus {
    CHECKING,
    STORAGE_SETUP_REQUIRED,
    RECONNECT_REQUIRED,
    RECOVERING_OPERATIONS,
    REBUILDING_DATABASE,
    APPLYING_DELTA,
    READY,
    READ_ONLY,
    REPAIR_REQUIRED,
    UNSUPPORTED_LIBRARY_VERSION,
}

data class LibraryAccessState(
    val status: LibraryStartupStatus = LibraryStartupStatus.CHECKING,
    val libraryId: String? = null,
    val displayName: String? = null,
    val displayPath: String? = null,
    val message: String? = null,
)

