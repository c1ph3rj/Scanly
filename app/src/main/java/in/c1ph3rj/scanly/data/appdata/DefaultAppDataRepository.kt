package `in`.c1ph3rj.scanly.data.appdata

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ui.ThumbnailCache
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.storage.DocumentStorageManager
import `in`.c1ph3rj.scanly.domain.model.AppStorageUsage
import `in`.c1ph3rj.scanly.domain.repository.AppDataRepository
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAppDataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ScanlyDatabase,
    private val documentStorageManager: DocumentStorageManager,
    private val thumbnailCache: ThumbnailCache,
    private val dispatchers: ScanlyDispatchers,
) : AppDataRepository {

    override suspend fun getStorageUsage(): ScanlyResult<AppStorageUsage> =
        withContext(dispatchers.io) {
            runCatching {
                AppStorageUsage(
                    documentsBytes = directorySize(documentsDirectory()),
                    exportCacheBytes = directorySize(exportCacheDirectory()),
                    databaseBytes = databaseFileSize(),
                )
            }.fold(
                onSuccess = { ScanlyResult.Success(it) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not calculate storage usage.",
                            cause = throwable,
                        ),
                    )
                },
            )
        }

    override suspend fun clearAllLibraryData(): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                database.withTransaction {
                    database.clearAllTables()
                }
                documentStorageManager.clearAllDocumentStorage()
                exportCacheDirectory().deleteRecursively()
                thumbnailCache.clearAll()
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not clear app data.",
                            cause = throwable,
                        ),
                    )
                },
            )
        }

    private fun documentsDirectory(): File =
        File(context.filesDir, DOCUMENTS_DIRECTORY)

    private fun exportCacheDirectory(): File =
        File(context.cacheDir, EXPORT_CACHE_DIRECTORY)

    private fun databaseFileSize(): Long {
        val databaseDirectory = context.getDatabasePath(DATABASE_NAME).parentFile ?: return 0L
        return DATABASE_FILE_SUFFIXES
            .map { suffix -> File(databaseDirectory, DATABASE_NAME + suffix) }
            .filter { file -> file.exists() }
            .sumOf { file -> file.length() }
    }

    private fun directorySize(directory: File): Long {
        if (!directory.exists()) {
            return 0L
        }
        return directory.walkTopDown()
            .filter { file -> file.isFile }
            .sumOf { file -> file.length() }
    }

    private companion object {
        const val DOCUMENTS_DIRECTORY = "documents"
        const val EXPORT_CACHE_DIRECTORY = "exports"
        const val DATABASE_NAME = "scanly.db"
        val DATABASE_FILE_SUFFIXES = listOf("", "-wal", "-shm")
    }
}
