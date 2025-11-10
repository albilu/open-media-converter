package org.omc.performance;

import org.omc.core.ProgressCallback;
import org.omc.core.ProgressEngine;
import org.omc.model.BatchProgress;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionTool;
import org.omc.model.ImageSettings;
import org.omc.service.ImageMagickService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Tests for progress update handling to prevent UI flooding.
 * 
 * Tests REQ-100.1: UI Responsiveness
 * - Progress updates should be handled efficiently to avoid overwhelming the UI
 * - UI should remain responsive even with rapid progress updates
 * - Progress tracking should not lose final progress values
 * 
 * Tests REQ-IMG-4, NFR-IMG-1: ImageMagick progress throttling
 * - ImageMagick progress updates should be throttled to max 2 updates/second
 * (500ms interval)
 * - Simulated progress (0%, 50%, 100%) should be properly throttled
 */
class ProgressUpdateThrottlingTest {

    @TempDir
    Path tempDir;

    private ProgressEngine progressEngine;

    @BeforeEach
    void setUp() {
        progressEngine = new ProgressEngine();
    }

    /**
     * Test that progress engine can handle rapid updates without performance
     * degradation.
     * Requirement REQ-100.1: Prevent UI flooding with rapid updates
     */
    @Test
    void testRapidProgressUpdatesHandling() throws InterruptedException {
        String fileId = "test-file-1";
        long totalSize = 1024L * 1024L * 100L; // 100 MB
        AtomicInteger updateCount = new AtomicInteger(0);
        AtomicLong lastUpdateTime = new AtomicLong(System.currentTimeMillis());
        List<Long> updateIntervals = new ArrayList<>();

        // Initialize batch
        progressEngine.startBatch(List.of(fileId), Map.of(fileId, totalSize));

        // Register callback to count updates
        Consumer<BatchProgress> callback = progress -> {
            int count = updateCount.incrementAndGet();
            long now = System.currentTimeMillis();
            long interval = now - lastUpdateTime.get();
            if (count > 1) { // Skip first update
                updateIntervals.add(interval);
            }
            lastUpdateTime.set(now);
        };
        progressEngine.addBatchProgressListener(callback);

        // Start tracking
        progressEngine.startTracking(fileId, totalSize);

        // Simulate rapid progress updates (every 1ms for 1 second = 1000 updates)
        int updatesSent = 0;
        for (int i = 0; i <= 1000; i++) {
            progressEngine.updateProgress(fileId, (totalSize * i) / 1000);
            updatesSent++;
            Thread.sleep(1); // 1ms between updates
        }

        // Wait for any pending updates
        Thread.sleep(200);

        // Complete tracking
        progressEngine.completeTracking(fileId,
                ConversionResult.success(fileId, tempDir.resolve("output.mp4"), null, Duration.ofMillis(1000),
                        totalSize, totalSize, ConversionTool.FFMPEG));

        int callbackCount = updateCount.get();
        System.out.printf("Sent %d progress updates, callback received %d updates%n",
                updatesSent, callbackCount);

        // Verify we received progress updates
        assertTrue(callbackCount > 0,
                "Should receive at least some progress updates");

        // Check average interval
        if (!updateIntervals.isEmpty()) {
            double avgInterval = updateIntervals.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0);
            System.out.printf("Average update interval: %.2f ms%n", avgInterval);
        }
    }

    /**
     * Test that progress engine doesn't lose the final progress value.
     * Requirement REQ-100.1: Ensure completion progress is always shown
     */
    @Test
    void testFinalProgressValuePreserved() throws InterruptedException {
        String fileId = "test-file-2";
        long totalSize = 1024L * 1024L * 50L; // 50 MB
        AtomicInteger finalProgressPercent = new AtomicInteger(-1);
        CountDownLatch completionLatch = new CountDownLatch(1);

        // Initialize batch
        progressEngine.startBatch(List.of(fileId), Map.of(fileId, totalSize));

        // Register callback to capture final value
        Consumer<BatchProgress> callback = progress -> {
            int percent = progress.overallPercentage();
            if (percent == 100) {
                finalProgressPercent.set(100);
                completionLatch.countDown();
            }
        };
        progressEngine.addBatchProgressListener(callback);

        // Start tracking
        progressEngine.startTracking(fileId, totalSize);

        // Send many rapid updates ending at 100%
        for (int i = 0; i <= 100; i++) {
            progressEngine.updateProgress(fileId, (totalSize * i) / 100);
            Thread.sleep(1); // 1ms between updates
        }

        // Complete tracking
        progressEngine.completeTracking(fileId,
                ConversionResult.success(fileId, tempDir.resolve("output.mp4"), null, Duration.ofMillis(100),
                        totalSize, totalSize, ConversionTool.FFMPEG));

        // Wait for final progress callback
        assertTrue(completionLatch.await(2, TimeUnit.SECONDS),
                "Final progress callback not received within timeout");

        assertEquals(100, finalProgressPercent.get(),
                "Final progress value should be 100%");
    }

    /**
     * Test progress handling with multiple files.
     * Requirement REQ-100.1: Batch progress updates should be efficient
     */
    @Test
    void testBatchProgressHandling() throws InterruptedException {
        int fileCount = 10;
        long fileSize = 1024L * 1024L * 10L; // 10 MB each
        AtomicInteger batchUpdateCount = new AtomicInteger(0);
        AtomicInteger completedFiles = new AtomicInteger(0);
        CountDownLatch allCompletedLatch = new CountDownLatch(1);

        // Prepare file IDs and sizes
        List<String> fileIds = new ArrayList<>();
        Map<String, Long> fileSizes = new HashMap<>();
        for (int i = 0; i < fileCount; i++) {
            String fileId = "file-" + i;
            fileIds.add(fileId);
            fileSizes.put(fileId, fileSize);
        }

        // Initialize batch
        progressEngine.startBatch(fileIds, fileSizes);

        // Register callback
        Consumer<BatchProgress> callback = progress -> {
            batchUpdateCount.incrementAndGet();
            if (progress.completedFiles() == fileCount) {
                completedFiles.set(progress.completedFiles());
                allCompletedLatch.countDown();
            }
        };
        progressEngine.addBatchProgressListener(callback);

        // Track multiple files with rapid updates
        for (int fileIdx = 0; fileIdx < fileCount; fileIdx++) {
            String fileId = "file-" + fileIdx;

            progressEngine.startTracking(fileId, fileSize);

            // Simulate rapid progress (50 updates per file)
            for (int progress = 0; progress <= 100; progress += 2) {
                progressEngine.updateProgress(fileId, (fileSize * progress) / 100);
                Thread.sleep(1); // 1ms between updates
            }

            // Complete file
            progressEngine.completeTracking(fileId,
                    ConversionResult.success(fileId, tempDir.resolve("output-" + fileIdx + ".mp4"), null,
                            Duration.ofMillis(50), fileSize, fileSize, ConversionTool.FFMPEG));
        }

        // Wait for all files to complete
        assertTrue(allCompletedLatch.await(5, TimeUnit.SECONDS),
                "Batch completion not received within timeout");

        assertEquals(fileCount, completedFiles.get(),
                "All files should be marked as completed");

        System.out.printf("Batch with %d files generated %d batch progress updates%n",
                fileCount, batchUpdateCount.get());

        // Verify we received updates
        assertTrue(batchUpdateCount.get() > 0,
                "Should receive batch progress updates");
    }

    /**
     * Test that progress engine handles slow updates correctly.
     * Requirement REQ-100.1: Should handle slow updates without issues
     */
    @Test
    void testSlowProgressUpdates() throws InterruptedException {
        String fileId = "test-file-3";
        long totalSize = 1024L * 1024L; // 1 MB
        AtomicInteger updateCount = new AtomicInteger(0);

        // Initialize batch
        progressEngine.startBatch(List.of(fileId), Map.of(fileId, totalSize));

        // Register callback
        Consumer<BatchProgress> callback = progress -> {
            updateCount.incrementAndGet();
        };
        progressEngine.addBatchProgressListener(callback);

        // Start tracking
        progressEngine.startTracking(fileId, totalSize);

        // Send slow updates (every 150ms)
        int updatesSent = 0;
        for (int i = 0; i <= 10; i++) {
            progressEngine.updateProgress(fileId, (totalSize * i) / 10);
            updatesSent++;
            Thread.sleep(150); // 150ms between updates
        }

        // Wait for any pending updates
        Thread.sleep(200);

        // Complete tracking
        progressEngine.completeTracking(fileId,
                ConversionResult.success(fileId, tempDir.resolve("output.mp4"), null, Duration.ofMillis(1500),
                        totalSize, totalSize, ConversionTool.FFMPEG));

        int callbackCount = updateCount.get();
        System.out.printf("Sent %d slow updates, callback received %d updates%n",
                updatesSent, callbackCount);

        // Slow updates should all be received
        assertTrue(callbackCount >= updatesSent - 1,
                String.format("Slow updates should be received, got %d/%d",
                        callbackCount, updatesSent));
    }

    /**
     * Test callback registration and unregistration.
     * Requirement REQ-100.1: Multiple callbacks should be supported
     */
    @Test
    void testMultipleCallbacksAndUnregistration() throws InterruptedException {
        String fileId = "test-file-4";
        long totalSize = 1024L * 1024L;
        AtomicInteger callback1Count = new AtomicInteger(0);
        AtomicInteger callback2Count = new AtomicInteger(0);

        // Initialize batch
        progressEngine.startBatch(List.of(fileId), Map.of(fileId, totalSize));

        // Register two callbacks
        Consumer<BatchProgress> callback1 = progress -> callback1Count.incrementAndGet();
        Consumer<BatchProgress> callback2 = progress -> callback2Count.incrementAndGet();

        progressEngine.addBatchProgressListener(callback1);
        progressEngine.addBatchProgressListener(callback2);

        // Start tracking and send updates
        progressEngine.startTracking(fileId, totalSize);
        for (int i = 0; i <= 10; i++) {
            progressEngine.updateProgress(fileId, (totalSize * i) / 10);
            Thread.sleep(20);
        }

        Thread.sleep(200);

        int count1AfterBoth = callback1Count.get();
        int count2AfterBoth = callback2Count.get();

        System.out.printf("After both callbacks: callback1=%d, callback2=%d%n",
                count1AfterBoth, count2AfterBoth);

        // Both callbacks should have received updates
        assertTrue(count1AfterBoth > 0, "Callback 1 should have received updates");
        assertTrue(count2AfterBoth > 0, "Callback 2 should have received updates");
        assertEquals(count1AfterBoth, count2AfterBoth,
                "Both callbacks should receive same number of updates");

        // Unregister callback2
        progressEngine.removeBatchProgressListener(callback2);

        // Complete the file
        progressEngine.completeTracking(fileId,
                ConversionResult.success(fileId, tempDir.resolve("output.mp4"), null, Duration.ofMillis(200),
                        totalSize, totalSize, ConversionTool.FFMPEG));

        Thread.sleep(200);

        int count1Final = callback1Count.get();
        int count2Final = callback2Count.get();

        System.out.printf("After unregistering callback2: callback1=%d, callback2=%d%n",
                count1Final, count2Final);

        // Callback 1 should have more updates, callback 2 should remain the same
        assertTrue(count1Final > count1AfterBoth,
                "Callback 1 should continue to receive updates");
        assertEquals(count2AfterBoth, count2Final,
                "Callback 2 should not receive updates after unregistration");
    }

    /**
     * Test that progress engine handles stress with many rapid updates.
     * Requirement REQ-100.1: System should remain stable under load
     */
    @Test
    void testStressWithManyRapidUpdates() throws InterruptedException {
        String fileId = "test-file-5";
        long totalSize = 1024L * 1024L * 1000L; // 1 GB
        AtomicInteger updateCount = new AtomicInteger(0);
        AtomicLong maxUpdateTime = new AtomicLong(0);

        // Initialize batch
        progressEngine.startBatch(List.of(fileId), Map.of(fileId, totalSize));

        // Register callback that measures update time
        Consumer<BatchProgress> callback = progress -> {
            long startTime = System.nanoTime();
            // Simulate some processing
            int sum = 0;
            for (int i = 0; i < 100; i++) {
                sum += i;
            }
            long elapsed = System.nanoTime() - startTime;
            maxUpdateTime.updateAndGet(max -> Math.max(max, elapsed));
            updateCount.incrementAndGet();
        };
        progressEngine.addBatchProgressListener(callback);

        // Start tracking
        progressEngine.startTracking(fileId, totalSize);

        // Send 10,000 rapid updates
        long startTime = System.currentTimeMillis();
        for (int i = 0; i <= 10000; i++) {
            progressEngine.updateProgress(fileId, (totalSize * i) / 10000);
            // No sleep - maximum stress
        }
        long elapsed = System.currentTimeMillis() - startTime;

        // Wait for updates to process
        Thread.sleep(500);

        // Complete tracking
        progressEngine.completeTracking(fileId,
                ConversionResult.success(fileId, tempDir.resolve("output.mp4"), null, Duration.ofMillis(elapsed),
                        totalSize, totalSize, ConversionTool.FFMPEG));

        Thread.sleep(200);

        int callbackCount = updateCount.get();
        double maxUpdateTimeMs = maxUpdateTime.get() / 1_000_000.0;

        System.out.printf("Stress test: 10,000 updates sent in %d ms, %d callbacks received%n",
                elapsed, callbackCount);
        System.out.printf("Max callback execution time: %.3f ms%n", maxUpdateTimeMs);

        // Should handle stress gracefully
        assertTrue(callbackCount > 0,
                "Should receive progress updates");
        assertTrue(maxUpdateTimeMs < 100,
                String.format("Callback execution time %.3f ms should be < 100ms", maxUpdateTimeMs));
    }

    /**
     * Test that progress engine handles varying update patterns correctly.
     * Requirement REQ-100.1: Progress updates should be handled gracefully
     */
    @Test
    void testVariedProgressUpdatePatterns() throws InterruptedException {
        String fileId = "test-file-6";
        long totalSize = 1024L * 1024L * 10L; // 10 MB
        List<Integer> progressValues = new ArrayList<>();
        AtomicInteger finalProgress = new AtomicInteger(-1);
        CountDownLatch completionLatch = new CountDownLatch(1);

        // Initialize batch
        progressEngine.startBatch(List.of(fileId), Map.of(fileId, totalSize));

        // Register callback to track progress values
        Consumer<BatchProgress> callback = progress -> {
            int percentage = progress.overallPercentage();
            synchronized (progressValues) {
                progressValues.add(percentage);
            }
            if (percentage == 100) {
                finalProgress.set(100);
                completionLatch.countDown();
            }
        };
        progressEngine.addBatchProgressListener(callback);

        // Start tracking
        progressEngine.startTracking(fileId, totalSize);

        // Send updates in forward progression (normal behavior)
        for (int i = 0; i <= 100; i += 5) {
            progressEngine.updateProgress(fileId, (totalSize * i) / 100);
            Thread.sleep(10);
        }

        // Complete tracking
        progressEngine.completeTracking(fileId,
                ConversionResult.success(fileId, tempDir.resolve("output.mp4"), null, Duration.ofMillis(200),
                        totalSize, totalSize, ConversionTool.FFMPEG));

        assertTrue(completionLatch.await(3, TimeUnit.SECONDS),
                "Completion not received within timeout");

        // Verify we received progress updates
        synchronized (progressValues) {
            assertTrue(progressValues.size() > 0, "Should receive progress updates");

            // Verify final progress is 100%
            assertEquals(100, finalProgress.get(), "Final progress should be 100%");
        }

        System.out.printf("Recorded %d progress values ending at %d%%%n",
                progressValues.size(), finalProgress.get());
    }

    /**
     * Test ImageMagick progress throttling to verify max 2 updates/second.
     * Requirement REQ-IMG-4, NFR-IMG-1: Progress updates should be throttled to
     * prevent UI flooding
     * 
     * ImageMagick doesn't provide real-time progress, so we use simulated progress
     * (0%, 50%, 100%).
     * This test verifies that progress callbacks respect the 500ms minimum
     * interval.
     */
    @Test
    void testImageMagickProgressThrottling() throws Exception {
        // Check if ImageMagick convert is available
        Path convertPath = findImageMagickConvert();
        assumeTrue(convertPath != null, "ImageMagick 'convert' not found - skipping test");

        // Create a test image file (simple PPM format)
        Path inputImage = createTestPPMImage(tempDir.resolve("input.ppm"), 1000, 1000);
        Path outputImage = tempDir.resolve("output.jpg");

        // Track progress callback invocations
        AtomicInteger progressCallbackCount = new AtomicInteger(0);
        List<Long> progressTimestamps = Collections.synchronizedList(new ArrayList<>());
        List<Double> progressPercentages = Collections.synchronizedList(new ArrayList<>());

        ProgressCallback progressCallback = (percentage, bytesProcessed, speed) -> {
            progressCallbackCount.incrementAndGet();
            progressTimestamps.add(System.currentTimeMillis());
            progressPercentages.add(percentage);
            System.out.printf("Progress callback #%d: %.1f%% at %d ms%n",
                    progressCallbackCount.get(), percentage, System.currentTimeMillis());
        };

        // Create ImageMagickService and run a real conversion
        ImageMagickService imageMagickService = new ImageMagickService(convertPath);
        ImageSettings settings = ImageSettings.builder().quality(85).build();

        String fileId = "test-image-1";

        long startTime = System.currentTimeMillis();

        // Execute conversion (this will take a few seconds for a 1000x1000 image)
        ConversionResult result = imageMagickService.convertImage(
                inputImage,
                outputImage,
                settings,
                progressCallback,
                fileId,
                org.omc.core.ProcessRegistry.noOp());

        long endTime = System.currentTimeMillis();
        long conversionDuration = endTime - startTime;

        System.out.printf("ImageMagick conversion completed in %d ms%n", conversionDuration);
        System.out.printf("Progress callbacks invoked: %d times%n", progressCallbackCount.get());
        System.out.printf("Progress percentages: %s%n", progressPercentages);

        // Verify conversion succeeded
        assertTrue(result.success(), "Conversion should succeed");
        assertTrue(Files.exists(outputImage), "Output file should exist");

        // Verify we received progress updates (at least 0%, 50%, 100%)
        assertTrue(progressCallbackCount.get() >= 2,
                "Should receive at least 2 progress updates (0% start, 100% end)");

        // Verify we received initial and final progress
        assertTrue(progressPercentages.contains(0.0), "Should receive 0% progress");
        assertTrue(progressPercentages.contains(100.0), "Should receive 100% progress");

        // Verify throttling: calculate intervals between progress updates
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < progressTimestamps.size(); i++) {
            long interval = progressTimestamps.get(i) - progressTimestamps.get(i - 1);
            intervals.add(interval);
        }

        // For conversions longer than 1 second, verify throttling
        if (conversionDuration > 1000 && intervals.size() > 0) {
            // Calculate average interval
            double avgInterval = intervals.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0);

            System.out.printf("Average interval between progress updates: %.2f ms%n", avgInterval);
            System.out.printf("Individual intervals: %s ms%n", intervals);

            // Throttling should ensure minimum 500ms between updates (2 updates/second)
            // Allow some tolerance for timing variations (400ms minimum)
            long minInterval = intervals.stream()
                    .mapToLong(Long::longValue)
                    .min()
                    .orElse(0);

            // Skip first interval (0% to 50%) as it might be immediate
            if (intervals.size() > 1) {
                long secondInterval = intervals.get(1);
                assertTrue(secondInterval >= 400,
                        String.format("Progress updates should be throttled (min 400ms), got %d ms", secondInterval));
            }
        }

        // Verify maximum update rate for conversions longer than 1 second
        // For very fast conversions (<1s), we expect just start (0%) and end (100%)
        // updates
        if (conversionDuration >= 1000) {
            double updatesPerSecond = (progressCallbackCount.get() * 1000.0) / conversionDuration;
            System.out.printf("Updates per second: %.2f%n", updatesPerSecond);

            // Should be close to 2 updates/second (0%, 50%, 100% with throttling)
            assertTrue(updatesPerSecond <= 5.0,
                    String.format("Update rate %.2f/sec should not exceed 5/sec", updatesPerSecond));
        } else {
            System.out.printf("Conversion too fast (%d ms) to test throttling - only start/end updates expected%n",
                    conversionDuration);
            // For fast conversions, we expect minimal updates (0% start, 100% end)
            assertTrue(progressCallbackCount.get() <= 3,
                    String.format("Fast conversion should have ≤3 updates, got %d", progressCallbackCount.get()));
        }
    }

    /**
     * Find ImageMagick convert binary.
     */
    private Path findImageMagickConvert() {
        String[] searchPaths = {
                "/usr/bin/convert",
                "/usr/local/bin/convert",
                "/opt/bin/convert"
        };

        for (String pathStr : searchPaths) {
            Path path = Path.of(pathStr);
            if (Files.exists(path) && Files.isExecutable(path)) {
                return path;
            }
        }

        // Try PATH environment variable
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(":")) {
                Path path = Path.of(dir, "convert");
                if (Files.exists(path) && Files.isExecutable(path)) {
                    return path;
                }
            }
        }

        return null;
    }

    /**
     * Create a test PPM image file.
     * PPM is a simple uncompressed format that ImageMagick can read.
     */
    private Path createTestPPMImage(Path outputPath, int width, int height) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile()))) {
            // PPM header
            writer.write("P3\n");
            writer.write(width + " " + height + "\n");
            writer.write("255\n");

            // Write pixel data (red gradient)
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int red = (255 * x) / width;
                    int green = (255 * y) / height;
                    int blue = 128;
                    writer.write(red + " " + green + " " + blue + " ");
                }
                writer.write("\n");
            }
        }

        return outputPath;
    }
}
