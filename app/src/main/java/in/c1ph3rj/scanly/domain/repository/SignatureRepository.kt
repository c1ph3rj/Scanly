package `in`.c1ph3rj.scanly.domain.repository

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.SavedSignature
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import `in`.c1ph3rj.scanly.domain.model.SignatureSource
import kotlinx.coroutines.flow.Flow

/** "Your signatures": reusable ink-mask images stored privately on this device. */
interface SignatureRepository {
    fun observeSignatures(): Flow<List<SavedSignature>>

    /** Saves [inkMask] (black RGB, alpha = ink) as a new signature. The bitmap is not recycled. */
    suspend fun saveSignature(
        inkMask: Bitmap,
        kind: SignatureKind,
        source: SignatureSource,
    ): ScanlyResult<SavedSignature>

    /**
     * Extracts an ink mask from a picked image (content URI). Transparent PNGs keep their shape;
     * photos are cleaned with [sensitivity] 0..1 (higher keeps fainter strokes).
     */
    suspend fun extractInk(uriString: String, sensitivity: Float): ScanlyResult<Bitmap>

    suspend fun deleteSignature(id: String): ScanlyResult<Unit>
}
