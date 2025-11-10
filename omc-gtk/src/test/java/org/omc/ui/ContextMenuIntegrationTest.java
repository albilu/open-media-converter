package org.omc.ui;

import org.omc.model.Resolution;
import org.omc.model.ImageSettings;
import org.omc.model.VideoSettings;
import org.omc.model.FileFormat;
import org.omc.model.FormatCategory;
import org.omc.model.ConversionFile;
import org.omc.model.PresetsBySection;
import org.omc.model.AudioSettings;
import org.omc.model.SectionPreset;
import org.omc.controller.ApplicationWorkflowController;
import org.omc.controller.FileManager;
import org.omc.controller.SettingsManager;
import org.omc.controller.StateManager;
import org.omc.core.ConversionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for file list context menu preset application
 * functionality.
 * 
 * <p>
 * Requirements: REQ-3.3 - Context menu preset application
 * Task 42: Integration Tests for Context Menu
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ContextMenuIntegrationTest {

    @Mock
    private FileManager fileManager;

    @Mock
    private SettingsManager settingsManager;

    @Mock
    private StateManager stateManager;

    @Mock
    private ConversionEngine conversionEngine;

    private ApplicationWorkflowController controller;

    @BeforeEach
    void setUp() {
        controller = new ApplicationWorkflowController(fileManager, settingsManager, stateManager, conversionEngine);
    }

    /**
     * Test that right-click on single video file shows correct presets in submenu.
     * Requirement REQ-3.3: Apply preset to single file.
     */
    @Test
    void testSingleVideoFileShowsVideoPresets() {
        // Create video presets
        VideoSettings videoSettings1 = VideoSettings.builder()
                .codec("libx264")
                .bitrate(5000)
                .outputFormat(FileFormat.MP4)
                .build();
        SectionPreset videoPreset1 = SectionPreset.forVideo("HD Video", "1080p preset", videoSettings1, false);

        VideoSettings videoSettings2 = VideoSettings.builder()
                .codec("libx265")
                .bitrate(8000)
                .outputFormat(FileFormat.MKV)
                .build();
        SectionPreset videoPreset2 = SectionPreset.forVideo("4K Video", "4K preset", videoSettings2, false);

        // Create mock video file
        Path videoFile = Path.of("/test/test.mp4");
        ConversionFile file = ConversionFile.create(videoFile, FileFormat.MP4, 1024L);

        when(fileManager.getFile(file.id())).thenReturn(Optional.of(file));

        PresetsBySection presets = new PresetsBySection(
                List.of(videoPreset1, videoPreset2),
                List.of(),
                List.of(),
                List.of());
        when(settingsManager.loadPresetsBySection()).thenReturn(presets);

        // Get available presets for this file
        List<SectionPreset> availablePresets = controller.getAvailablePresetsForFiles(List.of(file.id()));

        // Verify correct presets returned
        assertNotNull(availablePresets);
        assertEquals(2, availablePresets.size());
        assertTrue(availablePresets.stream().anyMatch(p -> p.name().equals("HD Video")));
        assertTrue(availablePresets.stream().anyMatch(p -> p.name().equals("4K Video")));
    }

    /**
     * Test that submenu lists correct presets for audio file category.
     * Requirement REQ-3.3: Preset filtering by category.
     */
    @Test
    void testAudioFileShowsOnlyAudioPresets() {
        // Create presets for different categories
        VideoSettings videoSettings = VideoSettings.builder().codec("libx264").outputFormat(FileFormat.MP4).build();
        SectionPreset videoPreset = SectionPreset.forVideo("VideoPreset", null, videoSettings, false);

        AudioSettings audioSettings = AudioSettings.builder().codec("aac").outputFormat(FileFormat.MP3).build();
        SectionPreset audioPreset = SectionPreset.forAudio("AudioPreset", null, audioSettings, false);

        // Create mock audio file
        Path audioFile = Path.of("/test/test.mp3");
        ConversionFile file = ConversionFile.create(audioFile, FileFormat.MP3, 512L);

        when(fileManager.getFile(file.id())).thenReturn(Optional.of(file));

        PresetsBySection presets = new PresetsBySection(
                List.of(videoPreset),
                List.of(audioPreset),
                List.of(),
                List.of());
        when(settingsManager.loadPresetsBySection()).thenReturn(presets);

        // Get available presets
        List<SectionPreset> availablePresets = controller.getAvailablePresetsForFiles(List.of(file.id()));

        // Verify only audio preset is returned
        assertNotNull(availablePresets);
        assertEquals(1, availablePresets.size());
        assertEquals("AudioPreset", availablePresets.get(0).name());
        assertEquals(FormatCategory.AUDIO, availablePresets.get(0).category());
    }

    /**
     * Test that mixed selection (video + audio files) shows error message.
     * Requirement REQ-3.3: Mixed selection handling.
     */
    @Test
    void testMixedSelectionReturnsEmptyList() {
        // Create mock video file
        Path videoFile = Path.of("/test/test.mp4");
        ConversionFile video = ConversionFile.create(videoFile, FileFormat.MP4, 1024L);

        // Create mock audio file
        Path audioFile = Path.of("/test/test.mp3");
        ConversionFile audio = ConversionFile.create(audioFile, FileFormat.MP3, 512L);

        when(fileManager.getFile(video.id())).thenReturn(Optional.of(video));
        when(fileManager.getFile(audio.id())).thenReturn(Optional.of(audio));

        // Try to get presets for mixed selection
        List<SectionPreset> availablePresets = controller.getAvailablePresetsForFiles(
                List.of(video.id(), audio.id()));

        // Verify empty list is returned for mixed selection
        assertNotNull(availablePresets);
        assertTrue(availablePresets.isEmpty(), "Mixed selection should return empty preset list");
    }

    /**
     * Test that applying preset updates file in FileManager with settings override.
     * Requirement REQ-3.3: Preset application updates file.
     */
    @Test
    void testApplyingPresetUpdatesFileWithOverride() {
        // Create and save a video preset
        VideoSettings videoSettings = VideoSettings.builder()
                .codec("libx265")
                .bitrate(8000)
                .resolution(new Resolution(1920, 1080))
                .outputFormat(FileFormat.MKV)
                .build();
        SectionPreset videoPreset = SectionPreset.forVideo("TestPreset", "Test preset", videoSettings, false);

        // Create mock video file
        Path videoFile = Path.of("/test/test.mp4");
        ConversionFile file = ConversionFile.create(videoFile, FileFormat.MP4, 1024L);

        when(fileManager.getFile(file.id())).thenReturn(Optional.of(file));

        // Verify file has no custom settings initially
        assertFalse(file.hasCustomSettings());

        // Apply preset
        controller.applyPresetToFiles(List.of(file.id()), videoPreset);

        // Capture the updated file
        ArgumentCaptor<ConversionFile> fileCaptor = ArgumentCaptor.forClass(ConversionFile.class);
        verify(fileManager).updateFile(fileCaptor.capture());
        ConversionFile updatedFile = fileCaptor.getValue();

        // Verify file now has custom settings
        assertTrue(updatedFile.hasCustomSettings(), "File should have custom settings after preset applied");
        assertNotNull(updatedFile.settingsOverride());
        assertEquals(FormatCategory.VIDEO, updatedFile.settingsOverride().getCategory());

        // Verify settings override contains correct values
        VideoSettings overrideSettings = updatedFile.settingsOverride().videoSettings();
        assertNotNull(overrideSettings);
        assertEquals("libx265", overrideSettings.codec());
        assertEquals(8000, overrideSettings.bitrate());
        assertEquals(FileFormat.MKV, overrideSettings.outputFormat());
    }

    /**
     * Test "Clear Custom Settings" removes override from file.
     * Requirement REQ-3.3: Clear custom settings functionality.
     */
    @Test
    void testClearCustomSettingsRemovesOverride() {
        // Create and save preset
        ImageSettings imageSettings = ImageSettings.builder()
                .quality(95)
                .outputFormat(FileFormat.WEBP)
                .build();
        SectionPreset imagePreset = SectionPreset.forImage("HighQualityImage", null, imageSettings, false);

        // Create mock image file
        Path imageFile = Path.of("/test/test.png");
        ConversionFile file = ConversionFile.create(imageFile, FileFormat.PNG, 2048L);

        when(fileManager.getFile(file.id())).thenReturn(Optional.of(file));

        // Apply preset
        controller.applyPresetToFiles(List.of(file.id()), imagePreset);

        // Capture the file with override
        ArgumentCaptor<ConversionFile> fileCaptor = ArgumentCaptor.forClass(ConversionFile.class);
        verify(fileManager, times(1)).updateFile(fileCaptor.capture());
        ConversionFile fileWithOverride = fileCaptor.getValue();
        assertTrue(fileWithOverride.hasCustomSettings());

        // Setup mock to return file with override
        when(fileManager.getFile(file.id())).thenReturn(Optional.of(fileWithOverride));

        // Clear custom settings
        controller.clearPresetFromFiles(List.of(file.id()));

        // Verify override was removed
        verify(fileManager, times(2)).updateFile(fileCaptor.capture());
        ConversionFile clearedFile = fileCaptor.getValue();
        assertFalse(clearedFile.hasCustomSettings(), "File should not have custom settings after clearing");
        assertNull(clearedFile.settingsOverride());
    }

    /**
     * Test that no presets available shows appropriate message.
     * Requirement REQ-3.3: Empty preset list handling.
     */
    @Test
    void testNoPresetsAvailableReturnsEmptyList() {
        // Create mock document file
        Path docFile = Path.of("/test/test.pdf");
        ConversionFile file = ConversionFile.create(docFile, FileFormat.PDF, 4096L);

        when(fileManager.getFile(file.id())).thenReturn(Optional.of(file));

        // No presets configured
        PresetsBySection emptyPresets = PresetsBySection.empty();
        when(settingsManager.loadPresetsBySection()).thenReturn(emptyPresets);

        // Get available presets
        List<SectionPreset> availablePresets = controller.getAvailablePresetsForFiles(List.of(file.id()));

        // Verify empty list returned
        assertNotNull(availablePresets);
        assertTrue(availablePresets.isEmpty(), "Should return empty list when no presets exist");
    }

    /**
     * Test applying preset to multiple files of same category.
     * Requirement REQ-3.3: Batch preset application.
     */
    @Test
    void testApplyPresetToMultipleFilesOfSameCategory() {
        // Create audio preset
        AudioSettings audioSettings = AudioSettings.builder()
                .codec("flac")
                .bitrate(320)
                .sampleRate(96000)
                .outputFormat(FileFormat.FLAC)
                .build();
        SectionPreset audioPreset = SectionPreset.forAudio("LosslessAudio", null, audioSettings, false);

        // Create multiple mock audio files
        Path audioFile1 = Path.of("/test/test1.mp3");
        Path audioFile2 = Path.of("/test/test2.mp3");
        Path audioFile3 = Path.of("/test/test3.mp3");

        ConversionFile file1 = ConversionFile.create(audioFile1, FileFormat.MP3, 512L);
        ConversionFile file2 = ConversionFile.create(audioFile2, FileFormat.MP3, 512L);
        ConversionFile file3 = ConversionFile.create(audioFile3, FileFormat.MP3, 512L);

        when(fileManager.getFile(file1.id())).thenReturn(Optional.of(file1));
        when(fileManager.getFile(file2.id())).thenReturn(Optional.of(file2));
        when(fileManager.getFile(file3.id())).thenReturn(Optional.of(file3));

        // Apply preset to all files
        controller.applyPresetToFiles(
                List.of(file1.id(), file2.id(), file3.id()),
                audioPreset);

        // Verify all files have custom settings
        ArgumentCaptor<ConversionFile> fileCaptor = ArgumentCaptor.forClass(ConversionFile.class);
        verify(fileManager, times(3)).updateFile(fileCaptor.capture());

        List<ConversionFile> updatedFiles = fileCaptor.getAllValues();
        assertEquals(3, updatedFiles.size());

        for (ConversionFile updatedFile : updatedFiles) {
            assertTrue(updatedFile.hasCustomSettings());
            assertEquals("flac", updatedFile.settingsOverride().audioSettings().codec());
        }
    }

    /**
     * Test that category mismatch between file and preset throws exception.
     * Requirement REQ-3.3: Category validation.
     */
    @Test
    void testCategoryMismatchThrowsException() {
        // Create video preset
        VideoSettings videoSettings = VideoSettings.builder()
                .codec("libx264")
                .outputFormat(FileFormat.MP4)
                .build();
        SectionPreset videoPreset = SectionPreset.forVideo("VideoPreset", null, videoSettings, false);

        // Create mock audio file (category mismatch)
        Path audioFile = Path.of("/test/test.mp3");
        ConversionFile audioFileObj = ConversionFile.create(audioFile, FileFormat.MP3, 512L);

        when(fileManager.getFile(audioFileObj.id())).thenReturn(Optional.of(audioFileObj));

        // Attempt to apply video preset to audio file
        assertThrows(IllegalArgumentException.class, () -> {
            controller.applyPresetToFiles(List.of(audioFileObj.id()), videoPreset);
        }, "Should throw exception when preset category doesn't match file category");
    }
}
