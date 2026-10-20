package `in`.c1ph3rj.scanly.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import `in`.c1ph3rj.scanly.data.local.db.entity.SignatureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SignatureDao {
    @Query("SELECT * FROM signatures ORDER BY updatedAtMillis DESC")
    fun observeSignatures(): Flow<List<SignatureEntity>>

    @Query("SELECT * FROM signatures WHERE id = :id")
    suspend fun getSignature(id: String): SignatureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSignature(signature: SignatureEntity)

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun deleteSignature(id: String)
}
