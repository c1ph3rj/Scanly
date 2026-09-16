package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.feature.library.LibrarySortOption
import `in`.c1ph3rj.scanly.feature.library.LibraryTab
import `in`.c1ph3rj.scanly.feature.library.LibraryUiState
import `in`.c1ph3rj.scanly.testing.InMemoryDocumentRepository
import `in`.c1ph3rj.scanly.testing.InMemoryGroupRepository
import `in`.c1ph3rj.scanly.testing.InMemoryPageRepository
import `in`.c1ph3rj.scanly.testing.InMemoryScanlyLibrary
import `in`.c1ph3rj.scanly.testing.requireSuccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentLibraryFlowTest {
    private val library = InMemoryScanlyLibrary()
    private val documents = InMemoryDocumentRepository(library)
    private val groups = InMemoryGroupRepository(library)
    private val pages = InMemoryPageRepository(library)

    private val createDocument = CreateDocumentUseCase(documents)
    private val renameDocument = RenameDocumentUseCase(documents)
    private val deleteDocument = DeleteDocumentUseCase(documents)
    private val suggestDocumentTitle = SuggestDocumentTitleUseCase(documents)
    private val observeDocuments = ObserveDocumentsUseCase(documents)
    private val observeUngrouped = ObserveUngroupedDocumentsUseCase(documents)

    private val createGroup = CreateGroupUseCase(groups)
    private val renameGroup = RenameGroupUseCase(groups)
    private val deleteGroup = DeleteGroupUseCase(groups)
    private val suggestGroupTitle = SuggestGroupTitleUseCase(groups)
    private val setDocumentGroup = SetDocumentGroupUseCase(groups)
    private val observeGroups = ObserveGroupsUseCase(groups)
    private val observeGroupDocuments = ObserveGroupDocumentsUseCase(groups)

    private val prepareCapture = PreparePageCaptureUseCase(pages)
    private val finalizeCapture = FinalizeCapturedPageUseCase(pages)
    private val movePage = MovePageUseCase(pages)
    private val deletePage = DeletePageUseCase(pages)
    private val observePages = ObserveDocumentPagesUseCase(pages)

    @Test
    fun createRenameAndDeleteDocuments_updatesObservedTitles() = runBlocking {
        val firstId = createDocument("Lease").requireSuccess()
        val secondId = createDocument("  Receipt   April ").requireSuccess()

        assertEquals(
            listOf("Lease", "Receipt April"),
            observeDocuments().first().map { it.title }.sorted(),
        )

        renameDocument(firstId, "Lease 2026").requireSuccess()
        assertEquals("Lease 2026", observeDocuments().first().single { it.id == firstId }.title)

        deleteDocument(secondId).requireSuccess()
        assertEquals(listOf("Lease 2026"), observeDocuments().first().map { it.title })
    }

    @Test
    fun suggestedDocumentAndGroupTitles_areDuplicateSafe() = runBlocking {
        val firstDocumentTitle = suggestDocumentTitle(DocumentTitleFormat.ScanIsoDate)
        createDocument(firstDocumentTitle).requireSuccess()
        val secondDocumentTitle = suggestDocumentTitle(DocumentTitleFormat.ScanIsoDate)
        assertEquals("$firstDocumentTitle (2)", secondDocumentTitle)

        val firstGroupTitle = suggestGroupTitle(GroupTitleFormat.FolderIsoDate)
        createGroup(firstGroupTitle).requireSuccess()
        val secondGroupTitle = suggestGroupTitle(GroupTitleFormat.FolderIsoDate)
        assertEquals("$firstGroupTitle (2)", secondGroupTitle)
    }

    @Test
    fun createRenameDeleteGroupsAndMembership_changesVisibleLibrarySets() = runBlocking {
        val homeId = createGroup("Home").requireSuccess()
        val workId = createGroup("Work").requireSuccess()
        val leaseId = createDocument("Lease").requireSuccess()
        val receiptId = createDocument("Receipt").requireSuccess()
        val invoiceId = createDocument("Invoice").requireSuccess()

        setDocumentGroup(leaseId, homeId).requireSuccess()
        setDocumentGroup(invoiceId, workId).requireSuccess()

        assertEquals(listOf(leaseId), observeGroupDocuments(homeId).first().map { it.id })
        assertEquals(
            listOf(receiptId),
            observeUngrouped().first().map { it.id },
        )

        renameGroup(homeId, "Home records").requireSuccess()
        assertEquals(
            "Home records",
            observeGroups().first().single { it.id == homeId }.title,
        )

        setDocumentGroup(leaseId, null).requireSuccess()
        assertTrue(observeGroupDocuments(homeId).first().isEmpty())
        assertEquals(
            setOf(leaseId, receiptId),
            observeUngrouped().first().map { it.id }.toSet(),
        )

        deleteGroup(workId).requireSuccess()
        assertNull(observeDocuments().first().single { it.id == invoiceId }.groupId)
        assertEquals(
            setOf("Home records"),
            observeGroups().first().map { it.title }.toSet(),
        )
    }

    @Test
    fun libraryAllFoldersDocuments_searchAndSort_useObservedMembership() = runBlocking {
        val travelId = createGroup("Travel records").requireSuccess()
        val workId = createGroup("Work").requireSuccess()
        val ticketId = createDocument("Travel ticket").requireSuccess()
        val invoiceId = createDocument("Invoice").requireSuccess()
        val receiptId = createDocument("Receipt").requireSuccess()
        setDocumentGroup(ticketId, travelId).requireSuccess()
        setDocumentGroup(invoiceId, workId).requireSuccess()

        val groups = observeGroups().first()
        val ungrouped = observeUngrouped().first()
        val allDocuments = observeDocuments().first()

        val allTab = LibraryUiState(
            groups = groups,
            ungroupedDocuments = ungrouped,
            allDocuments = allDocuments,
        )
        assertEquals(setOf(travelId, workId), allTab.visibleGroups.map { it.id }.toSet())
        assertEquals(listOf(receiptId), allTab.visibleDocuments.map { it.id })

        val search = allTab.copy(searchQuery = " travel ")
        assertEquals(listOf(travelId), search.visibleGroups.map { it.id })
        assertEquals(listOf(ticketId), search.visibleDocuments.map { it.id })

        val folders = allTab.copy(selectedTab = LibraryTab.Folders, searchQuery = "travel")
        assertEquals(listOf(travelId), folders.visibleGroups.map { it.id })
        assertTrue(folders.visibleDocuments.isEmpty())

        val documentsTab = LibraryUiState(
            groups = groups,
            ungroupedDocuments = ungrouped,
            allDocuments = allDocuments,
            selectedTab = LibraryTab.Documents,
            sortOption = LibrarySortOption.NameAscending,
        )
        assertTrue(documentsTab.visibleGroups.isEmpty())
        assertEquals(
            listOf("Invoice", "Receipt", "Travel ticket"),
            documentsTab.visibleDocuments.map { it.title },
        )
    }

    @Test
    fun pageReorderAndDelete_persistObservedOrder() = runBlocking {
        val documentId = createDocument("Packet").requireSuccess()
        val firstPageId = capturePage(documentId)
        val secondPageId = capturePage(documentId)
        val thirdPageId = capturePage(documentId)

        assertEquals(
            listOf(firstPageId, secondPageId, thirdPageId),
            observePages(documentId).first().map { it.id },
        )

        movePage(thirdPageId, 0).requireSuccess()
        assertEquals(
            listOf(thirdPageId, firstPageId, secondPageId),
            observePages(documentId).first().map { it.id },
        )
        assertEquals(listOf(0, 1, 2), observePages(documentId).first().map { it.pageIndex })

        deletePage(firstPageId).requireSuccess()
        assertEquals(
            listOf(thirdPageId, secondPageId),
            observePages(documentId).first().map { it.id },
        )
        assertEquals(2, observeDocuments().first().single { it.id == documentId }.pageCount)
    }

    private suspend fun capturePage(documentId: String): String {
        val draft = prepareCapture(documentId).requireSuccess()
        return finalizeCapture(draft).requireSuccess()
    }
}
