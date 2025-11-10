// filepath: src/test/java/org/omc/model/ImageFlipTest.java

package org.omc.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ImageFlip} enum.
 * 
 * <p>
 * Tests all enum constants, flip flag values, display names,
 * and the isNone() method.
 * </p>
 * 
 * <p>
 * Requirements: REQ-IMG-2.1
 * </p>
 */
@DisplayName("ImageFlip Enum Tests")
class ImageFlipTest {

    @Test
    @DisplayName("NONE has no horizontal flip")
    void none_HasNoHorizontalFlip() {
        assertFalse(ImageFlip.NONE.isFlipHorizontal(),
                "NONE should have flipHorizontal = false");
    }

    @Test
    @DisplayName("NONE has no vertical flip")
    void none_HasNoVerticalFlip() {
        assertFalse(ImageFlip.NONE.isFlipVertical(),
                "NONE should have flipVertical = false");
    }

    @Test
    @DisplayName("NONE.isNone() returns true")
    void none_IsNoneReturnsTrue() {
        assertTrue(ImageFlip.NONE.isNone(),
                "NONE.isNone() should return true");
    }

    @Test
    @DisplayName("NONE has correct display name")
    void none_HasCorrectDisplayName() {
        assertEquals("None", ImageFlip.NONE.getDisplayName(),
                "NONE display name should be 'None'");
    }

    @Test
    @DisplayName("HORIZONTAL has horizontal flip enabled")
    void horizontal_HasHorizontalFlipEnabled() {
        assertTrue(ImageFlip.HORIZONTAL.isFlipHorizontal(),
                "HORIZONTAL should have flipHorizontal = true");
    }

    @Test
    @DisplayName("HORIZONTAL has no vertical flip")
    void horizontal_HasNoVerticalFlip() {
        assertFalse(ImageFlip.HORIZONTAL.isFlipVertical(),
                "HORIZONTAL should have flipVertical = false");
    }

    @Test
    @DisplayName("HORIZONTAL.isNone() returns false")
    void horizontal_IsNoneReturnsFalse() {
        assertFalse(ImageFlip.HORIZONTAL.isNone(),
                "HORIZONTAL.isNone() should return false");
    }

    @Test
    @DisplayName("HORIZONTAL has correct display name")
    void horizontal_HasCorrectDisplayName() {
        assertEquals("Horizontal", ImageFlip.HORIZONTAL.getDisplayName(),
                "HORIZONTAL display name should be 'Horizontal'");
    }

    @Test
    @DisplayName("VERTICAL has vertical flip enabled")
    void vertical_HasVerticalFlipEnabled() {
        assertTrue(ImageFlip.VERTICAL.isFlipVertical(),
                "VERTICAL should have flipVertical = true");
    }

    @Test
    @DisplayName("VERTICAL has no horizontal flip")
    void vertical_HasNoHorizontalFlip() {
        assertFalse(ImageFlip.VERTICAL.isFlipHorizontal(),
                "VERTICAL should have flipHorizontal = false");
    }

    @Test
    @DisplayName("VERTICAL.isNone() returns false")
    void vertical_IsNoneReturnsFalse() {
        assertFalse(ImageFlip.VERTICAL.isNone(),
                "VERTICAL.isNone() should return false");
    }

    @Test
    @DisplayName("VERTICAL has correct display name")
    void vertical_HasCorrectDisplayName() {
        assertEquals("Vertical", ImageFlip.VERTICAL.getDisplayName(),
                "VERTICAL display name should be 'Vertical'");
    }

    @Test
    @DisplayName("BOTH has horizontal flip enabled")
    void both_HasHorizontalFlipEnabled() {
        assertTrue(ImageFlip.BOTH.isFlipHorizontal(),
                "BOTH should have flipHorizontal = true");
    }

    @Test
    @DisplayName("BOTH has vertical flip enabled")
    void both_HasVerticalFlipEnabled() {
        assertTrue(ImageFlip.BOTH.isFlipVertical(),
                "BOTH should have flipVertical = true");
    }

    @Test
    @DisplayName("BOTH.isNone() returns false")
    void both_IsNoneReturnsFalse() {
        assertFalse(ImageFlip.BOTH.isNone(),
                "BOTH.isNone() should return false");
    }

    @Test
    @DisplayName("BOTH has correct display name")
    void both_HasCorrectDisplayName() {
        assertEquals("Both", ImageFlip.BOTH.getDisplayName(),
                "BOTH display name should be 'Both'");
    }

    @Test
    @DisplayName("All enum constants are defined (4 total)")
    void allEnumConstants_AreDefined() {
        ImageFlip[] values = ImageFlip.values();
        assertEquals(4, values.length,
                "ImageFlip should have exactly 4 enum constants");
    }

    @ParameterizedTest
    @EnumSource(value = ImageFlip.class, names = { "HORIZONTAL", "VERTICAL", "BOTH" })
    @DisplayName("All non-NONE flips have isNone() return false")
    void nonNoneFlips_IsNoneReturnsFalse(ImageFlip flip) {
        assertFalse(flip.isNone(),
                flip.name() + ".isNone() should return false");
    }

    @ParameterizedTest
    @EnumSource(ImageFlip.class)
    @DisplayName("All enum constants have non-null display names")
    void allEnumConstants_HaveNonNullDisplayNames(ImageFlip flip) {
        assertNotNull(flip.getDisplayName(),
                flip.name() + " should have a non-null display name");
    }

    @ParameterizedTest
    @EnumSource(ImageFlip.class)
    @DisplayName("All enum constants have non-empty display names")
    void allEnumConstants_HaveNonEmptyDisplayNames(ImageFlip flip) {
        assertFalse(flip.getDisplayName().isEmpty(),
                flip.name() + " should have a non-empty display name");
    }

    @Test
    @DisplayName("Enum constants can be retrieved by name")
    void enumConstants_CanBeRetrievedByName() {
        assertEquals(ImageFlip.NONE, ImageFlip.valueOf("NONE"),
                "valueOf('NONE') should return NONE");
        assertEquals(ImageFlip.HORIZONTAL, ImageFlip.valueOf("HORIZONTAL"),
                "valueOf('HORIZONTAL') should return HORIZONTAL");
        assertEquals(ImageFlip.VERTICAL, ImageFlip.valueOf("VERTICAL"),
                "valueOf('VERTICAL') should return VERTICAL");
        assertEquals(ImageFlip.BOTH, ImageFlip.valueOf("BOTH"),
                "valueOf('BOTH') should return BOTH");
    }

    @Test
    @DisplayName("HORIZONTAL mirrors left-to-right only")
    void horizontal_MirrorsLeftToRightOnly() {
        assertTrue(ImageFlip.HORIZONTAL.isFlipHorizontal(),
                "HORIZONTAL should flip horizontally");
        assertFalse(ImageFlip.HORIZONTAL.isFlipVertical(),
                "HORIZONTAL should not flip vertically");
    }

    @Test
    @DisplayName("VERTICAL mirrors top-to-bottom only")
    void vertical_MirrorsTopToBottomOnly() {
        assertFalse(ImageFlip.VERTICAL.isFlipHorizontal(),
                "VERTICAL should not flip horizontally");
        assertTrue(ImageFlip.VERTICAL.isFlipVertical(),
                "VERTICAL should flip vertically");
    }

    @Test
    @DisplayName("BOTH is equivalent to 180-degree rotation")
    void both_IsEquivalentTo180Rotation() {
        assertTrue(ImageFlip.BOTH.isFlipHorizontal(),
                "BOTH should flip horizontally");
        assertTrue(ImageFlip.BOTH.isFlipVertical(),
                "BOTH should flip vertically");
    }

    @Test
    @DisplayName("NONE has no transformations")
    void none_HasNoTransformations() {
        assertFalse(ImageFlip.NONE.isFlipHorizontal(),
                "NONE should not flip horizontally");
        assertFalse(ImageFlip.NONE.isFlipVertical(),
                "NONE should not flip vertically");
        assertTrue(ImageFlip.NONE.isNone(),
                "NONE should return true for isNone()");
    }

    @Test
    @DisplayName("Each flip constant has unique combination of flags")
    void eachFlipConstant_HasUniqueFlagCombination() {
        // NONE: (false, false)
        assertFalse(ImageFlip.NONE.isFlipHorizontal());
        assertFalse(ImageFlip.NONE.isFlipVertical());

        // HORIZONTAL: (true, false)
        assertTrue(ImageFlip.HORIZONTAL.isFlipHorizontal());
        assertFalse(ImageFlip.HORIZONTAL.isFlipVertical());

        // VERTICAL: (false, true)
        assertFalse(ImageFlip.VERTICAL.isFlipHorizontal());
        assertTrue(ImageFlip.VERTICAL.isFlipVertical());

        // BOTH: (true, true)
        assertTrue(ImageFlip.BOTH.isFlipHorizontal());
        assertTrue(ImageFlip.BOTH.isFlipVertical());

        // Verify all four combinations are unique
        assertNotEquals(ImageFlip.NONE, ImageFlip.HORIZONTAL);
        assertNotEquals(ImageFlip.NONE, ImageFlip.VERTICAL);
        assertNotEquals(ImageFlip.NONE, ImageFlip.BOTH);
        assertNotEquals(ImageFlip.HORIZONTAL, ImageFlip.VERTICAL);
        assertNotEquals(ImageFlip.HORIZONTAL, ImageFlip.BOTH);
        assertNotEquals(ImageFlip.VERTICAL, ImageFlip.BOTH);
    }

    @Test
    @DisplayName("Flip flags cover all boolean combinations")
    void flipFlags_CoverAllBooleanCombinations() {
        // Verify that we have all 4 possible boolean combinations:
        // (false, false), (true, false), (false, true), (true, true)

        boolean foundFalseFalse = false;
        boolean foundTrueFalse = false;
        boolean foundFalseTrue = false;
        boolean foundTrueTrue = false;

        for (ImageFlip flip : ImageFlip.values()) {
            if (!flip.isFlipHorizontal() && !flip.isFlipVertical()) {
                foundFalseFalse = true;
            } else if (flip.isFlipHorizontal() && !flip.isFlipVertical()) {
                foundTrueFalse = true;
            } else if (!flip.isFlipHorizontal() && flip.isFlipVertical()) {
                foundFalseTrue = true;
            } else if (flip.isFlipHorizontal() && flip.isFlipVertical()) {
                foundTrueTrue = true;
            }
        }

        assertTrue(foundFalseFalse, "Should have flip with (false, false)");
        assertTrue(foundTrueFalse, "Should have flip with (true, false)");
        assertTrue(foundFalseTrue, "Should have flip with (false, true)");
        assertTrue(foundTrueTrue, "Should have flip with (true, true)");
    }
}
