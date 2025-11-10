package org.omc.model;

import org.omc.model.ImageSettings;
import org.omc.model.FormatCategory;
import org.omc.model.DocumentSettings;
import org.omc.model.FileSettingsOverride;
import org.omc.model.VideoSettings;
import org.omc.model.AudioSettings;
import org.omc.model.FileFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileSettingsOverride class.
 * Covers factory methods, category detection, serialization, equals/hashCode,
 * and toString.
 */
class FileSettingsOverrideTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void forVideo_WithValidSettings_ShouldCreateVideoOverride() {
        // Given: Valid video settings
        VideoSettings settings = VideoSettings.builder().build();

        // When: Create video override
        FileSettingsOverride override = FileSettingsOverride.forVideo("High Quality", settings);

        // Then: Should have preset name and video settings, others null
        assertEquals("High Quality", override.presetName());
        assertEquals(settings, override.videoSettings());
        assertNull(override.audioSettings());
        assertNull(override.imageSettings());
        assertNull(override.documentSettings());
    }

    @Test
    void forAudio_WithValidSettings_ShouldCreateAudioOverride() {
        // Given: Valid audio settings
        AudioSettings settings = AudioSettings.builder().build();

        // When: Create audio override
        FileSettingsOverride override = FileSettingsOverride.forAudio("Lossless", settings);

        // Then: Should have preset name and audio settings, others null
        assertEquals("Lossless", override.presetName());
        assertNull(override.videoSettings());
        assertEquals(settings, override.audioSettings());
        assertNull(override.imageSettings());
        assertNull(override.documentSettings());
    }

    @Test
    void forImage_WithValidSettings_ShouldCreateImageOverride() {
        // Given: Valid image settings
        ImageSettings settings = ImageSettings.builder().build();

        // When: Create image override
        FileSettingsOverride override = FileSettingsOverride.forImage("Compressed", settings);

        // Then: Should have preset name and image settings, others null
        assertEquals("Compressed", override.presetName());
        assertNull(override.videoSettings());
        assertNull(override.audioSettings());
        assertEquals(settings, override.imageSettings());
        assertNull(override.documentSettings());
    }

    @Test
    void forDocument_WithValidSettings_ShouldCreateDocumentOverride() {
        // Given: Valid document settings
        DocumentSettings settings = DocumentSettings.builder().build();

        // When: Create document override
        FileSettingsOverride override = FileSettingsOverride.forDocument("PDF Export", settings);

        // Then: Should have preset name and document settings, others null
        assertEquals("PDF Export", override.presetName());
        assertNull(override.videoSettings());
        assertNull(override.audioSettings());
        assertNull(override.imageSettings());
        assertEquals(settings, override.documentSettings());
    }

    @Test
    void forVideo_WithNullSettings_ShouldThrowNullPointerException() {
        // When/Then: Should throw NPE
        assertThrows(NullPointerException.class, () -> FileSettingsOverride.forVideo("Test", null));
    }

    @Test
    void forAudio_WithNullSettings_ShouldThrowNullPointerException() {
        // When/Then: Should throw NPE
        assertThrows(NullPointerException.class, () -> FileSettingsOverride.forAudio("Test", null));
    }

    @Test
    void forImage_WithNullSettings_ShouldThrowNullPointerException() {
        // When/Then: Should throw NPE
        assertThrows(NullPointerException.class, () -> FileSettingsOverride.forImage("Test", null));
    }

    @Test
    void forDocument_WithNullSettings_ShouldThrowNullPointerException() {
        // When/Then: Should throw NPE
        assertThrows(NullPointerException.class, () -> FileSettingsOverride.forDocument("Test", null));
    }

    @Test
    void getCategory_ForVideoOverride_ShouldReturnVideo() {
        // Given: Video override
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test", settings);

        // When/Then: Category should be VIDEO
        assertEquals(FormatCategory.VIDEO, override.getCategory());
    }

    @Test
    void getCategory_ForAudioOverride_ShouldReturnAudio() {
        // Given: Audio override
        AudioSettings settings = AudioSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forAudio("Test", settings);

        // When/Then: Category should be AUDIO
        assertEquals(FormatCategory.AUDIO, override.getCategory());
    }

    @Test
    void getCategory_ForImageOverride_ShouldReturnImage() {
        // Given: Image override
        ImageSettings settings = ImageSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forImage("Test", settings);

        // When/Then: Category should be IMAGE
        assertEquals(FormatCategory.IMAGE, override.getCategory());
    }

    @Test
    void getCategory_ForDocumentOverride_ShouldReturnDocument() {
        // Given: Document override
        DocumentSettings settings = DocumentSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forDocument("Test", settings);

        // When/Then: Category should be DOCUMENT
        assertEquals(FormatCategory.DOCUMENT, override.getCategory());
    }

    @Test
    void jsonSerialization_VideoOverride_ShouldPreserveAllFields() throws Exception {
        // Given: Video override
        VideoSettings settings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .bitrate(8000)
                .build();
        FileSettingsOverride original = FileSettingsOverride.forVideo("High Quality", settings);

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        FileSettingsOverride deserialized = objectMapper.readValue(json, FileSettingsOverride.class);

        // Then: Should be equal
        assertEquals(original, deserialized);
        assertEquals("High Quality", deserialized.presetName());
        assertEquals(settings, deserialized.videoSettings());
        assertNull(deserialized.audioSettings());
        assertNull(deserialized.imageSettings());
        assertNull(deserialized.documentSettings());
    }

    @Test
    void jsonSerialization_AudioOverride_ShouldPreserveAllFields() throws Exception {
        // Given: Audio override
        AudioSettings settings = AudioSettings.builder()
                .outputFormat(FileFormat.MP3)
                .bitrate(320)
                .build();
        FileSettingsOverride original = FileSettingsOverride.forAudio("High Quality", settings);

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        FileSettingsOverride deserialized = objectMapper.readValue(json, FileSettingsOverride.class);

        // Then: Should be equal
        assertEquals(original, deserialized);
        assertEquals("High Quality", deserialized.presetName());
        assertNull(deserialized.videoSettings());
        assertEquals(settings, deserialized.audioSettings());
        assertNull(deserialized.imageSettings());
        assertNull(deserialized.documentSettings());
    }

    @Test
    void jsonSerialization_ImageOverride_ShouldPreserveAllFields() throws Exception {
        // Given: Image override
        ImageSettings settings = ImageSettings.builder()
                .outputFormat(FileFormat.PNG)
                .quality(90)
                .build();
        FileSettingsOverride original = FileSettingsOverride.forImage("Compressed", settings);

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        FileSettingsOverride deserialized = objectMapper.readValue(json, FileSettingsOverride.class);

        // Then: Should be equal
        assertEquals(original, deserialized);
        assertEquals("Compressed", deserialized.presetName());
        assertNull(deserialized.videoSettings());
        assertNull(deserialized.audioSettings());
        assertEquals(settings, deserialized.imageSettings());
        assertNull(deserialized.documentSettings());
    }

    @Test
    void jsonSerialization_DocumentOverride_ShouldPreserveAllFields() throws Exception {
        // Given: Document override
        DocumentSettings settings = DocumentSettings.builder()
                .outputFormat(FileFormat.PDF)
                .build();
        FileSettingsOverride original = FileSettingsOverride.forDocument("PDF Export", settings);

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        FileSettingsOverride deserialized = objectMapper.readValue(json, FileSettingsOverride.class);

        // Then: Should be equal
        assertEquals(original, deserialized);
        assertEquals("PDF Export", deserialized.presetName());
        assertNull(deserialized.videoSettings());
        assertNull(deserialized.audioSettings());
        assertNull(deserialized.imageSettings());
        assertEquals(settings, deserialized.documentSettings());
    }

    @Test
    void equals_WithSameOverrides_ShouldReturnTrue() {
        // Given: Two identical video overrides
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override1 = FileSettingsOverride.forVideo("Test", settings);
        FileSettingsOverride override2 = FileSettingsOverride.forVideo("Test", settings);

        // Then: Should be equal
        assertEquals(override1, override2);
    }

    @Test
    void equals_WithDifferentPresetName_ShouldReturnFalse() {
        // Given: Two overrides with different preset names
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override1 = FileSettingsOverride.forVideo("Test1", settings);
        FileSettingsOverride override2 = FileSettingsOverride.forVideo("Test2", settings);

        // Then: Should not be equal
        assertNotEquals(override1, override2);
    }

    @Test
    void equals_WithDifferentSettings_ShouldReturnFalse() {
        // Given: Two overrides with different settings
        VideoSettings settings1 = VideoSettings.builder().bitrate(5000).build();
        VideoSettings settings2 = VideoSettings.builder().bitrate(8000).build();
        FileSettingsOverride override1 = FileSettingsOverride.forVideo("Test", settings1);
        FileSettingsOverride override2 = FileSettingsOverride.forVideo("Test", settings2);

        // Then: Should not be equal
        assertNotEquals(override1, override2);
    }

    @Test
    void hashCode_WithSameOverrides_ShouldBeEqual() {
        // Given: Two identical overrides
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override1 = FileSettingsOverride.forVideo("Test", settings);
        FileSettingsOverride override2 = FileSettingsOverride.forVideo("Test", settings);

        // Then: Hash codes should be equal
        assertEquals(override1.hashCode(), override2.hashCode());
    }

    @Test
    void hashCode_WithDifferentOverrides_ShouldBeDifferent() {
        // Given: Two different overrides
        VideoSettings settings1 = VideoSettings.builder().bitrate(5000).build();
        VideoSettings settings2 = VideoSettings.builder().bitrate(8000).build();
        FileSettingsOverride override1 = FileSettingsOverride.forVideo("Test", settings1);
        FileSettingsOverride override2 = FileSettingsOverride.forVideo("Test", settings2);

        // Then: Hash codes should be different
        assertNotEquals(override1.hashCode(), override2.hashCode());
    }

    @Test
    void toString_VideoOverride_ShouldIncludePresetNameAndCategoryAndSettings() {
        // Given: Video override
        VideoSettings settings = VideoSettings.builder().outputFormat(FileFormat.MP4).build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("High Quality", settings);

        // When: Get string representation
        String toString = override.toString();

        // Then: Should contain expected parts
        assertTrue(toString.contains("FileSettingsOverride{"));
        assertTrue(toString.contains("presetName='High Quality'"));
        assertTrue(toString.contains("category=VIDEO"));
        assertTrue(toString.contains("videoSettings="));
        assertTrue(toString.contains("audioSettings=null"));
        assertTrue(toString.contains("imageSettings=null"));
        assertTrue(toString.contains("documentSettings=null"));
        assertTrue(toString.endsWith("}"));
    }

    @Test
    void toString_AudioOverride_ShouldIncludePresetNameAndCategoryAndSettings() {
        // Given: Audio override
        AudioSettings settings = AudioSettings.builder().outputFormat(FileFormat.MP3).build();
        FileSettingsOverride override = FileSettingsOverride.forAudio("Lossless", settings);

        // When: Get string representation
        String toString = override.toString();

        // Then: Should contain expected parts
        assertTrue(toString.contains("FileSettingsOverride{"));
        assertTrue(toString.contains("presetName='Lossless'"));
        assertTrue(toString.contains("category=AUDIO"));
        assertTrue(toString.contains("videoSettings=null"));
        assertTrue(toString.contains("audioSettings="));
        assertTrue(toString.contains("imageSettings=null"));
        assertTrue(toString.contains("documentSettings=null"));
        assertTrue(toString.endsWith("}"));
    }

    @Test
    void presetName_ShouldReturnPresetName() {
        // Given: Override with preset name
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Custom Preset", settings);

        // Then: Should return preset name
        assertEquals("Custom Preset", override.presetName());
    }

    @Test
    void videoSettings_ShouldReturnVideoSettings() {
        // Given: Video override
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test", settings);

        // Then: Should return video settings
        assertEquals(settings, override.videoSettings());
    }

    @Test
    void audioSettings_ShouldReturnAudioSettings() {
        // Given: Audio override
        AudioSettings settings = AudioSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forAudio("Test", settings);

        // Then: Should return audio settings
        assertEquals(settings, override.audioSettings());
    }

    @Test
    void imageSettings_ShouldReturnImageSettings() {
        // Given: Image override
        ImageSettings settings = ImageSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forImage("Test", settings);

        // Then: Should return image settings
        assertEquals(settings, override.imageSettings());
    }

    @Test
    void documentSettings_ShouldReturnDocumentSettings() {
        // Given: Document override
        DocumentSettings settings = DocumentSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forDocument("Test", settings);

        // Then: Should return document settings
        assertEquals(settings, override.documentSettings());
    }
}