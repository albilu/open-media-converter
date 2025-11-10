package org.omc.model;

import org.omc.model.AudioMetadata;
import org.omc.model.FormatCategory;
import org.omc.model.MediaMetadata;
import org.omc.model.DocumentMetadata;
import org.omc.model.VideoMetadata;
import org.omc.model.ImageMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MediaMetadata interface and polymorphic JSON handling.
 * Tests the contract defined by the MediaMetadata interface and JSON type
 * discrimination.
 * Requirement REQ-002.2: File metadata extraction for conversion files.
 */
@DisplayName("MediaMetadata Tests")
class MediaMetadataTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // ==================== Interface Contract Tests ====================

    @Nested
    @DisplayName("Interface Contract Tests")
    class InterfaceContractTests {

        @Test
        @DisplayName("VideoMetadata implements MediaMetadata interface")
        void videoMetadata_ImplementsInterface() {
            MediaMetadata metadata = VideoMetadata.builder()
                    .duration(Duration.ofSeconds(120))
                    .width(1920)
                    .height(1080)
                    .videoCodec("h264")
                    .frameRate(30.0)
                    .build();

            assertEquals(FormatCategory.VIDEO, metadata.getCategory());
            assertTrue(metadata.isValid());
            assertNotNull(metadata.getSummary());
            assertTrue(metadata.getSummary().contains("1920x1080"));
        }

        @Test
        @DisplayName("AudioMetadata implements MediaMetadata interface")
        void audioMetadata_ImplementsInterface() {
            MediaMetadata metadata = AudioMetadata.builder()
                    .duration(Duration.ofSeconds(180))
                    .codec("aac")
                    .bitrate(128000)
                    .sampleRate(44100)
                    .channels(2)
                    .build();

            assertEquals(FormatCategory.AUDIO, metadata.getCategory());
            assertTrue(metadata.isValid());
            assertNotNull(metadata.getSummary());
            assertTrue(metadata.getSummary().contains("44.1 kHz"));
        }

        @Test
        @DisplayName("ImageMetadata implements MediaMetadata interface")
        void imageMetadata_ImplementsInterface() {
            MediaMetadata metadata = ImageMetadata.builder()
                    .width(1920)
                    .height(1080)
                    .colorSpace("RGB")
                    .bitDepth(8)
                    .build();

            assertEquals(FormatCategory.IMAGE, metadata.getCategory());
            assertTrue(metadata.isValid());
            assertNotNull(metadata.getSummary());
            assertTrue(metadata.getSummary().contains("1920x1080"));
        }

        @Test
        @DisplayName("DocumentMetadata implements MediaMetadata interface")
        void documentMetadata_ImplementsInterface() {
            MediaMetadata metadata = DocumentMetadata.builder()
                    .pageCount(10)
                    .author("John Doe")
                    .title("Test Document")
                    .build();

            assertEquals(FormatCategory.DOCUMENT, metadata.getCategory());
            assertTrue(metadata.isValid());
            assertNotNull(metadata.getSummary());
            assertTrue(metadata.getSummary().contains("10 page"));
        }
    }

    // ==================== Polymorphic JSON Serialization Tests
    // ====================

    @Nested
    @DisplayName("Polymorphic JSON Serialization Tests")
    class PolymorphicSerializationTests {

        @Test
        @DisplayName("VideoMetadata serializes with type discriminator")
        void videoMetadata_SerializesWithTypeDiscriminator() throws Exception {
            MediaMetadata metadata = VideoMetadata.builder()
                    .duration(Duration.ofSeconds(120))
                    .width(1920)
                    .height(1080)
                    .videoCodec("h264")
                    .frameRate(30.0)
                    .build();

            String json = objectMapper.writeValueAsString(metadata);

            assertNotNull(json);
            assertTrue(json.contains("\"type\":\"video\""));
            assertTrue(json.contains("\"width\":1920"));
            assertTrue(json.contains("\"height\":1080"));
        }

        @Test
        @DisplayName("AudioMetadata serializes with type discriminator")
        void audioMetadata_SerializesWithTypeDiscriminator() throws Exception {
            MediaMetadata metadata = AudioMetadata.builder()
                    .duration(Duration.ofSeconds(180))
                    .codec("aac")
                    .bitrate(128000)
                    .sampleRate(44100)
                    .channels(2)
                    .build();

            String json = objectMapper.writeValueAsString(metadata);

            assertNotNull(json);
            assertTrue(json.contains("\"type\":\"audio\""));
            assertTrue(json.contains("\"codec\":\"aac\""));
            assertTrue(json.contains("\"sampleRate\":44100"));
        }

        @Test
        @DisplayName("ImageMetadata serializes with type discriminator")
        void imageMetadata_SerializesWithTypeDiscriminator() throws Exception {
            MediaMetadata metadata = ImageMetadata.builder()
                    .width(1920)
                    .height(1080)
                    .colorSpace("RGB")
                    .bitDepth(8)
                    .build();

            String json = objectMapper.writeValueAsString(metadata);

            assertNotNull(json);
            assertTrue(json.contains("\"type\":\"image\""));
            assertTrue(json.contains("\"width\":1920"));
            assertTrue(json.contains("\"colorSpace\":\"RGB\""));
        }

        @Test
        @DisplayName("DocumentMetadata serializes with type discriminator")
        void documentMetadata_SerializesWithTypeDiscriminator() throws Exception {
            MediaMetadata metadata = DocumentMetadata.builder()
                    .pageCount(10)
                    .author("John Doe")
                    .title("Test Document")
                    .build();

            String json = objectMapper.writeValueAsString(metadata);

            assertNotNull(json);
            assertTrue(json.contains("\"type\":\"document\""));
            assertTrue(json.contains("\"pageCount\":10"));
            assertTrue(json.contains("\"author\":\"John Doe\""));
        }
    }

    // ==================== Polymorphic JSON Deserialization Tests
    // ====================

    @Nested
    @DisplayName("Polymorphic JSON Deserialization Tests")
    class PolymorphicDeserializationTests {

        @Test
        @DisplayName("VideoMetadata deserializes from JSON with type discriminator")
        void videoMetadata_DeserializesFromJson() throws Exception {
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

            assertNotNull(metadata);
            assertInstanceOf(VideoMetadata.class, metadata);
            assertEquals(FormatCategory.VIDEO, metadata.getCategory());

            VideoMetadata videoMetadata = (VideoMetadata) metadata;
            assertEquals(1920, videoMetadata.getWidth());
            assertEquals(1080, videoMetadata.getHeight());
            assertEquals("h264", videoMetadata.getVideoCodec());
        }

        @Test
        @DisplayName("AudioMetadata deserializes from JSON with type discriminator")
        void audioMetadata_DeserializesFromJson() throws Exception {
            String json = """
                    {
                        "type": "audio",
                        "duration": 180.0,
                        "codec": "aac",
                        "bitrate": 128000,
                        "sampleRate": 44100,
                        "channels": 2
                    }
                    """;

            MediaMetadata metadata = objectMapper.readValue(json, MediaMetadata.class);

            assertNotNull(metadata);
            assertInstanceOf(AudioMetadata.class, metadata);
            assertEquals(FormatCategory.AUDIO, metadata.getCategory());

            AudioMetadata audioMetadata = (AudioMetadata) metadata;
            assertEquals("aac", audioMetadata.getCodec());
            assertEquals(44100, audioMetadata.getSampleRate());
        }

        @Test
        @DisplayName("ImageMetadata deserializes from JSON with type discriminator")
        void imageMetadata_DeserializesFromJson() throws Exception {
            String json = """
                    {
                        "type": "image",
                        "width": 1920,
                        "height": 1080,
                        "colorSpace": "RGB",
                        "bitDepth": 8,
                        "hasAlpha": false
                    }
                    """;

            MediaMetadata metadata = objectMapper.readValue(json, MediaMetadata.class);

            assertNotNull(metadata);
            assertInstanceOf(ImageMetadata.class, metadata);
            assertEquals(FormatCategory.IMAGE, metadata.getCategory());

            ImageMetadata imageMetadata = (ImageMetadata) metadata;
            assertEquals(1920, imageMetadata.getWidth());
            assertEquals("RGB", imageMetadata.getColorSpace());
        }

        @Test
        @DisplayName("DocumentMetadata deserializes from JSON with type discriminator")
        void documentMetadata_DeserializesFromJson() throws Exception {
            String json = """
                    {
                        "type": "document",
                        "pageCount": 10,
                        "author": "John Doe",
                        "title": "Test Document",
                        "subject": "Testing",
                        "creator": "Test Creator"
                    }
                    """;

            MediaMetadata metadata = objectMapper.readValue(json, MediaMetadata.class);

            assertNotNull(metadata);
            assertInstanceOf(DocumentMetadata.class, metadata);
            assertEquals(FormatCategory.DOCUMENT, metadata.getCategory());

            DocumentMetadata documentMetadata = (DocumentMetadata) metadata;
            assertEquals(10, documentMetadata.getPageCount());
            assertEquals("John Doe", documentMetadata.getAuthor());
        }
    }

    // ==================== Roundtrip JSON Tests ====================

    @Nested
    @DisplayName("Roundtrip JSON Tests")
    class RoundtripJsonTests {

        @Test
        @DisplayName("VideoMetadata roundtrip preserves data")
        void videoMetadata_RoundtripPreservesData() throws Exception {
            MediaMetadata original = VideoMetadata.builder()
                    .duration(Duration.ofSeconds(120))
                    .width(1920)
                    .height(1080)
                    .videoCodec("h264")
                    .audioCodec("aac")
                    .frameRate(30.0)
                    .videoBitrate(5000000)
                    .audioBitrate(128000)
                    .build();

            String json = objectMapper.writeValueAsString(original);
            MediaMetadata deserialized = objectMapper.readValue(json, MediaMetadata.class);

            assertNotNull(deserialized);
            assertInstanceOf(VideoMetadata.class, deserialized);
            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("AudioMetadata roundtrip preserves data")
        void audioMetadata_RoundtripPreservesData() throws Exception {
            MediaMetadata original = AudioMetadata.builder()
                    .duration(Duration.ofSeconds(180))
                    .codec("aac")
                    .bitrate(128000)
                    .sampleRate(44100)
                    .channels(2)
                    .build();

            String json = objectMapper.writeValueAsString(original);
            MediaMetadata deserialized = objectMapper.readValue(json, MediaMetadata.class);

            assertNotNull(deserialized);
            assertInstanceOf(AudioMetadata.class, deserialized);
            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("ImageMetadata roundtrip preserves data")
        void imageMetadata_RoundtripPreservesData() throws Exception {
            MediaMetadata original = ImageMetadata.builder()
                    .width(1920)
                    .height(1080)
                    .colorSpace("RGB")
                    .bitDepth(8)
                    .hasAlpha(false)
                    .build();

            String json = objectMapper.writeValueAsString(original);
            MediaMetadata deserialized = objectMapper.readValue(json, MediaMetadata.class);

            assertNotNull(deserialized);
            assertInstanceOf(ImageMetadata.class, deserialized);
            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("DocumentMetadata roundtrip preserves data")
        void documentMetadata_RoundtripPreservesData() throws Exception {
            MediaMetadata original = DocumentMetadata.builder()
                    .pageCount(10)
                    .author("John Doe")
                    .title("Test Document")
                    .subject("Testing")
                    .creator("Test Creator")
                    .build();

            String json = objectMapper.writeValueAsString(original);
            MediaMetadata deserialized = objectMapper.readValue(json, MediaMetadata.class);

            assertNotNull(deserialized);
            assertInstanceOf(DocumentMetadata.class, deserialized);
            assertEquals(original, deserialized);
        }
    }

    // ==================== Type Discrimination Error Handling ====================

    @Nested
    @DisplayName("Type Discrimination Error Handling")
    class TypeDiscriminationErrorHandling {

        @Test
        @DisplayName("Missing type discriminator throws exception")
        void missingTypeDiscriminator_ThrowsException() {
            String json = """
                    {
                        "width": 1920,
                        "height": 1080,
                        "colorSpace": "RGB"
                    }
                    """;

            assertThrows(Exception.class, () -> {
                objectMapper.readValue(json, MediaMetadata.class);
            });
        }

        @Test
        @DisplayName("Invalid type discriminator throws exception")
        void invalidTypeDiscriminator_ThrowsException() {
            String json = """
                    {
                        "type": "unknown",
                        "width": 1920,
                        "height": 1080
                    }
                    """;

            assertThrows(Exception.class, () -> {
                objectMapper.readValue(json, MediaMetadata.class);
            });
        }

        @Test
        @DisplayName("Null type discriminator throws exception")
        void nullTypeDiscriminator_ThrowsException() {
            String json = """
                    {
                        "type": null,
                        "width": 1920,
                        "height": 1080
                    }
                    """;

            assertThrows(Exception.class, () -> {
                objectMapper.readValue(json, MediaMetadata.class);
            });
        }
    }

    // ==================== Validation Tests ====================

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Valid VideoMetadata returns true for isValid()")
        void validVideoMetadata_ReturnsTrue() {
            MediaMetadata metadata = VideoMetadata.builder()
                    .duration(Duration.ofSeconds(120))
                    .width(1920)
                    .height(1080)
                    .videoCodec("h264")
                    .frameRate(30.0)
                    .build();

            assertTrue(metadata.isValid());
        }

        @Test
        @DisplayName("Invalid VideoMetadata returns false for isValid()")
        void invalidVideoMetadata_ReturnsFalse() {
            MediaMetadata metadata = VideoMetadata.builder()
                    .width(0)
                    .height(0)
                    .build();

            assertFalse(metadata.isValid());
        }

        @Test
        @DisplayName("Valid AudioMetadata returns true for isValid()")
        void validAudioMetadata_ReturnsTrue() {
            MediaMetadata metadata = AudioMetadata.builder()
                    .duration(Duration.ofSeconds(180))
                    .codec("aac")
                    .sampleRate(44100)
                    .channels(2)
                    .build();

            assertTrue(metadata.isValid());
        }

        @Test
        @DisplayName("Invalid AudioMetadata returns false for isValid()")
        void invalidAudioMetadata_ReturnsFalse() {
            MediaMetadata metadata = AudioMetadata.builder()
                    .sampleRate(0)
                    .channels(0)
                    .build();

            assertFalse(metadata.isValid());
        }

        @Test
        @DisplayName("Valid ImageMetadata returns true for isValid()")
        void validImageMetadata_ReturnsTrue() {
            MediaMetadata metadata = ImageMetadata.builder()
                    .width(1920)
                    .height(1080)
                    .colorSpace("RGB")
                    .bitDepth(8)
                    .build();

            assertTrue(metadata.isValid());
        }

        @Test
        @DisplayName("Invalid ImageMetadata returns false for isValid()")
        void invalidImageMetadata_ReturnsFalse() {
            MediaMetadata metadata = ImageMetadata.builder()
                    .width(0)
                    .height(0)
                    .build();

            assertFalse(metadata.isValid());
        }

        @Test
        @DisplayName("Valid DocumentMetadata returns true for isValid()")
        void validDocumentMetadata_ReturnsTrue() {
            MediaMetadata metadata = DocumentMetadata.builder()
                    .pageCount(10)
                    .title("Test Document")
                    .build();

            assertTrue(metadata.isValid());
        }

        @Test
        @DisplayName("Invalid DocumentMetadata returns false for isValid()")
        void invalidDocumentMetadata_ReturnsFalse() {
            MediaMetadata metadata = DocumentMetadata.builder()
                    .pageCount(-1)
                    .build();

            assertFalse(metadata.isValid());
        }
    }

    // ==================== Summary Tests ====================

    @Nested
    @DisplayName("Summary Tests")
    class SummaryTests {

        @Test
        @DisplayName("VideoMetadata getSummary returns meaningful string")
        void videoMetadata_GetSummaryReturnsMeaningfulString() {
            MediaMetadata metadata = VideoMetadata.builder()
                    .duration(Duration.ofSeconds(120))
                    .width(1920)
                    .height(1080)
                    .videoCodec("h264")
                    .frameRate(30.0)
                    .build();

            String summary = metadata.getSummary();

            assertNotNull(summary);
            assertFalse(summary.isEmpty());
            assertTrue(summary.contains("1920x1080") || summary.contains("1920") && summary.contains("1080"));
        }

        @Test
        @DisplayName("AudioMetadata getSummary returns meaningful string")
        void audioMetadata_GetSummaryReturnsMeaningfulString() {
            MediaMetadata metadata = AudioMetadata.builder()
                    .duration(Duration.ofSeconds(180))
                    .codec("aac")
                    .sampleRate(44100)
                    .channels(2)
                    .build();

            String summary = metadata.getSummary();

            assertNotNull(summary);
            assertFalse(summary.isEmpty());
            assertTrue(summary.contains("44.1 kHz") || summary.contains("44100"));
        }

        @Test
        @DisplayName("ImageMetadata getSummary returns meaningful string")
        void imageMetadata_GetSummaryReturnsMeaningfulString() {
            MediaMetadata metadata = ImageMetadata.builder()
                    .width(1920)
                    .height(1080)
                    .colorSpace("RGB")
                    .bitDepth(8)
                    .build();

            String summary = metadata.getSummary();

            assertNotNull(summary);
            assertFalse(summary.isEmpty());
            assertTrue(summary.contains("1920x1080") || summary.contains("1920") && summary.contains("1080"));
        }

        @Test
        @DisplayName("DocumentMetadata getSummary returns meaningful string")
        void documentMetadata_GetSummaryReturnsMeaningfulString() {
            MediaMetadata metadata = DocumentMetadata.builder()
                    .pageCount(10)
                    .author("John Doe")
                    .title("Test Document")
                    .build();

            String summary = metadata.getSummary();

            assertNotNull(summary);
            assertFalse(summary.isEmpty());
            assertTrue(summary.contains("10") || summary.contains("page"));
        }
    }
}
