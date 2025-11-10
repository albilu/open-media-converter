package org.omc.model;

import org.omc.model.FormatCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FormatCategory enum.
 * Requirement REQ-006.1, REQ-006.2, REQ-006.3, REQ-006.4: Format
 * categorization.
 */
class FormatCategoryTest {

    @Test
    void testEnumValues() {
        // Test all expected values exist
        FormatCategory[] values = FormatCategory.values();
        assertEquals(5, values.length, "Should have exactly 5 category values");

        // Verify each value
        assertNotNull(FormatCategory.VIDEO);
        assertNotNull(FormatCategory.AUDIO);
        assertNotNull(FormatCategory.IMAGE);
        assertNotNull(FormatCategory.DOCUMENT);
        assertNotNull(FormatCategory.UNKNOWN);
    }

    @Test
    void testValueOf() {
        // Test valueOf for each category
        assertEquals(FormatCategory.VIDEO, FormatCategory.valueOf("VIDEO"));
        assertEquals(FormatCategory.AUDIO, FormatCategory.valueOf("AUDIO"));
        assertEquals(FormatCategory.IMAGE, FormatCategory.valueOf("IMAGE"));
        assertEquals(FormatCategory.DOCUMENT, FormatCategory.valueOf("DOCUMENT"));
        assertEquals(FormatCategory.UNKNOWN, FormatCategory.valueOf("UNKNOWN"));
    }

    @Test
    void testValueOfInvalidThrowsException() {
        // Test invalid value throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> FormatCategory.valueOf("INVALID"));
        assertThrows(IllegalArgumentException.class, () -> FormatCategory.valueOf("video"));
        assertThrows(IllegalArgumentException.class, () -> FormatCategory.valueOf(""));
    }

    @Test
    void testValueOfNullThrowsException() {
        // Test null throws NullPointerException
        assertThrows(NullPointerException.class, () -> FormatCategory.valueOf(null));
    }

    @Test
    void testEnumOrdering() {
        // Test that enum values maintain expected order
        FormatCategory[] values = FormatCategory.values();
        assertEquals(FormatCategory.VIDEO, values[0]);
        assertEquals(FormatCategory.AUDIO, values[1]);
        assertEquals(FormatCategory.IMAGE, values[2]);
        assertEquals(FormatCategory.DOCUMENT, values[3]);
        assertEquals(FormatCategory.UNKNOWN, values[4]);
    }

    @Test
    void testEnumEquality() {
        // Test enum equality
        FormatCategory cat1 = FormatCategory.VIDEO;
        FormatCategory cat2 = FormatCategory.valueOf("VIDEO");
        assertSame(cat1, cat2, "Enum values should be singletons");

        FormatCategory cat3 = FormatCategory.AUDIO;
        assertNotSame(cat1, cat3, "Different enum values should not be the same");
    }

    @Test
    void testEnumToString() {
        // Test toString returns enum name
        assertEquals("VIDEO", FormatCategory.VIDEO.toString());
        assertEquals("AUDIO", FormatCategory.AUDIO.toString());
        assertEquals("IMAGE", FormatCategory.IMAGE.toString());
        assertEquals("DOCUMENT", FormatCategory.DOCUMENT.toString());
        assertEquals("UNKNOWN", FormatCategory.UNKNOWN.toString());
    }

    @Test
    void testEnumName() {
        // Test name() method
        assertEquals("VIDEO", FormatCategory.VIDEO.name());
        assertEquals("AUDIO", FormatCategory.AUDIO.name());
        assertEquals("IMAGE", FormatCategory.IMAGE.name());
        assertEquals("DOCUMENT", FormatCategory.DOCUMENT.name());
        assertEquals("UNKNOWN", FormatCategory.UNKNOWN.name());
    }

    @Test
    void testEnumOrdinal() {
        // Test ordinal values
        assertEquals(0, FormatCategory.VIDEO.ordinal());
        assertEquals(1, FormatCategory.AUDIO.ordinal());
        assertEquals(2, FormatCategory.IMAGE.ordinal());
        assertEquals(3, FormatCategory.DOCUMENT.ordinal());
        assertEquals(4, FormatCategory.UNKNOWN.ordinal());
    }

    @Test
    void testEnumComparison() {
        // Test enum comparison (using ordinal)
        assertTrue(FormatCategory.VIDEO.compareTo(FormatCategory.AUDIO) < 0);
        assertTrue(FormatCategory.AUDIO.compareTo(FormatCategory.IMAGE) < 0);
        assertTrue(FormatCategory.IMAGE.compareTo(FormatCategory.DOCUMENT) < 0);
        assertTrue(FormatCategory.DOCUMENT.compareTo(FormatCategory.UNKNOWN) < 0);
        assertTrue(FormatCategory.UNKNOWN.compareTo(FormatCategory.VIDEO) > 0);
        assertEquals(0, FormatCategory.IMAGE.compareTo(FormatCategory.IMAGE));
    }

    @Test
    void testEnumInSwitch() {
        // Test enum can be used in switch statements
        String result = switch (FormatCategory.VIDEO) {
            case VIDEO -> "Movies";
            case AUDIO -> "Music";
            case IMAGE -> "Pictures";
            case DOCUMENT -> "Files";
            case UNKNOWN -> "Unknown";
        };
        assertEquals("Movies", result);
    }

    @Test
    void testEnumSerialization() {
        // Test that enum name can be serialized and deserialized
        FormatCategory original = FormatCategory.DOCUMENT;
        String serialized = original.name();
        FormatCategory deserialized = FormatCategory.valueOf(serialized);
        assertSame(original, deserialized);
    }

    @Test
    void testMediaCategories() {
        // Test that we have all expected media categories
        assertTrue(FormatCategory.VIDEO.name().startsWith("VIDEO"));
        assertTrue(FormatCategory.AUDIO.name().startsWith("AUDIO"));
        assertTrue(FormatCategory.IMAGE.name().startsWith("IMAGE"));
        assertTrue(FormatCategory.DOCUMENT.name().startsWith("DOCUMENT"));
    }

    @Test
    void testUnknownCategory() {
        // Test that UNKNOWN is distinct from other categories
        assertNotSame(FormatCategory.UNKNOWN, FormatCategory.VIDEO);
        assertNotSame(FormatCategory.UNKNOWN, FormatCategory.AUDIO);
        assertNotSame(FormatCategory.UNKNOWN, FormatCategory.IMAGE);
        assertNotSame(FormatCategory.UNKNOWN, FormatCategory.DOCUMENT);
    }

    @Test
    void testAllCategoriesInLoop() {
        // Test iteration over all categories
        int count = 0;
        for (FormatCategory category : FormatCategory.values()) {
            assertNotNull(category);
            assertNotNull(category.name());
            count++;
        }
        assertEquals(5, count, "Should iterate over all 5 categories");
    }
}
