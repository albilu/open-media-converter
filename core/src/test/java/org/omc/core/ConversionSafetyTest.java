package org.omc.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omc.model.*;
import org.omc.service.FileHandler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversionSafetyTest {
    @TempDir Path root;
    private Path output;
    private ConversionEngine engine;
    private ToolManager tools;
    private ProgressEngine progress;

    @BeforeEach
    void setUp() throws Exception {
        output = Files.createDirectory(root.resolve("output"));
        FileHandler handler = new FileHandler(new ConfigurationManager(root.resolve("config"),
                root.resolve("data"), root.resolve("cache")));
        ValidationEngine validation = spy(new ValidationEngine(handler));
        doReturn(ValidationResult.success()).when(validation).validateToolAvailability(any());
        tools = mock(ToolManager.class);
        when(tools.selectTool(any(), any())).thenReturn(ConversionTool.IMAGEMAGICK);
        when(tools.executeTool(any(), any(), any(), any(), any(ConversionSettings.class), any(), any(), any()))
                .thenAnswer(call -> {
                    Path destination = call.getArgument(2);
                    ConversionSettings settings = call.getArgument(4);
                    Files.writeString(destination, "converted:" + settings.imageSettings().quality());
                    return ConversionResult.success(call.getArgument(6), destination, "", Duration.ZERO,
                            5, Files.size(destination), ConversionTool.IMAGEMAGICK);
                });
        progress = new ProgressEngine();
        engine = new ConversionEngine(tools, validation, progress, handler, 2);
    }

    @AfterEach
    void close() {
        engine.shutdown();
    }

    private ConversionFile file(String name, String content) throws Exception {
        Path path = root.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return ConversionFile.create(path, FileFormat.PNG, Files.size(path));
    }

    private ConversionSettings.Builder settings() {
        return ConversionSettings.builder().outputDirectory(output)
                .imageSettings(ImageSettings.builder().quality(25).build());
    }

    @Test
    void replacingTheInputDoesNotDeleteTheConvertedFile() throws Exception {
        ConversionFile input = file("output/same.png", "original");
        ConversionResult result = engine.convertSingle(input,
                settings().overwriteExisting(true).deleteOriginalFile(true).build()).get(10, TimeUnit.SECONDS);
        assertTrue(result.success(), result.errorMessage().orElse(""));
        assertEquals("converted:25", Files.readString(input.path()));
    }

    @Test
    void differentInputsWithTheSameNameReceiveDifferentOutputs() throws Exception {
        ConversionFile first = file("one/same.png", "first");
        ConversionFile second = file("two/same.png", "second");
        BatchConversionResult result = engine.convertBatch(List.of(first, second), settings().build())
                .get(10, TimeUnit.SECONDS);
        assertEquals(2, result.successCount());
        assertEquals(2, result.results().stream().map(r -> r.outputPath().orElseThrow()).distinct().count());
        assertTrue(Files.exists(output.resolve("same.png")));
        assertTrue(Files.exists(output.resolve("same-2.png")));
    }

    @Test
    void outputNeverOverwritesAnotherInputInTheBatch() throws Exception {
        ConversionFile first = file("one/same.png", "first");
        ConversionFile second = file("output/same.png", "second");
        BatchConversionResult result = engine.convertBatch(List.of(first, second),
                settings().overwriteExisting(true).build()).get(10, TimeUnit.SECONDS);
        assertEquals(2, result.successCount());
        assertEquals(2, result.results().stream().map(r -> r.outputPath().orElseThrow()).distinct().count());
    }

    @Test
    void outputAppearingWhileTheToolRunsIsPreserved() throws Exception {
        ConversionFile input = file("one/picture.png", "original");
        when(tools.executeTool(any(), any(), any(), any(), any(ConversionSettings.class), any(), any(), any()))
                .thenAnswer(call -> {
                    Path temporary = call.getArgument(2);
                    Files.writeString(temporary, "converted");
                    Files.writeString(output.resolve("picture.png"), "external output");
                    return ConversionResult.success(input.id(), temporary, "", Duration.ZERO, 8, 9,
                            ConversionTool.IMAGEMAGICK);
                });
        ConversionResult result = engine.convertSingle(input, settings().deleteOriginalFile(true).build())
                .get(10, TimeUnit.SECONDS);
        assertFalse(result.success());
        assertEquals("external output", Files.readString(output.resolve("picture.png")));
        assertTrue(Files.exists(input.path()));
    }

    @Test
    void perFileOptionsReachTheToolAlongWithTheOutputFormat() throws Exception {
        ConversionFile input = file("picture.png", "original").withSettingsOverride(FileSettingsOverride.forImage(
                "custom", ImageSettings.builder().quality(91).outputFormat(FileFormat.JPEG).build()));
        ConversionResult result = engine.convertSingle(input, settings().build()).get(10, TimeUnit.SECONDS);
        assertTrue(result.success(), result.errorMessage().orElse(""));
        assertEquals(output.resolve("picture.jpg"), result.outputPath().orElseThrow());
        assertEquals("converted:91", Files.readString(result.outputPath().orElseThrow()));
    }

    @Test
    void failedPreflightEndsBatchProgress() throws Exception {
        ConversionFile input = file("picture.png", "original");
        Files.writeString(output.resolve("picture.png"), "existing");
        engine.convertBatch(List.of(input), settings().build()).get(10, TimeUnit.SECONDS);
        assertEquals(0, progress.getBatchProgress().pendingFiles());
        assertEquals(1, progress.getBatchProgress().failedFiles());
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "omc.realTools", matches = "true")
    void publishesAcrossFilesystemsWithoutLosingOutput() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("/dev/shm"), "omc-publication-");
        Path temporary = Files.writeString(output.resolve("temporary.png"), "finished conversion");
        Path destination = directory.resolve("result.png");
        try {
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.getFileStore(temporary).equals(Files.getFileStore(directory)));
            OutputPublisher.publish(temporary, destination, false);
            assertEquals("finished conversion", Files.readString(destination));
            assertFalse(Files.exists(temporary));
            try (var remaining = Files.list(directory)) { assertEquals(1, remaining.count()); }
        } finally {
            Files.deleteIfExists(destination);
            Files.deleteIfExists(directory);
        }
    }
}
