// filepath: src/main/java/org/omc/model/DocumentSettings.java

package org.omc.model;

import java.nio.file.Path;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Document-specific conversion settings.
 * <p>
 * This class encapsulates all settings related to document format conversion,
 * including templates, formatting preservation, fonts, table of contents,
 * margins, and output format.
 * </p>
 * 
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>REQ-006.4: Document format conversion with formatting controls</li>
 * <li>REQ-2.5: Document output format selection</li>
 * </ul>
 * 
 * @since 1.0
 */
public final class DocumentSettings {

    private final Path templatePath; // null for no template
    private final boolean preserveFormatting;
    private final boolean embedFonts; // for PDF output
    private final boolean generateTableOfContents;
    private final int marginTop; // in millimeters
    private final int marginBottom; // in millimeters
    private final int marginLeft; // in millimeters
    private final int marginRight; // in millimeters
    private final FileFormat outputFormat; // Requirement REQ-2.5: Output format for document conversion

    /**
     * Creates a new DocumentSettings instance.
     * 
     * @param templatePath            the template file path, or null for no
     *                                template
     * @param preserveFormatting      whether to preserve original formatting
     * @param embedFonts              whether to embed fonts in PDF output
     * @param generateTableOfContents whether to generate table of contents
     * @param marginTop               the top margin in millimeters (0-100)
     * @param marginBottom            the bottom margin in millimeters (0-100)
     * @param marginLeft              the left margin in millimeters (0-100)
     * @param marginRight             the right margin in millimeters (0-100)
     * @param outputFormat            the target output format (must be DOCUMENT
     *                                category)
     */
    @JsonCreator
    private DocumentSettings(
            @JsonProperty("templatePath") Path templatePath,
            @JsonProperty("preserveFormatting") boolean preserveFormatting,
            @JsonProperty("embedFonts") boolean embedFonts,
            @JsonProperty("generateTableOfContents") boolean generateTableOfContents,
            @JsonProperty("marginTop") int marginTop,
            @JsonProperty("marginBottom") int marginBottom,
            @JsonProperty("marginLeft") int marginLeft,
            @JsonProperty("marginRight") int marginRight,
            @JsonProperty("outputFormat") FileFormat outputFormat) {
        this.templatePath = templatePath;
        this.preserveFormatting = preserveFormatting;
        this.embedFonts = embedFonts;
        this.generateTableOfContents = generateTableOfContents;
        this.marginTop = marginTop;
        this.marginBottom = marginBottom;
        this.marginLeft = marginLeft;
        this.marginRight = marginRight;
        this.outputFormat = outputFormat;
    }

    /**
     * Returns the template file path.
     * 
     * @return the template path, or null if no template
     */
    @JsonProperty("templatePath")
    public Path templatePath() {
        return templatePath;
    }

    /**
     * Returns whether to preserve original formatting.
     * 
     * @return true if formatting should be preserved
     */
    @JsonProperty("preserveFormatting")
    public boolean preserveFormatting() {
        return preserveFormatting;
    }

    /**
     * Returns whether to embed fonts in PDF output.
     * 
     * @return true if fonts should be embedded
     */
    @JsonProperty("embedFonts")
    public boolean embedFonts() {
        return embedFonts;
    }

    /**
     * Returns whether to generate a table of contents.
     * 
     * @return true if table of contents should be generated
     */
    @JsonProperty("generateTableOfContents")
    public boolean generateTableOfContents() {
        return generateTableOfContents;
    }

    /**
     * Returns the top margin in millimeters.
     * 
     * @return the top margin (0-100mm)
     */
    @JsonProperty("marginTop")
    public int marginTop() {
        return marginTop;
    }

    /**
     * Returns the bottom margin in millimeters.
     * 
     * @return the bottom margin (0-100mm)
     */
    @JsonProperty("marginBottom")
    public int marginBottom() {
        return marginBottom;
    }

    /**
     * Returns the left margin in millimeters.
     * 
     * @return the left margin (0-100mm)
     */
    @JsonProperty("marginLeft")
    public int marginLeft() {
        return marginLeft;
    }

    /**
     * Returns the right margin in millimeters.
     * 
     * @return the right margin (0-100mm)
     */
    @JsonProperty("marginRight")
    public int marginRight() {
        return marginRight;
    }

    /**
     * Returns the target output format.
     * Requirement REQ-2.5: Document output format selection.
     * 
     * @return the output format (must be DOCUMENT category)
     */
    @JsonProperty("outputFormat")
    public FileFormat outputFormat() {
        return outputFormat;
    }

    /**
     * Validates document settings.
     * Requirement REQ-2.5: Validate output format is DOCUMENT category.
     * 
     * @return true if settings are valid
     */
    @JsonIgnore
    public boolean isValid() {
        // Template path validation (if provided, must exist)
        if (templatePath != null && !templatePath.toFile().exists()) {
            return false;
        }

        // Margin validation (0-100mm)
        if (marginTop < 0 || marginTop > 100) {
            return false;
        }
        if (marginBottom < 0 || marginBottom > 100) {
            return false;
        }
        if (marginLeft < 0 || marginLeft > 100) {
            return false;
        }
        if (marginRight < 0 || marginRight > 100) {
            return false;
        }

        // Output format must be DOCUMENT category
        if (outputFormat == null || !outputFormat.supportsCategory(FormatCategory.DOCUMENT)) {
            return false;
        }

        return true;
    }

    /**
     * Creates a new Builder for DocumentSettings.
     * 
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating DocumentSettings instances.
     */
    public static class Builder {
        private Path templatePath; // null means no template
        private boolean preserveFormatting = true;
        private boolean embedFonts = false;
        private boolean generateTableOfContents = false;
        private int marginTop = 25; // 25mm default
        private int marginBottom = 25;
        private int marginLeft = 25;
        private int marginRight = 25;
        private FileFormat outputFormat = FileFormat.PDF;

        /**
         * Sets the template file path.
         * 
         * @param templatePath the template path, or null for no template
         * @return this builder
         */
        public Builder templatePath(Path templatePath) {
            this.templatePath = templatePath;
            return this;
        }

        /**
         * Sets whether to preserve original formatting.
         * 
         * @param preserveFormatting true to preserve formatting
         * @return this builder
         */
        public Builder preserveFormatting(boolean preserveFormatting) {
            this.preserveFormatting = preserveFormatting;
            return this;
        }

        /**
         * Sets whether to embed fonts in PDF output.
         * 
         * @param embedFonts true to embed fonts
         * @return this builder
         */
        public Builder embedFonts(boolean embedFonts) {
            this.embedFonts = embedFonts;
            return this;
        }

        /**
         * Sets whether to generate a table of contents.
         * 
         * @param generateTableOfContents true to generate table of contents
         * @return this builder
         */
        public Builder generateTableOfContents(boolean generateTableOfContents) {
            this.generateTableOfContents = generateTableOfContents;
            return this;
        }

        /**
         * Sets the top margin.
         * 
         * @param marginTop the top margin in millimeters (0-100)
         * @return this builder
         */
        public Builder marginTop(int marginTop) {
            this.marginTop = marginTop;
            return this;
        }

        /**
         * Sets the bottom margin.
         * 
         * @param marginBottom the bottom margin in millimeters (0-100)
         * @return this builder
         */
        public Builder marginBottom(int marginBottom) {
            this.marginBottom = marginBottom;
            return this;
        }

        /**
         * Sets the left margin.
         * 
         * @param marginLeft the left margin in millimeters (0-100)
         * @return this builder
         */
        public Builder marginLeft(int marginLeft) {
            this.marginLeft = marginLeft;
            return this;
        }

        /**
         * Sets the right margin.
         * 
         * @param marginRight the right margin in millimeters (0-100)
         * @return this builder
         */
        public Builder marginRight(int marginRight) {
            this.marginRight = marginRight;
            return this;
        }

        /**
         * Sets the output format.
         * Requirement REQ-2.5: Document output format selection.
         * 
         * @param outputFormat the output format (must be DOCUMENT category)
         * @return this builder
         */
        public Builder outputFormat(FileFormat outputFormat) {
            this.outputFormat = outputFormat;
            return this;
        }

        /**
         * Validates the builder state.
         * 
         * @throws IllegalArgumentException if validation fails
         */
        private void validate() {
            if (marginTop < 0 || marginTop > 100) {
                throw new IllegalArgumentException("marginTop must be between 0 and 100, got: " + marginTop);
            }
            if (marginBottom < 0 || marginBottom > 100) {
                throw new IllegalArgumentException("marginBottom must be between 0 and 100, got: " + marginBottom);
            }
            if (marginLeft < 0 || marginLeft > 100) {
                throw new IllegalArgumentException("marginLeft must be between 0 and 100, got: " + marginLeft);
            }
            if (marginRight < 0 || marginRight > 100) {
                throw new IllegalArgumentException("marginRight must be between 0 and 100, got: " + marginRight);
            }
            if (outputFormat == null) {
                throw new IllegalArgumentException("Output format cannot be null");
            }
            if (!outputFormat.supportsCategory(FormatCategory.DOCUMENT)) {
                throw new IllegalArgumentException("Output format must be DOCUMENT category, got: " + outputFormat);
            }
        }

        /**
         * Builds the DocumentSettings instance.
         * Validates that margins are within range and output format is DOCUMENT
         * category.
         * 
         * @return a new DocumentSettings instance
         * @throws IllegalArgumentException if validation fails
         */
        public DocumentSettings build() {
            validate();
            return new DocumentSettings(templatePath, preserveFormatting, embedFonts,
                    generateTableOfContents, marginTop, marginBottom, marginLeft, marginRight, outputFormat);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DocumentSettings that = (DocumentSettings) o;
        return preserveFormatting == that.preserveFormatting &&
                embedFonts == that.embedFonts &&
                generateTableOfContents == that.generateTableOfContents &&
                marginTop == that.marginTop &&
                marginBottom == that.marginBottom &&
                marginLeft == that.marginLeft &&
                marginRight == that.marginRight &&
                Objects.equals(templatePath, that.templatePath) &&
                Objects.equals(outputFormat, that.outputFormat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(templatePath, preserveFormatting, embedFonts,
                generateTableOfContents, marginTop, marginBottom, marginLeft, marginRight, outputFormat);
    }

    @Override
    public String toString() {
        return "DocumentSettings{" +
                "templatePath=" + templatePath +
                ", preserveFormatting=" + preserveFormatting +
                ", embedFonts=" + embedFonts +
                ", generateTableOfContents=" + generateTableOfContents +
                ", marginTop=" + marginTop +
                ", marginBottom=" + marginBottom +
                ", marginLeft=" + marginLeft +
                ", marginRight=" + marginRight +
                ", outputFormat=" + outputFormat +
                '}';
    }
}
