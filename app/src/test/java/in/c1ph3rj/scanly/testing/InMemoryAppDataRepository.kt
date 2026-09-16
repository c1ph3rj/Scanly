package `in`.c1ph3rj.scanly.testing

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.AppStorageUsage
import `in`.c1ph3rj.scanly.domain.repository.AppDataRepository

class InMemoryAppDataRepository(
    private val library: InMemoryScanlyLibrary,
) : AppDataRepository {
    override suspend fun getStorageUsage(): ScanlyResult<AppStorageUsage> =
        runLibraryResult("Could not calculate storage usage.") {
            AppStorageUsage(
                documentsBytes = library.pages.value.size * 1_024L,
                exportCacheBytes = 0L,
                databaseBytes = (library.documents.value.size + library.groups.value.size).toLong(),
                archiveWorkingBytes = 0L,
            )
        }

    override suspend fun clearAllLibraryData(): ScanlyResult<Unit> =
        runLibraryResult("Could not clear app data.") {
            library.clearAll()
        }
}
