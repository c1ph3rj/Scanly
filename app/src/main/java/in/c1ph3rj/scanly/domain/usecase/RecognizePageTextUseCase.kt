package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.PageTextRecognizer
import javax.inject.Inject

class RecognizePageTextUseCase @Inject constructor(
    private val pageTextRecognizer: PageTextRecognizer,
) {
    suspend operator fun invoke(imagePath: String) = pageTextRecognizer.recognize(imagePath)
}
