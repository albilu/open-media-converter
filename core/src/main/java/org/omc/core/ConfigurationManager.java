package org.omc.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

import org.omc.exception.ErrorCode;
import org.omc.exception.StateIOException;
import org.omc.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages application configuration directories and paths.
 * Provides centralized access to config, data, log, and temp directories.
 * 
 * Requirements: REQ-005.3
 */
public class ConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);

    // Base directory names
    private static final String APP_NAME = "open-media-converter";

    // Subdirectory names
    private static final String LOGS_SUBDIR = "logs";
    private static final String TEMP_SUBDIR = "temp";
    private static final String TOOLS_SUBDIR = "tools";

    // File names
    private static final String SETTINGS_FILE = "settings.json";
    private static final String STATE_FILE = "state.json";
    private static final String PRESETS_FILE = "presets.json";
    private static final String TOOLS_FILE = "tools.json";

    private final Path configDirectory;
    private final Path dataDirectory;
    private final Path cacheDirectory;
    private final Path logDirectory;
    private final Path tempDirectory;
    private final Path toolsDirectory;

    /**
     * Creates a ConfigurationManager with default directories.
     *
     * @throws StateIOException if a required directory cannot be created
     */
    public ConfigurationManager() throws StateIOException {
        this(getDefaultConfigDirectory(), getDefaultDataDirectory(), getDefaultCacheDirectory());
    }

    /**
     * Creates a ConfigurationManager with custom directories.
     *
     * @param configDirectory The configuration directory path
     * @param dataDirectory   The data directory path
     * @param cacheDirectory  The cache directory path
     * @throws NullPointerException if any directory path is null
     * @throws StateIOException     if a required directory cannot be created;
     *                              callers otherwise assume the directories
     *                              exist once the constructor returns
     */
    public ConfigurationManager(Path configDirectory, Path dataDirectory, Path cacheDirectory)
            throws StateIOException {
        this.configDirectory = Objects.requireNonNull(configDirectory, "configDirectory cannot be null");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory cannot be null");
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "cacheDirectory cannot be null");
        this.logDirectory = dataDirectory.resolve(LOGS_SUBDIR);
        this.tempDirectory = cacheDirectory.resolve(TEMP_SUBDIR);
        this.toolsDirectory = dataDirectory.resolve(TOOLS_SUBDIR);

        initializeDirectories();
    }

    /**
     * Gets the default configuration directory (XDG config home based,
     * ~/.config/open-media-converter by default).
     *
     * @return The default config directory path
     */
    private static Path getDefaultConfigDirectory() {
        return PathUtils.xdgConfigHome().resolve(APP_NAME);
    }

    /**
     * Gets the default data directory (XDG data home based,
     * ~/.local/share/open-media-converter by default).
     *
     * @return The default data directory path
     */
    private static Path getDefaultDataDirectory() {
        return PathUtils.xdgDataHome().resolve(APP_NAME);
    }

    /**
     * Gets the default cache directory (XDG cache home based,
     * ~/.cache/open-media-converter by default).
     *
     * @return The default cache directory path
     */
    private static Path getDefaultCacheDirectory() {
        return PathUtils.xdgCacheHome().resolve(APP_NAME);
    }

    /**
     * Initializes all required directories, creating them if they don't exist.
     *
     * @throws StateIOException if any directory cannot be created
     */
    private void initializeDirectories() throws StateIOException {
        createDirectoryIfNotExists(configDirectory);
        createDirectoryIfNotExists(dataDirectory);
        createDirectoryIfNotExists(cacheDirectory);
        createDirectoryIfNotExists(logDirectory);
        createDirectoryIfNotExists(tempDirectory);
        createDirectoryIfNotExists(toolsDirectory);
    }

    /**
     * Creates a directory if it doesn't exist.
     *
     * @param directory The directory path
     * @throws StateIOException if the directory cannot be created; failure is
     *                          fatal because every consumer of this class
     *                          assumes the directories exist after construction
     */
    private void createDirectoryIfNotExists(Path directory) throws StateIOException {
        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
                logger.info("Created directory: {}", directory);
            } catch (IOException e) {
                logger.error("Failed to create directory: {}", directory, e);
                throw new StateIOException(
                        "Failed to create required directory: " + directory,
                        ErrorCode.CONFIGURATION_ERROR,
                        directory.toString(),
                        false,
                        e);
            }
        }
    }

    /**
     * Gets the configuration directory path.
     *
     * @return The config directory path
     */
    public Path getConfigDirectory() {
        return configDirectory;
    }

    /**
     * Gets the data directory path.
     *
     * @return The data directory path
     */
    public Path getDataDirectory() {
        return dataDirectory;
    }

    /**
     * Gets the cache directory path.
     *
     * @return The cache directory path
     */
    public Path getCacheDirectory() {
        return cacheDirectory;
    }

    /**
     * Gets the log directory path.
     *
     * @return The log directory path
     */
    public Path getLogDirectory() {
        return logDirectory;
    }

    /**
     * Gets the temporary files directory path.
     *
     * @return The temp directory path
     */
    public Path getTempDirectory() {
        return tempDirectory;
    }

    /**
     * Gets the tools (embedded binaries) directory path.
     *
     * @return The tools directory path
     */
    public Path getToolsDirectory() {
        return toolsDirectory;
    }

    /**
     * Gets the settings file path.
     *
     * @return The settings file path
     */
    public Path getSettingsFilePath() {
        return configDirectory.resolve(SETTINGS_FILE);
    }

    /**
     * Gets the application state file path.
     *
     * @return The state file path
     */
    public Path getStateFilePath() {
        return configDirectory.resolve(STATE_FILE);
    }

    /**
     * Gets the presets file path.
     *
     * @return The presets file path
     */
    public Path getPresetsFilePath() {
        return configDirectory.resolve(PRESETS_FILE);
    }

    /**
     * Gets the tools configuration file path.
     *
     * @return The tools file path
     */
    public Path getToolsConfigPath() {
        return configDirectory.resolve(TOOLS_FILE);
    }

    /**
     * Gets the path for a tool binary.
     *
     * @param toolName The name of the tool (e.g., "ffmpeg", "pandoc")
     * @return The tool binary path
     */
    public Path getToolPath(String toolName) {
        return toolsDirectory.resolve(toolName);
    }

    /**
     * Gets a path within the configuration directory.
     *
     * @param relativePath The relative path within config directory
     * @return The resolved path
     */
    public Path getConfigPath(String relativePath) {
        return configDirectory.resolve(relativePath);
    }

    /**
     * Gets a path within the data directory.
     *
     * @param relativePath The relative path within data directory
     * @return The resolved path
     */
    public Path getDataPath(String relativePath) {
        return dataDirectory.resolve(relativePath);
    }

    /**
     * Gets a path within the cache directory.
     *
     * @param relativePath The relative path within cache directory
     * @return The resolved path
     */
    public Path getCachePath(String relativePath) {
        return cacheDirectory.resolve(relativePath);
    }

    /**
     * Cleans up temporary files.
     * Removes all files and subdirectories in the temp directory; the temp
     * directory itself is kept.
     */
    public void cleanupTempFiles() {
        if (!Files.exists(tempDirectory)) {
            return;
        }

        // try-with-resources: Files.walk opens a directory handle per entry
        // that must be closed even when the consumer throws.
        try (Stream<Path> entries = Files.walk(tempDirectory)) {
            // Depth-first (reverse) order deletes subdirectories after their
            // contents, so empty subdirectories are removed too instead of
            // making Files.delete fail with DirectoryNotEmptyException.
            entries.sorted(Comparator.reverseOrder())
                    .filter(entry -> !entry.equals(tempDirectory))
                    .forEach(entry -> {
                        try {
                            Files.delete(entry);
                            logger.debug("Deleted temp entry: {}", entry);
                        } catch (IOException e) {
                            logger.warn("Failed to delete temp entry: {}", entry, e);
                        }
                    });
        } catch (IOException e) {
            logger.error("Failed to clean up temp files", e);
        }
    }

    /**
     * Checks if all required directories are accessible.
     *
     * @return true if all directories are accessible
     */
    public boolean areDirectoriesAccessible() {
        return Files.isDirectory(configDirectory) && Files.isWritable(configDirectory) &&
                Files.isDirectory(dataDirectory) && Files.isWritable(dataDirectory) &&
                Files.isDirectory(cacheDirectory) && Files.isWritable(cacheDirectory);
    }

    /**
     * Gets information about the configuration setup.
     *
     * @return A string with configuration information
     */
    public String getConfigurationInfo() {
        return String.format("""
                Open Media Converter Configuration:
                - Config Directory: %s
                - Data Directory: %s
                - Cache Directory: %s
                - Log Directory: %s
                - Temp Directory: %s
                - Tools Directory: %s
                - Directories Accessible: %s
                """,
                configDirectory,
                dataDirectory,
                cacheDirectory,
                logDirectory,
                tempDirectory,
                toolsDirectory,
                areDirectoriesAccessible());
    }
}
