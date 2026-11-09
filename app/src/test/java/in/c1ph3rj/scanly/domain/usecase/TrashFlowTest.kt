package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.TrashedDocument
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

class TrashFlowTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val baseTime = 1_700_000_000_000L
    private var clock = baseTime

    private val library = InMemoryScanlyLibrary()
    private val documents = InMemoryDocumentRepository(library, now = { clock })
    private val groups = InMemoryGroupRepository(library)
    private val pages = InMemoryPageRepository(library)
    private val trashEvents = TrashEvents()

    private val createDocument = CreateDocumentUseCase(documents)
    private val observeDocuments = ObserveDocumentsUseCase(documents)
    private val observeRecent = ObserveRecentDocumentsUseCase(documents)
    private val observeDocument = ObserveDocumentUseCase(documents)
    private val observeRecentlyDeleted = ObserveRecentlyDeletedUseCase(documents, groups, pages)
    private val moveToTrash = MoveDocumentToTrashUseCase(documents, trashEvents)
    private val restoreItem = RestoreTrashedItemUseCase(documents, groups, pages)
    private val purgeExpiredTrash = PurgeExpiredTrashUseCase(documents, groups, pages)
    private val emptyRecentlyDeleted = EmptyRecentlyDeletedUseCase(documents, groups, pages)

    /** Ids of the documents currently in Recently deleted on their own. */
    private suspend fun trashedDocumentIds(): List<String> =
        observeRecentlyDeleted().first().filterIsInstance<TrashedDocument>().map { it.id }

    @Test
    fun movingToTrash_hidesFromLiveLists_andShowsInTrash() = runBlocking {
        val leaseId = createDocument("Lease").requireSuccess()
        val receiptId = createDocument("Receipt").requireSuccess()

        moveToTrash(leaseId, "Lease").requireSuccess()

        assertEquals(listOf(receiptId), observeDocuments().first().map { it.id })
        assertEquals(listOf(receiptId), observeRecent(10).first().map { it.id })
        assertEquals(listOf(leaseId), trashedDocumentIds())

        val trashed = observeDocument(leaseId).first()
        assertNotNull(trashed)
        assertNotNull(trashed!!.deletedAtMillis)
    }

    @Test
    fun moveToTrash_emitsOneNoticeOnSuccess() = runBlocking {
        val leaseId = createDocument("Lease").requireSuccess()
        val notices = mutableListOf<TrashNotice>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            trashEvents.trashed.collect { notices += it }
        }

        moveToTrash(leaseId, "Lease").requireSuccess()
        yield()
        collector.cancel()

        assertEquals(listOf(TrashNotice(TrashKind.DOCUMENT, leaseId, "Lease")), notices)
    }

    @Test
    fun moveToTrash_missingDocument_failsAndEmitsNothing() = runBlocking {
        val notices = mutableListOf<TrashNotice>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            trashEvents.trashed.collect { notices += it }
        }

        val result = moveToTrash("does-not-exist", "Ghost")
        yield()
        collector.cancel()

        assertTrue(result is ScanlyResult.Failure)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun restore_putsDocumentBackInLibrary() = runBlocking {
        val leaseId = createDocument("Lease").requireSuccess()
        moveToTrash(leaseId, "Lease").requireSuccess()

        restoreItem(TrashKind.DOCUMENT, leaseId).requireSuccess()

        assertEquals(listOf(leaseId), observeDocuments().first().map { it.id })
        assertTrue(observeRecentlyDeleted().first().isEmpty())
        assertNull(observeDocument(leaseId).first()!!.deletedAtMillis)
    }

    @Test
    fun purgeExpiredTrash_removesOnlyDocumentsOlderThanRetention() = runBlocking {
        val oldId = createDocument("Old").requireSuccess()
        val recentId = createDocument("Recent").requireSuccess()
        val liveId = createDocument("Live").requireSuccess()

        clock = baseTime
        moveToTrash(oldId, "Old").requireSuccess()
        // Documents keep 7 days in the bin, so 30 days after the first trash the second one is 5 days old.
        clock = baseTime + 30 * day
        moveToTrash(recentId, "Recent").requireSuccess()

        val now = baseTime + 35 * day
        val purged = purgeExpiredTrash(nowMillis = now).requireSuccess()

        assertEquals(1, purged)
        assertEquals(listOf(recentId), trashedDocumentIds())
        assertNull(observeDocument(oldId).first())
        assertEquals(setOf(liveId), observeDocuments().first().map { it.id }.toSet())
    }

    @Test
    fun emptyRecentlyDeleted_removesAllTrashedDocuments_andLeavesLiveOnes() = runBlocking {
        val firstId = createDocument("First").requireSuccess()
        val secondId = createDocument("Second").requireSuccess()
        val liveId = createDocument("Live").requireSuccess()
        moveToTrash(firstId, "First").requireSuccess()
        moveToTrash(secondId, "Second").requireSuccess()

        val removed = emptyRecentlyDeleted().requireSuccess()

        assertEquals(2, removed)
        assertTrue(observeRecentlyDeleted().first().isEmpty())
        assertNull(observeDocument(firstId).first())
        assertNull(observeDocument(secondId).first())
        assertEquals(listOf(liveId), observeDocuments().first().map { it.id })
    }
}
