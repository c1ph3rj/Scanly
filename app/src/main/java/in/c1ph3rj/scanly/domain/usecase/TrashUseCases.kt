package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.TrashedDocument
import `in`.c1ph3rj.scanly.domain.model.TrashedItem
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import `in`.c1ph3rj.scanly.domain.repository.GroupRepository
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import `in`.c1ph3rj.scanly.domain.trash.TrashEvents
import `in`.c1ph3rj.scanly.domain.trash.TrashKind
import `in`.c1ph3rj.scanly.domain.trash.TrashNotice
import `in`.c1ph3rj.scanly.domain.trash.TrashPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Moves a document to Recently deleted and offers Undo app-wide. */
class MoveDocumentToTrashUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val trashEvents: TrashEvents,
) {
    suspend operator fun invoke(documentId: String, title: String): ScanlyResult<Unit> =
        repository.moveDocumentToTrash(documentId).also { result ->
            if (result is ScanlyResult.Success) {
                trashEvents.notifyTrashed(TrashNotice(TrashKind.DOCUMENT, documentId, title))
            }
        }
}

/**
 * Moves a folder to Recently deleted and offers Undo app-wide. With [includeDocuments] false
 * ("Delete folder only") its documents stay in the library and rejoin the folder if it comes back.
 */
class MoveGroupToTrashUseCase @Inject constructor(
    private val repository: GroupRepository,
    private val trashEvents: TrashEvents,
) {
    suspend operator fun invoke(groupId: String, title: String, includeDocuments: Boolean = true): ScanlyResult<Unit> =
        repository.moveGroupToTrash(groupId, includeDocuments).also { result ->
            if (result is ScanlyResult.Success) {
                trashEvents.notifyTrashed(TrashNotice(TrashKind.FOLDER, groupId, title))
            }
        }
}

/** Moves a page to Recently deleted and offers Undo app-wide. */
class MovePageToTrashUseCase @Inject constructor(
    private val repository: PageRepository,
    private val trashEvents: TrashEvents,
) {
    /** [pageNumber] is 1-based, for the "Page 3 moved to Recently deleted" snackbar. */
    suspend operator fun invoke(pageId: String, pageNumber: Int): ScanlyResult<Unit> =
        repository.movePageToTrash(pageId).also { result ->
            if (result is ScanlyResult.Success) {
                trashEvents.notifyTrashed(TrashNotice(TrashKind.PAGE, pageId, "Page $pageNumber"))
            }
        }
}

/** Everything in Recently deleted: folders, then documents, then pages, each newest first. */
class ObserveRecentlyDeletedUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val groupRepository: GroupRepository,
    private val pageRepository: PageRepository,
) {
    operator fun invoke(): Flow<List<TrashedItem>> = combine(
        groupRepository.observeTrashedGroups(),
        documentRepository.observeTrashedDocuments(),
        pageRepository.observeTrashedPages(),
    ) { folders, documents, pages ->
        folders + documents.mapNotNull { document ->
            document.deletedAtMillis?.let { deletedAt ->
                TrashedDocument(document, deletedAt)
            }
        } + pages
    }
}

class RestoreTrashedItemUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val groupRepository: GroupRepository,
    private val pageRepository: PageRepository,
) {
    suspend operator fun invoke(kind: TrashKind, id: String): ScanlyResult<Unit> = when (kind) {
        TrashKind.DOCUMENT -> documentRepository.restoreDocument(id)
        TrashKind.FOLDER -> groupRepository.restoreGroup(id)
        TrashKind.PAGE -> pageRepository.restorePage(id)
    }
}

class DeleteTrashedItemForeverUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val groupRepository: GroupRepository,
    private val pageRepository: PageRepository,
) {
    suspend operator fun invoke(kind: TrashKind, id: String): ScanlyResult<Unit> = when (kind) {
        TrashKind.DOCUMENT -> documentRepository.deleteDocument(id)
        TrashKind.FOLDER -> groupRepository.deleteGroupForever(id)
        TrashKind.PAGE -> pageRepository.deletePageForever(id)
    }
}

/** Permanently deletes everything in Recently deleted. @return number of items removed */
class EmptyRecentlyDeletedUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val groupRepository: GroupRepository,
    private val pageRepository: PageRepository,
) {
    suspend operator fun invoke(): ScanlyResult<Int> = sumOrFirstFailure(
        // Pages first: a folder or document removal would cascade them away uncounted.
        pageRepository.emptyTrashedPages(),
        groupRepository.emptyTrashedGroups(),
        documentRepository.emptyTrash(),
    )
}

/**
 * Removes whatever has been in Recently deleted longer than its kind's retention:
 * pages after [TrashKind.PAGE] days, documents and folders after theirs.
 */
class PurgeExpiredTrashUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val groupRepository: GroupRepository,
    private val pageRepository: PageRepository,
) {
    suspend operator fun invoke(nowMillis: Long = System.currentTimeMillis()): ScanlyResult<Int> = sumOrFirstFailure(
        pageRepository.purgeTrashedPagesBefore(TrashPolicy.purgeCutoff(TrashKind.PAGE, nowMillis)),
        groupRepository.purgeTrashedGroupsBefore(TrashPolicy.purgeCutoff(TrashKind.FOLDER, nowMillis)),
        documentRepository.purgeTrashDeletedBefore(TrashPolicy.purgeCutoff(TrashKind.DOCUMENT, nowMillis)),
    )
}

private fun sumOrFirstFailure(vararg results: ScanlyResult<Int>): ScanlyResult<Int> {
    var total = 0
    results.forEach { result ->
        when (result) {
            is ScanlyResult.Success -> total += result.value
            is ScanlyResult.Failure -> return result
        }
    }
    return ScanlyResult.Success(total)
}
