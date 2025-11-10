package org.omc.service;

import org.omc.core.ConfigurationManager;
import org.omc.model.ToolConfiguration;
import org.omc.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for discovering and validating external conversion tools.
 * Searches for embedded binaries first, then falls back to system binaries.
 * 
 * Requirements: REQ-004.1
 */
public class ToolDiscovery {
    private static final Logger logger = LoggerFactory.getLogger(ToolDiscovery.class);

    // Embedded binary paths (relative to classpath)
    private static final String EMBEDDED_BIN_PATH = "bin/";

    // System binary search paths
    private static final String[] SYSTEM_PATHS = {
            "/usr/bin/",
            "/usr/local/bin/",
            "/opt/bin/",
            "/snap/bin/"
    };

    // Tool executable names
    private static final String FFMPEG_NAME = "ffmpeg";
    private static final String FFPROBE_NAME = "ffprobe";
    private static final String PANDOC_NAME = "pandoc";
    private static final String LIBREOFFICE_NAME = "soffice";
    private static final String IMAGEMAGICK_NAME = "convert";

    // Version detection patterns
    private static final Pattern FFMPEG_VERSION_PATTERN = Pattern.compile("ffmpeg version ([\\d.]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PANDOC_VERSION_PATTERN = Pattern.compile("pandoc ([\\d.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIBREOFFICE_VERSION_PATTERN = Pattern.compile("LibreOffice ([\\d.]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGEMAGICK_VERSION_PATTERN = Pattern.compile("ImageMagick ([\\d.-]+)",
            Pattern.CASE_INSENSITIVE);

    // Process execution timeout (milliseconds)
    private static final long VERSION_CHECK_TIMEOUT = 5000;

    private final Path toolsConfigPath;

    /**
     * Creates a new tool discovery service.
     * 
     * @param configurationManager the configuration manager for accessing paths
     */
    public ToolDiscovery(ConfigurationManager configurationManager) {
        this.toolsConfigPath = configurationManager.getToolsConfigPath();
    }

    /**
     * Discovers all available tools and returns their configuration.
     * Searches for embedded binaries first, then system binaries.
     * Saves the configuration to tools.json for future use.
     * 
     * @return tool configuration with discovered tool paths
     */
    public ToolConfiguration discoverTools() {
        logger.info("Starting tool discovery...");

        ToolConfiguration config = new ToolConfiguration();

        // Discover FFmpeg and ffprobe
        discoverFfmpeg(config);

        // Discover Pandoc
        discoverPandoc(config);

        // Discover LibreOffice
        discoverLibreOffice(config);

        // Discover ImageMagick
        discoverImageMagick(config);

        // Save configuration
        saveConfiguration(config);

        logger.info("Tool discovery complete: {}", config);
        return config;
    }

    /**
     * Loads tool configuration from disk if available,
     * otherwise performs discovery.
     * 
     * @return tool configuration
     */
    public ToolConfiguration loadOrDiscoverTools() {
        Optional<ToolConfiguration> loaded = loadConfiguration();
        if (loaded.isPresent()) {
            logger.info("Loaded tool configuration from disk");
            return loaded.get();
        }

        logger.info("No tool configuration found, performing discovery");
        return discoverTools();
    }

    /**
     * Discovers FFmpeg and ffprobe binaries.
     * 
     * @param config the configuration to update
     */
    private void discoverFfmpeg(ToolConfiguration config) {
        // Try embedded FFmpeg
        Optional<Path> embeddedFfmpeg = findEmbeddedBinary(FFMPEG_NAME);
        Optional<Path> embeddedFfprobe = findEmbeddedBinary(FFPROBE_NAME);

        if (embeddedFfmpeg.isPresent() && embeddedFfprobe.isPresent()) {
            logger.info("Found embedded FFmpeg: {}", embeddedFfmpeg.get());
            config.setFfmpegPath(embeddedFfmpeg.get());
            config.setFfprobePath(embeddedFfprobe.get());

            // Detect version (FFmpeg uses single-dash flag)
            detectVersion(embeddedFfmpeg.get(), "-version")
                    .ifPresent(version -> {
                        config.setFfmpegVersion(version);
                        logger.info("FFmpeg version: {}", version);
                    });
            return;
        }

        // Try system FFmpeg
        Optional<Path> systemFfmpeg = findSystemBinary(FFMPEG_NAME);
        Optional<Path> systemFfprobe = findSystemBinary(FFPROBE_NAME);

        if (systemFfmpeg.isPresent() && systemFfprobe.isPresent()) {
            logger.info("Found system FFmpeg: {}", systemFfmpeg.get());
            config.setFfmpegPath(systemFfmpeg.get());
            config.setFfprobePath(systemFfprobe.get());

            // Detect version (FFmpeg uses single-dash flag)
            detectVersion(systemFfmpeg.get(), "-version")
                    .ifPresent(version -> {
                        config.setFfmpegVersion(version);
                        logger.info("FFmpeg version: {}", version);
                    });
        } else {
            logger.warn("FFmpeg not found in system paths");
        }
    }

    /**
     * Discovers Pandoc binary.
     * 
     * @param config the configuration to update
     */
    private void discoverPandoc(ToolConfiguration config) {
        // Try embedded Pandoc
        Optional<Path> embeddedPandoc = findEmbeddedBinary(PANDOC_NAME);
        if (embeddedPandoc.isPresent()) {
            logger.info("Found embedded Pandoc: {}", embeddedPandoc.get());
            config.setPandocPath(embeddedPandoc.get());

            // Detect version
            detectVersion(embeddedPandoc.get(), "--version")
                    .ifPresent(version -> {
                        config.setPandocVersion(version);
                        logger.info("Pandoc version: {}", version);
                    });
            return;
        }

        // Try system Pandoc
        Optional<Path> systemPandoc = findSystemBinary(PANDOC_NAME);
        if (systemPandoc.isPresent()) {
            logger.info("Found system Pandoc: {}", systemPandoc.get());
            config.setPandocPath(systemPandoc.get());

            // Detect version
            detectVersion(systemPandoc.get(), "--version")
                    .ifPresent(version -> {
                        config.setPandocVersion(version);
                        logger.info("Pandoc version: {}", version);
                    });
        } else {
            logger.warn("Pandoc not found in system paths");
        }
    }

    /**
     * Discovers LibreOffice binary.
     * 
     * @param config the configuration to update
     */
    private void discoverLibreOffice(ToolConfiguration config) {
        // LibreOffice is typically not embedded due to size
        // Try system LibreOffice
        Optional<Path> systemLibreOffice = findSystemBinary(LIBREOFFICE_NAME);
        if (systemLibreOffice.isPresent()) {
            logger.info("Found system LibreOffice: {}", systemLibreOffice.get());
            config.setLibreOfficePath(systemLibreOffice.get());

            // Detect version
            detectVersion(systemLibreOffice.get(), "--version")
                    .ifPresent(version -> {
                        config.setLibreOfficeVersion(version);
                        logger.info("LibreOffice version: {}", version);
                    });
        } else {
            logger.warn("LibreOffice not found in system paths");
        }
    }

    /**
     * Discovers ImageMagick convert binary.
     * 
     * Requirement REQ-IMG-1: ImageMagick tool discovery
     * 
     * @param config the configuration to update
     */
    private void discoverImageMagick(ToolConfiguration config) {
        // ImageMagick is typically not embedded due to size and complexity
        // Try system ImageMagick
        Optional<Path> systemConvert = findSystemBinary(IMAGEMAGICK_NAME);
        if (systemConvert.isPresent()) {
            logger.info("Found system ImageMagick convert: {}", systemConvert.get());
            config.setConvertPath(systemConvert.get());

            // Detect version
            detectImageMagickVersion(systemConvert.get())
                    .ifPresent(version -> {
                        config.setConvertVersion(version);
                        logger.info("ImageMagick version: {}", version);
                    });
        } else {
            logger.warn("ImageMagick 'convert' not found in system paths");
        }
    }

    /**
     * Detects ImageMagick version by executing "convert --version".
     * 
     * Requirement REQ-IMG-1: ImageMagick version detection
     * 
     * @param convertPath the path to the convert executable
     * @return the detected version string, or empty if detection failed
     */
    private Optional<String> detectImageMagickVersion(Path convertPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(convertPath.toString(), "--version");
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    // ImageMagick version is in the first line, no need to read all output
                    if (output.length() > 500) {
                        break;
                    }
                }
            }

            // Wait for process to complete with timeout
            boolean completed = process.waitFor(VERSION_CHECK_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                logger.warn("ImageMagick version detection timed out for: {}", convertPath);
                return Optional.empty();
            }

            // Parse version from output
            // Expected format: "Version: ImageMagick 7.1.0-62 Q16 x86_64..."
            Matcher matcher = IMAGEMAGICK_VERSION_PATTERN.matcher(output.toString());
            if (matcher.find()) {
                String version = matcher.group(1);
                logger.debug("Detected ImageMagick version: {}", version);
                return Optional.of(version);
            }

            logger.warn("Could not parse ImageMagick version from output: {}",
                    output.toString().substring(0, Math.min(200, output.length())));
            return Optional.empty();

        } catch (IOException e) {
            logger.warn("Failed to detect ImageMagick version for {}: {}", convertPath, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            logger.warn("ImageMagick version detection interrupted for {}: {}", convertPath, e.getMessage());
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Finds an embedded binary in the classpath resources.
     * 
     * @param binaryName the name of the binary
     * @return the path to the embedded binary, or empty if not found
     */
    private Optional<Path> findEmbeddedBinary(String binaryName) {
        try {
            // Try to extract embedded binary to temp directory
            String resourcePath = EMBEDDED_BIN_PATH + binaryName;
            var resource = getClass().getClassLoader().getResource(resourcePath);

            if (resource == null) {
                logger.debug("Embedded binary not found: {}", resourcePath);
                return Optional.empty();
            }

            // For now, return empty - full extraction logic would go here
            // In production, we would extract the binary to a temp location
            logger.debug("Embedded binary found but extraction not implemented: {}", resourcePath);
            return Optional.empty();

        } catch (Exception e) {
            logger.debug("Error finding embedded binary {}: {}", binaryName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Finds a binary in system paths.
     * 
     * @param binaryName the name of the binary
     * @return the path to the binary, or empty if not found
     */
    private Optional<Path> findSystemBinary(String binaryName) {
        // Check standard system paths
        for (String systemPath : SYSTEM_PATHS) {
            Path binaryPath = Paths.get(systemPath, binaryName);
            if (Files.isExecutable(binaryPath)) {
                return Optional.of(binaryPath);
            }
        }

        // Check PATH environment variable
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] paths = pathEnv.split(":");
            for (String pathDir : paths) {
                Path binaryPath = Paths.get(pathDir, binaryName);
                if (Files.isExecutable(binaryPath)) {
                    return Optional.of(binaryPath);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Detects the version of a tool by executing it with a version flag.
     * 
     * @param toolPath    the path to the tool executable
     * @param versionFlag the version flag to use (e.g., "--version")
     * @return the detected version string, or empty if detection failed
     */
    private Optional<String> detectVersion(Path toolPath, String versionFlag) {
        try {
            ProcessBuilder pb = new ProcessBuilder(toolPath.toString(), versionFlag);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // Wait for process to complete with timeout
            boolean completed = process.waitFor(VERSION_CHECK_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                logger.warn("Version detection timed out for: {}", toolPath);
                return Optional.empty();
            }

            // Parse version from output
            return parseVersion(toolPath.getFileName().toString(), output.toString());

        } catch (IOException | InterruptedException e) {
            logger.warn("Failed to detect version for {}: {}", toolPath, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parses the version string from tool output.
     * 
     * @param toolName the name of the tool
     * @param output   the tool output
     * @return the parsed version, or empty if parsing failed
     */
    private Optional<String> parseVersion(String toolName, String output) {
        Pattern pattern = switch (toolName) {
            case FFMPEG_NAME, FFPROBE_NAME -> FFMPEG_VERSION_PATTERN;
            case PANDOC_NAME -> PANDOC_VERSION_PATTERN;
            case LIBREOFFICE_NAME -> LIBREOFFICE_VERSION_PATTERN;
            case IMAGEMAGICK_NAME -> IMAGEMAGICK_VERSION_PATTERN;
            default -> null;
        };

        if (pattern == null) {
            return Optional.empty();
        }

        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }

        return Optional.empty();
    }

    /**
     * Loads tool configuration from disk.
     * 
     * @return the loaded configuration, or empty if file doesn't exist or is
     *         invalid
     */
    private Optional<ToolConfiguration> loadConfiguration() {
        if (!Files.exists(toolsConfigPath)) {
            return Optional.empty();
        }

        try {
            ToolConfiguration config = JsonUtils.readJsonFile(toolsConfigPath.toFile(), ToolConfiguration.class);
            return Optional.of(config);
        } catch (IOException e) {
            logger.warn("Failed to load tool configuration: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Saves tool configuration to disk.
     * 
     * @param config the configuration to save
     */
    private void saveConfiguration(ToolConfiguration config) {
        try {
            // Ensure parent directory exists
            Files.createDirectories(toolsConfigPath.getParent());

            JsonUtils.writeJsonFile(config, toolsConfigPath.toFile());
            logger.info("Saved tool configuration to: {}", toolsConfigPath);
        } catch (IOException e) {
            logger.error("Failed to save tool configuration: {}", e.getMessage(), e);
        }
    }

    /**
     * Checks if a specific tool is available.
     * 
     * @param toolPath the path to the tool
     * @return true if the tool exists and is executable
     */
    public boolean isToolAvailable(Path toolPath) {
        if (toolPath == null) {
            return false;
        }
        return Files.isExecutable(toolPath);
    }

    /**
     * Validates a tool configuration by checking if all configured tools are
     * accessible.
     * 
     * @param config the configuration to validate
     * @return list of tool names that are not accessible
     */
    public List<String> validateConfiguration(ToolConfiguration config) {
        List<String> unavailable = new ArrayList<>();

        if (config.getFfmpegPath() != null && !Files.isExecutable(config.getFfmpegPath())) {
            unavailable.add("ffmpeg");
        }

        if (config.getFfprobePath() != null && !Files.isExecutable(config.getFfprobePath())) {
            unavailable.add("ffprobe");
        }

        if (config.getPandocPath() != null && !Files.isExecutable(config.getPandocPath())) {
            unavailable.add("pandoc");
        }

        if (config.getLibreOfficePath() != null && !Files.isExecutable(config.getLibreOfficePath())) {
            unavailable.add("libreoffice");
        }

        if (config.getConvertPath() != null && !Files.isExecutable(config.getConvertPath())) {
            unavailable.add("imagemagick");
        }

        return unavailable;
    }
}
