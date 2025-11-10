// filepath: src/main/java/org/omc/model/ConversionProgress.java

package org.omc.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the progress of a file conversion.
 * Requirement REQ-004.3: Progress tracking and reporting.
 */
public final class ConversionProgress {

    private final String fileId;
    private final long totalBytes;
    private final long processedBytes;
    private final int percentage; // 0-100
    private final Instant startTime;
    private final Duration elapsedTime;
    private final Duration estimatedTimeRemaining;
    private final double bytesPerSecond;

    @JsonCreator
    public ConversionProgress(
            @JsonProperty("fileId") String fileId,
            @JsonProperty("totalBytes") long totalBytes,
            @JsonProperty("processedBytes") long processedBytes,
            @JsonProperty("percentage") int percentage,
            @JsonProperty("startTime") Instant startTime,
            @JsonProperty("elapsedTime") Duration elapsedTime,
            @JsonProperty("estimatedTimeRemaining") Duration estimatedTimeRemaining,
            @JsonProperty("bytesPerSecond") double bytesPerSecond) {
        this.fileId = fileId;
        this.totalBytes = totalBytes;
        this.processedBytes = processedBytes;
        this.percentage = percentage;
        this.startTime = startTime;
        this.elapsedTime = elapsedTime;
        this.estimatedTimeRemaining = estimatedTimeRemaining;
        this.bytesPerSecond = bytesPerSecond;
    }

    /**
     * Creates an initial progress for a file.
     */
    public static ConversionProgress initial(String fileId, long totalBytes) {
        return new ConversionProgress(
                fileId,
                totalBytes,
                0,
                0,
                Instant.now(),
                Duration.ZERO,
                Duration.ZERO,
                0.0);
    }

    /**
     * Creates an updated progress with new processed bytes.
     */
    public ConversionProgress update(long processedBytes) {
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

        return new ConversionProgress(
                fileId,
                totalBytes,
                processedBytes,
                pct,
                startTime,
                elapsed,
                eta,
                speed);
    }

    /**
     * Creates an updated progress with a direct percentage value.
     * This method is used when the percentage is known directly from the conversion
     * tool
     * (e.g., FFmpeg's time-based progress) to avoid percentage->bytes->percentage
     * conversion issues.
     * 
     * @param percentage the completion percentage (0.0 to 100.0)
     * @return updated progress with the given percentage
     */
    public ConversionProgress updateWithPercentage(double percentage) {
        Instant now = Instant.now();
        Duration elapsed = Duration.between(startTime, now);
        double elapsedSeconds = elapsed.toMillis() / 1000.0;

        // Clamp percentage to valid range
        int pct = (int) Math.min(100, Math.max(0, percentage));

        // Calculate processed bytes based on percentage (for display purposes)
        long processedBytes = (long) ((pct / 100.0) * totalBytes);

        // Calculate speed based on processed bytes
        double speed = elapsedSeconds > 0 ? processedBytes / elapsedSeconds : 0.0;

        // Calculate ETA based on remaining percentage and current speed
        long remainingBytes = totalBytes - processedBytes;
        Duration eta = speed > 0 ? Duration.ofSeconds((long) (remainingBytes / speed)) : Duration.ZERO;

        return new ConversionProgress(
                fileId,
                totalBytes,
                processedBytes,
                pct,
                startTime,
                elapsed,
                eta,
                speed);
    }

    @JsonProperty("fileId")
    public String fileId() {
        return fileId;
    }

    @JsonProperty("totalBytes")
    public long totalBytes() {
        return totalBytes;
    }

    @JsonProperty("processedBytes")
    public long processedBytes() {
        return processedBytes;
    }

    @JsonProperty("percentage")
    public int percentage() {
        return percentage;
    }

    @JsonProperty("startTime")
    public Instant startTime() {
        return startTime;
    }

    @JsonProperty("elapsedTime")
    public Duration elapsedTime() {
        return elapsedTime;
    }

    @JsonProperty("estimatedTimeRemaining")
    public Duration estimatedTimeRemaining() {
        return estimatedTimeRemaining;
    }

    @JsonProperty("bytesPerSecond")
    public double bytesPerSecond() {
        return bytesPerSecond;
    }

    /**
     * Checks if the conversion is complete.
     */
    @JsonIgnore
    public boolean isComplete() {
        return processedBytes >= totalBytes && totalBytes > 0;
    }

    /**
     * Formats the speed as a human-readable string.
     */
    public String formatSpeed() {
        if (bytesPerSecond < 1024) {
            return String.format(java.util.Locale.US, "%.1f B/s", bytesPerSecond);
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f KB/s", bytesPerSecond / 1024);
        } else if (bytesPerSecond < 1024 * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSecond / (1024 * 1024));
        } else {
            return String.format(java.util.Locale.US, "%.1f GB/s", bytesPerSecond / (1024 * 1024 * 1024));
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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConversionProgress that = (ConversionProgress) o;
        return totalBytes == that.totalBytes &&
                processedBytes == that.processedBytes &&
                percentage == that.percentage &&
                Double.compare(that.bytesPerSecond, bytesPerSecond) == 0 &&
                Objects.equals(fileId, that.fileId) &&
                Objects.equals(startTime, that.startTime) &&
                Objects.equals(elapsedTime, that.elapsedTime) &&
                Objects.equals(estimatedTimeRemaining, that.estimatedTimeRemaining);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileId, totalBytes, processedBytes, percentage,
                startTime, elapsedTime, estimatedTimeRemaining, bytesPerSecond);
    }

    @Override
    public String toString() {
        return "ConversionProgress{" +
                "fileId='" + fileId + '\'' +
                ", percentage=" + percentage +
                ", speed=" + formatSpeed() +
                ", eta=" + formatEta() +
                '}';
    }
}
