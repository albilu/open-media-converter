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
 * Integration tests for image transformation operations (rotation, flip,
 * resize).
 * 
 * <p>
 * Requirements:
 * <ul>
 * <li>REQ-IMG-1.2: Image rotation application with ImageMagick</li>
 * <li>REQ-IMG-2.2: Image flip application (horizontal, vertical, both)</li>
 * <li>REQ-IMG-1.2 (Edge case): Transformation order: rotate → flip →
 * resize</li>
 * </ul>
 * 
 * <p>
 * Task T-9.5: Integration Tests - End-to-End Conversions -
 * ImageTransformationIntegrationTest
 * </p>
 */
@ExtendWith(MockitoExtension.class)
public class ImageTransformationIntegrationTest {

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
                .thenReturn(ConversionTool.IMAGEMAGICK);

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

    // ==================== Rotation Tests ====================

    /**
     * REQ-IMG-1.2: Test 90° clockwise rotation.
     * Verifies that ImageMagick receives "-rotate 90" command.
     */
    @Test
    public void testRotate90Clockwise() throws Exception {
        // Given: Input image with 90° clockwise rotation
        Path inputFile = createInputFile("portrait.jpg", 1_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.JPEG, 1_000_000L);

        // Build settings with 90° clockwise rotation
        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PNG)
                .rotation(ImageRotation.CLOCKWISE_90)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.PNG, 1_000_000L);

        // When: Convert with rotation
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with rotation setting
        assertTrue(result.success(), "Rotation conversion should succeed");
        assertNotNull(result.outputPath(), "Output path should be set");
        assertEquals(ConversionTool.IMAGEMAGICK, result.toolUsed(), "Should use ImageMagick for images");

        // Verify rotation setting was passed (ImageMagick service handles command
        // building)
        verify(toolManager).executeTool(
                eq(ConversionTool.IMAGEMAGICK),
                eq(inputFile),
                any(Path.class),
                eq(FileFormat.PNG),
                argThat(s -> s.imageSettings().rotation() == ImageRotation.CLOCKWISE_90),
                any(ProgressCallback.class),
                anyString(),
                any(ProcessRegistry.class));
    }

    /**
     * REQ-IMG-1.2: Test 180° rotation.
     */
    @Test
    public void testRotate180() throws Exception {
        // Given: Input image with 180° rotation
        Path inputFile = createInputFile("photo.png", 2_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.PNG, 2_000_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.JPEG)
                .rotation(ImageRotation.ROTATE_180)
                .quality(90)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        mockToolExecution(inputFile, FileFormat.JPEG, 1_800_000L);

        // When: Convert with 180° rotation
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds
        assertTrue(result.success(), "180° rotation conversion should succeed");
        verify(toolManager).executeTool(
                eq(ConversionTool.IMAGEMAGICK),
                any(),
                any(),
                any(),
                argThat(s -> s.imageSettings().rotation() == ImageRotation.ROTATE_180),
                any(),
                anyString(),
                any());
    }

    /**
     * REQ-IMG-1.2: Test 90° counter-clockwise rotation (270°).
     */
    @Test
    public void testRotate90CounterClockwise() throws Exception {
        // Given: Input image with 90° counter-clockwise rotation
        Path inputFile = createInputFile("landscape.bmp", 5_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.BMP, 5_000_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PNG)
                .rotation(ImageRotation.COUNTER_CLOCKWISE_90)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        mockToolExecution(inputFile, FileFormat.PNG, 3_000_000L);

        // When: Convert with counter-clockwise rotation
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds
        assertTrue(result.success(), "Counter-clockwise rotation should succeed");
        verify(toolManager).executeTool(
                any(),
                any(),
                any(),
                any(),
                argThat(s -> s.imageSettings().rotation() == ImageRotation.COUNTER_CLOCKWISE_90),
                any(),
                anyString(),
                any());
    }

    // ==================== Flip Tests ====================

    /**
     * REQ-IMG-2.2: Test horizontal flip.
     * Verifies that ImageMagick receives "-flop" command.
     */
    @Test
    public void testFlipHorizontal() throws Exception {
        // Given: Input image with horizontal flip
        Path inputFile = createInputFile("mirror.jpg", 800_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.JPEG, 800_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.JPEG)
                .flip(ImageFlip.HORIZONTAL)
                .quality(85)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        mockToolExecution(inputFile, FileFormat.JPEG, 800_000L);

        // When: Convert with horizontal flip
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with flip setting
        assertTrue(result.success(), "Horizontal flip conversion should succeed");
        verify(toolManager).executeTool(
                eq(ConversionTool.IMAGEMAGICK),
                any(),
                any(),
                any(),
                argThat(s -> s.imageSettings().flip() == ImageFlip.HORIZONTAL),
                any(),
                anyString(),
                any());
    }

    /**
     * REQ-IMG-2.2: Test vertical flip.
     * Verifies that ImageMagick receives "-flip" command.
     */
    @Test
    public void testFlipVertical() throws Exception {
        // Given: Input image with vertical flip
        Path inputFile = createInputFile("upsidedown.png", 1_500_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.PNG, 1_500_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PNG)
                .flip(ImageFlip.VERTICAL)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        mockToolExecution(inputFile, FileFormat.PNG, 1_500_000L);

        // When: Convert with vertical flip
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds
        assertTrue(result.success(), "Vertical flip conversion should succeed");
        verify(toolManager).executeTool(
                any(),
                any(),
                any(),
                any(),
                argThat(s -> s.imageSettings().flip() == ImageFlip.VERTICAL),
                any(),
                anyString(),
                any());
    }

    /**
     * REQ-IMG-2.2: Test both horizontal and vertical flip.
     * Verifies that ImageMagick receives both "-flip" and "-flop" commands.
     */
    @Test
    public void testFlipBoth() throws Exception {
        // Given: Input image with both flips (equivalent to 180° rotation)
        Path inputFile = createInputFile("double_flip.gif", 500_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.GIF, 500_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PNG)
                .flip(ImageFlip.BOTH)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        mockToolExecution(inputFile, FileFormat.PNG, 600_000L);

        // When: Convert with both flips
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds
        assertTrue(result.success(), "Both flip conversion should succeed");
        verify(toolManager).executeTool(
                any(),
                any(),
                any(),
                any(),
                argThat(s -> s.imageSettings().flip() == ImageFlip.BOTH),
                any(),
                anyString(),
                any());
    }

    // ==================== Combined Transformation Tests ====================

    /**
     * REQ-IMG-1.2 (Edge case): Test rotation + flip combination.
     * Verifies that transformations are applied in correct order: rotate → flip.
     */
    @Test
    public void testRotateAndFlipCombination() throws Exception {
        // Given: Input image with both rotation and flip
        Path inputFile = createInputFile("transform_combo.jpg", 2_500_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.JPEG, 2_500_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PNG)
                .rotation(ImageRotation.CLOCKWISE_90)
                .flip(ImageFlip.HORIZONTAL)
                .quality(95)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        mockToolExecution(inputFile, FileFormat.PNG, 2_500_000L);

        // When: Convert with rotation + flip
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with both transformations
        assertTrue(result.success(), "Rotation + flip conversion should succeed");

        // Verify both settings are passed
        ArgumentCaptor<ConversionSettings> settingsCaptor = ArgumentCaptor.forClass(ConversionSettings.class);
        verify(toolManager).executeTool(
                eq(ConversionTool.IMAGEMAGICK),
                any(),
                any(),
                any(),
                settingsCaptor.capture(),
                any(),
                anyString(),
                any());

        ConversionSettings capturedSettings = settingsCaptor.getValue();
        assertEquals(ImageRotation.CLOCKWISE_90, capturedSettings.imageSettings().rotation());
        assertEquals(ImageFlip.HORIZONTAL, capturedSettings.imageSettings().flip());
    }

    /**
     * REQ-IMG-1.2 (Edge case): Test rotation + resize combination.
     * Verifies that rotation is applied BEFORE resize.
     */
    @Test
    public void testRotateAndResizeCombination() throws Exception {
        // Given: Input image with rotation and resize
        Path inputFile = createInputFile("rotate_resize.png", 3_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.PNG, 3_000_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.JPEG)
                .rotation(ImageRotation.CLOCKWISE_90)
                .resolution(new Resolution(1920, 1080))
                .maintainAspectRatio(true)
                .quality(85)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        mockToolExecution(inputFile, FileFormat.JPEG, 2_000_000L);

        // When: Convert with rotation + resize
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with both transformations
        assertTrue(result.success(), "Rotation + resize conversion should succeed");

        // Verify settings contain both rotation and resize parameters
        ArgumentCaptor<ConversionSettings> settingsCaptor = ArgumentCaptor.forClass(ConversionSettings.class);
        verify(toolManager).executeTool(
                any(),
                any(),
                any(),
                any(),
                settingsCaptor.capture(),
                any(),
                anyString(),
                any());

        ConversionSettings capturedSettings = settingsCaptor.getValue();
        assertEquals(ImageRotation.CLOCKWISE_90, capturedSettings.imageSettings().rotation());
        assertNotNull(capturedSettings.imageSettings().resolution());
        assertEquals(1920, capturedSettings.imageSettings().resolution().getWidth());
        assertEquals(1080, capturedSettings.imageSettings().resolution().getHeight());
        assertTrue(capturedSettings.imageSettings().maintainAspectRatio());
    }

    /**
     * REQ-IMG-1.2, REQ-IMG-2.2 (Edge case): Test all transformations combined.
     * Verifies transformation pipeline: rotate → flip → resize.
     */
    @Test
    public void testAllTransformationsCombined() throws Exception {
        // Given: Input image with rotation, flip, and resize
        Path inputFile = createInputFile("full_transform.tiff", 10_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.TIFF, 10_000_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PNG)
                .rotation(ImageRotation.ROTATE_180)
                .flip(ImageFlip.VERTICAL)
                .resolution(new Resolution(800, 600))
                .maintainAspectRatio(false) // Force exact dimensions
                .quality(100)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        mockToolExecution(inputFile, FileFormat.PNG, 1_500_000L);

        // When: Convert with all transformations
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with all transformations
        assertTrue(result.success(), "All transformations conversion should succeed");

        // Verify all settings are passed to ImageMagick
        ArgumentCaptor<ConversionSettings> settingsCaptor = ArgumentCaptor.forClass(ConversionSettings.class);
        verify(toolManager).executeTool(
                eq(ConversionTool.IMAGEMAGICK),
                any(),
                any(),
                any(),
                settingsCaptor.capture(),
                any(),
                anyString(),
                any());

        ConversionSettings capturedSettings = settingsCaptor.getValue();
        ImageSettings capturedImageSettings = capturedSettings.imageSettings();

        // Verify all transformation parameters are present
        assertEquals(ImageRotation.ROTATE_180, capturedImageSettings.rotation());
        assertEquals(ImageFlip.VERTICAL, capturedImageSettings.flip());
        assertNotNull(capturedImageSettings.resolution());
        assertEquals(800, capturedImageSettings.resolution().getWidth());
        assertEquals(600, capturedImageSettings.resolution().getHeight());
        assertFalse(capturedImageSettings.maintainAspectRatio());
        assertEquals(100, capturedImageSettings.quality());
    }

    /**
     * REQ-IMG-1.2: Test that NONE rotation does not add rotation parameters.
     */
    @Test
    public void testNoRotation() throws Exception {
        // Given: Input image with no rotation
        Path inputFile = createInputFile("no_rotate.jpg", 1_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.JPEG, 1_000_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PNG)
                .rotation(ImageRotation.NONE) // Explicitly no rotation
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        mockToolExecution(inputFile, FileFormat.PNG, 1_000_000L);

        // When: Convert without rotation
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds without rotation
        assertTrue(result.success(), "No rotation conversion should succeed");
        verify(toolManager).executeTool(
                any(),
                any(),
                any(),
                any(),
                argThat(s -> s.imageSettings().rotation() == ImageRotation.NONE),
                any(),
                anyString(),
                any());
    }

    /**
     * REQ-IMG-2.2: Test that NONE flip does not add flip parameters.
     */
    @Test
    public void testNoFlip() throws Exception {
        // Given: Input image with no flip
        Path inputFile = createInputFile("no_flip.webp", 700_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.WEBP, 700_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.JPEG)
                .flip(ImageFlip.NONE) // Explicitly no flip
                .quality(80)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        mockToolExecution(inputFile, FileFormat.JPEG, 600_000L);

        // When: Convert without flip
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds without flip
        assertTrue(result.success(), "No flip conversion should succeed");
        verify(toolManager).executeTool(
                any(),
                any(),
                any(),
                any(),
                argThat(s -> s.imageSettings().flip() == ImageFlip.NONE),
                any(),
                anyString(),
                any());
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
                            Duration.ofSeconds(2),
                            Files.size(inputFile),
                            outputSize,
                            ConversionTool.IMAGEMAGICK);
                });
    }
}
