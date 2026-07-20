package `in`.c1ph3rj.scanly.data.archive

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.BuildConfig
import `in`.c1ph3rj.scanly.core.ui.ThumbnailCache
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentGroupDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity
import `in`.c1ph3rj.scanly.data.storage.SharedStorageDestinationManager
import `in`.c1ph3rj.scanly.domain.model.ArchiveWorkPhase
import `in`.c1ph3rj.scanly.domain.model.BackupEstimate
import `in`.c1ph3rj.scanly.domain.model.RestoreMode
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

typealias ArchiveProgressCallback = suspend (ArchiveWorkPhase, Int, Int, String) -> Unit

@Singleton
class LibraryArchiveEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: ScanlyDatabase,
    private val documentDao: DocumentDao,
    private val groupDao: DocumentGroupDao,
    private val pageDao: ScanPageDao,
    private val destinationManager: SharedStorageDestinationManager,
    private val operationCoordinator: LibraryOperationCoordinator,
    private val thumbnailCache: ThumbnailCache,
) {
    private val backupTimeFormat = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

    suspend fun estimateBackup(): BackupEstimate {
        val snapshot = loadSnapshot(calculateHashes = false)
        val capacity = destinationManager.inspectBackupCapacity()
        val requiredBytes = LibraryArchivePolicy.backupRequiredBytes(snapshot.sourceBytes)
        val hasLibrary = snapshot.documents.isNotEmpty() || snapshot.groups.isNotEmpty()
        val reason = when {
            !hasLibrary -> "There is no library data to back up."
            !capacity.backupDirectoryReady -> capacity.errorMessage ?: "The backup folder is unavailable."
            !LibraryArchivePolicy.hasSufficientBackupCapacity(requiredBytes, capacity.availableBytes) ->
                "Not enough free space in ${capacity.destination.backupLabel}."
            else -> null
        }
        return BackupEstimate(
            sourceBytes = snapshot.sourceBytes,
            requiredBytes = requiredBytes,
            availableBytes = capacity.availableBytes,
            documentCount = snapshot.documents.size,
            pageCount = snapshot.pages.size,
            destinationLabel = capacity.destination.backupLabel,
            canBackup = reason == null,
            reason = reason,
        )
    }

    suspend fun createBackup(progress: ArchiveProgressCallback): String =
        operationCoordinator.withMaintenance("Library backup is in progress.") {
            progress(ArchiveWorkPhase.VALIDATING, 0, 0, "Checking library and storage")
            val estimate = estimateBackup()
            check(estimate.canBackup) { estimate.reason ?: "Backup is not available." }
            val snapshot = loadSnapshot(calculateHashes = true) { current, total ->
                progress(ArchiveWorkPhase.VALIDATING, current, total, "Checking library files")
            }
            val capacity = destinationManager.inspectBackupCapacity()
            check(capacity.backupDirectoryReady) {
                capacity.errorMessage ?: "The backup folder is no longer available."
            }
            check(LibraryArchivePolicy.hasSufficientBackupCapacity(estimate.requiredBytes, capacity.availableBytes)) {
                "Available storage changed. Free up space and try again."
            }

            val fileName = "Scanly-library-${LocalDateTime.now().format(backupTimeFormat)}.scanly"
            val saved = destinationManager.writeBackup(fileName) { output ->
                ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                    val manifest = snapshot.toJson()
                    zip.putNextEntry(ZipEntry(LibraryArchiveConstants.MANIFEST_ENTRY))
                    zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    snapshot.assets.forEachIndexed { index, asset ->
                        coroutineContext.ensureActive()
                        progress(
                            ArchiveWorkPhase.ARCHIVING,
                            index + 1,
                            snapshot.assets.size,
                            "Adding library files",
                        )
                        zip.putNextEntry(ZipEntry(asset.archivePath))
                        asset.source.inputStream().buffered().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            saved.fileName
        }

    suspend fun restore(
        sourceUri: Uri,
        mode: RestoreMode,
        workId: String,
        progress: ArchiveProgressCallback,
    ) {
        operationCoordinator.withMaintenance("Library restore is in progress.") {
            recoverInterruptedRestores()
            progress(ArchiveWorkPhase.VALIDATING, 0, 0, "Validating backup")
            val stagingDirectory = File(
                context.cacheDir,
                "${LibraryArchiveConstants.ARCHIVE_WORK_DIRECTORY}/restore-$workId",
            )
            stagingDirectory.deleteRecursively()
            check(stagingDirectory.mkdirs()) { "Could not create restore workspace." }
            try {
                val manifest = extractAndValidate(sourceUri, stagingDirectory, progress)
                installManifest(manifest, stagingDirectory, mode, workId, progress)
            } finally {
                stagingDirectory.deleteRecursively()
            }
        }
    }

    private suspend fun loadSnapshot(
        calculateHashes: Boolean,
        onHashProgress: suspend (Int, Int) -> Unit = { _, _ -> },
    ): LibrarySnapshot {
        val groups = groupDao.getAll()
        val documents = documentDao.getAll()
        val pages = pageDao.getAll()
        val documentsById = documents.associateBy { it.id }
        val assetSources = linkedMapOf<String, File>()

        fun register(documentId: String, path: String?): String? {
            if (path == null) return null
            val document = documentsById[documentId] ?: error("Page references an unknown document.")
            val root = File(document.rootDirectoryPath).canonicalFile
            val source = File(path).canonicalFile
            check(source.isFile && source.length() > 0L) { "A library file is missing: ${source.name}" }
            val relative = source.relativeToOrNull(root)?.invariantSeparatorsPath
                ?: error("A document file is outside Scanly storage.")
            check(!relative.startsWith("../") && relative != "..") { "Invalid library file path." }
            val archivePath = "documents/${document.id}/$relative"
            assetSources[archivePath] = source
            return archivePath
        }

        val documentRecords = documents.map { document ->
            ArchiveDocument(
                id = document.id,
                title = document.title,
                pageCount = document.pageCount,
                coverPath = register(document.id, document.coverThumbnailPath),
                preferredFilterPreset = document.preferredFilterPreset,
                createdAtMillis = document.createdAtMillis,
                updatedAtMillis = document.updatedAtMillis,
                groupId = document.groupId,
            )
        }
        val pageRecords = pages.map { page ->
            ArchivePage(
                id = page.id,
                documentId = page.documentId,
                pageIndex = page.pageIndex,
                rawPath = register(page.documentId, page.rawImagePath),
                processedPath = register(page.documentId, page.processedImagePath),
                thumbnailPath = register(page.documentId, page.thumbnailPath),
                rotationDegrees = page.rotationDegrees,
                cropTopLeftX = page.cropTopLeftX,
                cropTopLeftY = page.cropTopLeftY,
                cropTopRightX = page.cropTopRightX,
                cropTopRightY = page.cropTopRightY,
                cropBottomRightX = page.cropBottomRightX,
                cropBottomRightY = page.cropBottomRightY,
                cropBottomLeftX = page.cropBottomLeftX,
                cropBottomLeftY = page.cropBottomLeftY,
                filterPreset = page.filterPreset,
                filterBrightness = page.filterBrightness,
                filterContrast = page.filterContrast,
                filterSaturation = page.filterSaturation,
                filterSharpness = page.filterSharpness,
                processingState = page.processingState,
                createdAtMillis = page.createdAtMillis,
                updatedAtMillis = page.updatedAtMillis,
            )
        }
        val assets = assetSources.entries.mapIndexed { index, (archivePath, source) ->
            onHashProgress(index + 1, assetSources.size)
            ArchiveAsset(
                archivePath = archivePath,
                source = source,
                size = source.length(),
                sha256 = if (calculateHashes) sha256(source) else "",
            )
        }
        return LibrarySnapshot(
            groups = groups,
            documents = documentRecords,
            pages = pageRecords,
            assets = assets,
        )
    }

    private suspend fun extractAndValidate(
        sourceUri: Uri,
        stagingDirectory: File,
        progress: ArchiveProgressCallback,
    ): ArchiveManifest {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(sourceUri) ?: error("Could not open the selected backup.")
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            val firstEntry = zip.nextEntry ?: error("This file is not a valid Scanly backup.")
            check(firstEntry.name == LibraryArchiveConstants.MANIFEST_ENTRY) { "This is not a Scanly backup." }
            val manifestBytes = readCurrentEntry(zip, LibraryArchiveConstants.MAX_MANIFEST_BYTES)
            val manifest = ArchiveManifest.fromJson(JSONObject(String(manifestBytes, Charsets.UTF_8)))
            zip.closeEntry()
            manifest.validate()

            val requiredBytes = LibraryArchivePolicy.restoreRequiredBytes(manifest.sourceBytes)
            check(context.cacheDir.usableSpace >= requiredBytes) {
                "Not enough app storage to restore this backup."
            }

            val expectedAssets = manifest.assets.associateBy { it.archivePath }
            val extracted = mutableSetOf<String>()
            var entry = zip.nextEntry
            var current = 0
            while (entry != null) {
                coroutineContext.ensureActive()
                check(!entry.isDirectory) { "Unexpected directory in backup." }
                val asset = expectedAssets[entry.name] ?: error("Unexpected file in backup: ${entry.name}")
                check(extracted.add(entry.name)) { "Duplicate file in backup: ${entry.name}" }
                current++
                progress(
                    ArchiveWorkPhase.VALIDATING,
                    current,
                    expectedAssets.size,
                    "Checking backup files",
                )
                val target = safeArchiveFile(stagingDirectory, asset.archivePath)
                target.parentFile?.mkdirs()
                val digest = MessageDigest.getInstance(LibraryArchiveConstants.SHA_256)
                var written = 0L
                FileOutputStream(target).buffered().use { output ->
                    val buffer = ByteArray(LibraryArchiveConstants.BUFFER_SIZE)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        written += count
                        check(written <= asset.size) { "Backup file exceeds its declared size." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
                check(written == asset.size) { "Backup file size does not match its manifest." }
                check(digest.digest().toHex() == asset.sha256) { "Backup file checksum failed." }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            check(extracted == expectedAssets.keys) { "Backup is missing one or more library files." }
            return manifest
        }
    }

    private suspend fun installManifest(
        manifest: ArchiveManifest,
        stagingDirectory: File,
        mode: RestoreMode,
        workId: String,
        progress: ArchiveProgressCallback,
    ) {
        progress(ArchiveWorkPhase.RESTORING, 0, manifest.documents.size, "Preparing restored library")
        val currentDocuments = documentDao.getAll()
        val currentGroups = groupDao.getAll()
        val groupIds = manifest.groups.associate { it.id to UUID.randomUUID().toString() }
        val documentIds = manifest.documents.associate { it.id to UUID.randomUUID().toString() }
        val pageIds = manifest.pages.associate { it.id to UUID.randomUUID().toString() }
        val newRootsByOldId = documentIds.mapValues { (_, newId) ->
            File(context.filesDir, "documents/$newId")
        }
        val newRoots = newRootsByOldId.values.toList()
        val oldRoots = if (mode == RestoreMode.REPLACE) {
            currentDocuments.map { File(it.rootDirectoryPath) }
        } else {
            emptyList()
        }
        val journal = RestoreJournal.create(
            context,
            workId,
            oldRoots,
            newRoots,
            documentIds.values,
            groupIds.values,
        )
        var committed = false
        val usedGroupTitles = if (mode == RestoreMode.MERGE) {
            currentGroups.mapTo(mutableSetOf()) { it.title.lowercase() }
        } else {
            mutableSetOf()
        }
        val usedDocumentTitles = if (mode == RestoreMode.MERGE) {
            currentDocuments.mapTo(mutableSetOf()) { it.title.lowercase() }
        } else {
            mutableSetOf()
        }
        try {
            val restoredGroups = manifest.groups.map { group ->
                DocumentGroupEntity(
                    id = groupIds.getValue(group.id),
                    title = LibraryArchivePolicy.uniqueRestoredTitle(group.title, usedGroupTitles),
                    createdAtMillis = group.createdAtMillis,
                    updatedAtMillis = group.updatedAtMillis,
                )
            }
            val restoredDocuments = manifest.documents.mapIndexed { index, document ->
                coroutineContext.ensureActive()
                progress(
                    ArchiveWorkPhase.RESTORING,
                    index + 1,
                    manifest.documents.size,
                    "Copying restored documents",
                )
                val newId = documentIds.getValue(document.id)
                val newRoot = newRootsByOldId.getValue(document.id)
                check(!newRoot.exists()) { "Restore destination already exists." }
                val stagedRoot = safeArchiveFile(stagingDirectory, "documents/${document.id}")
                if (stagedRoot.exists()) {
                    check(stagedRoot.copyRecursively(newRoot, overwrite = false)) {
                        "Could not copy restored document files."
                    }
                } else {
                    check(newRoot.mkdirs()) { "Could not create restored document storage." }
                }
                DocumentEntity(
                    id = newId,
                    title = LibraryArchivePolicy.uniqueRestoredTitle(document.title, usedDocumentTitles),
                    pageCount = document.pageCount,
                    coverThumbnailPath = restorePath(document.coverPath, document.id, newRoot),
                    preferredFilterPreset = document.preferredFilterPreset,
                    rootDirectoryPath = newRoot.absolutePath,
                    createdAtMillis = document.createdAtMillis,
                    updatedAtMillis = document.updatedAtMillis,
                    groupId = document.groupId?.let(groupIds::getValue),
                )
            }
            val restoredPages = manifest.pages.map { page ->
                val newRoot = newRootsByOldId.getValue(page.documentId)
                ScanPageEntity(
                    id = pageIds.getValue(page.id),
                    documentId = documentIds.getValue(page.documentId),
                    pageIndex = page.pageIndex,
                    rawImagePath = restorePath(page.rawPath, page.documentId, newRoot),
                    processedImagePath = restorePath(page.processedPath, page.documentId, newRoot),
                    thumbnailPath = restorePath(page.thumbnailPath, page.documentId, newRoot),
                    rotationDegrees = page.rotationDegrees,
                    cropTopLeftX = page.cropTopLeftX,
                    cropTopLeftY = page.cropTopLeftY,
                    cropTopRightX = page.cropTopRightX,
                    cropTopRightY = page.cropTopRightY,
                    cropBottomRightX = page.cropBottomRightX,
                    cropBottomRightY = page.cropBottomRightY,
                    cropBottomLeftX = page.cropBottomLeftX,
                    cropBottomLeftY = page.cropBottomLeftY,
                    filterPreset = page.filterPreset,
                    filterBrightness = page.filterBrightness,
                    filterContrast = page.filterContrast,
                    filterSaturation = page.filterSaturation,
                    filterSharpness = page.filterSharpness,
                    processingState = page.processingState,
                    createdAtMillis = page.createdAtMillis,
                    updatedAtMillis = page.updatedAtMillis,
                )
            }
            progress(ArchiveWorkPhase.FINALIZING, 0, 1, "Updating library")
            database.withTransaction {
                if (mode == RestoreMode.REPLACE) database.clearAllTables()
                if (restoredGroups.isNotEmpty()) groupDao.insertAll(restoredGroups)
                if (restoredDocuments.isNotEmpty()) documentDao.insertAll(restoredDocuments)
                if (restoredPages.isNotEmpty()) pageDao.insertAll(restoredPages)
            }
            committed = true
            journal.markCommitted()
            oldRoots.forEach { it.deleteRecursively() }
            thumbnailCache.clearAll()
            journal.delete()
        } finally {
            if (!committed) {
                newRoots.forEach { it.deleteRecursively() }
                journal.delete()
            }
        }
    }

    private suspend fun recoverInterruptedRestores() {
        val journalDirectory = File(context.filesDir, LibraryArchiveConstants.ARCHIVE_JOURNAL_DIRECTORY)
        journalDirectory.listFiles { file -> file.name.startsWith("restore-journal-") }
            .orEmpty()
            .forEach { journalFile ->
                runCatching {
                    val journal = RestoreJournal.read(journalFile)
                    val installedIds = documentDao.getAll().mapTo(hashSetOf()) { it.id }
                    val installedGroupIds = groupDao.getAll().mapTo(hashSetOf()) { it.id }
                    val committed = journal.committed ||
                        journal.newDocumentIds.any(installedIds::contains) ||
                        journal.newGroupIds.any(installedGroupIds::contains)
                    if (committed) {
                        journal.oldRoots.forEach { it.deleteRecursively() }
                    } else {
                        journal.newRoots.forEach { it.deleteRecursively() }
                    }
                    journal.delete()
                }
            }
    }


    private data class LibrarySnapshot(
        val groups: List<DocumentGroupEntity>,
        val documents: List<ArchiveDocument>,
        val pages: List<ArchivePage>,
        val assets: List<ArchiveAsset>,
    ) {
        val sourceBytes: Long = assets.sumOf { it.size }

        fun toJson(): JSONObject = ArchiveManifest(
            createdAtMillis = System.currentTimeMillis(),
            sourceAppVersion = BuildConfig.VERSION_NAME,
            groups = groups.map {
                ArchiveGroup(it.id, it.title, it.createdAtMillis, it.updatedAtMillis)
            },
            documents = documents,
            pages = pages,
            assets = assets.map { ArchiveAssetRecord(it.archivePath, it.size, it.sha256) },
        ).toJson()
    }

    private data class ArchiveAsset(
        val archivePath: String,
        val source: File,
        val size: Long,
        val sha256: String,
    )
}
