package org.omc.model;

import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

/**
 * Unit tests for DocumentSettings class.
 * Covers builder pattern, validation, serialization, equals/hashCode, and
 * toString.
 */
class DocumentSettingsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void builder_WithDefaults_ShouldSetDefaultValuesIncludingOutputFormat() {
        // Given: Default builder
        DocumentSettings settings = DocumentSettings.builder().build();

        // Then: Check all default values
        assertNull(settings.templatePath());
        assertTrue(settings.preserveFormatting());
        assertFalse(settings.embedFonts());
        assertFalse(settings.generateTableOfContents());
        assertEquals(25, settings.marginTop());
        assertEquals(25, settings.marginBottom());
        assertEquals(25, settings.marginLeft());
        assertEquals(25, settings.marginRight());
        assertEquals(FileFormat.PDF, settings.outputFormat());
    }

    @Test
    void builder_WithCustomValuesIncludingMarginsAndOutputFormat_ShouldSetCustomValues() {
        // Given: Builder with custom values
        DocumentSettings settings = DocumentSettings.builder()
                .templatePath(Path.of("/tmp/template.docx"))
                .preserveFormatting(false)
                .embedFonts(true)
                .generateTableOfContents(true)
                .marginTop(10)
                .marginBottom(15)
                .marginLeft(20)
                .marginRight(30)
                .outputFormat(FileFormat.DOCX)
                .build();

        // Then: All values should be set
        assertEquals(Path.of("/tmp/template.docx"), settings.templatePath());
        assertFalse(settings.preserveFormatting());
        assertTrue(settings.embedFonts());
        assertTrue(settings.generateTableOfContents());
        assertEquals(10, settings.marginTop());
        assertEquals(15, settings.marginBottom());
        assertEquals(20, settings.marginLeft());
        assertEquals(30, settings.marginRight());
        assertEquals(FileFormat.DOCX, settings.outputFormat());
    }

    @Test
    void validation_MarginTopBelowZero_ShouldThrowException() {
        // Given: Invalid marginTop
        assertThrows(IllegalArgumentException.class, () -> DocumentSettings.builder()
                .marginTop(-1)
                .build());
    }

    @Test
    void validation_MarginTopAbove100_ShouldThrowException() {
        // Given: Invalid marginTop
        assertThrows(IllegalArgumentException.class, () -> DocumentSettings.builder()
                .marginTop(101)
                .build());
    }

    @Test
    void validation_MarginBottomBelowZero_ShouldThrowException() {
        // Given: Invalid marginBottom
        assertThrows(IllegalArgumentException.class, () -> DocumentSettings.builder()
                .marginBottom(-1)
                .build());
    }

    @Test
    void validation_MarginBottomAbove100_ShouldThrowException() {
        // Given: Invalid marginBottom
        assertThrows(IllegalArgumentException.class, () -> DocumentSettings.builder()
                .marginBottom(101)
                .build());
    }

    @Test
    void validation_MarginLeftBelowZero_ShouldThrowException() {
        // Given: Invalid marginLeft
        assertThrows(IllegalArgumentException.class, () -> DocumentSettings.builder()
                .marginLeft(-1)
                .build());
    }

    @Test
    void validation_MarginLeftAbove100_ShouldThrowException() {
        // Given: Invalid marginLeft
        assertThrows(IllegalArgumentException.class, () -> DocumentSettings.builder()
                .marginLeft(101)
                .build());
    }

    @Test
    void validation_MarginRightBelowZero_ShouldThrowException() {
        // Given: Invalid marginRight
        assertThrows(IllegalArgumentException.class, () -> DocumentSettings.builder()
                .marginRight(-1)
                .build());
    }

    @Test
    void validation_MarginRightAbove100_ShouldThrowException() {
        // Given: Invalid marginRight
        assertThrows(IllegalArgumentException.class, () -> DocumentSettings.builder()
                .marginRight(101)
                .build());
    }

    @Test
    void validation_OutputFormatValidDocument_ShouldAccept() {
        // Given: Valid DOCUMENT format
        DocumentSettings settings = DocumentSettings.builder()
                .outputFormat(FileFormat.HTML)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
        assertEquals(FileFormat.HTML, settings.outputFormat());
    }

    @Test
    void validation_OutputFormatInvalidVideo_ShouldThrowException() {
        // Given: Invalid VIDEO format
        assertThrows(IllegalArgumentException.class, () -> DocumentSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build());
    }

    @Test
    void validation_OutputFormatInvalidAudio_ShouldThrowException() {
        // Given: Invalid AUDIO format
        assertThrows(IllegalArgumentException.class, () -> DocumentSettings.builder()
                .outputFormat(FileFormat.MP3)
                .build());
    }

    @Test
    void validation_OutputFormatInvalidImage_ShouldThrowException() {
        // Given: Invalid IMAGE format
        assertThrows(IllegalArgumentException.class, () -> DocumentSettings.builder()
                .outputFormat(FileFormat.PNG)
                .build());
    }

    @Test
    void validation_OutputFormatNull_ShouldThrowException() {
        // Given: Null outputFormat
        assertThrows(IllegalArgumentException.class, () -> DocumentSettings.builder()
                .outputFormat(null)
                .build());
    }

    @Test
    void jsonSerialization_WithOutputFormat_ShouldPreserveOutputFormat() throws Exception {
        // Given: DocumentSettings with custom outputFormat
        DocumentSettings original = DocumentSettings.builder()
                .outputFormat(FileFormat.MARKDOWN)
                .build();

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        DocumentSettings deserialized = objectMapper.readValue(json, DocumentSettings.class);

        // Then: outputFormat should be preserved
        assertEquals(original.outputFormat(), deserialized.outputFormat());
        assertEquals(FileFormat.MARKDOWN, deserialized.outputFormat());
    }

    @Test
    void jsonSerialization_WithAllFields_ShouldPreserveAllFields() throws Exception {
        // Given: DocumentSettings with all fields set
        DocumentSettings original = DocumentSettings.builder()
                .templatePath(Path.of("/tmp/template.odt"))
                .preserveFormatting(false)
                .embedFonts(true)
                .generateTableOfContents(true)
                .marginTop(5)
                .marginBottom(10)
                .marginLeft(15)
                .marginRight(20)
                .outputFormat(FileFormat.ODT)
                .build();

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        DocumentSettings deserialized = objectMapper.readValue(json, DocumentSettings.class);

        // Then: All fields should be preserved
        assertEquals(original, deserialized);
    }

    @Test
    void equals_WithSameOutputFormat_ShouldReturnTrue() {
        // Given: Two identical DocumentSettings
        DocumentSettings settings1 = DocumentSettings.builder()
                .outputFormat(FileFormat.RTF)
                .build();
        DocumentSettings settings2 = DocumentSettings.builder()
                .outputFormat(FileFormat.RTF)
                .build();

        // Then: Should be equal
        assertEquals(settings1, settings2);
    }

    @Test
    void equals_WithDifferentOutputFormat_ShouldReturnFalse() {
        // Given: Two DocumentSettings with different outputFormat
        DocumentSettings settings1 = DocumentSettings.builder()
                .outputFormat(FileFormat.PDF)
                .build();
        DocumentSettings settings2 = DocumentSettings.builder()
                .outputFormat(FileFormat.DOCX)
                .build();

        // Then: Should not be equal
        assertNotEquals(settings1, settings2);
    }

    @Test
    void hashCode_WithSameOutputFormat_ShouldBeEqual() {
        // Given: Two identical DocumentSettings
        DocumentSettings settings1 = DocumentSettings.builder()
                .outputFormat(FileFormat.TXT)
                .build();
        DocumentSettings settings2 = DocumentSettings.builder()
                .outputFormat(FileFormat.TXT)
                .build();

        // Then: Hash codes should be equal
        assertEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void hashCode_WithDifferentOutputFormat_ShouldBeDifferent() {
        // Given: Two DocumentSettings with different outputFormat
        DocumentSettings settings1 = DocumentSettings.builder()
                .outputFormat(FileFormat.PDF)
                .build();
        DocumentSettings settings2 = DocumentSettings.builder()
                .outputFormat(FileFormat.EPUB)
                .build();

        // Then: Hash codes should be different
        assertNotEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void toString_ShouldIncludeOutputFormat() {
        // Given: DocumentSettings with custom outputFormat
        DocumentSettings settings = DocumentSettings.builder()
                .outputFormat(FileFormat.XLSX)
                .build();

        // When: Get string representation
        String toString = settings.toString();

        // Then: Should include outputFormat and other fields
        assertTrue(toString.contains("outputFormat=XLSX"));
        assertTrue(toString.contains("DocumentSettings{"));
        assertTrue(toString.contains("templatePath=null"));
        assertTrue(toString.contains("preserveFormatting=true"));
        assertTrue(toString.contains("embedFonts=false"));
        assertTrue(toString.contains("generateTableOfContents=false"));
        assertTrue(toString.contains("marginTop=25"));
        assertTrue(toString.contains("marginBottom=25"));
        assertTrue(toString.contains("marginLeft=25"));
        assertTrue(toString.contains("marginRight=25"));
    }

    @Test
    void templatePath_WithNull_ShouldBeAccepted() {
        // Given: Null templatePath
        DocumentSettings settings = DocumentSettings.builder()
                .templatePath(null)
                .build();

        // Then: templatePath should be null
        assertNull(settings.templatePath());
        assertTrue(settings.isValid());
    }

    @Test
    void templatePath_WithNonNull_ShouldBeAccepted() {
        // Given: Non-null templatePath
        Path path = Path.of("/some/path/template.pdf");
        DocumentSettings settings = DocumentSettings.builder()
                .templatePath(path)
                .build();

        // Then: templatePath should be set
        assertEquals(path, settings.templatePath());
    }

    @Test
    void booleanFlags_PreserveFormatting_ShouldBeSettable() {
        // Given: Custom preserveFormatting
        DocumentSettings settings = DocumentSettings.builder()
                .preserveFormatting(false)
                .build();

        // Then: Should be false
        assertFalse(settings.preserveFormatting());
    }

    @Test
    void booleanFlags_EmbedFonts_ShouldBeSettable() {
        // Given: Custom embedFonts
        DocumentSettings settings = DocumentSettings.builder()
                .embedFonts(true)
                .build();

        // Then: Should be true
        assertTrue(settings.embedFonts());
    }

    @Test
    void booleanFlags_GenerateTableOfContents_ShouldBeSettable() {
        // Given: Custom generateTableOfContents
        DocumentSettings settings = DocumentSettings.builder()
                .generateTableOfContents(true)
                .build();

        // Then: Should be true
        assertTrue(settings.generateTableOfContents());
    }
}