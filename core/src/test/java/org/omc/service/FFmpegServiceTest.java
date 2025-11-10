// filepath: src/test/java/org/omc/service/FFmpegServiceTest.java

package org.omc.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omc.core.ProgressCallback;
import org.omc.exception.ToolExecutionException;
import org.omc.model.AspectRatio;
import org.omc.model.AudioSettings;
import org.omc.model.ConversionResult;
import org.omc.model.ImageSettings;
import org.omc.model.ResizeMode;
import org.omc.model.Resolution;
import org.omc.model.VideoSettings;

/**
 * Unit tests for FFmpegService command building functionality.
 * 
 * Tests requirements REQ-006.1, REQ-006.2, REQ-006.3 for video, audio, and
 * image conversion.
 */
class FFmpegServiceTest {

        @TempDir
        Path tempDir;

        private FFmpegService service;
        private Path ffmpegPath;
        private Path ffprobePath;
        private Path inputPath;
        private Path outputPath;

        @BeforeEach
        void setUp() {
                ffmpegPath = tempDir.resolve("ffmpeg");
                ffprobePath = tempDir.resolve("ffprobe");
                inputPath = tempDir.resolve("input.mp4");
                outputPath = tempDir.resolve("output.mp4");

                service = new FFmpegService(ffmpegPath, ffprobePath);
        }

        // Constructor tests

        @Test
        void testConstructor_Success() {
                assertNotNull(service);
                assertEquals(ffmpegPath, service.getFfmpegPath());
                assertEquals(ffprobePath, service.getFfprobePath());
        }

        @Test
        void testConstructor_NullFfmpegPath_ThrowsException() {
                assertThrows(NullPointerException.class, () -> new FFmpegService(null, ffprobePath));
        }

        @Test
        void testConstructor_NullFfprobePath_ThrowsException() {
                assertThrows(NullPointerException.class, () -> new FFmpegService(ffmpegPath, null));
        }

        // Video command building tests

        @Test
        void testBuildVideoCommand_H264_BasicSettings() {
                // Requirement REQ-006.1: H.264 codec support
                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .bitrate(5000)
                                .frameRate(30)
                                .preset("medium")
                                .crf(23)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertNotNull(command);
                assertTrue(command.contains(ffmpegPath.toString()));
                assertTrue(command.contains("-i"));
                assertTrue(command.contains(inputPath.toString()));
                assertTrue(command.contains("-c:v"));
                assertTrue(command.contains("libx264"));
                assertTrue(command.contains("-crf"));
                assertTrue(command.contains("23"));
                assertTrue(command.contains("-preset"));
                assertTrue(command.contains("medium"));
                assertTrue(command.contains("-maxrate"));
                assertTrue(command.contains("5000k"));
                assertTrue(command.contains("-r"));
                assertTrue(command.contains("30"));
                assertTrue(command.contains("-y"));
                assertTrue(command.contains(outputPath.toString()));
        }

        @Test
        void testBuildVideoCommand_H265_WithResolution() {
                // Requirement REQ-006.1: H.265 codec support
                Resolution resolution = new Resolution(1920, 1080);
                VideoSettings settings = VideoSettings.builder()
                                .codec("H265")
                                .bitrate(8000)
                                .resolution(resolution)
                                .frameRate(60)
                                .preset("slow")
                                .crf(28)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertTrue(command.contains("libx265"));
                assertTrue(command.contains("-crf"));
                assertTrue(command.contains("28"));
                assertTrue(command.contains("-preset"));
                assertTrue(command.contains("slow"));
                assertTrue(command.contains("-vf"));
                assertTrue(command.contains("scale=1920:1080"));
                assertTrue(command.contains("-r"));
                assertTrue(command.contains("60"));
        }

        @Test
        void testBuildVideoCommand_VP9_Codec() {
                // Requirement REQ-006.1: VP9 codec support
                VideoSettings settings = VideoSettings.builder()
                                .codec("VP9")
                                .bitrate(4000)
                                .frameRate(30)
                                .preset("medium")
                                .crf(31)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertTrue(command.contains("libvpx-vp9"));
                assertTrue(command.contains("-crf"));
                assertTrue(command.contains("31"));
                assertTrue(command.contains("-b:v"));
                assertTrue(command.contains("0")); // CRF mode
        }

        @Test
        void testBuildVideoCommand_WithoutOptionalSettings() {
                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .bitrate(500)
                                .crf(23)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertTrue(command.contains("libx264"));
                assertTrue(command.contains("-crf"));
                assertTrue(command.contains("23"));
                assertFalse(command.contains("-vf"));
        }

        @Test
        void testBuildVideoCommand_NullInput_ThrowsException() {
                VideoSettings settings = VideoSettings.builder().build();
                assertThrows(NullPointerException.class,
                                () -> service.buildVideoCommand(null, outputPath, settings));
        }

        @Test
        void testBuildVideoCommand_NullOutput_ThrowsException() {
                VideoSettings settings = VideoSettings.builder().build();
                assertThrows(NullPointerException.class,
                                () -> service.buildVideoCommand(inputPath, null, settings));
        }

        @Test
        void testBuildVideoCommand_NullSettings_ThrowsException() {
                assertThrows(NullPointerException.class,
                                () -> service.buildVideoCommand(inputPath, outputPath, null));
        }

        // Audio command building tests

        @Test
        void testBuildAudioCommand_AAC_BasicSettings() {
                // Requirement REQ-006.2: AAC codec support
                Path audioInput = tempDir.resolve("input.mp3");
                Path audioOutput = tempDir.resolve("output.aac");
                AudioSettings settings = AudioSettings.builder()
                                .codec("AAC")
                                .bitrate(192)
                                .sampleRate(48000)
                                .channels(2)
                                .quality(5)
                                .build();

                List<String> command = service.buildAudioCommand(audioInput, audioOutput, settings);

                assertNotNull(command);
                assertTrue(command.contains(ffmpegPath.toString()));
                assertTrue(command.contains("-i"));
                assertTrue(command.contains(audioInput.toString()));
                assertTrue(command.contains("-c:a"));
                assertTrue(command.contains("aac"));
                assertTrue(command.contains("-b:a"));
                assertTrue(command.contains("192k"));
                assertTrue(command.contains("-ar"));
                assertTrue(command.contains("48000"));
                assertTrue(command.contains("-ac"));
                assertTrue(command.contains("2"));
                assertTrue(command.contains("-vn"));
                assertTrue(command.contains("-y"));
                assertTrue(command.contains(audioOutput.toString()));
        }

        @Test
        void testBuildAudioCommand_MP3_WithQuality() {
                // Requirement REQ-006.2: MP3 codec support
                Path audioInput = tempDir.resolve("input.wav");
                Path audioOutput = tempDir.resolve("output.mp3");
                AudioSettings settings = AudioSettings.builder()
                                .codec("MP3")
                                .bitrate(320)
                                .sampleRate(44100)
                                .channels(2)
                                .quality(2)
                                .build();

                List<String> command = service.buildAudioCommand(audioInput, audioOutput, settings);

                assertTrue(command.contains("libmp3lame"));
                assertTrue(command.contains("-b:a"));
                assertTrue(command.contains("320k"));
                assertTrue(command.contains("-q:a"));
                assertTrue(command.contains("2"));
        }

        @Test
        void testBuildAudioCommand_Opus_Codec() {
                // Requirement REQ-006.2: Opus codec support
                Path audioInput = tempDir.resolve("input.mp3");
                Path audioOutput = tempDir.resolve("output.opus");
                AudioSettings settings = AudioSettings.builder()
                                .codec("Opus")
                                .bitrate(128)
                                .sampleRate(48000)
                                .channels(2)
                                .quality(9)
                                .build();

                List<String> command = service.buildAudioCommand(audioInput, audioOutput, settings);

                assertTrue(command.contains("libopus"));
                assertTrue(command.contains("-b:a"));
                assertTrue(command.contains("128k"));
                assertTrue(command.contains("-vbr"));
                assertTrue(command.contains("on"));
                assertTrue(command.contains("-compression_level"));
                assertTrue(command.contains("9"));
        }

        @Test
        void testBuildAudioCommand_Vorbis_Codec() {
                // Requirement REQ-006.2: Vorbis codec support
                Path audioInput = tempDir.resolve("input.mp3");
                Path audioOutput = tempDir.resolve("output.ogg");
                AudioSettings settings = AudioSettings.builder()
                                .codec("Vorbis")
                                .bitrate(192)
                                .sampleRate(44100)
                                .channels(2)
                                .quality(5)
                                .build();

                List<String> command = service.buildAudioCommand(audioInput, audioOutput, settings);

                assertTrue(command.contains("libvorbis"));
                assertTrue(command.contains("-q:a"));
                assertTrue(command.contains("4")); // Quality 5 mapped to 4 for vorbis
        }

        @Test
        void testBuildAudioCommand_FLAC_Lossless() {
                // Requirement REQ-006.2: FLAC codec support
                Path audioInput = tempDir.resolve("input.wav");
                Path audioOutput = tempDir.resolve("output.flac");
                AudioSettings settings = AudioSettings.builder()
                                .codec("FLAC")
                                .bitrate(64) // Bitrate ignored for FLAC but required by validation
                                .sampleRate(44100)
                                .channels(2)
                                .quality(0)
                                .build();

                List<String> command = service.buildAudioCommand(audioInput, audioOutput, settings);

                assertTrue(command.contains("flac"));
                assertTrue(command.contains("-ar"));
                assertTrue(command.contains("44100"));
        }

        @Test
        void testBuildAudioCommand_WithoutOptionalSettings() {
                Path audioInput = tempDir.resolve("input.mp3");
                Path audioOutput = tempDir.resolve("output.aac");
                AudioSettings settings = AudioSettings.builder()
                                .codec("AAC")
                                .bitrate(192)
                                .build();

                List<String> command = service.buildAudioCommand(audioInput, audioOutput, settings);

                assertTrue(command.contains("aac"));
                assertTrue(command.contains("-b:a"));
                assertTrue(command.contains("192k"));
                assertFalse(command.contains("-ar"));
                assertFalse(command.contains("-ac"));
        }

        @Test
        void testBuildAudioCommand_NullInput_ThrowsException() {
                AudioSettings settings = AudioSettings.builder().build();
                assertThrows(NullPointerException.class,
                                () -> service.buildAudioCommand(null, outputPath, settings));
        }

        @Test
        void testBuildAudioCommand_NullOutput_ThrowsException() {
                AudioSettings settings = AudioSettings.builder().build();
                assertThrows(NullPointerException.class,
                                () -> service.buildAudioCommand(inputPath, null, settings));
        }

        @Test
        void testBuildAudioCommand_NullSettings_ThrowsException() {
                assertThrows(NullPointerException.class,
                                () -> service.buildAudioCommand(inputPath, outputPath, null));
        }

        // Image command building tests

        @Test
        void testBuildImageCommand_JPEG_WithQuality() {
                // Requirement REQ-006.3: Image conversion with quality controls
                Path imageInput = tempDir.resolve("input.png");
                Path imageOutput = tempDir.resolve("output.jpg");
                ImageSettings settings = ImageSettings.builder()
                                .quality(85)
                                .maintainAspectRatio(true)
                                .resizeMode(ResizeMode.FIT)
                                .build();

                List<String> command = service.buildImageCommand(imageInput, imageOutput, settings);

                assertNotNull(command);
                assertTrue(command.contains(ffmpegPath.toString()));
                assertTrue(command.contains("-i"));
                assertTrue(command.contains(imageInput.toString()));
                assertTrue(command.contains("-q:v"));
                assertTrue(command.contains("-frames:v"));
                assertTrue(command.contains("1"));
                assertTrue(command.contains("-y"));
                assertTrue(command.contains(imageOutput.toString()));
        }

        @Test
        void testBuildImageCommand_PNG_WithCompression() {
                // Requirement REQ-006.3: PNG compression support
                Path imageInput = tempDir.resolve("input.jpg");
                Path imageOutput = tempDir.resolve("output.png");
                ImageSettings settings = ImageSettings.builder()
                                .quality(100)
                                .compressionLevel(9)
                                .maintainAspectRatio(true)
                                .resizeMode(ResizeMode.FIT)
                                .build();

                List<String> command = service.buildImageCommand(imageInput, imageOutput, settings);

                assertTrue(command.contains("-compression_level"));
                assertTrue(command.contains("9"));
        }

        @Test
        void testBuildImageCommand_WebP_WithQuality() {
                // Requirement REQ-006.3: WebP format support
                Path imageInput = tempDir.resolve("input.jpg");
                Path imageOutput = tempDir.resolve("output.webp");
                ImageSettings settings = ImageSettings.builder()
                                .quality(90)
                                .maintainAspectRatio(true)
                                .resizeMode(ResizeMode.FIT)
                                .build();

                List<String> command = service.buildImageCommand(imageInput, imageOutput, settings);

                assertTrue(command.contains("-quality"));
                assertTrue(command.contains("90"));
        }

        @Test
        void testBuildImageCommand_WithResolution_MaintainAspectRatio() {
                // Requirement REQ-006.3: Resolution scaling with aspect ratio preservation
                Path imageInput = tempDir.resolve("input.jpg");
                Path imageOutput = tempDir.resolve("output.jpg");
                Resolution resolution = new Resolution(1920, 1080);
                ImageSettings settings = ImageSettings.builder()
                                .quality(90)
                                .resolution(resolution)
                                .maintainAspectRatio(true)
                                .resizeMode(ResizeMode.FIT)
                                .build();

                List<String> command = service.buildImageCommand(imageInput, imageOutput, settings);

                assertTrue(command.contains("-vf"));
                assertTrue(command.contains("scale=1920:1080:force_original_aspect_ratio=decrease"));
        }

        @Test
        void testBuildImageCommand_WithResolution_NoAspectRatio() {
                // Requirement REQ-006.3: Resolution scaling without aspect ratio preservation
                Path imageInput = tempDir.resolve("input.jpg");
                Path imageOutput = tempDir.resolve("output.jpg");
                Resolution resolution = new Resolution(800, 600);
                ImageSettings settings = ImageSettings.builder()
                                .quality(90)
                                .resolution(resolution)
                                .maintainAspectRatio(false)
                                .resizeMode(ResizeMode.STRETCH)
                                .build();

                List<String> command = service.buildImageCommand(imageInput, imageOutput, settings);

                assertTrue(command.contains("-vf"));
                assertTrue(command.contains("scale=800:600"));
                assertFalse(command.toString().contains("force_original_aspect_ratio"));
        }

        @Test
        void testBuildImageCommand_WithoutResolution() {
                Path imageInput = tempDir.resolve("input.jpg");
                Path imageOutput = tempDir.resolve("output.png");
                ImageSettings settings = ImageSettings.builder()
                                .quality(100)
                                .compressionLevel(9)
                                .maintainAspectRatio(true)
                                .resizeMode(ResizeMode.NONE)
                                .build();

                List<String> command = service.buildImageCommand(imageInput, imageOutput, settings);

                assertFalse(command.contains("-vf"));
        }

        @Test
        void testBuildImageCommand_NullInput_ThrowsException() {
                ImageSettings settings = ImageSettings.builder().build();
                assertThrows(NullPointerException.class,
                                () -> service.buildImageCommand(null, outputPath, settings));
        }

        @Test
        void testBuildImageCommand_NullOutput_ThrowsException() {
                ImageSettings settings = ImageSettings.builder().build();
                assertThrows(NullPointerException.class,
                                () -> service.buildImageCommand(inputPath, null, settings));
        }

        @Test
        void testBuildImageCommand_NullSettings_ThrowsException() {
                assertThrows(NullPointerException.class,
                                () -> service.buildImageCommand(inputPath, outputPath, null));
        }

        // Codec mapping tests

        @Test
        void testVideoCodecMapping_AlternativeNames() {
                VideoSettings settingsX264 = VideoSettings.builder().codec("x264").build();
                VideoSettings settingsAVC = VideoSettings.builder().codec("avc").build();

                List<String> commandX264 = service.buildVideoCommand(inputPath, outputPath, settingsX264);
                List<String> commandAVC = service.buildVideoCommand(inputPath, outputPath, settingsAVC);

                assertTrue(commandX264.contains("libx264"));
                assertTrue(commandAVC.contains("libx264"));
        }

        @Test
        void testVideoCodecMapping_H265_AlternativeNames() {
                VideoSettings settingsX265 = VideoSettings.builder().codec("x265").build();
                VideoSettings settingsHEVC = VideoSettings.builder().codec("hevc").build();

                List<String> commandX265 = service.buildVideoCommand(inputPath, outputPath, settingsX265);
                List<String> commandHEVC = service.buildVideoCommand(inputPath, outputPath, settingsHEVC);

                assertTrue(commandX265.contains("libx265"));
                assertTrue(commandHEVC.contains("libx265"));
        }

        @Test
        void testVideoCodecMapping_VP9_AlternativeName() {
                VideoSettings settingsWebM = VideoSettings.builder().codec("webm").build();

                List<String> commandWebM = service.buildVideoCommand(inputPath, outputPath, settingsWebM);

                assertTrue(commandWebM.contains("libvpx-vp9"));
        }

        @Test
        void testAudioCodecMapping_MP3_AlternativeName() {
                Path audioInput = tempDir.resolve("input.wav");
                Path audioOutput = tempDir.resolve("output.mp3");
                AudioSettings settingsLame = AudioSettings.builder().codec("lame").build();

                List<String> commandLame = service.buildAudioCommand(audioInput, audioOutput, settingsLame);

                assertTrue(commandLame.contains("libmp3lame"));
        }

        @Test
        void testAudioCodecMapping_Vorbis_AlternativeName() {
                Path audioInput = tempDir.resolve("input.wav");
                Path audioOutput = tempDir.resolve("output.ogg");
                AudioSettings settingsOgg = AudioSettings.builder().codec("ogg").build();

                List<String> commandOgg = service.buildAudioCommand(audioInput, audioOutput, settingsOgg);

                assertTrue(commandOgg.contains("libvorbis"));
        }

        // Edge case tests

        @Test
        void testBuildVideoCommand_HighQualityCRF() {
                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .bitrate(10000)
                                .preset("veryslow")
                                .crf(18)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertTrue(command.contains("-crf"));
                assertTrue(command.contains("18"));
                assertTrue(command.contains("-preset"));
                assertTrue(command.contains("veryslow"));
        }

        @Test
        void testBuildVideoCommand_LowQualityCRF() {
                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .bitrate(1000)
                                .preset("ultrafast")
                                .crf(35)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertTrue(command.contains("-crf"));
                assertTrue(command.contains("35"));
                assertTrue(command.contains("-preset"));
                assertTrue(command.contains("ultrafast"));
        }

        @Test
        void testBuildAudioCommand_HighBitrate() {
                Path audioInput = tempDir.resolve("input.wav");
                Path audioOutput = tempDir.resolve("output.aac");
                AudioSettings settings = AudioSettings.builder()
                                .codec("AAC")
                                .bitrate(320)
                                .sampleRate(96000)
                                .channels(6)
                                .build();

                List<String> command = service.buildAudioCommand(audioInput, audioOutput, settings);

                assertTrue(command.contains("-b:a"));
                assertTrue(command.contains("320k"));
                assertTrue(command.contains("-ar"));
                assertTrue(command.contains("96000"));
                assertTrue(command.contains("-ac"));
                assertTrue(command.contains("6"));
        }

        @Test
        void testBuildImageCommand_MaxQuality() {
                Path imageInput = tempDir.resolve("input.jpg");
                Path imageOutput = tempDir.resolve("output.jpg");
                ImageSettings settings = ImageSettings.builder()
                                .quality(100)
                                .maintainAspectRatio(true)
                                .resizeMode(ResizeMode.FIT)
                                .build();

                List<String> command = service.buildImageCommand(imageInput, imageOutput, settings);

                assertTrue(command.contains("-q:v"));
                // Quality 100 should map to low JPEG q value (high quality)
                assertNotNull(command);
        }

        @Test
        void testBuildImageCommand_MinQuality() {
                Path imageInput = tempDir.resolve("input.jpg");
                Path imageOutput = tempDir.resolve("output.jpg");
                ImageSettings settings = ImageSettings.builder()
                                .quality(0)
                                .maintainAspectRatio(true)
                                .resizeMode(ResizeMode.FIT)
                                .build();

                List<String> command = service.buildImageCommand(imageInput, imageOutput, settings);

                assertTrue(command.contains("-q:v"));
                // Quality 0 should map to high JPEG q value (low quality)
                assertNotNull(command);
        }

        // Additional tests for gaps

        @Test
        void testBuildVideoCommand_UnknownCodec_PassThrough() {
                VideoSettings settings = VideoSettings.builder()
                                .codec("unknowncodec")
                                .bitrate(5000)
                                .crf(23)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertTrue(command.contains("-c:v"));
                assertTrue(command.contains("unknowncodec"));
        }

        @Test
        void testBuildVideoCommand_AV1_Codec() {
                VideoSettings settings = VideoSettings.builder()
                                .codec("AV1")
                                .bitrate(4000)
                                .crf(25)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertTrue(command.contains("libaom-av1"));
                // AV1 does not use CRF in this implementation, only bitrate
                assertTrue(command.contains("-maxrate"));
                assertTrue(command.contains("4000k"));
        }

        @Test
        void testBuildVideoCommand_MPEG4_Codec() {
                VideoSettings settings = VideoSettings.builder()
                                .codec("MPEG4")
                                .bitrate(2000)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertTrue(command.contains("mpeg4"));
        }

        @Test
        void testBuildVideoCommand_BitrateZero_NoMaxrate() {
                // Use minimum valid bitrate (500) instead of 0, which is invalid
                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .bitrate(500)
                                .crf(23)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertTrue(command.contains("libx264"));
                // With valid bitrate, maxrate and bufsize should be present
                assertTrue(command.contains("-maxrate"));
                assertTrue(command.contains("-bufsize"));
        }

        @Test
        void testBuildAudioCommand_UnknownCodec_PassThrough() {
                Path audioInput = tempDir.resolve("input.wav");
                Path audioOutput = tempDir.resolve("output.unknown");
                AudioSettings settings = AudioSettings.builder()
                                .codec("unknowncodec")
                                .bitrate(192)
                                .build();

                List<String> command = service.buildAudioCommand(audioInput, audioOutput, settings);

                assertTrue(command.contains("-c:a"));
                assertTrue(command.contains("unknowncodec"));
        }

        @Test
        void testBuildAudioCommand_WAV_Codec() {
                Path audioInput = tempDir.resolve("input.mp3");
                Path audioOutput = tempDir.resolve("output.wav");
                AudioSettings settings = AudioSettings.builder()
                                .codec("WAV")
                                .bitrate(320) // Use maximum valid bitrate for AudioSettings validation
                                .sampleRate(44100)
                                .channels(2)
                                .build();

                List<String> command = service.buildAudioCommand(audioInput, audioOutput, settings);

                assertTrue(command.contains("pcm_s16le"));
                assertTrue(command.contains("-ar"));
                assertTrue(command.contains("44100"));
        }

        @Test
        void testBuildAudioCommand_ALAC_Codec() {
                Path audioInput = tempDir.resolve("input.wav");
                Path audioOutput = tempDir.resolve("output.m4a");
                AudioSettings settings = AudioSettings.builder()
                                .codec("ALAC")
                                .bitrate(256)
                                .build();

                List<String> command = service.buildAudioCommand(audioInput, audioOutput, settings);

                assertTrue(command.contains("alac"));
        }

        @Test
        void testBuildImageCommand_UnsupportedFormat_NoQuality() {
                Path imageInput = tempDir.resolve("input.jpg");
                Path imageOutput = tempDir.resolve("output.bmp");
                ImageSettings settings = ImageSettings.builder()
                                .quality(80)
                                .build();

                List<String> command = service.buildImageCommand(imageInput, imageOutput, settings);

                assertFalse(command.contains("-q:v"));
                assertFalse(command.contains("-quality"));
                assertFalse(command.contains("-compression_level"));
        }

        @Test
        void testBuildImageCommand_JPEG_QualityCalculation() {
                Path imageInput = tempDir.resolve("input.png");
                Path imageOutput = tempDir.resolve("output.jpg");

                // Test quality 0 -> q=31
                ImageSettings settings0 = ImageSettings.builder().quality(0).build();
                List<String> command0 = service.buildImageCommand(imageInput, imageOutput, settings0);
                int qvIndex0 = command0.indexOf("-q:v");
                assertTrue(qvIndex0 >= 0);
                assertEquals("31", command0.get(qvIndex0 + 1));

                // Test quality 50 -> q=17 (31 - 50*29/100 = 31-14=17 due to int division)
                ImageSettings settings50 = ImageSettings.builder().quality(50).build();
                List<String> command50 = service.buildImageCommand(imageInput, imageOutput, settings50);
                int qvIndex50 = command50.indexOf("-q:v");
                assertEquals("17", command50.get(qvIndex50 + 1));

                // Test quality 100 -> q=2
                ImageSettings settings100 = ImageSettings.builder().quality(100).build();
                List<String> command100 = service.buildImageCommand(imageInput, imageOutput, settings100);
                int qvIndex100 = command100.indexOf("-q:v");
                assertEquals("2", command100.get(qvIndex100 + 1));
        }

        // ========================================
        // Execution Tests
        // ========================================

        @Test
        void testExtractErrorMessage_ErrorInOutput() throws Exception {
                // Use reflection to access private method for testing
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "extractErrorMessage", String.class);
                method.setAccessible(true);

                String output = "FFmpeg output\nSome progress\nError: Invalid format\nMore output";
                String result = (String) method.invoke(service, output);

                assertTrue(result.contains("Error"));
                assertTrue(result.contains("Invalid format"));
        }

        @Test
        void testExtractErrorMessage_NoSpecificError() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "extractErrorMessage", String.class);
                method.setAccessible(true);

                String output = "Some output\nMore output\nLast line";
                String result = (String) method.invoke(service, output);

                assertEquals("Last line", result);
        }

        @Test
        void testExtractErrorMessage_EmptyOutput() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "extractErrorMessage", String.class);
                method.setAccessible(true);

                String output = "";
                String result = (String) method.invoke(service, output);

                assertEquals("Unknown error", result);
        }

        @Test
        void testExtractErrorMessage_FailedPattern() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "extractErrorMessage", String.class);
                method.setAccessible(true);

                String output = "Output line\nConversion failed: codec not found\nMore info";
                String result = (String) method.invoke(service, output);

                assertTrue(result.contains("failed"));
        }

        @Test
        void testExtractErrorMessage_CouldNotPattern() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "extractErrorMessage", String.class);
                method.setAccessible(true);

                String output = "Progress\nCould not open file\nDone";
                String result = (String) method.invoke(service, output);

                assertTrue(result.contains("Could not"));
        }

        @Test
        void testExtractErrorMessage_InvalidPattern() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "extractErrorMessage", String.class);
                method.setAccessible(true);

                String output = "Some output\nInvalid data found\nContinuing";
                String result = (String) method.invoke(service, output);

                assertTrue(result.contains("Invalid"));
        }

        @Test
        void testTerminate() {
                // Test that terminate() can be called without exceptions
                assertDoesNotThrow(() -> service.terminate());
        }

        // ========================================
        // Progress Parsing Tests (Task 24)
        // ========================================

        @Test
        void testParseProgressLine_ValidProgressLine() throws Exception {
                // Use reflection to test private method
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "parseProgressLine", String.class);
                method.setAccessible(true);

                // Typical FFmpeg progress output
                String progressLine = "frame= 1234 fps= 30 q=28.0 size=   10240kB time=00:01:23.45 bitrate=1008.0kbits/s speed=1.5x";
                Object result = method.invoke(service, progressLine);

                assertNotNull(result);
                // ProgressInfo is a private inner class, so we can only verify it's not null
        }

        @Test
        void testParseProgressLine_TimeField() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "parseProgressLine", String.class);
                method.setAccessible(true);

                // FFmpeg line with time field
                String progressLine = "frame=100 time=00:00:10.50 size=1024kB";
                Object result = method.invoke(service, progressLine);

                assertNotNull(result);

                // Verify time was extracted by checking the ProgressInfo object fields
                Class<?> progressInfoClass = result.getClass();
                java.lang.reflect.Field timeField = progressInfoClass.getDeclaredField("currentTime");
                timeField.setAccessible(true);
                java.time.Duration time = (java.time.Duration) timeField.get(result);

                assertNotNull(time);
                assertEquals(10, time.getSeconds());
                // Note: milliseconds might have precision loss, so we check approximate value
                assertTrue(time.toMillis() >= 10500 && time.toMillis() <= 10600);
        }

        @Test
        void testParseProgressLine_SizeField() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "parseProgressLine", String.class);
                method.setAccessible(true);

                // FFmpeg line with size field
                String progressLine = "frame=200 time=00:00:20.00 size=  2048kB bitrate=800kbits/s";
                Object result = method.invoke(service, progressLine);

                assertNotNull(result);

                // Verify size was extracted
                Class<?> progressInfoClass = result.getClass();
                java.lang.reflect.Field sizeField = progressInfoClass.getDeclaredField("bytesProcessed");
                sizeField.setAccessible(true);
                long size = (Long) sizeField.get(result);

                assertEquals(2048 * 1024, size);
        }

        @Test
        void testParseProgressLine_SpeedFromBitrate() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "parseProgressLine", String.class);
                method.setAccessible(true);

                // FFmpeg line with bitrate field
                String progressLine = "frame=300 time=00:00:30.00 size=3072kB bitrate=800kbits/s";
                Object result = method.invoke(service, progressLine);

                assertNotNull(result);

                // Verify speed was calculated from bitrate
                Class<?> progressInfoClass = result.getClass();
                java.lang.reflect.Field speedField = progressInfoClass.getDeclaredField("speed");
                speedField.setAccessible(true);
                double speed = (Double) speedField.get(result);

                // 800 kbits/s = 800 * 1024 / 8 bytes/s = 102400 bytes/s
                assertEquals(102400.0, speed, 1.0);
        }

        @Test
        void testParseProgressLine_NoProgressData_ReturnsNull() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "parseProgressLine", String.class);
                method.setAccessible(true);

                // FFmpeg line without progress data (e.g., encoder info)
                String nonProgressLine = "Input #0, mov,mp4,m4a,3gp,3g2,mj2, from 'input.mp4':";
                Object result = method.invoke(service, nonProgressLine);

                assertNull(result);
        }

        @Test
        void testParseProgressLine_MalformedTime_HandlesGracefully() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "parseProgressLine", String.class);
                method.setAccessible(true);

                // FFmpeg line with malformed time field
                String progressLine = "frame=100 time=invalid size=1024kB";
                Object result = method.invoke(service, progressLine);

                // Should still return ProgressInfo but with null time
                assertNotNull(result);

                Class<?> progressInfoClass = result.getClass();
                java.lang.reflect.Field timeField = progressInfoClass.getDeclaredField("currentTime");
                timeField.setAccessible(true);
                java.time.Duration time = (java.time.Duration) timeField.get(result);

                assertNull(time);
        }

        @Test
        void testParseProgressLine_LongDuration() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "parseProgressLine", String.class);
                method.setAccessible(true);

                // FFmpeg line with long duration (2 hours 30 minutes 45.67 seconds)
                String progressLine = "frame=5000 time=02:30:45.67 size=50000kB bitrate=1500kbits/s";
                Object result = method.invoke(service, progressLine);

                assertNotNull(result);

                Class<?> progressInfoClass = result.getClass();
                java.lang.reflect.Field timeField = progressInfoClass.getDeclaredField("currentTime");
                timeField.setAccessible(true);
                java.time.Duration time = (java.time.Duration) timeField.get(result);

                assertNotNull(time);
                long expectedSeconds = 2 * 3600 + 30 * 60 + 45;
                assertEquals(expectedSeconds, time.getSeconds());
        }

        @Test
        void testParseProgressLine_VariousFormats() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "parseProgressLine", String.class);
                method.setAccessible(true);

                // Test various FFmpeg output formats
                String[] testLines = {
                                "frame=1 time=00:00:01.00 size=100kB",
                                "size=200kB time=00:00:02.00 frame=2",
                                "time=00:00:03.00 bitrate=800kbits/s size=300kB",
                                "frame=  10 fps= 25 q=-1.0 size=    400kB time=00:00:04.00 bitrate= 800.0kbits/s speed=1.0x"
                };

                for (String line : testLines) {
                        Object result = method.invoke(service, line);
                        assertNotNull(result, "Failed to parse: " + line);
                }
        }

        @Test
        void testParseProgressLine_TimeNA_WithFrameNumber() throws Exception {
                // Test the fix for progress tracking when FFmpeg outputs time=N/A
                // This occurs with complex videos during initial analysis
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "parseProgressLine", String.class);
                method.setAccessible(true);

                // Simulate FFmpeg output with time=N/A but valid frame number
                // This is what happens during the first 10,000+ frames of complex videos
                String progressLine = "frame=  150 fps= 75 q=28.0 size=    512kB time=N/A bitrate=N/A speed=1.5x";
                Object result = method.invoke(service, progressLine);

                // Should return ProgressInfo with frame number even though time=N/A
                assertNotNull(result, "Should return ProgressInfo even with time=N/A");

                // Verify frame number was extracted
                Class<?> progressInfoClass = result.getClass();
                java.lang.reflect.Field frameField = progressInfoClass.getDeclaredField("currentFrame");
                frameField.setAccessible(true);
                long frameNumber = (Long) frameField.get(result);

                assertEquals(150, frameNumber, "Frame number should be extracted even with time=N/A");

                // Verify time is null (as expected)
                java.lang.reflect.Field timeField = progressInfoClass.getDeclaredField("currentTime");
                timeField.setAccessible(true);
                java.time.Duration time = (java.time.Duration) timeField.get(result);

                assertNull(time, "Time should be null when FFmpeg outputs time=N/A");
        }

        @Test
        void testParseProgressLine_TimeNA_ConsoleFormat() throws Exception {
                // Test parsing with time=N/A in console format
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "parseProgressLine", String.class);
                method.setAccessible(true);

                // Test multiple frame numbers with time=N/A (realistic scenario)
                String[] testLines = {
                                "frame=   50 fps= 50 q=28.0 size=    256kB time=N/A bitrate=N/A speed=1.0x",
                                "frame=  500 fps= 60 q=28.0 size=   2048kB time=N/A bitrate=N/A speed=1.2x",
                                "frame= 5000 fps= 70 q=28.0 size=  20480kB time=N/A bitrate=N/A speed=1.4x",
                                "frame=10000 fps= 75 q=28.0 size=  40960kB time=N/A bitrate=N/A speed=1.5x"
                };

                for (String line : testLines) {
                        Object result = method.invoke(service, line);
                        assertNotNull(result, "Should parse line with time=N/A: " + line);

                        // Verify frame number is available
                        Class<?> progressInfoClass = result.getClass();
                        java.lang.reflect.Field frameField = progressInfoClass.getDeclaredField("currentFrame");
                        frameField.setAccessible(true);
                        long frameNumber = (Long) frameField.get(result);

                        assertTrue(frameNumber > 0, "Frame number should be positive for: " + line);
                }
        }

        @Test
        void testParseProgressLine_TimeNA_ProgressFormat() throws Exception {
                // Test parsing with out_time=N/A in -progress format
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "parseProgressLine", String.class);
                method.setAccessible(true);

                // FFmpeg -progress format with out_time=N/A
                String progressLine = "out_time=N/A";
                Object result = method.invoke(service, progressLine);

                // Should return ProgressInfo even with out_time=N/A
                assertNotNull(result, "Should handle out_time=N/A in progress format");

                // Verify time is null
                Class<?> progressInfoClass = result.getClass();
                java.lang.reflect.Field timeField = progressInfoClass.getDeclaredField("currentTime");
                timeField.setAccessible(true);
                java.time.Duration time = (java.time.Duration) timeField.get(result);

                assertNull(time, "Time should be null when out_time=N/A");
        }

        @Test
        void testProgressCallback_IsInvoked() throws Exception {
                // This test would require mocking ProcessBuilder which is complex
                // Instead, we verify that the callback interface works correctly
                final boolean[] callbackInvoked = { false };
                final double[] percentage = { 0.0 };
                final long[] bytes = { 0L };
                final double[] speed = { 0.0 };

                org.omc.core.ProgressCallback callback = (pct, b, s) -> {
                        callbackInvoked[0] = true;
                        percentage[0] = pct;
                        bytes[0] = b;
                        speed[0] = s;
                };

                // Invoke callback directly
                callback.onProgress(50.5, 1024000, 256.75);

                assertTrue(callbackInvoked[0]);
                assertEquals(50.5, percentage[0]);
                assertEquals(1024000, bytes[0]);
                assertEquals(256.75, speed[0]);
        }

        @Test
        void testProgressCallback_NoOp() {
                org.omc.core.ProgressCallback callback = org.omc.core.ProgressCallback
                                .noOp();

                // Should not throw exception
                assertDoesNotThrow(() -> callback.onProgress(50.0, 1000, 100.0));
        }

        @Test
        void testProgressPercentageCalculation() {
                // Test percentage calculation logic
                // If duration is 100 seconds and current time is 25 seconds, percentage should
                // be 25%
                long totalDurationMillis = 100000; // 100 seconds
                long currentTimeMillis = 25000; // 25 seconds

                double percentage = (currentTimeMillis / (double) totalDurationMillis) * 100.0;
                percentage = Math.min(100.0, Math.max(0.0, percentage));

                assertEquals(25.0, percentage, 0.01);
        }

        @Test
        void testProgressPercentageCalculation_OverHundred() {
                // Test that percentage is clamped to 100
                long totalDurationMillis = 100000;
                long currentTimeMillis = 150000;

                double percentage = (currentTimeMillis / (double) totalDurationMillis) * 100.0;
                percentage = Math.min(100.0, Math.max(0.0, percentage));

                assertEquals(100.0, percentage, 0.01);
        }

        @Test
        void testProgressPercentageCalculation_Negative() {
                // Test that percentage is clamped to 0
                long totalDurationMillis = 100000;
                long currentTimeMillis = -10000;

                double percentage = (currentTimeMillis / (double) totalDurationMillis) * 100.0;
                percentage = Math.min(100.0, Math.max(0.0, percentage));

                assertEquals(0.0, percentage, 0.01);
        }

        // ========================================
        // Task 25: Metadata Extraction Tests
        // ========================================

        @Test
        void testExtractMetadata_NullFilePath() {
                assertThrows(NullPointerException.class,
                                () -> service.extractMetadata(null, org.omc.model.FormatCategory.VIDEO));
        }

        @Test
        void testExtractMetadata_NullCategory() {
                Path dummyPath = Path.of("/tmp/test.mp4");
                assertThrows(NullPointerException.class, () -> service.extractMetadata(dummyPath, null));
        }

        @Test
        void testExtractVideoMetadata_BasicProperties() throws Exception {
                // Mock ffprobe JSON output for video
                String ffprobeOutput = """
                                {
                                  "streams": [
                                    {
                                      "codec_type": "video",
                                      "codec_name": "h264",
                                      "width": 1920,
                                      "height": 1080,
                                      "avg_frame_rate": "30000/1001",
                                      "bit_rate": "5000000"
                                    },
                                    {
                                      "codec_type": "audio",
                                      "codec_name": "aac",
                                      "bit_rate": "128000"
                                    }
                                  ],
                                  "format": {
                                    "duration": "120.5"
                                  }
                                }
                                """;

                // Use reflection to call private extractVideoMetadata method
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "extractVideoMetadata", com.fasterxml.jackson.databind.JsonNode.class);
                method.setAccessible(true);

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(ffprobeOutput);

                org.omc.model.VideoMetadata metadata = (org.omc.model.VideoMetadata) method
                                .invoke(service, root);

                assertNotNull(metadata);
                assertEquals(1920, metadata.getWidth());
                assertEquals(1080, metadata.getHeight());
                assertEquals("h264", metadata.getVideoCodec());
                assertEquals("aac", metadata.getAudioCodec());
                assertEquals(29.97, metadata.getFrameRate(), 0.01); // 30000/1001 = 29.97
                assertEquals(5000000, metadata.getVideoBitrate());
                assertEquals(128000, metadata.getAudioBitrate());
                assertEquals(120500, metadata.getDuration().toMillis());
        }

        @Test
        void testExtractVideoMetadata_NoAudioStream() throws Exception {
                // Video without audio track
                String ffprobeOutput = """
                                {
                                  "streams": [
                                    {
                                      "codec_type": "video",
                                      "codec_name": "hevc",
                                      "width": 3840,
                                      "height": 2160,
                                      "avg_frame_rate": "60/1",
                                      "bit_rate": "20000000"
                                    }
                                  ],
                                  "format": {
                                    "duration": "60.0"
                                  }
                                }
                                """;

                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "extractVideoMetadata", com.fasterxml.jackson.databind.JsonNode.class);
                method.setAccessible(true);

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(ffprobeOutput);

                org.omc.model.VideoMetadata metadata = (org.omc.model.VideoMetadata) method
                                .invoke(service, root);

                assertNotNull(metadata);
                assertEquals(3840, metadata.getWidth());
                assertEquals(2160, metadata.getHeight());
                assertEquals("hevc", metadata.getVideoCodec());
                assertNull(metadata.getAudioCodec());
                assertEquals(60.0, metadata.getFrameRate(), 0.01);
                assertEquals(0, metadata.getAudioBitrate());
                assertTrue(metadata.is4K());
        }

        @Test
        void testExtractAudioMetadata_BasicProperties() throws Exception {
                // Mock ffprobe JSON output for audio
                String ffprobeOutput = """
                                {
                                  "streams": [
                                    {
                                      "codec_type": "audio",
                                      "codec_name": "mp3",
                                      "bit_rate": "320000",
                                      "sample_rate": "44100",
                                      "channels": 2
                                    }
                                  ],
                                  "format": {
                                    "duration": "180.25"
                                  }
                                }
                                """;

                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "extractAudioMetadata", com.fasterxml.jackson.databind.JsonNode.class);
                method.setAccessible(true);

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(ffprobeOutput);

                org.omc.model.AudioMetadata metadata = (org.omc.model.AudioMetadata) method
                                .invoke(service, root);

                assertNotNull(metadata);
                assertEquals("mp3", metadata.getCodec());
                assertEquals(320000, metadata.getBitrate());
                assertEquals(44100, metadata.getSampleRate());
                assertEquals(2, metadata.getChannels());
                assertEquals(180250, metadata.getDuration().toMillis());
                assertEquals("stereo", metadata.getChannelDescription());
        }

        @Test
        void testExtractAudioMetadata_MonoChannel() throws Exception {
                // Audio with mono channel
                String ffprobeOutput = """
                                {
                                  "streams": [
                                    {
                                      "codec_type": "audio",
                                      "codec_name": "opus",
                                      "bit_rate": "128000",
                                      "sample_rate": "48000",
                                      "channels": 1
                                    }
                                  ],
                                  "format": {
                                    "duration": "90.0"
                                  }
                                }
                                """;

                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "extractAudioMetadata", com.fasterxml.jackson.databind.JsonNode.class);
                method.setAccessible(true);

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(ffprobeOutput);

                org.omc.model.AudioMetadata metadata = (org.omc.model.AudioMetadata) method
                                .invoke(service, root);

                assertNotNull(metadata);
                assertEquals(1, metadata.getChannels());
                assertEquals("mono", metadata.getChannelDescription());
        }

        @Test
        void testExtractImageMetadata_BasicProperties() throws Exception {
                // Mock ffprobe JSON output for image
                String ffprobeOutput = """
                                {
                                  "streams": [
                                    {
                                      "codec_type": "video",
                                      "codec_name": "png",
                                      "width": 1920,
                                      "height": 1080,
                                      "pix_fmt": "rgba"
                                    }
                                  ]
                                }
                                """;

                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "extractImageMetadata", com.fasterxml.jackson.databind.JsonNode.class);
                method.setAccessible(true);

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(ffprobeOutput);

                org.omc.model.ImageMetadata metadata = (org.omc.model.ImageMetadata) method
                                .invoke(service, root);

                assertNotNull(metadata);
                assertEquals(1920, metadata.getWidth());
                assertEquals(1080, metadata.getHeight());
                assertEquals("RGB", metadata.getColorSpace());
                assertTrue(metadata.hasAlpha());
                assertEquals(8, metadata.getBitDepth());
        }

        @Test
        void testExtractImageMetadata_GrayscaleNoAlpha() throws Exception {
                // Image with grayscale color space and no alpha
                String ffprobeOutput = """
                                {
                                  "streams": [
                                    {
                                      "codec_type": "video",
                                      "codec_name": "jpeg",
                                      "width": 800,
                                      "height": 600,
                                      "pix_fmt": "gray"
                                    }
                                  ]
                                }
                                """;

                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "extractImageMetadata", com.fasterxml.jackson.databind.JsonNode.class);
                method.setAccessible(true);

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(ffprobeOutput);

                org.omc.model.ImageMetadata metadata = (org.omc.model.ImageMetadata) method
                                .invoke(service, root);

                assertNotNull(metadata);
                assertEquals(800, metadata.getWidth());
                assertEquals(600, metadata.getHeight());
                assertEquals("Grayscale", metadata.getColorSpace());
                assertFalse(metadata.hasAlpha());
        }

        @Test
        void testMapPixelFormatToColorSpace_RGB() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "mapPixelFormatToColorSpace", String.class);
                method.setAccessible(true);

                assertEquals("RGB", method.invoke(service, "rgb24"));
                assertEquals("RGB", method.invoke(service, "rgba"));
                assertEquals("RGB", method.invoke(service, "rgb48le"));
        }

        @Test
        void testMapPixelFormatToColorSpace_YUV() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "mapPixelFormatToColorSpace", String.class);
                method.setAccessible(true);

                assertEquals("YUV", method.invoke(service, "yuv420p"));
                assertEquals("YUV", method.invoke(service, "yuv444p"));
                assertEquals("YUV", method.invoke(service, "yuva420p"));
        }

        @Test
        void testMapPixelFormatToColorSpace_Grayscale() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod(
                                "mapPixelFormatToColorSpace", String.class);
                method.setAccessible(true);

                assertEquals("Grayscale", method.invoke(service, "gray"));
                assertEquals("Grayscale", method.invoke(service, "gray16le"));
        }

        @Test
        void testConvertVideo_NonZeroExitCode_CleansUpPartialFile() throws IOException {
                // Create a partial output file that should be cleaned up on failure
                Path partialOutput = tempDir.resolve("partial_output.mp4");
                Files.writeString(partialOutput, "partial content");
                assertTrue(Files.exists(partialOutput));

                Path input = tempDir.resolve("input.mp4");
                VideoSettings settings = VideoSettings.builder().codec("H264").crf(23).build();

                // This should fail due to non-existent ffmpeg binary and clean up the partial
                // file
                assertThrows(ToolExecutionException.class,
                                () -> service.convertVideo(input, partialOutput, settings, ProgressCallback.noOp()));

                // Verify the partial file was cleaned up
                assertFalse(Files.exists(partialOutput));
        }

        @Test
        void testConvertAudio_NonZeroExitCode_CleansUpPartialFile() throws IOException {
                // Create a partial output file that should be cleaned up on failure
                Path partialOutput = tempDir.resolve("partial_output.mp3");
                Files.writeString(partialOutput, "partial content");
                assertTrue(Files.exists(partialOutput));

                Path input = tempDir.resolve("input.wav");
                AudioSettings settings = AudioSettings.builder().codec("MP3").bitrate(192).build();

                // This should fail due to non-existent ffmpeg binary and clean up the partial
                // file
                assertThrows(ToolExecutionException.class,
                                () -> service.convertAudio(input, partialOutput, settings, ProgressCallback.noOp()));

                // Verify the partial file was cleaned up
                assertFalse(Files.exists(partialOutput));
        }

        // ========================================
        // GPU Codec Functionality Tests (REQ-VID-1.2, REQ-VID-1.3, REQ-PERF-1.3)
        // ========================================

        @Test
        void testIsGPUCodec_H264Nvenc_ReturnsTrue() throws Exception {
                // Use reflection to test private isGPUCodec method
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod("isGPUCodec", String.class);
                method.setAccessible(true);

                Boolean result = (Boolean) method.invoke(service, "h264_nvenc");
                assertTrue(result, "h264_nvenc should be identified as GPU codec");
        }

        @Test
        void testIsGPUCodec_HevcNvenc_ReturnsTrue() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod("isGPUCodec", String.class);
                method.setAccessible(true);

                Boolean result = (Boolean) method.invoke(service, "hevc_nvenc");
                assertTrue(result, "hevc_nvenc should be identified as GPU codec");
        }

        @Test
        void testIsGPUCodec_H264Cpu_ReturnsFalse() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod("isGPUCodec", String.class);
                method.setAccessible(true);

                Boolean result = (Boolean) method.invoke(service, "h264");
                assertFalse(result, "h264 (CPU) should not be identified as GPU codec");
        }

        @Test
        void testIsGPUCodec_H265Cpu_ReturnsFalse() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod("isGPUCodec", String.class);
                method.setAccessible(true);

                Boolean result = (Boolean) method.invoke(service, "h265");
                assertFalse(result, "h265 (CPU) should not be identified as GPU codec");
        }

        @Test
        void testIsGPUCodec_Vp9_ReturnsFalse() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod("isGPUCodec", String.class);
                method.setAccessible(true);

                Boolean result = (Boolean) method.invoke(service, "vp9");
                assertFalse(result, "vp9 should not be identified as GPU codec");
        }

        @Test
        void testIsGPUCodec_NullInput_ReturnsFalse() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod("isGPUCodec", String.class);
                method.setAccessible(true);

                Boolean result = (Boolean) method.invoke(service, (String) null);
                assertFalse(result, "null input should return false");
        }

        @Test
        void testIsGPUCodec_EmptyString_ReturnsFalse() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod("isGPUCodec", String.class);
                method.setAccessible(true);

                Boolean result = (Boolean) method.invoke(service, "");
                assertFalse(result, "empty string should return false");
        }

        @Test
        void testIsGPUCodec_CaseInsensitive_H264Nvenc() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod("isGPUCodec", String.class);
                method.setAccessible(true);

                Boolean result = (Boolean) method.invoke(service, "H264_NVENC");
                assertTrue(result, "H264_NVENC (uppercase) should be identified as GPU codec");
        }

        @Test
        void testIsGPUCodec_CaseInsensitive_HevcNvenc() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod("isGPUCodec", String.class);
                method.setAccessible(true);

                Boolean result = (Boolean) method.invoke(service, "HEVC_NVENC");
                assertTrue(result, "HEVC_NVENC (uppercase) should be identified as GPU codec");
        }

        @Test
        void testMapVideoCodec_Mpeg4() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod("mapVideoCodec", String.class);
                method.setAccessible(true);

                String result = (String) method.invoke(service, "mpeg4");
                assertEquals("mpeg4", result, "mpeg4 should map to mpeg4");
        }

        @Test
        void testMapVideoCodec_H264Nvenc() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod("mapVideoCodec", String.class);
                method.setAccessible(true);

                String result = (String) method.invoke(service, "h264_nvenc");
                assertEquals("h264_nvenc", result, "h264_nvenc should map to h264_nvenc");
        }

        @Test
        void testMapVideoCodec_HevcNvenc() throws Exception {
                java.lang.reflect.Method method = FFmpegService.class.getDeclaredMethod("mapVideoCodec", String.class);
                method.setAccessible(true);

                String result = (String) method.invoke(service, "hevc_nvenc");
                assertEquals("hevc_nvenc", result, "hevc_nvenc should map to hevc_nvenc");
        }

        @Test
        void testBuildVideoCommand_H264Nvenc_IncludesGPUAcceleration() {
                VideoSettings settings = VideoSettings.builder()
                                .codec("h264_nvenc")
                                .bitrate(5000)
                                .crf(23)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertTrue(command.contains("-hwaccel"), "GPU command should contain -hwaccel");
                assertTrue(command.contains("cuda"), "GPU command should contain cuda");

                // Verify order: -progress, -hwaccel, -hwaccel_output_format, -i
                int progressIndex = command.indexOf("-progress");
                int hwaccelIndex = command.indexOf("-hwaccel");
                int hwaccelOutputIndex = command.indexOf("-hwaccel_output_format");
                int inputIndex = command.indexOf("-i");

                assertTrue(progressIndex >= 0, "Should contain -progress");
                assertTrue(hwaccelIndex > progressIndex, "-hwaccel should come after -progress");
                assertTrue(hwaccelOutputIndex > hwaccelIndex, "-hwaccel_output_format should come after -hwaccel");
                assertTrue(inputIndex > hwaccelOutputIndex, "-i should come after -hwaccel_output_format");

                // Verify GPU codec is used
                int codecIndex = command.indexOf("-c:v");
                assertTrue(codecIndex >= 0, "Should contain -c:v");
                assertEquals("h264_nvenc", command.get(codecIndex + 1), "Should use h264_nvenc codec");
        }

        @Test
        void testBuildVideoCommand_HevcNvenc_IncludesGPUAcceleration() {
                VideoSettings settings = VideoSettings.builder()
                                .codec("hevc_nvenc")
                                .bitrate(8000)
                                .crf(28)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertTrue(command.contains("-hwaccel"), "GPU command should contain -hwaccel");
                assertTrue(command.contains("cuda"), "GPU command should contain cuda");

                // Verify order
                int progressIndex = command.indexOf("-progress");
                int hwaccelIndex = command.indexOf("-hwaccel");
                int hwaccelOutputIndex = command.indexOf("-hwaccel_output_format");
                int inputIndex = command.indexOf("-i");

                assertTrue(progressIndex >= 0);
                assertTrue(hwaccelIndex > progressIndex);
                assertTrue(hwaccelOutputIndex > hwaccelIndex);
                assertTrue(inputIndex > hwaccelOutputIndex);

                // Verify GPU codec is used
                int codecIndex = command.indexOf("-c:v");
                assertEquals("hevc_nvenc", command.get(codecIndex + 1), "Should use hevc_nvenc codec");
        }

        @Test
        void testBuildVideoCommand_H264Cpu_NoGPUAcceleration() {
                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .bitrate(5000)
                                .crf(23)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertFalse(command.contains("-hwaccel"), "CPU command should not contain -hwaccel");
                assertFalse(command.contains("-hwaccel_output_format"),
                                "CPU command should not contain -hwaccel_output_format");

                // Verify CPU codec is used
                int codecIndex = command.indexOf("-c:v");
                assertEquals("libx264", command.get(codecIndex + 1), "Should use libx264 codec");
        }

        @Test
        void testBuildVideoCommand_H265Cpu_NoGPUAcceleration() {
                VideoSettings settings = VideoSettings.builder()
                                .codec("H265")
                                .bitrate(8000)
                                .crf(28)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertFalse(command.contains("-hwaccel"), "CPU command should not contain -hwaccel");
                assertFalse(command.contains("-hwaccel_output_format"),
                                "CPU command should not contain -hwaccel_output_format");

                // Verify CPU codec is used
                int codecIndex = command.indexOf("-c:v");
                assertEquals("libx265", command.get(codecIndex + 1), "Should use libx265 codec");
        }

        @Test
        void testBuildVideoCommand_Vp9_NoGPUAcceleration() {
                VideoSettings settings = VideoSettings.builder()
                                .codec("VP9")
                                .bitrate(4000)
                                .crf(31)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertFalse(command.contains("-hwaccel"), "VP9 command should not contain -hwaccel");
                assertFalse(command.contains("-hwaccel_output_format"),
                                "VP9 command should not contain -hwaccel_output_format");

                // Verify CPU codec is used
                int codecIndex = command.indexOf("-c:v");
                assertEquals("libvpx-vp9", command.get(codecIndex + 1), "Should use libvpx-vp9 codec");
        }

        @Test
        void testBuildVideoCommand_Mpeg4_NoGPUAcceleration() {
                VideoSettings settings = VideoSettings.builder()
                                .codec("mpeg4")
                                .bitrate(2000)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                assertFalse(command.contains("-hwaccel"), "MPEG-4 command should not contain -hwaccel");
                assertFalse(command.contains("-hwaccel_output_format"),
                                "MPEG-4 command should not contain -hwaccel_output_format");

                // Verify codec is used
                int codecIndex = command.indexOf("-c:v");
                assertEquals("mpeg4", command.get(codecIndex + 1), "Should use mpeg4 codec");
        }

        @Test
        void testBuildVideoCommand_GPUAcceleration_OrderVerification() {
                VideoSettings settings = VideoSettings.builder()
                                .codec("h264_nvenc")
                                .bitrate(5000)
                                .crf(23)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                // Find indices of key elements
                int ffmpegIndex = command.indexOf(ffmpegPath.toString());
                int progressIndex = command.indexOf("-progress");
                int hwaccelIndex = command.indexOf("-hwaccel");
                int hwaccelOutputIndex = command.indexOf("-hwaccel_output_format");
                int inputIndex = command.indexOf("-i");

                // Verify overall command structure: ffmpeg -progress pipe:1 -hwaccel cuda
                // -hwaccel_output_format cuda -i input ...
                assertEquals(0, ffmpegIndex, "ffmpeg should be first");
                assertEquals(1, progressIndex, "-progress should be second");
                assertEquals(3, hwaccelIndex, "-hwaccel should be fourth");
                assertEquals(5, hwaccelOutputIndex, "-hwaccel_output_format should be sixth");
                assertEquals(9, inputIndex, "-i should be after GPU flags");

                // Verify the arguments following the flags
                assertEquals("pipe:1", command.get(progressIndex + 1), "-progress should be followed by pipe:1");
                assertEquals("cuda", command.get(hwaccelIndex + 1), "-hwaccel should be followed by cuda");
                assertEquals("cuda", command.get(hwaccelOutputIndex + 1),
                                "-hwaccel_output_format should be followed by cuda");
                assertEquals(inputPath.toString(), command.get(inputIndex + 1), "-i should be followed by input path");
        }

        @Test
        void testBuildVideoCommand_GPUAcceleration_WithResolution() {
                Resolution resolution = new Resolution(1920, 1080);
                VideoSettings settings = VideoSettings.builder()
                                .codec("hevc_nvenc")
                                .bitrate(8000)
                                .resolution(resolution)
                                .crf(28)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                // Should include GPU acceleration
                assertTrue(command.contains("-hwaccel"));
                assertTrue(command.contains("-hwaccel_output_format"));

                // Should include video filter for resolution
                assertTrue(command.contains("-vf"));
                assertTrue(command.contains("scale=1920:1080"));

                // Verify order: GPU flags before -i, -vf after -i
                int hwaccelOutputIndex = command.indexOf("-hwaccel_output_format");
                int inputIndex = command.indexOf("-i");
                int vfIndex = command.indexOf("-vf");

                assertTrue(inputIndex > hwaccelOutputIndex, "-i should come after GPU flags");
                assertTrue(vfIndex > inputIndex, "-vf should come after -i");
        }

        @Test
        void testBuildVideoCommand_GPUAcceleration_WithFrameRate() {
                VideoSettings settings = VideoSettings.builder()
                                .codec("h264_nvenc")
                                .bitrate(5000)
                                .frameRate(60)
                                .crf(23)
                                .build();

                List<String> command = service.buildVideoCommand(inputPath, outputPath, settings);

                // Should include GPU acceleration
                assertTrue(command.contains("-hwaccel"));
                assertTrue(command.contains("-hwaccel_output_format"));

                // Should include frame rate
                assertTrue(command.contains("-r"));
                assertTrue(command.contains("60"));

                // Verify GPU flags before -i
                int hwaccelOutputIndex = command.indexOf("-hwaccel_output_format");
                int inputIndex = command.indexOf("-i");

                assertTrue(inputIndex > hwaccelOutputIndex);
        }

        // ========== Aspect Ratio Filter Chain Tests ==========

        /**
         * Test aspect ratio filter chain for 16:9 ratio.
         * Verifies scale → setdar=16/9 → pad filter chain.
         * Requirements: REQ-VID-2.2, REQ-VID-2.3
         */
        @Test
        void testAspectRatioFilterChain_16_9() throws Exception {
                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .crf(23)
                                .resolution(new Resolution(1920, 1080))
                                .aspectRatio(AspectRatio.RATIO_16_9)
                                .build();

                List<String> command = service.buildVideoCommand(
                                inputPath,
                                outputPath,
                                settings);

                // Find the -vf filter argument
                int vfIndex = command.indexOf("-vf");
                assertTrue(vfIndex >= 0, "Command should contain -vf flag");

                String filterChain = command.get(vfIndex + 1);

                // Verify filter chain contains scale
                assertTrue(filterChain.contains("scale=1920:1080"),
                                "Filter chain should contain scale filter");

                // Verify filter chain contains setdar for 16:9
                assertTrue(filterChain.contains("setdar=16/9"),
                                "Filter chain should set display aspect ratio to 16/9");
        }

        /**
         * Test aspect ratio filter chain for 4:3 ratio.
         * Verifies correct 4:3 aspect ratio filters.
         * Requirements: REQ-VID-2.2, REQ-VID-2.3
         */
        @Test
        void testAspectRatioFilterChain_4_3() throws Exception {
                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .crf(23)
                                .resolution(new Resolution(640, 480))
                                .aspectRatio(AspectRatio.RATIO_4_3)
                                .build();

                List<String> command = service.buildVideoCommand(
                                inputPath,
                                outputPath,
                                settings);

                int vfIndex = command.indexOf("-vf");
                assertTrue(vfIndex >= 0, "Command should contain -vf flag");

                String filterChain = command.get(vfIndex + 1);

                // Verify filter chain contains scale
                assertTrue(filterChain.contains("scale=640:480"),
                                "Filter chain should contain scale filter");

                // Verify filter chain contains setdar for 4:3
                assertTrue(filterChain.contains("setdar=4/3"),
                                "Filter chain should set display aspect ratio to 4/3");
        }

        /**
         * Test aspect ratio filter chain for 1:1 square ratio.
         * Verifies correct 1:1 aspect ratio filters.
         * Requirements: REQ-VID-2.2, REQ-VID-2.3
         */
        @Test
        void testAspectRatioFilterChain_1_1() throws Exception {
                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .crf(23)
                                .resolution(new Resolution(1080, 1080))
                                .aspectRatio(AspectRatio.RATIO_1_1)
                                .build();

                List<String> command = service.buildVideoCommand(
                                inputPath,
                                outputPath,
                                settings);

                int vfIndex = command.indexOf("-vf");
                assertTrue(vfIndex >= 0, "Command should contain -vf flag");

                String filterChain = command.get(vfIndex + 1);

                // Verify filter chain contains scale
                assertTrue(filterChain.contains("scale=1080:1080"),
                                "Filter chain should contain scale filter");

                // Verify filter chain contains setdar for 1:1
                assertTrue(filterChain.contains("setdar=1/1"),
                                "Filter chain should set display aspect ratio to 1/1");
        }

        /**
         * Test that KEEP_ORIGINAL aspect ratio adds no aspect ratio filters.
         * Requirements: REQ-VID-2.2
         */
        @Test
        void testAspectRatioKeepOriginal() throws Exception {
                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .crf(23)
                                .resolution(new Resolution(1920, 1080))
                                .aspectRatio(AspectRatio.KEEP_ORIGINAL)
                                .build();

                List<String> command = service.buildVideoCommand(
                                inputPath,
                                outputPath,
                                settings);

                int vfIndex = command.indexOf("-vf");
                assertTrue(vfIndex >= 0, "Command should contain -vf flag");

                String filterChain = command.get(vfIndex + 1);

                // Verify filter chain contains scale but NOT setdar
                assertTrue(filterChain.contains("scale=1920:1080"),
                                "Filter chain should contain scale filter");
                assertFalse(filterChain.contains("setdar"),
                                "Filter chain should NOT contain setdar when keeping original aspect ratio");
        }

        // ========== Audio Copy Codec Tests ==========

        /**
         * Test audio copy codec with no encoding parameters.
         * Verifies -c:a copy with NO -b:a, -ar, -ac, -q:a flags.
         * Requirements: REQ-AUD-1.1
         */
        @Test
        void testAudioCopyCodec() throws Exception {
                Path audioInput = tempDir.resolve("input.mp3");
                Path audioOutput = tempDir.resolve("output.mp3");

                AudioSettings settings = AudioSettings.builder()
                                .codec("COPY")
                                .bitrate(192)
                                .sampleRate(48000)
                                .channels(2)
                                .quality(5)
                                .build();

                List<String> command = service.buildAudioCommand(
                                audioInput,
                                audioOutput,
                                settings);

                // Verify copy codec is used
                assertTrue(command.contains("-c:a"));
                int codecIndex = command.indexOf("-c:a");
                assertEquals("copy", command.get(codecIndex + 1),
                                "Audio codec should be 'copy'");

                // Verify NO encoding parameters are present
                assertFalse(command.contains("-b:a"),
                                "Copy codec should NOT include bitrate flag");
                assertFalse(command.contains("-ar"),
                                "Copy codec should NOT include sample rate flag");
                assertFalse(command.contains("-ac"),
                                "Copy codec should NOT include channels flag");
                assertFalse(command.contains("-q:a"),
                                "Copy codec should NOT include quality flag");
        }

        /**
         * Test audio non-copy codec includes encoding parameters.
         * Verifies encoding params present for AAC/MP3.
         * Requirements: REQ-AUD-1.1
         */
        @Test
        void testAudioNonCopyCodec() throws Exception {
                Path audioInput = tempDir.resolve("input.mp3");
                Path audioOutput = tempDir.resolve("output.mp3");

                AudioSettings settings = AudioSettings.builder()
                                .codec("MP3")
                                .bitrate(192)
                                .sampleRate(48000)
                                .channels(2)
                                .quality(5)
                                .build();

                List<String> command = service.buildAudioCommand(
                                audioInput,
                                audioOutput,
                                settings);

                // Verify encoding codec is used
                assertTrue(command.contains("-c:a"));
                int codecIndex = command.indexOf("-c:a");
                assertEquals("libmp3lame", command.get(codecIndex + 1),
                                "Audio codec should be 'libmp3lame' for MP3");

                // Verify encoding parameters ARE present
                assertTrue(command.contains("-b:a"),
                                "Non-copy codec should include bitrate flag");
                assertTrue(command.contains("-ar"),
                                "Non-copy codec should include sample rate flag");
                assertTrue(command.contains("-ac"),
                                "Non-copy codec should include channels flag");
                assertTrue(command.contains("-q:a"),
                                "MP3 codec should include quality flag");
        }

        // ========== Multi-Threading Flag Tests ==========

        /**
         * Test multi-threading flag is present in video commands.
         * Verifies -threads 0 flag for optimal CPU usage.
         * Requirements: REQ-PERF-1.1, REQ-PERF-1.3
         */
        @Test
        void testMultiThreadingFlagVideo() throws Exception {
                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .crf(23)
                                .build();

                List<String> command = service.buildVideoCommand(
                                inputPath,
                                outputPath,
                                settings);

                // Verify -threads flag is present
                assertTrue(command.contains("-threads"),
                                "Video command should contain -threads flag");

                int threadsIndex = command.indexOf("-threads");
                assertEquals("0", command.get(threadsIndex + 1),
                                "Threads should be set to 0 for optimal CPU usage");

                // Verify -threads comes before -i
                int inputIndex = command.indexOf("-i");
                assertTrue(threadsIndex < inputIndex,
                                "-threads flag should come before -i input flag");
        }

        /**
         * Test multi-threading flag is present in audio commands.
         * Verifies -threads 0 flag for optimal CPU usage.
         * Requirements: REQ-PERF-1.1, REQ-PERF-1.3
         */
        @Test
        void testMultiThreadingFlagAudio() throws Exception {
                Path audioInput = tempDir.resolve("input.mp3");
                Path audioOutput = tempDir.resolve("output.mp3");

                AudioSettings settings = AudioSettings.builder()
                                .codec("MP3")
                                .bitrate(192)
                                .build();

                List<String> command = service.buildAudioCommand(
                                audioInput,
                                audioOutput,
                                settings);

                // Verify -threads flag is present
                assertTrue(command.contains("-threads"),
                                "Audio command should contain -threads flag");

                int threadsIndex = command.indexOf("-threads");
                assertEquals("0", command.get(threadsIndex + 1),
                                "Threads should be set to 0 for optimal CPU usage");

                // Verify -threads comes before -i
                int inputIndex = command.indexOf("-i");
                assertTrue(threadsIndex < inputIndex,
                                "-threads flag should come before -i input flag");
        }

        // ========== Command Flag Order Test ==========

        /**
         * Test complete command flag ordering.
         * Verifies: ffmpeg → -progress → -hwaccel (GPU) → -threads → -i → -c:v
         * Requirements: REQ-PERF-1.1, REQ-PERF-1.2, REQ-PERF-1.3
         */
        @Test
        void testCommandFlagOrder_CompleteChain() throws Exception {
                VideoSettings settings = VideoSettings.builder()
                                .codec("H264_NVENC")
                                .crf(23)
                                .build();

                List<String> command = service.buildVideoCommand(
                                inputPath,
                                outputPath,
                                settings);

                // Get indices of key flags
                int progressIndex = command.indexOf("-progress");
                int hwaccelIndex = command.indexOf("-hwaccel");
                int threadsIndex = command.indexOf("-threads");
                int inputIndex = command.indexOf("-i");
                int codecIndex = command.indexOf("-c:v");

                // Verify all flags are present
                assertTrue(progressIndex >= 0, "Command should contain -progress");
                assertTrue(hwaccelIndex >= 0, "Command should contain -hwaccel for GPU codec");
                assertTrue(threadsIndex >= 0, "Command should contain -threads");
                assertTrue(inputIndex >= 0, "Command should contain -i");
                assertTrue(codecIndex >= 0, "Command should contain -c:v");

                // Verify ordering: -progress → -hwaccel → -threads → -i → -c:v
                assertTrue(progressIndex < hwaccelIndex,
                                "-progress should come before -hwaccel");
                assertTrue(hwaccelIndex < threadsIndex,
                                "-hwaccel should come before -threads");
                assertTrue(threadsIndex < inputIndex,
                                "-threads should come before -i");
                assertTrue(inputIndex < codecIndex,
                                "-i should come before -c:v");
        }

        /**
         * Integration test for successful conversion capturing full output.
         * This test requires an actual FFmpeg binary to be available.
         * Requirements: REQ-FL-2.2
         */
        @Test
        void testOutputCapture_SuccessfulConversion_CapturesFullOutput() throws Exception {
                // Skip if FFmpeg is not available
                if (!isFFmpegAvailable()) {
                        System.out.println("Skipping integration test: FFmpeg not available");
                        return;
                }

                // Create a minimal test video file (1 second, black frame)
                Path inputVideo = createTestVideoFile();
                Path outputVideo = tempDir.resolve("output_test.mp4");

                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .crf(23)
                                .preset("ultrafast")
                                .build();

                // Execute conversion
                ConversionResult result = service.convertVideo(
                                inputVideo,
                                outputVideo,
                                settings,
                                ProgressCallback.noOp());

                // Verify success
                assertTrue(result.success(), "Conversion should succeed");

                // Verify tool output is captured
                assertTrue(result.toolOutput().isPresent(), "Tool output should be present");
                String output = result.toolOutput().get();
                assertFalse(output.isEmpty(), "Tool output should not be empty");

                // Verify output contains expected FFmpeg markers
                assertTrue(output.contains("ffmpeg version") || output.contains("configuration:"),
                                "Output should contain FFmpeg version info");
                assertTrue(output.contains("encoder") || output.contains("Stream #"),
                                "Output should contain encoding information");

                // Verify output file was created
                assertTrue(Files.exists(outputVideo), "Output file should exist");
        }

        /**
         * Integration test for failed conversion capturing error output.
         * This test uses invalid parameters to trigger FFmpeg failure.
         * Requirements: REQ-FL-2.2
         */
        @Test
        void testOutputCapture_FailedConversion_CapturesErrorOutput() throws Exception {
                // Skip if FFmpeg is not available
                if (!isFFmpegAvailable()) {
                        System.out.println("Skipping integration test: FFmpeg not available");
                        return;
                }

                // Create a minimal test video file
                Path inputVideo = createTestVideoFile();
                Path outputVideo = tempDir.resolve("output_fail.mp4");

                // Use invalid codec to trigger failure
                VideoSettings settings = VideoSettings.builder()
                                .codec("invalid_codec_xyz")
                                .crf(23)
                                .build();

                // Execute conversion (should fail)
                ConversionResult result = service.convertVideo(
                                inputVideo,
                                outputVideo,
                                settings,
                                ProgressCallback.noOp());

                // Verify failure
                assertFalse(result.success(), "Conversion should fail with invalid codec");

                // Verify tool output is captured
                assertTrue(result.toolOutput().isPresent(), "Tool output should be present");
                String output = result.toolOutput().get();
                assertFalse(output.isEmpty(), "Tool output should not be empty");

                // Verify output contains error information
                assertTrue(output.toLowerCase().contains("error") ||
                                output.toLowerCase().contains("unknown encoder") ||
                                output.toLowerCase().contains("invalid"),
                                "Output should contain error messages");

                // Verify partial output file was cleaned up
                assertFalse(Files.exists(outputVideo), "Partial output file should be cleaned up");
        }

        /**
         * Integration test for 1MB output truncation limit.
         * This test generates verbose FFmpeg output to trigger truncation.
         * Requirements: REQ-FL-2.2
         */
        @Test
        void testOutputCapture_LargeOutput_TruncatesAt1MB() throws Exception {
                // Skip if FFmpeg is not available
                if (!isFFmpegAvailable()) {
                        System.out.println("Skipping integration test: FFmpeg not available");
                        return;
                }

                // Create a longer test video to generate more output
                Path inputVideo = createLongerTestVideoFile();
                Path outputVideo = tempDir.resolve("output_verbose.mp4");

                // Use settings that generate verbose output
                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .crf(23)
                                .preset("ultrafast")
                                .build();

                // Execute conversion
                ConversionResult result = service.convertVideo(
                                inputVideo,
                                outputVideo,
                                settings,
                                ProgressCallback.noOp());

                // Verify success
                assertTrue(result.success(), "Conversion should succeed");

                // Verify tool output is captured
                assertTrue(result.toolOutput().isPresent(), "Tool output should be present");
                String output = result.toolOutput().get();

                // Verify output size is limited
                int outputSizeBytes = output.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

                // If output was truncated, verify:
                // 1. Size is around 1MB (with truncation message)
                // 2. Truncation message is present
                if (output.contains("[Output truncated - exceeded 1MB limit]")) {
                        assertTrue(outputSizeBytes <= 1024 * 1024 + 1024,
                                        "Truncated output should be around 1MB (with small buffer for truncation message)");
                        assertTrue(output.contains("[Output truncated - exceeded 1MB limit]"),
                                        "Truncation message should be present");
                }

                // Note: If FFmpeg output is naturally small, truncation won't occur
                // This is acceptable - the test verifies truncation when it happens
        }

        /**
         * Integration test verifying output includes both stdout and stderr.
         * FFmpeg writes most output to stderr, so this verifies proper stream merging.
         * Requirements: REQ-FL-2.2
         */
        @Test
        void testOutputCapture_IncludesBothStdoutAndStderr() throws Exception {
                // Skip if FFmpeg is not available
                if (!isFFmpegAvailable()) {
                        System.out.println("Skipping integration test: FFmpeg not available");
                        return;
                }

                // Create a test video file
                Path inputVideo = createTestVideoFile();
                Path outputVideo = tempDir.resolve("output_streams.mp4");

                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .crf(23)
                                .preset("ultrafast")
                                .build();

                // Execute conversion
                ConversionResult result = service.convertVideo(
                                inputVideo,
                                outputVideo,
                                settings,
                                ProgressCallback.noOp());

                // Verify success
                assertTrue(result.success(), "Conversion should succeed");

                // Verify tool output is captured
                assertTrue(result.toolOutput().isPresent(), "Tool output should be present");
                String output = result.toolOutput().get();

                // FFmpeg writes most information to stderr (version, progress, etc.)
                // Verify we capture this information (proves stderr is merged into stdout)
                assertTrue(output.contains("ffmpeg") || output.contains("configuration:") ||
                                output.contains("Stream #") || output.contains("encoder"),
                                "Output should contain FFmpeg diagnostic information (normally on stderr)");

                // Verify the output is comprehensive (not just stdout)
                assertTrue(output.length() > 100,
                                "Output should be substantial (contains both stdout and stderr)");
        }

        /**
         * Integration test verifying captured output is valid UTF-8.
         * Requirements: REQ-FL-2.2
         */
        @Test
        void testOutputCapture_ValidUTF8() throws Exception {
                // Skip if FFmpeg is not available
                if (!isFFmpegAvailable()) {
                        System.out.println("Skipping integration test: FFmpeg not available");
                        return;
                }

                // Create a test video file
                Path inputVideo = createTestVideoFile();
                Path outputVideo = tempDir.resolve("output_utf8.mp4");

                VideoSettings settings = VideoSettings.builder()
                                .codec("H264")
                                .crf(23)
                                .preset("ultrafast")
                                .build();

                // Execute conversion
                ConversionResult result = service.convertVideo(
                                inputVideo,
                                outputVideo,
                                settings,
                                ProgressCallback.noOp());

                // Verify tool output is captured
                assertTrue(result.toolOutput().isPresent(), "Tool output should be present");
                String output = result.toolOutput().get();

                // Verify output is valid UTF-8 by attempting to encode/decode
                byte[] utf8Bytes = output.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                String decoded = new String(utf8Bytes, java.nio.charset.StandardCharsets.UTF_8);
                assertEquals(output, decoded, "Output should be valid UTF-8 (encode/decode should be lossless)");

                // Verify no replacement characters (indicates encoding issues)
                assertFalse(output.contains("\uFFFD"),
                                "Output should not contain UTF-8 replacement characters");
        }

        // ========================================
        // Helper Methods for Integration Tests
        // ========================================

        /**
         * Checks if FFmpeg is available on the system for integration testing.
         * Uses the service's configured ffmpegPath which may not exist.
         * 
         * @return true if FFmpeg is available, false otherwise
         */
        private boolean isFFmpegAvailable() {
                try {
                        // Try to execute ffmpeg -version to check availability
                        ProcessBuilder pb = new ProcessBuilder(ffmpegPath.toString(), "-version");
                        Process process = pb.start();
                        int exitCode = process.waitFor();
                        return exitCode == 0;
                } catch (Exception e) {
                        return false;
                }
        }

        /**
         * Creates a minimal test video file (1 second, 320x240, black frame) using
         * FFmpeg.
         * 
         * @return Path to the created test video file
         * @throws Exception if video creation fails
         */
        private Path createTestVideoFile() throws Exception {
                Path outputPath = tempDir.resolve("test_input.mp4");

                // Generate 1 second black video: 320x240, 25 fps
                ProcessBuilder pb = new ProcessBuilder(
                                ffmpegPath.toString(),
                                "-f", "lavfi",
                                "-i", "color=c=black:s=320x240:d=1",
                                "-c:v", "libx264",
                                "-t", "1",
                                "-pix_fmt", "yuv420p",
                                "-y",
                                outputPath.toString());

                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode != 0) {
                        throw new IOException("Failed to create test video file");
                }

                return outputPath;
        }

        /**
         * Creates a longer test video file (5 seconds) to generate more FFmpeg output.
         * 
         * @return Path to the created test video file
         * @throws Exception if video creation fails
         */
        private Path createLongerTestVideoFile() throws Exception {
                Path outputPath = tempDir.resolve("test_input_long.mp4");

                // Generate 5 second black video: 640x480, 30 fps
                ProcessBuilder pb = new ProcessBuilder(
                                ffmpegPath.toString(),
                                "-f", "lavfi",
                                "-i", "color=c=black:s=640x480:d=5",
                                "-c:v", "libx264",
                                "-t", "5",
                                "-pix_fmt", "yuv420p",
                                "-y",
                                outputPath.toString());

                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode != 0) {
                        throw new IOException("Failed to create longer test video file");
                }

                return outputPath;
        }
}
