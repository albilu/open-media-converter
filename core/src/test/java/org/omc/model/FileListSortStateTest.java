// filepath: src/test/java/org/omc/model/FileListSortStateTest.java

package org.omc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.omc.util.JsonUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for FileListSortState.
 * 
 * <p>
 * Requirements: REQ-FL-4.5
 * </p>
 */
@DisplayName("FileListSortState Tests")
class FileListSortStateTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonUtils.getObjectMapper();
    }

    // ========== Factory Methods Tests ==========

    @Test
    @DisplayName("unsorted() creates state with null sortField")
    void testUnsorted() {
        FileListSortState state = FileListSortState.unsorted();

        assertNotNull(state);
        assertNull(state.sortField());
        assertEquals(FileListSortState.SortDirection.ASCENDING, state.sortDir());
        assertFalse(state.isSorted());
    }

    @Test
    @DisplayName("byName() creates state sorted by NAME")
    void testByName() {
        FileListSortState ascending = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);

        assertEquals(FileListSortState.SortField.NAME, ascending.sortField());
        assertEquals(FileListSortState.SortDirection.ASCENDING, ascending.sortDir());
        assertTrue(ascending.isSorted());

        FileListSortState descending = FileListSortState.byName(FileListSortState.SortDirection.DESCENDING);
        assertEquals(FileListSortState.SortDirection.DESCENDING, descending.sortDir());
    }

    @Test
    @DisplayName("bySize() creates state sorted by SIZE")
    void testBySize() {
        FileListSortState state = FileListSortState.bySize(FileListSortState.SortDirection.ASCENDING);

        assertEquals(FileListSortState.SortField.SIZE, state.sortField());
        assertEquals(FileListSortState.SortDirection.ASCENDING, state.sortDir());
        assertTrue(state.isSorted());
    }

    @Test
    @DisplayName("byFormat() creates state sorted by FORMAT")
    void testByFormat() {
        FileListSortState state = FileListSortState.byFormat(FileListSortState.SortDirection.DESCENDING);

        assertEquals(FileListSortState.SortField.FORMAT, state.sortField());
        assertEquals(FileListSortState.SortDirection.DESCENDING, state.sortDir());
        assertTrue(state.isSorted());
    }

    @Test
    @DisplayName("byOutputFormat() creates state sorted by OUTPUT_FORMAT")
    void testByOutputFormat() {
        FileListSortState state = FileListSortState.byOutputFormat(FileListSortState.SortDirection.ASCENDING);

        assertEquals(FileListSortState.SortField.OUTPUT_FORMAT, state.sortField());
        assertEquals(FileListSortState.SortDirection.ASCENDING, state.sortDir());
        assertTrue(state.isSorted());
    }

    // ========== Immutable withXxx Methods Tests ==========

    @Test
    @DisplayName("withSortField() returns new instance with updated field")
    void testWithSortField() {
        FileListSortState original = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);
        FileListSortState updated = original.withSortField(FileListSortState.SortField.SIZE);

        // Original unchanged
        assertEquals(FileListSortState.SortField.NAME, original.sortField());

        // New instance has updated field
        assertEquals(FileListSortState.SortField.SIZE, updated.sortField());
        assertEquals(FileListSortState.SortDirection.ASCENDING, updated.sortDir());

        // Different instances
        assertNotSame(original, updated);
    }

    @Test
    @DisplayName("withSortField(null) creates unsorted state")
    void testWithSortFieldNull() {
        FileListSortState sorted = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);
        FileListSortState unsorted = sorted.withSortField(null);

        assertNull(unsorted.sortField());
        assertFalse(unsorted.isSorted());
    }

    @Test
    @DisplayName("withSortDirection() returns new instance with updated direction")
    void testWithSortDirection() {
        FileListSortState original = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);
        FileListSortState updated = original.withSortDirection(FileListSortState.SortDirection.DESCENDING);

        // Original unchanged
        assertEquals(FileListSortState.SortDirection.ASCENDING, original.sortDir());

        // New instance has updated direction
        assertEquals(FileListSortState.SortDirection.DESCENDING, updated.sortDir());
        assertEquals(FileListSortState.SortField.NAME, updated.sortField());

        // Different instances
        assertNotSame(original, updated);
    }

    @Test
    @DisplayName("withSortDirection(null) throws IllegalArgumentException")
    void testWithSortDirectionNull() {
        FileListSortState state = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);

        assertThrows(NullPointerException.class, () -> state.withSortDirection(null));
    }

    @Test
    @DisplayName("toggleDirection() flips direction")
    void testToggleDirection() {
        FileListSortState ascending = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);
        FileListSortState descending = ascending.toggleDirection();

        assertEquals(FileListSortState.SortDirection.DESCENDING, descending.sortDir());
        assertEquals(FileListSortState.SortField.NAME, descending.sortField());

        // Toggle again
        FileListSortState backToAscending = descending.toggleDirection();
        assertEquals(FileListSortState.SortDirection.ASCENDING, backToAscending.sortDir());
    }

    // ========== Comparator Creation Tests ==========

    @Test
    @DisplayName("createComparator() for unsorted state returns no-op comparator")
    void testCreateComparatorUnsorted() {
        FileListSortState unsorted = FileListSortState.unsorted();
        Comparator<ConversionFile> comparator = unsorted.createComparator();

        ConversionFile file1 = createTestFile("file1.mp4", 1000);
        ConversionFile file2 = createTestFile("file2.mp4", 2000);

        // No-op comparator always returns 0
        assertEquals(0, comparator.compare(file1, file2));
        assertEquals(0, comparator.compare(file2, file1));
    }

    @Test
    @DisplayName("createComparator() sorts by NAME using natural ordering")
    void testCreateComparatorByName() {
        FileListSortState state = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);
        Comparator<ConversionFile> comparator = state.createComparator();

        List<ConversionFile> files = List.of(
                createTestFile("file10.mp4", 1000),
                createTestFile("file2.mp4", 2000),
                createTestFile("file1.mp4", 3000),
                createTestFile("File3.mp4", 4000) // Case variation
        );

        List<ConversionFile> sorted = new ArrayList<>(files);
        sorted.sort(comparator);

        // Natural sort: file1, file2, File3, file10 (not file1, file10, file2)
        assertEquals("file1.mp4", sorted.get(0).fileName());
        assertEquals("file2.mp4", sorted.get(1).fileName());
        assertEquals("File3.mp4", sorted.get(2).fileName());
        assertEquals("file10.mp4", sorted.get(3).fileName());
    }

    @Test
    @DisplayName("createComparator() sorts by NAME descending")
    void testCreateComparatorByNameDescending() {
        FileListSortState state = FileListSortState.byName(FileListSortState.SortDirection.DESCENDING);
        Comparator<ConversionFile> comparator = state.createComparator();

        List<ConversionFile> files = List.of(
                createTestFile("file1.mp4", 1000),
                createTestFile("file2.mp4", 2000),
                createTestFile("file10.mp4", 3000));

        List<ConversionFile> sorted = new ArrayList<>(files);
        sorted.sort(comparator);

        // Descending: file10, file2, file1
        assertEquals("file10.mp4", sorted.get(0).fileName());
        assertEquals("file2.mp4", sorted.get(1).fileName());
        assertEquals("file1.mp4", sorted.get(2).fileName());
    }

    @Test
    @DisplayName("createComparator() sorts by SIZE")
    void testCreateComparatorBySize() {
        FileListSortState state = FileListSortState.bySize(FileListSortState.SortDirection.ASCENDING);
        Comparator<ConversionFile> comparator = state.createComparator();

        List<ConversionFile> files = List.of(
                createTestFile("large.mp4", 5000000),
                createTestFile("small.mp4", 100),
                createTestFile("medium.mp4", 10000));

        List<ConversionFile> sorted = new ArrayList<>(files);
        sorted.sort(comparator);

        // Ascending by size: small, medium, large
        assertEquals("small.mp4", sorted.get(0).fileName());
        assertEquals("medium.mp4", sorted.get(1).fileName());
        assertEquals("large.mp4", sorted.get(2).fileName());
    }

    @Test
    @DisplayName("createComparator() sorts by SIZE descending")
    void testCreateComparatorBySizeDescending() {
        FileListSortState state = FileListSortState.bySize(FileListSortState.SortDirection.DESCENDING);
        Comparator<ConversionFile> comparator = state.createComparator();

        List<ConversionFile> files = List.of(
                createTestFile("small.mp4", 100),
                createTestFile("large.mp4", 5000000),
                createTestFile("medium.mp4", 10000));

        List<ConversionFile> sorted = new ArrayList<>(files);
        sorted.sort(comparator);

        // Descending by size: large, medium, small
        assertEquals("large.mp4", sorted.get(0).fileName());
        assertEquals("medium.mp4", sorted.get(1).fileName());
        assertEquals("small.mp4", sorted.get(2).fileName());
    }

    @Test
    @DisplayName("createComparator() sorts by FORMAT")
    void testCreateComparatorByFormat() {
        FileListSortState state = FileListSortState.byFormat(FileListSortState.SortDirection.ASCENDING);
        Comparator<ConversionFile> comparator = state.createComparator();

        List<ConversionFile> files = List.of(
                createTestFileWithFormat("video.mp4", FileFormat.MP4),
                createTestFileWithFormat("audio.mp3", FileFormat.MP3),
                createTestFileWithFormat("video.avi", FileFormat.AVI));

        List<ConversionFile> sorted = new ArrayList<>(files);
        sorted.sort(comparator);

        // Alphabetically: AVI, MP3, MP4
        assertEquals(FileFormat.AVI, sorted.get(0).format());
        assertEquals(FileFormat.MP3, sorted.get(1).format());
        assertEquals(FileFormat.MP4, sorted.get(2).format());
    }

    @Test
    @DisplayName("createComparator() sorts by OUTPUT_FORMAT with 'Not Set' at end")
    void testCreateComparatorByOutputFormat() {
        FileListSortState state = FileListSortState.byOutputFormat(FileListSortState.SortDirection.ASCENDING);
        Comparator<ConversionFile> comparator = state.createComparator();

        // Create files with different output formats
        ConversionFile noSettings = createTestFile("no-settings.mp4", 1000);
        ConversionFile withPreset = createTestFileWithPreset("with-preset.mp4", "HD Video");
        ConversionFile withFormat = createTestFileWithOutputFormat("with-format.mp4", FileFormat.MP4);

        List<ConversionFile> files = List.of(withPreset, noSettings, withFormat);
        List<ConversionFile> sorted = new ArrayList<>(files);
        sorted.sort(comparator);

        // "Not Set" should be at the end
        assertEquals("with-preset.mp4", sorted.get(0).fileName()); // "HD Video"
        assertEquals("with-format.mp4", sorted.get(1).fileName()); // "MP4"
        assertEquals("no-settings.mp4", sorted.get(2).fileName()); // "Not Set"
    }

    @Test
    @DisplayName("createComparator() sorts by OUTPUT_FORMAT descending keeps 'Not Set' at end")
    void testCreateComparatorByOutputFormatDescending() {
        FileListSortState state = FileListSortState.byOutputFormat(FileListSortState.SortDirection.DESCENDING);
        Comparator<ConversionFile> comparator = state.createComparator();

        ConversionFile noSettings = createTestFile("no-settings.mp4", 1000);
        ConversionFile withPreset = createTestFileWithPreset("with-preset.mp4", "HD Video");
        ConversionFile withFormat = createTestFileWithOutputFormat("with-format.mp4", FileFormat.MP4);

        List<ConversionFile> files = List.of(withPreset, noSettings, withFormat);
        List<ConversionFile> sorted = new ArrayList<>(files);
        sorted.sort(comparator);

        // Descending: MP4, HD Video, then "Not Set" at end
        assertEquals("with-format.mp4", sorted.get(0).fileName()); // "MP4"
        assertEquals("with-preset.mp4", sorted.get(1).fileName()); // "HD Video"
        assertEquals("no-settings.mp4", sorted.get(2).fileName()); // "Not Set" (always at end)
    }

    // ========== JSON Serialization Tests ==========

    @Test
    @DisplayName("Serialize and deserialize sorted state")
    void testSerializationSorted() throws Exception {
        FileListSortState original = FileListSortState.byName(FileListSortState.SortDirection.DESCENDING);

        String json = objectMapper.writeValueAsString(original);
        FileListSortState deserialized = objectMapper.readValue(json, FileListSortState.class);

        assertEquals(original.sortField(), deserialized.sortField());
        assertEquals(original.sortDir(), deserialized.sortDir());
        assertEquals(original, deserialized);
    }

    @Test
    @DisplayName("Serialize and deserialize unsorted state")
    void testSerializationUnsorted() throws Exception {
        FileListSortState original = FileListSortState.unsorted();

        String json = objectMapper.writeValueAsString(original);
        FileListSortState deserialized = objectMapper.readValue(json, FileListSortState.class);

        assertNull(deserialized.sortField());
        assertEquals(FileListSortState.SortDirection.ASCENDING, deserialized.sortDir());
        assertEquals(original, deserialized);
    }

    @Test
    @DisplayName("Deserialize with missing sortDirection defaults to ASCENDING")
    void testDeserializationMissingDirection() throws Exception {
        String json = "{\"sortField\":\"NAME\"}";

        FileListSortState deserialized = objectMapper.readValue(json, FileListSortState.class);

        assertEquals(FileListSortState.SortField.NAME, deserialized.sortField());
        assertEquals(FileListSortState.SortDirection.ASCENDING, deserialized.sortDir());
    }

    @Test
    @DisplayName("Deserialize with null sortField")
    void testDeserializationNullSortField() throws Exception {
        String json = "{\"sortField\":null,\"sortDirection\":\"DESCENDING\"}";

        FileListSortState deserialized = objectMapper.readValue(json, FileListSortState.class);

        assertNull(deserialized.sortField());
        assertEquals(FileListSortState.SortDirection.DESCENDING, deserialized.sortDir());
        assertFalse(deserialized.isSorted());
    }

    // ========== Equals and HashCode Tests ==========

    @Test
    @DisplayName("equals() returns true for identical states")
    void testEquals() {
        FileListSortState state1 = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);
        FileListSortState state2 = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);

        assertEquals(state1, state2);
        assertEquals(state1.hashCode(), state2.hashCode());
    }

    @Test
    @DisplayName("equals() returns false for different sortFields")
    void testNotEqualsDifferentField() {
        FileListSortState state1 = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);
        FileListSortState state2 = FileListSortState.bySize(FileListSortState.SortDirection.ASCENDING);

        assertNotEquals(state1, state2);
    }

    @Test
    @DisplayName("equals() returns false for different sortDirections")
    void testNotEqualsDifferentDirection() {
        FileListSortState state1 = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);
        FileListSortState state2 = FileListSortState.byName(FileListSortState.SortDirection.DESCENDING);

        assertNotEquals(state1, state2);
    }

    @Test
    @DisplayName("equals() returns true for two unsorted states")
    void testEqualsUnsorted() {
        FileListSortState state1 = FileListSortState.unsorted();
        FileListSortState state2 = new FileListSortState(null, FileListSortState.SortDirection.ASCENDING);

        assertEquals(state1, state2);
    }

    // ========== toString() Tests ==========

    @Test
    @DisplayName("toString() for sorted state")
    void testToStringSorted() {
        FileListSortState state = FileListSortState.byName(FileListSortState.SortDirection.DESCENDING);

        String str = state.toString();

        assertTrue(str.contains("NAME"));
        assertTrue(str.contains("DESCENDING"));
        assertTrue(str.contains("FileListSortState"));
    }

    @Test
    @DisplayName("toString() for unsorted state")
    void testToStringUnsorted() {
        FileListSortState state = FileListSortState.unsorted();

        String str = state.toString();

        assertTrue(str.contains("unsorted"));
        assertTrue(str.contains("FileListSortState"));
    }

    // ========== Enum Tests ==========

    @Test
    @DisplayName("SortField enum has all expected values")
    void testSortFieldEnum() {
        assertEquals(4, FileListSortState.SortField.values().length);
        assertNotNull(FileListSortState.SortField.valueOf("NAME"));
        assertNotNull(FileListSortState.SortField.valueOf("SIZE"));
        assertNotNull(FileListSortState.SortField.valueOf("FORMAT"));
        assertNotNull(FileListSortState.SortField.valueOf("OUTPUT_FORMAT"));
    }

    @Test
    @DisplayName("SortDirection enum has all expected values")
    void testSortDirectionEnum() {
        assertEquals(2, FileListSortState.SortDirection.values().length);
        assertNotNull(FileListSortState.SortDirection.valueOf("ASCENDING"));
        assertNotNull(FileListSortState.SortDirection.valueOf("DESCENDING"));
    }

    // ========== Helper Methods ==========

    private ConversionFile createTestFile(String fileName, long size) {
        return ConversionFile.create(
                java.nio.file.Path.of("/test/" + fileName),
                FileFormat.MP4,
                size);
    }

    private ConversionFile createTestFileWithFormat(String fileName, FileFormat format) {
        return ConversionFile.create(
                java.nio.file.Path.of("/test/" + fileName),
                format,
                1000L);
    }

    private ConversionFile createTestFileWithPreset(String fileName, String presetName) {
        VideoSettings settings = VideoSettings.builder()
                .outputFormat(FileFormat.MP4)
                .build();

        FileSettingsOverride override = FileSettingsOverride.forVideo(presetName, settings);

        return ConversionFile.create(
                java.nio.file.Path.of("/test/" + fileName),
                FileFormat.MP4,
                1000L).withSettingsOverride(override);
    }

    private ConversionFile createTestFileWithOutputFormat(String fileName, FileFormat outputFormat) {
        VideoSettings settings = VideoSettings.builder()
                .outputFormat(outputFormat)
                .build();

        FileSettingsOverride override = FileSettingsOverride.forVideo(null, settings);

        return ConversionFile.create(
                java.nio.file.Path.of("/test/" + fileName),
                FileFormat.MP4,
                1000L).withSettingsOverride(override);
    }
}
