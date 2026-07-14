package `in`.c1ph3rj.scanly.domain.model

import org.junit.Test
import org.junit.Assert.assertEquals

class DocumentCornerModelTest {
    @Test
    fun `unknown and missing values safely fall back to accurate`() {
        assertEquals(DocumentCornerModel.ACCURATE, DocumentCornerModel.fromStorage(null))
        assertEquals(DocumentCornerModel.ACCURATE, DocumentCornerModel.fromStorage("future-model"))
    }

    @Test
    fun `legacy storage value resolves to accurate`() {
        assertEquals(DocumentCornerModel.ACCURATE, DocumentCornerModel.fromStorage("legacy"))
    }

    @Test
    fun `accurate storage value resolves to high`() {
        assertEquals(DocumentCornerModel.HIGH, DocumentCornerModel.fromStorage("accurate"))
    }

    @Test
    fun `high product-name storage alias resolves to high`() {
        assertEquals(DocumentCornerModel.HIGH, DocumentCornerModel.fromStorage("high"))
    }

    @Test
    fun `all model values round trip through storage`() {
        DocumentCornerModel.entries.forEach { model ->
            assertEquals(model, DocumentCornerModel.fromStorage(model.storageValue))
        }
    }

    @Test
    fun `display names match product ladder`() {
        assertEquals(
            listOf("Lite", "Standard", "High", "Accurate"),
            DocumentCornerModel.entries.map { it.displayName },
        )
    }
}
