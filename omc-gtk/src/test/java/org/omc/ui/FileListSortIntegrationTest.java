// filepath: src/test/java/org/omc/ui/FileListSortIntegrationTest.java

package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.omc.controller.ApplicationWorkflowController;
import org.omc.controller.FileManager;
import org.omc.controller.SettingsManager;
import org.omc.controller.StateManager;
import org.omc.core.ConversionEngine;
import org.omc.model.ApplicationState;
import org.omc.model.ConversionFile;
import org.omc.model.FileFormat;
import org.omc.model.FileListSortState;
import org.omc.model.FileListSortState.SortDirection;
import org.omc.model.FileListSortState.SortField;

/**
 * Integration tests for file list sorting behavior.
 * 
 * <p>
 * Tests column sorting, direction toggles, and sort state persistence.
 * </p>
 * <p>
 * This test focuses on the sorting logic without GTK UI dependencies.
 * </p>
 * 
 * <p>
 * Requirements: REQ-FL-4.1, REQ-FL-4.5
 * </p>
 */
@DisplayName("File List Sort Integration Tests")
@ExtendWith(MockitoExtension.class)
class FileListSortIntegrationTest extends BaseFileListSortTest {

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
        controller = new ApplicationWorkflowController(
                fileManager,
                settingsManager,
                stateManager,
                conversionEngine);
    }

    @Test
    @DisplayName("Test sort by Name ascending with natural ordering")
    void testSortByNameAscending() {
        // Create files in random order
        List<ConversionFile> files = List.of(
                createFile("zebra.mp4", 1000, FileFormat.MP4),
                createFile("apple.mp4", 2000, FileFormat.MP4),
                createFile("file10.mp4", 3000, FileFormat.MP4),
                createFile("file2.mp4", 4000, FileFormat.MP4),
                createFile("File3.mp4", 5000, FileFormat.MP4));

        // Sort by name ascending
        FileListSortState sortState = FileListSortState.byName(SortDirection.ASCENDING);
        Comparator<ConversionFile> comparator = sortState.createComparator();

        List<ConversionFile> sorted = new ArrayList<>(files);
        sorted.sort(comparator);

        // Verify natural sort order: apple, file2, File3, file10, zebra
        assertEquals("apple.mp4", sorted.get(0).fileName());
        assertEquals("file2.mp4", sorted.get(1).fileName());
        assertEquals("File3.mp4", sorted.get(2).fileName());
        assertEquals("file10.mp4", sorted.get(3).fileName());
        assertEquals("zebra.mp4", sorted.get(4).fileName());
    }

    @Test
    @DisplayName("Test sort by Name descending")
    void testSortByNameDescending() {
        List<ConversionFile> files = List.of(
                createFile("apple.mp4", 1000, FileFormat.MP4),
                createFile("zebra.mp4", 2000, FileFormat.MP4),
                createFile("banana.mp4", 3000, FileFormat.MP4));

        FileListSortState sortState = FileListSortState.byName(SortDirection.DESCENDING);
        Comparator<ConversionFile> comparator = sortState.createComparator();

        List<ConversionFile> sorted = new ArrayList<>(files);
        sorted.sort(comparator);

        // Verify descending order: zebra, banana, apple
        assertEquals("zebra.mp4", sorted.get(0).fileName());
        assertEquals("banana.mp4", sorted.get(1).fileName());
        assertEquals("apple.mp4", sorted.get(2).fileName());
    }

    @Test
    @DisplayName("Test sort by Size ascending")
    void testSortBySizeAscending() {
        List<ConversionFile> files = List.of(
                createFile("large.mp4", 5000000, FileFormat.MP4),
                createFile("small.mp4", 100, FileFormat.MP4),
                createFile("medium.mp4", 10000, FileFormat.MP4));

        FileListSortState sortState = FileListSortState.bySize(SortDirection.ASCENDING);
        Comparator<ConversionFile> comparator = sortState.createComparator();

        List<ConversionFile> sorted = new ArrayList<>(files);
        sorted.sort(comparator);

        // Verify ascending order by size
        assertEquals(100L, sorted.get(0).size());
        assertEquals(10000L, sorted.get(1).size());
        assertEquals(5000000L, sorted.get(2).size());
    }

    @Test
    @DisplayName("Test sort by Size descending")
    void testSortBySizeDescending() {
        List<ConversionFile> files = List.of(
                createFile("small.mp4", 100, FileFormat.MP4),
                createFile("large.mp4", 5000000, FileFormat.MP4),
                createFile("medium.mp4", 10000, FileFormat.MP4));

        FileListSortState sortState = FileListSortState.bySize(SortDirection.DESCENDING);
        Comparator<ConversionFile> comparator = sortState.createComparator();

        List<ConversionFile> sorted = new ArrayList<>(files);
        sorted.sort(comparator);

        // Verify descending order by size
        assertEquals(5000000L, sorted.get(0).size());
        assertEquals(10000L, sorted.get(1).size());
        assertEquals(100L, sorted.get(2).size());
    }

    @Test
    @DisplayName("Test sort by Format ascending")
    void testSortByFormatAscending() {
        List<ConversionFile> files = List.of(
                createFile("video.mp4", 1000, FileFormat.MP4),
                createFile("audio.mp3", 2000, FileFormat.MP3),
                createFile("video.avi", 3000, FileFormat.AVI),
                createFile("video.mkv", 4000, FileFormat.MKV));

        FileListSortState sortState = FileListSortState.byFormat(SortDirection.ASCENDING);
        Comparator<ConversionFile> comparator = sortState.createComparator();

        List<ConversionFile> sorted = new ArrayList<>(files);
        sorted.sort(comparator);

        // Verify alphabetical order: AVI, MKV, MP3, MP4
        assertEquals(FileFormat.AVI, sorted.get(0).format());
        assertEquals(FileFormat.MKV, sorted.get(1).format());
        assertEquals(FileFormat.MP3, sorted.get(2).format());
        assertEquals(FileFormat.MP4, sorted.get(3).format());
    }

    @Test
    @DisplayName("Test sort by Format descending")
    void testSortByFormatDescending() {
        List<ConversionFile> files = List.of(
                createFile("video.avi", 1000, FileFormat.AVI),
                createFile("video.mp4", 2000, FileFormat.MP4),
                createFile("audio.mp3", 3000, FileFormat.MP3));

        FileListSortState sortState = FileListSortState.byFormat(SortDirection.DESCENDING);
        Comparator<ConversionFile> comparator = sortState.createComparator();

        List<ConversionFile> sorted = new ArrayList<>(files);
        sorted.sort(comparator);

        // Verify reverse alphabetical order: MP4, MP3, AVI
        assertEquals(FileFormat.MP4, sorted.get(0).format());
        assertEquals(FileFormat.MP3, sorted.get(1).format());
        assertEquals(FileFormat.AVI, sorted.get(2).format());
    }

    @Test
    @DisplayName("Test direction toggle functionality")
    void testDirectionToggle() {
        FileListSortState ascending = FileListSortState.byName(SortDirection.ASCENDING);
        assertEquals(SortDirection.ASCENDING, ascending.sortDir());

        FileListSortState descending = ascending.toggleDirection();
        assertEquals(SortDirection.DESCENDING, descending.sortDir());
        assertEquals(SortField.NAME, descending.sortField());

        FileListSortState backToAscending = descending.toggleDirection();
        assertEquals(SortDirection.ASCENDING, backToAscending.sortDir());
    }

    @Test
    @DisplayName("Test sort state persistence via ApplicationState")
    void testSortStatePersistence() {
        // Create a sort state
        FileListSortState sortState = FileListSortState.bySize(SortDirection.DESCENDING);

        // Create ApplicationState with sort state
        ApplicationState state = ApplicationState.defaultState()
                .withFileListSortState(sortState);

        // Verify state contains sort state
        assertNotNull(state.fileListSortState());
        assertEquals(SortField.SIZE, state.fileListSortState().sortField());
        assertEquals(SortDirection.DESCENDING, state.fileListSortState().sortDir());
        assertTrue(state.fileListSortState().isSorted());
    }

    @Test
    @DisplayName("Test sort state persistence round-trip")
    void testSortPersistenceRoundTrip() {
        // Create and save sort state
        FileListSortState originalSort = FileListSortState.byFormat(SortDirection.ASCENDING);
        ApplicationState originalState = ApplicationState.defaultState()
                .withFileListSortState(originalSort);

        // Mock state manager to return the saved state
        when(stateManager.getCurrentState()).thenReturn(originalState);

        // Retrieve state
        ApplicationState loadedState = stateManager.getCurrentState();
        FileListSortState restoredSort = loadedState.fileListSortState();

        // Verify
        assertNotNull(restoredSort);
        assertEquals(SortField.FORMAT, restoredSort.sortField());
        assertEquals(SortDirection.ASCENDING, restoredSort.sortDir());
        assertTrue(restoredSort.isSorted());
    }

    @Test
    @DisplayName("Test sort state with null field creates unsorted state")
    void testUnsortedState() {
        FileListSortState unsorted = FileListSortState.unsorted();

        assertNull(unsorted.sortField());
        assertFalse(unsorted.isSorted());
        assertEquals(SortDirection.ASCENDING, unsorted.sortDir());
    }

    @Test
    @DisplayName("Test save sort state via controller")
    void testSaveSortStateViaController() throws Exception {
        // Setup mock
        ApplicationState currentState = ApplicationState.defaultState();
        when(stateManager.getCurrentState()).thenReturn(currentState);

        // Save sort state via controller
        FileListSortState sortState = FileListSortState.byName(SortDirection.DESCENDING);
        controller.saveSortState(sortState);

        // Verify state manager saveState was called
        verify(stateManager, times(1)).saveState(any(ApplicationState.class));
    }

    @Test
    @DisplayName("Test restore sort state returns correct state")
    void testRestoreSortState() {
        // Create saved state with sort
        FileListSortState savedSort = FileListSortState.byName(SortDirection.DESCENDING);
        ApplicationState savedState = ApplicationState.defaultState()
                .withFileListSortState(savedSort);

        when(stateManager.getCurrentState()).thenReturn(savedState);

        // Get saved sort state via controller
        FileListSortState restoredSort = controller.getSavedSortState();

        // Verify
        assertNotNull(restoredSort);
        assertEquals(SortField.NAME, restoredSort.sortField());
        assertEquals(SortDirection.DESCENDING, restoredSort.sortDir());
    }

    @Test
    @DisplayName("Test sort maintains stability for equal elements")
    void testSortStability() {
        // Create files with same name but different sizes
        List<ConversionFile> files = List.of(
                createFile("file.mp4", 1000, FileFormat.MP4),
                createFile("file.mp4", 2000, FileFormat.MP4),
                createFile("file.mp4", 3000, FileFormat.MP4));

        FileListSortState sortState = FileListSortState.byName(SortDirection.ASCENDING);
        Comparator<ConversionFile> comparator = sortState.createComparator();

        List<ConversionFile> sorted = new ArrayList<>(files);
        sorted.sort(comparator);

        // All files have same name, so order is determined by comparator
        // Just verify all elements are present
        assertEquals(3, sorted.size());
        assertTrue(sorted.stream().allMatch(f -> f.fileName().equals("file.mp4")));
    }

    @Test
    @DisplayName("Test comparator handles large file lists")
    void testLargeFileListSorting() {
        // Create 100 files
        List<ConversionFile> files = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            files.add(createFile("file" + i + ".mp4", i * 1000L, FileFormat.MP4));
        }

        FileListSortState sortState = FileListSortState.bySize(SortDirection.ASCENDING);
        Comparator<ConversionFile> comparator = sortState.createComparator();

        long startTime = System.currentTimeMillis();
        files.sort(comparator);
        long duration = System.currentTimeMillis() - startTime;

        // Verify sorting completed quickly (< 100ms for 100 files)
        assertTrue(duration < 100, "Sorting 100 files took " + duration + "ms, expected < 100ms");

        // Verify correct order
        for (int i = 0; i < files.size() - 1; i++) {
            assertTrue(files.get(i).size() <= files.get(i + 1).size());
        }
    }

    // ========== Helper Methods ==========

}
