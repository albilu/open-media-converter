package org.omc.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.omc.controller.ApplicationWorkflowController;
import org.omc.controller.SettingsManager;
import org.omc.exception.ToolExecutionException;
import org.omc.model.AspectRatio;
import org.omc.model.AudioSettings;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionSettings;
import org.omc.model.ConversionStatus;
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;
import org.omc.model.ImageSettings;
import org.omc.model.Resolution;
import org.omc.model.VideoSettings;
import org.omc.service.PandocService;

import static org.junit.jupiter.api.Assertions.*;

/** Real-tool regressions for the independently reproduced feature-audit failures. */
class FeatureAuditRegressionTest {
    @TempDir static Path root;
    private static DependencyFactory factory;
    private static ApplicationWorkflowController controller;
    private static Path image;
    private static Path notes;
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @BeforeAll
    static void prepare() throws Exception {
        factory = new DependencyFactory(root.resolve("config"));
        controller = factory.createApplicationController();
        controller.initialize();
        assertNotNull(factory.getToolManager().getImageMagickService(), "Run in the prepared Docker environment");
        byte[] pixels = new byte[120 * 80 * 3];
        new Random(739).nextBytes(pixels);
        Path ppm = root.resolve("pattern.ppm");
        try (var out = Files.newOutputStream(ppm)) {
            out.write("P6\n120 80\n255\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.write(pixels);
        }
        image = root.resolve("picture colors.png");
        run("convert", ppm.toString(), image.toString());
        notes = root.resolve("notes.md");
        Files.writeString(notes, "# Audit\n\nAuditSentinel739\n\n![Picture](picture%20colors.png)\n");
    }

    @AfterAll
    static void shutdown() {
        if (factory != null) factory.shutdown();
    }

    private static byte[] run(String... command) throws IOException, InterruptedException {
        Path capture = root.resolve("process-" + SEQUENCE.incrementAndGet() + ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(capture.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "External fixture/inspection command timed out");
            assertEquals(0, process.exitValue(), () -> "External command failed: " + List.of(command));
            assertTrue(Files.size(capture) <= 1_048_576);
            return Files.readAllBytes(capture);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static ConversionResult convert(Path input, ConversionSettings.Builder settings) throws Exception {
        Path output = Files.createDirectory(root.resolve("result-" + SEQUENCE.incrementAndGet()));
        ConversionFile file = ConversionFile.create(input, factory.getFileHandler().detectFormat(input), Files.size(input));
        return factory.getConversionEngine().convertBatch(List.of(file),
                settings.outputDirectory(output).build().withDefaults()).get(45, TimeUnit.SECONDS).results().getFirst();
    }

    private static ConversionSettings.Builder document(FileFormat output) {
        return ConversionSettings.builder().documentSettings(DocumentSettings.builder().outputFormat(output).build());
    }

    private static Path successful(ConversionResult result) {
        assertTrue(result.success(), () -> result.errorMessage().orElse("Conversion failed"));
        return result.outputPath().orElseThrow();
    }

    @Test
    void animatedInputNeverPublishesAnEmptySingleFrameOrDeletesTheOriginal() throws Exception {
        Path animation = root.resolve("animation.gif");
        run("convert", "-delay", "20", image.toString(), image.toString(), animation.toString());
        ConversionResult result = convert(animation, ConversionSettings.builder().deleteOriginalFile(true)
                .imageSettings(ImageSettings.builder().outputFormat(FileFormat.PNG).build()));
        assertFalse(result.success());
        assertTrue(Files.isRegularFile(animation));
        assertTrue(result.outputPath().isEmpty());
        try (var leftovers = Files.list(factory.getConfigurationManager().getTempDirectory())) {
            assertEquals(0, leftovers.count(), "Numbered frames and job directories must be removed");
        }
    }

    @Test
    void documentImagesSurviveExtractionPublicationAndOriginalDeletion() throws Exception {
        Path docx = successful(convert(notes, document(FileFormat.DOCX)));
        Path markdown = successful(convert(docx, document(FileFormat.MARKDOWN)));
        String text = Files.readString(markdown);
        var reference = java.util.regex.Pattern.compile("!\\[[^]]*\\]\\(([^)]+)\\)").matcher(text);
        assertTrue(reference.find(), text);
        Path extracted = markdown.getParent().resolve(reference.group(1));
        assertTrue(Files.size(extracted) > 0, "Extracted image must be published beside Markdown");
        assertArrayEquals(run("convert", image.toString(), "-depth", "8", "rgb:-"),
                run("convert", extracted.toString(), "-depth", "8", "rgb:-"));
        Path nativeDoc = successful(convert(docx, document(FileFormat.DOC)));
        Path html = successful(convert(nativeDoc, document(FileFormat.HTML).deleteOriginalFile(true)));
        assertFalse(Files.exists(nativeDoc));
        assertTrue(Files.readString(html).contains("data:image/png;base64,"));
        assertTrue(Files.readString(html).contains("AuditSentinel739"));
    }

    @Test
    void missingDocumentImageFailsAndPreservesSource() throws Exception {
        Path missing = root.resolve("missing.md");
        Files.writeString(missing, "![missing](does-not-exist.png)\n");
        ConversionResult result = convert(missing, document(FileFormat.MARKDOWN).deleteOriginalFile(true));
        assertFalse(result.success());
        assertTrue(Files.exists(missing));
    }

    @Test
    void preservedOfficePdfRetainsHeaderAndFooterContent() throws Exception {
        Path input = root.resolve("header-footer.docx");
        try (var fixture = FeatureAuditRegressionTest.class.getResourceAsStream("/documents/header-footer.docx")) {
            assertNotNull(fixture);
            Files.copy(fixture, input);
        }
        Path pdf = successful(convert(input, document(FileFormat.PDF)));
        String text = new String(run("pdftotext", pdf.toString(), "-"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("AUDIT_HEADER_MUST_SURVIVE"), text);
        assertTrue(text.contains("AUDIT_FOOTER_MUST_SURVIVE"), text);
        assertTrue(text.contains("AUDIT_BODY_ABC123"), text);
    }

    @Test
    void removedAndClearedRowsReleaseTheirStoredResults() throws Exception {
        Path output = Files.createDirectory(root.resolve("retention-" + SEQUENCE.incrementAndGet()));
        controller.handleSettingsSave(ConversionSettings.builder().imageSettings(ImageSettings.builder()
                .outputFormat(FileFormat.JPEG).quality(85).build())
                .outputDirectory(output).build().withDefaults());
        Path other = root.resolve("retention-other.png");
        run("convert", image.toString(), "-flop", other.toString());
        controller.addFiles(List.of(image, other));
        List<String> ids = controller.getFileList().stream().map(ConversionFile::id).toList();
        assertEquals(2, ids.size(), "Both distinct files must be admitted");
        controller.handleStartConversion();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (controller.isConversionInProgress() && System.nanoTime() < deadline) Thread.sleep(20);
        assertFalse(controller.isConversionInProgress(), "Conversion must finish before removal");
        for (String id : ids) successful(controller.getConversionResult(id));
        controller.removeFiles(List.of(ids.getFirst()));
        assertNull(controller.getConversionResult(ids.getFirst()));
        assertNotNull(controller.getConversionResult(ids.getLast()));
        controller.handleClearFiles();
        assertNull(controller.getConversionResult(ids.getLast()));
    }

    @Test
    void optimizedGifResizePreservesLogicalFrameGeometry() throws Exception {
        Path first = root.resolve("frame-one.png"), second = root.resolve("frame-two.png");
        run("convert", "-size", "120x80", "xc:red", "-fill", "white", "-draw", "rectangle 2,2 20,20", first.toString());
        run("convert", "-size", "120x80", "xc:red", "-fill", "white", "-draw", "rectangle 60,30 78,48", second.toString());
        Path input = root.resolve("optimized.gif"), reference = root.resolve("reference.gif");
        run("convert", "-delay", "30", first.toString(), second.toString(), "-loop", "0", "-layers", "Optimize", input.toString());
        run("convert", input.toString(), "-coalesce", "-resize", "60x40", reference.toString());
        Path actual = successful(convert(input, ConversionSettings.builder().imageSettings(ImageSettings.builder()
                .outputFormat(FileFormat.GIF).resolution(new Resolution(60, 40))
                .resizeMode(org.omc.model.ResizeMode.FIT).build())));
        assertArrayEquals(run("convert", reference.toString(), "-coalesce", "-depth", "8", "rgb:-"),
                run("convert", actual.toString(), "-coalesce", "-depth", "8", "rgb:-"));
        assertEquals(new String(run("identify", "-format", "%T,%D;", reference.toString())),
                new String(run("identify", "-format", "%T,%D;", actual.toString())));
    }

    @Test
    void pngCompressionZeroUsesTheUncompressedZlibMode() throws Exception {
        Path output = successful(convert(image, ConversionSettings.builder().imageSettings(ImageSettings.builder()
                .outputFormat(FileFormat.PNG).compressionLevel(0).build())));
        byte[] png = Files.readAllBytes(output);
        int offset = 8;
        boolean checked = false;
        while (offset + 12 <= png.length) {
            int length = java.nio.ByteBuffer.wrap(png, offset, 4).getInt();
            String type = new String(png, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if ("IDAT".equals(type)) {
                assertEquals(0, png[offset + 9] & 0xc0, "Zlib compression flags must represent the fastest mode");
                checked = true;
                break;
            }
            offset += length + 12;
        }
        assertTrue(checked, "PNG must contain compressed image data");
        assertArrayEquals(run("convert", image.toString(), "-depth", "8", "rgb:-"),
                run("convert", output.toString(), "-depth", "8", "rgb:-"));
    }

    @Test
    void detectedDocxReaderSurvivesAMisleadingTextExtension() throws Exception {
        Path docx = successful(convert(notes, document(FileFormat.DOCX)));
        Path renamed = Files.copy(docx, root.resolve("renamed-office.txt"));
        assertEquals(FileFormat.DOCX, factory.getFileHandler().detectFormat(renamed));
        Path html = successful(convert(renamed, document(FileFormat.HTML)));
        assertTrue(Files.readString(html).contains("AuditSentinel739"));
        assertTrue(Files.readString(html).contains("data:image/png;base64,"));
        Path pdf = successful(convert(renamed, document(FileFormat.PDF)));
        assertTrue(new String(run("pdftotext", pdf.toString(), "-")).contains("AuditSentinel739"));
    }

    @Test
    void vorbisHighQualityWorksForMonoAndExceedsSmallSizeBitrate() throws Exception {
        Path wav = root.resolve("vorbis-noise.wav");
        run("ffmpeg", "-v", "error", "-f", "lavfi", "-i", "anoisesrc=d=4:color=pink:seed=412", "-ar", "44100", wav.toString());
        Path high = successful(convert(wav, ConversionSettings.builder().audioSettings(AudioSettings.builder()
                .outputFormat(FileFormat.OGG).quality(0).bitrate(320).build())));
        Path small = successful(convert(wav, ConversionSettings.builder().audioSettings(AudioSettings.builder()
                .outputFormat(FileFormat.OGG).quality(7).bitrate(128).build())));
        run("ffmpeg", "-v", "error", "-i", high.toString(), "-f", "null", "-");
        run("ffmpeg", "-v", "error", "-i", small.toString(), "-f", "null", "-");
        assertTrue(Files.size(high) > Files.size(small), "High Quality must not select a lower Vorbis quality");
    }

    @Test
    void videoConversionPreservesBothAudioTracksAndSubtitleText() throws Exception {
        Path subtitles = Files.writeString(root.resolve("tracks.srt"), "1\n00:00:00,000 --> 00:00:00,800\nTrackSentinel\n");
        Path input = root.resolve("tracks.mkv");
        run("ffmpeg", "-v", "error", "-f", "lavfi", "-i", "color=red:size=80x40:duration=1",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=1", "-f", "lavfi", "-i", "sine=frequency=880:duration=1",
                "-i", subtitles.toString(), "-map", "0:v", "-map", "1:a", "-map", "2:a", "-map", "3:s",
                "-metadata:s:a:0", "language=eng", "-metadata:s:a:1", "language=fra",
                "-c:v", "libx264", "-c:a", "aac", "-c:s", "srt", input.toString());
        Path result = successful(convert(input, ConversionSettings.builder()
                .videoSettings(VideoSettings.builder().outputFormat(FileFormat.MKV).build())));
        String audio = new String(run("ffprobe", "-v", "error", "-select_streams", "a", "-show_entries",
                "stream=index:stream_tags=language", "-of", "json", result.toString()));
        var streams = new com.fasterxml.jackson.databind.ObjectMapper().readTree(audio).path("streams");
        assertEquals(2, streams.size());
        assertEquals("eng", streams.get(0).path("tags").path("language").asText());
        assertEquals("fra", streams.get(1).path("tags").path("language").asText());
        assertTrue(new String(run("ffmpeg", "-v", "error", "-i", result.toString(), "-map", "0:s:0", "-f", "srt", "-")).contains("TrackSentinel"));
    }

    @Test
    void plainTextPunctuationIsLiteralInHtml() throws Exception {
        Path text = root.resolve("literal.txt");
        String literal = "# Literal heading\n**literal asterisks** and [link](https://example.invalid)\n  <tag> & text\n\nlast line\n";
        Files.writeString(text, literal);
        String html = Files.readString(successful(convert(text, document(FileFormat.HTML))));
        assertTrue(html.contains("# Literal heading"));
        assertTrue(html.contains("**literal asterisks**"));
        assertTrue(html.contains("[link](https://example.invalid)"));
        assertTrue(html.contains("&lt;tag&gt; &amp; text"));
        assertFalse(html.contains("<strong>literal asterisks</strong>"));
        assertTrue(html.contains("<pre><code>"), "Literal whitespace needs a preformatted output block");
        assertTrue(html.contains("\n  &lt;tag&gt; &amp; text\n\nlast line"));
        Path roundTrip = successful(convert(text, document(FileFormat.TXT)));
        assertArrayEquals(Files.readAllBytes(text), Files.readAllBytes(roundTrip));
        Path pdf = successful(convert(text, document(FileFormat.PDF)));
        String rendered = new String(run("pdftotext", "-layout", pdf.toString(), "-"));
        assertTrue(rendered.lines().filter(line -> !line.isBlank()).count() >= 4, rendered);
        assertTrue(rendered.contains("\n  <tag> & text\n"), "PDF must retain the two-character indentation: " + rendered);
    }

    @Test
    void relativeTemplateStillWorksWhenResourcesUseAnIsolatedWorkingDirectory() throws Exception {
        Path template = root.resolve("custom-template.html");
        Files.writeString(template, "<!doctype html><html><body data-audit=\"relative-template\">$body$</body></html>");
        Path relative = Path.of("").toAbsolutePath().relativize(template);
        Path result = successful(convert(notes, ConversionSettings.builder().documentSettings(
                DocumentSettings.builder().outputFormat(FileFormat.HTML).templatePath(relative).build())));
        String html = Files.readString(result);
        assertTrue(html.contains("data-audit=\"relative-template\""));
        assertTrue(html.contains("AuditSentinel739"));
        assertTrue(html.contains("data:image/png;base64,"));
    }

    @Test
    void losslessWebpPreservesEveryPixelAndJpegRejectsLossless() throws Exception {
        Path webp = successful(convert(image, ConversionSettings.builder().imageSettings(
                ImageSettings.builder().outputFormat(FileFormat.WEBP).quality(ImageSettings.LOSSLESS_QUALITY).build())));
        assertArrayEquals(run("convert", image.toString(), "-depth", "8", "rgb:-"),
                run("convert", webp.toString(), "-depth", "8", "rgb:-"));
        assertFalse(convert(image, ConversionSettings.builder().imageSettings(
                ImageSettings.builder().outputFormat(FileFormat.JPEG).quality(ImageSettings.LOSSLESS_QUALITY).build())).success());
    }

    @Test
    void matchingCanvasAspectRatioRetainsDimensionsAndSquarePixels() throws Exception {
        Path video = root.resolve("video.mp4");
        run(factory.getToolManager().getFFmpegService().getFfmpegPath().toString(), "-nostdin", "-v", "error", "-y",
                "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=12", "-t", "1", "-c:v", "libx264", video.toString());
        Path output = successful(convert(video, ConversionSettings.builder().videoSettings(VideoSettings.builder()
                .resolution(new Resolution(320, 180)).aspectRatio(AspectRatio.RATIO_16_9).preset("ultrafast").build())));
        var metadata = org.omc.util.JsonUtils.getObjectMapper().readTree(run(
                factory.getToolManager().getFFmpegService().getFfprobePath().toString(), "-v", "error",
                "-show_streams", "-of", "json", output.toString())).get("streams").get(0);
        assertEquals(320, metadata.get("width").asInt());
        assertEquals(180, metadata.get("height").asInt());
        assertEquals("1:1", metadata.get("sample_aspect_ratio").asText());
        assertEquals("16:9", metadata.get("display_aspect_ratio").asText());
    }

    @Test
    void m4aProducedByEmbeddedEncoderUsesAudioSettings() throws Exception {
        Path audio = root.resolve("actual-audio.m4a");
        run(factory.getToolManager().getFFmpegService().getFfmpegPath().toString(), "-nostdin", "-v", "error", "-y",
                "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000", "-t", "1", audio.toString());
        assertEquals(FileFormat.M4A, factory.getFileHandler().detectFormat(audio));
        Path output = successful(convert(audio, ConversionSettings.builder()
                .audioSettings(AudioSettings.builder().outputFormat(FileFormat.WAV).build())
                .videoSettings(VideoSettings.builder().outputFormat(FileFormat.MP4).build())));
        assertTrue(output.toString().endsWith(".wav"));
    }

    @Test
    void legacyGlobalFormatMigratesWithoutOverridingExplicitSections() throws Exception {
        ConfigurationManager config = new ConfigurationManager(root.resolve("migration/config"),
                root.resolve("migration/data"), root.resolve("migration/cache"));
        SettingsManager manager = new SettingsManager(config, factory.getValidationEngine());
        String original = "{\"outputDirectory\":\"" + root + "\",\"parallelConversions\":3,\"outputFormat\":\"WEBM\"}";
        Files.writeString(config.getSettingsFilePath(), original);
        assertEquals(FileFormat.WEBM, manager.loadSettings().videoSettings().outputFormat());
        assertEquals(original, Files.readString(config.getSettingsFilePath()), "Migration on read must preserve the source file");
        var tree = org.omc.util.JsonUtils.getObjectMapper().readTree(original);
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree).set("videoSettings",
                org.omc.util.JsonUtils.getObjectMapper().valueToTree(VideoSettings.builder().outputFormat(FileFormat.MKV).build()));
        Files.writeString(config.getSettingsFilePath(), tree.toString());
        assertEquals(FileFormat.MKV, manager.loadSettings().videoSettings().outputFormat());
    }

    @Test
    void unavailablePdfRendererIsNotAdvertisedOrSelected() throws Exception {
        PandocService textOnly = new PandocService(factory.getToolManager().getPandocService().getPandocPath());
        ToolManager manager = new ToolManager(null, textOnly, null, null);
        assertTrue(manager.canConvertDocuments(FileFormat.MARKDOWN, FileFormat.HTML));
        assertFalse(manager.canConvertDocuments(FileFormat.MARKDOWN, FileFormat.PDF));
        ToolExecutionException error = assertThrows(ToolExecutionException.class,
                () -> manager.selectTool(FileFormat.MARKDOWN, FileFormat.PDF));
        assertTrue(error.getMessage().contains("LibreOffice"));
    }

    @Test
    void completedOutputsSurviveRestartAndAreExcludedFromTheNextBatch() throws Exception {
        Path config = root.resolve("persist/config");
        Path output = Files.createDirectories(root.resolve("persist/output"));
        Path original = root.resolve("persist-original.png");
        Files.copy(image, original);
        String identity;
        Path completedOutput;
        DependencyFactory session = new DependencyFactory(config);
        try {
            var controller = session.createApplicationController();
            controller.initialize();
            controller.addFiles(List.of(original));
            controller.updateSettings(ConversionSettings.builder().outputDirectory(output).deleteOriginalFile(true)
                    .imageSettings(ImageSettings.builder().outputFormat(FileFormat.JPEG).build()).build().withDefaults());
            List<String> admitted = controller.handleStartConversion();
            awaitBatch(controller);
            ConversionFile completed = controller.getFileList().getFirst();
            identity = completed.id();
            completedOutput = completed.outputPath().orElseThrow();
            assertEquals(List.of(identity), admitted);
            assertEquals(ConversionStatus.COMPLETED, completed.status());
            assertFalse(Files.exists(original));
            controller.shutdown(true);
        } finally {
            session.shutdown();
        }
        session = new DependencyFactory(config);
        try {
            var controller = session.createApplicationController();
            controller.initialize();
            assertEquals(1, controller.getFileList().size());
            assertEquals(identity, controller.getFileList().getFirst().id());
            assertEquals(completedOutput, controller.getFileList().getFirst().outputPath().orElseThrow());
            Path next = root.resolve("next-picture.png");
            run("convert", image.toString(), "-negate", next.toString());
            controller.addFiles(List.of(next));
            var pending = controller.getFileList().stream().filter(f -> f.status() != ConversionStatus.COMPLETED).toList();
            assertEquals(1, pending.size());
            List<String> admitted = controller.handleStartConversion();
            assertEquals(List.of(pending.getFirst().id()), admitted);
            awaitBatch(controller);
            assertTrue(controller.getFileList().stream().allMatch(f -> f.status() == ConversionStatus.COMPLETED));
        } finally {
            session.shutdown();
        }
    }

    private static void awaitBatch(org.omc.controller.ApplicationWorkflowController controller) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (controller.isConversionInProgress() && System.nanoTime() < deadline) Thread.sleep(20);
        assertFalse(controller.isConversionInProgress(), "Controller batch did not reach a terminal state");
    }
}
