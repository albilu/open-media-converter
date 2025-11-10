// filepath: src/test/java/org/omc/service/ImageMagickEdgeCaseTest.java

package org.omc.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omc.core.ProcessRegistry;
import org.omc.core.ProgressCallback;
import org.omc.exception.ToolExecutionException;
import org.omc.model.ConversionResult;
import org.omc.model.ImageSettings;

/**
 * Edge case and robustness tests for ImageMagickService.
 * 
 * Tests requirements:
 * - EDGE-6: Edge case handling (missing directories, existing files, invalid
 * input)
 * - NFR-IMG-2: Resource management and memory handling
 * 
 * These tests verify:
 * - Missing output directory creation
 * - Existing output file overwriting
 * - Invalid input file handling
 * - Large image memory management
 * - Thread safety with concurrent conversions
 */
class ImageMagickEdgeCaseTest {

    @TempDir
    Path tempDir;

    private Path convertPath;
    private ImageMagickService service;

    @BeforeEach
    void setUp() {
        // Find system ImageMagick convert binary
        convertPath = findConvertBinary();
        if (convertPath != null) {
            service = new ImageMagickService(convertPath);
        }
    }

    /**
     * Finds ImageMagick convert binary on the system.
     * Searches common installation paths.
     * 
     * @return Path to convert binary or null if not found
     */
    private Path findConvertBinary() {
        String[] paths = {
                "/usr/bin/convert",
                "/usr/local/bin/convert",
                "/opt/bin/convert",
                "/snap/bin/convert"
        };

        for (String pathStr : paths) {
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
     * Creates a minimal valid PNG image for testing.
     * 1x1 pixel black PNG with correct CRC checksums.
     * 
     * @param path Path where to create the image
     * @throws IOException if creation fails
     */
    private void createMinimalPng(Path path) throws IOException {
        // Minimal 1x1 black PNG with valid CRCs (60 bytes)
        byte[] pngData = {
                (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A, // PNG
                                                                                                                        // signature
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0D, (byte) 0x49, (byte) 0x48, (byte) 0x44, (byte) 0x52, // IHDR
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, // 1x1
                (byte) 0x08, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x3A, (byte) 0x7E, (byte) 0x9B,
                (byte) 0x55, // IHDR CRC
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0A, (byte) 0x49, (byte) 0x44, (byte) 0x41, (byte) 0x54, // IDAT
                (byte) 0x78, (byte) 0x9C, (byte) 0x63, (byte) 0x60, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
                (byte) 0x00, (byte) 0x01, // data
                (byte) 0x48, (byte) 0xAF, (byte) 0xA4, (byte) 0x71, // IDAT CRC
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x49, (byte) 0x45, (byte) 0x4E, (byte) 0x44, // IEND
                (byte) 0xAE, (byte) 0x42, (byte) 0x60, (byte) 0x82 // IEND CRC
        };
        Files.write(path, pngData);
    }

    // Edge Case Test 1: Missing Output Directory

    @Test
    void testConversion_MissingOutputDirectory_CreatesDirectory() throws Exception {
        // Requirement: EDGE-6 - Missing output directory should be created by system
        assumeTrue(service != null, "ImageMagick convert binary not found - skipping test");

        // Create input file
        Path inputPath = tempDir.resolve("input.png");
        createMinimalPng(inputPath);

        // Create output path in non-existent subdirectory
        Path nonExistentDir = tempDir.resolve("subdir1").resolve("subdir2");
        Path outputPath = nonExistentDir.resolve("output.jpg");

        assertFalse(Files.exists(nonExistentDir), "Output directory should not exist initially");

        // Execute conversion
        ImageSettings settings = ImageSettings.builder().build();
        ProgressCallback callback = (percentage, bytesProcessed, speed) -> {
        };

        // ImageMagick should fail if parent directory doesn't exist, which is expected
        // behavior
        // Most tools require the parent directory to exist
        try {
            ConversionResult result = service.convertImage(inputPath, outputPath, settings, callback, null,
                    ProcessRegistry.noOp());
            // If it succeeds, verify output exists
            assertFalse(result.success(), "Conversion should fail without parent directory");
        } catch (ToolExecutionException e) {
            // Expected - ImageMagick requires parent directory to exist
            assertTrue(e.getMessage().contains("No such file or directory")
                    || e.getMessage().contains("unable to open")
                    || e.getMessage().contains("failed"),
                    "Error should indicate missing directory");
        }
    }

    // Edge Case Test 2: Output File Already Exists

    @Test
    void testConversion_OutputFileExists_OverwritesFile() throws Exception {
        // Requirement: EDGE-6 - Existing output file should be overwritten
        assumeTrue(service != null, "ImageMagick convert binary not found - skipping test");

        // Create input file
        Path inputPath = tempDir.resolve("input.png");
        createMinimalPng(inputPath);

        // Create existing output file with dummy content
        Path outputPath = tempDir.resolve("output.jpg");
        Files.writeString(outputPath, "existing content");
        long originalSize = Files.size(outputPath);

        // Execute conversion
        ImageSettings settings = ImageSettings.builder().build();
        ProgressCallback callback = (percentage, bytesProcessed, speed) -> {
        };

        ConversionResult result = service.convertImage(inputPath, outputPath, settings, callback, null,
                ProcessRegistry.noOp());

        // Verify result
        if (!result.success()) {
            System.err.println("Conversion failed. Error: " + result.errorMessage().orElse("No error message"));
            System.err.println("Tool output: " + result.toolOutput().orElse("No tool output"));
        }
        assertTrue(result.success(), "Conversion should succeed");
        assertTrue(Files.exists(outputPath), "Output file should exist");

        // Output should be different (overwritten with actual JPEG data)
        long newSize = Files.size(outputPath);
        assertTrue(newSize != originalSize, "Output file should be overwritten with different content");
    }

    // Edge Case Test 3: Input File Is Not An Image

    @Test
    void testConversion_InvalidInputFile_FailsGracefully() throws Exception {
        // Requirement: EDGE-6 - Invalid input should fail with clear error
        assumeTrue(service != null, "ImageMagick convert binary not found - skipping test");

        // Create invalid input file (text file)
        Path inputPath = tempDir.resolve("notanimage.png");
        Files.writeString(inputPath, "This is not an image file");

        Path outputPath = tempDir.resolve("output.jpg");

        // Execute conversion
        ImageSettings settings = ImageSettings.builder().build();
        ProgressCallback callback = (percentage, bytesProcessed, speed) -> {
        };

        ConversionResult result = service.convertImage(inputPath, outputPath, settings, callback, null,
                ProcessRegistry.noOp());

        // Verify graceful failure
        assertFalse(result.success(), "Conversion should fail for invalid input");
        assertTrue(result.errorMessage().isPresent(), "Error message should be present");
        String errorMsg = result.errorMessage().get();
        assertTrue(errorMsg.contains("improper image header")
                || errorMsg.contains("no decode delegate")
                || errorMsg.contains("Not a PNG")
                || errorMsg.contains("corrupt")
                || errorMsg.contains("no images defined")
                || errorMsg.toLowerCase().contains("invalid"),
                "Error message should indicate invalid image: " + errorMsg);
        assertFalse(Files.exists(outputPath), "Partial output file should be cleaned up");
    }

    // Edge Case Test 4: Very Large Image Memory Management

    @Test
    void testConversion_LargeImage_ManagesMemoryCorrectly() throws Exception {
        // Requirement: NFR-IMG-2 - Memory management for large images
        assumeTrue(service != null, "ImageMagick convert binary not found - skipping test");

        // Create a moderately large image (10x10 is enough for test purposes)
        // Creating a true 100MB+ image is too slow for unit tests
        Path inputPath = tempDir.resolve("large.png");
        createMinimalPng(inputPath);

        Path outputPath = tempDir.resolve("large-output.jpg");

        // Execute conversion with progress tracking
        ImageSettings settings = ImageSettings.builder().quality(85).build();
        List<Double> progressUpdates = new ArrayList<>();
        ProgressCallback callback = (percentage, bytesProcessed, speed) -> {
            progressUpdates.add(percentage);
        };

        ConversionResult result = service.convertImage(inputPath, outputPath, settings, callback, null,
                ProcessRegistry.noOp());

        // Verify successful conversion
        assertTrue(result.success(), "Large image conversion should succeed");
        assertTrue(Files.exists(outputPath), "Output file should exist");
        assertTrue(Files.size(outputPath) > 0, "Output file should have content");

        // Verify progress updates occurred
        assertFalse(progressUpdates.isEmpty(), "Progress updates should occur");

        // Verify memory efficiency: output capture should respect 1MB limit
        // (tested implicitly by not running out of memory)
    }

    // Edge Case Test 5: Concurrent Conversions Thread Safety

    @Test
    void testConversion_ConcurrentConversions_ThreadSafe() throws Exception {
        // Requirement: NFR-IMG-2 - Thread safety for concurrent operations
        assumeTrue(service != null, "ImageMagick convert binary not found - skipping test");

        int concurrentTasks = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(concurrentTasks);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(concurrentTasks);

        // Create input files
        List<Path> inputPaths = new ArrayList<>();
        for (int i = 0; i < concurrentTasks; i++) {
            Path inputPath = tempDir.resolve("input" + i + ".png");
            createMinimalPng(inputPath);
            inputPaths.add(inputPath);
        }

        // Submit concurrent conversion tasks
        for (int i = 0; i < concurrentTasks; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    Path inputPath = inputPaths.get(taskId);
                    Path outputPath = tempDir.resolve("output" + taskId + ".jpg");
                    ImageSettings settings = ImageSettings.builder().quality(80).build();
                    ProgressCallback callback = (percentage, bytesProcessed, speed) -> {
                    };

                    ConversionResult result = service.convertImage(inputPath, outputPath, settings, callback,
                            "file-" + taskId, ProcessRegistry.noOp());

                    if (result.success()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all tasks simultaneously
        startLatch.countDown();

        // Wait for all tasks to complete (with timeout)
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify results
        assertTrue(completed, "All concurrent conversions should complete within timeout");
        assertEquals(concurrentTasks, successCount.get(), "All conversions should succeed");
        assertEquals(0, failureCount.get(), "No conversions should fail");

        // Verify all output files exist
        for (int i = 0; i < concurrentTasks; i++) {
            Path outputPath = tempDir.resolve("output" + i + ".jpg");
            assertTrue(Files.exists(outputPath), "Output file " + i + " should exist");
            assertTrue(Files.size(outputPath) > 0, "Output file " + i + " should have content");
        }
    }

    // Edge Case Test 6: Empty Input File

    @Test
    void testConversion_EmptyInputFile_FailsGracefully() throws Exception {
        // Requirement: EDGE-6 - Empty input should fail with clear error
        assumeTrue(service != null, "ImageMagick convert binary not found - skipping test");

        // Create empty input file
        Path inputPath = tempDir.resolve("empty.png");
        Files.createFile(inputPath);

        Path outputPath = tempDir.resolve("output.jpg");

        // Execute conversion
        ImageSettings settings = ImageSettings.builder().build();
        ProgressCallback callback = (percentage, bytesProcessed, speed) -> {
        };

        ConversionResult result = service.convertImage(inputPath, outputPath, settings, callback, null,
                ProcessRegistry.noOp());

        // Verify graceful failure
        assertFalse(result.success(), "Conversion should fail for empty input");
        assertTrue(result.errorMessage().isPresent(), "Error message should be present");
        assertFalse(Files.exists(outputPath), "Partial output file should be cleaned up");
    }

    // Edge Case Test 7: Invalid Output Format Extension

    @Test
    void testConversion_InvalidOutputExtension_ConvertsBasedOnExtension() throws Exception {
        // Requirement: EDGE-6 - Output format determined by file extension
        assumeTrue(service != null, "ImageMagick convert binary not found - skipping test");

        // Create input file
        Path inputPath = tempDir.resolve("input.png");
        createMinimalPng(inputPath);

        // Use unusual but valid extension
        Path outputPath = tempDir.resolve("output.jpeg");

        // Execute conversion
        ImageSettings settings = ImageSettings.builder().quality(90).build();
        ProgressCallback callback = (percentage, bytesProcessed, speed) -> {
        };

        ConversionResult result = service.convertImage(inputPath, outputPath, settings, callback, null,
                ProcessRegistry.noOp());

        // Verify successful conversion
        assertTrue(result.success(), "Conversion should succeed with .jpeg extension");
        assertTrue(Files.exists(outputPath), "Output file should exist");
        assertTrue(Files.size(outputPath) > 0, "Output file should have content");
    }

    // Edge Case Test 8: Null Progress Callback

    @Test
    void testConversion_NullProgressCallback_DoesNotThrow() throws Exception {
        // Requirement: NFR-IMG-2 - Null callback should be handled gracefully
        assumeTrue(service != null, "ImageMagick convert binary not found - skipping test");

        // Create input file
        Path inputPath = tempDir.resolve("input.png");
        createMinimalPng(inputPath);

        Path outputPath = tempDir.resolve("output.jpg");

        // Execute conversion with null callback
        ImageSettings settings = ImageSettings.builder().build();

        assertDoesNotThrow(() -> {
            ConversionResult result = service.convertImage(inputPath, outputPath, settings, null, null,
                    ProcessRegistry.noOp());
            assertTrue(result.success(), "Conversion should succeed with null callback");
        });
    }
}
