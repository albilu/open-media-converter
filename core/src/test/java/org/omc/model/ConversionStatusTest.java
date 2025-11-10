package org.omc.model;

import org.omc.model.ConversionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConversionStatus enum.
 * Requirement REQ-004.2, REQ-004.3: Status tracking through conversion
 * lifecycle.
 */
class ConversionStatusTest {

    @Test
    void testEnumValues() {
        // Test all expected values exist
        ConversionStatus[] values = ConversionStatus.values();
        assertEquals(5, values.length, "Should have exactly 5 status values");

        // Verify each value
        assertNotNull(ConversionStatus.PENDING);
        assertNotNull(ConversionStatus.IN_PROGRESS);
        assertNotNull(ConversionStatus.COMPLETED);
        assertNotNull(ConversionStatus.FAILED);
        assertNotNull(ConversionStatus.CANCELLED);
    }

    @Test
    void testValueOf() {
        // Test valueOf for each status
        assertEquals(ConversionStatus.PENDING, ConversionStatus.valueOf("PENDING"));
        assertEquals(ConversionStatus.IN_PROGRESS, ConversionStatus.valueOf("IN_PROGRESS"));
        assertEquals(ConversionStatus.COMPLETED, ConversionStatus.valueOf("COMPLETED"));
        assertEquals(ConversionStatus.FAILED, ConversionStatus.valueOf("FAILED"));
        assertEquals(ConversionStatus.CANCELLED, ConversionStatus.valueOf("CANCELLED"));
    }

    @Test
    void testValueOfInvalidThrowsException() {
        // Test invalid value throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> ConversionStatus.valueOf("INVALID"));
        assertThrows(IllegalArgumentException.class, () -> ConversionStatus.valueOf("pending"));
        assertThrows(IllegalArgumentException.class, () -> ConversionStatus.valueOf(""));
    }

    @Test
    void testValueOfNullThrowsException() {
        // Test null throws NullPointerException
        assertThrows(NullPointerException.class, () -> ConversionStatus.valueOf(null));
    }

    @Test
    void testEnumOrdering() {
        // Test that enum values maintain expected order (lifecycle order)
        ConversionStatus[] values = ConversionStatus.values();
        assertEquals(ConversionStatus.PENDING, values[0]);
        assertEquals(ConversionStatus.IN_PROGRESS, values[1]);
        assertEquals(ConversionStatus.COMPLETED, values[2]);
        assertEquals(ConversionStatus.FAILED, values[3]);
        assertEquals(ConversionStatus.CANCELLED, values[4]);
    }

    @Test
    void testEnumEquality() {
        // Test enum equality
        ConversionStatus status1 = ConversionStatus.PENDING;
        ConversionStatus status2 = ConversionStatus.valueOf("PENDING");
        assertSame(status1, status2, "Enum values should be singletons");

        ConversionStatus status3 = ConversionStatus.COMPLETED;
        assertNotSame(status1, status3, "Different enum values should not be the same");
    }

    @Test
    void testEnumToString() {
        // Test toString returns enum name
        assertEquals("PENDING", ConversionStatus.PENDING.toString());
        assertEquals("IN_PROGRESS", ConversionStatus.IN_PROGRESS.toString());
        assertEquals("COMPLETED", ConversionStatus.COMPLETED.toString());
        assertEquals("FAILED", ConversionStatus.FAILED.toString());
        assertEquals("CANCELLED", ConversionStatus.CANCELLED.toString());
    }

    @Test
    void testEnumName() {
        // Test name() method
        assertEquals("PENDING", ConversionStatus.PENDING.name());
        assertEquals("IN_PROGRESS", ConversionStatus.IN_PROGRESS.name());
        assertEquals("COMPLETED", ConversionStatus.COMPLETED.name());
        assertEquals("FAILED", ConversionStatus.FAILED.name());
        assertEquals("CANCELLED", ConversionStatus.CANCELLED.name());
    }

    @Test
    void testEnumOrdinal() {
        // Test ordinal values
        assertEquals(0, ConversionStatus.PENDING.ordinal());
        assertEquals(1, ConversionStatus.IN_PROGRESS.ordinal());
        assertEquals(2, ConversionStatus.COMPLETED.ordinal());
        assertEquals(3, ConversionStatus.FAILED.ordinal());
        assertEquals(4, ConversionStatus.CANCELLED.ordinal());
    }

    @Test
    void testEnumComparison() {
        // Test enum comparison (using ordinal)
        assertTrue(ConversionStatus.PENDING.compareTo(ConversionStatus.IN_PROGRESS) < 0);
        assertTrue(ConversionStatus.COMPLETED.compareTo(ConversionStatus.FAILED) < 0);
        assertTrue(ConversionStatus.FAILED.compareTo(ConversionStatus.PENDING) > 0);
        assertEquals(0, ConversionStatus.CANCELLED.compareTo(ConversionStatus.CANCELLED));
    }

    @Test
    void testEnumInSwitch() {
        // Test enum can be used in switch statements
        String result = switch (ConversionStatus.PENDING) {
            case PENDING -> "Waiting";
            case IN_PROGRESS -> "Converting";
            case COMPLETED -> "Done";
            case FAILED -> "Error";
            case CANCELLED -> "Cancelled";
        };
        assertEquals("Waiting", result);
    }

    @Test
    void testEnumSerialization() {
        // Test that enum name can be serialized and deserialized
        ConversionStatus original = ConversionStatus.IN_PROGRESS;
        String serialized = original.name();
        ConversionStatus deserialized = ConversionStatus.valueOf(serialized);
        assertSame(original, deserialized);
    }
}
