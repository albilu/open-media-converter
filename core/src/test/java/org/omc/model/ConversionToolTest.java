package org.omc.model;

import org.omc.model.ConversionTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConversionTool enum.
 * Requirement REQ-004.1: Tool selection and mapping to format categories.
 */
class ConversionToolTest {

    @Test
    void testEnumValues() {
        // Test all expected values exist
        ConversionTool[] values = ConversionTool.values();
        assertEquals(4, values.length, "Should have exactly 4 tool values");

        // Verify each value
        assertNotNull(ConversionTool.FFMPEG);
        assertNotNull(ConversionTool.PANDOC);
        assertNotNull(ConversionTool.LIBREOFFICE);
        assertNotNull(ConversionTool.IMAGEMAGICK);
    }

    @Test
    void testValueOf() {
        // Test valueOf for each tool
        assertEquals(ConversionTool.FFMPEG, ConversionTool.valueOf("FFMPEG"));
        assertEquals(ConversionTool.PANDOC, ConversionTool.valueOf("PANDOC"));
        assertEquals(ConversionTool.LIBREOFFICE, ConversionTool.valueOf("LIBREOFFICE"));
        assertEquals(ConversionTool.IMAGEMAGICK, ConversionTool.valueOf("IMAGEMAGICK"));
    }

    @Test
    void testValueOfInvalidThrowsException() {
        // Test invalid value throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> ConversionTool.valueOf("INVALID"));
        assertThrows(IllegalArgumentException.class, () -> ConversionTool.valueOf("ffmpeg"));
        assertThrows(IllegalArgumentException.class, () -> ConversionTool.valueOf(""));
    }

    @Test
    void testValueOfNullThrowsException() {
        // Test null throws NullPointerException
        assertThrows(NullPointerException.class, () -> ConversionTool.valueOf(null));
    }

    @Test
    void testEnumOrdering() {
        // Test that enum values maintain expected order
        ConversionTool[] values = ConversionTool.values();
        assertEquals(ConversionTool.FFMPEG, values[0]);
        assertEquals(ConversionTool.PANDOC, values[1]);
        assertEquals(ConversionTool.LIBREOFFICE, values[2]);
        assertEquals(ConversionTool.IMAGEMAGICK, values[3]);
    }

    @Test
    void testEnumEquality() {
        // Test enum equality
        ConversionTool tool1 = ConversionTool.FFMPEG;
        ConversionTool tool2 = ConversionTool.valueOf("FFMPEG");
        assertSame(tool1, tool2, "Enum values should be singletons");

        ConversionTool tool3 = ConversionTool.PANDOC;
        assertNotSame(tool1, tool3, "Different enum values should not be the same");
    }

    @Test
    void testEnumToString() {
        // Test toString returns enum name
        assertEquals("FFMPEG", ConversionTool.FFMPEG.toString());
        assertEquals("PANDOC", ConversionTool.PANDOC.toString());
        assertEquals("LIBREOFFICE", ConversionTool.LIBREOFFICE.toString());
        assertEquals("IMAGEMAGICK", ConversionTool.IMAGEMAGICK.toString());
    }

    @Test
    void testEnumName() {
        // Test name() method
        assertEquals("FFMPEG", ConversionTool.FFMPEG.name());
        assertEquals("PANDOC", ConversionTool.PANDOC.name());
        assertEquals("LIBREOFFICE", ConversionTool.LIBREOFFICE.name());
        assertEquals("IMAGEMAGICK", ConversionTool.IMAGEMAGICK.name());
    }

    @Test
    void testEnumOrdinal() {
        // Test ordinal values
        assertEquals(0, ConversionTool.FFMPEG.ordinal());
        assertEquals(1, ConversionTool.PANDOC.ordinal());
        assertEquals(2, ConversionTool.LIBREOFFICE.ordinal());
        assertEquals(3, ConversionTool.IMAGEMAGICK.ordinal());
    }

    @Test
    void testEnumComparison() {
        // Test enum comparison (using ordinal)
        assertTrue(ConversionTool.FFMPEG.compareTo(ConversionTool.PANDOC) < 0);
        assertTrue(ConversionTool.PANDOC.compareTo(ConversionTool.LIBREOFFICE) < 0);
        assertTrue(ConversionTool.LIBREOFFICE.compareTo(ConversionTool.FFMPEG) > 0);
        assertEquals(0, ConversionTool.PANDOC.compareTo(ConversionTool.PANDOC));
    }

    @Test
    void testEnumInSwitch() {
        // Test enum can be used in switch statements
        String result = switch (ConversionTool.FFMPEG) {
            case FFMPEG -> "Multimedia";
            case PANDOC -> "Documents";
            case LIBREOFFICE -> "Office";
            case IMAGEMAGICK -> "Images";
        };
        assertEquals("Multimedia", result);
    }

    @Test
    void testEnumSerialization() {
        // Test that enum name can be serialized and deserialized
        ConversionTool original = ConversionTool.LIBREOFFICE;
        String serialized = original.name();
        ConversionTool deserialized = ConversionTool.valueOf(serialized);
        assertSame(original, deserialized);
    }

    @Test
    void testToolMapping() {
        // Test that we can map tools to their purpose
        // FFMPEG handles video and audio
        ConversionTool ffmpeg = ConversionTool.FFMPEG;
        assertEquals("FFMPEG", ffmpeg.name());

        // PANDOC handles text documents
        ConversionTool pandoc = ConversionTool.PANDOC;
        assertEquals("PANDOC", pandoc.name());

        // LIBREOFFICE handles office documents
        ConversionTool libreoffice = ConversionTool.LIBREOFFICE;
        assertEquals("LIBREOFFICE", libreoffice.name());

        // IMAGEMAGICK handles images
        ConversionTool imagemagick = ConversionTool.IMAGEMAGICK;
        assertEquals("IMAGEMAGICK", imagemagick.name());
    }
}
