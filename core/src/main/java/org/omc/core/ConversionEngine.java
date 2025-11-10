// filepath: src/main/java/org/omc/core/ConversionEngine.java

package org.omc.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.omc.exception.ToolExecutionException;
import org.omc.model.AudioSettings;
import org.omc.model.BatchConversionResult;
import org.omc.model.BatchProgress;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionProgress;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionSettings;
import org.omc.model.ConversionTool;
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;
import org.omc.model.FileSettingsOverride;
import org.omc.model.FormatCategory;
import org.omc.model.ImageSettings;
import org.omc.model.ValidationResult;
import org.omc.model.VideoSettings;
import org.omc.service.FileHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core conversion engine responsible for orchestrating file conversions.
 * Manages parallel conversions, progress tracking, and conversion lifecycle.
 * 
 * Requirements:
 * - REQ-004.2: Batch processing with configurable parallelism, temporary file
 * management
 * - REQ-004.3: Progress tracking and status updates
 */
public class ConversionEngine implements ProcessRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ConversionEngine.class);
    private static final long DISK_SPACE_THRESHOLD_BYTES = 500L * 1024 * 1024; // 500 MB
    private static final long DISK_SPACE_CHECK_INTERVAL_MS = 5000; // Check every 5 seconds

    private final ToolManager toolManager;
    private final ValidationEngine validationEngine;
    private final ProgressEngine progressEngine;
    private final FileHandler fileHandler;
    private final ExecutorService executorService;
    private final ScheduledExecutorService diskSpaceMonitor;
    private final int parallelConversions;

    // State tracking for active conversions
    private final Map<String, CompletableFuture<ConversionResult>> activeConversions;
    private final Map<String, Process> activeProcesses; // Track processes for cancellation
    private final Map<String, ConversionResult> conversionResults; // Store conversion results (REQ-FL-2.2)
    private final AtomicBoolean paused;
    private final AtomicBoolean shuttingDown;
    private final AtomicBoolean diskSpacePaused;
    private volatile Path currentOutputDirectory;

    // Event handlers
    private BiConsumer<String, ConversionProgress> progressHandler;
    private BiConsumer<String, ConversionResult> completionHandler;

    /**
     * Creates a new ConversionEngine with specified dependencies and parallelism.
     * 
     * @param toolManager         the tool manager for selecting conversion tools
     * @param validationEngine    the validation engine for pre-conversion checks
     * @param progressEngine      the progress engine for tracking conversion
     *                            progress
     * @param fileHandler         the file handler for temporary file management
     * @param parallelConversions the maximum number of parallel conversions (1-16)
     * @throws IllegalArgumentException if parallelConversions is out of range
     */
    public ConversionEngine(
            ToolManager toolManager,
            ValidationEngine validationEngine,
            ProgressEngine progressEngine,
            FileHandler fileHandler,
            int parallelConversions) {

        Objects.requireNonNull(toolManager, "ToolManager cannot be null");
        Objects.requireNonNull(validationEngine, "ValidationEngine cannot be null");
        Objects.requireNonNull(progressEngine, "ProgressEngine cannot be null");
        Objects.requireNonNull(fileHandler, "FileHandler cannot be null");

        if (parallelConversions < 1 || parallelConversions > 16) {
            throw new IllegalArgumentException(
                    "Parallel conversions must be between 1 and 16, got: " + parallelConversions);
        }

        this.toolManager = toolManager;
        this.validationEngine = validationEngine;
        this.progressEngine = progressEngine;
        this.fileHandler = fileHandler;
        this.parallelConversions = parallelConversions;

        // Create bounded thread pool for conversions
        this.executorService = new ThreadPoolExecutor(
                parallelConversions,
                parallelConversions,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ConversionThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy());

        // Create scheduled executor for disk space monitoring
        // Requirement REQ-007.1: Monitor disk space during conversions
        this.diskSpaceMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "disk-space-monitor");
            t.setDaemon(true);
            return t;
        });

        this.activeConversions = new ConcurrentHashMap<>();
        this.activeProcesses = new ConcurrentHashMap<>();
        this.conversionResults = new ConcurrentHashMap<>();
        this.paused = new AtomicBoolean(false);
        this.shuttingDown = new AtomicBoolean(false);
        this.diskSpacePaused = new AtomicBoolean(false);
        this.currentOutputDirectory = null;

        // Start disk space monitoring
        startDiskSpaceMonitoring();

        logger.info("ConversionEngine initialized with {} parallel conversions", parallelConversions);
    }

    /**
     * Converts a batch of files with the specified settings.
     * Requirement REQ-004.2: Batch processing with parallel execution.
     * 
     * @param files    the list of files to convert
     * @param settings the conversion settings
     * @return a CompletableFuture that completes with the batch result
     * @throws IllegalStateException if the engine is shutting down
     */
    public CompletableFuture<BatchConversionResult> convertBatch(
            List<ConversionFile> files,
            ConversionSettings settings) {

        Objects.requireNonNull(files, "Files list cannot be null");
        Objects.requireNonNull(settings, "ConversionSettings cannot be null");

        if (shuttingDown.get()) {
            throw new IllegalStateException("ConversionEngine is shutting down");
        }

        logger.info("Starting batch conversion of {} files", files.size());
        Instant batchStart = Instant.now();

        // Set output directory for disk space monitoring
        // Requirement REQ-007.1: Monitor disk space during conversions
        setCurrentOutputDirectory(settings.outputDirectory());

        // Requirement REQ-004.2: Track batch progress across all files
        // Initialize batch tracking with all file IDs and sizes
        List<String> fileIds = new ArrayList<>();
        Map<String, Long> fileSizes = new HashMap<>();
        for (ConversionFile file : files) {
            fileIds.add(file.id());
            fileSizes.put(file.id(), file.size());
        }
        progressEngine.startBatch(fileIds, fileSizes);

        // Submit all files for conversion
        List<CompletableFuture<ConversionResult>> futures = new ArrayList<>();
        for (ConversionFile file : files) {
            CompletableFuture<ConversionResult> future = convertSingle(file, settings);
            futures.add(future);
        }

        // Combine all futures and create batch result
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<ConversionResult> results = new ArrayList<>();

                    // Collect results from all futures, handling exceptional completions
                    // Use indexed loop to correlate futures with original files
                    for (int i = 0; i < futures.size(); i++) {
                        CompletableFuture<ConversionResult> future = futures.get(i);
                        ConversionFile file = files.get(i);

                        try {
                            results.add(future.get());
                        } catch (InterruptedException | ExecutionException e) {
                            // Requirement REQ-004.2: Handle exceptional future completion
                            // This can happen if executor is shut down or task submission fails
                            logger.error("Failed to get conversion result for file: {}",
                                    file.fileName(), e);

                            // Create error message with fallback for null exception messages
                            String errorMsg = e.getMessage() != null
                                    ? "Conversion task failed: " + e.getMessage()
                                    : "Conversion task failed with unknown error";

                            // Add a failure result to maintain results.size() == files.size()
                            results.add(ConversionResult.failure(
                                    file.id(),
                                    errorMsg,
                                    "", // No tool output available for exceptional completion
                                    Duration.ZERO,
                                    file.size(),
                                    ConversionTool.FFMPEG));
                        }
                    }

                    Duration totalTime = Duration.between(batchStart, Instant.now());
                    BatchConversionResult batchResult = BatchConversionResult.from(results, totalTime);

                    // Log detailed batch statistics with performance metrics
                    logger.info("Batch conversion completed: {} total, {} successful, {} failed in {} (avg speed: {})",
                            batchResult.totalCount(),
                            batchResult.successCount(),
                            batchResult.failureCount(),
                            batchResult.formatTotalTime(),
                            batchResult.formatAverageSpeed());

                    // Log space metrics if available
                    if (batchResult.totalInputSize() > 0) {
                        logger.debug("Batch space metrics: {} - compression ratio {:.1f}%",
                                batchResult.formatSpaceSaved(),
                                batchResult.overallCompressionRatio());
                    }

                    return batchResult;
                });
    }

    /**
     * Converts a single file with the specified settings.
     * Requirement REQ-004.1: Tool selection and single file conversion.
     * 
     * @param file     the file to convert
     * @param settings the conversion settings
     * @return a CompletableFuture that completes with the conversion result
     * @throws IllegalStateException if the engine is shutting down
     */
    public CompletableFuture<ConversionResult> convertSingle(
            ConversionFile file,
            ConversionSettings settings) {

        Objects.requireNonNull(file, "ConversionFile cannot be null");
        Objects.requireNonNull(settings, "ConversionSettings cannot be null");

        if (shuttingDown.get()) {
            throw new IllegalStateException("ConversionEngine is shutting down");
        }

        String fileId = file.id();
        logger.debug("Submitting conversion for file: {} ({})", file.fileName(), fileId);

        // Set output directory for disk space monitoring
        // Requirement REQ-007.1: Monitor disk space during conversions
        setCurrentOutputDirectory(settings.outputDirectory());

        CompletableFuture<ConversionResult> future = CompletableFuture.supplyAsync(
                () -> {
                    // Wait if paused
                    waitIfPaused();

                    // Check if cancelled during pause
                    if (shuttingDown.get()) {
                        logger.info("Conversion cancelled during pause: {}", file.fileName());
                        return ConversionResult.failure(
                                fileId,
                                "Conversion cancelled",
                                "", // No tool output for cancellation during pause
                                Duration.ZERO,
                                file.size(),
                                ConversionTool.FFMPEG);
                    }

                    // Resolve output format for logging
                    FormatCategory category = file.format().getCategory();
                    Object resolvedSettings = resolveSettingsForFile(file, settings);
                    FileFormat outputFormat = getOutputFormatFromSettings(resolvedSettings, category);

                    logger.info("Starting conversion: {} -> {}",
                            file.fileName(), outputFormat);

                    // Requirement REQ-004.1: Validate conversion request
                    try {
                        return performConversion(file, settings);
                    } catch (CancellationException e) {
                        // User-initiated cancellation - log at info level, not error
                        logger.info("Conversion cancelled: {}", file.fileName());
                        return ConversionResult.cancelled(
                                fileId,
                                "", // No tool output for cancelled conversion
                                Duration.ZERO,
                                file.size(),
                                ConversionTool.FFMPEG);
                    } catch (Exception e) {
                        logger.error("Conversion failed with exception: {}", file.fileName(), e);
                        return ConversionResult.failure(
                                fileId,
                                "Conversion failed: " + e.getMessage(),
                                "", // No tool output for exception
                                Duration.ZERO,
                                file.size(),
                                ConversionTool.FFMPEG);
                    }
                },
                executorService);

        // Track the active conversion
        activeConversions.put(fileId, future);

        // Clean up tracking when complete
        future.whenComplete((result, throwable) -> {
            activeConversions.remove(fileId);

            ConversionResult finalResult = result;

            if (throwable != null) {
                // Distinguish between cancellation and actual errors
                if (throwable instanceof CancellationException) {
                    logger.info("Conversion cancelled: {}", file.fileName());
                    // Create a cancelled result for proper completion handling
                    finalResult = ConversionResult.cancelled(
                            fileId,
                            "", // No tool output for cancelled conversion
                            Duration.ZERO,
                            file.size(),
                            null // tool used is null for cancelled conversions
                    );
                } else {
                    logger.error("Conversion failed with exception: {}", file.fileName(), throwable);
                    // For other exceptions, create a failed result if result is null
                    if (finalResult == null) {
                        finalResult = ConversionResult.failure(
                                fileId,
                                throwable.getMessage() != null ? throwable.getMessage()
                                        : "Conversion failed with exception",
                                "", // No tool output for exceptional completion
                                Duration.ZERO,
                                file.size(),
                                null);
                    }
                }
            } else {
                logger.debug("Conversion completed: {} (success={})",
                        file.fileName(), result.success());
            }

            // Requirement REQ-FL-2.2: Store conversion result for later retrieval
            if (finalResult != null) {
                conversionResults.put(fileId, finalResult);
                logger.debug("Stored conversion result for file: {}", fileId);
            }

            // Notify completion handler if registered
            if (completionHandler != null && finalResult != null) {
                try {
                    completionHandler.accept(fileId, finalResult);
                } catch (Exception e) {
                    logger.error("Error in completion handler", e);
                }
            }
        });

        return future;
    }

    /**
     * Pauses all active conversions.
     * Requirement REQ-004.2: Pause/Resume/Cancel controls.
     * 
     * Conversions currently in progress will complete their current operation,
     * but no new conversions will start until resumed.
     */
    public void pauseConversion() {
        if (paused.compareAndSet(false, true)) {
            logger.info("Conversion engine paused - {} active conversions",
                    activeConversions.size());
        }
    }

    /**
     * Resumes paused conversions.
     * Requirement REQ-004.2: Pause/Resume/Cancel controls.
     */
    public void resumeConversion() {
        if (paused.compareAndSet(true, false)) {
            synchronized (paused) {
                paused.notifyAll();
            }
            logger.info("Conversion engine resumed");
        }
    }

    /**
     * Shuts down the conversion engine and cleans up resources.
     * Waits up to 30 seconds for active conversions to complete.
     * If conversions were already cancelled, uses a shorter timeout.
     * 
     * Requirement REQ-004.2: Resource cleanup on shutdown.
     */
    public void shutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
            logger.info("Shutting down ConversionEngine - {} active conversions",
                    activeConversions.size());

            try {
                // Check if conversions are already cancelled (no active processes)
                boolean alreadyCancelled = activeProcesses.isEmpty() && activeConversions.isEmpty();

                // Resume if paused to allow shutdown to proceed
                if (paused.get()) {
                    resumeConversion();
                }

                // Stop disk space monitoring
                diskSpaceMonitor.shutdownNow();

                // Initiate orderly shutdown
                executorService.shutdown();

                // Use shorter timeout if conversions were already cancelled
                // Otherwise wait up to 30 seconds for active conversions to complete
                long timeoutSeconds = alreadyCancelled ? 2 : 30;
                logger.debug("Waiting up to {} seconds for executor termination", timeoutSeconds);

                boolean terminated = executorService.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);

                if (!terminated) {
                    logger.warn("Executor did not terminate in time, forcing shutdown");
                    List<Runnable> pending = executorService.shutdownNow();
                    logger.warn("Cancelled {} pending tasks", pending.size());

                    // Wait again after forcing shutdown
                    executorService.awaitTermination(5, TimeUnit.SECONDS);
                }

                activeConversions.clear();
                conversionResults.clear();

                // Requirement REQ-004.2: Clean up temporary files on shutdown
                logger.info("Cleaning up temporary files");
                fileHandler.cleanupAll();

                logger.info("ConversionEngine shutdown complete");

            } catch (InterruptedException e) {
                logger.error("Interrupted while waiting for executor termination", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Cancels all active conversions and forcibly terminates associated processes.
     * Requirement REQ-004.2: Pause/Resume/Cancel controls.
     */
    public void cancelConversion() {
        logger.info("Cancelling all active conversions - {} active, {} running processes",
                activeConversions.size(), activeProcesses.size());

        // Resume if paused to allow cancellation to proceed
        if (paused.get()) {
            resumeConversion();
        }

        // First, forcibly destroy all active processes
        // This is the most reliable way to stop conversions immediately
        for (Map.Entry<String, Process> entry : activeProcesses.entrySet()) {
            Process process = entry.getValue();
            if (process != null && process.isAlive()) {
                logger.info("Forcibly destroying process for conversion: {}", entry.getKey());
                process.destroyForcibly();
            }
        }
        activeProcesses.clear();

        // Then cancel all active futures (this interrupts threads)
        for (Map.Entry<String, CompletableFuture<ConversionResult>> entry : activeConversions.entrySet()) {
            CompletableFuture<ConversionResult> future = entry.getValue();
            if (!future.isDone()) {
                future.cancel(true);
                logger.debug("Cancelled conversion future: {}", entry.getKey());
            }
        }

        activeConversions.clear();
        conversionResults.clear();
    }

    /**
     * Registers a handler for progress updates.
     * Requirement REQ-004.3: Progress tracking.
     * 
     * @param handler the progress update handler (fileId, progress)
     */
    public void onProgressUpdate(BiConsumer<String, ConversionProgress> handler) {
        this.progressHandler = handler;
        logger.debug("Progress update handler registered");
    }

    /**
     * Registers a handler for conversion completion.
     * Requirement REQ-004.3: Progress tracking.
     * 
     * @param handler the completion handler (fileId, result)
     */
    public void onConversionComplete(BiConsumer<String, ConversionResult> handler) {
        this.completionHandler = handler;
        logger.debug("Conversion completion handler registered");
    }

    /**
     * Registers a handler for batch progress updates.
     * Requirement REQ-004.3: Batch progress tracking with speed and ETA.
     * 
     * @param handler the batch progress update handler
     */
    public void onBatchProgressUpdate(Consumer<BatchProgress> handler) {
        this.progressEngine.addBatchProgressListener(handler);
        logger.debug("Batch progress update handler registered");
    }

    /**
     * Gets the number of currently active conversions.
     * 
     * @return the number of active conversions
     */
    public int getActiveConversionCount() {
        return activeConversions.size();
    }

    /**
     * Checks if the engine is currently paused.
     * 
     * @return true if paused, false otherwise
     */
    public boolean isPaused() {
        return paused.get();
    }

    /**
     * Checks if the engine is shutting down.
     * 
     * @return true if shutting down, false otherwise
     */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    /**
     * Retrieves the stored conversion result for a file.
     * Requirement REQ-FL-2.2: Store and retrieve conversion results with tool
     * output.
     * 
     * @param fileId the file identifier
     * @return the conversion result, or null if not found
     */
    public ConversionResult getConversionResult(String fileId) {
        return conversionResults.get(fileId);
    }

    /**
     * Starts background disk space monitoring.
     * Requirement REQ-007.1: Monitor disk space and pause if below threshold.
     */
    private void startDiskSpaceMonitoring() {
        diskSpaceMonitor.scheduleWithFixedDelay(
                this::checkDiskSpace,
                DISK_SPACE_CHECK_INTERVAL_MS,
                DISK_SPACE_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        logger.debug("Disk space monitoring started");
    }

    /**
     * Checks available disk space and pauses/resumes conversions as needed.
     * Requirement REQ-007.1: Pause conversion if disk space drops below 500MB
     * threshold.
     */
    private void checkDiskSpace() {
        try {
            // Only check if we have an active output directory and active conversions
            if (currentOutputDirectory == null || activeConversions.isEmpty()) {
                return;
            }

            long availableSpace = fileHandler.getAvailableSpace(currentOutputDirectory);

            if (availableSpace < DISK_SPACE_THRESHOLD_BYTES) {
                // Disk space low - pause if not already paused
                if (diskSpacePaused.compareAndSet(false, true)) {
                    logger.warn("Disk space below threshold ({} MB available). Pausing conversions.",
                            availableSpace / (1024 * 1024));
                    pauseConversion();
                }
            } else {
                // Disk space OK - resume if paused due to disk space
                if (diskSpacePaused.compareAndSet(true, false)) {
                    logger.info("Disk space above threshold ({} MB available). Resuming conversions.",
                            availableSpace / (1024 * 1024));
                    resumeConversion();
                }
            }
        } catch (Exception e) {
            logger.error("Error checking disk space: {}", e.getMessage());
        }
    }

    /**
     * Sets the current output directory for disk space monitoring.
     * 
     * @param directory the output directory to monitor
     */
    private void setCurrentOutputDirectory(Path directory) {
        this.currentOutputDirectory = directory;
    }

    /**
     * Updates the maximum number of parallel conversions.
     * This method reconfigures the thread pool to use the new parallelism value.
     * 
     * <p>
     * <b>Important:</b> This should only be called when no conversions are active.
     * Changing parallelism during active conversions may lead to unexpected
     * behavior.
     * </p>
     * 
     * @param newParallelConversions the new maximum number of parallel conversions
     *                               (1-16)
     * @throws IllegalArgumentException if newParallelConversions is out of range
     * @throws IllegalStateException    if conversions are currently active
     */
    public void setParallelConversions(int newParallelConversions) {
        if (newParallelConversions < 1 || newParallelConversions > 16) {
            throw new IllegalArgumentException(
                    "Parallel conversions must be between 1 and 16, got: " + newParallelConversions);
        }

        if (!activeConversions.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot change parallelism while conversions are active. " +
                            "Active conversions: " + activeConversions.size());
        }

        // ThreadPoolExecutor allows dynamic reconfiguration
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) executorService;
        int oldCoreSize = tpe.getCorePoolSize();
        int oldMaxSize = tpe.getMaximumPoolSize();

        // Set new core and maximum pool sizes
        // Order matters: when increasing, set max first; when decreasing, set core
        // first
        if (newParallelConversions > oldMaxSize) {
            // Increasing: set maximum first to avoid IllegalArgumentException
            tpe.setMaximumPoolSize(newParallelConversions);
            tpe.setCorePoolSize(newParallelConversions);
        } else {
            // Decreasing: set core first to avoid IllegalArgumentException
            tpe.setCorePoolSize(newParallelConversions);
            tpe.setMaximumPoolSize(newParallelConversions);
        }

        logger.info("Updated parallel conversions from {} to {}", oldCoreSize, newParallelConversions);
    }

    /**
     * Performs the actual file conversion.
     * Requirement REQ-004.1: Tool selection and single file conversion.
     * Requirement REQ-004.2: Progress tracking, error handling, and temporary file
     * management.
     * 
     * @param file     the file to convert
     * @param settings the conversion settings
     * @return the conversion result
     */
    private ConversionResult performConversion(ConversionFile file, ConversionSettings settings) {
        String fileId = file.id();
        Instant startTime = Instant.now();
        Path tempOutputPath = null;

        try {
            // Step 1: Resolve effective settings for this file
            // Requirement REQ-007: Support per-file settings overrides
            FormatCategory category = file.format().getCategory();
            Object resolvedSettings = resolveSettingsForFile(file, settings);
            FileFormat outputFormat = getOutputFormatFromSettings(resolvedSettings, category);

            if (file.hasCustomSettings()) {
                logger.info("Using custom settings override for file: {}", file.fileName());
            } else {
                logger.debug("Using section settings for file: {} (category: {})", file.fileName(), category);
            }
            logger.debug("Resolved output format: {}", outputFormat);

            // Step 2: Validate conversion request
            // Requirement REQ-004.1: Validate file and settings before conversion
            ValidationResult validation = validationEngine.validateConversionRequest(file, settings);
            if (validation == null) {
                logger.error("ValidationEngine returned null for {}", file.fileName());
                return ConversionResult.failure(
                        fileId,
                        "Internal error: validation failed",
                        "", // No tool output for validation failure
                        Duration.ZERO,
                        file.size(),
                        ConversionTool.FFMPEG);
            }
            if (!validation.isSuccess()) {
                logger.warn("Validation failed for {}: {}", file.fileName(), validation.getFirstError());
                return ConversionResult.failure(
                        fileId,
                        validation.getFirstError(),
                        "", // No tool output for validation failure
                        Duration.ZERO,
                        file.size(),
                        ConversionTool.FFMPEG);
            }

            // Step 3: Select appropriate tool
            // Requirement REQ-004.1: Tool selection based on format pair
            ConversionTool tool = toolManager.selectTool(file.format(), outputFormat);
            logger.debug("Selected tool {} for {} -> {}",
                    tool, file.format(), outputFormat);

            // Step 3: Validate tool availability
            ValidationResult toolCheck = validationEngine.validateToolAvailability(tool);
            if (!toolCheck.isSuccess()) {
                logger.error("Tool {} not available: {}", tool, toolCheck.getFirstError());
                return ConversionResult.failure(
                        fileId,
                        "Tool not available: " + toolCheck.getFirstError(),
                        "", // No tool output for tool availability failure
                        Duration.ZERO,
                        file.size(),
                        tool);
            }

            // Step 4: Generate final output file path
            // Requirement REQ-004.1: Create output file path (same name, different
            // extension)
            Path finalOutputPath = generateOutputPath(file, settings, outputFormat);
            logger.debug("Final output path: {}", finalOutputPath);

            // Step 5: Check for conflicts
            // Requirement REQ-004.1: Handle output file conflicts based on settings
            if (Files.exists(finalOutputPath) && !settings.overwriteExisting()) {
                logger.info("Output file exists and overwrite disabled: {}", finalOutputPath);
                return ConversionResult.failure(
                        fileId,
                        "Output file already exists: " + finalOutputPath.getFileName(),
                        "", // No tool output for file conflict
                        Duration.ZERO,
                        file.size(),
                        tool);
            }

            // Step 6: Check output directory and disk space
            Path outputDir = finalOutputPath.getParent();
            ValidationResult dirCheck = validationEngine.validateOutputDirectory(outputDir);
            if (!dirCheck.isSuccess()) {
                logger.error("Output directory validation failed: {}", dirCheck.getFirstError());
                return ConversionResult.failure(
                        fileId,
                        dirCheck.getFirstError(),
                        "", // No tool output for directory validation failure
                        Duration.ZERO,
                        file.size(),
                        tool);
            }

            // Estimate output size (rough estimate: 80% of input for compression)
            long estimatedOutputSize = (long) (file.size() * 0.8);
            ValidationResult spaceCheck = validationEngine.validateDiskSpace(outputDir, estimatedOutputSize);
            if (!spaceCheck.isSuccess()) {
                logger.error("Insufficient disk space: {}", spaceCheck.getFirstError());
                return ConversionResult.failure(
                        fileId,
                        spaceCheck.getFirstError(),
                        "", // No tool output for disk space validation failure
                        Duration.ZERO,
                        file.size(),
                        tool);
            }

            // Step 7: Create temporary output file
            // Requirement REQ-004.2: Use temporary file for atomic conversion
            String extension = outputFormat.getPrimaryExtension();
            tempOutputPath = fileHandler.createTemporaryFile("conversion-", "." + extension);
            fileHandler.registerCleanup(tempOutputPath);
            logger.debug("Created temporary output file: {}", tempOutputPath);

            // Step 8: Start progress tracking
            // Requirement REQ-004.2: Track progress using ProgressEngine
            progressEngine.startTracking(fileId, file.size());

            // Step 9: Create progress callback for the tool service
            ProgressCallback progressCallback = (percentage, bytesProcessed, speed) -> {
                // Use percentage directly to avoid percentage->bytes->percentage conversion
                // This fixes the issue where FFmpeg's output size grows faster than encoding
                // progress
                // for compressed video, causing the UI to reach 100% prematurely
                logger.trace("Progress callback: fileId={}, percentage={:.2f}%, bytesProcessed={}, speed={}",
                        fileId, percentage, bytesProcessed, speed);

                // Update progress engine with direct percentage
                progressEngine.updateProgressWithPercentage(fileId, percentage);

                // Notify registered progress handler with the updated progress
                if (progressHandler != null) {
                    try {
                        // Get the updated progress from the engine
                        progressEngine.getProgress(fileId)
                                .ifPresent(progress -> progressHandler.accept(fileId, progress));
                    } catch (Exception e) {
                        logger.error("Error in progress handler", e);
                    }
                }
            };

            // Step 10: Execute conversion with temporary file and error recovery
            // Requirement REQ-004.1: Execute conversion with progress callback
            // Requirement REQ-004.2, REQ-007.1: Error recovery with retry on transient
            // errors
            logger.info("Executing conversion: {} ({} -> {}) via temp file",
                    file.fileName(), file.format(), outputFormat);

            ConversionResult result = executeWithRetry(
                    tool,
                    file,
                    tempOutputPath,
                    settings,
                    outputFormat,
                    progressCallback,
                    fileId,
                    this // Pass ProcessRegistry instance for process tracking
            );

            // Step 11: Move temporary file to final location on success
            // Requirement REQ-004.2: Atomic move on success
            if (result.success()) {
                logger.debug("Conversion successful, moving temp file to final location: {}", finalOutputPath);
                // Ensure output directory exists before moving file
                Files.createDirectories(finalOutputPath.getParent());
                Files.move(tempOutputPath, finalOutputPath,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                fileHandler.unregisterCleanup(tempOutputPath);
                tempOutputPath = null; // Mark as moved successfully
                logger.info("Moved temp file to final location: {}", finalOutputPath);

                // Update result with final output path instead of temp path
                // This ensures "Open File Location" opens the correct directory
                result = ConversionResult.success(
                        result.fileId(),
                        finalOutputPath, // Use final path, not temp path
                        result.toolOutput().orElse(null),
                        result.conversionTime(),
                        result.inputSize(),
                        result.outputSize(),
                        result.toolUsed());

                // Step 11.5: Delete original file if requested
                // Requirement REQ-GEN-1.2: Delete original file after successful conversion
                if (settings.deleteOriginalFile()) {
                    deleteOriginalFile(file.path(), file.fileName());
                }
            } else {
                // Conversion failed - temp file will be cleaned up in finally block
                logger.warn("Conversion failed, temp file will be cleaned up: {}", tempOutputPath);
            }

            // Step 12: Complete progress tracking
            progressEngine.completeTracking(fileId, result);

            Duration totalTime = Duration.between(startTime, Instant.now());
            logger.info("Conversion completed: {} in {} ms (success={})",
                    file.fileName(), totalTime.toMillis(), result.success());

            return result;

        } catch (Exception e) {
            Duration totalTime = Duration.between(startTime, Instant.now());
            logger.error("Conversion failed with exception: {}", file.fileName(), e);

            ConversionResult failureResult = ConversionResult.failure(
                    fileId,
                    "Conversion error: " + e.getMessage(),
                    "", // No tool output for exception
                    totalTime,
                    file.size(),
                    ConversionTool.FFMPEG);

            progressEngine.completeTracking(fileId, failureResult);
            return failureResult;

        } finally {
            // Requirement REQ-004.2: Clean up temporary file on error or cancel
            if (tempOutputPath != null) {
                try {
                    if (Files.exists(tempOutputPath)) {
                        Files.delete(tempOutputPath);
                        fileHandler.unregisterCleanup(tempOutputPath);
                        logger.debug("Deleted temporary file after error/cancel: {}", tempOutputPath);
                    }
                } catch (IOException cleanupEx) {
                    logger.warn("Failed to delete temporary file: {}", tempOutputPath, cleanupEx);
                }
            }
        }
    }

    /**
     * Executes conversion with retry logic for transient errors.
     * Requirement REQ-004.2, REQ-007.1: Error recovery with single retry on
     * transient errors
     * 
     * Retry logic:
     * - Exit code 255: Transient error, retry once
     * - Other non-zero exit codes: Non-transient, skip file
     * - User cancellation: Don't retry
     * 
     * @param tool             the conversion tool to use
     * @param file             the file to convert
     * @param outputPath       the output path
     * @param settings         conversion settings
     * @param progressCallback progress callback
     * @param fileId           the file identifier
     * @param processRegistry  the process registry for tracking active processes
     * @return conversion result
     */
    private ConversionResult executeWithRetry(
            ConversionTool tool,
            ConversionFile file,
            Path tempOutputPath,
            ConversionSettings settings,
            FileFormat outputFormat,
            ProgressCallback progressCallback,
            String fileId,
            ProcessRegistry processRegistry) {

        int maxAttempts = 2; // 1 initial attempt + 1 retry
        ConversionResult lastResult = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // Check for cancellation before each attempt
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Conversion cancelled before attempt {} for {}", attempt, file.fileName());
                return ConversionResult.cancelled(
                        fileId,
                        "", // No tool output for cancelled attempt
                        Duration.ZERO,
                        file.size(),
                        tool);
            }

            try {
                logger.debug("Conversion attempt {} for {}", attempt, file.fileName());

                // Execute conversion
                ConversionResult result = toolManager.executeTool(
                        tool,
                        file.path(),
                        tempOutputPath,
                        outputFormat,
                        settings,
                        progressCallback,
                        fileId,
                        processRegistry);

                // If successful, return immediately
                if (result.success()) {
                    if (attempt > 1) {
                        logger.info("Conversion succeeded on retry (attempt {}) for {}",
                                attempt, file.fileName());
                    }
                    return result;
                }

                // Conversion failed - store the result
                lastResult = result;

                // Check if we should retry
                // Exit code 255 is embedded in error message: "exit code 255"
                boolean isTransientError = result.errorMessage()
                        .map(msg -> msg.contains("exit code 255"))
                        .orElse(false);

                if (isTransientError && attempt < maxAttempts) {
                    logger.warn("Transient error (exit code 255) detected for {}, retrying... (attempt {}/{})",
                            file.fileName(), attempt, maxAttempts);

                    // Brief delay before retry (500ms)
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.info("Retry interrupted for {}", file.fileName());
                        return ConversionResult.failure(
                                fileId,
                                "Conversion cancelled during retry",
                                "", // No tool output for interrupted retry
                                Duration.ZERO,
                                file.size(),
                                tool);
                    }
                } else {
                    // Non-transient error or retry exhausted
                    if (isTransientError) {
                        logger.error("Transient error persisted after {} attempts for {}: {}",
                                attempt, file.fileName(), result.errorMessage().orElse("Unknown error"));
                    } else {
                        logger.error("Non-transient error for {}, not retrying: {}",
                                file.fileName(), result.errorMessage().orElse("Unknown error"));
                    }
                    return result;
                }

            } catch (ToolExecutionException e) {
                // Tool execution exception with exit code
                logger.error("Tool execution failed for {} (attempt {}): {}",
                        file.fileName(), attempt, e.getDetailedMessage());

                // Check if this is a transient error (exit code 255)
                boolean isTransientError = e.getExitCode() != null && e.getExitCode() == 255;

                if (isTransientError && attempt < maxAttempts) {
                    logger.warn("Transient error (exit code 255) from exception for {}, retrying... (attempt {}/{})",
                            file.fileName(), attempt, maxAttempts);

                    // Brief delay before retry (500ms)
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.info("Retry interrupted for {}", file.fileName());
                        return ConversionResult.failure(
                                fileId,
                                "Conversion cancelled during retry",
                                "", // No tool output for interrupted retry
                                Duration.ZERO,
                                file.size(),
                                tool);
                    }

                    // Continue to next attempt
                    continue;
                } else {
                    // Non-transient error or retry exhausted
                    if (isTransientError) {
                        logger.error("Transient error persisted after {} attempts for {}",
                                attempt, file.fileName());
                    } else {
                        logger.error("Non-transient error for {}, not retrying (exit code: {})",
                                file.fileName(), e.getExitCode());
                    }

                    return ConversionResult.failure(
                            fileId,
                            "Tool execution error: " + e.getMessage(),
                            "", // No tool output for tool execution exception
                            Duration.ZERO,
                            file.size(),
                            tool);
                }
            } catch (Exception e) {
                // Other unexpected exceptions
                logger.error("Unexpected error during conversion for {}: {}", file.fileName(), e.getMessage(), e);
                return ConversionResult.failure(
                        fileId,
                        "Conversion error: " + e.getMessage(),
                        "", // No tool output for unexpected exception
                        Duration.ZERO,
                        file.size(),
                        tool);
            }
        }

        // Should not reach here, but return last result as fallback
        return lastResult != null ? lastResult
                : ConversionResult.failure(
                        fileId,
                        "Conversion failed after retries",
                        "", // No tool output for fallback failure
                        Duration.ZERO,
                        file.size(),
                        tool);
    }

    /**
     * Generates the output file path based on input file and settings.
     * Requirement REQ-004.1: Create output file path (same name, different
     * extension).
     * 
     * @param file         the input file
     * @param settings     the conversion settings
     * @param outputFormat the resolved output format
     * @return the output file path
     */
    private Path generateOutputPath(ConversionFile file, ConversionSettings settings, FileFormat outputFormat) {
        Path inputPath = file.path();
        String baseName = inputPath.getFileName().toString();

        // Remove extension
        int lastDot = baseName.lastIndexOf('.');
        if (lastDot > 0) {
            baseName = baseName.substring(0, lastDot);
        }

        // Add new extension
        String extension = outputFormat.getPrimaryExtension();
        String outputFileName = baseName + "." + extension;

        // Determine output directory
        Path outputDir = settings.outputDirectory();
        if (outputDir == null) {
            // Default: same directory as input
            outputDir = inputPath.getParent();
        }

        // Create subdirectory if requested
        if (settings.createSubdirectory()) {
            outputDir = outputDir.resolve("converted");
            try {
                Files.createDirectories(outputDir);
            } catch (IOException e) {
                logger.warn("Failed to create subdirectory: {}", outputDir, e);
            }
        }

        return outputDir.resolve(outputFileName);
    }

    /**
     * Resolves the effective settings for a conversion file.
     * Priority: File override > Section settings based on category.
     * Requirement REQ-007: Support per-file settings overrides.
     * 
     * @param file           the conversion file
     * @param globalSettings the global conversion settings
     * @return section-specific settings object (VideoSettings, AudioSettings, etc.)
     *         or null for UNKNOWN
     */
    private Object resolveSettingsForFile(ConversionFile file, ConversionSettings globalSettings) {
        FormatCategory category = file.format().getCategory();

        // Check for file-specific override first
        if (file.hasCustomSettings()) {
            FileSettingsOverride override = file.settingsOverride();

            return switch (category) {
                case VIDEO -> override.videoSettings();
                case AUDIO -> override.audioSettings();
                case IMAGE -> override.imageSettings();
                case DOCUMENT -> override.documentSettings();
                case UNKNOWN -> null;
            };
        }

        // Fall back to section settings
        return switch (category) {
            case VIDEO -> globalSettings.videoSettings();
            case AUDIO -> globalSettings.audioSettings();
            case IMAGE -> globalSettings.imageSettings();
            case DOCUMENT -> globalSettings.documentSettings();
            case UNKNOWN -> null;
        };
    }

    /**
     * Extracts output format from resolved section-specific settings.
     * 
     * @param settings the section-specific settings object
     * @param category the format category
     * @return the output format, or default format if settings is null
     */
    private FileFormat getOutputFormatFromSettings(Object settings, FormatCategory category) {
        if (settings == null) {
            return getDefaultFormatForCategory(category);
        }

        return switch (category) {
            case VIDEO -> ((VideoSettings) settings).outputFormat();
            case AUDIO -> ((AudioSettings) settings).outputFormat();
            case IMAGE -> ((ImageSettings) settings).outputFormat();
            case DOCUMENT -> ((DocumentSettings) settings).outputFormat();
            case UNKNOWN -> null;
        };
    }

    /**
     * Returns the default output format for a given category.
     * 
     * @param category the format category
     * @return default format (MP4 for video, MP3 for audio, PNG for image, PDF for
     *         document)
     */
    private FileFormat getDefaultFormatForCategory(FormatCategory category) {
        return switch (category) {
            case VIDEO -> FileFormat.MP4;
            case AUDIO -> FileFormat.MP3;
            case IMAGE -> FileFormat.PNG;
            case DOCUMENT -> FileFormat.PDF;
            case UNKNOWN -> null;
        };
    }

    /**
     * Waits while the engine is paused.
     * This is called by conversion tasks before starting work.
     */
    private void waitIfPaused() {
        while (paused.get() && !shuttingDown.get()) {
            synchronized (paused) {
                try {
                    logger.debug("Conversion task waiting - engine is paused");
                    paused.wait(1000); // Wake up periodically to check shutdown
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Conversion task interrupted while paused");
                    break;
                }
            }
        }
    }

    /**
     * Registers a process as active for cancellation support.
     * Implements ProcessRegistry interface.
     * 
     * @param fileId  the file ID associated with this conversion
     * @param process the process to register
     */
    @Override
    public void registerProcess(String fileId, Process process) {
        if (fileId != null && process != null) {
            activeProcesses.put(fileId, process);
            logger.debug("Registered active process for file: {}", fileId);
        }
    }

    /**
     * Unregisters a process when it completes or is cancelled.
     * Implements ProcessRegistry interface.
     * 
     * @param fileId the file ID associated with this conversion
     */
    @Override
    public void unregisterProcess(String fileId) {
        if (fileId != null) {
            activeProcesses.remove(fileId);
            logger.debug("Unregistered active process for file: {}", fileId);
        }
    }

    /**
     * Deletes the original file after successful conversion.
     * Implements REQ-GEN-1.2: Safe deletion only on success.
     * 
     * @param filePath the path to the original file
     * @param fileName the file name for logging purposes
     */
    private void deleteOriginalFile(Path filePath, String fileName) {
        try {
            if (Files.deleteIfExists(filePath)) {
                logger.info("Deleted original file: {}", fileName);
            } else {
                logger.warn("Original file not found for deletion: {}", fileName);
            }
        } catch (IOException e) {
            logger.warn("Failed to delete original file: {} - {}", fileName, e.getMessage());
        }
    }

    /**
     * Custom thread factory for conversion threads.
     * Names threads for easier debugging and monitoring.
     */
    private static class ConversionThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "conversion-" + threadNumber.getAndIncrement());
            thread.setDaemon(false); // Allow JVM to wait for conversions
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
