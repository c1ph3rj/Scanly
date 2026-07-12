package `in`.c1ph3rj.scanly.domain.model

import org.junit.Test
import org.junit.Assert.assertEquals

class DocumentCornerModelTest {
    @Test
    fun `unknown and missing values safely fall back to legacy`() {
        assertEquals(DocumentCornerModel.LEGACY, DocumentCornerModel.fromStorage(null))
        assertEquals(DocumentCornerModel.LEGACY, DocumentCornerModel.fromStorage("future-model"))
    }

    @Test
    fun `all model values round trip through storage`() {
        DocumentCornerModel.entries.forEach { model ->
            assertEquals(model, DocumentCornerModel.fromStorage(model.storageValue))
        }
    }
}
