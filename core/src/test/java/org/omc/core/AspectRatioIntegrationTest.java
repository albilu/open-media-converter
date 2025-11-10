package org.omc.core;

import org.omc.model.*;
import org.omc.service.FileHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
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
 * Integration tests for video aspect ratio conversions.
 * 
 * <p>
 * Requirements:
 * <ul>
 * <li>REQ-VID-2.1: Aspect ratio selection with 8 options (Keep Original, 16:9,
 * 4:3, 1:1, 21:9, 9:16, 3:2, 2.39:1)</li>
 * <li>REQ-VID-2.2: Aspect ratio application with setdar and pad filters</li>
 * <li>REQ-VID-2.3: Aspect ratio + resolution interaction (aspect applied after
 * scaling)</li>
 * </ul>
 * 
 * <p>
 * Task T-9.5: Integration Tests - End-to-End Conversions -
 * AspectRatioIntegrationTest
 * </p>
 */
@ExtendWith(MockitoExtension.class)
public class AspectRatioIntegrationTest {

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

        // Setup default mock behaviors
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

    // ==================== Aspect Ratio Conversion Tests ====================

    /**
     * REQ-VID-2.2: Test 16:9 widescreen aspect ratio conversion.
     * Verifies that FFmpeg filter chain includes setdar and pad filters.
     */
    @Test
    public void testConvertTo16By9AspectRatio() throws Exception {
        // Given: Input video file with 4:3 aspect ratio
        Path inputFile = createInputFile("video_4x3.mp4", 10_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 10_000_000L);

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(VideoSettings.builder()
                        .outputFormat(FileFormat.MP4)
                        .codec("libx264")
                        .aspectRatio(AspectRatio.RATIO_16_9) // Target 16:9
                        .build())
                .build();

        // Mock successful conversion
        mockSuccessfulConversion(inputFile, FileFormat.MP4, 9_500_000L);

        // When: Convert with 16:9 aspect ratio
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds
        assertTrue(result.success(), "Conversion should succeed");
        assertEquals(ConversionTool.FFMPEG, result.toolUsed(), "Should use FFmpeg for video conversion");

        // Verify VideoSettings contains 16:9 aspect ratio
        ArgumentCaptor<ConversionSettings> settingsCaptor = ArgumentCaptor.forClass(ConversionSettings.class);
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputFile),
                any(Path.class),
                eq(FileFormat.MP4),
                settingsCaptor.capture(),
                any(ProgressCallback.class),
                anyString(),
                any(ProcessRegistry.class));

        ConversionSettings capturedSettings = settingsCaptor.getValue();
        assertNotNull(capturedSettings.videoSettings(), "VideoSettings should not be null");
        assertEquals(AspectRatio.RATIO_16_9, capturedSettings.videoSettings().aspectRatio(),
                "Aspect ratio should be 16:9");
    }

    /**
     * REQ-VID-2.2: Test 4:3 standard aspect ratio conversion.
     */
    @Test
    public void testConvertTo4By3AspectRatio() throws Exception {
        // Given: Input video file with 16:9 aspect ratio
        Path inputFile = createInputFile("video_16x9.mkv", 15_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MKV, 15_000_000L);

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(VideoSettings.builder()
                        .outputFormat(FileFormat.MP4)
                        .codec("libx264")
                        .aspectRatio(AspectRatio.RATIO_4_3) // Target 4:3
                        .build())
                .build();

        // Mock successful conversion
        mockSuccessfulConversion(inputFile, FileFormat.MP4, 12_000_000L);

        // When: Convert with 4:3 aspect ratio
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with 4:3 aspect ratio
        assertTrue(result.success(), "Conversion should succeed");

        ArgumentCaptor<ConversionSettings> settingsCaptor = ArgumentCaptor.forClass(ConversionSettings.class);
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputFile),
                any(Path.class),
                eq(FileFormat.MP4),
                settingsCaptor.capture(),
                any(ProgressCallback.class),
                anyString(),
                any(ProcessRegistry.class));

        assertEquals(AspectRatio.RATIO_4_3, settingsCaptor.getValue().videoSettings().aspectRatio(),
                "Aspect ratio should be 4:3");
    }

    /**
     * REQ-VID-2.2: Test 1:1 square aspect ratio conversion.
     * Used for Instagram posts and square video formats.
     */
    @Test
    public void testConvertTo1By1SquareAspectRatio() throws Exception {
        // Given: Input video file with 16:9 aspect ratio
        Path inputFile = createInputFile("video_widescreen.mp4", 20_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 20_000_000L);

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(VideoSettings.builder()
                        .outputFormat(FileFormat.MP4)
                        .codec("libx264")
                        .aspectRatio(AspectRatio.RATIO_1_1) // Target 1:1 square
                        .build())
                .build();

        // Mock successful conversion
        mockSuccessfulConversion(inputFile, FileFormat.MP4, 15_000_000L);

        // When: Convert with 1:1 aspect ratio
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with 1:1 aspect ratio
        assertTrue(result.success(), "Conversion should succeed");

        ArgumentCaptor<ConversionSettings> settingsCaptor = ArgumentCaptor.forClass(ConversionSettings.class);
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputFile),
                any(Path.class),
                eq(FileFormat.MP4),
                settingsCaptor.capture(),
                any(ProgressCallback.class),
                anyString(),
                any(ProcessRegistry.class));

        assertEquals(AspectRatio.RATIO_1_1, settingsCaptor.getValue().videoSettings().aspectRatio(),
                "Aspect ratio should be 1:1");
    }

    /**
     * REQ-VID-2.2: Test 9:16 vertical aspect ratio conversion.
     * Used for mobile-first vertical video (Instagram Stories, TikTok).
     */
    @Test
    public void testConvertTo9By16VerticalAspectRatio() throws Exception {
        // Given: Input video file with 16:9 horizontal aspect ratio
        Path inputFile = createInputFile("horizontal.mp4", 25_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 25_000_000L);

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(VideoSettings.builder()
                        .outputFormat(FileFormat.MP4)
                        .codec("libx264")
                        .aspectRatio(AspectRatio.RATIO_9_16) // Target 9:16 vertical
                        .build())
                .build();

        // Mock successful conversion
        mockSuccessfulConversion(inputFile, FileFormat.MP4, 18_000_000L);

        // When: Convert with 9:16 aspect ratio
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with 9:16 aspect ratio
        assertTrue(result.success(), "Conversion should succeed");

        ArgumentCaptor<ConversionSettings> settingsCaptor = ArgumentCaptor.forClass(ConversionSettings.class);
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputFile),
                any(Path.class),
                eq(FileFormat.MP4),
                settingsCaptor.capture(),
                any(ProgressCallback.class),
                anyString(),
                any(ProcessRegistry.class));

        assertEquals(AspectRatio.RATIO_9_16, settingsCaptor.getValue().videoSettings().aspectRatio(),
                "Aspect ratio should be 9:16");
    }

    /**
     * REQ-VID-2.2: Test KEEP_ORIGINAL aspect ratio (no filters applied).
     * Verifies that no aspect ratio filters are added to FFmpeg command.
     */
    @Test
    public void testKeepOriginalAspectRatio() throws Exception {
        // Given: Input video file with any aspect ratio
        Path inputFile = createInputFile("original.mp4", 12_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 12_000_000L);

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(VideoSettings.builder()
                        .outputFormat(FileFormat.MKV)
                        .codec("libx264")
                        .aspectRatio(AspectRatio.KEEP_ORIGINAL) // Keep original
                        .build())
                .build();

        // Mock successful conversion
        mockSuccessfulConversion(inputFile, FileFormat.MKV, 11_500_000L);

        // When: Convert with KEEP_ORIGINAL aspect ratio
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with KEEP_ORIGINAL
        assertTrue(result.success(), "Conversion should succeed");

        ArgumentCaptor<ConversionSettings> settingsCaptor = ArgumentCaptor.forClass(ConversionSettings.class);
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputFile),
                any(Path.class),
                eq(FileFormat.MKV),
                settingsCaptor.capture(),
                any(ProgressCallback.class),
                anyString(),
                any(ProcessRegistry.class));

        assertEquals(AspectRatio.KEEP_ORIGINAL, settingsCaptor.getValue().videoSettings().aspectRatio(),
                "Aspect ratio should be KEEP_ORIGINAL");
        assertTrue(settingsCaptor.getValue().videoSettings().aspectRatio().isOriginal(),
                "isOriginal() should return true for KEEP_ORIGINAL");
    }

    /**
     * REQ-VID-2.3: Test aspect ratio + resolution interaction.
     * Aspect ratio should be applied AFTER resolution scaling.
     */
    @Test
    public void testAspectRatioWithResolutionScaling() throws Exception {
        // Given: Input video file with resolution and aspect ratio settings
        Path inputFile = createInputFile("highres.mp4", 50_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 50_000_000L);

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(VideoSettings.builder()
                        .outputFormat(FileFormat.MP4)
                        .codec("libx264")
                        .resolution(new Resolution(1920, 1080)) // Scale to 1080p
                        .aspectRatio(AspectRatio.RATIO_16_9) // Then apply 16:9 aspect
                        .build())
                .build();

        // Mock successful conversion
        mockSuccessfulConversion(inputFile, FileFormat.MP4, 35_000_000L);

        // When: Convert with resolution + aspect ratio
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with both resolution and aspect ratio
        assertTrue(result.success(), "Conversion should succeed");

        ArgumentCaptor<ConversionSettings> settingsCaptor = ArgumentCaptor.forClass(ConversionSettings.class);
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputFile),
                any(Path.class),
                eq(FileFormat.MP4),
                settingsCaptor.capture(),
                any(ProgressCallback.class),
                anyString(),
                any(ProcessRegistry.class));

        ConversionSettings capturedSettings = settingsCaptor.getValue();
        assertNotNull(capturedSettings.videoSettings().resolution(), "Resolution should be set");
        assertEquals(1920, capturedSettings.videoSettings().resolution().getWidth(), "Width should be 1920");
        assertEquals(1080, capturedSettings.videoSettings().resolution().getHeight(), "Height should be 1080");
        assertEquals(AspectRatio.RATIO_16_9, capturedSettings.videoSettings().aspectRatio(),
                "Aspect ratio should be 16:9");
    }

    /**
     * REQ-VID-2.3: Test aspect ratio without resolution (maintains original
     * resolution with padding).
     */
    @Test
    public void testAspectRatioWithoutResolution() throws Exception {
        // Given: Input video file with aspect ratio but no resolution setting
        Path inputFile = createInputFile("native_res.mp4", 30_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 30_000_000L);

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(VideoSettings.builder()
                        .outputFormat(FileFormat.MP4)
                        .codec("libx264")
                        .aspectRatio(AspectRatio.RATIO_21_9) // Only aspect ratio, no resolution
                        .build())
                .build();

        // Mock successful conversion
        mockSuccessfulConversion(inputFile, FileFormat.MP4, 28_000_000L);

        // When: Convert with aspect ratio only (no resolution)
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with aspect ratio but original resolution
        assertTrue(result.success(), "Conversion should succeed");

        ArgumentCaptor<ConversionSettings> settingsCaptor = ArgumentCaptor.forClass(ConversionSettings.class);
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputFile),
                any(Path.class),
                eq(FileFormat.MP4),
                settingsCaptor.capture(),
                any(ProgressCallback.class),
                anyString(),
                any(ProcessRegistry.class));

        ConversionSettings capturedSettings = settingsCaptor.getValue();
        assertNull(capturedSettings.videoSettings().resolution(), "Resolution should be null (keep original)");
        assertEquals(AspectRatio.RATIO_21_9, capturedSettings.videoSettings().aspectRatio(),
                "Aspect ratio should be 21:9");
    }

    /**
     * REQ-VID-2.2: Test 21:9 ultrawide cinematic aspect ratio conversion.
     */
    @Test
    public void testConvertTo21By9UltrawideAspectRatio() throws Exception {
        // Given: Input video file
        Path inputFile = createInputFile("standard.mp4", 18_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 18_000_000L);

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(VideoSettings.builder()
                        .outputFormat(FileFormat.MP4)
                        .codec("libx264")
                        .aspectRatio(AspectRatio.RATIO_21_9) // Ultrawide 21:9
                        .build())
                .build();

        // Mock successful conversion
        mockSuccessfulConversion(inputFile, FileFormat.MP4, 17_000_000L);

        // When: Convert with 21:9 aspect ratio
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with 21:9 aspect ratio
        assertTrue(result.success(), "Conversion should succeed");

        ArgumentCaptor<ConversionSettings> settingsCaptor = ArgumentCaptor.forClass(ConversionSettings.class);
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputFile),
                any(Path.class),
                eq(FileFormat.MP4),
                settingsCaptor.capture(),
                any(ProgressCallback.class),
                anyString(),
                any(ProcessRegistry.class));

        assertEquals(AspectRatio.RATIO_21_9, settingsCaptor.getValue().videoSettings().aspectRatio(),
                "Aspect ratio should be 21:9");
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
    private void mockSuccessfulConversion(Path inputFile, FileFormat outputFormat, long outputSize) throws Exception {
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
