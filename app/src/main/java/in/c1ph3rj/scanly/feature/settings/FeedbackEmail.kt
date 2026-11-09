package `in`.c1ph3rj.scanly.feature.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachment
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachmentLimits
import java.io.File

/** URI fields support email clients that ignore extras when composing a text-only message. */
internal fun feedbackEmailIntent(subject: String, body: String): Intent = Intent(
    Intent.ACTION_SENDTO,
    Uri.parse("mailto:$SUPPORT_EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"),
).putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
    .putExtra(Intent.EXTRA_SUBJECT, subject)
    .putExtra(Intent.EXTRA_TEXT, body)

/** Attachments use streams and ClipData, with temporary read access for the chosen email app. */
internal fun feedbackImageEmailIntent(subject: String, body: String, uris: List<Uri>): Intent {
    require(uris.isNotEmpty() && uris.size <= FeedbackAttachmentLimits.MAX_IMAGES)
    return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        clipData = ClipData.newRawUri("", uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

internal fun openFeedbackEmail(
    context: Context,
    subject: String,
    body: String,
    images: List<FeedbackAttachment> = emptyList(),
    chooserTitle: String = "",
    launch: (Intent) -> Unit = context::startActivity,
): Boolean = try {
    require(FeedbackAttachmentLimits.canSend(images))
    if (images.isEmpty()) {
        launch(feedbackEmailIntent(subject, body))
        true
    } else {
        val uris = images.map { image ->
            val file = File(image.path)
            require(file.isFile && file.length() == image.sizeBytes)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val share = feedbackImageEmailIntent(subject, body, uris)
        // SENDTO cannot reliably carry attachments. Offer only mailto-capable packages that also
        // accept image streams, using Android's chooser rather than an unrestricted share sheet.
        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        val targets = context.packageManager.queryIntentActivities(mail, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }.distinct().mapNotNull { packageName ->
                Intent(share).setPackage(packageName).takeIf {
                    it.resolveActivity(context.packageManager) != null
                }
            }
        if (targets.isEmpty()) false else {
            val chooser = Intent.createChooser(targets.first(), chooserTitle).apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = share.clipData
            }
            launch(chooser)
            true
        }
    }
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
} catch (_: IllegalArgumentException) {
    false
}
