package org.omc.service;

import org.omc.core.ConfigurationManager;
import org.omc.model.ToolConfiguration;
import org.omc.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    private static final String IMAGEMAGICK_MAGICK_NAME = "magick";

    /**
     * ImageMagick candidate binary names in probe order: the IM6-era
     * "convert" stays preferred, with IM7's "magick" as fallback for pure
     * IM7 installs that ship no "convert" binary ("magick input ... output"
     * accepts the same convert-style arguments this app builds).
     * Package-visible so tests can pin the production probe order.
     */
    static final String[] IMAGEMAGICK_CANDIDATE_NAMES = { IMAGEMAGICK_NAME, IMAGEMAGICK_MAGICK_NAME };

    // Version detection patterns
    // Release builds print "n7.0.1"/"6.1.1"; git-snapshot builds print
    // "N-110753-g1234abc" and are reported as their N-<build> identifier
    private static final Pattern FFMPEG_VERSION_PATTERN = Pattern.compile("ff(?:mpeg|probe) version n?([\\d.]+|N-\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PANDOC_VERSION_PATTERN = Pattern.compile("pandoc ([\\d.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIBREOFFICE_VERSION_PATTERN = Pattern.compile("LibreOffice ([\\d.]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGEMAGICK_VERSION_PATTERN = Pattern.compile("ImageMagick ([\\d.-]+)",
            Pattern.CASE_INSENSITIVE);

    // Process execution timeout (milliseconds)
    private static final long VERSION_CHECK_TIMEOUT = 5000;

    // Temporary-file suffix used for the atomic tools.json write
    private static final String TEMP_SUFFIX = ".tmp";

    private final Path toolsConfigPath;
    private final Path extractedToolsDirectory;

    /**
     * Creates a new tool discovery service.
     * 
     * @param configurationManager the configuration manager for accessing paths
     */
    public ToolDiscovery(ConfigurationManager configurationManager) {
        this.toolsConfigPath = configurationManager.getToolsConfigPath();
        this.extractedToolsDirectory = configurationManager.getCacheDirectory().resolve("tools");
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
            // Revalidate cached paths: a deleted/upgraded binary must not
            // leave a dead path in tools.json until manual rediscovery.
            List<String> missing = validateConfiguration(loaded.get());
            if (missing.isEmpty()) {
                logger.info("Loaded tool configuration from disk");
                return loaded.get();
            }
            logger.warn("Cached tool configuration stale (unavailable: {}), rediscovering", missing);
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
        discoverImageMagick(config, System.getenv("PATH"), IMAGEMAGICK_CANDIDATE_NAMES);
    }

    /**
     * Discovers ImageMagick by probing the candidate binary names in order
     * against the well-known system directories first and then the given
     * PATH. Package-visible so tests can probe controlled candidate names
     * against a fake tool directory regardless of the host installation.
     *
     * @param config         the configuration to update
     * @param pathEnv        the PATH environment variable to search
     * @param candidateNames binary names to probe, in preference order
     */
    void discoverImageMagick(ToolConfiguration config, String pathEnv, String[] candidateNames) {
        // ImageMagick is typically not embedded due to size and complexity
        // Try system ImageMagick
        Optional<Path> systemConvert = Optional.empty();
        for (String candidateName : candidateNames) {
            systemConvert = findSystemBinary(candidateName, pathEnv);
            if (systemConvert.isPresent()) {
                break;
            }
        }
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
            logger.warn("ImageMagick ({}) not found in system paths", String.join("/", candidateNames));
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

            // Read output on a daemon thread with a byte cap so a
            // malicious/hung binary emitting infinite output without EOF can
            // never block version detection forever: waitFor(timeout) below
            // always gets a chance to fire.
            java.util.concurrent.Future<String> outputFuture = readStreamAsync(process);
            String captured;
            try {
                captured = outputFuture.get(VERSION_CHECK_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                outputFuture.cancel(true);
                process.destroyForcibly();
                logger.warn("ImageMagick version detection timed out for: {}", convertPath);
                return Optional.empty();
            } catch (java.util.concurrent.ExecutionException e) {
                logger.warn("ImageMagick version detection failed for {}: {}", convertPath,
                        e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                process.destroyForcibly();
                return Optional.empty();
            }
            StringBuilder output = new StringBuilder(captured);
            // ImageMagick version is in the first line; keep only a prefix
            if (output.length() > 500) {
                output.setLength(500);
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
        String machine = System.getProperty("os.arch", "");
        String arch = switch (machine) {
            case "amd64", "x86_64" -> "linux-x86_64";
            case "aarch64", "arm64" -> "linux-aarch64";
            default -> null;
        };
        if (arch == null || !System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux")) {
            return Optional.empty();
        }
        String tool = binaryName.equals(FFPROBE_NAME) ? FFMPEG_NAME : binaryName;
        String resourcePath = EMBEDDED_BIN_PATH + arch + "/" + tool + "/" + binaryName;
        ClassLoader loader = getClass().getClassLoader();
        Path temporary = null;
        try (var checksumResource = loader.getResourceAsStream(resourcePath + ".sha256")) {
            if (checksumResource == null) return Optional.empty();
            // Accept coreutils "<hash>  <file>" format (hash is the first
            // token) and uppercase hex digests.
            String raw = new String(checksumResource.readNBytes(256), java.nio.charset.StandardCharsets.UTF_8).trim();
            String checksum = raw.split("\\s+")[0].toLowerCase(java.util.Locale.ROOT);
            if (!checksum.matches("[a-f0-9]{64}")) throw new IOException("Invalid embedded tool checksum");
            Path directory = extractedToolsDirectory.resolve(checksum);
            Files.createDirectories(directory);
            Path executable = directory.resolve(binaryName);
            if (Files.isRegularFile(executable) && checksum.equals(binaryChecksum(executable))) {
                makeExecutable(executable);
                return Optional.of(executable);
            }
            temporary = Files.createTempFile(directory, binaryName, ".part");
            try (var resource = loader.getResourceAsStream(resourcePath)) {
                if (resource == null) return Optional.empty();
                Files.copy(resource, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (!checksum.equals(binaryChecksum(temporary))) throw new IOException("Embedded tool checksum mismatch");
            makeExecutable(temporary);
            try {
                Files.move(temporary, executable, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, executable, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return Optional.of(executable);
        } catch (IOException | SecurityException e) {
            logger.warn("Could not extract embedded {}: {}", binaryName, e.getMessage());
            return Optional.empty();
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException e) { logger.debug("Could not remove extraction temporary file", e); }
            }
        }
    }

    private static String binaryChecksum(Path executable) throws IOException {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var input = new java.security.DigestInputStream(Files.newInputStream(executable), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", e);
        }
    }

    private static void makeExecutable(Path executable) throws IOException {
        try {
            Files.setPosixFilePermissions(executable, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException e) {
            if (!executable.toFile().setExecutable(true, true)) throw new IOException("Cannot mark tool executable");
        }
    }

    /**
     * Finds a binary in system paths.
     * 
     * @param binaryName the name of the binary
     * @return the path to the binary, or empty if not found
     */
    private Optional<Path> findSystemBinary(String binaryName) {
        return findSystemBinary(binaryName, System.getenv("PATH"));
    }

    /**
     * Finds a binary in system paths, probing an explicit $PATH value.
     *
     * @param binaryName the name of the binary
     * @param pathEnv    the PATH environment variable to search
     * @return the path to the binary, or empty if not found
     */
    Optional<Path> findSystemBinary(String binaryName, String pathEnv) {
        // Check well-known system directories FIRST so a malicious binary
        // planted early on $PATH cannot hijack discovery (PATH hijack).
        for (String systemPath : SYSTEM_PATHS) {
            Path binaryPath = Paths.get(systemPath, binaryName);
            if (isTrustedBinary(binaryPath, binaryName)) {
                return Optional.of(binaryPath);
            }
        }

        // Fall back to $PATH entries, still requiring the binary to report a
        // parseable version (proves it is the real tool, not a shim).
        // Empty segments are skipped: Paths.get("", name) resolves relative
        // to the working directory and would probe/execute an untrusted
        // binary planted there.
        if (pathEnv != null) {
            String[] paths = pathEnv.split(":");
            for (String pathDir : paths) {
                if (pathDir.isBlank()) {
                    continue; // Never resolve against the working directory
                }
                Path binaryPath = Paths.get(pathDir, binaryName);
                if (isTrustedBinary(binaryPath, binaryName)) {
                    return Optional.of(binaryPath);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * A system binary is trusted only if it is a regular executable file AND
     * actually runs (exits 0 with version output). This rejects broken
     * symlinks, wrapper shims that fail, and wrong-tool name collisions,
     * while tolerating future version-string formats (any non-blank output
     * is accepted, not just currently parseable versions).
     */
    private boolean isTrustedBinary(Path binaryPath, String binaryName) {
        if (!Files.isRegularFile(binaryPath) || !Files.isExecutable(binaryPath)) {
            return false;
        }
        try {
            String flag = "--version";
            if (binaryName.equals(FFMPEG_NAME) || binaryName.equals(FFPROBE_NAME)) {
                flag = "-version";
            }
            ProcessBuilder pb = new ProcessBuilder(binaryPath.toString(), flag);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            java.util.concurrent.Future<String> out = readStreamAsync(process);
            String captured;
            try {
                captured = out.get(VERSION_CHECK_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException e) {
                out.cancel(true);
                process.destroyForcibly();
                logger.debug("Rejecting unrunnable binary {}: {}", binaryPath, e.getMessage());
                return false;
            }
            boolean finished = process.waitFor(VERSION_CHECK_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            boolean ok = process.exitValue() == 0 && !captured.isBlank();
            if (!ok) {
                logger.debug("Rejecting binary with no version output: {}", binaryPath);
            }
            return ok;
        } catch (IOException | RuntimeException e) {
            logger.debug("Rejecting untrusted binary {}: {}", binaryPath, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Interrupted while probing binary {}: {}", binaryPath, e.getMessage());
            return false;
        }
    }

    /**
     * Detects the version of a tool by executing it with a version flag.
     * 
     * @param toolPath    the path to the tool executable
     * @param versionFlag the version flag to use (e.g., "--version")
     * @return the detected version string, or empty if detection failed
     */
    /**
     * Reads a process output stream asynchronously with a byte cap.
     * The reader thread is daemonized so it can never pin JVM shutdown, and
     * output is capped at 64KB so runaway binaries cannot OOM discovery.
     */
    private static java.util.concurrent.Future<String> readStreamAsync(Process process) {
        java.util.concurrent.ExecutorService reader = java.util.concurrent.Executors
                .newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "tool-discovery-reader");
                    t.setDaemon(true);
                    return t;
                });
        java.util.concurrent.Future<String> future = reader.submit(() -> {
            StringBuilder output = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (output.length() + line.length() + 1 > 64 * 1024) {
                        break;
                    }
                    output.append(line).append("\n");
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                }
            } catch (IOException e) {
                logger.debug("Version probe stream closed: {}", e.getMessage());
            }
            return output.toString();
        });
        reader.shutdown();
        return future;
    }

    private Optional<String> detectVersion(Path toolPath, String versionFlag) {
        try {
            ProcessBuilder pb = new ProcessBuilder(toolPath.toString(), versionFlag);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Async read: a binary emitting infinite output without EOF must
            // not block detection; waitFor(timeout) below must always fire.
            java.util.concurrent.Future<String> outputFuture = readStreamAsync(process);
            String captured;
            try {
                captured = outputFuture.get(VERSION_CHECK_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                outputFuture.cancel(true);
                process.destroyForcibly();
                logger.warn("Version detection timed out for: {}", toolPath);
                return Optional.empty();
            } catch (java.util.concurrent.ExecutionException e) {
                logger.warn("Failed to detect version for {}: {}", toolPath,
                        e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                process.destroyForcibly();
                return Optional.empty();
            }

            // Wait for process to complete with timeout
            boolean completed = process.waitFor(VERSION_CHECK_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                logger.warn("Version detection timed out for: {}", toolPath);
                return Optional.empty();
            }

            // Parse version from output
            return parseVersion(toolPath.getFileName().toString(), captured);

        } catch (IOException e) {
            logger.warn("Failed to detect version for {}: {}", toolPath, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Version detection interrupted for {}: {}", toolPath, e.getMessage());
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
     * <p>
     * Writes to a temporary file first and then renames it over the final
     * path (preferring an atomic move, with a plain replacement move as
     * fallback for filesystems without atomic move support), mirroring the
     * write pattern of {@code SettingsManager}/{@code StateManager}. The
     * write itself goes through {@link JsonUtils#writeJsonFile}, which
     * forces the data to physical storage before the rename.
     * </p>
     *
     * @param config the configuration to save
     */
    private void saveConfiguration(ToolConfiguration config) {
        Path tempPath = Path.of(toolsConfigPath.toString() + TEMP_SUFFIX);
        try {
            // Ensure parent directory exists
            Files.createDirectories(toolsConfigPath.getParent());

            // Write to temporary file first (fsync'd by JsonUtils), then
            // atomically rename over the final path
            JsonUtils.writeJsonFile(config, tempPath.toFile());
            moveWithAtomicFallback(tempPath, toolsConfigPath);
            logger.info("Saved tool configuration to: {}", toolsConfigPath);
        } catch (IOException e) {
            logger.error("Failed to save tool configuration: {}", e.getMessage(), e);
        } finally {
            // Clean up the temp file whether the move failed or never
            // happened; after a successful move this is a no-op
            deleteQuietly(tempPath);
        }
    }

    /**
     * Moves {@code source} to {@code target}, preferring an atomic move.
     *
     * <p>
     * Some filesystems (certain network mounts, FAT/exFAT) do not support
     * atomic moves; {@link Files#move} then throws
     * {@link AtomicMoveNotSupportedException}, which is unchecked and would
     * otherwise escape the {@code catch (IOException)} handler. This helper
     * catches it and retries with a plain
     * {@link StandardCopyOption#REPLACE_EXISTING} move, logging a warning.
     * </p>
     *
     * @param source the file to move (typically the temp file)
     * @param target the destination file
     * @throws IOException if both the atomic and the fallback move fail
     */
    private static void moveWithAtomicFallback(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            logger.warn("Atomic move not supported for {} ({}); falling back to a non-atomic move",
                    target, e.getMessage());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Deletes a file if it exists, swallowing IOExceptions (best effort).
     * Used for temp-file cleanup in {@code finally} blocks where the
     * original outcome must not be masked.
     *
     * @param file the file to delete, may no longer exist
     */
    private static void deleteQuietly(Path file) {
        try {
            if (Files.exists(file)) {
                Files.delete(file);
            }
        } catch (IOException e) {
            logger.warn("Failed to delete temp file: {}", file, e);
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
