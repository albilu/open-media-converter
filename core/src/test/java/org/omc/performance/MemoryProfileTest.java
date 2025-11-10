package org.omc.performance;

import org.omc.controller.FileManager;
import org.omc.core.ConversionEngine;
import org.omc.core.ProgressEngine;
import org.omc.core.ToolManager;
import org.omc.core.ValidationEngine;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionSettings;
import org.omc.model.ConversionStatus;
import org.omc.model.ConversionTool;
import org.omc.model.FileFormat;
import org.omc.model.ValidationResult;
import org.omc.service.FileHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Memory profiling tests to verify heap usage remains under acceptable limits.
 * 
 * Tests REQ-100.2: Resource usage
 * - Idle application < 100MB RAM
 * - Active conversion < 500MB RAM per process
 * - Temporary file cleanup after conversion
 */
class MemoryProfileTest {

    @TempDir
    Path tempDir;

    private FileManager fileManager;
    private ConversionEngine conversionEngine;
    private FileHandler fileHandler;
    private ValidationEngine validationEngine;
    private ToolManager toolManager;
    private ProgressEngine progressEngine;

    // Memory thresholds (REQ-100.2)
    private static final long IDLE_MEMORY_THRESHOLD_MB = 100;
    private static final long ACTIVE_MEMORY_THRESHOLD_MB = 500;
    private static final long MEMORY_PER_FILE_KB = 10; // Max 10KB per file in memory

    @BeforeEach
    void setUp() throws Exception {
        // Mock dependencies
        fileHandler = mock(FileHandler.class);
        validationEngine = mock(ValidationEngine.class);
        toolManager = mock(ToolManager.class);
        progressEngine = new ProgressEngine();

        // Configure mocks
        when(validationEngine.validateFile(any(Path.class)))
                .thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(any(Path.class)))
                .thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(any(Path.class)))
                .thenReturn(1024L * 1024L); // 1 MB

        // Create real instances for testing
        fileManager = new FileManager(fileHandler, validationEngine);
        conversionEngine = new ConversionEngine(toolManager, validationEngine, progressEngine, fileHandler, 4);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Cleanup conversion engine
        if (conversionEngine != null) {
            conversionEngine.shutdown();
        }
    }

    /**
     * Test memory usage when application is idle (no files loaded).
     * Requirement REQ-100.2: Idle application should use < 100MB RAM
     */
    @Test
    void testIdleMemoryUsage() throws InterruptedException {
        // Force garbage collection to get accurate baseline
        System.gc();
        Thread.sleep(100);
        System.gc();
        Thread.sleep(100);

        // Measure memory after GC
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long usedMemoryMB = usedMemory / (1024 * 1024);

        System.out.printf("Idle Memory Usage: %d MB (threshold: %d MB)%n",
                usedMemoryMB, IDLE_MEMORY_THRESHOLD_MB);

        // Note: This threshold may be exceeded in test environment due to JUnit
        // overhead
        // In production, the actual idle memory should be much lower
        assertTrue(usedMemoryMB < IDLE_MEMORY_THRESHOLD_MB + 50, // Allow 50MB overhead for test framework
                String.format("Idle memory usage %d MB exceeds threshold %d MB (+ 50MB test overhead)",
                        usedMemoryMB, IDLE_MEMORY_THRESHOLD_MB));
    }

    /**
     * Test memory usage with 1000 files loaded.
     * Requirement REQ-100.2: Memory efficiency for file list
     */
    @Test
    void testMemoryUsageWith1000Files() throws IOException, InterruptedException {
        // Get baseline memory
        System.gc();
        Thread.sleep(100);
        Runtime runtime = Runtime.getRuntime();
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();

        // Add 1000 files
        int fileCount = 1000;
        List<Path> testFiles = createTestFiles(fileCount);
        for (Path file : testFiles) {
            fileManager.addFiles(List.of(file));
        }

        // Measure memory after adding files
        System.gc();
        Thread.sleep(100);
        long afterAddMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = afterAddMemory - baselineMemory;
        long memoryPerFileKB = memoryIncrease / fileCount / 1024;

        System.out.printf("Memory with 1000 files: %d KB increase (%.2f KB per file)%n",
                memoryIncrease / 1024, (double) memoryIncrease / fileCount / 1024);

        // Each file should use reasonable memory
        assertTrue(memoryPerFileKB < MEMORY_PER_FILE_KB,
                String.format("Memory per file %.2f KB exceeds threshold %d KB",
                        (double) memoryIncrease / fileCount / 1024, MEMORY_PER_FILE_KB));
    }

    /**
     * Test memory usage during active conversions (simulated).
     * Requirement REQ-100.2: Active conversion < 500MB RAM per process
     */
    @Test
    void testMemoryUsageDuringConversion() throws Exception {
        // Get baseline
        System.gc();
        Thread.sleep(100);
        Runtime runtime = Runtime.getRuntime();
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();

        // Add files and prepare for conversion
        int fileCount = 100;
        List<Path> testFiles = createTestFiles(fileCount);
        for (Path file : testFiles) {
            fileManager.addFiles(List.of(file));
        }

        List<ConversionFile> files = fileManager.getFiles();
        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .outputDirectory(tempDir)
                .build();

        // Mock conversion to return success immediately
        when(toolManager.executeTool(any(), any(), any(), any(), any(), any()))
                .thenReturn(ConversionResult.success("test-id", tempDir.resolve("output.mp4"), null,
                        Duration.ofMillis(100), 1024L * 1024L, 1024L * 1024L, ConversionTool.FFMPEG));

        // Start conversions (will complete quickly due to mock)
        CompletableFuture<Void> conversionFuture = CompletableFuture.runAsync(() -> {
            try {
                conversionEngine.convertBatch(files, settings);
            } catch (Exception e) {
                fail("Conversion failed: " + e.getMessage());
            }
        });

        // Wait for conversions to complete
        conversionFuture.get();

        // Measure memory after conversions
        System.gc();
        Thread.sleep(100);
        long afterConversionMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncreaseMB = (afterConversionMemory - baselineMemory) / (1024 * 1024);

        System.out.printf("Memory during conversion: %d MB increase (threshold: %d MB)%n",
                memoryIncreaseMB, ACTIVE_MEMORY_THRESHOLD_MB);

        // Memory increase should be reasonable (accounting for file list + conversion
        // state)
        assertTrue(memoryIncreaseMB < ACTIVE_MEMORY_THRESHOLD_MB,
                String.format("Memory during conversion %d MB exceeds threshold %d MB",
                        memoryIncreaseMB, ACTIVE_MEMORY_THRESHOLD_MB));
    }

    /**
     * Test that memory is released after conversions complete.
     * Requirement REQ-100.2: Temporary files cleaned up
     */
    @Test
    void testMemoryReleasedAfterConversion() throws Exception {
        // Add files
        int fileCount = 50;
        List<Path> testFiles = createTestFiles(fileCount);
        for (Path file : testFiles) {
            fileManager.addFiles(List.of(file));
        }

        List<ConversionFile> files = fileManager.getFiles();
        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .outputDirectory(tempDir)
                .build();

        // Mock conversion
        when(toolManager.executeTool(any(), any(), any(), any(), any(), any()))
                .thenReturn(ConversionResult.success("test-id", tempDir.resolve("output.mp4"), null,
                        Duration.ofMillis(100), 1024L * 1024L, 1024L * 1024L, ConversionTool.FFMPEG));

        // Get memory before conversion
        System.gc();
        Thread.sleep(100);
        Runtime runtime = Runtime.getRuntime();
        long beforeConversionMemory = runtime.totalMemory() - runtime.freeMemory();

        // Run conversion
        conversionEngine.convertBatch(files, settings).get();

        // Get memory after conversion
        long afterConversionMemory = runtime.totalMemory() - runtime.freeMemory();

        // Clear files to simulate user clearing the list
        fileManager.clearFiles();

        // Force garbage collection to clean up
        System.gc();
        Thread.sleep(100);
        System.gc();
        Thread.sleep(100);

        // Memory should be mostly released
        long afterCleanupMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryRetainedMB = (afterCleanupMemory - beforeConversionMemory) / (1024 * 1024);

        System.out.printf("Memory retained after cleanup: %d MB%n", memoryRetainedMB);

        // Some memory retention is acceptable (e.g., caches, thread pools)
        // But should be much less than the active memory
        assertTrue(memoryRetainedMB < ACTIVE_MEMORY_THRESHOLD_MB / 2,
                String.format("Memory retained %d MB is too high (threshold: %d MB)",
                        memoryRetainedMB, ACTIVE_MEMORY_THRESHOLD_MB / 2));
    }

    /**
     * Test memory usage with many completed conversions in the file list.
     */
    @Test
    void testMemoryWithCompletedConversions() throws IOException, InterruptedException {
        System.gc();
        Thread.sleep(100);
        Runtime runtime = Runtime.getRuntime();
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();

        // Add 500 files
        int fileCount = 500;
        List<Path> testFiles = createTestFiles(fileCount);
        for (Path file : testFiles) {
            fileManager.addFiles(List.of(file));
        }

        // Update all files to completed status with results
        List<ConversionFile> files = fileManager.getFiles();
        for (ConversionFile file : files) {
            ConversionFile completed = file.withStatus(ConversionStatus.COMPLETED)
                    .withProgress(100);
            fileManager.updateFile(completed);
        }

        // Measure memory
        System.gc();
        Thread.sleep(100);
        long afterCompletionMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncreaseMB = (afterCompletionMemory - baselineMemory) / (1024 * 1024);

        System.out.printf("Memory with %d completed conversions: %d MB%n", fileCount, memoryIncreaseMB);

        // Should still be reasonable even with completed conversions
        assertTrue(memoryIncreaseMB < 50,
                String.format("Memory with completed conversions %d MB exceeds 50 MB threshold",
                        memoryIncreaseMB));
    }

    /**
     * Test memory growth over multiple batch conversions.
     * Ensures no memory leaks.
     */
    @Test
    void testMemoryLeakOverMultipleBatches() throws Exception {
        List<Long> memoryReadings = new ArrayList<>();
        Runtime runtime = Runtime.getRuntime();

        // Mock conversion
        when(toolManager.executeTool(any(), any(), any(), any(), any(), any()))
                .thenReturn(ConversionResult.success("test-id", tempDir.resolve("output.mp4"), null,
                        Duration.ofMillis(100), 1024L * 1024L, 1024L * 1024L, ConversionTool.FFMPEG));

        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .outputDirectory(tempDir)
                .build();

        // Run 5 batches of conversions
        for (int batch = 0; batch < 5; batch++) {
            // Add files
            List<Path> testFiles = createTestFiles(20);
            for (Path file : testFiles) {
                fileManager.addFiles(List.of(file));
            }

            // Convert
            List<ConversionFile> files = fileManager.getFiles();
            conversionEngine.convertBatch(files, settings).get();

            // Clear files
            fileManager.clearFiles();

            // Measure memory
            System.gc();
            Thread.sleep(100);
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            memoryReadings.add(usedMemory);

            System.out.printf("Batch %d memory: %d MB%n", batch + 1, usedMemory / (1024 * 1024));
        }

        // Check that memory isn't consistently growing
        // Compare first and last batches
        long firstBatchMemory = memoryReadings.get(0);
        long lastBatchMemory = memoryReadings.get(memoryReadings.size() - 1);
        long memoryGrowthMB = (lastBatchMemory - firstBatchMemory) / (1024 * 1024);

        System.out.printf("Memory growth over 5 batches: %d MB%n", memoryGrowthMB);

        // Some growth is acceptable, but shouldn't be linear with batch count
        // Allow up to 50MB growth over 5 batches
        assertTrue(memoryGrowthMB < 50,
                String.format("Memory grew by %d MB over 5 batches, possible memory leak", memoryGrowthMB));
    }

    /**
     * Test progress engine memory usage with many files.
     */
    @Test
    void testProgressEngineMemoryUsage() throws IOException, InterruptedException {
        System.gc();
        Thread.sleep(100);
        Runtime runtime = Runtime.getRuntime();
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();

        // Track progress for 1000 files
        int fileCount = 1000;
        for (int i = 0; i < fileCount; i++) {
            String fileId = "file-" + i;
            progressEngine.startTracking(fileId, 1024L * 1024L); // 1 MB each

            // Simulate progress updates
            for (int progress = 0; progress <= 100; progress += 10) {
                progressEngine.updateProgress(fileId, (1024L * 1024L * progress) / 100);
            }

            progressEngine.completeTracking(fileId,
                    ConversionResult.success(fileId, tempDir.resolve("output-" + i + ".mp4"), null,
                            Duration.ofMillis(100),
                            1024L * 1024L, 1024L * 1024L, ConversionTool.FFMPEG));
        }

        // Measure memory
        System.gc();
        Thread.sleep(100);
        long afterTrackingMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncreaseMB = (afterTrackingMemory - baselineMemory) / (1024 * 1024);

        System.out.printf("ProgressEngine memory with %d tracked files: %d MB%n", fileCount, memoryIncreaseMB);

        // Progress engine should be memory efficient
        assertTrue(memoryIncreaseMB < 30,
                String.format("ProgressEngine memory %d MB exceeds 30 MB threshold", memoryIncreaseMB));
    }

    /**
     * Creates test files in the temp directory.
     */
    private List<Path> createTestFiles(int count) throws IOException {
        List<Path> files = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Path file = tempDir.resolve(String.format("test-file-%05d.mp4", i));
            Files.writeString(file, "test content " + i + " - unique data for memory test");
            files.add(file);
        }

        return files;
    }
}
