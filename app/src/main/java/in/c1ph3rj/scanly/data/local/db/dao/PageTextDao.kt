package `in`.c1ph3rj.scanly.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import `in`.c1ph3rj.scanly.data.local.db.entity.PageTextEntity
import kotlinx.coroutines.flow.Flow

/** A page that has no text yet, or whose image changed since its text was read. */
data class PendingTextPageRow(
    val pageId: String,
    val documentId: String,
    val pageIndex: Int,
    val processedImagePath: String?,
    val rawImagePath: String?,
    val updatedAtMillis: Long,
)

/** One FTS hit. [snippet] marks matches with \u0002…\u0003; [offsets] is FTS4 `offsets()`. */
data class PageTextHitRow(
    val pageId: String,
    val documentId: String,
    val pageIndex: Int,
    val snippet: String,
    val offsets: String,
)

data class DocumentKindRow(
    val documentId: String,
    val kind: String,
)

data class TextIndexProgressRow(
    val totalPages: Int,
    val pendingPages: Int,
)

@Dao
interface PageTextDao {
    @Query(
        "SELECT t.* FROM page_texts t INNER JOIN scan_pages p ON p.id = t.pageId " +
            "WHERE t.documentId = :documentId AND p.deletedAtMillis IS NULL",
    )
    fun observeDocumentTexts(documentId: String): Flow<List<PageTextEntity>>

    @Query("SELECT * FROM page_texts WHERE pageId = :pageId")
    suspend fun getPageText(pageId: String): PageTextEntity?

    @Query(
        "SELECT t.* FROM page_texts t INNER JOIN scan_pages p ON p.id = t.pageId " +
            "WHERE t.documentId = :documentId AND p.deletedAtMillis IS NULL",
    )
    suspend fun getDocumentTexts(documentId: String): List<PageTextEntity>

    @Query(
        """
        SELECT p.id AS pageId, p.documentId AS documentId, p.pageIndex AS pageIndex,
               p.processedImagePath AS processedImagePath, p.rawImagePath AS rawImagePath,
               p.updatedAtMillis AS updatedAtMillis
        FROM scan_pages p
        INNER JOIN documents d ON d.id = p.documentId
        LEFT JOIN page_texts t ON t.pageId = p.id
        WHERE p.deletedAtMillis IS NULL
          AND (t.pageId IS NULL OR t.sourceUpdatedAtMillis != p.updatedAtMillis)
          AND (p.processedImagePath IS NOT NULL OR p.rawImagePath IS NOT NULL)
        ORDER BY d.updatedAtMillis DESC, p.pageIndex ASC
        LIMIT :limit
        """,
    )
    suspend fun getPendingPages(limit: Int): List<PendingTextPageRow>

    @Query(
        """
        SELECT p.id AS pageId, p.documentId AS documentId, p.pageIndex AS pageIndex,
               p.processedImagePath AS processedImagePath, p.rawImagePath AS rawImagePath,
               p.updatedAtMillis AS updatedAtMillis
        FROM scan_pages p
        LEFT JOIN page_texts t ON t.pageId = p.id
        WHERE p.documentId = :documentId AND p.deletedAtMillis IS NULL
          AND (t.pageId IS NULL OR t.sourceUpdatedAtMillis != p.updatedAtMillis)
          AND (p.processedImagePath IS NOT NULL OR p.rawImagePath IS NOT NULL)
        ORDER BY p.pageIndex ASC
        """,
    )
    suspend fun getPendingPagesForDocument(documentId: String): List<PendingTextPageRow>

    @Query(
        """
        SELECT p.id AS pageId, p.documentId AS documentId, p.pageIndex AS pageIndex,
               p.processedImagePath AS processedImagePath, p.rawImagePath AS rawImagePath,
               p.updatedAtMillis AS updatedAtMillis
        FROM scan_pages p
        WHERE p.documentId = :documentId AND p.deletedAtMillis IS NULL
          AND (p.processedImagePath IS NOT NULL OR p.rawImagePath IS NOT NULL)
        ORDER BY p.pageIndex ASC
        """,
    )
    suspend fun getDocumentPageSources(documentId: String): List<PendingTextPageRow>

    @Query(
        """
        SELECT p.id AS pageId, p.documentId AS documentId, p.pageIndex AS pageIndex,
               p.processedImagePath AS processedImagePath, p.rawImagePath AS rawImagePath,
               p.updatedAtMillis AS updatedAtMillis
        FROM scan_pages p WHERE p.id = :pageId
        """,
    )
    suspend fun getPageSource(pageId: String): PendingTextPageRow?

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM scan_pages WHERE deletedAtMillis IS NULL) AS totalPages,
            (SELECT COUNT(*) FROM scan_pages p
                LEFT JOIN page_texts t ON t.pageId = p.id
                WHERE p.deletedAtMillis IS NULL
                  AND (t.pageId IS NULL OR t.sourceUpdatedAtMillis != p.updatedAtMillis)
                  AND (p.processedImagePath IS NOT NULL OR p.rawImagePath IS NOT NULL)
            ) AS pendingPages
        """,
    )
    fun observeIndexProgress(): Flow<TextIndexProgressRow>

    /**
     * Full-text search. Ranking happens in Kotlin because FTS4 has no built-in rank; the
     * limit bounds snippet work on very common terms.
     */
    @Query(
        """
        SELECT t.pageId AS pageId, t.documentId AS documentId, p.pageIndex AS pageIndex,
               snippet(page_texts_fts, char(2), char(3), '…', -1, 14) AS snippet,
               offsets(page_texts_fts) AS offsets
        FROM page_texts_fts
        INNER JOIN page_texts t ON t.id = page_texts_fts.rowid
        INNER JOIN scan_pages p ON p.id = t.pageId
        INNER JOIN documents d ON d.id = p.documentId AND d.deletedAtMillis IS NULL
        WHERE page_texts_fts MATCH :match AND p.deletedAtMillis IS NULL
        LIMIT :limit
        """,
    )
    suspend fun search(match: String, limit: Int): List<PageTextHitRow>

    /** Kind of each document, taken from its earliest page that has one. */
    @Query(
        """
        SELECT t.documentId AS documentId, t.kind AS kind
        FROM page_texts t
        INNER JOIN scan_pages p ON p.id = t.pageId
        WHERE t.kind IS NOT NULL AND p.deletedAtMillis IS NULL
        ORDER BY t.documentId, p.pageIndex ASC
        """,
    )
    fun observeDocumentKinds(): Flow<List<DocumentKindRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PageTextEntity)

    @Query("DELETE FROM page_texts WHERE pageId = :pageId")
    suspend fun deleteByPageId(pageId: String)

    /** Delete + insert (never REPLACE) so the FTS delete trigger runs for the old row. */
    @Transaction
    suspend fun replace(entity: PageTextEntity) {
        deleteByPageId(entity.pageId)
        insert(entity.copy(id = 0L))
    }
}
