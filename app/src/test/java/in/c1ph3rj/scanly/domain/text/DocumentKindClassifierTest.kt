package `in`.c1ph3rj.scanly.domain.text

import `in`.c1ph3rj.scanly.domain.model.DocumentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentKindClassifierTest {
    @Test
    fun recognisesALease() {
        val text = "RESIDENTIAL LEASE AGREEMENT. This Lease Agreement is entered into by the Landlord " +
            "and the Tenant. The Tenant shall pay rent."
        assertEquals(DocumentKind.CONTRACT, DocumentKindClassifier.classify(text))
    }

    @Test
    fun recognisesAnInvoice() {
        assertEquals(
            DocumentKind.INVOICE,
            DocumentKindClassifier.classify("TAX INVOICE\nInvoice No: INV-2041\nBill To: Acme\nAmount due 120.00"),
        )
    }

    @Test
    fun weakOrAmbiguousEvidenceGivesNoLabel() {
        assertNull(DocumentKindClassifier.classify("Shopping list: milk, eggs, bread"))
        assertNull(DocumentKindClassifier.classify(""))
    }

    @Test
    fun cuesMatchWholeWordsOnly() {
        assertFalse(DocumentKindClassifier.containsPhrase("adobe reader", "dob"))
        assertTrue(DocumentKindClassifier.containsPhrase("dob: 1 jan", "dob"))
    }

    @Test
    fun recognisesAnIndianIdFromSurvivingCues() {
        assertEquals(
            DocumentKind.ID_DOCUMENT,
            // OCR as seen on a real card: "Identification" read as "ldentification".
            DocumentKindClassifier.classify("Government of India\nFather: Name\nUnique ldentification Authority of tndia"),
        )
    }
}
