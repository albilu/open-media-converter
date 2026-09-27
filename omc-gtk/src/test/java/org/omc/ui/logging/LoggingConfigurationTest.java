package org.omc.ui.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.omc.core.ConfigurationManager;
import org.omc.exception.StateIOException;
import org.omc.util.ThreadUtils;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises production logging in fresh JVMs, independent of logback-test.xml. */
class LoggingConfigurationTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @CsvSource({"default,INFO", "environment,DEBUG", "environmentTrue,DEBUG", "runtime,DEBUG",
            "explicit,WARN", "trace,TRACE", "property,WARN", "invalid,INFO"})
    void levelsApplyToConsoleAndFiles(String mode, String expectedLevel)
            throws IOException, InterruptedException {
        Map<String, String> environment = new HashMap<>();
        List<String> properties = new ArrayList<>();
        switch (mode) {
            case "environment" -> environment.put("OMC_DEBUG", "1");
            case "environmentTrue" -> environment.put("OMC_DEBUG", "true");
            case "runtime" -> environment.put("OMC_LOG_LEVEL", "ERROR");
            case "explicit" -> environment.putAll(Map.of("OMC_DEBUG", "1", "OMC_LOG_LEVEL", "WARN"));
            case "trace" -> environment.put("OMC_LOG_LEVEL", "TRACE");
            case "property" -> {
                environment.put("OMC_LOG_LEVEL", "TRACE");
                properties.add("-Domc.logging.level=WARN");
            }
            case "invalid" -> environment.put("OMC_LOG_LEVEL", "typo");
            default -> { }
        }
        String console = runProbe(mode, environment, properties);
        String application = Files.readString(defaultLogs().resolve("app.log"));
        String conversion = Files.readString(defaultLogs().resolve("conversion.log"));
        boolean info = !expectedLevel.equals("WARN");
        boolean debug = expectedLevel.equals("DEBUG") || expectedLevel.equals("TRACE");
        for (String output : List.of(console, application)) {
            assertTrue(output.contains("app-warning"), output);
            assertEquals(info, output.contains("app-information"), output);
            assertEquals(debug, output.contains("app-debug"), output);
            assertEquals(debug, output.contains("jul-fine"), output);
            assertEquals(expectedLevel.equals("TRACE"), output.contains("app-trace"), output);
        }
        for (String output : List.of(console, conversion)) {
            assertTrue(output.contains("conversion-warning"), output);
            assertEquals(info, output.contains("conversion-information"), output);
            assertEquals(debug, output.contains("conversion-debug"), output);
            assertEquals(expectedLevel.equals("TRACE"), output.contains("conversion-trace"), output);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "xdg", "relative", "blank", "override", "legacy", "property"})
    void resolvesLogDirectoryBeforeOpeningFiles(String mode) throws IOException, InterruptedException {
        Map<String, String> environment = new HashMap<>();
        List<String> properties = new ArrayList<>();
        Path expected = defaultLogs();
        switch (mode) {
            case "xdg" -> {
                Path state = temporary.resolve("custom state");
                environment.put("XDG_STATE_HOME", state.toString());
                expected = state.resolve("open-media-converter/logs");
            }
            case "relative" -> environment.put("XDG_STATE_HOME", "relative-state");
            case "blank" -> environment.putAll(Map.of("OMC_LOG_DIR", " ", "LOG_DIR", "",
                    "XDG_STATE_HOME", " "));
            case "override" -> {
                expected = temporary.resolve("explicit logs");
                environment.putAll(Map.of("OMC_LOG_DIR", expected.toString(), "LOG_DIR",
                        temporary.resolve("unused legacy").toString(), "XDG_STATE_HOME",
                        temporary.resolve("unused state").toString()));
            }
            case "legacy" -> {
                expected = temporary.resolve("legacy logs");
                environment.put("LOG_DIR", expected.toString());
            }
            case "property" -> {
                expected = temporary.resolve("property logs");
                environment.put("OMC_LOG_DIR", temporary.resolve("unused environment").toString());
                properties.add("-DOMC_LOG_DIR=" + expected);
            }
            default -> { }
        }
        String console = runProbe("directory", environment, properties);
        assertTrue(console.contains("CONFIGURATION_LOG_DIRECTORY=" + expected), console);
        for (String name : List.of("app.log", "conversion.log", "error.log")) {
            assertTrue(Files.isRegularFile(expected.resolve(name)), expected + "/" + name);
        }
        if (!expected.equals(defaultLogs())) assertFalse(Files.exists(defaultLogs()));
        assertFalse(Files.exists(temporary.resolve("home/.local/share/open-media-converter/logs")));
        assertFalse(Files.exists(temporary.resolve("relative-state")));
        assertFalse(Files.exists(temporary.resolve("unused environment")));
        assertFalse(Files.exists(temporary.resolve("unused state")));
        assertFalse(Files.exists(temporary.resolve("unused legacy")));
    }

    @Test
    void savesJulAndUncaughtFailuresWithReaderContext() throws IOException, InterruptedException {
        runProbe("failures", Map.of(), List.of());
        String errors = Files.readString(defaultLogs().resolve("error.log"));
        assertEquals(1, errors.split("jul-severe", -1).length - 1, "initialization must not duplicate JUL handlers");
        for (String message : List.of("jul-cause", "raw-thread-failure", "factory-thread-failure",
                "reader-thread-failure")) {
            assertTrue(errors.contains("IllegalStateException: " + message), errors);
        }
        assertTrue(errors.lines().anyMatch(line -> line.contains("Uncaught exception in thread Reader-")
                && line.contains("omcFile=reader-file omcTool=ffmpeg")), errors);
        String conversions = Files.readString(defaultLogs().resolve("conversion.log"));
        assertTrue(conversions.lines().anyMatch(line -> line.contains("reader-context")
                && line.contains("omcFile=reader-file omcTool=ffmpeg")), conversions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"explicitShutdown", "exitHook"})
    void burstSurvivesRotationAndBothShutdownPaths(String mode) throws IOException, InterruptedException {
        runProbe(mode, Map.of("OMC_DEBUG", "1"), List.of());
        Pattern marker = Pattern.compile("burst-event-(\\d+)");
        Set<Integer> seen = new HashSet<>();
        int records = 0;
        List<Path> paths;
        try (var files = Files.list(defaultLogs())) {
            paths = files.filter(path -> path.getFileName().toString().startsWith("conversion")).toList();
        }
        assertTrue(paths.stream().anyMatch(path -> path.toString().endsWith(".gz")), "must exercise actual rotation");
        for (Path path : paths) {
            String output;
            if (path.toString().endsWith(".gz")) {
                try (var input = new GZIPInputStream(Files.newInputStream(path))) {
                    output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                assertTrue(path.toString().endsWith(".log"), "compression must finish before exit: " + path);
                output = Files.readString(path);
            }
            var matches = marker.matcher(output);
            while (matches.find()) {
                records++;
                seen.add(Integer.parseInt(matches.group(1)));
            }
        }
        assertEquals(30_000, records, "no duplicate or dropped records");
        assertEquals(30_000, seen.size(), "every numbered event must be retained");
        assertTrue(seen.contains(0));
        assertTrue(seen.contains(29_999), "the last event must reach disk before exit");
    }

    @Test
    void removesExpiredArchivesOnStartup() throws IOException, InterruptedException {
        Files.createDirectories(defaultLogs());
        for (String log : List.of("app", "conversion", "error")) {
            for (int days : List.of(2, 10)) {
                Path archive = defaultLogs().resolve(log + "." + LocalDate.now().minusDays(days) + ".0.log.gz");
                try (var output = new GZIPOutputStream(Files.newOutputStream(archive))) {
                    output.write("old log entry".getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        runProbe("default", Map.of(), List.of());
        for (String log : List.of("app", "conversion", "error")) {
            assertFalse(Files.exists(defaultLogs().resolve(log + "." + LocalDate.now().minusDays(10) + ".0.log.gz")));
            assertTrue(Files.exists(defaultLogs().resolve(log + "." + LocalDate.now().minusDays(2) + ".0.log.gz")));
        }
    }

    private Path defaultLogs() {
        return temporary.resolve("home/.local/state/open-media-converter/logs");
    }

    private String runProbe(String mode, Map<String, String> environment, List<String> properties)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Duser.home=" + temporary.resolve("home"),
                "-Dlogback.configurationFile=" + getClass().getResource("/logback.xml").toExternalForm()));
        command.addAll(properties);
        command.addAll(List.of("-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                Probe.class.getName(), mode));
        Path transcript = temporary.resolve("console.txt");
        ProcessBuilder builder = new ProcessBuilder(command).directory(temporary.toFile())
                .redirectErrorStream(true).redirectOutput(transcript.toFile());
        for (String key : List.of("OMC_DEBUG", "OMC_LOG_LEVEL", "OMC_LOG_DIR", "LOG_DIR", "XDG_STATE_HOME",
                "XDG_CONFIG_HOME", "XDG_DATA_HOME", "XDG_CACHE_HOME",
                "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) {
            builder.environment().remove(key);
        }
        builder.environment().putAll(environment);
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "logging probe timed out");
            String output = Files.readString(transcript);
            assertEquals(0, process.exitValue(), output);
            assertFalse(output.contains("ERROR in ch.qos.logback"), output);
            return output;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    /** Forked entry point using the application's real logging lifecycle. */
    public static final class Probe {
        /** Emits diagnostic events, including failures and a high-volume conversion burst. */
        public static void main(String[] args) throws InterruptedException, StateIOException {
            LoggingConfiguration.initialize();
            LoggingConfiguration.initialize();
            if (args[0].equals("runtime")) LoggingConfiguration.enableDebugLogging();
            if (args[0].equals("directory")) {
                System.out.println("CONFIGURATION_LOG_DIRECTORY=" + new ConfigurationManager().getLogDirectory());
            }
            Logger application = LoggerFactory.getLogger("org.omc.ui.LoggingProbe");
            Logger conversion = LoggerFactory.getLogger("org.omc.service.LoggingProbe");
            application.warn("app-warning");
            application.info("app-information");
            application.debug("app-debug");
            application.trace("app-trace");
            conversion.warn("conversion-warning");
            conversion.info("conversion-information");
            conversion.debug("conversion-debug");
            conversion.trace("conversion-trace");
            java.util.logging.Logger jul = java.util.logging.Logger.getLogger("logging-probe");
            jul.fine("jul-fine");
            if (args[0].equals("failures")) {
                jul.log(java.util.logging.Level.SEVERE, "jul-severe", new IllegalStateException("jul-cause"));
                Thread raw = new Thread(() -> { throw new IllegalStateException("raw-thread-failure"); }, "Raw");
                Thread factory = ThreadUtils.createThreadFactory("Factory").newThread(
                        () -> { throw new IllegalStateException("factory-thread-failure"); });
                MDC.put("omcFile", "reader-file");
                MDC.put("omcTool", "ffmpeg");
                Thread reader = ThreadUtils.createContextThread("Reader", () -> {
                    conversion.info("reader-context");
                    throw new IllegalStateException("reader-thread-failure");
                });
                MDC.clear();
                for (Thread thread : List.of(raw, factory, reader)) {
                    thread.start();
                    thread.join();
                }
            }
            if (args[0].equals("explicitShutdown") || args[0].equals("exitHook")) {
                for (int i = 0; i < 30_000; i++) conversion.debug("burst-event-{} payload", i);
            }
            if (args[0].equals("exitHook")) System.exit(0);
            LoggingConfiguration.shutdown();
            LoggingConfiguration.shutdown();
        }
    }
}
