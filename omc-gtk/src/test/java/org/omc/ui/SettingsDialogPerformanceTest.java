package org.omc.ui;

import org.omc.model.FormatCategory;
import org.omc.ui.SettingsDialogJavaGi;
import org.omc.model.PresetsBySection;
import org.omc.model.ConversionSettings;
import org.omc.model.VideoSettings;
import org.omc.model.AudioSettings;
import org.omc.model.SectionPreset;
import org.omc.controller.SettingsManager;
import org.omc.core.ConfigurationManager;
import org.omc.core.ValidationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for SettingsManager preset persistence used by the
 * settings dialog performance path (preset caching).
 *
 * Requirements: REQ-5.2 (Performance optimizations)
 */
class SettingsDialogPerformanceTest {

    private Path testOutputDir;

    @TempDir
    Path tempDir;

    private SettingsManager settingsManager;
    private ConfigurationManager configurationManager;
    private ValidationEngine validationEngine;
    private org.omc.service.FileHandler fileHandler;

    private Path configDir;
    private Path dataDir;
    private Path cacheDir;

    @BeforeEach
    void setUp() throws Exception {
        // Create test data
        testOutputDir = Paths.get("target/test-output");
        Files.createDirectories(testOutputDir);

        // Create temporary directories for SettingsManager
        configDir = tempDir.resolve("config");
        dataDir = tempDir.resolve("data");
        cacheDir = tempDir.resolve("cache");
        Files.createDirectories(configDir);
        Files.createDirectories(dataDir);
        Files.createDirectories(cacheDir);

        // Initialize ConfigurationManager, ValidationEngine and SettingsManager
        configurationManager = new ConfigurationManager(configDir, dataDir, cacheDir);
        fileHandler = new org.omc.service.FileHandler(configurationManager);
        validationEngine = new ValidationEngine(fileHandler);
        settingsManager = new SettingsManager(configurationManager, validationEngine);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up temp directory
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }

    // ========== Integration Test ==========

    @Test
    void testSettingsManagerIntegrationWithCaching() throws IOException {
        // Create some presets and verify they can be loaded (simulating cache behavior)
        VideoSettings videoSettings = VideoSettings.builder()
                .codec("libx264")
                .bitrate(5000)
                .build();
        SectionPreset videoPreset = SectionPreset.forVideo("TestVideo", "Test video preset", videoSettings, false);

        AudioSettings audioSettings = AudioSettings.builder()
                .codec("aac")
                .bitrate(192)
                .build();
        SectionPreset audioPreset = SectionPreset.forAudio("TestAudio", "Test audio preset", audioSettings, false);

        // Save presets
        settingsManager.addSectionPreset(videoPreset);
        settingsManager.addSectionPreset(audioPreset);

        // Load presets (simulating what cache refresh would do)
        PresetsBySection loadedPresets = settingsManager.loadPresetsBySection();

        // Verify presets are loaded correctly
        assertFalse(loadedPresets.videoPresets().isEmpty(), "Video presets should be loaded");
        assertFalse(loadedPresets.audioPresets().isEmpty(), "Audio presets should be loaded");

        // Verify specific presets exist
        assertTrue(loadedPresets.videoPresets().stream().anyMatch(p -> p.name().equals("TestVideo")),
                "TestVideo preset should be loaded");
        assertTrue(loadedPresets.audioPresets().stream().anyMatch(p -> p.name().equals("TestAudio")),
                "TestAudio preset should be loaded");
    }
}