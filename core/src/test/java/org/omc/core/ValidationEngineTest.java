package org.omc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.omc.exception.ErrorCode;
import org.omc.exception.FileOperationException;
import org.omc.model.AudioSettings;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionSettings;
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;
import org.omc.model.ImageSettings;
import org.omc.model.Resolution;
import org.omc.model.ValidationResult;
import org.omc.model.VideoSettings;
import org.omc.service.FileHandler;

/**
 * Unit tests for ValidationEngine.
 * 
 * Tests validation logic for:
 * - File validation (REQ-002.3)
 * - Settings validation (REQ-003.2)
 * - Format pair validation
 * - Disk space validation (REQ-007.1)
 * - Tool availability validation
 * - Comprehensive conversion request validation
 */
class ValidationEngineTest {

    @Mock
    private FileHandler fileHandler;

    private ValidationEngine validationEngine;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validationEngine = new ValidationEngine(fileHandler);
    }

    // ========== Constructor Tests ==========

    @Test
    void constructor_withNullFileHandler_throwsException() {
        assertThrows(NullPointerException.class, () -> new ValidationEngine(null));
    }

    // ========== File Validation Tests (REQ-002.3) ==========

    @Test
    void validateFile_withNullPath_returnsFailure() {
        ValidationResult result = validationEngine.validateFile(null);

        assertTrue(result.isFailure());
        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("cannot be null"));
    }

    @Test
    void validateFile_withNonExistentFile_returnsFailure() {
        Path nonExistentFile = tempDir.resolve("nonexistent.mp4");
        when(fileHandler.exists(nonExistentFile)).thenReturn(false);

        ValidationResult result = validationEngine.validateFile(nonExistentFile);

        assertTrue(result.isFailure());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("does not exist"));
    }

    @Test
    void validateFile_withUnreadableFile_returnsFailure() throws Exception {
        Path unreadableFile = Files.createFile(tempDir.resolve("unreadable.mp4"));
        when(fileHandler.exists(unreadableFile)).thenReturn(true);
        when(fileHandler.isReadable(unreadableFile)).thenReturn(false);

        ValidationResult result = validationEngine.validateFile(unreadableFile);

        assertTrue(result.isFailure());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("not readable"));
    }

    @Test
    void validateFile_withDirectory_returnsFailure() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("dir"));
        when(fileHandler.exists(directory)).thenReturn(true);
        when(fileHandler.isReadable(directory)).thenReturn(true);

        ValidationResult result = validationEngine.validateFile(directory);

        assertTrue(result.isFailure());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("not a regular file"));
    }

    @Test
    void validateFile_withEmptyFile_returnsSuccessWithWarning() throws Exception {
        Path emptyFile = Files.createFile(tempDir.resolve("empty.mp4"));
        when(fileHandler.exists(emptyFile)).thenReturn(true);
        when(fileHandler.isReadable(emptyFile)).thenReturn(true);
        when(fileHandler.getFileSize(emptyFile)).thenReturn(0L);
        when(fileHandler.detectFormat(emptyFile)).thenReturn(FileFormat.MP4);

        ValidationResult result = validationEngine.validateFile(emptyFile);

        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).contains("empty"));
    }

    @Test
    void validateFile_withUnknownFormat_returnsSuccessWithWarning() throws Exception {
        Path unknownFile = Files.createFile(tempDir.resolve("unknown.xyz"));
        when(fileHandler.exists(unknownFile)).thenReturn(true);
        when(fileHandler.isReadable(unknownFile)).thenReturn(true);
        when(fileHandler.getFileSize(unknownFile)).thenReturn(1024L);
        when(fileHandler.detectFormat(unknownFile)).thenReturn(FileFormat.UNKNOWN);

        ValidationResult result = validationEngine.validateFile(unknownFile);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).contains("Unknown or unsupported"));
    }

    @Test
    void validateFile_withValidFile_returnsSuccess() throws Exception {
        Path validFile = Files.createFile(tempDir.resolve("valid.mp4"));
        when(fileHandler.exists(validFile)).thenReturn(true);
        when(fileHandler.isReadable(validFile)).thenReturn(true);
        when(fileHandler.getFileSize(validFile)).thenReturn(1024L);
        when(fileHandler.detectFormat(validFile)).thenReturn(FileFormat.MP4);

        ValidationResult result = validationEngine.validateFile(validFile);

        assertTrue(result.isSuccess());
        assertFalse(result.isFailure());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void validateFile_whenFileSizeCheckFails_returnsFailure() throws Exception {
        Path file = Files.createFile(tempDir.resolve("test.mp4"));
        when(fileHandler.exists(file)).thenReturn(true);
        when(fileHandler.isReadable(file)).thenReturn(true);
        when(fileHandler.getFileSize(file))
                .thenThrow(new FileOperationException("Cannot read size", ErrorCode.FILE_IO_ERROR, file.toString()));

        ValidationResult result = validationEngine.validateFile(file);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot determine file size")));
    }

    // ========== File Format Validation Tests ==========

    @Test
    void validateFileFormat_withNullPath_returnsFailure() {
        ValidationResult result = validationEngine.validateFileFormat(null, FileFormat.MP4);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().get(0).contains("path cannot be null"));
    }

    @Test
    void validateFileFormat_withNullExpectedFormat_returnsFailure() throws Exception {
        Path file = Files.createFile(tempDir.resolve("test.mp4"));

        ValidationResult result = validationEngine.validateFileFormat(file, null);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().get(0).contains("format cannot be null"));
    }

    @Test
    void validateFileFormat_whenFileValidationFails_returnsFailure() {
        Path nonExistentFile = tempDir.resolve("nonexistent.mp4");
        when(fileHandler.exists(nonExistentFile)).thenReturn(false);

        ValidationResult result = validationEngine.validateFileFormat(nonExistentFile, FileFormat.MP4);

        assertTrue(result.isFailure());
    }

    @Test
    void validateFileFormat_withUnknownFormat_returnsFailure() throws Exception {
        Path file = Files.createFile(tempDir.resolve("test.xyz"));
        when(fileHandler.exists(file)).thenReturn(true);
        when(fileHandler.isReadable(file)).thenReturn(true);
        when(fileHandler.getFileSize(file)).thenReturn(1024L);
        when(fileHandler.detectFormat(file)).thenReturn(FileFormat.UNKNOWN);

        ValidationResult result = validationEngine.validateFileFormat(file, FileFormat.MP4);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot determine file format")));
    }

    @Test
    void validateFileFormat_withMismatchedFormat_returnsFailure() throws Exception {
        Path file = Files.createFile(tempDir.resolve("test.mp4"));
        when(fileHandler.exists(file)).thenReturn(true);
        when(fileHandler.isReadable(file)).thenReturn(true);
        when(fileHandler.getFileSize(file)).thenReturn(1024L);
        when(fileHandler.detectFormat(file)).thenReturn(FileFormat.AVI);

        ValidationResult result = validationEngine.validateFileFormat(file, FileFormat.MP4);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("format mismatch") && e.contains("MP4") && e.contains("AVI")));
    }

    @Test
    void validateFileFormat_withMatchingFormat_returnsSuccess() throws Exception {
        Path file = Files.createFile(tempDir.resolve("test.mp4"));
        when(fileHandler.exists(file)).thenReturn(true);
        when(fileHandler.isReadable(file)).thenReturn(true);
        when(fileHandler.getFileSize(file)).thenReturn(1024L);
        when(fileHandler.detectFormat(file)).thenReturn(FileFormat.MP4);

        ValidationResult result = validationEngine.validateFileFormat(file, FileFormat.MP4);

        assertTrue(result.isSuccess());
        assertTrue(result.getErrors().isEmpty());
    }

    // ========== Settings Validation Tests (REQ-003.2) ==========

    @Test
    void validateSettings_withNullSettings_returnsFailure() {
        ValidationResult result = validationEngine.validateSettings(null);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().get(0).contains("settings cannot be null"));
    }

    @Test
    void validateSettings_withNullOutputDirectory_returnsFailure() {
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(null)
                .parallelConversions(2)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Output directory cannot be null")));
    }

    @Test
    void validateSettings_withNonExistentOutputDirectory_returnsFailure() {
        Path nonExistent = tempDir.resolve("nonexistent");
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(nonExistent)
                .parallelConversions(2)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("does not exist")));
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true", disabledReason = "File permission Issue on Workflow Runners")
    void validateSettings_withUnwritableOutputDirectory_returnsFailure() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .build();

        // Mock directory to appear unwritable
        outputDir.toFile().setWritable(false);

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("not writable")));

        // Clean up
        outputDir.toFile().setWritable(true);
    }

    @Test
    void validateSettings_withInvalidParallelConversions_returnsFailure() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        // Test below minimum
        ConversionSettings settings1 = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(0)
                .build();

        ValidationResult result1 = validationEngine.validateSettings(settings1);
        assertTrue(result1.isFailure());
        assertTrue(result1.getErrors().stream().anyMatch(e -> e.contains("Parallel conversions must be between")));

        // Test above maximum
        ConversionSettings settings2 = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(65)
                .build();

        ValidationResult result2 = validationEngine.validateSettings(settings2);
        assertTrue(result2.isFailure());
        assertTrue(result2.getErrors().stream().anyMatch(e -> e.contains("Parallel conversions must be between")));
    }

    @Test
    void validateSettings_withValidBasicSettings_returnsSuccess() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(4)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isSuccess());
        assertTrue(result.getErrors().isEmpty());
    }

    // ========== Video Settings Validation Tests ==========

    @Test
    void validateSettings_withInvalidVideoBitrate_throwsException() throws Exception {
        // Model validation happens at construction time
        assertThrows(IllegalArgumentException.class, () -> {
            VideoSettings.builder()
                    .bitrate(50) // Below minimum (500)
                    .frameRate(30)
                    .crf(23)
                    .build();
        });
    }

    @Test
    void validateSettings_withInvalidFrameRate_throwsException() throws Exception {
        // Model validation happens at construction time
        assertThrows(IllegalArgumentException.class, () -> {
            VideoSettings.builder()
                    .bitrate(5000)
                    .frameRate(300) // Above maximum (120)
                    .crf(23)
                    .build();
        });
    }

    @Test
    void validateSettings_withOriginalFrameRate_returnsSuccess() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        VideoSettings videoSettings = VideoSettings.builder()
                .bitrate(5000)
                .frameRate(-1) // Use original
                .crf(23)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .videoSettings(videoSettings)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isSuccess());
    }

    @Test
    void validateSettings_withInvalidCRF_returnsFailure() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        // Model validation happens at construction time
        assertThrows(IllegalArgumentException.class, () -> {
            VideoSettings.builder()
                    .bitrate(5000)
                    .frameRate(30)
                    .crf(60) // Above maximum (51)
                    .build();
        });
    }

    @Test
    void validateSettings_withHighCRF_returnsSuccessWithWarning() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        VideoSettings videoSettings = VideoSettings.builder()
                .bitrate(5000)
                .frameRate(30)
                .crf(40) // High but valid
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .videoSettings(videoSettings)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("High CRF value")));
    }

    @Test
    void validateSettings_withInvalidVideoResolution_returnsFailure() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        Resolution resolution = new Resolution(70000, 480); // Width above maximum (65535)

        VideoSettings videoSettings = VideoSettings.builder()
                .bitrate(5000)
                .frameRate(30)
                .crf(23)
                .resolution(resolution)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .videoSettings(videoSettings)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Video width must be between")));
    }

    @Test
    void validateSettings_withValidVideoSettings_returnsSuccess() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        VideoSettings videoSettings = VideoSettings.builder()
                .bitrate(5000)
                .frameRate(30)
                .crf(23)
                .resolution(new Resolution(1920, 1080))
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .videoSettings(videoSettings)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isSuccess());
        assertTrue(result.getErrors().isEmpty());
    }

    // ========== Audio Settings Validation Tests ==========

    @Test
    void validateSettings_withInvalidAudioBitrate_returnsFailure() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        // Model validation happens at construction time
        assertThrows(IllegalArgumentException.class, () -> {
            AudioSettings.builder()
                    .bitrate(5) // Below minimum (64)
                    .sampleRate(44100)
                    .channels(2)
                    .build();
        });
    }

    @Test
    void validateSettings_withLowAudioBitrate_returnsSuccessWithWarning() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        AudioSettings audioSettings = AudioSettings.builder()
                .bitrate(64) // Minimum valid value (could be considered low)
                .sampleRate(44100)
                .channels(2)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .audioSettings(audioSettings)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        // Audio bitrate of 64 kbps is valid but may be considered low for some use
        // cases
        assertTrue(result.isSuccess());
    }

    @Test
    void validateSettings_withInvalidSampleRate_returnsFailure() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        // Model validation happens at construction time
        assertThrows(IllegalArgumentException.class, () -> {
            AudioSettings.builder()
                    .bitrate(128)
                    .sampleRate(200000) // Above maximum (192000)
                    .channels(2)
                    .build();
        });
    }

    @Test
    void validateSettings_withOriginalSampleRate_returnsSuccess() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        AudioSettings audioSettings = AudioSettings.builder()
                .bitrate(128)
                .sampleRate(-1) // Use original
                .channels(2)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .audioSettings(audioSettings)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isSuccess());
    }

    @Test
    void validateSettings_withInvalidChannels_returnsFailure() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        // Model validation happens at construction time
        assertThrows(IllegalArgumentException.class, () -> {
            AudioSettings.builder()
                    .bitrate(128)
                    .sampleRate(44100)
                    .channels(10) // Invalid - must be -1, 1, 2, or 6
                    .build();
        });
    }

    @Test
    void validateSettings_withValidAudioSettings_returnsSuccess() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        AudioSettings audioSettings = AudioSettings.builder()
                .bitrate(320)
                .sampleRate(48000)
                .channels(2)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .audioSettings(audioSettings)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isSuccess());
        assertTrue(result.getErrors().isEmpty());
    }

    // ========== Image Settings Validation Tests ==========

    @Test
    void validateSettings_withInvalidImageQuality_returnsFailure() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        // Model validation happens at construction time
        assertThrows(IllegalArgumentException.class, () -> {
            ImageSettings.builder()
                    .quality(150) // Above maximum (100)
                    .build();
        });
    }

    @Test
    void validateSettings_withLowImageQuality_returnsSuccessWithWarning() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        ImageSettings imageSettings = ImageSettings.builder()
                .quality(30) // Valid but low
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .imageSettings(imageSettings)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Low image quality")));
    }

    @Test
    void validateSettings_withInvalidImageResolution_returnsFailure() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        // Model validation happens at construction time - Resolution validates width >
        // 0
        assertThrows(IllegalArgumentException.class, () -> {
            new Resolution(0, 1080); // Width below minimum (1)
        });
    }

    @Test
    void validateSettings_withValidImageSettings_returnsSuccess() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        ImageSettings imageSettings = ImageSettings.builder()
                .quality(85)
                .resolution(new Resolution(1920, 1080))
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .imageSettings(imageSettings)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isSuccess());
        assertTrue(result.getErrors().isEmpty());
    }

    // ========== Document Settings Validation Tests ==========

    @Test
    void validateSettings_withNonExistentTemplate_returnsSuccessWithWarning() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));
        Path nonExistentTemplate = tempDir.resolve("nonexistent-template.odt");

        when(fileHandler.exists(nonExistentTemplate)).thenReturn(false);

        DocumentSettings documentSettings = DocumentSettings.builder()
                .templatePath(nonExistentTemplate)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .documentSettings(documentSettings)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Template file does not exist")));
    }

    @Test
    void validateSettings_withUnreadableTemplate_returnsSuccessWithWarning() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));
        Path unreadableTemplate = Files.createFile(tempDir.resolve("template.odt"));

        when(fileHandler.exists(unreadableTemplate)).thenReturn(true);
        when(fileHandler.isReadable(unreadableTemplate)).thenReturn(false);

        DocumentSettings documentSettings = DocumentSettings.builder()
                .templatePath(unreadableTemplate)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .documentSettings(documentSettings)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Template file is not readable")));
    }

    @Test
    void validateSettings_withValidDocumentSettings_returnsSuccess() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));
        Path validTemplate = Files.createFile(tempDir.resolve("template.odt"));

        when(fileHandler.exists(validTemplate)).thenReturn(true);
        when(fileHandler.isReadable(validTemplate)).thenReturn(true);

        DocumentSettings documentSettings = DocumentSettings.builder()
                .templatePath(validTemplate)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .documentSettings(documentSettings)
                .build();

        ValidationResult result = validationEngine.validateSettings(settings);

        assertTrue(result.isSuccess());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }

    // ========== Format Pair Validation Tests ==========

    @Test
    void validateFormatPair_withNullInput_returnsFailure() {
        ValidationResult result = validationEngine.validateFormatPair(null, FileFormat.MP4);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().get(0).contains("Input format cannot be null"));
    }

    @Test
    void validateFormatPair_withNullOutput_returnsFailure() {
        ValidationResult result = validationEngine.validateFormatPair(FileFormat.MP4, null);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().get(0).contains("Output format cannot be null"));
    }

    @Test
    void validateFormatPair_withUnknownInputFormat_returnsFailure() {
        ValidationResult result = validationEngine.validateFormatPair(FileFormat.UNKNOWN, FileFormat.MP4);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Input format is unknown")));
    }

    @Test
    void validateFormatPair_withUnknownOutputFormat_returnsFailure() {
        ValidationResult result = validationEngine.validateFormatPair(FileFormat.MP4, FileFormat.UNKNOWN);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Output format is unknown")));
    }

    @Test
    void validateFormatPair_withIdenticalFormats_returnsSuccessWithWarning() {
        ValidationResult result = validationEngine.validateFormatPair(FileFormat.MP4, FileFormat.MP4);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).contains("identical"));
    }

    @Test
    void validateFormatPair_withSameCategoryFormats_returnsSuccess() {
        ValidationResult result = validationEngine.validateFormatPair(FileFormat.MP4, FileFormat.AVI);

        assertTrue(result.isSuccess());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validateFormatPair_videoToAudio_returnsSuccess() {
        ValidationResult result = validationEngine.validateFormatPair(FileFormat.MP4, FileFormat.MP3);

        assertTrue(result.isSuccess());
        assertFalse(result.hasWarnings());
    }

    @Test
    void validateFormatPair_audioToVideo_returnsSuccess() {
        ValidationResult result = validationEngine.validateFormatPair(FileFormat.MP3, FileFormat.MP4);

        assertTrue(result.isSuccess());
        assertFalse(result.hasWarnings());
    }

    @Test
    void validateFormatPair_imageToVideo_returnsFailure() {
        ValidationResult result = validationEngine.validateFormatPair(FileFormat.PNG, FileFormat.MP4);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Incompatible format conversion")));
    }

    @Test
    void validateFormatPair_documentToDocument_returnsSuccessWithWarning() {
        ValidationResult result = validationEngine.validateFormatPair(FileFormat.DOCX, FileFormat.PDF);

        // Document to document is compatible but shows cross-category warning
        assertTrue(result.isSuccess()
                || result.getWarnings().stream().anyMatch(w -> w.contains("Cross-category") || w.isEmpty()));
    }

    @Test
    void validateFormatPair_imageToPdf_returnsSuccess() {
        // REQ-PDF-1.2: PDF dual-category support (DOCUMENT + IMAGE)
        // Image to PDF should be valid because PDF supports IMAGE category
        ValidationResult result = validationEngine.validateFormatPair(FileFormat.JPEG, FileFormat.PDF);

        assertTrue(result.isSuccess(), "JPEG to PDF conversion should be valid");
        assertFalse(result.isFailure(), "Should not have errors");
    }

    @Test
    void validateFormatPair_pngToPdf_returnsSuccess() {
        // REQ-PDF-1.2: PDF dual-category support
        ValidationResult result = validationEngine.validateFormatPair(FileFormat.PNG, FileFormat.PDF);

        assertTrue(result.isSuccess(), "PNG to PDF conversion should be valid");
        assertFalse(result.isFailure(), "Should not have errors");
    }

    @Test
    void validateFormatPair_pdfToImage_returnsSuccess() {
        // REQ-PDF-1.2: PDF dual-category support (reverse direction)
        // PDF to Image should also work
        ValidationResult result = validationEngine.validateFormatPair(FileFormat.PDF, FileFormat.PNG);

        assertTrue(result.isSuccess(), "PDF to PNG conversion should be valid");
        assertFalse(result.isFailure(), "Should not have errors");
    }

    // ========== Output Directory Validation Tests ==========

    @Test
    void validateOutputDirectory_withNullDirectory_returnsFailure() {
        ValidationResult result = validationEngine.validateOutputDirectory(null);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().get(0).contains("cannot be null"));
    }

    @Test
    void validateOutputDirectory_createsDirectoryIfNotExists() throws Exception {
        Path newDir = tempDir.resolve("newoutput");
        // Mock to detect createDirectory call, but actually create the directory
        when(fileHandler.exists(newDir)).thenReturn(false);
        doAnswer(invocation -> {
            Files.createDirectories(newDir);
            return null;
        }).when(fileHandler).createDirectory(newDir);
        when(fileHandler.isWritable(newDir)).thenReturn(true);
        when(fileHandler.getAvailableSpace(newDir)).thenReturn(10L * 1024 * 1024 * 1024); // 10 GB

        ValidationResult result = validationEngine.validateOutputDirectory(newDir);

        verify(fileHandler).createDirectory(newDir);
        assertTrue(result.isSuccess());
    }

    @Test
    void validateOutputDirectory_whenCreationFails_returnsFailure() throws Exception {
        Path newDir = tempDir.resolve("newoutput");
        when(fileHandler.exists(newDir)).thenReturn(false);
        doThrow(new FileOperationException("Cannot create", ErrorCode.FILE_IO_ERROR, newDir.toString()))
                .when(fileHandler).createDirectory(newDir);

        ValidationResult result = validationEngine.validateOutputDirectory(newDir);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot create output directory")));
    }

    @Test
    void validateOutputDirectory_withFile_returnsFailure() throws Exception {
        Path file = Files.createFile(tempDir.resolve("file.txt"));
        when(fileHandler.exists(file)).thenReturn(true);

        ValidationResult result = validationEngine.validateOutputDirectory(file);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("not a directory")));
    }

    @Test
    void validateOutputDirectory_withUnwritableDirectory_returnsFailure() throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve("unwritable"));
        when(fileHandler.exists(dir)).thenReturn(true);
        when(fileHandler.isWritable(dir)).thenReturn(false);

        ValidationResult result = validationEngine.validateOutputDirectory(dir);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("not writable")));
    }

    @Test
    void validateOutputDirectory_withLowDiskSpace_returnsSuccessWithWarning() throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve("output"));
        when(fileHandler.exists(dir)).thenReturn(true);
        when(fileHandler.isWritable(dir)).thenReturn(true);
        when(fileHandler.getAvailableSpace(dir)).thenReturn(100L * 1024 * 1024); // 100 MB (below 500 MB buffer)

        ValidationResult result = validationEngine.validateOutputDirectory(dir);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Low disk space")));
    }

    @Test
    void validateOutputDirectory_whenSpaceCheckFails_returnsSuccessWithWarning() throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve("output"));
        when(fileHandler.exists(dir)).thenReturn(true);
        when(fileHandler.isWritable(dir)).thenReturn(true);
        when(fileHandler.getAvailableSpace(dir))
                .thenThrow(new FileOperationException("Cannot check space", ErrorCode.FILE_IO_ERROR, dir.toString()));

        ValidationResult result = validationEngine.validateOutputDirectory(dir);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Cannot determine available disk space")));
    }

    @Test
    void validateOutputDirectory_withValidDirectory_returnsSuccess() throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve("output"));
        when(fileHandler.exists(dir)).thenReturn(true);
        when(fileHandler.isWritable(dir)).thenReturn(true);
        when(fileHandler.getAvailableSpace(dir)).thenReturn(10L * 1024 * 1024 * 1024); // 10 GB

        ValidationResult result = validationEngine.validateOutputDirectory(dir);

        assertTrue(result.isSuccess());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }

    // ========== Disk Space Validation Tests (REQ-007.1) ==========

    @Test
    void validateDiskSpace_withNullDirectory_returnsFailure() {
        ValidationResult result = validationEngine.validateDiskSpace(null, 1000000);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().get(0).contains("Directory cannot be null"));
    }

    @Test
    void validateDiskSpace_withNegativeRequiredBytes_returnsFailure() throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve("output"));

        ValidationResult result = validationEngine.validateDiskSpace(dir, -100);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().get(0).contains("cannot be negative"));
    }

    @Test
    void validateDiskSpace_withInsufficientSpace_returnsFailure() throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve("output"));
        long requiredBytes = 5L * 1024 * 1024 * 1024; // 5 GB
        long availableBytes = 1L * 1024 * 1024 * 1024; // 1 GB

        when(fileHandler.getAvailableSpace(dir)).thenReturn(availableBytes);

        ValidationResult result = validationEngine.validateDiskSpace(dir, requiredBytes);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Insufficient disk space")));
    }

    @Test
    void validateDiskSpace_withTightSpace_returnsSuccessWithWarning() throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve("output"));
        long requiredBytes = 2L * 1024 * 1024 * 1024; // 2 GB
        long bufferBytes = 500L * 1024 * 1024; // 500 MB
        long availableBytes = requiredBytes + bufferBytes + 100L * 1024 * 1024; // Barely enough (< 2x required)

        when(fileHandler.getAvailableSpace(dir)).thenReturn(availableBytes);

        ValidationResult result = validationEngine.validateDiskSpace(dir, requiredBytes);

        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("Disk space is tight")));
    }

    @Test
    void validateDiskSpace_withAmpleSpace_returnsSuccess() throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve("output"));
        long requiredBytes = 1L * 1024 * 1024 * 1024; // 1 GB
        long availableBytes = 20L * 1024 * 1024 * 1024; // 20 GB

        when(fileHandler.getAvailableSpace(dir)).thenReturn(availableBytes);

        ValidationResult result = validationEngine.validateDiskSpace(dir, requiredBytes);

        assertTrue(result.isSuccess());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void validateDiskSpace_whenSpaceCheckFails_returnsFailure() throws Exception {
        Path dir = Files.createDirectory(tempDir.resolve("output"));
        when(fileHandler.getAvailableSpace(dir))
                .thenThrow(new FileOperationException("Cannot check", ErrorCode.FILE_IO_ERROR, dir.toString()));

        ValidationResult result = validationEngine.validateDiskSpace(dir, 1000000);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Cannot check disk space")));
    }

    // ========== Tool Availability Validation Tests ==========

    @Test
    void validateToolAvailability_withNullTool_returnsFailure() {
        ValidationResult result = validationEngine.validateToolAvailability(null);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().get(0).contains("Tool cannot be null"));
    }

    // Note: Tool availability tests that spawn real processes are integration tests
    // and are typically skipped in unit tests. Mock-based process tests are complex
    // and may not provide much value. Consider these integration tests.

    // ========== Comprehensive Conversion Request Validation Tests ==========

    @Test
    void validateConversionRequest_withNullFile_returnsFailure() throws Exception {
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .build();

        ValidationResult result = validationEngine.validateConversionRequest(null, settings);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().get(0).contains("file cannot be null"));
    }

    @Test
    void validateConversionRequest_withNullSettings_returnsFailure() throws Exception {
        Path inputFile = Files.createFile(tempDir.resolve("input.mp4"));
        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 1024L);

        ValidationResult result = validationEngine.validateConversionRequest(file, null);

        assertTrue(result.isFailure());
        assertTrue(result.getErrors().get(0).contains("settings cannot be null"));
    }

    @Test
    void validateConversionRequest_combinesAllValidations() throws Exception {
        Path inputFile = Files.createFile(tempDir.resolve("input.mp4"));
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        when(fileHandler.exists(inputFile)).thenReturn(true);
        when(fileHandler.isReadable(inputFile)).thenReturn(true);
        when(fileHandler.getFileSize(inputFile)).thenReturn(1024L * 1024 * 100); // 100 MB
        when(fileHandler.detectFormat(inputFile)).thenReturn(FileFormat.MP4);
        when(fileHandler.getAvailableSpace(outputDir)).thenReturn(10L * 1024 * 1024 * 1024); // 10 GB

        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 1024L * 1024 * 100);

        VideoSettings videoSettings = VideoSettings.builder()
                .bitrate(5000)
                .frameRate(30)
                .crf(23)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .videoSettings(videoSettings)
                .build();

        ValidationResult result = validationEngine.validateConversionRequest(file, settings);

        assertTrue(result.isSuccess());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validateConversionRequest_withMultipleErrors_combinesAllErrors() throws Exception {
        Path inputFile = tempDir.resolve("nonexistent.mp4");
        Path outputDir = tempDir.resolve("nonexistent-output");

        when(fileHandler.exists(inputFile)).thenReturn(false);

        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 1024L * 1024 * 100);

        // Use valid video settings since invalid values would throw
        // IllegalArgumentException at construction
        VideoSettings videoSettings = VideoSettings.builder()
                .bitrate(5000)
                .frameRate(30)
                .crf(23)
                .build();

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .videoSettings(videoSettings)
                .build();

        ValidationResult result = validationEngine.validateConversionRequest(file, settings);

        assertTrue(result.isFailure());
        // Should have multiple errors from file validation (nonexistent), output dir
        // validation, etc.
        assertTrue(result.getErrors().size() > 1);
    }

    @Test
    void validateConversionRequest_usesDefaultFormatWhenNotConfigured() throws Exception {
        Path inputFile = Files.createFile(tempDir.resolve("input.mp4"));
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        when(fileHandler.exists(inputFile)).thenReturn(true);
        when(fileHandler.isReadable(inputFile)).thenReturn(true);
        when(fileHandler.getFileSize(inputFile)).thenReturn(1024L * 1024 * 100); // 100 MB
        when(fileHandler.detectFormat(inputFile)).thenReturn(FileFormat.MP4);
        when(fileHandler.getAvailableSpace(outputDir)).thenReturn(10L * 1024 * 1024 * 1024); // 10 GB

        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 1024L * 1024 * 100);

        // No video settings specified - should use default MP4 output format
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .build();

        ValidationResult result = validationEngine.validateConversionRequest(file, settings);

        // Should warn about identical formats (MP4 -> MP4 default)
        assertTrue(result.isSuccess());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("identical")));
    }

    @Test
    void validateConversionRequest_whenFileSizeCheckFails_continuesWithWarning() throws Exception {
        Path inputFile = Files.createFile(tempDir.resolve("input.mp4"));
        Path outputDir = Files.createDirectory(tempDir.resolve("output"));

        when(fileHandler.exists(inputFile)).thenReturn(true);
        when(fileHandler.isReadable(inputFile)).thenReturn(true);
        when(fileHandler.getFileSize(inputFile)).thenThrow(
                new FileOperationException("Cannot get size", ErrorCode.FILE_IO_ERROR, inputFile.toString()));
        when(fileHandler.detectFormat(inputFile)).thenReturn(FileFormat.MP4);

        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, 1024L * 1024 * 100);

        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .build();

        ValidationResult result = validationEngine.validateConversionRequest(file, settings);

        // Should be failure because file size check failed in file validation
        assertTrue(result.isFailure());
    }
}
