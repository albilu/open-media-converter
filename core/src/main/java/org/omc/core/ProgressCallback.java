// filepath: src/main/java/org/omc/core/ProgressCallback.java

package org.omc.core;

/**
 * Callback interface for receiving conversion progress updates.
 * 
 * Requirements:
 * - REQ-004.3: Real-time progress tracking for conversions
 */
@FunctionalInterface
public interface ProgressCallback {

    /** Percentage marker for activity whose completion fraction is unknown. */
    double INDETERMINATE = -1.0;

    /**
     * Called when conversion progress is updated.
     * 
     * @param percentage     progress percentage (0.0 to 100.0), or
     *                       {@link #INDETERMINATE} when it cannot be measured
     * @param bytesProcessed number of bytes processed so far
     * @param speed          current conversion speed in bytes per second
     */
    void onProgress(double percentage, long bytesProcessed, double speed);

    /**
     * Reports ongoing activity without inventing a percentage, byte count or speed.
     * Activity updates should be emitted at most twice per second.
     */
    default void onIndeterminate() {
        onProgress(INDETERMINATE, 0, 0);
    }

    /**
     * Creates a no-op progress callback that ignores updates.
     * 
     * @return no-op callback
     */
    static ProgressCallback noOp() {
        return (percentage, bytesProcessed, speed) -> {
        };
    }
}
