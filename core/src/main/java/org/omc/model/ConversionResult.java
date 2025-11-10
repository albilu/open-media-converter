// filepath: src/main/java/org/omc/model/ConversionResult.java

package org.omc.model;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the result of a single file conversion.
 * Requirement REQ-004.2: Conversion execution with result tracking.
 * Requirement REQ-FL-2.2: Store tool output for conversion details dialog.
 */
public final class ConversionResult {

    private final String fileId;
    private final boolean success;
    private final Path outputPath;
    private final String errorMessage;
    private final String toolOutput;
    private final Duration conversionTime;
    private final long inputSize;
    private final long outputSize;
    private final ConversionTool toolUsed;

    @JsonCreator
    public ConversionResult(
            @JsonProperty("fileId") String fileId,
            @JsonProperty("success") boolean success,
            @JsonProperty("outputPath") Path outputPath,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("toolOutput") String toolOutput,
            @JsonProperty("conversionTime") Duration conversionTime,
            @JsonProperty("inputSize") long inputSize,
            @JsonProperty("outputSize") long outputSize,
            @JsonProperty("toolUsed") ConversionTool toolUsed) {
        this.fileId = fileId;
        this.success = success;
        this.outputPath = outputPath;
        this.errorMessage = errorMessage;
        this.toolOutput = toolOutput;
        this.conversionTime = conversionTime;
        this.inputSize = inputSize;
        this.outputSize = outputSize;
        this.toolUsed = toolUsed;
    }

    /**
     * Creates a successful conversion result.
     * 
     * @param fileId         The unique identifier of the converted file
     * @param outputPath     The path to the output file
     * @param toolOutput     The combined stdout and stderr from the tool execution
     *                       (max 1MB, nullable)
     * @param conversionTime The duration of the conversion
     * @param inputSize      The size of the input file in bytes
     * @param outputSize     The size of the output file in bytes
     * @param toolUsed       The conversion tool that was used
     * @return A ConversionResult representing success
     */
    public static ConversionResult success(String fileId, Path outputPath, String toolOutput,
            Duration conversionTime, long inputSize, long outputSize,
            ConversionTool toolUsed) {
        return new ConversionResult(fileId, true, outputPath, null, toolOutput, conversionTime,
                inputSize, outputSize, toolUsed);
    }

    /**
     * Creates a failed conversion result.
     * 
     * @param fileId         The unique identifier of the file
     * @param errorMessage   The error message describing the failure
     * @param toolOutput     The combined stdout and stderr from the tool execution
     *                       (max 1MB, nullable)
     * @param conversionTime The duration before failure
     * @param inputSize      The size of the input file in bytes
     * @param toolUsed       The conversion tool that was used
     * @return A ConversionResult representing failure
     */
    public static ConversionResult failure(String fileId, String errorMessage, String toolOutput,
            Duration conversionTime, long inputSize, ConversionTool toolUsed) {
        return new ConversionResult(fileId, false, null, errorMessage, toolOutput, conversionTime,
                inputSize, 0, toolUsed);
    }

    /**
     * Creates a cancelled conversion result.
     * 
     * @param fileId         The unique identifier of the file
     * @param toolOutput     The partial tool output captured before cancellation
     *                       (nullable)
     * @param conversionTime The duration before cancellation
     * @param inputSize      The size of the input file in bytes
     * @param toolUsed       The conversion tool that was used
     * @return A ConversionResult representing cancellation
     */
    public static ConversionResult cancelled(String fileId, String toolOutput, Duration conversionTime,
            long inputSize, ConversionTool toolUsed) {
        return new ConversionResult(fileId, false, null, "Conversion cancelled by user", toolOutput,
                conversionTime, inputSize, 0, toolUsed);
    }

    /**
     * Checks if this result represents a cancelled conversion.
     */
    @JsonIgnore
    public boolean isCancelled() {
        return !success && errorMessage != null && errorMessage.contains("cancelled");
    }

    @JsonProperty("fileId")
    public String fileId() {
        return fileId;
    }

    @JsonProperty("success")
    public boolean success() {
        return success;
    }

    // For JSON serialization - returns raw Path
    @JsonProperty("outputPath")
    Path getOutputPathForJson() {
        return outputPath;
    }

    // For application use - returns Optional
    @JsonIgnore
    public Optional<Path> outputPath() {
        return Optional.ofNullable(outputPath);
    }

    // For JSON serialization - returns raw String
    @JsonProperty("errorMessage")
    String getErrorMessageForJson() {
        return errorMessage;
    }

    // For application use - returns Optional
    @JsonIgnore
    public Optional<String> errorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    // For JSON serialization - returns raw String
    @JsonProperty("toolOutput")
    String getToolOutputForJson() {
        return toolOutput;
    }

    // For application use - returns Optional
    @JsonIgnore
    public Optional<String> toolOutput() {
        return Optional.ofNullable(toolOutput);
    }

    @JsonProperty("conversionTime")
    public Duration conversionTime() {
        return conversionTime;
    }

    @JsonProperty("inputSize")
    public long inputSize() {
        return inputSize;
    }

    @JsonProperty("outputSize")
    public long outputSize() {
        return outputSize;
    }

    @JsonProperty("toolUsed")
    public ConversionTool toolUsed() {
        return toolUsed;
    }

    /**
     * Calculates the space saved (positive) or increased (negative).
     */
    public long spaceSaved() {
        return inputSize - outputSize;
    }

    /**
     * Calculates the compression ratio as a percentage.
     * Positive means smaller output, negative means larger output.
     */
    public double compressionRatio() {
        if (inputSize == 0) {
            return 0.0;
        }
        return ((double) spaceSaved() / inputSize) * 100.0;
    }

    /**
     * Formats the conversion time as a human-readable string.
     */
    public String formatConversionTime() {
        long seconds = conversionTime.getSeconds();
        long minutes = seconds / 60;
        long secs = seconds % 60;

        if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConversionResult that = (ConversionResult) o;
        return success == that.success &&
                inputSize == that.inputSize &&
                outputSize == that.outputSize &&
                Objects.equals(fileId, that.fileId) &&
                Objects.equals(outputPath, that.outputPath) &&
                Objects.equals(errorMessage, that.errorMessage) &&
                Objects.equals(toolOutput, that.toolOutput) &&
                Objects.equals(conversionTime, that.conversionTime) &&
                toolUsed == that.toolUsed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileId, success, outputPath, errorMessage, toolOutput,
                conversionTime, inputSize, outputSize, toolUsed);
    }

    @Override
    public String toString() {
        return "ConversionResult{" +
                "fileId='" + fileId + '\'' +
                ", success=" + success +
                ", outputPath=" + outputPath +
                ", errorMessage='" + errorMessage + '\'' +
                ", toolOutput=" + (toolOutput != null ? toolOutput.length() + " bytes" : "null") +
                ", conversionTime=" + formatConversionTime() +
                ", compressionRatio=" + String.format(Locale.US, "%.1f%%", compressionRatio()) +
                ", toolUsed=" + toolUsed +
                '}';
    }
}
