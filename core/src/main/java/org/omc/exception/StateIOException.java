package org.omc.exception;

/**
 * Exception thrown when state persistence operations fail.
 * Examples: failed to load state, failed to save state, corrupted state file.
 * 
 * Requirements: REQ-007.1, REQ-005.1, REQ-005.2
 */
public class StateIOException extends MediaConverterException {
    private final String stateFile;
    private final boolean isLoadOperation;

    /**
     * Creates a StateIOException for load operation.
     *
     * @param message         The error message
     * @param errorCode       The error code
     * @param stateFile       The state file path
     * @param isLoadOperation true if this is a load operation, false for save
     */
    public StateIOException(String message, ErrorCode errorCode, String stateFile, boolean isLoadOperation) {
        super(message, errorCode);
        this.stateFile = stateFile;
        this.isLoadOperation = isLoadOperation;
    }

    /**
     * Creates a StateIOException with cause.
     *
     * @param message         The error message
     * @param errorCode       The error code
     * @param stateFile       The state file path
     * @param isLoadOperation true if this is a load operation, false for save
     * @param cause           The underlying cause
     */
    public StateIOException(String message, ErrorCode errorCode, String stateFile,
            boolean isLoadOperation, Throwable cause) {
        super(message, errorCode, cause);
        this.stateFile = stateFile;
        this.isLoadOperation = isLoadOperation;
    }

    /**
     * Gets the state file path.
     *
     * @return The state file path
     */
    public String getStateFile() {
        return stateFile;
    }

    /**
     * Checks if this exception is for a load operation.
     *
     * @return true if load operation, false if save operation
     */
    public boolean isLoadOperation() {
        return isLoadOperation;
    }

    /**
     * Checks if this exception is for a save operation.
     *
     * @return true if save operation, false if load operation
     */
    public boolean isSaveOperation() {
        return !isLoadOperation;
    }

    @Override
    public String getDetailedMessage() {
        String operation = isLoadOperation ? "Loading" : "Saving";
        return String.format("[%s] %s: %s (File: %s)",
                getErrorCode().getCode(), operation, getMessage(), stateFile);
    }
}
