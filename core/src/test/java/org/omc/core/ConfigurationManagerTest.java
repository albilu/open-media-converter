package org.omc.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omc.exception.ErrorCode;
import org.omc.exception.StateIOException;

/**
 * Tests for ConfigurationManager directory initialization and temp cleanup.
 *
 * <p>
 * Pins the P2 audit fixes: constructor fail-fast (null paths, unreachable
 * directories) and depth-first temp cleanup that also removes subdirectories.
 */
class ConfigurationManagerTest {

    @TempDir
    Path tempDir;

    private Path configDir;
    private Path dataDir;
    private Path cacheDir;

    @BeforeEach
    void setUp() {
        configDir = tempDir.resolve("config");
        dataDir = tempDir.resolve("data");
        cacheDir = tempDir.resolve("cache");
    }

    // ========== Constructor fail-fast tests ==========

    @Test
    void constructor_withNullConfigDirectory_throwsNullPointerException() throws StateIOException {
        assertThrows(NullPointerException.class,
                () -> new ConfigurationManager(null, dataDir, cacheDir));
    }

    @Test
    void constructor_withNullDataDirectory_throwsNullPointerException() throws StateIOException {
        assertThrows(NullPointerException.class,
                () -> new ConfigurationManager(configDir, null, cacheDir));
    }

    @Test
    void constructor_withNullCacheDirectory_throwsNullPointerException() throws StateIOException {
        assertThrows(NullPointerException.class,
                () -> new ConfigurationManager(configDir, dataDir, null));
    }

    @Test
    void constructor_whenDirectoryCannotBeCreated_throwsStateIOException() throws IOException {
        // A regular file where a parent directory should be makes
        // Files.createDirectories fail; the constructor must not swallow it.
        Path blocker = Files.createFile(tempDir.resolve("blocker"));
        Path impossibleConfigDir = blocker.resolve("config");

        StateIOException exception = assertThrows(StateIOException.class,
                () -> new ConfigurationManager(impossibleConfigDir, dataDir, cacheDir));

        assertEquals(ErrorCode.CONFIGURATION_ERROR, exception.getErrorCode());
        assertTrue(exception.getMessage().contains(impossibleConfigDir.toString()));
        assertTrue(exception.getCause() instanceof IOException);
    }

    @Test
    void constructor_withValidDirectories_createsAllDirectories() throws StateIOException {
        ConfigurationManager manager = new ConfigurationManager(configDir, dataDir, cacheDir);

        assertTrue(Files.isDirectory(configDir));
        assertTrue(Files.isDirectory(dataDir));
        assertTrue(Files.isDirectory(cacheDir));
        assertTrue(Files.isDirectory(manager.getLogDirectory()));
        assertTrue(Files.isDirectory(manager.getTempDirectory()));
        assertTrue(Files.isDirectory(manager.getToolsDirectory()));
    }

    // ========== Temp cleanup tests ==========

    @Test
    void cleanupTempFiles_removesFilesAndSubdirectories() throws IOException, StateIOException {
        ConfigurationManager manager = new ConfigurationManager(configDir, dataDir, cacheDir);
        Path temp = manager.getTempDirectory();

        Files.createDirectories(temp.resolve("sub/deep"));
        Files.writeString(temp.resolve("a.tmp"), "data");
        Files.writeString(temp.resolve("sub/b.tmp"), "data");
        Files.writeString(temp.resolve("sub/deep/c.tmp"), "data");

        manager.cleanupTempFiles();

        // The temp root itself is kept (it belongs to the app), but it must be
        // empty: files AND subdirectories removed depth-first.
        assertTrue(Files.isDirectory(temp), "temp root itself should survive cleanup");
        assertFalse(Files.exists(temp.resolve("sub")), "subdirectories should be deleted");
        try (Stream<Path> entries = Files.list(temp)) {
            assertEquals(0, entries.count(), "temp directory should be empty after cleanup");
        }
    }

    @Test
    void cleanupTempFiles_whenTempDirectoryMissing_doesNotThrow() throws IOException, StateIOException {
        ConfigurationManager manager = new ConfigurationManager(configDir, dataDir, cacheDir);
        // Recursively remove the temp root to simulate external cleanup
        try (Stream<Path> entries = Files.walk(manager.getTempDirectory())) {
            entries.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        assertDoesNotThrow(manager::cleanupTempFiles);
    }
}
