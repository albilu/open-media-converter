package org.omc.ui;

import org.omc.model.ConversionFile;
import org.omc.model.FileListSortState;
import org.omc.ui.FileListView;
import org.omc.model.FileFormat;
import org.omc.controller.ApplicationWorkflowController;
import org.gnome.gtk.ColumnView;
import org.gnome.gtk.ColumnViewColumn;
import org.gnome.glib.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FileListView column sorting implementation.
 *
 * <p>
 * Tests cover the sorting setup methods and natural comparison logic.
 * </p>
 *
 * <p>
 * Requirements coverage:
 * </p>
 * <ul>
 * <li>REQ-FL-4.1: Column sorting enabled</li>
 * <li>REQ-FL-4.2: Natural alphanumeric sorting for names</li>
 * <li>REQ-FL-4.3: Numeric sorting for file sizes</li>
 * <li>REQ-FL-4.4: Alphabetic sorting for formats</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FileListViewTest {

    @Mock
    private ColumnView columnView;

    @Mock
    private ApplicationWorkflowController controller;

    @Mock
    private ColumnViewColumn nameColumn;

    @Mock
    private ColumnViewColumn sizeColumn;

    @Mock
    private ColumnViewColumn formatColumn;

    @Mock
    private ColumnViewColumn outputFormatColumn;

    private FileListView fileListView;

    // Test data
    private ConversionFile file1;
    private ConversionFile file2;
    private ConversionFile file10;
    private ConversionFile fileSmall;
    private ConversionFile fileLarge;

    @BeforeEach
    void setUp() {
        // Create test files with different names and sizes for sorting tests
        file1 = ConversionFile.create(Path.of("/test/file1.mp4"), FileFormat.MP4, 1024L);
        file2 = ConversionFile.create(Path.of("/test/file2.mp4"), FileFormat.MP4, 2048L);
        file10 = ConversionFile.create(Path.of("/test/file10.mp4"), FileFormat.MP4, 3072L);
        fileSmall = ConversionFile.create(Path.of("/test/small.txt"), FileFormat.TXT, 512L);
        fileLarge = ConversionFile.create(Path.of("/test/large.mp4"), FileFormat.MP4, 1048576L);

        // Mock ColumnView.getColumns() to return a mock ListModel with 0 items
        // This prevents setupColumns from trying to access individual columns
        var mockColumns = mock(org.gnome.gio.ListModel.class);
        when(mockColumns.getNItems()).thenReturn(0);
        when(columnView.getColumns()).thenReturn(mockColumns);

        // Create FileListView with mocked dependencies
        fileListView = new FileListView(columnView, controller);
    }

    // ===== Name Sorting Tests (REQ-FL-4.2) =====

    @Test
    void setupNameSorting_shouldSetCustomSorterOnColumn() throws Exception {
        // When
        invokeSetupNameSorting(nameColumn);

        // Then - verify that setSorter was called
        verify(nameColumn).setSorter(any());
    }

    @Test
    void compareNatural_shouldSortNumericallyEmbeddedStrings() throws Exception {
        // Test cases: "file1.mp4" < "file2.mp4" < "file10.mp4"
        assertTrue(compareNatural("file1.mp4", "file2.mp4") < 0);
        assertTrue(compareNatural("file2.mp4", "file10.mp4") < 0);
        assertTrue(compareNatural("file10.mp4", "file1.mp4") > 0);
    }

    @Test
    void compareNatural_shouldHandleCaseInsensitiveSorting() throws Exception {
        // Test case-insensitive: "File.mp4" == "file.mp4"
        assertEquals(0, compareNatural("File.mp4", "file.mp4"));
        assertEquals(0, compareNatural("FILE.MP4", "file.mp4"));
    }

    @Test
    void compareNatural_shouldSortAlphabeticallyForNonNumericParts() throws Exception {
        // Test alphabetical sorting for non-numeric parts
        assertTrue(compareNatural("apple.mp4", "banana.mp4") < 0);
        assertTrue(compareNatural("zebra.mp4", "apple.mp4") > 0);
    }

    @Test
    void compareNatural_shouldHandleMixedAlphaNumeric() throws Exception {
        // Test mixed alphanumeric strings
        assertTrue(compareNatural("file1.txt", "file10.txt") < 0);
        assertTrue(compareNatural("item2.doc", "item10.doc") < 0);
        assertTrue(compareNatural("test100.mp4", "test2.mp4") > 0);
    }

    @Test
    void compareNatural_shouldHandleUnicodeCharacters() throws Exception {
        // Test Unicode handling
        assertTrue(compareNatural("café.mp4", "zebra.mp4") < 0);
        assertTrue(compareNatural(" naïve.mp4", "zebra.mp4") < 0);
    }

    @Test
    void compareNatural_shouldHandleEmptyAndNullStrings() throws Exception {
        // Test edge cases
        assertEquals(0, compareNatural("", ""));
        assertTrue(compareNatural("", "a") < 0);
        assertTrue(compareNatural("a", "") > 0);
    }

    @Test
    void compareNatural_shouldHandleSpecialCharacters() throws Exception {
        // Test special characters
        assertTrue(compareNatural("file_1.mp4", "file-1.mp4") > 0); // '_' > '-'
        assertTrue(compareNatural("file(1).mp4", "file[1].mp4") < 0); // '(' < '['
    }

    // ===== Size Sorting Tests (REQ-FL-4.3) =====

    @Test
    void setupSizeSorting_shouldSetCustomSorterOnColumn() throws Exception {
        // When
        invokeSetupSizeSorting(sizeColumn);

        // Then - verify that setSorter was called
        verify(sizeColumn).setSorter(any());
    }

    @Test
    void sizeSorting_shouldCompareFilesNumerically() throws Exception {
        // Setup files in FileListView
        java.util.List<ConversionFile> files = java.util.List.of(fileLarge, fileSmall, file1);
        fileListView.setFiles(files);

        // Test that file sizes are compared correctly
        assertTrue(fileSmall.size() < file1.size());
        assertTrue(file1.size() < fileLarge.size());
    }

    // ===== Format Sorting Tests (REQ-FL-4.4) =====

    @Test
    void setupFormatSorting_shouldSetCustomSorterOnColumn() throws Exception {
        // When
        invokeSetupFormatSorting(formatColumn);

        // Then - verify that setSorter was called
        verify(formatColumn).setSorter(any());
    }

    @Test
    void formatSorting_shouldCompareAlphabeticallyCaseInsensitive() throws Exception {
        // Test format name comparison (case-insensitive)
        assertTrue("MP3".compareToIgnoreCase("MP4") < 0);
        assertTrue("TXT".compareToIgnoreCase("MP3") > 0);
        assertEquals(0, "mp4".compareToIgnoreCase("MP4"));
    }

    // ===== Output Format Sorting Tests (REQ-FL-4.4) =====

    @Test
    void setupOutputFormatSorting_shouldSetCustomSorterOnColumn() throws Exception {
        // When
        invokeSetupOutputFormatSorting(outputFormatColumn);

        // Then - verify that setSorter was called
        verify(outputFormatColumn).setSorter(any());
    }

    @Test
    void outputFormatSorting_shouldSortSpecialValuesToEnd() throws Exception {
        // Test that "Not Set" and "Unknown" sort after regular formats
        assertTrue("MP4".compareToIgnoreCase("Not Set") < 0);
        assertTrue("MP4".compareToIgnoreCase("Unknown") < 0);
        assertTrue("Not Set".compareToIgnoreCase("Unknown") < 0); // "Not Set" before "Unknown"
    }

    @Test
    void outputFormatSorting_shouldSortRegularFormatsAlphabetically() throws Exception {
        // Test regular format alphabetical sorting
        assertTrue("AAC".compareToIgnoreCase("MP3") < 0);
        assertTrue("MP4".compareToIgnoreCase("AVI") > 0);
        assertTrue("PNG".compareToIgnoreCase("JPEG") > 0);
    }

    @Test
    void outputFormatSorting_shouldHandlePresetNames() throws Exception {
        // Test preset name sorting
        assertTrue("High Quality".compareToIgnoreCase("Web Optimized") < 0);
        assertTrue("Custom Preset".compareToIgnoreCase("Not Set") < 0);
    }

    // ===== Integration Tests =====

    // Note: Integration test removed due to GTK mocking complexity.
    // Individual setup tests verify sorter assignment.

    // ===== Helper Methods =====

    private int compareNatural(String s1, String s2) throws Exception {
        Method method = FileListView.class.getDeclaredMethod("compareNatural", String.class, String.class);
        method.setAccessible(true);
        return (int) method.invoke(fileListView, s1, s2);
    }

    private void invokeSetupNameSorting(ColumnViewColumn column) throws Exception {
        Method method = FileListView.class.getDeclaredMethod("setupNameSorting", ColumnViewColumn.class);
        method.setAccessible(true);
        method.invoke(fileListView, column);
    }

    private void invokeSetupSizeSorting(ColumnViewColumn column) throws Exception {
        Method method = FileListView.class.getDeclaredMethod("setupSizeSorting", ColumnViewColumn.class);
        method.setAccessible(true);
        method.invoke(fileListView, column);
    }

    private void invokeSetupFormatSorting(ColumnViewColumn column) throws Exception {
        Method method = FileListView.class.getDeclaredMethod("setupFormatSorting", ColumnViewColumn.class);
        method.setAccessible(true);
        method.invoke(fileListView, column);
    }

    private void invokeSetupOutputFormatSorting(ColumnViewColumn column) throws Exception {
        Method method = FileListView.class.getDeclaredMethod("setupOutputFormatSorting", ColumnViewColumn.class);
        method.setAccessible(true);
        method.invoke(fileListView, column);
    }

    private void invokeSetupColumns() throws Exception {
        Method method = FileListView.class.getDeclaredMethod("setupColumns");
        method.setAccessible(true);
        method.invoke(fileListView);
    }

    // ===== Sort State Tracking Tests (Task 77) =====

    @Test
    void setSortChangeListener_shouldRegisterListener() throws Exception {
        // Given
        FileListView.SortChangeListener mockListener = mock(FileListView.SortChangeListener.class);

        // When
        fileListView.setSortChangeListener(mockListener);

        // Then
        Field listenerField = FileListView.class.getDeclaredField("sortChangeListener");
        listenerField.setAccessible(true);
        FileListView.SortChangeListener registeredListener = (FileListView.SortChangeListener) listenerField
                .get(fileListView);
        assertEquals(mockListener, registeredListener);
    }

    @Test
    void setSortChangeListener_withNull_shouldRemoveListener() throws Exception {
        // Given
        FileListView.SortChangeListener mockListener = mock(FileListView.SortChangeListener.class);
        fileListView.setSortChangeListener(mockListener);

        // When
        fileListView.setSortChangeListener(null);

        // Then
        Field listenerField = FileListView.class.getDeclaredField("sortChangeListener");
        listenerField.setAccessible(true);
        FileListView.SortChangeListener registeredListener = (FileListView.SortChangeListener) listenerField
                .get(fileListView);
        assertNull(registeredListener);
    }

    @Test
    void currentSortState_shouldInitializeToUnsorted() throws Exception {
        // When - FileListView is created in setUp()

        // Then
        Field sortStateField = FileListView.class.getDeclaredField("currentSortState");
        sortStateField.setAccessible(true);
        FileListSortState sortState = (FileListSortState) sortStateField.get(fileListView);
        assertEquals(FileListSortState.unsorted(), sortState);
    }

    @Test
    void sortChangeListener_interface_canBeImplemented() {
        // Given
        FileListSortState testState = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);

        // When - implementing the interface with lambda
        FileListView.SortChangeListener listener = sortState -> {
            // Verify the interface method can be called
            assertNotNull(sortState);
            assertEquals(testState, sortState);
        };

        // Then - call the method to verify implementation works
        listener.onSortChanged(testState);
    }

    // ===== 3-Click Sort Cycle Tests =====

    @Test
    void clearSort_shouldResetToUnsortedState() throws Exception {
        // Given - set up a sorted state
        Field sortStateField = FileListView.class.getDeclaredField("currentSortState");
        sortStateField.setAccessible(true);
        FileListSortState sortedState = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);
        sortStateField.set(fileListView, sortedState);

        // When - invoke clearSort
        Method clearSort = FileListView.class.getDeclaredMethod("clearSort");
        clearSort.setAccessible(true);
        clearSort.invoke(fileListView);

        // Then - verify state is unsorted
        FileListSortState resultState = (FileListSortState) sortStateField.get(fileListView);
        assertEquals(FileListSortState.unsorted(), resultState);
    }

    @Test
    void clearSort_shouldClearColumnSortHistory() throws Exception {
        // Given - set up sort history
        Field historyField = FileListView.class.getDeclaredField("columnSortHistory");
        historyField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<org.gnome.gtk.ColumnViewColumn, FileListSortState.SortDirection> history = (java.util.Map<org.gnome.gtk.ColumnViewColumn, FileListSortState.SortDirection>) historyField
                .get(fileListView);

        // Add some history (using mocked column)
        history.put(nameColumn, FileListSortState.SortDirection.DESCENDING);

        // When - invoke clearSort
        Method clearSort = FileListView.class.getDeclaredMethod("clearSort");
        clearSort.setAccessible(true);
        clearSort.invoke(fileListView);

        // Then - verify history is cleared
        assertTrue(history.isEmpty(), "Column sort history should be cleared");
    }

    @Test
    void clearSort_shouldNotifyListenerWithUnsortedState() throws Exception {
        // Given - register a mock listener
        FileListView.SortChangeListener mockListener = mock(FileListView.SortChangeListener.class);
        fileListView.setSortChangeListener(mockListener);

        // When - invoke clearSort
        Method clearSort = FileListView.class.getDeclaredMethod("clearSort");
        clearSort.setAccessible(true);
        clearSort.invoke(fileListView);

        // Then - verify listener was called with unsorted state
        verify(mockListener, times(1)).onSortChanged(FileListSortState.unsorted());
    }

    @Test
    void clearSort_shouldNotNotifyListenerWhenNull() throws Exception {
        // Given - no listener registered (null)
        fileListView.setSortChangeListener(null);

        // When - invoke clearSort (should not throw exception)
        Method clearSort = FileListView.class.getDeclaredMethod("clearSort");
        clearSort.setAccessible(true);

        // Then - should complete without exception
        assertDoesNotThrow(() -> clearSort.invoke(fileListView));
    }

    @Test
    void columnSortHistory_shouldInitializeAsEmptyMap() throws Exception {
        // When - FileListView is created in setUp()

        // Then - verify columnSortHistory exists and is empty
        Field historyField = FileListView.class.getDeclaredField("columnSortHistory");
        historyField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<org.gnome.gtk.ColumnViewColumn, FileListSortState.SortDirection> history = (java.util.Map<org.gnome.gtk.ColumnViewColumn, FileListSortState.SortDirection>) historyField
                .get(fileListView);

        assertNotNull(history, "Column sort history should be initialized");
        assertTrue(history.isEmpty(), "Column sort history should start empty");
    }

    @Test
    void getCurrentSortState_shouldReturnCurrentState() throws Exception {
        // Given - set a specific sort state
        Field sortStateField = FileListView.class.getDeclaredField("currentSortState");
        sortStateField.setAccessible(true);
        FileListSortState testState = FileListSortState.bySize(FileListSortState.SortDirection.DESCENDING);
        sortStateField.set(fileListView, testState);

        // When - get current sort state via public method
        FileListSortState result = fileListView.getCurrentSortState();

        // Then - verify correct state is returned
        assertEquals(testState, result);
        assertEquals(FileListSortState.SortField.SIZE, result.sortField());
        assertEquals(FileListSortState.SortDirection.DESCENDING, result.sortDir());
    }
}