package `in`.c1ph3rj.scanly.data.library

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkingFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun createOperationDirectory(operationId: String = UUID.randomUUID().toString()): File =
        File(context.cacheDir, "library-work/$operationId").apply {
            check(exists() || mkdirs()) { "Could not create working directory." }
        }

    fun createCaptureFile(operationId: String = UUID.randomUUID().toString()): File =
        File(createOperationDirectory(operationId), "capture.jpg")

    fun clear(operationId: String) {
        File(context.cacheDir, "library-work/$operationId").deleteRecursively()
    }

    fun clearAll() {
        File(context.cacheDir, "library-work").deleteRecursively()
    }
}

