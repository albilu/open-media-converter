// filepath: src/test/java/org/omc/model/AspectRatioTest.java

package org.omc.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AspectRatio} enum.
 * 
 * <p>
 * Tests all enum constants, ratio calculations, display names,
 * and the isOriginal() method.
 * </p>
 * 
 * <p>
 * Requirements: REQ-VID-2.1
 * </p>
 */
@DisplayName("AspectRatio Enum Tests")
class AspectRatioTest {

        @Test
        @DisplayName("KEEP_ORIGINAL has null ratio")
        void keepOriginal_HasNullRatio() {
                assertNull(AspectRatio.KEEP_ORIGINAL.getRatio(),
                                "KEEP_ORIGINAL should have null ratio");
        }

        @Test
        @DisplayName("KEEP_ORIGINAL.isOriginal() returns true")
        void keepOriginal_IsOriginalReturnsTrue() {
                assertTrue(AspectRatio.KEEP_ORIGINAL.isOriginal(),
                                "KEEP_ORIGINAL.isOriginal() should return true");
        }

        @Test
        @DisplayName("KEEP_ORIGINAL has correct display name")
        void keepOriginal_HasCorrectDisplayName() {
                assertEquals("Keep Original", AspectRatio.KEEP_ORIGINAL.getDisplayName(),
                                "KEEP_ORIGINAL display name should be 'Keep Original'");
        }

        @Test
        @DisplayName("RATIO_16_9 has correct ratio calculation")
        void ratio16_9_HasCorrectRatio() {
                double expected = 16.0 / 9.0;
                assertEquals(expected, AspectRatio.RATIO_16_9.getRatio(), 0.0001,
                                "16:9 ratio should equal 1.777...");
        }

        @Test
        @DisplayName("RATIO_16_9 has correct display name")
        void ratio16_9_HasCorrectDisplayName() {
                assertEquals("16:9 (Widescreen)", AspectRatio.RATIO_16_9.getDisplayName(),
                                "16:9 display name should be '16:9 (Widescreen)'");
        }

        @Test
        @DisplayName("RATIO_16_9.isOriginal() returns false")
        void ratio16_9_IsOriginalReturnsFalse() {
                assertFalse(AspectRatio.RATIO_16_9.isOriginal(),
                                "RATIO_16_9.isOriginal() should return false");
        }

        @Test
        @DisplayName("RATIO_4_3 has correct ratio calculation")
        void ratio4_3_HasCorrectRatio() {
                double expected = 4.0 / 3.0;
                assertEquals(expected, AspectRatio.RATIO_4_3.getRatio(), 0.0001,
                                "4:3 ratio should equal 1.333...");
        }

        @Test
        @DisplayName("RATIO_4_3 has correct display name")
        void ratio4_3_HasCorrectDisplayName() {
                assertEquals("4:3 (Standard)", AspectRatio.RATIO_4_3.getDisplayName(),
                                "4:3 display name should be '4:3 (Standard)'");
        }

        @Test
        @DisplayName("RATIO_1_1 has correct ratio (exactly 1.0)")
        void ratio1_1_HasCorrectRatio() {
                assertEquals(1.0, AspectRatio.RATIO_1_1.getRatio(), 0.0001,
                                "1:1 ratio should equal 1.0 (square)");
        }

        @Test
        @DisplayName("RATIO_1_1 has correct display name")
        void ratio1_1_HasCorrectDisplayName() {
                assertEquals("1:1 (Square)", AspectRatio.RATIO_1_1.getDisplayName(),
                                "1:1 display name should be '1:1 (Square)'");
        }

        @Test
        @DisplayName("RATIO_21_9 has correct ratio calculation")
        void ratio21_9_HasCorrectRatio() {
                double expected = 21.0 / 9.0;
                assertEquals(expected, AspectRatio.RATIO_21_9.getRatio(), 0.0001,
                                "21:9 ratio should equal 2.333...");
        }

        @Test
        @DisplayName("RATIO_21_9 has correct display name")
        void ratio21_9_HasCorrectDisplayName() {
                assertEquals("21:9 (Ultrawide)", AspectRatio.RATIO_21_9.getDisplayName(),
                                "21:9 display name should be '21:9 (Ultrawide)'");
        }

        @Test
        @DisplayName("RATIO_9_16 has correct ratio calculation")
        void ratio9_16_HasCorrectRatio() {
                double expected = 9.0 / 16.0;
                assertEquals(expected, AspectRatio.RATIO_9_16.getRatio(), 0.0001,
                                "9:16 ratio should equal 0.5625 (vertical)");
        }

        @Test
        @DisplayName("RATIO_9_16 has correct display name")
        void ratio9_16_HasCorrectDisplayName() {
                assertEquals("9:16 (Vertical)", AspectRatio.RATIO_9_16.getDisplayName(),
                                "9:16 display name should be '9:16 (Vertical)'");
        }

        @Test
        @DisplayName("RATIO_3_2 has correct ratio calculation")
        void ratio3_2_HasCorrectRatio() {
                double expected = 3.0 / 2.0;
                assertEquals(expected, AspectRatio.RATIO_3_2.getRatio(), 0.0001,
                                "3:2 ratio should equal 1.5");
        }

        @Test
        @DisplayName("RATIO_3_2 has correct display name")
        void ratio3_2_HasCorrectDisplayName() {
                assertEquals("3:2", AspectRatio.RATIO_3_2.getDisplayName(),
                                "3:2 display name should be '3:2'");
        }

        @Test
        @DisplayName("RATIO_2_39_1 has correct ratio")
        void ratio2_39_1_HasCorrectRatio() {
                assertEquals(2.39, AspectRatio.RATIO_2_39_1.getRatio(), 0.0001,
                                "2.39:1 ratio should equal 2.39 (cinema)");
        }

        @Test
        @DisplayName("RATIO_2_39_1 has correct display name")
        void ratio2_39_1_HasCorrectDisplayName() {
                assertEquals("2.39:1 (Cinema)", AspectRatio.RATIO_2_39_1.getDisplayName(),
                                "2.39:1 display name should be '2.39:1 (Cinema)'");
        }

        @Test
        @DisplayName("All enum constants are defined (8 total)")
        void allEnumConstants_AreDefined() {
                AspectRatio[] values = AspectRatio.values();
                assertEquals(8, values.length,
                                "AspectRatio should have exactly 8 enum constants");
        }

        @ParameterizedTest
        @EnumSource(value = AspectRatio.class, names = { "RATIO_16_9", "RATIO_4_3", "RATIO_1_1",
                        "RATIO_21_9", "RATIO_9_16", "RATIO_3_2", "RATIO_2_39_1" })
        @DisplayName("All non-KEEP_ORIGINAL ratios have non-null ratio values")
        void nonOriginalRatios_HaveNonNullRatioValues(AspectRatio ratio) {
                assertNotNull(ratio.getRatio(),
                                ratio.name() + " should have a non-null ratio value");
        }

        @ParameterizedTest
        @EnumSource(value = AspectRatio.class, names = { "RATIO_16_9", "RATIO_4_3", "RATIO_1_1",
                        "RATIO_21_9", "RATIO_9_16", "RATIO_3_2", "RATIO_2_39_1" })
        @DisplayName("All non-KEEP_ORIGINAL ratios have isOriginal() return false")
        void nonOriginalRatios_IsOriginalReturnsFalse(AspectRatio ratio) {
                assertFalse(ratio.isOriginal(),
                                ratio.name() + ".isOriginal() should return false");
        }

        @ParameterizedTest
        @EnumSource(AspectRatio.class)
        @DisplayName("All enum constants have non-null display names")
        void allEnumConstants_HaveNonNullDisplayNames(AspectRatio ratio) {
                assertNotNull(ratio.getDisplayName(),
                                ratio.name() + " should have a non-null display name");
        }

        @ParameterizedTest
        @EnumSource(AspectRatio.class)
        @DisplayName("All enum constants have non-empty display names")
        void allEnumConstants_HaveNonEmptyDisplayNames(AspectRatio ratio) {
                assertFalse(ratio.getDisplayName().isEmpty(),
                                ratio.name() + " should have a non-empty display name");
        }

        @Test
        @DisplayName("Enum constants can be retrieved by name")
        void enumConstants_CanBeRetrievedByName() {
                assertEquals(AspectRatio.KEEP_ORIGINAL, AspectRatio.valueOf("KEEP_ORIGINAL"),
                                "valueOf('KEEP_ORIGINAL') should return KEEP_ORIGINAL");
                assertEquals(AspectRatio.RATIO_16_9, AspectRatio.valueOf("RATIO_16_9"),
                                "valueOf('RATIO_16_9') should return RATIO_16_9");
        }

        @Test
        @DisplayName("Ratio values are positive for all non-KEEP_ORIGINAL constants")
        void allRatioValues_ArePositive() {
                for (AspectRatio ratio : AspectRatio.values()) {
                        if (!ratio.isOriginal()) {
                                assertTrue(ratio.getRatio() > 0,
                                                ratio.name() + " ratio should be positive");
                        }
                }
        }

        @Test
        @DisplayName("Vertical ratio (9:16) is less than 1.0")
        void verticalRatio_IsLessThanOne() {
                assertTrue(AspectRatio.RATIO_9_16.getRatio() < 1.0,
                                "9:16 vertical ratio should be less than 1.0");
        }

        @Test
        @DisplayName("Widescreen ratios (16:9, 21:9, 2.39:1) are greater than 1.0")
        void widescreenRatios_AreGreaterThanOne() {
                assertTrue(AspectRatio.RATIO_16_9.getRatio() > 1.0,
                                "16:9 should be greater than 1.0");
                assertTrue(AspectRatio.RATIO_21_9.getRatio() > 1.0,
                                "21:9 should be greater than 1.0");
                assertTrue(AspectRatio.RATIO_2_39_1.getRatio() > 1.0,
                                "2.39:1 should be greater than 1.0");
        }

        @Test
        @DisplayName("Square ratio (1:1) equals exactly 1.0")
        void squareRatio_EqualsOne() {
                assertEquals(1.0, AspectRatio.RATIO_1_1.getRatio(),
                                "1:1 square ratio should equal exactly 1.0");
        }
}
