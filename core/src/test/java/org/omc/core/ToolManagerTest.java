// filepath: src/test/java/org/omc/core/ToolManagerTest.java

package org.omc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.omc.exception.ErrorCode;
import org.omc.exception.ToolExecutionException;
import org.omc.model.AudioSettings;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionSettings;
import org.omc.model.ConversionTool;
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;
import org.omc.model.ImageSettings;
import org.omc.model.Resolution;
import org.omc.model.VideoSettings;
import org.omc.service.FFmpegService;
import org.omc.service.ImageMagickService;
import org.omc.service.LibreOfficeService;
import org.omc.service.PandocService;

/**
 * Comprehensive unit tests for ToolManager.
 * 
 * Test Coverage:
 * - Tool selection logic for various format pairs
 * - Tool availability checking
 * - Tool execution delegation
 * - Error handling for unsupported formats
 * - Null parameter validation
 * 
 * Requirements tested:
 * - REQ-004.1: Tool selection based on format pairs
 * - REQ-006.1-4: Format support (video, audio, image, document)
 */
@ExtendWith(MockitoExtension.class)
class ToolManagerTest {

    private ToolManager toolManager;

    @Mock
    private FFmpegService ffmpegService;

    @Mock
    private PandocService pandocService;

    @Mock
    private LibreOfficeService libreOfficeService;

    @Mock
    private ImageMagickService imageMagickService;

    @Mock
    private ProgressCallback progressCallback;

    private Path inputPath;
    private Path outputPath;

    @BeforeEach
    void setUp() {
        toolManager = new ToolManager(ffmpegService, pandocService, libreOfficeService, imageMagickService);
        inputPath = Paths.get("/tmp/input.mp4");
        outputPath = Paths.get("/tmp/output.avi");
    }

    // Constructor tests

    @Test
    void testConstructor_WithValidServices_CreatesInstance() {
        // When
        ToolManager manager = new ToolManager(ffmpegService, pandocService, libreOfficeService, imageMagickService);

        // Then
        assertNotNull(manager);
        assertEquals(ffmpegService, manager.getFFmpegService());
        assertEquals(pandocService, manager.getPandocService());
        assertEquals(libreOfficeService, manager.getLibreOfficeService());
        assertEquals(imageMagickService, manager.getImageMagickService());
    }

    @Test
    void testConstructor_WithNullFFmpegService_ThrowsException() {
        // When/Then
        assertThrows(NullPointerException.class,
                () -> new ToolManager(null, pandocService, libreOfficeService, imageMagickService));
    }

    @Test
    void testConstructor_WithNullPandocService_ThrowsException() {
        // When/Then
        assertThrows(NullPointerException.class,
                () -> new ToolManager(ffmpegService, null, libreOfficeService, imageMagickService));
    }

    @Test
    void testConstructor_WithNullLibreOfficeService_ThrowsException() {
        // When/Then
        assertThrows(NullPointerException.class,
                () -> new ToolManager(ffmpegService, pandocService, null, imageMagickService));
    }

    // Tool selection tests - Video formats (REQ-006.1)

    @Test
    void testSelectTool_VideoToVideo_SelectsFFmpeg() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.MP4, FileFormat.AVI);

        // Then
        assertEquals(ConversionTool.FFMPEG, tool);
    }

    @Test
    void testSelectTool_MKVToMP4_SelectsFFmpeg() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.MKV, FileFormat.MP4);

        // Then
        assertEquals(ConversionTool.FFMPEG, tool);
    }

    @Test
    void testSelectTool_MOVToWEBM_SelectsFFmpeg() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.MOV, FileFormat.WEBM);

        // Then
        assertEquals(ConversionTool.FFMPEG, tool);
    }

    // Tool selection tests - Audio formats (REQ-006.2)

    @Test
    void testSelectTool_AudioToAudio_SelectsFFmpeg() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.MP3, FileFormat.WAV);

        // Then
        assertEquals(ConversionTool.FFMPEG, tool);
    }

    @Test
    void testSelectTool_FLACToOGG_SelectsFFmpeg() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.FLAC, FileFormat.OGG);

        // Then
        assertEquals(ConversionTool.FFMPEG, tool);
    }

    @Test
    void testSelectTool_AACToMP3_SelectsFFmpeg() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.AAC, FileFormat.MP3);

        // Then
        assertEquals(ConversionTool.FFMPEG, tool);
    }

    // Tool selection tests - Image formats (REQ-006.3)

    @Test
    void testSelectTool_ImageToImage_SelectsImageMagick() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.PNG, FileFormat.JPEG);

        // Then
        assertEquals(ConversionTool.IMAGEMAGICK, tool);
    }

    @Test
    void testSelectTool_GIFToWebP_SelectsImageMagick() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.GIF, FileFormat.WEBP);

        // Then
        assertEquals(ConversionTool.IMAGEMAGICK, tool);
    }

    @Test
    void testSelectTool_TIFFToPNG_SelectsImageMagick() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.TIFF, FileFormat.PNG);

        // Then
        assertEquals(ConversionTool.IMAGEMAGICK, tool);
    }

    @Test
    void testSelectTool_JPEGToPNG_SelectsImageMagick() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.JPEG, FileFormat.PNG);

        // Then
        assertEquals(ConversionTool.IMAGEMAGICK, tool);
    }

    @Test
    void testSelectTool_BMPToJPEG_SelectsImageMagick() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.BMP, FileFormat.JPEG);

        // Then
        assertEquals(ConversionTool.IMAGEMAGICK, tool);
    }

    @Test
    void testSelectTool_WebPToPNG_SelectsImageMagick() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.WEBP, FileFormat.PNG);

        // Then
        assertEquals(ConversionTool.IMAGEMAGICK, tool);
    }

    // Tool selection tests - Pandoc formats (REQ-006.4)

    @Test
    void testSelectTool_MarkdownToHTML_SelectsPandoc() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.MARKDOWN, FileFormat.HTML);

        // Then
        assertEquals(ConversionTool.PANDOC, tool);
    }

    @Test
    void testSelectTool_HTMLToMarkdown_SelectsPandoc() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.HTML, FileFormat.MARKDOWN);

        // Then
        assertEquals(ConversionTool.PANDOC, tool);
    }

    @Test
    void testSelectTool_RTFToHTML_SelectsPandoc() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.RTF, FileFormat.HTML);

        // Then
        assertEquals(ConversionTool.PANDOC, tool);
    }

    @Test
    void testSelectTool_TXTToMarkdown_SelectsPandoc() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.TXT, FileFormat.MARKDOWN);

        // Then
        assertEquals(ConversionTool.PANDOC, tool);
    }

    @Test
    void testSelectTool_EPUBToHTML_SelectsPandoc() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.EPUB, FileFormat.HTML);

        // Then
        assertEquals(ConversionTool.PANDOC, tool);
    }

    // Tool selection tests - LibreOffice formats (REQ-006.4)

    @Test
    void testSelectTool_DOCXToPDF_SelectsLibreOffice() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.DOCX, FileFormat.PDF);

        // Then
        assertEquals(ConversionTool.LIBREOFFICE, tool);
    }

    @Test
    void testSelectTool_PDFToDOCX_SelectsLibreOffice() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.PDF, FileFormat.DOCX);

        // Then
        assertEquals(ConversionTool.LIBREOFFICE, tool);
    }

    @Test
    void testSelectTool_XLSXToODS_SelectsLibreOffice() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.XLSX, FileFormat.ODS);

        // Then
        assertEquals(ConversionTool.LIBREOFFICE, tool);
    }

    @Test
    void testSelectTool_PPTXToODP_SelectsLibreOffice() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.PPTX, FileFormat.ODP);

        // Then
        assertEquals(ConversionTool.LIBREOFFICE, tool);
    }

    @Test
    void testSelectTool_ODTToPDF_SelectsLibreOffice() throws ToolExecutionException {
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.ODT, FileFormat.PDF);

        // Then
        assertEquals(ConversionTool.LIBREOFFICE, tool);
    }

    // Tool selection tests - Mixed document conversions

    @Test
    void testSelectTool_DOCXToMarkdown_SelectsLibreOffice() throws ToolExecutionException {
        // DOCX is a LibreOffice format, so it always selects LibreOffice
        // regardless of output format

        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.DOCX, FileFormat.MARKDOWN);

        // Then
        assertEquals(ConversionTool.LIBREOFFICE, tool);
    }

    @Test
    void testSelectTool_MarkdownToDOCX_SelectsPandoc() throws ToolExecutionException {
        // Markdown is a Pandoc format, so it selects Pandoc even when
        // output is a LibreOffice format

        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.MARKDOWN, FileFormat.DOCX);

        // Then
        assertEquals(ConversionTool.PANDOC, tool);
    }

    // Tool selection tests - New document format routing (REQ-SEL-1)

    @Test
    void testSelectTool_TEXToHTML_SelectsPandoc() throws ToolExecutionException {
        // TEX (LaTeX) should route to Pandoc
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.TEX, FileFormat.HTML);

        // Then
        assertEquals(ConversionTool.PANDOC, tool);
    }

    @Test
    void testSelectTool_LATEXToMarkdown_SelectsPandoc() throws ToolExecutionException {
        // LATEX (alternate LaTeX extension) should route to Pandoc
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.LATEX, FileFormat.MARKDOWN);

        // Then
        assertEquals(ConversionTool.PANDOC, tool);
    }

    @Test
    void testSelectTool_RSTToHTML_SelectsPandoc() throws ToolExecutionException {
        // RST (reStructuredText) should route to Pandoc
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.RST, FileFormat.HTML);

        // Then
        assertEquals(ConversionTool.PANDOC, tool);
    }

    @Test
    void testSelectTool_ORGToMarkdown_SelectsPandoc() throws ToolExecutionException {
        // ORG (Org-mode) should route to Pandoc
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.ORG, FileFormat.MARKDOWN);

        // Then
        assertEquals(ConversionTool.PANDOC, tool);
    }

    @Test
    void testSelectTool_DOCToPDF_SelectsLibreOffice() throws ToolExecutionException {
        // DOC (Word 97-2003) should route to LibreOffice
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.DOC, FileFormat.PDF);

        // Then
        assertEquals(ConversionTool.LIBREOFFICE, tool);
    }

    @Test
    void testSelectTool_XLSToPDF_SelectsLibreOffice() throws ToolExecutionException {
        // XLS (Excel 97-2003) should route to LibreOffice
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.XLS, FileFormat.PDF);

        // Then
        assertEquals(ConversionTool.LIBREOFFICE, tool);
    }

    @Test
    void testSelectTool_PPTToPDF_SelectsLibreOffice() throws ToolExecutionException {
        // PPT (PowerPoint 97-2003) should route to LibreOffice
        // When
        ConversionTool tool = toolManager.selectTool(FileFormat.PPT, FileFormat.PDF);

        // Then
        assertEquals(ConversionTool.LIBREOFFICE, tool);
    }

    // Tool selection tests - Error handling

    @Test
    void testSelectTool_UnknownInputFormat_ThrowsException() {
        // When/Then
        ToolExecutionException exception = assertThrows(
                ToolExecutionException.class,
                () -> toolManager.selectTool(FileFormat.UNKNOWN, FileFormat.MP4));

        assertEquals(ErrorCode.TOOL_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("unknown file format"));
    }

    @Test
    void testSelectTool_UnknownOutputFormat_ThrowsException() {
        // When/Then
        ToolExecutionException exception = assertThrows(
                ToolExecutionException.class,
                () -> toolManager.selectTool(FileFormat.MP4, FileFormat.UNKNOWN));

        assertEquals(ErrorCode.TOOL_NOT_FOUND, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("unknown file format"));
    }

    @Test
    void testSelectTool_NullInputFormat_ThrowsException() {
        // When/Then
        assertThrows(NullPointerException.class, () -> toolManager.selectTool(null, FileFormat.MP4));
    }

    @Test
    void testSelectTool_NullOutputFormat_ThrowsException() {
        // When/Then
        assertThrows(NullPointerException.class, () -> toolManager.selectTool(FileFormat.MP4, null));
    }

    // Tool availability tests

    @Test
    void testIsToolAvailable_FFmpeg_ReturnsTrue() {
        // When
        boolean available = toolManager.isToolAvailable(ConversionTool.FFMPEG);

        // Then
        assertTrue(available);
    }

    @Test
    void testIsToolAvailable_Pandoc_ReturnsTrue() {
        // When
        boolean available = toolManager.isToolAvailable(ConversionTool.PANDOC);

        // Then
        assertTrue(available);
    }

    @Test
    void testIsToolAvailable_LibreOffice_ReturnsTrue() {
        // When
        boolean available = toolManager.isToolAvailable(ConversionTool.LIBREOFFICE);

        // Then
        assertTrue(available);
    }

    @Test
    void testIsToolAvailable_NullTool_ThrowsException() {
        // When/Then
        assertThrows(NullPointerException.class, () -> toolManager.isToolAvailable(null));
    }

    // Tool execution tests - FFmpeg video conversion

    @Test
    void testExecuteTool_FFmpegVideoConversion_DelegatesToFFmpegService() throws ToolExecutionException {
        // Given
        VideoSettings videoSettings = VideoSettings.builder()
                .codec("h264")
                .bitrate(2000)
                .build();
        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .videoSettings(videoSettings)
                .build();

        ConversionResult expectedResult = ConversionResult.success(inputPath.toString(), outputPath, null,
                Duration.ofSeconds(10),
                1000L,
                800L,
                ConversionTool.FFMPEG);

        when(ffmpegService.convertVideo(any(), any(), any(), any(), isNull(), any(ProcessRegistry.class)))
                .thenReturn(expectedResult);

        // When
        ConversionResult result = toolManager.executeTool(
                ConversionTool.FFMPEG,
                inputPath,
                outputPath,
                settings.outputFormat(),
                settings,
                progressCallback);

        // Then
        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(ffmpegService).convertVideo(eq(inputPath), eq(outputPath), eq(videoSettings), eq(progressCallback),
                isNull(), any(ProcessRegistry.class));
    }

    @Test
    void testExecuteTool_FFmpegVideoConversion_MissingVideoSettings_ThrowsException() {
        // Given - settings without videoSettings for a video format conversion
        ConversionSettings settings = ConversionSettings.builder()
                .videoSettings(null)
                .build();

        // When/Then - should throw because video settings are required for video format
        ToolExecutionException exception = assertThrows(
                ToolExecutionException.class,
                () -> toolManager.executeTool(
                        ConversionTool.FFMPEG,
                        inputPath,
                        outputPath,
                        FileFormat.MP4,
                        settings,
                        progressCallback));

        assertEquals(ErrorCode.INVALID_SETTINGS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Section settings cannot be null for category: VIDEO"));
    }

    // Tool execution tests - FFmpeg audio conversion

    @Test
    void testExecuteTool_FFmpegAudioConversion_DelegatesToFFmpegService() throws ToolExecutionException {
        // Given
        AudioSettings audioSettings = AudioSettings.builder()
                .codec("mp3")
                .bitrate(192)
                .build();
        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP3)
                .audioSettings(audioSettings)
                .build();

        ConversionResult expectedResult = ConversionResult.success(inputPath.toString(), outputPath, null,
                Duration.ofSeconds(5),
                500L,
                400L,
                ConversionTool.FFMPEG);

        when(ffmpegService.convertAudio(any(), any(), any(), any(), isNull(), any(ProcessRegistry.class)))
                .thenReturn(expectedResult);

        // When
        ConversionResult result = toolManager.executeTool(
                ConversionTool.FFMPEG,
                inputPath,
                outputPath,
                settings.outputFormat(),
                settings,
                progressCallback);

        // Then
        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(ffmpegService).convertAudio(eq(inputPath), eq(outputPath), eq(audioSettings), eq(progressCallback),
                isNull(), any(ProcessRegistry.class));
    }

    @Test
    void testExecuteTool_FFmpegAudioConversion_MissingAudioSettings_ThrowsException() {
        // Given - settings without audioSettings for an audio format conversion
        ConversionSettings settings = ConversionSettings.builder()
                .audioSettings(null)
                .build();

        // When/Then - should throw because audio settings are required for audio format
        ToolExecutionException exception = assertThrows(
                ToolExecutionException.class,
                () -> toolManager.executeTool(
                        ConversionTool.FFMPEG,
                        inputPath,
                        outputPath,
                        FileFormat.MP3,
                        settings,
                        progressCallback));

        assertEquals(ErrorCode.INVALID_SETTINGS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Section settings cannot be null for category: AUDIO"));
    }

    // Tool execution tests - FFmpeg image conversion

    @Test
    void testExecuteTool_FFmpegImageConversion_ThrowsException() {
        // Given - image conversion requested with FFMPEG tool (no longer supported)
        ImageSettings imageSettings = ImageSettings.builder()
                .quality(90)
                .resolution(new Resolution(1920, 1080))
                .build();
        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.PNG)
                .imageSettings(imageSettings)
                .build();

        // When/Then - should throw because FFMPEG no longer handles IMAGE formats
        ToolExecutionException exception = assertThrows(
                ToolExecutionException.class,
                () -> toolManager.executeTool(
                        ConversionTool.FFMPEG,
                        inputPath,
                        outputPath,
                        settings.outputFormat(),
                        settings,
                        progressCallback));

        assertEquals(ErrorCode.INVALID_SETTINGS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("FFmpeg only supports video and audio formats"));
    }

    @Test
    void testExecuteTool_FFmpegImageConversion_MissingImageSettings_ThrowsException() {
        // Given - settings without imageSettings for an image format conversion
        // With the new tool-based settings extraction, FFMPEG + IMAGE format throws
        // before checking for null settings since FFMPEG doesn't support IMAGE category
        ConversionSettings settings = ConversionSettings.builder()
                .imageSettings(null)
                .build();

        // When/Then - should throw because FFMPEG doesn't support IMAGE formats
        ToolExecutionException exception = assertThrows(
                ToolExecutionException.class,
                () -> toolManager.executeTool(
                        ConversionTool.FFMPEG,
                        inputPath,
                        outputPath,
                        FileFormat.PNG,
                        settings,
                        progressCallback));

        assertEquals(ErrorCode.INVALID_SETTINGS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("FFmpeg only supports video and audio formats"));
    }

    @Test
    void testExecuteTool_FFmpegUnsupportedOutputCategory_ThrowsException() {
        // Given
        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.DOCX) // DOCUMENT category
                .build();

        // When/Then
        ToolExecutionException exception = assertThrows(
                ToolExecutionException.class,
                () -> toolManager.executeTool(
                        ConversionTool.FFMPEG,
                        inputPath,
                        outputPath,
                        settings.outputFormat(),
                        settings,
                        progressCallback));

        assertEquals(ErrorCode.INVALID_SETTINGS, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("FFmpeg only supports video and audio formats"));
    }

    // Tool execution tests - Pandoc document conversion

    @Test
    void testExecuteTool_PandocConversion_DelegatesToPandocService() throws ToolExecutionException {
        // Given
        DocumentSettings documentSettings = DocumentSettings.builder()
                .preserveFormatting(true)
                .build();
        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.HTML)
                .documentSettings(documentSettings)
                .build();

        ConversionResult expectedResult = ConversionResult.success(inputPath.toString(), outputPath, null,
                Duration.ofSeconds(3),
                200L,
                180L,
                ConversionTool.PANDOC);

        when(pandocService.convertDocument(any(), any(), any(), any(), isNull(), any(ProcessRegistry.class)))
                .thenReturn(expectedResult);

        // When
        ConversionResult result = toolManager.executeTool(
                ConversionTool.PANDOC,
                inputPath,
                outputPath,
                settings.outputFormat(),
                settings,
                progressCallback);

        // Then
        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(pandocService).convertDocument(eq(inputPath), eq(outputPath), eq(documentSettings), eq(progressCallback),
                isNull(), any(ProcessRegistry.class));
    }

    @Test
    void testExecuteTool_PandocConversion_MissingDocumentSettings_ThrowsException() {
        // Given - settings without documentSettings
        ConversionSettings settings = ConversionSettings.builder()
                .documentSettings(null)
                .build();

        // When/Then - should throw ToolExecutionException
        ToolExecutionException exception = assertThrows(
                ToolExecutionException.class,
                () -> toolManager.executeTool(
                        ConversionTool.PANDOC,
                        inputPath,
                        outputPath,
                        FileFormat.HTML,
                        settings,
                        progressCallback));

        // Verify error message
        assertTrue(exception.getMessage().contains("Section settings cannot be null for category: DOCUMENT"));
        assertEquals(ErrorCode.INVALID_SETTINGS, exception.getErrorCode());
    }

    // Tool execution tests - LibreOffice document conversion

    @Test
    void testExecuteTool_LibreOfficeConversion_DelegatesToLibreOfficeService() throws ToolExecutionException {
        // Given
        DocumentSettings documentSettings = DocumentSettings.builder()
                .preserveFormatting(true)
                .build();
        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.PDF)
                .documentSettings(documentSettings)
                .build();

        ConversionResult expectedResult = ConversionResult.success(inputPath.toString(), outputPath, null,
                Duration.ofSeconds(5),
                400L,
                350L,
                ConversionTool.LIBREOFFICE);

        when(libreOfficeService.convertDocument(any(), any(), any(), any(), isNull(), any(ProcessRegistry.class)))
                .thenReturn(expectedResult);

        // When
        ConversionResult result = toolManager.executeTool(
                ConversionTool.LIBREOFFICE,
                inputPath,
                outputPath,
                settings.outputFormat(),
                settings,
                progressCallback);

        // Then
        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(libreOfficeService).convertDocument(eq(inputPath), eq(outputPath), eq(documentSettings),
                eq(progressCallback), isNull(), any(ProcessRegistry.class));
    }

    @Test
    void testExecuteTool_LibreOfficeConversion_MissingDocumentSettings_ThrowsException() {
        // Given - settings without documentSettings
        ConversionSettings settings = ConversionSettings.builder()
                .documentSettings(null)
                .build();

        // When/Then - should throw ToolExecutionException
        ToolExecutionException exception = assertThrows(
                ToolExecutionException.class,
                () -> toolManager.executeTool(
                        ConversionTool.LIBREOFFICE,
                        inputPath,
                        outputPath,
                        FileFormat.PDF,
                        settings,
                        progressCallback));

        // Verify error message
        assertTrue(exception.getMessage().contains("Section settings cannot be null for category: DOCUMENT"));
        assertEquals(ErrorCode.INVALID_SETTINGS, exception.getErrorCode());
    }

    // Tool execution tests - ImageMagick image conversion (REQ-IMG-2)

    @Test
    void testExecuteTool_ImageMagickConversion_DelegatesToImageMagickService() throws ToolExecutionException {
        // Given
        ImageSettings imageSettings = ImageSettings.builder()
                .quality(85)
                .resolution(new Resolution(1920, 1080))
                .maintainAspectRatio(true)
                .build();
        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.JPEG)
                .imageSettings(imageSettings)
                .build();

        ConversionResult expectedResult = ConversionResult.success(inputPath.toString(), outputPath, null,
                Duration.ofSeconds(2),
                150L,
                120L,
                ConversionTool.IMAGEMAGICK);

        when(imageMagickService.convertImage(any(), any(), any(), any(), isNull(), any(ProcessRegistry.class)))
                .thenReturn(expectedResult);

        // When
        ConversionResult result = toolManager.executeTool(
                ConversionTool.IMAGEMAGICK,
                inputPath,
                outputPath,
                settings.outputFormat(),
                settings,
                progressCallback);

        // Then
        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(imageMagickService).convertImage(eq(inputPath), eq(outputPath), eq(imageSettings), eq(progressCallback),
                isNull(), any(ProcessRegistry.class));
    }

    @Test
    void testExecuteTool_ImageMagickConversion_MissingImageSettings_ThrowsException() {
        // Given - settings without imageSettings
        ConversionSettings settings = ConversionSettings.builder()
                .imageSettings(null)
                .build();

        // When/Then - should throw ToolExecutionException
        ToolExecutionException exception = assertThrows(
                ToolExecutionException.class,
                () -> toolManager.executeTool(
                        ConversionTool.IMAGEMAGICK,
                        inputPath,
                        outputPath,
                        FileFormat.JPEG,
                        settings,
                        progressCallback));

        // Verify error message
        assertTrue(exception.getMessage().contains("Section settings cannot be null for category: IMAGE"));
        assertEquals(ErrorCode.INVALID_SETTINGS, exception.getErrorCode());
    }

    @Test
    void testExecuteTool_ImageMagickConversion_ServiceNotAvailable_ThrowsException() {
        // Given - ToolManager without ImageMagickService
        ToolManager toolManagerWithoutImageMagick = new ToolManager(
                ffmpegService,
                pandocService,
                libreOfficeService,
                null // No ImageMagickService
        );

        ImageSettings imageSettings = ImageSettings.builder()
                .quality(90)
                .build();
        ConversionSettings settings = ConversionSettings.builder()
                .imageSettings(imageSettings)
                .build();

        // When/Then - should throw ToolExecutionException
        ToolExecutionException exception = assertThrows(
                ToolExecutionException.class,
                () -> toolManagerWithoutImageMagick.executeTool(
                        ConversionTool.IMAGEMAGICK,
                        inputPath,
                        outputPath,
                        FileFormat.JPEG,
                        settings,
                        progressCallback));

        // Verify error message
        assertTrue(exception.getMessage().contains("ImageMagick 'convert' binary not found"));
        assertEquals(ErrorCode.TOOL_NOT_FOUND, exception.getErrorCode());
    }

    // Tool execution tests - Parameter validation

    @Test
    void testExecuteTool_NullInputPath_ThrowsException() {
        // Given
        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();

        // When/Then
        assertThrows(NullPointerException.class, () -> toolManager.executeTool(ConversionTool.FFMPEG, null, outputPath,
                settings.outputFormat(), settings, progressCallback));
    }

    @Test
    void testExecuteTool_NullOutputPath_ThrowsException() {
        // Given
        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();

        // When/Then
        assertThrows(NullPointerException.class, () -> toolManager.executeTool(ConversionTool.FFMPEG, inputPath, null,
                settings.outputFormat(), settings, progressCallback));
    }

    @Test
    void testExecuteTool_NullSettings_ThrowsException() {
        // When/Then
        assertThrows(NullPointerException.class, () -> toolManager.executeTool(ConversionTool.FFMPEG, inputPath,
                outputPath, FileFormat.MP4, null, progressCallback));
    }

    @Test
    void testExecuteTool_NullProgressCallback_ThrowsException() {
        // Given
        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();

        // When/Then
        assertThrows(NullPointerException.class, () -> toolManager.executeTool(ConversionTool.FFMPEG, inputPath,
                outputPath, settings.outputFormat(), settings, null));
    }

    // Getter tests

    @Test
    void testGetFFmpegService_ReturnsCorrectService() {
        // When
        FFmpegService service = toolManager.getFFmpegService();

        // Then
        assertEquals(ffmpegService, service);
    }

    @Test
    void testGetPandocService_ReturnsCorrectService() {
        // When
        PandocService service = toolManager.getPandocService();

        // Then
        assertEquals(pandocService, service);
    }

    @Test
    void testGetLibreOfficeService_ReturnsCorrectService() {
        // When
        LibreOfficeService service = toolManager.getLibreOfficeService();

        // Then
        assertEquals(libreOfficeService, service);
    }
}
