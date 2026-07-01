package `in`.c1ph3rj.scanly.data.library

import `in`.c1ph3rj.scanly.data.local.db.dao.LibraryStateDao
import `in`.c1ph3rj.scanly.data.local.db.entity.LibraryStateEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ManifestFingerprintEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRoomStateUpdater @Inject constructor(
    private val stateDao: LibraryStateDao,
    private val session: LibrarySession,
) {
    suspend fun record(recordType: String, recordId: String, revision: Long, checksum: String, generation: Long) {
        stateDao.putFingerprints(listOf(ManifestFingerprintEntity(recordType, recordId, revision, checksum)))
        putState(generation)
    }

    suspend fun remove(recordType: String, recordId: String, generation: Long) {
        stateDao.deleteFingerprint(recordType, recordId)
        putState(generation)
    }

    private suspend fun putState(generation: Long) {
        stateDao.putState(
            LibraryStateEntity(
                libraryId = session.requireMarker().libraryId,
                appliedGeneration = generation,
                lastSynchronizedAtMillis = System.currentTimeMillis(),
                healthState = "READY",
            ),
        )
    }
}

