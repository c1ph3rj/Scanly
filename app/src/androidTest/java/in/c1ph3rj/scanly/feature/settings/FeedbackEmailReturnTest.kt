package `in`.c1ph3rj.scanly.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachment
import `in`.c1ph3rj.scanly.domain.repository.FeedbackAttachmentRepository
import `in`.c1ph3rj.scanly.domain.usecase.FeedbackAttachmentsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

internal class ReturnTestRepository : FeedbackAttachmentRepository {
    val images = mutableMapOf<String, FeedbackAttachment>()
    override suspend fun prepare(uri: String): ScanlyResult<FeedbackAttachment> {
        val image = FeedbackAttachment("/cache/$uri.png", 100, "image/png")
        images[image.path] = image
        return ScanlyResult.Success(image)
    }
    override suspend fun restore(paths: List<String>) = paths.mapNotNull { images[it] }
    override suspend fun validate(images: List<FeedbackAttachment>) = true
}

class FeedbackEmailReturnTest {
    private fun model(repo: ReturnTestRepository = ReturnTestRepository(), state: SavedStateHandle = SavedStateHandle()) =
        FeedbackViewModel(FeedbackAttachmentsUseCase(repo), state)
    private fun copy(state: SavedStateHandle) = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })
    private fun returned(model: FeedbackViewModel) {
        model.onEmailLaunched(); model.onHostStopped(); model.onHostResumed()
    }

    @Test fun initialResumePhotoPickerConfigurationAndNoDepartureDoNotAsk() = runBlocking<Unit>(Dispatchers.Main) {
        val model = model()
        model.uiState.first { !it.isPreparing }
        model.onHostResumed()
        model.onHostStopped(); model.onHostResumed()
        assertFalse(model.handoffState.value.showConfirmation)
        model.onEmailLaunched()
        assertFalse(model.handoffState.value.showConfirmation)
        model.onHostResumed() // chooser cancelled without leaving the app
        model.onHostStopped(); model.onHostResumed()
        assertFalse(model.handoffState.value.showConfirmation)
        model.onEmailLaunched(); model.onHostStopped(changingConfigurations = true); model.onHostResumed()
        assertFalse(model.handoffState.value.showConfirmation)
        model.viewModelScope.cancel()
    }

    @Test fun returnAsksOnceAndNotYetKeepsAttachmentsWithoutRepeating() = runBlocking<Unit>(Dispatchers.Main) {
        val model = model()
        model.uiState.first { !it.isPreparing }
        model.addImages(listOf("one"))
        val images = model.uiState.first { !it.isPreparing }.images
        returned(model)
        assertTrue(model.handoffState.value.showConfirmation)
        model.keepDraft()
        assertEquals(images, model.uiState.value.images)
        assertFalse(model.handoffState.value.showConfirmation)
        assertFalse(model.handoffState.value.closeRequested)
        model.onHostStopped(); model.onHostResumed()
        assertFalse(model.handoffState.value.showConfirmation)
        returned(model)
        assertTrue(model.handoffState.value.showConfirmation)
        model.viewModelScope.cancel()
    }

    @Test fun confirmationClearsSavedReferencesAndRequestsCloseOnlyOnce() = runBlocking<Unit>(Dispatchers.Main) {
        val state = SavedStateHandle()
        val model = model(state = state)
        model.uiState.first { !it.isPreparing }
        model.addImages(listOf("one"))
        model.uiState.first { !it.isPreparing }
        model.confirmSent() // cannot clear a draft without a return question
        assertEquals(1, model.uiState.value.images.size)
        returned(model)
        val epoch = model.handoffState.value.draftEpoch
        model.confirmSent()
        assertTrue(model.uiState.value.images.isEmpty())
        assertTrue(state.get<ArrayList<String>>("feedback_images").orEmpty().isEmpty())
        assertEquals(epoch + 1, model.handoffState.value.draftEpoch)
        assertTrue(model.handoffState.value.closeRequested)
        model.confirmSent()
        assertEquals(epoch + 1, model.handoffState.value.draftEpoch)
        model.consumeCloseRequest()
        assertFalse(model.handoffState.value.closeRequested)
        model.onHostStopped(); model.onHostResumed()
        assertFalse(model.handoffState.value.showConfirmation)
        model.viewModelScope.cancel()
    }

    @Test fun pendingReturnAndQuestionSurviveRecreationAndCloseStateIsRestored() = runBlocking<Unit>(Dispatchers.Main) {
        val repo = ReturnTestRepository()
        val saved = SavedStateHandle()
        val first = model(repo, saved)
        first.uiState.first { !it.isPreparing }
        first.addImages(listOf("one")); first.uiState.first { !it.isPreparing }
        first.onEmailLaunched(); first.onHostStopped()
        val restoredSaved = copy(saved)
        val second = model(repo, restoredSaved)
        second.uiState.first { !it.isPreparing }
        second.onHostResumed()
        assertTrue(second.handoffState.value.showConfirmation)
        assertEquals(1, second.uiState.value.images.size)
        val askingSaved = copy(restoredSaved)
        val third = model(repo, askingSaved)
        third.uiState.first { !it.isPreparing }
        assertTrue(third.handoffState.value.showConfirmation)
        third.confirmSent()
        val fourth = model(repo, copy(askingSaved))
        fourth.uiState.first { !it.isPreparing }
        assertTrue(fourth.handoffState.value.closeRequested)
        assertTrue(fourth.uiState.value.images.isEmpty())
        assertFalse(fourth.handoffState.value.showConfirmation)
        listOf(first, second, third, fourth).forEach { it.viewModelScope.cancel() }
    }

    @Test fun activityResultWithoutStopSurvivesRecreationAndLateResultsDoNotRepeat() = runBlocking<Unit>(Dispatchers.Main) {
        val repo = ReturnTestRepository()
        val saved = SavedStateHandle()
        val first = model(repo, saved)
        first.uiState.first { !it.isPreparing }
        first.onEmailReturned() // no successful launch
        assertFalse(first.handoffState.value.showConfirmation)
        first.onEmailLaunched()
        first.onHostResumed() // split-screen/chooser can resume without a full stop
        assertFalse(first.handoffState.value.showConfirmation)
        val restored = model(repo, copy(saved))
        restored.uiState.first { !it.isPreparing }
        restored.onEmailReturned()
        assertTrue(restored.handoffState.value.showConfirmation)
        restored.keepDraft()
        restored.onEmailReturned()
        assertFalse(restored.handoffState.value.showConfirmation)
        returned(restored)
        restored.confirmSent()
        restored.onEmailReturned()
        assertFalse(restored.handoffState.value.showConfirmation)
        assertTrue(restored.handoffState.value.closeRequested)
        first.viewModelScope.cancel(); restored.viewModelScope.cancel()
    }

}
