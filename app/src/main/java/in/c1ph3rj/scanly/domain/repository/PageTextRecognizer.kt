package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.RecognizedPageText

interface PageTextRecognizer {
    suspend fun recognize(imagePath: String): ScanlyResult<RecognizedPageText>
}
