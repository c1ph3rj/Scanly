package `in`.c1ph3rj.scanly.domain.search

import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.DocumentKind
import `in`.c1ph3rj.scanly.domain.model.HighlightedText
import `in`.c1ph3rj.scanly.domain.model.PageTextHit
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.SearchScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchRankerTest {
    private val lease = doc("lease", "Lease agreement", updated = 10, groupId = "home")
    private val invoice = doc("inv", "Invoice INV-2041", updated = 30)
    private val leaseNotes = doc("notes", "Lease notes", updated = 50)
    private val unrelated = doc("other", "Holiday photos", updated = 60)
    private val documents = listOf(lease, invoice, leaseNotes, unrelated)
    private val groups = listOf(DocumentGroup("home", "Home", 1, 1, null, 0, 0))
    private val hits = listOf(
        hit("lease", page = 1, count = 1),
        hit("lease", page = 0, count = 2),
        hit("inv", page = 0, count = 1),
    )

    private fun rank(
        query: String,
        textHits: List<PageTextHit>? = hits,
        scope: SearchScope = SearchScope.EVERYWHERE,
        kind: DocumentKind? = null,
    ) = LibrarySearchRanker.rank(
        SearchQuery.parse(query),
        documents,
        groups,
        mapOf("lease" to DocumentKind.CONTRACT, "inv" to DocumentKind.INVOICE),
        textHits,
        scope,
        kind,
    )

    @Test
    fun textMatchesComeFirstAndNameAlsoMatchingLeads() {
        val results = rank("lease")
        assertEquals(listOf("lease", "inv"), results.textMatches.map { it.document.id })
        assertEquals(listOf("notes"), results.nameMatches.map { it.document.id })
        assertTrue(results.textMatches.first().nameMatched)
        assertEquals(3, results.textMatches.first().textMatchCount)
        assertEquals("Home", results.textMatches.first().folderTitle)
    }

    @Test
    fun pageHitsAreInPageOrder() {
        assertEquals(listOf(0, 1), rank("lease").textMatches.first().pageHits.map { it.pageIndex })
    }

    @Test
    fun nameResultsArriveBeforeTextSearchCompletes() {
        val results = rank("lease", textHits = null)
        assertFalse(results.isTextSearchComplete)
        assertTrue(results.textMatches.isEmpty())
        assertEquals(setOf("lease", "notes"), results.nameMatches.map { it.document.id }.toSet())
    }

    @Test
    fun scopesRestrictMatching() {
        assertTrue(rank("lease", scope = SearchScope.NAMES).textMatches.isEmpty())
        val textOnly = rank("lease", scope = SearchScope.TEXT)
        assertTrue(textOnly.nameMatches.isEmpty())
        assertTrue(textOnly.folders.isEmpty())
        assertFalse(textOnly.textMatches.first().nameMatched)
    }

    @Test
    fun kindFilterKeepsOnlyThatKind() {
        assertEquals(listOf("inv"), rank("lease", kind = DocumentKind.INVOICE).textMatches.map { it.document.id })
    }

    @Test
    fun foldersMatchByName() {
        assertEquals(listOf("home"), rank("hom").folders.map { it.group.id })
    }

    @Test
    fun blankQueryWithTypeBrowsesThatType() {
        val results = rank("", kind = DocumentKind.CONTRACT)
        assertEquals(listOf("lease"), results.nameMatches.map { it.document.id })
        assertTrue(results.isTextSearchComplete)
    }

    @Test
    fun blankQueryHasNoResults() {
        assertTrue(rank("   ").isEmpty)
    }

    private fun doc(id: String, title: String, updated: Long, groupId: String? = null) = ScanDocument(
        id = id,
        title = title,
        pageCount = 2,
        coverThumbnailPath = null,
        rootDirectoryPath = "",
        createdAtMillis = 0,
        updatedAtMillis = updated,
        groupId = groupId,
    )

    private fun hit(documentId: String, page: Int, count: Int) =
        PageTextHit("$documentId-$page", documentId, page, HighlightedText("…"), count)
}
