package org.omc.model;

/**
 * External tools used for media conversion.
 * Each tool handles specific format categories.
 * 
 * Requirements: REQ-004.1
 */
public enum ConversionTool {
    /**
     * FFmpeg - handles video, audio, and image conversions.
     */
    FFMPEG,

    /**
     * Pandoc - handles text-based document conversions (Markdown, HTML, RTF, etc.).
     */
    PANDOC,

    /**
     * LibreOffice CLI - handles office document conversions (DOCX, PDF, XLSX, PPTX,
     * ODT).
     */
    LIBREOFFICE,

    /**
     * ImageMagick - handles image conversions (JPEG, PNG, GIF, BMP, TIFF, WebP,
     * SVG).
     */
    IMAGEMAGICK
}
