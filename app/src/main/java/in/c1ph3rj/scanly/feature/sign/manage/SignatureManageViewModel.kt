package `in`.c1ph3rj.scanly.feature.sign.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.SavedSignature
import `in`.c1ph3rj.scanly.domain.usecase.DeleteSignatureUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveSignaturesUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs "Your signatures": observes saved marks and removes them. */
@HiltViewModel
class SignatureManageViewModel @Inject constructor(
    observeSignaturesUseCase: ObserveSignaturesUseCase,
    private val deleteSignatureUseCase: DeleteSignatureUseCase,
) : ViewModel() {

    /** Saved signatures and initials, newest first as the repository emits them. */
    val signatures: StateFlow<List<SavedSignature>> = observeSignaturesUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val messages = Channel<String>(Channel.BUFFERED)

    /** One-shot messages for the screen to show in a snackbar. */
    val events: Flow<String> = messages.receiveAsFlow()

    /** Deletes the saved mark with [id]; a failure is reported through [events]. */
    fun delete(id: String) {
        viewModelScope.launch {
            when (val result = deleteSignatureUseCase(id)) {
                is ScanlyResult.Success -> Unit
                is ScanlyResult.Failure -> messages.send(result.error.message)
            }
        }
    }
}
