// filepath: src/main/java/org/omc/core/ProgressEngine.java

package org.omc.core;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.omc.model.BatchProgress;
import org.omc.model.ConversionProgress;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe progress tracking engine for conversions.
 * Tracks individual file progress and aggregates batch progress.
 * Uses moving averages for accurate time estimation.
 * Implements progress update throttling to prevent UI flickering.
 * Requirement REQ-004.3: Progress tracking and reporting.
 * Requirement NFR-FL-1: Performance - smooth UI updates without flickering.
 */
public class ProgressEngine {

    private static final Logger logger = LoggerFactory.getLogger(ProgressEngine.class);

    /**
     * Minimum interval between progress notifications in milliseconds.
     * Per NFR-FL-1: 100ms interval ensures smooth updates without flickering.
     */
    private static final long THROTTLE_INTERVAL_MS = 100;

    // Thread-safe progress storage
    private final ConcurrentHashMap<String, ConversionProgress> progressMap;
    private final ConcurrentHashMap<String, ConversionStatus> statusMap;
    private final ConcurrentHashMap<String, Long> fileSizeMap;

    // Throttling: track last notification time per file
    private final ConcurrentHashMap<String, Long> lastNotificationTimeMap;

    // Batch tracking
    private Instant batchStartTime;
    private int totalFiles;
    private long totalBytes;

    // Progress listeners
    private final CopyOnWriteArrayList<Consumer<ConversionProgress>> progressListeners;
    private final CopyOnWriteArrayList<Consumer<BatchProgress>> batchProgressListeners;

    /**
     * Creates a new ProgressEngine.
     */
    public ProgressEngine() {
        this.progressMap = new ConcurrentHashMap<>();
        this.statusMap = new ConcurrentHashMap<>();
        this.fileSizeMap = new ConcurrentHashMap<>();
        this.lastNotificationTimeMap = new ConcurrentHashMap<>();
        this.progressListeners = new CopyOnWriteArrayList<>();
        this.batchProgressListeners = new CopyOnWriteArrayList<>();
        this.batchStartTime = null;
        this.totalFiles = 0;
        this.totalBytes = 0;

        logger.debug("ProgressEngine initialized with {}ms throttle interval", THROTTLE_INTERVAL_MS);
    }

    /**
     * Starts tracking progress for a batch of files.
     * Requirement REQ-004.3: Batch progress initialization.
     * 
     * @param fileIds   the IDs of files to track
     * @param fileSizes map of file IDs to their sizes in bytes
     */
    public void startBatch(List<String> fileIds, Map<String, Long> fileSizes) {
        Objects.requireNonNull(fileIds, "File IDs cannot be null");
        Objects.requireNonNull(fileSizes, "File sizes cannot be null");

        // Clear previous state
        progressMap.clear();
        statusMap.clear();
        fileSizeMap.clear();
        lastNotificationTimeMap.clear();

        // Initialize batch tracking
        this.batchStartTime = Instant.now();
        this.totalFiles = fileIds.size();
        this.totalBytes = fileSizes.values().stream().mapToLong(Long::longValue).sum();

        // Initialize file sizes and statuses
        for (String fileId : fileIds) {
            long fileSize = fileSizes.getOrDefault(fileId, 0L);
            fileSizeMap.put(fileId, fileSize);
            statusMap.put(fileId, ConversionStatus.PENDING);
        }

        logger.info("Started batch tracking: {} files, {} total bytes", totalFiles, totalBytes);

        // Notify listeners of initial batch progress
        notifyBatchProgress();
    }

    /**
     * Starts tracking progress for a single file.
     * Requirement REQ-004.3: Individual file progress tracking.
     * 
     * @param fileId     the unique identifier for the file
     * @param totalBytes the total size of the file in bytes
     */
    public void startTracking(String fileId, long totalBytes) {
        Objects.requireNonNull(fileId, "File ID cannot be null");
        if (totalBytes < 0) {
            throw new IllegalArgumentException("Total bytes cannot be negative");
        }

        ConversionProgress progress = ConversionProgress.initial(fileId, totalBytes);
        progressMap.put(fileId, progress);
        statusMap.put(fileId, ConversionStatus.IN_PROGRESS);

        // Store file size if not already stored
        fileSizeMap.putIfAbsent(fileId, totalBytes);

        logger.debug("Started tracking file: {} ({} bytes)", fileId, totalBytes);

        // Notify listeners (force notification for start event)
        notifyProgressListeners(progress, true);
        notifyBatchProgress();
    }

    /**
     * Updates progress for a file.
     * Requirement REQ-004.3: Progress updates with time estimation.
     * 
     * @param fileId         the file identifier
     * @param processedBytes the number of bytes processed so far
     */
    public void updateProgress(String fileId, long processedBytes) {
        Objects.requireNonNull(fileId, "File ID cannot be null");
        if (processedBytes < 0) {
            logger.warn("Ignoring negative processedBytes for file: {}", fileId);
            return;
        }

        ConversionProgress currentProgress = progressMap.get(fileId);
        if (currentProgress == null) {
            logger.warn("No progress tracking found for file: {}", fileId);
            return;
        }

        // Update progress with new processed bytes
        ConversionProgress updatedProgress = currentProgress.update(processedBytes);
        progressMap.put(fileId, updatedProgress);

        logger.trace("Updated progress for file: {} - {}%", fileId, updatedProgress.percentage());

        // Notify listeners (throttled)
        notifyProgressListeners(updatedProgress, false);
        notifyBatchProgress();
    }

    /**
     * Updates progress for a file using a direct percentage value.
     * This method avoids the percentage->bytes->percentage conversion issue
     * by using the percentage directly from the conversion tool.
     * Requirement REQ-004.3: Accurate progress tracking for conversions.
     * 
     * @param fileId     the file identifier
     * @param percentage the completion percentage (0.0 to 100.0)
     */
    public void updateProgressWithPercentage(String fileId, double percentage) {
        Objects.requireNonNull(fileId, "File ID cannot be null");
        if (percentage < 0 || percentage > 100) {
            logger.warn("Ignoring invalid percentage {} for file: {}", percentage, fileId);
            return;
        }

        ConversionProgress currentProgress = progressMap.get(fileId);
        if (currentProgress == null) {
            logger.warn("No progress tracking found for file: {}", fileId);
            return;
        }

        // Update progress with direct percentage
        ConversionProgress updatedProgress = currentProgress.updateWithPercentage(percentage);
        progressMap.put(fileId, updatedProgress);

        logger.debug("Updated progress for file: {} - {:.2f}% (direct percentage update)", fileId, percentage);

        // Notify listeners (throttled for intermediate updates)
        notifyProgressListeners(updatedProgress, false);
        notifyBatchProgress();
    }

    /**
     * Completes tracking for a file with a result.
     * Requirement REQ-004.3: Completion tracking.
     * 
     * @param fileId the file identifier
     * @param result the conversion result
     */
    public void completeTracking(String fileId, ConversionResult result) {
        Objects.requireNonNull(fileId, "File ID cannot be null");
        Objects.requireNonNull(result, "Result cannot be null");

        ConversionProgress currentProgress = progressMap.get(fileId);
        if (currentProgress == null) {
            logger.warn("No progress tracking found for file: {}", fileId);
            return;
        }

        // Update to 100% complete
        ConversionProgress completedProgress = currentProgress.update(currentProgress.totalBytes());
        progressMap.put(fileId, completedProgress);

        // Update status based on result
        ConversionStatus status = result.success() ? ConversionStatus.COMPLETED : ConversionStatus.FAILED;
        statusMap.put(fileId, status);

        logger.debug("Completed tracking for file: {} - {}", fileId, status);

        // Notify listeners (force notification for completion event)
        notifyProgressListeners(completedProgress, true);
        notifyBatchProgress();
    }

    /**
     * Marks a file as cancelled.
     * 
     * @param fileId the file identifier
     */
    public void cancelTracking(String fileId) {
        Objects.requireNonNull(fileId, "File ID cannot be null");

        statusMap.put(fileId, ConversionStatus.CANCELLED);

        logger.debug("Cancelled tracking for file: {}", fileId);

        // Notify batch progress
        notifyBatchProgress();
    }

    /**
     * Gets the current progress for a file.
     * Requirement REQ-004.3: Progress query.
     * 
     * @param fileId the file identifier
     * @return the current progress, or empty if not tracking
     */
    public Optional<ConversionProgress> getProgress(String fileId) {
        Objects.requireNonNull(fileId, "File ID cannot be null");
        return Optional.ofNullable(progressMap.get(fileId));
    }

    /**
     * Gets the overall batch progress.
     * Requirement REQ-004.3: Batch progress aggregation.
     * 
     * @return the batch progress
     */
    public BatchProgress getBatchProgress() {
        if (batchStartTime == null) {
            // No batch started yet
            return BatchProgress.initial(0, 0);
        }

        // Count files by status
        int completedFiles = 0;
        int failedFiles = 0;
        int inProgressFiles = 0;

        for (ConversionStatus status : statusMap.values()) {
            switch (status) {
                case COMPLETED -> completedFiles++;
                case FAILED -> failedFiles++;
                case IN_PROGRESS -> inProgressFiles++;
                case PENDING -> {
                } // Pending files not counted as in progress
                case CANCELLED -> {
                } // Cancelled files counted as neither completed nor failed
            }
        }

        // Calculate total processed bytes across all files
        long processedBytes = 0;
        for (ConversionProgress progress : progressMap.values()) {
            processedBytes += progress.processedBytes();
        }

        // Build batch progress
        return BatchProgress.update(
                totalFiles,
                completedFiles,
                failedFiles,
                inProgressFiles,
                totalBytes,
                processedBytes,
                batchStartTime);
    }

    /**
     * Gets all currently tracked file IDs.
     * 
     * @return set of file IDs
     */
    public Set<String> getTrackedFiles() {
        return new HashSet<>(progressMap.keySet());
    }

    /**
     * Checks if a file is currently being tracked.
     * 
     * @param fileId the file identifier
     * @return true if tracking, false otherwise
     */
    public boolean isTracking(String fileId) {
        return progressMap.containsKey(fileId);
    }

    /**
     * Clears all tracking data.
     */
    public void reset() {
        progressMap.clear();
        statusMap.clear();
        fileSizeMap.clear();
        lastNotificationTimeMap.clear();
        batchStartTime = null;
        totalFiles = 0;
        totalBytes = 0;

        logger.debug("ProgressEngine reset");
    }

    /**
     * Registers a listener for individual file progress updates.
     * 
     * @param listener the progress listener
     */
    public void addProgressListener(Consumer<ConversionProgress> listener) {
        Objects.requireNonNull(listener, "Listener cannot be null");
        progressListeners.add(listener);
        logger.debug("Added progress listener");
    }

    /**
     * Removes a progress listener.
     * 
     * @param listener the listener to remove
     */
    public void removeProgressListener(Consumer<ConversionProgress> listener) {
        progressListeners.remove(listener);
        logger.debug("Removed progress listener");
    }

    /**
     * Registers a listener for batch progress updates.
     * 
     * @param listener the batch progress listener
     */
    public void addBatchProgressListener(Consumer<BatchProgress> listener) {
        Objects.requireNonNull(listener, "Listener cannot be null");
        batchProgressListeners.add(listener);
        logger.debug("Added batch progress listener");
    }

    /**
     * Removes a batch progress listener.
     * 
     * @param listener the listener to remove
     */
    public void removeBatchProgressListener(Consumer<BatchProgress> listener) {
        batchProgressListeners.remove(listener);
        logger.debug("Removed batch progress listener");
    }

    /**
     * Notifies all progress listeners of an update.
     * Implements throttling to prevent excessive UI updates and flickering.
     * Updates are limited to one notification per file every THROTTLE_INTERVAL_MS.
     * 
     * @param progress          the progress to notify
     * @param forceNotification if true, bypass throttling (for completion/start
     *                          events)
     */
    private void notifyProgressListeners(ConversionProgress progress, boolean forceNotification) {
        String fileId = progress.fileId();
        long currentTime = System.currentTimeMillis();

        // Check if we should throttle this notification
        if (!forceNotification) {
            Long lastNotificationTime = lastNotificationTimeMap.get(fileId);
            if (lastNotificationTime != null) {
                long timeSinceLastNotification = currentTime - lastNotificationTime;
                if (timeSinceLastNotification < THROTTLE_INTERVAL_MS) {
                    // Skip this notification - too soon after last one
                    logger.trace("Throttled progress notification for file: {} ({}ms since last)",
                            fileId, timeSinceLastNotification);
                    return;
                }
            }
        }

        // Update last notification time
        lastNotificationTimeMap.put(fileId, currentTime);

        // Notify all listeners
        for (Consumer<ConversionProgress> listener : progressListeners) {
            try {
                listener.accept(progress);
            } catch (Exception e) {
                logger.error("Error notifying progress listener", e);
            }
        }
    }

    /**
     * Notifies all batch progress listeners of an update.
     */
    private void notifyBatchProgress() {
        BatchProgress batchProgress = getBatchProgress();
        for (Consumer<BatchProgress> listener : batchProgressListeners) {
            try {
                listener.accept(batchProgress);
            } catch (Exception e) {
                logger.error("Error notifying batch progress listener", e);
            }
        }
    }
}
