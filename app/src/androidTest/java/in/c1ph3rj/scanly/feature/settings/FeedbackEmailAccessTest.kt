package `in`.c1ph3rj.scanly.feature.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

private const val RESULT_ACTION = "in.c1ph3rj.scanly.FEEDBACK_IMAGE_READ_TEST"

class FeedbackEmailAccessTest {
    @Test fun anotherAppCanReadAllGrantedAttachments() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = (1..2).map { File(context.cacheDir, "feedback_access_$it.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) } }
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
        val token = UUID.randomUUID().toString()
        val received = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getStringExtra("token") == token) received.complete(intent.getBooleanExtra("success", false))
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(RESULT_ACTION), ContextCompat.RECEIVER_EXPORTED)
        try {
            val intent = feedbackImageEmailIntent("Scanly attachment test", "Body", uris)
                .setClassName("in.c1ph3rj.scanly.test", FeedbackEmailTestActivity::class.java.name)
                .putExtra("token", token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            assertTrue(withTimeout(10_000) { received.await() })
        } finally {
            context.unregisterReceiver(receiver)
            files.forEach { it.delete() }
        }
    }
}
