package org.omc.exception;

/**
 * Enumeration of error codes for categorizing exceptions.
 * Provides machine-readable error codes and human-readable descriptions.
 * 
 * Requirements: REQ-007.1
 */
public enum ErrorCode {
    // File-related errors (1000-1999)
    FILE_NOT_FOUND("ERR-1001", "File not found"),
    FILE_NOT_READABLE("ERR-1002", "File is not readable"),
    FILE_NOT_WRITABLE("ERR-1003", "File is not writable"),
    FILE_ALREADY_EXISTS("ERR-1004", "File already exists"),
    FILE_TOO_LARGE("ERR-1005", "File exceeds maximum size"),
    INSUFFICIENT_DISK_SPACE("ERR-1006", "Insufficient disk space"),
    INVALID_FILE_FORMAT("ERR-1007", "Invalid or unsupported file format"),
    FILE_IO_ERROR("ERR-1008", "File I/O error"),

    // Conversion-related errors (2000-2999)
    CONVERSION_FAILED("ERR-2001", "Conversion failed"),
    CONVERSION_CANCELLED("ERR-2002", "Conversion cancelled by user"),
    CONVERSION_TIMEOUT("ERR-2003", "Conversion timeout"),
    INVALID_CONVERSION_SETTINGS("ERR-2004", "Invalid conversion settings"),
    UNSUPPORTED_CONVERSION("ERR-2005", "Unsupported format conversion"),
    OUTPUT_FILE_ERROR("ERR-2006", "Error creating output file"),

    // Tool-related errors (3000-3999)
    TOOL_NOT_FOUND("ERR-3001", "Required tool not found"),
    TOOL_EXECUTION_FAILED("ERR-3002", "Tool execution failed"),
    TOOL_VERSION_INCOMPATIBLE("ERR-3003", "Tool version incompatible"),
    TOOL_OUTPUT_PARSE_ERROR("ERR-3004", "Failed to parse tool output"),

    // Settings-related errors (4000-4999)
    INVALID_SETTINGS("ERR-4001", "Invalid settings"),
    SETTINGS_LOAD_ERROR("ERR-4002", "Failed to load settings"),
    SETTINGS_SAVE_ERROR("ERR-4003", "Failed to save settings"),

    // State persistence errors (5000-5999)
    STATE_LOAD_ERROR("ERR-5001", "Failed to load application state"),
    STATE_SAVE_ERROR("ERR-5002", "Failed to save application state"),
    STATE_MIGRATION_ERROR("ERR-5003", "Failed to migrate state from older version"),
    STATE_CORRUPTED("ERR-5004", "Application state is corrupted"),

    // Validation errors (6000-6999)
    VALIDATION_FAILED("ERR-6001", "Validation failed"),
    INVALID_INPUT("ERR-6002", "Invalid input"),
    MISSING_REQUIRED_FIELD("ERR-6003", "Required field is missing"),
    VALUE_OUT_OF_RANGE("ERR-6004", "Value is out of valid range"),

    // General errors (9000-9999)
    UNKNOWN_ERROR("ERR-9000", "Unknown error"),
    INTERNAL_ERROR("ERR-9001", "Internal error"),
    CONFIGURATION_ERROR("ERR-9002", "Configuration error");

    private final String code;
    private final String description;

    /**
     * Creates an ErrorCode enum value.
     *
     * @param code        The error code string (e.g., "ERR-1001")
     * @param description Human-readable description
     */
    ErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Gets the error code string.
     *
     * @return The error code
     */
    public String getCode() {
        return code;
    }

    /**
     * Gets the error description.
     *
     * @return The error description
     */
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return code + ": " + description;
    }
}
