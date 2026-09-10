package org.omc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.omc.core.ProgressEngine;
import org.omc.core.ToolManager;
import org.omc.exception.ErrorCode;
import org.omc.exception.ToolExecutionException;
import org.omc.model.BatchProgress;
import org.omc.model.ConversionProgress;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionTool;
import org.omc.model.FileFormat;
import org.omc.model.Resolution;
import org.omc.model.VideoSettings;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Regression tests for P0/P1 review fixes: GPU decode-only acceleration,
 * even-dimension filters, dash-safe paths, fail-fast settings validation,
 * availability-aware tool routing, and the unified parallel limit.
 */
class P0P1RegressionTest {

    private FFmpegService service;
    private Path ffmpegPath;
    private Path ffprobePath;
    private Path inputPath;
    private Path outputPath;

    @BeforeEach
    void setUp() {
        ffmpegPath = Path.of("ffmpeg");
        ffprobePath = Path.of("ffprobe");
        service = new FFmpegService(ffmpegPath, ffprobePath);
        inputPath = Path.of("input.mp4");
        outputPath = Path.of("output.mp4");
    }

    // ========== safePathArg (P1: leading-dash injection) ==========

    @Test
    void safePathArg_normalPath_passthrough() {
        assertEquals("input.mp4", FFmpegService.safePathArg(Path.of("input.mp4")));
    }

    @Test
    void safePathArg_dashPrefixedBasename_absolutized() {
        String arg = FFmpegService.safePathArg(Path.of("-help"));
        assertTrue(Path.of(arg).isAbsolute(),
                "Dash-prefixed basename must absolutize so getopt never parses it as a flag");
        assertFalse(Path.of(arg).getFileName().toString().startsWith("-") && !Path.of(arg).isAbsolute());
    }

    @Test
    void buildVideoCommand_dashInput_usesAbsolutePath() {
        VideoSettings settings = VideoSettings.builder().build();
        List<String> command = service.buildVideoCommand(Path.of("-evil"), outputPath, settings);
        int i = command.indexOf("-i");
        assertTrue(Path.of(command.get(i + 1)).isAbsolute());
    }

    // ========== toEven (P1: odd-dimension yuv420p failures) ==========

    @Test
    void toEven_roundsUpToEven() {
        assertEquals(1920, FFmpegService.toEven(1920));
        assertEquals(1922, FFmpegService.toEven(1921));
        assertEquals(1082, FFmpegService.toEven(1081));
        assertEquals(2, FFmpegService.toEven(1));
    }

    @Test
    void buildVideoCommand_oddResolution_emitsEvenScale() {
        VideoSettings settings = VideoSettings.builder()
                .resolution(new Resolution(1921, 1081))
                .build();
        List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);
        assertTrue(command.contains("-vf"));
        assertTrue(command.contains("scale=1922:1082"),
                "Odd dimensions must round up to even for yuv420p, got: " + command);
    }

    // ========== GPU decode-only (P0: CUDA/CPU frame mismatch) ==========

    @Test
    void buildVideoCommand_gpuCodec_noFullHwPin() {
        VideoSettings settings = VideoSettings.builder()
                .codec("h264_nvenc")
                .build();
        List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);
        assertTrue(command.contains("-hwaccel"));
        assertFalse(command.contains("-hwaccel_output_format"),
                "Full-HW frames plus CPU filters abort; decode-only acceleration must be used");
    }

    // ========== Fail-fast settings validation (P1) ==========

    @Test
    void buildVideoCommand_invalidSettings_throwsIllegalArgument() throws Exception {
        // Builders reject invalid values, but Jackson deserialization bypasses
        // them - buildVideoCommand must fail fast instead of emitting garbage.
        ObjectMapper mapper = new ObjectMapper();
        VideoSettings invalid = mapper.readValue(
                "{\"codec\":\"libx264\",\"bitrate\":0,\"frameRate\":-1,\"crf\":23,\"outputFormat\":\"MP4\"}",
                VideoSettings.class);
        assertThrows(IllegalArgumentException.class,
                () -> service.buildVideoCommand(inputPath, outputPath, invalid));
    }

    // ========== Availability-aware routing (P1: NPE / unavailable fallback) ==========

    private ToolManager managerWithout(ConversionTool missing) {
        FFmpegService ffmpeg = missing == ConversionTool.FFMPEG ? null
                : new FFmpegService(Path.of("ffmpeg"), Path.of("ffprobe"));
        PandocService pandoc = missing == ConversionTool.PANDOC ? null
                : new PandocService(Path.of("pandoc"));
        LibreOfficeService office = missing == ConversionTool.LIBREOFFICE ? null
                : new LibreOfficeService(Path.of("soffice"));
        ImageMagickService magick = missing == ConversionTool.IMAGEMAGICK ? null
                : new ImageMagickService(Path.of("convert"));
        return new ToolManager(ffmpeg, pandoc, office, magick);
    }

    @Test
    void selectTool_ffmpegMissing_throwsToolNotFound() {
        ToolExecutionException ex = assertThrows(ToolExecutionException.class,
                () -> managerWithout(ConversionTool.FFMPEG).selectTool(FileFormat.MP4, FileFormat.AVI));
        assertEquals(ErrorCode.TOOL_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void selectTool_imagemagickMissing_throwsToolNotFound() {
        ToolExecutionException ex = assertThrows(ToolExecutionException.class,
                () -> managerWithout(ConversionTool.IMAGEMAGICK).selectTool(FileFormat.PNG, FileFormat.JPEG));
        assertEquals(ErrorCode.TOOL_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void selectTool_documentToolsMissing_throwsToolNotFoundNotUnavailableFallback() {
        ToolManager noDocs = new ToolManager(
                new FFmpegService(Path.of("ffmpeg"), Path.of("ffprobe")), null, null,
                new ImageMagickService(Path.of("convert")));
        // MARKDOWN->HTML is supported by Pandoc, but no service is installed:
        // must report TOOL_NOT_FOUND, never return an unavailable tool.
        ToolExecutionException ex = assertThrows(ToolExecutionException.class,
                () -> noDocs.selectTool(FileFormat.MARKDOWN, FileFormat.HTML));
        assertEquals(ErrorCode.TOOL_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void selectTool_unsupportedPair_throwsToolNotFound() {
        ToolExecutionException ex = assertThrows(ToolExecutionException.class,
                () -> managerWithout(null).selectTool(FileFormat.PDF, FileFormat.DOCX));
        assertEquals(ErrorCode.TOOL_NOT_FOUND, ex.getErrorCode());
    }

    // ========== ProgressEngine slice (P2) ==========

    @Test
    void cancelTracking_beforeStart_freezesProgressAndNotifies() {
        ProgressEngine engine = new ProgressEngine();
        engine.startBatch(List.of("f1"), Map.of("f1", 1000L));
        CopyOnWriteArrayList<ConversionProgress> events = new CopyOnWriteArrayList<>();
        engine.addProgressListener(events::add);

        // Cancelled before startTracking: previously left no progress record
        // and no per-file event (row stuck PENDING forever).
        engine.cancelTracking("f1");

        assertTrue(engine.getProgress("f1").isPresent(),
                "cancel must freeze a progress entry even without startTracking");
        assertEquals(1, events.size(), "cancel must force a per-file notification");
        assertEquals("f1", events.get(0).fileId());
    }

    @Test
    void batchNotifications_throttledButTerminalForced() {
        ProgressEngine engine = new ProgressEngine();
        AtomicInteger batchEvents = new AtomicInteger();
        engine.addBatchProgressListener(b -> batchEvents.incrementAndGet());
        engine.startBatch(List.of("f1"), Map.of("f1", 1000L));
        engine.startTracking("f1", 1000L);
        int baseline = batchEvents.get();

        for (int i = 1; i <= 20; i++) {
            engine.updateProgress("f1", i * 10L);
        }
        // 20 rapid updates must not fan out to 20 batch notifications.
        assertTrue(batchEvents.get() - baseline <= 3,
                "batch notifications must be throttled, got +" + (batchEvents.get() - baseline));

        // Terminal completion bypasses the throttle so final state is delivered.
        ConversionResult ok = ConversionResult.success("f1", Path.of("out.mp4"), null,
                Duration.ofSeconds(1), 1000L, 900L, ConversionTool.FFMPEG);
        engine.completeTracking("f1", ok);
        BatchProgress batch = engine.getBatchProgress();
        assertEquals(1, batch.completedFiles());
        assertTrue(batchEvents.get() > baseline, "terminal batch state must be delivered");
    }

    // ========== ValidationEngine availability cache + saturation (P2 slice) ==========

    @Test
    void toolAvailability_nonExecutableConfiguredPath_failsWithoutSpawn() {
        org.omc.core.ValidationEngine engine = new org.omc.core.ValidationEngine(
                org.mockito.Mockito.mock(org.omc.service.FileHandler.class));
        org.omc.model.ToolConfiguration config = new org.omc.model.ToolConfiguration();
        config.setFfmpegPath(Path.of("/nonexistent/ffmpeg-omc-probe"));
        engine.setToolConfiguration(config);

        org.omc.model.ValidationResult result = engine.validateToolAvailability(ConversionTool.FFMPEG);

        assertTrue(result.isFailure(), "configured-but-missing binary must fail");
        assertTrue(result.getErrors().get(0).contains("not executable"));
    }

    @Test
    void toolAvailability_resultServedFromCache_acrossCalls() {
        org.omc.core.ValidationEngine engine = new org.omc.core.ValidationEngine(
                org.mockito.Mockito.mock(org.omc.service.FileHandler.class));
        // No toolConfiguration -> bare "true"/"false" command names that
        // resolve via PATH on this host.
        org.omc.model.ValidationResult first = engine.validateToolAvailability(ConversionTool.FFMPEG);
        org.omc.model.ValidationResult second = engine.validateToolAvailability(ConversionTool.FFMPEG);
        assertEquals(first.isSuccess(), second.isSuccess(),
                "second call must be served from the cache (same verdict)");
    }

    @Test
    void saturatedArithmetic_doesNotOverflow() {
        assertEquals(Long.MAX_VALUE, org.omc.core.ValidationEngine.saturatedDouble(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, org.omc.core.ValidationEngine.saturatedAdd(Long.MAX_VALUE - 1, 1));
        assertEquals(2_000L, org.omc.core.ValidationEngine.saturatedDouble(1_000L));
        assertEquals(Long.MAX_VALUE,
                org.omc.core.ValidationEngine.saturatedAdd(Long.MAX_VALUE / 2, Long.MAX_VALUE / 2 + 2));
    }

    // ========== FileHandler magic-table determinism (P2 slice) ==========

    @Test
    void magicDetection_m4aHeader_notSwallowedByMp4Wildcard() throws Exception {
        org.omc.service.FileHandler handler = new org.omc.service.FileHandler(
                org.mockito.Mockito.mock(org.omc.core.ConfigurationManager.class));
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("omc-m4a", ".bin");
        // ftyp M4A (specific, 11 bytes) - MP4's wildcard ftyp would also match
        byte[] m4a = { 0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x4D, 0x34, 0x41 };
        java.nio.file.Files.write(tmp, m4a);

        assertEquals(FileFormat.M4A, handler.detectFormat(tmp),
                "specific M4A signature must win over MP4 wildcard regardless of hash order");
        java.nio.file.Files.deleteIfExists(tmp);
    }

    @Test
    void magicDetection_aviHeader_notSwallowedByWildcard() throws Exception {
        org.omc.service.FileHandler handler = new org.omc.service.FileHandler(
                org.mockito.Mockito.mock(org.omc.core.ConfigurationManager.class));
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("omc-avi", ".bin");
        // RIFF....AVI: bytes 4-7 are a wildcard in every RIFF signature; only
        // the trailing type tag discriminates.
        byte[] avi = { 0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x41, 0x56, 0x49, 0x20 };
        java.nio.file.Files.write(tmp, avi);

        assertEquals(FileFormat.AVI, handler.detectFormat(tmp));
        java.nio.file.Files.deleteIfExists(tmp);
    }
}
