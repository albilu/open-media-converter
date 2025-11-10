package org.omc.model;

import org.omc.model.ToolConfiguration;
import org.omc.model.ConversionTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.omc.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolConfiguration model.
 * 
 * Requirements: REQ-004.1
 */
class ToolConfigurationTest {

    @Test
    void testDefaultConstructor() {
        ToolConfiguration config = new ToolConfiguration();

        assertNull(config.getFfmpegPath());
        assertNull(config.getFfprobePath());
        assertNull(config.getPandocPath());
        assertNull(config.getLibreOfficePath());
        assertNull(config.getFfmpegVersion());
        assertNull(config.getPandocVersion());
        assertNull(config.getLibreOfficeVersion());
    }

    @Test
    void testSettersAndGetters() {
        ToolConfiguration config = new ToolConfiguration();

        Path ffmpegPath = Paths.get("/usr/bin/ffmpeg");
        Path ffprobePath = Paths.get("/usr/bin/ffprobe");
        Path pandocPath = Paths.get("/usr/bin/pandoc");
        Path libreOfficePath = Paths.get("/usr/bin/soffice");

        config.setFfmpegPath(ffmpegPath);
        config.setFfprobePath(ffprobePath);
        config.setPandocPath(pandocPath);
        config.setLibreOfficePath(libreOfficePath);
        config.setFfmpegVersion("6.0.0");
        config.setPandocVersion("3.1.0");
        config.setLibreOfficeVersion("7.5.0");

        assertEquals(ffmpegPath, config.getFfmpegPath());
        assertEquals(ffprobePath, config.getFfprobePath());
        assertEquals(pandocPath, config.getPandocPath());
        assertEquals(libreOfficePath, config.getLibreOfficePath());
        assertEquals("6.0.0", config.getFfmpegVersion());
        assertEquals("3.1.0", config.getPandocVersion());
        assertEquals("7.5.0", config.getLibreOfficeVersion());
    }

    @Test
    void testIsFfmpegAvailable() {
        ToolConfiguration config = new ToolConfiguration();

        assertFalse(config.isFfmpegAvailable());

        config.setFfmpegPath(Paths.get("/usr/bin/ffmpeg"));
        assertFalse(config.isFfmpegAvailable()); // Still false - ffprobe not set

        config.setFfprobePath(Paths.get("/usr/bin/ffprobe"));
        assertTrue(config.isFfmpegAvailable()); // Now both are set
    }

    @Test
    void testIsPandocAvailable() {
        ToolConfiguration config = new ToolConfiguration();

        assertFalse(config.isPandocAvailable());

        config.setPandocPath(Paths.get("/usr/bin/pandoc"));
        assertTrue(config.isPandocAvailable());
    }

    @Test
    void testIsLibreOfficeAvailable() {
        ToolConfiguration config = new ToolConfiguration();

        assertFalse(config.isLibreOfficeAvailable());

        config.setLibreOfficePath(Paths.get("/usr/bin/soffice"));
        assertTrue(config.isLibreOfficeAvailable());
    }

    @Test
    void testIsToolAvailable() {
        ToolConfiguration config = new ToolConfiguration();

        assertFalse(config.isToolAvailable(ConversionTool.FFMPEG));
        assertFalse(config.isToolAvailable(ConversionTool.PANDOC));
        assertFalse(config.isToolAvailable(ConversionTool.LIBREOFFICE));

        config.setFfmpegPath(Paths.get("/usr/bin/ffmpeg"));
        config.setFfprobePath(Paths.get("/usr/bin/ffprobe"));
        config.setPandocPath(Paths.get("/usr/bin/pandoc"));
        config.setLibreOfficePath(Paths.get("/usr/bin/soffice"));

        assertTrue(config.isToolAvailable(ConversionTool.FFMPEG));
        assertTrue(config.isToolAvailable(ConversionTool.PANDOC));
        assertTrue(config.isToolAvailable(ConversionTool.LIBREOFFICE));
    }

    @Test
    void testJsonSerialization() throws JsonProcessingException {
        ToolConfiguration config = new ToolConfiguration();
        config.setFfmpegPath(Paths.get("/usr/bin/ffmpeg"));
        config.setFfprobePath(Paths.get("/usr/bin/ffprobe"));
        config.setPandocPath(Paths.get("/usr/bin/pandoc"));
        config.setLibreOfficePath(Paths.get("/usr/bin/soffice"));
        config.setFfmpegVersion("6.0.0");
        config.setPandocVersion("3.1.0");
        config.setLibreOfficeVersion("7.5.0");

        // Serialize to JSON
        String json = JsonUtils.toJson(config);
        assertNotNull(json);
        assertTrue(json.contains("ffmpegPath"));
        assertTrue(json.contains("/usr/bin/ffmpeg"));

        // Deserialize from JSON
        ToolConfiguration deserialized = JsonUtils.fromJson(json, ToolConfiguration.class);
        assertNotNull(deserialized);
        assertEquals(config.getFfmpegPath(), deserialized.getFfmpegPath());
        assertEquals(config.getFfprobePath(), deserialized.getFfprobePath());
        assertEquals(config.getPandocPath(), deserialized.getPandocPath());
        assertEquals(config.getLibreOfficePath(), deserialized.getLibreOfficePath());
        assertEquals(config.getFfmpegVersion(), deserialized.getFfmpegVersion());
        assertEquals(config.getPandocVersion(), deserialized.getPandocVersion());
        assertEquals(config.getLibreOfficeVersion(), deserialized.getLibreOfficeVersion());
    }

    @Test
    void testEqualsAndHashCode() {
        ToolConfiguration config1 = new ToolConfiguration();
        config1.setFfmpegPath(Paths.get("/usr/bin/ffmpeg"));
        config1.setFfmpegVersion("6.0.0");

        ToolConfiguration config2 = new ToolConfiguration();
        config2.setFfmpegPath(Paths.get("/usr/bin/ffmpeg"));
        config2.setFfmpegVersion("6.0.0");

        ToolConfiguration config3 = new ToolConfiguration();
        config3.setFfmpegPath(Paths.get("/usr/bin/ffmpeg"));
        config3.setFfmpegVersion("6.0.1"); // Different version

        // Test equals
        assertEquals(config1, config2);
        assertNotEquals(config1, config3);
        assertNotEquals(config1, null);
        assertNotEquals(config1, "not a config");

        // Test hashCode
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    @Test
    void testToString() {
        ToolConfiguration config = new ToolConfiguration();
        config.setFfmpegPath(Paths.get("/usr/bin/ffmpeg"));
        config.setFfmpegVersion("6.0.0");

        String str = config.toString();
        assertNotNull(str);
        assertTrue(str.contains("ffmpeg"));
        assertTrue(str.contains("6.0.0"));
    }

    @Test
    void testPartialConfiguration() {
        // Test with only FFmpeg configured
        ToolConfiguration config = new ToolConfiguration();
        config.setFfmpegPath(Paths.get("/usr/bin/ffmpeg"));
        config.setFfprobePath(Paths.get("/usr/bin/ffprobe"));
        config.setFfmpegVersion("6.0.0");

        assertTrue(config.isFfmpegAvailable());
        assertFalse(config.isPandocAvailable());
        assertFalse(config.isLibreOfficeAvailable());
        assertTrue(config.isToolAvailable(ConversionTool.FFMPEG));
        assertFalse(config.isToolAvailable(ConversionTool.PANDOC));
        assertFalse(config.isToolAvailable(ConversionTool.LIBREOFFICE));
    }

    @Test
    void testNullPaths() {
        ToolConfiguration config = new ToolConfiguration();

        config.setFfmpegPath(null);
        config.setFfprobePath(null);
        config.setPandocPath(null);
        config.setLibreOfficePath(null);

        assertNull(config.getFfmpegPath());
        assertNull(config.getFfprobePath());
        assertNull(config.getPandocPath());
        assertNull(config.getLibreOfficePath());

        assertFalse(config.isFfmpegAvailable());
        assertFalse(config.isPandocAvailable());
        assertFalse(config.isLibreOfficeAvailable());
    }
}
