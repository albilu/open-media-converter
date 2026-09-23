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
    private static Path image;
    private static Path notes;
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @BeforeAll
    static void prepare() throws Exception {
        factory = new DependencyFactory(root.resolve("config"));
        factory.createApplicationController().initialize();
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
    void plainTextPunctuationIsLiteralInHtml() throws Exception {
        Path text = root.resolve("literal.txt");
        Files.writeString(text, "# Literal heading\n**literal asterisks** and [link](https://example.invalid)\n<tag> & text\n");
        String html = Files.readString(successful(convert(text, document(FileFormat.HTML))));
        assertTrue(html.contains("# Literal heading"));
        assertTrue(html.contains("**literal asterisks**"));
        assertTrue(html.contains("[link](https://example.invalid)"));
        assertTrue(html.contains("&lt;tag&gt; &amp; text"));
        assertFalse(html.contains("<strong>literal asterisks</strong>"));
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
