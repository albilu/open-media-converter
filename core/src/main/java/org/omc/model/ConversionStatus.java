package org.omc.model;

/**
 * Status of a conversion operation for a file.
 * Represents the lifecycle of a file through the conversion process.
 * 
 * Requirements: REQ-004.2, REQ-004.3
 */
public enum ConversionStatus {
    /**
     * File has been added but conversion has not started yet.
     */
    PENDING,

    /**
     * Conversion is currently in progress.
     */
    IN_PROGRESS,

    /**
     * Conversion completed successfully.
     */
    COMPLETED,

    /**
     * Conversion failed due to an error.
     */
    FAILED,

    /**
     * Conversion was cancelled by the user.
     */
    CANCELLED
}
