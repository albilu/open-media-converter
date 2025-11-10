package org.omc.performance;

import org.omc.controller.FileManager;
import org.omc.core.ToolManager;
import org.omc.core.ValidationEngine;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionStatus;
import org.omc.model.ConversionTool;
import org.omc.model.FileFormat;
import org.omc.model.ValidationResult;
import org.omc.service.FFmpegService;
import org.omc.service.FileHandler;
import org.omc.service.ImageMagickService;
import org.omc.service.LibreOfficeService;
import org.omc.service.PandocService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Performance tests for file list operations with large numbers of files.
 * 
 * Tests REQ-100.1: UI responsiveness with 1000+ files
 * Tests REQ-100.2: Resource usage (memory and CPU)
 * Tests NFR-IMG-1: Tool selection performance (<1ms per selection)
 */
class FileListPerformanceTest {

    @TempDir
    Path tempDir;

    private FileManager fileManager;
    private FileHandler fileHandler;
    private ValidationEngine validationEngine;
    private ToolManager toolManager;

    // Performance thresholds
    private static final int LARGE_FILE_COUNT = 1000;
    private static final int VERY_LARGE_FILE_COUNT = 5000;
    private static final long ADD_FILES_THRESHOLD_MS = 2000; // 2 seconds for 1000 files
    private static final long UPDATE_FILE_THRESHOLD_MS = 100; // 100ms per update
    private static final long REMOVE_FILES_THRESHOLD_MS = 1000; // 1 second for 1000 files
    private static final long GET_ALL_FILES_THRESHOLD_MS = 50; // 50ms to retrieve all files
    private static final long TOOL_SELECTION_THRESHOLD_MICROS = 1000; // 1ms = 1000 microseconds

    @BeforeEach
    void setUp() throws Exception {
        // Mock dependencies
        fileHandler = mock(FileHandler.class);
        validationEngine = mock(ValidationEngine.class);

        // Configure mocks to allow file operations
        when(validationEngine.validateFile(any(Path.class)))
                .thenReturn(ValidationResult.success());
        when(fileHandler.detectFormat(any(Path.class)))
                .thenReturn(FileFormat.MP4);
        when(fileHandler.getFileSize(any(Path.class)))
                .thenReturn(1000L);

        // Create FileManager with mocked dependencies
        fileManager = new FileManager(fileHandler, validationEngine);

        // Create ToolManager with mocked services for tool selection tests
        FFmpegService ffmpegService = mock(FFmpegService.class);
        PandocService pandocService = mock(PandocService.class);
        LibreOfficeService libreOfficeService = mock(LibreOfficeService.class);
        ImageMagickService imageMagickService = mock(ImageMagickService.class);

        toolManager = new ToolManager(ffmpegService, pandocService, libreOfficeService, imageMagickService);
    }

    /**
     * Test adding 1000 files to the file manager.
     * Requirement REQ-100.1: UI should remain responsive
     */
    @Test
    void testAdd1000Files() throws IOException {
        // Create 1000 test files
        List<Path> testFiles = createTestFiles(LARGE_FILE_COUNT);

        // Measure time to add all files
        long startTime = System.currentTimeMillis();

        // Add files in batches (simulating real usage)
        for (Path file : testFiles) {
            fileManager.addFiles(List.of(file));
        }

        long duration = System.currentTimeMillis() - startTime;

        // Verify all files were added
        assertEquals(LARGE_FILE_COUNT, fileManager.getFiles().size());

        // Check performance threshold
        assertTrue(duration < ADD_FILES_THRESHOLD_MS,
                String.format("Adding %d files took %dms, expected < %dms",
                        LARGE_FILE_COUNT, duration, ADD_FILES_THRESHOLD_MS));

        System.out.printf("Performance: Added %d files in %dms (%.2f files/sec)%n",
                LARGE_FILE_COUNT, duration, (LARGE_FILE_COUNT * 1000.0) / duration);
    }

    /**
     * Test adding 5000 files to stress test the system.
     */
    @Test
    void testAdd5000Files() throws IOException {
        List<Path> testFiles = createTestFiles(VERY_LARGE_FILE_COUNT);

        long startTime = System.currentTimeMillis();

        for (Path file : testFiles) {
            fileManager.addFiles(List.of(file));
        }

        long duration = System.currentTimeMillis() - startTime;

        assertEquals(VERY_LARGE_FILE_COUNT, fileManager.getFiles().size());

        System.out.printf("Stress test: Added %d files in %dms (%.2f files/sec)%n",
                VERY_LARGE_FILE_COUNT, duration, (VERY_LARGE_FILE_COUNT * 1000.0) / duration);
    }

    /**
     * Test updating file status for 1000 files.
     * Requirement REQ-100.1: Updates should be fast
     */
    @Test
    void testUpdateStatusFor1000Files() throws IOException {
        // Add 1000 files
        List<Path> testFiles = createTestFiles(LARGE_FILE_COUNT);
        for (Path file : testFiles) {
            fileManager.addFiles(List.of(file));
        }

        List<ConversionFile> allFiles = fileManager.getFiles();
        long totalDuration = 0;

        // Update status for each file and measure individual update time
        // Note: ConversionFile is immutable, so we need to create new instances
        for (ConversionFile file : allFiles) {
            long startTime = System.nanoTime();
            ConversionFile updated = file.withStatus(ConversionStatus.IN_PROGRESS)
                    .withProgress(50);
            fileManager.updateFile(updated);
            long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to ms
            totalDuration += duration;
        }

        long averageUpdateTime = totalDuration / LARGE_FILE_COUNT;

        System.out.printf("Performance: Average file update time: %dms for %d files%n",
                averageUpdateTime, LARGE_FILE_COUNT);

        // Each individual update should be very fast
        assertTrue(averageUpdateTime < UPDATE_FILE_THRESHOLD_MS,
                String.format("Average update time %dms exceeded threshold %dms",
                        averageUpdateTime, UPDATE_FILE_THRESHOLD_MS));
    }

    /**
     * Test removing all files from a large list.
     */
    @Test
    void testRemove1000Files() throws IOException {
        // Add 1000 files
        List<Path> testFiles = createTestFiles(LARGE_FILE_COUNT);
        for (Path file : testFiles) {
            fileManager.addFiles(List.of(file));
        }

        assertEquals(LARGE_FILE_COUNT, fileManager.getFiles().size());

        // Measure time to remove all files
        long startTime = System.currentTimeMillis();
        fileManager.clearFiles();
        long duration = System.currentTimeMillis() - startTime;

        assertEquals(0, fileManager.getFiles().size());

        assertTrue(duration < REMOVE_FILES_THRESHOLD_MS,
                String.format("Removing %d files took %dms, expected < %dms",
                        LARGE_FILE_COUNT, duration, REMOVE_FILES_THRESHOLD_MS));

        System.out.printf("Performance: Removed %d files in %dms%n",
                LARGE_FILE_COUNT, duration);
    }

    /**
     * Test retrieving all files from a large collection.
     * Requirement REQ-100.1: File retrieval should be fast
     */
    @Test
    void testGetAllFilesPerformance() throws IOException {
        // Add 1000 files
        List<Path> testFiles = createTestFiles(LARGE_FILE_COUNT);
        for (Path file : testFiles) {
            fileManager.addFiles(List.of(file));
        }

        // Measure time to retrieve all files multiple times
        long totalDuration = 0;
        int iterations = 100;

        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            List<ConversionFile> files = fileManager.getFiles();
            long duration = System.nanoTime() - startTime;
            totalDuration += duration;

            assertEquals(LARGE_FILE_COUNT, files.size());
        }

        long averageDuration = (totalDuration / iterations) / 1_000_000; // Convert to ms

        assertTrue(averageDuration < GET_ALL_FILES_THRESHOLD_MS,
                String.format("Average getFiles() took %dms, expected < %dms",
                        averageDuration, GET_ALL_FILES_THRESHOLD_MS));

        System.out.printf("Performance: getFiles() averaged %dms over %d iterations%n",
                averageDuration, iterations);
    }

    /**
     * Test file lookup by ID performance.
     */
    @Test
    void testFileByIdLookupPerformance() throws IOException {
        // Add 1000 files
        List<Path> testFiles = createTestFiles(LARGE_FILE_COUNT);
        for (Path file : testFiles) {
            fileManager.addFiles(List.of(file));
        }

        // Get file IDs for testing
        List<ConversionFile> allFiles = fileManager.getFiles();
        String firstId = allFiles.get(0).id();
        String middleId = allFiles.get(LARGE_FILE_COUNT / 2).id();
        String lastId = allFiles.get(LARGE_FILE_COUNT - 1).id();

        // Warm up
        fileManager.getFile(firstId);

        // Measure lookup times for middle element
        long startTime = System.nanoTime();
        var found = fileManager.getFile(middleId);
        long lookupTime = (System.nanoTime() - startTime) / 1_000; // microseconds

        assertTrue(found.isPresent());
        assertEquals(middleId, found.get().id());

        // Lookup should be very fast (< 1ms even for middle element)
        assertTrue(lookupTime < 1000, // 1ms in microseconds
                String.format("File lookup took %d microseconds, expected < 1000", lookupTime));

        System.out.printf("Performance: File lookup by ID took %d microseconds%n", lookupTime);
    }

    /**
     * Test memory usage with large file collections.
     * Requirement REQ-100.2: Memory efficiency
     */
    @Test
    void testMemoryUsageWith1000Files() throws IOException {
        Runtime runtime = Runtime.getRuntime();

        // Force garbage collection and measure baseline
        System.gc();
        Thread.yield();
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();

        // Add 1000 files
        List<Path> testFiles = createTestFiles(LARGE_FILE_COUNT);
        for (Path file : testFiles) {
            fileManager.addFiles(List.of(file));
        }

        // Force garbage collection and measure after adding files
        System.gc();
        Thread.yield();
        long afterAddMemory = runtime.totalMemory() - runtime.freeMemory();

        long memoryUsed = afterAddMemory - baselineMemory;
        long memoryPerFile = memoryUsed / LARGE_FILE_COUNT;

        System.out.printf("Memory: %d files use %d KB total (%.2f KB per file)%n",
                LARGE_FILE_COUNT, memoryUsed / 1024, memoryPerFile / 1024.0);

        // Each file should use reasonable memory (< 10KB per file structure)
        // This is a rough estimate including all overhead
        assertTrue(memoryPerFile < 10 * 1024,
                String.format("Memory per file: %d bytes, expected < 10 KB", memoryPerFile));
    }

    /**
     * Test concurrent access to file list (simulating UI + conversion engine
     * access).
     */
    @Test
    void testConcurrentAccess() throws IOException, InterruptedException {
        // Add initial files
        List<Path> testFiles = createTestFiles(100);
        for (Path file : testFiles) {
            fileManager.addFiles(List.of(file));
        }

        List<Thread> threads = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();

        // Thread 1: Continuously read files
        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < 1000; i++) {
                    fileManager.getFiles();
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                exceptions.add(e);
            }
        });

        // Thread 2: Update file statuses
        Thread updater = new Thread(() -> {
            try {
                List<ConversionFile> files = fileManager.getFiles();
                for (int i = 0; i < 100; i++) {
                    for (ConversionFile file : files) {
                        ConversionFile updated = file.withProgress(i);
                        fileManager.updateFile(updated);
                    }
                    Thread.sleep(10);
                }
            } catch (Exception e) {
                exceptions.add(e);
            }
        });

        threads.add(reader);
        threads.add(updater);

        // Start all threads
        for (Thread t : threads) {
            t.start();
        }

        // Wait for completion
        for (Thread t : threads) {
            t.join();
        }

        // Check for exceptions
        assertTrue(exceptions.isEmpty(),
                "Concurrent access caused exceptions: " + exceptions);

        System.out.println("Concurrent access test completed successfully");
    }

    /**
     * Test tool selection performance for all format combinations.
     * Requirement NFR-IMG-1: Tool selection must complete in <1ms
     * Tests REQ-SEL-2: IMAGE category routing to ImageMagick
     */
    @Test
    void testToolSelection_Performance() throws Exception {
        // Test format combinations covering all categories
        List<FormatPair> testCases = new ArrayList<>();

        // VIDEO formats → FFMPEG
        testCases.add(new FormatPair(FileFormat.MP4, FileFormat.AVI, ConversionTool.FFMPEG));
        testCases.add(new FormatPair(FileFormat.MKV, FileFormat.MP4, ConversionTool.FFMPEG));
        testCases.add(new FormatPair(FileFormat.WEBM, FileFormat.MP4, ConversionTool.FFMPEG));
        testCases.add(new FormatPair(FileFormat.MOV, FileFormat.MP4, ConversionTool.FFMPEG));

        // AUDIO formats → FFMPEG
        testCases.add(new FormatPair(FileFormat.MP3, FileFormat.WAV, ConversionTool.FFMPEG));
        testCases.add(new FormatPair(FileFormat.FLAC, FileFormat.MP3, ConversionTool.FFMPEG));
        testCases.add(new FormatPair(FileFormat.OGG, FileFormat.MP3, ConversionTool.FFMPEG));
        testCases.add(new FormatPair(FileFormat.AAC, FileFormat.MP3, ConversionTool.FFMPEG));

        // IMAGE formats → IMAGEMAGICK (Requirement REQ-SEL-2)
        testCases.add(new FormatPair(FileFormat.PNG, FileFormat.JPEG, ConversionTool.IMAGEMAGICK));
        testCases.add(new FormatPair(FileFormat.JPEG, FileFormat.PNG, ConversionTool.IMAGEMAGICK));
        testCases.add(new FormatPair(FileFormat.GIF, FileFormat.PNG, ConversionTool.IMAGEMAGICK));
        testCases.add(new FormatPair(FileFormat.BMP, FileFormat.PNG, ConversionTool.IMAGEMAGICK));
        testCases.add(new FormatPair(FileFormat.TIFF, FileFormat.JPEG, ConversionTool.IMAGEMAGICK));
        testCases.add(new FormatPair(FileFormat.WEBP, FileFormat.PNG, ConversionTool.IMAGEMAGICK));

        // DOCUMENT formats → PANDOC
        testCases.add(new FormatPair(FileFormat.MARKDOWN, FileFormat.HTML, ConversionTool.PANDOC));
        testCases.add(new FormatPair(FileFormat.HTML, FileFormat.PDF, ConversionTool.PANDOC));
        testCases.add(new FormatPair(FileFormat.RST, FileFormat.HTML, ConversionTool.PANDOC));
        testCases.add(new FormatPair(FileFormat.ORG, FileFormat.HTML, ConversionTool.PANDOC));
        testCases.add(new FormatPair(FileFormat.TEX, FileFormat.PDF, ConversionTool.PANDOC));

        // DOCUMENT formats → LIBREOFFICE
        testCases.add(new FormatPair(FileFormat.DOCX, FileFormat.PDF, ConversionTool.LIBREOFFICE));
        testCases.add(new FormatPair(FileFormat.DOC, FileFormat.PDF, ConversionTool.LIBREOFFICE));
        testCases.add(new FormatPair(FileFormat.XLS, FileFormat.PDF, ConversionTool.LIBREOFFICE));
        testCases.add(new FormatPair(FileFormat.PPT, FileFormat.PDF, ConversionTool.LIBREOFFICE));
        testCases.add(new FormatPair(FileFormat.ODT, FileFormat.PDF, ConversionTool.LIBREOFFICE));

        // Warm up JVM (run each test case once)
        for (FormatPair pair : testCases) {
            toolManager.selectTool(pair.input, pair.output);
        }

        // Measure performance for each format combination
        long totalTime = 0;
        int iterations = 1000;

        for (FormatPair pair : testCases) {
            long startTime = System.nanoTime();

            for (int i = 0; i < iterations; i++) {
                ConversionTool selected = toolManager.selectTool(pair.input, pair.output);
                // Verify correct tool is selected
                assertEquals(pair.expectedTool, selected,
                        String.format("Wrong tool selected for %s → %s", pair.input, pair.output));
            }

            long duration = System.nanoTime() - startTime;
            long avgMicros = (duration / iterations) / 1000; // Convert to microseconds
            totalTime += duration;

            // Each selection should be < 1ms (Requirement NFR-IMG-1)
            assertTrue(avgMicros < TOOL_SELECTION_THRESHOLD_MICROS,
                    String.format("Tool selection for %s → %s took %d μs, expected < %d μs",
                            pair.input, pair.output, avgMicros, TOOL_SELECTION_THRESHOLD_MICROS));
        }

        long avgTotalMicros = (totalTime / (testCases.size() * iterations)) / 1000;

        System.out.printf("Tool selection performance: %d format combinations tested%n", testCases.size());
        System.out.printf("Average selection time: %d microseconds (%.3f ms)%n",
                avgTotalMicros, avgTotalMicros / 1000.0);
        System.out.printf("Total selections: %d, Total time: %.2f ms%n",
                testCases.size() * iterations, totalTime / 1_000_000.0);

        // Verify no performance regression - average should be well under 1ms
        assertTrue(avgTotalMicros < TOOL_SELECTION_THRESHOLD_MICROS,
                String.format("Average tool selection took %d μs, expected < %d μs",
                        avgTotalMicros, TOOL_SELECTION_THRESHOLD_MICROS));
    }

    /**
     * Helper class to represent format conversion pairs for testing.
     */
    private static class FormatPair {
        final FileFormat input;
        final FileFormat output;
        final ConversionTool expectedTool;

        FormatPair(FileFormat input, FileFormat output, ConversionTool expectedTool) {
            this.input = input;
            this.output = output;
            this.expectedTool = expectedTool;
        }
    }

    /**
     * Creates test files in the temp directory.
     * 
     * @param count Number of files to create
     * @return List of created file paths
     */
    private List<Path> createTestFiles(int count) throws IOException {
        List<Path> files = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Path file = tempDir.resolve(String.format("test-file-%05d.mp4", i));
            // Create unique content for each file to avoid duplicate detection
            Files.writeString(file, "test content " + i + " - unique data");
            files.add(file);
        }

        return files;
    }
}
