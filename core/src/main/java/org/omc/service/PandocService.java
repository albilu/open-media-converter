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
import org.omc.core.ToolProcess;
import org.omc.core.ProgressCallback;
import org.omc.exception.ErrorCode;
import org.omc.exception.ToolExecutionException;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionTool;
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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

    /**
     * Maximum wall time for a Pandoc process before forced termination
     * (1 hour, mirroring FFmpegService). Package-visible and non-final so
     * tests can scale it down; not part of the public API.
     */
    static long PROCESS_TIMEOUT_MILLIS = java.util.concurrent.TimeUnit.HOURS.toMillis(1);

    /**
     * How long the main thread waits for the output reader to drain after the
     * process has ended before using whatever was captured. The reader is a
     * daemon and pipe reads are not interruptible, so this join is a brief
     * courtesy, not a correctness requirement.
     */
    private static final long READER_JOIN_MILLIS = java.util.concurrent.TimeUnit.SECONDS.toMillis(2);

    private final Path pandocPath;
    private final LibreOfficeService pdfRenderer;

    // Pandoc-supported document formats for input
    private static final List<FileFormat> SUPPORTED_INPUT_FORMATS = List.of(
            FileFormat.MARKDOWN, FileFormat.HTML, FileFormat.DOCX,
            FileFormat.RTF, FileFormat.ODT, FileFormat.EPUB, FileFormat.TXT,
            FileFormat.TEX, FileFormat.LATEX, FileFormat.RST, FileFormat.ORG);

    // Pandoc-supported document formats for output
    private static final List<FileFormat> SUPPORTED_OUTPUT_FORMATS = List.of(
            FileFormat.MARKDOWN, FileFormat.HTML, FileFormat.DOCX,
            FileFormat.RTF, FileFormat.ODT, FileFormat.EPUB, FileFormat.TXT, FileFormat.PDF,
            FileFormat.TEX, FileFormat.LATEX, FileFormat.RST, FileFormat.ORG);

    /**
     * Creates a new PandocService with the specified Pandoc binary path.
     * 
     * Requirement REQ-004.1: Tool discovery and configuration
     * 
     * @param pandocPath path to the Pandoc executable
     * @throws NullPointerException if pandocPath is null
     */
    public PandocService(Path pandocPath) {
        this(pandocPath, null);
    }

    /**
     * Creates a text converter with an optional LibreOffice PDF renderer.
     * @param pandocPath Pandoc executable
     * @param pdfRenderer renderer required for PDF output, or null if unavailable
     */
    public PandocService(Path pandocPath, LibreOfficeService pdfRenderer) {
        this.pandocPath = Objects.requireNonNull(pandocPath, "pandocPath must not be null");
        this.pdfRenderer = pdfRenderer;
        logger.debug("PandocService initialized with path: {}", pandocPath);
    }

    /**
     * Checks format support and all runtime dependencies for this service.
     * @param input source format
     * @param output destination format
     * @return whether this instance can execute the complete conversion pipeline
     */
    public boolean supportsConversion(FileFormat input, FileFormat output) {
        return canConvert(input, output) && (output != FileFormat.PDF || pdfRenderer != null);
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

        return convertDocument(inputPath, outputPath, settings, progressCallback, fileId, processRegistry,
                detectFormat(inputPath));
    }

    /**
     * Converts using the authoritative input format established at admission.
     *
     * @param inputPath source document
     * @param outputPath destination document
     * @param settings document options
     * @param progressCallback progress receiver
     * @param fileId conversion identifier, or null
     * @param processRegistry process owner for cancellation
     * @param inputFormat detected input format, independent of its extension
     * @return conversion result
     * @throws ToolExecutionException if execution fails
     */
    public ConversionResult convertDocument(Path inputPath, Path outputPath, DocumentSettings settings,
            ProgressCallback progressCallback, String fileId, ProcessRegistry processRegistry, FileFormat inputFormat)
            throws ToolExecutionException {
        Objects.requireNonNull(inputFormat, "inputFormat must not be null");

        // MDC correlation: every log line emitted during this conversion
        // carries the file and tool context
        try (MDC.MDCCloseable omcFileCtx = MDC.putCloseable("omcFile", String.valueOf(fileId));
                MDC.MDCCloseable omcToolCtx = MDC.putCloseable("omcTool", "pandoc")) {
            return convertDocumentInternal(inputPath, outputPath, settings, progressCallback, fileId, processRegistry, inputFormat);
        }
    }

    private ConversionResult convertDocumentInternal(
            Path inputPath,
            Path outputPath,
            DocumentSettings settings,
            ProgressCallback progressCallback,
            String fileId,
            ProcessRegistry processRegistry, FileFormat inputFormat) throws ToolExecutionException {

        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        Objects.requireNonNull(progressCallback, "progressCallback must not be null");
        if (!settings.isValid()) {
            throw new IllegalArgumentException("Invalid document settings: " + settings);
        }

        if (detectFormat(outputPath) == FileFormat.PDF) {
            return convertPdf(inputPath, outputPath, settings, progressCallback, fileId, processRegistry, inputFormat);
        }
        Instant startTime = Instant.now();
        long inputSize = 0;
        ToolProcess ownedProcess = null;

        try {
            inputSize = Files.size(inputPath);
        } catch (IOException e) {
            logger.warn("Could not determine input file size: {}", e.getMessage());
        }

        Path formattingFilter = null;
        Path literalInput = null;
        Path extractedResources = null;
        boolean successful = false;
        try {
            if (inputFormat == FileFormat.TXT && detectFormat(outputPath) == FileFormat.TXT
                    && settings.preserveFormatting() && settings.templatePath() == null
                    && !settings.generateTableOfContents()) {
                // Pandoc's plain writer indents code blocks and adds document
                // spacing. A literal text round trip must retain the original bytes.
                Files.copy(inputPath, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (processRegistry != null) processRegistry.awaitRunning();
                progressCallback.onProgress(100, inputSize, 0);
                successful = true;
                return ConversionResult.success(inputPath.toString(), outputPath, "Preserved literal text without reformatting",
                        Duration.between(startTime, Instant.now()), inputSize, Files.size(outputPath), ConversionTool.PANDOC);
            }
            Path preparedInput = inputPath.toAbsolutePath();
            if (inputFormat == FileFormat.TXT) {
                // A preformatted HTML block is a literal text reader for Pandoc:
                // markup punctuation and whitespace remain text in every writer.
                literalInput = Files.createTempFile("omc-literal-", ".html");
                String text = Files.readString(inputPath).replace("&", "&amp;")
                        .replace("<", "&lt;").replace(">", "&gt;");
                Files.writeString(literalInput, "<!doctype html><meta charset=\"utf-8\"><pre><code>" + text + "</code></pre>");
                preparedInput = literalInput;
            }
            // Build Pandoc command
            List<String> command = buildCommand(preparedInput, outputPath.toAbsolutePath(), settings,
                    literalInput == null ? inputFormat : FileFormat.HTML, inputPath.toAbsolutePath().getParent());
            command.add("--fail-if-warnings");
            command.add("--metadata=pagetitle:" + inputPath.getFileName());
            FileFormat outputFormat = detectFormat(outputPath);
            if (literalInput != null && outputFormat == FileFormat.HTML) {
                // LibreOffice's HTML reader does not resolve Pandoc's default
                // font fallback list. A single generic family retains aligned
                // plain-text columns in both HTML and the PDF intermediate.
                command.add("--variable=monofont:monospace");
            }
            if (java.util.Set.of(FileFormat.MARKDOWN, FileFormat.RST, FileFormat.ORG,
                    FileFormat.TEX, FileFormat.LATEX).contains(outputFormat)) {
                Path directory = outputPath.toAbsolutePath().getParent();
                Files.createDirectories(directory);
                extractedResources = Files.createTempDirectory(directory, "omc-resources-");
                command.add("--extract-media=" + extractedResources.getFileName());
            }
            if (!settings.preserveFormatting()) {
                formattingFilter = Files.createTempFile("omc-formatting-", ".lua");
                try (var filter = getClass().getResourceAsStream("/pandoc/plain-formatting.lua")) {
                    if (filter == null) throw new IOException("Document formatting filter is missing");
                    Files.copy(filter, formattingFilter, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                command.add("--lua-filter=" + formattingFilter);
            }

            logger.debug("Executing Pandoc command: {}", String.join(" ", command));

            // Execute conversion process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(outputPath.toAbsolutePath().getParent().toFile());
            processBuilder.redirectErrorStream(true);

            ToolProcess process = ToolProcess.start(processBuilder, fileId, processRegistry);
            ownedProcess = process;


            // Read output in a separate daemon thread (named + handler so a
            // leak can never pin JVM shutdown)
            StringBuilder outputLog = new StringBuilder(4096); // Initial capacity for performance
            // errorOutput is capped independently: warning spam on corrupt
            // input must not bypass the 1MB cap via this side buffer.
            StringBuilder errorOutput = new StringBuilder();
            final int MAX_ERROR_OUTPUT_SIZE = 64 * 1024;

            Thread outputReader = org.omc.util.ThreadUtils.createThreadFactory("Pandoc-Reader")
                    .newThread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    int lineCount = 0;
                    boolean outputTruncated = false;

                    while ((line = reader.readLine()) != null) {
                        lineCount++;

                        // Requirement: Task 6.21 - Enforce 1MB output size
                        // limit (checked on every line so oversized single
                        // lines also truncate).
                        if (!outputTruncated) {
                            if (outputLog.length() + line.length() + 1 > MAX_OUTPUT_SIZE) {
                                outputLog.append(TRUNCATION_MESSAGE);
                                outputTruncated = true;
                                logger.warn("Tool output exceeded 1MB limit, truncating further output");
                            } else {
                                outputLog.append(line).append("\n");
                            }
                        }
                        // Continue reading even after truncation to detect errors

                        // Track errors and warnings separately (capped)
                        if (line.toLowerCase(java.util.Locale.ROOT).contains("error")
                                || line.toLowerCase(java.util.Locale.ROOT).contains("warning")) {
                            if (errorOutput.length() + line.length() + 1 <= MAX_ERROR_OUTPUT_SIZE) {
                                errorOutput.append(line).append("\n");
                            }
                        }
                        logger.trace("Pandoc output: {}", line);
                    }
                } catch (IOException e) {
                    logger.warn("Error reading Pandoc output: {}", e.getMessage());
                }
            });

            outputReader.start();

            // The Pandoc CLI has no measurable completion fraction.
            if (!process.isPaused()) progressCallback.onIndeterminate();

            // Wait for process to complete with timeout (1 hour default)
            // Check for interruption periodically so cancellation can work
            int exitCode = -1;
            boolean finished = false;
            long startWaitTime = process.activeElapsed().toMillis();

            while (!finished && (process.activeElapsed().toMillis() - startWaitTime) < PROCESS_TIMEOUT_MILLIS) {
                // Check for interruption (from cancel operation)
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Pandoc process interrupted, destroying process");
                    process.destroyForcibly();
                    throw new InterruptedException("Conversion cancelled by user");
                }

                // Wait for process with short timeout to allow interruption checks
                finished = process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (!finished && !process.isPaused()) progressCallback.onIndeterminate();
            }

            if (!finished) {
                logger.error("Pandoc process timed out after 1 hour");
                process.destroyForcibly();
                throw new ToolExecutionException(
                        "Pandoc process timed out after 1 hour",
                        ErrorCode.TOOL_EXECUTION_FAILED,
                        "pandoc",
                        pandocPath.toString(),
                        null,
                        "Process timeout after 1 hour");
            }

            exitCode = process.exitValue();

            // Bounded join: a grandchild inheriting the pipe would otherwise
            // block this join forever (pipe reads are not interruptible, but
            // the reader is a daemon so interrupting is best-effort).
            outputReader.join(READER_JOIN_MILLIS);
            if (outputReader.isAlive()) {
                outputReader.interrupt();
            }

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
            DocumentOutputOptions.apply(outputPath, detectFormat(outputPath), settings);
            long outputSize = Files.size(outputPath);
            if (processRegistry != null) processRegistry.awaitRunning();
            progressCallback.onProgress(100, inputSize, 0);
            successful = true;

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
            if (ownedProcess != null && ownedProcess.isAlive()) ownedProcess.destroyForcibly();
            if (literalInput != null) {
                try { Files.deleteIfExists(literalInput); }
                catch (IOException e) { logger.warn("Could not remove literal document input", e); }
            }
            if (extractedResources != null && !successful) {
                try (var paths = Files.walk(extractedResources)) {
                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                } catch (IOException e) { logger.warn("Could not remove incomplete document resources", e); }
            }
            if (formattingFilter != null) {
                try { Files.deleteIfExists(formattingFilter); }
                catch (IOException e) { logger.warn("Could not remove document filter", e); }
            }
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
     * @throws IllegalArgumentException if PDF output or TXT input is requested;
     *                                  use convertDocument for these preparation workflows
     */
    public List<String> buildCommand(Path input, Path output, DocumentSettings settings) {
        return buildCommand(input, output, settings, detectFormat(input), input.toAbsolutePath().getParent());
    }

    private List<String> buildCommand(Path input, Path output, DocumentSettings settings,
            FileFormat inputFormat, Path resourceDir) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(output, "output must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        if (!settings.isValid()) {
            throw new IllegalArgumentException("Invalid document settings: " + settings);
        }

        List<String> command = new ArrayList<>();
        command.add(pandocPath.toString());

        // Input file (dash-prefixed basenames are absolutized so getopt
        // never parses them as flags)
        command.add(FFmpegService.safePathArg(input));
        command.add("--resource-path=" + (resourceDir != null ? resourceDir.toString() : "."));

        // Output file
        command.add("-o");
        command.add(FFmpegService.safePathArg(output));

        // The output path belongs to our staging plan; input content was detected at admission.
        FileFormat outputFormat = detectFormat(output);
        if (outputFormat == FileFormat.PDF) {
            throw new IllegalArgumentException("PDF output uses convertDocument with a LibreOffice renderer.");
        }
        if (inputFormat == FileFormat.TXT) {
            throw new IllegalArgumentException("TXT input uses convertDocument for literal text preparation.");
        }

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

        // Template (if provided). NOTE: deliberately lenient (exists-only):
        // callers/tests pass directories here and pandoc reports misuse
        // itself; strict isRegularFile/isReadable checks broke that contract.
        if (settings.templatePath() != null && Files.exists(settings.templatePath())) {
            command.add((outputFormat == FileFormat.DOCX || outputFormat == FileFormat.ODT
                    ? "--reference-doc=" : "--template=") + settings.templatePath().toAbsolutePath());
        }

        // Standalone document (includes headers, etc.)
        command.add("--standalone");

        if (outputFormat == FileFormat.HTML) {
            // Resource portability is independent of stripping text formatting.
            // PDF rendering also needs resources embedded in its HTML intermediate.
            command.add("--embed-resources");
            command.add("--variable=header-includes:<style>@page { margin: " + settings.marginTop() + "mm "
                    + settings.marginRight() + "mm " + settings.marginBottom() + "mm "
                    + settings.marginLeft() + "mm; }</style>");
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
            case TEX, LATEX -> "latex";
            case RST -> "rst";
            case ORG -> "org";
            default -> throw new IllegalArgumentException("Unsupported Pandoc format: " + format);
        };
    }

    /**
     * Checks the supported reader/writer pair independently of installed tools.
     * @param input source format
     * @param output target format
     * @return true if the conversion is supported
     */
    public static boolean canConvert(FileFormat input, FileFormat output) {
        return SUPPORTED_INPUT_FORMATS.contains(input) && SUPPORTED_OUTPUT_FORMATS.contains(output);
    }

    private ConversionResult convertPdf(Path input, Path output, DocumentSettings settings,
            ProgressCallback callback, String fileId, ProcessRegistry registry, FileFormat inputFormat)
            throws ToolExecutionException {
        if (pdfRenderer == null) {
            throw new ToolExecutionException("Install LibreOffice to render text documents as PDF.",
                    ErrorCode.TOOL_NOT_FOUND, "libreoffice");
        }
        Path html = null;
        Instant start = Instant.now();
        // Neither stage exposes a completion fraction. Stage completion is not
        // half of the total work, and must not end the overall activity indicator.
        long[] lastActivity = {0};
        ProgressCallback stageProgress = (percent, bytes, speed) -> {
            long now = System.nanoTime();
            if (percent == ProgressCallback.INDETERMINATE
                    && (lastActivity[0] == 0 || now - lastActivity[0] >= 500_000_000L)) {
                lastActivity[0] = now;
                callback.onIndeterminate();
            }
        };
        try {
            // System temp dir (not the user output dir): inherits safe
            // permissions and avoids symlink games in world-writable parents.
            html = Files.createTempFile("omc-document-", ".html");
            ConversionResult textResult = convertDocument(input, html, settings.withOutputFormat(FileFormat.HTML),
                    stageProgress, fileId, registry, inputFormat);
            if (!textResult.success()) return textResult;
            if (Thread.currentThread().isInterrupted()) {
                throw new ToolExecutionException("Conversion cancelled", ErrorCode.TOOL_EXECUTION_FAILED, "pandoc");
            }
            ConversionResult pdf = pdfRenderer.convertDocument(html, output, settings,
                    stageProgress, fileId, registry);
            if (!pdf.success()) return pdf;
            callback.onProgress(100, Files.size(input), 0);
            return ConversionResult.success(fileId == null ? input.toString() : fileId, output,
                    textResult.toolOutput().orElse("") + "\n" + pdf.toolOutput().orElse(""),
                    Duration.between(start, Instant.now()), Files.size(input), Files.size(output), ConversionTool.PANDOC);
        } catch (IOException e) {
            throw new ToolExecutionException("Could not prepare the PDF document: " + e.getMessage(),
                    ErrorCode.TOOL_EXECUTION_FAILED, "pandoc");
        } finally {
            if (html != null) {
                try { Files.deleteIfExists(html); }
                catch (IOException e) { logger.warn("Could not remove intermediate document", e); }
            }
        }
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
