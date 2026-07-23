package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class NormalizedFaceRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height

    val isUsable: Boolean
        get() = left in 0f..1f &&
            top in 0f..1f &&
            right in 0f..1f &&
            bottom in 0f..1f &&
            width > 0f &&
            height > 0f &&
            area >= MIN_USABLE_AREA

    fun padded(
        horizontalFraction: Float,
        verticalFraction: Float,
    ): NormalizedFaceRegion {
        val horizontalPadding = width * horizontalFraction
        val verticalPadding = height * verticalFraction
        return NormalizedFaceRegion(
            left = (left - horizontalPadding).coerceIn(0f, 1f),
            top = (top - verticalPadding).coerceIn(0f, 1f),
            right = (right + horizontalPadding).coerceIn(0f, 1f),
            bottom = (bottom + verticalPadding).coerceIn(0f, 1f),
        )
    }

    private companion object {
        const val MIN_USABLE_AREA = 0.0025f
    }
}

enum class FaceDetectionAvailability {
    AVAILABLE,
    UNAVAILABLE,
}

data class IdCardFaceDetection(
    val regions: List<NormalizedFaceRegion> = emptyList(),
    val availability: FaceDetectionAvailability = FaceDetectionAvailability.AVAILABLE,
) {
    val hasFace: Boolean get() = regions.isNotEmpty()

    companion object {
        val Unavailable = IdCardFaceDetection(
            availability = FaceDetectionAvailability.UNAVAILABLE,
        )
    }
}

interface IdCardFaceDetector {
    /**
     * Detects faces on an already oriented and perspective-corrected ID image.
     * A detector failure is returned as [IdCardFaceDetection.Unavailable] so
     * capture and filtering never depend on face detection succeeding.
     */
    suspend fun detect(bitmap: Bitmap): IdCardFaceDetection
}

@Singleton
class MlKitIdCardFaceDetector @Inject constructor() : IdCardFaceDetector {
    private val detector by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(MIN_FACE_SIZE)
                .build(),
        )
    }

    override suspend fun detect(bitmap: Bitmap): IdCardFaceDetection =
        suspendCancellableCoroutine { continuation ->
            detector.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { faces ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    val width = bitmap.width.toFloat().coerceAtLeast(1f)
                    val height = bitmap.height.toFloat().coerceAtLeast(1f)
                    val regions = faces.mapNotNull { face ->
                        val bounds = face.boundingBox
                        NormalizedFaceRegion(
                            left = (bounds.left / width).coerceIn(0f, 1f),
                            top = (bounds.top / height).coerceIn(0f, 1f),
                            right = (bounds.right / width).coerceIn(0f, 1f),
                            bottom = (bounds.bottom / height).coerceIn(0f, 1f),
                        ).takeIf(NormalizedFaceRegion::isUsable)
                    }.sortedByDescending(NormalizedFaceRegion::area)
                    continuation.resume(IdCardFaceDetection(regions = regions))
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(IdCardFaceDetection.Unavailable)
                    }
                }
        }

    private companion object {
        const val MIN_FACE_SIZE = 0.08f
    }
}
