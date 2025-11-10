package org.omc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the result of a validation operation.
 * 
 * Contains success status, error messages, and warning messages.
 * Immutable once created.
 * 
 * Requirements: REQ-002.3, REQ-003.2, REQ-007.1
 */
public class ValidationResult {
    private final boolean success;
    private final List<String> errors;
    private final List<String> warnings;

    /**
     * Private constructor. Use builder or factory methods.
     */
    private ValidationResult(boolean success, List<String> errors, List<String> warnings) {
        this.success = success;
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    /**
     * Creates a successful validation result with no errors or warnings.
     *
     * @return Successful validation result
     */
    public static ValidationResult success() {
        return new ValidationResult(true, Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Creates a successful validation result with warnings.
     *
     * @param warnings Warning messages
     * @return Successful validation result with warnings
     * @throws IllegalArgumentException if warnings is null
     */
    public static ValidationResult successWithWarnings(List<String> warnings) {
        if (warnings == null) {
            throw new IllegalArgumentException("warnings cannot be null");
        }
        return new ValidationResult(true, Collections.emptyList(), warnings);
    }

    /**
     * Creates a failed validation result with a single error.
     *
     * @param error Error message
     * @return Failed validation result
     * @throws IllegalArgumentException if error is null
     */
    public static ValidationResult failure(String error) {
        if (error == null) {
            throw new IllegalArgumentException("error cannot be null");
        }
        return new ValidationResult(false, List.of(error), Collections.emptyList());
    }

    /**
     * Creates a failed validation result with multiple errors.
     *
     * @param errors Error messages
     * @return Failed validation result
     * @throws IllegalArgumentException if errors is null or empty
     */
    public static ValidationResult failure(List<String> errors) {
        if (errors == null) {
            throw new IllegalArgumentException("errors cannot be null");
        }
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("errors list cannot be empty for failure result");
        }
        return new ValidationResult(false, errors, Collections.emptyList());
    }

    /**
     * Creates a failed validation result with errors and warnings.
     *
     * @param errors   Error messages
     * @param warnings Warning messages
     * @return Failed validation result
     * @throws IllegalArgumentException if errors or warnings is null, or if errors
     *                                  is empty
     */
    public static ValidationResult failure(List<String> errors, List<String> warnings) {
        if (errors == null) {
            throw new IllegalArgumentException("errors cannot be null");
        }
        if (warnings == null) {
            throw new IllegalArgumentException("warnings cannot be null");
        }
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("errors list cannot be empty for failure result");
        }
        return new ValidationResult(false, errors, warnings);
    }

    /**
     * Checks if validation was successful.
     *
     * @return true if validation succeeded, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Checks if validation failed.
     *
     * @return true if validation failed, false otherwise
     */
    public boolean isFailure() {
        return !success;
    }

    /**
     * Gets all error messages.
     *
     * @return Unmodifiable list of error messages (empty if success)
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * Gets all warning messages.
     *
     * @return Unmodifiable list of warning messages (empty if no warnings)
     */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * Checks if there are any warnings.
     *
     * @return true if warnings exist, false otherwise
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Gets the first error message if available.
     *
     * @return First error message or empty string if no errors
     */
    public String getFirstError() {
        return errors.isEmpty() ? "" : errors.get(0);
    }

    /**
     * Combines this validation result with another.
     * Result is successful only if both are successful.
     * Errors and warnings are merged.
     *
     * @param other Other validation result
     * @return Combined validation result
     * @throws IllegalArgumentException if other is null
     */
    public ValidationResult combine(ValidationResult other) {
        if (other == null) {
            throw new IllegalArgumentException("other validation result cannot be null");
        }

        boolean combinedSuccess = this.success && other.success;

        List<String> combinedErrors = new ArrayList<>();
        combinedErrors.addAll(this.errors);
        combinedErrors.addAll(other.errors);

        List<String> combinedWarnings = new ArrayList<>();
        combinedWarnings.addAll(this.warnings);
        combinedWarnings.addAll(other.warnings);

        return new ValidationResult(combinedSuccess, combinedErrors, combinedWarnings);
    }

    /**
     * Gets a formatted string of all errors and warnings.
     *
     * @return Formatted message string
     */
    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();

        if (!errors.isEmpty()) {
            sb.append("Errors:\n");
            for (String error : errors) {
                sb.append("  - ").append(error).append("\n");
            }
        }

        if (!warnings.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("Warnings:\n");
            for (String warning : warnings) {
                sb.append("  - ").append(warning).append("\n");
            }
        }

        if (sb.length() == 0) {
            return "Validation successful";
        }

        return sb.toString().trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ValidationResult that = (ValidationResult) o;
        return success == that.success &&
                Objects.equals(errors, that.errors) &&
                Objects.equals(warnings, that.warnings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, errors, warnings);
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "success=" + success +
                ", errors=" + errors.size() +
                ", warnings=" + warnings.size() +
                '}';
    }
}
