package org.omc.service;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.omc.core.ToolManager;
import org.omc.model.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real executables and output inspection; enable with -Domc.realTools=true. */
@EnabledIfSystemProperty(named = "omc.realTools", matches = "true")
class RealConversionRegressionTest {
    @TempDir Path root;
    private FFmpegService ffmpeg;
    private ImageMagickService images;
    private PandocService pandoc;
    private LibreOfficeService office;
    private Path markdown;
    private Path png;

    @BeforeEach
    void prepare() throws Exception {
        ffmpeg = new FFmpegService(Path.of(System.getProperty("omc.ffmpeg", "/usr/bin/ffmpeg")),
                Path.of(System.getProperty("omc.ffprobe", "/usr/bin/ffprobe")));
        images = new ImageMagickService(Path.of("/usr/bin/convert"));
        office = new LibreOfficeService(Path.of("/usr/bin/soffice"));
        pandoc = new PandocService(Path.of(System.getProperty("omc.pandoc", "/usr/bin/pandoc")), office);
        markdown = Files.writeString(root.resolve("input.md"), "# Conversion heading\n\nA **bold** paragraph.\n\n## Details\n\nContent.\n");
        png = root.resolve("image.png");
        BufferedImage image = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 40; y++) for (int x = 0; x < 80; x++) image.setRGB(x, y, (x * 3 << 16) | (y * 6 << 8));
        assertTrue(ImageIO.write(image, "png", png.toFile()));
    }

    @Test
    void imageModesAndCompressionChangeActualOutput() throws Exception {
        for (ResizeMode mode : ResizeMode.values()) {
            Path output = root.resolve(mode.name() + ".png");
            var settings = ImageSettings.builder().resolution(new Resolution(30, 30)).resizeMode(mode).build();
            assertSuccess(images.convertImage(png, output, settings, (p, b, s) -> { }, null, org.omc.core.ProcessRegistry.noOp()));
            BufferedImage result = ImageIO.read(output.toFile());
            assertEquals(mode == ResizeMode.NONE ? 80 : 30, result.getWidth(), mode.name());
            assertEquals(mode == ResizeMode.NONE ? 40 : mode == ResizeMode.FILL || mode == ResizeMode.STRETCH ? 30 : 15,
                    result.getHeight(), mode.name());
        }
        Path low = root.resolve("low.png"), high = root.resolve("high.png");
        assertSuccess(images.convertImage(png, low, ImageSettings.builder().compressionLevel(1).build(), (p,b,s) -> { }, null, org.omc.core.ProcessRegistry.noOp()));
        assertSuccess(images.convertImage(png, high, ImageSettings.builder().compressionLevel(9).build(), (p,b,s) -> { }, null, org.omc.core.ProcessRegistry.noOp()));
        assertFalse(java.util.Arrays.equals(Files.readAllBytes(low), Files.readAllBytes(high)));
    }

    @Test
    void audioDefaultsWorkForEveryAudioContainer() throws Exception {
        Path input = root.resolve("tone.wav");
        command(ffmpeg.getFfmpegPath().toString(), "-v", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=0.3", input.toString());
        for (FileFormat format : FileFormat.getFormatsByCategory(FormatCategory.AUDIO)) {
            Path output = root.resolve("audio." + format.getPrimaryExtension());
            assertSuccess(ffmpeg.convertAudio(input, output, AudioSettings.builder().outputFormat(format).build(), (p,b,s) -> { }));
            assertTrue(Files.size(output) > 40, format.name());
            command(ffmpeg.getFfprobePath().toString(), "-v", "error", output.toString());
        }
    }

    @Test
    void videoDefaultsProduceReadableVideoAndAudioInEveryContainer() throws Exception {
        Path input = root.resolve("clip.mp4");
        command(ffmpeg.getFfmpegPath().toString(), "-v", "error", "-f", "lavfi", "-i", "color=red:size=80x40:duration=0.3",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=0.3", "-c:a", "aac",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", input.toString());
        for (FileFormat format : FileFormat.getFormatsByCategory(FormatCategory.VIDEO)) {
            Path output = root.resolve("output." + format.getPrimaryExtension());
            if (format.getCategory() == FormatCategory.AUDIO) {
                assertSuccess(ffmpeg.convertAudio(input, output, AudioSettings.builder().outputFormat(format).build(), (p,b,s) -> { }));
            } else {
                assertSuccess(ffmpeg.convertVideo(input, output, VideoSettings.builder().outputFormat(format).build(), (p,b,s) -> { }));
                assertTrue(command(ffmpeg.getFfprobePath().toString(), "-v", "error", "-show_streams", output.toString()).contains("codec_type=video"));
            }
            assertTrue(command(ffmpeg.getFfprobePath().toString(), "-v", "error", "-show_streams", output.toString()).contains("codec_type=audio"));
        }
    }

    @Test
    void everyImageOutputIncludingPdfAndSvgContainsTheTransformedImage() throws Exception {
        for (FileFormat format : FileFormat.getFormatsByCategory(FormatCategory.IMAGE)) {
            Path output = root.resolve("output." + format.getPrimaryExtension());
            assertSuccess(images.convertImage(png, output, ImageSettings.builder().outputFormat(format)
                    .resolution(new Resolution(30, 30)).resizeMode(ResizeMode.FILL).build(),
                    (p,b,s) -> { }, null, org.omc.core.ProcessRegistry.noOp()));
            if (format == FileFormat.PDF) {
                assertEquals("%PDF-", new String(Files.readAllBytes(output), 0, 5, java.nio.charset.StandardCharsets.US_ASCII));
            } else {
                assertEquals("30x30", command("/usr/bin/identify", "-format", "%wx%h", output.toString()).trim());
                if (format == FileFormat.SVG) assertTrue(Files.readString(output).contains("data:image/png;base64,"));
            }
        }
        Path svg = Files.writeString(root.resolve("vector.svg"),
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"80\" height=\"40\"><rect width=\"80\" height=\"40\" fill=\"red\"/></svg>");
        Path result = root.resolve("vector.png");
        assertSuccess(images.convertImage(svg, result, ImageSettings.builder().build(), (p,b,s) -> { }, null, org.omc.core.ProcessRegistry.noOp()));
        assertEquals(0xFF0000, ImageIO.read(result.toFile()).getRGB(5, 5) & 0xFFFFFF);
    }

    @Test
    void relativeDocumentImagesAreEmbeddedInHtmlAndOfficeOutputs() throws Exception {
        Path source = Files.writeString(root.resolve("illustrated.md"), "# Illustration\n\n![Sample](image.png)\n");
        for (boolean preserve : List.of(true, false)) {
            Path html = root.resolve("illustrated-" + preserve + ".html");
            assertSuccess(pandoc.convertDocument(source, html, DocumentSettings.builder().outputFormat(FileFormat.HTML)
                    .preserveFormatting(preserve).build(), (p,b,s) -> { }));
            assertTrue(Files.readString(html).contains("data:image/png;base64,"));
        }
        Path officeOutput = root.resolve("illustrated.docx");
        assertSuccess(pandoc.convertDocument(source, officeOutput, DocumentSettings.builder().outputFormat(FileFormat.DOCX).build(), (p,b,s) -> { }));
        try (ZipFile zip = new ZipFile(officeOutput.toFile())) {
            assertTrue(zip.stream().anyMatch(entry -> entry.getName().startsWith("word/media/")));
        }
    }

    @Test
    void textReadersWritersAndOfficeRoutingProduceRequestedSyntax() throws Exception {
        for (FileFormat format : List.of(FileFormat.TXT, FileFormat.TEX, FileFormat.RST, FileFormat.ORG, FileFormat.DOCX)) {
            Path intermediate = root.resolve("document." + format.getPrimaryExtension());
            assertSuccess(pandoc.convertDocument(markdown, intermediate, DocumentSettings.builder().outputFormat(format).build(), (p,b,s) -> { }));
            Path html = root.resolve(format.name() + ".html");
            assertSuccess(pandoc.convertDocument(intermediate, html, DocumentSettings.builder().outputFormat(FileFormat.HTML).build(), (p,b,s) -> { }));
            assertTrue(Files.readString(html).contains("Conversion heading"));
        }
        var manager = new ToolManager(ffmpeg, pandoc, office, images);
        assertEquals(ConversionTool.PANDOC, manager.selectTool(FileFormat.DOCX, FileFormat.MARKDOWN));
        Path output = root.resolve("from-office.md");
        assertSuccess(pandoc.convertDocument(root.resolve("document.docx"), output,
                DocumentSettings.builder().outputFormat(FileFormat.MARKDOWN).build(), (p,b,s) -> { }));
        assertTrue(Files.readString(output).contains("Conversion heading"));
        assertFalse(Files.readString(output).startsWith("%PDF-"));
    }

    @Test
    void pdfPipelineProducesPdfWithContentsAndNoLatexDependency() throws Exception {
        Path pdf = root.resolve("document.pdf");
        assertSuccess(pandoc.convertDocument(markdown, pdf, DocumentSettings.builder()
                .marginTop(12).marginLeft(16).embedFonts(true).generateTableOfContents(true).build(), (p,b,s) -> { }));
        assertEquals("%PDF-", new String(Files.readAllBytes(pdf), 0, 5, java.nio.charset.StandardCharsets.US_ASCII));
        String text = command("/usr/bin/pdftotext", pdf.toString(), "-");
        assertTrue(text.contains("Conversion heading"));
        assertTrue(text.indexOf("Details") != text.lastIndexOf("Details"), "Contents table must precede the body");
    }

    @Test
    void documentMarginsAndFormattingAffectExport() throws Exception {
        for (FileFormat format : List.of(FileFormat.DOCX, FileFormat.ODT)) {
            Path output = root.resolve("margins." + format.getPrimaryExtension());
            assertSuccess(pandoc.convertDocument(markdown, output, DocumentSettings.builder().outputFormat(format)
                    .marginTop(12).marginBottom(13).marginLeft(14).marginRight(15).build(), (p,b,s) -> { }));
            try (ZipFile zip = new ZipFile(output.toFile())) {
                if (format == FileFormat.ODT) {
                    assertEquals(java.util.zip.ZipEntry.STORED, zip.getEntry("mimetype").getMethod());
                }
                String entry = format == FileFormat.DOCX ? "word/document.xml" : "styles.xml";
                try (var stream = zip.getInputStream(zip.getEntry(entry))) {
                    String xml = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    assertTrue(xml.contains(format == FileFormat.DOCX ? "w:top=\"680\"" : "fo:margin-top=\"12mm\""), xml);
                }
            }
        }
        Path html = root.resolve("plain.html");
        assertSuccess(pandoc.convertDocument(markdown, html, DocumentSettings.builder().outputFormat(FileFormat.HTML)
                .preserveFormatting(false).build(), (p,b,s) -> { }));
        assertFalse(Files.readString(html).contains("<strong>"));
    }

    @Test
    void fourNativeOfficeJobsUseIndependentProfiles() throws Exception {
        Path docx = root.resolve("office.docx");
        assertSuccess(pandoc.convertDocument(markdown, docx, DocumentSettings.builder().outputFormat(FileFormat.DOCX).build(), (p,b,s) -> { }));
        try (var pool = Executors.newFixedThreadPool(4)) {
            var jobs = new ArrayList<java.util.concurrent.Future<ConversionResult>>();
            for (int i = 0; i < 4; i++) {
                Path output = root.resolve("parallel-" + i + ".pdf");
                jobs.add(pool.submit(() -> office.convertDocument(docx, output, DocumentSettings.builder().build(), (p,b,s) -> { })));
            }
            for (var job : jobs) assertSuccess(job.get(40, TimeUnit.SECONDS));
        }
    }

    private static void assertSuccess(ConversionResult result) {
        assertTrue(result.success(), () -> result.errorMessage() + " " + result.toolOutput());
    }

    private static String command(String... arguments) throws Exception {
        Path log = Files.createTempFile("omc-real-tool-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(arguments).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Tool timed out");
            String output = Files.readString(log);
            assertEquals(0, process.exitValue(), output);
            return output;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(log);
        }
    }
}
