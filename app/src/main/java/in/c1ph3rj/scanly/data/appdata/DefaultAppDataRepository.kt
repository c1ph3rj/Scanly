package `in`.c1ph3rj.scanly.data.appdata

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ui.ThumbnailCache
import `in`.c1ph3rj.scanly.data.library.DocumentAssetReader
import `in`.c1ph3rj.scanly.data.library.LibrarySession
import `in`.c1ph3rj.scanly.data.library.SharedLibraryFileSystem
import `in`.c1ph3rj.scanly.data.library.WorkingFileStore
import `in`.c1ph3rj.scanly.data.library.manifest.LibraryCatalog
import `in`.c1ph3rj.scanly.data.library.manifest.LibraryManifestStore
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.domain.model.AppStorageUsage
import `in`.c1ph3rj.scanly.domain.repository.AppDataRepository
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAppDataRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: ScanlyDatabase,
    private val session: LibrarySession,
    private val fileSystem: SharedLibraryFileSystem,
    private val manifestStore: LibraryManifestStore,
    private val assetReader: DocumentAssetReader,
    private val workingFiles: WorkingFileStore,
    private val thumbnailCache: ThumbnailCache,
    private val dispatchers: ScanlyDispatchers,
) : AppDataRepository {
    override suspend fun getStorageUsage(): ScanlyResult<AppStorageUsage> = withContext(dispatchers.io) {
        resultOf("Could not calculate storage usage.") {
            AppStorageUsage(
                documentsBytes = fileSystem.directorySize(session.requireTreeUri()),
                exportCacheBytes = directorySize(File(context.cacheDir, "exports")),
                databaseBytes = databaseFileSize(),
            )
        }
    }

    override suspend fun clearAllLibraryData(): ScanlyResult<Unit> = withContext(dispatchers.io) {
        resultOf("Could not clear the Scanly library.") {
            val tree = session.requireTreeUri()
            val marker = session.requireMarker()
            val generation = runCatching { manifestStore.readLatestCatalog(tree).value.generation + 1L }.getOrDefault(1L)
            listOf("documents", "groups", "operations", "tombstones", "catalog").forEach { path ->
                fileSystem.delete(tree, path)
                fileSystem.ensureDirectory(tree, path)
            }
            val catalog = LibraryCatalog(libraryId = marker.libraryId, generation = generation)
            manifestStore.writeCatalog(tree, catalog)
            session.updateCatalog(catalog)
            database.withTransaction { database.clearAllTables() }
            File(context.cacheDir, "exports").deleteRecursively()
            assetReader.clearCache()
            workingFiles.clearAll()
            thumbnailCache.clearAll()
        }
    }

    override suspend fun clearTemporaryCache(): ScanlyResult<Unit> = withContext(dispatchers.io) {
        resultOf("Could not clear temporary files.") {
            File(context.cacheDir, "exports").deleteRecursively()
            assetReader.clearCache()
            workingFiles.clearAll()
            thumbnailCache.clearAll()
        }
    }

    private fun databaseFileSize(): Long {
        val directory = context.getDatabasePath(DATABASE_NAME).parentFile ?: return 0L
        return listOf("", "-wal", "-shm").sumOf { suffix ->
            File(directory, DATABASE_NAME + suffix).takeIf(File::exists)?.length() ?: 0L
        }
    }

    private fun directorySize(directory: File): Long = if (!directory.exists()) 0L else
        directory.walkTopDown().filter(File::isFile).sumOf(File::length)

    private suspend fun <T> resultOf(fallback: String, block: suspend () -> T): ScanlyResult<T> =
        runCatching { block() }.fold(
            onSuccess = { ScanlyResult.Success(it) },
            onFailure = { ScanlyResult.Failure(ScanlyError(it.message ?: fallback, it)) },
        )

    private companion object { const val DATABASE_NAME = "scanly-index.db" }
}
