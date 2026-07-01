package `in`.c1ph3rj.scanly.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import `in`.c1ph3rj.scanly.data.local.db.entity.LibraryStateEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ManifestFingerprintEntity

@Dao
interface LibraryStateDao {
    @Query("SELECT * FROM library_state WHERE singletonId = 1")
    suspend fun getState(): LibraryStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(state: LibraryStateEntity)

    @Query("DELETE FROM library_state")
    suspend fun clearState()

    @Query("SELECT * FROM manifest_fingerprints")
    suspend fun getFingerprints(): List<ManifestFingerprintEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFingerprints(fingerprints: List<ManifestFingerprintEntity>)

    @Query("DELETE FROM manifest_fingerprints")
    suspend fun clearFingerprints()

    @Query("DELETE FROM manifest_fingerprints WHERE recordType = :recordType AND recordId = :recordId")
    suspend fun deleteFingerprint(recordType: String, recordId: String)
}
