package `in`.c1ph3rj.scanly.data.tag

import androidx.room.withTransaction
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.data.common.LibraryOperationCoordinator
import `in`.c1ph3rj.scanly.data.local.db.ScanlyDatabase
import `in`.c1ph3rj.scanly.data.local.db.dao.DocumentDao
import `in`.c1ph3rj.scanly.data.local.db.dao.TagDao
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentTagEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.TagEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.TagWithCount
import `in`.c1ph3rj.scanly.domain.model.DocumentTag
import `in`.c1ph3rj.scanly.domain.model.DocumentTagRules
import `in`.c1ph3rj.scanly.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tags live in `tags`; links in `document_tags`. Linking never touches the document row, so tagging
 * doesn't reorder the library (`updatedAtMillis`). Trashing a document keeps its links (counts
 * only include live documents); deleting it forever removes them.
 */
@Singleton
class DefaultTagRepository @Inject constructor(
    private val database: ScanlyDatabase,
    private val tagDao: TagDao,
    private val documentDao: DocumentDao,
    private val dispatchers: ScanlyDispatchers,
    private val operationCoordinator: LibraryOperationCoordinator,
) : TagRepository {

    override fun observeTags(): Flow<List<DocumentTag>> =
        tagDao.observeTagsWithCounts().map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeDocumentTags(documentId: String): Flow<List<DocumentTag>> =
        tagDao.observeTagsForDocument(documentId).map { rows -> rows.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeTagIdsByDocument(): Flow<Map<String, Set<String>>> =
        tagDao.observeDocumentTags().map { links ->
            links.groupBy(DocumentTagEntity::documentId) { it.tagId }
                .mapValues { (_, tagIds) -> tagIds.toSet() }
        }.distinctUntilChanged()

    override suspend fun createTag(name: String): ScanlyResult<DocumentTag> = mutate {
        val normalized = requireName(name)
        val key = DocumentTagRules.key(normalized)
        database.withTransaction {
            val existing = tagDao.getTagByKey(key)
            if (existing != null) {
                existing.toDomain(documentCount = tagDao.getLiveDocumentCount(existing.id))
            } else {
                val tag = TagEntity(
                    id = UUID.randomUUID().toString(),
                    name = normalized,
                    nameKey = key,
                    createdAtMillis = System.currentTimeMillis(),
                )
                tagDao.insertTag(tag)
                tag.toDomain(documentCount = 0)
            }
        }
    }

    override suspend fun renameTag(tagId: String, name: String): ScanlyResult<Unit> = mutate {
        val normalized = requireName(name)
        val key = DocumentTagRules.key(normalized)
        database.withTransaction {
            val tag = requireNotNull(tagDao.getTag(tagId)) { "Tag $tagId not found" }
            val clash = tagDao.getTagByKey(key)
            require(clash == null || clash.id == tagId) { "Another tag is already named \"$normalized\"" }
            if (tag.name != normalized || tag.nameKey != key) {
                tagDao.updateTag(tag.copy(name = normalized, nameKey = key))
            }
            Unit
        }
    }

    override suspend fun deleteTag(tagId: String): ScanlyResult<Unit> = mutate {
        database.withTransaction {
            // The FK cascades too; deleting the links first doesn't rely on foreign_keys being on.
            tagDao.deleteTagLinks(tagId)
            tagDao.deleteTag(tagId)
        }
    }

    override suspend fun setDocumentTags(documentId: String, tagIds: Set<String>): ScanlyResult<Unit> = mutate {
        database.withTransaction {
            checkNotNull(documentDao.getDocument(documentId)) { "Document $documentId not found" }
            val known = if (tagIds.isEmpty()) emptyList() else tagDao.getExistingTagIds(tagIds.toList())
            tagDao.deleteDocumentTags(documentId)
            if (known.isNotEmpty()) {
                tagDao.insertDocumentTags(known.map { DocumentTagEntity(documentId = documentId, tagId = it) })
            }
            Unit
        }
    }

    private fun requireName(raw: String): String {
        val normalized = DocumentTagRules.normalizeName(raw)
        require(normalized.isNotBlank()) { "Tag name is blank" }
        return normalized
    }

    private suspend fun <T> mutate(block: suspend () -> T): ScanlyResult<T> = withContext(dispatchers.io) {
        runCatching { operationCoordinator.withMutation { block() } }.fold(
            onSuccess = { ScanlyResult.Success(it) },
            onFailure = { throwable ->
                ScanlyResult.Failure(
                    // Repository messages are for logs; the UI validates names itself and shows its own text.
                    ScanlyError(
                        message = appString(R.string.default_page_text_repository_could_not_save_your_changes),
                        cause = throwable,
                    ),
                )
            },
        )
    }
}

/** Copies every tag of [sourceDocumentId] onto [targetDocumentId]. Run inside a transaction. */
internal suspend fun copyDocumentTags(tagDao: TagDao, sourceDocumentId: String, targetDocumentId: String) {
    val tagIds = tagDao.getTagIdsForDocument(sourceDocumentId)
    if (tagIds.isNotEmpty()) {
        tagDao.insertDocumentTags(tagIds.map { DocumentTagEntity(documentId = targetDocumentId, tagId = it) })
    }
}

private fun TagWithCount.toDomain(): DocumentTag = DocumentTag(
    id = id,
    name = name,
    createdAtMillis = createdAtMillis,
    documentCount = documentCount,
)

private fun TagEntity.toDomain(documentCount: Int): DocumentTag = DocumentTag(
    id = id,
    name = name,
    createdAtMillis = createdAtMillis,
    documentCount = documentCount,
)
