package `in`.c1ph3rj.scanly.domain.usecase

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Images shared into Scanly from another app, copied into the cache while the user picks where
 * they go (a new document or more pages for an existing one).
 *
 * The sending app's read grant only lasts as long as our task does, so the files are copied right
 * away; the import then reads the copies, which survive onboarding, rotation and process death.
 *
 * @property batchId Folder under `cache/incoming_images/` that holds [imagePaths].
 * @property imagePaths Local copies, in the order the sending app listed them.
 * @property truncated More images were shared than one import takes; the rest were dropped.
 * @property unreadableCount Shared items that could not be copied (gone, unreadable, too large).
 */
data class StagedImageShare(
    val batchId: String,
    val imagePaths: List<String>,
    val truncated: Boolean = false,
    val unreadableCount: Int = 0,
)

/** Copies shared image URIs into a fresh cache batch. Returns null when none could be copied. */
class StageSharedImagesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) {
    suspend operator fun invoke(uris: List<Uri>): StagedImageShare? = withContext(dispatchers.io) {
        if (uris.isEmpty()) return@withContext null
        val batchId = UUID.randomUUID().toString()
        val dir = File(SharedImageStaging.root(context), batchId)
        if (!dir.mkdirs() && !dir.isDirectory) return@withContext null
        val paths = try {
            uris.mapIndexedNotNull { index, uri ->
                val target = File(dir, String.format(Locale.ROOT, "%03d.img", index + 1))
                target.absolutePath.takeIf { copy(uri, target) }
            }
        } catch (cancelled: CancellationException) {
            dir.deleteRecursively()
            throw cancelled
        }
        if (paths.isEmpty()) {
            dir.deleteRecursively()
            return@withContext null
        }
        StagedImageShare(
            batchId = batchId,
            imagePaths = paths,
            unreadableCount = uris.size - paths.size,
        )
    }

    /** Streams [uri] into [target]; a failed or oversized copy leaves nothing behind. */
    private fun copy(uri: Uri, target: File): Boolean {
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        // A stream that never ends would otherwise fill the cache.
                        if (total > SharedImageStaging.MAX_IMAGE_BYTES) throw IOException("Shared image too large")
                        output.write(buffer, 0, read)
                    }
                    total > 0
                }
            } ?: false
        }.getOrDefault(false)
        if (!copied) target.delete()
        return copied
    }
}

/** Deletes staged share batches once they are imported or dismissed. */
class DiscardSharedImagesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) {
    suspend operator fun invoke(batchId: String) = withContext(dispatchers.io) {
        if (!SharedImageStaging.isValidBatchId(batchId)) return@withContext
        File(SharedImageStaging.root(context), batchId).deleteRecursively()
        Unit
    }

    /** Clears leftovers from earlier runs, keeping the batch still waiting for a destination. */
    suspend fun clearAllExcept(keepBatchId: String?) = withContext(dispatchers.io) {
        SharedImageStaging.root(context).listFiles()
            ?.filter { it.name != keepBatchId }
            ?.forEach { it.deleteRecursively() }
        Unit
    }
}

internal object SharedImageStaging {
    /** Well above any phone photo, so only broken or hostile streams hit it. */
    const val MAX_IMAGE_BYTES: Long = 100L * 1024 * 1024

    fun root(context: Context): File = File(context.cacheDir, "incoming_images")

    /** Batch ids come back from saved state; only accept ones we could have made. */
    fun isValidBatchId(batchId: String): Boolean =
        batchId.isNotEmpty() && batchId.all { it.isLetterOrDigit() || it == '-' }
}
