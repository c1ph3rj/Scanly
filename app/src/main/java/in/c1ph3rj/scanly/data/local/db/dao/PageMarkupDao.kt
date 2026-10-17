package `in`.c1ph3rj.scanly.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import `in`.c1ph3rj.scanly.data.local.db.entity.PageMarkupEntity

@Dao
interface PageMarkupDao {
    @Query("SELECT * FROM page_markups WHERE pageId = :pageId")
    suspend fun getMarkup(pageId: String): PageMarkupEntity?

    @Query(
        """
        SELECT pm.* FROM page_markups pm
        INNER JOIN scan_pages sp ON sp.id = pm.pageId
        WHERE sp.documentId = :documentId
        """,
    )
    suspend fun getMarkupsForDocument(documentId: String): List<PageMarkupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMarkup(markup: PageMarkupEntity)

    @Query("DELETE FROM page_markups WHERE pageId = :pageId")
    suspend fun deleteMarkup(pageId: String)
}
