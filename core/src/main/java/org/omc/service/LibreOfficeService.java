// filepath: src/main/java/org/omc/service/LibreOfficeService.java

package org.omc.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for executing LibreOffice document conversions.
 * LibreOffice handles Office and PDF formats: DOCX, XLSX, PPTX, ODT, ODS, ODP,
 * PDF.
 * 
 * Requirements:
 * - REQ-006.4: Document format conversion with Office format support
 * - REQ-004.2: Tool execution and process management
 * - REQ-004.3: Progress tracking for conversions
 */
public class LibreOfficeService {

    private static final Logger logger = LoggerFactory.getLogger(LibreOfficeService.class);

    private final Path libreOfficePath;

    // Tool output capture limits (Requirement REQ-FL-2.2)
    private static final int MAX_OUTPUT_SIZE = 1_048_576; // 1MB
    private static final String TRUNCATION_MESSAGE = "\n[Output truncated - exceeded 1MB limit]\n";

    // LibreOffice-supported document formats for input
    private static final List<FileFormat> SUPPORTED_INPUT_FORMATS = List.of(
            FileFormat.DOCX, FileFormat.XLSX, FileFormat.PPTX,
            FileFormat.ODT, FileFormat.ODS, FileFormat.ODP,
            FileFormat.RTF, FileFormat.TXT, FileFormat.HTML);

    // LibreOffice-supported document formats for output
    private static final List<FileFormat> SUPPORTED_OUTPUT_FORMATS = List.of(
            FileFormat.PDF, FileFormat.DOCX, FileFormat.XLSX, FileFormat.PPTX,
            FileFormat.ODT, FileFormat.ODS, FileFormat.ODP,
            FileFormat.HTML, FileFormat.TXT);

    /**
     * Creates a new LibreOfficeService with the specified LibreOffice binary path.
     * 
     * Requirement REQ-004.1: Tool discovery and configuration
     * 
     * @param libreOfficePath path to the LibreOffice executable (soffice)
     * @throws NullPointerException if libreOfficePath is null
     */
    public LibreOfficeService(Path libreOfficePath) {
        this.libreOfficePath = Objects.requireNonNull(libreOfficePath, "libreOfficePath must not be null");
        logger.debug("LibreOfficeService initialized with path: {}", libreOfficePath);
    }

    /**
     * Converts a document using LibreOffice.
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
     * Converts a document using LibreOffice with process registration for
     * cancellation support.
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

        // Create a temporary directory for LibreOffice output
        Path tempOutputDir = null;

        try {
            tempOutputDir = Files.createTempDirectory("libreoffice-conversion-");

            // Build LibreOffice command with temp directory
            List<String> command = buildCommand(inputPath, outputPath, settings, tempOutputDir);
            final Path finalTempOutputDir = tempOutputDir; // For cleanup in finally block

            logger.debug("Executing LibreOffice command: {}", String.join(" ", command));

            // Execute conversion process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // Register process for cancellation support
            if (fileId != null && processRegistry != null) {
                processRegistry.registerProcess(fileId, process);
            }

            // Read output in a separate thread (Requirement REQ-FL-2.2: Capture tool
            // output)
            StringBuilder outputLog = new StringBuilder(4096);
            StringBuilder errorOutput = new StringBuilder();
            final boolean[] outputTruncated = { false };

            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    int lineCount = 0;
                    while ((line = reader.readLine()) != null) {
                        lineCount++;

                        // Capture output with 1MB size limit (check every 100 lines for performance)
                        if (!outputTruncated[0]) {
                            if (lineCount % 100 == 0 && outputLog.length() > MAX_OUTPUT_SIZE) {
                                outputLog.append(TRUNCATION_MESSAGE);
                                outputTruncated[0] = true;
                                logger.warn("LibreOffice output exceeded 1MB limit, truncating");
                            } else {
                                outputLog.append(line).append("\n");
                            }
                        }

                        // Track error/warning lines separately
                        if (line.toLowerCase().contains("error") || line.toLowerCase().contains("warning")) {
                            errorOutput.append(line).append("\n");
                        }

                        logger.trace("LibreOffice output: {}", line);
                    }
                } catch (IOException e) {
                    logger.warn("Error reading LibreOffice output: {}", e.getMessage());
                }
            });

            outputReader.start();

            // LibreOffice doesn't provide progress updates, so we simulate progress
            Thread progressThread = simulateProgress(process, progressCallback, inputSize);

            // Wait for process completion with periodic interruption checks
            int exitCode = -1;
            boolean finished = false;

            while (!finished) {
                // Check for interruption (from cancel operation)
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("LibreOffice process interrupted, destroying process");
                    process.destroyForcibly();
                    progressThread.interrupt(); // Stop progress simulation
                    throw new InterruptedException("Conversion cancelled by user");
                }

                // Wait for process with short timeout to allow interruption checks
                finished = process.waitFor(500, TimeUnit.MILLISECONDS);
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
                        : "LibreOffice conversion failed with exit code " + exitCode;

                logger.error("LibreOffice conversion failed: {}", errorMessage);

                // Clean up partial output file if it exists
                if (Files.exists(outputPath)) {
                    try {
                        Files.deleteIfExists(outputPath);
                    } catch (IOException deleteEx) {
                        logger.warn("Failed to delete partial output file: {}", outputPath, deleteEx);
                    }
                }

                // Return failure result with captured output (Requirement REQ-FL-2.2)
                return ConversionResult.failure(
                        inputPath.toString(),
                        errorMessage,
                        outputLog.toString(),
                        conversionTime,
                        inputSize,
                        ConversionTool.LIBREOFFICE);
            }

            // LibreOffice creates output file using input filename with new extension
            // Example: input "document.docx" -> output "document.pdf" (not custom name)
            // We need to find the generated file and move it to the desired output path
            String inputFileName = inputPath.getFileName().toString();
            String inputBaseName = inputFileName.substring(0, inputFileName.lastIndexOf('.'));
            FileFormat outputFormat = detectFormat(outputPath);
            String expectedExtension = mapFormatToLibreOffice(outputFormat);
            Path generatedFile = tempOutputDir.resolve(inputBaseName + "." + expectedExtension);

            logger.debug("Looking for generated file: {}", generatedFile);

            if (!Files.exists(generatedFile)) {
                String errorMessage = "LibreOffice conversion succeeded but expected output file was not created: "
                        + generatedFile + " (looked in " + tempOutputDir + ")";
                logger.error(errorMessage);

                // Return failure result with captured output (Requirement REQ-FL-2.2)
                return ConversionResult.failure(
                        inputPath.toString(),
                        errorMessage,
                        outputLog.toString(),
                        conversionTime,
                        inputSize,
                        ConversionTool.LIBREOFFICE);
            }

            // Move/rename the generated file to the desired output path
            Files.createDirectories(outputPath.getParent() != null ? outputPath.getParent() : Path.of("."));
            Files.move(generatedFile, outputPath, StandardCopyOption.REPLACE_EXISTING);

            logger.debug("Moved generated file from {} to {}", generatedFile, outputPath);

            // Get output file size
            long outputSize = Files.size(outputPath);

            logger.info("LibreOffice conversion successful: {} -> {} in {}ms ({} bytes -> {} bytes)",
                    inputPath.getFileName(), outputPath.getFileName(), conversionTime.toMillis(), inputSize,
                    outputSize);

            // Return success result with captured output (Requirement REQ-FL-2.2)
            return ConversionResult.success(
                    inputPath.toString(),
                    outputPath,
                    outputLog.toString(),
                    conversionTime,
                    inputSize,
                    outputSize,
                    ConversionTool.LIBREOFFICE);

        } catch (IOException e) {
            logger.error("I/O error during LibreOffice conversion: {}", e.getMessage(), e);

            throw new ToolExecutionException(
                    "I/O error: " + e.getMessage(),
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "libreoffice",
                    libreOfficePath.toString(),
                    null,
                    "I/O error: " + e.getMessage());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("LibreOffice conversion interrupted: {}", e.getMessage());

            throw new ToolExecutionException(
                    "Process interrupted: " + e.getMessage(),
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "libreoffice",
                    libreOfficePath.toString(),
                    null,
                    "Process interrupted",
                    e);
        } finally {
            // Unregister process
            if (fileId != null && processRegistry != null) {
                processRegistry.unregisterProcess(fileId);
            }

            // Clean up temporary directory
            if (tempOutputDir != null && Files.exists(tempOutputDir)) {
                try {
                    Files.walk(tempOutputDir)
                            .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    logger.warn("Failed to delete temp file: {}", path, e);
                                }
                            });
                } catch (IOException e) {
                    logger.warn("Failed to clean up temp directory: {}", tempOutputDir, e);
                }
            }
        }
    }

    /**
     * Builds the LibreOffice command line arguments.
     * 
     * Requirement REQ-006.4: Document conversion with Office formats
     * 
     * LibreOffice uses the format: soffice --headless --convert-to <format>
     * --outdir
     * <dir>
     * <input>
     * 
     * @param input     path to input file
     * @param output    path to output file (used to determine format)
     * @param settings  document settings (currently limited support in LibreOffice
     *                  CLI)
     * @param outputDir directory where LibreOffice will create the output file
     * @return list of command arguments
     */
    private List<String> buildCommand(Path input, Path output, DocumentSettings settings, Path outputDir) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(output, "output must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        Objects.requireNonNull(outputDir, "outputDir must not be null");

        List<String> command = new ArrayList<>();
        command.add(libreOfficePath.toString());

        // Headless mode (no GUI)
        command.add("--headless");

        // Detect output format from extension
        FileFormat outputFormat = detectFormat(output);

        // Convert-to option with format
        command.add("--convert-to");
        command.add(mapFormatToLibreOffice(outputFormat));

        // Output directory (use provided outputDir instead of deriving from output
        // path)
        command.add("--outdir");
        command.add(outputDir.toString());

        // Input file (must be last)
        command.add(input.toString());

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
     * Gets the list of supported input formats for LibreOffice.
     * 
     * @return list of supported input formats
     */
    public List<FileFormat> getSupportedInputFormats() {
        return new ArrayList<>(SUPPORTED_INPUT_FORMATS);
    }

    /**
     * Gets the list of supported output formats for LibreOffice.
     * 
     * @return list of supported output formats
     */
    public List<FileFormat> getSupportedOutputFormats() {
        return new ArrayList<>(SUPPORTED_OUTPUT_FORMATS);
    }

    /**
     * Checks if a format is supported by LibreOffice for input.
     * 
     * @param format file format to check
     * @return true if supported for input
     */
    public boolean supportsInput(FileFormat format) {
        return SUPPORTED_INPUT_FORMATS.contains(format);
    }

    /**
     * Checks if a format is supported by LibreOffice for output.
     * 
     * @param format file format to check
     * @return true if supported for output
     */
    public boolean supportsOutput(FileFormat format) {
        return SUPPORTED_OUTPUT_FORMATS.contains(format);
    }

    /**
     * Maps FileFormat enum to LibreOffice format identifier.
     * 
     * LibreOffice format names:
     * - pdf: PDF
     * - docx: Microsoft Word 2007-365
     * - xlsx: Microsoft Excel 2007-365
     * - pptx: Microsoft PowerPoint 2007-365
     * - odt: OpenDocument Text
     * - ods: OpenDocument Spreadsheet
     * - odp: OpenDocument Presentation
     * - html: HTML
     * - txt: Plain Text
     * 
     * @param format FileFormat enum value
     * @return LibreOffice format string
     */
    private String mapFormatToLibreOffice(FileFormat format) {
        return switch (format) {
            case PDF -> "pdf";
            case DOCX -> "docx";
            case DOC -> "doc";
            case XLSX -> "xlsx";
            case XLS -> "xls";
            case PPTX -> "pptx";
            case PPT -> "ppt";
            case ODT -> "odt";
            case ODS -> "ods";
            case ODP -> "odp";
            case HTML -> "html";
            case TXT -> "txt";
            case RTF -> "rtf";
            case JPEG -> "jpeg";
            default -> "pdf"; // Default fallback to PDF
        };
    }

    /**
     * Simulates progress updates for LibreOffice conversion.
     * LibreOffice doesn't provide real-time progress, so we estimate based on file
     * size and time.
     * 
     * @param process   running LibreOffice process
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
     * Gets the path to the LibreOffice executable.
     * 
     * @return LibreOffice binary path
     */
    public Path getLibreOfficePath() {
        return libreOfficePath;
    }
}
