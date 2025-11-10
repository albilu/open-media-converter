package org.omc.model;

import org.omc.model.FormatCategory;
import org.omc.model.MediaMetadata;
import org.omc.model.DocumentMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DocumentMetadata class.
 */
class DocumentMetadataTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Builder tests
    @Test
    void testBuilder_AllFieldsSet_BuildsCorrectly() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(10)
                .title("Sample Document")
                .author("John Doe")
                .subject("Test Subject")
                .creator("LibreOffice")
                .build();

        assertEquals(10, metadata.getPageCount());
        assertEquals("Sample Document", metadata.getTitle());
        assertEquals("John Doe", metadata.getAuthor());
        assertEquals("Test Subject", metadata.getSubject());
        assertEquals("LibreOffice", metadata.getCreator());
    }

    @Test
    void testBuilder_DefaultValues_BuildsWithDefaults() {
        DocumentMetadata metadata = DocumentMetadata.builder().build();

        assertEquals(0, metadata.getPageCount());
        assertNull(metadata.getTitle());
        assertNull(metadata.getAuthor());
        assertNull(metadata.getSubject());
        assertNull(metadata.getCreator());
    }

    // Validation tests
    @Test
    void testIsValid_ZeroPages_ReturnsTrue() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(0)
                .build();

        assertTrue(metadata.isValid());
    }

    @Test
    void testIsValid_PositivePages_ReturnsTrue() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(10)
                .build();

        assertTrue(metadata.isValid());
    }

    @Test
    void testIsValid_NegativePages_ReturnsFalse() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(-1)
                .build();

        assertFalse(metadata.isValid());
    }

    // Helper methods tests
    @Test
    void testHasMetadata_NoMetadata_ReturnsFalse() {
        DocumentMetadata metadata = DocumentMetadata.builder().build();

        assertFalse(metadata.hasMetadata());
    }

    @Test
    void testHasMetadata_HasTitle_ReturnsTrue() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .title("Sample Document")
                .build();

        assertTrue(metadata.hasMetadata());
    }

    @Test
    void testHasMetadata_HasAuthor_ReturnsTrue() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .author("John Doe")
                .build();

        assertTrue(metadata.hasMetadata());
    }

    @Test
    void testHasMetadata_HasSubject_ReturnsTrue() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .subject("Test Subject")
                .build();

        assertTrue(metadata.hasMetadata());
    }

    @Test
    void testHasMetadata_BlankTitle_ReturnsFalse() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .title("")
                .build();

        assertFalse(metadata.hasMetadata());
    }

    @Test
    void testHasMetadata_WhitespaceTitle_ReturnsFalse() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .title("   ")
                .build();

        assertFalse(metadata.hasMetadata());
    }

    @Test
    void testIsMultiPage_SinglePage_ReturnsFalse() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(1)
                .build();

        assertFalse(metadata.isMultiPage());
    }

    @Test
    void testIsMultiPage_MultiplePages_ReturnsTrue() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(5)
                .build();

        assertTrue(metadata.isMultiPage());
    }

    @Test
    void testIsMultiPage_ZeroPages_ReturnsFalse() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(0)
                .build();

        assertFalse(metadata.isMultiPage());
    }

    // getSummary tests
    @Test
    void testGetSummary_WithTitleAndAuthor_ReturnsFormattedString() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(10)
                .title("Sample Document")
                .author("John Doe")
                .build();

        String summary = metadata.getSummary();
        assertTrue(summary.contains("Sample Document"));
        assertTrue(summary.contains("10 pages"));
        assertTrue(summary.contains("by John Doe"));
    }

    @Test
    void testGetSummary_NoTitle_WithAuthor_ReturnsFormattedString() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(5)
                .author("Jane Smith")
                .build();

        String summary = metadata.getSummary();
        assertTrue(summary.contains("5 pages"));
        assertTrue(summary.contains("by Jane Smith"));
        assertFalse(summary.contains(", ,"));
    }

    @Test
    void testGetSummary_SinglePage_ReturnsSingular() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(1)
                .build();

        String summary = metadata.getSummary();
        assertTrue(summary.contains("1 page"));
    }

    @Test
    void testGetSummary_ZeroPages_ReturnsPlural() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(0)
                .build();

        String summary = metadata.getSummary();
        assertTrue(summary.contains("0 pages"));
    }

    @Test
    void testGetSummary_NoMetadata_ReturnsOnlyPages() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(3)
                .build();

        String summary = metadata.getSummary();
        assertEquals("3 pages", summary);
    }

    // getCategory tests
    @Test
    void testGetCategory_ReturnsDocument() {
        DocumentMetadata metadata = DocumentMetadata.builder().build();

        assertEquals(FormatCategory.DOCUMENT, metadata.getCategory());
    }

    // equals/hashCode/toString tests
    @Test
    void testEquals_SameObject_ReturnsTrue() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(10)
                .title("Sample Document")
                .author("John Doe")
                .subject("Test Subject")
                .creator("LibreOffice")
                .build();

        assertEquals(metadata, metadata);
    }

    @Test
    void testEquals_EqualObjects_ReturnsTrue() {
        DocumentMetadata metadata1 = DocumentMetadata.builder()
                .pageCount(10)
                .title("Sample Document")
                .author("John Doe")
                .subject("Test Subject")
                .creator("LibreOffice")
                .build();

        DocumentMetadata metadata2 = DocumentMetadata.builder()
                .pageCount(10)
                .title("Sample Document")
                .author("John Doe")
                .subject("Test Subject")
                .creator("LibreOffice")
                .build();

        assertEquals(metadata1, metadata2);
    }

    @Test
    void testEquals_DifferentObjects_ReturnsFalse() {
        DocumentMetadata metadata1 = DocumentMetadata.builder()
                .pageCount(10)
                .build();

        DocumentMetadata metadata2 = DocumentMetadata.builder()
                .pageCount(5)
                .build();

        assertNotEquals(metadata1, metadata2);
    }

    @Test
    void testEquals_Null_ReturnsFalse() {
        DocumentMetadata metadata = DocumentMetadata.builder().build();

        assertNotEquals(null, metadata);
    }

    @Test
    void testEquals_DifferentClass_ReturnsFalse() {
        DocumentMetadata metadata = DocumentMetadata.builder().build();

        assertNotEquals("string", metadata);
    }

    @Test
    void testHashCode_EqualObjects_SameHashCode() {
        DocumentMetadata metadata1 = DocumentMetadata.builder()
                .pageCount(10)
                .title("Sample Document")
                .author("John Doe")
                .subject("Test Subject")
                .creator("LibreOffice")
                .build();

        DocumentMetadata metadata2 = DocumentMetadata.builder()
                .pageCount(10)
                .title("Sample Document")
                .author("John Doe")
                .subject("Test Subject")
                .creator("LibreOffice")
                .build();

        assertEquals(metadata1.hashCode(), metadata2.hashCode());
    }

    @Test
    void testToString_ContainsAllFields() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(10)
                .title("Sample Document")
                .author("John Doe")
                .subject("Test Subject")
                .creator("LibreOffice")
                .build();

        String toString = metadata.toString();
        assertTrue(toString.contains("DocumentMetadata"));
        assertTrue(toString.contains("pageCount=10"));
        assertTrue(toString.contains("title='Sample Document'"));
        assertTrue(toString.contains("author='John Doe'"));
        assertTrue(toString.contains("subject='Test Subject'"));
        assertTrue(toString.contains("creator='LibreOffice'"));
    }

    // JSON serialization tests
    @Test
    void testJsonSerialization_SerializesCorrectly() throws Exception {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(10)
                .title("Sample Document")
                .author("John Doe")
                .subject("Test Subject")
                .creator("LibreOffice")
                .build();

        String json = objectMapper.writeValueAsString(metadata);

        assertTrue(json.contains("\"type\":\"document\""));
        assertTrue(json.contains("\"pageCount\":10"));
        assertTrue(json.contains("\"title\":\"Sample Document\""));
        assertTrue(json.contains("\"author\":\"John Doe\""));
        assertTrue(json.contains("\"subject\":\"Test Subject\""));
        assertTrue(json.contains("\"creator\":\"LibreOffice\""));
    }

    @Test
    void testJsonDeserialization_DeserializesCorrectly() throws Exception {
        String json = """
                {
                    "type": "document",
                    "pageCount": 10,
                    "title": "Sample Document",
                    "author": "John Doe",
                    "subject": "Test Subject",
                    "creator": "LibreOffice"
                }
                """;

        MediaMetadata metadata = objectMapper.readValue(json, MediaMetadata.class);

        assertInstanceOf(DocumentMetadata.class, metadata);
        DocumentMetadata documentMetadata = (DocumentMetadata) metadata;
        assertEquals(10, documentMetadata.getPageCount());
        assertEquals("Sample Document", documentMetadata.getTitle());
        assertEquals("John Doe", documentMetadata.getAuthor());
        assertEquals("Test Subject", documentMetadata.getSubject());
        assertEquals("LibreOffice", documentMetadata.getCreator());
    }

    // Edge cases
    @Test
    void testBuilder_NegativePageCount_Accepted() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(-1)
                .build();

        assertEquals(-1, metadata.getPageCount());
    }

    @Test
    void testIsValid_LargePageCount_ReturnsTrue() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(10000)
                .build();

        assertTrue(metadata.isValid());
    }

    @Test
    void testHasMetadata_NullTitleNullAuthorNullSubject_ReturnsFalse() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .title(null)
                .author(null)
                .subject(null)
                .build();

        assertFalse(metadata.hasMetadata());
    }

    @Test
    void testGetSummary_NullTitleNullAuthor_ReturnsOnlyPages() {
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(5)
                .title(null)
                .author(null)
                .build();

        String summary = metadata.getSummary();
        assertEquals("5 pages", summary);
    }

    @Test
    void testEquals_NullFields_EqualObjects() {
        DocumentMetadata metadata1 = DocumentMetadata.builder()
                .title(null)
                .author(null)
                .subject(null)
                .creator(null)
                .build();

        DocumentMetadata metadata2 = DocumentMetadata.builder()
                .title(null)
                .author(null)
                .subject(null)
                .creator(null)
                .build();

        assertEquals(metadata1, metadata2);
    }
}