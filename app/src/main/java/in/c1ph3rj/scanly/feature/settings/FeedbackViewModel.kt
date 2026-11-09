package `in`.c1ph3rj.scanly.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachment
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachmentLimits
import `in`.c1ph3rj.scanly.domain.usecase.FeedbackAttachmentsUseCase
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class FeedbackAttachmentState(
    val images: List<FeedbackAttachment> = emptyList(),
    val isPreparing: Boolean = false,
)

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val attachments: FeedbackAttachmentsUseCase,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val state = MutableStateFlow(FeedbackAttachmentState(isPreparing = true))
    val uiState = state.asStateFlow()
    private val handoff = FeedbackEmailHandoff(savedState)
    val handoffState = handoff.state
    private var imageOperation: Job? = null
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    init {
        imageOperation = viewModelScope.launch {
            val paths = savedState.get<ArrayList<String>>("feedback_images").orEmpty()
            val restored = attachments.restore(paths)
            state.value = FeedbackAttachmentState(restored)
            if (restored.size != paths.size) messages.send(appString(R.string.feedback_missing_images))
            save()
        }
    }

    fun addImages(uris: List<String>) {
        if (state.value.isPreparing || uris.isEmpty()) return
        val available = FeedbackAttachmentLimits.MAX_IMAGES - state.value.images.size
        val selected = uris.distinct().take(available)
        state.value = state.value.copy(isPreparing = true)
        imageOperation = viewModelScope.launch {
            try {
                var failed = false
                for (uri in selected) {
                    when (val result = attachments.prepare(uri)) {
                        is ScanlyResult.Success -> {
                            state.value = state.value.copy(images = state.value.images + result.value)
                            save()
                        }
                        is ScanlyResult.Failure -> failed = true
                    }
                }
                if (failed) messages.send(appString(R.string.feedback_image_failed))
                if (uris.distinct().size > available) {
                    messages.send(appString(R.string.feedback_image_limit, FeedbackAttachmentLimits.MAX_IMAGES))
                }
            } finally {
                state.value = state.value.copy(isPreparing = false)
            }
        }
    }

    fun removeImage(path: String) {
        if (state.value.isPreparing) return
        // Do not delete: a previously opened email draft may still need this copy.
        state.value = state.value.copy(images = state.value.images.filterNot { it.path == path })
        save()
    }

    suspend fun imagesForEmail(): List<FeedbackAttachment>? {
        if (state.value.isPreparing) return null
        state.value = state.value.copy(isPreparing = true)
        return try {
            state.value.images.takeIf { attachments.validate(it) }.also {
                if (it == null) messages.send(appString(R.string.feedback_missing_images))
            }
        } finally {
            state.value = state.value.copy(isPreparing = false)
        }
    }

    fun onEmailLaunched() = handoff.onEmailLaunched()

    fun onEmailReturned() = handoff.onEmailReturned()

    fun onHostStopped(changingConfigurations: Boolean = false) = handoff.onHostStopped(changingConfigurations)

    fun onHostResumed() = handoff.onHostResumed()

    fun keepDraft() = handoff.keepDraft()

    fun confirmSent() {
        if (!handoffState.value.showConfirmation) return
        // A restoring/processing job must not put old attachment references back after clearing.
        imageOperation?.cancel()
        imageOperation = null
        state.value = FeedbackAttachmentState()
        save()
        // Cached files remain available to the external email draft.
        handoff.confirmSent()
    }

    fun consumeCloseRequest() = handoff.consumeCloseRequest()

    private fun save() {
        savedState["feedback_images"] = ArrayList(state.value.images.map { it.path })
    }
}
