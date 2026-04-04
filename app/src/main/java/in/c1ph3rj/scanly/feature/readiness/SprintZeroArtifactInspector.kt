package `in`.c1ph3rj.scanly.feature.readiness

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.ml.ScanlyModelAssets
import `in`.c1ph3rj.scanly.core.ml.SprintZeroReadinessReport
import `in`.c1ph3rj.scanly.core.ml.SprintZeroRuntimeDecision
import java.util.Locale
import javax.inject.Inject

class SprintZeroArtifactInspector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun inspect(): SprintZeroReadinessReport {
        val modelPresent = assetFileExists(ScanlyModelAssets.modelAssetPath)
        val modelSizeBytes = assetFileSizeOrNull(ScanlyModelAssets.modelAssetPath)
        val validationImageCount = listAssetFiles(ScanlyModelAssets.validationImagesDirectory)
            .count(::isSupportedValidationImage)
        val expectedCornersCount = listAssetFiles(ScanlyModelAssets.expectedCornersDirectory)
            .count(::isExpectedCornersFile)

        val actions = buildList {
            if (!modelPresent) {
                add("Place the exported float16 model at app/src/main/assets/${ScanlyModelAssets.modelAssetPath}.")
            }
            if (validationImageCount == 0) {
                add("Add real validation images under app/src/main/assets/${ScanlyModelAssets.validationImagesDirectory}.")
            }
            if (expectedCornersCount == 0) {
                add("Add labeled corner files ending with ${ScanlyModelAssets.expectedCornersExtension} under app/src/main/assets/${ScanlyModelAssets.expectedCornersDirectory}.")
            }
            if (modelPresent && validationImageCount >= 20 && expectedCornersCount >= 10) {
                add("Install the app on a physical phone and open Sprint 0 Readiness to capture runtime details.")
                add("Record the device name, input/output tensor shapes, and smoke inference time shown in the app.")
            } else {
                add("Run the benchmark checklist in docs/sprint-0/performance-budget.md on at least one mid-range device.")
            }
        }

        return SprintZeroReadinessReport(
            modelPresent = modelPresent,
            modelSizeBytes = modelSizeBytes,
            validationImageCount = validationImageCount,
            expectedCornersCount = expectedCornersCount,
            runtimeSummary = buildString {
                append(SprintZeroRuntimeDecision.primaryRuntime)
                append(" -> ")
                append(SprintZeroRuntimeDecision.validationPath)
            },
            userActions = actions,
        )
    }

    private fun assetFileExists(assetPath: String): Boolean {
        val parent = assetPath.substringBeforeLast('/', missingDelimiterValue = "")
        val fileName = assetPath.substringAfterLast('/')
        return listAssetFiles(parent).any { it == fileName }
    }

    private fun assetFileSizeOrNull(assetPath: String): Long? = try {
        context.assets.open(assetPath).use { inputStream ->
            inputStream.available().toLong()
        }
    } catch (_: Exception) {
        null
    }

    private fun listAssetFiles(path: String): List<String> = context.assets.list(path)?.toList().orEmpty()

    private fun isSupportedValidationImage(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.US)
        return extension in ScanlyModelAssets.supportedImageExtensions
    }

    private fun isExpectedCornersFile(fileName: String): Boolean =
        fileName.endsWith(ScanlyModelAssets.expectedCornersExtension, ignoreCase = true)
}
