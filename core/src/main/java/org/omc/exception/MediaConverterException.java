package org.omc.exception;

/**
 * Base exception for all Open Media Converter exceptions.
 * Provides error codes and context for better error handling and logging.
 * 
 * Requirements: REQ-007.1
 */
public class MediaConverterException extends Exception {
    private final ErrorCode errorCode;
    /**
     * Additional context for this exception. Deliberately untyped: subclasses
     * carry structured context in dedicated fields (e.g.
     * {@link FileOperationException#getFilePath()}) and expose type-safe
     * accessors, so {@link #getContext()} is only a fallback for generic
     * logging paths. The signature is kept for backward compatibility.
     */
    private final Object context;

    /**
     * Creates a MediaConverterException with message and error code.
     *
     * @param message   The error message
     * @param errorCode The error code
     */
    public MediaConverterException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.context = null;
    }

    /**
     * Creates a MediaConverterException with message, error code, and cause.
     *
     * @param message   The error message
     * @param errorCode The error code
     * @param cause     The underlying cause
     */
    public MediaConverterException(String message, ErrorCode errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = null;
    }

    /**
     * Creates a MediaConverterException with message, error code, cause, and
     * context.
     *
     * @param message   The error message
     * @param errorCode The error code
     * @param cause     The underlying cause
     * @param context   Additional context object
     */
    public MediaConverterException(String message, ErrorCode errorCode, Throwable cause, Object context) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = context;
    }

    /**
     * Gets the error code for this exception.
     *
     * @return The error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Gets the context object for this exception.
     *
     * @return The context object, or null if not set
     */
    public Object getContext() {
        return context;
    }

    /**
     * Gets a detailed error message including error code.
     *
     * @return The detailed error message; the plain message when no error
     *         code is set (the code is optional, so this must stay null-safe)
     */
    public String getDetailedMessage() {
        if (errorCode == null) {
            return getMessage();
        }
        return String.format("[%s] %s", errorCode.getCode(), getMessage());
    }
}
