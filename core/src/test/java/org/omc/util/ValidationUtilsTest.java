package org.omc.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ValidationUtilsTest {

    @Test
    void isBlank_coversNullEmptyAndWhitespace() {
        assertTrue(ValidationUtils.isBlank(null));
        assertTrue(ValidationUtils.isBlank(""));
        assertTrue(ValidationUtils.isBlank("   "));
        assertFalse(ValidationUtils.isBlank("x"));
        assertTrue(ValidationUtils.isNotBlank("x"));
        assertFalse(ValidationUtils.isNotBlank(null));
    }

    @Test
    void requireNotBlank_throwsOnlyForBlank() {
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requireNotBlank(null, "field"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requireNotBlank("  ", "field"));
        assertDoesNotThrow(() -> ValidationUtils.requireNotBlank("ok", "field"));
    }

    @Test
    void requireNonNull_returnsValueOrThrows() {
        assertEquals("v", ValidationUtils.requireNonNull("v", "field"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ValidationUtils.requireNonNull(null, "field"));
        assertTrue(e.getMessage().contains("field"));
    }

    @Test
    void isInRange_isInclusiveOnAllOverloads() {
        assertTrue(ValidationUtils.isInRange(1, 1, 10));
        assertTrue(ValidationUtils.isInRange(10, 1, 10));
        assertFalse(ValidationUtils.isInRange(0, 1, 10));
        assertTrue(ValidationUtils.isInRange(5L, 1L, 10L));
        assertFalse(ValidationUtils.isInRange(11L, 1L, 10L));
        assertTrue(ValidationUtils.isInRange(0.5, 0.0, 1.0));
        assertFalse(ValidationUtils.isInRange(-0.1, 0.0, 1.0));
    }

    @Test
    void requireInRange_throwsWithBoundsInMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ValidationUtils.requireInRange(11, 1, 10, "count"));
        assertTrue(e.getMessage().contains("count"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requireInRange(11L, 1L, 10L, "count"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requireInRange(1.5, 0.0, 1.0, "count"));
        assertDoesNotThrow(() -> ValidationUtils.requireInRange(5, 1, 10, "count"));
    }

    @Test
    void requirePositive_enforcesGreaterThanZero() {
        assertTrue(ValidationUtils.isPositive(1));
        assertFalse(ValidationUtils.isPositive(0));
        assertTrue(ValidationUtils.isPositive(1L));
        assertFalse(ValidationUtils.isPositive(-1L));
        assertTrue(ValidationUtils.isPositive(0.1));
        assertFalse(ValidationUtils.isPositive(0.0));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requirePositive(0, "size"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requirePositive(0L, "size"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requirePositive(0.0, "size"));
        assertDoesNotThrow(() -> ValidationUtils.requirePositive(1, "size"));
    }

    @Test
    void collectionEmptiness() {
        assertTrue(ValidationUtils.isEmpty(null));
        assertTrue(ValidationUtils.isEmpty(List.of()));
        assertFalse(ValidationUtils.isEmpty(List.of(1)));
        assertTrue(ValidationUtils.isNotEmpty(List.of(1)));
        assertEquals(List.of(1), ValidationUtils.requireNotEmpty(List.of(1), "items"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requireNotEmpty(List.of(), "items"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requireNotEmpty(null, "items"));
    }

    @Test
    void matches_handlesNullsAndPatterns() {
        assertFalse(ValidationUtils.matches(null, ".*"));
        assertFalse(ValidationUtils.matches("x", null));
        assertTrue(ValidationUtils.matches("abc123", "[a-z]+\\d+"));
        assertFalse(ValidationUtils.matches("abc", "\\d+"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requireMatches("abc", "\\d+", "code"));
        assertDoesNotThrow(() -> ValidationUtils.requireMatches("123", "\\d+", "code"));
    }

    @Test
    void constructor_isNotInstantiable() throws Exception {
        java.lang.reflect.Constructor<ValidationUtils> ctor = ValidationUtils.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        java.lang.reflect.InvocationTargetException e = assertThrows(java.lang.reflect.InvocationTargetException.class,
                ctor::newInstance);
        assertTrue(e.getCause() instanceof UnsupportedOperationException);
    }
}
