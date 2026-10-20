package `in`.c1ph3rj.scanly.data.sign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.common.runCatchingCancellable
import `in`.c1ph3rj.scanly.core.processing.SignatureInkExtractor
import `in`.c1ph3rj.scanly.data.local.db.dao.SignatureDao
import `in`.c1ph3rj.scanly.data.local.db.entity.SignatureEntity
import `in`.c1ph3rj.scanly.domain.model.SavedSignature
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import `in`.c1ph3rj.scanly.domain.model.SignatureSource
import `in`.c1ph3rj.scanly.domain.repository.SignatureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSignatureRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val signatureDao: SignatureDao,
    private val inkExtractor: SignatureInkExtractor,
    private val dispatchers: ScanlyDispatchers,
) : SignatureRepository {

    /** The last decoded upload, so moving the sensitivity slider does not re-decode the photo. */
    private val decodeMutex = Mutex()
    private var decodedUri: String? = null
    private var decodedBitmap: Bitmap? = null

    override fun observeSignatures(): Flow<List<SavedSignature>> =
        signatureDao.observeSignatures().map { rows -> rows.map { it.toDomain() } }

    override suspend fun saveSignature(
        inkMask: Bitmap,
        kind: SignatureKind,
        source: SignatureSource,
    ): ScanlyResult<SavedSignature> = withContext(dispatchers.io) {
        runCatchingCancellable {
            require(inkMask.width > 0 && inkMask.height > 0) { "The signature is empty." }
            val id = UUID.randomUUID().toString()
            val directory = File(signaturesRoot(context), id).apply {
                if (!exists() && !mkdirs()) error("Could not create signature storage.")
            }
            val file = File(directory, INK_FILE_NAME)
            FileOutputStream(file).use { output ->
                check(inkMask.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not save the signature." }
            }
            val now = System.currentTimeMillis()
            val entity = SignatureEntity(
                id = id,
                kind = kind.storageValue,
                source = source.storageValue,
                imagePath = file.absolutePath,
                widthPx = inkMask.width,
                heightPx = inkMask.height,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            signatureDao.upsertSignature(entity)
            entity.toDomain()
        }.toScanlyResult("Could not save the signature.")
    }

    override suspend fun extractInk(uriString: String, sensitivity: Float): ScanlyResult<Bitmap> =
        withContext(dispatchers.default) {
            runCatchingCancellable {
                decodeMutex.withLock {
                    val source = decodeCached(uriString)
                    val mask = if (inkExtractor.hasMeaningfulAlpha(source)) {
                        inkExtractor.maskFromTransparent(source)
                    } else {
                        inkExtractor.extract(source, sensitivity.coerceIn(0f, 1f))
                    }
                    mask ?: error("No signature found in this image. Try dark ink on light paper.")
                }
            }.toScanlyResult("Could not read this image.")
        }

    override suspend fun deleteSignature(id: String): ScanlyResult<Unit> = withContext(dispatchers.io) {
        runCatchingCancellable {
            signatureDao.deleteSignature(id)
            File(signaturesRoot(context), id).deleteRecursively()
            Unit
        }.toScanlyResult("Could not delete the signature.")
    }

    private fun decodeCached(uriString: String): Bitmap {
        decodedBitmap?.takeIf { decodedUri == uriString && !it.isRecycled }?.let { return it }
        decodedBitmap?.recycle()
        decodedBitmap = null
        decodedUri = null
        val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uriString))
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val longEdge = maxOf(info.size.width, info.size.height)
            if (longEdge > MAX_DECODE_EDGE) {
                val scale = MAX_DECODE_EDGE.toFloat() / longEdge
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
        decodedBitmap = bitmap
        decodedUri = uriString
        return bitmap
    }

    private fun SignatureEntity.toDomain() = SavedSignature(
        id = id,
        kind = SignatureKind.fromStorage(kind),
        source = SignatureSource.fromStorage(source),
        imagePath = imagePath,
        widthPx = widthPx,
        heightPx = heightPx,
        createdAtMillis = createdAtMillis,
    )

    private fun <T> Result<T>.toScanlyResult(fallbackMessage: String): ScanlyResult<T> =
        fold(
            onSuccess = { ScanlyResult.Success(it) },
            onFailure = { ScanlyResult.Failure(ScanlyError(it.message ?: fallbackMessage, it)) },
        )

    companion object {
        private const val SIGNATURES_DIRECTORY = "signatures"
        private const val INK_FILE_NAME = "ink.png"
        private const val MAX_DECODE_EDGE = 2400

        /** App-private root for saved signatures; cleared with the rest of the library. */
        fun signaturesRoot(context: Context): File = File(context.filesDir, SIGNATURES_DIRECTORY)
    }
}
