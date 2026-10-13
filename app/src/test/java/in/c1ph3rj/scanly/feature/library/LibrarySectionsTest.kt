package `in`.c1ph3rj.scanly.feature.library

import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class LibrarySectionsTest {

    @Test
    fun emptyInputProducesNoSections() {
        assertEquals(emptyList<LibraryDocumentSection>(), sections(emptyList(), LibrarySortOption.RecentlyUpdated))
    }

    @Test
    fun nameSortProducesSingleUntitledSectionInInputOrder() {
        val documents = listOf(
            doc("b", updated = "2026-10-01T10:00:00Z"),
            doc("a", updated = "2025-12-05T10:00:00Z"),
        )

        val result = sections(documents, LibrarySortOption.NameAscending)

        assertEquals(listOf("all"), result.map { it.key })
        assertEquals(listOf(null), result.map { it.title })
        assertEquals(listOf("b", "a"), result.single().documents.map { it.id })
    }

    @Test
    fun recentlyUpdatedGroupsByRelativeDateAndMonth() {
        val documents = listOf(
            doc("today", updated = "2026-10-14T08:00:00Z"),
            doc("yesterday", updated = "2026-10-13T09:00:00Z"),
            doc("week", updated = "2026-10-11T10:00:00Z"),
            doc("october", updated = "2026-10-01T10:00:00Z"),
            doc("september", updated = "2026-09-20T10:00:00Z"),
            doc("december", updated = "2025-12-05T10:00:00Z"),
        )

        val result = sections(documents, LibrarySortOption.RecentlyUpdated)

        assertEquals(
            listOf("Today", "Yesterday", "Previous 7 days", "October", "September", "December 2025"),
            result.map { it.title },
        )
        assertEquals(
            listOf(listOf("today"), listOf("yesterday"), listOf("week"), listOf("october"), listOf("september"), listOf("december")),
            result.map { section -> section.documents.map { it.id } },
        )
    }

    @Test
    fun oldestUpdatedProducesSectionsInAscendingOrder() {
        val documents = listOf(
            doc("december", updated = "2025-12-05T10:00:00Z"),
            doc("september", updated = "2026-09-20T10:00:00Z"),
            doc("october", updated = "2026-10-01T10:00:00Z"),
            doc("week", updated = "2026-10-11T10:00:00Z"),
            doc("yesterday", updated = "2026-10-13T09:00:00Z"),
            doc("today", updated = "2026-10-14T08:00:00Z"),
        )

        val result = sections(documents, LibrarySortOption.OldestUpdated)

        assertEquals(
            listOf("December 2025", "September", "October", "Previous 7 days", "Yesterday", "Today"),
            result.map { it.title },
        )
    }

    @Test
    fun newestCreatedUsesCreatedTimestampNotUpdatedTimestamp() {
        val documents = listOf(
            doc("created-today", updated = "2025-12-05T10:00:00Z", created = "2026-10-14T08:00:00Z"),
            doc("created-october", updated = "2026-10-14T09:00:00Z", created = "2026-10-01T10:00:00Z"),
        )

        val result = sections(documents, LibrarySortOption.NewestCreated)

        assertEquals(listOf("Today", "October"), result.map { it.title })
        assertEquals(
            listOf(listOf("created-today"), listOf("created-october")),
            result.map { section -> section.documents.map { it.id } },
        )
    }

    @Test
    fun futureTimestampsAreGroupedAsToday() {
        val documents = listOf(
            doc("future", updated = "2026-10-20T00:00:00Z"),
            doc("today", updated = "2026-10-14T08:00:00Z"),
        )

        val result = sections(documents, LibrarySortOption.RecentlyUpdated)

        assertEquals(listOf("today"), result.map { it.key })
        assertEquals(listOf("Today"), result.map { it.title })
        assertEquals(listOf("future", "today"), result.single().documents.map { it.id })
    }

    @Test
    fun nonContiguousBucketsGetUniqueKeysAndSeparateSections() {
        val documents = listOf(
            doc("first-today", updated = "2026-10-14T08:00:00Z"),
            doc("yesterday", updated = "2026-10-13T09:00:00Z"),
            doc("second-today", updated = "2026-10-14T07:00:00Z"),
        )

        val result = sections(documents, LibrarySortOption.RecentlyUpdated)

        assertEquals(listOf("today", "yesterday", "today-2"), result.map { it.key })
        assertEquals(3, result.map { it.key }.distinct().size)
        assertEquals(
            listOf(listOf("first-today"), listOf("yesterday"), listOf("second-today")),
            result.map { section -> section.documents.map { it.id } },
        )
    }

    private fun sections(
        documents: List<ScanDocument>,
        sortOption: LibrarySortOption,
    ): List<LibraryDocumentSection> = libraryDocumentSections(
        documents = documents,
        sortOption = sortOption,
        nowMillis = NOW_MILLIS,
        zoneId = ZoneId.of("UTC"),
        locale = Locale.ENGLISH,
    )

    private fun doc(
        id: String,
        updated: String,
        created: String = updated,
    ) = ScanDocument(
        id = id,
        title = id,
        pageCount = 0,
        coverThumbnailPath = null,
        rootDirectoryPath = "/$id",
        createdAtMillis = Instant.parse(created).toEpochMilli(),
        updatedAtMillis = Instant.parse(updated).toEpochMilli(),
    )

    private companion object {
        val NOW_MILLIS: Long = Instant.parse("2026-10-14T12:00:00Z").toEpochMilli()
    }
}
