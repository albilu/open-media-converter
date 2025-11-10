package org.omc.model;

import org.omc.model.ImageSettings;
import org.omc.model.FormatCategory;
import org.omc.model.DocumentSettings;
import org.omc.model.ConversionSettings;
import org.omc.model.VideoSettings;
import org.omc.model.AudioSettings;
import org.omc.model.FileFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ConversionSettings class.
 * Tests builder pattern, validation, serialization, and object methods.
 */
class ConversionSettingsTest {

    @TempDir
    Path tempDir;

    // Test data helper methods (to avoid @TempDir null initialization issue)
    private Path validOutputDir() {
        return tempDir.resolve("output");
    }

    private VideoSettings videoSettings() {
        return VideoSettings.builder()
                .codec("libx264")
                .bitrate(5000)
                .frameRate(30)
                .outputFormat(FileFormat.MP4)
                .build();
    }

    private AudioSettings audioSettings() {
        return AudioSettings.builder()
                .codec("aac")
                .bitrate(192)
                .outputFormat(FileFormat.MP3)
                .build();
    }

    private ImageSettings imageSettings() {
        return ImageSettings.builder()
                .quality(85)
                .outputFormat(FileFormat.PNG)
                .build();
    }

    private DocumentSettings documentSettings() {
        return DocumentSettings.builder()
                .outputFormat(FileFormat.PDF)
                .build();
    }

    @Test
    void shouldBuildWithAllSettings() {
        // Given
        Path outputDir = validOutputDir();

        // When
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .overwriteExisting(true)
                .createSubdirectory(false)
                .parallelConversions(8)
                .videoSettings(videoSettings())
                .audioSettings(audioSettings())
                .imageSettings(imageSettings())
                .documentSettings(documentSettings())
                .build();

        // Then
        assertEquals(outputDir, settings.outputDirectory());
        assertTrue(settings.overwriteExisting());
        assertFalse(settings.createSubdirectory());
        assertEquals(8, settings.parallelConversions());
        assertEquals(videoSettings(), settings.videoSettings());
        assertEquals(audioSettings(), settings.audioSettings());
        assertEquals(imageSettings(), settings.imageSettings());
        assertEquals(documentSettings(), settings.documentSettings());
    }

    @Test
    void shouldBuildWithDefaults() {
        // Given
        Path outputDir = validOutputDir();

        // When
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .build();

        // Then
        assertEquals(outputDir, settings.outputDirectory());
        assertFalse(settings.overwriteExisting());
        assertFalse(settings.createSubdirectory());
        assertEquals(4, settings.parallelConversions());
        assertNull(settings.videoSettings());
        assertNull(settings.audioSettings());
        assertNull(settings.imageSettings());
        assertNull(settings.documentSettings());
    }

    @Test
    void shouldBuildWithOutputFormatMethod() {
        // Given
        Path outputDir = validOutputDir();

        // When
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .outputFormat(FileFormat.MP4)
                .outputFormat(FileFormat.MP3)
                .outputFormat(FileFormat.PNG)
                .outputFormat(FileFormat.PDF)
                .build();

        // Then
        assertEquals(FileFormat.MP4, settings.videoSettings().outputFormat());
        assertEquals(FileFormat.MP3, settings.audioSettings().outputFormat());
        assertEquals(FileFormat.PNG, settings.imageSettings().outputFormat());
        assertEquals(FileFormat.PDF, settings.documentSettings().outputFormat());
    }

    @Test
    void shouldBuildWithOutputFormatMethodUpdatingExistingSettings() {
        // Given
        Path outputDir = validOutputDir();

        // When
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(videoSettings())
                .outputFormat(FileFormat.AVI) // Should update existing video settings
                .build();

        // Then
        assertEquals(FileFormat.AVI, settings.videoSettings().outputFormat());
        assertEquals("libx264", settings.videoSettings().codec()); // Other fields preserved
    }

    @Test
    void shouldReturnCorrectOutputFormatForCategory() {
        // Given
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(validOutputDir())
                .videoSettings(videoSettings())
                .audioSettings(audioSettings())
                .imageSettings(imageSettings())
                .documentSettings(documentSettings())
                .build();

        // When & Then
        assertEquals(FileFormat.MP4, settings.outputFormat(FormatCategory.VIDEO));
        assertEquals(FileFormat.MP3, settings.outputFormat(FormatCategory.AUDIO));
        assertEquals(FileFormat.PNG, settings.outputFormat(FormatCategory.IMAGE));
        assertEquals(FileFormat.PDF, settings.outputFormat(FormatCategory.DOCUMENT));
        assertNull(settings.outputFormat(FormatCategory.UNKNOWN));
    }

    @Test
    void shouldReturnNullOutputFormatForUnsetCategory() {
        // Given
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(validOutputDir())
                .videoSettings(videoSettings())
                .build();

        // When & Then
        assertEquals(FileFormat.MP4, settings.outputFormat(FormatCategory.VIDEO));
        assertNull(settings.outputFormat(FormatCategory.AUDIO));
        assertNull(settings.outputFormat(FormatCategory.IMAGE));
        assertNull(settings.outputFormat(FormatCategory.DOCUMENT));
    }

    @Test
    void shouldReturnFirstNonNullOutputFormat() {
        // Given - Video and Audio set
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(validOutputDir())
                .videoSettings(videoSettings())
                .audioSettings(audioSettings())
                .build();

        // When & Then
        assertEquals(FileFormat.MP4, settings.outputFormat()); // Video first

        // Given - Only Audio set
        ConversionSettings audioOnly = ConversionSettings.builder()
                .outputDirectory(validOutputDir())
                .audioSettings(audioSettings())
                .build();

        // When & Then
        assertEquals(FileFormat.MP3, audioOnly.outputFormat()); // Audio first available

        // Given - Only Image set
        ConversionSettings imageOnly = ConversionSettings.builder()
                .outputDirectory(validOutputDir())
                .imageSettings(imageSettings())
                .build();

        // When & Then
        assertEquals(FileFormat.PNG, imageOnly.outputFormat()); // Image first available

        // Given - Only Document set
        ConversionSettings documentOnly = ConversionSettings.builder()
                .outputDirectory(validOutputDir())
                .documentSettings(documentSettings())
                .build();

        // When & Then
        assertEquals(FileFormat.PDF, documentOnly.outputFormat()); // Document first available

        // Given - None set
        ConversionSettings none = ConversionSettings.builder()
                .outputDirectory(validOutputDir())
                .build();

        // When & Then
        assertNull(none.outputFormat()); // No formats set
    }

    @Test
    void shouldBeValidWithAllValidSettings() throws IOException {
        // Given
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(4)
                .videoSettings(videoSettings())
                .audioSettings(audioSettings())
                .imageSettings(imageSettings())
                .documentSettings(documentSettings())
                .build();

        // When & Then
        assertTrue(settings.isValid());
    }

    @Test
    void shouldBeInvalidWithNullOutputDirectory() {
        // Given
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(null)
                .build();

        // When & Then
        assertFalse(settings.isValid());
    }

    @Test
    void shouldBeInvalidWithNonExistentOutputDirectory() {
        // Given
        Path nonExistent = tempDir.resolve("nonexistent");
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(nonExistent)
                .build();

        // When & Then
        assertFalse(settings.isValid());
    }

    @Test
    void shouldBeInvalidWithNonWritableOutputDirectory() throws IOException {
        // Given
        Path readOnlyDir = tempDir.resolve("readonly");
        Files.createDirectories(readOnlyDir);
        readOnlyDir.toFile().setWritable(false);
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(readOnlyDir)
                .build();

        // When & Then
        assertFalse(settings.isValid());
    }

    @Test
    void shouldBeInvalidWithParallelConversionsTooLow() throws IOException {
        // Given
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(0)
                .build();

        // When & Then
        assertFalse(settings.isValid());
    }

    @Test
    void shouldBeInvalidWithParallelConversionsTooHigh() throws IOException {
        // Given
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(17)
                .build();

        // When & Then
        assertFalse(settings.isValid());
    }

    @Test
    void shouldBeInvalidWithInvalidVideoSettings() throws IOException {
        // Given
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);

        // When & Then - Builder should throw IllegalArgumentException for invalid codec
        assertThrows(IllegalArgumentException.class, () -> VideoSettings.builder()
                .codec("") // Invalid codec
                .outputFormat(FileFormat.MP4)
                .build());
    }

    @Test
    void shouldBeInvalidWithInvalidAudioSettings() throws IOException {
        // Given
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);

        // When & Then - Builder should throw IllegalArgumentException for invalid codec
        assertThrows(IllegalArgumentException.class, () -> AudioSettings.builder()
                .codec("") // Invalid codec
                .outputFormat(FileFormat.MP3)
                .build());
    }

    @Test
    void shouldBeInvalidWithInvalidImageSettings() throws IOException {
        // Given
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);

        // When & Then - Builder should throw IllegalArgumentException for invalid
        // quality
        assertThrows(IllegalArgumentException.class, () -> ImageSettings.builder()
                .quality(-5) // Invalid quality
                .outputFormat(FileFormat.PNG)
                .build());
    }

    @Test
    void shouldBeInvalidWithInvalidDocumentSettings() throws IOException {
        // Given
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);

        // When & Then - Builder should throw IllegalArgumentException for invalid
        // margin
        assertThrows(IllegalArgumentException.class, () -> DocumentSettings.builder()
                .marginTop(-5) // Invalid margin
                .outputFormat(FileFormat.PDF)
                .build());
    }

    @Test
    void shouldSerializeAndDeserializeWithJackson() throws IOException {
        // Given
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        ConversionSettings original = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .overwriteExisting(true)
                .createSubdirectory(true)
                .parallelConversions(6)
                .videoSettings(videoSettings())
                .audioSettings(audioSettings())
                .imageSettings(imageSettings())
                .documentSettings(documentSettings())
                .build();

        ObjectMapper mapper = new ObjectMapper();

        // When
        String json = mapper.writeValueAsString(original);
        ConversionSettings deserialized = mapper.readValue(json, ConversionSettings.class);

        // Then
        assertEquals(original, deserialized);
        assertEquals(original.outputDirectory(), deserialized.outputDirectory());
        assertEquals(original.overwriteExisting(), deserialized.overwriteExisting());
        assertEquals(original.createSubdirectory(), deserialized.createSubdirectory());
        assertEquals(original.parallelConversions(), deserialized.parallelConversions());
        assertEquals(original.videoSettings(), deserialized.videoSettings());
        assertEquals(original.audioSettings(), deserialized.audioSettings());
        assertEquals(original.imageSettings(), deserialized.imageSettings());
        assertEquals(original.documentSettings(), deserialized.documentSettings());
    }

    @Test
    void shouldIgnoreUnknownJsonProperties() throws IOException {
        // Given
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        String jsonWithExtraFields = """
                {
                    "outputDirectory": "%s",
                    "overwriteExisting": false,
                    "createSubdirectory": false,
                    "parallelConversions": 4,
                    "unknownField": "should be ignored",
                    "anotherUnknown": 123,
                    "nestedUnknown": {
                        "field": "value"
                    }
                }
                """.formatted(outputDir.toString().replace("\\", "\\\\"));

        ObjectMapper mapper = new ObjectMapper();

        // When
        ConversionSettings settings = mapper.readValue(jsonWithExtraFields, ConversionSettings.class);

        // Then
        assertEquals(outputDir, settings.outputDirectory());
        assertFalse(settings.overwriteExisting());
        assertFalse(settings.createSubdirectory());
        assertEquals(4, settings.parallelConversions());
        assertNull(settings.videoSettings());
        assertNull(settings.audioSettings());
        assertNull(settings.imageSettings());
        assertNull(settings.documentSettings());
    }

    @Test
    void shouldSupportBackwardCompatibilityWithOldOutputFormatField() throws IOException {
        // Given - Simulate old JSON format with single outputFormat field
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        String oldJsonFormat = """
                {
                    "outputDirectory": "%s",
                    "overwriteExisting": false,
                    "createSubdirectory": false,
                    "parallelConversions": 4,
                    "outputFormat": "MP4"
                }
                """.formatted(outputDir.toString().replace("\\", "\\\\"));

        ObjectMapper mapper = new ObjectMapper();

        // When
        ConversionSettings settings = mapper.readValue(oldJsonFormat, ConversionSettings.class);

        // Then - Should load successfully, ignoring unknown outputFormat field
        assertEquals(outputDir, settings.outputDirectory());
        assertFalse(settings.overwriteExisting());
        assertFalse(settings.createSubdirectory());
        assertEquals(4, settings.parallelConversions());
        // The old outputFormat field should be ignored due to @JsonIgnoreProperties
    }

    @Test
    void shouldImplementEqualsCorrectly() throws IOException {
        // Given
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        Path anotherDir = tempDir.resolve("another");
        Files.createDirectories(anotherDir);

        ConversionSettings settings1 = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .overwriteExisting(true)
                .createSubdirectory(false)
                .parallelConversions(4)
                .videoSettings(videoSettings())
                .build();

        ConversionSettings settings2 = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .overwriteExisting(true)
                .createSubdirectory(false)
                .parallelConversions(4)
                .videoSettings(videoSettings())
                .build();

        ConversionSettings settings3 = ConversionSettings.builder()
                .outputDirectory(anotherDir) // Different
                .overwriteExisting(true)
                .createSubdirectory(false)
                .parallelConversions(4)
                .videoSettings(videoSettings())
                .build();

        // When & Then
        assertEquals(settings1, settings2);
        assertNotEquals(settings1, settings3);
        assertNotEquals(settings1, null);
        assertNotEquals(settings1, "not a settings object");
    }

    @Test
    void shouldImplementHashCodeConsistently() throws IOException {
        // Given
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        ConversionSettings settings1 = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .overwriteExisting(true)
                .parallelConversions(4)
                .build();

        ConversionSettings settings2 = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .overwriteExisting(true)
                .parallelConversions(4)
                .build();

        // When & Then
        assertEquals(settings1.hashCode(), settings2.hashCode());
        assertEquals(settings1, settings2); // Equal objects should have equal hash codes
    }

    @Test
    void shouldImplementToString() throws IOException {
        // Given
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .overwriteExisting(true)
                .createSubdirectory(false)
                .parallelConversions(8)
                .videoSettings(videoSettings())
                .build();

        // When
        String toString = settings.toString();

        // Then
        assertTrue(toString.startsWith("ConversionSettings{"));
        assertTrue(toString.contains("outputDirectory=" + outputDir));
        assertTrue(toString.contains("overwriteExisting=true"));
        assertTrue(toString.contains("createSubdirectory=false"));
        assertTrue(toString.contains("parallelConversions=8"));
        assertTrue(toString.contains("videoSettings=" + videoSettings()));
        assertTrue(toString.contains("audioSettings=null"));
        assertTrue(toString.contains("imageSettings=null"));
        assertTrue(toString.contains("documentSettings=null"));
        assertTrue(toString.endsWith("}"));
    }

    @Test
    void shouldHandleNullSettingsInOutputFormatMethods() {
        // Given
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(validOutputDir())
                .build();

        // When & Then
        assertNull(settings.outputFormat(FormatCategory.VIDEO));
        assertNull(settings.outputFormat(FormatCategory.AUDIO));
        assertNull(settings.outputFormat(FormatCategory.IMAGE));
        assertNull(settings.outputFormat(FormatCategory.DOCUMENT));
        assertNull(settings.outputFormat()); // No formats set
    }

    @Test
    void shouldHandleNullOutputFormatsInSettings() {
        // Given - This test demonstrates that null settings don't cause NPE
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(validOutputDir())
                .build();

        // When & Then - Should handle null settings gracefully
        assertNull(settings.outputFormat(FormatCategory.VIDEO));
        assertNull(settings.outputFormat(FormatCategory.AUDIO));
        assertNull(settings.outputFormat(FormatCategory.IMAGE));
        assertNull(settings.outputFormat(FormatCategory.DOCUMENT));
    }

    @Test
    void shouldBuildWithMinimalRequiredFields() {
        // Given
        Path outputDir = validOutputDir();

        // When
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .build();

        // Then
        assertNotNull(settings);
        assertEquals(outputDir, settings.outputDirectory());
        // Other fields should have defaults
    }

    // ========== Delete Original File Tests (REQ-GEN-1.1) ==========

    @Test
    void shouldDefaultDeleteOriginalFileToFalse() {
        // Given: Builder with defaults
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(validOutputDir())
                .build();

        // Then: deleteOriginalFile should default to false
        assertFalse(settings.deleteOriginalFile());
    }

    @Test
    void shouldSetDeleteOriginalFileToTrue() {
        // Given: Builder with deleteOriginalFile set to true
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(validOutputDir())
                .deleteOriginalFile(true)
                .build();

        // Then: deleteOriginalFile should be true
        assertTrue(settings.deleteOriginalFile());
    }

    @Test
    void shouldSetDeleteOriginalFileToFalse() {
        // Given: Builder with deleteOriginalFile explicitly set to false
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(validOutputDir())
                .deleteOriginalFile(false)
                .build();

        // Then: deleteOriginalFile should be false
        assertFalse(settings.deleteOriginalFile());
    }

    @Test
    void jsonSerialization_WithDeleteOriginalFile_ShouldPreserveValue() throws IOException {
        // Given: ConversionSettings with deleteOriginalFile set to true
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        ConversionSettings original = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .deleteOriginalFile(true)
                .build();

        ObjectMapper mapper = new ObjectMapper();

        // When: Serialize and deserialize
        String json = mapper.writeValueAsString(original);
        ConversionSettings deserialized = mapper.readValue(json, ConversionSettings.class);

        // Then: deleteOriginalFile should be preserved
        assertEquals(original.deleteOriginalFile(), deserialized.deleteOriginalFile());
        assertTrue(deserialized.deleteOriginalFile());
    }

    @Test
    void jsonDeserialization_WithMissingDeleteOriginalFile_ShouldDefaultToFalse() throws IOException {
        // Given: JSON without deleteOriginalFile field (backward compatibility)
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        String json = """
                {
                    "outputDirectory": "%s",
                    "overwriteExisting": false,
                    "createSubdirectory": false,
                    "parallelConversions": 4
                }
                """.formatted(outputDir.toString().replace("\\", "\\\\"));

        ObjectMapper mapper = new ObjectMapper();

        // When: Deserialize
        ConversionSettings deserialized = mapper.readValue(json, ConversionSettings.class);

        // Then: deleteOriginalFile should default to false
        assertFalse(deserialized.deleteOriginalFile());
    }

    @Test
    void equals_WithDifferentDeleteOriginalFile_ShouldReturnFalse() throws IOException {
        // Given: Two ConversionSettings with different deleteOriginalFile values
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        ConversionSettings settings1 = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .deleteOriginalFile(true)
                .build();
        ConversionSettings settings2 = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .deleteOriginalFile(false)
                .build();

        // Then: Should not be equal
        assertNotEquals(settings1, settings2);
    }

    @Test
    void equals_WithSameDeleteOriginalFile_ShouldReturnTrue() throws IOException {
        // Given: Two identical ConversionSettings with same deleteOriginalFile
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        ConversionSettings settings1 = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .deleteOriginalFile(true)
                .build();
        ConversionSettings settings2 = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .deleteOriginalFile(true)
                .build();

        // Then: Should be equal
        assertEquals(settings1, settings2);
    }

    @Test
    void hashCode_WithSameDeleteOriginalFile_ShouldBeEqual() throws IOException {
        // Given: Two identical ConversionSettings with same deleteOriginalFile
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        ConversionSettings settings1 = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .deleteOriginalFile(true)
                .build();
        ConversionSettings settings2 = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .deleteOriginalFile(true)
                .build();

        // Then: Hash codes should be equal
        assertEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void toString_ShouldIncludeDeleteOriginalFile() throws IOException {
        // Given: ConversionSettings with deleteOriginalFile set to true
        Path outputDir = validOutputDir();
        Files.createDirectories(outputDir);
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .deleteOriginalFile(true)
                .build();

        // When: Get string representation
        String toString = settings.toString();

        // Then: Should include deleteOriginalFile
        assertTrue(toString.contains("deleteOriginalFile=true"));
        assertTrue(toString.contains("ConversionSettings{"));
    }
}
