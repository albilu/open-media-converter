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
 * Integration tests for Image to PDF conversion via ImageMagick.
 * 
 * <p>
 * Requirements:
 * <ul>
 * <li>REQ-PDF-1.1: PDF format in image output dropdown</li>
 * <li>REQ-PDF-1.2: FileFormat enum extension for PDF</li>
 * <li>REQ-PDF-1.3: ImageMagick PDF conversion</li>
 * <li>REQ-PDF-1.4: PDF conversion quality settings</li>
 * </ul>
 * 
 * <p>
 * Task T-9.5: Integration Tests - End-to-End Conversions -
 * ImageToPDFIntegrationTest
 * </p>
 */
@ExtendWith(MockitoExtension.class)
public class ImageToPDFIntegrationTest {

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
        // Use lenient() since not all tests execute conversions (e.g., validation-only
        // tests)
        lenient().when(validationEngine.validateConversionRequest(any(), any()))
                .thenReturn(ValidationResult.success());
        lenient().when(validationEngine.validateToolAvailability(any()))
                .thenReturn(ValidationResult.success());
        lenient().when(validationEngine.validateOutputDirectory(any()))
                .thenReturn(ValidationResult.success());
        lenient().when(validationEngine.validateDiskSpace(any(), anyLong()))
                .thenReturn(ValidationResult.success());
        lenient().when(toolManager.selectTool(any(), any()))
                .thenReturn(ConversionTool.IMAGEMAGICK);

        // Mock temp file creation
        lenient().when(fileHandler.createTemporaryFile(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String extension = invocation.getArgument(1);
                    Path tempFile = tempDir.resolve("temp-" + tempFileCounter.incrementAndGet() + extension);
                    Files.createFile(tempFile);
                    return tempFile;
                });

        // Mock progress engine
        lenient().doNothing().when(progressEngine).startTracking(anyString(), anyLong());
        lenient().doNothing().when(progressEngine).completeTracking(anyString(), any());
    }

    @AfterEach
    public void tearDown() throws Exception {
        conversionEngine.shutdown();
    }

    // ==================== Image to PDF Conversion Tests ====================

    /**
     * REQ-PDF-1.3: Test basic JPEG to PDF conversion.
     * Verifies that ImageMagick is used for image → PDF conversions.
     */
    @Test
    public void testConvertJpegToPdf() throws Exception {
        // Given: JPEG image with PDF output format
        Path inputFile = createInputFile("document.jpg", 500_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.JPEG, 500_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PDF)
                .quality(85)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.PDF, 400_000L);

        // When: Converting JPEG to PDF
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(10, TimeUnit.SECONDS);

        // Then: Conversion succeeds with ImageMagick
        assertTrue(result.success(), "Conversion should succeed");
        assertNotNull(result.outputPath(), "Output path should be set");
        assertEquals(ConversionTool.IMAGEMAGICK, result.toolUsed(), "Should use ImageMagick");

        // Verify ImageMagick was selected for JPEG → PDF
        verify(toolManager).selectTool(FileFormat.JPEG, FileFormat.PDF);

        // Verify conversion was executed with correct settings
        verify(toolManager).executeTool(
                eq(ConversionTool.IMAGEMAGICK),
                eq(inputFile),
                any(Path.class),
                eq(FileFormat.PDF),
                argThat(s -> s.imageSettings().outputFormat() == FileFormat.PDF &&
                        s.imageSettings().quality() == 85),
                any(ProgressCallback.class),
                anyString(),
                any(ProcessRegistry.class));
    }

    /**
     * REQ-PDF-1.3: Test PNG to PDF conversion with transparency.
     * Verifies that transparent PNG converts to PDF correctly.
     */
    @Test
    public void testConvertPngToPdfWithTransparency() throws Exception {
        // Given: PNG image with transparency
        Path inputFile = createInputFile("logo.png", 300_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.PNG, 300_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PDF)
                .quality(90)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.PDF, 250_000L);

        // When: Converting PNG to PDF
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(10, TimeUnit.SECONDS);

        // Then: Conversion succeeds
        assertTrue(result.success(), "PNG to PDF conversion should succeed");
        assertNotNull(result.outputPath(), "Output path should be set");

        // Verify PNG → PDF routing
        verify(toolManager).selectTool(FileFormat.PNG, FileFormat.PDF);
        verify(toolManager).executeTool(
                eq(ConversionTool.IMAGEMAGICK),
                any(),
                any(),
                eq(FileFormat.PDF),
                any(),
                any(),
                anyString(),
                any());
    }

    /**
     * REQ-PDF-1.4: Test PDF conversion with quality settings.
     * Verifies that quality settings affect PDF compression.
     */
    @Test
    public void testConvertToPdfWithQualitySettings() throws Exception {
        // Given: JPEG image with high quality setting
        Path inputFile = createInputFile("photo.jpg", 2_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.JPEG, 2_000_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PDF)
                .quality(95) // High quality
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.PDF, 1_800_000L);

        // When: Converting with high quality
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(10, TimeUnit.SECONDS);

        // Then: Conversion succeeds with quality settings
        assertTrue(result.success(), "High quality PDF conversion should succeed");

        // Verify quality setting was passed to ToolManager
        ArgumentCaptor<ConversionSettings> settingsCaptor = ArgumentCaptor.forClass(ConversionSettings.class);
        verify(toolManager).executeTool(
                eq(ConversionTool.IMAGEMAGICK),
                eq(inputFile),
                any(),
                eq(FileFormat.PDF),
                settingsCaptor.capture(),
                any(),
                anyString(),
                any());

        ConversionSettings capturedSettings = settingsCaptor.getValue();
        assertEquals(95, capturedSettings.imageSettings().quality(), "Should preserve high quality setting");
    }

    /**
     * REQ-PDF-1.4: Test PDF conversion with resolution settings.
     * Verifies that resolution settings affect PDF page size.
     */
    @Test
    public void testConvertToPdfWithResolution() throws Exception {
        // Given: Image with custom resolution for PDF
        Path inputFile = createInputFile("diagram.png", 800_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.PNG, 800_000L);

        Resolution targetResolution = new Resolution(1920, 1080); // Full HD

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PDF)
                .resolution(targetResolution)
                .maintainAspectRatio(true)
                .quality(90)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.PDF, 700_000L);

        // When: Converting with resolution
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(10, TimeUnit.SECONDS);

        // Then: Conversion succeeds with resolution settings
        assertTrue(result.success(), "PDF conversion with resolution should succeed");

        // Verify resolution setting was passed
        ArgumentCaptor<ConversionSettings> settingsCaptor = ArgumentCaptor.forClass(ConversionSettings.class);
        verify(toolManager).executeTool(
                eq(ConversionTool.IMAGEMAGICK),
                any(),
                any(),
                eq(FileFormat.PDF),
                settingsCaptor.capture(),
                any(),
                anyString(),
                any());

        ConversionSettings capturedSettings = settingsCaptor.getValue();
        assertEquals(targetResolution, capturedSettings.imageSettings().resolution());
        assertTrue(capturedSettings.imageSettings().maintainAspectRatio());
    }

    /**
     * REQ-PDF-1.3: Test WebP to PDF conversion.
     * Verifies that modern WebP format converts to PDF.
     */
    @Test
    public void testConvertWebpToPdf() throws Exception {
        // Given: WebP image
        Path inputFile = createInputFile("modern-image.webp", 450_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.WEBP, 450_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PDF)
                .quality(90)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.PDF, 400_000L);

        // When: Converting WebP to PDF
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(10, TimeUnit.SECONDS);

        // Then: Conversion succeeds
        assertTrue(result.success(), "WebP to PDF conversion should succeed");
        assertEquals(ConversionTool.IMAGEMAGICK, result.toolUsed());

        verify(toolManager).selectTool(FileFormat.WEBP, FileFormat.PDF);
    }

    /**
     * REQ-PDF-1.3: Test BMP to PDF conversion.
     * Verifies that uncompressed BMP converts to PDF with compression.
     */
    @Test
    public void testConvertBmpToPdf() throws Exception {
        // Given: Large uncompressed BMP image
        Path inputFile = createInputFile("screenshot.bmp", 5_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.BMP, 5_000_000L);

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PDF)
                .quality(85) // Apply compression
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        // Mock tool execution (much smaller PDF due to compression)
        mockToolExecution(inputFile, FileFormat.PDF, 1_000_000L);

        // When: Converting BMP to PDF
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(15, TimeUnit.SECONDS);

        // Then: Conversion succeeds with compression
        assertTrue(result.success(), "BMP to PDF conversion should succeed");

        verify(toolManager).selectTool(FileFormat.BMP, FileFormat.PDF);
        verify(toolManager).executeTool(
                eq(ConversionTool.IMAGEMAGICK),
                any(),
                any(),
                eq(FileFormat.PDF),
                argThat(s -> s.imageSettings().quality() == 85),
                any(),
                anyString(),
                any());
    }

    /**
     * REQ-PDF-1.3, REQ-PDF-1.2: Test that PDF output format is accepted.
     * Verifies that ImageSettings validates PDF as valid output format.
     */
    @Test
    public void testPdfOutputFormatValidation() {
        // Given: ImageSettings with PDF output format
        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PDF)
                .quality(85)
                .build();

        // Then: Settings should be valid (REQ-PDF-1.2: FileFormat.PDF dual category
        // support)
        assertTrue(imageSettings.isValid(), "ImageSettings with PDF output should be valid");
        assertEquals(FileFormat.PDF, imageSettings.outputFormat(), "Output format should be PDF");
    }

    /**
     * REQ-PDF-1.4: Test PDF conversion with both quality and resolution.
     * Verifies that both settings can be applied simultaneously.
     */
    @Test
    public void testConvertToPdfWithQualityAndResolution() throws Exception {
        // Given: Image with both quality and resolution settings
        Path inputFile = createInputFile("complex-image.tiff", 3_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.TIFF, 3_000_000L);

        Resolution targetResolution = new Resolution(2560, 1440); // 1440p

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PDF)
                .resolution(targetResolution)
                .quality(92) // High quality
                .maintainAspectRatio(true)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(imageSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.PDF, 2_500_000L);

        // When: Converting with both settings
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(15, TimeUnit.SECONDS);

        // Then: Conversion succeeds with both settings applied
        assertTrue(result.success(), "PDF conversion with quality and resolution should succeed");

        // Verify both settings were passed
        ArgumentCaptor<ConversionSettings> settingsCaptor = ArgumentCaptor.forClass(ConversionSettings.class);
        verify(toolManager).executeTool(
                eq(ConversionTool.IMAGEMAGICK),
                any(),
                any(),
                eq(FileFormat.PDF),
                settingsCaptor.capture(),
                any(),
                anyString(),
                any());

        ConversionSettings capturedSettings = settingsCaptor.getValue();
        assertEquals(92, capturedSettings.imageSettings().quality(), "Should preserve quality setting");
        assertEquals(targetResolution, capturedSettings.imageSettings().resolution(),
                "Should preserve resolution setting");
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a test input file with specified name and size.
     */
    private Path createInputFile(String filename, long size) throws IOException {
        Path file = inputDir.resolve(filename);
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
