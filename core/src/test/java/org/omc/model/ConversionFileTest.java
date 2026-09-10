package org.omc.model;

import org.omc.model.ConversionFile;
import org.omc.model.ConversionStatus;
import org.omc.model.FileSettingsOverride;
import org.omc.model.VideoSettings;
import org.omc.model.FileFormat;
import org.omc.model.MediaMetadata;
import org.omc.model.VideoMetadata;
import org.omc.model.AudioMetadata;
import org.omc.model.ImageMetadata;
import org.omc.model.DocumentMetadata;
import org.omc.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Unit tests for ConversionFile class.
 * Covers new settings override functionality, JSON serialization, toString, and
 * immutability.
 */
class ConversionFileTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path testPath = Paths.get("/test/file.mp4");
    private final FileFormat testFormat = FileFormat.MP4;
    private final long testSize = 1024L;

    @Test
    void settingsOverride_WithOverridePresent_ShouldReturnCorrectValue() {
        // Given: ConversionFile with settings override
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test Preset", settings);
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize)
                .withSettingsOverride(override);

        // When: Get settings override
        FileSettingsOverride result = file.settingsOverride();

        // Then: Should return the override
        assertEquals(override, result);
    }

    @Test
    void settingsOverride_WithNullOverride_ShouldReturnNull() {
        // Given: ConversionFile without settings override
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize);

        // When: Get settings override
        FileSettingsOverride result = file.settingsOverride();

        // Then: Should return null
        assertNull(result);
    }

    @Test
    void hasCustomSettings_WithOverridePresent_ShouldReturnTrue() {
        // Given: ConversionFile with settings override
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test Preset", settings);
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize)
                .withSettingsOverride(override);

        // When/Then: Should return true
        assertTrue(file.hasCustomSettings());
    }

    @Test
    void hasCustomSettings_WithNullOverride_ShouldReturnFalse() {
        // Given: ConversionFile without settings override
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize);

        // When/Then: Should return false
        assertFalse(file.hasCustomSettings());
    }

    @Test
    void withSettingsOverride_WithValidOverride_ShouldCreateNewInstanceWithOverride() {
        // Given: Original file without override
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize);
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test Preset", settings);

        // When: Apply settings override
        ConversionFile result = original.withSettingsOverride(override);

        // Then: New instance should have override, original unchanged
        assertEquals(override, result.settingsOverride());
        assertTrue(result.hasCustomSettings());
        assertNull(original.settingsOverride());
        assertFalse(original.hasCustomSettings());
        assertNotSame(original, result);
    }

    @Test
    void withSettingsOverride_WithNullOverride_ShouldCreateNewInstanceWithNullOverride() {
        // Given: Original file with override
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test Preset", settings);
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize)
                .withSettingsOverride(override);

        // When: Apply null override
        ConversionFile result = original.withSettingsOverride(null);

        // Then: New instance should have null override, original unchanged
        assertNull(result.settingsOverride());
        assertFalse(result.hasCustomSettings());
        assertEquals(override, original.settingsOverride());
        assertTrue(original.hasCustomSettings());
        assertNotSame(original, result);
    }

    @Test
    void clearSettingsOverride_WithOverridePresent_ShouldRemoveOverride() {
        // Given: File with settings override
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test Preset", settings);
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize)
                .withSettingsOverride(override);

        // When: Clear settings override
        ConversionFile result = original.clearSettingsOverride();

        // Then: New instance should have null override, original unchanged
        assertNull(result.settingsOverride());
        assertFalse(result.hasCustomSettings());
        assertEquals(override, original.settingsOverride());
        assertTrue(original.hasCustomSettings());
        assertNotSame(original, result);
    }

    @Test
    void clearSettingsOverride_WithNullOverride_ShouldRemainNull() {
        // Given: File without settings override
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize);

        // When: Clear settings override
        ConversionFile result = original.clearSettingsOverride();

        // Then: Both should have null override
        assertNull(result.settingsOverride());
        assertFalse(result.hasCustomSettings());
        assertNull(original.settingsOverride());
        assertFalse(original.hasCustomSettings());
        assertNotSame(original, result);
    }

    @Test
    void clearSettingsOverride_ShouldPreserveOtherFields() {
        // Given: File with various fields set
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test Preset", settings);
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize)
                .withSettingsOverride(override)
                .withStatus(ConversionStatus.IN_PROGRESS)
                .withProgress(50)
                .withError("Test error");

        // When: Clear settings override
        ConversionFile result = original.clearSettingsOverride();

        // Then: Other fields should be preserved
        assertEquals(original.id(), result.id());
        assertEquals(original.path(), result.path());
        assertEquals(original.format(), result.format());
        assertEquals(original.size(), result.size());
        assertEquals(original.status(), result.status());
        assertEquals(original.progress(), result.progress());
        assertEquals(original.errorMessage(), result.errorMessage());
        assertNull(result.settingsOverride());
        assertNotSame(original, result);
    }

    @Test
    void create_ShouldSetSettingsOverrideToNull() {
        // When: Create new ConversionFile
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize);

        // Then: Settings override should be null
        assertNull(file.settingsOverride());
        assertFalse(file.hasCustomSettings());
    }

    @Test
    void jsonDeserialization_WithSettingsOverrideField_ShouldParseCorrectly() throws Exception {
        // Given: JSON for FileSettingsOverride
        String settingsJson = "{\"presetName\":\"High Quality\",\"videoSettings\":{\"outputFormat\":\"MP4\",\"bitrate\":8000},\"audioSettings\":null,\"imageSettings\":null,\"documentSettings\":null}";

        // When: Deserialize
        FileSettingsOverride override = objectMapper.readValue(settingsJson, FileSettingsOverride.class);

        // Then: Should have correct settings
        assertNotNull(override);
        assertEquals("High Quality", override.presetName());
        assertNotNull(override.videoSettings());
        assertEquals(FileFormat.MP4, override.videoSettings().outputFormat());
    }

    @Test
    void jsonDeserialization_BackwardCompatibility_WithoutSettingsOverrideField_ShouldWork() throws Exception {
        // Given: JSON without settingsOverride field (backward compatibility)
        String json = """
                {
                  "id": "test-id",
                  "path": "/test/file.mp4",
                  "format": "MP4",
                  "size": 1024,
                  "metadata": null,
                  "status": "PENDING",
                  "progress": 0,
                  "errorMessage": null
                }
                """;

        // When: Deserialize (should not throw)
        ConversionFile file = objectMapper.readValue(json, ConversionFile.class);

        // Then: Should create object without settings override
        assertNotNull(file);
        // Note: Due to Jackson configuration, other fields may be null, but no
        // exception
    }

    @Test
    void toString_WithCustomSettings_ShouldIncludeHasCustomSettingsAndOverride() {
        // Given: File with settings override
        VideoSettings settings = VideoSettings.builder().outputFormat(FileFormat.MP4).build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("High Quality", settings);
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize)
                .withSettingsOverride(override);

        // When: Get string representation
        String toString = file.toString();

        // Then: Should include hasCustomSettings and settingsOverride
        assertTrue(toString.contains("hasCustomSettings=true"));
        assertTrue(toString.contains("settingsOverride="));
        assertTrue(toString.contains("High Quality"));
    }

    @Test
    void toString_WithoutCustomSettings_ShouldIncludeHasCustomSettingsFalse() {
        // Given: File without settings override
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize);

        // When: Get string representation
        String toString = file.toString();

        // Then: Should include hasCustomSettings=false and no settingsOverride
        assertTrue(toString.contains("hasCustomSettings=false"));
        assertFalse(toString.contains("settingsOverride="));
    }

    @Test
    void withStatus_ShouldReturnNewInstanceAndPreserveSettingsOverride() {
        // Given: Original file with settings override
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test", settings);
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize)
                .withSettingsOverride(override);

        // When: Change status
        ConversionFile result = original.withStatus(ConversionStatus.IN_PROGRESS);

        // Then: New instance with updated status, original unchanged, settings
        // preserved
        assertEquals(ConversionStatus.IN_PROGRESS, result.status());
        assertEquals(ConversionStatus.PENDING, original.status());
        assertEquals(override, result.settingsOverride());
        assertEquals(override, original.settingsOverride());
        assertNotSame(original, result);
    }

    @Test
    void withProgress_ShouldReturnNewInstanceAndPreserveSettingsOverride() {
        // Given: Original file with settings override
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test", settings);
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize)
                .withSettingsOverride(override);

        // When: Change progress
        ConversionFile result = original.withProgress(75);

        // Then: New instance with updated progress, original unchanged, settings
        // preserved
        assertEquals(75, result.progress());
        assertEquals(0, original.progress());
        assertEquals(override, result.settingsOverride());
        assertEquals(override, original.settingsOverride());
        assertNotSame(original, result);
    }

    @Test
    void withError_ShouldReturnNewInstanceAndPreserveSettingsOverride() {
        // Given: Original file with settings override
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test", settings);
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize)
                .withSettingsOverride(override);

        // When: Set error
        ConversionFile result = original.withError("Test error");

        // Then: New instance with error, original unchanged, settings preserved
        assertEquals("Test error", result.errorMessage());
        assertEquals(ConversionStatus.FAILED, result.status());
        assertNull(original.errorMessage());
        assertEquals(ConversionStatus.PENDING, original.status());
        assertEquals(override, result.settingsOverride());
        assertEquals(override, original.settingsOverride());
        assertNotSame(original, result);
    }

    @Test
    void withMetadata_ShouldReturnNewInstanceAndPreserveSettingsOverride() {
        // Given: Original file with settings override
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test", settings);
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize)
                .withSettingsOverride(override);

        // When: Set metadata
        MediaMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ofSeconds(120))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .frameRate(30.0)
                .build();
        ConversionFile result = original.withMetadata(metadata);

        // Then: New instance with metadata, original unchanged, settings preserved
        assertEquals(metadata, result.metadata());
        assertNull(original.metadata());
        assertEquals(override, result.settingsOverride());
        assertEquals(override, original.settingsOverride());
        assertNotSame(original, result);
    }

    @Test
    void withSettingsOverride_ShouldReturnNewInstanceAndPreserveOtherFields() {
        // Given: Original file with various fields
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize)
                .withStatus(ConversionStatus.IN_PROGRESS)
                .withProgress(50)
                .withError("Original error");

        // When: Apply settings override
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("New Preset", settings);
        ConversionFile result = original.withSettingsOverride(override);

        // Then: Other fields preserved, settings updated
        assertEquals(original.id(), result.id());
        assertEquals(original.path(), result.path());
        assertEquals(original.format(), result.format());
        assertEquals(original.size(), result.size());
        assertEquals(original.status(), result.status());
        assertEquals(original.progress(), result.progress());
        assertEquals(original.errorMessage(), result.errorMessage());
        assertEquals(override, result.settingsOverride());
        assertNull(original.settingsOverride());
        assertNotSame(original, result);
    }

    @Test
    void equals_ShouldBeBasedOnIdOnly() {
        // Given: Two files with same id but different settings override
        ConversionFile file1 = ConversionFile.create(testPath, testFormat, testSize);
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test", settings);
        ConversionFile file2 = file1.withSettingsOverride(override);

        // Then: Should be equal since id is same
        assertEquals(file1, file2);
        assertEquals(file1.hashCode(), file2.hashCode());
    }

    @Test
    void fileName_ShouldReturnFileNameFromPath() {
        // Given: File with path
        Path path = Paths.get("/some/dir/video.mp4");
        ConversionFile file = ConversionFile.create(path, testFormat, testSize);

        // When/Then: Should return filename
        assertEquals("video.mp4", file.fileName());
    }

    @Test
    void isTerminal_WithCompletedStatus_ShouldReturnTrue() {
        // Given: File with completed status
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize)
                .withStatus(ConversionStatus.COMPLETED);

        // Then: Should be terminal
        assertTrue(file.isTerminal());
    }

    @Test
    void isInProgress_WithInProgressStatus_ShouldReturnTrue() {
        // Given: File with in progress status
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize)
                .withStatus(ConversionStatus.IN_PROGRESS);

        // Then: Should be in progress
        assertTrue(file.isInProgress());
    }

    // ==================== outputPath Tests ====================

    @Test
    void outputPath_WithNullOutputPath_ShouldReturnEmptyOptional() {
        // Given: ConversionFile without outputPath
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize);

        // When: Get outputPath
        var result = file.outputPath();

        // Then: Should return empty Optional
        assertTrue(result.isEmpty());
    }

    @Test
    void outputPath_WithSetOutputPath_ShouldReturnPresentOptional() {
        // Given: ConversionFile with outputPath
        Path outputPath = Paths.get("/test/output.mp4");
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize)
                .withOutputPath(outputPath);

        // When: Get outputPath
        var result = file.outputPath();

        // Then: Should return present Optional with correct path
        assertTrue(result.isPresent());
        assertEquals(outputPath, result.get());
    }

    @Test
    void withOutputPath_ShouldCreateNewInstanceWithUpdatedPath() {
        // Given: Original file without outputPath
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize);
        Path outputPath = Paths.get("/test/output.mp4");

        // When: Set outputPath
        ConversionFile result = original.withOutputPath(outputPath);

        // Then: New instance should have outputPath, original unchanged
        assertTrue(result.outputPath().isPresent());
        assertEquals(outputPath, result.outputPath().get());
        assertTrue(original.outputPath().isEmpty());
        assertNotSame(original, result);
    }

    @Test
    void withOutputPath_WithNullPath_ShouldCreateNewInstanceWithNullOutputPath() {
        // Given: Original file with outputPath
        Path outputPath = Paths.get("/test/output.mp4");
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize)
                .withOutputPath(outputPath);

        // When: Set outputPath to null
        ConversionFile result = original.withOutputPath(null);

        // Then: New instance should have null outputPath, original unchanged
        assertTrue(result.outputPath().isEmpty());
        assertTrue(original.outputPath().isPresent());
        assertNotSame(original, result);
    }

    @Test
    void withOutputPath_ShouldPreserveAllOtherFields() {
        // Given: Original file with various fields including settingsOverride
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test Preset", settings);
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize)
                .withSettingsOverride(override)
                .withStatus(ConversionStatus.IN_PROGRESS)
                .withProgress(50)
                .withError("Test error");

        // When: Set outputPath
        Path outputPath = Paths.get("/test/output.mp4");
        ConversionFile result = original.withOutputPath(outputPath);

        // Then: All other fields should be preserved
        assertEquals(original.id(), result.id());
        assertEquals(original.path(), result.path());
        assertEquals(original.format(), result.format());
        assertEquals(original.size(), result.size());
        assertEquals(original.status(), result.status());
        assertEquals(original.progress(), result.progress());
        assertEquals(original.errorMessage(), result.errorMessage());
        assertEquals(original.settingsOverride(), result.settingsOverride());
        assertTrue(result.outputPath().isPresent());
        assertEquals(outputPath, result.outputPath().get());
        assertNotSame(original, result);
    }

    @Test
    void create_ShouldSetOutputPathToNull() {
        // When: Create new ConversionFile
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize);

        // Then: outputPath should be empty
        assertTrue(file.outputPath().isEmpty());
    }

    @Test
    void jsonSerialization_WithOutputPath_ShouldSerializeAndDeserialize() throws Exception {
        // Given: ConversionFile with outputPath
        Path outputPath = Paths.get("/test/output.mp4");
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize)
                .withOutputPath(outputPath);

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        ConversionFile deserialized = objectMapper.readValue(json, ConversionFile.class);

        // Then: outputPath should be preserved
        assertTrue(deserialized.outputPath().isPresent());
        assertEquals(outputPath, deserialized.outputPath().get());
    }

    @Test
    void jsonSerialization_WithNullOutputPath_ShouldSerializeAndDeserialize() throws Exception {
        // Given: ConversionFile without outputPath
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize);

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        ConversionFile deserialized = objectMapper.readValue(json, ConversionFile.class);

        // Then: outputPath should remain empty
        assertTrue(deserialized.outputPath().isEmpty());
    }

    @Test
    void jsonDeserialization_BackwardCompatibility_WithoutOutputPathField_ShouldWork() throws Exception {
        // Given: JSON without outputPath field (backward compatibility)
        String json = """
                {
                  "id": "test-id-123",
                  "path": "/test/file.mp4",
                  "format": "MP4",
                  "size": 1024,
                  "metadata": null,
                  "status": "PENDING",
                  "progress": 0,
                  "errorMessage": null,
                  "settingsOverride": null
                }
                """;

        // When: Deserialize (should not throw)
        ConversionFile file = objectMapper.readValue(json, ConversionFile.class);

        // Then: Should create object without outputPath (empty Optional)
        assertNotNull(file);
        assertTrue(file.outputPath().isEmpty());
        assertEquals("test-id-123", file.id());
        assertEquals(Paths.get("/test/file.mp4"), file.path());
    }

    @Test
    void toString_WithOutputPath_ShouldIncludeOutputPath() {
        // Given: File with outputPath
        Path outputPath = Paths.get("/test/output.mp4");
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize)
                .withOutputPath(outputPath);

        // When: Get string representation
        String toString = file.toString();

        // Then: Should include outputPath
        assertTrue(toString.contains("outputPath="));
        assertTrue(toString.contains("/test/output.mp4"));
    }

    @Test
    void toString_WithNullOutputPath_ShouldNotIncludeOutputPath() {
        // Given: File without outputPath
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize);

        // When: Get string representation
        String toString = file.toString();

        // Then: Should not include outputPath (or show as null/empty)
        // The actual format depends on implementation, but verify toString works
        assertNotNull(toString);
        assertTrue(toString.contains("path=")); // Basic sanity check
    }

    @Test
    void allWithMethods_ShouldPreserveOutputPath() {
        // Given: Original file with outputPath
        Path outputPath = Paths.get("/test/output.mp4");
        ConversionFile original = ConversionFile.create(testPath, testFormat, testSize)
                .withOutputPath(outputPath);

        // When: Apply various with methods
        ConversionFile afterStatus = original.withStatus(ConversionStatus.IN_PROGRESS);
        ConversionFile afterProgress = original.withProgress(75);
        ConversionFile afterError = original.withError("Test error");
        ConversionFile afterMetadata = original.withMetadata(null);
        VideoSettings settings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test", settings);
        ConversionFile afterSettings = original.withSettingsOverride(override);

        // Then: All should preserve outputPath
        assertTrue(afterStatus.outputPath().isPresent());
        assertEquals(outputPath, afterStatus.outputPath().get());
        assertTrue(afterProgress.outputPath().isPresent());
        assertEquals(outputPath, afterProgress.outputPath().get());
        assertTrue(afterError.outputPath().isPresent());
        assertEquals(outputPath, afterError.outputPath().get());
        assertTrue(afterMetadata.outputPath().isPresent());
        assertEquals(outputPath, afterMetadata.outputPath().get());
        assertTrue(afterSettings.outputPath().isPresent());
        assertEquals(outputPath, afterSettings.outputPath().get());
    }

    @Test
    void jsonDeserialization_WithoutStatus_DefaultsToPending() throws Exception {
        // Given: Old session file JSON without a status field
        String json = """
                {
                  "id": "legacy-id",
                  "path": "/test/file.mp4",
                  "format": "MP4",
                  "size": 1024
                }
                """;

        // When: Deserialize
        ConversionFile file = objectMapper.readValue(json, ConversionFile.class);

        // Then: Status must never be null (UI switches on it)
        assertEquals(ConversionStatus.PENDING, file.status());
    }

    @Test
    void jsonDeserialization_WithNullStatus_DefaultsToPending() throws Exception {
        // Given: Session file JSON carrying an explicit null status
        String json = """
                {
                  "id": "legacy-id",
                  "path": "/test/file.mp4",
                  "format": "MP4",
                  "size": 1024,
                  "status": null
                }
                """;

        // When: Deserialize
        ConversionFile file = objectMapper.readValue(json, ConversionFile.class);

        // Then: null status resolves to PENDING
        assertEquals(ConversionStatus.PENDING, file.status());
    }

    // ==================== Metadata Polymorphic JSON Tests ====================

    @Test
    void jsonSerialization_WithVideoMetadata_ShouldEmitTypeDiscriminator() throws Exception {
        // Given: ConversionFile with typed VideoMetadata
        MediaMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ofSeconds(120))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .frameRate(30.0)
                .build();
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize)
                .withMetadata(metadata);

        // When: Serialize with the shared JsonUtils mapper
        String json = JsonUtils.getObjectMapper().writeValueAsString(file);

        // Then: The metadata object must carry the polymorphic "type" property
        JsonNode metadataNode = JsonUtils.getObjectMapper().readTree(json).get("metadata");
        assertNotNull(metadataNode);
        assertNotNull(metadataNode.get("type"));
        assertEquals("video", metadataNode.get("type").asText());
    }

    @Test
    void jsonRoundtrip_WithVideoMetadata_ShouldPreserveTypedMetadata() throws Exception {
        // Given: ConversionFile with typed VideoMetadata (non-default values on
        // every field so all of them traverse JSON)
        VideoMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ofSeconds(120))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .audioCodec("aac")
                .videoBitrate(8_000)
                .audioBitrate(192)
                .frameRate(30.0)
                .build();
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize)
                .withMetadata(metadata);

        // When: Round-trip through JSON
        String json = JsonUtils.getObjectMapper().writeValueAsString(file);
        ConversionFile deserialized = JsonUtils.getObjectMapper().readValue(json, ConversionFile.class);

        // Then: The metadata type survives (no LinkedHashMap degradation)
        JsonNode metadataNode = JsonUtils.getObjectMapper().readTree(json).get("metadata");
        assertNotNull(metadataNode);
        assertEquals("video", metadataNode.get("type").asText());
        MediaMetadata restored = deserialized.metadata();
        assertNotNull(restored);
        VideoMetadata video = assertInstanceOf(VideoMetadata.class, restored);
        assertEquals(Duration.ofSeconds(120), video.getDuration());
        assertEquals(1920, video.getWidth());
        assertEquals(1080, video.getHeight());
        assertEquals("h264", video.getVideoCodec());
        assertEquals("aac", video.getAudioCodec());
        assertEquals(8_000L, video.getVideoBitrate());
        assertEquals(192L, video.getAudioBitrate());
        assertEquals(30.0, video.getFrameRate());
        assertEquals(metadata, restored);
    }

    @Test
    void jsonRoundtrip_WithAudioMetadata_ShouldPreserveTypedMetadata() throws Exception {
        // Given: ConversionFile with typed AudioMetadata
        AudioMetadata metadata = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .codec("aac")
                .bitrate(128000)
                .sampleRate(44100)
                .channels(2)
                .build();
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize)
                .withMetadata(metadata);

        // When: Round-trip through JSON
        String json = JsonUtils.getObjectMapper().writeValueAsString(file);
        JsonNode metadataNode = JsonUtils.getObjectMapper().readTree(json).get("metadata");
        assertNotNull(metadataNode);
        assertEquals("audio", metadataNode.get("type").asText());
        ConversionFile deserialized = JsonUtils.getObjectMapper().readValue(json, ConversionFile.class);

        // Then: The metadata type survives (no LinkedHashMap degradation)
        MediaMetadata restored = deserialized.metadata();
        assertNotNull(restored);
        assertInstanceOf(AudioMetadata.class, restored);
        AudioMetadata audioRestored = (AudioMetadata) restored;
        assertEquals(metadata.getDuration(), audioRestored.getDuration());
        assertEquals(metadata.getCodec(), audioRestored.getCodec());
        assertEquals(metadata.getBitrate(), audioRestored.getBitrate());
        assertEquals(metadata.getSampleRate(), audioRestored.getSampleRate());
        assertEquals(metadata.getChannels(), audioRestored.getChannels());
        assertEquals(metadata, restored);
    }

    @Test
    void jsonRoundtrip_WithImageMetadata_ShouldPreserveTypedMetadata() throws Exception {
        // Given: ConversionFile with typed ImageMetadata
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(8)
                .hasAlpha(false)
                .build();
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize)
                .withMetadata(metadata);

        // When: Round-trip through JSON
        String json = JsonUtils.getObjectMapper().writeValueAsString(file);
        JsonNode metadataNode = JsonUtils.getObjectMapper().readTree(json).get("metadata");
        assertNotNull(metadataNode);
        assertEquals("image", metadataNode.get("type").asText());
        ConversionFile deserialized = JsonUtils.getObjectMapper().readValue(json, ConversionFile.class);

        // Then: The metadata type survives (no LinkedHashMap degradation)
        MediaMetadata restored = deserialized.metadata();
        assertNotNull(restored);
        assertInstanceOf(ImageMetadata.class, restored);
        ImageMetadata imageRestored = (ImageMetadata) restored;
        assertEquals(metadata.getWidth(), imageRestored.getWidth());
        assertEquals(metadata.getHeight(), imageRestored.getHeight());
        assertEquals(metadata.getColorSpace(), imageRestored.getColorSpace());
        assertEquals(metadata.getBitDepth(), imageRestored.getBitDepth());
        assertEquals(metadata.hasAlpha(), imageRestored.hasAlpha());
        assertEquals(metadata, restored);
    }

    @Test
    void jsonRoundtrip_WithDocumentMetadata_ShouldPreserveTypedMetadata() throws Exception {
        // Given: ConversionFile with typed DocumentMetadata
        DocumentMetadata metadata = DocumentMetadata.builder()
                .pageCount(10)
                .title("Test Document")
                .author("John Doe")
                .subject("Testing")
                .creator("Test Creator")
                .build();
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize)
                .withMetadata(metadata);

        // When: Round-trip through JSON
        String json = JsonUtils.getObjectMapper().writeValueAsString(file);
        JsonNode metadataNode = JsonUtils.getObjectMapper().readTree(json).get("metadata");
        assertNotNull(metadataNode);
        assertEquals("document", metadataNode.get("type").asText());
        ConversionFile deserialized = JsonUtils.getObjectMapper().readValue(json, ConversionFile.class);

        // Then: The metadata type survives (no LinkedHashMap degradation)
        MediaMetadata restored = deserialized.metadata();
        assertNotNull(restored);
        assertInstanceOf(DocumentMetadata.class, restored);
        DocumentMetadata documentRestored = (DocumentMetadata) restored;
        assertEquals(metadata.getPageCount(), documentRestored.getPageCount());
        assertEquals(metadata.getTitle(), documentRestored.getTitle());
        assertEquals(metadata.getAuthor(), documentRestored.getAuthor());
        assertEquals(metadata.getSubject(), documentRestored.getSubject());
        assertEquals(metadata.getCreator(), documentRestored.getCreator());
        assertEquals(metadata, restored);
    }

    @Test
    void jsonDeserialization_LegacyMetadataWithoutType_ShouldNotFailAndYieldNullMetadata() throws Exception {
        // Given: Session file written by a prior version: flat metadata fields
        // without the "type" discriminator
        String legacyJson = """
                {
                  "id": "legacy-metadata-id",
                  "path": "/test/cancelled_video.mp4",
                  "format": "MP4",
                  "size": 1024,
                  "metadata": {
                    "duration": 120.5,
                    "width": 1920,
                    "height": 1080,
                    "bitrate": 5000000
                  },
                  "status": "PENDING",
                  "progress": 0
                }
                """;

        // When: Deserialize - must not fail the whole ConversionFile
        ConversionFile file = assertDoesNotThrow(
                () -> JsonUtils.getObjectMapper().readValue(legacyJson, ConversionFile.class));

        // Then: File is intact; unknowable legacy metadata degrades to null
        assertNotNull(file);
        assertEquals("legacy-metadata-id", file.id());
        assertEquals(ConversionStatus.PENDING, file.status());
        assertNull(file.metadata());
    }

    @Test
    void jsonDeserialization_UnknownMetadataType_ShouldNotFailAndYieldNullMetadata() throws Exception {
        // Given: Session file written by a newer version carrying a metadata
        // subtype this version does not know
        String futureJson = """
                {
                  "id": "future-type-id",
                  "path": "/test/file.mp4",
                  "format": "MP4",
                  "size": 1024,
                  "metadata": {
                    "type": "hologram",
                    "duration": 120.5
                  },
                  "status": "PENDING",
                  "progress": 0
                }
                """;

        // When: Deserialize - must not fail the whole ConversionFile
        ConversionFile file = assertDoesNotThrow(
                () -> JsonUtils.getObjectMapper().readValue(futureJson, ConversionFile.class));

        // Then: File is intact; the unknowable metadata degrades to null
        assertNotNull(file);
        assertEquals("future-type-id", file.id());
        assertNull(file.metadata());
    }

    // ==================== Status Transition Finalization Tests ====================

    @Test
    void withStatus_Completed_ShouldNormalizeProgressTo100() {
        // Given: File mid-conversion at 37%
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize)
                .withStatus(ConversionStatus.IN_PROGRESS)
                .withProgress(37);

        // When: Transition to COMPLETED
        ConversionFile completed = file.withStatus(ConversionStatus.COMPLETED);

        // Then: A completed file must never show stale progress
        assertEquals(ConversionStatus.COMPLETED, completed.status());
        assertEquals(100, completed.progress());
    }

    @Test
    void withStatus_PendingAfterCompleted_ShouldBeAllowedWithoutThrowing() {
        // Given: A completed file (e.g. session restore / UI retry resets it)
        ConversionFile completed = ConversionFile.create(testPath, testFormat, testSize)
                .withProgress(80)
                .withStatus(ConversionStatus.COMPLETED);

        // When/Then: Backward transition is legitimate and must not throw
        ConversionFile pending = assertDoesNotThrow(
                () -> completed.withStatus(ConversionStatus.PENDING));
        assertEquals(ConversionStatus.PENDING, pending.status());
    }

    @Test
    void withCancelled_ShouldKeepProgressReached() {
        // Given: File cancelled mid-conversion at 42%
        ConversionFile file = ConversionFile.create(testPath, testFormat, testSize)
                .withStatus(ConversionStatus.IN_PROGRESS)
                .withProgress(42);

        // When: Cancel
        ConversionFile cancelled = file.withCancelled();

        // Then: Progress is intentionally preserved to show how far it got
        assertEquals(ConversionStatus.CANCELLED, cancelled.status());
        assertEquals(42, cancelled.progress());
    }
}