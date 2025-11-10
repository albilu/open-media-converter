package org.omc.model;

import org.omc.model.FormatCategory;
import org.omc.model.AudioMetadata;
import org.omc.model.MediaMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AudioMetadata class.
 */
class AudioMetadataTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // Builder tests
    @Test
    void testBuilder_AllFieldsSet_BuildsCorrectly() {
        Duration duration = Duration.ofSeconds(180);
        AudioMetadata metadata = AudioMetadata.builder()
                .duration(duration)
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        assertEquals(duration, metadata.getDuration());
        assertEquals("flac", metadata.getCodec());
        assertEquals(1411000, metadata.getBitrate());
        assertEquals(44100, metadata.getSampleRate());
        assertEquals(2, metadata.getChannels());
    }

    @Test
    void testBuilder_DefaultValues_BuildsWithDefaults() {
        AudioMetadata metadata = AudioMetadata.builder().build();

        assertNull(metadata.getDuration());
        assertNull(metadata.getCodec());
        assertEquals(0, metadata.getBitrate());
        assertEquals(0, metadata.getSampleRate());
        assertEquals(0, metadata.getChannels());
    }

    // Validation tests
    @Test
    void testIsValid_AllFieldsValid_ReturnsTrue() {
        AudioMetadata metadata = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        assertTrue(metadata.isValid());
    }

    @Test
    void testIsValid_NullDuration_ReturnsFalse() {
        AudioMetadata metadata = AudioMetadata.builder()
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_ZeroDuration_ReturnsFalse() {
        AudioMetadata metadata = AudioMetadata.builder()
                .duration(Duration.ZERO)
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_NegativeDuration_ReturnsFalse() {
        AudioMetadata metadata = AudioMetadata.builder()
                .duration(Duration.ofSeconds(-1))
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_NullCodec_ReturnsFalse() {
        AudioMetadata metadata = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_BlankCodec_ReturnsFalse() {
        AudioMetadata metadata = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .codec("")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_NegativeBitrate_ReturnsFalse() {
        AudioMetadata metadata = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .codec("flac")
                .bitrate(-1)
                .sampleRate(44100)
                .channels(2)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_ZeroSampleRate_ReturnsFalse() {
        AudioMetadata metadata = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(0)
                .channels(2)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_ZeroChannels_ReturnsFalse() {
        AudioMetadata metadata = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(0)
                .build();

        assertFalse(metadata.isValid());
    }

    // Helper methods tests
    @Test
    void testGetChannelDescription_Mono_ReturnsMono() {
        AudioMetadata metadata = AudioMetadata.builder()
                .channels(1)
                .build();

        assertEquals("mono", metadata.getChannelDescription());
    }

    @Test
    void testGetChannelDescription_Stereo_ReturnsStereo() {
        AudioMetadata metadata = AudioMetadata.builder()
                .channels(2)
                .build();

        assertEquals("stereo", metadata.getChannelDescription());
    }

    @Test
    void testGetChannelDescription_5_1_Returns51() {
        AudioMetadata metadata = AudioMetadata.builder()
                .channels(6)
                .build();

        assertEquals("5.1", metadata.getChannelDescription());
    }

    @Test
    void testGetChannelDescription_7_1_Returns71() {
        AudioMetadata metadata = AudioMetadata.builder()
                .channels(8)
                .build();

        assertEquals("7.1", metadata.getChannelDescription());
    }

    @Test
    void testGetChannelDescription_Other_ReturnsChannels() {
        AudioMetadata metadata = AudioMetadata.builder()
                .channels(4)
                .build();

        assertEquals("4 channels", metadata.getChannelDescription());
    }

    @Test
    void testIsHighQuality_HighBitrate_ReturnsTrue() {
        AudioMetadata metadata = AudioMetadata.builder()
                .bitrate(320000)
                .build();

        assertTrue(metadata.isHighQuality());
    }

    @Test
    void testIsHighQuality_LowBitrate_ReturnsFalse() {
        AudioMetadata metadata = AudioMetadata.builder()
                .bitrate(128000)
                .build();

        assertFalse(metadata.isHighQuality());
    }

    @Test
    void testIsHighQuality_LosslessCodec_ReturnsTrue() {
        AudioMetadata metadata = AudioMetadata.builder()
                .codec("flac")
                .bitrate(128000)
                .build();

        assertTrue(metadata.isHighQuality());
    }

    @Test
    void testIsLossless_Flac_ReturnsTrue() {
        AudioMetadata metadata = AudioMetadata.builder()
                .codec("flac")
                .build();

        assertTrue(metadata.isLossless());
    }

    @Test
    void testIsLossless_Alac_ReturnsTrue() {
        AudioMetadata metadata = AudioMetadata.builder()
                .codec("alac")
                .build();

        assertTrue(metadata.isLossless());
    }

    @Test
    void testIsLossless_Wav_ReturnsTrue() {
        AudioMetadata metadata = AudioMetadata.builder()
                .codec("wav")
                .build();

        assertTrue(metadata.isLossless());
    }

    @Test
    void testIsLossless_Pcm_ReturnsTrue() {
        AudioMetadata metadata = AudioMetadata.builder()
                .codec("pcm")
                .build();

        assertTrue(metadata.isLossless());
    }

    @Test
    void testIsLossless_Mp3_ReturnsFalse() {
        AudioMetadata metadata = AudioMetadata.builder()
                .codec("mp3")
                .build();

        assertFalse(metadata.isLossless());
    }

    @Test
    void testIsLossless_NullCodec_ReturnsFalse() {
        AudioMetadata metadata = AudioMetadata.builder()
                .codec(null)
                .build();

        assertFalse(metadata.isLossless());
    }

    // getSummary tests
    @Test
    void testGetSummary_ValidMetadata_ReturnsFormattedString() {
        AudioMetadata metadata = AudioMetadata.builder()
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .duration(Duration.ofSeconds(183)) // 3:03
                .build();

        String summary = metadata.getSummary();
        assertTrue(summary.contains("flac"));
        assertTrue(summary.contains("1411 kbps"));
        assertTrue(summary.contains("44.1 kHz"));
        assertTrue(summary.contains("stereo"));
        assertTrue(summary.contains("3:03"));
    }

    @Test
    void testGetSummary_NullDuration_ReturnsUnknown() {
        AudioMetadata metadata = AudioMetadata.builder()
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        String summary = metadata.getSummary();
        assertTrue(summary.contains("unknown"));
    }

    // getCategory tests
    @Test
    void testGetCategory_ReturnsAudio() {
        AudioMetadata metadata = AudioMetadata.builder().build();

        assertEquals(FormatCategory.AUDIO, metadata.getCategory());
    }

    // equals/hashCode/toString tests
    @Test
    void testEquals_SameObject_ReturnsTrue() {
        AudioMetadata metadata = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        assertEquals(metadata, metadata);
    }

    @Test
    void testEquals_EqualObjects_ReturnsTrue() {
        AudioMetadata metadata1 = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        AudioMetadata metadata2 = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        assertEquals(metadata1, metadata2);
    }

    @Test
    void testEquals_DifferentObjects_ReturnsFalse() {
        AudioMetadata metadata1 = AudioMetadata.builder()
                .codec("flac")
                .build();

        AudioMetadata metadata2 = AudioMetadata.builder()
                .codec("mp3")
                .build();

        assertNotEquals(metadata1, metadata2);
    }

    @Test
    void testEquals_Null_ReturnsFalse() {
        AudioMetadata metadata = AudioMetadata.builder().build();

        assertNotEquals(null, metadata);
    }

    @Test
    void testEquals_DifferentClass_ReturnsFalse() {
        AudioMetadata metadata = AudioMetadata.builder().build();

        assertNotEquals("string", metadata);
    }

    @Test
    void testHashCode_EqualObjects_SameHashCode() {
        AudioMetadata metadata1 = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        AudioMetadata metadata2 = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        assertEquals(metadata1.hashCode(), metadata2.hashCode());
    }

    @Test
    void testToString_ContainsAllFields() {
        AudioMetadata metadata = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        String toString = metadata.toString();
        assertTrue(toString.contains("AudioMetadata"));
        assertTrue(toString.contains("duration=PT3M"));
        assertTrue(toString.contains("codec='flac'"));
        assertTrue(toString.contains("bitrate=1411000"));
        assertTrue(toString.contains("sampleRate=44100"));
        assertTrue(toString.contains("channels=2"));
    }

    // JSON serialization tests
    @Test
    void testJsonSerialization_SerializesCorrectly() throws Exception {
        AudioMetadata metadata = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(2)
                .build();

        String json = objectMapper.writeValueAsString(metadata);

        assertTrue(json.contains("\"type\":\"audio\""));
        assertTrue(json.contains("\"duration\":180"));
        assertTrue(json.contains("\"codec\":\"flac\""));
        assertTrue(json.contains("\"bitrate\":1411000"));
        assertTrue(json.contains("\"sampleRate\":44100"));
        assertTrue(json.contains("\"channels\":2"));
    }

    @Test
    void testJsonDeserialization_DeserializesCorrectly() throws Exception {
        String json = """
                {
                    "type": "audio",
                    "duration": 180.0,
                    "codec": "flac",
                    "bitrate": 1411000,
                    "sampleRate": 44100,
                    "channels": 2
                }
                """;

        MediaMetadata metadata = objectMapper.readValue(json, MediaMetadata.class);

        assertInstanceOf(AudioMetadata.class, metadata);
        AudioMetadata audioMetadata = (AudioMetadata) metadata;
        assertEquals(Duration.ofSeconds(180), audioMetadata.getDuration());
        assertEquals("flac", audioMetadata.getCodec());
        assertEquals(1411000, audioMetadata.getBitrate());
        assertEquals(44100, audioMetadata.getSampleRate());
        assertEquals(2, audioMetadata.getChannels());
    }

    // Edge cases
    @Test
    void testBuilder_NegativeValues_Accepted() {
        AudioMetadata metadata = AudioMetadata.builder()
                .bitrate(-1)
                .sampleRate(-1)
                .channels(-1)
                .build();

        assertEquals(-1, metadata.getBitrate());
        assertEquals(-1, metadata.getSampleRate());
        assertEquals(-1, metadata.getChannels());
    }

    @Test
    void testIsValid_NegativeChannels_ReturnsFalse() {
        AudioMetadata metadata = AudioMetadata.builder()
                .duration(Duration.ofSeconds(180))
                .codec("flac")
                .bitrate(1411000)
                .sampleRate(44100)
                .channels(-1)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testGetChannelDescription_NegativeChannels_ReturnsChannels() {
        AudioMetadata metadata = AudioMetadata.builder()
                .channels(-1)
                .build();

        assertEquals("-1 channels", metadata.getChannelDescription());
    }
}