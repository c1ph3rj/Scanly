package `in`.c1ph3rj.scanly.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachment
import `in`.c1ph3rj.scanly.domain.repository.FeedbackAttachmentRepository
import `in`.c1ph3rj.scanly.domain.usecase.FeedbackAttachmentsUseCase
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FeedbackStateTest {
    private class Repository : FeedbackAttachmentRepository {
        val images = mutableMapOf<String, FeedbackAttachment>()
        var valid = true
        override suspend fun prepare(uri: String): ScanlyResult<FeedbackAttachment> {
            if (uri == "bad") return ScanlyResult.Failure(ScanlyError("Unreadable"))
            val image = FeedbackAttachment("/cache/$uri.png", 200_000, "image/png")
            images[image.path] = image
            return ScanlyResult.Success(image)
        }
        override suspend fun restore(paths: List<String>) = paths.mapNotNull { images[it] }
        override suspend fun validate(images: List<FeedbackAttachment>) = valid
    }

    @Test fun capsSelectionAndRestoresPreparedCopiesAfterRecreation() = runBlocking {
        val repository = Repository()
        val saved = SavedStateHandle()
        val first = FeedbackViewModel(FeedbackAttachmentsUseCase(repository), saved)
        first.uiState.first { !it.isPreparing }
        first.addImages((1..8).map { "$it" })
        val ready = first.uiState.first { !it.isPreparing }
        assertEquals(5, ready.images.size)
        val restoredState = SavedStateHandle(mapOf("feedback_images" to saved.get<ArrayList<String>>("feedback_images")))
        val restored = FeedbackViewModel(FeedbackAttachmentsUseCase(repository), restoredState)
        assertEquals(ready.images, restored.uiState.first { !it.isPreparing }.images)
        restored.removeImage(ready.images.first().path)
        assertEquals(4, restored.uiState.value.images.size)
        restored.addImages(listOf("bad"))
        assertEquals(4, restored.uiState.first { !it.isPreparing }.images.size)
        first.viewModelScope.cancel(); restored.viewModelScope.cancel()
    }

    @Test fun missingPreparedImageBlocksEmailWithoutSilentlyDroppingAttachments() = runBlocking {
        val repository = Repository()
        val model = FeedbackViewModel(FeedbackAttachmentsUseCase(repository), SavedStateHandle())
        model.uiState.first { !it.isPreparing }
        model.addImages(listOf("one"))
        model.uiState.first { !it.isPreparing }
        repository.valid = false
        assertNull(model.imagesForEmail())
        assertEquals(1, model.uiState.value.images.size)
        assertFalse(model.uiState.value.isPreparing)
        model.viewModelScope.cancel()
    }
}
