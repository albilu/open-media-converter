package org.omc.model;

import org.omc.model.FormatCategory;
import org.omc.model.FileFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileFormat enum.
 * 
 * Tests:
 * - Format properties (category, extensions, MIME type)
 * - Format detection from extension
 * - Format detection from MIME type
 * - Primary extension retrieval
 * - Input/output validity checks
 * - Category filtering
 * 
 * Requirements: REQ-006.1, REQ-006.2, REQ-006.3, REQ-006.4
 */
@DisplayName("FileFormat Tests")
class FileFormatTest {

    // ========================================
    // Video Format Tests
    // ========================================

    @Test
    @DisplayName("MP4 format has correct properties")
    void mp4_HasCorrectProperties() {
        assertEquals(FormatCategory.VIDEO, FileFormat.MP4.getCategory());
        assertTrue(FileFormat.MP4.getExtensions().contains("mp4"));
        assertTrue(FileFormat.MP4.getExtensions().contains("m4v"));
        assertEquals("video/mp4", FileFormat.MP4.getMimeType());
        assertEquals("mp4", FileFormat.MP4.getPrimaryExtension());
    }

    @Test
    @DisplayName("AVI format has correct properties")
    void avi_HasCorrectProperties() {
        assertEquals(FormatCategory.VIDEO, FileFormat.AVI.getCategory());
        assertTrue(FileFormat.AVI.getExtensions().contains("avi"));
        assertEquals("video/x-msvideo", FileFormat.AVI.getMimeType());
        assertEquals("avi", FileFormat.AVI.getPrimaryExtension());
    }

    @Test
    @DisplayName("MOV format has correct properties")
    void mov_HasCorrectProperties() {
        assertEquals(FormatCategory.VIDEO, FileFormat.MOV.getCategory());
        assertTrue(FileFormat.MOV.getExtensions().contains("mov"));
        assertTrue(FileFormat.MOV.getExtensions().contains("qt"));
        assertEquals("video/quicktime", FileFormat.MOV.getMimeType());
        assertEquals("mov", FileFormat.MOV.getPrimaryExtension());
    }

    @Test
    @DisplayName("MKV format has correct properties")
    void mkv_HasCorrectProperties() {
        assertEquals(FormatCategory.VIDEO, FileFormat.MKV.getCategory());
        assertTrue(FileFormat.MKV.getExtensions().contains("mkv"));
        assertEquals("video/x-matroska", FileFormat.MKV.getMimeType());
        assertEquals("mkv", FileFormat.MKV.getPrimaryExtension());
    }

    @Test
    @DisplayName("WMV format has correct properties")
    void wmv_HasCorrectProperties() {
        assertEquals(FormatCategory.VIDEO, FileFormat.WMV.getCategory());
        assertTrue(FileFormat.WMV.getExtensions().contains("wmv"));
        assertEquals("video/x-ms-wmv", FileFormat.WMV.getMimeType());
    }

    @Test
    @DisplayName("FLV format has correct properties")
    void flv_HasCorrectProperties() {
        assertEquals(FormatCategory.VIDEO, FileFormat.FLV.getCategory());
        assertTrue(FileFormat.FLV.getExtensions().contains("flv"));
        assertEquals("video/x-flv", FileFormat.FLV.getMimeType());
    }

    @Test
    @DisplayName("WEBM video format has correct properties")
    void webm_HasCorrectProperties() {
        assertEquals(FormatCategory.VIDEO, FileFormat.WEBM.getCategory());
        assertTrue(FileFormat.WEBM.getExtensions().contains("webm"));
        assertEquals("video/webm", FileFormat.WEBM.getMimeType());
    }

    // ========================================
    // Audio Format Tests
    // ========================================

    @Test
    @DisplayName("MP3 format has correct properties")
    void mp3_HasCorrectProperties() {
        assertEquals(FormatCategory.AUDIO, FileFormat.MP3.getCategory());
        assertTrue(FileFormat.MP3.getExtensions().contains("mp3"));
        assertEquals("audio/mpeg", FileFormat.MP3.getMimeType());
        assertEquals("mp3", FileFormat.MP3.getPrimaryExtension());
    }

    @Test
    @DisplayName("WAV format has correct properties")
    void wav_HasCorrectProperties() {
        assertEquals(FormatCategory.AUDIO, FileFormat.WAV.getCategory());
        assertTrue(FileFormat.WAV.getExtensions().contains("wav"));
        assertEquals("audio/wav", FileFormat.WAV.getMimeType());
    }

    @Test
    @DisplayName("FLAC format has correct properties")
    void flac_HasCorrectProperties() {
        assertEquals(FormatCategory.AUDIO, FileFormat.FLAC.getCategory());
        assertTrue(FileFormat.FLAC.getExtensions().contains("flac"));
        assertEquals("audio/flac", FileFormat.FLAC.getMimeType());
    }

    @Test
    @DisplayName("AAC format has correct properties")
    void aac_HasCorrectProperties() {
        assertEquals(FormatCategory.AUDIO, FileFormat.AAC.getCategory());
        assertTrue(FileFormat.AAC.getExtensions().contains("aac"));
        assertEquals("audio/aac", FileFormat.AAC.getMimeType());
    }

    @Test
    @DisplayName("OGG format has correct properties")
    void ogg_HasCorrectProperties() {
        assertEquals(FormatCategory.AUDIO, FileFormat.OGG.getCategory());
        assertTrue(FileFormat.OGG.getExtensions().contains("ogg"));
        assertTrue(FileFormat.OGG.getExtensions().contains("oga"));
        assertEquals("audio/ogg", FileFormat.OGG.getMimeType());
    }

    @Test
    @DisplayName("M4A format has correct properties")
    void m4a_HasCorrectProperties() {
        assertEquals(FormatCategory.AUDIO, FileFormat.M4A.getCategory());
        assertTrue(FileFormat.M4A.getExtensions().contains("m4a"));
        assertEquals("audio/mp4", FileFormat.M4A.getMimeType());
    }

    // ========================================
    // Image Format Tests
    // ========================================

    @Test
    @DisplayName("JPEG format has correct properties")
    void jpeg_HasCorrectProperties() {
        assertEquals(FormatCategory.IMAGE, FileFormat.JPEG.getCategory());
        assertTrue(FileFormat.JPEG.getExtensions().contains("jpg"));
        assertTrue(FileFormat.JPEG.getExtensions().contains("jpeg"));
        assertTrue(FileFormat.JPEG.getExtensions().contains("jpe"));
        assertEquals("image/jpeg", FileFormat.JPEG.getMimeType());
        assertEquals("jpg", FileFormat.JPEG.getPrimaryExtension());
    }

    @Test
    @DisplayName("PNG format has correct properties")
    void png_HasCorrectProperties() {
        assertEquals(FormatCategory.IMAGE, FileFormat.PNG.getCategory());
        assertTrue(FileFormat.PNG.getExtensions().contains("png"));
        assertEquals("image/png", FileFormat.PNG.getMimeType());
    }

    @Test
    @DisplayName("GIF format has correct properties")
    void gif_HasCorrectProperties() {
        assertEquals(FormatCategory.IMAGE, FileFormat.GIF.getCategory());
        assertTrue(FileFormat.GIF.getExtensions().contains("gif"));
        assertEquals("image/gif", FileFormat.GIF.getMimeType());
    }

    @Test
    @DisplayName("BMP format has correct properties")
    void bmp_HasCorrectProperties() {
        assertEquals(FormatCategory.IMAGE, FileFormat.BMP.getCategory());
        assertTrue(FileFormat.BMP.getExtensions().contains("bmp"));
        assertTrue(FileFormat.BMP.getExtensions().contains("dib"));
        assertEquals("image/bmp", FileFormat.BMP.getMimeType());
    }

    @Test
    @DisplayName("TIFF format has correct properties")
    void tiff_HasCorrectProperties() {
        assertEquals(FormatCategory.IMAGE, FileFormat.TIFF.getCategory());
        assertTrue(FileFormat.TIFF.getExtensions().contains("tiff"));
        assertTrue(FileFormat.TIFF.getExtensions().contains("tif"));
        assertEquals("image/tiff", FileFormat.TIFF.getMimeType());
    }

    @Test
    @DisplayName("WEBP format has correct properties")
    void webp_HasCorrectProperties() {
        assertEquals(FormatCategory.IMAGE, FileFormat.WEBP.getCategory());
        assertTrue(FileFormat.WEBP.getExtensions().contains("webp"));
        assertEquals("image/webp", FileFormat.WEBP.getMimeType());
    }

    // ========================================
    // Document Format Tests
    // ========================================

    @Test
    @DisplayName("DOCX format has correct properties")
    void docx_HasCorrectProperties() {
        assertEquals(FormatCategory.DOCUMENT, FileFormat.DOCX.getCategory());
        assertTrue(FileFormat.DOCX.getExtensions().contains("docx"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                FileFormat.DOCX.getMimeType());
    }

    @Test
    @DisplayName("PDF format has correct properties")
    void pdf_HasCorrectProperties() {
        assertEquals(FormatCategory.DOCUMENT, FileFormat.PDF.getCategory());
        assertTrue(FileFormat.PDF.getExtensions().contains("pdf"));
        assertEquals("application/pdf", FileFormat.PDF.getMimeType());
    }

    @Test
    @DisplayName("HTML format has correct properties")
    void html_HasCorrectProperties() {
        assertEquals(FormatCategory.DOCUMENT, FileFormat.HTML.getCategory());
        assertTrue(FileFormat.HTML.getExtensions().contains("html"));
        assertTrue(FileFormat.HTML.getExtensions().contains("htm"));
        assertEquals("text/html", FileFormat.HTML.getMimeType());
    }

    @Test
    @DisplayName("MARKDOWN format has correct properties")
    void markdown_HasCorrectProperties() {
        assertEquals(FormatCategory.DOCUMENT, FileFormat.MARKDOWN.getCategory());
        assertTrue(FileFormat.MARKDOWN.getExtensions().contains("md"));
        assertTrue(FileFormat.MARKDOWN.getExtensions().contains("markdown"));
        assertEquals("text/markdown", FileFormat.MARKDOWN.getMimeType());
    }

    @Test
    @DisplayName("RTF format has correct properties")
    void rtf_HasCorrectProperties() {
        assertEquals(FormatCategory.DOCUMENT, FileFormat.RTF.getCategory());
        assertTrue(FileFormat.RTF.getExtensions().contains("rtf"));
        assertEquals("application/rtf", FileFormat.RTF.getMimeType());
    }

    @Test
    @DisplayName("ODT format has correct properties")
    void odt_HasCorrectProperties() {
        assertEquals(FormatCategory.DOCUMENT, FileFormat.ODT.getCategory());
        assertTrue(FileFormat.ODT.getExtensions().contains("odt"));
        assertEquals("application/vnd.oasis.opendocument.text", FileFormat.ODT.getMimeType());
    }

    @Test
    @DisplayName("ODS format has correct properties")
    void ods_HasCorrectProperties() {
        assertEquals(FormatCategory.DOCUMENT, FileFormat.ODS.getCategory());
        assertTrue(FileFormat.ODS.getExtensions().contains("ods"));
        assertEquals("application/vnd.oasis.opendocument.spreadsheet", FileFormat.ODS.getMimeType());
    }

    @Test
    @DisplayName("ODP format has correct properties")
    void odp_HasCorrectProperties() {
        assertEquals(FormatCategory.DOCUMENT, FileFormat.ODP.getCategory());
        assertTrue(FileFormat.ODP.getExtensions().contains("odp"));
        assertEquals("application/vnd.oasis.opendocument.presentation", FileFormat.ODP.getMimeType());
    }

    @Test
    @DisplayName("XLSX format has correct properties")
    void xlsx_HasCorrectProperties() {
        assertEquals(FormatCategory.DOCUMENT, FileFormat.XLSX.getCategory());
        assertTrue(FileFormat.XLSX.getExtensions().contains("xlsx"));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                FileFormat.XLSX.getMimeType());
    }

    @Test
    @DisplayName("PPTX format has correct properties")
    void pptx_HasCorrectProperties() {
        assertEquals(FormatCategory.DOCUMENT, FileFormat.PPTX.getCategory());
        assertTrue(FileFormat.PPTX.getExtensions().contains("pptx"));
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                FileFormat.PPTX.getMimeType());
    }

    @Test
    @DisplayName("TXT format has correct properties")
    void txt_HasCorrectProperties() {
        assertEquals(FormatCategory.DOCUMENT, FileFormat.TXT.getCategory());
        assertTrue(FileFormat.TXT.getExtensions().contains("txt"));
        assertEquals("text/plain", FileFormat.TXT.getMimeType());
    }

    @Test
    @DisplayName("EPUB format has correct properties")
    void epub_HasCorrectProperties() {
        assertEquals(FormatCategory.DOCUMENT, FileFormat.EPUB.getCategory());
        assertTrue(FileFormat.EPUB.getExtensions().contains("epub"));
        assertEquals("application/epub+zip", FileFormat.EPUB.getMimeType());
    }

    // ========================================
    // Unknown Format Tests
    // ========================================

    @Test
    @DisplayName("UNKNOWN format has correct properties")
    void unknown_HasCorrectProperties() {
        assertEquals(FormatCategory.UNKNOWN, FileFormat.UNKNOWN.getCategory());
        assertTrue(FileFormat.UNKNOWN.getExtensions().isEmpty());
        assertEquals("application/octet-stream", FileFormat.UNKNOWN.getMimeType());
        assertEquals("", FileFormat.UNKNOWN.getPrimaryExtension());
    }

    // ========================================
    // fromExtension Tests
    // ========================================

    @ParameterizedTest
    @CsvSource({
            "mp4, MP4",
            "m4v, MP4",
            "avi, AVI",
            "mov, MOV",
            "qt, MOV",
            "mkv, MKV",
            "wmv, WMV",
            "flv, FLV",
            "webm, WEBM"
    })
    @DisplayName("fromExtension detects video formats correctly")
    void fromExtension_VideoFormats_Detected(String extension, FileFormat expected) {
        assertEquals(expected, FileFormat.fromExtension(extension));
    }

    @ParameterizedTest
    @CsvSource({
            "mp3, MP3",
            "wav, WAV",
            "flac, FLAC",
            "aac, AAC",
            "ogg, OGG",
            "oga, OGG",
            "m4a, M4A"
    })
    @DisplayName("fromExtension detects audio formats correctly")
    void fromExtension_AudioFormats_Detected(String extension, FileFormat expected) {
        assertEquals(expected, FileFormat.fromExtension(extension));
    }

    @ParameterizedTest
    @CsvSource({
            "jpg, JPEG",
            "jpeg, JPEG",
            "jpe, JPEG",
            "png, PNG",
            "gif, GIF",
            "bmp, BMP",
            "dib, BMP",
            "tiff, TIFF",
            "tif, TIFF",
            "webp, WEBP"
    })
    @DisplayName("fromExtension detects image formats correctly")
    void fromExtension_ImageFormats_Detected(String extension, FileFormat expected) {
        assertEquals(expected, FileFormat.fromExtension(extension));
    }

    @ParameterizedTest
    @CsvSource({
            "docx, DOCX",
            "pdf, PDF",
            "html, HTML",
            "htm, HTML",
            "md, MARKDOWN",
            "markdown, MARKDOWN",
            "rtf, RTF",
            "odt, ODT",
            "ods, ODS",
            "odp, ODP",
            "xlsx, XLSX",
            "pptx, PPTX",
            "txt, TXT",
            "epub, EPUB"
    })
    @DisplayName("fromExtension detects document formats correctly")
    void fromExtension_DocumentFormats_Detected(String extension, FileFormat expected) {
        assertEquals(expected, FileFormat.fromExtension(extension));
    }

    @Test
    @DisplayName("fromExtension handles extension with leading dot")
    void fromExtension_WithLeadingDot_DetectsCorrectly() {
        assertEquals(FileFormat.MP4, FileFormat.fromExtension(".mp4"));
        assertEquals(FileFormat.PNG, FileFormat.fromExtension(".png"));
        assertEquals(FileFormat.PDF, FileFormat.fromExtension(".pdf"));
    }

    @Test
    @DisplayName("fromExtension is case-insensitive")
    void fromExtension_CaseInsensitive() {
        assertEquals(FileFormat.MP4, FileFormat.fromExtension("MP4"));
        assertEquals(FileFormat.MP4, FileFormat.fromExtension("Mp4"));
        assertEquals(FileFormat.MP4, FileFormat.fromExtension("mP4"));
        assertEquals(FileFormat.JPEG, FileFormat.fromExtension("JPG"));
        assertEquals(FileFormat.JPEG, FileFormat.fromExtension("JpEg"));
    }

    @Test
    @DisplayName("fromExtension handles whitespace")
    void fromExtension_WithWhitespace_TrimsAndDetects() {
        assertEquals(FileFormat.MP4, FileFormat.fromExtension("  mp4  "));
        assertEquals(FileFormat.PNG, FileFormat.fromExtension(" .png "));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "unknown", "xyz", "12345" })
    @DisplayName("fromExtension returns UNKNOWN for invalid extensions")
    void fromExtension_InvalidExtension_ReturnsUnknown(String extension) {
        assertEquals(FileFormat.UNKNOWN, FileFormat.fromExtension(extension));
    }

    @Test
    @DisplayName("fromExtension with null returns UNKNOWN")
    void fromExtension_WithNull_ReturnsUnknown() {
        assertEquals(FileFormat.UNKNOWN, FileFormat.fromExtension(null));
    }

    // ========================================
    // fromMimeType Tests
    // ========================================

    @ParameterizedTest
    @CsvSource({
            "video/mp4, MP4",
            "video/x-msvideo, AVI",
            "video/quicktime, MOV",
            "video/x-matroska, MKV",
            "video/x-ms-wmv, WMV",
            "video/x-flv, FLV",
            "video/webm, WEBM"
    })
    @DisplayName("fromMimeType detects video formats correctly")
    void fromMimeType_VideoFormats_Detected(String mimeType, FileFormat expected) {
        assertEquals(expected, FileFormat.fromMimeType(mimeType));
    }

    @ParameterizedTest
    @CsvSource({
            "audio/mpeg, MP3",
            "audio/wav, WAV",
            "audio/flac, FLAC",
            "audio/aac, AAC",
            "audio/ogg, OGG",
            "audio/mp4, M4A"
    })
    @DisplayName("fromMimeType detects audio formats correctly")
    void fromMimeType_AudioFormats_Detected(String mimeType, FileFormat expected) {
        assertEquals(expected, FileFormat.fromMimeType(mimeType));
    }

    @ParameterizedTest
    @CsvSource({
            "image/jpeg, JPEG",
            "image/png, PNG",
            "image/gif, GIF",
            "image/bmp, BMP",
            "image/tiff, TIFF",
            "image/webp, WEBP"
    })
    @DisplayName("fromMimeType detects image formats correctly")
    void fromMimeType_ImageFormats_Detected(String mimeType, FileFormat expected) {
        assertEquals(expected, FileFormat.fromMimeType(mimeType));
    }

    @Test
    @DisplayName("fromMimeType detects document formats correctly")
    void fromMimeType_DocumentFormats_Detected() {
        assertEquals(FileFormat.DOCX,
                FileFormat.fromMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertEquals(FileFormat.PDF, FileFormat.fromMimeType("application/pdf"));
        assertEquals(FileFormat.HTML, FileFormat.fromMimeType("text/html"));
        assertEquals(FileFormat.MARKDOWN, FileFormat.fromMimeType("text/markdown"));
        assertEquals(FileFormat.RTF, FileFormat.fromMimeType("application/rtf"));
        assertEquals(FileFormat.ODT, FileFormat.fromMimeType("application/vnd.oasis.opendocument.text"));
        assertEquals(FileFormat.XLSX,
                FileFormat.fromMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertEquals(FileFormat.TXT, FileFormat.fromMimeType("text/plain"));
        assertEquals(FileFormat.EPUB, FileFormat.fromMimeType("application/epub+zip"));
    }

    @Test
    @DisplayName("fromMimeType is case-insensitive")
    void fromMimeType_CaseInsensitive() {
        assertEquals(FileFormat.MP4, FileFormat.fromMimeType("VIDEO/MP4"));
        assertEquals(FileFormat.MP4, FileFormat.fromMimeType("Video/Mp4"));
        assertEquals(FileFormat.PNG, FileFormat.fromMimeType("IMAGE/PNG"));
    }

    @Test
    @DisplayName("fromMimeType handles whitespace")
    void fromMimeType_WithWhitespace_TrimsAndDetects() {
        assertEquals(FileFormat.MP4, FileFormat.fromMimeType("  video/mp4  "));
        assertEquals(FileFormat.PNG, FileFormat.fromMimeType(" image/png "));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "unknown/type", "invalid", "text/unknown" })
    @DisplayName("fromMimeType returns UNKNOWN for invalid MIME types")
    void fromMimeType_InvalidMimeType_ReturnsUnknown(String mimeType) {
        assertEquals(FileFormat.UNKNOWN, FileFormat.fromMimeType(mimeType));
    }

    @Test
    @DisplayName("fromMimeType with null returns UNKNOWN")
    void fromMimeType_WithNull_ReturnsUnknown() {
        assertEquals(FileFormat.UNKNOWN, FileFormat.fromMimeType(null));
    }

    // ========================================
    // Input/Output Validity Tests
    // ========================================

    @ParameterizedTest
    @EnumSource(value = FileFormat.class, names = "UNKNOWN", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("All formats except UNKNOWN are valid input")
    void allFormatsExceptUnknown_AreValidInput(FileFormat format) {
        assertTrue(format.isValidInput());
    }

    @Test
    @DisplayName("UNKNOWN format is not valid input")
    void unknownFormat_IsNotValidInput() {
        assertFalse(FileFormat.UNKNOWN.isValidInput());
    }

    @ParameterizedTest
    @EnumSource(value = FileFormat.class, names = "UNKNOWN", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("All formats except UNKNOWN are valid output")
    void allFormatsExceptUnknown_AreValidOutput(FileFormat format) {
        assertTrue(format.isValidOutput());
    }

    @Test
    @DisplayName("UNKNOWN format is not valid output")
    void unknownFormat_IsNotValidOutput() {
        assertFalse(FileFormat.UNKNOWN.isValidOutput());
    }

    // ========================================
    // Category Filtering Tests
    // ========================================

    @Test
    @DisplayName("getFormatsByCategory returns all VIDEO formats")
    void getFormatsByCategory_Video_ReturnsAllVideoFormats() {
        FileFormat[] videoFormats = FileFormat.getFormatsByCategory(FormatCategory.VIDEO);

        assertEquals(8, videoFormats.length);
        assertTrue(Arrays.asList(videoFormats).contains(FileFormat.MP4));
        assertTrue(Arrays.asList(videoFormats).contains(FileFormat.AVI));
        assertTrue(Arrays.asList(videoFormats).contains(FileFormat.MOV));
        assertTrue(Arrays.asList(videoFormats).contains(FileFormat.MKV));
        assertTrue(Arrays.asList(videoFormats).contains(FileFormat.WMV));
        assertTrue(Arrays.asList(videoFormats).contains(FileFormat.FLV));
        assertTrue(Arrays.asList(videoFormats).contains(FileFormat.WEBM));
        assertTrue(Arrays.asList(videoFormats).contains(FileFormat.MP3));
    }

    @Test
    @DisplayName("getFormatsByCategory returns all AUDIO formats")
    void getFormatsByCategory_Audio_ReturnsAllAudioFormats() {
        FileFormat[] audioFormats = FileFormat.getFormatsByCategory(FormatCategory.AUDIO);

        assertEquals(6, audioFormats.length);
        assertTrue(Arrays.asList(audioFormats).contains(FileFormat.MP3));
        assertTrue(Arrays.asList(audioFormats).contains(FileFormat.WAV));
        assertTrue(Arrays.asList(audioFormats).contains(FileFormat.FLAC));
        assertTrue(Arrays.asList(audioFormats).contains(FileFormat.AAC));
        assertTrue(Arrays.asList(audioFormats).contains(FileFormat.OGG));
        assertTrue(Arrays.asList(audioFormats).contains(FileFormat.M4A));
    }

    @Test
    @DisplayName("getFormatsByCategory returns all IMAGE formats")
    void getFormatsByCategory_Image_ReturnsAllImageFormats() {
        FileFormat[] imageFormats = FileFormat.getFormatsByCategory(FormatCategory.IMAGE);

        // Requirement REQ-PDF-1.2: PDF should be included in IMAGE category
        assertEquals(8, imageFormats.length);
        assertTrue(Arrays.asList(imageFormats).contains(FileFormat.JPEG));
        assertTrue(Arrays.asList(imageFormats).contains(FileFormat.PNG));
        assertTrue(Arrays.asList(imageFormats).contains(FileFormat.GIF));
        assertTrue(Arrays.asList(imageFormats).contains(FileFormat.BMP));
        assertTrue(Arrays.asList(imageFormats).contains(FileFormat.TIFF));
        assertTrue(Arrays.asList(imageFormats).contains(FileFormat.WEBP));
        assertTrue(Arrays.asList(imageFormats).contains(FileFormat.SVG));
        assertTrue(Arrays.asList(imageFormats).contains(FileFormat.PDF));
    }

    @Test
    @DisplayName("getFormatsByCategory returns all DOCUMENT formats")
    void getFormatsByCategory_Document_ReturnsAllDocumentFormats() {
        FileFormat[] documentFormats = FileFormat.getFormatsByCategory(FormatCategory.DOCUMENT);

        assertEquals(21, documentFormats.length);
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.DOCX));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.DOC));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.PDF));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.HTML));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.MARKDOWN));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.RTF));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.ODT));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.ODS));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.ODP));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.XLSX));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.XLS));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.PPTX));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.PPT));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.TXT));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.EPUB));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.TEX));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.LATEX));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.RST));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.ORG));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.CSV));
        assertTrue(Arrays.asList(documentFormats).contains(FileFormat.JPEG));
    }

    @Test
    @DisplayName("getFormatsByCategory returns UNKNOWN format for UNKNOWN category")
    void getFormatsByCategory_Unknown_ReturnsUnknownFormat() {
        FileFormat[] unknownFormats = FileFormat.getFormatsByCategory(FormatCategory.UNKNOWN);

        assertEquals(1, unknownFormats.length);
        assertEquals(FileFormat.UNKNOWN, unknownFormats[0]);
    }

    // ========================================
    // Edge Cases and Integration Tests
    // ========================================

    @Test
    @DisplayName("All formats have non-null category")
    void allFormats_HaveNonNullCategory() {
        for (FileFormat format : FileFormat.values()) {
            assertNotNull(format.getCategory());
        }
    }

    @Test
    @DisplayName("All formats have non-null MIME type")
    void allFormats_HaveNonNullMimeType() {
        for (FileFormat format : FileFormat.values()) {
            assertNotNull(format.getMimeType());
            assertFalse(format.getMimeType().isBlank());
        }
    }

    @Test
    @DisplayName("All formats except UNKNOWN have at least one extension")
    void allFormatsExceptUnknown_HaveAtLeastOneExtension() {
        for (FileFormat format : FileFormat.values()) {
            if (format != FileFormat.UNKNOWN) {
                assertFalse(format.getExtensions().isEmpty(),
                        format + " should have at least one extension");
            }
        }
    }

    @Test
    @DisplayName("Extension detection is reversible for primary extension")
    void extensionDetection_IsReversibleForPrimaryExtension() {
        for (FileFormat format : FileFormat.values()) {
            if (format != FileFormat.UNKNOWN) {
                String primaryExt = format.getPrimaryExtension();
                FileFormat detected = FileFormat.fromExtension(primaryExt);
                assertEquals(format, detected,
                        "Primary extension '" + primaryExt + "' should detect " + format);
            }
        }
    }

    @Test
    @DisplayName("MIME type detection is reversible")
    void mimeTypeDetection_IsReversible() {
        for (FileFormat format : FileFormat.values()) {
            String mimeType = format.getMimeType();
            FileFormat detected = FileFormat.fromMimeType(mimeType);
            assertEquals(format, detected,
                    "MIME type '" + mimeType + "' should detect " + format);
        }
    }

    @Test
    @DisplayName("All formats across all categories cover all enum values (accounting for dual-category formats)")
    void categoryFiltering_CoversAllFormats() {
        int total = 0;
        for (FormatCategory category : FormatCategory.values()) {
            total += FileFormat.getFormatsByCategory(category).length;
        }

        // PDF appears in both DOCUMENT and IMAGE categories, so total will be enum
        // count + 1
        int expectedTotal = FileFormat.values().length + 3; // +3 for PDF/JPEG/MP3 dual-category
        assertEquals(expectedTotal, total,
                "Sum of formats in all categories should equal total enum values plus dual-category formats (PDF)");
    }
}
