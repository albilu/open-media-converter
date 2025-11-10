package org.omc.exception;

import org.omc.model.FileFormat;

/**
 * Exception thrown when conversion operations fail.
 * Examples: conversion failed, unsupported format pair, timeout.
 * 
 * Requirements: REQ-007.1, REQ-001.1, REQ-001.2
 */
public class ConversionException extends MediaConverterException {
    private final String inputFile;
    private final String outputFile;
    private final FileFormat sourceFormat;
    private final FileFormat targetFormat;

    /**
     * Creates a ConversionException with basic information.
     *
     * @param message   The error message
     * @param errorCode The error code
     * @param inputFile The input file path
     */
    public ConversionException(String message, ErrorCode errorCode, String inputFile) {
        super(message, errorCode);
        this.inputFile = inputFile;
        this.outputFile = null;
        this.sourceFormat = null;
        this.targetFormat = null;
    }

    /**
     * Creates a ConversionException with full conversion context.
     *
     * @param message      The error message
     * @param errorCode    The error code
     * @param inputFile    The input file path
     * @param outputFile   The output file path
     * @param sourceFormat The source format
     * @param targetFormat The target format
     */
    public ConversionException(String message, ErrorCode errorCode, String inputFile,
            String outputFile, FileFormat sourceFormat, FileFormat targetFormat) {
        super(message, errorCode);
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.sourceFormat = sourceFormat;
        this.targetFormat = targetFormat;
    }

    /**
     * Creates a ConversionException with full context and cause.
     *
     * @param message      The error message
     * @param errorCode    The error code
     * @param inputFile    The input file path
     * @param outputFile   The output file path
     * @param sourceFormat The source format
     * @param targetFormat The target format
     * @param cause        The underlying cause
     */
    public ConversionException(String message, ErrorCode errorCode, String inputFile,
            String outputFile, FileFormat sourceFormat, FileFormat targetFormat,
            Throwable cause) {
        super(message, errorCode, cause);
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.sourceFormat = sourceFormat;
        this.targetFormat = targetFormat;
    }

    /**
     * Gets the input file path.
     *
     * @return The input file path
     */
    public String getInputFile() {
        return inputFile;
    }

    /**
     * Gets the output file path.
     *
     * @return The output file path, or null if not set
     */
    public String getOutputFile() {
        return outputFile;
    }

    /**
     * Gets the source format.
     *
     * @return The source format, or null if not set
     */
    public FileFormat getSourceFormat() {
        return sourceFormat;
    }

    /**
     * Gets the target format.
     *
     * @return The target format, or null if not set
     */
    public FileFormat getTargetFormat() {
        return targetFormat;
    }

    @Override
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] %s", getErrorCode().getCode(), getMessage()));

        if (inputFile != null) {
            sb.append(String.format("\n  Input: %s", inputFile));
        }
        if (outputFile != null) {
            sb.append(String.format("\n  Output: %s", outputFile));
        }
        if (sourceFormat != null && targetFormat != null) {
            sb.append(String.format("\n  Conversion: %s -> %s", sourceFormat, targetFormat));
        }

        return sb.toString();
    }
}
