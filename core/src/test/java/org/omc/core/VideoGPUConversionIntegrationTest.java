package org.omc.core;

import org.omc.model.*;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for GPU codec conversions.
 * 
 * <p>
 * Requirements:
 * <ul>
 * <li>REQ-VID-1.1: Support MPEG-4 codec</li>
 * <li>REQ-VID-1.2: Support H.264 NVIDIA GPU encoding (h264_nvenc)</li>
 * <li>REQ-VID-1.3: Support HEVC NVIDIA GPU encoding (hevc_nvenc)</li>
 * <li>REQ-PERF-1.3: GPU hardware acceleration with CUDA flags</li>
 * </ul>
 * 
 * <p>
 * Task T-9.5: Integration Tests - End-to-End Conversions -
 * VideoGPUConversionIntegrationTest
 * </p>
 */
@ExtendWith(MockitoExtension.class)
public class VideoGPUConversionIntegrationTest {

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
    private AtomicInteger tempFileCounter;

    @BeforeEach
    public void setUp() throws Exception {
        // Create real directories for testing
        inputDir = tempDir.resolve("input");
        outputDir = tempDir.resolve("output");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        tempFileCounter = new AtomicInteger(0);

        conversionEngine = new ConversionEngine(
                toolManager,
                validationEngine,
                progressEngine,
                fileHandler,
                2 // 2 parallel conversions
        );

        // Setup default mock behaviors for successful conversions
        when(validationEngine.validateConversionRequest(any(), any()))
                .thenReturn(ValidationResult.success());
        when(validationEngine.validateToolAvailability(any()))
                .thenReturn(ValidationResult.success());
        when(validationEngine.validateOutputDirectory(any()))
                .thenReturn(ValidationResult.success());
        when(validationEngine.validateDiskSpace(any(), anyLong()))
                .thenReturn(ValidationResult.success());
        when(toolManager.selectTool(any(), any()))
                .thenReturn(ConversionTool.FFMPEG);

        // Mock temp file creation
        when(fileHandler.createTemporaryFile(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String extension = invocation.getArgument(1);
                    Path tempFile = tempDir.resolve("temp-" + tempFileCounter.incrementAndGet() + extension);
                    Files.createFile(tempFile);
                    return tempFile;
                });

        // Mock progress engine
        doNothing().when(progressEngine).startTracking(anyString(), anyLong());
        doNothing().when(progressEngine).completeTracking(anyString(), any());
    }

    @AfterEach
    public void tearDown() throws Exception {
        conversionEngine.shutdown();
    }

    // ==================== GPU Codec Tests ====================

    /**
     * REQ-VID-1.2: Test H.264 NVIDIA GPU codec conversion (h264_nvenc).
     */
    @Test
    public void testH264NvencConversion() throws Exception {
        // Given: Input video file
        Path inputFile = createInputFile("video.mp4", 5_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 5_000_000L);

        // Build settings with H.264 GPU codec
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4) // Set output format in VideoSettings
                .codec("h264_nvenc") // GPU codec
                .bitrate(5000)
                .resolution(new Resolution(1920, 1080))
                .aspectRatio(AspectRatio.KEEP_ORIGINAL)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(videoSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.MP4, 4_800_000L);

        // When: Convert with H.264 GPU codec
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with GPU codec
        assertTrue(result.success(), "H.264 GPU conversion should succeed");
        assertNotNull(result.outputPath(), "Output path should be set");
        assertEquals(ConversionTool.FFMPEG, result.toolUsed(), "Should use FFmpeg for video");
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputFile),
                any(Path.class),
                eq(FileFormat.MP4),
                eq(settings),
                any(ProgressCallback.class),
                anyString(),
                any(ProcessRegistry.class));
    }

    /**
     * REQ-VID-1.3: Test HEVC NVIDIA GPU codec conversion (hevc_nvenc).
     */
    @Test
    public void testHevcNvencConversion() throws Exception {
        // Given: Input video file
        Path inputFile = createInputFile("video.avi", 8_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.AVI, 8_000_000L);

        // Build settings with HEVC GPU codec
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MKV) // Set output format in VideoSettings
                .codec("hevc_nvenc") // GPU codec
                .bitrate(3000)
                .resolution(new Resolution(3840, 2160)) // 4K
                .aspectRatio(AspectRatio.KEEP_ORIGINAL)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(videoSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.MKV, 7_500_000L);

        // When: Convert with HEVC GPU codec
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with GPU codec
        assertTrue(result.success(), "HEVC GPU conversion should succeed");
        assertNotNull(result.outputPath(), "Output path should be set");
        assertEquals(ConversionTool.FFMPEG, result.toolUsed(), "Should use FFmpeg for video");
    }

    /**
     * REQ-VID-1.1: Test MPEG-4 codec conversion (CPU codec).
     */
    @Test
    public void testMpeg4Conversion() throws Exception {
        // Given: Input video file
        Path inputFile = createInputFile("video.mkv", 6_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MKV, 6_000_000L);

        // Build settings with MPEG-4 codec
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.AVI) // Set output format in VideoSettings
                .codec("mpeg4") // CPU codec
                .bitrate(2000)
                .resolution(new Resolution(720, 480))
                .aspectRatio(AspectRatio.KEEP_ORIGINAL)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(videoSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.AVI, 2_500_000L);

        // When: Convert with MPEG-4 codec
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with CPU codec
        assertTrue(result.success(), "MPEG-4 CPU conversion should succeed");
        assertNotNull(result.outputPath(), "Output path should be set");
        assertEquals(ConversionTool.FFMPEG, result.toolUsed(), "Should use FFmpeg for video");
    }

    /**
     * REQ-VID-1.2: Test GPU codec with aspect ratio conversion.
     */
    @Test
    public void testGPUCodecWithAspectRatio() throws Exception {
        // Given: Input video file
        Path inputFile = createInputFile("video.mp4", 4_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 4_000_000L);

        // Build settings with GPU codec and aspect ratio
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4) // Set output format in VideoSettings
                .codec("h264_nvenc")
                .bitrate(4000)
                .resolution(new Resolution(1920, 1080))
                .aspectRatio(AspectRatio.RATIO_16_9) // Force 16:9 aspect ratio
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(videoSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.MP4, 3_800_000L);

        // When: Convert with GPU codec and aspect ratio
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds
        assertTrue(result.success(), "GPU conversion with aspect ratio should succeed");
        assertNotNull(result.outputPath(), "Output path should be set");
    }

    /**
     * Test GPU codec fallback when GPU not available (simulated failure).
     */
    @Test
    public void testGPUCodecFallbackOnError() throws Exception {
        // Given: Input video file
        Path inputFile = createInputFile("video.mp4", 3_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 3_000_000L);

        // Build settings with GPU codec
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4) // Set output format in VideoSettings
                .codec("h264_nvenc")
                .bitrate(3000)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(videoSettings)
                .build();

        // Mock GPU codec failure (no NVIDIA GPU available)
        when(toolManager.executeTool(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ConversionResult.failure(
                        file.id(),
                        "GPU codec not available: No NVIDIA GPU detected",
                        null,
                        Duration.ofSeconds(1),
                        file.size(),
                        ConversionTool.FFMPEG));

        // When: Convert with GPU codec
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion fails with appropriate error
        assertFalse(result.success(), "GPU conversion should fail when GPU not available");
        assertTrue(result.errorMessage().orElse("").contains("GPU codec not available"),
                "Error message should indicate GPU unavailability");
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a test input file with specified name and size.
     */
    private Path createInputFile(String fileName, long size) throws IOException {
        Path file = inputDir.resolve(fileName);
        Files.write(file, new byte[(int) Math.min(size, 1024)]); // Write up to 1KB
        return file;
    }

    /**
     * Mocks tool execution for successful conversion.
     */
    private void mockToolExecution(Path inputFile, FileFormat outputFormat, long outputSize) throws Exception {
        when(toolManager.executeTool(
                any(ConversionTool.class),
                eq(inputFile),
                any(Path.class),
                eq(outputFormat),
                any(ConversionSettings.class),
                any(ProgressCallback.class),
                anyString(),
                any(ProcessRegistry.class))).thenAnswer(invocation -> {
                    Path tempOutputPath = invocation.getArgument(2);
                    Files.write(tempOutputPath, new byte[(int) Math.min(outputSize, 1024)]);

                    String fileId = invocation.getArgument(6);
                    return ConversionResult.success(
                            fileId,
                            outputDir.resolve("output." + outputFormat.getPrimaryExtension()),
                            null,
                            Duration.ofSeconds(3),
                            Files.size(inputFile),
                            outputSize,
                            ConversionTool.FFMPEG);
                });
    }
}
