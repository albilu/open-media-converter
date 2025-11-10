// filepath: src/main/java/org/omc/model/BatchConversionResult.java

package org.omc.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Aggregates results from multiple file conversions.
 * Requirement REQ-004.2: Batch conversion with aggregated results.
 */
public final class BatchConversionResult {

    private final List<ConversionResult> results;
    private final Duration totalTime;
    private final int successCount;
    private final int failureCount;
    private final long totalInputSize;
    private final long totalOutputSize;

    @JsonCreator
    public BatchConversionResult(
            @JsonProperty("results") List<ConversionResult> results,
            @JsonProperty("totalTime") Duration totalTime,
            @JsonProperty("successCount") int successCount,
            @JsonProperty("failureCount") int failureCount,
            @JsonProperty("totalInputSize") long totalInputSize,
            @JsonProperty("totalOutputSize") long totalOutputSize) {
        this.results = new ArrayList<>(results);
        this.totalTime = totalTime;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.totalInputSize = totalInputSize;
        this.totalOutputSize = totalOutputSize;
    }

    /**
     * Creates a batch result from a list of individual results.
     */
    public static BatchConversionResult from(List<ConversionResult> results, Duration totalTime) {
        int successes = (int) results.stream().filter(ConversionResult::success).count();
        int failures = results.size() - successes;

        long inputSize = results.stream().mapToLong(ConversionResult::inputSize).sum();
        long outputSize = results.stream().mapToLong(ConversionResult::outputSize).sum();

        return new BatchConversionResult(results, totalTime, successes, failures, inputSize, outputSize);
    }

    public List<ConversionResult> results() {
        return Collections.unmodifiableList(results);
    }

    public Duration totalTime() {
        return totalTime;
    }

    public int successCount() {
        return successCount;
    }

    public int failureCount() {
        return failureCount;
    }

    public int totalCount() {
        return results.size();
    }

    public long totalInputSize() {
        return totalInputSize;
    }

    public long totalOutputSize() {
        return totalOutputSize;
    }

    /**
     * Calculates total space saved across all conversions.
     */
    public long totalSpaceSaved() {
        return totalInputSize - totalOutputSize;
    }

    /**
     * Calculates overall compression ratio as a percentage.
     */
    public double overallCompressionRatio() {
        if (totalInputSize == 0) {
            return 0.0;
        }
        return ((double) totalSpaceSaved() / totalInputSize) * 100.0;
    }

    /**
     * Calculates average conversion speed in bytes per second.
     * Requirement REQ-004.3: Track conversion statistics including speed.
     * 
     * @return bytes per second, or 0 if no time elapsed
     */
    public double averageConversionSpeed() {
        long totalSeconds = totalTime.getSeconds();
        if (totalSeconds == 0) {
            return 0.0;
        }
        return (double) totalInputSize / totalSeconds;
    }

    /**
     * Formats the average conversion speed as a human-readable string.
     * 
     * @return formatted speed string (e.g., "1.2 MB/s")
     */
    public String formatAverageSpeed() {
        double bytesPerSecond = averageConversionSpeed();
        if (bytesPerSecond < 1024) {
            return String.format("%.1f B/s", bytesPerSecond);
        } else if (bytesPerSecond < 1024 * 1024) {
            return String.format("%.1f KB/s", bytesPerSecond / 1024.0);
        } else if (bytesPerSecond < 1024 * 1024 * 1024) {
            return String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB/s", bytesPerSecond / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Gets all failed results.
     */
    public List<ConversionResult> failures() {
        return results.stream()
                .filter(r -> !r.success())
                .collect(Collectors.toList());
    }

    /**
     * Gets all successful results.
     */
    public List<ConversionResult> successes() {
        return results.stream()
                .filter(ConversionResult::success)
                .collect(Collectors.toList());
    }

    /**
     * Checks if all conversions succeeded.
     */
    public boolean allSucceeded() {
        return failureCount == 0;
    }

    /**
     * Checks if any conversions succeeded.
     */
    public boolean anySucceeded() {
        return successCount > 0;
    }

    /**
     * Formats the total time as a human-readable string.
     */
    public String formatTotalTime() {
        long seconds = totalTime.getSeconds();
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
     * Formats the space saved as a human-readable string.
     */
    public String formatSpaceSaved() {
        long saved = totalSpaceSaved();
        if (saved < 0) {
            return formatSize(-saved) + " increase";
        } else {
            return formatSize(saved) + " saved";
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BatchConversionResult that = (BatchConversionResult) o;
        return successCount == that.successCount &&
                failureCount == that.failureCount &&
                totalInputSize == that.totalInputSize &&
                totalOutputSize == that.totalOutputSize &&
                Objects.equals(results, that.results) &&
                Objects.equals(totalTime, that.totalTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(results, totalTime, successCount, failureCount,
                totalInputSize, totalOutputSize);
    }

    @Override
    public String toString() {
        return "BatchConversionResult{" +
                "totalCount=" + totalCount() +
                ", successCount=" + successCount +
                ", failureCount=" + failureCount +
                ", totalTime=" + formatTotalTime() +
                ", spaceSaved=" + formatSpaceSaved() +
                ", compressionRatio=" + String.format("%.1f%%", overallCompressionRatio()) +
                ", avgSpeed=" + formatAverageSpeed() +
                '}';
    }
}
