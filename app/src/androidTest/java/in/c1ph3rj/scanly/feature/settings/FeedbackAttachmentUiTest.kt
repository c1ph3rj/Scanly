package `in`.c1ph3rj.scanly.feature.settings

import android.graphics.Bitmap
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachment
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FeedbackAttachmentUiTest {
    @get:Rule val compose = createComposeRule()
    private var clearInput: () -> Unit = {}
    private fun fixture(dark: Boolean = false, preparing: Boolean = false, count: Int = 2) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "feedback_ui.png")
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(224, 242, 241))
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(0, 100, 90); textSize = 34f }
        canvas.drawText("Scanly", 30f, 80f, paint)
        canvas.drawText("Search results", 30f, 150f, paint.apply { textSize = 25f })
        canvas.drawRect(30f, 190f, 370f, 200f, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val images = (1..count).map {
            val copy = File(context.cacheDir, "feedback_ui_$it.png")
            file.copyTo(copy, overwrite = true)
            FeedbackAttachment(copy.path, copy.length(), "image/png")
        }
        compose.setContent {
            val focus = androidx.compose.ui.platform.LocalFocusManager.current
            val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            SideEffect { clearInput = { focus.clearFocus(); keyboard?.hide() } }
            var state by remember { mutableStateOf(FeedbackAttachmentState(images, preparing)) }
            ScanlyTheme(darkTheme = dark) {
                SettingsFeedbackScreen({}, remember { SnackbarHostState() }, { _, _ -> }, { _, _ -> }, state,
                    onRemoveImage = { path -> state = state.copy(images = state.images.filterNot { it.path == path }) })
            }
        }
    }

    @Test fun fiveImageLimitAndRemovalAreReflectedInControls() {
        fixture(count = 5)
        compose.onNodeWithTag("feedback_add_images").assertIsNotEnabled()
        compose.onNodeWithTag("feedback_attachments").performScrollTo()
        compose.onNodeWithTag("feedback_remove_1").performClick()
        compose.onNodeWithTag("feedback_add_images").assertIsEnabled()
    }

    @Test fun preparingImagesDisablesSendAndPickerEvenWithMessage() {
        fixture(preparing = true, count = 0)
        compose.onNodeWithTag("feedback_message").performTextInput("Please add this feature")
        compose.onNodeWithTag("feedback_send").assertIsNotEnabled()
        compose.onNodeWithTag("feedback_add_images").assertIsNotEnabled()
    }

    @Test fun lightFormWithAttachmentsIsReadable() { capture(false, "feedback-images-light.png") }
    @Test fun darkFormWithAttachmentsIsReadable() { capture(true, "feedback-images-dark.png") }

    private fun capture(dark: Boolean, filename: String) {
        fixture(dark)
        compose.onNodeWithTag("feedback_subject").performTextInput("A small improvement")
        compose.onNodeWithTag("feedback_message").performTextInput("Could search show the page number next to each result?")
        compose.runOnIdle { clearInput() }
        compose.waitForIdle()
        compose.onNodeWithTag("feedback_send").assertIsEnabled()
        compose.onNodeWithTag("feedback_attachments").performScrollTo()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.getExternalFilesDir(null), filename).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
