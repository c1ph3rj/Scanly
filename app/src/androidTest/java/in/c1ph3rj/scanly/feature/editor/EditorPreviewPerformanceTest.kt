package `in`.c1ph3rj.scanly.feature.editor

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class EditorPreviewPerformanceTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun filterAndTonalChangesShowLoadingAndReuseTheDecodedPage() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = File.createTempFile("editor_preview_", ".png", cache)
        val original = Color.rgb(64, 96, 128)
        val bitmap = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(original)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }

        val pixel = AtomicInteger()
        val loadingFrames = AtomicInteger()
        val adjustments = mutableStateOf(PageFilterAdjustments.Default)
        val filter = mutableStateOf(PageFilterPreset.ORIGINAL)
        try {
            composeRule.setContent {
                val preview = rememberEditorPreview(
                    rawImagePath = file.path,
                    fallbackImagePath = null,
                    rotationDegrees = 0,
                    selectedFilter = filter.value,
                    filterAdjustments = adjustments.value,
                )
                SideEffect {
                    if (preview == EditorPreview.Loading) loadingFrames.incrementAndGet()
                    val image = (preview as? EditorPreview.Ready)?.image
                    pixel.set(image?.asAndroidBitmap()?.getPixel(0, 0) ?: Color.TRANSPARENT)
                }
            }
            composeRule.waitUntil(10_000) { pixel.get() == original }
            assertTrue(file.delete())
            val beforeAdjustment = loadingFrames.get()
            composeRule.runOnIdle { adjustments.value = PageFilterAdjustments(brightness = 0.5f) }
            composeRule.waitUntil(10_000) { pixel.get() == Color.rgb(104, 136, 168) }
            assertTrue(loadingFrames.get() > beforeAdjustment)
            composeRule.runOnIdle { adjustments.value = PageFilterAdjustments.Default }
            composeRule.waitUntil(10_000) { pixel.get() == original }

            val beforeFilter = loadingFrames.get()
            composeRule.runOnIdle { filter.value = PageFilterPreset.GRAYSCALE }
            composeRule.waitUntil(10_000) {
                val rendered = pixel.get()
                rendered != Color.TRANSPARENT && rendered != original && Color.red(rendered) == Color.blue(rendered)
            }
            assertTrue(loadingFrames.get() > beforeFilter)
        } finally {
            file.delete()
        }
    }
}
