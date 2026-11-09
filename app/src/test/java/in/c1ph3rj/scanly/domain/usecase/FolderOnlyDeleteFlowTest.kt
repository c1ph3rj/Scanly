package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.TrashedDocument
import `in`.c1ph3rj.scanly.domain.model.TrashedFolder
import `in`.c1ph3rj.scanly.domain.model.TrashedItem
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Delete folder only": the folder goes to the bin and its documents stay in the library. */
class FolderOnlyDeleteFlowTest {
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
    private val observeRecentlyDeleted = ObserveRecentlyDeletedUseCase(documents, groups, pages)
    private val moveDocumentToTrash = MoveDocumentToTrashUseCase(documents, trashEvents)
    private val moveGroupToTrash = MoveGroupToTrashUseCase(groups, trashEvents)
    private val restoreItem = RestoreTrashedItemUseCase(documents, groups, pages)
    private val deleteForever = DeleteTrashedItemForeverUseCase(documents, groups, pages)

    // region helpers

    private suspend fun newFolder(title: String): String = createGroup(title).requireSuccess()

    private suspend fun newDocument(title: String, folderId: String? = null): String =
        createDocument(title, folderId).requireSuccess()

    private suspend fun bin(): List<TrashedItem> = observeRecentlyDeleted().first()

    private suspend fun binFolders(): List<TrashedFolder> = bin().filterIsInstance<TrashedFolder>()

    private suspend fun binDocumentIds(): List<String> =
        bin().filterIsInstance<TrashedDocument>().map { it.id }

    private suspend fun liveDocumentIds(): List<String> = observeLiveDocuments().first().map { it.id }

    private suspend fun folderDocumentIds(folderId: String): Set<String> =
        observeGroupDocuments(folderId).first().map { it.id }.toSet()

    // endregion

    @Test
    fun folderOnlyDelete_keepsDocumentsLiveAndUngrouped_andBinShowsOneFolderWithKeptCount() = runBlocking {
        val folderId = newFolder("Taxes")
        val w2Id = newDocument("W2", folderId)
        val form1099Id = newDocument("1099", folderId)
        val looseId = newDocument("Loose")

        moveGroupToTrash(folderId, "Taxes", includeDocuments = false).requireSuccess()

        assertTrue(observeGroups().first().isEmpty())
        assertEquals(setOf(w2Id, form1099Id, looseId), liveDocumentIds().toSet())
        assertNull(observeDocument(w2Id).first()!!.groupId)
        assertNull(observeDocument(form1099Id).first()!!.groupId)
        assertNull(observeDocument(w2Id).first()!!.deletedAtMillis)

        val items = bin()
        assertEquals(1, items.size)
        val folder = items.single() as TrashedFolder
        assertEquals(folderId, folder.id)
        assertEquals(0, folder.documentCount)
        assertEquals(2, folder.keptDocumentCount)
        assertTrue(items.none { it is TrashedDocument })
    }

    @Test
    fun folderOnlyDelete_emitsFolderNotice() = runBlocking {
        val folderId = newFolder("Taxes")
        newDocument("W2", folderId)
        val notices = mutableListOf<TrashNotice>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            trashEvents.trashed.collect { notices += it }
        }

        moveGroupToTrash(folderId, "Taxes", includeDocuments = false).requireSuccess()
        yield()
        collector.cancel()

        assertEquals(listOf(TrashNotice(TrashKind.FOLDER, folderId, "Taxes")), notices)
    }

    @Test
    fun restoringFolderOnlyDelete_putsBothDocumentsBackInFolder() = runBlocking {
        val folderId = newFolder("Taxes")
        val w2Id = newDocument("W2", folderId)
        val form1099Id = newDocument("1099", folderId)
        val looseId = newDocument("Loose")
        moveGroupToTrash(folderId, "Taxes", includeDocuments = false).requireSuccess()

        restoreItem(TrashKind.FOLDER, folderId).requireSuccess()

        assertEquals(listOf(folderId), observeGroups().first().map { it.id })
        assertEquals(setOf(w2Id, form1099Id), folderDocumentIds(folderId))
        assertEquals(folderId, observeDocument(w2Id).first()!!.groupId)
        assertEquals(folderId, observeDocument(form1099Id).first()!!.groupId)
        assertNull(observeDocument(looseId).first()!!.groupId)
        assertTrue(binFolders().isEmpty())
        assertTrue(bin().isEmpty())
    }

    @Test
    fun restoringFolderOnlyDelete_documentMovedToAnotherFolderFirst_staysInThatFolder() = runBlocking {
        val folderId = newFolder("Taxes")
        val otherFolderId = newFolder("Receipts")
        val w2Id = newDocument("W2", folderId)
        val form1099Id = newDocument("1099", folderId)
        moveGroupToTrash(folderId, "Taxes", includeDocuments = false).requireSuccess()
        groups.setDocumentGroup(w2Id, otherFolderId).requireSuccess()

        restoreItem(TrashKind.FOLDER, folderId).requireSuccess()

        assertEquals(setOf(w2Id), folderDocumentIds(otherFolderId))
        assertEquals(setOf(form1099Id), folderDocumentIds(folderId))
        assertEquals(otherFolderId, observeDocument(w2Id).first()!!.groupId)
    }

    @Test
    fun restoringFolderOnlyDelete_documentMovedInAndOutByHand_isNotPulledBack() = runBlocking {
        val folderId = newFolder("Taxes")
        val otherFolderId = newFolder("Receipts")
        val w2Id = newDocument("W2", folderId)
        moveGroupToTrash(folderId, "Taxes", includeDocuments = false).requireSuccess()
        groups.setDocumentGroup(w2Id, otherFolderId).requireSuccess()
        groups.setDocumentGroup(w2Id, null).requireSuccess()

        restoreItem(TrashKind.FOLDER, folderId).requireSuccess()

        assertNull(observeDocument(w2Id).first()!!.groupId)
        assertTrue(folderDocumentIds(folderId).isEmpty())
    }

    @Test
    fun restoringFolderOnlyDelete_documentTrashedOnItsOwnFirst_staysInBin() = runBlocking {
        val folderId = newFolder("Taxes")
        val w2Id = newDocument("W2", folderId)
        val form1099Id = newDocument("1099", folderId)
        moveGroupToTrash(folderId, "Taxes", includeDocuments = false).requireSuccess()
        moveDocumentToTrash(w2Id, "W2").requireSuccess()

        val binBeforeRestore = bin()
        assertEquals(2, binBeforeRestore.size)
        assertEquals(1, (binBeforeRestore.single { it is TrashedFolder } as TrashedFolder).keptDocumentCount)
        assertEquals(listOf(w2Id), binDocumentIds())

        restoreItem(TrashKind.FOLDER, folderId).requireSuccess()

        assertEquals(listOf(w2Id), binDocumentIds())
        assertNull(observeDocument(w2Id).first()!!.groupId)
        assertNotNullDeletedAt(w2Id)
        assertEquals(setOf(form1099Id), folderDocumentIds(folderId))
        assertTrue(binFolders().isEmpty())
    }

    @Test
    fun deleteFolderOnlyEntryForever_removesFolder_keepsReleasedDocumentsLiveAndUngrouped() = runBlocking {
        val folderId = newFolder("Taxes")
        val w2Id = newDocument("W2", folderId)
        val form1099Id = newDocument("1099", folderId)
        val looseId = newDocument("Loose")
        moveGroupToTrash(folderId, "Taxes", includeDocuments = false).requireSuccess()

        deleteForever(TrashKind.FOLDER, folderId).requireSuccess()

        assertTrue(observeGroups().first().isEmpty())
        assertTrue(binFolders().isEmpty())
        assertTrue(bin().isEmpty())
        assertEquals(setOf(w2Id, form1099Id, looseId), liveDocumentIds().toSet())
        assertNull(observeDocument(w2Id).first()!!.groupId)
        assertNull(observeDocument(form1099Id).first()!!.groupId)
        assertNull(library.releasedFromGroupId(w2Id))
        assertNull(library.releasedFromGroupId(form1099Id))

        // A new folder gets a new id, so it never pulls the released documents in.
        val newerFolderId = newFolder("Taxes")
        moveGroupToTrash(newerFolderId, "Taxes", includeDocuments = true).requireSuccess()
        restoreItem(TrashKind.FOLDER, newerFolderId).requireSuccess()

        assertTrue(folderDocumentIds(newerFolderId).isEmpty())
        assertNull(observeDocument(w2Id).first()!!.groupId)
        assertNull(observeDocument(form1099Id).first()!!.groupId)
        assertEquals(setOf(w2Id, form1099Id, looseId), liveDocumentIds().toSet())
    }

    @Test
    fun folderTrashedWithDocuments_stillGoesToBinWithItsDocuments() = runBlocking {
        val folderId = newFolder("Taxes")
        val w2Id = newDocument("W2", folderId)
        val form1099Id = newDocument("1099", folderId)

        moveGroupToTrash(folderId, "Taxes").requireSuccess()

        val items = bin()
        assertEquals(1, items.size)
        val folder = items.single() as TrashedFolder
        assertEquals(2, folder.documentCount)
        assertEquals(0, folder.keptDocumentCount)
        assertTrue(items.none { it is TrashedDocument })
        assertNotNullDeletedAt(w2Id)
        assertNotNullDeletedAt(form1099Id)
        assertTrue(liveDocumentIds().isEmpty())
    }

    private suspend fun assertNotNullDeletedAt(documentId: String) {
        assertTrue(observeDocument(documentId).first()!!.deletedAtMillis != null)
    }
}
