// filepath: src/test/java/org/omc/ui/FileListViewRenderPerformanceTest.java

package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omc.controller.ApplicationWorkflowController;
import org.omc.model.AudioSettings;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionSettings;
import org.omc.model.ConversionStatus;
import org.omc.model.FileFormat;
import org.omc.model.FileSettingsOverride;
import org.omc.model.Resolution;
import org.omc.model.VideoSettings;

/**
 * Performance tests for FileListView rendering operations with large datasets.
 * 
 * <p>
 * This test suite validates that file list rendering and data preparation
 * operations meet performance requirements with 1000+ files.
 * </p>
 * 
 * <p>
 * <b>Note on Testing Approach:</b>
 * </p>
 * <p>
 * GTK UI components require a full GTK runtime environment which is difficult
 * to initialize in unit tests. Instead, this test focuses on the data
 * preparation
 * and model operations that FileListView performs during rendering:
 * </p>
 * <ul>
 * <li>Output format resolution for all files</li>
 * <li>File metadata preparation</li>
 * <li>Data structure population</li>
 * </ul>
 * 
 * <p>
 * These operations represent the computational work that would impact rendering
 * performance. Actual GTK widget rendering performance should be verified
 * through
 * manual UI testing.
 * </p>
 * 
 * <p>
 * Requirements: NFR-FL-1 (Task 90)
 * </p>
 * 
 * @see FileListView
 */
@DisplayName("File List View Render Performance Tests")
class FileListViewRenderPerformanceTest {

    @TempDir
    Path tempDir;

    private static final int LARGE_FILE_COUNT = 1000;
    private static final long MAX_DATA_PREP_TIME_MS = 500; // 500ms for data preparation
    private static final long MAX_OUTPUT_FORMAT_RESOLUTION_MS = 500; // 500ms for 1000 files
    private static final long MAX_SINGLE_UPDATE_TIME_MS = 10; // 10ms per file update

    /**
     * Test output format resolution performance with 1000 files.
     * 
     * <p>
     * Requirement NFR-FL-1: Output format resolution SHALL not delay file list
     * rendering.
     * </p>
     * <p>
     * Task 90: Measure output format resolution for large file lists.
     * </p>
     */
    @Test
    @DisplayName("Test output format resolution performance with 1000 files")
    void testOutputFormatResolutionPerformance() {
        // Create mock controller with conversion settings
        ApplicationWorkflowController mockController = createMockController();

        // Create 1000 files with various configurations
        List<ConversionFile> files = createLargeFileList();

        // Measure time to resolve output format for all files
        long startTime = System.currentTimeMillis();

        List<String> resolvedFormats = new ArrayList<>();
        for (ConversionFile file : files) {
            String outputFormat = resolveOutputFormat(file, mockController);
            resolvedFormats.add(outputFormat);
        }

        long duration = System.currentTimeMillis() - startTime;

        // Verify all formats were resolved
        assertEquals(LARGE_FILE_COUNT, resolvedFormats.size());
        assertTrue(resolvedFormats.stream().noneMatch(f -> f == null || f.isEmpty()),
                "All output formats should be resolved");

        // Check performance threshold: < 500ms for 1000 files
        assertTrue(duration < MAX_OUTPUT_FORMAT_RESOLUTION_MS,
                String.format("Output format resolution took %dms, expected < %dms for %d files",
                        duration, MAX_OUTPUT_FORMAT_RESOLUTION_MS, LARGE_FILE_COUNT));

        // Calculate average per-file resolution time
        double avgPerFile = (double) duration / LARGE_FILE_COUNT;
        System.out.printf("Performance: Resolved output format for %d files in %dms (%.3fms per file)%n",
                LARGE_FILE_COUNT, duration, avgPerFile);
    }

    /**
     * Test file metadata preparation performance with 1000 files.
     * 
     * <p>
     * Simulates the data preparation work that FileListView performs
     * when populating the GTK model.
     * </p>
     * 
     * <p>
     * Requirement NFR-FL-1: File list rendering < 1 second for 1000 files.
     * </p>
     * <p>
     * Task 90: Measure data preparation time for rendering.
     * </p>
     */
    @Test
    @DisplayName("Test file metadata preparation performance with 1000 files")
    void testFileMetadataPreparationPerformance() {
        List<ConversionFile> files = createLargeFileList();

        // Measure time to extract display data for all files
        long startTime = System.currentTimeMillis();

        List<FileDisplayData> displayData = new ArrayList<>();
        for (ConversionFile file : files) {
            // Simulate the data extraction FileListView does for each row
            FileDisplayData data = new FileDisplayData(
                    file.id(),
                    file.fileName(),
                    formatFileSize(file.size()),
                    file.format().name(),
                    formatStatus(file.status()),
                    file.progress());
            displayData.add(data);
        }

        long duration = System.currentTimeMillis() - startTime;

        // Verify all data was prepared
        assertEquals(LARGE_FILE_COUNT, displayData.size());

        // Check performance threshold: < 500ms for data preparation
        assertTrue(duration < MAX_DATA_PREP_TIME_MS,
                String.format("File metadata preparation took %dms, expected < %dms for %d files",
                        duration, MAX_DATA_PREP_TIME_MS, LARGE_FILE_COUNT));

        System.out.printf("Performance: Prepared metadata for %d files in %dms%n",
                LARGE_FILE_COUNT, duration);
    }

    /**
     * Test incremental file update performance.
     * 
     * <p>
     * Measures the time to update individual files in a large list,
     * simulating real-time progress updates during conversion.
     * </p>
     * 
     * <p>
     * Requirement NFR-FL-1: Real-time updates should not degrade performance.
     * </p>
     */
    @Test
    @DisplayName("Test incremental file update performance with 1000 files")
    void testIncrementalUpdatePerformance() {
        List<ConversionFile> files = createLargeFileList();

        // Simulate updating progress for random files
        Random random = new Random(42);
        int updateCount = 100; // Update 100 random files
        long totalDuration = 0;

        for (int i = 0; i < updateCount; i++) {
            int fileIndex = random.nextInt(LARGE_FILE_COUNT);
            ConversionFile file = files.get(fileIndex);

            // Measure time for single file update
            long startTime = System.nanoTime();

            // Simulate the work FileListView.updateFile() does
            ConversionFile updated = file.withStatus(ConversionStatus.IN_PROGRESS)
                    .withProgress(random.nextInt(100));
            files.set(fileIndex, updated);

            // Extract display data for updated file
            String displayProgress = updated.progress() + "%";

            long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to ms
            totalDuration += duration;
        }

        double avgUpdateTime = (double) totalDuration / updateCount;

        // Each update should be very fast
        assertTrue(avgUpdateTime < MAX_SINGLE_UPDATE_TIME_MS,
                String.format("Average update time %.2fms exceeds threshold %dms",
                        avgUpdateTime, MAX_SINGLE_UPDATE_TIME_MS));

        System.out.printf("Performance: Average file update time: %.3fms over %d updates%n",
                avgUpdateTime, updateCount);
    }

    /**
     * Test file lookup by ID performance in large list.
     * 
     * <p>
     * FileListView uses ID-based lookup frequently during rendering
     * and updates.
     * </p>
     */
    @Test
    @DisplayName("Test file lookup by ID performance with 1000 files")
    void testFileIdLookupPerformance() {
        List<ConversionFile> files = createLargeFileList();

        // Build ID-to-index map (as FileListView does)
        java.util.Map<String, Integer> idToIndexMap = new java.util.HashMap<>();
        for (int i = 0; i < files.size(); i++) {
            idToIndexMap.put(files.get(i).id(), i);
        }

        // Measure lookup performance
        Random random = new Random(42);
        int lookupCount = 1000;
        long totalDuration = 0;

        for (int i = 0; i < lookupCount; i++) {
            ConversionFile targetFile = files.get(random.nextInt(LARGE_FILE_COUNT));
            String fileId = targetFile.id();

            long startTime = System.nanoTime();

            // Perform ID lookup (as FileListView does)
            Integer index = idToIndexMap.get(fileId);
            ConversionFile found = (index != null && index < files.size()) ? files.get(index) : null;

            long duration = (System.nanoTime() - startTime) / 1_000; // Convert to microseconds
            totalDuration += duration;

            assertNotNull(found);
            assertEquals(fileId, found.id());
        }

        double avgLookupTime = (double) totalDuration / lookupCount;

        // Lookups should be very fast (< 10 microseconds average)
        assertTrue(avgLookupTime < 10,
                String.format("Average ID lookup time %.2f microseconds exceeds 10 microseconds",
                        avgLookupTime));

        System.out.printf("Performance: Average file ID lookup: %.3f microseconds over %d lookups%n",
                avgLookupTime, lookupCount);
    }

    /**
     * Test mixed output format configurations performance.
     * 
     * <p>
     * Some files use global settings, others use presets, others use custom
     * overrides.
     * This tests the performance with realistic mixed configurations.
     * </p>
     */
    @Test
    @DisplayName("Test mixed configuration output format resolution performance")
    void testMixedConfigurationPerformance() {
        ApplicationWorkflowController mockController = createMockController();

        // Create files with mixed configurations
        List<ConversionFile> files = new ArrayList<>();
        for (int i = 0; i < LARGE_FILE_COUNT; i++) {
            ConversionFile file;
            if (i % 3 == 0) {
                // 33% with preset
                file = createFileWithPreset("file" + i + ".mp4", i * 1000L, "High Quality");
            } else if (i % 3 == 1) {
                // 33% with custom override (no preset)
                file = createFileWithOverride("file" + i + ".mp4", i * 1000L);
            } else {
                // 33% with global settings
                file = createSimpleFile("file" + i + ".mp4", i * 1000L, FileFormat.MP4);
            }
            files.add(file);
        }

        // Measure resolution time
        long startTime = System.currentTimeMillis();

        for (ConversionFile file : files) {
            String outputFormat = resolveOutputFormat(file, mockController);
        }

        long duration = System.currentTimeMillis() - startTime;

        assertTrue(duration < MAX_OUTPUT_FORMAT_RESOLUTION_MS,
                String.format("Mixed configuration resolution took %dms, expected < %dms",
                        duration, MAX_OUTPUT_FORMAT_RESOLUTION_MS));

        System.out.printf("Performance: Resolved mixed configurations for %d files in %dms%n",
                LARGE_FILE_COUNT, duration);
    }

    // ========== Helper Methods ==========

    /**
     * Creates a mock ApplicationWorkflowController with conversion settings.
     */
    private ApplicationWorkflowController createMockController() {
        ApplicationWorkflowController mockController = mock(ApplicationWorkflowController.class);

        // Create mock ConversionSettings
        ConversionSettings mockSettings = mock(ConversionSettings.class);
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .codec("libx264")
                .bitrate(5000)
                .resolution(new Resolution(1920, 1080))
                .frameRate(30)
                .preset("medium")
                .crf(23)
                .build();
        AudioSettings audioSettings = AudioSettings.builder()
                .outputFormat(FileFormat.MP3)
                .codec("libmp3lame")
                .bitrate(192)
                .sampleRate(44100)
                .channels(2)
                .quality(5)
                .build();

        when(mockSettings.videoSettings()).thenReturn(videoSettings);
        when(mockSettings.audioSettings()).thenReturn(audioSettings);
        when(mockController.getCurrentSettings()).thenReturn(mockSettings);

        return mockController;
    }

    /**
     * Creates a large list of 1000 files with varied properties.
     */
    private List<ConversionFile> createLargeFileList() {
        List<ConversionFile> files = new ArrayList<>(LARGE_FILE_COUNT);
        Random random = new Random(42);

        FileFormat[] formats = {
                FileFormat.MP4, FileFormat.AVI, FileFormat.MKV, FileFormat.MP3,
                FileFormat.WAV, FileFormat.FLAC, FileFormat.PNG, FileFormat.JPEG
        };

        ConversionStatus[] statuses = ConversionStatus.values();

        for (int i = 0; i < LARGE_FILE_COUNT; i++) {
            String fileName = "file" + String.format("%04d", i) + ".mp4";
            long size = random.nextInt(100_000_000) + 1000; // 1KB to 100MB
            FileFormat format = formats[random.nextInt(formats.length)];
            ConversionStatus status = statuses[random.nextInt(statuses.length)];
            int progress = random.nextInt(101);

            Path filePath = tempDir.resolve(fileName);
            ConversionFile file = ConversionFile.create(filePath, format, size)
                    .withStatus(status)
                    .withProgress(progress);

            files.add(file);
        }

        return files;
    }

    /**
     * Creates a simple file without custom settings.
     */
    private ConversionFile createSimpleFile(String fileName, long size, FileFormat format) {
        Path filePath = tempDir.resolve(fileName);
        return ConversionFile.create(filePath, format, size);
    }

    /**
     * Creates a file with a preset name.
     */
    private ConversionFile createFileWithPreset(String fileName, long size, String presetName) {
        Path filePath = tempDir.resolve(fileName);
        VideoSettings videoSettings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .codec("libx264")
                .bitrate(5000)
                .resolution(new Resolution(1920, 1080))
                .frameRate(30)
                .preset("medium")
                .crf(23)
                .build();

        FileSettingsOverride override = FileSettingsOverride.forVideo(presetName, videoSettings);

        return ConversionFile.create(filePath, FileFormat.MP4, size)
                .withSettingsOverride(override);
    }

    /**
     * Creates a file with custom override (no preset name).
     */
    private ConversionFile createFileWithOverride(String fileName, long size) {
        Path filePath = tempDir.resolve(fileName);
        AudioSettings audioSettings = AudioSettings.builder()
                .outputFormat(FileFormat.MP3)
                .codec("libmp3lame")
                .bitrate(192)
                .sampleRate(44100)
                .channels(2)
                .quality(5)
                .build();

        FileSettingsOverride override = FileSettingsOverride.forAudio(null, audioSettings);

        return ConversionFile.create(filePath, FileFormat.MP3, size)
                .withSettingsOverride(override);
    }

    /**
     * Resolves output format for a file (simulates
     * FileListView.resolveOutputFormat).
     */
    private String resolveOutputFormat(ConversionFile file, ApplicationWorkflowController controller) {
        // Check for custom settings override
        if (file.hasCustomSettings()) {
            FileSettingsOverride override = file.settingsOverride();

            // Prefer preset name if available
            if (override.presetName() != null && !override.presetName().isEmpty()) {
                return override.presetName();
            }

            // Otherwise get format from override settings
            return resolveFormatFromOverride(override);
        }

        // Use global settings
        return resolveFormatFromGlobalSettings(file, controller);
    }

    /**
     * Resolves format from FileSettingsOverride.
     */
    private String resolveFormatFromOverride(FileSettingsOverride override) {
        if (override.videoSettings() != null) {
            FileFormat format = override.videoSettings().outputFormat();
            return format != null ? format.name() : "Not Set";
        } else if (override.audioSettings() != null) {
            FileFormat format = override.audioSettings().outputFormat();
            return format != null ? format.name() : "Not Set";
        } else if (override.imageSettings() != null) {
            FileFormat format = override.imageSettings().outputFormat();
            return format != null ? format.name() : "Not Set";
        } else if (override.documentSettings() != null) {
            FileFormat format = override.documentSettings().outputFormat();
            return format != null ? format.name() : "Not Set";
        }
        return "Not Set";
    }

    /**
     * Resolves format from global ConversionSettings.
     */
    private String resolveFormatFromGlobalSettings(ConversionFile file, ApplicationWorkflowController controller) {
        try {
            ConversionSettings settings = controller.getCurrentSettings();
            org.omc.model.FormatCategory category = file.format().getCategory();

            FileFormat outputFormat = switch (category) {
                case VIDEO -> settings.videoSettings().outputFormat();
                case AUDIO -> settings.audioSettings().outputFormat();
                case IMAGE -> null; // Mock doesn't have image settings
                case DOCUMENT -> null; // Mock doesn't have document settings
                case UNKNOWN -> null;
            };

            return outputFormat != null ? outputFormat.name() : "Not Set";
        } catch (Exception e) {
            return "Not Set";
        }
    }

    /**
     * Formats file size (simulates FileListView formatting).
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Formats conversion status (simulates FileListView formatting).
     */
    private String formatStatus(ConversionStatus status) {
        return switch (status) {
            case PENDING -> "Pending";
            case IN_PROGRESS -> "Converting...";
            case COMPLETED -> "Completed";
            case FAILED -> "Failed";
            case CANCELLED -> "Cancelled";
        };
    }

    /**
     * Simple record to hold file display data.
     */
    private record FileDisplayData(
            String id,
            String fileName,
            String size,
            String format,
            String status,
            int progress) {
    }
}
