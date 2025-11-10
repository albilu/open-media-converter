package org.omc.core;

import org.omc.controller.FileManager;
import org.omc.exception.ToolExecutionException;
import org.omc.model.*;
import org.omc.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NOTE: This test only validates tool selection logic.
 * It does not test the FileManager API for output format selection,
 * as that functionality is tested separately in FileManager tests.
 */

/**
 * Integration tests for complete tool selection workflow.
 * 
 * <p>
 * Requirements:
 * <ul>
 * <li>REQ-SEL-1: Document format tool routing</li>
 * <li>REQ-SEL-2: Image format tool routing</li>
 * <li>REQ-SEL-3: Video/audio format tool routing</li>
 * <li>NFR-IMG-4: Backward compatibility with existing conversions</li>
 * </ul>
 * 
 * <p>
 * Tests complete workflow: add file → detect format → select tool → verify tool
 * </p>
 */
public class ToolSelectionIntegrationTest {

    @TempDir
    Path tempDir;

    private ToolManager toolManager;
    private FileManager fileManager;
    private ValidationEngine validationEngine;
    private FileHandler fileHandler;

    private Path pngFile;
    private Path jpegFile;
    private Path htmlFile;
    private Path docxFile;
    private Path mp4File;
    private Path mp3File;

    @BeforeEach
    public void setUp() throws IOException {
        // Create mock tool services (all non-null for availability)
        FFmpegService ffmpegService = new FFmpegService(
                Path.of("/usr/bin/ffmpeg"),
                Path.of("/usr/bin/ffprobe"));
        PandocService pandocService = new PandocService(Path.of("/usr/bin/pandoc"));
        LibreOfficeService libreOfficeService = new LibreOfficeService(Path.of("/usr/bin/soffice"));
        ImageMagickService imageMagickService = new ImageMagickService(Path.of("/usr/bin/convert"));

        // Create ToolManager with all services
        toolManager = new ToolManager(
                ffmpegService,
                pandocService,
                libreOfficeService,
                imageMagickService);

        // Create supporting components with proper dependency chain
        ConfigurationManager configManager = new ConfigurationManager();
        fileHandler = new FileHandler(configManager);
        validationEngine = new ValidationEngine(fileHandler);
        fileManager = new FileManager(fileHandler, validationEngine);

        // Create test files with realistic content
        pngFile = createTestFile("test.png", "PNG_IMAGE_DATA");
        jpegFile = createTestFile("test.jpeg", "JPEG_IMAGE_DATA");
        htmlFile = createTestFile("test.html", "<html><body>Test</body></html>");
        docxFile = createTestFile("test.docx", "DOCX_BINARY_DATA");
        mp4File = createTestFile("test.mp4", "MP4_VIDEO_DATA");
        mp3File = createTestFile("test.mp3", "MP3_AUDIO_DATA");
    }

    /**
     * Creates a test file with specified name and content.
     */
    private Path createTestFile(String filename, String content) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file;
    }

    /**
     * Test image file tool selection - PNG file should route to IMAGEMAGICK.
     * 
     * Requirements: REQ-SEL-2
     */
    @Test
    public void testImageFileToolSelection_Png() throws Exception {
        // Given: PNG file can be added to FileManager
        List<ConversionFile> addedFiles = fileManager.addFiles(List.of(pngFile));
        assertFalse(addedFiles.isEmpty(), "PNG file should be added");

        // When: Tool is selected for PNG → JPEG
        ConversionTool selectedTool = toolManager.selectTool(FileFormat.PNG, FileFormat.JPEG);

        // Then: IMAGEMAGICK tool should be selected
        assertEquals(ConversionTool.IMAGEMAGICK, selectedTool,
                "PNG → JPEG conversion should use IMAGEMAGICK");

        // And: Tool should be available
        assertTrue(toolManager.isToolAvailable(ConversionTool.IMAGEMAGICK),
                "IMAGEMAGICK tool should be available");
    }

    /**
     * Test image file tool selection - JPEG file should route to IMAGEMAGICK.
     * 
     * Requirements: REQ-SEL-2
     */
    @Test
    public void testImageFileToolSelection_Jpeg() throws Exception {
        // Given: JPEG file can be added to FileManager
        List<ConversionFile> addedFiles = fileManager.addFiles(List.of(jpegFile));
        assertFalse(addedFiles.isEmpty(), "JPEG file should be added");

        // When: Tool is selected for JPEG → WEBP
        ConversionTool selectedTool = toolManager.selectTool(FileFormat.JPEG, FileFormat.WEBP);

        // Then: IMAGEMAGICK tool should be selected
        assertEquals(ConversionTool.IMAGEMAGICK, selectedTool,
                "JPEG → WEBP conversion should use IMAGEMAGICK");

        // And: Tool should be available
        assertTrue(toolManager.isToolAvailable(ConversionTool.IMAGEMAGICK),
                "IMAGEMAGICK tool should be available");
    }

    /**
     * Test image format detection and tool selection for all image formats.
     * 
     * Requirements: REQ-SEL-2
     */
    @Test
    public void testImageFormats_AllRouteToImageMagick() throws Exception {
        // Given: All image formats (JPEG covers both .jpg and .jpeg extensions)
        List<FileFormat> imageFormats = List.of(
                FileFormat.PNG, FileFormat.JPEG,
                FileFormat.GIF, FileFormat.BMP, FileFormat.TIFF,
                FileFormat.WEBP);

        // When/Then: Each image format should route to IMAGEMAGICK
        for (FileFormat inputFormat : imageFormats) {
            for (FileFormat outputFormat : imageFormats) {
                if (inputFormat != outputFormat) {
                    ConversionTool tool = toolManager.selectTool(inputFormat, outputFormat);
                    assertEquals(ConversionTool.IMAGEMAGICK, tool,
                            String.format("%s → %s should use IMAGEMAGICK", inputFormat, outputFormat));
                }
            }
        }
    }

    /**
     * Test HTML file tool selection - should route to PANDOC.
     * 
     * Requirements: REQ-SEL-1
     */
    @Test
    public void testHtmlFileToolSelection() throws Exception {
        // Given: HTML file can be added to FileManager
        List<ConversionFile> addedFiles = fileManager.addFiles(List.of(htmlFile));
        assertFalse(addedFiles.isEmpty(), "HTML file should be added");

        // When: Tool is selected for HTML → MARKDOWN
        ConversionTool selectedTool = toolManager.selectTool(FileFormat.HTML, FileFormat.MARKDOWN);

        // Then: PANDOC tool should be selected
        assertEquals(ConversionTool.PANDOC, selectedTool,
                "HTML → MARKDOWN conversion should use PANDOC");

        // And: Tool should be available
        assertTrue(toolManager.isToolAvailable(ConversionTool.PANDOC),
                "PANDOC tool should be available");
    }

    /**
     * Test DOCX file tool selection - should route to LIBREOFFICE.
     * 
     * Requirements: REQ-SEL-1
     */
    @Test
    public void testDocxFileToolSelection() throws Exception {
        // Given: DOCX file can be added to FileManager
        List<ConversionFile> addedFiles = fileManager.addFiles(List.of(docxFile));
        assertFalse(addedFiles.isEmpty(), "DOCX file should be added");

        // When: Tool is selected for DOCX → PDF
        ConversionTool selectedTool = toolManager.selectTool(FileFormat.DOCX, FileFormat.PDF);

        // Then: LIBREOFFICE tool should be selected
        assertEquals(ConversionTool.LIBREOFFICE, selectedTool,
                "DOCX → PDF conversion should use LIBREOFFICE");

        // And: Tool should be available
        assertTrue(toolManager.isToolAvailable(ConversionTool.LIBREOFFICE),
                "LIBREOFFICE tool should be available");
    }

    /**
     * Test Pandoc document formats route correctly.
     * 
     * Requirements: REQ-SEL-1
     */
    @Test
    public void testPandocDocumentFormats() throws Exception {
        // Given: Pandoc-supported formats
        List<FileFormat> pandocFormats = List.of(
                FileFormat.MARKDOWN, FileFormat.HTML,
                FileFormat.RTF, FileFormat.TXT, FileFormat.EPUB,
                FileFormat.TEX, FileFormat.LATEX, FileFormat.RST, FileFormat.ORG);

        // When/Then: Each Pandoc format should route to PANDOC
        for (FileFormat format : pandocFormats) {
            ConversionTool tool = toolManager.selectTool(format, FileFormat.PDF);
            assertEquals(ConversionTool.PANDOC, tool,
                    String.format("%s → PDF should use PANDOC", format));
        }
    }

    /**
     * Test LibreOffice document formats route correctly.
     * 
     * Requirements: REQ-SEL-1
     */
    @Test
    public void testLibreOfficeDocumentFormats() throws Exception {
        // Given: LibreOffice-supported formats
        List<FileFormat> libreOfficeFormats = List.of(
                FileFormat.DOCX, FileFormat.DOC, FileFormat.PDF,
                FileFormat.XLSX, FileFormat.XLS, FileFormat.PPTX,
                FileFormat.PPT, FileFormat.ODT, FileFormat.ODS, FileFormat.ODP);

        // When/Then: Each LibreOffice format should route to LIBREOFFICE
        for (FileFormat format : libreOfficeFormats) {
            if (format != FileFormat.PDF) { // Skip PDF → PDF
                ConversionTool tool = toolManager.selectTool(format, FileFormat.PDF);
                assertEquals(ConversionTool.LIBREOFFICE, tool,
                        String.format("%s → PDF should use LIBREOFFICE", format));
            }
        }
    }

    /**
     * Test video file tool selection - should route to FFMPEG.
     * 
     * Requirements: REQ-SEL-3, NFR-IMG-4
     */
    @Test
    public void testVideoFileToolSelection() throws Exception {
        // Given: MP4 file can be added to FileManager
        List<ConversionFile> addedFiles = fileManager.addFiles(List.of(mp4File));
        assertFalse(addedFiles.isEmpty(), "MP4 file should be added");

        // When: Tool is selected for MP4 → AVI
        ConversionTool selectedTool = toolManager.selectTool(FileFormat.MP4, FileFormat.AVI);

        // Then: FFMPEG tool should be selected (backward compatibility)
        assertEquals(ConversionTool.FFMPEG, selectedTool,
                "MP4 → AVI conversion should use FFMPEG");

        // And: Tool should be available
        assertTrue(toolManager.isToolAvailable(ConversionTool.FFMPEG),
                "FFMPEG tool should be available");
    }

    /**
     * Test audio file tool selection - should route to FFMPEG.
     * 
     * Requirements: REQ-SEL-3, NFR-IMG-4
     */
    @Test
    public void testAudioFileToolSelection() throws Exception {
        // Given: MP3 file can be added to FileManager
        List<ConversionFile> addedFiles = fileManager.addFiles(List.of(mp3File));
        assertFalse(addedFiles.isEmpty(), "MP3 file should be added");

        // When: Tool is selected for MP3 → WAV
        ConversionTool selectedTool = toolManager.selectTool(FileFormat.MP3, FileFormat.WAV);

        // Then: FFMPEG tool should be selected (backward compatibility)
        assertEquals(ConversionTool.FFMPEG, selectedTool,
                "MP3 → WAV conversion should use FFMPEG");

        // And: Tool should be available
        assertTrue(toolManager.isToolAvailable(ConversionTool.FFMPEG),
                "FFMPEG tool should be available");
    }

    /**
     * Test backward compatibility - existing video/audio conversions still work.
     * 
     * Requirements: NFR-IMG-4
     */
    @Test
    public void testBackwardCompatibility_ExistingConversions() throws Exception {
        // Given: Multiple format pairs that should maintain existing tool selection

        // Video conversions should still use FFMPEG
        assertEquals(ConversionTool.FFMPEG,
                toolManager.selectTool(FileFormat.MP4, FileFormat.WEBM),
                "Video conversions should still use FFMPEG");
        assertEquals(ConversionTool.FFMPEG,
                toolManager.selectTool(FileFormat.AVI, FileFormat.MKV),
                "Video conversions should still use FFMPEG");

        // Audio conversions should still use FFMPEG
        assertEquals(ConversionTool.FFMPEG,
                toolManager.selectTool(FileFormat.MP3, FileFormat.FLAC),
                "Audio conversions should still use FFMPEG");
        assertEquals(ConversionTool.FFMPEG,
                toolManager.selectTool(FileFormat.WAV, FileFormat.OGG),
                "Audio conversions should still use FFMPEG");

        // Document conversions should maintain Pandoc/LibreOffice split
        assertEquals(ConversionTool.PANDOC,
                toolManager.selectTool(FileFormat.MARKDOWN, FileFormat.HTML),
                "Markdown conversions should still use PANDOC");
        assertEquals(ConversionTool.LIBREOFFICE,
                toolManager.selectTool(FileFormat.DOCX, FileFormat.ODT),
                "DOCX conversions should still use LIBREOFFICE");
    }

    /**
     * Test category-based routing for all categories.
     * 
     * Requirements: REQ-SEL-1, REQ-SEL-2, REQ-SEL-3
     */
    @Test
    public void testCategoryBasedRouting() throws Exception {
        // VIDEO category → FFMPEG
        ConversionTool videoTool = toolManager.selectTool(FileFormat.MP4, FileFormat.AVI);
        assertEquals(ConversionTool.FFMPEG, videoTool, "VIDEO category should route to FFMPEG");

        // AUDIO category → FFMPEG
        ConversionTool audioTool = toolManager.selectTool(FileFormat.MP3, FileFormat.WAV);
        assertEquals(ConversionTool.FFMPEG, audioTool, "AUDIO category should route to FFMPEG");

        // IMAGE category → IMAGEMAGICK
        ConversionTool imageTool = toolManager.selectTool(FileFormat.PNG, FileFormat.JPEG);
        assertEquals(ConversionTool.IMAGEMAGICK, imageTool, "IMAGE category should route to IMAGEMAGICK");

        // DOCUMENT category → PANDOC or LIBREOFFICE (depends on format)
        ConversionTool docTool1 = toolManager.selectTool(FileFormat.MARKDOWN, FileFormat.PDF);
        assertEquals(ConversionTool.PANDOC, docTool1, "Markdown should route to PANDOC");

        ConversionTool docTool2 = toolManager.selectTool(FileFormat.DOCX, FileFormat.PDF);
        assertEquals(ConversionTool.LIBREOFFICE, docTool2, "DOCX should route to LIBREOFFICE");
    }

    /**
     * Test tool selection with UNKNOWN format throws exception.
     * 
     * Requirements: REQ-SEL-1
     */
    @Test
    public void testUnknownFormatThrowsException() {
        // When/Then: UNKNOWN format should throw ToolExecutionException
        assertThrows(ToolExecutionException.class, () -> {
            toolManager.selectTool(FileFormat.UNKNOWN, FileFormat.PNG);
        }, "UNKNOWN input format should throw ToolExecutionException");

        assertThrows(ToolExecutionException.class, () -> {
            toolManager.selectTool(FileFormat.PNG, FileFormat.UNKNOWN);
        }, "UNKNOWN output format should throw ToolExecutionException");
    }

    /**
     * Test new document format extensions route correctly.
     * 
     * Requirements: REQ-SEL-1
     */
    @Test
    public void testNewDocumentFormatExtensions() throws Exception {
        // HTML (including .htm extension) → PANDOC
        assertEquals(ConversionTool.PANDOC,
                toolManager.selectTool(FileFormat.HTML, FileFormat.MARKDOWN),
                "HTML should route to PANDOC");

        // TEX (LaTeX) → PANDOC
        assertEquals(ConversionTool.PANDOC,
                toolManager.selectTool(FileFormat.TEX, FileFormat.PDF),
                "TEX should route to PANDOC");

        // LATEX (LaTeX alternate) → PANDOC
        assertEquals(ConversionTool.PANDOC,
                toolManager.selectTool(FileFormat.LATEX, FileFormat.HTML),
                "LATEX should route to PANDOC");

        // RST (reStructuredText) → PANDOC
        assertEquals(ConversionTool.PANDOC,
                toolManager.selectTool(FileFormat.RST, FileFormat.DOCX),
                "RST should route to PANDOC");

        // ORG (Org-mode) → PANDOC
        assertEquals(ConversionTool.PANDOC,
                toolManager.selectTool(FileFormat.ORG, FileFormat.HTML),
                "ORG should route to PANDOC");

        // DOC (Word 97-2003) → LIBREOFFICE
        assertEquals(ConversionTool.LIBREOFFICE,
                toolManager.selectTool(FileFormat.DOC, FileFormat.DOCX),
                "DOC should route to LIBREOFFICE");

        // XLS (Excel 97-2003) → LIBREOFFICE
        assertEquals(ConversionTool.LIBREOFFICE,
                toolManager.selectTool(FileFormat.XLS, FileFormat.XLSX),
                "XLS should route to LIBREOFFICE");

        // PPT (PowerPoint 97-2003) → LIBREOFFICE
        assertEquals(ConversionTool.LIBREOFFICE,
                toolManager.selectTool(FileFormat.PPT, FileFormat.PPTX),
                "PPT should route to LIBREOFFICE");
    }

    /**
     * Test tool availability checking for all tools.
     * 
     * Requirements: REQ-SEL-4
     */
    @Test
    public void testToolAvailabilityChecking() {
        // All tools should be available in this test setup
        assertTrue(toolManager.isToolAvailable(ConversionTool.FFMPEG),
                "FFMPEG should be available");
        assertTrue(toolManager.isToolAvailable(ConversionTool.PANDOC),
                "PANDOC should be available");
        assertTrue(toolManager.isToolAvailable(ConversionTool.LIBREOFFICE),
                "LIBREOFFICE should be available");
        assertTrue(toolManager.isToolAvailable(ConversionTool.IMAGEMAGICK),
                "IMAGEMAGICK should be available");
    }
}
