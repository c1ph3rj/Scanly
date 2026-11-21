package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentTag
import `in`.c1ph3rj.scanly.domain.repository.TagRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveTagsUseCase @Inject constructor(private val tagRepository: TagRepository) {
    operator fun invoke(): Flow<List<DocumentTag>> = tagRepository.observeTags()
}

class ObserveDocumentTagsUseCase @Inject constructor(private val tagRepository: TagRepository) {
    operator fun invoke(documentId: String): Flow<List<DocumentTag>> = tagRepository.observeDocumentTags(documentId)
}

class ObserveTagIdsByDocumentUseCase @Inject constructor(private val tagRepository: TagRepository) {
    operator fun invoke(): Flow<Map<String, Set<String>>> = tagRepository.observeTagIdsByDocument()
}

class CreateTagUseCase @Inject constructor(private val tagRepository: TagRepository) {
    suspend operator fun invoke(name: String): ScanlyResult<DocumentTag> = tagRepository.createTag(name)
}

class RenameTagUseCase @Inject constructor(private val tagRepository: TagRepository) {
    suspend operator fun invoke(tagId: String, name: String): ScanlyResult<Unit> = tagRepository.renameTag(tagId, name)
}

class DeleteTagUseCase @Inject constructor(private val tagRepository: TagRepository) {
    suspend operator fun invoke(tagId: String): ScanlyResult<Unit> = tagRepository.deleteTag(tagId)
}

class SetDocumentTagsUseCase @Inject constructor(private val tagRepository: TagRepository) {
    suspend operator fun invoke(documentId: String, tagIds: Set<String>): ScanlyResult<Unit> =
        tagRepository.setDocumentTags(documentId, tagIds)
}
