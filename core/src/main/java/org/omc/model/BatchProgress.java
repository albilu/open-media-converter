// filepath: src/main/java/org/omc/model/BatchProgress.java

package org.omc.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the overall progress of a batch conversion.
 * Requirement REQ-004.3: Batch progress tracking.
 */
public final class BatchProgress {

    private final int totalFiles;
    private final int completedFiles;
    private final int failedFiles;
    private final int inProgressFiles;
    private final int pendingFiles;
    private final long totalBytes;
    private final long processedBytes;
    private final int overallPercentage;
    private final Instant startTime;
    private final Duration elapsedTime;
    private final Duration estimatedTimeRemaining;
    private final double bytesPerSecond;

    @JsonCreator
    public BatchProgress(
            @JsonProperty("totalFiles") int totalFiles,
            @JsonProperty("completedFiles") int completedFiles,
            @JsonProperty("failedFiles") int failedFiles,
            @JsonProperty("inProgressFiles") int inProgressFiles,
            @JsonProperty("pendingFiles") int pendingFiles,
            @JsonProperty("totalBytes") long totalBytes,
            @JsonProperty("processedBytes") long processedBytes,
            @JsonProperty("overallPercentage") int overallPercentage,
            @JsonProperty("startTime") Instant startTime,
            @JsonProperty("elapsedTime") Duration elapsedTime,
            @JsonProperty("estimatedTimeRemaining") Duration estimatedTimeRemaining,
            @JsonProperty("bytesPerSecond") double bytesPerSecond) {
        this.totalFiles = totalFiles;
        this.completedFiles = completedFiles;
        this.failedFiles = failedFiles;
        this.inProgressFiles = inProgressFiles;
        this.pendingFiles = pendingFiles;
        this.totalBytes = totalBytes;
        this.processedBytes = processedBytes;
        this.overallPercentage = overallPercentage;
        this.startTime = startTime;
        this.elapsedTime = elapsedTime;
        this.estimatedTimeRemaining = estimatedTimeRemaining;
        this.bytesPerSecond = bytesPerSecond;
    }

    /**
     * Creates an initial batch progress.
     */
    public static BatchProgress initial(int totalFiles, long totalBytes) {
        return new BatchProgress(
                totalFiles,
                0,
                0,
                0,
                totalFiles,
                totalBytes,
                0,
                0,
                Instant.now(),
                Duration.ZERO,
                Duration.ZERO,
                0.0);
    }

    /**
     * Creates an updated batch progress.
     */
    public static BatchProgress update(int totalFiles, int completedFiles, int failedFiles,
            int inProgressFiles, long totalBytes, long processedBytes,
            Instant startTime) {
        int pendingFiles = totalFiles - completedFiles - failedFiles - inProgressFiles;

        Instant now = Instant.now();
        Duration elapsed = Duration.between(startTime, now);
        double elapsedSeconds = elapsed.toMillis() / 1000.0;

        // Calculate speed and ETA
        double speed = elapsedSeconds > 0 ? processedBytes / elapsedSeconds : 0.0;
        long remainingBytes = totalBytes - processedBytes;
        Duration eta = speed > 0 ? Duration.ofSeconds((long) (remainingBytes / speed)) : Duration.ZERO;

        // Calculate percentage
        int pct = totalBytes > 0 ? (int) ((processedBytes * 100) / totalBytes) : 0;
        pct = Math.min(100, Math.max(0, pct)); // Clamp to 0-100

        return new BatchProgress(
                totalFiles,
                completedFiles,
                failedFiles,
                inProgressFiles,
                pendingFiles,
                totalBytes,
                processedBytes,
                pct,
                startTime,
                elapsed,
                eta,
                speed);
    }

    public int totalFiles() {
        return totalFiles;
    }

    public int completedFiles() {
        return completedFiles;
    }

    public int failedFiles() {
        return failedFiles;
    }

    public int inProgressFiles() {
        return inProgressFiles;
    }

    public int pendingFiles() {
        return pendingFiles;
    }

    public long totalBytes() {
        return totalBytes;
    }

    public long processedBytes() {
        return processedBytes;
    }

    public int overallPercentage() {
        return overallPercentage;
    }

    public Instant startTime() {
        return startTime;
    }

    public Duration elapsedTime() {
        return elapsedTime;
    }

    public Duration estimatedTimeRemaining() {
        return estimatedTimeRemaining;
    }

    public double bytesPerSecond() {
        return bytesPerSecond;
    }

    /**
     * Checks if the batch is complete.
     */
    public boolean isComplete() {
        return (completedFiles + failedFiles) >= totalFiles;
    }

    /**
     * Formats the speed as a human-readable string.
     */
    public String formatSpeed() {
        if (bytesPerSecond < 1024) {
            return String.format(Locale.US, "%.1f B/s", bytesPerSecond);
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB/s", bytesPerSecond / 1024);
        } else if (bytesPerSecond < 1024 * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB/s", bytesPerSecond / (1024 * 1024));
        } else {
            return String.format(Locale.US, "%.1f GB/s", bytesPerSecond / (1024 * 1024 * 1024));
        }
    }

    /**
     * Formats the ETA as a human-readable string.
     */
    public String formatEta() {
        if (estimatedTimeRemaining.isZero() || estimatedTimeRemaining.isNegative()) {
            return "Unknown";
        }

        long seconds = estimatedTimeRemaining.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }

    /**
     * Formats a summary status message.
     */
    public String formatStatusMessage() {
        return String.format("Converting %d of %d files (%d%% complete)",
                completedFiles + inProgressFiles, totalFiles, overallPercentage);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BatchProgress that = (BatchProgress) o;
        return totalFiles == that.totalFiles &&
                completedFiles == that.completedFiles &&
                failedFiles == that.failedFiles &&
                inProgressFiles == that.inProgressFiles &&
                pendingFiles == that.pendingFiles &&
                totalBytes == that.totalBytes &&
                processedBytes == that.processedBytes &&
                overallPercentage == that.overallPercentage &&
                Double.compare(that.bytesPerSecond, bytesPerSecond) == 0 &&
                Objects.equals(startTime, that.startTime) &&
                Objects.equals(elapsedTime, that.elapsedTime) &&
                Objects.equals(estimatedTimeRemaining, that.estimatedTimeRemaining);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalFiles, completedFiles, failedFiles, inProgressFiles,
                pendingFiles, totalBytes, processedBytes, overallPercentage,
                startTime, elapsedTime, estimatedTimeRemaining, bytesPerSecond);
    }

    @Override
    public String toString() {
        return "BatchProgress{" +
                "status='" + formatStatusMessage() + '\'' +
                ", speed=" + formatSpeed() +
                ", eta=" + formatEta() +
                ", completed=" + completedFiles +
                ", failed=" + failedFiles +
                ", inProgress=" + inProgressFiles +
                ", pending=" + pendingFiles +
                '}';
    }
}
