package `in`.c1ph3rj.scanly.data.library

import android.net.Uri
import androidx.room.withTransaction
import `in`.c1ph3rj.scanly.data.library.manifest.CatalogRecord
import `in`.c1ph3rj.scanly.data.library.manifest.DocumentManifest
import `in`.c1ph3rj.scanly.data.library.manifest.GroupManifest
import `in`.c1ph3rj.scanly.data.library.manifest.LibraryCatalog
import `in`.c1ph3rj.scanly.data.library.manifest.LibraryManifestStore
import `in`.c1ph3rj.scanly.data.library.manifest.LibraryMarker
import `in`.c1ph3rj.scanly.data.library.manifest.StoredManifest
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentGroupDao
import `in`.c1ph3rj.scanly.data.local.db.dao.LibraryStateDao
import `in`.c1ph3rj.scanly.data.local.db.dao.ScanPageDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentGroupEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.LibraryStateEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ManifestFingerprintEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryIndexSynchronizer @Inject constructor(
    private val database: ScanlyDatabase,
    private val documentDao: DocumentDao,
    private val pageDao: ScanPageDao,
    private val groupDao: DocumentGroupDao,
    private val stateDao: LibraryStateDao,
    private val manifestStore: LibraryManifestStore,
    private val fileSystem: SharedLibraryFileSystem,
) {
    suspend fun synchronize(treeUri: Uri, marker: LibraryMarker, forceRebuild: Boolean = false): LibraryCatalog {
        var catalog = runCatching { manifestStore.readLatestCatalog(treeUri).value }.getOrNull()
        val operations = manifestStore.listOperations(treeUri)
        if (catalog == null || operations.isNotEmpty()) {
            catalog = rebuildCatalog(treeUri, marker, catalog, operations.maxOfOrNull { it.catalogGeneration })
            operations.forEach { operation -> runCatching { manifestStore.removeOperation(treeUri, operation.id) } }
        }

        val state = stateDao.getState()
        val rebuild = forceRebuild || state == null || state.libraryId != marker.libraryId
        if (!rebuild && state.appliedGeneration == catalog.generation) return catalog

        val fingerprints = stateDao.getFingerprints().associateBy { "${it.recordType}:${it.recordId}" }
        val activeGroups = catalog.groups.associateBy(CatalogRecord::id)
        val changedGroups = catalog.groups.mapNotNull { record ->
            val cached = fingerprints["group:${record.id}"]
            if (cached?.revision == record.revision && cached.checksum != record.checksum) {
                error("Conflicting group manifest detected for ${record.id}.")
            }
            if (rebuild || cached?.revision != record.revision || cached.checksum != record.checksum) {
                manifestStore.readLatestGroup(treeUri, record.id)
                    ?: error("Missing group manifest for ${record.id}.")
            } else null
        }
        val changedDocuments = catalog.documents.mapNotNull { record ->
            val cached = fingerprints["document:${record.id}"]
            if (cached?.revision == record.revision && cached.checksum != record.checksum) {
                error("Conflicting document manifest detected for ${record.id}.")
            }
            if (rebuild || cached?.revision != record.revision || cached.checksum != record.checksum) {
                manifestStore.readLatestDocument(treeUri, record.id)
                    ?: error("Missing document manifest for ${record.id}.")
            } else null
        }.map { stored -> stored.copy(value = normalizeMissingAssets(treeUri, stored.value)) }

        database.withTransaction {
            if (rebuild) database.clearAllTables()

            val catalogGroupIds = catalog.groups.map(CatalogRecord::id).toSet()
            groupDao.getAllIds().filterNot(catalogGroupIds::contains).forEach { groupDao.deleteById(it) }
            changedGroups.forEach { stored -> groupDao.upsert(stored.toEntity()) }

            val catalogDocumentIds = catalog.documents.map(CatalogRecord::id).toSet()
            documentDao.getAllIds().filterNot(catalogDocumentIds::contains).forEach { documentDao.deleteById(it) }
            changedDocuments.forEach { stored ->
                val normalized = stored.value.copy(
                    groupId = stored.value.groupId?.takeIf(activeGroups::containsKey),
                )
                documentDao.upsert(normalized.toEntity(stored.checksum))
                pageDao.deleteByDocumentId(normalized.id)
                pageDao.upsertAll(normalized.pages.map { page -> page.toEntity(normalized.id) })
            }

            stateDao.clearFingerprints()
            stateDao.putFingerprints(
                catalog.documents.map { it.toFingerprint("document") } +
                    catalog.groups.map { it.toFingerprint("group") },
            )
            stateDao.putState(
                LibraryStateEntity(
                    libraryId = marker.libraryId,
                    appliedGeneration = catalog.generation,
                    lastSynchronizedAtMillis = System.currentTimeMillis(),
                    healthState = "READY",
                ),
            )
        }
        return catalog
    }

    suspend fun clearIndex() = database.withTransaction { database.clearAllTables() }

    private suspend fun rebuildCatalog(
        treeUri: Uri,
        marker: LibraryMarker,
        previous: LibraryCatalog?,
        requestedGeneration: Long?,
    ): LibraryCatalog {
        val documents = manifestStore.scanDocuments(treeUri)
        val groups = manifestStore.scanGroups(treeUri)
        val tombstones = manifestStore.scanTombstones(treeUri)
        val deletedDocuments = tombstones.filter { it.type == "document" }.map { it.id }.toSet()
        val deletedGroups = tombstones.filter { it.type == "group" }.map { it.id }.toSet()
        val generation = maxOf(previous?.generation?.plus(1) ?: 1L, requestedGeneration ?: 1L)
        return LibraryCatalog(
            libraryId = marker.libraryId,
            generation = generation,
            documents = documents.filterNot { it.value.id in deletedDocuments }
                .map { CatalogRecord(it.value.id, it.value.revision, it.checksum) },
            groups = groups.filterNot { it.value.id in deletedGroups }
                .map { CatalogRecord(it.value.id, it.value.revision, it.checksum) },
            tombstones = (previous?.tombstones.orEmpty() + tombstones)
                .distinctBy { "${it.type}:${it.id}" },
        ).also { manifestStore.writeCatalog(treeUri, it) }
    }

    private fun StoredManifest<GroupManifest>.toEntity() = DocumentGroupEntity(
        id = value.id,
        title = value.title,
        createdAtMillis = value.createdAtMillis,
        updatedAtMillis = value.updatedAtMillis,
        revision = value.revision,
        manifestChecksum = checksum,
    )

    private fun DocumentManifest.toEntity(checksum: String) = DocumentEntity(
        id = id,
        title = title,
        pageCount = pages.size,
        coverThumbnail = pages.firstOrNull()?.thumbnailAsset,
        preferredFilterPreset = preferredFilterPreset,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        groupId = groupId,
        revision = revision,
        manifestChecksum = checksum,
    )

    private fun `in`.c1ph3rj.scanly.data.library.manifest.PageManifest.toEntity(documentId: String) = ScanPageEntity(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        rawAsset = rawAsset,
        processedAsset = processedAsset,
        thumbnailAsset = thumbnailAsset,
        rotationDegrees = rotationDegrees,
        cropTopLeftX = crop?.topLeftX,
        cropTopLeftY = crop?.topLeftY,
        cropTopRightX = crop?.topRightX,
        cropTopRightY = crop?.topRightY,
        cropBottomRightX = crop?.bottomRightX,
        cropBottomRightY = crop?.bottomRightY,
        cropBottomLeftX = crop?.bottomLeftX,
        cropBottomLeftY = crop?.bottomLeftY,
        filterPreset = filterPreset,
        processingState = processingState,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private fun CatalogRecord.toFingerprint(type: String) = ManifestFingerprintEntity(
        recordType = type,
        recordId = id,
        revision = revision,
        checksum = checksum,
    )

    private suspend fun normalizeMissingAssets(treeUri: Uri, manifest: DocumentManifest): DocumentManifest =
        manifest.copy(
            pages = manifest.pages.map { page ->
                val raw = page.rawAsset?.takeIf { fileSystem.stat(treeUri, it.relativePath) != null }
                val processed = page.processedAsset?.takeIf { fileSystem.stat(treeUri, it.relativePath) != null }
                val thumbnail = page.thumbnailAsset?.takeIf { fileSystem.stat(treeUri, it.relativePath) != null }
                page.copy(
                    rawAsset = raw,
                    processedAsset = processed,
                    thumbnailAsset = thumbnail ?: processed ?: raw,
                    processingState = if (raw == null && processed != null) "needs_review" else page.processingState,
                )
            },
        )
}
