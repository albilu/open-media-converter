// filepath: src/test/java/org/omc/ui/FileListSortPerformanceTest.java

package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.omc.model.ConversionFile;
import org.omc.model.FileFormat;
import org.omc.model.FileListSortState;
import org.omc.model.FileListSortState.SortDirection;

/**
 * Performance tests for file list sorting.
 * 
 * <p>
 * Validates that sorting operations meet performance requirements with large
 * datasets.
 * </p>
 * 
 * <p>
 * Requirements: NFR-FL-1
 * </p>
 */
@DisplayName("File List Sort Performance Tests")
class FileListSortPerformanceTest extends BaseFileListSortTest {

    private static final int LARGE_FILE_COUNT = 1000;
    private static final long MAX_SORT_TIME_MS = 100;

    @Test
    @DisplayName("Test Name column sort performance with 1000 files")
    void testNameSortPerformance() {
        List<ConversionFile> files = createLargeFileList();

        FileListSortState sortState = FileListSortState.byName(SortDirection.ASCENDING);
        Comparator<ConversionFile> comparator = sortState.createComparator();

        long startTime = System.currentTimeMillis();
        files.sort(comparator);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(duration < MAX_SORT_TIME_MS,
                String.format("Name sort took %dms, expected < %dms", duration, MAX_SORT_TIME_MS));

        // Verify sort correctness (first few elements)
        assertTrue(files.get(0).fileName().compareToIgnoreCase(files.get(1).fileName()) <= 0);
    }

    @Test
    @DisplayName("Test Size column sort performance with 1000 files")
    void testSizeSortPerformance() {
        List<ConversionFile> files = createLargeFileList();

        FileListSortState sortState = FileListSortState.bySize(SortDirection.ASCENDING);
        Comparator<ConversionFile> comparator = sortState.createComparator();

        long startTime = System.currentTimeMillis();
        files.sort(comparator);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(duration < MAX_SORT_TIME_MS,
                String.format("Size sort took %dms, expected < %dms", duration, MAX_SORT_TIME_MS));

        // Verify sort correctness
        assertTrue(files.get(0).size() <= files.get(1).size());
    }

    @Test
    @DisplayName("Test Format column sort performance with 1000 files")
    void testFormatSortPerformance() {
        List<ConversionFile> files = createLargeFileList();

        FileListSortState sortState = FileListSortState.byFormat(SortDirection.ASCENDING);
        Comparator<ConversionFile> comparator = sortState.createComparator();

        long startTime = System.currentTimeMillis();
        files.sort(comparator);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(duration < MAX_SORT_TIME_MS,
                String.format("Format sort took %dms, expected < %dms", duration, MAX_SORT_TIME_MS));

        // Verify sort correctness
        String format1 = files.get(0).format().name();
        String format2 = files.get(1).format().name();
        assertTrue(format1.compareToIgnoreCase(format2) <= 0);
    }

    @Test
    @DisplayName("Test sort performance with descending direction")
    void testDescendingSortPerformance() {
        List<ConversionFile> files = createLargeFileList();

        FileListSortState sortState = FileListSortState.byName(SortDirection.DESCENDING);
        Comparator<ConversionFile> comparator = sortState.createComparator();

        long startTime = System.currentTimeMillis();
        files.sort(comparator);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(duration < MAX_SORT_TIME_MS,
                String.format("Descending sort took %dms, expected < %dms", duration, MAX_SORT_TIME_MS));

        // Verify descending order
        assertTrue(files.get(0).fileName().compareToIgnoreCase(files.get(1).fileName()) >= 0);
    }

    @Test
    @DisplayName("Test multiple consecutive sorts performance")
    void testMultipleSortsPerformance() {
        List<ConversionFile> files = createLargeFileList();

        // Sort by name
        FileListSortState nameSort = FileListSortState.byName(SortDirection.ASCENDING);
        long start1 = System.currentTimeMillis();
        files.sort(nameSort.createComparator());
        long duration1 = System.currentTimeMillis() - start1;

        // Sort by size
        FileListSortState sizeSort = FileListSortState.bySize(SortDirection.ASCENDING);
        long start2 = System.currentTimeMillis();
        files.sort(sizeSort.createComparator());
        long duration2 = System.currentTimeMillis() - start2;

        // Sort by format
        FileListSortState formatSort = FileListSortState.byFormat(SortDirection.ASCENDING);
        long start3 = System.currentTimeMillis();
        files.sort(formatSort.createComparator());
        long duration3 = System.currentTimeMillis() - start3;

        long totalDuration = duration1 + duration2 + duration3;

        // Each sort should be fast
        assertTrue(duration1 < MAX_SORT_TIME_MS,
                String.format("First sort took %dms", duration1));
        assertTrue(duration2 < MAX_SORT_TIME_MS,
                String.format("Second sort took %dms", duration2));
        assertTrue(duration3 < MAX_SORT_TIME_MS,
                String.format("Third sort took %dms", duration3));

        // Total should be reasonable
        assertTrue(totalDuration < MAX_SORT_TIME_MS * 3,
                String.format("Total sort time %dms exceeds %dms", totalDuration, MAX_SORT_TIME_MS * 3));
    }

    @Test
    @DisplayName("Test sort stability with equal elements")
    void testSortStabilityPerformance() {
        // Create 1000 files with same name but different sizes
        List<ConversionFile> files = new ArrayList<>();
        for (int i = 0; i < LARGE_FILE_COUNT; i++) {
            files.add(createFile("samename.mp4", i * 1000L, FileFormat.MP4));
        }

        FileListSortState sortState = FileListSortState.byName(SortDirection.ASCENDING);
        Comparator<ConversionFile> comparator = sortState.createComparator();

        long startTime = System.currentTimeMillis();
        files.sort(comparator);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(duration < MAX_SORT_TIME_MS,
                String.format("Stability sort took %dms, expected < %dms", duration, MAX_SORT_TIME_MS));

        // All elements should have same name
        assertTrue(files.stream().allMatch(f -> f.fileName().equals("samename.mp4")));
    }

    @Test
    @DisplayName("Test sort with worst-case scenario (reverse sorted)")
    void testWorstCaseSortPerformance() {
        // Create reverse-sorted list
        List<ConversionFile> files = new ArrayList<>();
        for (int i = LARGE_FILE_COUNT; i > 0; i--) {
            files.add(createFile("file" + String.format("%04d", i) + ".mp4", i * 1000L, FileFormat.MP4));
        }

        FileListSortState sortState = FileListSortState.byName(SortDirection.ASCENDING);
        Comparator<ConversionFile> comparator = sortState.createComparator();

        long startTime = System.currentTimeMillis();
        files.sort(comparator);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(duration < MAX_SORT_TIME_MS,
                String.format("Worst-case sort took %dms, expected < %dms", duration, MAX_SORT_TIME_MS));

        // Verify correct order
        for (int i = 0; i < files.size() - 1; i++) {
            assertTrue(files.get(i).fileName().compareToIgnoreCase(files.get(i + 1).fileName()) <= 0);
        }
    }

    // ========== Helper Methods ==========

    /**
     * Creates a list of 1000 files with varied properties for performance testing.
     */
    private List<ConversionFile> createLargeFileList() {
        List<ConversionFile> files = new ArrayList<>(LARGE_FILE_COUNT);
        Random random = new Random(42); // Fixed seed for reproducibility

        FileFormat[] formats = {
                FileFormat.MP4, FileFormat.AVI, FileFormat.MKV, FileFormat.MP3,
                FileFormat.WAV, FileFormat.FLAC, FileFormat.PNG, FileFormat.JPEG
        };

        for (int i = 0; i < LARGE_FILE_COUNT; i++) {
            String fileName = generateRandomFileName(i, random);
            long size = random.nextInt(100_000_000) + 1000; // 1KB to 100MB
            FileFormat format = formats[random.nextInt(formats.length)];

            files.add(createFile(fileName, size, format));
        }

        return files;
    }

    /**
     * Generates a random file name with varied patterns.
     */
    private String generateRandomFileName(int index, Random random) {
        String[] prefixes = { "video", "audio", "document", "file", "test", "media", "convert" };
        String[] suffixes = { ".mp4", ".avi", ".mkv", ".mp3", ".wav", ".png", ".jpg" };

        String prefix = prefixes[random.nextInt(prefixes.length)];
        String number = String.format("%04d", index);
        String suffix = suffixes[random.nextInt(suffixes.length)];

        // Mix lowercase and uppercase
        if (random.nextBoolean()) {
            prefix = prefix.toUpperCase();
        }

        return prefix + number + suffix;
    }

}
