package org.omc.model;

import org.omc.model.AudioSettings;
import org.omc.model.FileFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AudioSettings class.
 * Covers builder pattern, validation, serialization, equals/hashCode, and
 * toString.
 */
class AudioSettingsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void builder_WithDefaults_ShouldSetDefaultValues() {
        // Given: Default builder
        AudioSettings settings = AudioSettings.builder().build();

        // Then: Check all default values
        assertEquals("libmp3lame", settings.codec());
        assertEquals(192, settings.bitrate());
        assertEquals(-1, settings.sampleRate());
        assertEquals(-1, settings.channels());
        assertEquals(5, settings.quality());
        assertEquals(FileFormat.MP3, settings.outputFormat());
    }

    @Test
    void builder_WithCustomOutputFormat_ShouldSetCustomValue() {
        // Given: Builder with custom outputFormat
        AudioSettings settings = AudioSettings.builder()
                .outputFormat(FileFormat.AAC)
                .build();

        // Then: outputFormat should be AAC, others default
        assertEquals(FileFormat.AAC, settings.outputFormat());
        assertEquals("aac", settings.codec());
        assertEquals(192, settings.bitrate());
        assertEquals(-1, settings.sampleRate());
        assertEquals(-1, settings.channels());
        assertEquals(5, settings.quality());
    }

    @Test
    void isValid_WithValidAudioOutputFormat_ShouldReturnTrue() {
        // Given: Valid AUDIO format
        AudioSettings settings = AudioSettings.builder()
                .outputFormat(FileFormat.MP3)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithInvalidVideoOutputFormat_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> AudioSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build());
    }

    @Test
    void isValid_WithInvalidImageOutputFormat_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> AudioSettings.builder()
                .outputFormat(FileFormat.PNG)
                .build());
    }

    @Test
    void isValid_WithInvalidDocumentOutputFormat_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> AudioSettings.builder()
                .outputFormat(FileFormat.PDF)
                .build());
    }

    @Test
    void isValid_WithNullOutputFormat_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> AudioSettings.builder()
                .outputFormat(null)
                .build());
    }

    @Test
    void isValid_WithBitrateTooLow_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> AudioSettings.builder()
                .bitrate(63)
                .build());
    }

    @Test
    void isValid_WithBitrateTooHigh_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> AudioSettings.builder()
                .bitrate(321)
                .build());
    }

    @Test
    void isValid_WithInvalidSampleRate_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> AudioSettings.builder()
                .sampleRate(12345)
                .build());
    }

    @Test
    void isValid_WithValidSampleRate_ShouldReturnTrue() {
        // Given: Valid sample rate
        AudioSettings settings = AudioSettings.builder()
                .sampleRate(44100)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithOriginalSampleRate_ShouldReturnTrue() {
        // Given: Original sample rate (-1)
        AudioSettings settings = AudioSettings.builder()
                .sampleRate(-1)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithInvalidChannels_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> AudioSettings.builder()
                .channels(3)
                .build());
    }

    @Test
    void isValid_WithValidChannels_ShouldReturnTrue() {
        // Given: Valid channels (e.g., 1 for mono)
        AudioSettings settings = AudioSettings.builder()
                .channels(1)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithOriginalChannels_ShouldReturnTrue() {
        // Given: Original channels (-1)
        AudioSettings settings = AudioSettings.builder()
                .channels(-1)
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
    }

    @Test
    void isValid_WithQualityTooLow_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> AudioSettings.builder()
                .quality(-1)
                .build());
    }

    @Test
    void isValid_WithQualityTooHigh_ShouldReturnFalse() {
        // Since builder validates, this throws exception
        assertThrows(IllegalArgumentException.class, () -> AudioSettings.builder()
                .quality(10)
                .build());
    }

    @Test
    void jsonSerialization_WithOutputFormat_ShouldPreserveOutputFormat() throws Exception {
        // Given: AudioSettings with custom outputFormat
        AudioSettings original = AudioSettings.builder()
                .outputFormat(FileFormat.WAV)
                .build();

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        AudioSettings deserialized = objectMapper.readValue(json, AudioSettings.class);

        // Then: outputFormat should be preserved
        assertEquals(original.outputFormat(), deserialized.outputFormat());
        assertEquals(FileFormat.WAV, deserialized.outputFormat());
    }

    @Test
    void jsonSerialization_WithAllFields_ShouldPreserveAllFields() throws Exception {
        // Given: AudioSettings with all fields set
        AudioSettings original = AudioSettings.builder()
                .codec("mp3")
                .bitrate(256)
                .sampleRate(48000)
                .channels(2)
                .quality(3)
                .outputFormat(FileFormat.OGG)
                .build();

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        AudioSettings deserialized = objectMapper.readValue(json, AudioSettings.class);

        // Then: All fields should be preserved
        assertEquals(original, deserialized);
    }

    @Test
    void equals_WithSameOutputFormat_ShouldReturnTrue() {
        // Given: Two identical AudioSettings
        AudioSettings settings1 = AudioSettings.builder()
                .outputFormat(FileFormat.FLAC)
                .build();
        AudioSettings settings2 = AudioSettings.builder()
                .outputFormat(FileFormat.FLAC)
                .build();

        // Then: Should be equal
        assertEquals(settings1, settings2);
    }

    @Test
    void equals_WithDifferentOutputFormat_ShouldReturnFalse() {
        // Given: Two AudioSettings with different outputFormat
        AudioSettings settings1 = AudioSettings.builder()
                .outputFormat(FileFormat.MP3)
                .build();
        AudioSettings settings2 = AudioSettings.builder()
                .outputFormat(FileFormat.AAC)
                .build();

        // Then: Should not be equal
        assertNotEquals(settings1, settings2);
    }

    @Test
    void hashCode_WithSameOutputFormat_ShouldBeEqual() {
        // Given: Two identical AudioSettings
        AudioSettings settings1 = AudioSettings.builder()
                .outputFormat(FileFormat.WAV)
                .build();
        AudioSettings settings2 = AudioSettings.builder()
                .outputFormat(FileFormat.WAV)
                .build();

        // Then: Hash codes should be equal
        assertEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void hashCode_WithDifferentOutputFormat_ShouldBeDifferent() {
        // Given: Two AudioSettings with different outputFormat
        AudioSettings settings1 = AudioSettings.builder()
                .outputFormat(FileFormat.MP3)
                .build();
        AudioSettings settings2 = AudioSettings.builder()
                .outputFormat(FileFormat.OGG)
                .build();

        // Then: Hash codes should be different
        assertNotEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void toString_ShouldIncludeOutputFormat() {
        // Given: AudioSettings with custom outputFormat
        AudioSettings settings = AudioSettings.builder()
                .outputFormat(FileFormat.AAC)
                .build();

        // When: Get string representation
        String toString = settings.toString();

        // Then: Should include outputFormat
        assertTrue(toString.contains("outputFormat=AAC"));
        assertTrue(toString.contains("AudioSettings{"));
        assertTrue(toString.contains("codec='aac'"));
        assertTrue(toString.contains("bitrate=192"));
        assertTrue(toString.contains("sampleRate=-1"));
        assertTrue(toString.contains("channels=-1"));
        assertTrue(toString.contains("quality=5"));
    }

    // ========== Copy Codec Tests (REQ-AUD-1.1) ==========

    @Test
    void isValid_WithCopyCodec_ShouldReturnTrue() {
        // Given: AudioSettings with copy codec
        AudioSettings settings = AudioSettings.builder()
                .codec("copy")
                .build();

        // Then: Should be valid
        assertTrue(settings.isValid());
        assertEquals("copy", settings.codec());
    }

    @Test
    void builder_WithCopyCodec_ShouldAcceptCopyCodec() {
        // Given/When: Build AudioSettings with copy codec
        AudioSettings settings = AudioSettings.builder()
                .codec("copy")
                .outputFormat(FileFormat.MP3)
                .build();

        // Then: Copy codec should be set
        assertTrue(settings.isValid());
        assertEquals("copy", settings.codec());
        assertEquals(FileFormat.MP3, settings.outputFormat());
    }

    @Test
    void jsonSerialization_WithCopyCodec_ShouldPreserveCopyCodec() throws Exception {
        // Given: AudioSettings with copy codec
        AudioSettings original = AudioSettings.builder()
                .codec("copy")
                .bitrate(192)
                .outputFormat(FileFormat.MP3)
                .build();

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        AudioSettings deserialized = objectMapper.readValue(json, AudioSettings.class);

        // Then: Copy codec should be preserved
        assertEquals(original.codec(), deserialized.codec());
        assertEquals("copy", deserialized.codec());
    }

    @Test
    void equals_WithDifferentCodec_CopyVsNonCopy_ShouldReturnFalse() {
        // Given: Two AudioSettings with different codecs (copy vs aac)
        AudioSettings settings1 = AudioSettings.builder()
                .codec("copy")
                .build();
        AudioSettings settings2 = AudioSettings.builder()
                .codec("aac")
                .build();

        // Then: Should not be equal
        assertNotEquals(settings1, settings2);
    }

    @Test
    void equals_WithSameCodec_BothCopy_ShouldReturnTrue() {
        // Given: Two identical AudioSettings with copy codec
        AudioSettings settings1 = AudioSettings.builder()
                .codec("copy")
                .outputFormat(FileFormat.AAC)
                .build();
        AudioSettings settings2 = AudioSettings.builder()
                .codec("copy")
                .outputFormat(FileFormat.AAC)
                .build();

        // Then: Should be equal
        assertEquals(settings1, settings2);
    }

    @Test
    void hashCode_WithCopyCodec_ShouldBeEqual() {
        // Given: Two identical AudioSettings with copy codec
        AudioSettings settings1 = AudioSettings.builder()
                .codec("copy")
                .build();
        AudioSettings settings2 = AudioSettings.builder()
                .codec("copy")
                .build();

        // Then: Hash codes should be equal
        assertEquals(settings1.hashCode(), settings2.hashCode());
    }

    @Test
    void toString_ShouldIncludeCopyCodec() {
        // Given: AudioSettings with copy codec
        AudioSettings settings = AudioSettings.builder()
                .codec("copy")
                .build();

        // When: Get string representation
        String toString = settings.toString();

        // Then: Should include copy codec
        assertTrue(toString.contains("codec='copy'"));
        assertTrue(toString.contains("AudioSettings{"));
    }

    @Test
    void builder_WithAllCodecs_ShouldAcceptAllValidCodecs() {
        // Given/When: Build AudioSettings with various codecs
        AudioSettings aac = AudioSettings.builder().codec("aac").build();
        AudioSettings mp3 = AudioSettings.builder().codec("mp3").build();
        AudioSettings opus = AudioSettings.builder().codec("opus").build();
        AudioSettings vorbis = AudioSettings.builder().codec("vorbis").build();
        AudioSettings flac = AudioSettings.builder().codec("flac").build();
        AudioSettings copy = AudioSettings.builder().codec("copy").build();

        // Then: All should be valid
        assertTrue(aac.isValid());
        assertTrue(mp3.isValid());
        assertTrue(opus.isValid());
        assertTrue(vorbis.isValid());
        assertTrue(flac.isValid());
        assertTrue(copy.isValid());
    }

    // ========== Missing-Field Safe Defaults (JSON deserialization) ==========

    @Test
    void jsonDeserialization_WithMinimalJson_ShouldUseBuilderDefaultsForAllMissingFields() throws Exception {
        // Given: settings/preset JSON carrying only the output format
        String json = "{\"outputFormat\":\"MP3\"}";

        // When
        AudioSettings deserialized = objectMapper.readValue(json, AudioSettings.class);

        // Then: every missing field must deserialize to its Builder default
        assertEquals("libmp3lame", deserialized.codec());
        assertEquals(192, deserialized.bitrate());
        assertEquals(-1, deserialized.sampleRate());
        assertEquals(-1, deserialized.channels());
        assertEquals(5, deserialized.quality());
        assertEquals(FileFormat.MP3, deserialized.outputFormat());
        assertTrue(deserialized.isValid(), "minimal JSON must deserialize to a valid settings object");
    }

    @Test
    void jsonDeserialization_WithEmptyObject_ShouldUseBuilderDefaultsAndKeepNullFormat() throws Exception {
        // Given: JSON with no keys at all (the builder cannot build a null
        // format; deserialization must not invent one either)
        String json = "{}";

        // When
        AudioSettings deserialized = objectMapper.readValue(json, AudioSettings.class);

        // Then: all other fields still fall back to Builder defaults
        assertEquals("libmp3lame", deserialized.codec());
        assertEquals(192, deserialized.bitrate());
        assertEquals(-1, deserialized.sampleRate());
        assertEquals(-1, deserialized.channels());
        assertEquals(5, deserialized.quality());
        assertNull(deserialized.outputFormat());
        assertFalse(deserialized.isValid(), "null outputFormat must stay invalid");
    }

    @Test
    void jsonDeserialization_WithExplicitValues_ShouldNotAlterThem() throws Exception {
        // Given: complete JSON with explicit values
        String json = "{\"codec\":\"copy\",\"bitrate\":256,\"sampleRate\":48000,"
                + "\"channels\":2,\"quality\":3,\"outputFormat\":\"OGG\"}";

        // When
        AudioSettings deserialized = objectMapper.readValue(json, AudioSettings.class);

        // Then: explicit values are preserved exactly
        assertEquals("copy", deserialized.codec());
        assertEquals(256, deserialized.bitrate());
        assertEquals(48000, deserialized.sampleRate());
        assertEquals(2, deserialized.channels());
        assertEquals(3, deserialized.quality());
        assertEquals(FileFormat.OGG, deserialized.outputFormat());
    }

    @Test
    void jsonDeserialization_WithExplicitInvalidBitrate_ShouldNotBeFixed() throws Exception {
        // Given: an explicit out-of-range bitrate (missing-key defaulting must
        // not "fix" explicit values)
        String json = "{\"bitrate\":0,\"outputFormat\":\"MP3\"}";

        // When
        AudioSettings deserialized = objectMapper.readValue(json, AudioSettings.class);

        // Then: the explicit 0 stays and remains invalid
        assertEquals(0, deserialized.bitrate());
        assertFalse(deserialized.isValid());
    }

    // ========== withOutputFormat Tests ==========

    @Test
    void withOutputFormat_ShouldPreserveAllFields() {
        // Given: AudioSettings with every field set to a non-default value
        AudioSettings original = AudioSettings.builder()
                .codec("aac")
                .bitrate(256)
                .sampleRate(48000)
                .channels(2)
                .quality(3)
                .outputFormat(FileFormat.AAC)
                .build();

        // When: Changing only the output format
        AudioSettings updated = original.withOutputFormat(FileFormat.WAV);

        // Then: All fields except the format must be preserved; the codec is
        // rewritten for container compatibility exactly like Builder.outputFormat
        // (aac in a WAV container becomes pcm_s16le)
        assertEquals(FileFormat.WAV, updated.outputFormat());
        assertEquals("pcm_s16le", updated.codec());
        assertEquals(256, updated.bitrate());
        assertEquals(48000, updated.sampleRate());
        assertEquals(2, updated.channels());
        assertEquals(3, updated.quality());
    }

    @Test
    void withOutputFormat_WithContainerCompatibleCodec_ShouldKeepCodec() {
        // Given: Codec already compatible with the target container
        AudioSettings original = AudioSettings.builder()
                .codec("flac")
                .outputFormat(FileFormat.FLAC)
                .build();

        // When: Re-selecting a container the codec fits
        AudioSettings updated = original.withOutputFormat(FileFormat.FLAC);

        // Then: Codec is kept unchanged
        assertEquals("flac", updated.codec());
        assertEquals(FileFormat.FLAC, updated.outputFormat());
    }

    @Test
    void withOutputFormat_WithCopyCodec_ShouldRetainCopyCodec() {
        // Given: Stream-copy codec (compatibility depends on the source stream)
        AudioSettings original = AudioSettings.builder()
                .codec("copy")
                .outputFormat(FileFormat.MP3)
                .build();

        // When: Changing the output format
        AudioSettings updated = original.withOutputFormat(FileFormat.OGG);

        // Then: Copy codec must survive the format change
        assertEquals("copy", updated.codec());
        assertEquals(FileFormat.OGG, updated.outputFormat());
    }
}
