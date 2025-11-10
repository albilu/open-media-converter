package org.omc.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    private static final String CONFIG_DIR_NAME = ".config";
    private static final String DATA_DIR_NAME = ".local/share";
    private static final String CACHE_DIR_NAME = ".cache";

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
     */
    public ConfigurationManager() {
        this(getDefaultConfigDirectory(), getDefaultDataDirectory(), getDefaultCacheDirectory());
    }

    /**
     * Creates a ConfigurationManager with custom directories.
     *
     * @param configDirectory The configuration directory path
     * @param dataDirectory   The data directory path
     * @param cacheDirectory  The cache directory path
     */
    public ConfigurationManager(Path configDirectory, Path dataDirectory, Path cacheDirectory) {
        this.configDirectory = configDirectory;
        this.dataDirectory = dataDirectory;
        this.cacheDirectory = cacheDirectory;
        this.logDirectory = dataDirectory.resolve(LOGS_SUBDIR);
        this.tempDirectory = cacheDirectory.resolve(TEMP_SUBDIR);
        this.toolsDirectory = dataDirectory.resolve(TOOLS_SUBDIR);

        initializeDirectories();
    }

    /**
     * Gets the default configuration directory (~/.config/open-media-converter).
     *
     * @return The default config directory path
     */
    private static Path getDefaultConfigDirectory() {
        String home = System.getProperty("user.home");
        return Paths.get(home, CONFIG_DIR_NAME, APP_NAME);
    }

    /**
     * Gets the default data directory (~/.local/share/open-media-converter).
     *
     * @return The default data directory path
     */
    private static Path getDefaultDataDirectory() {
        String home = System.getProperty("user.home");
        return Paths.get(home, DATA_DIR_NAME, APP_NAME);
    }

    /**
     * Gets the default cache directory (~/.cache/open-media-converter).
     *
     * @return The default cache directory path
     */
    private static Path getDefaultCacheDirectory() {
        String home = System.getProperty("user.home");
        return Paths.get(home, CACHE_DIR_NAME, APP_NAME);
    }

    /**
     * Initializes all required directories, creating them if they don't exist.
     */
    private void initializeDirectories() {
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
     */
    private void createDirectoryIfNotExists(Path directory) {
        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
                logger.info("Created directory: {}", directory);
            } catch (IOException e) {
                logger.error("Failed to create directory: {}", directory, e);
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
     * Removes all files in the temp directory.
     */
    public void cleanupTempFiles() {
        try {
            if (Files.exists(tempDirectory)) {
                Files.walk(tempDirectory)
                        .filter(Files::isRegularFile)
                        .forEach(file -> {
                            try {
                                Files.delete(file);
                                logger.debug("Deleted temp file: {}", file);
                            } catch (IOException e) {
                                logger.warn("Failed to delete temp file: {}", file, e);
                            }
                        });
            }
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
