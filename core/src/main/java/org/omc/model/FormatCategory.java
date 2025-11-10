package org.omc.model;

/**
 * Categories of file formats supported by the application.
 * Each FileFormat belongs to exactly one category.
 * 
 * Requirements: REQ-006.1, REQ-006.2, REQ-006.3, REQ-006.4
 */
public enum FormatCategory {
    /**
     * Video formats (MP4, AVI, MOV, MKV, WMV, FLV, WebM)
     */
    VIDEO,

    /**
     * Audio formats (MP3, WAV, FLAC, AAC, OGG, M4A)
     */
    AUDIO,

    /**
     * Image formats (JPEG, PNG, GIF, BMP, TIFF, WebP)
     */
    IMAGE,

    /**
     * Document formats (DOCX, PDF, HTML, Markdown, RTF, ODT, XLSX, PPTX, TXT, EPUB)
     */
    DOCUMENT,

    /**
     * Unknown or unsupported format
     */
    UNKNOWN
}
