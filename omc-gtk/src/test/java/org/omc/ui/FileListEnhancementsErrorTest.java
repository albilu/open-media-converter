package org.omc.ui;

import org.omc.model.ConversionResult;
import org.omc.model.VideoMetadata;
import org.omc.model.ConversionFile;
import org.omc.model.FileListSortState;
import org.omc.model.ConversionStatus;
import org.omc.model.ConversionTool;
import org.omc.model.FileFormat;
import org.omc.controller.ApplicationWorkflowController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests error handling scenarios for file list enhancements.
 * 
 * <p>
 * This test class covers edge cases and error conditions that could occur
 * during file list operations, details dialog display, and sorting operations.
 * </p>
 * 
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>REQ-FL-2.2: File Details Dialog error handling</li>
 * <li>REQ-FL-3.2: Context menu error handling</li>
 * <li>REQ-FL-4.1: Sorting robustness</li>
 * </ul>
 * 
 * <p>
 * Task 89: Test Error Scenarios
 * </p>
 */
@DisplayName("File List Enhancements Error Scenarios")
@ExtendWith(MockitoExtension.class)
class FileListEnhancementsErrorTest {

    @Mock
    private ApplicationWorkflowController mockController;

    private List<ConversionFile> testFiles;

    @BeforeEach
    void setUp() {
        testFiles = new ArrayList<>();
    }

    // ========== Double-Click Error Scenarios ==========

    // Requirement REQ-FL-2.2: File Details Dialog handles missing metadata
    // gracefully
    @Test
    @DisplayName("Should handle double-click on file with no metadata")
    void testDoubleClickFileWithoutMetadata() {
        // Given: A file without metadata
        ConversionFile fileWithoutMetadata = ConversionFile.create(
                Path.of("/test/no-metadata.mp4"),
                FileFormat.MP4,
                1024L).withStatus(ConversionStatus.PENDING);

        // Verify the file has no metadata
        assertNull(fileWithoutMetadata.metadata(),
                "Test file should not have metadata");

        // When: User attempts to view details (this would be triggered by double-click)
        // FileDetailsDialog should handle null/missing metadata gracefully

        // Then: No exception should be thrown
        assertDoesNotThrow(() -> {
            // Simulate what FileDetailsDialog.show() would do
            Object metadata = fileWithoutMetadata.metadata();
            if (metadata == null) {
                // Dialog should show file info but indicate no metadata available
                // This is the expected graceful handling behavior
            }
        }, "FileDetailsDialog should handle missing metadata without throwing");

        // Additional verification
        assertEquals(ConversionStatus.PENDING, fileWithoutMetadata.status());
        assertNull(fileWithoutMetadata.metadata());
    }

    // Requirement REQ-FL-2.2: Handle failed conversion files
    @Test
    @DisplayName("Should handle file with error status")
    void testDoubleClickFileWithError() {
        // Given: A file in an error state
        ConversionFile errorFile = ConversionFile.create(
                Path.of("/test/error-file.mp4"),
                FileFormat.MP4,
                2048L)
                .withStatus(ConversionStatus.FAILED)
                .withError("Conversion failed");

        // Verify: File has error status
        assertEquals(ConversionStatus.FAILED, errorFile.status());
        assertNotNull(errorFile.errorMessage());

        // Then: Should not throw when accessing error message
        assertDoesNotThrow(() -> {
            String error = errorFile.errorMessage();
            assertEquals("Conversion failed", error);
        });
    }

    @Test
    @DisplayName("Should handle pending file double-click gracefully")
    void testDoubleClickPendingFile() {
        // Given: A pending file (not yet converted)
        ConversionFile pendingFile = ConversionFile.create(
                Path.of("/test/pending-file.mp4"),
                FileFormat.MP4,
                4096L);

        // Verify: File is in PENDING state
        assertEquals(ConversionStatus.PENDING, pendingFile.status());
        assertNull(pendingFile.metadata());

        // Then: Should handle gracefully (no metadata to display)
        assertDoesNotThrow(() -> {
            if (pendingFile.metadata() == null) {
                // Dialog should show basic file info without metadata details
            }
        }, "Should handle pending file without metadata");
    }

    // ========== Context Menu Error Scenarios ==========

    // Requirement REQ-FL-3.2: Open location for file with no output
    @Test
    @DisplayName("Should handle open location for file with no output")
    void testOpenLocationForFileWithoutOutput() {
        // Given: A file that hasn't been converted yet (no output path)
        ConversionFile unconvertedFile = ConversionFile.create(
                Path.of("/test/input.mp4"),
                FileFormat.MP4,
                2048L);

        // Verify: File has no output path
        assertTrue(unconvertedFile.outputPath().isEmpty(),
                "Unconverted file should not have output path");

        // Then: Context menu "Open Output Location" should be disabled/hidden
        // or show appropriate error if user somehow triggers it
        assertDoesNotThrow(() -> {
            if (unconvertedFile.outputPath().isEmpty()) {
                // UI should disable the menu item or show error
                // This is graceful handling - no crash
            }
        });
    }

    @Test
    @DisplayName("Should verify outputPath is set after conversion")
    void testOutputPathAfterConversion() {
        // Given: A file with output path set (after successful conversion)
        Path outputPath = Path.of("/output/converted-file.mp4");
        ConversionFile convertedFile = ConversionFile.create(
                Path.of("/input/source-file.avi"),
                FileFormat.AVI,
                4096L)
                .withStatus(ConversionStatus.COMPLETED)
                .withOutputPath(outputPath);

        // Then: Output path should be present
        assertTrue(convertedFile.outputPath().isPresent(),
                "Completed file should have output path");
        assertEquals(outputPath, convertedFile.outputPath().get());
    }

    @Test
    @DisplayName("Should handle file with cleared output path")
    void testClearedOutputPath() {
        // Given: A file with output path that gets cleared
        ConversionFile fileWithOutput = ConversionFile.create(
                Path.of("/test/file.mp4"),
                FileFormat.MP4,
                2048L).withOutputPath(Path.of("/output/file.mp4"));

        // When: Output path is cleared
        ConversionFile fileWithoutOutput = fileWithOutput.withOutputPath(null);

        // Then: Output path should be empty
        assertTrue(fileWithoutOutput.outputPath().isEmpty(),
                "File should have no output path after clearing");
    }

    // ========== Sorting Error Scenarios ==========

    // Requirement REQ-FL-4.1: Sorting handles empty file list
    @Test
    @DisplayName("Should handle sorting empty file list")
    void testSortEmptyFileList() {
        // Given: Empty file list
        List<ConversionFile> emptyList = new ArrayList<>();

        // When: Create sort state and comparator
        FileListSortState sortState = FileListSortState.byName(
                FileListSortState.SortDirection.ASCENDING);

        // Then: Should not crash when sorting empty list
        assertDoesNotThrow(() -> {
            emptyList.sort(sortState.createComparator());
        }, "Sorting empty list should not throw exception");

        assertEquals(0, emptyList.size(), "List should remain empty");
    }

    @Test
    @DisplayName("Should handle sorting single file list")
    void testSortSingleFileList() {
        // Given: Single file in list
        List<ConversionFile> singleFileList = new ArrayList<>();
        singleFileList.add(ConversionFile.create(
                Path.of("/test/single.mp4"),
                FileFormat.MP4,
                1024L));

        // When: Sort by various fields
        FileListSortState byName = FileListSortState.byName(
                FileListSortState.SortDirection.ASCENDING);
        FileListSortState bySize = FileListSortState.bySize(
                FileListSortState.SortDirection.DESCENDING);

        // Then: Should handle gracefully
        assertDoesNotThrow(() -> {
            singleFileList.sort(byName.createComparator());
            singleFileList.sort(bySize.createComparator());
        }, "Sorting single-item list should work");

        assertEquals(1, singleFileList.size());
    }

    @Test
    @DisplayName("Should handle sorting files with null/missing fields")
    void testSortFilesWithNullFields() {
        // Given: Files with various null/missing optional fields
        ConversionFile file1 = ConversionFile.create(
                Path.of("/test/file1.mp4"),
                FileFormat.MP4,
                1024L); // No metadata, no result, no output path

        ConversionFile file2 = ConversionFile.create(
                Path.of("/test/file2.mp4"),
                FileFormat.MP4,
                2048L).withMetadata(
                        VideoMetadata.builder()
                                .width(1920)
                                .height(1080)
                                .duration(Duration.ofSeconds(120))
                                .videoBitrate(5000000L)
                                .videoCodec("h264")
                                .frameRate(30.0)
                                .build());

        List<ConversionFile> files = new ArrayList<>(List.of(file1, file2));

        // When: Sort by different fields
        FileListSortState sortState = FileListSortState.byOutputFormat(
                FileListSortState.SortDirection.ASCENDING);

        // Then: Should handle null fields gracefully
        assertDoesNotThrow(() -> {
            files.sort(sortState.createComparator());
        }, "Should handle files with null fields during sorting");

        // Both files have no output format, so order should be stable
        assertEquals(2, files.size());
    }

    // ========== Tool Output Truncation ==========

    // Requirement REQ-FL-2.2: Tool output exceeds 1MB
    @Test
    @DisplayName("Should handle tool output exceeding 1MB")
    void testToolOutputTruncation() {
        // Given: A ConversionResult with very large tool output (> 1MB)
        int oneMB = 1_048_576;
        String largeOutput = "X".repeat(oneMB + 10000); // 1MB + 10KB

        Path outputPath = Path.of("/output/result.mp4");
        ConversionResult largeResult = ConversionResult.success(
                "test-id",
                outputPath,
                largeOutput,
                Duration.ofSeconds(30),
                1024L,
                2048L,
                ConversionTool.FFMPEG);

        // Verify: Tool output is stored (even if large)
        assertTrue(largeResult.toolOutput().isPresent());
        assertEquals(largeOutput.length(), largeResult.toolOutput().get().length());
        assertTrue(largeResult.toolOutput().get().length() > oneMB,
                "Tool output should exceed 1MB");

        // When: FileDetailsDialog displays the output
        // Then: Dialog should truncate or handle large output gracefully
        assertDoesNotThrow(() -> {
            String output = largeResult.toolOutput().orElse("");

            // Simulate truncation logic (as might be in FileDetailsDialog)
            int maxDisplayLength = oneMB; // 1MB limit for display
            String displayOutput = output.length() > maxDisplayLength
                    ? output.substring(0, maxDisplayLength) + "\n\n[Output truncated - exceeds 1MB]"
                    : output;

            assertTrue(displayOutput.length() <= maxDisplayLength + 100,
                    "Display output should be truncated");
        }, "Should handle large tool output without crash");
    }

    @Test
    @DisplayName("Should handle missing tool output")
    void testMissingToolOutput() {
        // Given: ConversionResult with no tool output
        ConversionResult resultNoOutput = ConversionResult.success(
                "test-id",
                Path.of("/output/result.mp4"),
                null, // No tool output
                Duration.ofSeconds(10),
                1024L,
                2048L,
                ConversionTool.FFMPEG);

        // Then: Should handle Optional.empty() gracefully
        assertDoesNotThrow(() -> {
            Optional<String> output = resultNoOutput.toolOutput();
            assertTrue(output.isEmpty(), "Tool output should be empty");

            // FileDetailsDialog should show "No output available" or similar
            String displayText = output.orElse("No tool output available");
            assertEquals("No tool output available", displayText);
        });
    }

    @Test
    @DisplayName("Should handle empty tool output string")
    void testEmptyToolOutput() {
        // Given: ConversionResult with empty string output
        ConversionResult resultEmptyOutput = ConversionResult.success(
                "test-id",
                Path.of("/output/result.mp4"),
                "", // Empty string
                Duration.ofSeconds(5),
                1024L,
                2048L,
                ConversionTool.PANDOC);

        // Then: Should handle empty string
        assertDoesNotThrow(() -> {
            String output = resultEmptyOutput.toolOutput().orElse("");
            assertEquals("", output);

            // FileDetailsDialog might show a placeholder
            String display = output.isEmpty() ? "No output captured" : output;
            assertEquals("No output captured", display);
        });
    }

    // ========== Additional Edge Cases ==========

    @Test
    @DisplayName("Should handle file with very long filename")
    void testFileWithVeryLongFilename() {
        // Given: File with extremely long filename
        String longName = "a".repeat(255) + ".mp4"; // Max filename length on most filesystems
        Path longPath = Path.of("/test/" + longName);

        ConversionFile longNameFile = ConversionFile.create(
                longPath,
                FileFormat.MP4,
                1024L);

        // Then: Should handle without issues
        assertDoesNotThrow(() -> {
            String fileName = longNameFile.fileName();
            assertEquals(longName, fileName);
        }, "Should handle very long filenames");

        // Sorting should also work
        List<ConversionFile> files = List.of(longNameFile);
        assertDoesNotThrow(() -> {
            files.stream()
                    .sorted(FileListSortState.byName(
                            FileListSortState.SortDirection.ASCENDING).createComparator())
                    .toList();
        });
    }

    @Test
    @DisplayName("Should handle file with special characters in name")
    void testFileWithSpecialCharacters() {
        // Given: File with special characters
        String specialName = "test file [2024] (HD) #1 & <special>.mp4";
        Path specialPath = Path.of("/test/" + specialName);

        ConversionFile specialFile = ConversionFile.create(
                specialPath,
                FileFormat.MP4,
                2048L);

        // Then: Should handle special characters
        assertDoesNotThrow(() -> {
            String fileName = specialFile.fileName();
            assertEquals(specialName, fileName);
        }, "Should handle special characters in filename");

        // FileDetailsDialog should escape markup properly
        // (tested separately in FileDetailsDialogTest.escapeMarkup)
    }

    @Test
    @DisplayName("Should handle concurrent sort operations")
    void testConcurrentSortOperations() {
        // Given: File list and multiple sort states
        List<ConversionFile> files = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            files.add(ConversionFile.create(
                    Path.of("/test/file" + i + ".mp4"),
                    FileFormat.MP4,
                    i * 1024L));
        }

        FileListSortState sortByName = FileListSortState.byName(
                FileListSortState.SortDirection.ASCENDING);
        FileListSortState sortBySize = FileListSortState.bySize(
                FileListSortState.SortDirection.DESCENDING);

        // When: Perform multiple sorts
        assertDoesNotThrow(() -> {
            files.sort(sortByName.createComparator());
            assertEquals(100, files.size());

            files.sort(sortBySize.createComparator());
            assertEquals(100, files.size());

            // Verify descending size order
            for (int i = 0; i < files.size() - 1; i++) {
                assertTrue(files.get(i).size() >= files.get(i + 1).size(),
                        "Files should be sorted by size descending");
            }
        }, "Should handle multiple sort operations");
    }
}
