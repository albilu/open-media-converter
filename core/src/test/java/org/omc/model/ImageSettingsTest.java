package org.omc.model;

import org.omc.model.Resolution;
import org.omc.model.ResizeMode;
import org.omc.model.ImageSettings;
import org.omc.model.FileFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ImageSettings class.
 * Covers builder pattern, validation, serialization, equals/hashCode, and
 * toString.
 */
class ImageSettingsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void builder_WithDefaults_ShouldSetDefaultValues() {
        // Given: Default builder
        ImageSettings settings = ImageSettings.builder().build();

        // Then: Check all default values (0 means use tool defaults)
        assertEquals(0, settings.quality());
        assertNull(settings.resolution());
        assertTrue(settings.maintainAspectRatio());
        assertEquals(0, settings.compressionLevel());
        assertEquals(ResizeMode.NONE, settings.resizeMode());
        assertEquals(FileFormat.PNG, settings.outputFormat());
    }

    @Test
    void builder_WithCustomValuesIncludingOutputFormatAndResizeMode_ShouldSetCustomValues() {
        // Given: Builder with custom values
        Resolution resolution = new Resolution(1920, 1080);
        ImageSettings settings = ImageSettings.builder()
                .quality(90)
                .resolution(resolution)
                .maintainAspectRatio(false)
                .compressionLevel(8)
                .resizeMode(ResizeMode.FIT)
                .outputFormat(FileFormat.JPEG)
                .build();

        // Then: All values should be set correctly
        assertEquals(90, settings.quality());
        assertEquals(resolution, settings.resolution());
        assertFalse(settings.maintainAspectRatio());
        assertEquals(8, settings.compressionLevel());
        assertEquals(ResizeMode.FIT, settings.resizeMode());
        assertEquals(FileFormat.JPEG, settings.outputFormat());
    }

    @Test
    void isValid_WithQualityTooLow_ShouldThrowException() {
        // Given: Quality below 0 (not -1)
        // Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> ImageSettings.builder()
                .quality(-2)
                .build());
    }

    @Test
    void isValid_WithQualityTooHigh_ShouldThrowException() {
        // Given: Quality above 100
        // Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> ImageSettings.builder()
                .quality(101)
                .build());
    }

    @Test
    void isValid_WithQualityZero_ShouldReturnTrue() {
        // Given: Quality 0
        ImageSettings settings = ImageSettings.builder()
                .quality(0)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithQualityHundred_ShouldReturnTrue() {
        // Given: Quality 100
        ImageSettings settings = ImageSettings.builder()
                .quality(100)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithQualityLossless_ShouldReturnTrue() {
        // Given: Quality -1 (lossless)
        ImageSettings settings = ImageSettings.builder()
                .quality(-1)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithCompressionLevelTooLow_ShouldThrowException() {
        // Given: Compression level below 0
        // Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> ImageSettings.builder()
                .compressionLevel(-1)
                .build());
    }

    @Test
    void isValid_WithCompressionLevelTooHigh_ShouldThrowException() {
        // Given: Compression level above 9
        // Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> ImageSettings.builder()
                .compressionLevel(10)
                .build());
    }

    @Test
    void isValid_WithCompressionLevelZero_ShouldReturnTrue() {
        // Given: Compression level 0
        ImageSettings settings = ImageSettings.builder()
                .compressionLevel(0)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithCompressionLevelNine_ShouldReturnTrue() {
        // Given: Compression level 9
        ImageSettings settings = ImageSettings.builder()
                .compressionLevel(9)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithValidImageOutputFormat_ShouldReturnTrue() {
        // Given: Valid IMAGE format
        ImageSettings settings = ImageSettings.builder()
                .outputFormat(FileFormat.JPEG)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithInvalidVideoOutputFormat_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> ImageSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build());
    }

    @Test
    void isValid_WithInvalidAudioOutputFormat_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> ImageSettings.builder()
                .outputFormat(FileFormat.MP3)
                .build());
    }

    @Test
    void isValid_WithInvalidDocumentOutputFormat_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        // Use HTML (pure DOCUMENT format) instead of PDF (which supports IMAGE)
        assertThrows(IllegalArgumentException.class, () -> ImageSettings.builder()
                .outputFormat(FileFormat.HTML)
                .build());
    }

    @Test
    void isValid_WithPdfOutputFormat_ShouldReturnTrue() {
        // Requirement REQ-PDF-1.2: PDF should be accepted as valid image output format
        // PDF has dual category support (DOCUMENT + IMAGE)
        ImageSettings settings = ImageSettings.builder()
                .outputFormat(FileFormat.PDF)
                .build();

        assertTrue(settings.isValid());
        assertEquals(FileFormat.PDF, settings.outputFormat());
    }

    @Test
    void builder_WithNullOutputFormat_ShouldThrowException() {
        // Given: Null outputFormat
        // Then: Should throw exception
        assertThrows(IllegalArgumentException.class, () -> ImageSettings.builder()
                .outputFormat(null)
                .build());
    }

    @Test
    void jsonSerialization_WithOutputFormatAndResizeMode_ShouldPreserveValues() throws Exception {
        // Given: ImageSettings with custom outputFormat and resizeMode
        ImageSettings original = ImageSettings.builder()
                .outputFormat(FileFormat.WEBP)
                .resizeMode(ResizeMode.LANCZOS)
                .build();

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        ImageSettings deserialized = objectMapper.readValue(json, ImageSettings.class);

        // Then: outputFormat and resizeMode should be preserved
        assertEquals(original.outputFormat(), deserialized.outputFormat());
        assertEquals(FileFormat.WEBP, deserialized.outputFormat());
        assertEquals(original.resizeMode(), deserialized.resizeMode());
        assertEquals(ResizeMode.LANCZOS, deserialized.resizeMode());
    }

    @Test
    void jsonSerialization_WithAllFields_ShouldPreserveAllFields() throws Exception {
        // Given: ImageSettings with all fields set (resolution null for simplicity)
        ImageSettings original = ImageSettings.builder()
                .quality(95)
                .resolution(null)
                .maintainAspectRatio(false)
                .compressionLevel(7)
                .resizeMode(ResizeMode.BICUBIC)
                .outputFormat(FileFormat.TIFF)
                .build();

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        ImageSettings deserialized = objectMapper.readValue(json, ImageSettings.class);

        // Then: All fields should be preserved
        assertEquals(original, deserialized);
    }

    @Test
    void equals_WithSameValuesIncludingOutputFormat_ShouldReturnTrue() {
        // Given: Two identical ImageSettings
        Resolution resolution = new Resolution(1920, 1080);
        ImageSettings settings1 = ImageSettings.builder()
                .quality(90)
                .resolution(resolution)
                .maintainAspectRatio(false)
                .compressionLevel(8)
                .resizeMode(ResizeMode.FILL)
                .outputFormat(FileFormat.BMP)
                .build();
        ImageSettings settings2 = ImageSettings.builder()
                .quality(90)
                .resolution(resolution)
                .maintainAspectRatio(false)
                .compressionLevel(8)
                .resizeMode(ResizeMode.FILL)
                .outputFormat(FileFormat.BMP)
                .build();

        // Then: Should be equal
        assertEquals(settings1, settings2);
    }

    @Test
    void equals_WithDifferentOutputFormat_ShouldReturnFalse() {
        // Given: Two ImageSettings with different outputFormat
        ImageSettings settings1 = ImageSettings.builder()
                .outputFormat(FileFormat.PNG)
                .build();
        ImageSettings settings2 = ImageSettings.builder()
                .outputFormat(FileFormat.GIF)
                .build();

        // Then: Should not be equal
        assertNotEquals(settings1, settings2);
    }

    @Test
    void hashCode_WithSameValuesIncludingOutputFormat_ShouldBeEqual() {
        // Given: Two identical ImageSettings
        Resolution resolution = new Resolution(800, 600);
        ImageSettings settings1 = ImageSettings.builder()
                .quality(75)
                .resolution(resolution)
                .maintainAspectRatio(true)
                .compressionLevel(5)
                .resizeMode(ResizeMode.STRETCH)
                .outputFormat(FileFormat.JPEG)
                .build();
        ImageSettings settings2 = ImageSettings.builder()
                .quality(75)
                .resolution(resolution)
                .maintainAspectRatio(true)
                .compressionLevel(5)
                .resizeMode(ResizeMode.STRETCH)
                .outputFormat(FileFormat.JPEG)
                .build();

        // Then: Hash codes should be equal
        assertEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void hashCode_WithDifferentOutputFormat_ShouldBeDifferent() {
        // Given: Two ImageSettings with different outputFormat
        ImageSettings settings1 = ImageSettings.builder()
                .outputFormat(FileFormat.PNG)
                .build();
        ImageSettings settings2 = ImageSettings.builder()
                .outputFormat(FileFormat.WEBP)
                .build();

        // Then: Hash codes should be different
        assertNotEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void toString_ShouldIncludeOutputFormatAndResizeMode() {
        // Given: ImageSettings with custom values
        Resolution resolution = new Resolution(1024, 768);
        ImageSettings settings = ImageSettings.builder()
                .quality(80)
                .resolution(resolution)
                .maintainAspectRatio(true)
                .compressionLevel(4)
                .resizeMode(ResizeMode.BILINEAR)
                .outputFormat(FileFormat.GIF)
                .build();

        // When: Get string representation
        String toString = settings.toString();

        // Then: Should include all fields
        assertTrue(toString.contains("ImageSettings{"));
        assertTrue(toString.contains("quality=80"));
        assertTrue(toString.contains("resolution=1024x768"));
        assertTrue(toString.contains("maintainAspectRatio=true"));
        assertTrue(toString.contains("compressionLevel=4"));
        assertTrue(toString.contains("resizeMode=Bilinear"));
        assertTrue(toString.contains("outputFormat=GIF"));
    }

    @Test
    void resizeMode_None_ShouldBeHandledCorrectly() {
        // Given: ResizeMode.NONE
        ImageSettings settings = ImageSettings.builder()
                .resizeMode(ResizeMode.NONE)
                .build();

        // Then: Should be set correctly
        assertEquals(ResizeMode.NONE, settings.resizeMode());
        assertTrue(settings.isValid());
    }

    @Test
    void resizeMode_Fit_ShouldBeHandledCorrectly() {
        // Given: ResizeMode.FIT
        ImageSettings settings = ImageSettings.builder()
                .resizeMode(ResizeMode.FIT)
                .build();

        // Then: Should be set correctly
        assertEquals(ResizeMode.FIT, settings.resizeMode());
        assertTrue(settings.isValid());
    }

    @Test
    void resizeMode_Fill_ShouldBeHandledCorrectly() {
        // Given: ResizeMode.FILL
        ImageSettings settings = ImageSettings.builder()
                .resizeMode(ResizeMode.FILL)
                .build();

        // Then: Should be set correctly
        assertEquals(ResizeMode.FILL, settings.resizeMode());
        assertTrue(settings.isValid());
    }

    @Test
    void resizeMode_Stretch_ShouldBeHandledCorrectly() {
        // Given: ResizeMode.STRETCH
        ImageSettings settings = ImageSettings.builder()
                .resizeMode(ResizeMode.STRETCH)
                .build();

        // Then: Should be set correctly
        assertEquals(ResizeMode.STRETCH, settings.resizeMode());
        assertTrue(settings.isValid());
    }

    @Test
    void resizeMode_Lanczos_ShouldBeHandledCorrectly() {
        // Given: ResizeMode.LANCZOS
        ImageSettings settings = ImageSettings.builder()
                .resizeMode(ResizeMode.LANCZOS)
                .build();

        // Then: Should be set correctly
        assertEquals(ResizeMode.LANCZOS, settings.resizeMode());
        assertTrue(settings.isValid());
    }

    @Test
    void resizeMode_Bicubic_ShouldBeHandledCorrectly() {
        // Given: ResizeMode.BICUBIC
        ImageSettings settings = ImageSettings.builder()
                .resizeMode(ResizeMode.BICUBIC)
                .build();

        // Then: Should be set correctly
        assertEquals(ResizeMode.BICUBIC, settings.resizeMode());
        assertTrue(settings.isValid());
    }

    @Test
    void resizeMode_Bilinear_ShouldBeHandledCorrectly() {
        // Given: ResizeMode.BILINEAR
        ImageSettings settings = ImageSettings.builder()
                .resizeMode(ResizeMode.BILINEAR)
                .build();

        // Then: Should be set correctly
        assertEquals(ResizeMode.BILINEAR, settings.resizeMode());
        assertTrue(settings.isValid());
    }

    @Test
    void resizeMode_NearestNeighbor_ShouldBeHandledCorrectly() {
        // Given: ResizeMode.NEAREST_NEIGHBOR
        ImageSettings settings = ImageSettings.builder()
                .resizeMode(ResizeMode.NEAREST_NEIGHBOR)
                .build();

        // Then: Should be set correctly
        assertEquals(ResizeMode.NEAREST_NEIGHBOR, settings.resizeMode());
        assertTrue(settings.isValid());
    }

    @Test
    void resolution_WithMaintainAspectRatioTrue_ShouldBeValid() {
        // Given: Resolution with maintainAspectRatio true
        Resolution resolution = new Resolution(1920, 1080);
        ImageSettings settings = ImageSettings.builder()
                .resolution(resolution)
                .maintainAspectRatio(true)
                .build();

        // Then: Should be valid
        assertEquals(resolution, settings.resolution());
        assertTrue(settings.maintainAspectRatio());
        assertTrue(settings.isValid());
    }

    @Test
    void resolution_WithMaintainAspectRatioFalse_ShouldBeValid() {
        // Given: Resolution with maintainAspectRatio false
        Resolution resolution = new Resolution(800, 600);
        ImageSettings settings = ImageSettings.builder()
                .resolution(resolution)
                .maintainAspectRatio(false)
                .build();

        // Then: Should be valid
        assertEquals(resolution, settings.resolution());
        assertFalse(settings.maintainAspectRatio());
        assertTrue(settings.isValid());
    }

    @Test
    void resolution_Null_ShouldBeValid() {
        // Given: Null resolution (original size)
        ImageSettings settings = ImageSettings.builder()
                .resolution(null)
                .build();

        // Then: Should be valid
        assertNull(settings.resolution());
        assertTrue(settings.isValid());
    }

    // ========== Rotation Tests (REQ-IMG-1.1) ==========

    @Test
    void builder_WithDefaults_ShouldSetDefaultRotation() {
        // Given: Default builder
        ImageSettings settings = ImageSettings.builder().build();

        // Then: Rotation should default to NONE
        assertEquals(ImageRotation.NONE, settings.rotation());
    }

    @Test
    void builder_WithCustomRotation_ShouldSetCustomValue() {
        // Given: Builder with custom rotation
        ImageSettings settings = ImageSettings.builder()
                .rotation(ImageRotation.CLOCKWISE_90)
                .build();

        // Then: Rotation should be CLOCKWISE_90
        assertEquals(ImageRotation.CLOCKWISE_90, settings.rotation());
    }

    @Test
    void rotation_WithAllRotations_ShouldAcceptAllValues() {
        // Given/When: Build ImageSettings with all rotations
        ImageSettings none = ImageSettings.builder().rotation(ImageRotation.NONE).build();
        ImageSettings rotate90 = ImageSettings.builder().rotation(ImageRotation.CLOCKWISE_90).build();
        ImageSettings rotate180 = ImageSettings.builder().rotation(ImageRotation.ROTATE_180).build();
        ImageSettings rotate270 = ImageSettings.builder().rotation(ImageRotation.COUNTER_CLOCKWISE_90).build();

        // Then: All should have correct rotation set
        assertEquals(ImageRotation.NONE, none.rotation());
        assertEquals(ImageRotation.CLOCKWISE_90, rotate90.rotation());
        assertEquals(ImageRotation.ROTATE_180, rotate180.rotation());
        assertEquals(ImageRotation.COUNTER_CLOCKWISE_90, rotate270.rotation());
    }

    @Test
    void jsonSerialization_WithRotation_ShouldPreserveRotation() throws Exception {
        // Given: ImageSettings with custom rotation
        ImageSettings original = ImageSettings.builder()
                .rotation(ImageRotation.CLOCKWISE_90)
                .build();

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        ImageSettings deserialized = objectMapper.readValue(json, ImageSettings.class);

        // Then: Rotation should be preserved
        assertEquals(original.rotation(), deserialized.rotation());
        assertEquals(ImageRotation.CLOCKWISE_90, deserialized.rotation());
    }

    @Test
    void jsonSerialization_WithMissingRotation_ShouldDefaultToNone() throws Exception {
        // Given: JSON without rotation field (backward compatibility)
        String json = "{\"quality\":0,\"resolution\":null,\"maintainAspectRatio\":true," +
                "\"compressionLevel\":0,\"resizeMode\":\"None\",\"outputFormat\":\"PNG\"}";

        // When: Deserialize
        ImageSettings deserialized = objectMapper.readValue(json, ImageSettings.class);

        // Then: Rotation should default to NONE
        assertEquals(ImageRotation.NONE, deserialized.rotation());
    }

    @Test
    void equals_WithDifferentRotation_ShouldReturnFalse() {
        // Given: Two ImageSettings with different rotations
        ImageSettings settings1 = ImageSettings.builder()
                .rotation(ImageRotation.CLOCKWISE_90)
                .build();
        ImageSettings settings2 = ImageSettings.builder()
                .rotation(ImageRotation.ROTATE_180)
                .build();

        // Then: Should not be equal
        assertNotEquals(settings1, settings2);
    }

    @Test
    void equals_WithSameRotation_ShouldReturnTrue() {
        // Given: Two identical ImageSettings with same rotation
        ImageSettings settings1 = ImageSettings.builder()
                .rotation(ImageRotation.COUNTER_CLOCKWISE_90)
                .build();
        ImageSettings settings2 = ImageSettings.builder()
                .rotation(ImageRotation.COUNTER_CLOCKWISE_90)
                .build();

        // Then: Should be equal
        assertEquals(settings1, settings2);
    }

    @Test
    void hashCode_WithSameRotation_ShouldBeEqual() {
        // Given: Two identical ImageSettings with same rotation
        ImageSettings settings1 = ImageSettings.builder()
                .rotation(ImageRotation.ROTATE_180)
                .build();
        ImageSettings settings2 = ImageSettings.builder()
                .rotation(ImageRotation.ROTATE_180)
                .build();

        // Then: Hash codes should be equal
        assertEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void toString_ShouldIncludeRotation() {
        // Given: ImageSettings with custom rotation
        ImageSettings settings = ImageSettings.builder()
                .rotation(ImageRotation.CLOCKWISE_90)
                .build();

        // When: Get string representation
        String toString = settings.toString();

        // Then: Should include rotation
        assertTrue(toString.contains("rotation=CLOCKWISE_90"));
        assertTrue(toString.contains("ImageSettings{"));
    }

    // ========== Flip Tests (REQ-IMG-2.1) ==========

    @Test
    void builder_WithDefaults_ShouldSetDefaultFlip() {
        // Given: Default builder
        ImageSettings settings = ImageSettings.builder().build();

        // Then: Flip should default to NONE
        assertEquals(ImageFlip.NONE, settings.flip());
    }

    @Test
    void builder_WithCustomFlip_ShouldSetCustomValue() {
        // Given: Builder with custom flip
        ImageSettings settings = ImageSettings.builder()
                .flip(ImageFlip.HORIZONTAL)
                .build();

        // Then: Flip should be HORIZONTAL
        assertEquals(ImageFlip.HORIZONTAL, settings.flip());
    }

    @Test
    void flip_WithAllFlips_ShouldAcceptAllValues() {
        // Given/When: Build ImageSettings with all flips
        ImageSettings none = ImageSettings.builder().flip(ImageFlip.NONE).build();
        ImageSettings horizontal = ImageSettings.builder().flip(ImageFlip.HORIZONTAL).build();
        ImageSettings vertical = ImageSettings.builder().flip(ImageFlip.VERTICAL).build();
        ImageSettings both = ImageSettings.builder().flip(ImageFlip.BOTH).build();

        // Then: All should have correct flip set
        assertEquals(ImageFlip.NONE, none.flip());
        assertEquals(ImageFlip.HORIZONTAL, horizontal.flip());
        assertEquals(ImageFlip.VERTICAL, vertical.flip());
        assertEquals(ImageFlip.BOTH, both.flip());
    }

    @Test
    void jsonSerialization_WithFlip_ShouldPreserveFlip() throws Exception {
        // Given: ImageSettings with custom flip
        ImageSettings original = ImageSettings.builder()
                .flip(ImageFlip.HORIZONTAL)
                .build();

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        ImageSettings deserialized = objectMapper.readValue(json, ImageSettings.class);

        // Then: Flip should be preserved
        assertEquals(original.flip(), deserialized.flip());
        assertEquals(ImageFlip.HORIZONTAL, deserialized.flip());
    }

    @Test
    void jsonSerialization_WithMissingFlip_ShouldDefaultToNone() throws Exception {
        // Given: JSON without flip field (backward compatibility)
        String json = "{\"quality\":0,\"resolution\":null,\"maintainAspectRatio\":true," +
                "\"compressionLevel\":0,\"resizeMode\":\"None\",\"outputFormat\":\"PNG\"}";

        // When: Deserialize
        ImageSettings deserialized = objectMapper.readValue(json, ImageSettings.class);

        // Then: Flip should default to NONE
        assertEquals(ImageFlip.NONE, deserialized.flip());
    }

    @Test
    void equals_WithDifferentFlip_ShouldReturnFalse() {
        // Given: Two ImageSettings with different flips
        ImageSettings settings1 = ImageSettings.builder()
                .flip(ImageFlip.HORIZONTAL)
                .build();
        ImageSettings settings2 = ImageSettings.builder()
                .flip(ImageFlip.VERTICAL)
                .build();

        // Then: Should not be equal
        assertNotEquals(settings1, settings2);
    }

    @Test
    void equals_WithSameFlip_ShouldReturnTrue() {
        // Given: Two identical ImageSettings with same flip
        ImageSettings settings1 = ImageSettings.builder()
                .flip(ImageFlip.BOTH)
                .build();
        ImageSettings settings2 = ImageSettings.builder()
                .flip(ImageFlip.BOTH)
                .build();

        // Then: Should be equal
        assertEquals(settings1, settings2);
    }

    @Test
    void hashCode_WithSameFlip_ShouldBeEqual() {
        // Given: Two identical ImageSettings with same flip
        ImageSettings settings1 = ImageSettings.builder()
                .flip(ImageFlip.VERTICAL)
                .build();
        ImageSettings settings2 = ImageSettings.builder()
                .flip(ImageFlip.VERTICAL)
                .build();

        // Then: Hash codes should be equal
        assertEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void toString_ShouldIncludeFlip() {
        // Given: ImageSettings with custom flip
        ImageSettings settings = ImageSettings.builder()
                .flip(ImageFlip.HORIZONTAL)
                .build();

        // When: Get string representation
        String toString = settings.toString();

        // Then: Should include flip
        assertTrue(toString.contains("flip=HORIZONTAL"));
        assertTrue(toString.contains("ImageSettings{"));
    }

    // ========== Combined Rotation and Flip Tests ==========

    @Test
    void jsonSerialization_WithRotationAndFlip_ShouldPreserveAll() throws Exception {
        // Given: ImageSettings with rotation and flip
        ImageSettings original = ImageSettings.builder()
                .quality(85)
                .rotation(ImageRotation.CLOCKWISE_90)
                .flip(ImageFlip.HORIZONTAL)
                .outputFormat(FileFormat.JPEG)
                .build();

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        ImageSettings deserialized = objectMapper.readValue(json, ImageSettings.class);

        // Then: All fields should be preserved
        assertEquals(original, deserialized);
        assertEquals(ImageRotation.CLOCKWISE_90, deserialized.rotation());
        assertEquals(ImageFlip.HORIZONTAL, deserialized.flip());
    }

    @Test
    void builder_WithRotationAndFlip_ShouldAcceptBoth() {
        // Given/When: Build ImageSettings with both rotation and flip
        ImageSettings settings = ImageSettings.builder()
                .rotation(ImageRotation.ROTATE_180)
                .flip(ImageFlip.VERTICAL)
                .build();

        // Then: Both should be set correctly
        assertEquals(ImageRotation.ROTATE_180, settings.rotation());
        assertEquals(ImageFlip.VERTICAL, settings.flip());
        assertTrue(settings.isValid());
    }
}