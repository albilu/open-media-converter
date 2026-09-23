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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
    void setUp() throws Exception {
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

    @Test
    void testSaveConfiguration_ReplacesFileViaTempAndAtomicRename() throws IOException {
        assumeTrue(isUnixLike(), "inode-based replace detection requires a Unix-like filesystem");

        // Given: An existing tools.json (observed by its inode)
        Path toolsConfigPath = configManager.getToolsConfigPath();
        ToolConfiguration seed = new ToolConfiguration();
        seed.setFfmpegPath(tempDir.resolve("old-ffmpeg"));
        org.omc.util.JsonUtils.writeJsonFile(seed, toolsConfigPath.toFile());
        long inodeBefore = (Long) Files.getAttribute(toolsConfigPath, "unix:ino");

        // When: Discovery saves a fresh configuration
        toolDiscovery.discoverTools();

        // Then: The file was replaced by a rename (new inode), not
        // truncated and rewritten in place
        long inodeAfter = (Long) Files.getAttribute(toolsConfigPath, "unix:ino");
        assertNotEquals(inodeBefore, inodeAfter,
                "tools.json must be replaced via temp-file + rename, not rewritten in place");

        // And: No temporary file is left behind
        String tempPrefix = toolsConfigPath.getFileName().toString() + ".tmp";
        try (var files = Files.list(toolsConfigPath.getParent())) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().startsWith(tempPrefix)),
                    "temp file must be cleaned up after the move");
        }

        // And: The replaced file is still valid JSON
        assertNotNull(org.omc.util.JsonUtils.readJsonFile(
                toolsConfigPath.toFile(), ToolConfiguration.class));
    }

    @Test
    void testFindSystemBinary_EmptyPathSegment_NotResolvedAgainstCwd() throws IOException {
        assumeTrue(isUnixLike(), "Requires POSIX executable scripts");

        // A binary name that never exists in the well-known system
        // directories, so discovery can only reach it via $PATH
        String binaryName = "omc-empty-path-segment-probe";

        // The trust probe executes no-slash commands via $PATH lookup, so
        // plant the executable in a writable directory on this JVM's $PATH
        // (outside SYSTEM_PATHS) to make the probe deterministically trusted
        Path pathDir = findWritablePathEntry();
        assumeTrue(pathDir != null, "No writable $PATH entry outside SYSTEM_PATHS; skipping");
        Path pathProbe = pathDir.resolve(binaryName);
        // Plant a matching file in the current working directory: an empty
        // $PATH segment resolves the candidate against CWD and then executes
        // it (untrusted execution vector)
        Path cwdProbe = Path.of(binaryName);
        try {
            Files.writeString(pathProbe, "#!/bin/sh\necho 'probe 1.0'\n");
            Files.setPosixFilePermissions(pathProbe, PosixFilePermissions.fromString("rwxr-xr-x"));
            Files.writeString(cwdProbe, "#!/bin/sh\necho 'probe 1.0'\n");
            Files.setPosixFilePermissions(cwdProbe, PosixFilePermissions.fromString("rwxr-xr-x"));

            Path emptyDir = Files.createDirectories(tempDir.resolve("empty-path-entry"));
            // Leading ':' produces an empty first segment in the split
            String pathEnv = ":" + emptyDir.toAbsolutePath();

            Optional<Path> found = toolDiscovery.findSystemBinary(binaryName, pathEnv);
            assertTrue(found.isEmpty(),
                    "empty $PATH segment must be skipped, not resolved against the working directory");
        } finally {
            Files.deleteIfExists(cwdProbe);
            Files.deleteIfExists(pathProbe);
        }
    }

    /**
     * Returns the first user-writable directory on this JVM's $PATH that is
     * not one of the well-known SYSTEM_PATHS directories.
     */
    private static Path findWritablePathEntry() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        List<String> systemPaths = List.of("/usr/bin/", "/usr/local/bin/", "/opt/bin/", "/snap/bin/");
        for (String entry : pathEnv.split(":")) {
            if (entry.isBlank() || systemPaths.contains(entry) || !Path.of(entry).isAbsolute()) {
                continue;
            }
            Path dir = Path.of(entry);
            if (Files.isDirectory(dir) && Files.isWritable(dir)) {
                return dir;
            }
        }
        return null;
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

    // ========================================
    // FFmpeg git-snapshot version parsing
    // ========================================

    private static java.lang.reflect.Method parseVersionProbe() throws Exception {
        java.lang.reflect.Method method = ToolDiscovery.class.getDeclaredMethod(
                "parseVersion", String.class, String.class);
        method.setAccessible(true);
        return method;
    }

    @SuppressWarnings("unchecked")
    private Optional<String> parseVersion(String toolName, String output) throws Exception {
        return (Optional<String>) parseVersionProbe().invoke(toolDiscovery, toolName, output);
    }

    @Test
    void testParseVersion_FfmpegGitSnapshot_ParsesSnapshotVersion() throws Exception {
        // Git-snapshot builds print "ffmpeg version N-110753-g1234abc ...":
        // the optional 'n' prefix followed by digits does not match the dash
        // after 'N', so the version used to come back empty
        Optional<String> version = parseVersion("ffmpeg",
                "ffmpeg version N-110753-g1234abc Copyright (c) 2000-2023 the FFmpeg developers");

        assertTrue(version.isPresent(), "git-snapshot version must be parsed");
        assertEquals("N-110753", version.get(), "snapshot maps to its N-<build> identifier");
    }

    @Test
    void testParseVersion_FfprobeGitSnapshot_ParsesSnapshotVersion() throws Exception {
        Optional<String> version = parseVersion("ffprobe",
                "ffprobe version N-110753-g1234abc Copyright (c) 2000-2023 the FFmpeg developers");

        assertTrue(version.isPresent(), "ffprobe git-snapshot version must be parsed");
        assertEquals("N-110753", version.get());
    }

    @Test
    void testParseVersion_FfmpegReleaseFormats_Unchanged() throws Exception {
        // Guard: release version formats must keep parsing exactly as before
        assertEquals(Optional.of("7.0.1"),
                parseVersion("ffmpeg", "ffmpeg version n7.0.1 Copyright (c) 2000-2024 the FFmpeg developers"));
        assertEquals(Optional.of("6.1.1"),
                parseVersion("ffmpeg", "ffmpeg version 6.1.1-3ubuntu5 Copyright"));
        assertEquals(Optional.of("4.4.2"),
                parseVersion("ffprobe", "ffprobe version 4.4.2-0ubuntu0.22.04.1"));
    }

    @Test
    void testDetectVersion_FfmpegGitSnapshotStub_ReportsSnapshotVersion() throws Exception {
        assumeTrue(isUnixLike(), "Requires POSIX executable scripts");

        // End-to-end through the process-executing detector: a stub ffmpeg
        // printing a git-snapshot version line must yield a non-empty version
        Path ffmpegStub = tempDir.resolve("ffmpeg");
        Files.writeString(ffmpegStub,
                "#!/bin/sh\necho 'ffmpeg version N-110753-g1234abc Copyright (c) 2000-2023 the FFmpeg developers'\n");
        Files.setPosixFilePermissions(ffmpegStub, PosixFilePermissions.fromString("rwxr-xr-x"));

        java.lang.reflect.Method detectVersion = ToolDiscovery.class.getDeclaredMethod(
                "detectVersion", Path.class, String.class);
        detectVersion.setAccessible(true);

        @SuppressWarnings("unchecked")
        Optional<String> version = (Optional<String>) detectVersion.invoke(toolDiscovery, ffmpegStub, "-version");

        assertTrue(version.isPresent(), "git-snapshot ffmpeg must report a version");
        assertEquals("N-110753", version.get());
    }

    // ========================================
    // ImageMagick 7 "magick" binary discovery
    // ========================================

    @Test
    void testImageMagickCandidateNames_ConvertPreferredThenMagick() {
        // IM6 compatibility: "convert" stays the preferred probe name; IM7's
        // "magick" (whose "magick input ... output" form accepts the same
        // convert-style arguments) is the fallback
        assertArrayEquals(new String[] { "convert", "magick" }, ToolDiscovery.IMAGEMAGICK_CANDIDATE_NAMES);
    }

    @Test
    void testDiscoverImageMagick_Im7OnlyMagickStub_DiscoveredAsConvertTool() throws Exception {
        assumeTrue(isUnixLike(), "Requires POSIX executable scripts");

        // A pure IM7 install ships only "magick". The stub uses a unique name
        // so the probe can never resolve a real system binary instead.
        Path fakeBin = Files.createDirectories(tempDir.resolve("im7-only-bin"));
        Path magickStub = fakeBin.resolve("omc-im7-magick");
        Files.writeString(magickStub,
                "#!/bin/sh\necho 'Version: ImageMagick 7.1.1-15 Q16 x86_64 2023-07-01 https://imagemagick.org'\n");
        Files.setPosixFilePermissions(magickStub, PosixFilePermissions.fromString("rwxr-xr-x"));

        ToolConfiguration config = new ToolConfiguration();
        toolDiscovery.discoverImageMagick(config, fakeBin.toString(),
                new String[] { "omc-absent-convert", "omc-im7-magick" });

        assertEquals(magickStub, config.getConvertPath(),
                "IM7-only install must be discovered through the magick binary");
        assertEquals("7.1.1-15", config.getConvertVersion());
        assertTrue(config.isImageMagickAvailable());
    }

    @Test
    void testDiscoverImageMagick_ConvertPreferredOverMagick() throws Exception {
        assumeTrue(isUnixLike(), "Requires POSIX executable scripts");

        Path fakeBin = Files.createDirectories(tempDir.resolve("im6-and-7-bin"));
        Path convertStub = fakeBin.resolve("omc-im6-convert");
        Files.writeString(convertStub,
                "#!/bin/sh\necho 'Version: ImageMagick 6.9.12-98 Q16 x86_64 2021-10-01 https://legacy.imagemagick.org'\n");
        Files.setPosixFilePermissions(convertStub, PosixFilePermissions.fromString("rwxr-xr-x"));
        Path magickStub = fakeBin.resolve("omc-im7-magick2");
        Files.writeString(magickStub,
                "#!/bin/sh\necho 'Version: ImageMagick 7.1.1-15 Q16 x86_64 2023-07-01 https://imagemagick.org'\n");
        Files.setPosixFilePermissions(magickStub, PosixFilePermissions.fromString("rwxr-xr-x"));

        ToolConfiguration config = new ToolConfiguration();
        toolDiscovery.discoverImageMagick(config, fakeBin.toString(),
                new String[] { "omc-im6-convert", "omc-im7-magick2" });

        assertEquals(convertStub, config.getConvertPath(),
                "convert stays preferred for IM6 compatibility when both exist");
        assertEquals("6.9.12-98", config.getConvertVersion());
    }

    // ========================================
    // Interrupt handling regression tests
    // ========================================

    private static java.lang.reflect.Method trustedBinaryProbe() throws Exception {
        java.lang.reflect.Method method = ToolDiscovery.class.getDeclaredMethod(
                "isTrustedBinary", Path.class, String.class);
        method.setAccessible(true);
        return method;
    }

    /**
     * Interrupting a version probe blocked reading tool output must restore
     * the interrupt flag instead of swallowing it in a generic catch.
     */
    @Test
    void testIsTrustedBinary_InterruptedDuringOutputRead_RestoresInterruptFlag() throws Exception {
        assumeTrue(isUnixLike(), "Requires POSIX executable scripts");
        // Stub holds stdout open with no output and never exits: the probe
        // blocks waiting for the async output reader.
        Path stub = tempDir.resolve("stub-silent-hang");
        Files.writeString(stub, "#!/bin/sh\nexec sleep 60\n");
        Files.setPosixFilePermissions(stub, PosixFilePermissions.fromString("rwxr-xr-x"));

        java.lang.reflect.Method probe = trustedBinaryProbe();
        Thread worker = new Thread(() -> {
            try {
                probe.invoke(toolDiscovery, stub, "ffmpeg");
            } catch (ReflectiveOperationException e) {
                // probe returns false on interrupt; only unexpected here
            }
        });
        worker.setDaemon(true);
        worker.start();
        Thread.sleep(500); // let the worker block in the bounded get()
        worker.interrupt();
        worker.join(5000);

        assertFalse(worker.isAlive(), "isTrustedBinary must return after interruption");
        assertTrue(worker.isInterrupted(), "interrupt flag must be restored by isTrustedBinary");
    }

    /**
     * Interrupting a version probe blocked in waitFor must restore the
     * interrupt flag instead of swallowing it in a generic catch.
     */
    @Test
    void testIsTrustedBinary_InterruptedDuringWait_RestoresInterruptFlag() throws Exception {
        assumeTrue(isUnixLike(), "Requires POSIX executable scripts");
        // Stub emits a version line, closes its streams (so the async reader
        // completes) and then never exits: the probe blocks in waitFor().
        Path stub = tempDir.resolve("stub-blocking-wait");
        Files.writeString(stub, "#!/bin/sh\necho ffmpeg version 1.0\nexec 1>&- 2>&-\nexec sleep 60\n");
        Files.setPosixFilePermissions(stub, PosixFilePermissions.fromString("rwxr-xr-x"));

        java.lang.reflect.Method probe = trustedBinaryProbe();
        Thread worker = new Thread(() -> {
            try {
                probe.invoke(toolDiscovery, stub, "ffmpeg");
            } catch (ReflectiveOperationException e) {
                // probe returns false on interrupt; only unexpected here
            }
        });
        worker.setDaemon(true);
        worker.start();
        Thread.sleep(500); // let the worker reach the bounded waitFor()
        worker.interrupt();
        worker.join(5000);

        assertFalse(worker.isAlive(), "isTrustedBinary must return after interruption");
        assertTrue(worker.isInterrupted(), "interrupt flag must be restored by isTrustedBinary");
    }
}
