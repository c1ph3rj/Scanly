package `in`.c1ph3rj.scanly.domain.repository

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.SavedExport
import `in`.c1ph3rj.scanly.domain.model.SavedSignature
import `in`.c1ph3rj.scanly.domain.model.SignInk
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

    /** Frees the decoded upload that [extractInk] keeps between calls. Call when the create screen closes. */
    fun clearUploadCache()

    suspend fun deleteSignature(id: String): ScanlyResult<Unit>

    /** Puts [id] first among its kind, so the sign editor offers it before the others. */
    suspend fun moveSignatureToFront(id: String): ScanlyResult<Unit>

    /**
     * Renders [id] as a transparent PNG in [ink] into the app cache and returns its path, ready
     * to share. The file is overwritten by the next export.
     */
    suspend fun exportSignatureImage(id: String, ink: SignInk): ScanlyResult<String>

    /** Saves [id] as a transparent PNG in [ink] to Pictures/Scanly. */
    suspend fun saveSignatureToGallery(id: String, ink: SignInk): ScanlyResult<SavedExport>
}
