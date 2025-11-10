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
    private static final int MIN_PARALLEL_CONVERSIONS = 1;
    private static final int MAX_PARALLEL_CONVERSIONS = 64;

    // Video validation ranges
    private static final int MIN_VIDEO_BITRATE = 100; // kbps
    private static final int MAX_VIDEO_BITRATE = 100000; // kbps
    private static final int MIN_FRAME_RATE = 1;
    private static final int MAX_FRAME_RATE = 240;
    private static final int MIN_CRF = 0;
    private static final int MAX_CRF = 51;

    // Audio validation ranges
    private static final int MIN_AUDIO_BITRATE = 8; // kbps
    private static final int MAX_AUDIO_BITRATE = 1000; // kbps
    private static final int MIN_SAMPLE_RATE = 8000; // Hz
    private static final int MAX_SAMPLE_RATE = 192000; // Hz
    private static final int MIN_AUDIO_CHANNELS = 1;
    private static final int MAX_AUDIO_CHANNELS = 8;

    // Image validation ranges
    private static final int MIN_IMAGE_QUALITY = 0;
    private static final int MAX_IMAGE_QUALITY = 100;
    private static final int MIN_IMAGE_DIMENSION = 1;
    private static final int MAX_IMAGE_DIMENSION = 65535;

    private final FileHandler fileHandler;

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
        if (settings.bitrate() < MIN_VIDEO_BITRATE || settings.bitrate() > MAX_VIDEO_BITRATE) {
            errors.add(String.format("Video bitrate must be between %d and %d kbps (got: %d)",
                    MIN_VIDEO_BITRATE, MAX_VIDEO_BITRATE, settings.bitrate()));
        }

        // Validate frame rate (-1 means use original, which is valid)
        if (settings.frameRate() != -1
                && (settings.frameRate() < MIN_FRAME_RATE || settings.frameRate() > MAX_FRAME_RATE)) {
            errors.add(String.format("Frame rate must be -1 (original) or between %d and %d fps (got: %d)",
                    MIN_FRAME_RATE, MAX_FRAME_RATE, settings.frameRate()));
        }

        // Validate CRF
        if (settings.crf() < MIN_CRF || settings.crf() > MAX_CRF) {
            errors.add(String.format("CRF must be between %d and %d (got: %d)",
                    MIN_CRF, MAX_CRF, settings.crf()));
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
            if (settings.bitrate() < MIN_AUDIO_BITRATE || settings.bitrate() > MAX_AUDIO_BITRATE) {
                errors.add(String.format("Audio bitrate must be between %d and %d kbps (got: %d)",
                        MIN_AUDIO_BITRATE, MAX_AUDIO_BITRATE, settings.bitrate()));
            }

            // Validate sample rate (-1 means use original, which is valid)
            if (settings.sampleRate() != -1
                    && (settings.sampleRate() < MIN_SAMPLE_RATE || settings.sampleRate() > MAX_SAMPLE_RATE)) {
                errors.add(String.format("Sample rate must be -1 (original) or between %d and %d Hz (got: %d)",
                        MIN_SAMPLE_RATE, MAX_SAMPLE_RATE, settings.sampleRate()));
            }

            // Validate channels (-1 means use original, which is valid)
            if (settings.channels() != -1
                    && (settings.channels() < MIN_AUDIO_CHANNELS || settings.channels() > MAX_AUDIO_CHANNELS)) {
                errors.add(String.format("Audio channels must be -1 (original) or between %d and %d (got: %d)",
                        MIN_AUDIO_CHANNELS, MAX_AUDIO_CHANNELS, settings.channels()));
            }

            // Warn about low bitrate
            if (settings.bitrate() < 64) {
                warnings.add("Low audio bitrate (< 64 kbps) may result in poor quality");
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

        // Validate quality
        if (settings.quality() < MIN_IMAGE_QUALITY || settings.quality() > MAX_IMAGE_QUALITY) {
            errors.add(String.format("Image quality must be between %d and %d (got: %d)",
                    MIN_IMAGE_QUALITY, MAX_IMAGE_QUALITY, settings.quality()));
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

        // Warn about low quality
        if (settings.quality() < 50) {
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

        // Video can generate thumbnail images
        if (inputCategory == FormatCategory.VIDEO && outputCategory == FormatCategory.IMAGE) {
            return true;
        }

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
            long totalRequired = requiredBytes + MIN_DISK_SPACE_BUFFER;

            if (availableSpace < totalRequired) {
                String error = String.format(
                        "Insufficient disk space: %.2f GB available, %.2f GB required (including buffer)",
                        availableSpace / (1024.0 * 1024.0 * 1024.0),
                        totalRequired / (1024.0 * 1024.0 * 1024.0));
                return ValidationResult.failure(error);
            }

            // Warn if space is tight (less than 2x required)
            if (availableSpace < totalRequired * 2) {
                String warning = String.format(
                        "Disk space is tight: %.2f GB available, %.2f GB recommended",
                        availableSpace / (1024.0 * 1024.0 * 1024.0),
                        (totalRequired * 2) / (1024.0 * 1024.0 * 1024.0));
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

        try {
            // FFmpeg uses single-dash flags, other tools use double-dash
            String versionFlag = (tool == ConversionTool.FFMPEG) ? "-version" : "--version";

            ProcessBuilder pb = new ProcessBuilder(command, versionFlag);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Wait for process to complete with timeout
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
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

        } catch (IOException | InterruptedException e) {
            logger.warn("Error checking tool availability for {}", tool, e);
            return ValidationResult.failure("Tool not available: " + tool + " (" + e.getMessage() + ")");
        }
    }

    /**
     * Gets the command name for a conversion tool.
     */
    private String getToolCommand(ConversionTool tool) {
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
        // This mirrors ConversionEngine's fallback behavior
        FormatCategory category = file.format().getCategory();
        FileFormat outputFormat = settings.outputFormat(category);
        if (outputFormat == null) {
            // Use default format for category (same defaults as ConversionEngine)
            outputFormat = getDefaultFormatForCategory(category);
            logger.debug("No output format configured for category {}, using default: {}",
                    category, outputFormat);
        }

        // Validate format pair
        ValidationResult formatPairResult = validateFormatPair(file.format(), outputFormat);

        // Validate disk space (estimate 2x input file size as required space)
        ValidationResult diskSpaceResult = ValidationResult.success();
        try {
            long fileSize = fileHandler.getFileSize(file.path());
            long estimatedRequired = fileSize * 2; // Conservative estimate
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

    /**
     * Returns the default output format for a given category. This should match
     * ConversionEngine's default format logic.
     *
     * @param category the format category
     * @return default format (MP4 for video, MP3 for audio, PNG for image, PDF
     *         for document)
     */
    private FileFormat getDefaultFormatForCategory(FormatCategory category) {
        return switch (category) {
            case VIDEO ->
                FileFormat.MP4;
            case AUDIO ->
                FileFormat.MP3;
            case IMAGE ->
                FileFormat.PNG;
            case DOCUMENT ->
                FileFormat.PDF;
            case UNKNOWN ->
                null;
        };
    }
}
