package org.omc.model;

import java.util.Arrays;
import java.util.List;

/**
 * Enumeration of supported file formats for conversion.
 * Each format belongs to a category (VIDEO, AUDIO, IMAGE, DOCUMENT).
 * Some formats like PDF support dual categories (both DOCUMENT and IMAGE).
 * 
 * Requirements: REQ-006.1, REQ-006.2, REQ-006.3, REQ-006.4, REQ-PDF-1.2
 */
public enum FileFormat {
    // Video formats (REQ-006.1)
    MP4(FormatCategory.VIDEO, null, List.of("mp4", "m4v"), "video/mp4"),
    AVI(FormatCategory.VIDEO, null, List.of("avi"), "video/x-msvideo"),
    MOV(FormatCategory.VIDEO, null, List.of("mov", "qt"), "video/quicktime"),
    MKV(FormatCategory.VIDEO, null, List.of("mkv"), "video/x-matroska"),
    WMV(FormatCategory.VIDEO, null, List.of("wmv"), "video/x-ms-wmv"),
    FLV(FormatCategory.VIDEO, null, List.of("flv"), "video/x-flv"),
    WEBM(FormatCategory.VIDEO, null, List.of("webm"), "video/webm"),

    // Audio formats (REQ-006.2)
    MP3(FormatCategory.AUDIO, FormatCategory.VIDEO, List.of("mp3"), "audio/mpeg"),
    WAV(FormatCategory.AUDIO, null, List.of("wav"), "audio/wav"),
    FLAC(FormatCategory.AUDIO, null, List.of("flac"), "audio/flac"),
    AAC(FormatCategory.AUDIO, null, List.of("aac"), "audio/aac"),
    OGG(FormatCategory.AUDIO, null, List.of("ogg", "oga"), "audio/ogg"),
    M4A(FormatCategory.AUDIO, null, List.of("m4a"), "audio/mp4"),

    // Image formats (REQ-006.3)
    JPEG(FormatCategory.IMAGE, FormatCategory.DOCUMENT, List.of("jpg", "jpeg", "jpe"), "image/jpeg"),
    PNG(FormatCategory.IMAGE, null, List.of("png"), "image/png"),
    GIF(FormatCategory.IMAGE, null, List.of("gif"), "image/gif"),
    BMP(FormatCategory.IMAGE, null, List.of("bmp", "dib"), "image/bmp"),
    TIFF(FormatCategory.IMAGE, null, List.of("tiff", "tif"), "image/tiff"),
    WEBP(FormatCategory.IMAGE, null, List.of("webp"), "image/webp"),
    SVG(FormatCategory.IMAGE, null, List.of("svg"), "image/svg+xml"),

    // Document formats (REQ-006.4)
    DOCX(FormatCategory.DOCUMENT, null, List.of("docx"),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    DOC(FormatCategory.DOCUMENT, null, List.of("doc"), "application/msword"),
    // REQ-PDF-1.2: PDF supports both DOCUMENT and IMAGE categories
    PDF(FormatCategory.DOCUMENT, FormatCategory.IMAGE, List.of("pdf"), "application/pdf"),
    HTML(FormatCategory.DOCUMENT, null, List.of("html", "htm"), "text/html"),
    MARKDOWN(FormatCategory.DOCUMENT, null, List.of("md", "markdown"), "text/markdown"),
    RTF(FormatCategory.DOCUMENT, null, List.of("rtf"), "application/rtf"),
    ODT(FormatCategory.DOCUMENT, null, List.of("odt"), "application/vnd.oasis.opendocument.text"),
    ODS(FormatCategory.DOCUMENT, null, List.of("ods"), "application/vnd.oasis.opendocument.spreadsheet"),
    ODP(FormatCategory.DOCUMENT, null, List.of("odp"), "application/vnd.oasis.opendocument.presentation"),
    CSV(FormatCategory.DOCUMENT, null, List.of("csv"), "text/csv"),
    XLSX(FormatCategory.DOCUMENT, null, List.of("xlsx"),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    XLS(FormatCategory.DOCUMENT, null, List.of("xls"), "application/vnd.ms-excel"),
    PPTX(FormatCategory.DOCUMENT, null, List.of("pptx"),
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    PPT(FormatCategory.DOCUMENT, null, List.of("ppt"), "application/vnd.ms-powerpoint"),
    TXT(FormatCategory.DOCUMENT, null, List.of("txt"), "text/plain"),
    EPUB(FormatCategory.DOCUMENT, null, List.of("epub"), "application/epub+zip"),
    TEX(FormatCategory.DOCUMENT, null, List.of("tex"), "text/x-tex"),
    LATEX(FormatCategory.DOCUMENT, null, List.of("latex"), "text/x-latex"),
    RST(FormatCategory.DOCUMENT, null, List.of("rst"), "text/x-rst"),
    ORG(FormatCategory.DOCUMENT, null, List.of("org"), "text/org"),

    // Unknown format
    UNKNOWN(FormatCategory.UNKNOWN, null, List.of(), "application/octet-stream");

    private final FormatCategory category;
    private final FormatCategory secondaryCategory; // Nullable, for dual-category formats like PDF
    private final List<String> extensions;
    private final String mimeType;

    /**
     * Creates a FileFormat enum value with optional secondary category.
     *
     * @param category          The primary format category
     * @param secondaryCategory The secondary format category (nullable, for
     *                          dual-category formats)
     * @param extensions        List of file extensions (without dot)
     * @param mimeType          The MIME type for this format
     */
    FileFormat(FormatCategory category, FormatCategory secondaryCategory, List<String> extensions, String mimeType) {
        this.category = category;
        this.secondaryCategory = secondaryCategory;
        this.extensions = extensions;
        this.mimeType = mimeType;
    }

    /**
     * Gets all formats for a specific category.
     * Includes formats where the category is either primary or secondary (e.g., PDF
     * in IMAGE category).
     * Requirement REQ-PDF-1.2: PDF dual-category support
     *
     * @param category The format category
     * @return Array of formats in that category
     */
    public static FileFormat[] getFormatsByCategory(FormatCategory category) {
        return Arrays.stream(values())
                .filter(f -> f.supportsCategory(category))
                .toArray(FileFormat[]::new);
    }

    /**
     * Checks if this format supports a given category.
     * Returns true if the category matches either the primary or secondary
     * category.
     * 
     * @param category The category to check
     * @return true if format supports this category
     */
    public boolean supportsCategory(FormatCategory category) {
        return this.category == category || this.secondaryCategory == category;
    }

    /**
     * Gets the primary format category.
     *
     * @return The primary format category
     */
    public FormatCategory getCategory() {
        return category;
    }

    /**
     * Gets the list of file extensions for this format.
     *
     * @return List of extensions (without dot)
     */
    public List<String> getExtensions() {
        return extensions;
    }

    /**
     * Gets the MIME type for this format.
     *
     * @return The MIME type string
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Gets the primary file extension for this format.
     *
     * @return The first extension, or empty string if none
     */
    public String getPrimaryExtension() {
        return extensions.isEmpty() ? "" : extensions.get(0);
    }

    /**
     * Detects format from file extension.
     * Case-insensitive comparison.
     *
     * @param extension The file extension (with or without dot)
     * @return The detected FileFormat, or UNKNOWN if not recognized
     */
    public static FileFormat fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return UNKNOWN;
        }

        // Remove leading dot if present
        String ext = extension.toLowerCase().trim();
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }

        // Search for matching format
        for (FileFormat format : values()) {
            if (format.extensions.contains(ext)) {
                return format;
            }
        }

        return UNKNOWN;
    }

    /**
     * Detects format from MIME type.
     * Case-insensitive comparison.
     *
     * @param mimeType The MIME type string
     * @return The detected FileFormat, or UNKNOWN if not recognized
     */
    public static FileFormat fromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return UNKNOWN;
        }

        String mime = mimeType.toLowerCase().trim();

        // Search for exact match
        for (FileFormat format : values()) {
            if (format.mimeType.equalsIgnoreCase(mime)) {
                return format;
            }
        }

        return UNKNOWN;
    }

    /**
     * Checks if this format can be used as input.
     * All formats except UNKNOWN can be input.
     *
     * @return true if format can be used as input
     */
    public boolean isValidInput() {
        return this != UNKNOWN;
    }

    /**
     * Checks if this format can be used as output.
     * All formats except UNKNOWN can be output.
     *
     * @return true if format can be used as output
     */
    public boolean isValidOutput() {
        return this != UNKNOWN;
    }

}
