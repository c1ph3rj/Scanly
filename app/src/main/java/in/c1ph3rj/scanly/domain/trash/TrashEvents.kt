package `in`.c1ph3rj.scanly.domain.trash

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Something that was just moved to Recently deleted, for the app-wide "Undo" snackbar. */
data class TrashNotice(
    val kind: TrashKind,
    val id: String,
    /** Short name for the snackbar: a document or folder title, or "Page 3". */
    val label: String,
)

/**
 * App-wide stream of items moved to Recently deleted. The snackbar lives above the navigation
 * host, so Undo still shows after the deleting screen (e.g. document detail) closes.
 */
@Singleton
class TrashEvents @Inject constructor() {
    private val _trashed = MutableSharedFlow<TrashNotice>(extraBufferCapacity = 4)
    val trashed: SharedFlow<TrashNotice> = _trashed.asSharedFlow()

    fun notifyTrashed(notice: TrashNotice) {
        _trashed.tryEmit(notice)
    }
}
