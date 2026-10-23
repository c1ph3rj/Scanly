package `in`.c1ph3rj.scanly.data.text

import `in`.c1ph3rj.scanly.domain.model.OcrEngine
import `in`.c1ph3rj.scanly.domain.repository.SettingsRepository
import javax.inject.Inject

/** Which OCR engine the user has selected right now; read again for every page. */
interface OcrEngineSource {
    suspend fun selectedEngine(): OcrEngine
}

class SettingsOcrEngineSource @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : OcrEngineSource {
    override suspend fun selectedEngine(): OcrEngine = settingsRepository.getOcrEngine()
}
