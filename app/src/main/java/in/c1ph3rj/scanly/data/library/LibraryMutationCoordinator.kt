package `in`.c1ph3rj.scanly.data.library

import android.net.Uri
import `in`.c1ph3rj.scanly.data.library.manifest.CatalogRecord
import `in`.c1ph3rj.scanly.data.library.manifest.DocumentManifest
import `in`.c1ph3rj.scanly.data.library.manifest.GroupManifest
import `in`.c1ph3rj.scanly.data.library.manifest.LibraryManifestStore
import `in`.c1ph3rj.scanly.data.library.manifest.OperationJournal
import `in`.c1ph3rj.scanly.data.library.manifest.TombstoneManifest
import `in`.c1ph3rj.scanly.domain.model.LibraryAssetRef
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryMutationCoordinator @Inject constructor(
    private val session: LibrarySession,
    private val fileSystem: SharedLibraryFileSystem,
    private val manifestStore: LibraryManifestStore,
) {
    private val mutex = Mutex()

    suspend fun <T> commitDocument(
        manifest: DocumentManifest,
        operationType: String,
        roomCommit: suspend (checksum: String, generation: Long) -> T,
    ): T = mutex.withLock {
        val tree = session.requireTreeUri()
        val catalog = session.requireCatalog()
        val generation = catalog.generation + 1L
        val operationId = UUID.randomUUID().toString()
        manifestStore.writeOperation(tree, OperationJournal(operationId, operationType, manifest.id, manifest.revision, generation, System.currentTimeMillis()))
        val stored = manifestStore.writeDocument(tree, manifest)
        val updated = catalog.copy(
            generation = generation,
            documents = catalog.documents.filterNot { it.id == manifest.id } + CatalogRecord(manifest.id, manifest.revision, stored.checksum),
        )
        manifestStore.writeCatalog(tree, updated)
        session.updateCatalog(updated)
        val result = roomCommit(stored.checksum, generation)
        manifestStore.removeOperation(tree, operationId)
        result
    }

    suspend fun <T> commitGroup(
        manifest: GroupManifest,
        operationType: String,
        roomCommit: suspend (checksum: String, generation: Long) -> T,
    ): T = mutex.withLock {
        val tree = session.requireTreeUri()
        val catalog = session.requireCatalog()
        val generation = catalog.generation + 1L
        val operationId = UUID.randomUUID().toString()
        manifestStore.writeOperation(tree, OperationJournal(operationId, operationType, manifest.id, manifest.revision, generation, System.currentTimeMillis()))
        val stored = manifestStore.writeGroup(tree, manifest)
        val updated = catalog.copy(
            generation = generation,
            groups = catalog.groups.filterNot { it.id == manifest.id } + CatalogRecord(manifest.id, manifest.revision, stored.checksum),
        )
        manifestStore.writeCatalog(tree, updated)
        session.updateCatalog(updated)
        val result = roomCommit(stored.checksum, generation)
        manifestStore.removeOperation(tree, operationId)
        result
    }

    suspend fun <T> deleteRecord(
        type: String,
        id: String,
        roomCommit: suspend (generation: Long) -> T,
    ): T = mutex.withLock {
        val tree = session.requireTreeUri()
        val catalog = session.requireCatalog()
        val generation = catalog.generation + 1L
        val operationId = UUID.randomUUID().toString()
        manifestStore.writeOperation(tree, OperationJournal(operationId, "delete_$type", id, 1L, generation, System.currentTimeMillis()))
        val tombstone = TombstoneManifest(type, id, System.currentTimeMillis(), generation)
        manifestStore.writeTombstone(tree, tombstone)
        val updated = catalog.copy(
            generation = generation,
            documents = if (type == "document") catalog.documents.filterNot { it.id == id } else catalog.documents,
            groups = if (type == "group") catalog.groups.filterNot { it.id == id } else catalog.groups,
            tombstones = catalog.tombstones.filterNot { it.type == type && it.id == id } + tombstone,
        )
        manifestStore.writeCatalog(tree, updated)
        session.updateCatalog(updated)
        val result = roomCommit(generation)
        manifestStore.removeOperation(tree, operationId)
        runCatching { fileSystem.delete(tree, if (type == "document") "documents/$id" else "groups/$id") }
        result
    }

    suspend fun storeAsset(relativePath: String, source: File, revision: Long): LibraryAssetRef {
        val tree = session.requireTreeUri()
        val stored = fileSystem.copyFromFile(tree, relativePath, source, "image/jpeg")
        return LibraryAssetRef(relativePath, revision, stored.size, stored.sha256, "image/jpeg")
    }

    suspend fun deleteAsset(asset: LibraryAssetRef?) {
        if (asset != null) runCatching { fileSystem.delete(session.requireTreeUri(), asset.relativePath) }
    }

    fun treeUri(): Uri = session.requireTreeUri()
}
