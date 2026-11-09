package `in`.c1ph3rj.scanly.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import `in`.c1ph3rj.scanly.data.local.db.entity.PageTextEntity
import `in`.c1ph3rj.scanly.domain.search.FtsExpression
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

/**
 * A page matching an FTS query, before ranking. [matchinfo] is FTS4 `matchinfo(…, 'pcnalx')`
 * (see `Bm25`); snippets are fetched afterwards for the best pages only.
 */
class PageTextCandidateRow(
    val pageId: String,
    val documentId: String,
    val pageIndex: Int,
    val matchinfo: ByteArray,
)

/** [snippet] marks matches with …; [offsets] is FTS4 `offsets()`. */
data class PageTextSnippetRow(
    val pageId: String,
    val snippet: String,
    val offsets: String,
)

/** A page's indexed text, for re-deriving its search forms or building a snippet in Kotlin. */
data class PageSearchTextRow(
    val id: Long,
    val pageId: String,
    val searchText: String,
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
        INNER JOIN documents d ON d.id = p.documentId AND d.deletedAtMillis IS NULL
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
            (SELECT COUNT(*) FROM scan_pages p
                INNER JOIN documents d ON d.id = p.documentId AND d.deletedAtMillis IS NULL
                WHERE p.deletedAtMillis IS NULL) AS totalPages,
            (SELECT COUNT(*) FROM scan_pages p
                INNER JOIN documents d ON d.id = p.documentId AND d.deletedAtMillis IS NULL
                LEFT JOIN page_texts t ON t.pageId = p.id
                WHERE p.deletedAtMillis IS NULL
                  AND (t.pageId IS NULL OR t.sourceUpdatedAtMillis != p.updatedAtMillis)
                  AND (p.processedImagePath IS NOT NULL OR p.rawImagePath IS NOT NULL)
            ) AS pendingPages
        """,
    )
    fun observeIndexProgress(): Flow<TextIndexProgressRow>

    /**
     * Live pages with a text row that [engine] did not produce (another engine, or nothing
     * recognized). A blank result from the other engine is worth retrying, so those count too.
     * Pages [engine] already tried and failed on, or found empty, are left out: reading them again
     * would only fall back again. Pages skipped because Indra was paused or timed out still count.
     */
    @Query(
        """
        SELECT COUNT(*) FROM page_texts t
        INNER JOIN scan_pages p ON p.id = t.pageId
        INNER JOIN documents d ON d.id = p.documentId AND d.deletedAtMillis IS NULL
        WHERE p.deletedAtMillis IS NULL
          AND (p.processedImagePath IS NOT NULL OR p.rawImagePath IS NOT NULL)
          AND (t.engine IS NULL OR t.engine != :engine)
          AND NOT (t.requestedEngine = :engine AND t.fallbackReason IN ('INDRA_FAILED', 'INDRA_EMPTY'))
        """,
    )
    fun observePagesNotReadWith(engine: String): Flow<Int>

    /**
     * Marks the same pages as stale (`sourceUpdatedAtMillis = -1` never equals a page's
     * `updatedAtMillis`), so the indexer re-reads them. Returns the rows marked.
     */
    @Query(
        """
        UPDATE page_texts SET sourceUpdatedAtMillis = -1
        WHERE pageId IN (
            SELECT t.pageId FROM page_texts t
            INNER JOIN scan_pages p ON p.id = t.pageId
            INNER JOIN documents d ON d.id = p.documentId AND d.deletedAtMillis IS NULL
            WHERE p.deletedAtMillis IS NULL
              AND (p.processedImagePath IS NOT NULL OR p.rawImagePath IS NOT NULL)
              AND (t.engine IS NULL OR t.engine != :engine)
              AND NOT (t.requestedEngine = :engine AND t.fallbackReason IN ('INDRA_FAILED', 'INDRA_EMPTY'))
        )
        """,
    )
    suspend fun markPagesNotReadWithStale(engine: String): Int

    /**
     * Pages matching [match], with what BM25 ranking needs and nothing else; FTS4 has no
     * built-in rank, so ranking happens in Kotlin and [snippetQuery] runs for the top pages only.
     */
    @Query(
        """
        SELECT t.pageId AS pageId, t.documentId AS documentId, p.pageIndex AS pageIndex,
               matchinfo(page_texts_fts, 'pcnalx') AS matchinfo
        FROM page_texts_fts
        INNER JOIN page_texts t ON t.id = page_texts_fts.rowid
        INNER JOIN scan_pages p ON p.id = t.pageId
        INNER JOIN documents d ON d.id = p.documentId AND d.deletedAtMillis IS NULL
        WHERE page_texts_fts MATCH :match AND p.deletedAtMillis IS NULL
        LIMIT :limit
        """,
    )
    suspend fun searchCandidates(match: String, limit: Int): List<PageTextCandidateRow>

    /** Pages matching [match], without the snippet work; used to tell exact hits from prefix ones. */
    @Query(
        """
        SELECT t.pageId FROM page_texts_fts
        INNER JOIN page_texts t ON t.id = page_texts_fts.rowid
        WHERE page_texts_fts MATCH :match
        LIMIT :limit
        """,
    )
    suspend fun searchPageIds(match: String, limit: Int): List<String>

    /** Candidates for queries assembled at runtime (see [groupedMatchQuery]). */
    @RawQuery
    suspend fun searchCandidatesRaw(query: SupportSQLiteQuery): List<PageTextCandidateRow>

    /** Snippets for chosen pages (see [snippetQuery]). */
    @RawQuery
    suspend fun snippetsRaw(query: SupportSQLiteQuery): List<PageTextSnippetRow>

    /** Text rows whose derived search forms predate [version] (see `SearchTextDeriver`). */
    @Query("SELECT id, pageId, searchText FROM page_texts WHERE derivedVersion < :version LIMIT :limit")
    suspend fun getRowsWithDerivedVersionBelow(version: Int, limit: Int): List<PageSearchTextRow>

    @Query("SELECT COUNT(*) FROM page_texts WHERE derivedVersion < :version")
    suspend fun countRowsWithDerivedVersionBelow(version: Int): Int

    /** A plain UPDATE: Room's FTS sync triggers re-index the row. */
    @Query(
        "UPDATE page_texts SET searchText = :searchText, derivedText = :derivedText, " +
            "derivedVersion = :version WHERE id = :id",
    )
    suspend fun updateDerived(id: Long, searchText: String, derivedText: String, version: Int)

    @Query("SELECT id, pageId, searchText FROM page_texts WHERE pageId IN (:pageIds)")
    suspend fun getSearchTexts(pageIds: List<String>): List<PageSearchTextRow>

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

/**
 * Candidates matching every group of [expression] (each group's alternatives OR-ed) and none of
 * its exclusions, checked in
 * one sub-select per group instead of `(a OR b) c`: parentheses need SQLite's enhanced query
 * syntax, which a device may not have, and without them OR and implicit AND bind differently
 * between the two syntaxes. The outer MATCH is every alternative OR-ed, so `matchinfo` and
 * snippets see all of them.
 */
internal fun groupedMatchQuery(expression: FtsExpression, limit: Int): SupportSQLiteQuery {
    val groupFilters = expression.groups.joinToString("") {
        " AND page_texts_fts.rowid IN (SELECT docid FROM page_texts_fts WHERE page_texts_fts MATCH ?)"
    } + expression.excluded.joinToString("") {
        " AND page_texts_fts.rowid NOT IN (SELECT docid FROM page_texts_fts WHERE page_texts_fts MATCH ?)"
    }
    val sql = """
        SELECT t.pageId AS pageId, t.documentId AS documentId, p.pageIndex AS pageIndex,
               matchinfo(page_texts_fts, 'pcnalx') AS matchinfo
        FROM page_texts_fts
        INNER JOIN page_texts t ON t.id = page_texts_fts.rowid
        INNER JOIN scan_pages p ON p.id = t.pageId
        INNER JOIN documents d ON d.id = p.documentId AND d.deletedAtMillis IS NULL
        WHERE page_texts_fts MATCH ? AND p.deletedAtMillis IS NULL$groupFilters
        LIMIT ?
    """.trimIndent()
    val args = buildList<Any> {
        add(expression.anyOf)
        expression.groups.indices.forEach { add(expression.group(it)) }
        addAll(expression.excluded)
        add(limit)
    }
    return SimpleSQLiteQuery(sql, args.toTypedArray())
}

/**
 * Snippet and offsets for [pageIds] (at most [MAX_SQL_ARGUMENTS] − 1), highlighting [match].
 * Snippets come from the text column only; derived forms are never shown.
 */
internal fun snippetQuery(match: String, pageIds: List<String>): SupportSQLiteQuery {
    require(pageIds.size < MAX_SQL_ARGUMENTS)
    val placeholders = pageIds.joinToString(",") { "?" }
    val sql = """
        SELECT t.pageId AS pageId,
               snippet(page_texts_fts, char(2), char(3), '…', 0, 14) AS snippet,
               offsets(page_texts_fts) AS offsets
        FROM page_texts_fts
        INNER JOIN page_texts t ON t.id = page_texts_fts.rowid
        WHERE page_texts_fts MATCH ? AND t.pageId IN ($placeholders)
    """.trimIndent()
    return SimpleSQLiteQuery(sql, (listOf<Any>(match) + pageIds).toTypedArray())
}

/** Bound parameters per statement stay well under SQLite's oldest default limit of 999. */
internal const val MAX_SQL_ARGUMENTS = 500
