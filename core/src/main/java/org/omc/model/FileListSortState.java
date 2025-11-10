// filepath: src/main/java/org/omc/model/FileListSortState.java

package org.omc.model;

import java.util.Comparator;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the sort state of the file list in the UI.
 * 
 * <p>
 * This immutable record stores which column the file list is sorted by and in
 * which direction.
 * The sort state is persisted across sessions as part of ApplicationState to
 * maintain the user's
 * preferred file list ordering.
 * </p>
 * 
 * <p>
 * <b>Usage Example:</b>
 * </p>
 * 
 * <pre>{@code
 * // Create default unsorted state
 * FileListSortState unsorted = FileListSortState.unsorted();
 * 
 * // Create sorted by name ascending
 * FileListSortState byName = FileListSortState.byName(SortDirection.ASCENDING);
 * 
 * // Toggle sort direction
 * FileListSortState toggled = byName.toggleDirection();
 * 
 * // Change sort field
 * FileListSortState bySize = byName.withSortField(SortField.SIZE);
 * 
 * // Create comparator for sorting
 * Comparator<ConversionFile> comparator = bySize.createComparator();
 * }</pre>
 * 
 * <p>
 * <b>Design Decisions:</b>
 * </p>
 * <ul>
 * <li>Immutable record pattern for thread safety and simplicity</li>
 * <li>Fluent withXxx() methods return new instances</li>
 * <li>Null sortField indicates unsorted state</li>
 * <li>Natural string sorting for NAME field (case-insensitive,
 * alphanumeric)</li>
 * <li>Special case: "Not Set" output formats sort to end</li>
 * </ul>
 * 
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>REQ-FL-4.5: Sort state persistence across application sessions</li>
 * <li>REQ-FL-4.1: Sortable columns in file list</li>
 * <li>REQ-FL-4.2: Natural sorting for file names</li>
 * </ul>
 * 
 * @see SortField
 * @see SortDirection
 * @see ApplicationState
 */
public record FileListSortState(
        @JsonProperty("sortField") SortField sortField,
        @JsonProperty("sortDirection") SortDirection sortDir) {

    /**
     * Enum representing sortable columns in the file list.
     * 
     * <p>
     * Requirement REQ-FL-4.1: Sortable columns
     * </p>
     */
    public enum SortField {
        /** Sort by file name (natural alphanumeric ordering) */
        NAME,

        /** Sort by file size in bytes */
        SIZE,

        /** Sort by input file format */
        FORMAT,

        /** Sort by output format or preset name */
        OUTPUT_FORMAT
    }

    /**
     * Enum representing sort direction.
     * 
     * <p>
     * Requirement REQ-FL-4.1: Ascending and descending sort
     * </p>
     */
    public enum SortDirection {
        /** Sort ascending (A-Z, 0-9, smallest to largest) */
        ASCENDING,

        /** Sort descending (Z-A, 9-0, largest to smallest) */
        DESCENDING
    }

    /**
     * JSON constructor for deserialization.
     * 
     * @param sortField The field to sort by (null for unsorted)
     * @param sortDir   The sort direction (defaults to ASCENDING if null)
     */
    @JsonCreator
    public FileListSortState(
            @JsonProperty("sortField") SortField sortField,
            @JsonProperty("sortDirection") SortDirection sortDir) {
        this.sortField = sortField;
        this.sortDir = sortDir != null ? sortDir : SortDirection.ASCENDING;
    }

    /**
     * Creates an unsorted state (no active sort).
     * 
     * @return FileListSortState with null sortField
     */
    public static FileListSortState unsorted() {
        return new FileListSortState(null, SortDirection.ASCENDING);
    }

    /**
     * Creates a state sorted by name.
     * 
     * @param dir The sort direction
     * @return FileListSortState sorted by NAME
     */
    public static FileListSortState byName(SortDirection dir) {
        return new FileListSortState(SortField.NAME, dir);
    }

    /**
     * Creates a state sorted by size.
     * 
     * @param dir The sort direction
     * @return FileListSortState sorted by SIZE
     */
    public static FileListSortState bySize(SortDirection dir) {
        return new FileListSortState(SortField.SIZE, dir);
    }

    /**
     * Creates a state sorted by format.
     * 
     * @param dir The sort direction
     * @return FileListSortState sorted by FORMAT
     */
    public static FileListSortState byFormat(SortDirection dir) {
        return new FileListSortState(SortField.FORMAT, dir);
    }

    /**
     * Creates a state sorted by output format.
     * 
     * @param dir The sort direction
     * @return FileListSortState sorted by OUTPUT_FORMAT
     */
    public static FileListSortState byOutputFormat(SortDirection dir) {
        return new FileListSortState(SortField.OUTPUT_FORMAT, dir);
    }

    /**
     * Checks if this state represents an active sort.
     * 
     * @return true if sortField is non-null, false for unsorted state
     */
    @JsonIgnore
    public boolean isSorted() {
        return sortField != null;
    }

    /**
     * Returns a new state with the specified sort field.
     * 
     * @param field The new sort field (null for unsorted)
     * @return A new FileListSortState with updated field
     */
    public FileListSortState withSortField(SortField field) {
        return new FileListSortState(field, sortDir);
    }

    /**
     * Returns a new state with the specified sort direction.
     * 
     * @param dir The new sort direction
     * @return A new FileListSortState with updated direction
     * @throws IllegalArgumentException if dir is null
     */
    public FileListSortState withSortDirection(SortDirection dir) {
        Objects.requireNonNull(dir, "Sort direction cannot be null");
        return new FileListSortState(sortField, dir);
    }

    /**
     * Returns a new state with the sort direction toggled.
     * 
     * <p>
     * ASCENDING becomes DESCENDING, and vice versa.
     * </p>
     * 
     * @return A new FileListSortState with opposite direction
     */
    public FileListSortState toggleDirection() {
        SortDirection newDir = (sortDir == SortDirection.ASCENDING)
                ? SortDirection.DESCENDING
                : SortDirection.ASCENDING;
        return new FileListSortState(sortField, newDir);
    }

    /**
     * Creates a Comparator for ConversionFile based on this sort state.
     * 
     * <p>
     * If the sort state is unsorted (sortField is null), returns a comparator
     * that maintains insertion order (always returns 0).
     * </p>
     * 
     * <p>
     * <b>Sort Behavior:</b>
     * </p>
     * <ul>
     * <li><b>NAME:</b> Natural alphanumeric sort (case-insensitive)</li>
     * <li><b>SIZE:</b> Numeric comparison of file sizes</li>
     * <li><b>FORMAT:</b> Alphabetic comparison of format names
     * (case-insensitive)</li>
     * <li><b>OUTPUT_FORMAT:</b> Alphabetic with "Not Set" sorting to end</li>
     * </ul>
     * 
     * <p>
     * Requirement REQ-FL-4.1: Sortable columns implementation
     * </p>
     * <p>
     * Requirement REQ-FL-4.2: Natural string sorting for names
     * </p>
     * 
     * @return A Comparator that can be used to sort ConversionFile lists
     */
    public Comparator<ConversionFile> createComparator() {
        if (!isSorted()) {
            // Unsorted - maintain insertion order
            return (f1, f2) -> 0;
        }

        Comparator<ConversionFile> comparator = switch (sortField) {
            case NAME -> Comparator.comparing(
                    ConversionFile::fileName,
                    this::compareNatural);

            case SIZE -> Comparator.comparingLong(ConversionFile::size);

            case FORMAT -> Comparator.comparing(
                    f -> f.format().name(),
                    String.CASE_INSENSITIVE_ORDER);

            case OUTPUT_FORMAT -> Comparator.comparing(
                    this::resolveOutputFormat,
                    this::compareOutputFormat);
        };

        // Apply direction
        if (sortDir == SortDirection.DESCENDING) {
            comparator = comparator.reversed();
        }

        return comparator;
    }

    /**
     * Natural string comparison for file names.
     * 
     * <p>
     * Implements alphanumeric sorting where numeric parts are compared numerically.
     * This ensures proper ordering (e.g., file1, file2, file10 instead of file1,
     * file10, file2).
     * </p>
     * 
     * <p>
     * Algorithm: Split strings into alternating text and numeric chunks, compare
     * chunk by chunk.
     * Text chunks are compared case-insensitively, numeric chunks are compared
     * numerically.
     * </p>
     * 
     * <p>
     * Requirement REQ-FL-4.2: Natural alphanumeric sorting
     * </p>
     * 
     * @param s1 First string
     * @param s2 Second string
     * @return Negative, zero, or positive integer as s1 is less than, equal to, or
     *         greater than s2
     */
    private int compareNatural(String s1, String s2) {
        int idx1 = 0, idx2 = 0;
        int len1 = s1.length(), len2 = s2.length();

        while (idx1 < len1 && idx2 < len2) {
            // Extract next chunk (either digits or non-digits)
            boolean isDigit1 = Character.isDigit(s1.charAt(idx1));
            boolean isDigit2 = Character.isDigit(s2.charAt(idx2));

            // If one is digit and other is not, digit comes first
            if (isDigit1 != isDigit2) {
                return isDigit1 ? -1 : 1;
            }

            if (isDigit1) {
                // Both are digit chunks - compare numerically
                int start1 = idx1;
                while (idx1 < len1 && Character.isDigit(s1.charAt(idx1))) {
                    idx1++;
                }

                int start2 = idx2;
                while (idx2 < len2 && Character.isDigit(s2.charAt(idx2))) {
                    idx2++;
                }

                // Parse as long to handle large numbers
                try {
                    long num1 = Long.parseLong(s1.substring(start1, idx1));
                    long num2 = Long.parseLong(s2.substring(start2, idx2));
                    int cmp = Long.compare(num1, num2);
                    if (cmp != 0) {
                        return cmp;
                    }
                } catch (NumberFormatException e) {
                    // Fallback to string comparison if number too large
                    int cmp = s1.substring(start1, idx1).compareTo(s2.substring(start2, idx2));
                    if (cmp != 0) {
                        return cmp;
                    }
                }
            } else {
                // Both are text chunks - compare case-insensitively
                int start1 = idx1;
                while (idx1 < len1 && !Character.isDigit(s1.charAt(idx1))) {
                    idx1++;
                }

                int start2 = idx2;
                while (idx2 < len2 && !Character.isDigit(s2.charAt(idx2))) {
                    idx2++;
                }

                int cmp = s1.substring(start1, idx1).compareToIgnoreCase(s2.substring(start2, idx2));
                if (cmp != 0) {
                    return cmp;
                }
            }
        }

        // All chunks equal - shorter string comes first
        return Integer.compare(len1, len2);
    }

    /**
     * Resolves the output format string for a file.
     * 
     * <p>
     * Returns "Not Set" if the file has no configured output format.
     * This is a simplified version that checks for custom settings override.
     * </p>
     * 
     * @param file The conversion file
     * @return Output format name or "Not Set"
     */
    private String resolveOutputFormat(ConversionFile file) {
        if (file.settingsOverride() != null) {
            var override = file.settingsOverride();
            if (override.presetName() != null) {
                return override.presetName();
            }
            // Try to extract format from settings
            return extractFormatFromOverride(override);
        }
        return "Not Set"; // Simplified - full implementation would check global settings
    }

    /**
     * Extracts format name from settings override.
     * 
     * @param override The settings override
     * @return Format name or "Not Set"
     */
    private String extractFormatFromOverride(FileSettingsOverride override) {
        if (override.videoSettings() != null && override.videoSettings().outputFormat() != null) {
            return override.videoSettings().outputFormat().name();
        } else if (override.audioSettings() != null && override.audioSettings().outputFormat() != null) {
            return override.audioSettings().outputFormat().name();
        } else if (override.imageSettings() != null && override.imageSettings().outputFormat() != null) {
            return override.imageSettings().outputFormat().name();
        } else if (override.documentSettings() != null && override.documentSettings().outputFormat() != null) {
            return override.documentSettings().outputFormat().name();
        }
        return "Not Set";
    }

    /**
     * Compares output format strings with special handling for "Not Set".
     * 
     * <p>
     * "Not Set" values are always sorted to the end for ASCENDING order.
     * When reversed by the comparator (for DESCENDING), "Not Set" will naturally
     * end up at the start,
     * which is incorrect. To fix this, we need to apply the special handling in the
     * base comparator,
     * and then manually handle reversal for "Not Set" values.
     * </p>
     * 
     * <p>
     * Actually, for simplicity: "Not Set" always returns a large value (like
     * comparing to "zzz"),
     * so when reversed, it still sorts to the opposite end correctly.
     * </p>
     * 
     * @param format1 First format string
     * @param format2 Second format string
     * @return Comparison result
     */
    private int compareOutputFormat(String format1, String format2) {
        boolean isNotSet1 = "Not Set".equals(format1);
        boolean isNotSet2 = "Not Set".equals(format2);

        // Both "Not Set" - equal
        if (isNotSet1 && isNotSet2) {
            return 0;
        }

        // "Not Set" values should always sort to the end in ASCENDING
        // When the comparator is reversed for DESCENDING, this ensures they stay at the
        // end
        // Solution: Make "Not Set" always compare as "greater than" any normal value
        // but when reversed, we need special handling.
        // Better approach: Always push "Not Set" to the far end by returning extreme
        // values
        if (isNotSet1) {
            return sortDir == SortDirection.ASCENDING ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        }

        if (isNotSet2) {
            return sortDir == SortDirection.ASCENDING ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }

        // Normal case-insensitive comparison
        return format1.compareToIgnoreCase(format2);
    }

    @Override
    public String toString() {
        if (!isSorted()) {
            return "FileListSortState{unsorted}";
        }
        return String.format("FileListSortState{sortField=%s, sortDir=%s}", sortField, sortDir);
    }
}
