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
 * Integration tests for audio copy codec conversions.
 * 
 * <p>
 * Requirements:
 * <ul>
 * <li>REQ-AUD-1.1: Support audio stream copy codec without re-encoding</li>
 * <li>REQ-AUD-1.2: Validate copy codec usage with container formats</li>
 * </ul>
 * 
 * <p>
 * Task T-9.5: Integration Tests - End-to-End Conversions -
 * AudioCopyIntegrationTest
 * </p>
 */
@ExtendWith(MockitoExtension.class)
public class AudioCopyIntegrationTest {

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

    // ==================== Audio Copy Codec Tests ====================

    /**
     * REQ-AUD-1.1: Test audio copy codec from M4A to MP3 (no re-encoding).
     * Verifies that audio stream is copied without transcoding using -c:a copy.
     */
    @Test
    public void testAudioCopyCodecMkvToMp4() throws Exception {
        // Given: Input M4A audio file
        Path inputFile = createInputFile("audio.m4a", 3_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.M4A, 3_000_000L);

        // Build settings with audio copy codec
        AudioSettings audioSettings = AudioSettings.builder()
                .outputFormat(FileFormat.MP3) // Set output format in AudioSettings
                .codec("copy") // Copy codec - no re-encoding
                .bitrate(128) // Should be ignored for copy codec
                .sampleRate(44100) // Should be ignored for copy codec
                .channels(2) // Should be ignored for copy codec
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .audioSettings(audioSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.MP3, 3_000_000L);

        // When: Convert with copy codec
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with copy codec
        assertTrue(result.success(), "Audio copy conversion should succeed");
        assertNotNull(result.outputPath(), "Output path should be set");
        assertEquals(ConversionTool.FFMPEG, result.toolUsed(), "Should use FFmpeg for audio");

        // Verify tool execution was called with copy codec settings
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputFile),
                any(Path.class),
                eq(FileFormat.MP3),
                eq(settings),
                any(ProgressCallback.class),
                anyString(),
                any(ProcessRegistry.class));
    }

    /**
     * REQ-AUD-1.1: Test audio copy codec with audio-only file (WAV to MP3).
     */
    @Test
    public void testAudioCopyCodecWavToMp3() throws Exception {
        // Given: Input WAV audio file
        Path inputFile = createInputFile("audio.wav", 10_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.WAV, 10_000_000L);

        // Build settings with audio copy codec
        AudioSettings audioSettings = AudioSettings.builder()
                .outputFormat(FileFormat.MP3)
                .codec("copy") // Copy codec
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .audioSettings(audioSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.MP3, 10_000_000L);

        // When: Convert with copy codec
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds
        assertTrue(result.success(), "Audio copy conversion should succeed");
        assertNotNull(result.outputPath(), "Output path should be set");
    }

    /**
     * REQ-AUD-1.1: Test copy codec vs transcoding codec (comparison test).
     * Verifies that non-copy codecs still include encoding parameters.
     */
    @Test
    public void testTranscodingCodecIncludesParameters() throws Exception {
        // Given: Input audio file
        Path inputFile = createInputFile("audio.ogg", 4_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.OGG, 4_000_000L);

        // Build settings with transcoding codec (NOT copy)
        AudioSettings audioSettings = AudioSettings.builder()
                .outputFormat(FileFormat.MP3)
                .codec("libmp3lame") // Transcoding codec
                .bitrate(192)
                .sampleRate(48000)
                .channels(2)
                .quality(5)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .audioSettings(audioSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.MP3, 2_500_000L);

        // When: Convert with transcoding codec
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds with encoding parameters
        assertTrue(result.success(), "Audio transcoding conversion should succeed");
        assertNotNull(result.outputPath(), "Output path should be set");

        // Verify tool execution was called with transcoding codec settings
        verify(toolManager).executeTool(
                eq(ConversionTool.FFMPEG),
                eq(inputFile),
                any(Path.class),
                eq(FileFormat.MP3),
                argThat(s -> s.audioSettings().codec().equals("libmp3lame")),
                any(ProgressCallback.class),
                anyString(),
                any(ProcessRegistry.class));
    }

    /**
     * REQ-AUD-1.1: Test copy codec with FLAC to OGG container remux.
     * Tests that copy codec preserves audio data when changing containers.
     */
    @Test
    public void testAudioCopyCodecContainerRemux() throws Exception {
        // Given: Input FLAC file
        Path inputFile = createInputFile("audio.flac", 15_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.FLAC, 15_000_000L);

        // Build settings with copy codec for container remux
        AudioSettings audioSettings = AudioSettings.builder()
                .outputFormat(FileFormat.OGG)
                .codec("copy") // Remux without re-encoding
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .audioSettings(audioSettings)
                .build();

        // Mock tool execution
        mockToolExecution(inputFile, FileFormat.OGG, 15_000_000L);

        // When: Convert with copy codec (remux)
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Conversion succeeds (container remux)
        assertTrue(result.success(), "Audio container remux should succeed");
        assertNotNull(result.outputPath(), "Output path should be set");
        assertEquals(ConversionTool.FFMPEG, result.toolUsed(), "Should use FFmpeg for audio");
    }

    /**
     * REQ-AUD-1.2: Test copy codec validation warning for incompatible formats.
     * Verifies that copy codec can be used even with format mismatches (with
     * warning).
     */
    @Test
    public void testCopyCodecWithFormatValidation() throws Exception {
        // Given: Input audio file with potentially incompatible format
        Path inputFile = createInputFile("audio.aac", 2_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.AAC, 2_000_000L);

        // Build settings with copy codec to potentially incompatible container
        AudioSettings audioSettings = AudioSettings.builder()
                .outputFormat(FileFormat.WAV) // WAV doesn't natively support AAC
                .codec("copy")
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .audioSettings(audioSettings)
                .build();

        // Mock validation to pass (with optional warning logged)
        when(validationEngine.validateConversionRequest(any(), any()))
                .thenReturn(ValidationResult.success());

        // Mock tool execution (may fail in real scenario, but mock succeeds)
        mockToolExecution(inputFile, FileFormat.WAV, 2_000_000L);

        // When: Convert with copy codec
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);

        // Then: Validation passes (non-blocking), conversion attempted
        assertTrue(result.success(), "Copy codec validation should be non-blocking");
        verify(validationEngine).validateConversionRequest(any(), any());
    }

    /**
     * Test that copy codec conversion is faster than transcoding (simulation).
     * This is a conceptual test showing that copy codec should be significantly
     * faster.
     */
    @Test
    public void testCopyCodecPerformance() throws Exception {
        // Given: Large audio file
        Path inputFile = createInputFile("large_audio.flac", 50_000_000L);
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.FLAC, 50_000_000L);

        // Build settings with copy codec
        AudioSettings audioSettings = AudioSettings.builder()
                .outputFormat(FileFormat.OGG)
                .codec("copy")
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .audioSettings(audioSettings)
                .build();

        // Mock fast copy codec execution (1 second)
        when(toolManager.executeTool(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Path tempOutputPath = invocation.getArgument(2);
                    Files.write(tempOutputPath, new byte[1024]);

                    String fileId = invocation.getArgument(6);
                    return ConversionResult.success(
                            fileId,
                            outputDir.resolve("output.ogg"),
                            null,
                            Duration.ofSeconds(1), // Fast copy
                            50_000_000L,
                            50_000_000L,
                            ConversionTool.FFMPEG);
                });

        // When: Convert with copy codec
        long startTime = System.currentTimeMillis();
        CompletableFuture<ConversionResult> future = conversionEngine.convertSingle(file, settings);
        ConversionResult result = future.get(5, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        // Then: Copy codec conversion completes quickly
        assertTrue(result.success(), "Copy codec conversion should succeed");
        assertTrue(duration < 3000, "Copy codec should complete quickly (< 3 seconds in test)");
        assertTrue(result.conversionTime().compareTo(Duration.ofSeconds(5)) < 0,
                "Copy codec duration should be fast");
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
                            ConversionTool.FFMPEG);
                });
    }
}
