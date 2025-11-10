package org.omc.model;

import org.omc.model.VideoSettings;
import org.omc.model.FileFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VideoSettings class.
 * Covers builder pattern, validation, serialization, equals/hashCode, and
 * toString.
 */
class VideoSettingsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void builder_WithDefaults_ShouldSetDefaultValues() {
        // Given: Default builder
        VideoSettings settings = VideoSettings.builder().build();

        // Then: Check all default values
        assertEquals("libx264", settings.codec());
        assertEquals(5000, settings.bitrate());
        assertNull(settings.resolution());
        assertEquals(-1, settings.frameRate());
        assertEquals("medium", settings.preset());
        assertEquals(23, settings.crf());
        assertEquals(FileFormat.MP4, settings.outputFormat());
    }

    @Test
    void builder_WithCustomOutputFormat_ShouldSetCustomValue() {
        // Given: Builder with custom outputFormat
        VideoSettings settings = VideoSettings.builder()
                .outputFormat(FileFormat.MKV)
                .build();

        // Then: outputFormat should be MKV, others default
        assertEquals(FileFormat.MKV, settings.outputFormat());
        assertEquals("libx264", settings.codec());
        assertEquals(5000, settings.bitrate());
        assertNull(settings.resolution());
        assertEquals(-1, settings.frameRate());
        assertEquals("medium", settings.preset());
        assertEquals(23, settings.crf());
    }

    @Test
    void isValid_WithValidVideoOutputFormat_ShouldReturnTrue() {
        // Given: Valid VIDEO format
        VideoSettings settings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithInvalidAudioOutputFormat_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> VideoSettings.builder()
                .outputFormat(FileFormat.AAC)
                .build());
    }

    @Test
    void isValid_WithInvalidImageOutputFormat_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> VideoSettings.builder()
                .outputFormat(FileFormat.PNG)
                .build());
    }

    @Test
    void isValid_WithInvalidDocumentOutputFormat_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> VideoSettings.builder()
                .outputFormat(FileFormat.PDF)
                .build());
    }

    @Test
    void isValid_WithBitrateTooLow_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> VideoSettings.builder()
                .bitrate(499)
                .build());
    }

    @Test
    void isValid_WithBitrateTooHigh_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> VideoSettings.builder()
                .bitrate(50001)
                .build());
    }

    @Test
    void isValid_WithInvalidFrameRate_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> VideoSettings.builder()
                .frameRate(0)
                .build());
    }

    @Test
    void isValid_WithFrameRateTooHigh_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> VideoSettings.builder()
                .frameRate(121)
                .build());
    }

    @Test
    void isValid_WithValidFrameRate_ShouldReturnTrue() {
        // Given: Valid frameRate
        VideoSettings settings = VideoSettings.builder()
                .frameRate(30)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithOriginalFrameRate_ShouldReturnTrue() {
        // Given: Original frameRate (-1)
        VideoSettings settings = VideoSettings.builder()
                .frameRate(-1)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithCrfTooLow_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> VideoSettings.builder()
                .crf(-1)
                .build());
    }

    @Test
    void isValid_WithCrfTooHigh_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> VideoSettings.builder()
                .crf(52)
                .build());
    }

    @Test
    void isValid_WithInvalidPreset_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> VideoSettings.builder()
                .preset("invalid")
                .build());
    }

    @Test
    void isValid_WithNullPreset_ShouldReturnTrue() {
        // Given: Null preset
        VideoSettings settings = VideoSettings.builder()
                .preset(null)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithValidPreset_ShouldReturnTrue() {
        // Given: Valid preset
        VideoSettings settings = VideoSettings.builder()
                .preset("fast")
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void jsonSerialization_WithOutputFormat_ShouldPreserveOutputFormat() throws Exception {
        // Given: VideoSettings with custom outputFormat
        VideoSettings original = VideoSettings.builder()
                .outputFormat(FileFormat.WEBM)
                .build();

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        VideoSettings deserialized = objectMapper.readValue(json, VideoSettings.class);

        // Then: outputFormat should be preserved
        assertEquals(original.outputFormat(), deserialized.outputFormat());
        assertEquals(FileFormat.WEBM, deserialized.outputFormat());
    }

    @Test
    void jsonSerialization_WithAllFields_ShouldPreserveAllFields() throws Exception {
        // Given: VideoSettings with all fields set (resolution null for simplicity)
        VideoSettings original = VideoSettings.builder()
                .codec("libx265")
                .bitrate(8000)
                .resolution(null)
                .frameRate(60)
                .preset("slow")
                .crf(20)
                .outputFormat(FileFormat.MKV)
                .build();

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        VideoSettings deserialized = objectMapper.readValue(json, VideoSettings.class);

        // Then: All fields should be preserved
        assertEquals(original, deserialized);
    }

    @Test
    void equals_WithSameOutputFormat_ShouldReturnTrue() {
        // Given: Two identical VideoSettings
        VideoSettings settings1 = VideoSettings.builder()
                .outputFormat(FileFormat.AVI)
                .build();
        VideoSettings settings2 = VideoSettings.builder()
                .outputFormat(FileFormat.AVI)
                .build();

        // Then: Should be equal
        assertEquals(settings1, settings2);
    }

    @Test
    void equals_WithDifferentOutputFormat_ShouldReturnFalse() {
        // Given: Two VideoSettings with different outputFormat
        VideoSettings settings1 = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();
        VideoSettings settings2 = VideoSettings.builder()
                .outputFormat(FileFormat.MKV)
                .build();

        // Then: Should not be equal
        assertNotEquals(settings1, settings2);
    }

    @Test
    void hashCode_WithSameOutputFormat_ShouldBeEqual() {
        // Given: Two identical VideoSettings
        VideoSettings settings1 = VideoSettings.builder()
                .outputFormat(FileFormat.FLV)
                .build();
        VideoSettings settings2 = VideoSettings.builder()
                .outputFormat(FileFormat.FLV)
                .build();

        // Then: Hash codes should be equal
        assertEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void hashCode_WithDifferentOutputFormat_ShouldBeDifferent() {
        // Given: Two VideoSettings with different outputFormat
        VideoSettings settings1 = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();
        VideoSettings settings2 = VideoSettings.builder()
                .outputFormat(FileFormat.WMV)
                .build();

        // Then: Hash codes should be different
        assertNotEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void toString_ShouldIncludeOutputFormat() {
        // Given: VideoSettings with custom outputFormat
        VideoSettings settings = VideoSettings.builder()
                .outputFormat(FileFormat.MOV)
                .build();

        // When: Get string representation
        String toString = settings.toString();

        // Then: Should include outputFormat
        assertTrue(toString.contains("outputFormat=MOV"));
        assertTrue(toString.contains("VideoSettings{"));
        assertTrue(toString.contains("codec='libx264'"));
        assertTrue(toString.contains("bitrate=5000"));
        assertTrue(toString.contains("resolution=null"));
        assertTrue(toString.contains("frameRate=-1"));
        assertTrue(toString.contains("preset='medium'"));
        assertTrue(toString.contains("crf=23"));
    }

    // ========== GPU Codec Validation Tests (REQ-VID-1.1, REQ-VID-1.2, REQ-VID-1.3)
    // ==========

    @Test
    void isValid_WithMPEG4Codec_ShouldReturnTrue() {
        // Given: VideoSettings with MPEG-4 codec
        VideoSettings settings = VideoSettings.builder()
                .codec("mpeg4")
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
        assertEquals("mpeg4", settings.codec());
    }

    @Test
    void isValid_WithH264NVENCCodec_ShouldReturnTrue() {
        // Given: VideoSettings with H.264 NVIDIA GPU codec
        VideoSettings settings = VideoSettings.builder()
                .codec("h264_nvenc")
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
        assertEquals("h264_nvenc", settings.codec());
    }

    @Test
    void isValid_WithHEVCNVENCCodec_ShouldReturnTrue() {
        // Given: VideoSettings with HEVC (H.265) NVIDIA GPU codec
        VideoSettings settings = VideoSettings.builder()
                .codec("hevc_nvenc")
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
        assertEquals("hevc_nvenc", settings.codec());
    }

    @Test
    void builder_WithGPUCodec_ShouldAcceptAllGPUCodecs() {
        // Given/When: Build VideoSettings with various GPU codecs
        VideoSettings mpeg4 = VideoSettings.builder().codec("mpeg4").build();
        VideoSettings h264nvenc = VideoSettings.builder().codec("h264_nvenc").build();
        VideoSettings hevcnvenc = VideoSettings.builder().codec("hevc_nvenc").build();

        // Then: All should be valid
        assertTrue(mpeg4.isValid());
        assertTrue(h264nvenc.isValid());
        assertTrue(hevcnvenc.isValid());
    }

    // ========== Aspect Ratio Tests (REQ-VID-2.1) ==========

    @Test
    void builder_WithDefaults_ShouldSetDefaultAspectRatio() {
        // Given: Default builder
        VideoSettings settings = VideoSettings.builder().build();

        // Then: Aspect ratio should default to KEEP_ORIGINAL
        assertEquals(AspectRatio.KEEP_ORIGINAL, settings.aspectRatio());
    }

    @Test
    void builder_WithCustomAspectRatio_ShouldSetCustomValue() {
        // Given: Builder with custom aspect ratio
        VideoSettings settings = VideoSettings.builder()
                .aspectRatio(AspectRatio.RATIO_16_9)
                .build();

        // Then: Aspect ratio should be 16:9
        assertEquals(AspectRatio.RATIO_16_9, settings.aspectRatio());
    }

    @Test
    void aspectRatio_WithAllAspectRatios_ShouldAcceptAllValues() {
        // Given/When: Build VideoSettings with all aspect ratios
        VideoSettings keepOriginal = VideoSettings.builder().aspectRatio(AspectRatio.KEEP_ORIGINAL).build();
        VideoSettings ratio16_9 = VideoSettings.builder().aspectRatio(AspectRatio.RATIO_16_9).build();
        VideoSettings ratio4_3 = VideoSettings.builder().aspectRatio(AspectRatio.RATIO_4_3).build();
        VideoSettings ratio1_1 = VideoSettings.builder().aspectRatio(AspectRatio.RATIO_1_1).build();
        VideoSettings ratio21_9 = VideoSettings.builder().aspectRatio(AspectRatio.RATIO_21_9).build();
        VideoSettings ratio9_16 = VideoSettings.builder().aspectRatio(AspectRatio.RATIO_9_16).build();
        VideoSettings ratio3_2 = VideoSettings.builder().aspectRatio(AspectRatio.RATIO_3_2).build();
        VideoSettings ratio2_39_1 = VideoSettings.builder().aspectRatio(AspectRatio.RATIO_2_39_1).build();

        // Then: All should have correct aspect ratio set
        assertEquals(AspectRatio.KEEP_ORIGINAL, keepOriginal.aspectRatio());
        assertEquals(AspectRatio.RATIO_16_9, ratio16_9.aspectRatio());
        assertEquals(AspectRatio.RATIO_4_3, ratio4_3.aspectRatio());
        assertEquals(AspectRatio.RATIO_1_1, ratio1_1.aspectRatio());
        assertEquals(AspectRatio.RATIO_21_9, ratio21_9.aspectRatio());
        assertEquals(AspectRatio.RATIO_9_16, ratio9_16.aspectRatio());
        assertEquals(AspectRatio.RATIO_3_2, ratio3_2.aspectRatio());
        assertEquals(AspectRatio.RATIO_2_39_1, ratio2_39_1.aspectRatio());
    }

    @Test
    void isValid_WithNullAspectRatio_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> VideoSettings.builder()
                .aspectRatio(null)
                .build());
    }

    @Test
    void jsonSerialization_WithAspectRatio_ShouldPreserveAspectRatio() throws Exception {
        // Given: VideoSettings with custom aspect ratio
        VideoSettings original = VideoSettings.builder()
                .aspectRatio(AspectRatio.RATIO_16_9)
                .build();

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        VideoSettings deserialized = objectMapper.readValue(json, VideoSettings.class);

        // Then: Aspect ratio should be preserved
        assertEquals(original.aspectRatio(), deserialized.aspectRatio());
        assertEquals(AspectRatio.RATIO_16_9, deserialized.aspectRatio());
    }

    @Test
    void jsonSerialization_WithMissingAspectRatio_ShouldDefaultToKeepOriginal() throws Exception {
        // Given: JSON without aspectRatio field (backward compatibility)
        String json = "{\"codec\":\"libx264\",\"bitrate\":5000,\"resolution\":null," +
                "\"frameRate\":-1,\"preset\":\"medium\",\"crf\":23,\"outputFormat\":\"MP4\"}";

        // When: Deserialize
        VideoSettings deserialized = objectMapper.readValue(json, VideoSettings.class);

        // Then: Aspect ratio should default to KEEP_ORIGINAL
        assertEquals(AspectRatio.KEEP_ORIGINAL, deserialized.aspectRatio());
    }

    @Test
    void equals_WithDifferentAspectRatio_ShouldReturnFalse() {
        // Given: Two VideoSettings with different aspect ratios
        VideoSettings settings1 = VideoSettings.builder()
                .aspectRatio(AspectRatio.RATIO_16_9)
                .build();
        VideoSettings settings2 = VideoSettings.builder()
                .aspectRatio(AspectRatio.RATIO_4_3)
                .build();

        // Then: Should not be equal
        assertNotEquals(settings1, settings2);
    }

    @Test
    void equals_WithSameAspectRatio_ShouldReturnTrue() {
        // Given: Two identical VideoSettings with same aspect ratio
        VideoSettings settings1 = VideoSettings.builder()
                .aspectRatio(AspectRatio.RATIO_21_9)
                .build();
        VideoSettings settings2 = VideoSettings.builder()
                .aspectRatio(AspectRatio.RATIO_21_9)
                .build();

        // Then: Should be equal
        assertEquals(settings1, settings2);
    }

    @Test
    void hashCode_WithSameAspectRatio_ShouldBeEqual() {
        // Given: Two identical VideoSettings with same aspect ratio
        VideoSettings settings1 = VideoSettings.builder()
                .aspectRatio(AspectRatio.RATIO_1_1)
                .build();
        VideoSettings settings2 = VideoSettings.builder()
                .aspectRatio(AspectRatio.RATIO_1_1)
                .build();

        // Then: Hash codes should be equal
        assertEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void toString_ShouldIncludeAspectRatio() {
        // Given: VideoSettings with custom aspect ratio
        VideoSettings settings = VideoSettings.builder()
                .aspectRatio(AspectRatio.RATIO_9_16)
                .build();

        // When: Get string representation
        String toString = settings.toString();

        // Then: Should include aspectRatio
        assertTrue(toString.contains("aspectRatio=RATIO_9_16"));
        assertTrue(toString.contains("VideoSettings{"));
    }

    @Test
    void jsonSerialization_WithGPUCodecAndAspectRatio_ShouldPreserveAll() throws Exception {
        // Given: VideoSettings with GPU codec and custom aspect ratio
        VideoSettings original = VideoSettings.builder()
                .codec("h264_nvenc")
                .bitrate(8000)
                .aspectRatio(AspectRatio.RATIO_16_9)
                .outputFormat(FileFormat.MP4)
                .build();

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        VideoSettings deserialized = objectMapper.readValue(json, VideoSettings.class);

        // Then: All fields should be preserved
        assertEquals(original, deserialized);
        assertEquals("h264_nvenc", deserialized.codec());
        assertEquals(AspectRatio.RATIO_16_9, deserialized.aspectRatio());
    }
}