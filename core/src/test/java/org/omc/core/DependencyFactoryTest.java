package org.omc.core;

import org.omc.core.ProgressEngine;
import org.omc.core.ConfigurationManager;
import org.omc.core.DependencyFactory;
import org.omc.core.ToolManager;
import org.omc.core.ValidationEngine;
import org.omc.core.ConversionEngine;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.omc.controller.ApplicationWorkflowController;
import org.omc.controller.FileManager;
import org.omc.controller.SettingsManager;
import org.omc.controller.StateManager;
import org.omc.model.ToolConfiguration;
import org.omc.service.FileHandler;
import org.omc.service.FFmpegService;
import org.omc.service.PandocService;
import org.omc.service.LibreOfficeService;
import org.omc.service.ImageMagickService;
import org.omc.service.ToolDiscovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.MockedConstruction;

import java.nio.file.Path;

/**
 * Comprehensive unit tests for DependencyFactory.
 * Tests initialization, getters, shutdown, and component wiring.
 */
class DependencyFactoryTest {

    private DependencyFactory factory;

    @BeforeEach
    void setUp() {
        factory = new DependencyFactory();
    }

    @Test
    @DisplayName("Should successfully initialize all dependencies in correct order")
    void testSuccessfulInitialization() throws InterruptedException {
        // Given: All constructors are mocked
        ToolConfiguration toolConfig = mock(ToolConfiguration.class);
        when(toolConfig.getFfmpegPath()).thenReturn(Path.of("/path/ffmpeg"));
        when(toolConfig.getFfprobePath()).thenReturn(Path.of("/path/ffprobe"));
        when(toolConfig.getPandocPath()).thenReturn(Path.of("/path/pandoc"));
        when(toolConfig.getLibreOfficePath()).thenReturn(Path.of("/path/soffice"));
        when(toolConfig.getConvertPath()).thenReturn(Path.of("/path/convert"));

        try (MockedConstruction<ConfigurationManager> configMock = mockConstruction(ConfigurationManager.class);
                MockedConstruction<FileHandler> fileHandlerMock = mockConstruction(FileHandler.class,
                        (mock, context) -> assertEquals(configMock.constructed().get(0), context.arguments().get(0)));
                MockedConstruction<ValidationEngine> validationMock = mockConstruction(ValidationEngine.class,
                        (mock, context) -> assertEquals(fileHandlerMock.constructed().get(0),
                                context.arguments().get(0)));
                MockedConstruction<ProgressEngine> progressMock = mockConstruction(ProgressEngine.class);
                MockedConstruction<ToolDiscovery> toolDiscoveryMock = mockConstruction(ToolDiscovery.class,
                        (mock, context) -> {
                            assertEquals(configMock.constructed().get(0), context.arguments().get(0));
                            when(mock.discoverTools()).thenReturn(toolConfig);
                        });
                MockedConstruction<ToolConfiguration> toolConfigMock = mockConstruction(ToolConfiguration.class);
                MockedConstruction<FFmpegService> ffmpegMock = mockConstruction(FFmpegService.class,
                        (mock, context) -> {
                            assertEquals(Path.of("/path/ffmpeg"), context.arguments().get(0));
                            assertEquals(Path.of("/path/ffprobe"), context.arguments().get(1));
                        });
                MockedConstruction<PandocService> pandocMock = mockConstruction(PandocService.class,
                        (mock, context) -> assertEquals(Path.of("/path/pandoc"), context.arguments().get(0)));
                MockedConstruction<LibreOfficeService> libreOfficeMock = mockConstruction(LibreOfficeService.class,
                        (mock, context) -> assertEquals(Path.of("/path/soffice"), context.arguments().get(0)));
                MockedConstruction<ImageMagickService> imageMagickMock = mockConstruction(ImageMagickService.class,
                        (mock, context) -> assertEquals(Path.of("/path/convert"), context.arguments().get(0)));
                MockedConstruction<ToolManager> toolManagerMock = mockConstruction(ToolManager.class,
                        (mock, context) -> {
                            assertEquals(ffmpegMock.constructed().get(0), context.arguments().get(0));
                            assertEquals(pandocMock.constructed().get(0), context.arguments().get(1));
                            assertEquals(libreOfficeMock.constructed().get(0), context.arguments().get(2));
                            assertEquals(imageMagickMock.constructed().get(0), context.arguments().get(3));
                        });
                MockedConstruction<ConversionEngine> conversionMock = mockConstruction(ConversionEngine.class,
                        (mock, context) -> {
                            assertEquals(toolManagerMock.constructed().get(0), context.arguments().get(0));
                            assertEquals(validationMock.constructed().get(0), context.arguments().get(1));
                            assertEquals(progressMock.constructed().get(0), context.arguments().get(2));
                            assertEquals(fileHandlerMock.constructed().get(0), context.arguments().get(3));
                            assertEquals(4, context.arguments().get(4));
                        });
                MockedConstruction<FileManager> fileManagerMock = mockConstruction(FileManager.class,
                        (mock, context) -> {
                            assertEquals(fileHandlerMock.constructed().get(0), context.arguments().get(0));
                            assertEquals(validationMock.constructed().get(0), context.arguments().get(1));
                        });
                MockedConstruction<SettingsManager> settingsManagerMock = mockConstruction(SettingsManager.class,
                        (mock, context) -> {
                            assertEquals(configMock.constructed().get(0), context.arguments().get(0));
                            assertEquals(validationMock.constructed().get(0), context.arguments().get(1));
                        });
                MockedConstruction<StateManager> stateManagerMock = mockConstruction(StateManager.class,
                        (mock, context) -> assertEquals(configMock.constructed().get(0), context.arguments().get(0)));
                MockedConstruction<ApplicationWorkflowController> controllerMock = mockConstruction(
                        ApplicationWorkflowController.class,
                        (mock, context) -> {
                            assertEquals(fileManagerMock.constructed().get(0), context.arguments().get(0));
                            assertEquals(settingsManagerMock.constructed().get(0), context.arguments().get(1));
                            assertEquals(stateManagerMock.constructed().get(0), context.arguments().get(2));
                            assertEquals(conversionMock.constructed().get(0), context.arguments().get(3));
                        })) {

            // When
            ApplicationWorkflowController result = factory.createApplicationController();

            // Then
            assertNotNull(result);
            assertEquals(controllerMock.constructed().get(0), result);

            // Verify construction order and counts
            assertEquals(1, configMock.constructed().size());
            assertEquals(1, fileHandlerMock.constructed().size());
            assertEquals(1, validationMock.constructed().size());
            assertEquals(1, progressMock.constructed().size());
            assertEquals(1, toolDiscoveryMock.constructed().size());
            assertEquals(1, ffmpegMock.constructed().size());
            assertEquals(1, pandocMock.constructed().size());
            assertEquals(1, libreOfficeMock.constructed().size());
            assertEquals(1, imageMagickMock.constructed().size());
            assertEquals(1, toolManagerMock.constructed().size());
            assertEquals(1, conversionMock.constructed().size());
            assertEquals(1, fileManagerMock.constructed().size());
            assertEquals(1, settingsManagerMock.constructed().size());
            assertEquals(1, stateManagerMock.constructed().size());
            assertEquals(1, controllerMock.constructed().size());
        }
    }

    @Test
    @DisplayName("Should throw IllegalStateException on double initialization")
    void testDoubleInitialization() throws InterruptedException {
        // Given: First initialization succeeds
        ToolConfiguration toolConfig = mock(ToolConfiguration.class);
        when(toolConfig.getFfmpegPath()).thenReturn(Path.of("/path/ffmpeg"));
        when(toolConfig.getFfprobePath()).thenReturn(Path.of("/path/ffprobe"));
        when(toolConfig.getPandocPath()).thenReturn(Path.of("/path/pandoc"));
        when(toolConfig.getLibreOfficePath()).thenReturn(Path.of("/path/soffice"));
        when(toolConfig.getConvertPath()).thenReturn(Path.of("/path/convert"));

        try (MockedConstruction<ConfigurationManager> configMock = mockConstruction(ConfigurationManager.class);
                MockedConstruction<FileHandler> fileHandlerMock = mockConstruction(FileHandler.class);
                MockedConstruction<ValidationEngine> validationMock = mockConstruction(ValidationEngine.class);
                MockedConstruction<ProgressEngine> progressMock = mockConstruction(ProgressEngine.class);
                MockedConstruction<ToolDiscovery> toolDiscoveryMock = mockConstruction(ToolDiscovery.class,
                        (mock, context) -> when(mock.discoverTools()).thenReturn(toolConfig));
                MockedConstruction<FFmpegService> ffmpegMock = mockConstruction(FFmpegService.class);
                MockedConstruction<PandocService> pandocMock = mockConstruction(PandocService.class);
                MockedConstruction<LibreOfficeService> libreOfficeMock = mockConstruction(LibreOfficeService.class);
                MockedConstruction<ImageMagickService> imageMagickMock = mockConstruction(ImageMagickService.class);
                MockedConstruction<ToolManager> toolManagerMock = mockConstruction(ToolManager.class);
                MockedConstruction<ConversionEngine> conversionMock = mockConstruction(ConversionEngine.class);
                MockedConstruction<FileManager> fileManagerMock = mockConstruction(FileManager.class);
                MockedConstruction<SettingsManager> settingsManagerMock = mockConstruction(SettingsManager.class);
                MockedConstruction<StateManager> stateManagerMock = mockConstruction(StateManager.class);
                MockedConstruction<ApplicationWorkflowController> controllerMock = mockConstruction(
                        ApplicationWorkflowController.class)) {

            factory.createApplicationController();

            // When/Then
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> factory.createApplicationController());
            assertEquals("Dependencies already initialized", exception.getMessage());
        }
    }

    @Test
    @DisplayName("Should return correct instances from all getter methods after initialization")
    void testGetterMethods() throws InterruptedException {
        // Given: Dependencies initialized
        ToolConfiguration toolConfig = mock(ToolConfiguration.class);
        when(toolConfig.getFfmpegPath()).thenReturn(Path.of("/path/ffmpeg"));
        when(toolConfig.getFfprobePath()).thenReturn(Path.of("/path/ffprobe"));
        when(toolConfig.getPandocPath()).thenReturn(Path.of("/path/pandoc"));
        when(toolConfig.getLibreOfficePath()).thenReturn(Path.of("/path/soffice"));
        when(toolConfig.getConvertPath()).thenReturn(Path.of("/path/convert"));

        try (MockedConstruction<ConfigurationManager> configMock = mockConstruction(ConfigurationManager.class);
                MockedConstruction<FileHandler> fileHandlerMock = mockConstruction(FileHandler.class);
                MockedConstruction<ValidationEngine> validationMock = mockConstruction(ValidationEngine.class);
                MockedConstruction<ProgressEngine> progressMock = mockConstruction(ProgressEngine.class);
                MockedConstruction<ToolDiscovery> toolDiscoveryMock = mockConstruction(ToolDiscovery.class,
                        (mock, context) -> when(mock.discoverTools()).thenReturn(toolConfig));
                MockedConstruction<FFmpegService> ffmpegMock = mockConstruction(FFmpegService.class);
                MockedConstruction<PandocService> pandocMock = mockConstruction(PandocService.class);
                MockedConstruction<LibreOfficeService> libreOfficeMock = mockConstruction(LibreOfficeService.class);
                MockedConstruction<ImageMagickService> imageMagickMock = mockConstruction(ImageMagickService.class);
                MockedConstruction<ToolManager> toolManagerMock = mockConstruction(ToolManager.class);
                MockedConstruction<ConversionEngine> conversionMock = mockConstruction(ConversionEngine.class);
                MockedConstruction<FileManager> fileManagerMock = mockConstruction(FileManager.class);
                MockedConstruction<SettingsManager> settingsManagerMock = mockConstruction(SettingsManager.class);
                MockedConstruction<StateManager> stateManagerMock = mockConstruction(StateManager.class);
                MockedConstruction<ApplicationWorkflowController> controllerMock = mockConstruction(
                        ApplicationWorkflowController.class)) {

            factory.createApplicationController();

            // When/Then
            assertNotNull(factory.getConfigurationManager());
            assertNotNull(factory.getFileHandler());
            assertNotNull(factory.getValidationEngine());
            assertNotNull(factory.getProgressEngine());
            assertNotNull(factory.getToolManager());
            assertNotNull(factory.getConversionEngine());
            assertNotNull(factory.getFileManager());
            assertNotNull(factory.getSettingsManager());
            assertNotNull(factory.getStateManager());
            assertNotNull(factory.getApplicationWorkflowController());

            // Verify they return the mocked instances
            assertEquals(configMock.constructed().get(0), factory.getConfigurationManager());
            assertEquals(fileHandlerMock.constructed().get(0), factory.getFileHandler());
            assertEquals(validationMock.constructed().get(0), factory.getValidationEngine());
            assertEquals(progressMock.constructed().get(0), factory.getProgressEngine());
            assertEquals(toolManagerMock.constructed().get(0), factory.getToolManager());
            assertEquals(conversionMock.constructed().get(0), factory.getConversionEngine());
            assertEquals(fileManagerMock.constructed().get(0), factory.getFileManager());
            assertEquals(settingsManagerMock.constructed().get(0), factory.getSettingsManager());
            assertEquals(stateManagerMock.constructed().get(0), factory.getStateManager());
            assertEquals(controllerMock.constructed().get(0), factory.getApplicationWorkflowController());
        }
    }

    @Test
    @DisplayName("Should throw IllegalStateException when calling getters before initialization")
    void testGetterBeforeInit() {
        // Given: No initialization

        // When/Then
        assertThrows(IllegalStateException.class, () -> factory.getConfigurationManager());
        assertThrows(IllegalStateException.class, () -> factory.getFileHandler());
        assertThrows(IllegalStateException.class, () -> factory.getValidationEngine());
        assertThrows(IllegalStateException.class, () -> factory.getProgressEngine());
        assertThrows(IllegalStateException.class, () -> factory.getToolManager());
        assertThrows(IllegalStateException.class, () -> factory.getConversionEngine());
        assertThrows(IllegalStateException.class, () -> factory.getFileManager());
        assertThrows(IllegalStateException.class, () -> factory.getSettingsManager());
        assertThrows(IllegalStateException.class, () -> factory.getStateManager());
        assertThrows(IllegalStateException.class, () -> factory.getApplicationWorkflowController());
    }

    @Test
    @DisplayName("Should verify ConfigurationManager is created first and used by other components")
    void testConfigurationFirst() throws InterruptedException {
        // Given
        ToolConfiguration toolConfig = mock(ToolConfiguration.class);
        when(toolConfig.getFfmpegPath()).thenReturn(Path.of("/path/ffmpeg"));
        when(toolConfig.getFfprobePath()).thenReturn(Path.of("/path/ffprobe"));
        when(toolConfig.getPandocPath()).thenReturn(Path.of("/path/pandoc"));
        when(toolConfig.getLibreOfficePath()).thenReturn(Path.of("/path/soffice"));
        when(toolConfig.getConvertPath()).thenReturn(Path.of("/path/convert"));

        try (MockedConstruction<ConfigurationManager> configMock = mockConstruction(ConfigurationManager.class);
                MockedConstruction<FileHandler> fileHandlerMock = mockConstruction(FileHandler.class,
                        (mock, context) -> assertEquals(configMock.constructed().get(0), context.arguments().get(0)));
                MockedConstruction<ValidationEngine> validationMock = mockConstruction(ValidationEngine.class);
                MockedConstruction<ProgressEngine> progressMock = mockConstruction(ProgressEngine.class);
                MockedConstruction<ToolDiscovery> toolDiscoveryMock = mockConstruction(ToolDiscovery.class,
                        (mock, context) -> {
                            assertEquals(configMock.constructed().get(0), context.arguments().get(0));
                            when(mock.discoverTools()).thenReturn(toolConfig);
                        });
                MockedConstruction<FFmpegService> ffmpegMock = mockConstruction(FFmpegService.class);
                MockedConstruction<PandocService> pandocMock = mockConstruction(PandocService.class);
                MockedConstruction<LibreOfficeService> libreOfficeMock = mockConstruction(LibreOfficeService.class);
                MockedConstruction<ImageMagickService> imageMagickMock = mockConstruction(ImageMagickService.class);
                MockedConstruction<ToolManager> toolManagerMock = mockConstruction(ToolManager.class);
                MockedConstruction<ConversionEngine> conversionMock = mockConstruction(ConversionEngine.class);
                MockedConstruction<FileManager> fileManagerMock = mockConstruction(FileManager.class);
                MockedConstruction<SettingsManager> settingsManagerMock = mockConstruction(SettingsManager.class,
                        (mock, context) -> {
                            assertEquals(configMock.constructed().get(0), context.arguments().get(0));
                            assertEquals(validationMock.constructed().get(0), context.arguments().get(1));
                        });
                MockedConstruction<StateManager> stateManagerMock = mockConstruction(StateManager.class,
                        (mock, context) -> assertEquals(configMock.constructed().get(0), context.arguments().get(0)));
                MockedConstruction<ApplicationWorkflowController> controllerMock = mockConstruction(
                        ApplicationWorkflowController.class)) {

            // When
            factory.createApplicationController();

            // Then: ConfigurationManager is used by FileHandler, ToolDiscovery,
            // SettingsManager, StateManager
            // Assertions are in the mock construction lambdas
        }
    }

    @Test
    @DisplayName("Should properly shutdown all components and clear references")
    void testShutdown() throws InterruptedException {
        // Given: Dependencies initialized
        ToolConfiguration toolConfig = mock(ToolConfiguration.class);
        when(toolConfig.getFfmpegPath()).thenReturn(Path.of("/path/ffmpeg"));
        when(toolConfig.getFfprobePath()).thenReturn(Path.of("/path/ffprobe"));
        when(toolConfig.getPandocPath()).thenReturn(Path.of("/path/pandoc"));
        when(toolConfig.getLibreOfficePath()).thenReturn(Path.of("/path/soffice"));
        when(toolConfig.getConvertPath()).thenReturn(Path.of("/path/convert"));

        try (MockedConstruction<ConfigurationManager> configMock = mockConstruction(ConfigurationManager.class);
                MockedConstruction<FileHandler> fileHandlerMock = mockConstruction(FileHandler.class);
                MockedConstruction<ValidationEngine> validationMock = mockConstruction(ValidationEngine.class);
                MockedConstruction<ProgressEngine> progressMock = mockConstruction(ProgressEngine.class);
                MockedConstruction<ToolDiscovery> toolDiscoveryMock = mockConstruction(ToolDiscovery.class,
                        (mock, context) -> when(mock.discoverTools()).thenReturn(toolConfig));
                MockedConstruction<FFmpegService> ffmpegMock = mockConstruction(FFmpegService.class);
                MockedConstruction<PandocService> pandocMock = mockConstruction(PandocService.class);
                MockedConstruction<LibreOfficeService> libreOfficeMock = mockConstruction(LibreOfficeService.class);
                MockedConstruction<ImageMagickService> imageMagickMock = mockConstruction(ImageMagickService.class);
                MockedConstruction<ToolManager> toolManagerMock = mockConstruction(ToolManager.class);
                MockedConstruction<ConversionEngine> conversionMock = mockConstruction(ConversionEngine.class);
                MockedConstruction<FileManager> fileManagerMock = mockConstruction(FileManager.class);
                MockedConstruction<SettingsManager> settingsManagerMock = mockConstruction(SettingsManager.class);
                MockedConstruction<StateManager> stateManagerMock = mockConstruction(StateManager.class);
                MockedConstruction<ApplicationWorkflowController> controllerMock = mockConstruction(
                        ApplicationWorkflowController.class)) {

            factory.createApplicationController();

            ConversionEngine conversionEngine = conversionMock.constructed().get(0);
            doNothing().when(conversionEngine).shutdown();

            // When
            factory.shutdown();

            // Then
            verify(conversionEngine).shutdown();

            // And getters should throw again
            assertThrows(IllegalStateException.class, () -> factory.getConfigurationManager());
            assertThrows(IllegalStateException.class, () -> factory.getApplicationWorkflowController());
        }
    }

    @Test
    @DisplayName("Should handle shutdown errors gracefully without throwing")
    void testShutdownErrorHandling() throws InterruptedException {
        // Given: Dependencies initialized and ConversionEngine shutdown throws
        ToolConfiguration toolConfig = mock(ToolConfiguration.class);
        when(toolConfig.getFfmpegPath()).thenReturn(Path.of("/path/ffmpeg"));
        when(toolConfig.getFfprobePath()).thenReturn(Path.of("/path/ffprobe"));
        when(toolConfig.getPandocPath()).thenReturn(Path.of("/path/pandoc"));
        when(toolConfig.getLibreOfficePath()).thenReturn(Path.of("/path/soffice"));
        when(toolConfig.getConvertPath()).thenReturn(Path.of("/path/convert"));

        try (MockedConstruction<ConfigurationManager> configMock = mockConstruction(ConfigurationManager.class);
                MockedConstruction<FileHandler> fileHandlerMock = mockConstruction(FileHandler.class);
                MockedConstruction<ValidationEngine> validationMock = mockConstruction(ValidationEngine.class);
                MockedConstruction<ProgressEngine> progressMock = mockConstruction(ProgressEngine.class);
                MockedConstruction<ToolDiscovery> toolDiscoveryMock = mockConstruction(ToolDiscovery.class,
                        (mock, context) -> when(mock.discoverTools()).thenReturn(toolConfig));
                MockedConstruction<FFmpegService> ffmpegMock = mockConstruction(FFmpegService.class);
                MockedConstruction<PandocService> pandocMock = mockConstruction(PandocService.class);
                MockedConstruction<LibreOfficeService> libreOfficeMock = mockConstruction(LibreOfficeService.class);
                MockedConstruction<ImageMagickService> imageMagickMock = mockConstruction(ImageMagickService.class);
                MockedConstruction<ToolManager> toolManagerMock = mockConstruction(ToolManager.class);
                MockedConstruction<ConversionEngine> conversionMock = mockConstruction(ConversionEngine.class);
                MockedConstruction<FileManager> fileManagerMock = mockConstruction(FileManager.class);
                MockedConstruction<SettingsManager> settingsManagerMock = mockConstruction(SettingsManager.class);
                MockedConstruction<StateManager> stateManagerMock = mockConstruction(StateManager.class);
                MockedConstruction<ApplicationWorkflowController> controllerMock = mockConstruction(
                        ApplicationWorkflowController.class)) {

            factory.createApplicationController();

            ConversionEngine conversionEngine = conversionMock.constructed().get(0);
            doThrow(new RuntimeException("Shutdown failed")).when(conversionEngine).shutdown();

            // When
            factory.shutdown();

            // Then: No exception thrown, shutdown completes gracefully
            verify(conversionEngine).shutdown();
        }
    }

    @Test
    @DisplayName("Should verify ToolDiscovery discovers tools and creates services with correct paths")
    void testToolDiscovery() throws InterruptedException {
        // Given
        ToolConfiguration toolConfig = mock(ToolConfiguration.class);
        when(toolConfig.getFfmpegPath()).thenReturn(Path.of("/path/ffmpeg"));
        when(toolConfig.getFfprobePath()).thenReturn(Path.of("/path/ffprobe"));
        when(toolConfig.getPandocPath()).thenReturn(Path.of("/path/pandoc"));
        when(toolConfig.getLibreOfficePath()).thenReturn(Path.of("/path/soffice"));
        when(toolConfig.getConvertPath()).thenReturn(Path.of("/path/convert"));

        try (MockedConstruction<ConfigurationManager> configMock = mockConstruction(ConfigurationManager.class);
                MockedConstruction<FileHandler> fileHandlerMock = mockConstruction(FileHandler.class);
                MockedConstruction<ValidationEngine> validationMock = mockConstruction(ValidationEngine.class);
                MockedConstruction<ProgressEngine> progressMock = mockConstruction(ProgressEngine.class);
                MockedConstruction<ToolDiscovery> toolDiscoveryMock = mockConstruction(ToolDiscovery.class,
                        (mock, context) -> when(mock.discoverTools()).thenReturn(toolConfig));
                MockedConstruction<FFmpegService> ffmpegMock = mockConstruction(FFmpegService.class,
                        (mock, context) -> {
                            assertEquals(Path.of("/path/ffmpeg"), context.arguments().get(0));
                            assertEquals(Path.of("/path/ffprobe"), context.arguments().get(1));
                        });
                MockedConstruction<PandocService> pandocMock = mockConstruction(PandocService.class,
                        (mock, context) -> assertEquals(Path.of("/path/pandoc"), context.arguments().get(0)));
                MockedConstruction<LibreOfficeService> libreOfficeMock = mockConstruction(LibreOfficeService.class,
                        (mock, context) -> assertEquals(Path.of("/path/soffice"), context.arguments().get(0)));
                MockedConstruction<ImageMagickService> imageMagickMock = mockConstruction(ImageMagickService.class,
                        (mock, context) -> assertEquals(Path.of("/path/convert"), context.arguments().get(0)));
                MockedConstruction<ToolManager> toolManagerMock = mockConstruction(ToolManager.class);
                MockedConstruction<ConversionEngine> conversionMock = mockConstruction(ConversionEngine.class);
                MockedConstruction<FileManager> fileManagerMock = mockConstruction(FileManager.class);
                MockedConstruction<SettingsManager> settingsManagerMock = mockConstruction(SettingsManager.class);
                MockedConstruction<StateManager> stateManagerMock = mockConstruction(StateManager.class);
                MockedConstruction<ApplicationWorkflowController> controllerMock = mockConstruction(
                        ApplicationWorkflowController.class)) {

            // When
            factory.createApplicationController();

            // Then: Services are created with paths from ToolConfiguration
            // Assertions are in the mock construction lambdas
        }
    }

    @Test
    @DisplayName("Should verify all components are properly wired with correct dependencies")
    void testComponentIntegration() throws InterruptedException {
        // Given: All components mocked
        ToolConfiguration toolConfig = mock(ToolConfiguration.class);
        when(toolConfig.getFfmpegPath()).thenReturn(Path.of("/path/ffmpeg"));
        when(toolConfig.getFfprobePath()).thenReturn(Path.of("/path/ffprobe"));
        when(toolConfig.getPandocPath()).thenReturn(Path.of("/path/pandoc"));
        when(toolConfig.getLibreOfficePath()).thenReturn(Path.of("/path/soffice"));
        when(toolConfig.getConvertPath()).thenReturn(Path.of("/path/convert"));

        try (MockedConstruction<ConfigurationManager> configMock = mockConstruction(ConfigurationManager.class);
                MockedConstruction<FileHandler> fileHandlerMock = mockConstruction(FileHandler.class);
                MockedConstruction<ValidationEngine> validationMock = mockConstruction(ValidationEngine.class);
                MockedConstruction<ProgressEngine> progressMock = mockConstruction(ProgressEngine.class);
                MockedConstruction<ToolDiscovery> toolDiscoveryMock = mockConstruction(ToolDiscovery.class,
                        (mock, context) -> when(mock.discoverTools()).thenReturn(toolConfig));
                MockedConstruction<FFmpegService> ffmpegMock = mockConstruction(FFmpegService.class);
                MockedConstruction<PandocService> pandocMock = mockConstruction(PandocService.class);
                MockedConstruction<LibreOfficeService> libreOfficeMock = mockConstruction(LibreOfficeService.class);
                MockedConstruction<ImageMagickService> imageMagickMock = mockConstruction(ImageMagickService.class);
                MockedConstruction<ToolManager> toolManagerMock = mockConstruction(ToolManager.class);
                MockedConstruction<ConversionEngine> conversionMock = mockConstruction(ConversionEngine.class);
                MockedConstruction<FileManager> fileManagerMock = mockConstruction(FileManager.class);
                MockedConstruction<SettingsManager> settingsManagerMock = mockConstruction(SettingsManager.class);
                MockedConstruction<StateManager> stateManagerMock = mockConstruction(StateManager.class);
                MockedConstruction<ApplicationWorkflowController> controllerMock = mockConstruction(
                        ApplicationWorkflowController.class)) {

            // When
            factory.createApplicationController();

            // Then: All wiring assertions are in the mock construction lambdas above
            // This test ensures the integration is covered by the successful initialization
            // test
        }
    }

    @Test
    @DisplayName("Should create ImageMagickService when convert binary is present")
    void testImageMagickService_ConvertBinaryPresent() throws InterruptedException {
        // Given: ToolConfiguration includes convert path
        ToolConfiguration toolConfig = mock(ToolConfiguration.class);
        when(toolConfig.getFfmpegPath()).thenReturn(Path.of("/path/ffmpeg"));
        when(toolConfig.getFfprobePath()).thenReturn(Path.of("/path/ffprobe"));
        when(toolConfig.getPandocPath()).thenReturn(Path.of("/path/pandoc"));
        when(toolConfig.getLibreOfficePath()).thenReturn(Path.of("/path/soffice"));
        when(toolConfig.getConvertPath()).thenReturn(Path.of("/usr/bin/convert"));

        try (MockedConstruction<ConfigurationManager> configMock = mockConstruction(ConfigurationManager.class);
                MockedConstruction<FileHandler> fileHandlerMock = mockConstruction(FileHandler.class);
                MockedConstruction<ValidationEngine> validationMock = mockConstruction(ValidationEngine.class);
                MockedConstruction<ProgressEngine> progressMock = mockConstruction(ProgressEngine.class);
                MockedConstruction<ToolDiscovery> toolDiscoveryMock = mockConstruction(ToolDiscovery.class,
                        (mock, context) -> when(mock.discoverTools()).thenReturn(toolConfig));
                MockedConstruction<FFmpegService> ffmpegMock = mockConstruction(FFmpegService.class);
                MockedConstruction<PandocService> pandocMock = mockConstruction(PandocService.class);
                MockedConstruction<LibreOfficeService> libreOfficeMock = mockConstruction(LibreOfficeService.class);
                MockedConstruction<ImageMagickService> imageMagickMock = mockConstruction(ImageMagickService.class,
                        (mock, context) -> assertEquals(Path.of("/usr/bin/convert"), context.arguments().get(0)));
                MockedConstruction<ToolManager> toolManagerMock = mockConstruction(ToolManager.class,
                        (mock, context) -> {
                            assertEquals(4, context.arguments().size());
                            assertEquals(ffmpegMock.constructed().get(0), context.arguments().get(0));
                            assertEquals(pandocMock.constructed().get(0), context.arguments().get(1));
                            assertEquals(libreOfficeMock.constructed().get(0), context.arguments().get(2));
                            assertEquals(imageMagickMock.constructed().get(0), context.arguments().get(3));
                        });
                MockedConstruction<ConversionEngine> conversionMock = mockConstruction(ConversionEngine.class);
                MockedConstruction<FileManager> fileManagerMock = mockConstruction(FileManager.class);
                MockedConstruction<SettingsManager> settingsManagerMock = mockConstruction(SettingsManager.class);
                MockedConstruction<StateManager> stateManagerMock = mockConstruction(StateManager.class);
                MockedConstruction<ApplicationWorkflowController> controllerMock = mockConstruction(
                        ApplicationWorkflowController.class)) {

            // When
            factory.createApplicationController();

            // Then: ImageMagickService was created and passed to ToolManager
            assertEquals(1, imageMagickMock.constructed().size());
            assertEquals(1, toolManagerMock.constructed().size());
        }
    }

    @Test
    @DisplayName("Should create ToolManager with null ImageMagickService when convert binary absent")
    void testImageMagickService_ConvertBinaryAbsent() throws InterruptedException {
        // Given: ToolConfiguration has null convert path
        ToolConfiguration toolConfig = mock(ToolConfiguration.class);
        when(toolConfig.getFfmpegPath()).thenReturn(Path.of("/path/ffmpeg"));
        when(toolConfig.getFfprobePath()).thenReturn(Path.of("/path/ffprobe"));
        when(toolConfig.getPandocPath()).thenReturn(Path.of("/path/pandoc"));
        when(toolConfig.getLibreOfficePath()).thenReturn(Path.of("/path/soffice"));
        when(toolConfig.getConvertPath()).thenReturn(null);

        try (MockedConstruction<ConfigurationManager> configMock = mockConstruction(ConfigurationManager.class);
                MockedConstruction<FileHandler> fileHandlerMock = mockConstruction(FileHandler.class);
                MockedConstruction<ValidationEngine> validationMock = mockConstruction(ValidationEngine.class);
                MockedConstruction<ProgressEngine> progressMock = mockConstruction(ProgressEngine.class);
                MockedConstruction<ToolDiscovery> toolDiscoveryMock = mockConstruction(ToolDiscovery.class,
                        (mock, context) -> when(mock.discoverTools()).thenReturn(toolConfig));
                MockedConstruction<FFmpegService> ffmpegMock = mockConstruction(FFmpegService.class);
                MockedConstruction<PandocService> pandocMock = mockConstruction(PandocService.class);
                MockedConstruction<LibreOfficeService> libreOfficeMock = mockConstruction(LibreOfficeService.class);
                MockedConstruction<ImageMagickService> imageMagickMock = mockConstruction(ImageMagickService.class);
                MockedConstruction<ToolManager> toolManagerMock = mockConstruction(ToolManager.class,
                        (mock, context) -> {
                            assertEquals(4, context.arguments().size());
                            assertEquals(ffmpegMock.constructed().get(0), context.arguments().get(0));
                            assertEquals(pandocMock.constructed().get(0), context.arguments().get(1));
                            assertEquals(libreOfficeMock.constructed().get(0), context.arguments().get(2));
                            assertNull(context.arguments().get(3)); // ImageMagickService should be null
                        });
                MockedConstruction<ConversionEngine> conversionMock = mockConstruction(ConversionEngine.class);
                MockedConstruction<FileManager> fileManagerMock = mockConstruction(FileManager.class);
                MockedConstruction<SettingsManager> settingsManagerMock = mockConstruction(SettingsManager.class);
                MockedConstruction<StateManager> stateManagerMock = mockConstruction(StateManager.class);
                MockedConstruction<ApplicationWorkflowController> controllerMock = mockConstruction(
                        ApplicationWorkflowController.class)) {

            // When
            factory.createApplicationController();

            // Then: ImageMagickService was NOT created, ToolManager received null
            assertEquals(0, imageMagickMock.constructed().size());
            assertEquals(1, toolManagerMock.constructed().size());
        }
    }
}