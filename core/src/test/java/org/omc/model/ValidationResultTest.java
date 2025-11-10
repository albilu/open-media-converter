package org.omc.model;

import org.omc.model.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ValidationResult class.
 * Tests success/failure states, warnings, error handling, and combine
 * operations.
 */
@DisplayName("ValidationResult Tests")
class ValidationResultTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodsTest {

        @Test
        @DisplayName("Should create successful result without warnings")
        void shouldCreateSuccessResult() {
            ValidationResult result = ValidationResult.success();

            assertTrue(result.isSuccess());
            assertFalse(result.isFailure());
            assertFalse(result.hasWarnings());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getWarnings().isEmpty());
            assertEquals("", result.getFirstError());
            assertEquals("Validation successful", result.getFormattedMessage());
        }

        @Test
        @DisplayName("Should create successful result with warnings")
        void shouldCreateSuccessWithWarnings() {
            List<String> warnings = List.of("Warning 1", "Warning 2");
            ValidationResult result = ValidationResult.successWithWarnings(warnings);

            assertTrue(result.isSuccess());
            assertFalse(result.isFailure());
            assertTrue(result.hasWarnings());
            assertTrue(result.getErrors().isEmpty());
            assertEquals(warnings, result.getWarnings());
            assertEquals("", result.getFirstError());
            assertTrue(result.getFormattedMessage().contains("Warnings:"));
        }

        @Test
        @DisplayName("Should throw exception for null warnings in successWithWarnings")
        void shouldThrowForNullWarnings() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationResult.successWithWarnings(null));
        }

        @Test
        @DisplayName("Should create failure result with single error")
        void shouldCreateFailureWithSingleError() {
            String error = "Test error";
            ValidationResult result = ValidationResult.failure(error);

            assertFalse(result.isSuccess());
            assertTrue(result.isFailure());
            assertFalse(result.hasWarnings());
            assertEquals(List.of(error), result.getErrors());
            assertTrue(result.getWarnings().isEmpty());
            assertEquals(error, result.getFirstError());
            assertTrue(result.getFormattedMessage().contains("Errors:"));
        }

        @Test
        @DisplayName("Should throw exception for null error in failure")
        void shouldThrowForNullError() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationResult.failure((String) null));
        }

        @Test
        @DisplayName("Should create failure result with multiple errors")
        void shouldCreateFailureWithMultipleErrors() {
            List<String> errors = List.of("Error 1", "Error 2");
            ValidationResult result = ValidationResult.failure(errors);

            assertFalse(result.isSuccess());
            assertTrue(result.isFailure());
            assertFalse(result.hasWarnings());
            assertEquals(errors, result.getErrors());
            assertTrue(result.getWarnings().isEmpty());
            assertEquals("Error 1", result.getFirstError());
        }

        @Test
        @DisplayName("Should throw exception for null errors list")
        void shouldThrowForNullErrorsList() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationResult.failure((List<String>) null));
        }

        @Test
        @DisplayName("Should throw exception for empty errors list")
        void shouldThrowForEmptyErrorsList() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationResult.failure(List.of()));
        }

        @Test
        @DisplayName("Should create failure result with errors and warnings")
        void shouldCreateFailureWithErrorsAndWarnings() {
            List<String> errors = List.of("Error 1");
            List<String> warnings = List.of("Warning 1");
            ValidationResult result = ValidationResult.failure(errors, warnings);

            assertFalse(result.isSuccess());
            assertTrue(result.isFailure());
            assertTrue(result.hasWarnings());
            assertEquals(errors, result.getErrors());
            assertEquals(warnings, result.getWarnings());
            assertEquals("Error 1", result.getFirstError());
        }

        @Test
        @DisplayName("Should throw exception for null errors in failure with warnings")
        void shouldThrowForNullErrorsInFailureWithWarnings() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationResult.failure(null, List.of("Warning")));
        }

        @Test
        @DisplayName("Should throw exception for empty errors in failure with warnings")
        void shouldThrowForEmptyErrorsInFailureWithWarnings() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationResult.failure(List.of(), List.of("Warning")));
        }

        @Test
        @DisplayName("Should throw exception for null warnings in failure with warnings")
        void shouldThrowForNullWarningsInFailureWithWarnings() {
            assertThrows(IllegalArgumentException.class,
                    () -> ValidationResult.failure(List.of("Error"), null));
        }
    }

    @Nested
    @DisplayName("Combine Operations")
    class CombineOperationsTest {

        @Test
        @DisplayName("Should combine two successful results")
        void shouldCombineTwoSuccessResults() {
            ValidationResult result1 = ValidationResult.success();
            ValidationResult result2 = ValidationResult.success();

            ValidationResult combined = result1.combine(result2);

            assertTrue(combined.isSuccess());
            assertFalse(combined.hasWarnings());
            assertTrue(combined.getErrors().isEmpty());
            assertTrue(combined.getWarnings().isEmpty());
        }

        @Test
        @DisplayName("Should combine success with warnings")
        void shouldCombineSuccessWithWarnings() {
            ValidationResult result1 = ValidationResult.success();
            ValidationResult result2 = ValidationResult.successWithWarnings(List.of("Warning"));

            ValidationResult combined = result1.combine(result2);

            assertTrue(combined.isSuccess());
            assertTrue(combined.hasWarnings());
            assertEquals(List.of("Warning"), combined.getWarnings());
        }

        @Test
        @DisplayName("Should combine failure with success")
        void shouldCombineFailureWithSuccess() {
            ValidationResult result1 = ValidationResult.failure("Error");
            ValidationResult result2 = ValidationResult.success();

            ValidationResult combined = result1.combine(result2);

            assertFalse(combined.isSuccess());
            assertEquals(List.of("Error"), combined.getErrors());
        }

        @Test
        @DisplayName("Should combine two failures")
        void shouldCombineTwoFailures() {
            ValidationResult result1 = ValidationResult.failure("Error 1");
            ValidationResult result2 = ValidationResult.failure("Error 2");

            ValidationResult combined = result1.combine(result2);

            assertFalse(combined.isSuccess());
            assertEquals(List.of("Error 1", "Error 2"), combined.getErrors());
        }

        @Test
        @DisplayName("Should combine results with warnings")
        void shouldCombineResultsWithWarnings() {
            ValidationResult result1 = ValidationResult.successWithWarnings(List.of("Warning 1"));
            ValidationResult result2 = ValidationResult.successWithWarnings(List.of("Warning 2"));

            ValidationResult combined = result1.combine(result2);

            assertTrue(combined.isSuccess());
            assertEquals(List.of("Warning 1", "Warning 2"), combined.getWarnings());
        }

        @Test
        @DisplayName("Should combine mixed results")
        void shouldCombineMixedResults() {
            ValidationResult result1 = ValidationResult.failure(List.of("Error 1"), List.of("Warning 1"));
            ValidationResult result2 = ValidationResult.successWithWarnings(List.of("Warning 2"));

            ValidationResult combined = result1.combine(result2);

            assertFalse(combined.isSuccess());
            assertEquals(List.of("Error 1"), combined.getErrors());
            assertEquals(List.of("Warning 1", "Warning 2"), combined.getWarnings());
        }

        @Test
        @DisplayName("Should throw exception for null other result")
        void shouldThrowForNullOtherResult() {
            ValidationResult result = ValidationResult.success();

            assertThrows(IllegalArgumentException.class,
                    () -> result.combine(null));
        }
    }

    @Nested
    @DisplayName("Formatted Message")
    class FormattedMessageTest {

        @Test
        @DisplayName("Should format success message")
        void shouldFormatSuccessMessage() {
            ValidationResult result = ValidationResult.success();

            assertEquals("Validation successful", result.getFormattedMessage());
        }

        @Test
        @DisplayName("Should format warnings only")
        void shouldFormatWarningsOnly() {
            ValidationResult result = ValidationResult.successWithWarnings(List.of("Warning 1", "Warning 2"));

            String message = result.getFormattedMessage();
            assertTrue(message.contains("Warnings:"));
            assertTrue(message.contains("  - Warning 1"));
            assertTrue(message.contains("  - Warning 2"));
        }

        @Test
        @DisplayName("Should format errors only")
        void shouldFormatErrorsOnly() {
            ValidationResult result = ValidationResult.failure(List.of("Error 1", "Error 2"));

            String message = result.getFormattedMessage();
            assertTrue(message.contains("Errors:"));
            assertTrue(message.contains("  - Error 1"));
            assertTrue(message.contains("  - Error 2"));
        }

        @Test
        @DisplayName("Should format errors and warnings")
        void shouldFormatErrorsAndWarnings() {
            ValidationResult result = ValidationResult.failure(List.of("Error 1"), List.of("Warning 1"));

            String message = result.getFormattedMessage();
            assertTrue(message.contains("Errors:"));
            assertTrue(message.contains("  - Error 1"));
            assertTrue(message.contains("Warnings:"));
            assertTrue(message.contains("  - Warning 1"));
        }
    }

    @Nested
    @DisplayName("Object Methods")
    class ObjectMethodsTest {

        @Test
        @DisplayName("Should be equal for same success results")
        void shouldBeEqualForSameSuccess() {
            ValidationResult result1 = ValidationResult.success();
            ValidationResult result2 = ValidationResult.success();

            assertEquals(result1, result2);
            assertEquals(result1.hashCode(), result2.hashCode());
        }

        @Test
        @DisplayName("Should be equal for same failure results")
        void shouldBeEqualForSameFailure() {
            ValidationResult result1 = ValidationResult.failure("Error");
            ValidationResult result2 = ValidationResult.failure("Error");

            assertEquals(result1, result2);
            assertEquals(result1.hashCode(), result2.hashCode());
        }

        @Test
        @DisplayName("Should be equal for same success with warnings")
        void shouldBeEqualForSameSuccessWithWarnings() {
            List<String> warnings = List.of("Warning");
            ValidationResult result1 = ValidationResult.successWithWarnings(warnings);
            ValidationResult result2 = ValidationResult.successWithWarnings(warnings);

            assertEquals(result1, result2);
            assertEquals(result1.hashCode(), result2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal for different results")
        void shouldNotBeEqualForDifferentResults() {
            ValidationResult success = ValidationResult.success();
            ValidationResult failure = ValidationResult.failure("Error");

            assertNotEquals(success, failure);
            assertNotEquals(success.hashCode(), failure.hashCode());
        }

        @Test
        @DisplayName("Should not be equal to null")
        void shouldNotBeEqualToNull() {
            ValidationResult result = ValidationResult.success();

            assertNotEquals(result, null);
        }

        @Test
        @DisplayName("Should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            ValidationResult result = ValidationResult.success();

            assertNotEquals(result, "string");
        }

        @Test
        @DisplayName("Should have proper toString")
        void shouldHaveProperToString() {
            ValidationResult result = ValidationResult.success();

            String toString = result.toString();
            assertTrue(toString.contains("ValidationResult"));
            assertTrue(toString.contains("success=true"));
            assertTrue(toString.contains("errors=0"));
            assertTrue(toString.contains("warnings=0"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("Should handle empty strings in errors")
        void shouldHandleEmptyStringsInErrors() {
            ValidationResult result = ValidationResult.failure("");

            assertFalse(result.isSuccess());
            assertEquals("", result.getFirstError());
        }

        @Test
        @DisplayName("Should handle empty strings in warnings")
        void shouldHandleEmptyStringsInWarnings() {
            ValidationResult result = ValidationResult.successWithWarnings(List.of(""));

            assertTrue(result.isSuccess());
            assertTrue(result.hasWarnings());
            assertEquals(List.of(""), result.getWarnings());
        }

        @Test
        @DisplayName("Should handle large lists")
        void shouldHandleLargeLists() {
            List<String> largeErrors = java.util.stream.IntStream.range(0, 1000)
                    .mapToObj(i -> "Error " + i)
                    .toList();
            List<String> largeWarnings = java.util.stream.IntStream.range(0, 1000)
                    .mapToObj(i -> "Warning " + i)
                    .toList();

            ValidationResult result = ValidationResult.failure(largeErrors, largeWarnings);

            assertFalse(result.isSuccess());
            assertEquals(1000, result.getErrors().size());
            assertEquals(1000, result.getWarnings().size());
        }
    }
}