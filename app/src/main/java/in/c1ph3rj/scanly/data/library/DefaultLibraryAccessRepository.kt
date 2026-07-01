package `in`.c1ph3rj.scanly.data.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.data.library.manifest.CURRENT_LIBRARY_FORMAT
import `in`.c1ph3rj.scanly.data.library.manifest.LibraryManifestStore
import `in`.c1ph3rj.scanly.domain.model.LibraryAccessState
import `in`.c1ph3rj.scanly.domain.model.LibraryStartupStatus
import `in`.c1ph3rj.scanly.domain.repository.LibraryAccessRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.libraryAccessDataStore by preferencesDataStore(name = "scanly_library_access")

@Singleton
class DefaultLibraryAccessRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fileSystem: SharedLibraryFileSystem,
    private val manifestStore: LibraryManifestStore,
    private val synchronizer: LibraryIndexSynchronizer,
    private val session: LibrarySession,
) : LibraryAccessRepository {
    private val _state = MutableStateFlow(LibraryAccessState())
    override val state: StateFlow<LibraryAccessState> = _state

    override suspend fun initialize() {
        _state.value = LibraryAccessState(LibraryStartupStatus.CHECKING)
        val stored = context.libraryAccessDataStore.data.first()[treeUriKey]
        if (stored.isNullOrBlank()) {
            _state.value = LibraryAccessState(LibraryStartupStatus.STORAGE_SETUP_REQUIRED)
            return
        }
        val uri = Uri.parse(stored)
        if (!fileSystem.hasPersistedReadWriteGrant(uri)) {
            session.disconnect()
            _state.value = LibraryAccessState(
                LibraryStartupStatus.RECONNECT_REQUIRED,
                message = "Select your existing Scanly folder to reconnect it.",
            )
            return
        }
        runCatching { connectInternal(uri, persist = false) }
            .onFailure { throwable ->
                session.disconnect()
                _state.value = LibraryAccessState(
                    failureStatus(throwable),
                    message = throwable.message ?: "The shared Scanly library needs repair.",
                )
            }
    }

    override suspend fun connect(treeUri: String): ScanlyResult<Unit> {
        val previousState = _state.value
        return runCatching {
        val uri = Uri.parse(treeUri)
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        connectInternal(uri, persist = true)
        }.fold(
        onSuccess = { ScanlyResult.Success(Unit) },
        onFailure = { throwable ->
            _state.value = if (previousState.status == LibraryStartupStatus.READY) previousState else
                LibraryAccessState(failureStatus(throwable), message = throwable.message)
            ScanlyResult.Failure(ScanlyError(throwable.message ?: "Could not connect the Scanly library.", throwable))
        },
        )
    }

    override suspend fun disconnect(): ScanlyResult<Unit> = runCatching {
        val stored = context.libraryAccessDataStore.data.first()[treeUriKey]
        stored?.let { value ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(value),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        context.libraryAccessDataStore.edit { it.remove(treeUriKey) }
        synchronizer.clearIndex()
        session.disconnect()
        _state.value = LibraryAccessState(LibraryStartupStatus.STORAGE_SETUP_REQUIRED)
    }.fold(
        onSuccess = { ScanlyResult.Success(Unit) },
        onFailure = { ScanlyResult.Failure(ScanlyError(it.message ?: "Could not disconnect the library.", it)) },
    )

    override suspend fun rebuildIndex(): ScanlyResult<Unit> = runCatching {
        val treeUri = session.requireTreeUri()
        val marker = session.requireMarker()
        _state.value = LibraryAccessState(LibraryStartupStatus.REBUILDING_DATABASE, marker.libraryId)
        val catalog = synchronizer.synchronize(treeUri, marker, forceRebuild = true)
        session.updateCatalog(catalog)
        _state.value = readyState(marker.libraryId, fileSystem.displayName(treeUri))
    }.fold(
        onSuccess = { ScanlyResult.Success(Unit) },
        onFailure = { ScanlyResult.Failure(ScanlyError(it.message ?: "Could not rebuild the library index.", it)) },
    )

    private suspend fun connectInternal(uri: Uri, persist: Boolean) {
        _state.value = LibraryAccessState(LibraryStartupStatus.CHECKING)
        val marker = manifestStore.readMarkerOrNull(uri) ?: manifestStore.createLibrary(uri)
        if (marker.minimumReaderVersion > CURRENT_LIBRARY_FORMAT) {
            _state.value = LibraryAccessState(LibraryStartupStatus.UNSUPPORTED_LIBRARY_VERSION, marker.libraryId)
            error("This Scanly library requires a newer app.")
        }
        _state.value = LibraryAccessState(LibraryStartupStatus.APPLYING_DELTA, marker.libraryId)
        val catalog = synchronizer.synchronize(uri, marker)
        session.connect(uri, marker, catalog)
        if (persist) context.libraryAccessDataStore.edit { it[treeUriKey] = uri.toString() }
        discardLegacyPrivateStorage()
        _state.value = readyState(marker.libraryId, fileSystem.displayName(uri))
    }

    private fun readyState(libraryId: String, displayName: String = "Scanly library") = LibraryAccessState(
        status = LibraryStartupStatus.READY,
        libraryId = libraryId,
        displayName = displayName,
    )

    private fun failureStatus(throwable: Throwable): LibraryStartupStatus =
        if (throwable.message?.contains("newer", ignoreCase = true) == true ||
            throwable.message?.contains("unsupported", ignoreCase = true) == true
        ) {
            LibraryStartupStatus.UNSUPPORTED_LIBRARY_VERSION
        } else {
            LibraryStartupStatus.REPAIR_REQUIRED
        }

    private fun discardLegacyPrivateStorage() {
        runCatching { java.io.File(context.filesDir, "documents").deleteRecursively() }
        runCatching { context.deleteDatabase("scanly.db") }
    }

    private companion object {
        val treeUriKey = stringPreferencesKey("tree_uri")
    }
}
