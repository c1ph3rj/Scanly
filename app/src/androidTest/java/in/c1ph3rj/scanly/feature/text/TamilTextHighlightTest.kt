package `in`.c1ph3rj.scanly.feature.text

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import `in`.c1ph3rj.scanly.domain.search.SearchQuery
import `in`.c1ph3rj.scanly.ui.theme.ScanlyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Isolated rendering fixture; does not read or change saved documents. */
class TamilTextHighlightTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun tamilHighlightsKeepWholeCharactersOnAndroid() {
        composeRule.setContent {
            ScanlyTheme(darkTheme = true, pureBlack = true) {
                val text = "தமிழ்நாடு அரசு உணவுப்பொருள் வழங்கல்"
                val highlighted = highlightWordPrefixes(text, SearchQuery.parse("தமிழ்").allWords, matchHighlightStyle())
                assertEquals(text, highlighted.text)
                assertEquals(listOf(0 to 5), highlighted.spanStyles.map { it.start to it.end })
                val partial = highlightWordPrefixes("தமிழ்நாடு", SearchQuery.parse("தம").allWords, matchHighlightStyle())
                assertEquals(listOf(0 to 3), partial.spanStyles.map { it.start to it.end })

                Column(
                    modifier = Modifier
                        .testTag("tamil_fixture")
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Plain text", color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text("Search: தமிழ்", color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        highlighted,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text("Partial search: தம", color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        partial,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        val bitmap = composeRule.onNodeWithTag("tamil_fixture").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "test-artifacts/tamil-text-highlight.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
