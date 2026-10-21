package `in`.c1ph3rj.scanly.feature.sign.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.SavedSignature
import `in`.c1ph3rj.scanly.domain.model.SignInk
import `in`.c1ph3rj.scanly.domain.usecase.DeleteSignatureUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ExportSignatureImageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.MoveSignatureToFrontUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveSignaturesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RecordPositiveMomentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SaveSignatureToGalleryUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SignatureManageEvent {
    data class ShowMessage(val message: String) : SignatureManageEvent

    /** A tinted PNG is ready at [filePath] for the share sheet. */
    data class ShareImage(val filePath: String, val title: String) : SignatureManageEvent
}

/** Backs "Your signatures": observes saved marks, reorders, exports and removes them. */
@HiltViewModel
class SignatureManageViewModel @Inject constructor(
    observeSignaturesUseCase: ObserveSignaturesUseCase,
    private val deleteSignatureUseCase: DeleteSignatureUseCase,
    private val moveSignatureToFrontUseCase: MoveSignatureToFrontUseCase,
    private val exportSignatureImageUseCase: ExportSignatureImageUseCase,
    private val saveSignatureToGalleryUseCase: SaveSignatureToGalleryUseCase,
    private val recordPositiveMomentUseCase: RecordPositiveMomentUseCase,
) : ViewModel() {

    /** Saved signatures and initials, most recently used (or moved to front) first. */
    val signatures: StateFlow<List<SavedSignature>> = observeSignaturesUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _busy = MutableStateFlow(false)

    /** True while an image is being prepared, so share / save can't be tapped twice. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val messages = Channel<SignatureManageEvent>(Channel.BUFFERED)

    /** One-shot messages and share requests for the screen. */
    val events: Flow<SignatureManageEvent> = messages.receiveAsFlow()

    /** Deletes the saved mark with [id]; a failure is reported through [events]. */
    fun delete(id: String) {
        viewModelScope.launch {
            when (val result = deleteSignatureUseCase(id)) {
                is ScanlyResult.Success -> Unit
                is ScanlyResult.Failure -> messages.send(SignatureManageEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun moveToFront(signature: SavedSignature) {
        viewModelScope.launch {
            val message = when (val result = moveSignatureToFrontUseCase(signature.id)) {
                is ScanlyResult.Success -> "${signature.kind.label} will be offered first when signing"
                is ScanlyResult.Failure -> result.error.message
            }
            messages.send(SignatureManageEvent.ShowMessage(message))
        }
    }

    fun share(signature: SavedSignature, ink: SignInk) = runExport {
        when (val result = exportSignatureImageUseCase(signature.id, ink)) {
            is ScanlyResult.Success -> {
                recordPositiveMomentUseCase()
                SignatureManageEvent.ShareImage(result.value, signature.kind.label)
            }
            is ScanlyResult.Failure -> SignatureManageEvent.ShowMessage(result.error.message)
        }
    }

    fun saveToPhone(signature: SavedSignature, ink: SignInk) = runExport {
        when (val result = saveSignatureToGalleryUseCase(signature.id, ink)) {
            is ScanlyResult.Success -> {
                recordPositiveMomentUseCase()
                SignatureManageEvent.ShowMessage("Saved to ${result.value.destinationLabel}")
            }
            is ScanlyResult.Failure -> SignatureManageEvent.ShowMessage(result.error.message)
        }
    }

    private fun runExport(block: suspend () -> SignatureManageEvent) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                messages.send(block())
            } finally {
                _busy.value = false
            }
        }
    }
}
