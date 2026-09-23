package org.omc.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FormatCategory enum.
 */
class FormatCategoryTest {

    @Test
    void testEnumValues() {
        // The category set is a contract: tool routing, section settings and
        // format classification all depend on it
        FormatCategory[] values = FormatCategory.values();
        assertEquals(5, values.length, "Should have exactly 5 categories");

        assertNotNull(FormatCategory.VIDEO);
        assertNotNull(FormatCategory.AUDIO);
        assertNotNull(FormatCategory.IMAGE);
        assertNotNull(FormatCategory.DOCUMENT);
        assertNotNull(FormatCategory.UNKNOWN);
    }
}
