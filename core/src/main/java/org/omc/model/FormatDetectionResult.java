// filepath: src/main/java/org/omc/model/FormatDetectionResult.java

package org.omc.model;

import java.util.Objects;

/**
 * Result of format detection with confidence score.
 * 
 * Requirement REQ-002.3: Format detection with confidence scoring.
 */
public class FormatDetectionResult {

    /**
     * Detection method used.
     */
    public enum DetectionMethod {
        /** Format detected by magic bytes (most reliable) */
        MAGIC_BYTES,

        /** Format detected by file extension (less reliable) */
        EXTENSION,

        /** Format could not be detected */
        UNKNOWN
    }

    private final FileFormat format;
    private final DetectionMethod method;
    private final double confidence; // 0.0 to 1.0

    private FormatDetectionResult(FileFormat format, DetectionMethod method, double confidence) {
        this.format = Objects.requireNonNull(format, "Format cannot be null");
        this.method = Objects.requireNonNull(method, "Detection method cannot be null");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0, got: " + confidence);
        }
        this.confidence = confidence;
    }

    /**
     * Creates a detection result based on magic bytes.
     *
     * @param format The detected format
     * @return Detection result with high confidence (0.95)
     */
    public static FormatDetectionResult fromMagicBytes(FileFormat format) {
        return new FormatDetectionResult(format, DetectionMethod.MAGIC_BYTES, 0.95);
    }

    /**
     * Creates a detection result based on file extension.
     *
     * @param format The detected format
     * @return Detection result with medium confidence (0.70)
     */
    public static FormatDetectionResult fromExtension(FileFormat format) {
        return new FormatDetectionResult(format, DetectionMethod.EXTENSION, 0.70);
    }

    /**
     * Creates a detection result when format matches both magic bytes and
     * extension.
     *
     * @param format The detected format
     * @return Detection result with very high confidence (0.99)
     */
    public static FormatDetectionResult fromBoth(FileFormat format) {
        return new FormatDetectionResult(format, DetectionMethod.MAGIC_BYTES, 0.99);
    }

    /**
     * Creates a detection result for unknown format.
     *
     * @return Detection result with zero confidence
     */
    public static FormatDetectionResult unknown() {
        return new FormatDetectionResult(FileFormat.UNKNOWN, DetectionMethod.UNKNOWN, 0.0);
    }

    /**
     * Gets the detected file format.
     *
     * @return The file format
     */
    public FileFormat getFormat() {
        return format;
    }

    /**
     * Gets the detection method used.
     *
     * @return The detection method
     */
    public DetectionMethod getMethod() {
        return method;
    }

    /**
     * Gets the confidence score (0.0 to 1.0).
     * Higher values indicate more reliable detection.
     *
     * @return The confidence score
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * Checks if the detection was successful (format is not UNKNOWN).
     *
     * @return true if format was successfully detected
     */
    public boolean isDetected() {
        return format != FileFormat.UNKNOWN;
    }

    /**
     * Checks if the confidence is high (>= 0.90).
     *
     * @return true if confidence is high
     */
    public boolean isHighConfidence() {
        return confidence >= 0.90;
    }

    /**
     * Checks if the confidence is medium (>= 0.60 and < 0.90).
     *
     * @return true if confidence is medium
     */
    public boolean isMediumConfidence() {
        return confidence >= 0.60 && confidence < 0.90;
    }

    /**
     * Checks if the confidence is low (< 0.60).
     *
     * @return true if confidence is low
     */
    public boolean isLowConfidence() {
        return confidence < 0.60;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FormatDetectionResult that = (FormatDetectionResult) o;
        return Double.compare(that.confidence, confidence) == 0 &&
                format == that.format &&
                method == that.method;
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, method, confidence);
    }

    @Override
    public String toString() {
        return String.format("FormatDetectionResult{format=%s, method=%s, confidence=%.2f}",
                format, method, confidence);
    }
}
