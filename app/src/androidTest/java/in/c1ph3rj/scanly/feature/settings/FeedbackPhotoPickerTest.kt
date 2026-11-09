package `in`.c1ph3rj.scanly.feature.settings

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachment
import `in`.c1ph3rj.scanly.domain.repository.FeedbackAttachmentRepository
import `in`.c1ph3rj.scanly.domain.usecase.FeedbackAttachmentsUseCase
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Captures the actual route's launch intents without opening or changing the system gallery. */
@SdkSuppress(minSdkVersion = 33)
class FeedbackPhotoPickerTest {
    @get:Rule val compose = createComposeRule()

    private class Registry : ActivityResultRegistry() {
        var launched: Intent? = null
        var launchedCode = 0
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
            options: ActivityOptionsCompat?) {
            launchedCode = requestCode
            launched = contract.createIntent(InstrumentationRegistry.getInstrumentation().targetContext, input)
        }
        fun complete(resultCode: Int, result: Intent?) { dispatchResult(launchedCode, resultCode, result) }
    }

    private class Repository : FeedbackAttachmentRepository {
        override suspend fun prepare(uri: String) = ScanlyResult.Success(
            FeedbackAttachment("/cache/${uri.substringAfterLast('/')}.png", 100, "image/png"))
        override suspend fun restore(paths: List<String>) = emptyList<FeedbackAttachment>()
        override suspend fun validate(images: List<FeedbackAttachment>) = true
    }

    @Test fun routeBoundsPickerToFiveThenOneThenTwoRemainingSlots() {
        val registry = Registry()
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        val model = FeedbackViewModel(FeedbackAttachmentsUseCase(Repository()), SavedStateHandle())
        try {
            compose.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    ScanlyTheme { SettingsFeedbackRoute({}, model) }
                }
            }
            compose.waitUntil { !model.uiState.value.isPreparing }
            compose.onNodeWithTag("feedback_add_images").performScrollTo().performClick()
            assertPicker(registry, 5)
            compose.runOnIdle {
                val uris = (1..4).map { Uri.parse("content://feedback-picker-test/$it") }
                val clip = ClipData.newRawUri("", uris.first()).apply {
                    uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
                registry.complete(Activity.RESULT_OK, Intent().apply { clipData = clip })
            }
            compose.waitUntil { model.uiState.value.images.size == 4 && !model.uiState.value.isPreparing }
            compose.onNodeWithTag("feedback_add_images").performScrollTo().performClick()
            assertPicker(registry, 1)
            compose.runOnIdle {
                registry.complete(Activity.RESULT_CANCELED, null)
                model.removeImage(model.uiState.value.images.first().path)
            }
            compose.waitUntil { model.uiState.value.images.size == 3 }
            compose.onNodeWithTag("feedback_add_images").performScrollTo().performClick()
            assertPicker(registry, 2)
            compose.runOnIdle { registry.complete(Activity.RESULT_CANCELED, null) }
        } finally {
            model.viewModelScope.cancel()
        }
    }

    private fun assertPicker(registry: Registry, expectedMax: Int) {
        val intent = requireNotNull(registry.launched)
        assertEquals(MediaStore.ACTION_PICK_IMAGES, intent.action)
        assertEquals("image/*", intent.type)
        assertEquals(expectedMax, intent.getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 1))
        assertFalse(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
    }
}
