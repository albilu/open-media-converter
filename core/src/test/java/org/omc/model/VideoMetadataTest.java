package org.omc.model;

import org.omc.model.FormatCategory;
import org.omc.model.MediaMetadata;
import org.omc.model.VideoMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VideoMetadata class.
 */
class VideoMetadataTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // Builder tests
    @Test
    void testBuilder_AllFieldsSet_BuildsCorrectly() {
        Duration duration = Duration.ofSeconds(120);
        VideoMetadata metadata = VideoMetadata.builder()
                .duration(duration)
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .audioCodec("aac")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        assertEquals(duration, metadata.getDuration());
        assertEquals(1920, metadata.getWidth());
        assertEquals(1080, metadata.getHeight());
        assertEquals("h264", metadata.getVideoCodec());
        assertEquals("aac", metadata.getAudioCodec());
        assertEquals(30.0, metadata.getFrameRate());
        assertEquals(5000000, metadata.getVideoBitrate());
        assertEquals(128000, metadata.getAudioBitrate());
    }

    @Test
    void testBuilder_DefaultValues_BuildsWithDefaults() {
        VideoMetadata metadata = VideoMetadata.builder().build();

        assertNull(metadata.getDuration());
        assertEquals(0, metadata.getWidth());
        assertEquals(0, metadata.getHeight());
        assertNull(metadata.getVideoCodec());
        assertNull(metadata.getAudioCodec());
        assertEquals(0.0, metadata.getFrameRate());
        assertEquals(0, metadata.getVideoBitrate());
        assertEquals(0, metadata.getAudioBitrate());
    }

    // Validation tests
    @Test
    void testIsValid_AllFieldsValid_ReturnsTrue() {
        VideoMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ofSeconds(60))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .audioCodec("aac")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        assertTrue(metadata.isValid());
    }

    @Test
    void testIsValid_NullDuration_ReturnsFalse() {
        VideoMetadata metadata = VideoMetadata.builder()
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_ZeroDuration_ReturnsFalse() {
        VideoMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ZERO)
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_NegativeDuration_ReturnsFalse() {
        VideoMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ofSeconds(-1))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_ZeroWidth_ReturnsFalse() {
        VideoMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ofSeconds(60))
                .width(0)
                .height(1080)
                .videoCodec("h264")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_NullVideoCodec_ReturnsFalse() {
        VideoMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ofSeconds(60))
                .width(1920)
                .height(1080)
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_BlankVideoCodec_ReturnsFalse() {
        VideoMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ofSeconds(60))
                .width(1920)
                .height(1080)
                .videoCodec("")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_ZeroFrameRate_ReturnsFalse() {
        VideoMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ofSeconds(60))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .frameRate(0.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_NegativeBitrates_ReturnsFalse() {
        VideoMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ofSeconds(60))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .frameRate(30.0)
                .videoBitrate(-1)
                .audioBitrate(128000)
                .build();

        assertFalse(metadata.isValid());
    }

    // Helper methods tests
    @Test
    void testGetResolution_ReturnsCorrectFormat() {
        VideoMetadata metadata = VideoMetadata.builder()
                .width(1920)
                .height(1080)
                .build();

        assertEquals("1920x1080", metadata.getResolution());
    }

    @Test
    void testGetAspectRatio_CalculatesCorrectly() {
        VideoMetadata metadata = VideoMetadata.builder()
                .width(1920)
                .height(1080)
                .build();

        assertEquals(16.0 / 9.0, metadata.getAspectRatio(), 0.001);
    }

    @Test
    void testGetAspectRatio_ZeroHeight_ReturnsZero() {
        VideoMetadata metadata = VideoMetadata.builder()
                .width(1920)
                .height(0)
                .build();

        assertEquals(0.0, metadata.getAspectRatio());
    }

    @Test
    void testIsHD_Height720_ReturnsTrue() {
        VideoMetadata metadata = VideoMetadata.builder()
                .height(720)
                .build();

        assertTrue(metadata.isHD());
    }

    @Test
    void testIsHD_Height719_ReturnsFalse() {
        VideoMetadata metadata = VideoMetadata.builder()
                .height(719)
                .build();

        assertFalse(metadata.isHD());
    }

    @Test
    void testIsFullHD_Height1080_ReturnsTrue() {
        VideoMetadata metadata = VideoMetadata.builder()
                .height(1080)
                .build();

        assertTrue(metadata.isFullHD());
    }

    @Test
    void testIsFullHD_Height1079_ReturnsFalse() {
        VideoMetadata metadata = VideoMetadata.builder()
                .height(1079)
                .build();

        assertFalse(metadata.isFullHD());
    }

    @Test
    void testIs4K_Height2160_ReturnsTrue() {
        VideoMetadata metadata = VideoMetadata.builder()
                .height(2160)
                .build();

        assertTrue(metadata.is4K());
    }

    @Test
    void testIs4K_Height2159_ReturnsFalse() {
        VideoMetadata metadata = VideoMetadata.builder()
                .height(2159)
                .build();

        assertFalse(metadata.is4K());
    }

    // getSummary tests
    @Test
    void testGetSummary_ValidMetadata_ReturnsFormattedString() {
        VideoMetadata metadata = VideoMetadata.builder()
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .frameRate(30.0)
                .duration(Duration.ofSeconds(3661)) // 1h 1m 1s
                .build();

        String summary = metadata.getSummary();
        assertTrue(summary.contains("1920x1080"));
        assertTrue(summary.contains("h264"));
        assertTrue(summary.contains("30"));
        assertTrue(summary.contains("fps"));
        assertTrue(summary.contains("1:01:01"));
    }

    @Test
    void testGetSummary_NullDuration_ReturnsUnknown() {
        VideoMetadata metadata = VideoMetadata.builder()
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .frameRate(30.0)
                .build();

        String summary = metadata.getSummary();
        assertTrue(summary.contains("unknown"));
    }

    // getCategory tests
    @Test
    void testGetCategory_ReturnsVideo() {
        VideoMetadata metadata = VideoMetadata.builder().build();

        assertEquals(FormatCategory.VIDEO, metadata.getCategory());
    }

    // equals/hashCode/toString tests
    @Test
    void testEquals_SameObject_ReturnsTrue() {
        VideoMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ofSeconds(60))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .audioCodec("aac")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        assertEquals(metadata, metadata);
    }

    @Test
    void testEquals_EqualObjects_ReturnsTrue() {
        VideoMetadata metadata1 = VideoMetadata.builder()
                .duration(Duration.ofSeconds(60))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .audioCodec("aac")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        VideoMetadata metadata2 = VideoMetadata.builder()
                .duration(Duration.ofSeconds(60))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .audioCodec("aac")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        assertEquals(metadata1, metadata2);
    }

    @Test
    void testEquals_DifferentObjects_ReturnsFalse() {
        VideoMetadata metadata1 = VideoMetadata.builder()
                .width(1920)
                .height(1080)
                .build();

        VideoMetadata metadata2 = VideoMetadata.builder()
                .width(1280)
                .height(720)
                .build();

        assertNotEquals(metadata1, metadata2);
    }

    @Test
    void testEquals_Null_ReturnsFalse() {
        VideoMetadata metadata = VideoMetadata.builder().build();

        assertNotEquals(null, metadata);
    }

    @Test
    void testEquals_DifferentClass_ReturnsFalse() {
        VideoMetadata metadata = VideoMetadata.builder().build();

        assertNotEquals("string", metadata);
    }

    @Test
    void testHashCode_EqualObjects_SameHashCode() {
        VideoMetadata metadata1 = VideoMetadata.builder()
                .duration(Duration.ofSeconds(60))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .audioCodec("aac")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        VideoMetadata metadata2 = VideoMetadata.builder()
                .duration(Duration.ofSeconds(60))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .audioCodec("aac")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        assertEquals(metadata1.hashCode(), metadata2.hashCode());
    }

    @Test
    void testToString_ContainsAllFields() {
        VideoMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ofSeconds(60))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .audioCodec("aac")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        String toString = metadata.toString();
        assertTrue(toString.contains("VideoMetadata"));
        assertTrue(toString.contains("duration=PT1M"));
        assertTrue(toString.contains("resolution=1920x1080"));
        assertTrue(toString.contains("videoCodec='h264'"));
        assertTrue(toString.contains("audioCodec='aac'"));
        assertTrue(toString.contains("frameRate=30.0"));
        assertTrue(toString.contains("videoBitrate=5000000"));
        assertTrue(toString.contains("audioBitrate=128000"));
    }

    // JSON serialization tests
    @Test
    void testJsonSerialization_SerializesCorrectly() throws Exception {
        VideoMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ofSeconds(120))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .audioCodec("aac")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        String json = objectMapper.writeValueAsString(metadata);

        assertTrue(json.contains("\"type\":\"video\""));
        assertTrue(json.contains("\"duration\":120"));
        assertTrue(json.contains("\"width\":1920"));
        assertTrue(json.contains("\"height\":1080"));
        assertTrue(json.contains("\"videoCodec\":\"h264\""));
        assertTrue(json.contains("\"audioCodec\":\"aac\""));
        assertTrue(json.contains("\"frameRate\":30.0"));
        assertTrue(json.contains("\"videoBitrate\":5000000"));
        assertTrue(json.contains("\"audioBitrate\":128000"));
    }

    @Test
    void testJsonDeserialization_DeserializesCorrectly() throws Exception {
        String json = """
                {
                    "type": "video",
                    "duration": 120.0,
                    "width": 1920,
                    "height": 1080,
                    "videoCodec": "h264",
                    "audioCodec": "aac",
                    "frameRate": 30.0,
                    "videoBitrate": 5000000,
                    "audioBitrate": 128000
                }
                """;

        MediaMetadata metadata = objectMapper.readValue(json, MediaMetadata.class);

        assertInstanceOf(VideoMetadata.class, metadata);
        VideoMetadata videoMetadata = (VideoMetadata) metadata;
        assertEquals(Duration.ofSeconds(120), videoMetadata.getDuration());
        assertEquals(1920, videoMetadata.getWidth());
        assertEquals(1080, videoMetadata.getHeight());
        assertEquals("h264", videoMetadata.getVideoCodec());
        assertEquals("aac", videoMetadata.getAudioCodec());
        assertEquals(30.0, videoMetadata.getFrameRate());
        assertEquals(5000000, videoMetadata.getVideoBitrate());
        assertEquals(128000, videoMetadata.getAudioBitrate());
    }

    // Edge cases
    @Test
    void testBuilder_NegativeValues_Accepted() {
        VideoMetadata metadata = VideoMetadata.builder()
                .width(-1)
                .height(-1)
                .frameRate(-1.0)
                .videoBitrate(-1)
                .audioBitrate(-1)
                .build();

        assertEquals(-1, metadata.getWidth());
        assertEquals(-1, metadata.getHeight());
        assertEquals(-1.0, metadata.getFrameRate());
        assertEquals(-1, metadata.getVideoBitrate());
        assertEquals(-1, metadata.getAudioBitrate());
    }

    @Test
    void testIsValid_BlankAudioCodec_ReturnsTrue() {
        VideoMetadata metadata = VideoMetadata.builder()
                .duration(Duration.ofSeconds(60))
                .width(1920)
                .height(1080)
                .videoCodec("h264")
                .audioCodec("")
                .frameRate(30.0)
                .videoBitrate(5000000)
                .audioBitrate(128000)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testGetAspectRatio_NegativeHeight_ReturnsZero() {
        VideoMetadata metadata = VideoMetadata.builder()
                .width(1920)
                .height(-1080)
                .build();

        assertEquals(0.0, metadata.getAspectRatio());
    }
}