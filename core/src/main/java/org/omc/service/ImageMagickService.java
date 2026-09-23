// filepath: src/main/java/org/omc/service/ImageMagickService.java

package org.omc.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.omc.core.ProcessRegistry;
import org.omc.core.ProgressCallback;
import org.omc.exception.ErrorCode;
import org.omc.exception.ToolExecutionException;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionTool;
import org.omc.model.FileFormat;
import org.omc.model.ImageSettings;
import org.omc.model.Resolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Service for building and executing ImageMagick "convert" commands for image
 * conversion.
 * 
 * <p>
 * This service uses ImageMagick's {@code convert} tool to handle image format
 * conversions
 * with support for quality controls, resolution adjustments, and compression
 * settings.
 * </p>
 * 
 * <p>
 * Progress tracking uses ImageMagick's {@code -monitor} flag for real-time
 * progress output:
 * </p>
 * <ul>
 * <li>0% when conversion starts</li>
 * <li>Real-time progress parsed from monitor output (format:
 * "Operation/Image//path[file]: X of Y, Z% complete")</li>
 * <li>100% when conversion completes successfully</li>
 * <li>Progress updates throttled to max 2 updates/second (NFR-IMG-1)</li>
 * </ul>
 * 
 * Requirements:
 * <ul>
 * <li>REQ-IMG-2: ImageMagick service implementation</li>
 * <li>REQ-IMG-3: ImageMagick command building</li>
 * <li>REQ-IMG-4: Progress tracking for ImageMagick conversions</li>
 * <li>NFR-IMG-1: Performance with progress throttling</li>
 * <li>NFR-IMG-2: Resource management with output size limits</li>
 * </ul>
 */
public class ImageMagickService {

    private static final Logger logger = LoggerFactory.getLogger(ImageMagickService.class);

    /** Maximum size of captured tool output (1MB) to prevent memory issues */
    private static final int MAX_OUTPUT_SIZE = 1024 * 1024;

    /** Message appended when output is truncated due to size limit */
    private static final String TRUNCATION_MESSAGE = "\n[Output truncated - exceeded 1MB limit]\n";

    /**
     * Maximum wall time for an ImageMagick process before forced termination
     * (1 hour, mirroring PandocService). Package-visible and non-final so
     * tests can scale it down; not part of the public API.
     */
    static long PROCESS_TIMEOUT_MILLIS = TimeUnit.HOURS.toMillis(1);

    /**
     * How long the main thread waits for the output reader to drain after the
     * process has ended before using whatever was captured. The reader is a
     * daemon and pipe reads are not interruptible, so this join is a brief
     * courtesy, not a correctness requirement.
     */
    private static final long READER_JOIN_MILLIS = TimeUnit.SECONDS.toMillis(2);

    private final Path convertPath;

    /**
     * Creates a new ImageMagickService with specified convert binary path.
     * 
     * @param convertPath path to ImageMagick "convert" executable
     * @throws NullPointerException if convertPath is null
     */
    public ImageMagickService(Path convertPath) {
        this.convertPath = Objects.requireNonNull(convertPath, "convertPath must not be null");
        logger.debug("ImageMagickService initialized with convert={}", convertPath);
    }

    /**
     * Builds an ImageMagick "convert" command for image conversion.
     * 
     * <p>
     * Command structure:
     * {@code convert -monitor <input> [rotation] [flip] [quality_opts] [resize_opts] [compress_opts] <output>}
     * </p>
     * 
     * <p>
     * Progress tracking: The {@code -monitor} flag enables real-time progress
     * output in the format:
     * </p>
     * <ul>
     * <li>{@code Operation/Image//path[filename.ext]: current of total, percentage% complete}</li>
     * <li>Example:
     * {@code Resize/Image//home/user[image.png]: 874 of 875, 100% complete}</li>
     * </ul>
     * 
     * <p>
     * Quality options by format:
     * </p>
     * <ul>
     * <li>JPEG/JPG: {@code -quality <0-100>}</li>
     * <li>PNG: {@code -quality <0-100> -compress Zip}</li>
     * <li>WebP: {@code -quality <0-100>}</li>
     * <li>PDF: {@code -quality <0-100>} (Requirement REQ-PDF-1.3)</li>
     * </ul>
     * 
     * <p>
     * Resolution options:
     * </p>
     * <ul>
     * <li>Maintain aspect ratio: {@code -resize <width>x<height>}</li>
     * <li>Force exact dimensions: {@code -resize <width>x<height>!}</li>
     * </ul>
     * 
     * <p>
     * Rotation and Flip (Requirements REQ-IMG-1.2, REQ-IMG-2.2):
     * </p>
     * <ul>
     * <li>Rotation: {@code -rotate <degrees>} (90, 180, 270)</li>
     * <li>Vertical flip: {@code -flip}</li>
     * <li>Horizontal flip: {@code -flop}</li>
     * </ul>
     * 
     * Requirements: REQ-IMG-3, REQ-PDF-1.3, REQ-PDF-1.4
     * 
     * @param inputPath  input image file path
     * @param outputPath output image file path
     * @param settings   image conversion settings
     * @return list of command arguments
     * @throws NullPointerException if any parameter is null
     */
    public List<String> buildImageCommand(Path inputPath, Path outputPath, ImageSettings settings) {
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        if (!settings.isValid()) {
            throw new IllegalArgumentException("Invalid image settings: " + settings);
        }

        List<String> command = new ArrayList<>();
        command.add(convertPath.toString());

        // Add -monitor flag for real-time progress tracking
        command.add("-monitor");

        // Add input file (absolutized when dash-prefixed)
        command.add(FFmpegService.safePathArg(inputPath));

        // 1. ROTATION (FIRST) - REQ-IMG-1.2
        // Apply rotation before flip and resize operations
        if (settings.rotation() != null && !settings.rotation().isNone()) {
            command.add("-rotate");
            command.add(String.valueOf(settings.rotation().getDegrees()));
            logger.debug("Added rotation parameter: {} degrees", settings.rotation().getDegrees());
        }

        // 2. FLIP (AFTER ROTATION, BEFORE RESIZE) - REQ-IMG-2.2
        // Apply flip operations: -flip (vertical), -flop (horizontal)
        if (settings.flip() != null && !settings.flip().isNone()) {
            if (settings.flip().isFlipVertical()) {
                command.add("-flip");
                logger.debug("Added vertical flip parameter");
            }
            if (settings.flip().isFlipHorizontal()) {
                command.add("-flop");
                logger.debug("Added horizontal flip parameter");
            }
        }

        // 3. Quality parameter (BEFORE resize). NOTE: 0 means "unset" in
        // this codebase (int default; -1 = lossless), so > 0 is the correct
        // bound here despite 0 being a legal JPEG quality in theory.
        Integer quality = settings.quality();
        if (quality != null && quality == ImageSettings.LOSSLESS_QUALITY) {
            FileFormat outputFormat = FileFormat.fromExtension(getFileExtension(outputPath));
            if (outputFormat == FileFormat.WEBP) {
                command.add("-define");
                command.add("webp:lossless=true");
            } else if (outputFormat == FileFormat.JPEG) {
                throw new IllegalArgumentException("JPEG does not support lossless conversion. Choose PNG, TIFF or WebP.");
            }
        }
        if (quality != null && quality > 0 && quality <= 100) {
            String outputExt = getFileExtension(outputPath);
            FileFormat outputFormat = FileFormat.fromExtension(outputExt);

            // Quality applies to JPEG, PNG, and WebP formats
            if (outputFormat != null && supportsQualityParameter(outputFormat)) {
                command.add("-quality");
                command.add(String.valueOf(quality));
                logger.debug("Added quality parameter: {}", quality);
            }
        }

        // 4. RESIZE
        Resolution resolution = settings.resolution();
        if (resolution != null && resolution.getWidth() > 0 && resolution.getHeight() > 0
                && settings.resizeMode() != org.omc.model.ResizeMode.NONE) {
            String resizeSpec = resolution.getWidth() + "x" + resolution.getHeight();
            String filter = switch (settings.resizeMode()) {
                case LANCZOS -> "Lanczos";
                case BICUBIC -> "Cubic";
                case BILINEAR -> "Triangle";
                case NEAREST_NEIGHBOR -> "Point";
                default -> null;
            };
            if (filter != null) {
                command.add("-filter");
                command.add(filter);
            }
            command.add("-resize");
            if (settings.resizeMode() == org.omc.model.ResizeMode.FILL) {
                command.add(resizeSpec + "^");
                command.add("-gravity");
                command.add("center");
                command.add("-extent");
                command.add(resizeSpec);
            } else {
                command.add(resizeSpec + (settings.resizeMode() == org.omc.model.ResizeMode.STRETCH
                        || !settings.maintainAspectRatio() ? "!" : ""));
            }
            logger.debug("Added resize parameter: {}", resizeSpec);
        }

        // 5. Compression parameter for PNG (0 = unset/default here; the
        // int default is 0, so >= 0 would emit -define on every command).
        Integer compressionLevel = settings.compressionLevel();
        if (compressionLevel != null && compressionLevel > 0 && compressionLevel <= 9) {
            String outputExt = getFileExtension(outputPath);
            FileFormat outputFormat = FileFormat.fromExtension(outputExt);

            if (outputFormat == FileFormat.PNG) {
                command.add("-define");
                command.add("png:compression-level=" + compressionLevel);
            }
        }

        // Add output file
        command.add(FFmpegService.safePathArg(outputPath));

        logger.debug("Built ImageMagick command: {}", String.join(" ", command));
        return command;
    }

    /**
     * Executes an image conversion with real-time progress tracking.
     * 
     * <p>
     * This method executes ImageMagick's convert tool with the {@code -monitor}
     * flag to
     * enable real-time progress output. Progress is parsed from monitor output
     * lines:
     * </p>
     * <ul>
     * <li>0% when conversion starts</li>
     * <li>Real-time progress parsed from lines like "Operation/Image//path[file]: X
     * of Y, Z% complete"</li>
     * <li>100% when conversion completes successfully</li>
     * <li>Progress updates throttled to max 2 updates/second (NFR-IMG-1)</li>
     * </ul>
     * 
     * <p>
     * Tool output (stdout/stderr) is captured with a 1MB size limit to prevent
     * memory issues.
     * If the output exceeds this limit, it will be truncated and a truncation
     * message appended.
     * </p>
     * 
     * Requirements: REQ-IMG-2, REQ-IMG-4, NFR-IMG-1, NFR-IMG-2
     * 
     * @param inputPath        input image file path
     * @param outputPath       output image file path
     * @param settings         image conversion settings
     * @param progressCallback callback for progress updates (can be null)
     * @param fileId           file ID for process registration (can be null)
     * @param processRegistry  registry to track active processes
     * @return conversion result with success/failure information
     * @throws ToolExecutionException if execution fails
     * @throws NullPointerException   if inputPath, outputPath, settings, or
     *                                processRegistry is null
     */
    public ConversionResult convertImage(
            Path inputPath,
            Path outputPath,
            ImageSettings settings,
            ProgressCallback progressCallback,
            String fileId,
            ProcessRegistry processRegistry) throws ToolExecutionException {

        // MDC correlation: every log line emitted during this conversion
        // carries the file and tool context
        try (MDC.MDCCloseable omcFileCtx = MDC.putCloseable("omcFile", String.valueOf(fileId));
                MDC.MDCCloseable omcToolCtx = MDC.putCloseable("omcTool", "imagemagick")) {
            return convertImageInternal(inputPath, outputPath, settings, progressCallback, fileId, processRegistry);
        }
    }

    private ConversionResult convertImageInternal(
            Path inputPath,
            Path outputPath,
            ImageSettings settings,
            ProgressCallback progressCallback,
            String fileId,
            ProcessRegistry processRegistry) throws ToolExecutionException {

        // Null checks for required parameters
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        Objects.requireNonNull(processRegistry, "processRegistry must not be null");

        if (outputPath.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".svg")) {
            return convertSvg(inputPath, outputPath, settings, progressCallback, fileId, processRegistry);
        }

        logger.info("Executing ImageMagick conversion: {} → {} (progressCallback: {})",
                inputPath, outputPath, progressCallback != null ? "provided" : "NULL");

        Instant startTime = Instant.now();
        long inputSize = 0;

        try {
            inputSize = Files.size(inputPath);
        } catch (IOException e) {
            logger.warn("Could not determine input file size: {}", inputPath, e);
        }

        // Task 3.4: Build command and start process
        List<String> command = buildImageCommand(inputPath, outputPath, settings);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true); // Redirect stderr to stdout

        logger.info("Executing ImageMagick command: {}", String.join(" ", command));

        Process process = null;
        StringBuilder outputLog = new StringBuilder(4096);
        final boolean[] outputTruncated = { false };

        try {
            // Start process and track start time
            process = processBuilder.start();
            logger.debug("ImageMagick process started with PID: {}", process.pid());

            // Register process in ProcessRegistry with fileId
            if (fileId != null) {
                processRegistry.registerProcess(fileId, process);
                logger.debug("Registered process for fileId: {}", fileId);
            }

            // Task 3.6: Report 0% progress when process starts
            if (progressCallback != null) {
                progressCallback.onProgress(0.0, 0, 0);
            }

            // Effectively-final aliases so the reader lambda can capture them.
            final Process convertProcess = process;
            final long inputSizeFinal = inputSize;

            // Task 3.5: Capture output with 1MB limit and parse progress on a
            // daemon reader thread - a hung convert holding its pipe open with
            // no output would block an inline read-to-EOF loop forever, so the
            // bounded waitFor below would never fire (mirrors the
            // PandocService/LibreOfficeService gobbler pattern).
            // NOTE: ImageMagick -monitor uses \r (carriage return) instead of \n
            // (newline) to update progress on the same line, so we must read
            // character-by-character.
            Thread outputReader = org.omc.util.ThreadUtils.createThreadFactory("ImageMagick-Reader")
                    .newThread(() -> {
                try (InputStreamReader reader = new InputStreamReader(convertProcess.getInputStream(),
                        java.nio.charset.StandardCharsets.UTF_8)) {
                    StringBuilder currentLine = new StringBuilder(256);
                    int ch;
                    int segmentCount = 0;
                    long lastProgressUpdate = 0; // Initialize to 0 to allow first callback immediately
                    // -monitor restarts at 0% for every stage (Resize, then
                    // Extent...); enforce monotonic progress so the UI never jumps
                    // 100% -> 0%.
                    double maxSeenProgress = 0.0;

                    while ((ch = reader.read()) != -1) {
                        if (ch == '\r' || ch == '\n') {
                            // Process the accumulated line/segment
                            if (currentLine.length() > 0) {
                                String line = currentLine.toString();
                                segmentCount++;

                                // Append line to output log if not truncated
                                if (!outputTruncated[0]) {
                                    if (outputLog.length() + line.length() + 1 > MAX_OUTPUT_SIZE) {
                                        outputLog.append(TRUNCATION_MESSAGE);
                                        outputTruncated[0] = true;
                                        logger.warn("Tool output exceeded 1MB limit, truncating further output");
                                    } else {
                                        outputLog.append(line).append('\n');
                                    }
                                }

                                // Task 3.6: Parse real-time progress from -monitor output
                                // Format: "Operation/Image//path[filename.ext]: current of total, percentage%
                                // complete"
                                // Example: "Resize/Image//home/xxx/Images[20250723_085451.png]: 874 of 875,
                                // 100% complete"
                                double parsedProgress = parseMonitorProgress(line);
                                if (parsedProgress >= 0 && progressCallback != null) {
                                    parsedProgress = Math.max(parsedProgress, maxSeenProgress);
                                    maxSeenProgress = parsedProgress;
                                    long currentTime = System.currentTimeMillis();
                                    // Throttle to max 2 updates/second (500ms intervals)
                                    if (currentTime - lastProgressUpdate >= 500) {
                                        long estimatedBytes = (long) (inputSizeFinal * parsedProgress / 100.0);
                                        progressCallback.onProgress(parsedProgress, estimatedBytes, 0);
                                        lastProgressUpdate = currentTime;
                                    }
                                }

                                // Clear buffer for next segment
                                currentLine.setLength(0);
                            }
                        } else {
                            // Bound the accumulator: the 1MB cap applies while
                            // accumulating too, so a segment that never
                            // terminates cannot grow without limit. The
                            // oversized partial segment is dropped (same
                            // semantics as an oversized line in FFmpegService)
                            // and the truncation marker records the loss.
                            if (currentLine.length() < MAX_OUTPUT_SIZE) {
                                currentLine.append((char) ch);
                            }
                            if (!outputTruncated[0]
                                    && outputLog.length() + currentLine.length() + 1 > MAX_OUTPUT_SIZE) {
                                outputLog.append(TRUNCATION_MESSAGE);
                                outputTruncated[0] = true;
                                logger.warn("Tool output exceeded 1MB limit, truncating further output");
                                currentLine.setLength(0);
                            }
                        }
                    }

                    // Process any remaining content in buffer
                    if (currentLine.length() > 0 && !outputTruncated[0]) {
                        String line = currentLine.toString();
                        if (outputLog.length() + line.length() + 1 > MAX_OUTPUT_SIZE) {
                            outputLog.append(TRUNCATION_MESSAGE);
                            outputTruncated[0] = true;
                            logger.warn("Tool output exceeded 1MB limit, truncating further output");
                        } else {
                            outputLog.append(line).append('\n');
                        }
                    }

                    logger.debug("ImageMagick output reading completed. Total segments parsed: {}", segmentCount);
                } catch (IOException e) {
                    logger.trace("Error reading ImageMagick output: {}", e.getMessage());
                }
            });
            outputReader.start();

            // Task 3.7: Wait for process with bounded timeout. Interruption is
            // checked on a time cadence (every 500ms wait slice) so a silent
            // convert still observes cancellation.
            boolean completed = false;
            long startWaitTime = System.currentTimeMillis();

            while (!completed && (System.currentTimeMillis() - startWaitTime) < PROCESS_TIMEOUT_MILLIS) {
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Conversion interrupted by user");
                    process.destroyForcibly();
                    cleanupPartialFile(outputPath);
                    throw new InterruptedException("Conversion cancelled by user");
                }

                completed = process.waitFor(500, TimeUnit.MILLISECONDS);
            }

            if (!completed) {
                // Timeout - destroy process and throw exception
                logger.error("ImageMagick process timed out after 1 hour");
                process.destroyForcibly();
                cleanupPartialFile(outputPath);
                throw new ToolExecutionException(
                        "ImageMagick conversion timed out after 1 hour",
                        ErrorCode.CONVERSION_TIMEOUT,
                        "imagemagick");
            }

            // The process has ended, so the pipe is at EOF and the reader
            // drains quickly; the bounded join only matters for a pipe held
            // open by a grandchild.
            outputReader.join(READER_JOIN_MILLIS);
            if (outputReader.isAlive()) {
                outputReader.interrupt();
            }

            // Get exit code and calculate conversion time
            int exitCode = process.exitValue();
            Duration conversionTime = Duration.between(startTime, Instant.now());

            if (exitCode == 0) {
                if (!Files.isRegularFile(outputPath) || Files.size(outputPath) == 0) {
                    cleanupPartialFile(outputPath);
                    return ConversionResult.failure(inputPath.toString(),
                            "The image converter did not create a complete output file. "
                                    + "For animated or multi-page input, choose an output format that retains all frames.",
                            outputLog.toString(), conversionTime, inputSize, ConversionTool.IMAGEMAGICK);
                }
                // Task 3.7: Create success ConversionResult
                long outputSize = 0;
                try {
                    outputSize = Files.size(outputPath);
                } catch (IOException e) {
                    logger.warn("Could not determine output file size: {}", outputPath, e);
                }

                // Report 100% progress on success
                if (progressCallback != null) {
                    progressCallback.onProgress(100.0, outputSize, 0);
                }

                logger.info("Conversion completed successfully in {}", conversionTime);

                return ConversionResult.success(
                        inputPath.toString(),
                        outputPath,
                        outputLog.toString(),
                        conversionTime,
                        inputSize,
                        outputSize,
                        ConversionTool.IMAGEMAGICK);
            } else {
                // Task 3.7: Extract error message, clean up partial file, create failure result
                String errorMessage = extractErrorMessage(outputLog.toString());
                cleanupPartialFile(outputPath);

                logger.error("Conversion failed with exit code {}: {}", exitCode, errorMessage);

                return ConversionResult.failure(
                        inputPath.toString(),
                        "ImageMagick conversion failed (exit code " + exitCode + "): " + errorMessage,
                        outputLog.toString(),
                        conversionTime,
                        inputSize,
                        ConversionTool.IMAGEMAGICK);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolExecutionException(
                    "Conversion interrupted: " + e.getMessage(),
                    ErrorCode.CONVERSION_CANCELLED,
                    "imagemagick",
                    null,
                    null,
                    null,
                    e);
        } catch (IOException e) {
            cleanupPartialFile(outputPath);
            throw new ToolExecutionException(
                    "Failed to execute ImageMagick: " + e.getMessage(),
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "imagemagick",
                    null,
                    null,
                    null,
                    e);
        } finally {
            // Unregister process from ProcessRegistry
            if (fileId != null) {
                processRegistry.unregisterProcess(fileId);
            }

            // A convert still running here (interrupt path, or any other
            // unexpected exit) must not outlive this call: the bounded
            // timeout no longer applies once the waiter returns.
            // destroyForcibly is a no-op on an already-terminated process,
            // so normal completion is unaffected.
            if (process != null && process.isAlive()) {
                logger.warn("Forcibly terminating ImageMagick process");
                process.destroyForcibly();
            }
        }
    }

    /**
     * Cleans up a partially written output file after a failed conversion.
     * 
     * <p>
     * This method attempts to delete the output file if it exists. Failures are
     * logged
     * but do not throw exceptions, as cleanup is a best-effort operation.
     * </p>
     * 
     * Requirements: REQ-IMG-2, NFR-IMG-2
     * 
     * @param outputPath path to the partial output file
     */
    private void cleanupPartialFile(Path outputPath) {
        try {
            if (Files.exists(outputPath)) {
                Files.deleteIfExists(outputPath);
                logger.info("Cleaned up partial output file: {}", outputPath);
            }
        } catch (IOException e) {
            logger.warn("Failed to clean up partial output file: {}", outputPath, e);
        }
    }

    /**
     * Parses progress percentage from ImageMagick -monitor output line.
     * 
     * <p>
     * Expected format:
     * {@code Operation/Image//path[filename.ext]: current of total, percentage% complete}
     * </p>
     * <p>
     * Examples:
     * </p>
     * <ul>
     * <li>{@code Resize/Image//home/xxx/Images[20250723_085451.png]: 874 of 875, 100% complete}</li>
     * <li>{@code Save/Image//home/xxx/.cache/open-media-converter/temp[conversion-1589084659952825825.png]: 499 of 500, 99% complete}</li>
     * </ul>
     * 
     * Requirements: REQ-IMG-4, NFR-IMG-1
     * 
     * @param line output line from ImageMagick -monitor
     * @return progress percentage (0-100), or -1 if line does not contain progress
     *         information
     */
    private double parseMonitorProgress(String line) {
        if (line == null || line.isBlank()) {
            return -1;
        }

        // Look for pattern: "X of Y, Z% complete"
        // The progress percentage is followed by "% complete"
        int completeIndex = line.indexOf("% complete");

        if (completeIndex > 0) {
            // The % sign is at completeIndex, and we need to find the number before it
            // Search backward from the % to find the start of the number
            int endIndex = completeIndex; // Position of '%' in "X% complete"
            int startIndex = endIndex - 1;

            // Skip backward through digits and decimal point
            while (startIndex >= 0 && (Character.isDigit(line.charAt(startIndex)) || line.charAt(startIndex) == '.')) {
                startIndex--;
            }
            startIndex++; // Move to the first digit

            if (startIndex < endIndex) {
                try {
                    String percentStr = line.substring(startIndex, endIndex).trim();
                    double progress = Double.parseDouble(percentStr);
                    // Clamp to valid range [0, 100]
                    return Math.max(0, Math.min(100, progress));
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    logger.trace("Failed to parse progress from line: {}", line, e);
                    return -1;
                }
            }
        }

        return -1;
    }

    /**
     * Extracts a meaningful error message from ImageMagick tool output.
     * 
     * <p>
     * This method searches backward through the output for lines containing common
     * error indicators. If no match is found, it returns the last non-empty line or
     * a
     * default error message.
     * </p>
     * 
     * Requirements: NFR-IMG-3
     * 
     * @param output tool output to parse
     * @return extracted error message
     */
    private String extractErrorMessage(String output) {
        if (output == null || output.isBlank()) {
            return "Unknown error";
        }

        String[] lines = output.split("\n");

        // Search backward for lines containing error indicators
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            String lowerLine = line.toLowerCase();

            if (lowerLine.contains("error") ||
                    lowerLine.contains("invalid") ||
                    lowerLine.contains("could not") ||
                    lowerLine.contains("failed")) {
                return line;
            }
        }

        // If no error indicator found, return last non-empty line
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                return line;
            }
        }

        return "Unknown error";
    }

    /** Preserves full-colour raster content without requiring a vector tracing delegate. */
    private ConversionResult convertSvg(Path input, Path output, ImageSettings settings,
            ProgressCallback callback, String fileId, ProcessRegistry registry) throws ToolExecutionException {
        Path raster = null;
        Instant start = Instant.now();
        try {
            // System temp dir: the output parent may be missing (throws
            // NoSuchFileException) or world-writable.
            raster = Files.createTempFile("omc-svg-", ".png");
            ConversionResult result = convertImage(input, raster, settings, callback, fileId, registry);
            if (!result.success()) return result;
            if (Thread.currentThread().isInterrupted()) {
                return ConversionResult.cancelled(fileId, result.toolOutput().orElse(null),
                        Duration.between(start, Instant.now()), Files.size(input), ConversionTool.IMAGEMAGICK);
            }
            RasterSvgExporter.write(raster, output);
            return ConversionResult.success(fileId, output, result.toolOutput().orElse(null),
                    Duration.between(start, Instant.now()), Files.size(input), Files.size(output), ConversionTool.IMAGEMAGICK);
        } catch (IOException e) {
            cleanupPartialFile(output);
            throw new ToolExecutionException("Could not create SVG output: " + e.getMessage(),
                    ErrorCode.TOOL_EXECUTION_FAILED, "imagemagick");
        } finally {
            if (raster != null) {
                try { Files.deleteIfExists(raster); }
                catch (IOException e) { logger.warn("Could not remove intermediate image", e); }
            }
        }
    }

    /** Checks whether the selected encoder accepts ImageMagick's quality option. */
    private boolean supportsQualityParameter(FileFormat format) {
        // Requirement REQ-PDF-1.3: PDF supports quality parameter for compression
        return format == FileFormat.JPEG ||
                format == FileFormat.PNG ||
                format == FileFormat.WEBP ||
                format == FileFormat.PDF;
    }

    /**
     * Extracts file extension from a path.
     * 
     * @param path file path
     * @return file extension (lowercase, without dot) or empty string if no
     *         extension
     */
    private String getFileExtension(Path path) {
        String fileName = path.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Gets the ImageMagick convert executable path.
     * 
     * @return convert binary path
     */
    public Path getConvertPath() {
        return convertPath;
    }
}
