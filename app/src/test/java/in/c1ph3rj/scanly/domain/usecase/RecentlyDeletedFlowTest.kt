package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.TrashedDocument
import `in`.c1ph3rj.scanly.domain.model.TrashedFolder
import `in`.c1ph3rj.scanly.domain.model.TrashedItem
import `in`.c1ph3rj.scanly.domain.model.TrashedPage
import `in`.c1ph3rj.scanly.domain.trash.TrashEvents
import `in`.c1ph3rj.scanly.domain.trash.TrashKind
import `in`.c1ph3rj.scanly.domain.trash.TrashNotice
import `in`.c1ph3rj.scanly.testing.InMemoryDocumentRepository
import `in`.c1ph3rj.scanly.testing.InMemoryGroupRepository
import `in`.c1ph3rj.scanly.testing.InMemoryPageRepository
import `in`.c1ph3rj.scanly.testing.InMemoryScanlyLibrary
import `in`.c1ph3rj.scanly.testing.requireSuccess
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RecentlyDeletedFlowTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val baseTime = 1_700_000_000_000L

    private val library = InMemoryScanlyLibrary()
    private val documents = InMemoryDocumentRepository(library)
    private val groups = InMemoryGroupRepository(library)
    private val pages = InMemoryPageRepository(library)
    private val trashEvents = TrashEvents()

    private val createDocument = CreateDocumentUseCase(documents)
    private val createGroup = CreateGroupUseCase(groups)
    private val observeLiveDocuments = ObserveDocumentsUseCase(documents)
    private val observeGroups = ObserveGroupsUseCase(groups)
    private val observeGroupDocuments = ObserveGroupDocumentsUseCase(groups)
    private val observeDocument = ObserveDocumentUseCase(documents)
    private val observePages = ObserveDocumentPagesUseCase(pages)
    private val observeRecentlyDeleted = ObserveRecentlyDeletedUseCase(documents, groups, pages)
    private val moveDocumentToTrash = MoveDocumentToTrashUseCase(documents, trashEvents)
    private val moveGroupToTrash = MoveGroupToTrashUseCase(groups, trashEvents)
    private val movePageToTrash = MovePageToTrashUseCase(pages, trashEvents)
    private val restoreItem = RestoreTrashedItemUseCase(documents, groups, pages)
    private val deleteForever = DeleteTrashedItemForeverUseCase(documents, groups, pages)
    private val purgeExpiredTrash = PurgeExpiredTrashUseCase(documents, groups, pages)
    private val emptyRecentlyDeleted = EmptyRecentlyDeletedUseCase(documents, groups, pages)

    // region helpers

    private suspend fun newFolder(title: String): String = createGroup(title).requireSuccess()

    private suspend fun newDocument(title: String, folderId: String? = null): String =
        createDocument(title, folderId).requireSuccess()

    /** Adds [count] processed pages (indexes 0..count-1) to a document and returns their ids. */
    private fun addPages(documentId: String, count: Int): List<String> = (0 until count).map { index ->
        val pageId = "$documentId-page-$index"
        library.upsertPage(
            ScanPage(
                id = pageId,
                documentId = documentId,
                pageIndex = index,
                rawImagePath = null,
                processedImagePath = null,
                thumbnailPath = "/thumbs/$pageId.jpg",
                rotationDegrees = 0,
                cropQuad = null,
                filterPreset = PageFilterPreset.AUTO,
                processingState = PageProcessingState.PROCESSED,
                createdAtMillis = baseTime,
                updatedAtMillis = baseTime,
            ),
        )
        pageId
    }

    private suspend fun bin(): List<TrashedItem> = observeRecentlyDeleted().first()

    private suspend fun binFolders(): List<TrashedFolder> = bin().filterIsInstance<TrashedFolder>()

    private suspend fun binDocumentIds(): List<String> =
        bin().filterIsInstance<TrashedDocument>().map { it.id }

    private suspend fun binPages(): List<TrashedPage> = bin().filterIsInstance<TrashedPage>()

    private suspend fun liveDocumentIds(): List<String> = observeLiveDocuments().first().map { it.id }

    /** Live page ids of a document in order, paired with their stored index. */
    private suspend fun livePageIndexes(documentId: String): List<Pair<String, Int>> =
        observePages(documentId).first().map { it.id to it.pageIndex }

    // endregion

    // region folders

    @Test
    fun trashingFolder_hidesFolderAndItsDocumentsFromLiveLists() = runBlocking {
        val folderId = newFolder("Taxes")
        val w2Id = newDocument("W2", folderId)
        val form1099Id = newDocument("1099", folderId)
        val looseId = newDocument("Loose")

        moveGroupToTrash(folderId, "Taxes").requireSuccess()

        assertEquals(listOf(looseId), liveDocumentIds())
        assertTrue(observeGroups().first().isEmpty())
        assertTrue(observeGroupDocuments(folderId).first().isEmpty())
        assertNotNull(observeDocument(w2Id).first()!!.deletedAtMillis)
        assertNotNull(observeDocument(form1099Id).first()!!.deletedAtMillis)
    }

    @Test
    fun binShowsFolderAsOneEntry_notItsDocuments() = runBlocking {
        val folderId = newFolder("Taxes")
        newDocument("W2", folderId)
        newDocument("1099", folderId)

        moveGroupToTrash(folderId, "Taxes").requireSuccess()

        val items = bin()
        assertEquals(1, items.size)
        val folder = items.single() as TrashedFolder
        assertEquals(folderId, folder.id)
        assertEquals("Taxes", folder.title)
        assertEquals(2, folder.documentCount)
        assertTrue(items.none { it is TrashedDocument })
    }

    @Test
    fun moveGroupToTrash_emitsFolderNotice() = runBlocking {
        val folderId = newFolder("Taxes")
        newDocument("W2", folderId)
        val notices = mutableListOf<TrashNotice>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            trashEvents.trashed.collect { notices += it }
        }

        moveGroupToTrash(folderId, "Taxes").requireSuccess()
        yield()
        collector.cancel()

        assertEquals(listOf(TrashNotice(TrashKind.FOLDER, folderId, "Taxes")), notices)
    }

    @Test
    fun restoringFolder_bringsBackFolderAndBothDocumentsInIt() = runBlocking {
        val folderId = newFolder("Taxes")
        val w2Id = newDocument("W2", folderId)
        val form1099Id = newDocument("1099", folderId)
        val looseId = newDocument("Loose")
        moveGroupToTrash(folderId, "Taxes").requireSuccess()

        restoreItem(TrashKind.FOLDER, folderId).requireSuccess()

        assertEquals(setOf(w2Id, form1099Id, looseId), liveDocumentIds().toSet())
        assertEquals(listOf(folderId), observeGroups().first().map { it.id })
        assertEquals(setOf(w2Id, form1099Id), observeGroupDocuments(folderId).first().map { it.id }.toSet())
        assertTrue(bin().isEmpty())
        assertNull(observeDocument(w2Id).first()!!.deletedAtMillis)
    }

    @Test
    fun documentDeletedOnItsOwnBeforeFolder_staysSeparate_andIsNotRestoredWithFolder() = runBlocking {
        val folderId = newFolder("Taxes")
        val soloId = newDocument("Solo", folderId)
        val withFolderId = newDocument("WithFolder", folderId)
        moveDocumentToTrash(soloId, "Solo").requireSuccess()
        moveGroupToTrash(folderId, "Taxes").requireSuccess()

        val binBeforeRestore = bin()
        assertEquals(2, binBeforeRestore.size)
        assertEquals(1, binFolders().single().documentCount)
        assertEquals(listOf(soloId), binDocumentIds())

        restoreItem(TrashKind.FOLDER, folderId).requireSuccess()

        assertEquals(listOf(soloId), binDocumentIds())
        assertTrue(binFolders().isEmpty())
        assertNotNull(observeDocument(soloId).first()!!.deletedAtMillis)
        assertEquals(listOf(withFolderId), observeGroupDocuments(folderId).first().map { it.id })
    }

    @Test
    fun documentDeletedOnItsOwnBeforeFolder_restoredWhileFolderIsTrashed_comesBackOutsideFolder() = runBlocking {
        val folderId = newFolder("Taxes")
        val soloId = newDocument("Solo", folderId)
        newDocument("WithFolder", folderId)
        moveDocumentToTrash(soloId, "Solo").requireSuccess()
        moveGroupToTrash(folderId, "Taxes").requireSuccess()

        restoreItem(TrashKind.DOCUMENT, soloId).requireSuccess()

        val restored = observeDocument(soloId).first()!!
        assertNull(restored.deletedAtMillis)
        assertNull(restored.groupId)
        assertTrue(liveDocumentIds().contains(soloId))
        assertTrue(binDocumentIds().isEmpty())
        assertEquals(1, binFolders().single().documentCount)
    }

    @Test
    fun deleteGroupForever_removesFolderAndItsDocumentsPermanently() = runBlocking {
        val folderId = newFolder("Taxes")
        val w2Id = newDocument("W2", folderId)
        val form1099Id = newDocument("1099", folderId)
        val looseId = newDocument("Loose")
        moveGroupToTrash(folderId, "Taxes").requireSuccess()

        deleteForever(TrashKind.FOLDER, folderId).requireSuccess()

        assertEquals(listOf(looseId), liveDocumentIds())
        assertTrue(bin().isEmpty())
        assertNull(observeDocument(w2Id).first())
        assertNull(observeDocument(form1099Id).first())
        assertTrue(observeGroups().first().isEmpty())
    }

    @Test
    fun deleteGroupForever_keepsDocumentThatWasDeletedOnItsOwn() = runBlocking {
        val folderId = newFolder("Taxes")
        val soloId = newDocument("Solo", folderId)
        newDocument("WithFolder", folderId)
        moveDocumentToTrash(soloId, "Solo").requireSuccess()
        moveGroupToTrash(folderId, "Taxes").requireSuccess()

        deleteForever(TrashKind.FOLDER, folderId).requireSuccess()

        assertEquals(listOf(soloId), binDocumentIds())
        assertTrue(binFolders().isEmpty())
        assertTrue(observeGroups().first().isEmpty())
        assertNull(observeDocument(soloId).first()!!.groupId)
    }

    // endregion

    // region pages

    @Test
    fun trashingMiddlePage_renumbersLivePages_andShowsPageInBin() = runBlocking {
        val documentId = newDocument("Contract")
        val (firstPage, middlePage, lastPage) = addPages(documentId, 3)

        movePageToTrash(middlePage, 2).requireSuccess()

        assertEquals(listOf(firstPage to 0, lastPage to 1), livePageIndexes(documentId))
        assertEquals(2, observeDocument(documentId).first()!!.pageCount)
        val trashed = binPages().single()
        assertEquals(middlePage, trashed.id)
        assertEquals(2, trashed.pageNumber)
        assertEquals("Contract", trashed.documentTitle)
        assertEquals(documentId, trashed.documentId)
    }

    @Test
    fun movePageToTrash_emitsPageNotice() = runBlocking {
        val documentId = newDocument("Contract")
        val (_, middlePage, _) = addPages(documentId, 3)
        val notices = mutableListOf<TrashNotice>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            trashEvents.trashed.collect { notices += it }
        }

        movePageToTrash(middlePage, 2).requireSuccess()
        yield()
        collector.cancel()

        assertEquals(listOf(TrashNotice(TrashKind.PAGE, middlePage, "Page 2")), notices)
    }

    @Test
    fun restoringPage_putsItBackAtItsIndex_andShiftsLaterPages() = runBlocking {
        val documentId = newDocument("Contract")
        val (firstPage, middlePage, lastPage) = addPages(documentId, 3)
        movePageToTrash(middlePage, 2).requireSuccess()

        restoreItem(TrashKind.PAGE, middlePage).requireSuccess()

        assertEquals(
            listOf(firstPage to 0, middlePage to 1, lastPage to 2),
            livePageIndexes(documentId),
        )
        assertEquals(3, observeDocument(documentId).first()!!.pageCount)
        assertTrue(bin().isEmpty())
    }

    @Test
    fun restoringPage_clampsToEnd_whenDocumentHasFewerPagesNow() = runBlocking {
        val documentId = newDocument("Contract")
        val (firstPage, secondPage, lastPage) = addPages(documentId, 3)
        movePageToTrash(lastPage, 3).requireSuccess()
        movePageToTrash(firstPage, 1).requireSuccess()
        // Only the second page is live now, so the last page has nowhere to go but the end.
        assertEquals(listOf(secondPage to 0), livePageIndexes(documentId))

        restoreItem(TrashKind.PAGE, lastPage).requireSuccess()

        assertEquals(listOf(secondPage to 0, lastPage to 1), livePageIndexes(documentId))
        assertEquals(2, observeDocument(documentId).first()!!.pageCount)
        assertEquals(listOf(firstPage), binPages().map { it.id })
    }

    @Test
    fun pagesOfTrashedDocument_areNotListedInBinOnTheirOwn() = runBlocking {
        val documentId = newDocument("Contract")
        val (firstPage, _) = addPages(documentId, 2)
        movePageToTrash(firstPage, 1).requireSuccess()
        assertEquals(listOf(firstPage), binPages().map { it.id })

        moveDocumentToTrash(documentId, "Contract").requireSuccess()

        assertTrue(binPages().isEmpty())
        assertEquals(listOf(documentId), binDocumentIds())

        restoreItem(TrashKind.DOCUMENT, documentId).requireSuccess()

        assertEquals(listOf(firstPage), binPages().map { it.id })
        assertTrue(binDocumentIds().isEmpty())
    }

    // endregion

    // region purge and empty

    @Test
    fun purgeExpiredTrash_appliesPerKindRetention() = runBlocking {
        val now = baseTime + 100 * day

        val livePagesDocumentId = newDocument("Pages")
        val (oldPage, recentPage, _) = addPages(livePagesDocumentId, 3)
        library.setClock(now - 2 * day - 1)
        movePageToTrash(oldPage, 1).requireSuccess()
        library.setClock(now - day)
        movePageToTrash(recentPage, 2).requireSuccess()

        val oldDocumentId = newDocument("Old")
        library.setClock(now - 7 * day - 1)
        moveDocumentToTrash(oldDocumentId, "Old").requireSuccess()
        val recentDocumentId = newDocument("Recent")
        library.setClock(now - 6 * day)
        moveDocumentToTrash(recentDocumentId, "Recent").requireSuccess()

        val oldFolderId = newFolder("Old folder")
        val oldFolderDocumentId = newDocument("Inside old folder", oldFolderId)
        library.setClock(now - 7 * day - 1)
        moveGroupToTrash(oldFolderId, "Old folder").requireSuccess()
        val recentFolderId = newFolder("Recent folder")
        library.setClock(now - 6 * day)
        moveGroupToTrash(recentFolderId, "Recent folder").requireSuccess()

        val purged = purgeExpiredTrash(nowMillis = now).requireSuccess()

        // One page, one document and one folder are past their retention.
        assertEquals(3, purged)
        assertEquals(listOf(recentPage), binPages().map { it.id })
        assertEquals(listOf(recentDocumentId), binDocumentIds())
        assertEquals(listOf(recentFolderId), binFolders().map { it.id })
        assertNull(observeDocument(oldDocumentId).first())
        assertNull(observeDocument(oldFolderDocumentId).first())
    }

    @Test
    fun emptyRecentlyDeleted_removesPagesFoldersAndDocuments_returnsTotal_andLeavesLiveItems() = runBlocking {
        val liveDocumentId = newDocument("Live")
        val livePages = addPages(liveDocumentId, 2)
        val trashedPageDocumentId = newDocument("Pages")
        val trashedPage = addPages(trashedPageDocumentId, 2).first()
        movePageToTrash(trashedPage, 1).requireSuccess()

        val folderId = newFolder("Taxes")
        newDocument("W2", folderId)
        newDocument("1099", folderId)
        moveGroupToTrash(folderId, "Taxes").requireSuccess()

        val soloId = newDocument("Solo")
        moveDocumentToTrash(soloId, "Solo").requireSuccess()

        val removed = emptyRecentlyDeleted().requireSuccess()

        // One page, one folder (its two documents go with it) and one document.
        assertEquals(3, removed)
        assertTrue(bin().isEmpty())
        assertNull(observeDocument(soloId).first())
        assertEquals(setOf(liveDocumentId, trashedPageDocumentId), liveDocumentIds().toSet())
        assertEquals(livePages, observePages(liveDocumentId).first().map { it.id })
        assertEquals(2, observeDocument(liveDocumentId).first()!!.pageCount)
    }

    // endregion
}
