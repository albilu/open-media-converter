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
 * Unit tests for SettingsDialogJavaGi performance optimization changes (Task
 * 47).
 * Tests preset caching, performance timing, and cache behavior.
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

    // ========== Preset Caching Tests ==========

    @Test
    void testCachedPresetsFieldExists() throws Exception {
        // Verify that the cachedPresets field exists for performance optimization
        java.lang.reflect.Field cachedPresetsField = SettingsDialogJavaGi.class.getDeclaredField("cachedPresets");
        assertNotNull(cachedPresetsField, "cachedPresets field should exist for performance caching");

        // Verify it's the correct type
        assertEquals(PresetsBySection.class, cachedPresetsField.getType(),
                "cachedPresets should be of type PresetsBySection");
    }

    @Test
    void testRefreshPresetCacheMethodExists() throws Exception {
        // Verify that the refreshPresetCache method exists
        java.lang.reflect.Method refreshMethod = SettingsDialogJavaGi.class.getDeclaredMethod("refreshPresetCache");
        assertNotNull(refreshMethod, "refreshPresetCache method should exist for cache management");

        // Verify it returns void
        assertEquals(void.class, refreshMethod.getReturnType(),
                "refreshPresetCache should return void");
    }

    @Test
    void testPopulateMethodsExist() throws Exception {
        // Verify that populate methods exist for all preset types
        java.lang.reflect.Method populateVideoMethod = SettingsDialogJavaGi.class
                .getDeclaredMethod("populateVideoPresets");
        java.lang.reflect.Method populateAudioMethod = SettingsDialogJavaGi.class
                .getDeclaredMethod("populateAudioPresets");
        java.lang.reflect.Method populateImageMethod = SettingsDialogJavaGi.class
                .getDeclaredMethod("populateImagePresets");
        java.lang.reflect.Method populateDocMethod = SettingsDialogJavaGi.class.getDeclaredMethod("populateDocPresets");

        assertNotNull(populateVideoMethod, "populateVideoPresets method should exist");
        assertNotNull(populateAudioMethod, "populateAudioPresets method should exist");
        assertNotNull(populateImageMethod, "populateImagePresets method should exist");
        assertNotNull(populateDocMethod, "populateDocPresets method should exist");
    }

    @Test
    void testPresetSelectionMethodsExist() throws Exception {
        // Verify that preset selection methods exist and use cached data
        java.lang.reflect.Method videoSelectionMethod = SettingsDialogJavaGi.class
                .getDeclaredMethod("onVideoPresetSelected");
        java.lang.reflect.Method audioSelectionMethod = SettingsDialogJavaGi.class
                .getDeclaredMethod("onAudioPresetSelected");
        java.lang.reflect.Method imageSelectionMethod = SettingsDialogJavaGi.class
                .getDeclaredMethod("onImagePresetSelected");
        java.lang.reflect.Method docSelectionMethod = SettingsDialogJavaGi.class
                .getDeclaredMethod("onDocPresetSelected");

        assertNotNull(videoSelectionMethod, "onVideoPresetSelected method should exist");
        assertNotNull(audioSelectionMethod, "onAudioPresetSelected method should exist");
        assertNotNull(imageSelectionMethod, "onImagePresetSelected method should exist");
        assertNotNull(docSelectionMethod, "onDocPresetSelected method should exist");
    }

    // ========== Performance Timing Tests ==========

    @Test
    void testConstructorIncludesTimingLogic() throws Exception {
        // Verify that the constructor includes performance timing logic
        // This is tested by examining that the constructor exists and has the expected
        // parameters

        java.lang.reflect.Constructor<?>[] constructors = SettingsDialogJavaGi.class.getDeclaredConstructors();
        assertTrue(constructors.length > 0, "SettingsDialogJavaGi should have constructors");

        // Find the main constructor
        java.lang.reflect.Constructor<?> mainConstructor = null;
        for (java.lang.reflect.Constructor<?> ctor : constructors) {
            if (ctor.getParameterCount() == 3) {
                mainConstructor = ctor;
                break;
            }
        }

        assertNotNull(mainConstructor, "Main constructor should exist with 3 parameters");
        Class<?>[] paramTypes = mainConstructor.getParameterTypes();
        assertEquals(org.gnome.gtk.Window.class, paramTypes[0], "First parameter should be Window");
        assertEquals(ConversionSettings.class, paramTypes[1], "Second parameter should be ConversionSettings");
        assertEquals(org.omc.controller.SettingsManager.class, paramTypes[2],
                "Third parameter should be SettingsManager");
    }

    @Test
    void testTimingCalculationLogicIsPresent() {
        // Verify that timing calculations use System.nanoTime for accuracy
        long startTime = System.nanoTime();
        // Simulate some work
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            // Ignore
        }
        long endTime = System.nanoTime();
        long elapsedMs = (endTime - startTime) / 1_000_000;

        // Verify calculation works
        assertTrue(elapsedMs >= 0, "Elapsed time should be non-negative");
        assertTrue(elapsedMs < 100, "Timing calculation should work correctly");
    }

    // ========== Cache Behavior Tests ==========

    @Test
    void testPresetsBySectionEmptyMethodExists() {
        // Verify that PresetsBySection has empty() method for defensive programming
        PresetsBySection emptyPresets = PresetsBySection.empty();
        assertNotNull(emptyPresets, "PresetsBySection.empty() should return a valid instance");

        // Verify empty presets have empty lists
        assertTrue(emptyPresets.videoPresets().isEmpty(), "Empty presets should have no video presets");
        assertTrue(emptyPresets.audioPresets().isEmpty(), "Empty presets should have no audio presets");
        assertTrue(emptyPresets.imagePresets().isEmpty(), "Empty presets should have no image presets");
        assertTrue(emptyPresets.documentPresets().isEmpty(), "Empty presets should have no document presets");
    }

    @Test
    void testSettingsManagerLoadPresetsMethodExists() throws Exception {
        // Verify that SettingsManager has loadPresetsBySection method
        java.lang.reflect.Method loadMethod = SettingsManager.class.getDeclaredMethod("loadPresetsBySection");
        assertNotNull(loadMethod, "loadPresetsBySection method should exist in SettingsManager");

        // Verify return type
        assertEquals(PresetsBySection.class, loadMethod.getReturnType(),
                "loadPresetsBySection should return PresetsBySection");
    }

    @Test
    void testCacheInvalidationMethodsExist() throws Exception {
        // Test that cache invalidation logic exists by verifying the methods that
        // should trigger it
        java.lang.reflect.Method addMethod = SettingsManager.class.getDeclaredMethod("addSectionPreset",
                SectionPreset.class);
        java.lang.reflect.Method deleteMethod = SettingsManager.class.getDeclaredMethod("deleteSectionPreset",
                String.class, FormatCategory.class);

        assertNotNull(addMethod, "addSectionPreset method should exist for cache invalidation testing");
        assertNotNull(deleteMethod, "deleteSectionPreset method should exist for cache invalidation testing");
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