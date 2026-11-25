package `in`.c1ph3rj.scanly.data.text.indra

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.domain.model.OcrEngine
import javax.inject.Inject
import javax.inject.Singleton

/** Folder under `assets/` that holds the Indra models and vocabularies. */
internal const val INDRA_ASSET_DIR = "models/indra"

/**
 * Whether this build ships the Indra OCR models. They are local-only assets (see `.gitignore`), so
 * a build made without them must not default to Indra: it would claim Indra and quietly read every
 * page with ML Kit. With them, Indra is the default and ML Kit is the fallback.
 */
@Singleton
class IndraAvailability @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** True when every model and vocabulary Indra loads is in the APK. Checked once per process. */
    val isBundled: Boolean by lazy {
        val present = try {
            context.assets.list(INDRA_ASSET_DIR)?.toSet().orEmpty()
        } catch (e: Exception) {
            emptySet()
        }
        hasAllRequiredFiles(present).also { bundled ->
            if (!bundled) {
                runCatching {
                    Log.i(TAG, "Indra OCR models are not bundled; ML Kit is the default engine. Missing: ${REQUIRED_FILES - present}")
                }
            }
        }
    }

    /** Engine used until the user picks one in Settings. */
    val defaultEngine: OcrEngine
        get() = defaultEngineFor(isBundled)

    companion object {
        private const val TAG = "IndraOcr"

        /** The seven runtime models and four vocabularies `IndraModels` loads. */
        val REQUIRED_FILES: Set<String> = setOf(
            "det.tflite",
            "orient.tflite",
            "script.tflite",
            "rec.tflite",
            "rec_cjk.tflite",
            "rec_rtl.tflite",
            "rec_world.tflite",
            "vocab_base.json",
            "vocab_cjk.json",
            "vocab_rtl.json",
            "vocab_world.json",
        )

        fun hasAllRequiredFiles(present: Set<String>): Boolean = present.containsAll(REQUIRED_FILES)

        fun defaultEngineFor(indraBundled: Boolean): OcrEngine =
            if (indraBundled) OcrEngine.INDRA else OcrEngine.ML_KIT
    }
}
