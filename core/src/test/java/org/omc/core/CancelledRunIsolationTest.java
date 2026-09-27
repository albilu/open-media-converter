package org.omc.core;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.omc.exception.FileOperationException;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionSettings;
import org.omc.model.FileFormat;
import org.omc.service.FileHandler;
import org.omc.service.ImageMagickService;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real conversion while a previous run is stalled before tool startup. */
class CancelledRunIsolationTest {
    @TempDir Path root;

    @Test
    @Timeout(20)
    void cancellationDuringDrainIsNotErasedByReplacementAdmission() throws Exception {
        var configuration = new ConfigurationManager(root.resolve("drain-config"), root.resolve("drain-data"), root.resolve("drain-cache"));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        FileHandler files = new FileHandler(configuration) {
            @Override
            public Path createTemporaryFile(String prefix, String suffix) throws FileOperationException {
                if (first.compareAndSet(true, false)) {
                    entered.countDown();
                    try { release.await(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                return super.createTemporaryFile(prefix, suffix);
            }
        };
        var tools = new ToolManager(null, null, null, new ImageMagickService(Path.of("/usr/bin/convert")));
        var engine = new ConversionEngine(tools, new ValidationEngine(files), new ProgressEngine(), files, 2);
        Path input = root.resolve("drain.png"), output = Files.createDirectory(root.resolve("drain-output"));
        assertTrue(ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", input.toFile()));
        var file = ConversionFile.create(input, FileFormat.PNG, Files.size(input));
        var settings = ConversionSettings.builder().outputDirectory(output).outputFormat(FileFormat.JPEG).build().withDefaults();
        var replacement = new java.util.concurrent.CompletableFuture<org.omc.model.BatchConversionResult>();
        Thread submitter = Thread.ofPlatform().unstarted(() -> {
            try { engine.convertBatch(List.of(file), settings).whenComplete((value, error) -> {
                if (error == null) replacement.complete(value); else replacement.completeExceptionally(error);
            }); }
            catch (RuntimeException e) { replacement.completeExceptionally(e); }
        });
        try {
            var old = engine.convertBatch(List.of(file), settings);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            engine.cancelConversion();
            assertTrue(old.get(2, TimeUnit.SECONDS).results().getFirst().isCancelled());
            submitter.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (java.util.Arrays.stream(submitter.getStackTrace())
                    .noneMatch(frame -> frame.getMethodName().equals("waitForCancelDrain"))
                    && System.nanoTime() < deadline) Thread.sleep(5);
            assertTrue(java.util.Arrays.stream(submitter.getStackTrace())
                    .anyMatch(frame -> frame.getMethodName().equals("waitForCancelDrain")));
            engine.cancelConversion(); // A new request while replacement admission is waiting.
            release.countDown();
            assertTrue(replacement.get(5, TimeUnit.SECONDS).results().getFirst().isCancelled());
            assertTrue(Files.exists(input));
            assertFalse(Files.exists(output.resolve("drain.jpg")));
        } finally {
            release.countDown();
            submitter.join(5000);
            engine.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void cancelledWorkerCannotPublishOrDeleteAfterAReplacementRun() throws Exception {
        var configuration = new ConfigurationManager(root.resolve("config"), root.resolve("data"), root.resolve("cache"));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        FileHandler files = new FileHandler(configuration) {
            @Override
            public Path createTemporaryFile(String prefix, String suffix) throws FileOperationException {
                if (first.compareAndSet(true, false)) {
                    entered.countDown();
                    boolean interrupted = false;
                    while (release.getCount() > 0) {
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    }
                    if (interrupted) Thread.currentThread().interrupt();
                }
                return super.createTemporaryFile(prefix, suffix);
            }
        };
        var tools = new ToolManager(null, null, null, new ImageMagickService(Path.of("/usr/bin/convert")));
        var engine = new ConversionEngine(tools, new ValidationEngine(files), new ProgressEngine(), files, 2);
        Path oldInput = root.resolve("cancelled.png"), newInput = root.resolve("replacement.png");
        assertTrue(ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", oldInput.toFile()));
        Files.copy(oldInput, newInput);
        Path oldOutput = Files.createDirectory(root.resolve("old-output"));
        Path newOutput = Files.createDirectory(root.resolve("new-output"));
        var oldFile = ConversionFile.create(oldInput, FileFormat.PNG, Files.size(oldInput));
        try {
            var oldBatch = engine.convertBatch(List.of(oldFile), ConversionSettings.builder()
                    .outputDirectory(oldOutput).outputFormat(FileFormat.JPEG).deleteOriginalFile(true).build().withDefaults());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            engine.cancelConversion();
            assertTrue(oldBatch.get(2, TimeUnit.SECONDS).results().getFirst().isCancelled());
            var replacement = engine.convertBatch(List.of(ConversionFile.create(newInput, FileFormat.PNG, Files.size(newInput))),
                    ConversionSettings.builder().outputDirectory(newOutput).outputFormat(FileFormat.JPEG).build().withDefaults());
            assertEquals(1, replacement.get(10, TimeUnit.SECONDS).successCount());
        } finally {
            release.countDown();
            engine.shutdown();
        }
        assertTrue(Files.exists(oldInput), "The cancelled source must survive the old worker's eventual return");
        assertFalse(Files.exists(oldOutput.resolve("cancelled.jpg")), "Cancelled work must not publish after restart");
        assertTrue(Files.size(newOutput.resolve("replacement.jpg")) > 0);
    }

    @Test
    @Timeout(20)
    void forgettingAPendingResultPreventsLateLogRetention() throws Exception {
        var factory = new DependencyFactory(root.resolve("result-config"));
        factory.createApplicationController().initialize();
        var engine = factory.getConversionEngine();
        Path input = root.resolve("pending.png");
        assertTrue(ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", input.toFile()));
        Path output = Files.createDirectory(root.resolve("pending-output"));
        var file = ConversionFile.create(input, FileFormat.PNG, Files.size(input));
        try {
            engine.pauseConversion();
            var pending = engine.convertBatch(List.of(file), ConversionSettings.builder()
                    .outputDirectory(output).outputFormat(FileFormat.JPEG).build().withDefaults());
            engine.forgetResults(List.of(file.id()));
            engine.resumeConversion();
            assertEquals(1, pending.get(10, TimeUnit.SECONDS).successCount());
            assertNull(engine.getConversionResult(file.id()), "A removed row must not regain its completed log");
        } finally {
            factory.shutdown();
        }
    }
}
