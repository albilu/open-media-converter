package org.omc.util;

import java.util.Collection;

/**
 * Utility class for input validation including null checks, range checks, and
 * string validation.
 * All methods are static and thread-safe.
 * 
 * Requirements: All
 */
public final class ValidationUtils {

    // Private constructor to prevent instantiation
    private ValidationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Checks if a string is null or blank.
     *
     * @param value The string to check
     * @return true if null or blank
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Checks if a string is not null and not blank.
     *
     * @param value The string to check
     * @return true if not null and not blank
     */
    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    /**
     * Requires that a string is not null or blank.
     *
     * @param value     The string to check
     * @param fieldName The name of the field for error message
     * @throws IllegalArgumentException if string is null or blank
     */
    public static void requireNotBlank(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
    }

    /**
     * Requires that an object is not null.
     *
     * @param value     The object to check
     * @param fieldName The name of the field for error message
     * @param <T>       The type of the object
     * @return The object if not null
     * @throws IllegalArgumentException if object is null
     */
    public static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        return value;
    }

    /**
     * Checks if a value is within a range (inclusive).
     *
     * @param value The value to check
     * @param min   The minimum value (inclusive)
     * @param max   The maximum value (inclusive)
     * @return true if value is in range
     */
    public static boolean isInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    /**
     * Checks if a value is within a range (inclusive).
     *
     * @param value The value to check
     * @param min   The minimum value (inclusive)
     * @param max   The maximum value (inclusive)
     * @return true if value is in range
     */
    public static boolean isInRange(long value, long min, long max) {
        return value >= min && value <= max;
    }

    /**
     * Checks if a value is within a range (inclusive).
     *
     * @param value The value to check
     * @param min   The minimum value (inclusive)
     * @param max   The maximum value (inclusive)
     * @return true if value is in range
     */
    public static boolean isInRange(double value, double min, double max) {
        return value >= min && value <= max;
    }

    /**
     * Requires that a value is within a range (inclusive).
     *
     * @param value     The value to check
     * @param min       The minimum value (inclusive)
     * @param max       The maximum value (inclusive)
     * @param fieldName The name of the field for error message
     * @throws IllegalArgumentException if value is out of range
     */
    public static void requireInRange(int value, int min, int max, String fieldName) {
        if (!isInRange(value, min, max)) {
            throw new IllegalArgumentException(
                    String.format("%s must be between %d and %d, got: %d", fieldName, min, max, value));
        }
    }

    /**
     * Requires that a value is within a range (inclusive).
     *
     * @param value     The value to check
     * @param min       The minimum value (inclusive)
     * @param max       The maximum value (inclusive)
     * @param fieldName The name of the field for error message
     * @throws IllegalArgumentException if value is out of range
     */
    public static void requireInRange(long value, long min, long max, String fieldName) {
        if (!isInRange(value, min, max)) {
            throw new IllegalArgumentException(
                    String.format("%s must be between %d and %d, got: %d", fieldName, min, max, value));
        }
    }

    /**
     * Requires that a value is within a range (inclusive).
     *
     * @param value     The value to check
     * @param min       The minimum value (inclusive)
     * @param max       The maximum value (inclusive)
     * @param fieldName The name of the field for error message
     * @throws IllegalArgumentException if value is out of range
     */
    public static void requireInRange(double value, double min, double max, String fieldName) {
        if (!isInRange(value, min, max)) {
            throw new IllegalArgumentException(
                    String.format("%s must be between %.2f and %.2f, got: %.2f", fieldName, min, max, value));
        }
    }

    /**
     * Checks if a value is positive (greater than zero).
     *
     * @param value The value to check
     * @return true if positive
     */
    public static boolean isPositive(int value) {
        return value > 0;
    }

    /**
     * Checks if a value is positive (greater than zero).
     *
     * @param value The value to check
     * @return true if positive
     */
    public static boolean isPositive(long value) {
        return value > 0;
    }

    /**
     * Checks if a value is positive (greater than zero).
     *
     * @param value The value to check
     * @return true if positive
     */
    public static boolean isPositive(double value) {
        return value > 0;
    }

    /**
     * Requires that a value is positive (greater than zero).
     *
     * @param value     The value to check
     * @param fieldName The name of the field for error message
     * @throws IllegalArgumentException if value is not positive
     */
    public static void requirePositive(int value, String fieldName) {
        if (!isPositive(value)) {
            throw new IllegalArgumentException(
                    String.format("%s must be positive, got: %d", fieldName, value));
        }
    }

    /**
     * Requires that a value is positive (greater than zero).
     *
     * @param value     The value to check
     * @param fieldName The name of the field for error message
     * @throws IllegalArgumentException if value is not positive
     */
    public static void requirePositive(long value, String fieldName) {
        if (!isPositive(value)) {
            throw new IllegalArgumentException(
                    String.format("%s must be positive, got: %d", fieldName, value));
        }
    }

    /**
     * Requires that a value is positive (greater than zero).
     *
     * @param value     The value to check
     * @param fieldName The name of the field for error message
     * @throws IllegalArgumentException if value is not positive
     */
    public static void requirePositive(double value, String fieldName) {
        if (!isPositive(value)) {
            throw new IllegalArgumentException(
                    String.format("%s must be positive, got: %.2f", fieldName, value));
        }
    }

    /**
     * Checks if a collection is null or empty.
     *
     * @param collection The collection to check
     * @return true if null or empty
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Checks if a collection is not null and not empty.
     *
     * @param collection The collection to check
     * @return true if not null and not empty
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * Requires that a collection is not null or empty.
     *
     * @param collection The collection to check
     * @param fieldName  The name of the field for error message
     * @param <T>        The type of the collection
     * @return The collection if not null or empty
     * @throws IllegalArgumentException if collection is null or empty
     */
    public static <T extends Collection<?>> T requireNotEmpty(T collection, String fieldName) {
        if (isEmpty(collection)) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return collection;
    }

    /**
     * Checks if a string matches a regular expression pattern.
     *
     * @param value   The string to check
     * @param pattern The regex pattern
     * @return true if string matches pattern
     */
    public static boolean matches(String value, String pattern) {
        if (value == null || pattern == null) {
            return false;
        }
        return value.matches(pattern);
    }

    /**
     * Requires that a string matches a regular expression pattern.
     *
     * @param value     The string to check
     * @param pattern   The regex pattern
     * @param fieldName The name of the field for error message
     * @throws IllegalArgumentException if string doesn't match pattern
     */
    public static void requireMatches(String value, String pattern, String fieldName) {
        if (!matches(value, pattern)) {
            throw new IllegalArgumentException(
                    String.format("%s does not match required pattern: %s", fieldName, pattern));
        }
    }
}
