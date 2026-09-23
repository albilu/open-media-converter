package org.omc.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConversionTool enum.
 */
class ConversionToolTest {

    @Test
    void testEnumValues() {
        // The tool set is a contract: ToolManager routing, result metadata
        // and the tool-output viewer all depend on it
        ConversionTool[] values = ConversionTool.values();
        assertEquals(4, values.length, "Should have exactly 4 conversion tools");

        assertNotNull(ConversionTool.FFMPEG);
        assertNotNull(ConversionTool.PANDOC);
        assertNotNull(ConversionTool.LIBREOFFICE);
        assertNotNull(ConversionTool.IMAGEMAGICK);
    }
}
