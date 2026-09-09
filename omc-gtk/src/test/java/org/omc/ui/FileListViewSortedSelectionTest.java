package org.omc.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gnome.gtk.ColumnView;
import org.gnome.gtk.CustomSorter;
import org.gnome.gtk.MultiSelection;
import org.gnome.gtk.SortListModel;
import org.gnome.gtk.StringObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.omc.controller.ApplicationWorkflowController;
import org.omc.model.ConversionFile;
import org.omc.model.FileFormat;

/**
 * Tests for selection correctness when the GTK list model is sorted.
 *
 * <p>
 * The MultiSelection wraps a SortListModel, so selection bitset positions refer
 * to the visual (sorted) order, NOT to the insertion-ordered Java file list.
 * getSelectedFileIds() must resolve ids through the selection model's own
 * items, otherwise the wrong file is returned after a header-click sort.
 * </p>
 *
 * <p>
 * GTK list-model GObjects (StringList, SortListModel, MultiSelection,
 * CustomSorter) are fully functional headless, so these are real model tests:
 * the view's model chain is genuinely re-sorted, mirroring what GTK does when
 * the user clicks a column header.
 * </p>
 *
 * <p>
 * Requirements: REQ-002.2 (file selection for operations), REQ-FL-4.1
 * (column sorting enabled)
 * </p>
 */
@DisplayName("File list selection with sorted GTK model")
@ExtendWith(MockitoExtension.class)
class FileListViewSortedSelectionTest {

    @Mock
    private ColumnView columnView;

    @Mock
    private ApplicationWorkflowController controller;

    private FileListView fileListView;

    private ConversionFile fileCherry;
    private ConversionFile fileApple;
    private ConversionFile fileBanana;

    @BeforeEach
    void setUp() {
        var mockColumns = mock(org.gnome.gio.ListModel.class);
        when(mockColumns.getNItems()).thenReturn(0);
        when(columnView.getColumns()).thenReturn(mockColumns);

        fileListView = new FileListView(columnView, controller);
    }

    private void loadFilesInInsertionOrder() {
        fileCherry = ConversionFile.create(Path.of("/test/cherry.mp4"), FileFormat.MP4, 300L);
        fileApple = ConversionFile.create(Path.of("/test/apple.mp4"), FileFormat.MP4, 100L);
        fileBanana = ConversionFile.create(Path.of("/test/banana.mp4"), FileFormat.MP4, 200L);

        // Insertion order: cherry, apple, banana (deliberately not alphabetical)
        fileListView.setFiles(List.of(fileCherry, fileApple, fileBanana));
    }

    /**
     * Simulates a header-click sort by file name ascending: the GTK model chain
     * (SortListModel inside the MultiSelection) is re-sorted to visual order
     * [apple, banana, cherry] while the insertion-ordered Java list stays as
     * [cherry, apple, banana].
     */
    private void applyNameAscendingSort() throws Exception {
        Map<String, String> idToName = new HashMap<>();
        idToName.put(fileApple.id(), "apple");
        idToName.put(fileBanana.id(), "banana");
        idToName.put(fileCherry.id(), "cherry");

        SortListModel sortListModel = (SortListModel) readField("sortListModel");
        sortListModel.setSorter(new CustomSorter((a, b) -> idToName
                .get(new StringObject(a).getString())
                .compareTo(idToName.get(new StringObject(b).getString()))));
    }

    private Object readField(String name) throws Exception {
        Field field = FileListView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(fileListView);
    }

    @SuppressWarnings("unchecked")
    private MultiSelection<StringObject> selectionModel() throws Exception {
        return (MultiSelection<StringObject>) readField("selectionModel");
    }

    @Test
    @DisplayName("Selecting the first sorted row returns the visually first file, not the first inserted")
    void getSelectedFileIds_sortedList_returnsVisuallySelectedFile() throws Exception {
        loadFilesInInsertionOrder();
        applyNameAscendingSort();

        // Sanity: the selection model really presents sorted (visual) order
        var selectionModel = selectionModel();
        assertEquals(3, selectionModel.getNItems());
        assertEquals(fileApple.id(), ((StringObject) selectionModel.getItem(0)).getString());
        assertEquals(fileBanana.id(), ((StringObject) selectionModel.getItem(1)).getString());
        assertEquals(fileCherry.id(), ((StringObject) selectionModel.getItem(2)).getString());

        // Select visual row 0 (apple)
        selectionModel.selectItem(0, false);

        List<String> selected = fileListView.getSelectedFileIds();

        // Current buggy behavior maps position 0 back to files.get(0) = cherry
        assertEquals(List.of(fileApple.id()), selected,
                "selection positions are visual (sorted) positions and must resolve to the sorted model's item");
    }

    @Test
    @DisplayName("Multi-selection on a sorted list returns exactly the visually selected files")
    void getSelectedFileIds_sortedList_multiSelection() throws Exception {
        loadFilesInInsertionOrder();
        applyNameAscendingSort();

        var selectionModel = selectionModel();
        selectionModel.selectItem(0, false); // apple (visual)
        selectionModel.selectItem(2, false); // cherry (visual)

        List<String> selected = fileListView.getSelectedFileIds();

        assertEquals(2, selected.size());
        assertTrue(selected.contains(fileApple.id()), "apple (visual row 0) must be selected");
        assertTrue(selected.contains(fileCherry.id()), "cherry (visual row 2) must be selected");
        assertTrue(!selected.contains(fileBanana.id()), "banana (visual row 1) must not be selected");
    }

    @Test
    @DisplayName("Descending sort maps selection positions through the reversed visual order")
    void getSelectedFileIds_sortedList_descending() throws Exception {
        loadFilesInInsertionOrder();

        Map<String, String> idToName = new HashMap<>();
        idToName.put(fileApple.id(), "apple");
        idToName.put(fileBanana.id(), "banana");
        idToName.put(fileCherry.id(), "cherry");

        SortListModel sortListModel = (SortListModel) readField("sortListModel");
        sortListModel.setSorter(new CustomSorter((a, b) -> idToName
                .get(new StringObject(b).getString())
                .compareTo(idToName.get(new StringObject(a).getString())))); // descending

        var selectionModel = selectionModel();
        assertEquals(fileCherry.id(), ((StringObject) selectionModel.getItem(0)).getString());

        selectionModel.selectItem(1, false); // visual row 1 = banana

        assertEquals(List.of(fileBanana.id()), fileListView.getSelectedFileIds());
    }

    @Test
    @DisplayName("Unsorted list keeps insertion-order selection semantics")
    void getSelectedFileIds_unsorted_keepsInsertionOrder() throws Exception {
        loadFilesInInsertionOrder();

        var selectionModel = selectionModel();
        selectionModel.selectItem(0, false);

        assertEquals(List.of(fileCherry.id()), fileListView.getSelectedFileIds());
    }

    @Test
    @DisplayName("Empty selection returns an empty list")
    void getSelectedFileIds_emptySelection_returnsEmptyList() throws Exception {
        loadFilesInInsertionOrder();
        applyNameAscendingSort();

        List<String> selected = fileListView.getSelectedFileIds();

        assertTrue(selected.isEmpty(), "no selection must yield no ids");
    }

    @Test
    @DisplayName("Select all on a sorted list returns every file exactly once")
    void getSelectedFileIds_selectAll_sorted() throws Exception {
        loadFilesInInsertionOrder();
        applyNameAscendingSort();

        fileListView.selectAll();

        List<String> selected = fileListView.getSelectedFileIds();
        assertEquals(3, selected.size());
        assertTrue(selected.contains(fileApple.id()));
        assertTrue(selected.contains(fileBanana.id()));
        assertTrue(selected.contains(fileCherry.id()));
    }
}
