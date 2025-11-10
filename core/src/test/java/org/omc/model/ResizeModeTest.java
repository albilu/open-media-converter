// filepath: src/test/java/org/omc/model/ResizeModeTest.java

package org.omc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ResizeMode enum.
 * 
 * <p>
 * Tests cover:
 * - Enum values and display names
 * - JSON serialization/deserialization via fromDisplayName
 * - toString() method
 * - All enum constants
 * 
 * <p>
 * Requirement: REQ-4.8 (Image resize mode dropdown population)
 */
@DisplayName("ResizeMode Tests")
class ResizeModeTest {

    // ===========================
    // Enum Values Tests
    // ===========================

    @Test
    @DisplayName("All enum values are defined")
    void allEnumValues_AreDefined() {
        ResizeMode[] values = ResizeMode.values();

        assertEquals(8, values.length, "Should have 8 resize modes");
        assertNotNull(ResizeMode.valueOf("NONE"));
        assertNotNull(ResizeMode.valueOf("FIT"));
        assertNotNull(ResizeMode.valueOf("FILL"));
        assertNotNull(ResizeMode.valueOf("STRETCH"));
        assertNotNull(ResizeMode.valueOf("LANCZOS"));
        assertNotNull(ResizeMode.valueOf("BICUBIC"));
        assertNotNull(ResizeMode.valueOf("BILINEAR"));
        assertNotNull(ResizeMode.valueOf("NEAREST_NEIGHBOR"));
    }

    @Test
    @DisplayName("NONE has correct display name")
    void none_HasCorrectDisplayName() {
        assertEquals("None", ResizeMode.NONE.getDisplayName());
        assertEquals("None", ResizeMode.NONE.toString());
    }

    @Test
    @DisplayName("FIT has correct display name")
    void fit_HasCorrectDisplayName() {
        assertEquals("Fit (maintain aspect)", ResizeMode.FIT.getDisplayName());
        assertEquals("Fit (maintain aspect)", ResizeMode.FIT.toString());
    }

    @Test
    @DisplayName("FILL has correct display name")
    void fill_HasCorrectDisplayName() {
        assertEquals("Fill (crop)", ResizeMode.FILL.getDisplayName());
        assertEquals("Fill (crop)", ResizeMode.FILL.toString());
    }

    @Test
    @DisplayName("STRETCH has correct display name")
    void stretch_HasCorrectDisplayName() {
        assertEquals("Stretch", ResizeMode.STRETCH.getDisplayName());
        assertEquals("Stretch", ResizeMode.STRETCH.toString());
    }

    @Test
    @DisplayName("LANCZOS has correct display name")
    void lanczos_HasCorrectDisplayName() {
        assertEquals("Lanczos", ResizeMode.LANCZOS.getDisplayName());
        assertEquals("Lanczos", ResizeMode.LANCZOS.toString());
    }

    @Test
    @DisplayName("BICUBIC has correct display name")
    void bicubic_HasCorrectDisplayName() {
        assertEquals("Bicubic", ResizeMode.BICUBIC.getDisplayName());
        assertEquals("Bicubic", ResizeMode.BICUBIC.toString());
    }

    @Test
    @DisplayName("BILINEAR has correct display name")
    void bilinear_HasCorrectDisplayName() {
        assertEquals("Bilinear", ResizeMode.BILINEAR.getDisplayName());
        assertEquals("Bilinear", ResizeMode.BILINEAR.toString());
    }

    @Test
    @DisplayName("NEAREST_NEIGHBOR has correct display name")
    void nearestNeighbor_HasCorrectDisplayName() {
        assertEquals("Nearest Neighbor", ResizeMode.NEAREST_NEIGHBOR.getDisplayName());
        assertEquals("Nearest Neighbor", ResizeMode.NEAREST_NEIGHBOR.toString());
    }

    // ===========================
    // fromDisplayName Tests
    // ===========================

    @Test
    @DisplayName("fromDisplayName returns correct enum for 'None'")
    void fromDisplayName_WithNone_ReturnsNone() {
        assertEquals(ResizeMode.NONE, ResizeMode.fromDisplayName("None"));
    }

    @Test
    @DisplayName("fromDisplayName returns correct enum for 'Fit (maintain aspect)'")
    void fromDisplayName_WithFit_ReturnsFit() {
        assertEquals(ResizeMode.FIT, ResizeMode.fromDisplayName("Fit (maintain aspect)"));
    }

    @Test
    @DisplayName("fromDisplayName returns correct enum for 'Fill (crop)'")
    void fromDisplayName_WithFill_ReturnsFill() {
        assertEquals(ResizeMode.FILL, ResizeMode.fromDisplayName("Fill (crop)"));
    }

    @Test
    @DisplayName("fromDisplayName returns correct enum for 'Stretch'")
    void fromDisplayName_WithStretch_ReturnsStretch() {
        assertEquals(ResizeMode.STRETCH, ResizeMode.fromDisplayName("Stretch"));
    }

    @Test
    @DisplayName("fromDisplayName returns correct enum for 'Lanczos'")
    void fromDisplayName_WithLanczos_ReturnsLanczos() {
        assertEquals(ResizeMode.LANCZOS, ResizeMode.fromDisplayName("Lanczos"));
    }

    @Test
    @DisplayName("fromDisplayName returns correct enum for 'Bicubic'")
    void fromDisplayName_WithBicubic_ReturnsBicubic() {
        assertEquals(ResizeMode.BICUBIC, ResizeMode.fromDisplayName("Bicubic"));
    }

    @Test
    @DisplayName("fromDisplayName returns correct enum for 'Bilinear'")
    void fromDisplayName_WithBilinear_ReturnsBilinear() {
        assertEquals(ResizeMode.BILINEAR, ResizeMode.fromDisplayName("Bilinear"));
    }

    @Test
    @DisplayName("fromDisplayName returns correct enum for 'Nearest Neighbor'")
    void fromDisplayName_WithNearestNeighbor_ReturnsNearestNeighbor() {
        assertEquals(ResizeMode.NEAREST_NEIGHBOR, ResizeMode.fromDisplayName("Nearest Neighbor"));
    }

    @Test
    @DisplayName("fromDisplayName throws exception for unknown display name")
    void fromDisplayName_WithUnknownDisplayName_ThrowsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ResizeMode.fromDisplayName("Invalid Mode"));

        assertTrue(exception.getMessage().contains("Unknown resize mode"));
        assertTrue(exception.getMessage().contains("Invalid Mode"));
    }

    @Test
    @DisplayName("fromDisplayName throws exception for null")
    void fromDisplayName_WithNull_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> ResizeMode.fromDisplayName(null));
    }

    // ===========================
    // JSON Serialization Round-Trip Tests
    // ===========================

    @Test
    @DisplayName("toString returns display name for all modes")
    void toString_ReturnsDisplayNameForAllModes() {
        for (ResizeMode mode : ResizeMode.values()) {
            assertEquals(mode.getDisplayName(), mode.toString());
        }
    }

    @Test
    @DisplayName("fromDisplayName round-trip works for all modes")
    void fromDisplayName_RoundTrip_WorksForAllModes() {
        for (ResizeMode mode : ResizeMode.values()) {
            String displayName = mode.getDisplayName();
            ResizeMode parsed = ResizeMode.fromDisplayName(displayName);
            assertEquals(mode, parsed, "Round-trip should work for " + mode);
        }
    }

    // ===========================
    // Edge Cases
    // ===========================

    @Test
    @DisplayName("fromDisplayName is case-sensitive")
    void fromDisplayName_IsCaseSensitive() {
        assertThrows(IllegalArgumentException.class, () -> ResizeMode.fromDisplayName("none"));
        assertThrows(IllegalArgumentException.class, () -> ResizeMode.fromDisplayName("NONE"));
        assertThrows(IllegalArgumentException.class, () -> ResizeMode.fromDisplayName("lanczos"));
    }

    @Test
    @DisplayName("Display names are unique")
    void displayNames_AreUnique() {
        ResizeMode[] modes = ResizeMode.values();
        for (int i = 0; i < modes.length; i++) {
            for (int j = i + 1; j < modes.length; j++) {
                assertNotEquals(
                        modes[i].getDisplayName(),
                        modes[j].getDisplayName(),
                        "Display names should be unique: " + modes[i] + " vs " + modes[j]);
            }
        }
    }

    @Test
    @DisplayName("Enum constants are unique")
    void enumConstants_AreUnique() {
        ResizeMode[] modes = ResizeMode.values();
        for (int i = 0; i < modes.length; i++) {
            for (int j = i + 1; j < modes.length; j++) {
                assertNotSame(
                        modes[i],
                        modes[j],
                        "Enum constants should be unique: " + modes[i] + " vs " + modes[j]);
            }
        }
    }

    @Test
    @DisplayName("Display names are not empty")
    void displayNames_AreNotEmpty() {
        for (ResizeMode mode : ResizeMode.values()) {
            assertFalse(mode.getDisplayName().isEmpty(), "Display name should not be empty for " + mode);
        }
    }
}
