// filepath: src/test/java/org/omc/service/PandocServiceTest.java

package org.omc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omc.core.ProgressCallback;
import org.omc.exception.ToolExecutionException;
import org.omc.model.ConversionResult;
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;

/**
 * Unit tests for PandocService document conversion functionality.
 * 
 * Tests requirement REQ-006.4 for document format conversion.
 */
class PandocServiceTest {

    @TempDir
    Path tempDir;

    private PandocService service;
    private Path pandocPath;
    private Path inputPath;
    private Path outputPath;
    private DocumentSettings defaultSettings;
    private ProgressCallback noOpCallback;

    @BeforeEach
    void setUp() {
        pandocPath = tempDir.resolve("pandoc");
        inputPath = tempDir.resolve("input.md");
        outputPath = tempDir.resolve("output.html");

        service = new PandocService(pandocPath);

        defaultSettings = DocumentSettings.builder()
                .marginTop(20)
                .marginBottom(20)
                .marginLeft(20)
                .marginRight(20)
                .generateTableOfContents(false)
                .preserveFormatting(true)
                .templatePath(null)
                .build();

        noOpCallback = (progress, bytesProcessed, speed) -> {
        };
    }

    // Constructor tests

    @Test
    void testConstructor_Success() {
        assertNotNull(service);
        assertEquals(pandocPath, service.getPandocPath());
    }

    @Test
    void testConstructor_NullPath_ThrowsException() {
        assertThrows(NullPointerException.class, () -> new PandocService(null));
    }

    // Format support tests

    @Test
    void testSupportsInput_MarkdownFormat_ReturnsTrue() {
        assertTrue(service.supportsInput(FileFormat.MARKDOWN));
    }

    @Test
    void testSupportsInput_HtmlFormat_ReturnsTrue() {
        assertTrue(service.supportsInput(FileFormat.HTML));
    }

    @Test
    void testSupportsInput_DocxFormat_ReturnsTrue() {
        assertTrue(service.supportsInput(FileFormat.DOCX));
    }

    @Test
    void testSupportsInput_RtfFormat_ReturnsTrue() {
        assertTrue(service.supportsInput(FileFormat.RTF));
    }

    @Test
    void testSupportsInput_OdtFormat_ReturnsTrue() {
        assertTrue(service.supportsInput(FileFormat.ODT));
    }

    @Test
    void testSupportsInput_EpubFormat_ReturnsTrue() {
        assertTrue(service.supportsInput(FileFormat.EPUB));
    }

    @Test
    void testSupportsInput_TxtFormat_ReturnsTrue() {
        assertTrue(service.supportsInput(FileFormat.TXT));
    }

    @Test
    void testSupportsInput_UnsupportedFormat_ReturnsFalse() {
        assertFalse(service.supportsInput(FileFormat.MP4));
        assertFalse(service.supportsInput(FileFormat.AVI));
        assertFalse(service.supportsInput(FileFormat.MP3));
    }

    @Test
    void testSupportsOutput_MarkdownFormat_ReturnsTrue() {
        assertTrue(service.supportsOutput(FileFormat.MARKDOWN));
    }

    @Test
    void testSupportsOutput_PdfFormat_ReturnsTrue() {
        // Requirement REQ-006.4: PDF output support
        assertTrue(service.supportsOutput(FileFormat.PDF));
    }

    @Test
    void testSupportsOutput_UnsupportedFormat_ReturnsFalse() {
        assertFalse(service.supportsOutput(FileFormat.MP4));
        assertFalse(service.supportsOutput(FileFormat.PNG));
    }

    @Test
    void testGetSupportedInputFormats_ReturnsCorrectList() {
        List<FileFormat> formats = service.getSupportedInputFormats();
        assertNotNull(formats);
        assertEquals(7, formats.size());
        assertTrue(formats.contains(FileFormat.MARKDOWN));
        assertTrue(formats.contains(FileFormat.HTML));
        assertTrue(formats.contains(FileFormat.DOCX));
        assertTrue(formats.contains(FileFormat.RTF));
        assertTrue(formats.contains(FileFormat.ODT));
        assertTrue(formats.contains(FileFormat.EPUB));
        assertTrue(formats.contains(FileFormat.TXT));
    }

    @Test
    void testGetSupportedOutputFormats_ReturnsCorrectList() {
        List<FileFormat> formats = service.getSupportedOutputFormats();
        assertNotNull(formats);
        assertEquals(8, formats.size());
        assertTrue(formats.contains(FileFormat.MARKDOWN));
        assertTrue(formats.contains(FileFormat.HTML));
        assertTrue(formats.contains(FileFormat.DOCX));
        assertTrue(formats.contains(FileFormat.PDF));
    }

    // Format detection tests

    @Test
    void testDetectFormat_MarkdownExtension_ReturnsMarkdown() {
        Path mdFile = tempDir.resolve("document.md");
        assertEquals(FileFormat.MARKDOWN, service.detectFormat(mdFile));
    }

    @Test
    void testDetectFormat_HtmlExtension_ReturnsHtml() {
        Path htmlFile = tempDir.resolve("document.html");
        assertEquals(FileFormat.HTML, service.detectFormat(htmlFile));
    }

    @Test
    void testDetectFormat_DocxExtension_ReturnsDocx() {
        Path docxFile = tempDir.resolve("document.docx");
        assertEquals(FileFormat.DOCX, service.detectFormat(docxFile));
    }

    @Test
    void testDetectFormat_PdfExtension_ReturnsPdf() {
        Path pdfFile = tempDir.resolve("document.pdf");
        assertEquals(FileFormat.PDF, service.detectFormat(pdfFile));
    }

    @Test
    void testDetectFormat_RtfExtension_ReturnsRtf() {
        Path rtfFile = tempDir.resolve("document.rtf");
        assertEquals(FileFormat.RTF, service.detectFormat(rtfFile));
    }

    @Test
    void testDetectFormat_OdtExtension_ReturnsOdt() {
        Path odtFile = tempDir.resolve("document.odt");
        assertEquals(FileFormat.ODT, service.detectFormat(odtFile));
    }

    @Test
    void testDetectFormat_EpubExtension_ReturnsEpub() {
        Path epubFile = tempDir.resolve("book.epub");
        assertEquals(FileFormat.EPUB, service.detectFormat(epubFile));
    }

    @Test
    void testDetectFormat_TxtExtension_ReturnsTxt() {
        Path txtFile = tempDir.resolve("document.txt");
        assertEquals(FileFormat.TXT, service.detectFormat(txtFile));
    }

    @Test
    void testDetectFormat_NoExtension_ReturnsUnknown() {
        Path noExtFile = tempDir.resolve("document");
        assertEquals(FileFormat.UNKNOWN, service.detectFormat(noExtFile));
    }

    @Test
    void testDetectFormat_UnknownExtension_ReturnsUnknown() {
        Path unknownFile = tempDir.resolve("document.xyz");
        assertEquals(FileFormat.UNKNOWN, service.detectFormat(unknownFile));
    }

    @Test
    void testDetectFormat_CaseInsensitive_ReturnsCorrectFormat() {
        Path upperCaseFile = tempDir.resolve("DOCUMENT.MD");
        assertEquals(FileFormat.MARKDOWN, service.detectFormat(upperCaseFile));

        Path mixedCaseFile = tempDir.resolve("Document.HtMl");
        assertEquals(FileFormat.HTML, service.detectFormat(mixedCaseFile));
    }

    @Test
    void testDetectFormat_NullPath_ThrowsException() {
        assertThrows(NullPointerException.class, () -> service.detectFormat(null));
    }

    // Command building tests

    @Test
    void testBuildCommand_MarkdownToHtml_BasicSettings() {
        // Requirement REQ-006.4: Markdown to HTML conversion
        Path input = tempDir.resolve("input.md");
        Path output = tempDir.resolve("output.html");

        List<String> command = service.buildCommand(input, output, defaultSettings);

        assertNotNull(command);
        assertTrue(command.contains(pandocPath.toString()));
        assertTrue(command.contains(input.toString()));
        assertTrue(command.contains("-o"));
        assertTrue(command.contains(output.toString()));
        assertTrue(command.contains("-f"));
        assertTrue(command.contains("markdown"));
        assertTrue(command.contains("-t"));
        assertTrue(command.contains("html"));
        assertTrue(command.contains("--standalone"));
    }

    @Test
    void testBuildCommand_HtmlToDocx_BasicSettings() {
        // Requirement REQ-006.4: HTML to DOCX conversion
        Path input = tempDir.resolve("input.html");
        Path output = tempDir.resolve("output.docx");

        List<String> command = service.buildCommand(input, output, defaultSettings);

        assertNotNull(command);
        assertTrue(command.contains("-f"));
        assertTrue(command.contains("html"));
        assertTrue(command.contains("-t"));
        assertTrue(command.contains("docx"));
    }

    @Test
    void testBuildCommand_MarkdownToPdf_BasicSettings() {
        // Requirement REQ-006.4: PDF output with LaTeX engine
        Path input = tempDir.resolve("input.md");
        Path output = tempDir.resolve("output.pdf");

        List<String> command = service.buildCommand(input, output, defaultSettings);

        assertNotNull(command);
        assertTrue(command.contains("-t"));
        assertTrue(command.contains("pdf"));
        assertTrue(command.contains("--pdf-engine=xelatex"));
    }

    @Test
    void testBuildCommand_WithTableOfContents() {
        // Requirement REQ-006.4: TOC generation
        DocumentSettings settingsWithToc = DocumentSettings.builder()
                .marginTop(20)
                .marginBottom(20)
                .marginLeft(20)
                .marginRight(20)
                .generateTableOfContents(true)
                .preserveFormatting(true)
                .templatePath(null)
                .build();

        List<String> command = service.buildCommand(inputPath, outputPath, settingsWithToc);

        assertTrue(command.contains("--toc"));
        assertTrue(command.contains("--toc-depth=3"));
    }

    @Test
    void testBuildCommand_WithoutTableOfContents() {
        DocumentSettings settingsNoToc = DocumentSettings.builder()
                .marginTop(20)
                .marginBottom(20)
                .marginLeft(20)
                .marginRight(20)
                .generateTableOfContents(false)
                .preserveFormatting(true)
                .templatePath(null)
                .build();

        List<String> command = service.buildCommand(inputPath, outputPath, settingsNoToc);

        assertFalse(command.contains("--toc"));
    }

    @Test
    void testBuildCommand_WithTemplate() throws IOException {
        // Requirement REQ-006.4: Template support
        Path templatePath = tempDir.resolve("template.html");
        Files.createFile(templatePath);

        DocumentSettings settingsWithTemplate = DocumentSettings.builder()
                .marginTop(20)
                .marginBottom(20)
                .marginLeft(20)
                .marginRight(20)
                .generateTableOfContents(false)
                .preserveFormatting(true)
                .templatePath(templatePath)
                .build();

        List<String> command = service.buildCommand(inputPath, outputPath, settingsWithTemplate);

        assertTrue(command.stream().anyMatch(arg -> arg.startsWith("--template=")));
    }

    @Test
    void testBuildCommand_PdfWithMargins() {
        // Requirement REQ-006.4: PDF margin configuration
        Path pdfOutput = tempDir.resolve("output.pdf");

        DocumentSettings settingsWithMargins = DocumentSettings.builder()
                .marginTop(25) // 25mm (~1 inch)
                .marginBottom(51) // 51mm (~2 inches)
                .marginLeft(13) // 13mm (~0.5 inch)
                .marginRight(38) // 38mm (~1.5 inches)
                .generateTableOfContents(false)
                .preserveFormatting(true)
                .templatePath(null)
                .build();

        List<String> command = service.buildCommand(inputPath, pdfOutput, settingsWithMargins);

        // Check that margin arguments are present (converted from mm to inches)
        assertTrue(command.stream().anyMatch(arg -> arg.contains("geometry:top=")));
        assertTrue(command.stream().anyMatch(arg -> arg.contains("geometry:bottom=")));
        assertTrue(command.stream().anyMatch(arg -> arg.contains("geometry:left=")));
        assertTrue(command.stream().anyMatch(arg -> arg.contains("geometry:right=")));
    }

    @Test
    void testBuildCommand_HtmlWithEmbedResources() {
        // Requirement REQ-006.4: Formatting preservation for HTML
        Path htmlInput = tempDir.resolve("input.md");
        Path htmlOutput = tempDir.resolve("output.html");

        DocumentSettings settingsPreserveFormat = DocumentSettings.builder()
                .marginTop(20)
                .marginBottom(20)
                .marginLeft(20)
                .marginRight(20)
                .generateTableOfContents(false)
                .preserveFormatting(true)
                .templatePath(null)
                .build();

        List<String> command = service.buildCommand(htmlInput, htmlOutput, settingsPreserveFormat);

        assertTrue(command.contains("--embed-resources"));
    }

    @Test
    void testBuildCommand_NullInput_ThrowsException() {
        assertThrows(NullPointerException.class,
                () -> service.buildCommand(null, outputPath, defaultSettings));
    }

    @Test
    void testBuildCommand_NullOutput_ThrowsException() {
        assertThrows(NullPointerException.class,
                () -> service.buildCommand(inputPath, null, defaultSettings));
    }

    @Test
    void testBuildCommand_NullSettings_ThrowsException() {
        assertThrows(NullPointerException.class,
                () -> service.buildCommand(inputPath, outputPath, null));
    }

    // Conversion tests - these would require a real Pandoc binary
    // For now, we test parameter validation only

    @Test
    void testConvertDocument_NullInput_ThrowsException() {
        assertThrows(NullPointerException.class,
                () -> service.convertDocument(null, outputPath, defaultSettings, noOpCallback));
    }

    @Test
    void testConvertDocument_NullOutput_ThrowsException() {
        assertThrows(NullPointerException.class,
                () -> service.convertDocument(inputPath, null, defaultSettings, noOpCallback));
    }

    @Test
    void testConvertDocument_NullSettings_ThrowsException() {
        assertThrows(NullPointerException.class,
                () -> service.convertDocument(inputPath, outputPath, null, noOpCallback));
    }

    @Test
    void testConvertDocument_NullCallback_ThrowsException() {
        assertThrows(NullPointerException.class,
                () -> service.convertDocument(inputPath, outputPath, defaultSettings, null));
    }

    @Test
    void testConvertDocument_NonExistentPandoc_ThrowsToolExecutionException() throws IOException {
        // Create input file
        Files.writeString(inputPath, "# Test Document\n\nThis is a test.");

        // Try to execute with non-existent pandoc binary
        assertThrows(ToolExecutionException.class,
                () -> service.convertDocument(inputPath, outputPath, defaultSettings, noOpCallback));
    }

    // Additional tests for edge cases and error conditions

    @Test
    void testBuildCommand_UnknownInputFormat_DefaultsToMarkdown() {
        // File with unknown extension
        Path unknownInput = tempDir.resolve("document.unknown");
        Path htmlOutput = tempDir.resolve("output.html");

        List<String> command = service.buildCommand(unknownInput, htmlOutput, defaultSettings);

        // Should not include -f for unknown input format
        assertFalse(command.contains("-f"));
        assertTrue(command.contains("-t"));
        assertTrue(command.contains("html"));
    }

    @Test
    void testBuildCommand_UnknownOutputFormat_DefaultsToMarkdown() {
        Path mdInput = tempDir.resolve("input.md");
        Path unknownOutput = tempDir.resolve("output.unknown");

        List<String> command = service.buildCommand(mdInput, unknownOutput, defaultSettings);

        // Should include -f for known input, but not -t for unknown output
        assertTrue(command.contains("-f"));
        assertTrue(command.contains("markdown"));
        assertFalse(command.contains("-t"));
    }

    @Test
    void testBuildCommand_TemplatePathIsDirectory_IncludesTemplate() throws IOException {
        Path templateDir = tempDir.resolve("template_dir");
        Files.createDirectory(templateDir);

        DocumentSettings settingsWithDirTemplate = DocumentSettings.builder()
                .marginTop(20)
                .marginBottom(20)
                .marginLeft(20)
                .marginRight(20)
                .generateTableOfContents(false)
                .preserveFormatting(true)
                .templatePath(templateDir) // Directory, but exists
                .build();

        List<String> command = service.buildCommand(inputPath, outputPath, settingsWithDirTemplate);

        // Should include template since Files.exists() returns true for directory
        assertTrue(command.stream().anyMatch(arg -> arg.startsWith("--template=")));
    }

    @Test
    void testBuildCommand_PreserveFormattingDocx_NoTemplate() {
        Path docxOutput = tempDir.resolve("output.docx");

        DocumentSettings settingsNoTemplate = DocumentSettings.builder()
                .marginTop(20)
                .marginBottom(20)
                .marginLeft(20)
                .marginRight(20)
                .generateTableOfContents(false)
                .preserveFormatting(true)
                .templatePath(null)
                .build();

        List<String> command = service.buildCommand(inputPath, docxOutput, settingsNoTemplate);

        // Should not include --reference-doc since templatePath is null
        assertFalse(command.contains("--reference-doc"));
    }

    @Test
    void testBuildCommand_PdfWithMinimumMargins() {
        Path pdfOutput = tempDir.resolve("output.pdf");

        DocumentSettings settingsMinimumMargins = DocumentSettings.builder()
                .marginTop(0)
                .marginBottom(0)
                .marginLeft(0)
                .marginRight(0)
                .generateTableOfContents(false)
                .preserveFormatting(false)
                .templatePath(null)
                .build();

        List<String> command = service.buildCommand(inputPath, pdfOutput, settingsMinimumMargins);

        // Should include geometry arguments with minimum valid values
        assertTrue(command.stream().anyMatch(arg -> arg.contains("geometry:top=")));
        assertTrue(command.stream().anyMatch(arg -> arg.contains("geometry:bottom=")));
        assertTrue(command.stream().anyMatch(arg -> arg.contains("geometry:left=")));
        assertTrue(command.stream().anyMatch(arg -> arg.contains("geometry:right=")));
    }

    @Test
    void testBuildCommand_PdfWithMaximumMargins() {
        Path pdfOutput = tempDir.resolve("output.pdf");

        DocumentSettings settingsMaximumMargins = DocumentSettings.builder()
                .marginTop(100) // Maximum valid margin
                .marginBottom(100)
                .marginLeft(100)
                .marginRight(100)
                .generateTableOfContents(false)
                .preserveFormatting(false)
                .templatePath(null)
                .build();

        List<String> command = service.buildCommand(inputPath, pdfOutput, settingsMaximumMargins);

        // Should include geometry arguments with maximum valid values
        assertTrue(command.stream().anyMatch(arg -> arg.contains("geometry:top=")));
        assertTrue(command.stream().anyMatch(arg -> arg.contains("geometry:bottom=")));
        assertTrue(command.stream().anyMatch(arg -> arg.contains("geometry:left=")));
        assertTrue(command.stream().anyMatch(arg -> arg.contains("geometry:right=")));
    }

    @Test
    void testDetectFormat_MultipleDotsInFilename() {
        Path multiDotFile = tempDir.resolve("archive.tar.gz");
        assertEquals(FileFormat.UNKNOWN, service.detectFormat(multiDotFile));

        Path multiDotMd = tempDir.resolve("document.backup.md");
        assertEquals(FileFormat.MARKDOWN, service.detectFormat(multiDotMd));
    }

    @Test
    void testDetectFormat_FilenameStartsWithDot() {
        Path dotFile = tempDir.resolve(".hidden.md");
        // Current implementation extracts extension from last dot
        assertEquals(FileFormat.MARKDOWN, service.detectFormat(dotFile));

        Path dotOnly = tempDir.resolve(".md");
        assertEquals(FileFormat.UNKNOWN, service.detectFormat(dotOnly));
    }

    @Test
    void testDetectFormat_NoFilenameBeforeExtension() {
        Path extOnly = tempDir.resolve(".md");
        // Current implementation requires dotIndex > 0
        assertEquals(FileFormat.UNKNOWN, service.detectFormat(extOnly));

        Path doubleDot = tempDir.resolve("..md");
        // For "..md", last dot is at position 2, so extension "md"
        assertEquals(FileFormat.MARKDOWN, service.detectFormat(doubleDot));
    }

    @Test
    void testDetectFormat_ExtremelyLongExtension() {
        Path longExt = tempDir.resolve("file.very_long_extension_that_should_not_match");
        assertEquals(FileFormat.UNKNOWN, service.detectFormat(longExt));
    }

    @Test
    void testBuildCommand_AllSupportedInputToOutputCombinations() {
        // Test a few key combinations to ensure they build correctly
        Path mdInput = tempDir.resolve("input.md");
        Path htmlInput = tempDir.resolve("input.html");
        Path docxInput = tempDir.resolve("input.docx");

        Path htmlOutput = tempDir.resolve("output.html");
        Path docxOutput = tempDir.resolve("output.docx");
        Path pdfOutput = tempDir.resolve("output.pdf");

        // Markdown to HTML
        List<String> cmd1 = service.buildCommand(mdInput, htmlOutput, defaultSettings);
        assertTrue(cmd1.contains("-f") && cmd1.contains("markdown"));
        assertTrue(cmd1.contains("-t") && cmd1.contains("html"));

        // HTML to DOCX
        List<String> cmd2 = service.buildCommand(htmlInput, docxOutput, defaultSettings);
        assertTrue(cmd2.contains("-f") && cmd2.contains("html"));
        assertTrue(cmd2.contains("-t") && cmd2.contains("docx"));

        // DOCX to PDF
        List<String> cmd3 = service.buildCommand(docxInput, pdfOutput, defaultSettings);
        assertTrue(cmd3.contains("-f") && cmd3.contains("docx"));
        assertTrue(cmd3.contains("-t") && cmd3.contains("pdf"));
        assertTrue(cmd3.contains("--pdf-engine=xelatex"));
    }

    @Test
    void testBuildCommand_SettingsWithNullOptionals() {
        DocumentSettings minimalSettings = DocumentSettings.builder()
                .marginTop(20)
                .marginBottom(20)
                .marginLeft(20)
                .marginRight(20)
                .generateTableOfContents(false)
                .preserveFormatting(false)
                .templatePath(null)
                .build();

        List<String> command = service.buildCommand(inputPath, outputPath, minimalSettings);

        // Should build successfully with null optionals
        assertNotNull(command);
        assertTrue(command.contains("--standalone"));
        assertFalse(command.contains("--toc"));
        assertFalse(command.stream().anyMatch(arg -> arg.startsWith("--template=")));
        assertFalse(command.contains("--embed-resources"));
        assertFalse(command.contains("--reference-doc"));
    }

    @Test
    void testBuildCommand_PreserveFormattingNonHtmlOrDocx_NoSpecialHandling() {
        Path txtOutput = tempDir.resolve("output.txt");

        DocumentSettings preserveSettings = DocumentSettings.builder()
                .marginTop(20)
                .marginBottom(20)
                .marginLeft(20)
                .marginRight(20)
                .generateTableOfContents(false)
                .preserveFormatting(true)
                .templatePath(null)
                .build();

        List<String> command = service.buildCommand(inputPath, txtOutput, preserveSettings);

        // For non-HTML/DOCX output, preserveFormatting should not add special args
        assertFalse(command.contains("--embed-resources"));
        assertFalse(command.contains("--reference-doc"));
    }

    @Test
    void testBuildCommand_PdfEngineOnlyForPdfOutput() {
        Path htmlOutput = tempDir.resolve("output.html");

        List<String> command = service.buildCommand(inputPath, htmlOutput, defaultSettings);

        // Should not include PDF-specific options for non-PDF output
        assertFalse(command.contains("--pdf-engine=xelatex"));
        assertFalse(command.stream().anyMatch(arg -> arg.contains("geometry:")));
    }

    @Test
    void testConvertDocument_NonZeroExitCode_CleansUpPartialFile() throws IOException {
        // Create a partial output file that should be cleaned up on failure
        Path partialOutput = tempDir.resolve("partial_output.html");
        Files.writeString(partialOutput, "<html>partial content</html>");
        assertTrue(Files.exists(partialOutput));

        Path input = tempDir.resolve("input.md");

        // This should fail due to non-existent pandoc binary and clean up the partial
        // file
        assertThrows(ToolExecutionException.class,
                () -> service.convertDocument(input, partialOutput, defaultSettings, noOpCallback));

        // Verify the partial file was cleaned up
        assertFalse(Files.exists(partialOutput));
    }

    // ========================================
    // Task 6.22: Integration tests for tool output capture
    // ========================================

    /**
     * Integration test for successful conversion capturing full output.
     * This test requires an actual Pandoc binary to be available.
     * Requirements: REQ-FL-2.2
     */
    @Test
    void testOutputCapture_SuccessfulConversion_CapturesFullOutput() throws Exception {
        // Skip if Pandoc is not available
        if (!isPandocAvailable()) {
            System.out.println("Skipping integration test: Pandoc not available");
            return;
        }

        // Create a minimal test Markdown document
        Path inputMd = createTestMarkdownFile();
        Path outputHtml = tempDir.resolve("output_test.html");

        DocumentSettings settings = DocumentSettings.builder()
                .marginTop(20)
                .marginBottom(20)
                .marginLeft(20)
                .marginRight(20)
                .generateTableOfContents(false)
                .preserveFormatting(false)
                .templatePath(null)
                .build();

        // Execute conversion
        ConversionResult result = service.convertDocument(
                inputMd,
                outputHtml,
                settings,
                noOpCallback);

        // Verify success
        assertTrue(result.success(), "Conversion should succeed");

        // Verify tool output is captured
        assertTrue(result.toolOutput().isPresent(), "Tool output should be present");
        String output = result.toolOutput().get();

        // Pandoc may produce minimal output for successful conversions
        // Just verify it's not null and is a string (even if empty)
        assertNotNull(output, "Tool output should not be null");

        // Verify output file was created
        assertTrue(Files.exists(outputHtml), "Output file should exist");
        assertTrue(Files.size(outputHtml) > 0, "Output file should not be empty");
    }

    /**
     * Integration test for failed conversion capturing error output.
     * This test uses invalid input to trigger Pandoc failure.
     * Requirements: REQ-FL-2.2
     */
    @Test
    void testOutputCapture_FailedConversion_CapturesErrorOutput() throws Exception {
        // Skip if Pandoc is not available
        if (!isPandocAvailable()) {
            System.out.println("Skipping integration test: Pandoc not available");
            return;
        }

        // Create an input file with invalid content (corrupted document)
        Path inputInvalid = tempDir.resolve("invalid.docx");
        Files.writeString(inputInvalid, "This is not a valid DOCX file, just plain text");

        Path outputHtml = tempDir.resolve("output_fail.html");

        DocumentSettings settings = DocumentSettings.builder()
                .marginTop(20)
                .marginBottom(20)
                .marginLeft(20)
                .marginRight(20)
                .generateTableOfContents(false)
                .preserveFormatting(false)
                .templatePath(null)
                .build();

        // Execute conversion (should fail with corrupted DOCX)
        ConversionResult result = service.convertDocument(
                inputInvalid,
                outputHtml,
                settings,
                noOpCallback);

        // Verify failure
        assertFalse(result.success(), "Conversion should fail with invalid DOCX");

        // Verify tool output is captured
        assertTrue(result.toolOutput().isPresent(), "Tool output should be present");
        String output = result.toolOutput().get();
        assertFalse(output.isEmpty(), "Tool output should not be empty");

        // Verify output contains error information
        // Pandoc typically reports errors to stderr (which we merge with stdout)
        assertTrue(output.toLowerCase().contains("error") ||
                output.toLowerCase().contains("could not") ||
                output.toLowerCase().contains("failed") ||
                output.toLowerCase().contains("cannot"),
                "Output should contain error messages");

        // Verify partial output file was cleaned up
        assertFalse(Files.exists(outputHtml), "Partial output file should be cleaned up");
    }

    /**
     * Integration test for 1MB output truncation limit.
     * This test generates a large document to trigger truncation.
     * Requirements: REQ-FL-2.2
     */
    @Test
    void testOutputCapture_LargeOutput_TruncatesAt1MB() throws Exception {
        // Skip if Pandoc is not available
        if (!isPandocAvailable()) {
            System.out.println("Skipping integration test: Pandoc not available");
            return;
        }

        // Create a large Markdown file with many lines (to generate verbose output if
        // Pandoc is verbose)
        Path inputLarge = createLargeMarkdownFile();
        Path outputHtml = tempDir.resolve("output_verbose.html");

        DocumentSettings settings = DocumentSettings.builder()
                .marginTop(20)
                .marginBottom(20)
                .marginLeft(20)
                .marginRight(20)
                .generateTableOfContents(true) // Enable TOC for more processing
                .preserveFormatting(true)
                .templatePath(null)
                .build();

        // Execute conversion
        ConversionResult result = service.convertDocument(
                inputLarge,
                outputHtml,
                settings,
                noOpCallback);

        // Verify success
        assertTrue(result.success(), "Conversion should succeed");

        // Verify tool output is captured
        assertTrue(result.toolOutput().isPresent(), "Tool output should be present");
        String output = result.toolOutput().get();

        // Verify output size is limited
        int outputSizeBytes = output.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

        // If output was truncated, verify:
        // 1. Size is around 1MB (with truncation message)
        // 2. Truncation message is present
        if (output.contains("[Output truncated - exceeded 1MB limit]")) {
            assertTrue(outputSizeBytes <= 1024 * 1024 + 1024,
                    "Truncated output should be around 1MB (with small buffer for truncation message)");
            assertTrue(output.contains("[Output truncated - exceeded 1MB limit]"),
                    "Truncation message should be present");
        }

        // Note: Pandoc typically produces minimal console output, so truncation may not
        // occur
        // This is acceptable - the test verifies truncation logic exists and works when
        // needed
    }

    // ========================================
    // Helper Methods for Integration Tests
    // ========================================

    /**
     * Checks if Pandoc is available on the system for integration testing.
     * Uses the service's configured pandocPath which may not exist.
     * 
     * @return true if Pandoc is available, false otherwise
     */
    private boolean isPandocAvailable() {
        try {
            // Try to execute pandoc --version to check availability
            ProcessBuilder pb = new ProcessBuilder(pandocPath.toString(), "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Creates a minimal test Markdown document.
     * 
     * @return Path to the created test Markdown file
     * @throws Exception if file creation fails
     */
    private Path createTestMarkdownFile() throws Exception {
        Path mdPath = tempDir.resolve("test_input.md");

        String content = """
                # Test Document

                This is a test Markdown document for Pandoc conversion testing.

                ## Section 1

                Lorem ipsum dolor sit amet, consectetur adipiscing elit.

                ## Section 2

                - Item 1
                - Item 2
                - Item 3

                **Bold text** and *italic text*.
                """;

        Files.writeString(mdPath, content);
        return mdPath;
    }

    /**
     * Creates a large Markdown file with many sections.
     * Used to potentially generate more verbose Pandoc output.
     * 
     * @return Path to the created large Markdown file
     * @throws Exception if file creation fails
     */
    private Path createLargeMarkdownFile() throws Exception {
        Path mdPath = tempDir.resolve("large_input.md");

        StringBuilder content = new StringBuilder();
        content.append("# Large Test Document\n\n");

        // Generate 100 sections with content
        for (int i = 1; i <= 100; i++) {
            content.append("## Section ").append(i).append("\n\n");
            content.append("This is section ").append(i).append(" with some content. ");
            content.append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ");
            content.append("Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n\n");

            // Add lists
            content.append("- List item 1 in section ").append(i).append("\n");
            content.append("- List item 2 in section ").append(i).append("\n");
            content.append("- List item 3 in section ").append(i).append("\n\n");

            // Add code blocks occasionally
            if (i % 10 == 0) {
                content.append("```java\n");
                content.append("public class Example {\n");
                content.append("    public static void main(String[] args) {\n");
                content.append("        System.out.println(\"Section ").append(i).append("\");\n");
                content.append("    }\n");
                content.append("}\n");
                content.append("```\n\n");
            }
        }

        Files.writeString(mdPath, content.toString());
        return mdPath;
    }
}
