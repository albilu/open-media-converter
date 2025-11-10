// filepath: src/main/java/org/omc/service/PandocService.java

package org.omc.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.omc.core.ProcessRegistry;
import org.omc.core.ProgressCallback;
import org.omc.exception.ErrorCode;
import org.omc.exception.ToolExecutionException;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionTool;
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for executing Pandoc document conversions.
 * Pandoc handles text-based document formats: Markdown, HTML, DOCX, RTF, ODT,
 * EPUB.
 * 
 * Requirements:
 * - REQ-006.4: Document format conversion with formatting preservation
 * - REQ-004.2: Tool execution and process management
 * - REQ-004.3: Progress tracking for conversions
 */
public class PandocService {

    private static final Logger logger = LoggerFactory.getLogger(PandocService.class);

    /** Maximum size of captured tool output (1MB) to prevent memory issues */
    private static final int MAX_OUTPUT_SIZE = 1024 * 1024;

    /** Message appended when output is truncated due to size limit */
    private static final String TRUNCATION_MESSAGE = "\n[Output truncated - exceeded 1MB limit]\n";

    private final Path pandocPath;

    // Pandoc-supported document formats for input
    private static final List<FileFormat> SUPPORTED_INPUT_FORMATS = List.of(
            FileFormat.MARKDOWN, FileFormat.HTML, FileFormat.DOCX,
            FileFormat.RTF, FileFormat.ODT, FileFormat.EPUB, FileFormat.TXT);

    // Pandoc-supported document formats for output
    private static final List<FileFormat> SUPPORTED_OUTPUT_FORMATS = List.of(
            FileFormat.MARKDOWN, FileFormat.HTML, FileFormat.DOCX,
            FileFormat.RTF, FileFormat.ODT, FileFormat.EPUB, FileFormat.TXT, FileFormat.PDF);

    /**
     * Creates a new PandocService with the specified Pandoc binary path.
     * 
     * Requirement REQ-004.1: Tool discovery and configuration
     * 
     * @param pandocPath path to the Pandoc executable
     * @throws NullPointerException if pandocPath is null
     */
    public PandocService(Path pandocPath) {
        this.pandocPath = Objects.requireNonNull(pandocPath, "pandocPath must not be null");
        logger.debug("PandocService initialized with path: {}", pandocPath);
    }

    /**
     * Converts a document using Pandoc.
     * 
     * Requirements:
     * - REQ-006.4: Document format conversion
     * - REQ-004.2: Tool execution and process management
     * 
     * @param inputPath        path to input document
     * @param outputPath       path to output document
     * @param settings         document conversion settings
     * @param progressCallback callback for progress updates (can be no-op)
     * @return conversion result with success status and timing
     * @throws ToolExecutionException if conversion fails
     * @throws NullPointerException   if any parameter is null
     */
    public ConversionResult convertDocument(
            Path inputPath,
            Path outputPath,
            DocumentSettings settings,
            ProgressCallback progressCallback) throws ToolExecutionException {
        return convertDocument(inputPath, outputPath, settings, progressCallback, null, ProcessRegistry.noOp());
    }

    /**
     * Converts a document using Pandoc with process registration for cancellation
     * support.
     * 
     * Requirements:
     * - REQ-006.4: Document format conversion
     * - REQ-004.2: Tool execution and process management
     * 
     * @param inputPath        path to input document
     * @param outputPath       path to output document
     * @param settings         document conversion settings
     * @param progressCallback callback for progress updates (can be no-op)
     * @param fileId           file ID for process registration (can be null)
     * @param processRegistry  registry to track active processes
     * @return conversion result with success status and timing
     * @throws ToolExecutionException if conversion fails
     * @throws NullPointerException   if any parameter is null
     */
    public ConversionResult convertDocument(
            Path inputPath,
            Path outputPath,
            DocumentSettings settings,
            ProgressCallback progressCallback,
            String fileId,
            ProcessRegistry processRegistry) throws ToolExecutionException {

        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        Objects.requireNonNull(progressCallback, "progressCallback must not be null");

        Instant startTime = Instant.now();
        long inputSize = 0;

        try {
            inputSize = Files.size(inputPath);
        } catch (IOException e) {
            logger.warn("Could not determine input file size: {}", e.getMessage());
        }

        try {
            // Build Pandoc command
            List<String> command = buildCommand(inputPath, outputPath, settings);

            logger.debug("Executing Pandoc command: {}", String.join(" ", command));

            // Execute conversion process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Register process for cancellation support
            if (fileId != null && processRegistry != null) {
                processRegistry.registerProcess(fileId, process);
            }

            // Read output in a separate thread
            StringBuilder outputLog = new StringBuilder(4096); // Initial capacity for performance
            StringBuilder errorOutput = new StringBuilder();

            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    int lineCount = 0;
                    boolean outputTruncated = false;

                    while ((line = reader.readLine()) != null) {
                        lineCount++;

                        // Requirement: Task 6.21 - Enforce 1MB output size limit
                        // Check every 100 lines for performance (avoid constant size checks)
                        if (!outputTruncated) {
                            if (lineCount % 100 == 0 && outputLog.length() > MAX_OUTPUT_SIZE) {
                                outputLog.append(TRUNCATION_MESSAGE);
                                outputTruncated = true;
                                logger.warn("Tool output exceeded 1MB limit, truncating further output");
                            } else {
                                outputLog.append(line).append("\n");
                            }
                        }
                        // Continue reading even after truncation to detect errors

                        // Track errors and warnings separately
                        if (line.toLowerCase().contains("error") || line.toLowerCase().contains("warning")) {
                            errorOutput.append(line).append("\n");
                        }
                        logger.trace("Pandoc output: {}", line);
                    }
                } catch (IOException e) {
                    logger.warn("Error reading Pandoc output: {}", e.getMessage());
                }
            });

            outputReader.start();

            // Pandoc doesn't provide progress updates, so we simulate progress
            Thread progressThread = simulateProgress(process, progressCallback, inputSize);

            // Wait for process completion with periodic interruption checks
            int exitCode = -1;
            boolean finished = false;

            while (!finished) {
                // Check for interruption (from cancel operation)
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Pandoc process interrupted, destroying process");
                    process.destroyForcibly();
                    progressThread.interrupt(); // Stop progress simulation
                    throw new InterruptedException("Conversion cancelled by user");
                }

                // Wait for process with short timeout to allow interruption checks
                finished = process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);
            }

            exitCode = process.exitValue();

            // Wait for progress thread to complete its final 100% update before proceeding
            // This ensures the completion status is set AFTER all progress updates
            progressThread.join(1000); // Wait up to 1 second for progress thread
            outputReader.join();

            Duration conversionTime = Duration.between(startTime, Instant.now());

            if (exitCode != 0) {
                String errorMessage = errorOutput.length() > 0
                        ? errorOutput.toString()
                        : "Pandoc conversion failed with exit code " + exitCode;

                logger.error("Pandoc conversion failed: {}", errorMessage);

                // Requirement REQ-004.2: Clean up partial output file on error
                cleanupPartialFile(outputPath);

                return ConversionResult.failure(
                        inputPath.toString(),
                        "Pandoc conversion failed (exit code " + exitCode + "): " + errorMessage,
                        outputLog.toString(), // Tool output for debugging
                        conversionTime,
                        inputSize,
                        ConversionTool.PANDOC);
            }

            // Get output file size
            long outputSize = Files.exists(outputPath) ? Files.size(outputPath) : 0;

            logger.info("Pandoc conversion successful: {} -> {} in {}ms ({} bytes -> {} bytes)",
                    inputPath.getFileName(), outputPath.getFileName(), conversionTime.toMillis(), inputSize,
                    outputSize);

            return ConversionResult.success(
                    inputPath.toString(),
                    outputPath,
                    outputLog.toString(), // Tool output for conversion details dialog
                    conversionTime,
                    inputSize,
                    outputSize,
                    ConversionTool.PANDOC);

        } catch (IOException e) {
            Duration conversionTime = Duration.between(startTime, Instant.now());
            logger.error("I/O error during Pandoc conversion: {}", e.getMessage(), e);

            // Requirement REQ-004.2: Clean up partial output file on error
            cleanupPartialFile(outputPath);

            throw new ToolExecutionException(
                    "I/O error: " + e.getMessage(),
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "pandoc",
                    pandocPath.toString(),
                    null,
                    "I/O error: " + e.getMessage());

        } catch (InterruptedException e) {
            Duration conversionTime = Duration.between(startTime, Instant.now());
            Thread.currentThread().interrupt();
            logger.error("Pandoc conversion interrupted: {}", e.getMessage());

            // Requirement REQ-004.2: Clean up partial output file on error
            cleanupPartialFile(outputPath);

            throw new ToolExecutionException(
                    "Process interrupted: " + e.getMessage(),
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "pandoc",
                    pandocPath.toString(),
                    null,
                    "Process interrupted",
                    e);
        } finally {
            // Unregister process
            if (fileId != null && processRegistry != null) {
                processRegistry.unregisterProcess(fileId);
            }
        }
    }

    /**
     * Builds the Pandoc command line arguments.
     * 
     * Requirement REQ-006.4: Document conversion with formatting options
     * 
     * @param input    path to input file
     * @param output   path to output file
     * @param settings document settings
     * @return list of command arguments
     */
    public List<String> buildCommand(Path input, Path output, DocumentSettings settings) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(output, "output must not be null");
        Objects.requireNonNull(settings, "settings must not be null");

        List<String> command = new ArrayList<>();
        command.add(pandocPath.toString());

        // Input file
        command.add(input.toString());

        // Output file
        command.add("-o");
        command.add(output.toString());

        // Detect input and output formats from extensions
        FileFormat inputFormat = detectFormat(input);
        FileFormat outputFormat = detectFormat(output);

        // Explicitly set input format if known
        if (inputFormat != FileFormat.UNKNOWN) {
            command.add("-f");
            command.add(mapFormatToPandoc(inputFormat));
        }

        // Explicitly set output format if known
        if (outputFormat != FileFormat.UNKNOWN) {
            command.add("-t");
            command.add(mapFormatToPandoc(outputFormat));
        }

        // Apply document settings

        // Table of contents
        if (settings.generateTableOfContents()) {
            command.add("--toc");
            command.add("--toc-depth=3");
        }

        // Template (if provided)
        if (settings.templatePath() != null && Files.exists(settings.templatePath())) {
            command.add("--template=" + settings.templatePath().toString());
        }

        // Standalone document (includes headers, etc.)
        command.add("--standalone");

        // Preserve formatting (use appropriate writer options)
        if (settings.preserveFormatting()) {
            // For HTML output
            if (outputFormat == FileFormat.HTML) {
                command.add("--embed-resources");
            }

            // For DOCX output
            if (outputFormat == FileFormat.DOCX && settings.templatePath() != null) {
                command.add("--reference-doc=" + settings.templatePath().toString());
            }
        }

        // PDF-specific options (Pandoc uses LaTeX for PDF generation)
        if (outputFormat == FileFormat.PDF) {
            // Margins (convert mm to inches: 1 inch = 25.4 mm)
            double topInches = settings.marginTop() / 25.4;
            double bottomInches = settings.marginBottom() / 25.4;
            double leftInches = settings.marginLeft() / 25.4;
            double rightInches = settings.marginRight() / 25.4;

            command.add(String.format("-V geometry:margin=%fin", topInches));
            command.add(String.format("-V geometry:top=%fin", topInches));
            command.add(String.format("-V geometry:bottom=%fin", bottomInches));
            command.add(String.format("-V geometry:left=%fin", leftInches));
            command.add(String.format("-V geometry:right=%fin", rightInches));

            // PDF engine (pdflatex is default, but xelatex supports more fonts)
            command.add("--pdf-engine=xelatex");
        }

        return command;
    }

    /**
     * Detects document format from file path extension.
     * 
     * @param filePath file path to analyze
     * @return detected FileFormat
     */
    public FileFormat detectFormat(Path filePath) {
        Objects.requireNonNull(filePath, "filePath must not be null");

        String fileName = filePath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            String extension = fileName.substring(dotIndex + 1).toLowerCase();
            return FileFormat.fromExtension(extension);
        }

        return FileFormat.UNKNOWN;
    }

    /**
     * Gets the list of supported input formats for Pandoc.
     * 
     * @return list of supported input formats
     */
    public List<FileFormat> getSupportedInputFormats() {
        return new ArrayList<>(SUPPORTED_INPUT_FORMATS);
    }

    /**
     * Gets the list of supported output formats for Pandoc.
     * 
     * @return list of supported output formats
     */
    public List<FileFormat> getSupportedOutputFormats() {
        return new ArrayList<>(SUPPORTED_OUTPUT_FORMATS);
    }

    /**
     * Checks if a format is supported by Pandoc for input.
     * 
     * @param format file format to check
     * @return true if supported for input
     */
    public boolean supportsInput(FileFormat format) {
        return SUPPORTED_INPUT_FORMATS.contains(format);
    }

    /**
     * Checks if a format is supported by Pandoc for output.
     * 
     * @param format file format to check
     * @return true if supported for output
     */
    public boolean supportsOutput(FileFormat format) {
        return SUPPORTED_OUTPUT_FORMATS.contains(format);
    }

    /**
     * Maps FileFormat enum to Pandoc format identifier.
     * 
     * @param format FileFormat enum value
     * @return Pandoc format string
     */
    private String mapFormatToPandoc(FileFormat format) {
        return switch (format) {
            case MARKDOWN -> "markdown";
            case HTML -> "html";
            case DOCX -> "docx";
            case RTF -> "rtf";
            case ODT -> "odt";
            case EPUB -> "epub";
            case TXT -> "plain";
            case PDF -> "pdf";
            default -> "markdown"; // Default fallback
        };
    }

    /**
     * Simulates progress updates for Pandoc conversion.
     * Pandoc doesn't provide real-time progress, so we estimate based on file size
     * and time.
     * 
     * @param process   running Pandoc process
     * @param callback  progress callback
     * @param inputSize input file size in bytes
     * @return the progress thread (caller must join before checking exit code)
     */
    private Thread simulateProgress(Process process, ProgressCallback callback, long inputSize) {
        Thread progressThread = new Thread(() -> {
            try {
                double progress = 0.0;
                while (process.isAlive() && progress < 100.0) {
                    // Simulate progress: increment by 10% every 500ms
                    progress = Math.min(progress + 10.0, 95.0); // Cap at 95% until done

                    long bytesProcessed = (long) (inputSize * progress / 100.0);
                    double speed = bytesProcessed / (progress / 100.0 + 0.1); // Rough estimate

                    callback.onProgress(progress, bytesProcessed, speed);

                    Thread.sleep(500);
                }

                // Final progress update
                if (!process.isAlive()) {
                    callback.onProgress(100.0, inputSize, 0.0);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.trace("Progress simulation interrupted");
            }
        });

        progressThread.start();
        return progressThread;
    }

    /**
     * Gets the path to the Pandoc executable.
     * 
     * @return Pandoc binary path
     */
    public Path getPandocPath() {
        return pandocPath;
    }

    /**
     * Cleans up partial output file after a failed conversion.
     * Requirement REQ-004.2: Partial file cleanup on error.
     * 
     * @param outputPath path to the partial output file
     */
    private void cleanupPartialFile(Path outputPath) {
        if (outputPath == null) {
            return;
        }

        try {
            if (Files.exists(outputPath)) {
                long fileSize = Files.size(outputPath);
                Files.deleteIfExists(outputPath);
                logger.info("Cleaned up partial output file: {} ({} bytes)",
                        outputPath.getFileName(), fileSize);
            }
        } catch (IOException e) {
            logger.warn("Failed to clean up partial output file: {}", outputPath, e);
        }
    }
}
