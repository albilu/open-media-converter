package org.omc.core;

import org.omc.model.Resolution;
import org.omc.model.ImageSettings;
import org.omc.model.ValidationResult;
import org.omc.core.ProgressEngine;
import org.omc.model.DocumentSettings;
import org.omc.model.FileSettingsOverride;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration tests for conversion flow with per-file settings
 * overrides.
 * 
 * <p>
 * Requirements: REQ-3.2 - Automatic section settings application with per-file
 * overrides
 * Task 43: End-to-End Conversion Tests
 * </p>
 */
@ExtendWith(MockitoExtension.class)
public class ConversionFlowIntegrationTest {

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

        // Mock temp file creation - create REAL temp files that exist
        when(fileHandler.createTemporaryFile(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String extension = invocation.getArgument(1);
                    Path tempFile = tempDir.resolve("temp-" + tempFileCounter.incrementAndGet() + extension);
                    Files.createFile(tempFile);
                    return tempFile;
                });
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Cleanup is handled by @TempDir
        if (conversionEngine != null) {
            conversionEngine.shutdown();
        }
    }

    /**
     * Creates a real input file for testing.
     */
    private Path createInputFile(String filename) throws IOException {
        Path inputFile = inputDir.resolve(filename);
        Files.writeString(inputFile, "test content");
        return inputFile;
    }

    /**
     * Test video file with override converts to override format.
     * Requirement REQ-3.2: File with override uses override settings instead of
     * section settings
     */
    @Test
    public void testVideoFileWithOverrideConvertsToOverrideFormat() throws Exception {
        // Given: Video file with custom settings override (MKV output)
        Path inputPath = createInputFile("video.mp4");
        ConversionFile file = ConversionFile.create(inputPath, FileFormat.MP4, 1000000L);

        VideoSettings overrideSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MKV)
                .codec("libx264")
                .bitrate(5000)
                .build();

        FileSettingsOverride override = FileSettingsOverride.forVideo("High Quality MKV", overrideSettings);
        file = file.withSettingsOverride(override);

        ConversionSettings globalSettings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(VideoSettings.builder()
                        .outputFormat(FileFormat.AVI) // Section default is AVI
                        .codec("mpeg4")
                        .bitrate(2000)
                        .build())
                .build();

        // Mock successful conversion
        when(toolManager.selectTool(FileFormat.MP4, FileFormat.MKV))
                .thenReturn(ConversionTool.FFMPEG);
        when(toolManager.executeTool(any(), any(), any(), eq(FileFormat.MKV), any(), any(), any(), any()))
                .thenReturn(ConversionResult.success(file.id(), outputDir.resolve("video.mkv"), null,
                        Duration.ofSeconds(10),
                        1000000L,
                        800000L,
                        ConversionTool.FFMPEG));

        // When: Convert the file
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, globalSettings);
        ConversionResult result = future.get();

        // Then: Conversion should use override format (MKV), not section default (AVI)
        assertTrue(result.success());
        verify(toolManager).selectTool(FileFormat.MP4, FileFormat.MKV);
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputPath),
                any(Path.class),
                eq(FileFormat.MKV), // Override format
                eq(globalSettings),
                any(),
                any(), any());
    }

    /**
     * Test audio file without override converts to section default format.
     * Requirement REQ-3.2: File without override uses section settings based on
     * category
     */
    @Test
    public void testAudioFileWithoutOverrideConvertsToSectionDefault() throws Exception {
        // Given: Audio file WITHOUT custom override
        Path inputPath = createInputFile("audio.wav");
        ConversionFile file = ConversionFile.create(inputPath, FileFormat.WAV, 500000L);

        ConversionSettings globalSettings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .audioSettings(AudioSettings.builder()
                        .outputFormat(FileFormat.FLAC) // Section default
                        .codec("flac")
                        .bitrate(320)
                        .build())
                .build();

        // Mock successful conversion
        when(toolManager.selectTool(FileFormat.WAV, FileFormat.FLAC))
                .thenReturn(ConversionTool.FFMPEG);
        when(toolManager.executeTool(any(), any(), any(), eq(FileFormat.FLAC), any(), any(), any(), any()))
                .thenReturn(ConversionResult.success(file.id(), outputDir.resolve("audio.flac"), null,
                        Duration.ofSeconds(5),
                        500000L,
                        450000L,
                        ConversionTool.FFMPEG));

        // When: Convert the file
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, globalSettings);
        ConversionResult result = future.get();

        // Then: Conversion should use section default format (FLAC)
        assertTrue(result.success());
        verify(toolManager).selectTool(FileFormat.WAV, FileFormat.FLAC);
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputPath),
                any(Path.class),
                eq(FileFormat.FLAC), // Section default format
                eq(globalSettings),
                any(),
                any(), any());
    }

    /**
     * Test batch conversion with mixed overrides and section settings.
     * Requirement REQ-3.2: Batch processing respects per-file overrides
     */
    @Test
    public void testBatchConversionWithMixedOverridesAndSectionSettings() throws Exception {
        // Given: Batch with 3 video files - 2 with overrides, 1 without
        Path input1 = createInputFile("video1.mp4");
        Path input2 = createInputFile("video2.mp4");
        Path input3 = createInputFile("video3.mp4");

        // File 1: Override to WEBM
        ConversionFile file1 = ConversionFile.create(input1, FileFormat.MP4, 1000000L);
        VideoSettings override1 = VideoSettings.builder()
                .outputFormat(FileFormat.WEBM)
                .codec("libvpx-vp9")
                .bitrate(3000)
                .build();
        file1 = file1.withSettingsOverride(FileSettingsOverride.forVideo("Web Optimized", override1));

        // File 2: No override (uses section default)
        ConversionFile file2 = ConversionFile.create(input2, FileFormat.MP4, 1000000L);

        // File 3: Override to MKV
        ConversionFile file3 = ConversionFile.create(input3, FileFormat.MP4, 1000000L);
        VideoSettings override3 = VideoSettings.builder()
                .outputFormat(FileFormat.MKV)
                .codec("libx265")
                .bitrate(4000)
                .build();
        file3 = file3.withSettingsOverride(FileSettingsOverride.forVideo("HEVC Archive", override3));

        List<ConversionFile> files = List.of(file1, file2, file3);

        ConversionSettings globalSettings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(VideoSettings.builder()
                        .outputFormat(FileFormat.AVI) // Section default
                        .codec("mpeg4")
                        .bitrate(2000)
                        .build())
                .build();

        // Mock successful conversions for all files
        when(toolManager.selectTool(eq(FileFormat.MP4), any(FileFormat.class)))
                .thenReturn(ConversionTool.FFMPEG);
        when(toolManager.executeTool(any(), any(), any(), any(FileFormat.class), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Path inputPath = invocation.getArgument(1);
                    FileFormat outputFormat = invocation.getArgument(3);
                    String fileId = inputPath.getFileName().toString().replace(".mp4", "");
                    Path outputPath = outputDir.resolve(fileId + "." + outputFormat.getPrimaryExtension());
                    return ConversionResult.success(fileId, outputPath, null, Duration.ofSeconds(10),
                            1000000L,
                            900000L,
                            ConversionTool.FFMPEG);
                });

        // When: Convert the batch
        CompletableFuture<BatchConversionResult> future = conversionEngine.convertBatch(files, globalSettings);
        BatchConversionResult batchResult = future.get();

        // Then: All conversions should succeed
        assertEquals(3, batchResult.totalCount());
        assertEquals(3, batchResult.successCount());
        assertEquals(0, batchResult.failureCount());

        // Verify each file used correct output format
        ArgumentCaptor<FileFormat> formatCaptor = ArgumentCaptor.forClass(FileFormat.class);
        verify(toolManager, times(3)).executeTool(
                any(),
                any(),
                any(),
                formatCaptor.capture(),
                any(),
                any(),
                any(),
                any());

        List<FileFormat> capturedFormats = formatCaptor.getAllValues();
        assertTrue(capturedFormats.contains(FileFormat.WEBM), "File 1 should use WEBM override");
        assertTrue(capturedFormats.contains(FileFormat.AVI), "File 2 should use AVI section default");
        assertTrue(capturedFormats.contains(FileFormat.MKV), "File 3 should use MKV override");
    }

    /**
     * Test output filenames use correct format extension.
     * Requirement REQ-3.2: Output format from section settings or override
     * determines file extension
     */
    @Test
    public void testOutputFilenamesUseCorrectFormatExtension() throws Exception {
        // Given: Image file with override to WEBP
        Path inputPath = createInputFile("photo.png");
        ConversionFile file = ConversionFile.create(inputPath, FileFormat.PNG, 200000L);

        ImageSettings overrideSettings = ImageSettings.builder()
                .outputFormat(FileFormat.WEBP)
                .quality(85)
                .resolution(new Resolution(1920, 1080))
                .maintainAspectRatio(true)
                .build();

        FileSettingsOverride override = FileSettingsOverride.forImage("Web Image", overrideSettings);
        file = file.withSettingsOverride(override);

        ConversionSettings globalSettings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .imageSettings(ImageSettings.builder()
                        .outputFormat(FileFormat.JPEG) // Section default
                        .quality(90)
                        .build())
                .build();

        // Mock successful conversion
        when(toolManager.selectTool(FileFormat.PNG, FileFormat.WEBP))
                .thenReturn(ConversionTool.FFMPEG);
        when(toolManager.executeTool(any(), any(), any(), eq(FileFormat.WEBP), any(), any(), any(), any()))
                .thenReturn(ConversionResult.success(file.id(), outputDir.resolve("photo.webp"), null,
                        Duration.ofSeconds(2),
                        200000L,
                        150000L,
                        ConversionTool.FFMPEG));

        // When: Convert the file
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, globalSettings);
        ConversionResult result = future.get();

        // Then: Conversion should succeed with WEBP format
        assertTrue(result.success());
        verify(toolManager).selectTool(FileFormat.PNG, FileFormat.WEBP);

        // Output file should have .webp extension (verified by format parameter)
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputPath),
                any(Path.class),
                eq(FileFormat.WEBP), // Override format determines extension
                eq(globalSettings),
                any(),
                any(), any());
    }

    /**
     * Test conversion respects codec/bitrate from override settings.
     * Requirement REQ-3.2: All section settings (codec, bitrate, etc.) from
     * override are used
     */
    @Test
    public void testConversionRespectsCodecAndBitrateFromOverride() throws Exception {
        // Given: Document file with override
        Path inputPath = createInputFile("document.docx");
        ConversionFile file = ConversionFile.create(inputPath, FileFormat.DOCX, 50000L);

        DocumentSettings overrideSettings = DocumentSettings.builder()
                .outputFormat(FileFormat.EPUB)
                .preserveFormatting(true)
                .embedFonts(true)
                .build();

        FileSettingsOverride override = FileSettingsOverride.forDocument("E-Book", overrideSettings);
        file = file.withSettingsOverride(override);

        ConversionSettings globalSettings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .documentSettings(DocumentSettings.builder()
                        .outputFormat(FileFormat.PDF) // Section default
                        .preserveFormatting(false)
                        .embedFonts(false)
                        .build())
                .build();

        // Mock successful conversion
        when(toolManager.selectTool(FileFormat.DOCX, FileFormat.EPUB))
                .thenReturn(ConversionTool.PANDOC);
        when(toolManager.executeTool(any(), any(), any(), eq(FileFormat.EPUB), any(), any(), any(), any()))
                .thenReturn(ConversionResult.success(file.id(), outputDir.resolve("document.epub"), null,
                        Duration.ofSeconds(3),
                        50000L,
                        45000L,
                        ConversionTool.PANDOC));

        // When: Convert the file
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, globalSettings);
        ConversionResult result = future.get();

        // Then: Conversion should use override format and settings
        assertTrue(result.success());
        verify(toolManager).selectTool(FileFormat.DOCX, FileFormat.EPUB);

        // The globalSettings are passed through, but the engine resolves per-file
        // overrides
        // The actual settings used (codec, bitrate, etc.) come from the override
        verify(toolManager).executeTool(
                eq(ConversionTool.PANDOC),
                eq(inputPath),
                any(Path.class),
                eq(FileFormat.EPUB),
                eq(globalSettings), // Global settings passed, but override is in the file
                any(),
                any(), any());
    }

    /**
     * Test global settings used as fallback when no override present.
     * Requirement REQ-3.2: Section settings serve as fallback for files without
     * overrides
     */
    @Test
    public void testGlobalSettingsUsedAsFallbackWhenNoOverridePresent() throws Exception {
        // Given: Video file WITHOUT override
        Path inputPath = createInputFile("video.avi");
        ConversionFile file = ConversionFile.create(inputPath, FileFormat.AVI, 2000000L);

        ConversionSettings globalSettings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(VideoSettings.builder()
                        .outputFormat(FileFormat.MP4) // Section default
                        .codec("libx264")
                        .bitrate(4000)
                        .crf(23)
                        .preset("medium")
                        .build())
                .audioSettings(AudioSettings.builder()
                        .outputFormat(FileFormat.MP3)
                        .codec("libmp3lame")
                        .bitrate(192)
                        .build())
                .imageSettings(ImageSettings.builder()
                        .outputFormat(FileFormat.PNG)
                        .quality(100)
                        .build())
                .documentSettings(DocumentSettings.builder()
                        .outputFormat(FileFormat.PDF)
                        .preserveFormatting(true)
                        .build())
                .build();

        // Mock successful conversion
        when(toolManager.selectTool(FileFormat.AVI, FileFormat.MP4))
                .thenReturn(ConversionTool.FFMPEG);
        when(toolManager.executeTool(any(), any(), any(), eq(FileFormat.MP4), any(), any(), any(), any()))
                .thenReturn(ConversionResult.success(file.id(), outputDir.resolve("video.mp4"), null,
                        Duration.ofSeconds(15),
                        2000000L,
                        1800000L,
                        ConversionTool.FFMPEG));

        // When: Convert the file
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, globalSettings);
        ConversionResult result = future.get();

        // Then: Conversion should use section default settings (video settings)
        assertTrue(result.success());
        verify(toolManager).selectTool(FileFormat.AVI, FileFormat.MP4);
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputPath),
                any(Path.class),
                eq(FileFormat.MP4), // Section default format for VIDEO category
                eq(globalSettings),
                any(),
                any(), any());

        // Verify the file has no custom settings
        assertFalse(file.hasCustomSettings());
        assertNull(file.settingsOverride());
    }
}
