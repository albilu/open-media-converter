package org.omc.core;

import org.omc.exception.FileOperationException;
import org.omc.model.AudioSettings;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionSettings;
import org.omc.model.ConversionTool;
import static org.omc.model.ConversionTool.FFMPEG;
import static org.omc.model.ConversionTool.LIBREOFFICE;
import static org.omc.model.ConversionTool.PANDOC;
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;
import org.omc.model.FormatCategory;
import static org.omc.model.FormatCategory.AUDIO;
import static org.omc.model.FormatCategory.DOCUMENT;
import static org.omc.model.FormatCategory.IMAGE;
import static org.omc.model.FormatCategory.UNKNOWN;
import static org.omc.model.FormatCategory.VIDEO;
import org.omc.model.ImageSettings;
import org.omc.model.Resolution;
import org.omc.model.ValidationResult;
import org.omc.model.VideoSettings;
import org.omc.service.FileHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Engine for validating files, settings, and system resources before conversion
 * operations.
 *
 * Provides comprehensive validation including: - File existence, readability,
 * and format validation - Settings validation for all conversion types - Format
 * pair compatibility checking - System resource validation (disk space, output
 * directory)
 *
 * Requirements: REQ-002.3, REQ-003.2, REQ-007.1
 */
public class ValidationEngine {

    private static final Logger logger = LoggerFactory.getLogger(ValidationEngine.class);

    // Validation thresholds
    private static final long MIN_DISK_SPACE_BUFFER = 500 * 1024 * 1024; // 500 MB safety buffer
    /**
     * Parallelism bounds shared with ConversionEngine and the settings-dialog
     * validation in ApplicationWorkflowController (single source of truth).
     */
    public static final int MIN_PARALLEL_CONVERSIONS = 1;
    /**
     * @see #MIN_PARALLEL_CONVERSIONS
     */
    public static final int MAX_PARALLEL_CONVERSIONS = 16;

    // Media bounds live in the model classes (VideoSettings, AudioSettings,
    // ImageSettings) as the single source of truth; JSON-deserialized settings
    // bypass Builder validation, so the engine must enforce exactly the same
    // bounds the model's isValid() enforces.

    // Image dimension validation ranges
    private static final int MIN_IMAGE_DIMENSION = 1;
    private static final int MAX_IMAGE_DIMENSION = 65535;

    private final FileHandler fileHandler;
    private volatile org.omc.model.ToolConfiguration toolConfiguration;

    /**
     * Cached tool-availability probes. Spawning {@code -version} per file on a
     * worker thread was O(files) process spawns with 10s waits; results are
     * cached per tool with a TTL and invalidated on reconfiguration.
     */
    private final java.util.concurrent.ConcurrentHashMap<ConversionTool, CachedAvailability> availabilityCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long AVAILABILITY_CACHE_TTL_MS = 60_000;

    private record CachedAvailability(ValidationResult result, long checkedAt) {
    }

    /**
     * Uses the same discovered binaries for validation and conversion.
     * @param configuration installed and extracted tool paths
     */
    public void setToolConfiguration(org.omc.model.ToolConfiguration configuration) {
        toolConfiguration = Objects.requireNonNull(configuration, "configuration");
        availabilityCache.clear();
    }

    /**
     * Saturating doubling for disk estimates: a huge input must not wrap
     * {@code value * 2} negative and silently pass validation.
     *
     * @param value bytes to double
     * @return {@code value * 2}, saturating at {@link Long#MAX_VALUE}
     */
    public static long saturatedDouble(long value) {
        return value > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : value * 2;
    }

    /**
     * Saturating addition for disk estimates.
     *
     * @param a first addend in bytes
     * @param b second addend in bytes
     * @return {@code a + b}, saturating at {@link Long#MAX_VALUE}
     */
    public static long saturatedAdd(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    /**
     * Estimates the disk space a pre-flight validation should require for an
     * input file of the given size, using a deliberately conservative
     * {@code 2x} policy via {@link #saturatedDouble(long)}.
     *
     * <p>
     * This intentionally differs from ConversionEngine's runtime heuristic
     * (~0.8x of the input, estimating the compressed output size once the
     * actual tool and per-file settings are known). Pre-flight validation
     * runs before those details exist and its failure only blocks a job that
     * has not started yet, so erring high is cheap here: a false "tight
     * space" warning is recoverable, while an under-estimate lets a
     * conversion start and fail halfway after wasting time and disk.
     * </p>
     *
     * @param fileSize input file size in bytes; a negative value yields a
     *                 negative estimate which {@code validateDiskSpace}
     *                 rejects as invalid input
     * @return estimated required bytes, saturating at {@link Long#MAX_VALUE}
     */
    public static long estimateRequiredBytes(long fileSize) {
        return saturatedDouble(fileSize);
    }

    /**
     * Creates a new ValidationEngine.
     *
     * @param fileHandler File handler for file system operations
     */
    public ValidationEngine(FileHandler fileHandler) {
        this.fileHandler = Objects.requireNonNull(fileHandler, "fileHandler cannot be null");
        logger.debug("ValidationEngine initialized");
    }

    /**
     * Validates a file for conversion. Checks existence, readability, and
     * format detection.
     *
     * Requirement REQ-002.3: File validation
     *
     * @param filePath Path to the file
     * @return Validation result
     */
    public ValidationResult validateFile(Path filePath) {
        if (filePath == null) {
            return ValidationResult.failure("File path cannot be null");
        }

        logger.debug("Validating file: {}", filePath);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check existence
        if (!fileHandler.exists(filePath)) {
            errors.add("File does not exist: " + filePath);
            return ValidationResult.failure(errors);
        }

        // Check readability
        if (!fileHandler.isReadable(filePath)) {
            errors.add("File is not readable: " + filePath);
            return ValidationResult.failure(errors);
        }

        // Check if it's a regular file
        if (!Files.isRegularFile(filePath)) {
            errors.add("Path is not a regular file: " + filePath);
            return ValidationResult.failure(errors);
        }

        // Check file size
        try {
            long size = fileHandler.getFileSize(filePath);
            if (size == 0) {
                warnings.add("File is empty: " + filePath);
            }
        } catch (FileOperationException e) {
            errors.add("Cannot determine file size: " + e.getMessage());
        }

        // Try to detect format
        FileFormat format = fileHandler.detectFormat(filePath);
        if (format == FileFormat.UNKNOWN) {
            warnings.add("Unknown or unsupported file format: " + filePath);
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors, warnings);
        }

        if (!warnings.isEmpty()) {
            return ValidationResult.successWithWarnings(warnings);
        }

        logger.debug("File validation successful: {}", filePath);
        return ValidationResult.success();
    }

    /**
     * Validates a file format matches expected format.
     *
     * Requirement REQ-002.3: Format validation
     *
     * @param filePath       Path to the file
     * @param expectedFormat Expected file format
     * @return Validation result
     */
    public ValidationResult validateFileFormat(Path filePath, FileFormat expectedFormat) {
        if (filePath == null) {
            return ValidationResult.failure("File path cannot be null");
        }
        if (expectedFormat == null) {
            return ValidationResult.failure("Expected format cannot be null");
        }

        logger.debug("Validating file format: {} against {}", filePath, expectedFormat);

        // First validate the file itself
        ValidationResult fileResult = validateFile(filePath);
        if (fileResult.isFailure()) {
            return fileResult;
        }

        // Detect actual format
        FileFormat actualFormat = fileHandler.detectFormat(filePath);

        if (actualFormat == FileFormat.UNKNOWN) {
            return ValidationResult.failure("Cannot determine file format: " + filePath);
        }

        if (actualFormat != expectedFormat) {
            String error = String.format("File format mismatch: expected %s but found %s for file %s",
                    expectedFormat, actualFormat, filePath);
            return ValidationResult.failure(error);
        }

        logger.debug("File format validation successful: {}", filePath);
        return fileResult; // Preserve any warnings from file validation
    }

    /**
     * Validates conversion settings.
     *
     * Requirement REQ-003.2: Settings validation
     *
     * @param settings Conversion settings
     * @return Validation result
     */
    public ValidationResult validateSettings(ConversionSettings settings) {
        if (settings == null) {
            return ValidationResult.failure("Conversion settings cannot be null");
        }

        logger.debug("Validating conversion settings");
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate output directory
        if (settings.outputDirectory() == null) {
            errors.add("Output directory cannot be null");
        } else if (!settings.outputDirectory().toFile().exists()) {
            errors.add("Output directory does not exist: " + settings.outputDirectory());
        } else if (!settings.outputDirectory().toFile().canWrite()) {
            errors.add("Output directory is not writable: " + settings.outputDirectory());
        }

        // Validate parallel conversions
        if (settings.parallelConversions() < MIN_PARALLEL_CONVERSIONS
                || settings.parallelConversions() > MAX_PARALLEL_CONVERSIONS) {
            errors.add(String.format("Parallel conversions must be between %d and %d (got: %d)",
                    MIN_PARALLEL_CONVERSIONS, MAX_PARALLEL_CONVERSIONS, settings.parallelConversions()));
        }

        // Validate format-specific settings
        // Since output formats are per-section, we validate each section if present
        if (settings.videoSettings() != null) {
            ValidationResult videoResult = validateVideoSettings(settings.videoSettings());
            errors.addAll(videoResult.getErrors());
            warnings.addAll(videoResult.getWarnings());
        }

        if (settings.audioSettings() != null) {
            ValidationResult audioResult = validateAudioSettings(settings.audioSettings());
            errors.addAll(audioResult.getErrors());
            warnings.addAll(audioResult.getWarnings());
        }

        if (settings.imageSettings() != null) {
            ValidationResult imageResult = validateImageSettings(settings.imageSettings());
            errors.addAll(imageResult.getErrors());
            warnings.addAll(imageResult.getWarnings());
        }

        if (settings.documentSettings() != null) {
            ValidationResult docResult = validateDocumentSettings(settings.documentSettings());
            errors.addAll(docResult.getErrors());
            warnings.addAll(docResult.getWarnings());
        }

        if (!errors.isEmpty()) {
            logger.warn("Settings validation failed with {} errors", errors.size());
            return ValidationResult.failure(errors, warnings);
        }

        if (!warnings.isEmpty()) {
            logger.debug("Settings validation succeeded with {} warnings", warnings.size());
            return ValidationResult.successWithWarnings(warnings);
        }

        logger.debug("Settings validation successful");
        return ValidationResult.success();
    }

    /**
     * Validates video settings.
     */
    private ValidationResult validateVideoSettings(VideoSettings settings) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate bitrate
        if (settings.bitrate() < VideoSettings.MIN_BITRATE || settings.bitrate() > VideoSettings.MAX_BITRATE) {
            errors.add(String.format("Video bitrate must be between %d and %d kbps (got: %d)",
                    VideoSettings.MIN_BITRATE, VideoSettings.MAX_BITRATE, settings.bitrate()));
        }

        // Validate frame rate (-1 means use original, which is valid)
        if (settings.frameRate() != -1
                && (settings.frameRate() < VideoSettings.MIN_FRAME_RATE
                        || settings.frameRate() > VideoSettings.MAX_FRAME_RATE)) {
            errors.add(String.format("Frame rate must be -1 (original) or between %d and %d fps (got: %d)",
                    VideoSettings.MIN_FRAME_RATE, VideoSettings.MAX_FRAME_RATE, settings.frameRate()));
        }

        // Validate CRF
        if (settings.crf() < VideoSettings.MIN_CRF || settings.crf() > VideoSettings.MAX_CRF) {
            errors.add(String.format("CRF must be between %d and %d (got: %d)",
                    VideoSettings.MIN_CRF, VideoSettings.MAX_CRF, settings.crf()));
        }

        // Validate resolution
        if (settings.resolution() != null) {
            Resolution res = settings.resolution();
            if (res.getWidth() < MIN_IMAGE_DIMENSION || res.getWidth() > MAX_IMAGE_DIMENSION) {
                errors.add(String.format("Video width must be between %d and %d (got: %d)",
                        MIN_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION, res.getWidth()));
            }
            if (res.getHeight() < MIN_IMAGE_DIMENSION || res.getHeight() > MAX_IMAGE_DIMENSION) {
                errors.add(String.format("Video height must be between %d and %d (got: %d)",
                        MIN_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION, res.getHeight()));
            }
        }

        // Warn about high CRF values
        if (settings.crf() > 35) {
            warnings.add("High CRF value (> 35) may result in poor quality");
        }

        return errors.isEmpty()
                ? (warnings.isEmpty() ? ValidationResult.success() : ValidationResult.successWithWarnings(warnings))
                : ValidationResult.failure(errors, warnings);
    }

    /**
     * Validates audio settings.
     * 
     * Requirement REQ-AUD-1.2: Copy codec validation
     */
    private ValidationResult validateAudioSettings(AudioSettings settings) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Requirement REQ-AUD-1.2: Warn about copy codec compatibility
        if ("copy".equalsIgnoreCase(settings.codec())) {
            warnings.add("Copy mode may fail if source audio codec is incompatible with output container");
        }

        // Skip encoding parameter validation for copy codec
        if (!"copy".equalsIgnoreCase(settings.codec())) {
            // Validate bitrate
            if (settings.bitrate() < AudioSettings.MIN_BITRATE || settings.bitrate() > AudioSettings.MAX_BITRATE) {
                errors.add(String.format("Audio bitrate must be between %d and %d kbps (got: %d)",
                        AudioSettings.MIN_BITRATE, AudioSettings.MAX_BITRATE, settings.bitrate()));
            }

            // Validate sample rate (-1 means use original, which is valid)
            if (!AudioSettings.isValidSampleRate(settings.sampleRate())) {
                errors.add(String.format("Sample rate must be -1 (original) or a standard rate (got: %d)",
                        settings.sampleRate()));
            }

            // Validate channels (-1 means use original, which is valid)
            if (!AudioSettings.isValidChannels(settings.channels())) {
                errors.add(String.format("Audio channels must be -1 (original), 1, 2, or 6 (got: %d)",
                        settings.channels()));
            }
        }

        return errors.isEmpty()
                ? (warnings.isEmpty() ? ValidationResult.success() : ValidationResult.successWithWarnings(warnings))
                : ValidationResult.failure(errors, warnings);
    }

    /**
     * Validates image settings.
     */
    private ValidationResult validateImageSettings(ImageSettings settings) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate quality (-1 means lossless, which is valid)
        if (!ImageSettings.isValidQuality(settings.quality())) {
            errors.add(String.format("Image quality must be %d (lossless) or between %d and %d (got: %d)",
                    ImageSettings.LOSSLESS_QUALITY, ImageSettings.MIN_QUALITY, ImageSettings.MAX_QUALITY,
                    settings.quality()));
        }

        // Validate resolution
        if (settings.resolution() != null) {
            Resolution res = settings.resolution();
            if (res.getWidth() < MIN_IMAGE_DIMENSION || res.getWidth() > MAX_IMAGE_DIMENSION) {
                errors.add(String.format("Image width must be between %d and %d (got: %d)",
                        MIN_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION, res.getWidth()));
            }
            if (res.getHeight() < MIN_IMAGE_DIMENSION || res.getHeight() > MAX_IMAGE_DIMENSION) {
                errors.add(String.format("Image height must be between %d and %d (got: %d)",
                        MIN_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION, res.getHeight()));
            }
        }

        // Warn about low quality (lossless is not low quality)
        if (settings.quality() != ImageSettings.LOSSLESS_QUALITY && settings.quality() < 50) {
            warnings.add("Low image quality (< 50) may result in visible artifacts");
        }

        return errors.isEmpty()
                ? (warnings.isEmpty() ? ValidationResult.success() : ValidationResult.successWithWarnings(warnings))
                : ValidationResult.failure(errors, warnings);
    }

    /**
     * Validates document settings.
     */
    private ValidationResult validateDocumentSettings(DocumentSettings settings) {
        List<String> warnings = new ArrayList<>();

        // Check if template exists (if specified)
        if (settings.templatePath() != null) {
            if (!fileHandler.exists(settings.templatePath())) {
                warnings.add("Template file does not exist: " + settings.templatePath());
            } else if (!fileHandler.isReadable(settings.templatePath())) {
                warnings.add("Template file is not readable: " + settings.templatePath());
            }
        }

        return warnings.isEmpty() ? ValidationResult.success() : ValidationResult.successWithWarnings(warnings);
    }

    /**
     * Validates format pair compatibility (input → output).
     *
     * Requirement REQ-006: Format compatibility validation
     * Requirement REQ-PDF-1.2: PDF dual-category support (IMAGE and DOCUMENT)
     *
     * @param input  Input file format
     * @param output Output file format
     * @return Validation result
     */
    public ValidationResult validateFormatPair(FileFormat input, FileFormat output) {
        if (input == null) {
            return ValidationResult.failure("Input format cannot be null");
        }
        if (output == null) {
            return ValidationResult.failure("Output format cannot be null");
        }

        logger.debug("Validating format pair: {} -> {}", input, output);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check for unknown formats
        if (input == FileFormat.UNKNOWN) {
            errors.add("Input format is unknown");
        }
        if (output == FileFormat.UNKNOWN) {
            errors.add("Output format is unknown");
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }

        // Check if formats are in the same category or compatible
        FormatCategory inputCategory = input.getCategory();
        FormatCategory outputCategory = output.getCategory();

        // Same format conversion is usually pointless
        if (input == output) {
            warnings.add("Input and output formats are identical - no conversion needed");
        }

        // REQ-PDF-1.2: Check category compatibility with dual-category support
        // If output format supports the input's category (e.g., PDF supports both
        // DOCUMENT and IMAGE),
        // the conversion is valid within the same category
        boolean sameCategoryConversion = inputCategory == outputCategory
                || output.supportsCategory(inputCategory)
                || input.supportsCategory(outputCategory);

        if (!sameCategoryConversion) {
            // Different category conversion - check if it's a supported cross-category
            // conversion
            boolean compatible = isCrossCategoryCompatible(input, output, inputCategory, outputCategory);
            if (!compatible) {
                errors.add(String.format("Incompatible format conversion: %s (%s) -> %s (%s)",
                        input, inputCategory, output, outputCategory));
            } else {
                warnings.add(String.format("Cross-category conversion: %s -> %s may have limitations",
                        inputCategory, outputCategory));
            }
        }

        if (!errors.isEmpty()) {
            logger.warn("Format pair validation failed: {} -> {}", input, output);
            return ValidationResult.failure(errors, warnings);
        }

        if (!warnings.isEmpty()) {
            logger.debug("Format pair validation succeeded with warnings: {} -> {}", input, output);
            return ValidationResult.successWithWarnings(warnings);
        }

        logger.debug("Format pair validation successful: {} -> {}", input, output);
        return ValidationResult.success();
    }

    /**
     * Checks if cross-category conversion is supported.
     * REQ-PDF-1.2: Supports dual-category formats like PDF (DOCUMENT + IMAGE)
     *
     * @param inputFormat    Input file format (for dual-category checks)
     * @param outputFormat   Output file format (for dual-category checks)
     * @param inputCategory  Primary category of input format
     * @param outputCategory Primary category of output format
     * @return true if cross-category conversion is supported
     */
    private boolean isCrossCategoryCompatible(FileFormat inputFormat, FileFormat outputFormat,
            FormatCategory inputCategory, FormatCategory outputCategory) {

        // Check dual-category support: if output supports input's category or vice
        // versa
        if (outputFormat.supportsCategory(inputCategory) || inputFormat.supportsCategory(outputCategory)) {
            return true;
        }

        // Video can extract audio
        if (inputCategory == FormatCategory.VIDEO && outputCategory == FormatCategory.AUDIO) {
            return true;
        }

        // Note: VIDEO -> IMAGE is intentionally NOT supported: no tool adapter
        // can produce an image from a video (FFmpeg only handles video/audio
        // output), so validation rejects it up front with a clear error
        // instead of letting it fail later in ToolManager.

        // Documents can convert between each other to some extent
        if (inputCategory == FormatCategory.DOCUMENT && outputCategory == FormatCategory.DOCUMENT) {
            return true;
        }

        // Most other cross-category conversions are not supported
        return false;
    }

    /**
     * Validates output directory.
     *
     * Requirement REQ-002.3: Output directory validation
     *
     * @param directory Output directory path
     * @return Validation result
     */
    public ValidationResult validateOutputDirectory(Path directory) {
        if (directory == null) {
            return ValidationResult.failure("Output directory cannot be null");
        }

        logger.debug("Validating output directory: {}", directory);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check if directory exists
        if (!fileHandler.exists(directory)) {
            // Try to create it
            try {
                fileHandler.createDirectory(directory);
                logger.info("Created output directory: {}", directory);
            } catch (FileOperationException e) {
                errors.add("Cannot create output directory: " + e.getMessage());
                return ValidationResult.failure(errors);
            }
        }

        // Check if it's a directory
        if (!Files.isDirectory(directory)) {
            errors.add("Output path is not a directory: " + directory);
            return ValidationResult.failure(errors);
        }

        // Check writability
        if (!fileHandler.isWritable(directory)) {
            errors.add("Output directory is not writable: " + directory);
            return ValidationResult.failure(errors);
        }

        // Check available space
        try {
            long availableSpace = fileHandler.getAvailableSpace(directory);
            if (availableSpace < MIN_DISK_SPACE_BUFFER) {
                warnings.add(String.format("Low disk space: only %.2f GB available",
                        availableSpace / (1024.0 * 1024.0 * 1024.0)));
            }
        } catch (FileOperationException e) {
            warnings.add("Cannot determine available disk space: " + e.getMessage());
        }

        if (!errors.isEmpty()) {
            logger.warn("Output directory validation failed: {}", directory);
            return ValidationResult.failure(errors, warnings);
        }

        if (!warnings.isEmpty()) {
            logger.debug("Output directory validation succeeded with warnings: {}", directory);
            return ValidationResult.successWithWarnings(warnings);
        }

        logger.debug("Output directory validation successful: {}", directory);
        return ValidationResult.success();
    }

    /**
     * Validates available disk space for conversion.
     *
     * Requirement REQ-007.1: Disk space validation
     *
     * @param directory     Target directory
     * @param requiredBytes Required bytes (approximate)
     * @return Validation result
     */
    public ValidationResult validateDiskSpace(Path directory, long requiredBytes) {
        if (directory == null) {
            return ValidationResult.failure("Directory cannot be null");
        }
        if (requiredBytes < 0) {
            return ValidationResult.failure("Required bytes cannot be negative");
        }

        logger.debug("Validating disk space: {} bytes required in {}", requiredBytes, directory);

        try {
            long availableSpace = fileHandler.getAvailableSpace(directory);
            long totalRequired = saturatedAdd(requiredBytes, MIN_DISK_SPACE_BUFFER);

            if (availableSpace < totalRequired) {
                String error = String.format(
                        "Insufficient disk space: %.2f GB available, %.2f GB required (including buffer)",
                        availableSpace / (1024.0 * 1024.0 * 1024.0),
                        totalRequired / (1024.0 * 1024.0 * 1024.0));
                return ValidationResult.failure(error);
            }

            // Warn if space is tight (less than 2x required); the message
            // must use the same saturated value as the comparison - the raw
            // totalRequired * 2 overflows to a negative GB figure near
            // saturation
            if (availableSpace < saturatedDouble(totalRequired)) {
                String warning = String.format(
                        "Disk space is tight: %.2f GB available, %.2f GB recommended",
                        availableSpace / (1024.0 * 1024.0 * 1024.0),
                        saturatedDouble(totalRequired) / (1024.0 * 1024.0 * 1024.0));
                return ValidationResult.successWithWarnings(List.of(warning));
            }

            logger.debug("Disk space validation successful");
            return ValidationResult.success();

        } catch (FileOperationException e) {
            logger.error("Error checking disk space", e);
            return ValidationResult.failure("Cannot check disk space: " + e.getMessage());
        }
    }

    /**
     * Validates tool availability.
     *
     * Requirement REQ-004.1: Tool availability validation
     *
     * @param tool Conversion tool
     * @return Validation result
     */
    public ValidationResult validateToolAvailability(ConversionTool tool) {
        if (tool == null) {
            return ValidationResult.failure("Tool cannot be null");
        }

        logger.debug("Validating tool availability: {}", tool);

        String command = getToolCommand(tool);
        if (command == null) {
            return ValidationResult.failure("Unknown tool: " + tool);
        }

        // Fast path: a configured binary that is gone or non-executable fails
        // immediately instead of paying a doomed process spawn + 10s wait.
        Path commandPath = Path.of(command);
        if (toolConfiguration != null && !Files.isExecutable(commandPath)) {
            return ValidationResult.failure("Tool not available: " + tool + " (not executable: " + command + ")");
        }

        // Serve fresh-enough probes from the cache (worker threads validate
        // per file; without this a batch pays one spawn per file).
        CachedAvailability cached = availabilityCache.get(tool);
        if (cached != null && System.currentTimeMillis() - cached.checkedAt() < AVAILABILITY_CACHE_TTL_MS) {
            return cached.result();
        }

        ValidationResult result = probeToolAvailability(tool, command);
        availabilityCache.put(tool, new CachedAvailability(result, System.currentTimeMillis()));
        return result;
    }

    private ValidationResult probeToolAvailability(ConversionTool tool, String command) {
        Process process = null;
        try {
            // FFmpeg uses single-dash flags, other tools use double-dash
            String versionFlag = (tool == ConversionTool.FFMPEG || tool == ConversionTool.IMAGEMAGICK)
                    ? "-version" : "--version";

            ProcessBuilder pb = new ProcessBuilder(command, versionFlag);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            process = pb.start();

            // Wait for process to complete with timeout
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                // Brief follow-up wait so the destroyed process is reaped
                // instead of lingering as a zombie.
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                return ValidationResult.failure("Tool check timed out: " + tool);
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                logger.debug("Tool availability validation successful: {}", tool);
                return ValidationResult.success();
            } else {
                return ValidationResult
                        .failure("Tool not available or not working: " + tool + " (exit code: " + exitCode + ")");
            }

        } catch (IOException e) {
            logger.warn("Error checking tool availability for {}", tool, e);
            return ValidationResult.failure("Tool not available: " + tool + " (" + e.getMessage() + ")");
        } catch (InterruptedException e) {
            // Restore the interrupt flag: swallowing it breaks cancellation
            // for every caller up the stack.
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            logger.warn("Tool availability check interrupted for {}", tool, e);
            return ValidationResult.failure("Tool check interrupted: " + tool);
        }
    }

    /**
     * Gets the command name for a conversion tool.
     */
    private String getToolCommand(ConversionTool tool) {
        if (toolConfiguration != null) {
            Path path = switch (tool) {
                case FFMPEG -> toolConfiguration.getFfmpegPath();
                case PANDOC -> toolConfiguration.getPandocPath();
                case LIBREOFFICE -> toolConfiguration.getLibreOfficePath();
                case IMAGEMAGICK -> toolConfiguration.getConvertPath();
            };
            return path == null ? null : path.toString();
        }
        return switch (tool) {
            case FFMPEG ->
                "ffmpeg";
            case PANDOC ->
                "pandoc";
            case LIBREOFFICE ->
                "soffice";
            case IMAGEMAGICK ->
                "convert";
        };
    }

    /**
     * Comprehensive validation of a conversion request. Validates file,
     * settings, format pair, and disk space.
     *
     * Requirement REQ-002.3, REQ-003.2: Comprehensive validation
     *
     * @param file     Conversion file
     * @param settings Conversion settings
     * @return Combined validation result
     */
    public ValidationResult validateConversionRequest(ConversionFile file, ConversionSettings settings) {
        if (file == null) {
            return ValidationResult.failure("Conversion file cannot be null");
        }
        if (settings == null) {
            return ValidationResult.failure("Conversion settings cannot be null");
        }

        logger.debug("Validating conversion request for file: {}", file.path());

        // Validate file
        ValidationResult fileResult = validateFile(file.path());

        // Validate settings
        ValidationResult settingsResult = validateSettings(settings);

        // Get output format for validation - use default if category settings are not
        // configured
        // Delegates to ConversionEngine's category default so validation always
        // resolves the same default format the conversion itself will use
        FormatCategory category = file.format().getCategory();
        FileFormat outputFormat = settings.outputFormat(category);
        if (outputFormat == null) {
            outputFormat = ConversionEngine.defaultFormatForCategory(category);
            logger.debug("No output format configured for category {}, using default: {}",
                    category, outputFormat);
        }

        // Validate format pair
        ValidationResult formatPairResult = validateFormatPair(file.format(), outputFormat);

        // Validate disk space: conservative 2x policy, see estimateRequiredBytes
        ValidationResult diskSpaceResult = ValidationResult.success();
        try {
            long fileSize = fileHandler.getFileSize(file.path());
            long estimatedRequired = estimateRequiredBytes(fileSize);
            diskSpaceResult = validateDiskSpace(settings.outputDirectory(), estimatedRequired);
        } catch (FileOperationException e) {
            logger.warn("Could not estimate required disk space", e);
            diskSpaceResult = ValidationResult.successWithWarnings(
                    List.of("Could not estimate required disk space: " + e.getMessage()));
        }

        // Combine all results
        ValidationResult combinedResult = fileResult
                .combine(settingsResult)
                .combine(formatPairResult)
                .combine(diskSpaceResult);

        if (combinedResult.isFailure()) {
            logger.warn("Conversion request validation failed for: {}", file.path());
        } else {
            logger.debug("Conversion request validation successful for: {}", file.path());
        }

        return combinedResult;
    }
}
