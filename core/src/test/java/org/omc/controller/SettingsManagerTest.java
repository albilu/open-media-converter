package org.omc.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.omc.model.ImageSettings;
import org.omc.model.DocumentSettings;
import org.omc.model.ConversionSettings;
import org.omc.model.VideoSettings;
import org.omc.model.FileFormat;
import org.omc.model.FormatCategory;
import org.omc.model.PresetsBySection;
import org.omc.controller.SettingsManager;
import org.omc.model.AudioSettings;
import org.omc.model.SectionPreset;
import org.omc.model.SettingsPreset;
import org.omc.core.ConfigurationManager;
import org.omc.core.ValidationEngine;
import org.omc.exception.InvalidSettingsException;
import org.omc.service.FileHandler;
import org.omc.util.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for SettingsManager.
 * Tests settings persistence, validation, defaults, and corruption handling.
 * 
 * Requirements: REQ-003.1, REQ-005.3
 */
class SettingsManagerTest {

    @TempDir
    Path tempDir;

    private ConfigurationManager configurationManager;
    private ValidationEngine validationEngine;
    private FileHandler fileHandler;
    private SettingsManager settingsManager;

    private Path configDir;
    private Path dataDir;
    private Path cacheDir;

    @BeforeEach
    void setUp() throws Exception {
        // Create temporary directories for testing
        configDir = tempDir.resolve("config");
        dataDir = tempDir.resolve("data");
        cacheDir = tempDir.resolve("cache");

        try {
            Files.createDirectories(configDir);
            Files.createDirectories(dataDir);
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            fail("Failed to create test directories: " + e.getMessage());
        }

        // Create instances
        configurationManager = new ConfigurationManager(configDir, dataDir, cacheDir);
        fileHandler = new FileHandler(configurationManager);
        validationEngine = new ValidationEngine(fileHandler);
        settingsManager = new SettingsManager(configurationManager, validationEngine);
    }

    @AfterEach
    void tearDown() {
        // Cleanup is handled by @TempDir
    }

    @Test
    void testConstructorWithNullConfigurationManager() {
        assertThrows(NullPointerException.class, () -> {
            new SettingsManager(null, validationEngine);
        });
    }

    @Test
    void testConstructorWithNullValidationEngine() {
        assertThrows(NullPointerException.class, () -> {
            new SettingsManager(configurationManager, null);
        });
    }

    @Test
    void testLoadSettingsWhenFileDoesNotExist() {
        // When: Load settings when file doesn't exist
        ConversionSettings settings = settingsManager.loadSettings();

        // Then: Should return default settings
        assertNotNull(settings);
        assertNotNull(settings.outputDirectory());
        assertEquals(4, settings.parallelConversions());
        assertFalse(settings.overwriteExisting());
        assertFalse(settings.createSubdirectory());
    }

    @Test
    void testSaveAndLoadSettings() throws InvalidSettingsException, IOException {
        // Given: Valid settings
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .outputDirectory(outputDir)
                .overwriteExisting(true)
                .createSubdirectory(true)
                .parallelConversions(8)
                .build();

        // When: Save settings
        settingsManager.saveSettings(settings);

        // Then: Settings file should exist
        assertTrue(Files.exists(configurationManager.getSettingsFilePath()));

        // When: Load settings
        ConversionSettings loaded = settingsManager.loadSettings();

        // Then: Loaded settings should match saved settings
        assertNotNull(loaded);
        assertEquals(FileFormat.MP4, loaded.outputFormat());
        assertEquals(outputDir, loaded.outputDirectory());
        assertTrue(loaded.overwriteExisting());
        assertTrue(loaded.createSubdirectory());
        assertEquals(8, loaded.parallelConversions());
    }

    @Test
    void testSaveSettingsWithInvalidSettings() {
        // Given: Invalid settings (parallel conversions out of range)
        ConversionSettings invalidSettings = ConversionSettings.builder()
                .outputDirectory(tempDir)
                .parallelConversions(100) // Too high
                .build();

        // When/Then: Should throw InvalidSettingsException
        assertThrows(InvalidSettingsException.class, () -> {
            settingsManager.saveSettings(invalidSettings);
        });
    }

    @Test
    void testSaveSettingsWithNullSettings() {
        // When/Then: Should throw NullPointerException
        assertThrows(NullPointerException.class, () -> {
            settingsManager.saveSettings(null);
        });
    }

    @Test
    void testGetCurrentSettingsWhenNotLoaded() {
        // When: Get current settings without loading first
        ConversionSettings settings = settingsManager.getCurrentSettings();

        // Then: Should automatically load and return settings
        assertNotNull(settings);
        assertNotNull(settings.outputDirectory());
    }

    @Test
    void testGetCurrentSettingsAfterLoad() throws InvalidSettingsException, IOException {
        // Given: Save settings first
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        ConversionSettings savedSettings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP3)
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .build();

        settingsManager.saveSettings(savedSettings);
        settingsManager.loadSettings();

        // When: Get current settings
        ConversionSettings current = settingsManager.getCurrentSettings();

        // Then: Should return loaded settings
        assertNotNull(current);
        assertEquals(FileFormat.MP3, current.outputFormat(org.omc.model.FormatCategory.AUDIO));
        assertEquals(2, current.parallelConversions());
    }

    @Test
    void testUpdateSettings() throws InvalidSettingsException, IOException {
        // Given: Initial settings
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        ConversionSettings initialSettings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .outputDirectory(outputDir)
                .parallelConversions(4)
                .build();

        settingsManager.saveSettings(initialSettings);

        // When: Update settings
        ConversionSettings updatedSettings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .outputDirectory(outputDir)
                .parallelConversions(6)
                .overwriteExisting(true)
                .build();

        settingsManager.updateSettings(updatedSettings);

        // Then: Current settings should be updated
        ConversionSettings current = settingsManager.getCurrentSettings();
        assertEquals(6, current.parallelConversions());
        assertTrue(current.overwriteExisting());

        // And: Settings should be persisted
        ConversionSettings reloaded = settingsManager.loadSettings();
        assertEquals(6, reloaded.parallelConversions());
        assertTrue(reloaded.overwriteExisting());
    }

    @Test
    void testUpdateSettingsWithInvalidSettings() {
        // Given: Invalid settings
        ConversionSettings invalidSettings = ConversionSettings.builder()
                .outputDirectory(tempDir)
                .parallelConversions(0) // Too low
                .build();

        // When/Then: Should throw InvalidSettingsException
        assertThrows(InvalidSettingsException.class, () -> {
            settingsManager.updateSettings(invalidSettings);
        });
    }

    @Test
    void testResetToDefaults() throws IOException {
        // Given: Custom settings exist
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        ConversionSettings customSettings = ConversionSettings.builder()
                .outputFormat(FileFormat.AVI)
                .outputDirectory(outputDir)
                .parallelConversions(12)
                .overwriteExisting(true)
                .build();

        try {
            settingsManager.saveSettings(customSettings);
        } catch (InvalidSettingsException e) {
            fail("Failed to save custom settings: " + e.getMessage());
        }

        // When: Reset to defaults
        settingsManager.resetToDefaults();

        // Then: Current settings should be defaults
        ConversionSettings current = settingsManager.getCurrentSettings();
        assertNotNull(current);
        assertEquals(4, current.parallelConversions());
        assertFalse(current.overwriteExisting());

        // And: Settings file should contain defaults
        ConversionSettings reloaded = settingsManager.loadSettings();
        assertEquals(4, reloaded.parallelConversions());
        assertFalse(reloaded.overwriteExisting());
    }

    @Test
    void testCreateDefaultSettings() {
        // When: Create default settings
        ConversionSettings defaults = SettingsManager.createDefaultSettings();

        // Then: Should have sensible defaults
        assertNotNull(defaults);
        assertNotNull(defaults.outputDirectory());
        assertEquals(4, defaults.parallelConversions());
        assertFalse(defaults.overwriteExisting());
        assertFalse(defaults.createSubdirectory());
        assertEquals(FileFormat.MP4, defaults.outputFormat()); // Default to MP4
    }

    @Test
    void testLoadSettingsWithCorruptedFile() throws IOException {
        // Given: Corrupted settings file
        Path settingsPath = configurationManager.getSettingsFilePath();
        Files.writeString(settingsPath, "{ invalid json }", StandardOpenOption.CREATE);

        // When: Load settings
        ConversionSettings settings = settingsManager.loadSettings();

        // Then: Should return default settings
        assertNotNull(settings);
        assertEquals(4, settings.parallelConversions());

        // And: Corrupted file should be backed up
        try (var files = Files.list(configDir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().contains(".backup")));
        }
    }

    @Test
    void testLoadSettingsWithEmptyFile() throws IOException {
        // Given: Empty settings file
        Path settingsPath = configurationManager.getSettingsFilePath();
        Files.writeString(settingsPath, "", StandardOpenOption.CREATE);

        // When: Load settings
        ConversionSettings settings = settingsManager.loadSettings();

        // Then: Should return default settings
        assertNotNull(settings);
        assertEquals(4, settings.parallelConversions());
    }

    @Test
    void testLoadSettingsWithInvalidValues() throws IOException {
        // Given: Settings file with invalid values
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        ConversionSettings invalidSettings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(100) // Invalid
                .build();

        // Write directly to file, bypassing validation
        Path settingsPath = configurationManager.getSettingsFilePath();
        JsonUtils.writeJsonFile(invalidSettings, settingsPath.toFile());

        // When: Load settings
        ConversionSettings settings = settingsManager.loadSettings();

        // Then: Should return default settings (validation fails)
        assertNotNull(settings);
        assertEquals(4, settings.parallelConversions()); // Default value
    }

    @Test
    void testAtomicWrite() throws InvalidSettingsException, IOException {
        // Given: Valid settings
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .outputDirectory(outputDir)
                .parallelConversions(4)
                .build();

        // When: Save settings
        settingsManager.saveSettings(settings);

        // Then: Temporary file should not exist
        Path settingsPath = configurationManager.getSettingsFilePath();
        Path tempPath = Path.of(settingsPath.toString() + ".tmp");
        assertFalse(Files.exists(tempPath));

        // And: Final file should exist
        assertTrue(Files.exists(settingsPath));
    }

    @Test
    void testSettingsFileExists() throws InvalidSettingsException, IOException {
        // When: No settings file exists
        assertFalse(settingsManager.settingsFileExists());

        // When: Save settings
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .outputDirectory(outputDir)
                .parallelConversions(4)
                .build();

        settingsManager.saveSettings(settings);

        // Then: Settings file should exist
        assertTrue(settingsManager.settingsFileExists());
    }

    @Test
    void testGetSettingsFilePath() {
        // When: Get settings file path
        Path path = settingsManager.getSettingsFilePath();

        // Then: Should return correct path
        assertNotNull(path);
        assertEquals(configurationManager.getSettingsFilePath(), path);
        assertTrue(path.toString().endsWith("settings.json"));
    }

    @Test
    void testConcurrentSaveOperations() throws Exception {
        // Given: Valid settings
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        // When: Save settings multiple times concurrently
        Thread[] threads = new Thread[5];
        for (int i = 0; i < threads.length; i++) {
            final int parallelCount = i + 2;
            threads[i] = new Thread(() -> {
                try {
                    ConversionSettings settings = ConversionSettings.builder()
                            .outputFormat(FileFormat.MP4)
                            .outputDirectory(outputDir)
                            .parallelConversions(parallelCount)
                            .build();
                    settingsManager.saveSettings(settings);
                } catch (Exception e) {
                    fail("Concurrent save failed: " + e.getMessage());
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then: Settings file should exist and be valid
        assertTrue(settingsManager.settingsFileExists());
        ConversionSettings loaded = settingsManager.loadSettings();
        assertNotNull(loaded);
        assertTrue(loaded.parallelConversions() >= 2 && loaded.parallelConversions() <= 6);
    }

    @Test
    void testLoadSettingsPreservesWarnings() throws InvalidSettingsException, IOException {
        // Given: Settings with low disk space warning
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        ConversionSettings settings = ConversionSettings.builder()
                .outputFormat(FileFormat.MP4)
                .outputDirectory(outputDir)
                .parallelConversions(4)
                .build();

        settingsManager.saveSettings(settings);

        // When: Load settings
        ConversionSettings loaded = settingsManager.loadSettings();

        // Then: Should load successfully despite warnings
        assertNotNull(loaded);
        assertEquals(outputDir, loaded.outputDirectory());
    }

    // ========== Tests for Preset Methods (Task 37) ==========

    @Test
    void testLoadPresetsBySection_WithExistingFile() throws IOException {
        // Given: A presets file with valid PresetsBySection
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .codec("libx264")
                .bitrate(5000)
                .build();

        SectionPreset videoPreset = SectionPreset.forVideo(
                "High Quality 1080p",
                "1080p video with high bitrate",
                videoSettings,
                false);

        AudioSettings audioSettings = AudioSettings.builder()
                .outputFormat(FileFormat.MP3)
                .bitrate(320)
                .build();

        SectionPreset audioPreset = SectionPreset.forAudio(
                "High Quality Audio",
                "320kbps MP3",
                audioSettings,
                false);

        PresetsBySection presets = new PresetsBySection(
                List.of(videoPreset),
                List.of(audioPreset),
                List.of(),
                List.of());

        Path presetsPath = configurationManager.getPresetsFilePath();
        JsonUtils.writeJsonFile(presets, presetsPath.toFile());

        // When: Load presets
        PresetsBySection loaded = settingsManager.loadPresetsBySection();

        // Then: Should load correctly
        assertNotNull(loaded);
        assertEquals(1, loaded.videoPresets().size());
        assertEquals(1, loaded.audioPresets().size());
        assertEquals(0, loaded.imagePresets().size());
        assertEquals(0, loaded.documentPresets().size());

        SectionPreset loadedVideo = loaded.videoPresets().get(0);
        assertEquals("High Quality 1080p", loadedVideo.name());
        assertEquals(FormatCategory.VIDEO, loadedVideo.category());
    }

    @Test
    void testLoadPresetsBySection_WithMissingFile() {
        // When: Load presets when file doesn't exist
        PresetsBySection presets = settingsManager.loadPresetsBySection();

        // Then: Should return empty PresetsBySection
        assertNotNull(presets);
        assertEquals(0, presets.videoPresets().size());
        assertEquals(0, presets.audioPresets().size());
        assertEquals(0, presets.imagePresets().size());
        assertEquals(0, presets.documentPresets().size());
        assertEquals(0, presets.totalPresetCount());
    }

    @Test
    void testSavePresetsBySection_WritesCorrectJsonStructure() throws IOException {
        // Given: PresetsBySection with presets
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();

        SectionPreset preset = SectionPreset.forVideo(
                "Test Preset",
                "Test Description",
                videoSettings,
                false);

        PresetsBySection presets = new PresetsBySection(
                List.of(preset),
                List.of(),
                List.of(),
                List.of());

        // When: Add preset (which internally calls savePresetsBySection)
        settingsManager.addSectionPreset(preset);

        // Then: File should exist and be readable
        Path presetsPath = configurationManager.getPresetsFilePath();
        assertTrue(Files.exists(presetsPath));

        // Verify JSON structure by loading back
        PresetsBySection loaded = JsonUtils.readJsonFile(presetsPath.toFile(), PresetsBySection.class);
        assertNotNull(loaded);
        assertEquals(1, loaded.videoPresets().size());
        assertEquals("Test Preset", loaded.videoPresets().get(0).name());
    }

    @Test
    void testAddSectionPreset_PreventsDuplicates() throws IOException {
        // Given: A preset already exists
        VideoSettings settings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();

        SectionPreset preset1 = SectionPreset.forVideo(
                "Test Preset",
                "First description",
                settings,
                false);

        // When: Add preset first time
        settingsManager.addSectionPreset(preset1);

        // Then: Should be added
        PresetsBySection presets = settingsManager.loadPresetsBySection();
        assertEquals(1, presets.videoPresets().size());

        // When: Try to add preset with same name (duplicate)
        SectionPreset preset2 = SectionPreset.forVideo(
                "Test Preset",
                "Second description (duplicate name)",
                settings,
                false);

        // Then: Should throw IllegalArgumentException
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> settingsManager.addSectionPreset(preset2));

        assertTrue(exception.getMessage().contains("already exists"));

        // Verify original preset still exists and wasn't modified
        presets = settingsManager.loadPresetsBySection();
        assertEquals(1, presets.videoPresets().size());
        assertEquals("First description", presets.videoPresets().get(0).description());
    }

    @Test
    void testAddSectionPreset_AddsToCorrectCategory() throws IOException {
        // Given: Presets for different categories
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();

        AudioSettings audioSettings = AudioSettings.builder()
                .outputFormat(FileFormat.MP3)
                .build();

        ImageSettings imageSettings = ImageSettings.builder()
                .outputFormat(FileFormat.PNG)
                .build();

        DocumentSettings documentSettings = DocumentSettings.builder()
                .outputFormat(FileFormat.PDF)
                .build();

        // When: Add presets to each category
        settingsManager.addSectionPreset(SectionPreset.forVideo("Video Preset", null, videoSettings, false));
        settingsManager.addSectionPreset(SectionPreset.forAudio("Audio Preset", null, audioSettings, false));
        settingsManager.addSectionPreset(SectionPreset.forImage("Image Preset", null, imageSettings, false));
        settingsManager.addSectionPreset(SectionPreset.forDocument("Doc Preset", null, documentSettings, false));

        // Then: Each category should have one preset
        PresetsBySection presets = settingsManager.loadPresetsBySection();
        assertEquals(1, presets.videoPresets().size());
        assertEquals(1, presets.audioPresets().size());
        assertEquals(1, presets.imagePresets().size());
        assertEquals(1, presets.documentPresets().size());

        assertEquals("Video Preset", presets.videoPresets().get(0).name());
        assertEquals("Audio Preset", presets.audioPresets().get(0).name());
        assertEquals("Image Preset", presets.imagePresets().get(0).name());
        assertEquals("Doc Preset", presets.documentPresets().get(0).name());
    }

    @Test
    void testDeleteSectionPreset_RemovesCorrectPreset() throws IOException {
        // Given: Multiple presets in same category
        VideoSettings settings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();

        settingsManager.addSectionPreset(SectionPreset.forVideo("Preset A", null, settings, false));
        settingsManager.addSectionPreset(SectionPreset.forVideo("Preset B", null, settings, false));
        settingsManager.addSectionPreset(SectionPreset.forVideo("Preset C", null, settings, false));

        // Verify 3 presets exist
        PresetsBySection presets = settingsManager.loadPresetsBySection();
        assertEquals(3, presets.videoPresets().size());

        // When: Delete one preset
        settingsManager.deleteSectionPreset("Preset B", FormatCategory.VIDEO);

        // Then: Should have 2 presets, and "Preset B" should be gone
        presets = settingsManager.loadPresetsBySection();
        assertEquals(2, presets.videoPresets().size());
        assertTrue(presets.videoPresets().stream().anyMatch(p -> p.name().equals("Preset A")));
        assertFalse(presets.videoPresets().stream().anyMatch(p -> p.name().equals("Preset B")));
        assertTrue(presets.videoPresets().stream().anyMatch(p -> p.name().equals("Preset C")));
    }

    @Test
    void testDeleteSectionPreset_NonExistentPreset() throws IOException {
        // Given: Some presets exist
        VideoSettings settings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();

        settingsManager.addSectionPreset(SectionPreset.forVideo("Preset A", null, settings, false));

        // When: Try to delete non-existent preset
        settingsManager.deleteSectionPreset("Non-Existent", FormatCategory.VIDEO);

        // Then: Should not throw exception, existing presets should remain
        PresetsBySection presets = settingsManager.loadPresetsBySection();
        assertEquals(1, presets.videoPresets().size());
        assertEquals("Preset A", presets.videoPresets().get(0).name());
    }

    @Test
    void testDeleteSectionPreset_OnlyAffectsCorrectCategory() throws IOException {
        // Given: Presets in multiple categories
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();

        AudioSettings audioSettings = AudioSettings.builder()
                .outputFormat(FileFormat.MP3)
                .build();

        settingsManager.addSectionPreset(SectionPreset.forVideo("Test Preset", null, videoSettings, false));
        settingsManager.addSectionPreset(SectionPreset.forAudio("Test Preset", null, audioSettings, false));

        // When: Delete from video category only
        settingsManager.deleteSectionPreset("Test Preset", FormatCategory.VIDEO);

        // Then: Video preset should be deleted, audio preset should remain
        PresetsBySection presets = settingsManager.loadPresetsBySection();
        assertEquals(0, presets.videoPresets().size());
        assertEquals(1, presets.audioPresets().size());
        assertEquals("Test Preset", presets.audioPresets().get(0).name());
    }

    // ========== Preset read-merge-write cycle concurrency tests ==========

    @Test
    void testAddSectionPreset_ConcurrentAdds_PersistAllPresetsWithoutLoss() throws Exception {
        // Given: Concurrent starters all adding distinct presets
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();

        int threadCount = 8;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        Thread[] threads = new Thread[threadCount];
        List<Exception> failures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final String name = "Concurrent Preset " + i;
            threads[i] = new Thread(() -> {
                try {
                    barrier.await();
                    settingsManager.addSectionPreset(
                            SectionPreset.forVideo(name, null, videoSettings, false));
                } catch (Exception e) {
                    failures.add(e);
                }
            });
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        // Then: Every add must be persisted - a lost update means the
        // load-modify-save cycle was not guarded
        assertTrue(failures.isEmpty(), () -> "Concurrent adds must not fail: " + failures);
        PresetsBySection presets = settingsManager.loadPresetsBySection();
        assertEquals(threadCount, presets.videoPresets().size(),
                "every concurrent add must be persisted (no lost updates)");
    }

    @Test
    void testDeleteSectionPreset_ConcurrentDeletes_RemoveAllPresetsWithoutLoss() throws Exception {
        // Given: Eight section presets to remove concurrently
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();

        int threadCount = 8;
        for (int i = 0; i < threadCount; i++) {
            settingsManager.addSectionPreset(
                    SectionPreset.forVideo("Doomed Preset " + i, null, videoSettings, false));
        }

        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        Thread[] threads = new Thread[threadCount];
        List<Exception> failures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final String name = "Doomed Preset " + i;
            threads[i] = new Thread(() -> {
                try {
                    barrier.await();
                    settingsManager.deleteSectionPreset(name, FormatCategory.VIDEO);
                } catch (Exception e) {
                    failures.add(e);
                }
            });
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        // Then: Every delete must be applied - a resurrected preset means the
        // load-modify-save cycle was not guarded
        assertTrue(failures.isEmpty(), () -> "Concurrent deletes must not fail: " + failures);
        PresetsBySection presets = settingsManager.loadPresetsBySection();
        assertEquals(0, presets.videoPresets().size(),
                "every concurrent delete must be applied (no lost updates)");
    }

    @Test
    void testDeletePreset_ConcurrentDeletes_RemoveAllPresetsWithoutLoss() throws Exception {
        // Given: Eight custom classic presets to remove concurrently
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        int threadCount = 8;
        for (int i = 0; i < threadCount; i++) {
            settingsManager.savePreset(createUserPresetNamed("Doomed Classic " + i, outputDir));
        }
        assertEquals(threadCount, settingsManager.getPresets().stream()
                .filter(p -> !p.builtIn()).count(), "seeding failed");

        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        Thread[] threads = new Thread[threadCount];
        List<Exception> failures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final String name = "Doomed Classic " + i;
            threads[i] = new Thread(() -> {
                try {
                    barrier.await();
                    settingsManager.deletePreset(name);
                } catch (Exception e) {
                    failures.add(e);
                }
            });
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        // Then: Every delete must be applied - a resurrected preset means the
        // load-modify-save cycle was not guarded
        assertTrue(failures.isEmpty(), () -> "Concurrent deletes must not fail: " + failures);
        assertEquals(0, settingsManager.getPresets().stream()
                        .filter(p -> !p.builtIn()).count(),
                "every concurrent delete must be applied (no lost updates)");
    }

    @Test
    void testReplacePresetsForCategory_ReplacesCorrectCategory() {
        // Given: Initial presets
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();

        AudioSettings audioSettings = AudioSettings.builder()
                .outputFormat(FileFormat.MP3)
                .build();

        SectionPreset videoPreset1 = SectionPreset.forVideo("Video 1", null, videoSettings, false);
        SectionPreset videoPreset2 = SectionPreset.forVideo("Video 2", null, videoSettings, false);
        SectionPreset audioPreset = SectionPreset.forAudio("Audio 1", null, audioSettings, false);

        PresetsBySection original = new PresetsBySection(
                List.of(videoPreset1),
                List.of(audioPreset),
                List.of(),
                List.of());

        // When: Replace video presets using reflection (since it's private)
        // We'll test this indirectly through addSectionPreset

        // Create new video preset list
        List<SectionPreset> newVideoPresets = List.of(videoPreset2);

        // Use withVideoPresets to create updated instance
        PresetsBySection updated = original.withVideoPresets(newVideoPresets);

        // Then: Video presets should be replaced, audio should remain
        assertEquals(1, updated.videoPresets().size());
        assertEquals("Video 2", updated.videoPresets().get(0).name());
        assertEquals(1, updated.audioPresets().size());
        assertEquals("Audio 1", updated.audioPresets().get(0).name());
    }

    // ========== Legacy preset format data-loss regression tests ==========

    @Test
    void testLoadPresetsBySection_MigratesLegacyPresetContainer() throws IOException {
        // Given: Legacy {"presets":[...]} container file written by the old savePreset() API
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path presetsPath = configurationManager.getPresetsFilePath();
        JsonUtils.writeJsonFile(
                Map.of("presets", List.of(createLegacyPreset(outputDir))),
                presetsPath.toFile());

        // When: Load presets (triggers migration)
        PresetsBySection loaded = settingsManager.loadPresetsBySection();

        // Then: Legacy preset is migrated into the video section, not dropped
        assertEquals(1, loaded.videoPresets().size());
        assertEquals("Legacy Video", loaded.videoPresets().get(0).name());

        // And: File is rewritten in new format so subsequent loads work
        PresetsBySection reread = JsonUtils.readJsonFile(presetsPath.toFile(), PresetsBySection.class);
        assertEquals(1, reread.videoPresets().size());
        assertEquals("Legacy Video", reread.videoPresets().get(0).name());
    }

    @Test
    void testLoadPresetsBySection_LegacyPresetContainer_BackedUpWithOriginalContent() throws IOException {
        // Given: Legacy {"presets":[...]} container file
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path presetsPath = configurationManager.getPresetsFilePath();
        JsonUtils.writeJsonFile(
                Map.of("presets", List.of(createLegacyPreset(outputDir))),
                presetsPath.toFile());
        String originalContent = Files.readString(presetsPath);

        // When: Load presets (triggers migration)
        settingsManager.loadPresetsBySection();

        // Then: A timestamped backup of the original content exists before any overwrite
        List<Path> backups;
        try (var files = Files.list(configDir)) {
            backups = files
                    .filter(p -> p.getFileName().toString().startsWith("presets.json.old."))
                    .filter(p -> p.getFileName().toString().endsWith(".bak"))
                    .toList();
        }
        assertEquals(1, backups.size());
        assertEquals(originalContent, Files.readString(backups.get(0)));
    }

    @Test
    void testLoadPresetsBySection_CorruptFile_ReturnsEmptyWithBackup() throws IOException {
        // Given: A truly corrupt presets file
        Path presetsPath = configurationManager.getPresetsFilePath();
        Files.writeString(presetsPath, "{ not valid json");

        // When: Load presets
        PresetsBySection loaded = settingsManager.loadPresetsBySection();

        // Then: Falls back to empty presets
        assertEquals(0, loaded.totalPresetCount());

        // And: The unreadable content is backed up before any destructive write
        try (var files = Files.list(configDir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("presets.json.old.")
                    && p.getFileName().toString().endsWith(".bak")),
                    "Corrupt presets file should be backed up");
        }
    }

    @Test
    void testOldPresetApi_OnNewFormatFile_DoesNotWipeSectionPresets() throws IOException {
        // Given: A new-format file with a section preset
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();
        SectionPreset sectionPreset = SectionPreset.forVideo("Section Video", null, videoSettings, false);
        Path presetsPath = configurationManager.getPresetsFilePath();
        JsonUtils.writeJsonFile(
                new PresetsBySection(List.of(sectionPreset), List.of(), List.of(), List.of()),
                presetsPath.toFile());

        // When: Old API reads the new-format file
        List<SettingsPreset> customPresets = assertDoesNotThrow(() -> settingsManager.getPresets())
                .stream().filter(p -> !p.builtIn()).toList();

        // Then: No custom presets reported, no exception thrown
        assertEquals(0, customPresets.size());

        // When: Old API saves a preset
        settingsManager.savePreset(createLegacyPreset(outputDir));

        // Then: Existing section presets must survive the save
        PresetsBySection after = settingsManager.loadPresetsBySection();
        assertTrue(after.videoPresets().stream().anyMatch(p -> p.name().equals("Section Video")),
                "savePreset must not wipe existing section presets");
    }

    @Test
    void testLoadPresetsBySection_MigratesBareArrayFormat() throws IOException {
        // Given: Ancient bare-array [...] presets file
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path presetsPath = configurationManager.getPresetsFilePath();
        JsonUtils.writeJsonFile(List.of(createLegacyPreset(outputDir)), presetsPath.toFile());

        // When: Load presets (triggers migration)
        PresetsBySection loaded = settingsManager.loadPresetsBySection();

        // Then: Bare-array preset is migrated into the video section
        assertEquals(1, loaded.videoPresets().size());
        assertEquals("Legacy Video", loaded.videoPresets().get(0).name());
    }

    @Test
    void testLoadPresetsBySection_MigratesLegacyGlobalOutputFormatPreset() throws IOException {
        // Given: Genuinely-old preset file whose settings only carry the global
        // outputFormat field written by versions before section-based settings
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path presetsPath = configurationManager.getPresetsFilePath();
        String legacyJson = """
                {
                  "presets": [
                    {
                      "name": "Legacy MP4",
                      "description": "Old global outputFormat preset",
                      "settings": {
                        "outputDirectory": "%s",
                        "overwriteExisting": false,
                        "createSubdirectory": false,
                        "parallelConversions": 4,
                        "outputFormat": "MP4"
                      },
                      "builtIn": false,
                      "createdAt": 1700000000000
                    }
                  ]
                }
                """.formatted(outputDir);
        Files.writeString(presetsPath, legacyJson);

        // When: Load presets (triggers migration)
        PresetsBySection loaded = settingsManager.loadPresetsBySection();

        // Then: The preset survives with video settings mapped from the global format
        assertEquals(1, loaded.videoPresets().size());
        SectionPreset migrated = loaded.videoPresets().get(0);
        assertEquals("Legacy MP4", migrated.name());
        assertNotNull(migrated.videoSettings(), "video settings must be mapped from global outputFormat");
        assertEquals(FileFormat.MP4, migrated.videoSettings().outputFormat());
    }

    @Test
    void testSavePreset_BackupFailure_DoesNotOverwriteUnparseablePresetsFile() throws IOException {
        // Given: An unparseable presets file that also cannot be backed up
        // (read permission removed so Files.copy fails on the source)
        Path presetsPath = configurationManager.getPresetsFilePath();
        Files.writeString(presetsPath, "{ not valid json");

        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        assumeTrue(presetsPath.toFile().setReadable(false),
                "POSIX permissions required for this test");

        try {
            // When: Saving a preset would back up then overwrite the file
            IOException error = assertThrows(IOException.class,
                    () -> settingsManager.savePreset(createLegacyPreset(outputDir)),
                    "savePreset must fail closed when backup fails");

            // Then: The original content must not be destroyed
            assertTrue(presetsPath.toFile().setReadable(true), "restore read permission");
            assertEquals("{ not valid json", Files.readString(presetsPath));
        } finally {
            presetsPath.toFile().setReadable(true);
        }
    }

    @Test
    void testMigration_SameSecondBackups_DoNotOverwriteEachOther() throws IOException {
        // Given: A corrupt presets file that triggers two migration attempts
        // within the same second (parse always fails, file is never rewritten)
        Path presetsPath = configurationManager.getPresetsFilePath();
        Files.writeString(presetsPath, "{ not valid json");

        // When: Two migration attempts run back to back
        settingsManager.loadPresetsBySection();
        settingsManager.loadPresetsBySection();

        // Then: Both backups must survive (distinct names, not overwriting)
        List<Path> backups;
        try (var files = Files.list(configDir)) {
            backups = files
                    .filter(p -> p.getFileName().toString().startsWith("presets.json.old."))
                    .filter(p -> p.getFileName().toString().endsWith(".bak"))
                    .toList();
        }
        assertEquals(2, backups.size(), "same-second backups must not overwrite each other");
    }

    @Test
    void testLoadPresetsBySection_CurrentFormatWithUnknownField_LoadsAndKeepsKnownValues() throws IOException {
        // Given: A current-format file carrying extra unknown fields, as written
        // by a newer application version (forward compatibility)
        Path presetsPath = configurationManager.getPresetsFilePath();
        String currentWithExtra = """
                {
                  "videoPresets": [
                    {
                      "name": "Future Preset",
                      "description": "Current schema plus unknown fields",
                      "category": "VIDEO",
                      "videoSettings": {"outputFormat": "MP4", "codec": "libx264", "bitrate": 5000},
                      "builtIn": false,
                      "createdAt": 1700000000000,
                      "presetColor": "blue"
                    }
                  ],
                  "audioPresets": [],
                  "imagePresets": [],
                  "documentPresets": [],
                  "schemaVersion": 2
                }
                """;
        Files.writeString(presetsPath, currentWithExtra);

        // When: Load presets
        PresetsBySection loaded = settingsManager.loadPresetsBySection();

        // Then: File loads leniently; known values are kept, unknown fields ignored
        assertEquals(1, loaded.videoPresets().size());
        assertEquals("Future Preset", loaded.videoPresets().get(0).name());
        assertNotNull(loaded.videoPresets().get(0).videoSettings());
        assertEquals(FileFormat.MP4, loaded.videoPresets().get(0).videoSettings().outputFormat());

        // And: No backup or migration was triggered for a current-shape file
        try (var files = Files.list(configDir)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith("presets.json.old.")),
                    "Current-shape file with unknown fields must load directly, not migrate");
        }
    }

    @Test
    void testLoadPresetsBySection_AmbiguousFile_TakesSafeMigrationPath() throws IOException {
        // Given: A parseable file whose shape matches neither the current
        // sectioned schema nor the known legacy schemas
        Path presetsPath = configurationManager.getPresetsFilePath();
        String ambiguous = "{\"somethingUnknown\": 42}";
        Files.writeString(presetsPath, ambiguous);

        // When: Load presets
        PresetsBySection loaded = settingsManager.loadPresetsBySection();

        // Then: Empty result via the migration path
        assertEquals(0, loaded.totalPresetCount());

        // And: A backup of the original content exists (data safety over
        // silent accept for ambiguous shapes)
        List<Path> backups;
        try (var files = Files.list(configDir)) {
            backups = files
                    .filter(p -> p.getFileName().toString().startsWith("presets.json.old."))
                    .filter(p -> p.getFileName().toString().endsWith(".bak"))
                    .toList();
        }
        assertEquals(1, backups.size(), "Ambiguous file must go through backup+migration, not silent accept");
        assertEquals(ambiguous, Files.readString(backups.get(0)));
    }

    @Test
    void deletePreset_OnFileWithSectionPresets_PreservesSectionPresets() throws IOException {
        // Given: A mixed presets file with a legacy "presets" array AND section presets
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();
        SectionPreset sectionPreset = SectionPreset.forVideo("Section Video", null, videoSettings, false);

        Path presetsPath = configurationManager.getPresetsFilePath();
        JsonUtils.writeJsonFile(
                Map.of(
                        "presets", List.of(createLegacyPreset(outputDir)),
                        "videoPresets", List.of(sectionPreset),
                        "audioPresets", List.of(),
                        "imagePresets", List.of(),
                        "documentPresets", List.of()),
                presetsPath.toFile());

        // When: Delete the legacy preset via the old API
        settingsManager.deletePreset("Legacy Video");

        // Then: The deleted preset is gone
        assertTrue(settingsManager.getPresets().stream()
                .filter(p -> !p.builtIn())
                .noneMatch(p -> p.name().equals("Legacy Video")));

        // And: Coexisting section presets must survive the delete
        PresetsBySection after = settingsManager.loadPresetsBySection();
        assertTrue(after.videoPresets().stream().anyMatch(p -> p.name().equals("Section Video")),
                "deletePreset must not wipe coexisting section presets");
    }

    @Test
    void addSectionPreset_OnFileWithLegacyPresets_PreservesLegacyPresets() throws IOException {
        // Given: A legacy {"presets":[A]} presets file written by the old API
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path presetsPath = configurationManager.getPresetsFilePath();
        JsonUtils.writeJsonFile(
                Map.of("presets", List.of(createLegacyPreset(outputDir))),
                presetsPath.toFile());

        // When: Add a section preset via the new API
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();
        settingsManager.addSectionPreset(
                SectionPreset.forVideo("New Section Video", null, videoSettings, false));

        // Then: The new section preset is stored
        PresetsBySection sections = settingsManager.loadPresetsBySection();
        assertTrue(sections.videoPresets().stream()
                .anyMatch(p -> p.name().equals("New Section Video")));

        // And: The coexisting legacy preset survives the save - migration
        // consumes the legacy "presets" key (so repeated loads cannot
        // re-migrate it), but the preset itself is preserved as a section
        // preset
        assertTrue(sections.videoPresets().stream()
                .anyMatch(p -> p.name().equals("Legacy Video")),
                "the coexisting legacy preset must survive the save as a migrated section preset");
    }

    // ========== Mixed legacy/section presets regression tests ==========

    @Test
    void MixedFile_RepeatedLoads_DoNotAccumulateDuplicates() throws IOException {
        // Given: A new-format file with a section preset
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();
        settingsManager.addSectionPreset(
                SectionPreset.forVideo("Section Video", null, videoSettings, false));

        // And: The old API saves a legacy preset into the same file (mixed shape,
        // the reviewer's repro for how mixed files arise in the wild)
        settingsManager.savePreset(createLegacyPreset(outputDir));

        Path presetsPath = configurationManager.getPresetsFilePath();
        String originalContent = Files.readString(presetsPath);

        JsonNode mixedRoot = JsonUtils.getObjectMapper().readTree(presetsPath.toFile());
        assertTrue(mixedRoot.has("presets"), "sanity: file must carry the legacy key");
        assertTrue(mixedRoot.has("videoPresets"), "sanity: file must carry section keys");

        // When: Load three times
        PresetsBySection first = settingsManager.loadPresetsBySection();
        PresetsBySection second = settingsManager.loadPresetsBySection();
        PresetsBySection third = settingsManager.loadPresetsBySection();

        // Then: Section preset count is stable - no duplicate legacy appends
        assertEquals(2, first.videoPresets().size());
        assertEquals(2, second.videoPresets().size(),
                "second load must not re-migrate and append another copy");
        assertEquals(2, third.videoPresets().size(),
                "third load must not re-migrate and append another copy");
        assertEquals(1, third.videoPresets().stream()
                .filter(p -> p.name().equals("Legacy Video")).count());

        // And: The file is no longer legacy-shaped - migration consumed the
        // legacy "presets" key, so subsequent loads take the normal path
        JsonNode afterRoot = JsonUtils.getObjectMapper().readTree(presetsPath.toFile());
        assertFalse(afterRoot.has("presets"),
                "migration save must consume the legacy 'presets' key");
        assertTrue(afterRoot.has("videoPresets"));

        // And: Exactly one timestamped backup exists, retaining the original
        // mixed content (no per-load backup litter)
        List<Path> backups;
        try (var files = Files.list(configDir)) {
            backups = files
                    .filter(p -> p.getFileName().toString().startsWith("presets.json.old."))
                    .filter(p -> p.getFileName().toString().endsWith(".bak"))
                    .toList();
        }
        assertEquals(1, backups.size(), "only the first load may migrate");
        assertEquals(originalContent, Files.readString(backups.get(0)));
    }

    @Test
    void Migration_SkipsDuplicatesAlreadyInSection() throws IOException {
        // Given: A mixed file whose legacy preset name already exists in the
        // matching section (e.g. left behind by earlier duplicate appends)
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();
        SectionPreset existing = SectionPreset.forVideo("Legacy Video", null, videoSettings, false);

        Path presetsPath = configurationManager.getPresetsFilePath();
        JsonUtils.writeJsonFile(
                Map.of(
                        "presets", List.of(createLegacyPreset(outputDir)),
                        "videoPresets", List.of(existing),
                        "audioPresets", List.of(),
                        "imagePresets", List.of(),
                        "documentPresets", List.of()),
                presetsPath.toFile());

        // When: Load (triggers migration)
        PresetsBySection loaded = settingsManager.loadPresetsBySection();

        // Then: No duplicate appended - the section keeps exactly one preset
        assertEquals(1, loaded.videoPresets().size(),
                "migration must skip a legacy preset whose name already exists in the section");
        assertEquals("Legacy Video", loaded.videoPresets().get(0).name());
    }

    @Test
    void LegacyPresetsArray_NullElements_Ignored() throws IOException {
        // Given: A legacy presets array containing a null entry
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        Path presetsPath = configurationManager.getPresetsFilePath();

        // When/Then: loadPresetsBySection does not NPE; the valid preset migrates
        JsonUtils.writeJsonFile(
                Map.of("presets", Arrays.asList(null, createLegacyPreset(outputDir))),
                presetsPath.toFile());
        PresetsBySection loaded = assertDoesNotThrow(() -> settingsManager.loadPresetsBySection());
        assertEquals(1, loaded.videoPresets().size(), "null entries must be ignored");
        assertEquals("Legacy Video", loaded.videoPresets().get(0).name());

        // And: deletePreset on a file with null entries does not NPE
        JsonUtils.writeJsonFile(
                Map.of("presets", Arrays.asList(null, createLegacyPreset(outputDir))),
                presetsPath.toFile());
        assertDoesNotThrow(() -> settingsManager.deletePreset("Legacy Video"));
        assertTrue(settingsManager.getPresets().stream()
                .filter(p -> !p.builtIn())
                .noneMatch(p -> p.name().equals("Legacy Video")));

        // And: savePreset on a file with null entries does not NPE
        JsonUtils.writeJsonFile(
                Map.of("presets", Arrays.asList(null, createLegacyPreset(outputDir))),
                presetsPath.toFile());
        assertDoesNotThrow(() -> settingsManager.savePreset(createLegacyPreset(outputDir)));
    }

    private SettingsPreset createLegacyPreset(Path outputDir) {
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .codec("libx264")
                .build();
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(videoSettings)
                .build();
        return SettingsPreset.createUserPreset("Legacy Video", "Old flat-format preset", settings);
    }

    private SettingsPreset createUserPresetNamed(String name, Path outputDir) {
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .codec("libx264")
                .build();
        ConversionSettings settings = ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(videoSettings)
                .build();
        return SettingsPreset.createUserPreset(name, "Concurrent delete test preset", settings);
    }
}
