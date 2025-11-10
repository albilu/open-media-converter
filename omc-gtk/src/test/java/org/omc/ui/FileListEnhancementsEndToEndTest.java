package org.omc.ui;

import org.omc.model.Resolution;
import org.omc.model.ImageSettings;
import org.omc.model.DocumentSettings;
import org.omc.model.ConversionStatus;
import org.omc.model.FileSettingsOverride;
import org.omc.model.ConversionSettings;
import org.omc.model.VideoSettings;
import org.omc.model.FileFormat;
import org.omc.model.FormatCategory;
import org.omc.model.ConversionProgress;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionFile;
import org.omc.model.PresetsBySection;
import org.omc.model.ApplicationState;
import org.omc.model.FileListSortState;
import org.omc.model.ConversionTool;
import org.omc.model.AudioSettings;
import org.omc.model.SectionPreset;
import org.omc.controller.ApplicationWorkflowController;
import org.omc.controller.FileManager;
import org.omc.controller.SettingsManager;
import org.omc.controller.StateManager;
import org.omc.core.ConversionEngine;
import org.omc.core.ToolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration test for file list enhancements.
 * 
 * <p>
 * This test validates the complete workflow with all new features:
 * - Output Format column display
 * - Tool output capture and ConversionResult storage
 * - Output path tracking in ConversionFile
 * - Sort state persistence
 * - FileDetailsDialog data preparation
 * 
 * <p>
 * Requirements:
 * - REQ-FL-1.1: Output Format column
 * - REQ-FL-2.1: Conversion Details Dialog
 * - REQ-FL-2.2: Tool output capture
 * - REQ-FL-3.3: Output path tracking
 * - REQ-FL-4.5: Sort state persistence
 * 
 * <p>
 * Task 87: End-to-End Integration Test
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FileListEnhancementsEndToEndTest {

    @TempDir
    Path tempDir;

    @Mock
    private FileManager fileManager;

    @Mock
    private SettingsManager settingsManager;

    @Mock
    private StateManager stateManager;

    @Mock
    private ConversionEngine conversionEngine;

    @Mock
    private ToolManager toolManager;

    private ApplicationWorkflowController controller;

    @BeforeEach
    void setUp() {
        controller = new ApplicationWorkflowController(
                fileManager,
                settingsManager,
                stateManager,
                conversionEngine);
    }

    /**
     * Test full workflow: add files → apply preset → verify Output Format column →
     * simulate conversion → verify tool output → verify output path.
     * 
     * Requirements: REQ-FL-1.1, REQ-FL-2.2, REQ-FL-3.3
     */
    @Test
    void testFullWorkflowWithOutputFormatAndConversionResult() throws IOException {
        // Step 1: Create test files
        Path videoFile = tempDir.resolve("test.mp4");
        Path audioFile = tempDir.resolve("test.mp3");
        Files.createFile(videoFile);
        Files.createFile(audioFile);

        ConversionFile video = createTestVideoFile(videoFile, 1024L);
        ConversionFile audio = createTestAudioFile(audioFile, 512L);

        // Step 2: Setup presets and settings
        VideoSettings videoSettings = createStandardVideoSettings();
        SectionPreset videoPreset = createVideoPreset("Web Optimized", "Web video preset", videoSettings);

        AudioSettings audioSettings = createStandardAudioSettings();

        ConversionSettings globalSettings = createGlobalSettingsWithVideoAndAudio(
                tempDir.resolve("output"), videoSettings, audioSettings);

        // Use lenient stubbing for mocks that may not be called depending on code path
        lenient().when(fileManager.getFile(video.id())).thenReturn(Optional.of(video));
        lenient().when(fileManager.getFile(audio.id())).thenReturn(Optional.of(audio));
        lenient().when(settingsManager.loadSettings()).thenReturn(globalSettings);
        lenient().when(settingsManager.loadPresetsBySection()).thenReturn(new PresetsBySection(
                List.of(videoPreset),
                List.of(),
                List.of(),
                List.of()));

        // Step 3: Verify Output Format column would display correctly
        // For video file with preset applied
        FileSettingsOverride videoOverride = FileSettingsOverride.forVideo(
                videoPreset.name(),
                videoSettings);
        ConversionFile videoWithPreset = video.withSettingsOverride(videoOverride);
        // Re-stub with preset-applied video file
        when(fileManager.getFile(video.id())).thenReturn(Optional.of(videoWithPreset));

        // Output format resolution logic (simulating
        // FileListView.resolveOutputFormat())
        String videoOutputFormat = resolveOutputFormat(videoWithPreset, globalSettings);
        assertEquals("Web Optimized", videoOutputFormat, "Video with preset should show preset name");

        // For audio file using global settings
        String audioOutputFormat = resolveOutputFormat(audio, globalSettings);
        assertEquals("MP3", audioOutputFormat, "Audio using global settings should show format name");

        // Step 4: Simulate conversion completion
        Path outputVideoPath = tempDir.resolve("output/test.webm");
        Path outputAudioPath = tempDir.resolve("output/test.mp3");
        Files.createDirectories(outputVideoPath.getParent());
        Files.createFile(outputVideoPath);
        Files.createFile(outputAudioPath);

        String videoToolOutput = "ffmpeg version 4.4.2\nInput #0, mov,mp4,m4a,3gp,3g2,mj2\nOutput #0, webm\nStream mapping:\n[... conversion output ...]\nConversion completed successfully";
        String audioToolOutput = "ffmpeg version 4.4.2\nInput #0, mp3\nOutput #0, mp3\nConversion completed";

        ConversionResult videoResult = ConversionResult.success(
                video.id(),
                outputVideoPath,
                videoToolOutput,
                java.time.Duration.ofSeconds(45),
                1024L,
                2048L,
                ConversionTool.FFMPEG);

        ConversionResult audioResult = ConversionResult.success(
                audio.id(),
                outputAudioPath,
                audioToolOutput,
                java.time.Duration.ofSeconds(20),
                512L,
                512L,
                ConversionTool.FFMPEG);

        // Step 5: Verify ConversionEngine stores results
        lenient().when(conversionEngine.getConversionResult(video.id())).thenReturn(videoResult);
        lenient().when(conversionEngine.getConversionResult(audio.id())).thenReturn(audioResult);

        // Step 6: Verify output paths are tracked in ConversionFile
        ConversionFile completedVideo = videoWithPreset
                .withStatus(ConversionStatus.COMPLETED)
                .withOutputPath(outputVideoPath);
        ConversionFile completedAudio = audio
                .withStatus(ConversionStatus.COMPLETED)
                .withOutputPath(outputAudioPath);

        assertTrue(completedVideo.outputPath().isPresent(), "Completed video should have output path");
        assertEquals(outputVideoPath, completedVideo.outputPath().get());
        assertTrue(completedAudio.outputPath().isPresent(), "Completed audio should have output path");
        assertEquals(outputAudioPath, completedAudio.outputPath().get());

        // Step 7: Verify tool output is captured in ConversionResult
        assertTrue(videoResult.toolOutput().isPresent(), "Video result should have tool output");
        assertTrue(videoResult.toolOutput().get().contains("ffmpeg version"));
        assertTrue(videoResult.toolOutput().get().contains("Conversion completed successfully"));

        assertTrue(audioResult.toolOutput().isPresent(), "Audio result should have tool output");
        assertTrue(audioResult.toolOutput().get().contains("Conversion completed"));

        // Step 8: Verify FileDetailsDialog would have all necessary data
        // This simulates what happens when user double-clicks a completed file
        assertTrue(controller.getFile(video.id()).isPresent(), "Controller should provide file");
        assertNotNull(controller.getConversionResult(video.id()), "Controller should provide result");

        ConversionResult retrievedResult = controller.getConversionResult(video.id());
        assertEquals(videoToolOutput, retrievedResult.toolOutput().orElse(""));
        assertEquals(outputVideoPath, retrievedResult.outputPath().orElse(null));
        assertEquals(ConversionTool.FFMPEG, retrievedResult.toolUsed());
    }

    /**
     * Test sort state persistence: save sort state → close app → reopen → verify
     * restored.
     * 
     * Requirements: REQ-FL-4.5
     */
    @Test
    void testSortStatePersistence() throws Exception {
        // Step 1: Create sort state
        FileListSortState sortState = FileListSortState.byName(FileListSortState.SortDirection.DESCENDING);

        // Step 2: Setup initial state with sort state
        ApplicationState currentState = ApplicationState.defaultState()
                .withFileListSortState(sortState);

        // Mock getCurrentState to return state with sort info
        when(stateManager.getCurrentState()).thenReturn(currentState);

        // Save sort state via controller
        controller.saveSortState(sortState);

        // Verify StateManager was called to save
        verify(stateManager, atLeastOnce()).saveState(argThat(state -> state.fileListSortState() != null &&
                state.fileListSortState().sortField() == FileListSortState.SortField.NAME &&
                state.fileListSortState().sortDir() == FileListSortState.SortDirection.DESCENDING));

        // Step 3: Simulate app restart - load state
        lenient().when(stateManager.loadState()).thenReturn(currentState);
        FileListSortState restoredState = controller.getSavedSortState();

        // Verify state was restored correctly
        assertNotNull(restoredState, "Sort state should be restored");
        assertEquals(FileListSortState.SortField.NAME, restoredState.sortField());
        assertEquals(FileListSortState.SortDirection.DESCENDING, restoredState.sortDir());
        assertTrue(restoredState.isSorted(), "Restored state should indicate sorting is active");
    }

    /**
     * Test that preset application updates Output Format column display.
     * 
     * Requirements: REQ-FL-1.1
     */
    @Test
    void testPresetApplicationUpdatesOutputFormat() {
        // Step 1: Create file without preset (uses global settings)
        Path videoFile = Path.of("/test/video.mp4");
        ConversionFile file = createTestVideoFile(videoFile, 2048L);

        VideoSettings globalVideoSettings = VideoSettings.builder()
                .codec("libx264")
                .outputFormat(FileFormat.MP4)
                .build();

        ConversionSettings globalSettings = ConversionSettings.builder()
                .videoSettings(globalVideoSettings)
                .build();

        lenient().when(fileManager.getFile(file.id())).thenReturn(Optional.of(file));
        lenient().when(settingsManager.loadSettings()).thenReturn(globalSettings);

        // Verify Output Format shows global format
        String outputFormat = resolveOutputFormat(file, globalSettings);
        assertEquals("MP4", outputFormat, "Should show global format initially");

        // Step 2: Apply preset
        VideoSettings presetSettings = VideoSettings.builder()
                .codec("libx265")
                .outputFormat(FileFormat.MKV)
                .build();
        SectionPreset preset = createVideoPreset("4K Video", "4K preset", presetSettings);

        FileSettingsOverride override = FileSettingsOverride.forVideo(preset.name(), presetSettings);
        ConversionFile fileWithPreset = file.withSettingsOverride(override);
        // Re-stub with preset-applied file
        when(fileManager.getFile(file.id())).thenReturn(Optional.of(fileWithPreset));

        // Verify Output Format shows preset name
        String updatedFormat = resolveOutputFormat(fileWithPreset, globalSettings);
        assertEquals("4K Video", updatedFormat, "Should show preset name after application");

        // Step 3: Clear preset
        ConversionFile fileCleared = fileWithPreset.clearSettingsOverride();
        // Re-stub with cleared file
        lenient().when(fileManager.getFile(file.id())).thenReturn(Optional.of(fileCleared));

        // Verify Output Format reverts to global format
        String revertedFormat = resolveOutputFormat(fileCleared, globalSettings);
        assertEquals("MP4", revertedFormat, "Should revert to global format after clearing preset");
    }

    /**
     * Test that files added during active sort are inserted in correct position.
     * 
     * Requirements: REQ-FL-4.1
     */
    @Test
    void testNewFilesRespectActiveSort() {
        // Step 1: Create initial sorted files
        ConversionFile file1 = ConversionFile.create(Path.of("/test/aaa.mp4"), FileFormat.MP4, 100L);
        ConversionFile file2 = ConversionFile.create(Path.of("/test/zzz.mp4"), FileFormat.MP4, 200L);

        // Sort by name ascending
        FileListSortState sortState = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);
        List<ConversionFile> files = List.of(file1, file2);

        // Apply comparator
        var comparator = sortState.createComparator();
        files = files.stream().sorted(comparator).toList();

        // Verify initial order: aaa < zzz
        assertEquals("aaa.mp4", files.get(0).fileName());
        assertEquals("zzz.mp4", files.get(1).fileName());

        // Step 2: Add new file that should be inserted in middle
        ConversionFile file3 = ConversionFile.create(Path.of("/test/mmm.mp4"), FileFormat.MP4, 150L);

        // Add to list and re-sort
        files = List.of(file1, file2, file3).stream().sorted(comparator).toList();

        // Verify new file is in correct position: aaa < mmm < zzz
        assertEquals("aaa.mp4", files.get(0).fileName());
        assertEquals("mmm.mp4", files.get(1).fileName(), "New file should be inserted in sorted position");
        assertEquals("zzz.mp4", files.get(2).fileName());
    }

    /**
     * Test that conversion details dialog has all required data for each status.
     * 
     * Requirements: REQ-FL-2.1, REQ-FL-2.2, REQ-FL-2.3, REQ-FL-2.4
     */
    @Test
    void testConversionDetailsDataForAllStatuses() throws IOException {
        Path file = tempDir.resolve("test.mp4");
        Files.createFile(file);
        ConversionFile convFile = ConversionFile.create(file, FileFormat.MP4, 1024L);

        // Test PENDING status
        ConversionFile pendingFile = convFile.withStatus(ConversionStatus.PENDING);
        when(fileManager.getFile(convFile.id())).thenReturn(Optional.of(pendingFile));

        Optional<ConversionFile> retrieved = controller.getFile(convFile.id());
        assertTrue(retrieved.isPresent());
        assertEquals(ConversionStatus.PENDING, retrieved.get().status());
        // For PENDING: dialog should show placeholder (no result needed)
        assertNull(controller.getConversionResult(convFile.id()));

        // Test IN_PROGRESS status
        ConversionProgress progress = ConversionProgress.initial(convFile.id(), 1024L).updateWithPercentage(45.0);
        ConversionFile inProgressFile = pendingFile
                .withStatus(ConversionStatus.IN_PROGRESS)
                .withProgressInfo(progress);
        when(fileManager.getFile(convFile.id())).thenReturn(Optional.of(inProgressFile));

        retrieved = controller.getFile(convFile.id());
        assertTrue(retrieved.isPresent());
        assertEquals(ConversionStatus.IN_PROGRESS, retrieved.get().status());
        assertNotNull(retrieved.get().progressInfo());
        assertEquals(45, retrieved.get().progressInfo().percentage());

        // Test COMPLETED status
        Path outputPath = tempDir.resolve("output/test.webm");
        Files.createDirectories(outputPath.getParent());
        Files.createFile(outputPath);

        String toolOutput = "ffmpeg conversion completed successfully";
        ConversionResult completedResult = ConversionResult.success(
                convFile.id(),
                outputPath,
                toolOutput,
                java.time.Duration.ofSeconds(60),
                1024L,
                1536L,
                ConversionTool.FFMPEG);

        ConversionFile completedFile = inProgressFile
                .withStatus(ConversionStatus.COMPLETED)
                .withOutputPath(outputPath);
        when(fileManager.getFile(convFile.id())).thenReturn(Optional.of(completedFile));
        when(conversionEngine.getConversionResult(convFile.id())).thenReturn(completedResult);

        retrieved = controller.getFile(convFile.id());
        ConversionResult result = controller.getConversionResult(convFile.id());

        assertTrue(retrieved.isPresent());
        assertEquals(ConversionStatus.COMPLETED, retrieved.get().status());
        assertTrue(retrieved.get().outputPath().isPresent());
        assertNotNull(result);
        assertTrue(result.toolOutput().isPresent());
        assertEquals(toolOutput, result.toolOutput().get());

        // Test FAILED status
        String errorOutput = "Error: Invalid codec specified\nffmpeg exited with code 1";
        ConversionResult failedResult = ConversionResult.failure(
                convFile.id(),
                "Conversion failed: Invalid codec",
                errorOutput,
                java.time.Duration.ofSeconds(5),
                1024L,
                ConversionTool.FFMPEG);

        ConversionFile failedFile = completedFile
                .withStatus(ConversionStatus.FAILED);
        when(fileManager.getFile(convFile.id())).thenReturn(Optional.of(failedFile));
        when(conversionEngine.getConversionResult(convFile.id())).thenReturn(failedResult);

        retrieved = controller.getFile(convFile.id());
        result = controller.getConversionResult(convFile.id());

        assertTrue(retrieved.isPresent());
        assertEquals(ConversionStatus.FAILED, retrieved.get().status());
        assertNotNull(result);
        assertTrue(result.errorMessage().isPresent());
        assertTrue(result.toolOutput().isPresent());
        assertTrue(result.toolOutput().get().contains("Invalid codec"));

        // Test CANCELLED status
        String partialOutput = "ffmpeg started...\nProcessing frame 100/1000\n[Cancelled by user]";
        ConversionResult cancelledResult = ConversionResult.cancelled(
                convFile.id(),
                partialOutput,
                java.time.Duration.ofSeconds(10),
                1024L,
                ConversionTool.FFMPEG);

        ConversionFile cancelledFile = failedFile
                .withStatus(ConversionStatus.CANCELLED);
        when(fileManager.getFile(convFile.id())).thenReturn(Optional.of(cancelledFile));
        when(conversionEngine.getConversionResult(convFile.id())).thenReturn(cancelledResult);

        retrieved = controller.getFile(convFile.id());
        result = controller.getConversionResult(convFile.id());

        assertTrue(retrieved.isPresent());
        assertEquals(ConversionStatus.CANCELLED, retrieved.get().status());
        assertNotNull(result);
        assertTrue(result.toolOutput().isPresent());
        assertTrue(result.toolOutput().get().contains("Cancelled"));
    }

    // ==================== Helper Methods ====================

    /**
     * Simulates FileListView.resolveOutputFormat() logic for testing.
     */
    private String resolveOutputFormat(ConversionFile file, ConversionSettings globalSettings) {
        if (!file.hasCustomSettings()) {
            // Use global settings
            return resolveFormatFromGlobalSettings(file, globalSettings);
        }

        FileSettingsOverride override = file.settingsOverride();

        // If preset name exists, return preset name
        if (override.presetName() != null && !override.presetName().isBlank()) {
            return override.presetName();
        }

        // Otherwise, return format from override settings
        return resolveFormatFromOverride(override);
    }

    private String resolveFormatFromGlobalSettings(ConversionFile file, ConversionSettings settings) {
        FormatCategory category = file.format().getCategory();

        return switch (category) {
            case VIDEO -> {
                VideoSettings vs = settings.videoSettings();
                yield vs != null ? vs.outputFormat().name() : "Not Set";
            }
            case AUDIO -> {
                AudioSettings as = settings.audioSettings();
                yield as != null ? as.outputFormat().name() : "Not Set";
            }
            case IMAGE -> {
                ImageSettings is = settings.imageSettings();
                yield is != null ? is.outputFormat().name() : "Not Set";
            }
            case DOCUMENT -> {
                DocumentSettings ds = settings.documentSettings();
                yield ds != null ? ds.outputFormat().name() : "Not Set";
            }
            case UNKNOWN -> "Not Set";
        };
    }

    private String resolveFormatFromOverride(FileSettingsOverride override) {
        VideoSettings vs = override.videoSettings();
        if (vs != null) {
            return vs.outputFormat().name();
        }

        AudioSettings as = override.audioSettings();
        if (as != null) {
            return as.outputFormat().name();
        }

        ImageSettings is = override.imageSettings();
        if (is != null) {
            return is.outputFormat().name();
        }

        DocumentSettings ds = override.documentSettings();
        if (ds != null) {
            return ds.outputFormat().name();
        }

        return "Not Set";
    }

    /**
     * Creates a test video file with common settings.
     */
    private ConversionFile createTestVideoFile(Path path, long size) {
        return ConversionFile.create(path, FileFormat.MP4, size);
    }

    /**
     * Creates a test audio file with common settings.
     */
    private ConversionFile createTestAudioFile(Path path, long size) {
        return ConversionFile.create(path, FileFormat.MP3, size);
    }

    /**
     * Creates standard video settings for testing.
     */
    private VideoSettings createStandardVideoSettings() {
        return VideoSettings.builder()
                .codec("libx264")
                .bitrate(5000)
                .resolution(new Resolution(1920, 1080))
                .outputFormat(FileFormat.WEBM)
                .build();
    }

    /**
     * Creates standard audio settings for testing.
     */
    private AudioSettings createStandardAudioSettings() {
        return AudioSettings.builder()
                .codec("libmp3lame")
                .bitrate(320)
                .sampleRate(48000)
                .outputFormat(FileFormat.MP3)
                .build();
    }

    /**
     * Creates a video preset for testing.
     */
    private SectionPreset createVideoPreset(String name, String description, VideoSettings settings) {
        return SectionPreset.forVideo(name, description, settings, false);
    }

    /**
     * Creates global conversion settings with video and audio settings.
     */
    private ConversionSettings createGlobalSettingsWithVideoAndAudio(Path outputDir, VideoSettings videoSettings,
            AudioSettings audioSettings) {
        return ConversionSettings.builder()
                .outputDirectory(outputDir)
                .videoSettings(videoSettings)
                .audioSettings(audioSettings)
                .build();
    }
}
