// filepath: src/main/java/org/omc/core/ToolManager.java

package org.omc.core;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

import org.omc.exception.ErrorCode;
import org.omc.exception.ToolExecutionException;
import org.omc.model.AudioSettings;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionSettings;
import org.omc.model.ConversionTool;
import org.omc.model.DocumentSettings;
import org.omc.model.FileFormat;
import org.omc.model.FormatCategory;
import org.omc.model.ImageSettings;
import org.omc.model.VideoSettings;
import org.omc.service.FFmpegService;
import org.omc.service.ImageMagickService;
import org.omc.service.LibreOfficeService;
import org.omc.service.PandocService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages conversion tool selection and execution.
 * Coordinates FFmpegService, PandocService, LibreOfficeService, and
 * ImageMagickService
 * to provide a unified conversion interface.
 * 
 * Tool selection logic:
 * - Video/Audio formats → FFmpegService
 * - Image formats → ImageMagickService
 * - Text document formats (Markdown, HTML, RTF, TXT, EPUB) → PandocService
 * - Office document formats (DOCX, PDF, XLSX, PPTX, ODT, ODS, ODP) →
 * LibreOfficeService
 * 
 * Requirements:
 * - REQ-004.1: Tool selection based on format pairs
 * - REQ-006.1: Video format conversion support
 * - REQ-006.2: Audio format conversion support
 * - REQ-006.3: Image format conversion support
 * - REQ-006.4: Document format conversion support
 * - REQ-SEL-2: IMAGE category routing to ImageMagick
 */
public class ToolManager {

    private static final Logger logger = LoggerFactory.getLogger(ToolManager.class);

    private final FFmpegService ffmpegService;
    private final PandocService pandocService;
    private final LibreOfficeService libreOfficeService;
    private final ImageMagickService imageMagickService;

    // Pandoc-specific formats (text-based documents)
    // Requirement REQ-SEL-1: Extended document format support
    private static final Set<FileFormat> PANDOC_FORMATS = Set.of(
            FileFormat.MARKDOWN, FileFormat.HTML, FileFormat.RTF,
            FileFormat.TXT, FileFormat.EPUB, FileFormat.TEX, FileFormat.LATEX,
            FileFormat.RST, FileFormat.ORG);

    // LibreOffice-specific formats (office documents and PDF)
    // Requirement REQ-SEL-1: Legacy Office format support
    private static final Set<FileFormat> LIBREOFFICE_FORMATS = Set.of(
            FileFormat.DOCX, FileFormat.PDF, FileFormat.XLSX, FileFormat.PPTX,
            FileFormat.ODT, FileFormat.ODS, FileFormat.ODP, FileFormat.DOC,
            FileFormat.XLS, FileFormat.PPT);

    /**
     * Creates a new ToolManager with specified tool services.
     * 
     * Requirement REQ-004.1: Tool coordination and selection
     * Requirement REQ-DEP-1: Dependency injection for all services
     * 
     * @param ffmpegService      service for video/audio conversion
     * @param pandocService      service for text document conversion
     * @param libreOfficeService service for office document conversion
     * @param imageMagickService service for image conversion (may be null if
     *                           convert binary not found)
     * @throws NullPointerException if ffmpegService, pandocService, or
     *                              libreOfficeService is null
     */
    public ToolManager(
            FFmpegService ffmpegService,
            PandocService pandocService,
            LibreOfficeService libreOfficeService,
            ImageMagickService imageMagickService) {

        this.ffmpegService = Objects.requireNonNull(ffmpegService, "ffmpegService must not be null");
        this.pandocService = Objects.requireNonNull(pandocService, "pandocService must not be null");
        this.libreOfficeService = Objects.requireNonNull(libreOfficeService, "libreOfficeService must not be null");
        this.imageMagickService = imageMagickService; // May be null

        logger.info("ToolManager initialized with all four services (ImageMagick: {})",
                imageMagickService != null ? "available" : "not available");
    }

    /**
     * Selects the appropriate conversion tool for the given format pair.
     * 
     * Selection logic (Requirement REQ-004.1):
     * - VIDEO, AUDIO categories → FFMPEG
     * - IMAGE category → IMAGEMAGICK (Requirement REQ-SEL-2)
     * - DOCUMENT category:
     * - Markdown, HTML, RTF, TXT, EPUB → PANDOC
     * - DOCX, PDF, XLSX, PPTX, ODT, ODS, ODP → LIBREOFFICE
     * 
     * @param inputFormat  input file format
     * @param outputFormat desired output format
     * @return the appropriate conversion tool
     * @throws ToolExecutionException if no suitable tool found for format pair
     * @throws NullPointerException   if either format is null
     */
    public ConversionTool selectTool(FileFormat inputFormat, FileFormat outputFormat)
            throws ToolExecutionException {

        Objects.requireNonNull(inputFormat, "inputFormat must not be null");
        Objects.requireNonNull(outputFormat, "outputFormat must not be null");

        logger.debug("Selecting tool for conversion: {} ({}) → {} ({})",
                inputFormat, inputFormat.getCategory(), outputFormat, outputFormat.getCategory());

        // Check for UNKNOWN formats
        if (inputFormat == FileFormat.UNKNOWN || outputFormat == FileFormat.UNKNOWN) {
            logger.error("Cannot select tool for UNKNOWN format: input={}, output={}",
                    inputFormat, outputFormat);
            throw new ToolExecutionException(
                    "Unable to determine conversion tool for unknown file format. " +
                            "Please ensure the file has a valid extension.",
                    ErrorCode.TOOL_NOT_FOUND,
                    "Cannot convert UNKNOWN format");
        }

        FormatCategory inputCategory = inputFormat.getCategory();
        FormatCategory outputCategory = outputFormat.getCategory();

        // Video/Audio: use FFmpeg
        // Requirement REQ-006.1, REQ-006.2
        if (inputCategory == FormatCategory.VIDEO ||
                inputCategory == FormatCategory.AUDIO) {
            logger.debug("Selected FFMPEG for {} → {} (media category: {})",
                    inputFormat, outputFormat, inputCategory);
            return ConversionTool.FFMPEG;
        }

        // Image: use ImageMagick
        // Requirement REQ-SEL-2, REQ-IMG-2
        if (inputCategory == FormatCategory.IMAGE) {
            logger.debug("Selected IMAGEMAGICK for {} → {} (IMAGE category)",
                    inputFormat, outputFormat);
            return ConversionTool.IMAGEMAGICK;
        }

        // Document formats: choose between Pandoc and LibreOffice
        // Requirement REQ-006.4
        if (inputCategory == FormatCategory.DOCUMENT) {
            ConversionTool tool = selectDocumentTool(inputFormat, outputFormat);
            logger.debug("Selected {} for document conversion {} → {}", tool, inputFormat, outputFormat);
            return tool;
        }

        // Unsupported category
        logger.error("Unsupported format category: input={} ({}), output={} ({})",
                inputFormat, inputCategory, outputFormat, outputCategory);
        throw new ToolExecutionException(
                "The format category '" + inputCategory + "' is not supported for conversion.",
                ErrorCode.TOOL_NOT_FOUND,
                "Unsupported format category: " + inputCategory);
    }

    /**
     * Selects the appropriate tool for document conversions.
     * 
     * Priority:
     * 1. If input is Pandoc-specific (Markdown, HTML, RTF, TXT, EPUB) → Pandoc
     * 2. If input is LibreOffice-specific (DOCX, PDF, XLSX, PPTX, ODT, ODS, ODP) →
     * LibreOffice
     * 3. If output is Pandoc-specific and input supported by Pandoc → Pandoc
     * 4. Otherwise → LibreOffice
     * 
     * @param inputFormat  input document format
     * @param outputFormat output document format
     * @return PANDOC or LIBREOFFICE
     */
    private ConversionTool selectDocumentTool(FileFormat inputFormat, FileFormat outputFormat) {
        // Pandoc for text-based documents (Markdown, HTML, RTF, TXT, EPUB)
        if (PANDOC_FORMATS.contains(inputFormat)) {
            return ConversionTool.PANDOC;
        }

        // LibreOffice for office documents (DOCX, PDF, XLSX, PPTX, ODT, ODS, ODP)
        if (LIBREOFFICE_FORMATS.contains(inputFormat)) {
            return ConversionTool.LIBREOFFICE;
        }

        // If output is text-based and Pandoc can handle it, prefer Pandoc
        if (PANDOC_FORMATS.contains(outputFormat) && pandocService.supportsInput(inputFormat)) {
            return ConversionTool.PANDOC;
        }

        // Default to LibreOffice for document conversions
        return ConversionTool.LIBREOFFICE;
    }

    /**
     * Checks if a specific tool is available.
     * 
     * Requirement REQ-SEL-4: Tool availability checks
     * 
     * @param tool the conversion tool to check
     * @return true if tool is available, false otherwise
     * @throws NullPointerException if tool is null
     */
    public boolean isToolAvailable(ConversionTool tool) {
        Objects.requireNonNull(tool, "tool must not be null");

        // For this implementation, we assume tools are available if services were
        // instantiated
        // In a real implementation, this would check if binaries exist and are
        // executable
        boolean available = switch (tool) {
            case FFMPEG -> ffmpegService != null;
            case PANDOC -> pandocService != null;
            case LIBREOFFICE -> libreOfficeService != null;
            case IMAGEMAGICK -> imageMagickService != null;
        };

        logger.debug("Tool availability check: {} = {}", tool, available);
        return available;
    }

    /**
     * Executes a conversion using the specified tool.
     * 
     * Requirements:
     * - REQ-004.1: Tool execution delegation
     * - REQ-004.2: Conversion execution
     * 
     * @param tool             the conversion tool to use
     * @param inputPath        input file path
     * @param outputPath       output file path
     * @param settings         conversion settings
     * @param progressCallback callback for progress updates
     * @return conversion result
     * @throws ToolExecutionException if conversion fails
     * @throws NullPointerException   if any parameter is null
     */
    public ConversionResult executeTool(
            ConversionTool tool,
            Path inputPath,
            Path outputPath,
            FileFormat outputFormat,
            ConversionSettings settings,
            ProgressCallback progressCallback) throws ToolExecutionException {

        return executeTool(tool, inputPath, outputPath, outputFormat, settings, progressCallback, null,
                ProcessRegistry.noOp());
    }

    public ConversionResult executeTool(
            ConversionTool tool,
            Path inputPath,
            Path outputPath,
            FileFormat outputFormat,
            ConversionSettings settings,
            ProgressCallback progressCallback,
            String fileId,
            ProcessRegistry processRegistry) throws ToolExecutionException {

        Objects.requireNonNull(tool, "tool must not be null");
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(outputFormat, "outputFormat must not be null");
        Objects.requireNonNull(settings, "settings must not be null");
        Objects.requireNonNull(progressCallback, "progressCallback must not be null");
        Objects.requireNonNull(processRegistry, "processRegistry must not be null");

        logger.info("Executing conversion with {}: {} → {}", tool, inputPath, outputPath);

        // Extract section-specific settings based on the tool being used
        // Requirement REQ-PDF-1.2: Handle dual-category formats (PDF as DOCUMENT +
        // IMAGE)
        // For ImageMagick, always use imageSettings even when output is PDF
        Object sectionSettings = switch (tool) {
            case FFMPEG -> switch (outputFormat.getCategory()) {
                case VIDEO -> settings.videoSettings();
                case AUDIO -> settings.audioSettings();
                default -> throw new ToolExecutionException(
                        "FFmpeg only supports video and audio formats, but output format is: "
                                + outputFormat.getCategory(),
                        ErrorCode.INVALID_SETTINGS,
                        "ffmpeg");
            };
            case IMAGEMAGICK -> settings.imageSettings();
            case PANDOC -> settings.documentSettings();
            case LIBREOFFICE -> settings.documentSettings();
        };

        return this.executeTool(tool, inputPath, outputPath, outputFormat, sectionSettings, progressCallback, fileId,
                processRegistry);
    }

    /**
     * Executes FFmpeg for video/audio conversion.
     */
    private ConversionResult executeFFmpeg(
            Path inputPath,
            Path outputPath,
            FileFormat outputFormat,
            Object sectionSettings,
            ProgressCallback progressCallback,
            String fileId,
            ProcessRegistry processRegistry) throws ToolExecutionException {

        FormatCategory category = outputFormat.getCategory();

        return switch (category) {
            case VIDEO -> {
                if (!(sectionSettings instanceof VideoSettings)) {
                    throw new ToolExecutionException(
                            "Video settings are required for video format conversion.",
                            ErrorCode.INVALID_SETTINGS,
                            "ffmpeg");
                }
                VideoSettings videoSettings = (VideoSettings) sectionSettings;
                yield ffmpegService.convertVideo(inputPath, outputPath, videoSettings, progressCallback, fileId,
                        processRegistry);
            }
            case AUDIO -> {
                if (!(sectionSettings instanceof AudioSettings)) {
                    throw new ToolExecutionException(
                            "Audio settings are required for audio format conversion.",
                            ErrorCode.INVALID_SETTINGS,
                            "ffmpeg");
                }
                AudioSettings audioSettings = (AudioSettings) sectionSettings;
                yield ffmpegService.convertAudio(inputPath, outputPath, audioSettings, progressCallback, fileId,
                        processRegistry);
            }
            default -> throw new ToolExecutionException(
                    "FFmpeg only supports video and audio formats.",
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "ffmpeg");
        };
    }

    /**
     * Executes Pandoc for text document conversion.
     */
    private ConversionResult executePandoc(
            Path inputPath,
            Path outputPath,
            Object sectionSettings,
            ProgressCallback progressCallback,
            String fileId,
            ProcessRegistry processRegistry) throws ToolExecutionException {

        if (!(sectionSettings instanceof DocumentSettings)) {
            throw new ToolExecutionException(
                    "Document settings are required for document format conversion.",
                    ErrorCode.INVALID_SETTINGS,
                    "pandoc");
        }

        DocumentSettings documentSettings = (DocumentSettings) sectionSettings;
        return pandocService.convertDocument(inputPath, outputPath, documentSettings, progressCallback, fileId,
                processRegistry);
    }

    /**
     * Executes LibreOffice for office document conversion.
     */
    private ConversionResult executeLibreOffice(
            Path inputPath,
            Path outputPath,
            Object sectionSettings,
            ProgressCallback progressCallback,
            String fileId,
            ProcessRegistry processRegistry) throws ToolExecutionException {

        if (!(sectionSettings instanceof DocumentSettings)) {
            throw new ToolExecutionException(
                    "Document settings are required for document format conversion.",
                    ErrorCode.INVALID_SETTINGS,
                    "libreoffice");
        }

        DocumentSettings documentSettings = (DocumentSettings) sectionSettings;
        return libreOfficeService.convertDocument(inputPath, outputPath, documentSettings, progressCallback, fileId,
                processRegistry);
    }

    /**
     * Executes ImageMagick for image conversion.
     * 
     * Requirement REQ-IMG-2: ImageMagick service integration
     * 
     * @param inputPath        input file path
     * @param outputPath       output file path
     * @param sectionSettings  settings object (must be ImageSettings)
     * @param progressCallback callback for progress updates
     * @param fileId           file identifier for process tracking
     * @param processRegistry  registry for tracking conversion processes
     * @return conversion result
     * @throws ToolExecutionException if settings type mismatch or conversion fails
     */
    private ConversionResult executeImageMagick(
            Path inputPath,
            Path outputPath,
            Object sectionSettings,
            ProgressCallback progressCallback,
            String fileId,
            ProcessRegistry processRegistry) throws ToolExecutionException {

        if (imageMagickService == null) {
            throw new ToolExecutionException(
                    "ImageMagick 'convert' binary not found. Please install ImageMagick to convert image files.",
                    ErrorCode.TOOL_NOT_FOUND,
                    "imagemagick");
        }

        if (!(sectionSettings instanceof ImageSettings)) {
            throw new ToolExecutionException(
                    "Image settings are required for image format conversion.",
                    ErrorCode.INVALID_SETTINGS,
                    "imagemagick");
        }

        ImageSettings imageSettings = (ImageSettings) sectionSettings;
        return imageMagickService.convertImage(inputPath, outputPath, imageSettings, progressCallback, fileId,
                processRegistry);
    }

    /**
     * Validates that the section settings object matches the expected category.
     * Requirement REQ-007: Validate per-file settings type consistency.
     * 
     * @param sectionSettings the section settings object (may be null)
     * @param category        the expected format category
     * @throws ToolExecutionException if settings don't match expected category
     */
    private void validateSettingsMatchCategory(Object sectionSettings, FormatCategory category)
            throws ToolExecutionException {

        if (sectionSettings == null) {
            throw new ToolExecutionException(
                    "Section settings cannot be null for category: " + category,
                    ErrorCode.INVALID_SETTINGS,
                    "tool-manager");
        }

        boolean isValid = switch (category) {
            case VIDEO -> sectionSettings instanceof VideoSettings;
            case AUDIO -> sectionSettings instanceof AudioSettings;
            case IMAGE -> sectionSettings instanceof ImageSettings;
            case DOCUMENT -> sectionSettings instanceof DocumentSettings;
            case UNKNOWN -> false;
        };

        if (!isValid) {
            throw new ToolExecutionException(
                    String.format("Settings type mismatch: expected %s settings but got %s",
                            category, sectionSettings.getClass().getSimpleName()),
                    ErrorCode.INVALID_SETTINGS,
                    "tool-manager");
        }
    }

    /**
     * Gets the FFmpeg service instance.
     * 
     * @return the FFmpeg service
     */
    public FFmpegService getFFmpegService() {
        return ffmpegService;
    }

    /**
     * Gets the Pandoc service instance.
     * 
     * @return the Pandoc service
     */
    public PandocService getPandocService() {
        return pandocService;
    }

    /**
     * Gets the LibreOffice service instance.
     * 
     * @return the LibreOffice service
     */
    public LibreOfficeService getLibreOfficeService() {
        return libreOfficeService;
    }

    /**
     * Gets the ImageMagick service instance.
     * 
     * Requirement REQ-IMG-2: ImageMagick service access
     * 
     * @return the ImageMagick service
     */
    public ImageMagickService getImageMagickService() {
        return imageMagickService;
    }

    /**
     * Executes the specified conversion tool with the resolved section-specific
     * settings.
     * 
     * Requirement REQ-004.1: Execute conversion tool with appropriate parameters.
     * Requirement REQ-007: Support per-file settings overrides.
     * 
     * @param tool             the conversion tool to use
     * @param inputPath        input file path
     * @param outputPath       output file path
     * @param outputFormat     desired output format
     * @param sectionSettings  section-specific settings (VideoSettings,
     *                         AudioSettings, etc.)
     * @param progressCallback callback for progress updates
     * @return conversion result
     * @throws ToolExecutionException if conversion fails or settings are invalid
     * @throws NullPointerException   if any required parameter is null
     */
    public ConversionResult executeTool(
            ConversionTool tool,
            Path inputPath,
            Path outputPath,
            FileFormat outputFormat,
            Object sectionSettings,
            ProgressCallback progressCallback) throws ToolExecutionException {

        return executeTool(tool, inputPath, outputPath, outputFormat, sectionSettings, progressCallback, null,
                ProcessRegistry.noOp());
    }

    public ConversionResult executeTool(
            ConversionTool tool,
            Path inputPath,
            Path outputPath,
            FileFormat outputFormat,
            Object sectionSettings,
            ProgressCallback progressCallback,
            String fileId,
            ProcessRegistry processRegistry) throws ToolExecutionException {

        Objects.requireNonNull(tool, "tool must not be null");
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(outputFormat, "outputFormat must not be null");
        Objects.requireNonNull(progressCallback, "progressCallback must not be null");
        Objects.requireNonNull(processRegistry, "processRegistry must not be null");

        // REQ-PDF-1.2: Validate settings based on tool type, not output format category
        // This allows ImageMagick to use ImageSettings even when converting to PDF
        // (DOCUMENT category)
        FormatCategory expectedCategory = switch (tool) {
            case FFMPEG -> outputFormat.getCategory(); // FFMPEG: use output category (VIDEO/AUDIO)
            case IMAGEMAGICK -> FormatCategory.IMAGE; // IMAGEMAGICK: always IMAGE
            case PANDOC, LIBREOFFICE -> FormatCategory.DOCUMENT; // Pandoc/LibreOffice: always DOCUMENT
        };
        validateSettingsMatchCategory(sectionSettings, expectedCategory);

        logger.info("Executing conversion with {}: {} → {}", tool, inputPath.getFileName(), outputPath.getFileName());
        long startTime = System.currentTimeMillis();

        try {
            ConversionResult result = switch (tool) {
                case FFMPEG -> executeFFmpeg(inputPath, outputPath, outputFormat, sectionSettings, progressCallback,
                        fileId, processRegistry);
                case PANDOC ->
                    executePandoc(inputPath, outputPath, sectionSettings, progressCallback, fileId, processRegistry);
                case LIBREOFFICE -> executeLibreOffice(inputPath, outputPath, sectionSettings, progressCallback, fileId,
                        processRegistry);
                case IMAGEMAGICK -> executeImageMagick(inputPath, outputPath, sectionSettings, progressCallback, fileId,
                        processRegistry);
            };

            long elapsedMs = System.currentTimeMillis() - startTime;
            logger.info("Conversion completed successfully with {} in {}ms: {} → {}",
                    tool, elapsedMs, inputPath.getFileName(), outputPath.getFileName());

            return result;
        } catch (ToolExecutionException e) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            logger.error("Conversion failed with {} after {}ms: {} - {}",
                    tool, elapsedMs, inputPath.getFileName(), e.getMessage());
            throw e;
        }
    }
}
