package `in`.c1ph3rj.scanly.feature.library

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.domain.trash.TrashEvents
import `in`.c1ph3rj.scanly.domain.trash.TrashKind
import `in`.c1ph3rj.scanly.domain.trash.TrashNotice
import `in`.c1ph3rj.scanly.domain.usecase.RestoreTrashedItemUseCase
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashUndoViewModel @Inject constructor(
    trashEvents: TrashEvents,
    private val restoreTrashedItemUseCase: RestoreTrashedItemUseCase,
) : ViewModel() {
    val trashed: SharedFlow<TrashNotice> = trashEvents.trashed

    fun undo(notice: TrashNotice) {
        viewModelScope.launch {
            // A failed restore leaves the item in Recently deleted, where it can still be restored.
            restoreTrashedItemUseCase(notice.kind, notice.id)
        }
    }
}

/**
 * "Moved to Recently deleted · Undo", shown above the navigation host so it survives the
 * deleting screen closing (deleting from document detail pops back to the library).
 */
@Composable
fun TrashUndoSnackbarHost(
    modifier: Modifier = Modifier,
    viewModel: TrashUndoViewModel = hiltViewModel(),
) {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.trashed.collect { notice ->
            // A newer delete replaces the current snackbar; its Undo only restores its own item.
            hostState.currentSnackbarData?.dismiss()
            launch {
                val result = hostState.showSnackbar(
                    message = notice.snackbarMessage(),
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undo(notice)
                }
            }
        }
    }
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { data ->
            Snackbar(
                action = {
                    data.visuals.actionLabel?.let { label ->
                        TextButton(onClick = { data.performAction() }) {
                            Text(text = label)
                        }
                    }
                },
            ) {
                Text(
                    text = data.visuals.message,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

private fun TrashNotice.snackbarMessage(): String = when (kind) {
    TrashKind.PAGE -> "$label moved to Recently deleted"
    TrashKind.DOCUMENT, TrashKind.FOLDER -> "\"$label\" moved to Recently deleted"
}
