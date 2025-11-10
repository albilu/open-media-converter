package org.omc.service;

import org.omc.service.ToolDiscovery;
import org.omc.core.ConfigurationManager;
import org.omc.model.ToolConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolDiscovery service.
 * 
 * Requirements: REQ-004.1
 */
class ToolDiscoveryTest {

    @TempDir
    Path tempDir;

    private ConfigurationManager configManager;
    private ToolDiscovery toolDiscovery;

    @BeforeEach
    void setUp() throws IOException {
        // Create temporary directories for testing
        Path configDir = tempDir.resolve("config");
        Path dataDir = tempDir.resolve("data");
        Path cacheDir = tempDir.resolve("cache");

        Files.createDirectories(configDir);
        Files.createDirectories(dataDir);
        Files.createDirectories(cacheDir);

        configManager = new ConfigurationManager(configDir, dataDir, cacheDir);
        toolDiscovery = new ToolDiscovery(configManager);
    }

    @AfterEach
    void tearDown() {
        // Cleanup happens automatically with @TempDir
    }

    @Test
    void testConstructor() {
        assertNotNull(toolDiscovery);
    }

    @Test
    void testDiscoverToolsCreatesConfiguration() {
        ToolConfiguration config = toolDiscovery.discoverTools();

        assertNotNull(config);
        // Note: Actual paths depend on system installation
        // We just verify the method returns a non-null configuration
    }

    @Test
    void testLoadOrDiscoverToolsWhenNoConfigExists() {
        // First call should perform discovery
        ToolConfiguration config = toolDiscovery.loadOrDiscoverTools();

        assertNotNull(config);
        // Should have saved configuration
        assertTrue(Files.exists(configManager.getToolsConfigPath()));
    }

    @Test
    void testLoadOrDiscoverToolsLoadsExistingConfig() throws IOException {
        // Create a mock configuration
        ToolConfiguration savedConfig = new ToolConfiguration();
        savedConfig.setFfmpegPath(Path.of("/usr/bin/ffmpeg"));
        savedConfig.setFfprobePath(Path.of("/usr/bin/ffprobe"));
        savedConfig.setFfmpegVersion("6.0.0");

        // Save it
        org.omc.util.JsonUtils.writeJsonFile(
                savedConfig,
                configManager.getToolsConfigPath().toFile());

        // Load it back
        ToolConfiguration loaded = toolDiscovery.loadOrDiscoverTools();

        assertNotNull(loaded);
        assertEquals(Path.of("/usr/bin/ffmpeg"), loaded.getFfmpegPath());
        assertEquals(Path.of("/usr/bin/ffprobe"), loaded.getFfprobePath());
        assertEquals("6.0.0", loaded.getFfmpegVersion());
    }

    @Test
    void testLoadOrDiscoverToolsBackwardCompatibility() throws IOException {
        // Create a mock configuration without ImageMagick fields (old format)
        ToolConfiguration savedConfig = new ToolConfiguration();
        savedConfig.setFfmpegPath(Path.of("/usr/bin/ffmpeg"));
        savedConfig.setFfprobePath(Path.of("/usr/bin/ffprobe"));
        savedConfig.setPandocPath(Path.of("/usr/bin/pandoc"));
        savedConfig.setFfmpegVersion("6.0.0");
        savedConfig.setPandocVersion("3.1.0");
        // Deliberately not setting convertPath and convertVersion

        // Save it
        org.omc.util.JsonUtils.writeJsonFile(
                savedConfig,
                configManager.getToolsConfigPath().toFile());

        // Load it back
        ToolConfiguration loaded = toolDiscovery.loadOrDiscoverTools();

        assertNotNull(loaded);
        assertEquals(Path.of("/usr/bin/ffmpeg"), loaded.getFfmpegPath());
        assertEquals(Path.of("/usr/bin/ffprobe"), loaded.getFfprobePath());
        assertEquals(Path.of("/usr/bin/pandoc"), loaded.getPandocPath());
        assertEquals("6.0.0", loaded.getFfmpegVersion());
        assertEquals("3.1.0", loaded.getPandocVersion());
        // ImageMagick fields should be null (backward compatible)
        assertNull(loaded.getConvertPath());
        assertNull(loaded.getConvertVersion());
    }

    @Test
    void testIsToolAvailableWithNullPath() {
        assertFalse(toolDiscovery.isToolAvailable(null));
    }

    @Test
    void testIsToolAvailableWithNonExistentPath() {
        Path nonExistent = tempDir.resolve("nonexistent");
        assertFalse(toolDiscovery.isToolAvailable(nonExistent));
    }

    @Test
    void testIsToolAvailableWithExistingExecutable() throws IOException {
        // Create a mock executable file
        Path executable = tempDir.resolve("mock-tool");
        Files.createFile(executable);

        // Make it executable (Unix-like systems only)
        if (isUnixLike()) {
            Set<PosixFilePermission> perms = Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(executable, perms);

            assertTrue(toolDiscovery.isToolAvailable(executable));
        }
    }

    @Test
    void testIsToolAvailableWithNonExecutableFile() throws IOException {
        // Create a non-executable file
        Path nonExecutable = tempDir.resolve("non-executable");
        Files.createFile(nonExecutable);

        if (isUnixLike()) {
            Set<PosixFilePermission> perms = Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(nonExecutable, perms);

            assertFalse(toolDiscovery.isToolAvailable(nonExecutable));
        }
    }

    @Test
    void testValidateConfigurationAllToolsAvailable() throws IOException {
        ToolConfiguration config = new ToolConfiguration();

        // Create mock executable files
        Path ffmpeg = createMockExecutable("ffmpeg");
        Path ffprobe = createMockExecutable("ffprobe");
        Path pandoc = createMockExecutable("pandoc");
        Path soffice = createMockExecutable("soffice");
        Path convert = createMockExecutable("convert");

        config.setFfmpegPath(ffmpeg);
        config.setFfprobePath(ffprobe);
        config.setPandocPath(pandoc);
        config.setLibreOfficePath(soffice);
        config.setConvertPath(convert);

        List<String> unavailable = toolDiscovery.validateConfiguration(config);

        if (isUnixLike()) {
            assertTrue(unavailable.isEmpty(), "All tools should be available");
        }
    }

    @Test
    void testValidateConfigurationSomeToolsUnavailable() throws IOException {
        ToolConfiguration config = new ToolConfiguration();

        // Create some mock executables
        Path ffmpeg = createMockExecutable("ffmpeg");
        Path ffprobe = createMockExecutable("ffprobe");

        // Set some valid and some invalid paths
        config.setFfmpegPath(ffmpeg);
        config.setFfprobePath(ffprobe);
        config.setPandocPath(tempDir.resolve("nonexistent-pandoc"));
        config.setLibreOfficePath(tempDir.resolve("nonexistent-soffice"));
        config.setConvertPath(tempDir.resolve("nonexistent-convert"));

        List<String> unavailable = toolDiscovery.validateConfiguration(config);

        assertNotNull(unavailable);
        assertTrue(unavailable.contains("pandoc"));
        assertTrue(unavailable.contains("libreoffice"));
        assertTrue(unavailable.contains("imagemagick"));

        if (isUnixLike()) {
            assertFalse(unavailable.contains("ffmpeg"));
            assertFalse(unavailable.contains("ffprobe"));
        }
    }

    @Test
    void testValidateConfigurationWithNullPaths() {
        ToolConfiguration config = new ToolConfiguration();
        // All paths are null

        List<String> unavailable = toolDiscovery.validateConfiguration(config);

        assertNotNull(unavailable);
        // Should not report unavailable if path is null (tool not configured)
        assertTrue(unavailable.isEmpty());
    }

    @Test
    void testValidateConfigurationWithMixedNullAndInvalidPaths() throws IOException {
        ToolConfiguration config = new ToolConfiguration();

        // Valid ffmpeg
        Path ffmpeg = createMockExecutable("ffmpeg");
        config.setFfmpegPath(ffmpeg);
        config.setFfprobePath(ffmpeg); // Reuse for simplicity

        // Invalid pandoc
        config.setPandocPath(tempDir.resolve("nonexistent"));

        // Null LibreOffice (not configured)
        config.setLibreOfficePath(null);

        List<String> unavailable = toolDiscovery.validateConfiguration(config);

        assertNotNull(unavailable);
        assertTrue(unavailable.contains("pandoc"));
        assertFalse(unavailable.contains("libreoffice")); // Null paths not reported

        if (isUnixLike()) {
            assertFalse(unavailable.contains("ffmpeg"));
        }
    }

    @Test
    void testDiscoverSystemFFmpeg() {
        // This test depends on system installation
        // We just verify it doesn't crash
        ToolConfiguration config = toolDiscovery.discoverTools();

        assertNotNull(config);

        // If FFmpeg is installed on system, paths should be set
        if (config.isFfmpegAvailable()) {
            assertNotNull(config.getFfmpegPath());
            assertNotNull(config.getFfprobePath());
            assertTrue(Files.exists(config.getFfmpegPath()));
            assertTrue(Files.exists(config.getFfprobePath()));
        }
    }

    @Test
    void testDiscoverSystemPandoc() {
        // This test depends on system installation
        // We just verify it doesn't crash
        ToolConfiguration config = toolDiscovery.discoverTools();

        assertNotNull(config);

        // If Pandoc is installed on system, path should be set
        if (config.isPandocAvailable()) {
            assertNotNull(config.getPandocPath());
            assertTrue(Files.exists(config.getPandocPath()));
        }
    }

    @Test
    void testDiscoverSystemLibreOffice() {
        // This test depends on system installation
        // We just verify it doesn't crash
        ToolConfiguration config = toolDiscovery.discoverTools();

        assertNotNull(config);

        // If LibreOffice is installed on system, path should be set
        if (config.isLibreOfficeAvailable()) {
            assertNotNull(config.getLibreOfficePath());
            assertTrue(Files.exists(config.getLibreOfficePath()));
        }
    }

    @Test
    void testDiscoverSystemImageMagick() {
        // This test depends on system installation
        // We just verify it doesn't crash
        ToolConfiguration config = toolDiscovery.discoverTools();

        assertNotNull(config);

        // If ImageMagick is installed on system, path should be set
        if (config.isImageMagickAvailable()) {
            assertNotNull(config.getConvertPath());
            assertTrue(Files.exists(config.getConvertPath()));
        }
    }

    @Test
    void testSaveAndLoadConfiguration() throws IOException {
        // Discover tools
        ToolConfiguration original = toolDiscovery.discoverTools();

        // Configuration should be saved automatically
        assertTrue(Files.exists(configManager.getToolsConfigPath()));

        // Create new ToolDiscovery instance
        ToolDiscovery newToolDiscovery = new ToolDiscovery(configManager);

        // Load configuration
        ToolConfiguration loaded = newToolDiscovery.loadOrDiscoverTools();

        assertNotNull(loaded);
        assertEquals(original.getFfmpegPath(), loaded.getFfmpegPath());
        assertEquals(original.getFfprobePath(), loaded.getFfprobePath());
        assertEquals(original.getPandocPath(), loaded.getPandocPath());
        assertEquals(original.getLibreOfficePath(), loaded.getLibreOfficePath());
        assertEquals(original.getConvertPath(), loaded.getConvertPath());
        assertEquals(original.getFfmpegVersion(), loaded.getFfmpegVersion());
        assertEquals(original.getPandocVersion(), loaded.getPandocVersion());
        assertEquals(original.getLibreOfficeVersion(), loaded.getLibreOfficeVersion());
        assertEquals(original.getConvertVersion(), loaded.getConvertVersion());
    }

    @Test
    void testConfigurationPersistence() throws IOException {
        // First discovery
        ToolConfiguration config1 = toolDiscovery.loadOrDiscoverTools();
        Path toolsConfigPath = configManager.getToolsConfigPath();

        assertTrue(Files.exists(toolsConfigPath));

        // Create new instance and load
        ToolDiscovery newToolDiscovery = new ToolDiscovery(configManager);
        ToolConfiguration config2 = newToolDiscovery.loadOrDiscoverTools();

        // Should load same configuration, not rediscover
        assertEquals(config1.getFfmpegPath(), config2.getFfmpegPath());
        assertEquals(config1.getPandocPath(), config2.getPandocPath());
        assertEquals(config1.getLibreOfficePath(), config2.getLibreOfficePath());
    }

    // Helper methods

    /**
     * Creates a mock executable file for testing.
     */
    private Path createMockExecutable(String name) throws IOException {
        Path executable = tempDir.resolve(name);
        Files.createFile(executable);

        if (isUnixLike()) {
            Set<PosixFilePermission> perms = Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(executable, perms);
        }

        return executable;
    }

    /**
     * Checks if running on Unix-like system (Linux, macOS).
     */
    private boolean isUnixLike() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("nix") || os.contains("nux") || os.contains("mac");
    }
}
