package org.omc.core;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.omc.model.ConversionFile;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionSettings;
import org.omc.model.ConversionTool;
import org.omc.model.FileFormat;
import org.omc.service.FileHandler;
import org.omc.service.ImageMagickService;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class PauseProgressIntegrationTest {
    @TempDir Path root;

    @Test
    void pauseAtToolCompletionDefersPublicationAndOriginalDeletion() throws Exception {
        for (boolean cancel : new boolean[] { false, true }) {
            Path directory = Files.createDirectory(root.resolve(cancel ? "cancel" : "resume"));
            var configuration = new ConfigurationManager(directory.resolve("config"), directory.resolve("data"), directory.resolve("cache"));
            var files = new FileHandler(configuration);
            var progress = new ProgressEngine();
            var tools = new ToolManager(null, null, null, new ImageMagickService(Path.of("/usr/bin/convert")));
            var engine = new ConversionEngine(tools, new ValidationEngine(files), progress, files, 1);
            Path input = directory.resolve("input.png"), output = Files.createDirectory(directory.resolve("output"));
            assertTrue(ImageIO.write(new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB), "png", input.toFile()));
            var file = ConversionFile.create(input, FileFormat.PNG, Files.size(input));
            AtomicBoolean once = new AtomicBoolean();
            CountDownLatch paused = new CountDownLatch(1);
            engine.onProgressUpdate((id, value) -> {
                if (value.percentage() == 99 && once.compareAndSet(false, true)) {
                    engine.pauseConversion();
                    paused.countDown();
                }
            });
            try {
                var future = engine.convertBatch(List.of(file), ConversionSettings.builder()
                        .outputDirectory(output).outputFormat(FileFormat.JPEG).deleteOriginalFile(true).build().withDefaults());
                assertTrue(paused.await(10, TimeUnit.SECONDS));
                Thread.sleep(200);
                assertFalse(future.isDone());
                assertTrue(Files.exists(input));
                assertFalse(Files.exists(output.resolve("input.jpg")));
                assertTrue(progress.getProgress(file.id()).orElseThrow().paused());
                if (cancel) engine.cancelConversion(); else engine.resumeConversion();
                var result = future.get(5, TimeUnit.SECONDS).results().getFirst();
                if (cancel) {
                    assertTrue(result.isCancelled());
                    assertTrue(Files.exists(input));
                    assertFalse(Files.exists(output.resolve("input.jpg")));
                    assertEquals(99, progress.getProgress(file.id()).orElseThrow().percentage());
                } else {
                    assertTrue(result.success());
                    assertFalse(Files.exists(input));
                    assertNotNull(ImageIO.read(output.resolve("input.jpg").toFile()));
                    assertEquals(100, progress.getProgress(file.id()).orElseThrow().percentage());
                }
            } finally { engine.shutdown(); }
        }
    }

    @Test
    void pauseFreezesProgressAndExcludesIdleTimeFromSpeedAndEta() throws Exception {
        var progress = new ProgressEngine();
        progress.startBatch(List.of("video"), Map.of("video", 10000L));
        progress.startTracking("video", 10000);
        Thread.sleep(60);
        progress.updateProgressWithPercentage("video", 25);
        progress.setPaused(true);
        var before = progress.getProgress("video").orElseThrow();
        var batch = progress.getBatchProgress();
        Thread.sleep(250);
        progress.updateProgressWithPercentage("video", 75);
        assertEquals(before, progress.getProgress("video").orElseThrow());
        assertEquals(batch.elapsedTime(), progress.getBatchProgress().elapsedTime());
        assertEquals("Paused", before.formatEta());
        progress.setPaused(false);
        progress.updateProgressWithPercentage("video", 30);
        var after = progress.getProgress("video").orElseThrow();
        assertTrue(after.elapsedTime().minus(before.elapsedTime()).toMillis() < 200);
        assertFalse(after.paused());
        progress.completeTracking("video", ConversionResult.failure("video", "encoder failed", "",
                Duration.ofSeconds(1), 10000, ConversionTool.FFMPEG));
        assertEquals(30, progress.getProgress("video").orElseThrow().percentage());
        progress.updateProgressWithPercentage("video", 100);
        assertEquals(30, progress.getProgress("video").orElseThrow().percentage());
        assertTrue(progress.getBatchProgress().isComplete());
    }
}
