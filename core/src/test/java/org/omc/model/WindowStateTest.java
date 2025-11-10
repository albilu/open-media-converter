// filepath: src/test/java/org/omc/model/WindowStateTest.java

package org.omc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for WindowState model.
 * Tests window state creation, validation, and immutability.
 *
 * Requirement REQ-005.1: Window geometry persistence.
 */
class WindowStateTest {

    @Test
    void defaultState_ShouldCreateValidDefaultWindowState() {
        // When: Create default state
        WindowState state = WindowState.defaultState();

        // Then: Should have default values
        assertNotNull(state);
        assertEquals(1000, state.width());
        assertEquals(700, state.height());
        assertEquals(100, state.x());
        assertEquals(100, state.y());
        assertFalse(state.maximized());
        assertFalse(state.fullscreen());
        assertTrue(state.isValid());
    }

    @Test
    void constructor_ShouldCreateStateWithGivenParameters() {
        // Given: Valid parameters
        int width = 1920;
        int height = 1080;
        int x = 200;
        int y = 150;
        boolean maximized = true;
        boolean fullscreen = false;

        // When: Create state
        WindowState state = new WindowState(width, height, x, y, maximized, fullscreen);

        // Then: Should have given values
        assertNotNull(state);
        assertEquals(width, state.width());
        assertEquals(height, state.height());
        assertEquals(x, state.x());
        assertEquals(y, state.y());
        assertEquals(maximized, state.maximized());
        assertEquals(fullscreen, state.fullscreen());
    }

    @Test
    void isValid_ShouldReturnTrueForValidDimensions() {
        // Given: Valid window state
        WindowState state = new WindowState(1200, 800, 100, 100, false, false);

        // When/Then: Should be valid
        assertTrue(state.isValid());
    }

    @Test
    void isValid_ShouldReturnFalseForInvalidWidth() {
        // Given: Invalid widths
        WindowState tooSmall = new WindowState(300, 600, 0, 0, false, false);
        WindowState tooLarge = new WindowState(8000, 600, 0, 0, false, false);

        // When/Then: Should be invalid
        assertFalse(tooSmall.isValid());
        assertFalse(tooLarge.isValid());
    }

    @Test
    void isValid_ShouldReturnFalseForInvalidHeight() {
        // Given: Invalid heights
        WindowState tooSmall = new WindowState(1000, 200, 0, 0, false, false);
        WindowState tooLarge = new WindowState(1000, 5000, 0, 0, false, false);

        // When/Then: Should be invalid
        assertFalse(tooSmall.isValid());
        assertFalse(tooLarge.isValid());
    }

    @Test
    void isValid_ShouldReturnFalseForInvalidPosition() {
        // Given: Invalid positions
        WindowState tooNegativeX = new WindowState(1000, 700, -8000, 0, false, false);
        WindowState tooNegativeY = new WindowState(1000, 700, 0, -5000, false, false);
        WindowState tooPositiveX = new WindowState(1000, 700, 8000, 0, false, false);
        WindowState tooPositiveY = new WindowState(1000, 700, 0, 5000, false, false);

        // When/Then: Should be invalid
        assertFalse(tooNegativeX.isValid());
        assertFalse(tooNegativeY.isValid());
        assertFalse(tooPositiveX.isValid());
        assertFalse(tooPositiveY.isValid());
    }

    @Test
    void isValid_ShouldAllowNegativePositionsForMultiMonitor() {
        // Given: Negative position (valid for multi-monitor setups)
        WindowState state = new WindowState(1000, 700, -100, -50, false, false);

        // When/Then: Should be valid
        assertTrue(state.isValid());
    }

    @Test
    void withSize_ShouldCreateNewStateWithUpdatedSize() {
        // Given: Original state
        WindowState original = WindowState.defaultState();

        // When: Update size
        WindowState updated = original.withSize(1600, 900);

        // Then: Should have new size, others unchanged
        assertNotNull(updated);
        assertNotSame(original, updated);
        assertEquals(1600, updated.width());
        assertEquals(900, updated.height());
        assertEquals(original.x(), updated.x());
        assertEquals(original.y(), updated.y());
        assertEquals(original.maximized(), updated.maximized());
        assertEquals(original.fullscreen(), updated.fullscreen());
    }

    @Test
    void withPosition_ShouldCreateNewStateWithUpdatedPosition() {
        // Given: Original state
        WindowState original = WindowState.defaultState();

        // When: Update position
        WindowState updated = original.withPosition(300, 200);

        // Then: Should have new position, others unchanged
        assertNotNull(updated);
        assertNotSame(original, updated);
        assertEquals(original.width(), updated.width());
        assertEquals(original.height(), updated.height());
        assertEquals(300, updated.x());
        assertEquals(200, updated.y());
        assertEquals(original.maximized(), updated.maximized());
        assertEquals(original.fullscreen(), updated.fullscreen());
    }

    @Test
    void withMaximized_ShouldCreateNewStateWithUpdatedMaximized() {
        // Given: Original state
        WindowState original = WindowState.defaultState();

        // When: Update maximized
        WindowState updated = original.withMaximized(true);

        // Then: Should have new maximized state, others unchanged
        assertNotNull(updated);
        assertNotSame(original, updated);
        assertEquals(original.width(), updated.width());
        assertEquals(original.height(), updated.height());
        assertEquals(original.x(), updated.x());
        assertEquals(original.y(), updated.y());
        assertTrue(updated.maximized());
        assertEquals(original.fullscreen(), updated.fullscreen());
    }

    @Test
    void withFullscreen_ShouldCreateNewStateWithUpdatedFullscreen() {
        // Given: Original state
        WindowState original = WindowState.defaultState();

        // When: Update fullscreen
        WindowState updated = original.withFullscreen(true);

        // Then: Should have new fullscreen state, others unchanged
        assertNotNull(updated);
        assertNotSame(original, updated);
        assertEquals(original.width(), updated.width());
        assertEquals(original.height(), updated.height());
        assertEquals(original.x(), updated.x());
        assertEquals(original.y(), updated.y());
        assertEquals(original.maximized(), updated.maximized());
        assertTrue(updated.fullscreen());
    }

    @Test
    void equals_ShouldReturnTrueForIdenticalStates() {
        // Given: Two identical states
        WindowState state1 = new WindowState(1200, 800, 100, 100, false, false);
        WindowState state2 = new WindowState(1200, 800, 100, 100, false, false);

        // When/Then: Should be equal
        assertEquals(state1, state2);
        assertEquals(state1.hashCode(), state2.hashCode());
    }

    @Test
    void equals_ShouldReturnFalseForDifferentStates() {
        // Given: Two different states
        WindowState state1 = WindowState.defaultState();
        WindowState state2 = new WindowState(1920, 1080, 200, 150, true, false);

        // When/Then: Should not be equal
        assertNotEquals(state1, state2);
    }

    @Test
    void equals_ShouldHandleSameInstance() {
        // Given: Single state
        WindowState state = WindowState.defaultState();

        // When/Then: Should equal itself
        assertEquals(state, state);
    }

    @Test
    void equals_ShouldHandleNull() {
        // Given: State
        WindowState state = WindowState.defaultState();

        // When/Then: Should not equal null
        assertNotEquals(state, null);
    }

    @Test
    void equals_ShouldHandleDifferentClass() {
        // Given: State and different object
        WindowState state = WindowState.defaultState();

        // When/Then: Should not equal different class
        assertNotEquals(state, "String object");
    }

    @Test
    void toString_ShouldIncludeAllFields() {
        // Given: State
        WindowState state = WindowState.defaultState();

        // When: Convert to string
        String str = state.toString();

        // Then: Should contain all key information
        assertNotNull(str);
        assertTrue(str.contains("width"));
        assertTrue(str.contains("height"));
        assertTrue(str.contains("x"));
        assertTrue(str.contains("y"));
        assertTrue(str.contains("maximized"));
        assertTrue(str.contains("fullscreen"));
    }
}