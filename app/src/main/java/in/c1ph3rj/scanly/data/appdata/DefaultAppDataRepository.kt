package `in`.c1ph3rj.scanly.data.appdata

import android.content.Context
import androidx.room.withTransaction
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.data.sign.DefaultSignatureRepository
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ui.ThumbnailCache
import `in`.c1ph3rj.scanly.data.common.LibraryOperationCoordinator
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.storage.DocumentStorageManager
import `in`.c1ph3rj.scanly.domain.model.AppStorageUsage
import `in`.c1ph3rj.scanly.domain.repository.AppDataRepository
import `in`.c1ph3rj.scanly.domain.repository.QrHistoryRepository
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAppDataRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: ScanlyDatabase,
    private val documentStorageManager: DocumentStorageManager,
    private val thumbnailCache: ThumbnailCache,
    private val dispatchers: ScanlyDispatchers,
    private val operationCoordinator: LibraryOperationCoordinator,
    private val qrHistoryRepository: QrHistoryRepository,
) : AppDataRepository {

    override suspend fun getStorageUsage(): ScanlyResult<AppStorageUsage> =
        withContext(dispatchers.io) {
            runCatching {
                AppStorageUsage(
                    documentsBytes = directorySize(documentsDirectory()),
                    exportCacheBytes = directorySize(exportCacheDirectory()) +
                        directorySize(pdfToolsCacheDirectory()) +
                        directorySize(incomingPdfDirectory()) +
                        directorySize(qrToolsCacheDirectory()),
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
            operationCoordinator.withMutation {
                runCatching {
                    database.withTransaction {
                        database.clearAllTables()
                    }
                    documentStorageManager.clearAllDocumentStorage()
                    exportCacheDirectory().deleteRecursively()
                    // PDF tool outputs and working copies, including decrypted copies of
                    // password-protected PDFs, must not survive "Clear all data".
                    pdfToolsCacheDirectory().deleteRecursively()
                    // PDFs opened from other apps are private copies of the user's files.
                    incomingPdfDirectory().deleteRecursively()
                    qrToolsCacheDirectory().deleteRecursively()
                    signatureShareDirectory().deleteRecursively()
                    DefaultSignatureRepository.signaturesRoot(context).deleteRecursively()
                    // Scanned QR contents are private history; clearing through the repository also
                    // resets its in-memory copy.
                    qrHistoryRepository.clear()
                    legacyArchiveWorkingDirectory().deleteRecursively()
                    legacyArchiveJournalDirectory().deleteRecursively()
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
        }

    override suspend fun clearExportCache(): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                exportCacheDirectory().listFiles().orEmpty().forEach { entry ->
                    entry.deleteRecursively()
                }
                // Skip recent files: a PDF tool or reader that is still open reads its working copy
                // here, and a QR code that was just shared may still be read by the receiving app.
                val cutoff = System.currentTimeMillis() - PDF_TOOLS_IN_USE_WINDOW_MILLIS
                listOf(pdfToolsCacheDirectory(), incomingPdfDirectory(), qrToolsCacheDirectory())
                    .forEach { directory ->
                        directory.listFiles().orEmpty()
                            .filter { entry -> entry.lastModified() < cutoff }
                            .forEach { entry -> entry.deleteRecursively() }
                    }
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not clear temporary exports.",
                            cause = throwable,
                        ),
                    )
                },
            )
        }

    override suspend fun deleteLegacyArchiveLeftovers(): ScanlyResult<Unit> =
        withContext(dispatchers.io) {
            // Cancel the unique work the removed library backup worker used, so an orphaned run
            // from an older app version cannot keep executing after upgrade.
            runCatching {
                WorkManager.getInstance(context)
                    .cancelUniqueWork(LEGACY_ARCHIVE_UNIQUE_WORK_NAME)
                    .result.get()
            }
            runCatching {
                legacyArchiveWorkingDirectory().deleteRecursively()
                legacyArchiveJournalDirectory().deleteRecursively()
            }.fold(
                onSuccess = { ScanlyResult.Success(Unit) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not remove old backup files.",
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

    private fun pdfToolsCacheDirectory(): File =
        File(context.cacheDir, PDF_TOOLS_CACHE_DIRECTORY)

    private fun incomingPdfDirectory(): File =
        File(context.cacheDir, INCOMING_PDF_CACHE_DIRECTORY)

    private fun qrToolsCacheDirectory(): File =
        File(context.cacheDir, QR_TOOLS_CACHE_DIRECTORY)

    private fun signatureShareDirectory(): File =
        File(context.cacheDir, SIGNATURE_SHARE_DIRECTORY)

    private fun legacyArchiveWorkingDirectory(): File =
        File(context.cacheDir, LEGACY_ARCHIVE_WORK_DIRECTORY)

    private fun legacyArchiveJournalDirectory(): File =
        File(context.filesDir, LEGACY_ARCHIVE_JOURNAL_DIRECTORY)

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
        /** Matches `DefaultPdfToolkitRepository.toolsDir()`. */
        const val PDF_TOOLS_CACHE_DIRECTORY = "pdf-tools"
        /** Matches `IncomingPdf.copyToCache()`. */
        const val INCOMING_PDF_CACHE_DIRECTORY = "incoming_pdf"
        /** Matches `DefaultQrCodeRepository`. */
        const val QR_TOOLS_CACHE_DIRECTORY = "qr-tools"
        /** Matches `DefaultSignatureRepository`. */
        const val SIGNATURE_SHARE_DIRECTORY = "signature_share"
        const val PDF_TOOLS_IN_USE_WINDOW_MILLIS = 60 * 60 * 1000L
        const val DATABASE_NAME = "scanly.db"
        val DATABASE_FILE_SUFFIXES = listOf("", "-wal", "-shm")

        // Identifiers of the removed library backup/restore feature, kept only for upgrade cleanup.
        const val LEGACY_ARCHIVE_UNIQUE_WORK_NAME = "scanly-library-archive"
        const val LEGACY_ARCHIVE_WORK_DIRECTORY = "library-archive"
        const val LEGACY_ARCHIVE_JOURNAL_DIRECTORY = "library-archive-journal"
    }
}
