package `in`.c1ph3rj.scanly.testing

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.AppStorageUsage
import `in`.c1ph3rj.scanly.domain.repository.AppDataRepository

class InMemoryAppDataRepository(
    private val library: InMemoryScanlyLibrary,
) : AppDataRepository {
    var exportCacheBytes: Long = 0L
        private set
    var exportCacheClears: Int = 0
        private set

    override suspend fun getStorageUsage(): ScanlyResult<AppStorageUsage> =
        runLibraryResult("Could not calculate storage usage.") {
            AppStorageUsage(
                documentsBytes = library.pages.value.size * 1_024L,
                exportCacheBytes = exportCacheBytes,
                databaseBytes = (library.documents.value.size + library.groups.value.size).toLong(),
            )
        }

    override suspend fun clearAllLibraryData(): ScanlyResult<Unit> =
        runLibraryResult("Could not clear app data.") {
            library.clearAll()
        }

    override suspend fun clearExportCache(): ScanlyResult<Unit> =
        runLibraryResult("Could not clear temporary exports.") {
            exportCacheBytes = 0L
            exportCacheClears++
        }

    override suspend fun deleteLegacyArchiveLeftovers(): ScanlyResult<Unit> =
        runLibraryResult("Could not remove old backup files.") {}
}
