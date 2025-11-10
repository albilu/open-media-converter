package org.omc.core;

import org.omc.model.ImageSettings;
import org.omc.model.ValidationResult;
import org.omc.core.ProgressEngine;
import org.omc.model.DocumentSettings;
import org.omc.model.ConversionSettings;
import org.omc.model.VideoSettings;
import org.omc.model.FileFormat;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionFile;
import org.omc.model.BatchConversionResult;
import org.omc.core.ToolManager;
import org.omc.model.ConversionTool;
import org.omc.core.ValidationEngine;
import org.omc.core.ConversionEngine;
import org.omc.model.AudioSettings;
import org.omc.service.FileHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Integration tests for error handling scenarios in the conversion workflow.
 * 
 * <p>
 * Requirements:
 * <ul>
 * <li>REQ-004.4: Error handling and recovery</li>
 * <li>REQ-005.2: Validation before conversion</li>
 * <li>REQ-008.1: User feedback for errors</li>
 * </ul>
 * 
 * <p>
 * Task 100: Final Integration Testing - Error Scenario Coverage
 * </p>
 */
@ExtendWith(MockitoExtension.class)
public class ErrorScenarioIntegrationTest {

    @TempDir
    Path tempDir;

    @Mock
    private ToolManager toolManager;

    @Mock
    private ValidationEngine validationEngine;

    @Mock
    private ProgressEngine progressEngine;

    @Mock
    private FileHandler fileHandler;

    private ConversionEngine conversionEngine;
    private Path inputDir;
    private Path outputDir;

    @BeforeEach
    public void setUp() throws Exception {
        inputDir = tempDir.resolve("input");
        outputDir = tempDir.resolve("output");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        conversionEngine = new ConversionEngine(
                toolManager,
                validationEngine,
                progressEngine,
                fileHandler,
                2);

        // Default mock: validation passes (will be overridden in specific tests)
        // Use lenient() for default stubs that may not be called in all tests
        lenient().when(validationEngine.validateConversionRequest(any(), any()))
                .thenReturn(ValidationResult.success());
        lenient().when(validationEngine.validateToolAvailability(any()))
                .thenReturn(ValidationResult.success());
        lenient().when(validationEngine.validateOutputDirectory(any()))
                .thenReturn(ValidationResult.success());
        lenient().when(validationEngine.validateDiskSpace(any(), anyLong()))
                .thenReturn(ValidationResult.success());

        // Mock fileHandler to create temporary files
        lenient().when(fileHandler.createTemporaryFile(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String prefix = invocation.getArgument(0);
                    String suffix = invocation.getArgument(1);
                    return Files.createTempFile(tempDir, prefix, suffix);
                });
        lenient().doNothing().when(fileHandler).registerCleanup(any(Path.class));
        lenient().doNothing().when(fileHandler).unregisterCleanup(any(Path.class));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (conversionEngine != null) {
            conversionEngine.shutdown();
        }
    }

    // ========== Tool Availability Errors ==========

    @Test
    public void testFFmpegNotFound() throws Exception {
        // Given: FFmpeg is not available
        Path inputFile = createInputFile("video.mp4", 1000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 1000L);

        when(toolManager.selectTool(FileFormat.MP4, FileFormat.AVI))
                .thenReturn(ConversionTool.FFMPEG);
        when(validationEngine.validateToolAvailability(ConversionTool.FFMPEG))
                .thenReturn(ValidationResult.failure("FFmpeg not found"));

        ConversionSettings settings = createVideoSettings(FileFormat.AVI);

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail with tool error
        assertFalse(result.success(), "Conversion should fail when FFmpeg is not found");
        assertTrue(result.errorMessage().isPresent(), "Error message should be present");
        assertTrue(result.errorMessage().get().contains("Tool not available"),
                "Error should mention tool availability");

        // Tool execution should not be attempted
        verify(toolManager, never()).executeTool(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testPandocNotFound() throws Exception {
        // Given: Pandoc is not available
        Path inputFile = createInputFile("document.md", 500L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MARKDOWN, 500L);

        when(toolManager.selectTool(FileFormat.MARKDOWN, FileFormat.HTML))
                .thenReturn(ConversionTool.PANDOC);
        when(validationEngine.validateToolAvailability(ConversionTool.PANDOC))
                .thenReturn(ValidationResult.failure("Pandoc not found"));

        ConversionSettings settings = createDocumentSettings(FileFormat.HTML);

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail
        assertFalse(result.success());
        assertTrue(result.errorMessage().get().contains("Tool not available"));
        verify(toolManager, never()).executeTool(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testLibreOfficeNotFound() throws Exception {
        // Given: LibreOffice is not available
        Path inputFile = createInputFile("document.docx", 10000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.DOCX, 10000L);

        when(toolManager.selectTool(FileFormat.DOCX, FileFormat.PDF))
                .thenReturn(ConversionTool.LIBREOFFICE);
        when(validationEngine.validateToolAvailability(ConversionTool.LIBREOFFICE))
                .thenReturn(ValidationResult.failure("LibreOffice not found"));

        ConversionSettings settings = createDocumentSettings(FileFormat.PDF);

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail
        assertFalse(result.success());
        assertTrue(result.errorMessage().get().contains("Tool not available"));
        verify(toolManager, never()).executeTool(any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ========== File Validation Errors ==========

    @Test
    public void testNonExistentInputFile() throws Exception {
        // Given: Input file does not exist
        Path nonExistentFile = inputDir.resolve("nonexistent.mp4");
        ConversionFile file = ConversionFile.create(nonExistentFile, FileFormat.MP4, 1000L);

        lenient().when(toolManager.selectTool(FileFormat.MP4, FileFormat.AVI))
                .thenReturn(ConversionTool.FFMPEG);
        when(validationEngine.validateConversionRequest(any(), any()))
                .thenReturn(ValidationResult.failure("Input file does not exist"));

        ConversionSettings settings = createVideoSettings(FileFormat.AVI);

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail with validation error
        assertFalse(result.success());
        assertTrue(result.errorMessage().get().contains("Input file does not exist") ||
                result.errorMessage().get().contains("validation failed"),
                "Error should mention file validation issue");
    }

    @Test
    public void testZeroByteInputFile() throws Exception {
        // Given: Input file has zero bytes
        Path emptyFile = createInputFile("empty.mp3", 0L);
        ConversionFile file = ConversionFile.create(emptyFile, FileFormat.MP3, 0L);

        lenient().when(toolManager.selectTool(FileFormat.MP3, FileFormat.WAV))
                .thenReturn(ConversionTool.FFMPEG);
        when(validationEngine.validateConversionRequest(any(), any()))
                .thenReturn(ValidationResult.failure("Input file is empty"));

        ConversionSettings settings = createAudioSettings(FileFormat.WAV);

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail
        assertFalse(result.success());
        assertTrue(result.errorMessage().isPresent());
    }

    @Test
    public void testCorruptedInputFile() throws Exception {
        // Given: Corrupted input file (tool execution fails)
        Path corruptedFile = createInputFile("corrupted.mp4", 1000L);
        ConversionFile file = ConversionFile.create(corruptedFile, FileFormat.MP4, 1000L);

        when(toolManager.selectTool(FileFormat.MP4, FileFormat.AVI))
                .thenReturn(ConversionTool.FFMPEG);
        when(toolManager.executeTool(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ConversionResult.failure(file.id(),
                        "FFmpeg error: Invalid data found when processing input", null, Duration.ofSeconds(1),
                        1000L,
                        ConversionTool.FFMPEG));

        ConversionSettings settings = createVideoSettings(FileFormat.AVI);

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail with tool error
        assertFalse(result.success());
        assertTrue(result.errorMessage().get().contains("Invalid data") ||
                result.errorMessage().get().contains("FFmpeg error"));
    }

    // ========== Disk Space and Permission Errors ==========

    @Test
    public void testInsufficientDiskSpace() throws Exception {
        // Given: Insufficient disk space
        Path inputFile = createInputFile("large-video.mp4", 5_000_000_000L); // 5GB
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 5_000_000_000L);

        when(toolManager.selectTool(FileFormat.MP4, FileFormat.AVI))
                .thenReturn(ConversionTool.FFMPEG);
        when(validationEngine.validateDiskSpace(any(), anyLong()))
                .thenReturn(ValidationResult.failure("Insufficient disk space"));

        ConversionSettings settings = createVideoSettings(FileFormat.AVI);

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail
        assertFalse(result.success());
        assertTrue(result.errorMessage().get().contains("Insufficient disk space") ||
                result.errorMessage().get().contains("disk space"));
    }

    @Test
    public void testOutputDirectoryNotWritable() throws Exception {
        // Given: Output directory is not writable
        Path inputFile = createInputFile("audio.mp3", 1000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP3, 1000L);

        when(toolManager.selectTool(FileFormat.MP3, FileFormat.WAV))
                .thenReturn(ConversionTool.FFMPEG);
        when(validationEngine.validateOutputDirectory(any()))
                .thenReturn(ValidationResult.failure("Output directory is not writable"));

        ConversionSettings settings = createAudioSettings(FileFormat.WAV);

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail
        assertFalse(result.success());
        assertTrue(result.errorMessage().get().contains("not writable") ||
                result.errorMessage().get().contains("Output directory"));
    }

    @Test
    public void testOutputDirectoryDoesNotExist() throws Exception {
        // Given: Output directory does not exist
        Path inputFile = createInputFile("image.png", 5000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.PNG, 5000L);

        Path nonExistentOutput = tempDir.resolve("nonexistent-output");

        when(toolManager.selectTool(FileFormat.PNG, FileFormat.JPEG))
                .thenReturn(ConversionTool.FFMPEG);
        when(validationEngine.validateOutputDirectory(nonExistentOutput))
                .thenReturn(ValidationResult.failure("Output directory does not exist"));

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(nonExistentOutput)
                .imageSettings(ImageSettings.builder()
                        .outputFormat(FileFormat.JPEG)
                        .quality(90)
                        .build())
                .build();

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail
        assertFalse(result.success());
        assertTrue(result.errorMessage().get().contains("Output directory"));
    }

    // ========== Tool Execution Errors ==========

    @Test
    public void testToolExecutionTimeout() throws Exception {
        // Given: Tool execution times out
        Path inputFile = createInputFile("video.mp4", 100000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 100000L);

        when(toolManager.selectTool(FileFormat.MP4, FileFormat.AVI))
                .thenReturn(ConversionTool.FFMPEG);
        when(toolManager.executeTool(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ConversionResult.failure(file.id(), "Conversion timeout: Process took longer than allowed",
                        null, Duration.ofMinutes(10),
                        100000L,
                        ConversionTool.FFMPEG));

        ConversionSettings settings = createVideoSettings(FileFormat.AVI);

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail with timeout error
        assertFalse(result.success());
        assertTrue(result.errorMessage().get().contains("timeout") ||
                result.errorMessage().get().contains("took longer"));
    }

    @Test
    public void testToolCrash() throws Exception {
        // Given: Tool crashes during execution
        Path inputFile = createInputFile("problematic.mkv", 50000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MKV, 50000L);

        when(toolManager.selectTool(FileFormat.MKV, FileFormat.MP4))
                .thenReturn(ConversionTool.FFMPEG);
        when(toolManager.executeTool(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ConversionResult.failure(file.id(), "Tool crashed: Exit code 139 (segmentation fault)",
                        null, Duration.ofSeconds(5),
                        50000L,
                        ConversionTool.FFMPEG));

        ConversionSettings settings = createVideoSettings(FileFormat.MP4);

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail
        assertFalse(result.success());
        assertTrue(result.errorMessage().get().contains("crashed") ||
                result.errorMessage().get().contains("Exit code"));
    }

    @Test
    public void testUnsupportedFormatCombination() throws Exception {
        // Given: Unsupported format conversion
        Path inputFile = createInputFile("document.pdf", 20000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.PDF, 20000L);

        when(toolManager.selectTool(FileFormat.PDF, FileFormat.DOCX))
                .thenThrow(new IllegalArgumentException("No tool supports PDF → DOCX conversion"));

        ConversionSettings settings = createDocumentSettings(FileFormat.DOCX);

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail
        assertFalse(result.success());
        assertTrue(result.errorMessage().isPresent());
    }

    // ========== Batch Conversion Error Handling ==========

    @Test
    public void testBatchConversionWithPartialFailures() throws Exception {
        // Given: Batch with some successful and some failed conversions
        Path file1 = createInputFile("good1.mp3", 1000L);
        Path file2 = createInputFile("bad.mp3", 1000L);
        Path file3 = createInputFile("good2.mp3", 1000L);

        ConversionFile conv1 = ConversionFile.create(file1, FileFormat.MP3, 1000L);
        ConversionFile conv2 = ConversionFile.create(file2, FileFormat.MP3, 1000L);
        ConversionFile conv3 = ConversionFile.create(file3, FileFormat.MP3, 1000L);

        when(toolManager.selectTool(FileFormat.MP3, FileFormat.WAV))
                .thenReturn(ConversionTool.FFMPEG);

        // Configure tool execution results - use any() matchers for flexible matching
        when(toolManager.executeTool(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Path inputPath = invocation.getArgument(1);
                    String fileId = invocation.getArgument(6);

                    // Match by input file path to return appropriate result
                    if (inputPath.equals(file1)) {
                        return ConversionResult.success(conv1.id(), outputDir.resolve("good1.wav"), null,
                                Duration.ofSeconds(2), 1000L, 2000L, ConversionTool.FFMPEG);
                    } else if (inputPath.equals(file2)) {
                        return ConversionResult.failure(conv2.id(), "Corrupted audio stream", null,
                                Duration.ofSeconds(1), 1000L, ConversionTool.FFMPEG);
                    } else if (inputPath.equals(file3)) {
                        return ConversionResult.success(conv3.id(), outputDir.resolve("good2.wav"), null,
                                Duration.ofSeconds(2), 1000L, 2000L, ConversionTool.FFMPEG);
                    } else {
                        throw new IllegalStateException("Unexpected file: " + inputPath);
                    }
                });

        ConversionSettings settings = createAudioSettings(FileFormat.WAV);

        // When: Convert batch
        CompletableFuture<BatchConversionResult> future = conversionEngine.convertBatch(
                java.util.List.of(conv1, conv2, conv3),
                settings);
        BatchConversionResult batchResult = future.get();

        // Then: Batch should complete with partial success
        assertEquals(2, batchResult.successCount(), "Should have 2 successful conversions");
        assertEquals(1, batchResult.failureCount(), "Should have 1 failed conversion");
        assertFalse(batchResult.allSucceeded(), "Not all conversions should succeed");
    }

    // ========== ImageMagick Error Scenarios ==========

    /**
     * Test ImageMagick error when convert binary is not found.
     * Requirements: REQ-IMG-1, NFR-IMG-3, EDGE-1
     */
    @Test
    public void testImageConversion_MissingImageMagick() throws Exception {
        // Given: ImageMagick is not available
        Path inputFile = createInputFile("image.png", 5000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.PNG, 5000L);

        when(toolManager.selectTool(FileFormat.PNG, FileFormat.JPEG))
                .thenReturn(ConversionTool.IMAGEMAGICK);
        when(validationEngine.validateToolAvailability(ConversionTool.IMAGEMAGICK))
                .thenReturn(ValidationResult
                        .failure("ImageMagick 'convert' binary not found. Please install ImageMagick."));

        ConversionSettings settings = createImageSettings(FileFormat.JPEG);

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail with clear error message
        assertFalse(result.success(), "Conversion should fail when ImageMagick is not found");
        assertTrue(result.errorMessage().isPresent(), "Error message should be present");
        assertTrue(result.errorMessage().get().contains("Tool not available") ||
                result.errorMessage().get().contains("ImageMagick") ||
                result.errorMessage().get().contains("convert"),
                "Error should mention ImageMagick or convert binary: " + result.errorMessage().get());

        // Tool execution should not be attempted
        verify(toolManager, never()).executeTool(any(), any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * Test ImageMagick error handling for corrupted image files.
     * Requirements: REQ-IMG-2, NFR-IMG-3, EDGE-1
     */
    @Test
    public void testImageConversion_CorruptedImage() throws Exception {
        // Given: Corrupted image file (tool execution fails)
        Path corruptedImage = createInputFile("corrupted.png", 1000L);
        ConversionFile file = ConversionFile.create(corruptedImage, FileFormat.PNG, 1000L);

        when(toolManager.selectTool(FileFormat.PNG, FileFormat.JPEG))
                .thenReturn(ConversionTool.IMAGEMAGICK);
        when(toolManager.executeTool(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ConversionResult.failure(
                        file.id(),
                        "convert: improper image header `corrupted.png' @ error/png.c/ReadPNGImage/4089.",
                        null,
                        Duration.ofSeconds(1),
                        1000L,
                        ConversionTool.IMAGEMAGICK));

        ConversionSettings settings = createImageSettings(FileFormat.JPEG);

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail with descriptive error
        assertFalse(result.success(), "Conversion should fail for corrupted image");
        assertTrue(result.errorMessage().isPresent(), "Error message should be present");
        assertTrue(result.errorMessage().get().contains("improper image header") ||
                result.errorMessage().get().contains("error"),
                "Error should describe image corruption: " + result.errorMessage().get());
        assertEquals(ConversionTool.IMAGEMAGICK, result.toolUsed(), "Should use ImageMagick tool");
    }

    /**
     * Test ImageMagick error for unsupported or invalid format conversion.
     * Requirements: REQ-IMG-2, NFR-IMG-3, EDGE-3
     */
    @Test
    public void testImageConversion_UnsupportedFormat() throws Exception {
        // Given: Unsupported format conversion attempt
        Path inputFile = createInputFile("image.bmp", 2000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.BMP, 2000L);

        when(toolManager.selectTool(FileFormat.BMP, FileFormat.PNG))
                .thenReturn(ConversionTool.IMAGEMAGICK);
        when(toolManager.executeTool(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ConversionResult.failure(
                        file.id(),
                        "convert: no decode delegate for this image format `BMP' @ error/constitute.c/ReadImage/560.",
                        null,
                        Duration.ofMillis(500),
                        2000L,
                        ConversionTool.IMAGEMAGICK));

        ConversionSettings settings = createImageSettings(FileFormat.PNG);

        // When: Attempt conversion
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get();

        // Then: Conversion should fail with format error
        assertFalse(result.success(), "Conversion should fail for unsupported format");
        assertTrue(result.errorMessage().isPresent(), "Error message should be present");
        assertTrue(result.errorMessage().get().contains("no decode delegate") ||
                result.errorMessage().get().contains("format"),
                "Error should mention unsupported format: " + result.errorMessage().get());
        assertEquals(ConversionTool.IMAGEMAGICK, result.toolUsed(), "Should use ImageMagick tool");
    }

    /**
     * Test ImageMagick process cancellation and cleanup.
     * Requirements: REQ-IMG-2, REQ-SEL-4, EDGE-4
     */
    @Test
    public void testImageConversion_Cancellation() throws Exception {
        // Given: Image conversion that will be cancelled
        Path inputFile = createInputFile("large-image.tiff", 50_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.TIFF, 50_000_000L);

        when(toolManager.selectTool(FileFormat.TIFF, FileFormat.PNG))
                .thenReturn(ConversionTool.IMAGEMAGICK);

        // Simulate long-running conversion
        when(toolManager.executeTool(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    // Simulate long-running process
                    Thread.sleep(500);
                    return ConversionResult.success(
                            file.id(),
                            outputDir.resolve("large-image.png"),
                            null,
                            Duration.ofMillis(500),
                            50_000_000L,
                            30_000_000L,
                            ConversionTool.IMAGEMAGICK);
                });

        ConversionSettings settings = createImageSettings(FileFormat.PNG);

        // When: Start conversion and cancel it immediately
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);

        // Wait a bit for conversion to start, then cancel
        Thread.sleep(50);
        conversionEngine.cancelConversion();

        // Then: Future should be cancelled
        assertTrue(future.isCancelled() || future.isCompletedExceptionally(),
                "Conversion future should be cancelled or completed exceptionally");

        // If we try to get the result, it should throw CancellationException or
        // ExecutionException
        try {
            ConversionResult result = future.get();
            // If we get here, the result should indicate failure/cancellation
            assertFalse(result.success(), "If result is returned, it should indicate failure");
        } catch (java.util.concurrent.CancellationException e) {
            // Expected - conversion was cancelled
            assertTrue(true, "Conversion was properly cancelled");
        } catch (ExecutionException e) {
            // Also acceptable - conversion was interrupted
            assertTrue(e.getMessage().contains("cancel") || e.getMessage().contains("interrupt"),
                    "Exception should indicate cancellation");
        }
    }

    // ========== Helper Methods ==========

    private Path createInputFile(String filename, long size) throws IOException {
        Path file = inputDir.resolve(filename);
        if (size == 0) {
            Files.createFile(file);
        } else {
            Files.writeString(file, "test content for " + filename);
        }
        return file;
    }

    private ConversionSettings createVideoSettings(FileFormat outputFormat) {
        return ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(VideoSettings.builder()
                        .outputFormat(outputFormat)
                        .codec("libx264")
                        .bitrate(4000)
                        .build())
                .build();
    }

    private ConversionSettings createAudioSettings(FileFormat outputFormat) {
        return ConversionSettings.builder()
                .outputDirectory(outputDir)
                .audioSettings(AudioSettings.builder()
                        .outputFormat(outputFormat)
                        .codec("libmp3lame")
                        .bitrate(192)
                        .build())
                .build();
    }

    private ConversionSettings createImageSettings(FileFormat outputFormat) {
        return ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(ImageSettings.builder()
                        .outputFormat(outputFormat)
                        .quality(90)
                        .build())
                .build();
    }

    private ConversionSettings createDocumentSettings(FileFormat outputFormat) {
        return ConversionSettings.builder()
                .outputDirectory(outputDir)
                .documentSettings(DocumentSettings.builder()
                        .outputFormat(outputFormat)
                        .preserveFormatting(true)
                        .build())
                .build();
    }
}
