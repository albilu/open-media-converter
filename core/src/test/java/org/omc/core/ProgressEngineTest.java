package org.omc.core;

import org.omc.model.ConversionProgress;
import org.omc.model.ConversionResult;
import org.omc.model.BatchProgress;
import org.omc.core.ProgressEngine;
import org.omc.model.ConversionTool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgressEngineTest {

    private ProgressEngine progressEngine;

    @Mock
    private Consumer<ConversionProgress> progressListener;

    @Mock
    private Consumer<BatchProgress> batchProgressListener;

    @Mock
    private ConversionTool mockTool;

    @BeforeEach
    void setUp() {
        progressEngine = new ProgressEngine();
    }

    @AfterEach
    void tearDown() {
        progressEngine.reset();
    }

    // Batch initialization tests

    @Test
    void testStartBatch_ValidInputs_InitializesCorrectly() {
        // Given
        List<String> fileIds = Arrays.asList("file1", "file2");
        Map<String, Long> fileSizes = Map.of("file1", 1000L, "file2", 2000L);

        // When
        progressEngine.startBatch(fileIds, fileSizes);

        // Then
        BatchProgress batchProgress = progressEngine.getBatchProgress();
        assertEquals(2, batchProgress.totalFiles());
        assertEquals(3000L, batchProgress.totalBytes());
        assertEquals(2, batchProgress.pendingFiles());
        assertEquals(0, batchProgress.inProgressFiles());
        assertEquals(0, batchProgress.completedFiles());
        assertEquals(0, batchProgress.failedFiles());
    }

    @Test
    void testStartBatch_NullFileIds_ThrowsNullPointerException() {
        // Given
        Map<String, Long> fileSizes = Map.of("file1", 1000L);

        // When & Then
        assertThrows(NullPointerException.class, () -> progressEngine.startBatch(null, fileSizes));
    }

    @Test
    void testStartBatch_NullFileSizes_ThrowsNullPointerException() {
        // Given
        List<String> fileIds = Arrays.asList("file1");

        // When & Then
        assertThrows(NullPointerException.class, () -> progressEngine.startBatch(fileIds, null));
    }

    @Test
    void testStartBatch_EmptyFileIds_InitializesWithZero() {
        // Given
        List<String> fileIds = Collections.emptyList();
        Map<String, Long> fileSizes = Collections.emptyMap();

        // When
        progressEngine.startBatch(fileIds, fileSizes);

        // Then
        BatchProgress batchProgress = progressEngine.getBatchProgress();
        assertEquals(0, batchProgress.totalFiles());
        assertEquals(0L, batchProgress.totalBytes());
    }

    // Single file tracking tests

    @Test
    void testStartTracking_ValidInputs_InitializesProgress() {
        // Given
        String fileId = "file1";
        long totalBytes = 1000L;

        // When
        progressEngine.startTracking(fileId, totalBytes);

        // Then
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress(fileId);
        assertTrue(progressOpt.isPresent());
        ConversionProgress progress = progressOpt.get();
        assertEquals(fileId, progress.fileId());
        assertEquals(totalBytes, progress.totalBytes());
        assertEquals(0, progress.processedBytes());
        assertEquals(0, progress.percentage());
    }

    @Test
    void testStartTracking_NullFileId_ThrowsNullPointerException() {
        // When & Then
        assertThrows(NullPointerException.class, () -> progressEngine.startTracking(null, 1000L));
    }

    @Test
    void testStartTracking_NegativeTotalBytes_ThrowsIllegalArgumentException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> progressEngine.startTracking("file1", -1L));
    }

    @Test
    void testStartTracking_ZeroTotalBytes_Succeeds() {
        // When
        progressEngine.startTracking("file1", 0L);

        // Then
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress("file1");
        assertTrue(progressOpt.isPresent());
        assertEquals(0L, progressOpt.get().totalBytes());
    }

    // Progress update tests

    @Test
    void testUpdateProgress_ValidInputs_UpdatesProgress() throws InterruptedException {
        // Given
        String fileId = "file1";
        long totalBytes = 1000L;
        progressEngine.startTracking(fileId, totalBytes);

        // When
        Thread.sleep(10); // Small delay for time calculation
        progressEngine.updateProgress(fileId, 500L);

        // Then
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress(fileId);
        assertTrue(progressOpt.isPresent());
        ConversionProgress progress = progressOpt.get();
        assertEquals(500L, progress.processedBytes());
        assertEquals(50, progress.percentage());
        assertTrue(progress.elapsedTime().toMillis() > 0);
        assertTrue(progress.bytesPerSecond() > 0);
    }

    @Test
    void testUpdateProgress_NullFileId_ThrowsNullPointerException() {
        // When & Then
        assertThrows(NullPointerException.class, () -> progressEngine.updateProgress(null, 100L));
    }

    @Test
    void testUpdateProgress_NegativeProcessedBytes_IgnoresUpdate() {
        // Given
        String fileId = "file1";
        progressEngine.startTracking(fileId, 1000L);

        // When
        progressEngine.updateProgress(fileId, -100L);

        // Then
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress(fileId);
        assertTrue(progressOpt.isPresent());
        assertEquals(0L, progressOpt.get().processedBytes());
    }

    @Test
    void testUpdateProgress_NoTrackingFound_IgnoresUpdate() {
        // When
        progressEngine.updateProgress("nonexistent", 100L);

        // Then
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress("nonexistent");
        assertFalse(progressOpt.isPresent());
    }

    // Percentage-based progress update tests

    @Test
    void testUpdateProgressWithPercentage_ValidInputs_UpdatesProgress() throws InterruptedException {
        // Given
        String fileId = "file1";
        progressEngine.startTracking(fileId, 1000L);
        Thread.sleep(10); // Ensure some elapsed time

        // When
        progressEngine.updateProgressWithPercentage(fileId, 50.0);

        // Then
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress(fileId);
        assertTrue(progressOpt.isPresent());
        ConversionProgress progress = progressOpt.get();
        assertEquals(50, progress.percentage());
        assertEquals(500L, progress.processedBytes()); // 50% of 1000
        assertTrue(progress.bytesPerSecond() > 0);
    }

    @Test
    void testUpdateProgressWithPercentage_FullProgress_ReachesOneHundredPercent() throws InterruptedException {
        // Given
        String fileId = "file1";
        progressEngine.startTracking(fileId, 1000L);
        Thread.sleep(10);

        // When
        progressEngine.updateProgressWithPercentage(fileId, 100.0);

        // Then
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress(fileId);
        assertTrue(progressOpt.isPresent());
        ConversionProgress progress = progressOpt.get();
        assertEquals(100, progress.percentage());
        assertEquals(1000L, progress.processedBytes());
    }

    @Test
    void testUpdateProgressWithPercentage_IntermediateValues_AccurateTracking() throws InterruptedException {
        // Given
        String fileId = "file1";
        progressEngine.startTracking(fileId, 1000L);
        Thread.sleep(10);

        // When - simulate FFmpeg progress updates
        progressEngine.updateProgressWithPercentage(fileId, 10.5);
        ConversionProgress progress1 = progressEngine.getProgress(fileId).get();

        Thread.sleep(10);
        progressEngine.updateProgressWithPercentage(fileId, 35.2);
        ConversionProgress progress2 = progressEngine.getProgress(fileId).get();

        Thread.sleep(10);
        progressEngine.updateProgressWithPercentage(fileId, 67.8);
        ConversionProgress progress3 = progressEngine.getProgress(fileId).get();

        // Then - percentages should match inputs (not calculated from bytes)
        assertEquals(10, progress1.percentage()); // 10.5 truncated to 10
        assertEquals(35, progress2.percentage()); // 35.2 truncated to 35
        assertEquals(67, progress3.percentage()); // 67.8 truncated to 67
    }

    @Test
    void testUpdateProgressWithPercentage_NullFileId_ThrowsNullPointerException() {
        // When & Then
        assertThrows(NullPointerException.class,
                () -> progressEngine.updateProgressWithPercentage(null, 50.0));
    }

    @Test
    void testUpdateProgressWithPercentage_InvalidPercentageNegative_IgnoresUpdate() {
        // Given
        String fileId = "file1";
        progressEngine.startTracking(fileId, 1000L);

        // When
        progressEngine.updateProgressWithPercentage(fileId, -10.0);

        // Then - progress should remain at 0
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress(fileId);
        assertTrue(progressOpt.isPresent());
        assertEquals(0, progressOpt.get().percentage());
    }

    @Test
    void testUpdateProgressWithPercentage_InvalidPercentageOverHundred_IgnoresUpdate() {
        // Given
        String fileId = "file1";
        progressEngine.startTracking(fileId, 1000L);

        // When
        progressEngine.updateProgressWithPercentage(fileId, 150.0);

        // Then - progress should remain at 0
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress(fileId);
        assertTrue(progressOpt.isPresent());
        assertEquals(0, progressOpt.get().percentage());
    }

    @Test
    void testUpdateProgressWithPercentage_NoTrackingFound_IgnoresUpdate() {
        // When
        progressEngine.updateProgressWithPercentage("nonexistent", 50.0);

        // Then
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress("nonexistent");
        assertFalse(progressOpt.isPresent());
    }

    @Test
    void testUpdateProgressWithPercentage_ComparedToBytesBased_AvoidsPrematureCompletion() throws InterruptedException {
        // Given - simulate a video conversion where output size != input size
        String fileId = "file1";
        long inputSize = 10000000L; // 10 MB input
        progressEngine.startTracking(fileId, inputSize);
        Thread.sleep(10);

        // When - FFmpeg reports 40% progress by time, but output file is already 5MB
        // (50% of input)
        // Old method: would calculate percentage from bytes = 5MB/10MB = 50%
        // New method: uses percentage directly = 40%
        progressEngine.updateProgressWithPercentage(fileId, 40.0);

        // Then - should show 40%, not a higher percentage calculated from bytes
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress(fileId);
        assertTrue(progressOpt.isPresent());
        ConversionProgress progress = progressOpt.get();
        assertEquals(40, progress.percentage());
        assertNotEquals(50, progress.percentage()); // Proves we're not calculating from bytes
    }

    // Progress queries tests

    @Test
    void testGetProgress_ExistingFile_ReturnsProgress() {
        // Given
        String fileId = "file1";
        progressEngine.startTracking(fileId, 1000L);

        // When
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress(fileId);

        // Then
        assertTrue(progressOpt.isPresent());
        assertEquals(fileId, progressOpt.get().fileId());
    }

    @Test
    void testGetProgress_NonExistingFile_ReturnsEmpty() {
        // When
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress("nonexistent");

        // Then
        assertFalse(progressOpt.isPresent());
    }

    @Test
    void testGetProgress_NullFileId_ThrowsNullPointerException() {
        // When & Then
        assertThrows(NullPointerException.class, () -> progressEngine.getProgress(null));
    }

    // Batch progress tests

    @Test
    void testGetBatchProgress_NoBatchStarted_ReturnsInitial() {
        // When
        BatchProgress batchProgress = progressEngine.getBatchProgress();

        // Then
        assertEquals(0, batchProgress.totalFiles());
        assertEquals(0L, batchProgress.totalBytes());
    }

    @Test
    void testGetBatchProgress_WithMultipleFiles_AggregatesCorrectly() {
        // Given
        List<String> fileIds = Arrays.asList("file1", "file2", "file3");
        Map<String, Long> fileSizes = Map.of("file1", 1000L, "file2", 2000L, "file3", 3000L);
        progressEngine.startBatch(fileIds, fileSizes);

        progressEngine.startTracking("file1", 1000L);
        progressEngine.startTracking("file2", 2000L);
        progressEngine.updateProgress("file1", 500L);
        progressEngine.updateProgress("file2", 1000L);

        // When
        BatchProgress batchProgress = progressEngine.getBatchProgress();

        // Then
        assertEquals(3, batchProgress.totalFiles());
        assertEquals(6000L, batchProgress.totalBytes());
        assertEquals(1500L, batchProgress.processedBytes());
        assertEquals(25, batchProgress.overallPercentage());
        assertEquals(2, batchProgress.inProgressFiles());
        assertEquals(1, batchProgress.pendingFiles());
    }

    // Time estimation tests

    @Test
    void testTimeEstimation_UpdatesCorrectly() throws InterruptedException {
        // Given
        String fileId = "file1";
        long totalBytes = 10000L;
        progressEngine.startTracking(fileId, totalBytes);

        // When
        Thread.sleep(200);
        progressEngine.updateProgress(fileId, 1000L);

        // Then
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress(fileId);
        assertTrue(progressOpt.isPresent());
        ConversionProgress progress = progressOpt.get();
        assertTrue(progress.elapsedTime().toMillis() >= 200);
        assertTrue(progress.estimatedTimeRemaining().getSeconds() > 0);
        assertTrue(progress.bytesPerSecond() > 0);
    }

    // Status tracking tests

    @Test
    void testStatusTracking_StartTracking_SetsInProgress() {
        // Given
        progressEngine.startBatch(Arrays.asList("file1"), Map.of("file1", 1000L));

        // When
        progressEngine.startTracking("file1", 1000L);

        // Then
        BatchProgress batchProgress = progressEngine.getBatchProgress();
        assertEquals(1, batchProgress.inProgressFiles());
        assertEquals(0, batchProgress.pendingFiles());
    }

    @Test
    void testCompleteTracking_Success_SetsCompleted() {
        // Given
        progressEngine.startBatch(Arrays.asList("file1"), Map.of("file1", 1000L));
        progressEngine.startTracking("file1", 1000L);
        ConversionResult result = ConversionResult.success("file1", Path.of("/output"), null, Duration.ofSeconds(1),
                1000L, 800L, mockTool);

        // When
        progressEngine.completeTracking("file1", result);

        // Then
        BatchProgress batchProgress = progressEngine.getBatchProgress();
        assertEquals(1, batchProgress.completedFiles());
        assertEquals(0, batchProgress.inProgressFiles());
    }

    @Test
    void testCompleteTracking_Failure_SetsFailed() {
        // Given
        progressEngine.startBatch(Arrays.asList("file1"), Map.of("file1", 1000L));
        progressEngine.startTracking("file1", 1000L);
        ConversionResult result = ConversionResult.failure("file1", "error", null, Duration.ofSeconds(1), 1000L,
                mockTool);

        // When
        progressEngine.completeTracking("file1", result);

        // Then
        BatchProgress batchProgress = progressEngine.getBatchProgress();
        assertEquals(1, batchProgress.failedFiles());
        assertEquals(0, batchProgress.inProgressFiles());
    }

    @Test
    void testCancelTracking_SetsCancelled() {
        // Given
        progressEngine.startBatch(Arrays.asList("file1"), Map.of("file1", 1000L));
        progressEngine.startTracking("file1", 1000L);

        // When
        progressEngine.cancelTracking("file1");

        // Then
        BatchProgress batchProgress = progressEngine.getBatchProgress();
        assertEquals(0, batchProgress.inProgressFiles());
        assertEquals(0, batchProgress.completedFiles());
        assertEquals(0, batchProgress.failedFiles());
        assertEquals(1, batchProgress.pendingFiles()); // Cancelled not counted as pending
    }

    // Listener tests

    @Test
    void testProgressListeners_NotifiedOnUpdates() throws InterruptedException {
        // Given
        progressEngine.addProgressListener(progressListener);
        progressEngine.startTracking("file1", 1000L);

        // When - add delay to exceed throttle interval (100ms)
        Thread.sleep(150);
        progressEngine.updateProgress("file1", 500L);

        // Then
        verify(progressListener, times(2)).accept(any(ConversionProgress.class)); // startTracking and updateProgress
    }

    @Test
    void testBatchProgressListeners_NotifiedOnUpdates() {
        // Given
        progressEngine.addBatchProgressListener(batchProgressListener);
        List<String> fileIds = Arrays.asList("file1");
        Map<String, Long> fileSizes = Map.of("file1", 1000L);

        // When
        progressEngine.startBatch(fileIds, fileSizes);

        // Then
        verify(batchProgressListener, atLeastOnce()).accept(any(BatchProgress.class));
    }

    @Test
    void testRemoveListeners_StopsNotifications() throws InterruptedException {
        // Given
        progressEngine.addProgressListener(progressListener);
        progressEngine.startTracking("file1", 1000L);

        // When
        Thread.sleep(150); // Exceed throttle interval
        progressEngine.removeProgressListener(progressListener);
        progressEngine.updateProgress("file1", 500L);

        // Then
        verify(progressListener, times(1)).accept(any(ConversionProgress.class)); // Only from startTracking
    }

    @Test
    void testProgressThrottling_LimitsNotificationFrequency() throws InterruptedException {
        // Given
        progressEngine.addProgressListener(progressListener);
        progressEngine.startTracking("file1", 1000L);

        // When - send multiple rapid updates within throttle interval (100ms)
        progressEngine.updateProgressWithPercentage("file1", 10.0);
        progressEngine.updateProgressWithPercentage("file1", 20.0);
        progressEngine.updateProgressWithPercentage("file1", 30.0);

        // Then - should only get 1 notification for startTracking, rapid updates are
        // throttled
        verify(progressListener, times(1)).accept(any(ConversionProgress.class));

        // When - wait for throttle interval to pass and send another update
        Thread.sleep(150); // Exceed throttle interval (100ms)
        progressEngine.updateProgressWithPercentage("file1", 40.0);

        // Then - should now get the second notification
        verify(progressListener, times(2)).accept(any(ConversionProgress.class));
    }

    @Test
    void testProgressThrottling_CompletionNotThrottled() {
        // Given
        progressEngine.addProgressListener(progressListener);
        progressEngine.startTracking("file1", 1000L);

        // When - send rapid updates followed immediately by completion
        progressEngine.updateProgressWithPercentage("file1", 50.0);
        ConversionResult result = ConversionResult.success(
                "file1",
                Path.of("/path/to/output"),
                null,
                Duration.ofSeconds(1),
                1000L,
                1000L,
                ConversionTool.FFMPEG);
        progressEngine.completeTracking("file1", result);

        // Then - should get notifications for start and completion (intermediate update
        // throttled)
        // Start notification + completion notification = 2 notifications
        verify(progressListener, times(2)).accept(any(ConversionProgress.class));
    }

    // Thread safety tests

    @Test
    void testConcurrentUpdates_ThreadSafe() throws InterruptedException {
        // Given
        progressEngine.startBatch(Arrays.asList("file1", "file2"), Map.of("file1", 1000L, "file2", 1000L));
        CountDownLatch latch = new CountDownLatch(2);

        // When
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> {
            progressEngine.startTracking("file1", 1000L);
            for (int i = 0; i < 10; i++) {
                progressEngine.updateProgress("file1", i * 100L);
            }
            latch.countDown();
        });
        executor.submit(() -> {
            progressEngine.startTracking("file2", 1000L);
            for (int i = 0; i < 10; i++) {
                progressEngine.updateProgress("file2", i * 100L);
            }
            latch.countDown();
        });

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        BatchProgress batchProgress = progressEngine.getBatchProgress();
        assertTrue(batchProgress.processedBytes() >= 0);
        assertTrue(batchProgress.processedBytes() <= 2000L);
    }

    // Edge cases

    @Test
    void testZeroBytesFile_HandlesCorrectly() {
        // Given
        progressEngine.startBatch(Arrays.asList("file1"), Map.of("file1", 0L));
        progressEngine.startTracking("file1", 0L);

        // When
        progressEngine.updateProgress("file1", 0L);

        // Then
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress("file1");
        assertTrue(progressOpt.isPresent());
        ConversionProgress progress = progressOpt.get();
        assertEquals(0, progress.percentage()); // Current implementation sets to 0 for 0/0
    }

    @Test
    void testMultipleFiles_DifferentRates_AggregatesCorrectly() {
        // Given
        progressEngine.startBatch(Arrays.asList("file1", "file2"), Map.of("file1", 1000L, "file2", 2000L));
        progressEngine.startTracking("file1", 1000L);
        progressEngine.startTracking("file2", 2000L);

        // When
        progressEngine.updateProgress("file1", 1000L); // Complete
        progressEngine.updateProgress("file2", 500L); // Half

        // Then
        BatchProgress batchProgress = progressEngine.getBatchProgress();
        assertEquals(1500L, batchProgress.processedBytes());
        assertEquals(1500L * 100 / 3000L, batchProgress.overallPercentage());
    }

    // Reset tests

    @Test
    void testReset_ClearsAllState() {
        // Given
        progressEngine.startBatch(Arrays.asList("file1"), Map.of("file1", 1000L));
        progressEngine.startTracking("file1", 1000L);
        progressEngine.updateProgress("file1", 500L);

        // When
        progressEngine.reset();

        // Then
        assertFalse(progressEngine.getProgress("file1").isPresent());
        assertEquals(0, progressEngine.getTrackedFiles().size());
        BatchProgress batchProgress = progressEngine.getBatchProgress();
        assertEquals(0, batchProgress.totalFiles());
    }

    // Completion tests

    @Test
    void testCompleteTracking_Success_UpdatesToComplete() {
        // Given
        progressEngine.startTracking("file1", 1000L);
        ConversionResult result = ConversionResult.success("file1", Path.of("/output"), null, Duration.ofSeconds(1),
                1000L, 800L, mockTool);

        // When
        progressEngine.completeTracking("file1", result);

        // Then
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress("file1");
        assertTrue(progressOpt.isPresent());
        ConversionProgress progress = progressOpt.get();
        assertEquals(1000L, progress.processedBytes());
        assertEquals(100, progress.percentage());
        assertTrue(progress.isComplete());
    }

    @Test
    void testCompleteTracking_Failure_UpdatesToComplete() {
        // Given
        progressEngine.startTracking("file1", 1000L);
        ConversionResult result = ConversionResult.failure("file1", "error", null, Duration.ofSeconds(1), 1000L,
                mockTool);

        // When
        progressEngine.completeTracking("file1", result);

        // Then
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress("file1");
        assertTrue(progressOpt.isPresent());
        ConversionProgress progress = progressOpt.get();
        assertEquals(1000L, progress.processedBytes());
        assertEquals(100, progress.percentage());
        assertTrue(progress.isComplete());
    }

    @Test
    void testCompleteTracking_NullFileId_ThrowsNullPointerException() {
        // Given
        ConversionResult result = ConversionResult.success("file1", Path.of("/output"), null, Duration.ofSeconds(1),
                1000L, 800L, mockTool);

        // When & Then
        assertThrows(NullPointerException.class, () -> progressEngine.completeTracking(null, result));
    }

    @Test
    void testCompleteTracking_NullResult_ThrowsNullPointerException() {
        // When & Then
        assertThrows(NullPointerException.class, () -> progressEngine.completeTracking("file1", null));
    }

    @Test
    void testCompleteTracking_NoTrackingFound_Ignores() {
        // Given
        ConversionResult result = ConversionResult.success("file1", Path.of("/output"), null, Duration.ofSeconds(1),
                1000L, 800L, mockTool);

        // When
        progressEngine.completeTracking("file1", result);

        // Then
        Optional<ConversionProgress> progressOpt = progressEngine.getProgress("file1");
        assertFalse(progressOpt.isPresent());
    }

    // Additional utility methods tests

    @Test
    void testGetTrackedFiles_ReturnsCorrectSet() {
        // Given
        progressEngine.startTracking("file1", 1000L);
        progressEngine.startTracking("file2", 2000L);

        // When
        Set<String> trackedFiles = progressEngine.getTrackedFiles();

        // Then
        assertEquals(2, trackedFiles.size());
        assertTrue(trackedFiles.contains("file1"));
        assertTrue(trackedFiles.contains("file2"));
    }

    @Test
    void testIsTracking_ExistingFile_ReturnsTrue() {
        // Given
        progressEngine.startTracking("file1", 1000L);

        // When
        boolean isTracking = progressEngine.isTracking("file1");

        // Then
        assertTrue(isTracking);
    }

    @Test
    void testIsTracking_NonExistingFile_ReturnsFalse() {
        // When
        boolean isTracking = progressEngine.isTracking("nonexistent");

        // Then
        assertFalse(isTracking);
    }
}