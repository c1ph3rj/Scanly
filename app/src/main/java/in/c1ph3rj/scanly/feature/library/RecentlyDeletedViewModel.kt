package `in`.c1ph3rj.scanly.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.TrashedDocument
import `in`.c1ph3rj.scanly.domain.model.TrashedFolder
import `in`.c1ph3rj.scanly.domain.model.TrashedItem
import `in`.c1ph3rj.scanly.domain.model.TrashedPage
import `in`.c1ph3rj.scanly.domain.trash.TrashKind
import `in`.c1ph3rj.scanly.domain.trash.TrashPolicy
import `in`.c1ph3rj.scanly.domain.usecase.DeleteTrashedItemForeverUseCase
import `in`.c1ph3rj.scanly.domain.usecase.EmptyRecentlyDeletedUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveRecentlyDeletedUseCase
import `in`.c1ph3rj.scanly.domain.usecase.PurgeExpiredTrashUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RestoreTrashedItemUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row in Recently deleted and how long it has left before it is removed for good. */
data class RecentlyDeletedRow(
    val item: TrashedItem,
    val daysUntilPurge: Int,
) {
    /** Stable across kinds: a page and a document never share a key even if ids collide. */
    val key: String get() = "${item.kind.name}:${item.id}"
}

data class RecentlyDeletedUiState(
    val folders: List<RecentlyDeletedRow> = emptyList(),
    val documents: List<RecentlyDeletedRow> = emptyList(),
    val pages: List<RecentlyDeletedRow> = emptyList(),
    val isLoading: Boolean = true,
    /** [RecentlyDeletedRow.key]s with a restore or delete in flight; their rows ignore taps. */
    val busyKeys: Set<String> = emptySet(),
    val isEmptying: Boolean = false,
) {
    val itemCount: Int get() = folders.size + documents.size + pages.size

    val isEmpty: Boolean get() = !isLoading && itemCount == 0
}

sealed interface RecentlyDeletedEvent {
    data class ShowMessage(val message: String) : RecentlyDeletedEvent
}

@HiltViewModel
class RecentlyDeletedViewModel @Inject constructor(
    observeRecentlyDeletedUseCase: ObserveRecentlyDeletedUseCase,
    private val restoreTrashedItemUseCase: RestoreTrashedItemUseCase,
    private val deleteTrashedItemForeverUseCase: DeleteTrashedItemForeverUseCase,
    private val emptyRecentlyDeletedUseCase: EmptyRecentlyDeletedUseCase,
    purgeExpiredTrashUseCase: PurgeExpiredTrashUseCase,
) : ViewModel() {
    private val busy = MutableStateFlow(BusyState())

    // Buffered so a snackbar that is still showing never suspends the action that emitted the message.
    private val _events = MutableSharedFlow<RecentlyDeletedEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RecentlyDeletedEvent> = _events.asSharedFlow()

    val uiState: StateFlow<RecentlyDeletedUiState> = combine(
        observeRecentlyDeletedUseCase(),
        busy,
    ) { items, busyState ->
        val now = System.currentTimeMillis()
        val rows = items.map { item ->
            RecentlyDeletedRow(item, TrashPolicy.daysUntilPurge(item.kind, item.deletedAtMillis, now))
        }
        RecentlyDeletedUiState(
            folders = rows.filter { it.item is TrashedFolder },
            documents = rows.filter { it.item is TrashedDocument },
            pages = rows.filter { it.item is TrashedPage },
            isLoading = false,
            busyKeys = busyState.keys,
            isEmptying = busyState.emptying,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecentlyDeletedUiState(),
    )

    init {
        // The app only purges on start; pages expire after two days, so tidy up whenever the bin opens.
        viewModelScope.launch { purgeExpiredTrashUseCase() }
    }

    fun restore(row: RecentlyDeletedRow) = runForRow(row) {
        when (val result = restoreTrashedItemUseCase(row.item.kind, row.item.id)) {
            is ScanlyResult.Success -> emit(restoredMessage(row.item))
            is ScanlyResult.Failure -> emit(result.error.message)
        }
    }

    fun deleteForever(row: RecentlyDeletedRow) = runForRow(row) {
        when (val result = deleteTrashedItemForeverUseCase(row.item.kind, row.item.id)) {
            is ScanlyResult.Success -> emit("Deleted permanently")
            is ScanlyResult.Failure -> emit(result.error.message)
        }
    }

    /** Restores every row in [rows]; one message sums up the result. */
    fun restoreAll(rows: List<RecentlyDeletedRow>) = runForRows(rows) {
        // Folders first, so their documents are back before any of their pages.
        val outcome = runEach(rows.sortedBy { it.item.kind.restoreOrder() }) { row ->
            restoreTrashedItemUseCase(row.item.kind, row.item.id)
        }
        emit(outcome.message(verb = "Restored"))
    }

    /** Permanently deletes every row in [rows]; one message sums up the result. */
    fun deleteForeverAll(rows: List<RecentlyDeletedRow>) = runForRows(rows) {
        // Pages first, then documents, then folders: a parent removal would cascade its children uncounted.
        val outcome = runEach(rows.sortedByDescending { it.item.kind.restoreOrder() }) { row ->
            deleteTrashedItemForeverUseCase(row.item.kind, row.item.id)
        }
        emit(outcome.message(verb = "Deleted", suffix = " permanently"))
    }

    fun emptyAll() {
        if (busy.value.emptying) return
        viewModelScope.launch {
            busy.update { it.copy(emptying = true) }
            when (val result = emptyRecentlyDeletedUseCase()) {
                is ScanlyResult.Success -> emit("Recently deleted is empty")
                is ScanlyResult.Failure -> emit(result.error.message)
            }
            busy.update { it.copy(emptying = false) }
        }
    }

    private fun restoredMessage(item: TrashedItem): String = when (item) {
        is TrashedFolder -> "Folder restored"
        is TrashedDocument -> "Document restored"
        is TrashedPage -> "Page ${item.pageNumber} restored to \"${item.documentTitle}\""
    }

    private fun runForRow(row: RecentlyDeletedRow, block: suspend () -> Unit) {
        if (row.key in busy.value.keys || busy.value.emptying) return
        viewModelScope.launch {
            busy.update { it.copy(keys = it.keys + row.key) }
            try {
                block()
            } finally {
                busy.update { it.copy(keys = it.keys - row.key) }
            }
        }
    }

    private fun runForRows(rows: List<RecentlyDeletedRow>, block: suspend () -> Unit) {
        val keys = rows.map { it.key }.toSet()
        if (keys.isEmpty() || busy.value.emptying || keys.any { it in busy.value.keys }) return
        viewModelScope.launch {
            busy.update { it.copy(keys = it.keys + keys) }
            try {
                block()
            } finally {
                busy.update { it.copy(keys = it.keys - keys) }
            }
        }
    }

    private suspend fun runEach(
        rows: List<RecentlyDeletedRow>,
        action: suspend (RecentlyDeletedRow) -> ScanlyResult<Unit>,
    ): BatchOutcome {
        var succeeded = 0
        var firstError: String? = null
        rows.forEach { row ->
            when (val result = action(row)) {
                is ScanlyResult.Success -> succeeded++
                is ScanlyResult.Failure -> if (firstError == null) firstError = result.error.message
            }
        }
        return BatchOutcome(total = rows.size, succeeded = succeeded, firstError = firstError)
    }

    private suspend fun emit(message: String) {
        _events.emit(RecentlyDeletedEvent.ShowMessage(message))
    }

    private data class BatchOutcome(val total: Int, val succeeded: Int, val firstError: String?) {
        /** "Restored 3 items", or "Restored 2 of 3 items. <first error>" when some failed. */
        fun message(verb: String, suffix: String = ""): String {
            val noun = if (total == 1) "item" else "items"
            return if (firstError == null) {
                "$verb $total $noun$suffix"
            } else {
                "$verb $succeeded of $total $noun$suffix. $firstError"
            }
        }
    }

    private data class BusyState(
        val keys: Set<String> = emptySet(),
        val emptying: Boolean = false,
    )
}

/** Folders, then documents, then pages: parents come back before their children. */
private fun TrashKind.restoreOrder(): Int = when (this) {
    TrashKind.FOLDER -> 0
    TrashKind.DOCUMENT -> 1
    TrashKind.PAGE -> 2
}

/** Retention line shown under the bin's title. */
val recentlyDeletedRetentionNote: String =
    "Pages are kept for ${TrashKind.PAGE.retentionLabel()}, documents and folders for " +
        "${TrashKind.DOCUMENT.retentionLabel()}. After that they are deleted permanently."
