package `in`.c1ph3rj.scanly.testing

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentTag
import `in`.c1ph3rj.scanly.domain.model.DocumentTagRules
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Mirrors `DefaultTagRepository`. Names are normalized with [DocumentTagRules]; a name whose key
 * is taken returns (create) or rejects (rename) the existing tag. Tagging never touches documents.
 *
 * With a [library], counts include only live documents, links of documents deleted forever
 * disappear (like the FK cascade), and tagging a document that doesn't exist fails. Without one,
 * every linked document counts.
 */
class InMemoryTagRepository(
    private val library: InMemoryScanlyLibrary? = null,
    private val now: () -> Long = defaultClock(),
) : TagRepository {
    private val tags = MutableStateFlow<List<StoredTag>>(emptyList())

    /** Document id -> tag ids, as stored (may include documents deleted since, filtered on read). */
    private val links = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    private var nextId = 1

    private val documents: Flow<List<ScanDocument>?> = library?.documents ?: flowOf(null)

    /** Tag ids per existing document right now. */
    val currentLinks: Map<String, Set<String>> get() = visibleLinks(links.value, library?.documents?.value)

    override fun observeTags(): Flow<List<DocumentTag>> =
        combine(tags, links, documents) { tagRows, linkRows, documentRows ->
            tagRows.map { it.toDomain(linkRows, documentRows) }.sortedWith(byName)
        }.distinctUntilChanged()

    override fun observeDocumentTags(documentId: String): Flow<List<DocumentTag>> =
        combine(tags, links, documents) { tagRows, linkRows, documentRows ->
            val ids = visibleLinks(linkRows, documentRows)[documentId].orEmpty()
            tagRows.filter { it.id in ids }.map { it.toDomain(linkRows, documentRows) }.sortedWith(byName)
        }.distinctUntilChanged()

    override fun observeTagIdsByDocument(): Flow<Map<String, Set<String>>> =
        combine(links, documents) { linkRows, documentRows -> visibleLinks(linkRows, documentRows) }
            .distinctUntilChanged()

    override suspend fun createTag(name: String): ScanlyResult<DocumentTag> = runLibraryResult(FAILURE) {
        val normalized = DocumentTagRules.normalizeName(name)
        require(normalized.isNotBlank()) { FAILURE }
        val key = DocumentTagRules.key(normalized)
        val existing = tags.value.firstOrNull { it.key == key }
        val tag = existing ?: StoredTag(id = "tag-${nextId++}", name = normalized, key = key, createdAtMillis = now())
            .also { tags.value = tags.value + it }
        tag.toDomain(links.value, library?.documents?.value)
    }

    override suspend fun renameTag(tagId: String, name: String): ScanlyResult<Unit> = runLibraryResult(FAILURE) {
        val normalized = DocumentTagRules.normalizeName(name)
        require(normalized.isNotBlank()) { FAILURE }
        val key = DocumentTagRules.key(normalized)
        requireNotNull(tags.value.firstOrNull { it.id == tagId }) { FAILURE }
        require(tags.value.none { it.key == key && it.id != tagId }) { FAILURE }
        tags.value = tags.value.map { if (it.id == tagId) it.copy(name = normalized, key = key) else it }
    }

    override suspend fun deleteTag(tagId: String): ScanlyResult<Unit> = runLibraryResult(FAILURE) {
        links.value = links.value.mapValues { (_, ids) -> ids - tagId }.filterValues { it.isNotEmpty() }
        tags.value = tags.value.filterNot { it.id == tagId }
    }

    override suspend fun setDocumentTags(documentId: String, tagIds: Set<String>): ScanlyResult<Unit> =
        runLibraryResult(FAILURE) {
            if (library != null) requireNotNull(library.document(documentId)) { FAILURE }
            val known = tagIds.filterTo(LinkedHashSet()) { id -> tags.value.any { it.id == id } }
            links.value = if (known.isEmpty()) links.value - documentId else links.value + (documentId to known)
        }

    private fun visibleLinks(linkRows: Map<String, Set<String>>, documentRows: List<ScanDocument>?): Map<String, Set<String>> {
        if (documentRows == null) return linkRows
        val existing = documentRows.mapTo(HashSet()) { it.id }
        return linkRows.filterKeys { it in existing }
    }

    private fun StoredTag.toDomain(linkRows: Map<String, Set<String>>, documentRows: List<ScanDocument>?): DocumentTag {
        val tagged = visibleLinks(linkRows, documentRows).filterValues { id in it }.keys
        val count = if (documentRows == null) {
            tagged.size
        } else {
            documentRows.count { it.id in tagged && it.deletedAtMillis == null }
        }
        return DocumentTag(id = id, name = name, createdAtMillis = createdAtMillis, documentCount = count)
    }

    private data class StoredTag(val id: String, val name: String, val key: String, val createdAtMillis: Long)

    private companion object {
        const val FAILURE = "Could not save your changes."

        /** Like `ORDER BY name COLLATE NOCASE` (ASCII case folding), then oldest first. */
        val byName = compareBy<DocumentTag>({ it.name.lowercase(Locale.ROOT) }, { it.createdAtMillis })

        fun defaultClock(): () -> Long {
            val clock = AtomicLong(1_700_000_000_000L)
            return { clock.getAndAdd(1_000L) }
        }
    }
}
