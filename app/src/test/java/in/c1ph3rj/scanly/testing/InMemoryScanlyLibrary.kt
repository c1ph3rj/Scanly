package `in`.c1ph3rj.scanly.testing

import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.TrashedFolder
import `in`.c1ph3rj.scanly.domain.model.TrashedPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** A trashed page plus what it needs to return to the same place. */
data class TrashedPageRecord(
    val page: ScanPage,
    val deletedAtMillis: Long,
    /** 0-based index the page had when it was deleted. */
    val deletedFromIndex: Int,
)

/**
 * Contract-complete in-memory library used by JVM flow tests.
 *
 * Mirrors the Room-backed repositories:
 * - trashed documents keep their row with [ScanDocument.deletedAtMillis];
 * - a folder in Recently deleted is tracked in [groupTrashedAt];
 * - documents that went to the bin together with their folder are flagged in
 *   [documentsDeletedWithGroup];
 * - trashed pages live in [trashedPageRecordsState], so live page lists and counts never see them.
 */
class InMemoryScanlyLibrary {
    private val clock = AtomicLong(1_700_000_000_000L)

    /** Every document row, trashed ones included. */
    private val documentsState = MutableStateFlow<List<ScanDocument>>(emptyList())

    /** Every folder row, trashed ones included. Stats count live documents only. */
    private val groupsState = MutableStateFlow<List<DocumentGroup>>(emptyList())

    /** Live pages only. */
    private val pagesState = MutableStateFlow<List<ScanPage>>(emptyList())

    /** Folder id -> when it was moved to Recently deleted. */
    private val groupTrashedAt = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Ids of documents that went to Recently deleted together with their folder. */
    private val documentsDeletedWithGroup = MutableStateFlow<Set<String>>(emptySet())

    /** Document id -> folder it was released from by a "Delete folder only" delete. */
    private val releasedFromGroupState = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Page id -> trashed page. */
    private val trashedPageRecordsState = MutableStateFlow<Map<String, TrashedPageRecord>>(emptyMap())

    private val liveGroupsState = MutableStateFlow<List<DocumentGroup>>(emptyList())
    private val trashedFoldersState = MutableStateFlow<List<TrashedFolder>>(emptyList())
    private val trashedDocumentsState = MutableStateFlow<List<ScanDocument>>(emptyList())
    private val trashedPagesState = MutableStateFlow<List<TrashedPage>>(emptyList())

    /** Every document row, trashed ones included. */
    val documents: StateFlow<List<ScanDocument>> = documentsState.asStateFlow()

    /** Folders that are not in Recently deleted. */
    val groups: StateFlow<List<DocumentGroup>> = liveGroupsState.asStateFlow()

    /** Live pages only. */
    val pages: StateFlow<List<ScanPage>> = pagesState.asStateFlow()

    val trashedFolders: StateFlow<List<TrashedFolder>> = trashedFoldersState.asStateFlow()

    /** Documents deleted on their own (not with a folder). */
    val trashedDocuments: StateFlow<List<ScanDocument>> = trashedDocumentsState.asStateFlow()

    /** Trashed pages of live documents. */
    val trashedPages: StateFlow<List<TrashedPage>> = trashedPagesState.asStateFlow()

    /** Returns the current fake time and advances it by one second. */
    fun now(): Long = clock.getAndAdd(1_000L)

    /** Sets the fake clock so the next [now] call returns exactly [millis]. */
    fun setClock(millis: Long) {
        clock.set(millis)
    }

    fun document(id: String): ScanDocument? = documentsState.value.firstOrNull { it.id == id }

    /** Folder by id, whether it is live or in Recently deleted. */
    fun group(id: String): DocumentGroup? = groupsState.value.firstOrNull { it.id == id }

    /** Live page by id. Trashed pages are not found here; use [trashedPageRecord]. */
    fun page(id: String): ScanPage? = pagesState.value.firstOrNull { it.id == id }

    /** Live pages of a document, in order. */
    fun pagesFor(documentId: String): List<ScanPage> =
        pagesState.value.filter { it.documentId == documentId }.sortedBy { it.pageIndex }

    fun trashedPageRecord(pageId: String): TrashedPageRecord? = trashedPageRecordsState.value[pageId]

    fun trashedPageRecords(): Collection<TrashedPageRecord> = trashedPageRecordsState.value.values

    /** Trashed folder id -> deletion time. */
    fun trashedGroupTimes(): Map<String, Long> = groupTrashedAt.value

    fun isGroupTrashed(groupId: String): Boolean = groupTrashedAt.value.containsKey(groupId)

    fun isDocumentDeletedWithGroup(documentId: String): Boolean =
        documentId in documentsDeletedWithGroup.value

    /** Folder a document was released from by "Delete folder only", or null once that is forgotten. */
    fun releasedFromGroupId(documentId: String): String? = releasedFromGroupState.value[documentId]

    /** A move by hand overrides "rejoin the folder if it is restored" (mirrors setDocumentGroup in the DB repo). */
    fun forgetRelease(documentId: String) {
        releasedFromGroupState.value = releasedFromGroupState.value - documentId
        refreshDerivedState()
    }

    fun upsertDocument(document: ScanDocument) {
        documentsState.value = documentsState.value
            .filterNot { it.id == document.id } + document
        refreshDerivedState()
    }

    fun upsertGroup(group: DocumentGroup) {
        groupsState.value = groupsState.value
            .filterNot { it.id == group.id } + group
        refreshDerivedState()
    }

    fun replacePages(documentId: String, pages: List<ScanPage>) {
        pagesState.value = pagesState.value.filterNot { it.documentId == documentId } + pages
        refreshDerivedState()
    }

    fun upsertPage(page: ScanPage) {
        val remaining = pagesState.value.filterNot { it.id == page.id }
        pagesState.value = remaining + page
        refreshDerivedState()
    }

    /** Permanently removes a document with its live and trashed pages. */
    fun deleteDocument(documentId: String) {
        documentsState.value = documentsState.value.filterNot { it.id == documentId }
        pagesState.value = pagesState.value.filterNot { it.documentId == documentId }
        trashedPageRecordsState.value = trashedPageRecordsState.value
            .filterValues { it.page.documentId != documentId }
        documentsDeletedWithGroup.value = documentsDeletedWithGroup.value - documentId
        releasedFromGroupState.value = releasedFromGroupState.value - documentId
        refreshDerivedState()
    }

    /** Permanently removes a folder. Its documents stay, moved out of it (the DB sets groupId to NULL). */
    fun deleteGroup(groupId: String) {
        groupsState.value = groupsState.value.filterNot { it.id == groupId }
        groupTrashedAt.value = groupTrashedAt.value - groupId
        documentsState.value = documentsState.value.map { document ->
            if (document.groupId == groupId) {
                document.copy(groupId = null, updatedAtMillis = now())
            } else {
                document
            }
        }
        refreshDerivedState()
    }

    /**
     * Moves a folder to Recently deleted. With [includeDocuments] its live documents go too, flagged
     * as deleted-with-folder: restoring the folder brings them back and deleting it removes them.
     * Without it ("Delete folder only") they leave the folder, stay live and remember the folder in
     * [releasedFromGroupId], so restoring the folder puts them back.
     */
    fun trashGroup(groupId: String, deletedAtMillis: Long, includeDocuments: Boolean = true) {
        val memberIds = documentsState.value
            .filter { it.groupId == groupId && it.deletedAtMillis == null }
            .map { it.id }
            .toSet()
        groupTrashedAt.value = groupTrashedAt.value + (groupId to deletedAtMillis)
        if (includeDocuments) {
            documentsState.value = documentsState.value.map { document ->
                if (document.id in memberIds) document.copy(deletedAtMillis = deletedAtMillis) else document
            }
            documentsDeletedWithGroup.value = documentsDeletedWithGroup.value + memberIds
        } else {
            documentsState.value = documentsState.value.map { document ->
                if (document.id in memberIds) document.copy(groupId = null) else document
            }
            releasedFromGroupState.value = releasedFromGroupState.value + memberIds.associateWith { groupId }
        }
        refreshDerivedState()
    }

    /**
     * Brings a trashed folder back. Documents that went to the bin with it return; released documents
     * that are still live and outside any folder rejoin it. The release marks for this folder are cleared.
     */
    fun restoreGroup(groupId: String) {
        val returningIds = documentsState.value
            .filter { it.groupId == groupId && it.id in documentsDeletedWithGroup.value }
            .map { it.id }
            .toSet()
        val rejoiningIds = documentsState.value
            .filter { document ->
                document.groupId == null &&
                    document.deletedAtMillis == null &&
                    releasedFromGroupState.value[document.id] == groupId
            }
            .map { it.id }
            .toSet()
        groupTrashedAt.value = groupTrashedAt.value - groupId
        documentsState.value = documentsState.value.map { document ->
            when (document.id) {
                in returningIds -> document.copy(deletedAtMillis = null)
                in rejoiningIds -> document.copy(groupId = groupId)
                else -> document
            }
        }
        documentsDeletedWithGroup.value = documentsDeletedWithGroup.value - returningIds
        forgetReleasedFrom(groupId)
        refreshDerivedState()
    }

    /** Permanently removes folders and the documents that went to the bin with them. Released documents stay. */
    fun deleteTrashedGroups(groupIds: Collection<String>) {
        val doomedDocumentIds = documentsState.value
            .filter { it.groupId in groupIds && it.id in documentsDeletedWithGroup.value }
            .map { it.id }
        doomedDocumentIds.forEach(::deleteDocument)
        groupIds.forEach { groupId ->
            forgetReleasedFrom(groupId)
            deleteGroup(groupId)
        }
    }

    private fun forgetReleasedFrom(groupId: String) {
        releasedFromGroupState.value = releasedFromGroupState.value.filterValues { it != groupId }
    }

    /** Permanently removes a live page; the remaining live pages renumber to 0..n-1. */
    fun deletePage(pageId: String) {
        val page = page(pageId) ?: error("Page not found.")
        replacePages(page.documentId, renumbered(pagesFor(page.documentId).filterNot { it.id == pageId }))
    }

    /**
     * Moves a live page to Recently deleted. The remaining live pages renumber to 0..n-1, and the
     * page remembers the index it had so a restore can put it back.
     */
    fun trashPage(pageId: String, deletedAtMillis: Long) {
        val page = page(pageId) ?: error("Page not found.")
        trashedPageRecordsState.value = trashedPageRecordsState.value +
            (pageId to TrashedPageRecord(page, deletedAtMillis, deletedFromIndex = page.pageIndex))
        replacePages(page.documentId, renumbered(pagesFor(page.documentId).filterNot { it.id == pageId }))
    }

    private fun renumbered(pages: List<ScanPage>): List<ScanPage> =
        pages.mapIndexed { index, page -> page.copy(pageIndex = index) }

    /**
     * Puts a trashed page back at the index it was deleted from, clamped to the live count.
     * Later live pages shift up by one.
     */
    fun restorePage(pageId: String) {
        val record = trashedPageRecord(pageId) ?: error("Page not found.")
        val documentId = record.page.documentId
        val livePages = pagesFor(documentId)
        val target = record.deletedFromIndex.coerceIn(0, livePages.size)
        val shifted = livePages.map { livePage ->
            if (livePage.pageIndex >= target) livePage.copy(pageIndex = livePage.pageIndex + 1) else livePage
        }
        trashedPageRecordsState.value = trashedPageRecordsState.value - pageId
        replacePages(documentId, shifted + record.page.copy(pageIndex = target))
    }

    /** Forgets trashed pages (permanent delete or purge). Live pages are untouched. */
    fun removeTrashedPages(pageIds: Collection<String>) {
        trashedPageRecordsState.value = trashedPageRecordsState.value - pageIds.toSet()
        refreshDerivedState()
    }

    fun clearAll() {
        documentsState.value = emptyList()
        groupsState.value = emptyList()
        pagesState.value = emptyList()
        groupTrashedAt.value = emptyMap()
        documentsDeletedWithGroup.value = emptySet()
        releasedFromGroupState.value = emptyMap()
        trashedPageRecordsState.value = emptyMap()
        refreshDerivedState()
    }

    private fun refreshDerivedState() {
        val livePagesByDocument = pagesState.value.groupBy { it.documentId }
        val documentsWithCounts = documentsState.value.map { document ->
            val documentPages = livePagesByDocument[document.id].orEmpty().sortedBy { it.pageIndex }
            document.copy(
                pageCount = documentPages.size,
                coverThumbnailPath = documentPages.firstOrNull()?.thumbnailPath
                    ?: document.coverThumbnailPath,
            )
        }
        documentsState.value = documentsWithCounts

        val trashedGroupTimes = groupTrashedAt.value
        val withGroupIds = documentsDeletedWithGroup.value

        val liveDocumentsByGroup = documentsWithCounts
            .filter { it.deletedAtMillis == null }
            .groupBy { it.groupId }
        groupsState.value = groupsState.value.map { group ->
            val members = liveDocumentsByGroup[group.id].orEmpty()
            group.copy(
                documentCount = members.size,
                totalPageCount = members.sumOf { it.pageCount },
                coverThumbnailPath = members.firstNotNullOfOrNull { it.coverThumbnailPath },
            )
        }
        liveGroupsState.value = groupsState.value.filterNot { trashedGroupTimes.containsKey(it.id) }

        trashedFoldersState.value = trashedGroupTimes.mapNotNull { (groupId, deletedAt) ->
            val group = groupsState.value.firstOrNull { it.id == groupId } ?: return@mapNotNull null
            val members = documentsWithCounts.filter { it.groupId == groupId && it.id in withGroupIds }
            val kept = documentsWithCounts.count { document ->
                document.groupId == null &&
                    document.deletedAtMillis == null &&
                    releasedFromGroupState.value[document.id] == groupId
            }
            TrashedFolder(
                id = groupId,
                title = group.title,
                documentCount = members.size,
                pageCount = members.sumOf { it.pageCount },
                coverThumbnailPath = members.firstOrNull()?.coverThumbnailPath,
                deletedAtMillis = deletedAt,
                keptDocumentCount = kept,
            )
        }.sortedByDescending { it.deletedAtMillis }

        // Documents deleted on their own. Ones that went with a folder are listed under that folder.
        trashedDocumentsState.value = documentsWithCounts
            .filter { it.deletedAtMillis != null && it.id !in withGroupIds }
            .sortedByDescending { it.deletedAtMillis }

        // Pages of a trashed document go and come back with it, so they are not listed on their own.
        trashedPagesState.value = trashedPageRecordsState.value.values.mapNotNull { record ->
            val document = documentsWithCounts.firstOrNull { it.id == record.page.documentId }
                ?: return@mapNotNull null
            if (document.deletedAtMillis != null) return@mapNotNull null
            TrashedPage(
                id = record.page.id,
                documentId = document.id,
                documentTitle = document.title,
                pageNumber = record.deletedFromIndex + 1,
                thumbnailPath = record.page.thumbnailPath,
                deletedAtMillis = record.deletedAtMillis,
            )
        }.sortedByDescending { it.deletedAtMillis }
    }
}
