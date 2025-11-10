package org.omc.model;

import org.omc.model.FormatCategory;
import org.omc.model.MediaMetadata;
import org.omc.model.ImageMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ImageMetadata class.
 */
class ImageMetadataTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Builder tests
    @Test
    void testBuilder_AllFieldsSet_BuildsCorrectly() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(24)
                .hasAlpha(true)
                .build();

        assertEquals(1920, metadata.getWidth());
        assertEquals(1080, metadata.getHeight());
        assertEquals("RGB", metadata.getColorSpace());
        assertEquals(24, metadata.getBitDepth());
        assertTrue(metadata.hasAlpha());
    }

    @Test
    void testBuilder_DefaultValues_BuildsWithDefaults() {
        ImageMetadata metadata = ImageMetadata.builder().build();

        assertEquals(0, metadata.getWidth());
        assertEquals(0, metadata.getHeight());
        assertNull(metadata.getColorSpace());
        assertEquals(0, metadata.getBitDepth());
        assertFalse(metadata.hasAlpha());
    }

    // Validation tests
    @Test
    void testIsValid_AllFieldsValid_ReturnsTrue() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(24)
                .hasAlpha(true)
                .build();

        assertTrue(metadata.isValid());
    }

    @Test
    void testIsValid_ZeroWidth_ReturnsFalse() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(0)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(24)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_ZeroHeight_ReturnsFalse() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(0)
                .colorSpace("RGB")
                .bitDepth(24)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_NullColorSpace_ReturnsFalse() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .bitDepth(24)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_BlankColorSpace_ReturnsFalse() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .colorSpace("")
                .bitDepth(24)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testIsValid_ZeroBitDepth_ReturnsFalse() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(0)
                .build();

        assertFalse(metadata.isValid());
    }

    // Helper methods tests
    @Test
    void testGetResolution_ReturnsCorrectFormat() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .build();

        assertEquals("1920x1080", metadata.getResolution());
    }

    @Test
    void testGetAspectRatio_CalculatesCorrectly() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .build();

        assertEquals(16.0 / 9.0, metadata.getAspectRatio(), 0.001);
    }

    @Test
    void testGetAspectRatio_ZeroHeight_ReturnsZero() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(0)
                .build();

        assertEquals(0.0, metadata.getAspectRatio());
    }

    @Test
    void testGetPixelCount_CalculatesCorrectly() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .build();

        assertEquals(1920L * 1080L, metadata.getPixelCount());
    }

    @Test
    void testGetMegapixels_CalculatesCorrectly() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(2000)
                .height(2000)
                .build();

        assertEquals(4.0, metadata.getMegapixels(), 0.001);
    }

    @Test
    void testIsHighResolution_Above5MP_ReturnsTrue() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(3000)
                .height(2000) // 6MP
                .build();

        assertTrue(metadata.isHighResolution());
    }

    @Test
    void testIsHighResolution_Below5MP_ReturnsFalse() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(2000)
                .height(2000) // 4MP
                .build();

        assertFalse(metadata.isHighResolution());
    }

    @Test
    void testIsHighResolution_Exactly5MP_ReturnsFalse() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(2560)
                .height(1953) // Approximately 5MP
                .build();

        // 2560 * 1953 = 4,999,680 pixels ≈ 5MP
        assertFalse(metadata.isHighResolution());
    }

    // getSummary tests
    @Test
    void testGetSummary_ValidMetadata_ReturnsFormattedString() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(24)
                .hasAlpha(true)
                .build();

        String summary = metadata.getSummary();
        assertTrue(summary.contains("1920x1080"));
        assertTrue(summary.contains("RGB"));
        assertTrue(summary.contains("24-bit"));
        assertTrue(summary.contains("alpha"));
    }

    @Test
    void testGetSummary_NoAlpha_ReturnsWithoutAlpha() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(24)
                .hasAlpha(false)
                .build();

        String summary = metadata.getSummary();
        assertFalse(summary.contains("alpha"));
    }

    // getCategory tests
    @Test
    void testGetCategory_ReturnsImage() {
        ImageMetadata metadata = ImageMetadata.builder().build();

        assertEquals(FormatCategory.IMAGE, metadata.getCategory());
    }

    // equals/hashCode/toString tests
    @Test
    void testEquals_SameObject_ReturnsTrue() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(24)
                .hasAlpha(true)
                .build();

        assertEquals(metadata, metadata);
    }

    @Test
    void testEquals_EqualObjects_ReturnsTrue() {
        ImageMetadata metadata1 = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(24)
                .hasAlpha(true)
                .build();

        ImageMetadata metadata2 = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(24)
                .hasAlpha(true)
                .build();

        assertEquals(metadata1, metadata2);
    }

    @Test
    void testEquals_DifferentObjects_ReturnsFalse() {
        ImageMetadata metadata1 = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .build();

        ImageMetadata metadata2 = ImageMetadata.builder()
                .width(1280)
                .height(720)
                .build();

        assertNotEquals(metadata1, metadata2);
    }

    @Test
    void testEquals_Null_ReturnsFalse() {
        ImageMetadata metadata = ImageMetadata.builder().build();

        assertNotEquals(null, metadata);
    }

    @Test
    void testEquals_DifferentClass_ReturnsFalse() {
        ImageMetadata metadata = ImageMetadata.builder().build();

        assertNotEquals("string", metadata);
    }

    @Test
    void testHashCode_EqualObjects_SameHashCode() {
        ImageMetadata metadata1 = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(24)
                .hasAlpha(true)
                .build();

        ImageMetadata metadata2 = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(24)
                .hasAlpha(true)
                .build();

        assertEquals(metadata1.hashCode(), metadata2.hashCode());
    }

    @Test
    void testToString_ContainsAllFields() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(24)
                .hasAlpha(true)
                .build();

        String toString = metadata.toString();
        assertTrue(toString.contains("ImageMetadata"));
        assertTrue(toString.contains("width=1920"));
        assertTrue(toString.contains("height=1080"));
        assertTrue(toString.contains("colorSpace='RGB'"));
        assertTrue(toString.contains("bitDepth=24"));
        assertTrue(toString.contains("hasAlpha=true"));
    }

    // JSON serialization tests
    @Test
    void testJsonSerialization_SerializesCorrectly() throws Exception {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(24)
                .hasAlpha(true)
                .build();

        String json = objectMapper.writeValueAsString(metadata);

        assertTrue(json.contains("\"type\":\"image\""));
        assertTrue(json.contains("\"width\":1920"));
        assertTrue(json.contains("\"height\":1080"));
        assertTrue(json.contains("\"colorSpace\":\"RGB\""));
        assertTrue(json.contains("\"bitDepth\":24"));
    }

    @Test
    void testJsonDeserialization_DeserializesCorrectly() throws Exception {
        String json = """
                {
                    "type": "image",
                    "width": 1920,
                    "height": 1080,
                    "colorSpace": "RGB",
                    "bitDepth": 24,
                    "hasAlpha": true
                }
                """;

        MediaMetadata metadata = objectMapper.readValue(json, MediaMetadata.class);

        assertInstanceOf(ImageMetadata.class, metadata);
        ImageMetadata imageMetadata = (ImageMetadata) metadata;
        assertEquals(1920, imageMetadata.getWidth());
        assertEquals(1080, imageMetadata.getHeight());
        assertEquals("RGB", imageMetadata.getColorSpace());
        assertEquals(24, imageMetadata.getBitDepth());
        assertTrue(imageMetadata.hasAlpha());
    }

    // Edge cases
    @Test
    void testBuilder_NegativeValues_Accepted() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(-1)
                .height(-1)
                .bitDepth(-1)
                .build();

        assertEquals(-1, metadata.getWidth());
        assertEquals(-1, metadata.getHeight());
        assertEquals(-1, metadata.getBitDepth());
    }

    @Test
    void testIsValid_NegativeWidth_ReturnsFalse() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(-1)
                .height(1080)
                .colorSpace("RGB")
                .bitDepth(24)
                .build();

        assertFalse(metadata.isValid());
    }

    @Test
    void testGetAspectRatio_NegativeHeight_ReturnsZero() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(1920)
                .height(-1080)
                .build();

        assertEquals(0.0, metadata.getAspectRatio());
    }

    @Test
    void testGetPixelCount_LargeImage_NoOverflow() {
        ImageMetadata metadata = ImageMetadata.builder()
                .width(100000)
                .height(100000)
                .build();

        assertEquals(100000L * 100000L, metadata.getPixelCount());
    }
}