package org.omc.ui;

import org.omc.model.BatchProgress;
import org.omc.model.ConversionProgress;
import org.gnome.gtk.Label;
import org.gnome.gtk.ProgressBar;
import org.gnome.gtk.Revealer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom component for displaying conversion progress.
 * 
 * <p>
 * Displays overall batch progress with file counts, progress bars,
 * time remaining estimates, and conversion speed metrics.
 * </p>
 * 
 * <p>
 * <strong>Thread Safety:</strong> This component is NOT thread-safe for GTK
 * widget updates.
 * All public methods that modify the UI MUST be called on the GTK main thread.
 * Callers from background threads should use {@code GLib.idleAdd()} to marshal
 * calls
 * to the main thread (typically handled by {@link MainWindowJavaGi}).
 * </p>
 * 
 * <p>
 * The internal {@code fileProgressMap} uses {@code ConcurrentHashMap} for
 * thread-safe
 * storage, but GTK widget operations require main thread execution.
 * </p>
 * 
 * <p>
 * Requirements: REQ-004.3 - Progress tracking and reporting
 * </p>
 */
public class ProgressView {

    private static final Logger logger = LoggerFactory.getLogger(ProgressView.class);

    private final Revealer revealer;
    private final ProgressBar progressBar;
    private final Label statusLabel;
    private final Label timeRemainingLabel;
    private final Label conversionSpeedLabel;

    // Track individual file progress for detailed logging (thread-safe)
    private final Map<String, ConversionProgress> fileProgressMap = new ConcurrentHashMap<>();

    /**
     * Constructs the progress view component.
     * 
     * <p>
     * All widgets must be non-null and properly initialized GTK widgets.
     * </p>
     * 
     * @param revealer             the GTK Revealer widget (must not be null)
     * @param progressBar          the progress bar widget (must not be null)
     * @param statusLabel          the status label widget (must not be null)
     * @param timeRemainingLabel   the time remaining label widget (must not be
     *                             null)
     * @param conversionSpeedLabel the conversion speed label widget (must not be
     *                             null)
     * @throws NullPointerException if any widget is null
     */
    public ProgressView(Revealer revealer, ProgressBar progressBar, Label statusLabel,
            Label timeRemainingLabel, Label conversionSpeedLabel) {
        this.revealer = Objects.requireNonNull(revealer, "revealer must not be null");
        this.progressBar = Objects.requireNonNull(progressBar, "progressBar must not be null");
        this.statusLabel = Objects.requireNonNull(statusLabel, "statusLabel must not be null");
        this.timeRemainingLabel = Objects.requireNonNull(timeRemainingLabel,
                "timeRemainingLabel must not be null");
        this.conversionSpeedLabel = Objects.requireNonNull(conversionSpeedLabel,
                "conversionSpeedLabel must not be null");

        logger.debug("ProgressView initialized");
    }

    /**
     * Shows the progress view.
     * 
     * <p>
     * <strong>Thread Safety:</strong> Must be called on the GTK main thread.
     * </p>
     */
    public void show() {
        revealer.setRevealChild(true);
        logger.debug("Progress view shown");
    }

    /**
     * Hides the progress view.
     * 
     * <p>
     * <strong>Thread Safety:</strong> Must be called on the GTK main thread.
     * </p>
     */
    public void hide() {
        revealer.setRevealChild(false);
        logger.debug("Progress view hidden");
    }

    /**
     * Updates the overall batch progress display.
     * 
     * <p>
     * This is the primary method for updating progress, using the
     * BatchProgress model that contains all necessary metrics.
     * </p>
     * 
     * <p>
     * <strong>Thread Safety:</strong> Must be called on the GTK main thread.
     * Use {@code GLib.idleAdd()} from background threads.
     * </p>
     * 
     * @param batchProgress the batch progress information
     */
    public void updateOverallProgress(BatchProgress batchProgress) {
        if (batchProgress == null) {
            logger.warn("Received null BatchProgress, ignoring update");
            return;
        }

        // Update progress bar with percentage
        double fraction = batchProgress.overallPercentage() / 100.0;
        progressBar.setFraction(fraction);
        progressBar.setText(String.format("%d%%", batchProgress.overallPercentage()));

        // Update status label with file counts
        String status = batchProgress.formatStatusMessage();
        statusLabel.setLabel(status);

        // Update time remaining using BatchProgress formatter
        String timeRemaining = batchProgress.formatEta();
        timeRemainingLabel.setLabel("Time remaining: " + timeRemaining);

        // Update conversion speed using BatchProgress formatter
        String speed = batchProgress.formatSpeed();
        conversionSpeedLabel.setLabel("Speed: " + speed);

        logger.debug("Overall progress updated: {} - {} at {}",
                status, timeRemaining, speed);
    }

    /**
     * Updates progress for an individual file.
     * 
     * <p>
     * This method tracks per-file progress for detailed monitoring
     * and logging. The individual progress is stored internally but
     * the UI displays overall batch progress via updateOverallProgress().
     * </p>
     * 
     * @param fileId   the unique file identifier
     * @param progress the conversion progress for this file
     */
    public void updateFileProgress(String fileId, ConversionProgress progress) {
        if (fileId == null || progress == null) {
            logger.warn("Received invalid file progress update: fileId={}, progress={}",
                    fileId, progress);
            return;
        }

        // Store file progress for tracking
        fileProgressMap.put(fileId, progress);

        logger.debug("File progress updated: {} - {}% at {}",
                fileId, progress.percentage(), progress.formatSpeed());
    }

    /**
     * Updates the progress display (legacy method).
     * 
     * <p>
     * This method is retained for backward compatibility and internal use.
     * New code should use updateOverallProgress(BatchProgress) instead.
     * </p>
     * 
     * <p>
     * <strong>Thread Safety:</strong> Must be called on the GTK main thread.
     * Use {@code GLib.idleAdd()} from background threads.
     * </p>
     * 
     * @param currentFile          the current file being converted (1-based)
     * @param totalFiles           the total number of files
     * @param overallProgress      the overall progress (0.0 to 1.0)
     * @param timeRemainingSeconds estimated time remaining in seconds
     * @param speed                conversion speed string (e.g., "2.5 MB/s")
     */
    public void updateProgress(int currentFile, int totalFiles, double overallProgress,
            long timeRemainingSeconds, String speed) {
        // Update progress bar
        progressBar.setFraction(overallProgress);
        progressBar.setText(String.format("%.0f%%", overallProgress * 100));

        // Update status label
        String status = String.format("Converting %d of %d files...", currentFile, totalFiles);
        statusLabel.setLabel(status);

        // Update time remaining
        String timeRemaining = formatTimeRemaining(timeRemainingSeconds);
        timeRemainingLabel.setLabel("Time remaining: " + timeRemaining);

        // Update conversion speed
        conversionSpeedLabel.setLabel("Speed: " + (speed != null ? speed : "--"));

        logger.debug("Progress updated: {}/{} ({}%)", currentFile, totalFiles,
                (int) (overallProgress * 100));
    }

    /**
     * Clears all tracked file progress.
     * 
     * <p>
     * Should be called when starting a new batch conversion
     * or when clearing the file list.
     * </p>
     */
    public void clearFileProgress() {
        fileProgressMap.clear();
        logger.debug("File progress tracking cleared");
    }

    /**
     * Gets the current progress for a specific file.
     * 
     * @param fileId the unique file identifier
     * @return the conversion progress, or null if not found
     */
    public ConversionProgress getFileProgress(String fileId) {
        return fileProgressMap.get(fileId);
    }

    /**
     * Formats time remaining in seconds to human-readable format.
     * 
     * @param seconds the number of seconds
     * @return formatted time string (e.g., "2h 15m" or "45s")
     */
    private String formatTimeRemaining(long seconds) {
        if (seconds < 0) {
            return "--";
        }

        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return minutes + "m " + secs + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }
}
