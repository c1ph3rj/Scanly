package `in`.c1ph3rj.scanly.testing

import `in`.c1ph3rj.scanly.data.archive.LibraryArchivePolicy
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.RestoreMode
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Contract-complete in-memory library used by JVM flow tests. Title uniqueness and
 * restore copy naming delegate to the shipped helpers the real repositories use.
 */
class InMemoryScanlyLibrary {
    private val clock = AtomicLong(1_700_000_000_000L)

    private val documentsState = MutableStateFlow<List<ScanDocument>>(emptyList())
    private val groupsState = MutableStateFlow<List<DocumentGroup>>(emptyList())
    private val pagesState = MutableStateFlow<List<ScanPage>>(emptyList())

    val documents: StateFlow<List<ScanDocument>> = documentsState.asStateFlow()
    val groups: StateFlow<List<DocumentGroup>> = groupsState.asStateFlow()
    val pages: StateFlow<List<ScanPage>> = pagesState.asStateFlow()

    fun now(): Long = clock.getAndAdd(1_000L)

    fun document(id: String): ScanDocument? = documentsState.value.firstOrNull { it.id == id }

    fun group(id: String): DocumentGroup? = groupsState.value.firstOrNull { it.id == id }

    fun page(id: String): ScanPage? = pagesState.value.firstOrNull { it.id == id }

    fun pagesFor(documentId: String): List<ScanPage> =
        pagesState.value.filter { it.documentId == documentId }.sortedBy { it.pageIndex }

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

    fun deleteDocument(documentId: String) {
        documentsState.value = documentsState.value.filterNot { it.id == documentId }
        pagesState.value = pagesState.value.filterNot { it.documentId == documentId }
        refreshDerivedState()
    }

    fun deleteGroup(groupId: String) {
        groupsState.value = groupsState.value.filterNot { it.id == groupId }
        documentsState.value = documentsState.value.map { document ->
            if (document.groupId == groupId) {
                document.copy(groupId = null, updatedAtMillis = now())
            } else {
                document
            }
        }
        refreshDerivedState()
    }

    fun deletePage(pageId: String) {
        val page = page(pageId) ?: error("Page not found.")
        val remaining = pagesFor(page.documentId).filterNot { it.id == pageId }
            .mapIndexed { index, remainingPage -> remainingPage.copy(pageIndex = index) }
        replacePages(page.documentId, remaining)
    }

    fun snapshot(): LibrarySnapshot = LibrarySnapshot(
        documents = documentsState.value,
        groups = groupsState.value,
        pages = pagesState.value,
    )

    fun restore(snapshot: LibrarySnapshot, mode: RestoreMode) {
        val groupIds = snapshot.groups.associate { it.id to UUID.randomUUID().toString() }
        val documentIds = snapshot.documents.associate { it.id to UUID.randomUUID().toString() }
        val pageIds = snapshot.pages.associate { it.id to UUID.randomUUID().toString() }
        val usedGroupTitles = if (mode == RestoreMode.MERGE) {
            groupsState.value.mapTo(mutableSetOf()) { it.title.lowercase() }
        } else {
            mutableSetOf()
        }
        val usedDocumentTitles = if (mode == RestoreMode.MERGE) {
            documentsState.value.mapTo(mutableSetOf()) { it.title.lowercase() }
        } else {
            mutableSetOf()
        }
        val restoredGroups = snapshot.groups.map { group ->
            group.copy(
                id = groupIds.getValue(group.id),
                title = LibraryArchivePolicy.uniqueRestoredTitle(group.title, usedGroupTitles),
            )
        }
        val restoredDocuments = snapshot.documents.map { document ->
            document.copy(
                id = documentIds.getValue(document.id),
                title = LibraryArchivePolicy.uniqueRestoredTitle(document.title, usedDocumentTitles),
                groupId = document.groupId?.let(groupIds::getValue),
                rootDirectoryPath = "/documents/${documentIds.getValue(document.id)}",
            )
        }
        val restoredPages = snapshot.pages.map { page ->
            page.copy(
                id = pageIds.getValue(page.id),
                documentId = documentIds.getValue(page.documentId),
            )
        }
        if (mode == RestoreMode.REPLACE) {
            documentsState.value = restoredDocuments
            groupsState.value = restoredGroups
            pagesState.value = restoredPages
        } else {
            documentsState.value = documentsState.value + restoredDocuments
            groupsState.value = groupsState.value + restoredGroups
            pagesState.value = pagesState.value + restoredPages
        }
        refreshDerivedState()
    }

    fun clearAll() {
        documentsState.value = emptyList()
        groupsState.value = emptyList()
        pagesState.value = emptyList()
    }

    private fun refreshDerivedState() {
        val pagesByDocument = pagesState.value.groupBy { it.documentId }
        documentsState.value = documentsState.value.map { document ->
            val documentPages = pagesByDocument[document.id].orEmpty().sortedBy { it.pageIndex }
            document.copy(
                pageCount = documentPages.size,
                coverThumbnailPath = documentPages.firstOrNull()?.thumbnailPath
                    ?: document.coverThumbnailPath,
            )
        }
        val documentsByGroup = documentsState.value.groupBy { it.groupId }
        groupsState.value = groupsState.value.map { group ->
            val members = documentsByGroup[group.id].orEmpty()
            group.copy(
                documentCount = members.size,
                totalPageCount = members.sumOf { it.pageCount },
                coverThumbnailPath = members.firstNotNullOfOrNull { it.coverThumbnailPath },
            )
        }
    }
}

data class LibrarySnapshot(
    val documents: List<ScanDocument>,
    val groups: List<DocumentGroup>,
    val pages: List<ScanPage>,
)
