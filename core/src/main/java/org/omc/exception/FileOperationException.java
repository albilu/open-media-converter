package org.omc.exception;

/**
 * Exception thrown when file operations fail.
 * Examples: file not found, file not readable, insufficient disk space.
 * 
 * Requirements: REQ-007.1, REQ-002.1, REQ-002.3
 */
public class FileOperationException extends MediaConverterException {
    private final String filePath;

    /**
     * Creates a FileOperationException with file path and error code.
     *
     * @param message   The error message
     * @param errorCode The error code
     * @param filePath  The file path that caused the error
     */
    public FileOperationException(String message, ErrorCode errorCode, String filePath) {
        super(message, errorCode);
        this.filePath = filePath;
    }

    /**
     * Creates a FileOperationException with file path, error code, and cause.
     *
     * @param message   The error message
     * @param errorCode The error code
     * @param filePath  The file path that caused the error
     * @param cause     The underlying cause
     */
    public FileOperationException(String message, ErrorCode errorCode, String filePath, Throwable cause) {
        super(message, errorCode, cause, filePath);
        this.filePath = filePath;
    }

    /**
     * Gets the file path that caused this exception.
     *
     * @return The file path
     */
    public String getFilePath() {
        return filePath;
    }

    @Override
    public String getDetailedMessage() {
        return String.format("[%s] %s: %s", getErrorCode().getCode(), getMessage(), filePath);
    }
}
