package `in`.c1ph3rj.scanly.feature.library

import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryUiStateTest {

    @Test
    fun allTabKeepsFolderedDocumentsInsideFoldersUntilSearchIsActive() {
        val grouped = document(id = "grouped", title = "Lease", groupId = "home")
        val ungrouped = document(id = "loose", title = "Receipt")

        val state = LibraryUiState(
            groups = listOf(group(id = "home", title = "Home")),
            ungroupedDocuments = listOf(ungrouped),
            allDocuments = listOf(grouped, ungrouped),
        )

        assertEquals(listOf("home"), state.visibleGroups.map { it.id })
        assertEquals(listOf("loose"), state.visibleDocuments.map { it.id })
    }

    @Test
    fun allTabSearchReturnsMatchingFoldersAndDocumentsFromEveryLocation() {
        val state = LibraryUiState(
            groups = listOf(
                group(id = "travel", title = "Travel records"),
                group(id = "work", title = "Work"),
            ),
            ungroupedDocuments = emptyList(),
            allDocuments = listOf(
                document(id = "ticket", title = "Travel ticket", groupId = "travel"),
                document(id = "invoice", title = "Invoice", groupId = "work"),
            ),
            searchQuery = " travel ",
        )

        assertEquals(listOf("travel"), state.visibleGroups.map { it.id })
        assertEquals(listOf("ticket"), state.visibleDocuments.map { it.id })
    }

    @Test
    fun foldersTabExcludesDocumentsWhilePreservingTheSharedSearchQuery() {
        val state = LibraryUiState(
            groups = listOf(group(id = "tax", title = "Tax")),
            ungroupedDocuments = listOf(document(id = "tax-doc", title = "Tax return")),
            allDocuments = listOf(document(id = "tax-doc", title = "Tax return")),
            searchQuery = "tax",
            selectedTab = LibraryTab.Folders,
        )

        assertEquals(listOf("tax"), state.visibleGroups.map { it.id })
        assertTrue(state.visibleDocuments.isEmpty())
    }

    @Test
    fun documentsTabShowsFolderedAndUngroupedDocumentsButNoFolders() {
        val state = LibraryUiState(
            groups = listOf(group(id = "home", title = "Home")),
            ungroupedDocuments = listOf(document(id = "loose", title = "Receipt", updatedAt = 20L)),
            allDocuments = listOf(
                document(id = "grouped", title = "Lease", groupId = "home", updatedAt = 30L),
                document(id = "loose", title = "Receipt", updatedAt = 20L),
            ),
            selectedTab = LibraryTab.Documents,
        )

        assertTrue(state.visibleGroups.isEmpty())
        assertEquals(listOf("grouped", "loose"), state.visibleDocuments.map { it.id })
    }

    @Test
    fun nameAndDateSortOptionsAreAppliedDeterministically() {
        val documents = listOf(
            document(id = "b", title = "beta", createdAt = 10L, updatedAt = 30L),
            document(id = "a", title = "Alpha", createdAt = 30L, updatedAt = 20L),
            document(id = "g", title = "gamma", createdAt = 20L, updatedAt = 10L),
        )

        fun sortedIds(option: LibrarySortOption): List<String> = LibraryUiState(
            allDocuments = documents,
            selectedTab = LibraryTab.Documents,
            sortOption = option,
        ).visibleDocuments.map { it.id }

        assertEquals(listOf("a", "b", "g"), sortedIds(LibrarySortOption.NameAscending))
        assertEquals(listOf("g", "b", "a"), sortedIds(LibrarySortOption.NameDescending))
        assertEquals(listOf("b", "a", "g"), sortedIds(LibrarySortOption.RecentlyUpdated))
        assertEquals(listOf("g", "a", "b"), sortedIds(LibrarySortOption.OldestUpdated))
        assertEquals(listOf("a", "g", "b"), sortedIds(LibrarySortOption.NewestCreated))
        assertEquals(listOf("b", "g", "a"), sortedIds(LibrarySortOption.OldestCreated))
    }

    @Test
    fun folderResultsUseTheSelectedSortOptionToo() {
        val state = LibraryUiState(
            groups = listOf(
                group(id = "z", title = "Zoo", updatedAt = 10L),
                group(id = "a", title = "Archive", updatedAt = 30L),
                group(id = "m", title = "Manuals", updatedAt = 20L),
            ),
            selectedTab = LibraryTab.Folders,
            sortOption = LibrarySortOption.OldestUpdated,
        )

        assertEquals(listOf("z", "m", "a"), state.visibleGroups.map { it.id })
    }

    private fun group(
        id: String,
        title: String,
        createdAt: Long = 10L,
        updatedAt: Long = 20L,
    ) = DocumentGroup(
        id = id,
        title = title,
        documentCount = 0,
        totalPageCount = 0,
        coverThumbnail = null,
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
    )

    private fun document(
        id: String,
        title: String,
        groupId: String? = null,
        createdAt: Long = 10L,
        updatedAt: Long = 20L,
    ) = ScanDocument(
        id = id,
        title = title,
        pageCount = 0,
        coverThumbnail = null,
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
        groupId = groupId,
    )
}
