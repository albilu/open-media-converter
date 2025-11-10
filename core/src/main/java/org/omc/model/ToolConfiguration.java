package org.omc.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for external conversion tools.
 * Stores paths and version information for FFmpeg, Pandoc, LibreOffice, and
 * ImageMagick.
 * 
 * Requirements: REQ-004.1, REQ-DEP-2
 */
public class ToolConfiguration {
    @JsonProperty
    private Path ffmpegPath;

    @JsonProperty
    private String ffmpegVersion;

    @JsonProperty
    private Path ffprobePath;

    @JsonProperty
    private Path pandocPath;

    @JsonProperty
    private String pandocVersion;

    @JsonProperty
    private Path libreOfficePath;

    @JsonProperty
    private String libreOfficeVersion;

    /**
     * Path to the ImageMagick convert executable.
     */
    @JsonProperty
    private Path convertPath;

    /**
     * Version string for ImageMagick convert.
     */
    @JsonProperty
    private String convertVersion;

    /**
     * Creates an empty tool configuration.
     */
    public ToolConfiguration() {
    }

    /**
     * Gets the path to the FFmpeg executable.
     * 
     * @return FFmpeg path, or null if not found
     */
    @JsonProperty
    public Path getFfmpegPath() {
        return ffmpegPath;
    }

    /**
     * Sets the path to the FFmpeg executable.
     * 
     * @param ffmpegPath FFmpeg path
     */
    public void setFfmpegPath(Path ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    /**
     * Gets the FFmpeg version string.
     * 
     * @return FFmpeg version
     */
    @JsonProperty
    public String getFfmpegVersion() {
        return ffmpegVersion;
    }

    /**
     * Sets the FFmpeg version string.
     * 
     * @param ffmpegVersion FFmpeg version
     */
    public void setFfmpegVersion(String ffmpegVersion) {
        this.ffmpegVersion = ffmpegVersion;
    }

    /**
     * Gets the path to the ffprobe executable.
     * 
     * @return ffprobe path, or null if not found
     */
    @JsonProperty
    public Path getFfprobePath() {
        return ffprobePath;
    }

    /**
     * Sets the path to the ffprobe executable.
     * 
     * @param ffprobePath ffprobe path
     */
    public void setFfprobePath(Path ffprobePath) {
        this.ffprobePath = ffprobePath;
    }

    /**
     * Gets the path to the Pandoc executable.
     * 
     * @return Pandoc path, or null if not found
     */
    @JsonProperty
    public Path getPandocPath() {
        return pandocPath;
    }

    /**
     * Sets the path to the Pandoc executable.
     * 
     * @param pandocPath Pandoc path
     */
    public void setPandocPath(Path pandocPath) {
        this.pandocPath = pandocPath;
    }

    /**
     * Gets the Pandoc version string.
     * 
     * @return Pandoc version
     */
    @JsonProperty
    public String getPandocVersion() {
        return pandocVersion;
    }

    /**
     * Sets the Pandoc version string.
     * 
     * @param pandocVersion Pandoc version
     */
    public void setPandocVersion(String pandocVersion) {
        this.pandocVersion = pandocVersion;
    }

    /**
     * Gets the path to the LibreOffice (soffice) executable.
     * 
     * @return LibreOffice path, or null if not found
     */
    @JsonProperty
    public Path getLibreOfficePath() {
        return libreOfficePath;
    }

    /**
     * Sets the path to the LibreOffice (soffice) executable.
     * 
     * @param libreOfficePath LibreOffice path
     */
    public void setLibreOfficePath(Path libreOfficePath) {
        this.libreOfficePath = libreOfficePath;
    }

    /**
     * Gets the LibreOffice version string.
     * 
     * @return LibreOffice version
     */
    @JsonProperty
    public String getLibreOfficeVersion() {
        return libreOfficeVersion;
    }

    /**
     * Sets the LibreOffice version string.
     * 
     * @param libreOfficeVersion LibreOffice version
     */
    public void setLibreOfficeVersion(String libreOfficeVersion) {
        this.libreOfficeVersion = libreOfficeVersion;
    }

    /**
     * Gets the path to the ImageMagick convert executable.
     * 
     * @return convert path, or null if not found
     */
    @JsonProperty
    public Path getConvertPath() {
        return convertPath;
    }

    /**
     * Sets the path to the ImageMagick convert executable.
     * 
     * @param convertPath convert path
     */
    public void setConvertPath(Path convertPath) {
        this.convertPath = convertPath;
    }

    /**
     * Gets the ImageMagick convert version string.
     * 
     * @return convert version
     */
    @JsonProperty
    public String getConvertVersion() {
        return convertVersion;
    }

    /**
     * Sets the ImageMagick convert version string.
     * 
     * @param convertVersion convert version
     */
    public void setConvertVersion(String convertVersion) {
        this.convertVersion = convertVersion;
    }

    /**
     * Checks if FFmpeg is available.
     * 
     * @return true if FFmpeg path is set and ffprobe is also available
     */
    @JsonIgnore
    public boolean isFfmpegAvailable() {
        return ffmpegPath != null && ffprobePath != null;
    }

    /**
     * Checks if Pandoc is available.
     * 
     * @return true if Pandoc path is set
     */
    @JsonIgnore
    public boolean isPandocAvailable() {
        return pandocPath != null;
    }

    /**
     * Checks if LibreOffice is available.
     * 
     * @return true if LibreOffice path is set
     */
    @JsonIgnore
    public boolean isLibreOfficeAvailable() {
        return libreOfficePath != null;
    }

    /**
     * Checks if ImageMagick is available.
     * 
     * @return true if ImageMagick convert path is set
     */
    @JsonIgnore
    public boolean isImageMagickAvailable() {
        return convertPath != null;
    }

    /**
     * Checks if the specified tool is available.
     * 
     * @param tool the conversion tool to check
     * @return true if the tool is available
     */
    @JsonIgnore
    public boolean isToolAvailable(ConversionTool tool) {
        return switch (tool) {
            case FFMPEG -> isFfmpegAvailable();
            case PANDOC -> isPandocAvailable();
            case LIBREOFFICE -> isLibreOfficeAvailable();
            case IMAGEMAGICK -> isImageMagickAvailable();
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ToolConfiguration that = (ToolConfiguration) o;
        return Objects.equals(ffmpegPath, that.ffmpegPath) &&
                Objects.equals(ffmpegVersion, that.ffmpegVersion) &&
                Objects.equals(ffprobePath, that.ffprobePath) &&
                Objects.equals(pandocPath, that.pandocPath) &&
                Objects.equals(pandocVersion, that.pandocVersion) &&
                Objects.equals(libreOfficePath, that.libreOfficePath) &&
                Objects.equals(libreOfficeVersion, that.libreOfficeVersion) &&
                Objects.equals(convertPath, that.convertPath) &&
                Objects.equals(convertVersion, that.convertVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ffmpegPath, ffmpegVersion, ffprobePath, pandocPath,
                pandocVersion, libreOfficePath, libreOfficeVersion,
                convertPath, convertVersion);
    }

    @Override
    public String toString() {
        return "ToolConfiguration{" +
                "ffmpegPath=" + ffmpegPath +
                ", ffmpegVersion='" + ffmpegVersion + '\'' +
                ", ffprobePath=" + ffprobePath +
                ", pandocPath=" + pandocPath +
                ", pandocVersion='" + pandocVersion + '\'' +
                ", libreOfficePath=" + libreOfficePath +
                ", libreOfficeVersion='" + libreOfficeVersion + '\'' +
                ", convertPath=" + convertPath +
                ", convertVersion='" + convertVersion + '\'' +
                '}';
    }
}
