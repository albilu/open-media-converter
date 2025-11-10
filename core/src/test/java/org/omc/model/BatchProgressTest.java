// filepath: src/test/java/org/omc/model/BatchProgressTest.java

package org.omc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for BatchProgress.
 * Requirement REQ-004.3: Batch progress tracking and calculations.
 */
class BatchProgressTest {

    // ===== Constructor Tests =====

    @Test
    void constructor_WithValidParameters_CreatesInstance() {
        Instant start = Instant.now();
        BatchProgress progress = new BatchProgress(
                10, 5, 1, 2, 2,
                1000000, 600000, 60,
                start, Duration.ofMinutes(5),
                Duration.ofMinutes(3), 2000.0);

        assertEquals(10, progress.totalFiles());
        assertEquals(5, progress.completedFiles());
        assertEquals(1, progress.failedFiles());
        assertEquals(2, progress.inProgressFiles());
        assertEquals(2, progress.pendingFiles());
        assertEquals(1000000, progress.totalBytes());
        assertEquals(600000, progress.processedBytes());
        assertEquals(60, progress.overallPercentage());
        assertEquals(start, progress.startTime());
        assertEquals(Duration.ofMinutes(5), progress.elapsedTime());
        assertEquals(Duration.ofMinutes(3), progress.estimatedTimeRemaining());
        assertEquals(2000.0, progress.bytesPerSecond(), 0.001);
    }

    @Test
    void constructor_WithZeroValues_CreatesInstance() {
        Instant start = Instant.now();
        BatchProgress progress = new BatchProgress(
                0, 0, 0, 0, 0,
                0, 0, 0,
                start, Duration.ZERO,
                Duration.ZERO, 0.0);

        assertEquals(0, progress.totalFiles());
        assertEquals(0, progress.totalBytes());
        assertEquals(0, progress.overallPercentage());
    }

    // ===== initial() Factory Method Tests =====

    @Test
    void initial_WithValidParameters_CreatesInitialProgress() {
        BatchProgress progress = BatchProgress.initial(10, 5000000);

        assertEquals(10, progress.totalFiles());
        assertEquals(0, progress.completedFiles());
        assertEquals(0, progress.failedFiles());
        assertEquals(0, progress.inProgressFiles());
        assertEquals(10, progress.pendingFiles());
        assertEquals(5000000, progress.totalBytes());
        assertEquals(0, progress.processedBytes());
        assertEquals(0, progress.overallPercentage());
        assertNotNull(progress.startTime());
        assertEquals(Duration.ZERO, progress.elapsedTime());
        assertEquals(Duration.ZERO, progress.estimatedTimeRemaining());
        assertEquals(0.0, progress.bytesPerSecond(), 0.001);
    }

    @Test
    void initial_WithZeroFiles_CreatesProgress() {
        BatchProgress progress = BatchProgress.initial(0, 0);

        assertEquals(0, progress.totalFiles());
        assertEquals(0, progress.totalBytes());
        assertEquals(0, progress.pendingFiles());
    }

    @Test
    void initial_WithSingleFile_CreatesProgress() {
        BatchProgress progress = BatchProgress.initial(1, 1024);

        assertEquals(1, progress.totalFiles());
        assertEquals(1024, progress.totalBytes());
        assertEquals(1, progress.pendingFiles());
    }

    // ===== update() Factory Method Tests =====

    @Test
    void update_WithHalfComplete_CalculatesCorrectly() throws InterruptedException {
        Instant start = Instant.now().minusSeconds(10);
        Thread.sleep(10); // Small delay to ensure elapsed time > 0

        BatchProgress progress = BatchProgress.update(
                10, 5, 0, 2,
                1000000, 500000,
                start);

        assertEquals(10, progress.totalFiles());
        assertEquals(5, progress.completedFiles());
        assertEquals(0, progress.failedFiles());
        assertEquals(2, progress.inProgressFiles());
        assertEquals(3, progress.pendingFiles()); // 10 - 5 - 0 - 2 = 3
        assertEquals(1000000, progress.totalBytes());
        assertEquals(500000, progress.processedBytes());
        assertEquals(50, progress.overallPercentage());
        assertTrue(progress.bytesPerSecond() > 0);
        assertTrue(progress.elapsedTime().toMillis() > 0);
        assertTrue(progress.estimatedTimeRemaining().toMillis() >= 0);
    }

    @Test
    void update_WithAllComplete_CalculatesCorrectly() {
        Instant start = Instant.now().minusSeconds(5);

        BatchProgress progress = BatchProgress.update(
                10, 10, 0, 0,
                1000000, 1000000,
                start);

        assertEquals(10, progress.completedFiles());
        assertEquals(0, progress.pendingFiles());
        assertEquals(100, progress.overallPercentage());
        assertTrue(progress.isComplete());
    }

    @Test
    void update_WithSomeFailures_CalculatesCorrectly() {
        Instant start = Instant.now().minusSeconds(10);

        BatchProgress progress = BatchProgress.update(
                10, 5, 3, 1,
                1000000, 600000,
                start);

        assertEquals(5, progress.completedFiles());
        assertEquals(3, progress.failedFiles());
        assertEquals(1, progress.inProgressFiles());
        assertEquals(1, progress.pendingFiles()); // 10 - 5 - 3 - 1 = 1
    }

    @Test
    void update_WithZeroElapsedTime_HandlesGracefully() {
        Instant start = Instant.now();

        BatchProgress progress = BatchProgress.update(
                10, 2, 0, 1,
                1000000, 100000,
                start);

        // Speed calculation should handle zero elapsed time
        assertTrue(progress.bytesPerSecond() >= 0);
        assertEquals(10, progress.overallPercentage());
    }

    @Test
    void update_WithZeroTotalBytes_HandlesGracefully() {
        Instant start = Instant.now().minusSeconds(5);

        BatchProgress progress = BatchProgress.update(
                10, 2, 0, 1,
                0, 0,
                start);

        assertEquals(0, progress.overallPercentage());
        assertTrue(progress.elapsedTime().toMillis() > 0);
    }

    @Test
    void update_WithProcessedBytesExceedingTotal_ClampsPercentage() {
        Instant start = Instant.now().minusSeconds(5);

        BatchProgress progress = BatchProgress.update(
                10, 5, 0, 2,
                1000000, 1500000, // Processed > total
                start);

        // Percentage should be clamped to 100
        assertEquals(100, progress.overallPercentage());
    }

    @Test
    void update_WithNegativeProcessedBytes_ClampsPercentage() {
        Instant start = Instant.now().minusSeconds(5);

        BatchProgress progress = BatchProgress.update(
                10, 0, 0, 1,
                1000000, -100, // Negative processed (shouldn't happen in practice)
                start);

        // Percentage should be clamped to 0
        assertEquals(0, progress.overallPercentage());
    }

    // ===== isComplete() Tests =====

    @Test
    void isComplete_WithAllCompleted_ReturnsTrue() {
        BatchProgress progress = BatchProgress.initial(10, 1000000);
        BatchProgress completed = BatchProgress.update(
                10, 10, 0, 0,
                1000000, 1000000,
                progress.startTime());

        assertTrue(completed.isComplete());
    }

    @Test
    void isComplete_WithAllFailed_ReturnsTrue() {
        BatchProgress progress = BatchProgress.initial(10, 1000000);
        BatchProgress failed = BatchProgress.update(
                10, 0, 10, 0,
                1000000, 0,
                progress.startTime());

        assertTrue(failed.isComplete());
    }

    @Test
    void isComplete_WithMixedCompletedAndFailed_ReturnsTrue() {
        BatchProgress progress = BatchProgress.initial(10, 1000000);
        BatchProgress mixed = BatchProgress.update(
                10, 7, 3, 0,
                1000000, 700000,
                progress.startTime());

        assertTrue(mixed.isComplete());
    }

    @Test
    void isComplete_WithInProgress_ReturnsFalse() {
        BatchProgress progress = BatchProgress.update(
                10, 5, 1, 2,
                1000000, 600000,
                Instant.now());

        assertFalse(progress.isComplete());
    }

    @Test
    void isComplete_WithPending_ReturnsFalse() {
        BatchProgress progress = BatchProgress.update(
                10, 5, 0, 0,
                1000000, 500000,
                Instant.now());

        assertFalse(progress.isComplete());
    }

    // ===== formatSpeed() Tests =====

    @Test
    void formatSpeed_WithBytesPerSecond_FormatsCorrectly() {
        BatchProgress progress = new BatchProgress(
                10, 5, 0, 2, 3,
                1000000, 500000, 50,
                Instant.now(), Duration.ofMinutes(1),
                Duration.ofMinutes(1), 512.5);

        assertEquals("512.5 B/s", progress.formatSpeed());
    }

    @Test
    void formatSpeed_WithKilobytesPerSecond_FormatsCorrectly() {
        BatchProgress progress = new BatchProgress(
                10, 5, 0, 2, 3,
                1000000, 500000, 50,
                Instant.now(), Duration.ofMinutes(1),
                Duration.ofMinutes(1), 51200.0);

        assertEquals("50.0 KB/s", progress.formatSpeed());
    }

    @Test
    void formatSpeed_WithMegabytesPerSecond_FormatsCorrectly() {
        BatchProgress progress = new BatchProgress(
                10, 5, 0, 2, 3,
                1000000, 500000, 50,
                Instant.now(), Duration.ofMinutes(1),
                Duration.ofMinutes(1), 5242880.0);

        assertEquals("5.0 MB/s", progress.formatSpeed());
    }

    @Test
    void formatSpeed_WithGigabytesPerSecond_FormatsCorrectly() {
        BatchProgress progress = new BatchProgress(
                10, 5, 0, 2, 3,
                1000000, 500000, 50,
                Instant.now(), Duration.ofMinutes(1),
                Duration.ofMinutes(1), 5368709120.0);

        assertEquals("5.0 GB/s", progress.formatSpeed());
    }

    @Test
    void formatSpeed_WithZeroSpeed_FormatsCorrectly() {
        BatchProgress progress = new BatchProgress(
                10, 0, 0, 1, 9,
                1000000, 0, 0,
                Instant.now(), Duration.ZERO,
                Duration.ZERO, 0.0);

        assertEquals("0.0 B/s", progress.formatSpeed());
    }

    @Test
    void formatSpeed_WithDecimalValues_FormatsCorrectly() {
        BatchProgress progress = new BatchProgress(
                10, 5, 0, 2, 3,
                1000000, 500000, 50,
                Instant.now(), Duration.ofMinutes(1),
                Duration.ofMinutes(1), 1536.75);

        assertEquals("1.5 KB/s", progress.formatSpeed());
    }

    // ===== formatEta() Tests =====

    @Test
    void formatEta_WithHoursMinutesSeconds_FormatsCorrectly() {
        BatchProgress progress = new BatchProgress(
                10, 5, 0, 2, 3,
                1000000, 500000, 50,
                Instant.now(), Duration.ofMinutes(1),
                Duration.ofSeconds(3723), // 1h 2m 3s
                5000.0);

        assertEquals("1h 2m 3s", progress.formatEta());
    }

    @Test
    void formatEta_WithMinutesSeconds_FormatsCorrectly() {
        BatchProgress progress = new BatchProgress(
                10, 5, 0, 2, 3,
                1000000, 500000, 50,
                Instant.now(), Duration.ofMinutes(1),
                Duration.ofSeconds(123), // 2m 3s
                5000.0);

        assertEquals("2m 3s", progress.formatEta());
    }

    @Test
    void formatEta_WithOnlySeconds_FormatsCorrectly() {
        BatchProgress progress = new BatchProgress(
                10, 5, 0, 2, 3,
                1000000, 500000, 50,
                Instant.now(), Duration.ofMinutes(1),
                Duration.ofSeconds(45),
                5000.0);

        assertEquals("45s", progress.formatEta());
    }

    @Test
    void formatEta_WithZeroDuration_ReturnsUnknown() {
        BatchProgress progress = new BatchProgress(
                10, 5, 0, 2, 3,
                1000000, 500000, 50,
                Instant.now(), Duration.ofMinutes(1),
                Duration.ZERO,
                5000.0);

        assertEquals("Unknown", progress.formatEta());
    }

    @Test
    void formatEta_WithNegativeDuration_ReturnsUnknown() {
        BatchProgress progress = new BatchProgress(
                10, 5, 0, 2, 3,
                1000000, 500000, 50,
                Instant.now(), Duration.ofMinutes(1),
                Duration.ofSeconds(-10),
                5000.0);

        assertEquals("Unknown", progress.formatEta());
    }

    @Test
    void formatEta_WithExactHours_FormatsCorrectly() {
        BatchProgress progress = new BatchProgress(
                10, 5, 0, 2, 3,
                1000000, 500000, 50,
                Instant.now(), Duration.ofMinutes(1),
                Duration.ofHours(2), // 2h 0m 0s
                5000.0);

        assertEquals("2h 0m 0s", progress.formatEta());
    }

    // ===== formatStatusMessage() Tests =====

    @Test
    void formatStatusMessage_WithInProgressFiles_FormatsCorrectly() {
        BatchProgress progress = new BatchProgress(
                10, 5, 0, 2, 3,
                1000000, 600000, 60,
                Instant.now(), Duration.ofMinutes(1),
                Duration.ofMinutes(1), 10000.0);

        assertEquals("Converting 7 of 10 files (60% complete)", progress.formatStatusMessage());
    }

    @Test
    void formatStatusMessage_WithNoInProgress_FormatsCorrectly() {
        BatchProgress progress = new BatchProgress(
                10, 5, 0, 0, 5,
                1000000, 500000, 50,
                Instant.now(), Duration.ofMinutes(1),
                Duration.ofMinutes(1), 10000.0);

        assertEquals("Converting 5 of 10 files (50% complete)", progress.formatStatusMessage());
    }

    @Test
    void formatStatusMessage_WithComplete_FormatsCorrectly() {
        BatchProgress progress = new BatchProgress(
                10, 10, 0, 0, 0,
                1000000, 1000000, 100,
                Instant.now(), Duration.ofMinutes(5),
                Duration.ZERO, 3333.3);

        assertEquals("Converting 10 of 10 files (100% complete)", progress.formatStatusMessage());
    }

    @Test
    void formatStatusMessage_WithZeroProgress_FormatsCorrectly() {
        BatchProgress progress = BatchProgress.initial(10, 1000000);

        assertEquals("Converting 0 of 10 files (0% complete)", progress.formatStatusMessage());
    }

    // ===== equals() Tests =====

    @Test
    void equals_WithSameInstance_ReturnsTrue() {
        BatchProgress progress = BatchProgress.initial(10, 1000000);

        assertEquals(progress, progress);
    }

    @Test
    void equals_WithEqualObjects_ReturnsTrue() {
        Instant start = Instant.now();
        BatchProgress progress1 = new BatchProgress(
                10, 5, 1, 2, 2,
                1000000, 600000, 60,
                start, Duration.ofMinutes(5),
                Duration.ofMinutes(3), 2000.0);
        BatchProgress progress2 = new BatchProgress(
                10, 5, 1, 2, 2,
                1000000, 600000, 60,
                start, Duration.ofMinutes(5),
                Duration.ofMinutes(3), 2000.0);

        assertEquals(progress1, progress2);
        assertEquals(progress1.hashCode(), progress2.hashCode());
    }

    @Test
    void equals_WithDifferentTotalFiles_ReturnsFalse() {
        Instant start = Instant.now();
        BatchProgress progress1 = new BatchProgress(
                10, 5, 1, 2, 2,
                1000000, 600000, 60,
                start, Duration.ofMinutes(5),
                Duration.ofMinutes(3), 2000.0);
        BatchProgress progress2 = new BatchProgress(
                20, 5, 1, 2, 2,
                1000000, 600000, 60,
                start, Duration.ofMinutes(5),
                Duration.ofMinutes(3), 2000.0);

        assertNotEquals(progress1, progress2);
    }

    @Test
    void equals_WithDifferentCompletedFiles_ReturnsFalse() {
        Instant start = Instant.now();
        BatchProgress progress1 = new BatchProgress(
                10, 5, 1, 2, 2,
                1000000, 600000, 60,
                start, Duration.ofMinutes(5),
                Duration.ofMinutes(3), 2000.0);
        BatchProgress progress2 = new BatchProgress(
                10, 6, 1, 2, 2,
                1000000, 600000, 60,
                start, Duration.ofMinutes(5),
                Duration.ofMinutes(3), 2000.0);

        assertNotEquals(progress1, progress2);
    }

    @Test
    void equals_WithDifferentSpeed_ReturnsFalse() {
        Instant start = Instant.now();
        BatchProgress progress1 = new BatchProgress(
                10, 5, 1, 2, 2,
                1000000, 600000, 60,
                start, Duration.ofMinutes(5),
                Duration.ofMinutes(3), 2000.0);
        BatchProgress progress2 = new BatchProgress(
                10, 5, 1, 2, 2,
                1000000, 600000, 60,
                start, Duration.ofMinutes(5),
                Duration.ofMinutes(3), 3000.0);

        assertNotEquals(progress1, progress2);
    }

    @Test
    void equals_WithNull_ReturnsFalse() {
        BatchProgress progress = BatchProgress.initial(10, 1000000);

        assertNotEquals(progress, null);
    }

    @Test
    void equals_WithDifferentClass_ReturnsFalse() {
        BatchProgress progress = BatchProgress.initial(10, 1000000);

        assertNotEquals(progress, "Not a BatchProgress");
    }

    // ===== hashCode() Tests =====

    @Test
    void hashCode_WithEqualObjects_ReturnsSameHash() {
        Instant start = Instant.now();
        BatchProgress progress1 = new BatchProgress(
                10, 5, 1, 2, 2,
                1000000, 600000, 60,
                start, Duration.ofMinutes(5),
                Duration.ofMinutes(3), 2000.0);
        BatchProgress progress2 = new BatchProgress(
                10, 5, 1, 2, 2,
                1000000, 600000, 60,
                start, Duration.ofMinutes(5),
                Duration.ofMinutes(3), 2000.0);

        assertEquals(progress1.hashCode(), progress2.hashCode());
    }

    @Test
    void hashCode_WithDifferentObjects_ReturnsDifferentHash() {
        BatchProgress progress1 = BatchProgress.initial(10, 1000000);
        BatchProgress progress2 = BatchProgress.initial(20, 2000000);

        assertNotEquals(progress1.hashCode(), progress2.hashCode());
    }

    // ===== toString() Tests =====

    @Test
    void toString_ContainsKeyInformation() {
        BatchProgress progress = new BatchProgress(
                10, 5, 1, 2, 2,
                1000000, 600000, 60,
                Instant.now(), Duration.ofMinutes(5),
                Duration.ofMinutes(3), 2000.0);

        String str = progress.toString();
        assertTrue(str.contains("BatchProgress"));
        assertTrue(str.contains("completed=5"));
        assertTrue(str.contains("failed=1"));
        assertTrue(str.contains("inProgress=2"));
        assertTrue(str.contains("pending=2"));
    }

    @Test
    void toString_ContainsFormattedSpeed() {
        BatchProgress progress = new BatchProgress(
                10, 5, 1, 2, 2,
                1000000, 600000, 60,
                Instant.now(), Duration.ofMinutes(5),
                Duration.ofMinutes(3), 51200.0);

        String str = progress.toString();
        assertTrue(str.contains("KB/s"));
    }

    @Test
    void toString_ContainsFormattedEta() {
        BatchProgress progress = new BatchProgress(
                10, 5, 1, 2, 2,
                1000000, 600000, 60,
                Instant.now(), Duration.ofMinutes(5),
                Duration.ofSeconds(180), // 3m 0s
                51200.0);

        String str = progress.toString();
        assertTrue(str.contains("eta=3m 0s"));
    }
}
