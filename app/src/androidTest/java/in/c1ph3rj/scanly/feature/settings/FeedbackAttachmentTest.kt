package `in`.c1ph3rj.scanly.feature.settings

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.data.settings.DefaultFeedbackAttachmentRepository
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachmentLimits
import java.io.File
import java.io.RandomAccessFile
import java.util.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FeedbackAttachmentTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository get() = DefaultFeedbackAttachmentRepository(context)

    @Test fun largePhotoIsCompressedWithinBudgetWithoutChangingOriginal() = runBlocking<Unit> {
        val file = File(context.cacheDir, "feedback_test_large.png")
        val bitmap = Bitmap.createBitmap(2600, 2000, Bitmap.Config.ARGB_8888)
        val random = Random(1234)
        val pixels = IntArray(2600 * 2000) { random.nextInt() or (0xff shl 24) }
        bitmap.setPixels(pixels, 0, 2600, 0, 0, 2600, 2000)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        val originalSize = file.length()
        assertTrue(originalSize > FeedbackAttachmentLimits.MAX_IMAGE_BYTES)
        val result = repository.prepare(Uri.fromFile(file).toString()) as ScanlyResult.Success
        val image = result.value
        assertTrue(image.sizeBytes in 1..FeedbackAttachmentLimits.MAX_IMAGE_BYTES)
        assertEquals(originalSize, file.length())
        assertNotEquals(file.path, image.path)
        assertEquals("image/jpeg", image.mimeType)
        val decoded = requireNotNull(BitmapFactory.decodeFile(image.path))
        assertTrue(maxOf(decoded.width, decoded.height) in 960..FeedbackAttachmentLimits.MAX_EDGE)
        decoded.recycle()
        assertTrue(repository.validate(listOf(image)))
        file.delete()
        File(image.path).delete()
    }

    @Test fun screenshotStaysLosslessAndOrientationAndGpsAreNotCopied() = runBlocking<Unit> {
        val file = File(context.cacheDir, "feedback_test_rotated.jpg")
        val bitmap = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setLatLong(13.08, 80.27)
            saveAttributes()
        }
        val image = (repository.prepare(Uri.fromFile(file).toString()) as ScanlyResult.Success).value
        assertEquals("image/png", image.mimeType)
        val decoded = requireNotNull(BitmapFactory.decodeFile(image.path))
        assertEquals(1200, decoded.width)
        assertEquals(800, decoded.height)
        assertEquals(android.graphics.Color.WHITE, decoded.getPixel(10, 10))
        decoded.recycle()
        assertNull(ExifInterface(File(image.path)).latLong)
        assertNotEquals(ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface(File(image.path)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
        File(image.path).delete()
        assertFalse(repository.validate(listOf(image)))
        assertTrue(repository.restore(listOf(image.path)).isEmpty())
        file.delete()
    }

    @Test fun rejectsCorruptOversizeAndMissingImagesWithoutLeavingSourceCopies() = runBlocking<Unit> {
        val corrupt = File(context.cacheDir, "feedback_test_corrupt.png").apply { writeText("not an image") }
        assertTrue(repository.prepare(Uri.fromFile(corrupt).toString()) is ScanlyResult.Failure)
        val huge = File(context.cacheDir, "feedback_test_oversize.png")
        RandomAccessFile(huge, "rw").use { it.setLength(FeedbackAttachmentLimits.MAX_SOURCE_BYTES + 1) }
        assertTrue(repository.prepare(Uri.fromFile(huge).toString()) is ScanlyResult.Failure)
        assertTrue(repository.restore(listOf(corrupt.path, huge.path, "/missing.png")).isEmpty())
        assertTrue(File(context.cacheDir, "feedback_images").listFiles().orEmpty().none { it.extension == "tmp" })
        corrupt.delete(); huge.delete()
    }

    @Test fun streamsCarryEveryImageAndTemporaryReadAccessWithEmailFields() {
        val uris = listOf(Uri.parse("content://test/one"), Uri.parse("content://test/two"))
        val intent = feedbackImageEmailIntent("Scanly Issue", "தமிழ் & details", uris)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals("image/*", intent.type)
        @Suppress("DEPRECATION")
        assertEquals(uris, intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(2, intent.clipData?.itemCount)
        assertEquals(uris.last(), intent.clipData?.getItemAt(1)?.uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertArrayEquals(arrayOf(SUPPORT_EMAIL), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("தமிழ் & details", intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
