package `in`.c1ph3rj.scanly.feature.settings

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class FeedbackFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sendRequiresMessageAndDraftSurvivesRecreationAndHandOff() {
        val restoration = StateRestorationTester(compose)
        val drafts = mutableListOf<Pair<String, String>>()
        val copies = mutableListOf<Pair<String, String>>()
        restoration.setContent {
            ScanlyTheme {
                SettingsFeedbackScreen({}, remember { SnackbarHostState() },
                    { subject, body -> drafts += subject to body },
                    { subject, body -> copies += subject to body })
            }
        }
        compose.onNodeWithTag("feedback_send").assertIsNotEnabled()
        compose.onNodeWithTag("feedback_copy").assertIsNotEnabled()
        compose.onNodeWithTag("feedback_subject").performTextInput("Search & Tamil")
        compose.onNodeWithTag("feedback_send").assertIsNotEnabled()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.feedback_issue)).performClick()
        compose.onNodeWithTag("feedback_message").performTextInput("தமிழ் & + ? #\nSecond line")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("feedback_subject").assertTextContains("Search & Tamil")
        compose.onNodeWithTag("feedback_message").assertTextContains("தமிழ் & + ? #\nSecond line")
        compose.onNodeWithTag("feedback_send").assertIsEnabled().performClick()
        compose.onNodeWithTag("feedback_copy").performClick()
        compose.runOnIdle {
            assertEquals("Scanly Issue: Search & Tamil", drafts.single().first)
            assertTrue(drafts.single().second.startsWith("தமிழ் & + ? #\nSecond line\n\n"))
            assertEquals(drafts, copies)
        }
        compose.onNodeWithTag("feedback_message").assertTextContains("தமிழ் & + ? #\nSecond line")
    }

    @Test fun blankMessageIsRejected() {
        compose.setContent {
            ScanlyTheme {
                SettingsFeedbackScreen({}, remember { SnackbarHostState() }, { _, _ -> fail() }, { _, _ -> fail() })
            }
        }
        compose.onNodeWithTag("feedback_message").performTextInput("   ")
        compose.onNodeWithTag("feedback_send").assertIsNotEnabled()
        compose.onNodeWithTag("feedback_copy").assertIsNotEnabled()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "feedback-form.png")
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun emailDraftEncodesUnicodeAndReservedCharactersWithoutExtraRecipients() {
        val subject = "Scanly Issue: தமிழ் & ? # + ="
        val body = "First line\nதமிழ் &body=changed&bcc=someone@example.com?#+%"
        val intent = feedbackEmailIntent(subject, body)
        val uri = requireNotNull(intent.data)
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertTrue(uri.toString().startsWith("mailto:$SUPPORT_EMAIL?"))
        // mailto is opaque: parse the encoded query separately for exact round-trip assertions.
        val query = android.net.Uri.parse("https://test.invalid/?" + uri.toString().substringAfter('?'))
        assertEquals(setOf("subject", "body"), query.queryParameterNames)
        assertEquals(subject, query.getQueryParameter("subject"))
        assertEquals(body, query.getQueryParameter("body"))
        assertArrayEquals(arrayOf(SUPPORT_EMAIL), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals(subject, intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(body, intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
