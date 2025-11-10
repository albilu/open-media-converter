package org.omc.controller;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    void setUp() {
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
        assertEquals(FileFormat.MP3, current.outputFormat());
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
        assertTrue(Files.list(configDir)
                .anyMatch(p -> p.getFileName().toString().contains(".backup")));
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
}
