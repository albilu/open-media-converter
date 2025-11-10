// filepath: src/main/java/org/omc/service/FFmpegService.java

package org.omc.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.omc.core.ProcessRegistry;
import org.omc.core.ProgressCallback;
import org.omc.exception.ErrorCode;
import org.omc.exception.ToolExecutionException;
import org.omc.model.AudioSettings;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionTool;
import org.omc.model.ImageSettings;
import org.omc.model.Resolution;
import org.omc.model.VideoSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for building and executing FFmpeg commands for video, audio, and
 * image conversion.
 * 
 * Requirements:
 * - REQ-006.1: Video format conversion with codec support (H.264, H.265, VP9)
 * - REQ-006.2: Audio format conversion with codec support (AAC, MP3, Opus,
 * Vorbis, FLAC)
 * - REQ-006.3: Image format conversion with quality controls
 */
public class FFmpegService {

    private static final Logger logger = LoggerFactory.getLogger(FFmpegService.class);

    /** Maximum size of captured tool output (1MB) to prevent memory issues */
    private static final int MAX_OUTPUT_SIZE = 1024 * 1024;

    /** Message appended when output is truncated due to size limit */
    private static final String TRUNCATION_MESSAGE = "\n[Output truncated - exceeded 1MB limit]\n";

    private final Path ffmpegPath;
    private final Path ffprobePath;

    /**
     * Creates a new FFmpegService with specified tool paths.
     * 
     * @param ffmpegPath  path to FFmpeg executable
     * @param ffprobePath path to ffprobe executable
     * @throws NullPointerException if ffmpegPath or ffprobePath is null
     */
    public FFmpegService(Path ffmpegPath, Path ffprobePath) {
        this.ffmpegPath = Objects.requireNonNull(ffmpegPath, "ffmpegPath must not be null");
        this.ffprobePath = Objects.requireNonNull(ffprobePath, "ffprobePath must not be null");
        logger.debug("FFmpegService initialized with ffmpeg={}, ffprobe={}", ffmpegPath, ffprobePath);
    }

    /**
     * Builds an FFmpeg command for video conversion.
     * 
     * Requirements:
     * - REQ-006.1: Basic video conversion
     * - REQ-VID-1.2: H.264 NVIDIA GPU codec support
     * - REQ-VID-1.3: HEVC NVIDIA GPU codec support
     * - REQ-PERF-1.3: GPU hardware acceleration
     * 
     * @param inputPath  input video file path
     * @param outputPath output video file path
     * @param settings   video conversion settings
     * @return list of command arguments
     * @throws NullPointerException if any parameter is null
     */
    public List<String> buildVideoCommand(Path inputPath, Path outputPath, VideoSettings settings) {
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(settings, "settings must not be null");

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath.toString());

        // Enable progress output to stdout (pipe:1)
        command.add("-progress");
        command.add("pipe:1");

        // GPU acceleration BEFORE input (REQ-PERF-1.3, REQ-VID-1.2, REQ-VID-1.3)
        if (isGPUCodec(settings.codec())) {
            command.add("-hwaccel");
            command.add("cuda");
            command.add("-hwaccel_output_format");
            command.add("cuda");
        }

        // Multi-threading for performance (REQ-PERF-1.1)
        command.add("-threads");
        command.add("0");

        // Input file
        command.add("-i");
        command.add(inputPath.toString());

        // Video codec mapping
        String ffmpegCodec = mapVideoCodec(settings.codec());
        command.add("-c:v");
        command.add(ffmpegCodec);

        // Codec-specific settings
        if (ffmpegCodec.equals("libx264") || ffmpegCodec.equals("libx265")) {
            // H.264 / H.265 settings
            // Requirement REQ-006.1: CRF and preset support
            command.add("-crf");
            command.add(String.valueOf(settings.crf()));

            if (settings.preset() != null) {
                command.add("-preset");
                command.add(settings.preset());
            }
        } else if (ffmpegCodec.equals("libvpx-vp9")) {
            // VP9 settings
            // Requirement REQ-006.1: VP9 codec support
            command.add("-crf");
            command.add(String.valueOf(settings.crf()));

            command.add("-b:v");
            command.add("0"); // Use CRF mode
        }

        // Bitrate (used as max bitrate for CRF mode)
        if (settings.bitrate() > 0) {
            command.add("-maxrate");
            command.add(settings.bitrate() + "k");
            command.add("-bufsize");
            command.add((settings.bitrate() * 2) + "k");
        }

        // Video filter chain (REQ-VID-2.2, REQ-VID-2.3)
        String videoFilter = buildVideoFilterChain(settings);
        if (!videoFilter.isEmpty()) {
            command.add("-vf");
            command.add(videoFilter);
        }

        // Frame rate
        if (settings.frameRate() > 0) {
            command.add("-r");
            command.add(String.valueOf(settings.frameRate()));
        }

        // Audio codec (copy by default)
        command.add("-c:a");
        command.add("copy");
        command.add("-b:a");
        command.add("192k");

        // Overwrite output file
        command.add("-y");

        // Output file
        command.add(outputPath.toString());

        logger.debug("Built video command: {}", command);
        return command;
    }

    /**
     * Builds an FFmpeg command for audio conversion.
     * 
     * Requirements: REQ-006.2
     * 
     * @param inputPath  input audio file path
     * @param outputPath output audio file path
     * @param settings   audio conversion settings
     * @return list of command arguments
     * @throws NullPointerException if any parameter is null
     */
    public List<String> buildAudioCommand(Path inputPath, Path outputPath, AudioSettings settings) {
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(settings, "settings must not be null");

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath.toString());

        // Enable progress output to stdout (pipe:1)
        command.add("-progress");
        command.add("pipe:1");

        // Enable multi-threading (Requirement REQ-PERF-1.1)
        command.add("-threads");
        command.add("0");

        // Input file
        command.add("-i");
        command.add(inputPath.toString());

        // Audio codec mapping
        String ffmpegCodec = mapAudioCodec(settings.codec());
        command.add("-c:a");
        command.add(ffmpegCodec);

        // Requirement REQ-AUD-1.1: Skip encoding parameters for copy codec
        if (!ffmpegCodec.equals("copy")) {
            // Bitrate
            command.add("-b:a");
            command.add(settings.bitrate() + "k");

            // Sample rate
            if (settings.sampleRate() > 0) {
                command.add("-ar");
                command.add(String.valueOf(settings.sampleRate()));
            }

            // Channels
            if (settings.channels() > 0) {
                command.add("-ac");
                command.add(String.valueOf(settings.channels()));
            }

            // Codec-specific quality settings
            // Requirement REQ-006.2: Support for MP3, AAC, Opus, Vorbis, FLAC
            if (ffmpegCodec.equals("libmp3lame")) {
                // MP3: quality 0-9 (lower is better)
                command.add("-q:a");
                command.add(String.valueOf(settings.quality()));
            } else if (ffmpegCodec.equals("libvorbis")) {
                // Vorbis: quality -1 to 10 (higher is better)
                // Map 0-9 to -1 to 8
                int vorbisQuality = settings.quality() - 1;
                command.add("-q:a");
                command.add(String.valueOf(vorbisQuality));
            } else if (ffmpegCodec.equals("libopus")) {
                // Opus: use VBR with quality settings
                command.add("-vbr");
                command.add("on");
                command.add("-compression_level");
                command.add(String.valueOf(settings.quality()));
            }
            // AAC and FLAC use bitrate only
        }

        // No video stream
        command.add("-vn");

        // Overwrite output file
        command.add("-y");

        // Output file
        command.add(outputPath.toString());

        logger.debug("Built audio command: {}", command);
        return command;
    }

    /**
     * Builds an FFmpeg command for image conversion.
     * 
     * Requirements: REQ-006.3
     * 
     * @param inputPath  input image file path
     * @param outputPath output image file path
     * @param settings   image conversion settings
     * @return list of command arguments
     * @throws NullPointerException if any parameter is null
     */
    public List<String> buildImageCommand(Path inputPath, Path outputPath, ImageSettings settings) {
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(settings, "settings must not be null");

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath.toString());

        // Enable progress output to stdout (pipe:1)
        command.add("-progress");
        command.add("pipe:1");

        // Input file
        command.add("-i");
        command.add(inputPath.toString());

        // Resolution and scaling
        if (settings.resolution() != null) {
            Resolution res = settings.resolution();
            String scaleFilter;

            if (settings.maintainAspectRatio()) {
                // Scale while maintaining aspect ratio
                // Use -1 for one dimension to maintain aspect ratio
                scaleFilter = String.format("scale=%d:%d:force_original_aspect_ratio=decrease",
                        res.getWidth(), res.getHeight());
            } else {
                // Stretch to exact dimensions
                scaleFilter = String.format("scale=%d:%d", res.getWidth(), res.getHeight());
            }

            command.add("-vf");
            command.add(scaleFilter);
        }

        // Quality settings (format-specific)
        String outputExtension = outputPath.getFileName().toString().toLowerCase();

        if (outputExtension.endsWith(".jpg") || outputExtension.endsWith(".jpeg")) {
            // JPEG quality (2-31, lower is better; invert our 0-100 scale)
            int jpegQuality = 31 - (settings.quality() * 29 / 100);
            command.add("-q:v");
            command.add(String.valueOf(Math.max(2, jpegQuality)));
        } else if (outputExtension.endsWith(".webp")) {
            // WebP quality (0-100, higher is better)
            command.add("-quality");
            command.add(String.valueOf(settings.quality()));
        } else if (outputExtension.endsWith(".png")) {
            // PNG compression level (0-9)
            command.add("-compression_level");
            command.add(String.valueOf(settings.compressionLevel()));
        }

        // Single frame output
        command.add("-frames:v");
        command.add("1");

        // Overwrite output file
        command.add("-y");

        // Output file
        command.add(outputPath.toString());

        logger.debug("Built image command: {}", command);
        return command;
    }

    /**
     * Checks if the given codec is a GPU-accelerated codec.
     * 
     * Requirements: REQ-VID-1.2, REQ-VID-1.3
     * 
     * @param codec codec identifier string
     * @return true if codec is h264_nvenc or hevc_nvenc, false otherwise
     */
    private boolean isGPUCodec(String codec) {
        if (codec == null) {
            return false;
        }
        String lowerCodec = codec.toLowerCase();
        return lowerCodec.equals("h264_nvenc") || lowerCodec.equals("hevc_nvenc");
    }

    /**
     * Maps video codec name to FFmpeg codec identifier.
     * 
     * Requirements:
     * - REQ-006.1 - H.264, H.265, VP9 support
     * - REQ-VID-1.1 - MPEG-4 codec support
     * - REQ-VID-1.2 - NVIDIA H.264 GPU codec support (h264_nvenc)
     * - REQ-VID-1.3 - NVIDIA HEVC GPU codec support (hevc_nvenc)
     * 
     * @param codec user-friendly codec name
     * @return FFmpeg codec identifier
     */
    private String mapVideoCodec(String codec) {
        if (codec == null) {
            return "libx264"; // Default
        }

        return switch (codec.toLowerCase()) {
            case "h264", "x264", "avc" -> "libx264";
            case "h265", "x265", "hevc" -> "libx265";
            case "vp9", "webm" -> "libvpx-vp9";
            case "av1" -> "libaom-av1";
            case "mpeg4" -> "mpeg4"; // REQ-VID-1.1
            case "h264_nvenc" -> "h264_nvenc"; // REQ-VID-1.2
            case "hevc_nvenc" -> "hevc_nvenc"; // REQ-VID-1.3
            case "mpeg2" -> "mpeg2video";
            default -> codec; // Pass through unknown codecs
        };
    }

    /**
     * Builds FFmpeg video filter chain with resolution scaling and aspect ratio.
     * 
     * Filter order: scale → setdar → pad
     * 
     * Requirements: REQ-VID-2.2, REQ-VID-2.3
     * 
     * @param settings video conversion settings
     * @return filter chain string, or empty string if no filters needed
     */
    private String buildVideoFilterChain(VideoSettings settings) {
        List<String> filters = new ArrayList<>();

        // 1. Scale filter (if resolution specified)
        if (settings.resolution() != null) {
            Resolution res = settings.resolution();
            filters.add(String.format("scale=%d:%d", res.getWidth(), res.getHeight()));
        }

        // 2. Aspect ratio filter (if not KEEP_ORIGINAL)
        if (settings.aspectRatio() != null && !settings.aspectRatio().isOriginal()) {
            double targetRatio = settings.aspectRatio().getRatio();

            // setdar: Set Display Aspect Ratio metadata
            filters.add(String.format("setdar=%s", formatRatio(targetRatio)));

            // pad: Add letterboxing/pillarboxing if needed
            String padFilter = buildPaddingFilter(targetRatio, settings.resolution());
            if (!padFilter.isEmpty()) {
                filters.add(padFilter);
            }
        }

        return String.join(",", filters);
    }

    /**
     * Formats aspect ratio as fraction string for FFmpeg filters.
     * 
     * Examples:
     * - 1.777 → "16/9"
     * - 1.333 → "4/3"
     * - 1.5 → "3/2"
     * 
     * @param ratio decimal aspect ratio (must be positive, finite)
     * @return formatted ratio string (e.g., "16/9")
     * @throws IllegalArgumentException if ratio is invalid (zero, negative, NaN, or
     *                                  infinite)
     */
    private String formatRatio(double ratio) {
        // Validate input
        if (ratio <= 0 || Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            throw new IllegalArgumentException("Invalid aspect ratio: " + ratio);
        }

        // Convert decimal ratio to fractional string (e.g., 1.777 → "16/9")
        if (Math.abs(ratio - 16.0 / 9.0) < 0.01)
            return "16/9";
        if (Math.abs(ratio - 4.0 / 3.0) < 0.01)
            return "4/3";
        if (Math.abs(ratio - 1.0) < 0.01)
            return "1/1";
        if (Math.abs(ratio - 21.0 / 9.0) < 0.01)
            return "21/9";
        if (Math.abs(ratio - 9.0 / 16.0) < 0.01)
            return "9/16";
        if (Math.abs(ratio - 3.0 / 2.0) < 0.01)
            return "3/2";
        if (Math.abs(ratio - 2.39) < 0.01)
            return "239/100";

        // Fallback: use decimal format for precision (FFmpeg accepts decimal ratios)
        // Use Locale.ROOT to ensure period as decimal separator (not comma)
        return String.format(java.util.Locale.ROOT, "%.3f", ratio);
    }

    /**
     * Builds padding filter for aspect ratio correction.
     * 
     * Supports two modes:
     * 1. Dynamic padding (resolution=null): Uses FFmpeg expressions to calculate
     * padding
     * based on input dimensions (iw, ih). Adds pillarboxing if input is narrower
     * than
     * target ratio, or letterboxing if input is wider.
     * 
     * 2. Fixed resolution: Calculates exact padding dimensions from target
     * resolution.
     * Returns empty string if resolution already matches target ratio.
     * 
     * FFmpeg pad filter format: pad=width:height:x:y:color
     * 
     * @param targetRatio target aspect ratio (e.g., 1.777 for 16:9)
     * @param resolution  target resolution (may be null for dynamic padding)
     * @return pad filter string, or empty if no padding needed
     * @throws IllegalArgumentException if resolution has invalid dimensions
     */
    private String buildPaddingFilter(double targetRatio, Resolution resolution) {
        if (resolution == null) {
            // No resolution specified - use input dimensions with dynamic padding
            // Format: pad=width:height:x:y:color
            // If input ratio < target, pillarbox (add width): width = ih * targetRatio,
            // height = ih
            // If input ratio > target, letterbox (add height): width = iw, height = iw /
            // targetRatio
            // FFmpeg expression: if(lt(iw/ih, targetRatio), ih*targetRatio, iw) :
            // if(lt(iw/ih, targetRatio), ih, iw/targetRatio)
            // Use Locale.ROOT to ensure period as decimal separator (not comma) for FFmpeg
            // compatibility
            return String.format(java.util.Locale.ROOT,
                    "pad=if(lt(iw/ih\\,%f)\\,ih*%f\\,iw):if(lt(iw/ih\\,%f)\\,ih\\,iw/%f):(ow-iw)/2:(oh-ih)/2:black",
                    targetRatio, targetRatio, targetRatio, targetRatio);
        }

        // Resolution specified - padding calculated from target dimensions
        int targetWidth = resolution.getWidth();
        int targetHeight = resolution.getHeight();

        // Validate resolution dimensions
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException(
                    "Invalid resolution dimensions: " + targetWidth + "x" + targetHeight);
        }

        double currentRatio = (double) targetWidth / targetHeight;

        if (Math.abs(currentRatio - targetRatio) < 0.01) {
            // Already at target ratio
            return "";
        }

        if (currentRatio < targetRatio) {
            // Add pillarboxing (vertical black bars on left/right)
            int newWidth = (int) (targetHeight * targetRatio);
            int xOffset = (newWidth - targetWidth) / 2;
            return String.format("pad=%d:%d:%d:0:black", newWidth, targetHeight, xOffset);
        } else {
            // Add letterboxing (horizontal black bars on top/bottom)
            int newHeight = (int) (targetWidth / targetRatio);
            int yOffset = (newHeight - targetHeight) / 2;
            return String.format("pad=%d:%d:0:%d:black", targetWidth, newHeight, yOffset);
        }
    }

    /**
     * Uses ffprobe to query the duration of a media file.
     * 
     * @param inputPath path to the input media file
     * @return duration of the file, or null if unable to determine
     * @throws ToolExecutionException if ffprobe execution fails
     */
    private Duration getDuration(Path inputPath) throws ToolExecutionException {
        List<String> command = List.of(
                ffprobePath.toString(),
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                inputPath.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        logger.debug("Executing ffprobe command: {}", String.join(" ", command));

        try {
            Process process = processBuilder.start();

            // Apply size limit similar to extractMetadata for consistency
            StringBuilder outputCapture = new StringBuilder(4096);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                int lineCount = 0;
                boolean truncated = false;

                while ((line = reader.readLine()) != null) {
                    lineCount++;

                    // Check every 100 lines for size limit (performance optimization)
                    if (!truncated) {
                        if (lineCount % 100 == 0 && outputCapture.length() > MAX_OUTPUT_SIZE) {
                            logger.warn("ffprobe output exceeded 1MB limit during duration extraction for {}",
                                    inputPath);
                            truncated = true;
                            // Don't append more, but continue reading to process completion
                        } else {
                            outputCapture.append(line).append("\n");
                        }
                    }
                }
            }

            String output = outputCapture.toString();

            if (process.waitFor() != 0) {
                logger.warn("ffprobe failed to get duration for {}", inputPath);
                return null;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(output);
            JsonNode format = root.path("format");
            if (format.has("duration")) {
                double durationSeconds = format.get("duration").asDouble();
                return Duration.ofMillis((long) (durationSeconds * 1000));
            }

        } catch (IOException | InterruptedException e) {
            logger.warn("Failed to get duration using ffprobe: {}", e.getMessage());
            return null;
        }

        return null;
    }

    /**
     * Uses ffprobe to query the total number of frames in a video file.
     * This is used for frame-based progress tracking when time-based tracking
     * fails.
     * 
     * @param inputPath path to the input media file
     * @return total number of frames, or -1 if unable to determine
     */
    private long getTotalFrames(Path inputPath) {
        List<String> command = List.of(
                ffprobePath.toString(),
                "-v", "quiet",
                "-print_format", "json",
                "-show_streams",
                "-show_format", // Also get format section for duration
                "-select_streams", "v:0", // Select first video stream only
                inputPath.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        logger.debug("Executing ffprobe command to get frame count: {}", String.join(" ", command));

        try {
            Process process = processBuilder.start();

            // Apply size limit similar to extractMetadata for consistency
            StringBuilder outputCapture = new StringBuilder(4096);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                int lineCount = 0;
                boolean truncated = false;

                while ((line = reader.readLine()) != null) {
                    lineCount++;

                    // Check every 100 lines for size limit (performance optimization)
                    if (!truncated) {
                        if (lineCount % 100 == 0 && outputCapture.length() > MAX_OUTPUT_SIZE) {
                            logger.warn("ffprobe output exceeded 1MB limit during frame count extraction for {}",
                                    inputPath);
                            truncated = true;
                            // Don't append more, but continue reading to process completion
                        } else {
                            outputCapture.append(line).append("\n");
                        }
                    }
                }
            }

            String output = outputCapture.toString();

            if (process.waitFor() != 0) {
                logger.warn("ffprobe failed to get frame count for {}", inputPath);
                return -1;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(output);
            JsonNode streams = root.path("streams");

            if (streams.isArray() && streams.size() > 0) {
                JsonNode videoStream = streams.get(0);

                // Try nb_frames first (most reliable)
                if (videoStream.has("nb_frames")) {
                    long frames = videoStream.get("nb_frames").asLong(-1);
                    if (frames > 0) {
                        logger.debug("Found {} total frames from nb_frames", frames);
                        return frames;
                    }
                }

                // Fall back to calculating from duration and frame rate
                // Try duration from stream first, then from format section
                double duration = -1.0;
                if (videoStream.has("duration")) {
                    duration = videoStream.get("duration").asDouble(-1.0);
                } else {
                    JsonNode format = root.path("format");
                    if (format.has("duration")) {
                        duration = format.get("duration").asDouble(-1.0);
                    }
                }

                if (duration > 0 && videoStream.has("avg_frame_rate")) {
                    String fpsStr = videoStream.get("avg_frame_rate").asText();

                    // Parse frame rate (format: "24/1" or "24000/1001")
                    if (fpsStr.contains("/")) {
                        String[] parts = fpsStr.split("/");
                        if (parts.length == 2) {
                            double num = Double.parseDouble(parts[0]);
                            double den = Double.parseDouble(parts[1]);
                            if (den > 0) {
                                double fps = num / den;
                                long estimatedFrames = (long) (duration * fps);
                                if (estimatedFrames > 0) {
                                    logger.debug("Estimated {} total frames from duration {} and fps {}",
                                            estimatedFrames, duration, fps);
                                    return estimatedFrames;
                                }
                            }
                        }
                    }
                }
            }

        } catch (IOException | InterruptedException | NumberFormatException e) {
            logger.warn("Failed to get frame count using ffprobe: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Extracts media metadata using ffprobe.
     * 
     * Requirements:
     * - REQ-002.2: File metadata extraction for video, audio, and image files
     * - REQ-015: Metadata extraction using ffprobe
     * 
     * @param filePath path to the media file
     * @param category the format category (VIDEO, AUDIO, or IMAGE)
     * @return MediaMetadata object, or null if extraction fails
     * @throws ToolExecutionException if ffprobe execution fails
     */
    public org.omc.model.MediaMetadata extractMetadata(
            Path filePath,
            org.omc.model.FormatCategory category) throws ToolExecutionException {

        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(category, "category must not be null");

        // Build ffprobe command to get JSON output with format and stream information
        List<String> command = List.of(
                ffprobePath.toString(),
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                filePath.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        logger.debug("Extracting metadata from {} using ffprobe", filePath);

        try {
            Process process = processBuilder.start();

            // Requirement: Task 5.19 - Capture output with size limit for metadata
            // extraction
            StringBuilder outputCapture = new StringBuilder(4096);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                int lineCount = 0;
                boolean truncated = false;

                while ((line = reader.readLine()) != null) {
                    lineCount++;

                    // Check every 100 lines for size limit (performance optimization)
                    if (!truncated) {
                        if (lineCount % 100 == 0 && outputCapture.length() > MAX_OUTPUT_SIZE) {
                            logger.warn("ffprobe output exceeded 1MB limit during metadata extraction for {}",
                                    filePath);
                            truncated = true;
                            // Don't append more, but continue reading to process completion
                        } else {
                            outputCapture.append(line).append("\n");
                        }
                    }
                }
            }

            String output = outputCapture.toString();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warn("ffprobe failed to extract metadata for {}, exit code: {}", filePath, exitCode);
                throw new ToolExecutionException(
                        "ffprobe failed to extract metadata",
                        ErrorCode.TOOL_EXECUTION_FAILED,
                        "ffprobe",
                        ffprobePath.toString(),
                        exitCode,
                        "Failed to extract metadata: exit code " + exitCode,
                        null);
            }

            // Parse JSON output
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(output);

            // Extract metadata based on category
            return switch (category) {
                case VIDEO -> extractVideoMetadata(root);
                case AUDIO -> extractAudioMetadata(root);
                case IMAGE -> extractImageMetadata(root);
                case DOCUMENT -> null; // Documents not handled by FFmpeg
                default -> {
                    logger.warn("Unknown format category: {}", category);
                    yield null;
                }
            };

        } catch (IOException e) {
            logger.error("Failed to execute ffprobe for metadata extraction", e);
            throw new ToolExecutionException(
                    "Failed to execute ffprobe: " + e.getMessage(),
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "ffprobe",
                    ffprobePath.toString(),
                    null,
                    e.getMessage(),
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("ffprobe process interrupted", e);
            throw new ToolExecutionException(
                    "ffprobe process interrupted",
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "ffprobe",
                    ffprobePath.toString(),
                    null,
                    "Process interrupted",
                    e);
        }
    }

    /**
     * Extracts duration from ffprobe format section.
     * 
     * @param root JSON root node from ffprobe
     * @return Duration object or null if not available
     */
    private Duration extractDuration(JsonNode root) {
        JsonNode format = root.path("format");
        if (format.has("duration")) {
            double durationSeconds = format.get("duration").asDouble();
            return Duration.ofMillis((long) (durationSeconds * 1000));
        }
        return null;
    }

    /**
     * Extracts video metadata from ffprobe JSON output.
     * 
     * @param root JSON root node from ffprobe
     * @return VideoMetadata object
     */
    private org.omc.model.VideoMetadata extractVideoMetadata(JsonNode root) {
        // Get format section for duration
        Duration duration = extractDuration(root);

        // Find video and audio streams
        JsonNode streams = root.path("streams");
        JsonNode videoStream = null;
        JsonNode audioStream = null;

        for (JsonNode stream : streams) {
            String codecType = stream.path("codec_type").asText();
            if ("video".equals(codecType) && videoStream == null) {
                videoStream = stream;
            } else if ("audio".equals(codecType) && audioStream == null) {
                audioStream = stream;
            }
        }

        if (videoStream == null) {
            logger.warn("No video stream found in metadata");
            return null;
        }

        // Extract video properties
        int width = videoStream.path("width").asInt(0);
        int height = videoStream.path("height").asInt(0);
        JsonNode codecNode = videoStream.path("codec_name");
        String videoCodec = codecNode.isMissingNode() ? "unknown" : codecNode.asText();

        // Extract frame rate
        double frameRate = 0.0;
        JsonNode frameRateNode = videoStream.path("avg_frame_rate");
        String avgFrameRate = frameRateNode.isMissingNode() ? "" : frameRateNode.asText();
        if (!avgFrameRate.isEmpty() && avgFrameRate.contains("/")) {
            String[] parts = avgFrameRate.split("/");
            if (parts.length == 2) {
                try {
                    double num = Double.parseDouble(parts[0]);
                    double den = Double.parseDouble(parts[1]);
                    if (den > 0) {
                        frameRate = num / den;
                    }
                } catch (NumberFormatException e) {
                    logger.trace("Failed to parse frame rate: {}", avgFrameRate);
                }
            }
        }

        // Extract bitrates
        long videoBitrate = videoStream.path("bit_rate").asLong(0);
        long audioBitrate = 0;
        String audioCodec = null;

        if (audioStream != null) {
            audioBitrate = audioStream.path("bit_rate").asLong(0);
            JsonNode audioCodecNode = audioStream.path("codec_name");
            audioCodec = audioCodecNode.isMissingNode() ? null : audioCodecNode.asText();
        }

        // Build VideoMetadata
        return org.omc.model.VideoMetadata.builder()
                .duration(duration != null ? duration : Duration.ZERO)
                .width(width)
                .height(height)
                .videoCodec(videoCodec)
                .audioCodec(audioCodec)
                .frameRate(frameRate)
                .videoBitrate(videoBitrate)
                .audioBitrate(audioBitrate)
                .build();
    }

    /**
     * Extracts audio metadata from ffprobe JSON output.
     * 
     * @param root JSON root node from ffprobe
     * @return AudioMetadata object
     */
    private org.omc.model.AudioMetadata extractAudioMetadata(JsonNode root) {
        // Get format section for duration
        Duration duration = extractDuration(root);

        // Find audio stream
        JsonNode streams = root.path("streams");
        JsonNode audioStream = null;

        for (JsonNode stream : streams) {
            String codecType = stream.path("codec_type").asText();
            if ("audio".equals(codecType)) {
                audioStream = stream;
                break;
            }
        }

        if (audioStream == null) {
            logger.warn("No audio stream found in metadata");
            return null;
        }

        // Extract audio properties
        JsonNode audioCodecNode = audioStream.path("codec_name");
        String codec = audioCodecNode.isMissingNode() ? "unknown" : audioCodecNode.asText();
        long bitrate = audioStream.path("bit_rate").asLong(0);
        int sampleRate = audioStream.path("sample_rate").asInt(0);
        int channels = audioStream.path("channels").asInt(0);

        // Build AudioMetadata
        return org.omc.model.AudioMetadata.builder()
                .duration(duration != null ? duration : Duration.ZERO)
                .codec(codec)
                .bitrate(bitrate)
                .sampleRate(sampleRate)
                .channels(channels)
                .build();
    }

    /**
     * Extracts image metadata from ffprobe JSON output.
     * 
     * @param root JSON root node from ffprobe
     * @return ImageMetadata object
     */
    private org.omc.model.ImageMetadata extractImageMetadata(JsonNode root) {
        // Find video stream (images are treated as single-frame videos)
        JsonNode streams = root.path("streams");
        JsonNode imageStream = null;

        for (JsonNode stream : streams) {
            String codecType = stream.path("codec_type").asText();
            if ("video".equals(codecType)) {
                imageStream = stream;
                break;
            }
        }

        if (imageStream == null) {
            logger.warn("No image stream found in metadata");
            return null;
        }

        // Extract image properties
        int width = imageStream.path("width").asInt(0);
        int height = imageStream.path("height").asInt(0);

        // Extract color space/pixel format
        JsonNode pixelFormatNode = imageStream.path("pix_fmt");
        String pixelFormat = pixelFormatNode.isMissingNode() ? "unknown" : pixelFormatNode.asText();
        String colorSpace = mapPixelFormatToColorSpace(pixelFormat);

        // Determine bit depth from pixel format
        int bitDepth = estimateBitDepth(pixelFormat);

        // Check for alpha channel (careful: must avoid false matches like "gray"
        // containing 'a')
        boolean hasAlpha = pixelFormat.contains("rgba") || pixelFormat.contains("yuva") ||
                pixelFormat.matches(".*[^a-z]a[^a-z].*") || pixelFormat.endsWith("a");

        // Build ImageMetadata
        return org.omc.model.ImageMetadata.builder()
                .width(width)
                .height(height)
                .colorSpace(colorSpace)
                .bitDepth(bitDepth)
                .hasAlpha(hasAlpha)
                .build();
    }

    /**
     * Maps FFmpeg pixel format to human-readable color space.
     * 
     * @param pixelFormat FFmpeg pixel format string
     * @return color space name
     */
    private String mapPixelFormatToColorSpace(String pixelFormat) {
        if (pixelFormat == null || pixelFormat.isEmpty()) {
            return "Unknown";
        }

        String lower = pixelFormat.toLowerCase();

        if (lower.contains("cmyk")) {
            return "CMYK";
        } else if (lower.contains("rgb")) {
            return "RGB";
        } else if (lower.contains("gray") || lower.contains("grey")) {
            return "Grayscale";
        } else if (lower.contains("yuv") || lower.contains("y")) {
            return "YUV";
        }

        return "Unknown";
    }

    /**
     * Estimates bit depth from pixel format.
     * 
     * @param pixelFormat FFmpeg pixel format string
     * @return estimated bit depth per channel
     */
    private int estimateBitDepth(String pixelFormat) {
        if (pixelFormat == null || pixelFormat.isEmpty()) {
            return 8; // Default
        }

        // Look for bit depth indicators in pixel format
        if (pixelFormat.contains("16") || pixelFormat.contains("p16")) {
            return 16;
        } else if (pixelFormat.contains("10") || pixelFormat.contains("p10")) {
            return 10;
        } else if (pixelFormat.contains("12") || pixelFormat.contains("p12")) {
            return 12;
        } else if (pixelFormat.contains("48")) {
            return 16; // RGB48 = 16 bits per channel
        } else if (pixelFormat.contains("24")) {
            return 8; // RGB24 = 8 bits per channel
        }

        return 8; // Default to 8-bit
    }

    /**
     * Maps audio codec name to FFmpeg codec identifier.
     * 
     * Requirements: REQ-006.2 - AAC, MP3, Opus, Vorbis, FLAC support
     * 
     * @param codec user-friendly codec name
     * @return FFmpeg codec identifier
     */
    private String mapAudioCodec(String codec) {
        if (codec == null) {
            return "aac"; // Default
        }

        return switch (codec.toLowerCase()) {
            case "aac" -> "aac";
            case "mp3", "lame" -> "libmp3lame";
            case "opus" -> "libopus";
            case "vorbis", "ogg" -> "libvorbis";
            case "flac" -> "flac";
            case "wav", "pcm" -> "pcm_s16le";
            case "alac" -> "alac";
            case "ac3" -> "ac3";
            case "copy" -> "copy"; // Requirement REQ-AUD-1.1: Stream copy without re-encoding
            default -> codec; // Pass through unknown codecs
        };
    }

    /**
     * Gets the FFmpeg executable path.
     * 
     * @return FFmpeg path
     */
    public Path getFfmpegPath() {
        return ffmpegPath;
    }

    /**
     * Gets the ffprobe executable path.
     * 
     * @return ffprobe path
     */
    public Path getFfprobePath() {
        return ffprobePath;
    }

    /**
     * Executes a video conversion with progress tracking.
     * 
     * Requirements: REQ-004.2 - Conversion execution with process management
     * 
     * @param inputPath        input video file path
     * @param outputPath       output video file path
     * @param settings         video conversion settings
     * @param progressCallback callback for progress updates (can be null)
     * @return conversion result with success/failure information
     * @throws ToolExecutionException if execution fails
     */
    public ConversionResult convertVideo(Path inputPath, Path outputPath, VideoSettings settings,
            ProgressCallback progressCallback) throws ToolExecutionException {
        return convertVideo(inputPath, outputPath, settings, progressCallback, null, ProcessRegistry.noOp());
    }

    public ConversionResult convertVideo(Path inputPath, Path outputPath, VideoSettings settings,
            ProgressCallback progressCallback, String fileId, ProcessRegistry processRegistry)
            throws ToolExecutionException {
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(settings, "settings must not be null");

        List<String> command = buildVideoCommand(inputPath, outputPath, settings);
        return executeConversion(command, inputPath, outputPath, progressCallback, fileId, processRegistry);
    }

    /**
     * Executes an audio conversion with progress tracking.
     * 
     * Requirements: REQ-004.2 - Conversion execution with process management
     * 
     * @param inputPath        input audio file path
     * @param outputPath       output audio file path
     * @param settings         audio conversion settings
     * @param progressCallback callback for progress updates (can be null)
     * @return conversion result with success/failure information
     * @throws ToolExecutionException if execution fails
     */
    public ConversionResult convertAudio(Path inputPath, Path outputPath, AudioSettings settings,
            ProgressCallback progressCallback) throws ToolExecutionException {
        return convertAudio(inputPath, outputPath, settings, progressCallback, null, ProcessRegistry.noOp());
    }

    public ConversionResult convertAudio(Path inputPath, Path outputPath, AudioSettings settings,
            ProgressCallback progressCallback, String fileId, ProcessRegistry processRegistry)
            throws ToolExecutionException {
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(settings, "settings must not be null");

        List<String> command = buildAudioCommand(inputPath, outputPath, settings);
        return executeConversion(command, inputPath, outputPath, progressCallback, fileId, processRegistry);
    }

    /**
     * Executes an image conversion with progress tracking.
     * 
     * @deprecated Image conversions now use {@link ImageMagickService}.
     *             This method is retained for backward compatibility testing only.
     *             Use {@link ImageMagickService#convertImage} instead for better
     *             format support
     *             (including SVG), superior quality settings, and purpose-built
     *             image handling.
     * 
     *             Requirements: REQ-004.2 - Conversion execution with process
     *             management
     * 
     * @param inputPath        input image file path
     * @param outputPath       output image file path
     * @param settings         image conversion settings
     * @param progressCallback callback for progress updates (can be null)
     * @return conversion result with success/failure information
     * @throws ToolExecutionException if execution fails
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public ConversionResult convertImage(Path inputPath, Path outputPath, ImageSettings settings,
            ProgressCallback progressCallback) throws ToolExecutionException {
        logger.warn(
                "DEPRECATED: FFmpegService.convertImage() called. Image conversions should use ImageMagickService for better quality and format support.");
        return convertImage(inputPath, outputPath, settings, progressCallback, null, ProcessRegistry.noOp());
    }

    /**
     * Executes an image conversion with progress tracking and process registration.
     * 
     * @deprecated Image conversions now use {@link ImageMagickService}.
     *             This method is retained for backward compatibility testing only.
     *             Use {@link ImageMagickService#convertImage} instead for better
     *             format support
     *             (including SVG), superior quality settings, and purpose-built
     *             image handling.
     * 
     * @param inputPath        input image file path
     * @param outputPath       output image file path
     * @param settings         image conversion settings
     * @param progressCallback callback for progress updates (can be null)
     * @param fileId           file ID for process registration (can be null)
     * @param processRegistry  registry to track active processes (can be noOp)
     * @return conversion result with success/failure information
     * @throws ToolExecutionException if execution fails
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public ConversionResult convertImage(Path inputPath, Path outputPath, ImageSettings settings,
            ProgressCallback progressCallback, String fileId, ProcessRegistry processRegistry)
            throws ToolExecutionException {
        logger.warn(
                "DEPRECATED: FFmpegService.convertImage() called. Image conversions should use ImageMagickService for better quality and format support.");
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(settings, "settings must not be null");

        List<String> command = buildImageCommand(inputPath, outputPath, settings);
        return executeConversion(command, inputPath, outputPath, progressCallback, fileId, processRegistry);
    }

    /**
     * Executes an FFmpeg command and tracks progress.
     * 
     * Requirements: REQ-004.2 - Process execution with timeout and stream handling
     * Requirements: REQ-004.3 - Progress tracking with throttling (max 2
     * updates/second)
     * 
     * @param command          FFmpeg command arguments
     * @param inputPath        input file path (for size calculation)
     * @param outputPath       output file path (for result)
     * @param progressCallback callback for progress updates (can be null)
     * @param fileId           file ID for process registration (can be null)
     * @param processRegistry  registry to track active processes (can be noOp)
     * @return conversion result
     * @throws ToolExecutionException if execution fails
     */
    private ConversionResult executeConversion(List<String> command, Path inputPath, Path outputPath,
            ProgressCallback progressCallback, String fileId,
            ProcessRegistry processRegistry) throws ToolExecutionException {
        Instant startTime = Instant.now();
        long inputSize = 0;

        try {
            inputSize = Files.size(inputPath);
        } catch (IOException e) {
            logger.warn("Could not determine input file size: {}", e.getMessage());
        }

        // Get total duration for progress calculation
        Duration totalDuration = getDuration(inputPath);
        if (totalDuration != null && totalDuration.isZero()) {
            totalDuration = null; // Treat zero duration as unknown
        }

        // Get total frames for frame-based progress (fallback if time-based fails)
        long totalFrames = getTotalFrames(inputPath);

        if (totalDuration != null) {
            logger.info("Total duration for progress tracking: {} seconds", totalDuration.getSeconds());
        } else {
            logger.warn("Could not determine duration - will try frame-based progress");
        }

        if (totalFrames > 0) {
            logger.info("Total frames for progress tracking: {}", totalFrames);
        } else {
            logger.warn("Could not determine frame count");
        }

        if (totalDuration == null && totalFrames <= 0) {
            logger.warn("Neither duration nor frame count available - progress tracking will be disabled");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true); // Merge stderr into stdout

        logger.info("Executing FFmpeg command: {}", String.join(" ", command));

        Process process = null;
        StringBuilder outputLog = new StringBuilder(4096); // Initial capacity for performance
        boolean outputTruncated = false;

        // Progress throttling: max 2 updates per second (500ms minimum interval)
        long lastProgressUpdateMillis = 0;
        final long PROGRESS_THROTTLE_MS = 500;

        try {
            process = processBuilder.start();

            // Register process for cancellation support
            if (fileId != null && processRegistry != null) {
                processRegistry.registerProcess(fileId, process);
            }

            // Read output stream with progress tracking
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    lineCount++;

                    // Requirement: Task 5.18 - Enforce 1MB output size limit
                    // Check every 100 lines for performance (avoid constant size checks)
                    if (!outputTruncated) {
                        if (lineCount % 100 == 0 && outputLog.length() > MAX_OUTPUT_SIZE) {
                            outputLog.append(TRUNCATION_MESSAGE);
                            outputTruncated = true;
                            logger.warn("Tool output exceeded 1MB limit, truncating further output");
                        } else {
                            outputLog.append(line).append("\n");
                        }
                    }
                    // Continue reading even after truncation for progress tracking

                    // Log first 50 lines at debug level to see what we're receiving
                    if (lineCount <= 50) {
                        logger.debug("FFmpeg output line {}: {}", lineCount, line);
                    } else {
                        logger.trace("FFmpeg output: {}", line);
                    }

                    // Parse progress if callback provided and (duration known OR frame count known)
                    // Requirement REQ-004.3: Throttle progress updates to max 2 per second
                    if (progressCallback != null && (totalDuration != null || totalFrames > 0)) {
                        ProgressInfo progress = parseProgressLine(line);
                        if (progress != null) {
                            logger.debug("Parsed progress info from line '{}': time={}, bytes={}, frame={}",
                                    line, progress.currentTime, progress.bytesProcessed, progress.currentFrame);

                            double percentage = -1.0;

                            // Try time-based progress first (preferred method)
                            if (progress.currentTime != null && totalDuration != null) {
                                percentage = (progress.currentTime.toMillis() / (double) totalDuration.toMillis())
                                        * 100.0;
                                logger.trace("Using time-based progress: {}%", String.format("%.1f", percentage));
                            }
                            // Fall back to frame-based progress if time is not available
                            else if (progress.currentFrame >= 0 && totalFrames > 0) {
                                percentage = (progress.currentFrame / (double) totalFrames) * 100.0;
                                logger.debug("Using frame-based progress: frame {}/{} = {}%",
                                        progress.currentFrame, totalFrames, String.format("%.1f", percentage));
                            }

                            // If we got a valid percentage, send the update
                            if (percentage >= 0.0) {
                                long currentTimeMillis = System.currentTimeMillis();
                                if (currentTimeMillis - lastProgressUpdateMillis >= PROGRESS_THROTTLE_MS) {
                                    percentage = Math.min(100.0, Math.max(0.0, percentage));

                                    if (progress.currentTime != null && totalDuration != null) {
                                        logger.info("Progress update: {}% (time={}/{}, {} of {} seconds)",
                                                String.format("%.1f", percentage),
                                                progress.currentTime.getSeconds(),
                                                totalDuration.getSeconds(),
                                                progress.currentTime.toMillis(),
                                                totalDuration.toMillis());
                                    } else if (progress.currentFrame >= 0 && totalFrames > 0) {
                                        logger.info("Progress update: {}% (frame={}/{})",
                                                String.format("%.1f", percentage),
                                                progress.currentFrame,
                                                totalFrames);
                                    }

                                    progressCallback.onProgress(percentage, progress.bytesProcessed, progress.speed);
                                    lastProgressUpdateMillis = currentTimeMillis;
                                } else {
                                    logger.trace("Progress throttled ({}ms since last update)",
                                            currentTimeMillis - lastProgressUpdateMillis);
                                }
                            }
                        }
                    }
                }
                logger.debug("Finished reading FFmpeg output. Total lines read: {}", lineCount);
            }

            // Wait for process to complete with timeout (1 hour default)
            // Check for interruption periodically so cancellation can work
            boolean finished = false;
            long timeoutMillis = TimeUnit.HOURS.toMillis(1);
            long startWaitTime = System.currentTimeMillis();

            while (!finished && (System.currentTimeMillis() - startWaitTime) < timeoutMillis) {
                // Check for interruption (from cancel operation)
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("FFmpeg process interrupted, destroying process");
                    process.destroyForcibly();
                    throw new InterruptedException("Conversion cancelled by user");
                }

                // Wait for process with short timeout to allow interruption checks
                finished = process.waitFor(500, TimeUnit.MILLISECONDS);
            }

            if (!finished) {
                process.destroyForcibly();
                throw new ToolExecutionException(
                        "FFmpeg process timed out after 1 hour",
                        ErrorCode.TOOL_EXECUTION_FAILED,
                        "ffmpeg",
                        ffmpegPath.toString(),
                        null,
                        "Process timeout after 1 hour");
            }

            int exitCode = process.exitValue();
            Duration conversionTime = Duration.between(startTime, Instant.now());

            if (exitCode == 0) {
                // Success - get output file size
                long outputSize = 0;
                try {
                    outputSize = Files.size(outputPath);
                } catch (IOException e) {
                    logger.warn("Could not determine output file size: {}", e.getMessage());
                }

                logger.info("Conversion successful: {} -> {} in {}",
                        inputPath.getFileName(), outputPath.getFileName(), conversionTime);

                return ConversionResult.success(
                        inputPath.toString(),
                        outputPath,
                        outputLog.toString(), // Tool output for conversion details dialog
                        conversionTime,
                        inputSize,
                        outputSize,
                        ConversionTool.FFMPEG);
            } else {
                // Failure - extract error from output
                String errorMessage = extractErrorMessage(outputLog.toString());
                logger.error("Conversion failed with exit code {}: {}", exitCode, errorMessage);

                // Requirement REQ-004.2: Clean up partial output file on error
                cleanupPartialFile(outputPath);

                return ConversionResult.failure(
                        inputPath.toString(),
                        "FFmpeg conversion failed (exit code " + exitCode + "): " + errorMessage,
                        outputLog.toString(), // Tool output for debugging
                        conversionTime,
                        inputSize,
                        ConversionTool.FFMPEG);
            }

        } catch (IOException e) {
            Duration conversionTime = Duration.between(startTime, Instant.now());
            logger.error("Failed to execute FFmpeg process", e);

            // Requirement REQ-004.2: Clean up partial output file on error
            cleanupPartialFile(outputPath);

            throw new ToolExecutionException(
                    "Failed to execute FFmpeg: " + e.getMessage(),
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "ffmpeg",
                    ffmpegPath.toString(),
                    null,
                    e.getMessage(),
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Duration conversionTime = Duration.between(startTime, Instant.now());
            logger.error("FFmpeg process interrupted", e);

            // Requirement REQ-004.2: Clean up partial output file on error
            cleanupPartialFile(outputPath);

            throw new ToolExecutionException(
                    "FFmpeg process interrupted: " + e.getMessage(),
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "ffmpeg",
                    ffmpegPath.toString(),
                    null,
                    "Process interrupted",
                    e);
        } finally {
            // Unregister process
            if (fileId != null && processRegistry != null) {
                processRegistry.unregisterProcess(fileId);
            }

            // Ensure process is cleaned up
            if (process != null && process.isAlive()) {
                logger.warn("Forcibly terminating FFmpeg process");
                process.destroyForcibly();
            }
        }
    }

    /**
     * Parses FFmpeg progress output to extract time, size, frame, and bitrate.
     * Supports both regular console format and -progress format.
     * 
     * @param line FFmpeg output line
     * @return ProgressInfo with parsed values, or null if not a progress line
     */
    private ProgressInfo parseProgressLine(String line) {
        long frameNumber = -1;
        Duration currentTime = null;
        long bytesProcessed = 0;
        double speed = 0;

        // ALWAYS try to parse frame number first (from frame= format)
        // This ensures we can track progress even when time=N/A
        if (line.startsWith("frame=")) {
            try {
                // Skip "frame=" prefix and any leading spaces
                int start = 6; // length of "frame="
                while (start < line.length() && Character.isWhitespace(line.charAt(start))) {
                    start++;
                }

                // Find the end of the frame number (next space)
                int end = start;
                while (end < line.length() && Character.isDigit(line.charAt(end))) {
                    end++;
                }

                if (end > start) {
                    String frameStr = line.substring(start, end);
                    frameNumber = Long.parseLong(frameStr);
                    logger.trace("Parsed frame number: {}", frameNumber);
                }
                // Don't return here - continue parsing for time, size, speed
            } catch (NumberFormatException e) {
                logger.trace("Failed to parse frame number from: {}", line);
                // Continue anyway - we might still get useful info from the rest of the line
            }
        }

        // Check for -progress format (out_time_ms=value, out_time_us=value, or
        // out_time=value)
        if (line.startsWith("out_time_ms=") || line.startsWith("out_time_us=") || line.startsWith("out_time=")) {
            try {
                String value = line.substring(line.indexOf('=') + 1).trim();

                // Handle N/A case - FFmpeg outputs this when it can't determine time yet
                // Return progress info with null time but preserve frame number if available
                if ("N/A".equals(value)) {
                    logger.trace("FFmpeg reports out_time=N/A - time not yet available, using frame-based progress");
                    // Return with null time - the frame-based fallback will handle progress
                    return new ProgressInfo(null, 0, 0, frameNumber);
                }

                if (line.startsWith("out_time_us=")) {
                    // Microseconds format
                    long microseconds = Long.parseLong(value);
                    Duration currentTimeVal = Duration.ofNanos(microseconds * 1000);
                    return new ProgressInfo(currentTimeVal, 0, 0);
                } else if (line.startsWith("out_time_ms=")) {
                    // Milliseconds format (actually microseconds according to FFmpeg docs)
                    long milliseconds = Long.parseLong(value) / 1000;
                    long microseconds = milliseconds * 1000;
                    Duration currentTimeVal = Duration.ofNanos(microseconds * 1000);
                    return new ProgressInfo(currentTimeVal, 0, 0);
                } else {
                    // out_time= format - try to parse as HH:MM:SS.ss or microseconds
                    if (value.contains(":")) {
                        // Parse HH:MM:SS.ss format
                        String[] parts = value.split(":");
                        if (parts.length == 3) {
                            long hours = Long.parseLong(parts[0]);
                            long minutes = Long.parseLong(parts[1]);
                            double seconds = Double.parseDouble(parts[2]);
                            long totalMillis = hours * 3600000 + minutes * 60000 + (long) (seconds * 1000);
                            Duration currentTimeVal = Duration.ofMillis(totalMillis);
                            return new ProgressInfo(currentTimeVal, 0, 0);
                        }
                    } else {
                        // Try parsing as microseconds
                        long microseconds = Long.parseLong(value);
                        Duration currentTimeVal = Duration.ofNanos(microseconds * 1000);
                        return new ProgressInfo(currentTimeVal, 0, 0);
                    }
                }
            } catch (NumberFormatException e) {
                logger.trace("Failed to parse progress time from: {}", line);
                return null;
            }
        }

        // Parse regular console format fields (time=, size=, bitrate=, speed=)
        // This applies to both frame= lines and time= lines
        if (!line.contains("time=") && !line.contains("size=") && frameNumber < 0) {
            return null;
        }

        // Parse time=HH:MM:SS.ss or time=N/A
        int timeIndex = line.indexOf("time=");
        if (timeIndex >= 0) {
            int start = timeIndex + 5;
            int end = line.indexOf(' ', start);
            if (end == -1)
                end = line.length();
            String timeStr = line.substring(start, end).trim();

            // Skip if time is N/A - we'll use frame-based progress instead
            if (!"N/A".equals(timeStr)) {
                try {
                    String[] parts = timeStr.split(":");
                    if (parts.length == 3) {
                        long hours = Long.parseLong(parts[0]);
                        long minutes = Long.parseLong(parts[1]);
                        double seconds = Double.parseDouble(parts[2]);
                        long totalSeconds = hours * 3600 + minutes * 60 + (long) seconds;
                        long millis = (long) ((seconds % 1) * 1000);
                        currentTime = Duration.ofSeconds(totalSeconds, millis * 1_000_000);
                    }
                } catch (NumberFormatException e) {
                    logger.trace("Failed to parse time from: {}", timeStr);
                }
            } else {
                logger.trace("time=N/A detected, will rely on frame-based progress");
            }
        }

        // Parse size=XXXXkB
        int sizeIndex = line.indexOf("size=");
        if (sizeIndex >= 0) {
            int start = sizeIndex + 5;
            // Skip leading whitespace
            while (start < line.length() && Character.isWhitespace(line.charAt(start))) {
                start++;
            }
            int end = line.indexOf(' ', start);
            if (end == -1)
                end = line.length();
            String sizeStr = line.substring(start, end).trim();
            try {
                if (sizeStr.endsWith("kB")) {
                    double kb = Double.parseDouble(sizeStr.substring(0, sizeStr.length() - 2));
                    bytesProcessed = (long) (kb * 1024);
                } else if (sizeStr.endsWith("KiB")) {
                    double kb = Double.parseDouble(sizeStr.substring(0, sizeStr.length() - 3));
                    bytesProcessed = (long) (kb * 1024);
                }
            } catch (NumberFormatException e) {
                logger.trace("Failed to parse size from: {}", sizeStr);
            }
        }

        // Parse bitrate=XXXXkbits/s
        int bitrateIndex = line.indexOf("bitrate=");
        if (bitrateIndex >= 0) {
            int start = bitrateIndex + 8;
            int end = line.indexOf(' ', start);
            if (end == -1)
                end = line.length();
            String bitrateStr = line.substring(start, end).trim();
            try {
                if (bitrateStr.endsWith("kbits/s")) {
                    double kbps = Double.parseDouble(bitrateStr.substring(0, bitrateStr.length() - 7));
                    speed = kbps * 1024 / 8; // bytes per second
                } else if (!"N/A".equals(bitrateStr)) {
                    // Try parsing without unit
                    double kbps = Double.parseDouble(bitrateStr);
                    speed = kbps * 1024 / 8;
                }
            } catch (NumberFormatException e) {
                logger.trace("Failed to parse bitrate from: {}", bitrateStr);
            }
        }

        // Return ProgressInfo if we have EITHER time OR frame number
        // This ensures progress updates even when time=N/A but frames are available
        if (currentTime != null || frameNumber >= 0) {
            return new ProgressInfo(currentTime, bytesProcessed, speed, frameNumber);
        }

        return null;
    }

    /**
     * Internal class to hold parsed progress information from FFmpeg output.
     */
    private static class ProgressInfo {
        final Duration currentTime;
        final long bytesProcessed;
        final double speed;
        final long currentFrame; // Frame number for frame-based progress tracking

        ProgressInfo(Duration currentTime, long bytesProcessed, double speed) {
            this.currentTime = currentTime;
            this.bytesProcessed = bytesProcessed;
            this.speed = speed;
            this.currentFrame = -1;
        }

        ProgressInfo(Duration currentTime, long bytesProcessed, double speed, long currentFrame) {
            this.currentTime = currentTime;
            this.bytesProcessed = bytesProcessed;
            this.speed = speed;
            this.currentFrame = currentFrame;
        }
    }

    /**
     * Extracts a user-friendly error message from FFmpeg output.
     * 
     * @param output FFmpeg output log
     * @return extracted error message
     */
    private String extractErrorMessage(String output) {
        // Look for common error patterns in FFmpeg output
        String[] lines = output.split("\n");

        // FFmpeg typically outputs errors with specific patterns
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();

            if (line.contains("Error") || line.contains("error")) {
                return line;
            }

            if (line.contains("Invalid") || line.contains("invalid")) {
                return line;
            }

            if (line.contains("Could not") || line.contains("could not")) {
                return line;
            }

            if (line.contains("Failed") || line.contains("failed")) {
                return line;
            }
        }

        // If no specific error found, return last non-empty line
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                return line;
            }
        }

        return "Unknown error";
    }

    /**
     * Cleans up partial output file after a failed conversion.
     * Requirement REQ-004.2: Partial file cleanup on error.
     * 
     * @param outputPath path to the partial output file
     */
    private void cleanupPartialFile(Path outputPath) {
        if (outputPath == null) {
            return;
        }

        try {
            if (Files.exists(outputPath)) {
                long fileSize = Files.size(outputPath);
                Files.deleteIfExists(outputPath);
                logger.info("Cleaned up partial output file: {} ({} bytes)",
                        outputPath.getFileName(), fileSize);
            }
        } catch (IOException e) {
            logger.warn("Failed to clean up partial output file: {}", outputPath, e);
        }
    }

    /**
     * Terminates any running FFmpeg process.
     * This method is called when cancellation is requested.
     * 
     * Requirements: REQ-004.2 - Process termination support
     */
    public void terminate() {
        // Process termination is handled in executeConversion via
        // process.destroyForcibly()
        // This method serves as a hook for external cancellation signals
        logger.debug("Terminate called on FFmpegService - no active processes to terminate");
    }
}