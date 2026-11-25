package `in`.c1ph3rj.scanly.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import `in`.c1ph3rj.scanly.data.local.db.entity.NamingTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NamingTemplateDao {
    @Query("SELECT * FROM naming_templates ORDER BY createdAtMillis ASC, id ASC")
    fun observeTemplates(): Flow<List<NamingTemplateEntity>>

    @Query("SELECT * FROM naming_templates ORDER BY createdAtMillis ASC, id ASC")
    suspend fun getTemplates(): List<NamingTemplateEntity>

    @Query("SELECT * FROM naming_templates WHERE id = :id")
    suspend fun getTemplate(id: String): NamingTemplateEntity?

    @Query("SELECT * FROM naming_templates WHERE isDefault = 1 ORDER BY updatedAtMillis DESC LIMIT 1")
    suspend fun getDefaultTemplate(): NamingTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(template: NamingTemplateEntity)

    @Update
    suspend fun update(template: NamingTemplateEntity)

    @Query("DELETE FROM naming_templates WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE naming_templates SET isDefault = 0 WHERE isDefault = 1")
    suspend fun clearDefault()

    /** @return rows changed: 0 when [id] doesn't exist. */
    @Query("UPDATE naming_templates SET isDefault = 1 WHERE id = :id")
    suspend fun markDefault(id: String): Int
}
