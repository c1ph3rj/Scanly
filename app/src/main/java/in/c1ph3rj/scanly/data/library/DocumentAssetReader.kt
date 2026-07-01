package `in`.c1ph3rj.scanly.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.domain.model.LibraryAssetRef
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DocumentAssetReaderEntryPoint {
    fun documentAssetReader(): DocumentAssetReader
}

@Singleton
class DocumentAssetReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val session: LibrarySession,
    private val fileSystem: SharedLibraryFileSystem,
) {
    suspend fun materialize(asset: LibraryAssetRef): File {
        val extension = if (asset.mimeType == "image/jpeg") ".jpg" else ".bin"
        val destination = File(context.cacheDir, "asset-cache/${asset.sha256}$extension")
        if (destination.isFile && destination.length() == asset.byteCount) return destination
        fileSystem.copyToFile(session.requireTreeUri(), asset.relativePath, destination)
        require(destination.length() == asset.byteCount) { "Shared asset verification failed." }
        return destination
    }

    suspend fun materialize(relativePath: String): File {
        val entry = fileSystem.stat(session.requireTreeUri(), relativePath)
            ?: error("Missing shared asset $relativePath.")
        val key = relativePath.hashCode().toUInt().toString(16) + "-${entry.size}"
        val destination = File(context.cacheDir, "asset-cache/$key.jpg")
        if (destination.isFile && destination.length() == entry.size) return destination
        return fileSystem.copyToFile(session.requireTreeUri(), relativePath, destination)
    }

    suspend fun decode(asset: LibraryAssetRef, targetPx: Int): Bitmap? {
        val file = materialize(asset)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        var width = bounds.outWidth
        var height = bounds.outHeight
        while (width > targetPx || height > targetPx) {
            width /= 2
            height /= 2
            sample *= 2
        }
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun clearCache() {
        File(context.cacheDir, "asset-cache").deleteRecursively()
    }
}
