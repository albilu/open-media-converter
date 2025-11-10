// filepath: src/test/java/org/omc/core/StatePersistenceIntegrationTest.java

package org.omc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omc.controller.SettingsManager;
import org.omc.controller.StateManager;
import org.omc.exception.InvalidSettingsException;
import org.omc.model.ApplicationState;
import org.omc.model.AudioSettings;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionSettings;
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;
import org.omc.model.ImageSettings;
import org.omc.model.Resolution;
import org.omc.model.SessionState;
import org.omc.model.SettingsPreset;
import org.omc.model.VideoSettings;
import org.omc.model.WindowState;

/**
 * Integration tests for state and settings persistence.
 * Tests the complete workflow of saving and restoring application state,
 * window state, session state, settings, and presets across sessions.
 * 
 * Requirements: REQ-005.1, REQ-005.2, REQ-005.3
 */
class StatePersistenceIntegrationTest {

        @TempDir
        Path tempDir;

        private ConfigurationManager configurationManager;
        private ValidationEngine validationEngine;
        private StateManager stateManager;
        private SettingsManager settingsManager;

        private Path configDir;
        private Path dataDir;
        private Path cacheDir;

        @BeforeEach
        void setUp() throws IOException {
                // Create temporary directory structure
                configDir = tempDir.resolve("config");
                dataDir = tempDir.resolve("data");
                cacheDir = tempDir.resolve("cache");

                Files.createDirectories(configDir);
                Files.createDirectories(dataDir);
                Files.createDirectories(cacheDir);

                // Initialize components
                configurationManager = new ConfigurationManager(configDir, dataDir, cacheDir);
                org.omc.service.FileHandler fileHandler = new org.omc.service.FileHandler(
                                configurationManager);
                validationEngine = new ValidationEngine(fileHandler);
                stateManager = new StateManager(configurationManager);
                settingsManager = new SettingsManager(configurationManager, validationEngine);
        }

        @AfterEach
        void tearDown() {
                // Cleanup handled by @TempDir
        }

        // ========================================================================
        // 1. Window State Persistence Tests
        // ========================================================================

        @Test
        void testWindowStatePersistence() throws IOException {
                // Given: Window state with custom geometry
                WindowState originalWindow = new WindowState(
                                1920, 1080, // size
                                100, 50, // position
                                false, // maximized
                                false // fullscreen
                );

                SessionState session = SessionState.empty();
                ApplicationState state = ApplicationState.create(
                                originalWindow,
                                session,
                                null, // conversionSettings
                                "1.0.0");

                // When: Save and reload state
                stateManager.saveState(state);
                ApplicationState loadedState = stateManager.loadState();

                // Then: Window state should be preserved
                assertNotNull(loadedState);
                WindowState loadedWindow = loadedState.windowState();
                assertEquals(1920, loadedWindow.width());
                assertEquals(1080, loadedWindow.height());
                assertEquals(100, loadedWindow.x());
                assertEquals(50, loadedWindow.y());
                assertFalse(loadedWindow.maximized());
                assertFalse(loadedWindow.fullscreen());
        }

        @Test
        void testMaximizedWindowStatePersistence() throws IOException {
                // Given: Maximized window state
                WindowState originalWindow = new WindowState(
                                1920, 1080,
                                0, 0,
                                true, // maximized
                                false);

                SessionState session = SessionState.empty();
                ApplicationState state = ApplicationState.create(originalWindow, session, null, "1.0.0");

                // When: Save and reload
                stateManager.saveState(state);
                ApplicationState loadedState = stateManager.loadState();

                // Then: Maximized flag should be preserved
                assertTrue(loadedState.windowState().maximized());
                assertFalse(loadedState.windowState().fullscreen());
        }

        @Test
        void testFullscreenWindowStatePersistence() throws IOException {
                // Given: Fullscreen window state
                WindowState originalWindow = new WindowState(
                                1920, 1080,
                                0, 0,
                                false,
                                true // fullscreen
                );

                SessionState session = SessionState.empty();
                ApplicationState state = ApplicationState.create(originalWindow, session, null, "1.0.0");

                // When: Save and reload
                stateManager.saveState(state);
                ApplicationState loadedState = stateManager.loadState();

                // Then: Fullscreen flag should be preserved
                assertFalse(loadedState.windowState().maximized());
                assertTrue(loadedState.windowState().fullscreen());
        }

        // ========================================================================
        // 2. Session State Persistence Tests
        // ========================================================================

        @Test
        void testSessionStatePersistence() throws IOException {
                // Given: Session state with recent files and directories
                Path file1 = tempDir.resolve("video1.mp4");
                Path file2 = tempDir.resolve("audio1.mp3");
                Path outputDir = tempDir.resolve("output");
                Files.createFile(file1);
                Files.createFile(file2);
                Files.createDirectories(outputDir);

                SessionState originalSession = new SessionState(
                                List.of(file1, file2), // recentFilePaths
                                tempDir, // lastInputDirectory
                                outputDir, // lastOutputDirectory
                                List.of(), // pendingFiles (empty)
                                null // lastUsedPreset
                );

                WindowState window = WindowState.defaultState();
                ApplicationState state = ApplicationState.create(window, originalSession, null, "1.0.0");

                // When: Save and reload state
                stateManager.saveState(state);
                ApplicationState loadedState = stateManager.loadState();

                // Then: Session state should be preserved
                SessionState loadedSession = loadedState.sessionState();
                assertEquals(2, loadedSession.recentFilePaths().size());
                assertTrue(loadedSession.recentFilePaths().contains(file1));
                assertTrue(loadedSession.recentFilePaths().contains(file2));
                assertEquals(tempDir, loadedSession.lastInputDirectory());
                assertEquals(outputDir, loadedSession.lastOutputDirectory());
        }

        @Test
        void testPendingConversionsPersistence() throws IOException {
                // Given: Session with pending conversions (files must exist for validation)
                Path file1 = tempDir.resolve("file1.mp4");
                Path file2 = tempDir.resolve("file2.mp4");

                // IMPORTANT: Create files BEFORE creating ConversionFile objects
                // because StateManager.loadState() calls validated() which filters non-existent
                // files
                Files.writeString(file1, "fake video content");
                Files.writeString(file2, "fake video content");

                ConversionFile convFile1 = ConversionFile.create(file1, FileFormat.MP4, Files.size(file1));
                ConversionFile convFile2 = ConversionFile.create(file2, FileFormat.MP4, Files.size(file2));

                SessionState originalSession = new SessionState(
                                List.of(),
                                tempDir,
                                tempDir,
                                List.of(convFile1, convFile2), // pending files
                                null);

                WindowState window = WindowState.defaultState();
                ApplicationState state = ApplicationState.create(window, originalSession, null, "1.0.0");

                // When: Save and reload
                stateManager.saveState(state);
                ApplicationState loadedState = stateManager.loadState();

                // Then: Pending conversions should be preserved (validated() removes
                // non-existent files)
                SessionState loadedSession = loadedState.sessionState();
                assertEquals(2, loadedSession.pendingFiles().size(),
                                "Pending files should persist if they exist on disk during reload");
        }

        // ========================================================================
        // 3. Settings Persistence Tests
        // ========================================================================

        @Test
        void testConversionSettingsPersistence() throws IOException, InvalidSettingsException {
                // Given: Custom conversion settings
                Path outputDir = tempDir.resolve("custom-output");
                Files.createDirectories(outputDir);

                VideoSettings videoSettings = VideoSettings.builder()
                                .codec("h265")
                                .bitrate(8000)
                                .resolution(new Resolution(1920, 1080))
                                .outputFormat(FileFormat.MP4)
                                .build();

                AudioSettings audioSettings = AudioSettings.builder()
                                .codec("opus")
                                .bitrate(192)
                                .outputFormat(FileFormat.MP3)
                                .build();

                ConversionSettings originalSettings = ConversionSettings.builder()
                                .outputDirectory(outputDir)
                                .overwriteExisting(true)
                                .parallelConversions(8)
                                .videoSettings(videoSettings)
                                .audioSettings(audioSettings)
                                .build();

                // When: Save and reload settings
                settingsManager.saveSettings(originalSettings);
                ConversionSettings loadedSettings = settingsManager.loadSettings();

                // Then: Settings should be preserved
                assertEquals(outputDir, loadedSettings.outputDirectory());
                assertTrue(loadedSettings.overwriteExisting());
                assertEquals(8, loadedSettings.parallelConversions());

                // Verify video settings
                VideoSettings loadedVideo = loadedSettings.videoSettings();
                assertEquals("h265", loadedVideo.codec());
                assertEquals(8000, loadedVideo.bitrate());
                assertEquals(1920, loadedVideo.resolution().getWidth());
                assertEquals(1080, loadedVideo.resolution().getHeight());

                // Verify audio settings
                AudioSettings loadedAudio = loadedSettings.audioSettings();
                assertEquals("opus", loadedAudio.codec());
                assertEquals(192, loadedAudio.bitrate());
        }

        @Test
        void testImageAndDocumentSettingsPersistence() throws IOException, InvalidSettingsException {
                // Given: Settings with image and document configurations
                Path outputDir = tempDir.resolve("output");
                Files.createDirectories(outputDir);

                ImageSettings imageSettings = ImageSettings.builder()
                                .quality(95)
                                .outputFormat(FileFormat.PNG)
                                .build();

                DocumentSettings documentSettings = DocumentSettings.builder()
                                .outputFormat(FileFormat.PDF)
                                .build();

                ConversionSettings originalSettings = ConversionSettings.builder()
                                .outputDirectory(outputDir)
                                .imageSettings(imageSettings)
                                .documentSettings(documentSettings)
                                .build();

                // When: Save and reload
                settingsManager.saveSettings(originalSettings);
                ConversionSettings loadedSettings = settingsManager.loadSettings();

                // Then: Image settings preserved
                ImageSettings loadedImage = loadedSettings.imageSettings();
                assertEquals(95, loadedImage.quality());

                // Then: Document settings preserved
                DocumentSettings loadedDocument = loadedSettings.documentSettings();
                assertEquals(FileFormat.PDF, loadedDocument.outputFormat());
        }

        // ========================================================================
        // 4. Preset Management Persistence Tests
        // ========================================================================

        @Test
        void testPresetPersistence() throws IOException, InvalidSettingsException {
                // Given: Custom preset
                Path outputDir = tempDir.resolve("output");
                Files.createDirectories(outputDir);

                VideoSettings videoSettings = VideoSettings.builder()
                                .codec("h264")
                                .bitrate(5000)
                                .resolution(new Resolution(1280, 720))
                                .outputFormat(FileFormat.MP4)
                                .build();

                ConversionSettings presetSettings = ConversionSettings.builder()
                                .outputDirectory(outputDir)
                                .videoSettings(videoSettings)
                                .build();

                SettingsPreset preset = SettingsPreset.createUserPreset(
                                "My Custom Preset",
                                "Custom HD preset for YouTube",
                                presetSettings);

                // When: Save preset
                settingsManager.savePreset(preset);
                List<SettingsPreset> presets = settingsManager.getPresets();

                // Then: Preset should be retrievable
                assertTrue(presets.stream().anyMatch(p -> p.name().equals("My Custom Preset")));

                // When: Load preset by name
                SettingsPreset loadedPreset = presets.stream()
                                .filter(p -> p.name().equals("My Custom Preset"))
                                .findFirst()
                                .orElse(null);

                // Then: Preset settings should match
                assertNotNull(loadedPreset);
                assertEquals("My Custom Preset", loadedPreset.name());
                assertEquals("Custom HD preset for YouTube", loadedPreset.description());
                assertEquals("h264", loadedPreset.settings().videoSettings().codec());
                assertEquals(5000, loadedPreset.settings().videoSettings().bitrate());
        }

        @Test
        void testMultiplePresetsPersistence() throws IOException, InvalidSettingsException {
                // Given: Multiple presets with valid settings (must have at least one section
                // setting)
                Path outputDir = tempDir.resolve("output");
                Files.createDirectories(outputDir);

                VideoSettings videoSettings = VideoSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .build();

                SettingsPreset preset1 = SettingsPreset.createUserPreset(
                                "Custom HD Quality",
                                "Best quality",
                                ConversionSettings.builder()
                                                .outputDirectory(outputDir)
                                                .videoSettings(videoSettings)
                                                .build());

                SettingsPreset preset2 = SettingsPreset.createUserPreset(
                                "Custom Web Settings",
                                "Fast loading",
                                ConversionSettings.builder()
                                                .outputDirectory(outputDir)
                                                .videoSettings(videoSettings)
                                                .build());

                SettingsPreset preset3 = SettingsPreset.createUserPreset(
                                "Custom Mobile Settings",
                                "Small file size",
                                ConversionSettings.builder()
                                                .outputDirectory(outputDir)
                                                .videoSettings(videoSettings)
                                                .build());

                // When: Save all presets
                settingsManager.savePreset(preset1);
                settingsManager.savePreset(preset2);
                settingsManager.savePreset(preset3);

                // Then: All presets should be retrievable
                List<SettingsPreset> presets = settingsManager.getPresets();
                assertTrue(presets.size() >= 3); // May include built-in presets
                assertTrue(presets.stream().anyMatch(p -> p.name().equals("Custom HD Quality")));
                assertTrue(presets.stream().anyMatch(p -> p.name().equals("Custom Web Settings")));
                assertTrue(presets.stream().anyMatch(p -> p.name().equals("Custom Mobile Settings")));
        }

        @Test
        void testPresetDeletionPersistence() throws IOException, InvalidSettingsException {
                // Given: Saved preset with valid settings
                Path outputDir = tempDir.resolve("output");
                Files.createDirectories(outputDir);

                VideoSettings videoSettings = VideoSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .build();

                SettingsPreset preset = SettingsPreset.createUserPreset(
                                "To Delete",
                                "This will be deleted",
                                ConversionSettings.builder()
                                                .outputDirectory(outputDir)
                                                .videoSettings(videoSettings)
                                                .build());

                settingsManager.savePreset(preset);
                assertTrue(settingsManager.getPresets().stream()
                                .anyMatch(p -> p.name().equals("To Delete")));

                // When: Delete preset
                settingsManager.deletePreset("To Delete");

                // Then: Preset should be gone
                List<SettingsPreset> presets = settingsManager.getPresets();
                assertFalse(presets.stream().anyMatch(p -> p.name().equals("To Delete")));
        }

        // ========================================================================
        // 5. Cross-Session State Restoration Tests
        // ========================================================================

        @Test
        void testCompleteSessionRestoration() throws IOException, InvalidSettingsException {
                // Given: Complete application state
                Path file1 = tempDir.resolve("video.mp4");
                Path outputDir = tempDir.resolve("output");
                Files.writeString(file1, "fake video"); // Create with content
                Files.createDirectories(outputDir);

                // Set up window state
                WindowState window = new WindowState(1600, 900, 50, 50, false, false);

                // Set up session state
                SessionState session = new SessionState(
                                List.of(file1),
                                tempDir,
                                outputDir,
                                List.of(),
                                null);

                // Set up settings with valid section settings (required for isValid())
                VideoSettings videoSettings = VideoSettings.builder()
                                .outputFormat(FileFormat.MP4)
                                .build();

                ConversionSettings settings = ConversionSettings.builder()
                                .outputDirectory(outputDir)
                                .parallelConversions(6)
                                .videoSettings(videoSettings)
                                .build();

                // When: Save everything
                ApplicationState state = ApplicationState.create(window, session, null, "1.0.0");
                stateManager.saveState(state);
                settingsManager.saveSettings(settings);

                // Simulate application restart - create new managers
                StateManager newStateManager = new StateManager(configurationManager);
                SettingsManager newSettingsManager = new SettingsManager(configurationManager, validationEngine);

                // When: Reload everything
                ApplicationState loadedState = newStateManager.loadState();
                ConversionSettings loadedSettings = newSettingsManager.loadSettings();

                // Then: Everything should be restored
                // Window state
                assertEquals(1600, loadedState.windowState().width());
                assertEquals(900, loadedState.windowState().height());

                // Session state
                assertEquals(1, loadedState.sessionState().recentFilePaths().size());
                assertEquals(file1, loadedState.sessionState().recentFilePaths().get(0));
                assertEquals(tempDir, loadedState.sessionState().lastInputDirectory());
                assertEquals(outputDir, loadedState.sessionState().lastOutputDirectory());

                // Settings
                assertEquals(outputDir, loadedSettings.outputDirectory());
                assertEquals(6, loadedSettings.parallelConversions());
        }

        @Test
        void testStatePersistenceWithNonExistentFiles() throws IOException {
                // Given: Session state with non-existent files
                Path nonExistentFile = tempDir.resolve("does-not-exist.mp4");

                SessionState session = new SessionState(
                                List.of(nonExistentFile),
                                tempDir,
                                tempDir,
                                List.of(),
                                null);

                WindowState window = WindowState.defaultState();
                ApplicationState state = ApplicationState.create(window, session, null, "1.0.0");

                // When: Save and reload
                stateManager.saveState(state);
                ApplicationState loadedState = stateManager.loadState();

                // Then: State should load but with cleaned file list (validated removes
                // non-existent files)
                assertNotNull(loadedState);
                assertNotNull(loadedState.sessionState());
                // The validated() method in ApplicationState removes non-existent files
                assertEquals(0, loadedState.sessionState().recentFilePaths().size());
        }

        // ========================================================================
        // 6. State Migration and Versioning Tests
        // ========================================================================

        @Test
        void testStateVersionPersistence() throws IOException {
                // Given: State with version
                ApplicationState state = ApplicationState.create(
                                WindowState.defaultState(),
                                SessionState.empty(),
                                null,
                                "1.0.0");

                // When: Save and reload
                stateManager.saveState(state);
                ApplicationState loadedState = stateManager.loadState();

                // Then: Version should be preserved
                assertEquals("1.0.0", loadedState.version());
        }

        @Test
        void testCorruptedStateRecovery() throws IOException {
                // Given: Corrupted state file
                Path statePath = configDir.resolve("state.json");
                Files.createDirectories(statePath.getParent());
                Files.writeString(statePath, "{invalid json content");

                // When: Load state
                ApplicationState loadedState = stateManager.loadState();

                // Then: Should return default state (corruption handled gracefully)
                assertNotNull(loadedState);
                assertEquals(WindowState.defaultState().width(), loadedState.windowState().width());
                assertEquals(SessionState.empty().recentFilePaths().size(),
                                loadedState.sessionState().recentFilePaths().size());
        }

        @Test
        void testCorruptedSettingsRecovery() throws IOException {
                // Given: Corrupted settings file
                Path settingsPath = configDir.resolve("settings.json");
                Files.createDirectories(settingsPath.getParent());
                Files.writeString(settingsPath, "not valid json at all");

                // When: Load settings
                ConversionSettings loadedSettings = settingsManager.loadSettings();

                // Then: Should return default settings
                assertNotNull(loadedSettings);
                assertNotNull(loadedSettings.outputDirectory());
                assertEquals(4, loadedSettings.parallelConversions());
        }
}
