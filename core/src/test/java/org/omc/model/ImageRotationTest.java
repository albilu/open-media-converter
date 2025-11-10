// filepath: src/test/java/org/omc/model/ImageRotationTest.java

package org.omc.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ImageRotation} enum.
 * 
 * <p>
 * Tests all enum constants, degree values, display names,
 * and the isNone() method.
 * </p>
 * 
 * <p>
 * Requirements: REQ-IMG-1.1
 * </p>
 */
@DisplayName("ImageRotation Enum Tests")
class ImageRotationTest {

        @Test
        @DisplayName("NONE has null degrees")
        void none_HasNullDegrees() {
                assertNull(ImageRotation.NONE.getDegrees(),
                                "NONE should have null degrees");
        }

        @Test
        @DisplayName("NONE.isNone() returns true")
        void none_IsNoneReturnsTrue() {
                assertTrue(ImageRotation.NONE.isNone(),
                                "NONE.isNone() should return true");
        }

        @Test
        @DisplayName("NONE has correct display name")
        void none_HasCorrectDisplayName() {
                assertEquals("None", ImageRotation.NONE.getDisplayName(),
                                "NONE display name should be 'None'");
        }

        @Test
        @DisplayName("CLOCKWISE_90 has correct degrees (90)")
        void clockwise90_HasCorrectDegrees() {
                assertEquals(90, ImageRotation.CLOCKWISE_90.getDegrees(),
                                "CLOCKWISE_90 should have 90 degrees");
        }

        @Test
        @DisplayName("CLOCKWISE_90 has correct display name")
        void clockwise90_HasCorrectDisplayName() {
                assertEquals("90° Clockwise", ImageRotation.CLOCKWISE_90.getDisplayName(),
                                "CLOCKWISE_90 display name should be '90° Clockwise'");
        }

        @Test
        @DisplayName("CLOCKWISE_90.isNone() returns false")
        void clockwise90_IsNoneReturnsFalse() {
                assertFalse(ImageRotation.CLOCKWISE_90.isNone(),
                                "CLOCKWISE_90.isNone() should return false");
        }

        @Test
        @DisplayName("ROTATE_180 has correct degrees (180)")
        void rotate180_HasCorrectDegrees() {
                assertEquals(180, ImageRotation.ROTATE_180.getDegrees(),
                                "ROTATE_180 should have 180 degrees");
        }

        @Test
        @DisplayName("ROTATE_180 has correct display name")
        void rotate180_HasCorrectDisplayName() {
                assertEquals("180°", ImageRotation.ROTATE_180.getDisplayName(),
                                "ROTATE_180 display name should be '180°'");
        }

        @Test
        @DisplayName("ROTATE_180.isNone() returns false")
        void rotate180_IsNoneReturnsFalse() {
                assertFalse(ImageRotation.ROTATE_180.isNone(),
                                "ROTATE_180.isNone() should return false");
        }

        @Test
        @DisplayName("COUNTER_CLOCKWISE_90 has correct degrees (270)")
        void counterClockwise90_HasCorrectDegrees() {
                assertEquals(270, ImageRotation.COUNTER_CLOCKWISE_90.getDegrees(),
                                "COUNTER_CLOCKWISE_90 should have 270 degrees");
        }

        @Test
        @DisplayName("COUNTER_CLOCKWISE_90 has correct display name")
        void counterClockwise90_HasCorrectDisplayName() {
                assertEquals("90° Counter-Clockwise", ImageRotation.COUNTER_CLOCKWISE_90.getDisplayName(),
                                "COUNTER_CLOCKWISE_90 display name should be '90° Counter-Clockwise'");
        }

        @Test
        @DisplayName("COUNTER_CLOCKWISE_90.isNone() returns false")
        void counterClockwise90_IsNoneReturnsFalse() {
                assertFalse(ImageRotation.COUNTER_CLOCKWISE_90.isNone(),
                                "COUNTER_CLOCKWISE_90.isNone() should return false");
        }

        @Test
        @DisplayName("All enum constants are defined (4 total)")
        void allEnumConstants_AreDefined() {
                ImageRotation[] values = ImageRotation.values();
                assertEquals(4, values.length,
                                "ImageRotation should have exactly 4 enum constants");
        }

        @ParameterizedTest
        @EnumSource(value = ImageRotation.class, names = { "CLOCKWISE_90", "ROTATE_180", "COUNTER_CLOCKWISE_90" })
        @DisplayName("All non-NONE rotations have non-null degree values")
        void nonNoneRotations_HaveNonNullDegreeValues(ImageRotation rotation) {
                assertNotNull(rotation.getDegrees(),
                                rotation.name() + " should have a non-null degree value");
        }

        @ParameterizedTest
        @EnumSource(value = ImageRotation.class, names = { "CLOCKWISE_90", "ROTATE_180", "COUNTER_CLOCKWISE_90" })
        @DisplayName("All non-NONE rotations have isNone() return false")
        void nonNoneRotations_IsNoneReturnsFalse(ImageRotation rotation) {
                assertFalse(rotation.isNone(),
                                rotation.name() + ".isNone() should return false");
        }

        @ParameterizedTest
        @EnumSource(ImageRotation.class)
        @DisplayName("All enum constants have non-null display names")
        void allEnumConstants_HaveNonNullDisplayNames(ImageRotation rotation) {
                assertNotNull(rotation.getDisplayName(),
                                rotation.name() + " should have a non-null display name");
        }

        @ParameterizedTest
        @EnumSource(ImageRotation.class)
        @DisplayName("All enum constants have non-empty display names")
        void allEnumConstants_HaveNonEmptyDisplayNames(ImageRotation rotation) {
                assertFalse(rotation.getDisplayName().isEmpty(),
                                rotation.name() + " should have a non-empty display name");
        }

        @Test
        @DisplayName("Enum constants can be retrieved by name")
        void enumConstants_CanBeRetrievedByName() {
                assertEquals(ImageRotation.NONE, ImageRotation.valueOf("NONE"),
                                "valueOf('NONE') should return NONE");
                assertEquals(ImageRotation.CLOCKWISE_90, ImageRotation.valueOf("CLOCKWISE_90"),
                                "valueOf('CLOCKWISE_90') should return CLOCKWISE_90");
                assertEquals(ImageRotation.ROTATE_180, ImageRotation.valueOf("ROTATE_180"),
                                "valueOf('ROTATE_180') should return ROTATE_180");
                assertEquals(ImageRotation.COUNTER_CLOCKWISE_90, ImageRotation.valueOf("COUNTER_CLOCKWISE_90"),
                                "valueOf('COUNTER_CLOCKWISE_90') should return COUNTER_CLOCKWISE_90");
        }

        @Test
        @DisplayName("Degree values are valid rotation angles for all non-NONE constants")
        void allDegreeValues_AreValidRotationAngles() {
                for (ImageRotation rotation : ImageRotation.values()) {
                        if (!rotation.isNone()) {
                                Integer degrees = rotation.getDegrees();
                                assertTrue(degrees >= 0 && degrees <= 360,
                                                rotation.name() + " degrees should be between 0 and 360");
                                assertTrue(degrees % 90 == 0,
                                                rotation.name() + " degrees should be a multiple of 90");
                        }
                }
        }

        @Test
        @DisplayName("All rotation degrees are unique except for NONE")
        void allRotationDegrees_AreUnique() {
                assertEquals(90, ImageRotation.CLOCKWISE_90.getDegrees(),
                                "CLOCKWISE_90 should be 90 degrees");
                assertEquals(180, ImageRotation.ROTATE_180.getDegrees(),
                                "ROTATE_180 should be 180 degrees");
                assertEquals(270, ImageRotation.COUNTER_CLOCKWISE_90.getDegrees(),
                                "COUNTER_CLOCKWISE_90 should be 270 degrees");

                // Verify they're all different
                assertNotEquals(ImageRotation.CLOCKWISE_90.getDegrees(),
                                ImageRotation.ROTATE_180.getDegrees(),
                                "90° and 180° should have different degree values");
                assertNotEquals(ImageRotation.CLOCKWISE_90.getDegrees(),
                                ImageRotation.COUNTER_CLOCKWISE_90.getDegrees(),
                                "90° clockwise and counter-clockwise should have different degree values");
                assertNotEquals(ImageRotation.ROTATE_180.getDegrees(),
                                ImageRotation.COUNTER_CLOCKWISE_90.getDegrees(),
                                "180° and 270° should have different degree values");
        }

        @Test
        @DisplayName("CLOCKWISE_90 rotates portrait to landscape")
        void clockwise90_RotatesPortraitToLandscape() {
                // 90 degrees clockwise converts portrait (9:16) to landscape (16:9)
                assertEquals(90, ImageRotation.CLOCKWISE_90.getDegrees(),
                                "90° clockwise rotation should use 90 degrees");
        }

        @Test
        @DisplayName("COUNTER_CLOCKWISE_90 is equivalent to 270° clockwise")
        void counterClockwise90_IsEquivalentTo270Clockwise() {
                assertEquals(270, ImageRotation.COUNTER_CLOCKWISE_90.getDegrees(),
                                "90° counter-clockwise should be represented as 270° clockwise");
        }

        @Test
        @DisplayName("ROTATE_180 inverts image orientation")
        void rotate180_InvertsImageOrientation() {
                assertEquals(180, ImageRotation.ROTATE_180.getDegrees(),
                                "180° rotation should use 180 degrees");
        }
}
