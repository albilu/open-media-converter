package org.omc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.omc.controller.ApplicationWorkflowController;
import org.omc.controller.FileManager;
import org.omc.controller.SettingsManager;
import org.omc.controller.StateManager;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionSettings;
import org.omc.model.FileFormat;
import org.omc.model.ToolConfiguration;
import org.omc.model.ValidationResult;
import org.omc.model.VideoSettings;
import org.omc.service.FFmpegService;
import org.omc.service.FileHandler;
import org.omc.service.ImageMagickService;
import org.omc.service.LibreOfficeService;
import org.omc.service.PandocService;
import org.omc.service.ToolDiscovery;

/**
 * Regression tests for the P2 audit fixes owned by this agent:
 * <ul>
 * <li>DependencyFactory: race-free initialization, engine cleanup on
 * Phase-4 failure, safe publication of components</li>
 * <li>ValidationEngine: documented, saturating 2x disk-space estimate</li>
 * </ul>
 */
class AgentARegressionTest {

    @TempDir
    Path tempDir;

    private DependencyFactory factory;

    @BeforeEach
    void setUp() {
        factory = new DependencyFactory();
    }

    // ========== DependencyFactory: double-initialization race ==========

    /**
     * Races several threads into {@code createApplicationController} against a
     * factory with a real component stack (Mockito construction mocks are
     * thread-confined and would silently miss construction on pool threads).
     * The main thread joins the barrier so at least one contestant runs where
     * mocks apply; whatever wins, exactly one full initialization may happen
     * and every loser must observe {@code IllegalStateException}.
     */
    @Test
    void createApplicationController_concurrentCalls_initializeExactlyOnce() throws Exception {
        int threads = 4;
        DependencyFactory raceFactory = new DependencyFactory(tempDir.resolve("race-config"));

        ExecutorService pool = Executors.newFixedThreadPool(threads - 1);
        try (var discovery = Mockito.mockConstruction(ToolDiscovery.class,
                (mock, context) -> when(mock.discoverTools()).thenReturn(new ToolConfiguration()))) {
            CyclicBarrier barrier = new CyclicBarrier(threads);
            List<Future<ApplicationWorkflowController>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads - 1; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    return raceFactory.createApplicationController();
                }));
            }

            // Main thread participates in the race alongside the pool threads
            barrier.await();
            int successes = 0;
            int rejected = 0;
            try {
                raceFactory.createApplicationController();
                successes++;
            } catch (IllegalStateException e) {
                if ("Dependencies already initialized".equals(e.getMessage())) {
                    rejected++;
                }
            }

            for (Future<ApplicationWorkflowController> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof IllegalStateException
                            && "Dependencies already initialized".equals(e.getCause().getMessage())) {
                        rejected++;
                    }
                }
            }

            assertEquals(1, successes, "exactly one thread must complete initialization");
            assertEquals(threads - 1, rejected,
                    "all other threads must be rejected with IllegalStateException");

            // Clean up the real ConversionEngine (worker pool + disk monitor)
            raceFactory.shutdown();
        } finally {
            pool.shutdownNow();
        }
    }

    // ========== DependencyFactory: Phase-4 failure engine leak ==========

    @Test
    void createApplicationController_whenControllerCreationFails_shutsDownEngine() {
        ToolConfiguration toolConfig = fullToolConfig();

        try (FactoryMocks mocks = FactoryMocks.createFailingController(toolConfig)) {
            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> factory.createApplicationController());

            assertEquals("Dependency initialization failed", thrown.getMessage());

            // The engine owns a thread pool plus a disk-space monitor thread:
            // they must not leak when controller creation fails after Phase 3.
            ConversionEngine engine = mocks.engine.constructed().get(0);
            verify(engine).shutdown();
        }
    }

    // ========== DependencyFactory: safe publication contract ==========

    /**
     * The race and visibility guarantees are locking/memory-visibility
     * contracts rather than observable behavior, so they are pinned via
     * reflection: {@code createApplicationController} must be synchronized and
     * every component field volatile so getters on other threads never observe
     * half-published state.
     */
    @Test
    void dependencyFactory_publishesComponentsSafely() throws Exception {
        Method init = DependencyFactory.class.getMethod("createApplicationController");
        assertTrue(Modifier.isSynchronized(init.getModifiers()),
                "createApplicationController must be synchronized to prevent double initialization");

        List<String> componentFields = List.of(
                "configManager", "fileHandler", "validationEngine", "progressEngine",
                "toolManager", "conversionEngine", "fileManager", "settingsManager",
                "stateManager", "controller");
        for (String fieldName : componentFields) {
            Field field = DependencyFactory.class.getDeclaredField(fieldName);
            assertTrue(Modifier.isVolatile(field.getModifiers()),
                    fieldName + " must be volatile for safe publication to other threads");
        }
    }

    // ========== ValidationEngine: disk-space estimate policy ==========

    @Test
    void estimateRequiredBytes_usesConservativeTwoTimesPolicy() {
        assertEquals(0, ValidationEngine.estimateRequiredBytes(0));
        assertEquals(200, ValidationEngine.estimateRequiredBytes(100));
        assertEquals(2L * 1024 * 1024, ValidationEngine.estimateRequiredBytes(1024 * 1024));
    }

    @Test
    void estimateRequiredBytes_saturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, ValidationEngine.estimateRequiredBytes(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE,
                ValidationEngine.estimateRequiredBytes(Long.MAX_VALUE / 2 + 1));
    }

    @Test
    void validateConversionRequest_enforcesTwoTimesFilesizeEstimate() throws Exception {
        FileHandler fileHandler = mock(FileHandler.class);
        ValidationEngine engine = new ValidationEngine(fileHandler);

        Path inputFile = Files.createFile(tempDir.resolve("input.mp4"));
        Path outputDir = Files.createDirectory(tempDir.resolve("out"));
        long fileSize = 100L * 1024 * 1024; // 100 MB
        // Enough space for 1x + 500MB buffer, NOT enough for the conservative
        // 2x + buffer pre-flight estimate: proves the 2x policy is applied.
        long available = fileSize + 500L * 1024 * 1024 + 50L * 1024 * 1024;

        when(fileHandler.exists(inputFile)).thenReturn(true);
        when(fileHandler.isReadable(inputFile)).thenReturn(true);
        when(fileHandler.getFileSize(inputFile)).thenReturn(fileSize);
        when(fileHandler.detectFormat(inputFile)).thenReturn(FileFormat.MP4);
        when(fileHandler.getAvailableSpace(outputDir)).thenReturn(available);

        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, fileSize);
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .videoSettings(VideoSettings.builder().bitrate(5000).frameRate(30).crf(23).build())
                .build();

        ValidationResult result = engine.validateConversionRequest(file, settings);

        assertTrue(result.isFailure(),
                "space that fits 1x but not 2x must fail pre-flight validation");
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Insufficient disk space")));
    }

    @Test
    void validateConversionRequest_withSpaceForTwoTimesEstimate_succeeds() throws Exception {
        FileHandler fileHandler = mock(FileHandler.class);
        ValidationEngine engine = new ValidationEngine(fileHandler);

        Path inputFile = Files.createFile(tempDir.resolve("input.mp4"));
        Path outputDir = Files.createDirectory(tempDir.resolve("out"));
        long fileSize = 100L * 1024 * 1024;
        long available = 10 * fileSize; // 1GB: comfortably above 2x + 500MB buffer

        when(fileHandler.exists(inputFile)).thenReturn(true);
        when(fileHandler.isReadable(inputFile)).thenReturn(true);
        when(fileHandler.getFileSize(inputFile)).thenReturn(fileSize);
        when(fileHandler.detectFormat(inputFile)).thenReturn(FileFormat.MP4);
        when(fileHandler.getAvailableSpace(outputDir)).thenReturn(available);

        ConversionFile file = ConversionFile.create(inputFile, FileFormat.MP4, fileSize);
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .videoSettings(VideoSettings.builder().bitrate(5000).frameRate(30).crf(23).build())
                .build();

        ValidationResult result = engine.validateConversionRequest(file, settings);

        assertTrue(result.isSuccess());
    }

    // ========== Helpers ==========

    private static ToolConfiguration fullToolConfig() {
        ToolConfiguration toolConfig = mock(ToolConfiguration.class);
        when(toolConfig.getFfmpegPath()).thenReturn(Path.of("/path/ffmpeg"));
        when(toolConfig.getFfprobePath()).thenReturn(Path.of("/path/ffprobe"));
        when(toolConfig.getPandocPath()).thenReturn(Path.of("/path/pandoc"));
        when(toolConfig.getLibreOfficePath()).thenReturn(Path.of("/path/soffice"));
        when(toolConfig.getConvertPath()).thenReturn(Path.of("/path/convert"));
        return toolConfig;
    }

    /**
     * Bundles the full MockedConstruction stack used by DependencyFactory so
     * tests can close everything in one try-with-resources block.
     */
    private static final class FactoryMocks implements AutoCloseable {

        final MockedConstruction<ConfigurationManager> config;
        final MockedConstruction<FileHandler> fileHandler;
        final MockedConstruction<ValidationEngine> validation;
        final MockedConstruction<ProgressEngine> progress;
        final MockedConstruction<ToolDiscovery> toolDiscovery;
        final MockedConstruction<FFmpegService> ffmpeg;
        final MockedConstruction<PandocService> pandoc;
        final MockedConstruction<LibreOfficeService> libreOffice;
        final MockedConstruction<ImageMagickService> imageMagick;
        final MockedConstruction<ToolManager> toolManager;
        final MockedConstruction<ConversionEngine> engine;
        final MockedConstruction<FileManager> fileManager;
        final MockedConstruction<SettingsManager> settingsManager;
        final MockedConstruction<StateManager> stateManager;
        final MockedConstruction<ApplicationWorkflowController> controller;

        private FactoryMocks(ToolConfiguration toolConfig,
                MockedConstruction.MockInitializer<ApplicationWorkflowController> controllerInit) {

            config = Mockito.mockConstruction(ConfigurationManager.class);
            fileHandler = Mockito.mockConstruction(FileHandler.class);
            validation = Mockito.mockConstruction(ValidationEngine.class);
            progress = Mockito.mockConstruction(ProgressEngine.class);
            toolDiscovery = Mockito.mockConstruction(ToolDiscovery.class,
                    (mock, context) -> when(mock.discoverTools()).thenReturn(toolConfig));
            ffmpeg = Mockito.mockConstruction(FFmpegService.class);
            pandoc = Mockito.mockConstruction(PandocService.class);
            libreOffice = Mockito.mockConstruction(LibreOfficeService.class);
            imageMagick = Mockito.mockConstruction(ImageMagickService.class);
            toolManager = Mockito.mockConstruction(ToolManager.class);
            engine = Mockito.mockConstruction(ConversionEngine.class);
            fileManager = Mockito.mockConstruction(FileManager.class);
            settingsManager = Mockito.mockConstruction(SettingsManager.class);
            stateManager = Mockito.mockConstruction(StateManager.class);
            controller = Mockito.mockConstruction(ApplicationWorkflowController.class, controllerInit);
        }

        static FactoryMocks create(ToolConfiguration toolConfig) {
            return new FactoryMocks(toolConfig, (mock, context) -> {
            });
        }

        static FactoryMocks createFailingController(ToolConfiguration toolConfig) {
            return new FactoryMocks(toolConfig, (mock, context) -> {
                throw new RuntimeException("controller construction boom");
            });
        }

        @Override
        public void close() {
            controller.close();
            stateManager.close();
            settingsManager.close();
            fileManager.close();
            engine.close();
            toolManager.close();
            imageMagick.close();
            libreOffice.close();
            pandoc.close();
            ffmpeg.close();
            toolDiscovery.close();
            progress.close();
            validation.close();
            fileHandler.close();
            config.close();
        }
    }
}
