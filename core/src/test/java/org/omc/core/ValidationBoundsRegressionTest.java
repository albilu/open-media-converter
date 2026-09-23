package org.omc.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omc.model.AudioSettings;
import org.omc.model.ConversionSettings;
import org.omc.model.ImageSettings;
import org.omc.model.ValidationResult;
import org.omc.model.VideoSettings;
import org.omc.service.FileHandler;
import org.omc.util.JsonUtils;

/**
 * Regression tests for the split-brain validation defect: model-level
 * {@code isValid()} bounds and {@link ValidationEngine} bounds disagreed, so
 * JSON-deserialized settings (which bypass Builder validation) could be saved
 * by the engine yet rejected at conversion start and wiped on next launch.
 * The engine must enforce exactly the model bounds, including the image
 * quality -1 (lossless) special case.
 */
class ValidationBoundsRegressionTest {

    private ValidationEngine validationEngine;

    @TempDir
    Path tempDir;

    private Path outputDir;

    @BeforeEach
    void setUp() throws Exception {
        validationEngine = new ValidationEngine(mock(FileHandler.class));
        outputDir = Files.createDirectory(tempDir.resolve("output"));
    }

    private ConversionSettings settingsWithVideo(VideoSettings video) {
        return ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .videoSettings(video)
                .build();
    }

    private ConversionSettings settingsWithAudio(AudioSettings audio) {
        return ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .audioSettings(audio)
                .build();
    }

    private ConversionSettings settingsWithImage(ImageSettings image) {
        return ConversionSettings.builder()
                .outputDirectory(outputDir)
                .parallelConversions(2)
                .imageSettings(image)
                .build();
    }

    @Test
    void videoBitrateBelowModelMinimum_isRejected() throws Exception {
        VideoSettings video = JsonUtils.getObjectMapper().readValue(
                "{\"codec\":\"libx264\",\"bitrate\":200,\"frameRate\":30,\"crf\":23,\"outputFormat\":\"MP4\"}",
                VideoSettings.class);

        ValidationResult result = validationEngine.validateSettings(settingsWithVideo(video));

        assertFalse(video.isValid());
        assertTrue(result.isFailure(), "engine must reject what the model rejects");
    }

    @Test
    void videoFrameRateAboveModelMaximum_isRejected() throws Exception {
        VideoSettings video = JsonUtils.getObjectMapper().readValue(
                "{\"codec\":\"libx264\",\"bitrate\":5000,\"frameRate\":200,\"crf\":23,\"outputFormat\":\"MP4\"}",
                VideoSettings.class);

        ValidationResult result = validationEngine.validateSettings(settingsWithVideo(video));

        assertFalse(video.isValid());
        assertTrue(result.isFailure(), "engine must reject what the model rejects");
    }

    @Test
    void audioBitrateAboveModelMaximum_isRejected() throws Exception {
        AudioSettings audio = JsonUtils.getObjectMapper().readValue(
                "{\"codec\":\"libmp3lame\",\"bitrate\":500,\"sampleRate\":44100,\"channels\":2,\"quality\":5,\"outputFormat\":\"MP3\"}",
                AudioSettings.class);

        ValidationResult result = validationEngine.validateSettings(settingsWithAudio(audio));

        assertFalse(audio.isValid());
        assertTrue(result.isFailure(), "engine must reject what the model rejects");
    }

    @Test
    void audioSampleRateOutsideModelSet_isRejected() throws Exception {
        AudioSettings audio = JsonUtils.getObjectMapper().readValue(
                "{\"codec\":\"libmp3lame\",\"bitrate\":192,\"sampleRate\":12345,\"channels\":2,\"quality\":5,\"outputFormat\":\"MP3\"}",
                AudioSettings.class);

        ValidationResult result = validationEngine.validateSettings(settingsWithAudio(audio));

        assertFalse(audio.isValid());
        assertTrue(result.isFailure(), "engine must reject what the model rejects");
    }

    @Test
    void audioChannelsOutsideModelSet_isRejected() throws Exception {
        AudioSettings audio = JsonUtils.getObjectMapper().readValue(
                "{\"codec\":\"libmp3lame\",\"bitrate\":192,\"sampleRate\":44100,\"channels\":4,\"quality\":5,\"outputFormat\":\"MP3\"}",
                AudioSettings.class);

        ValidationResult result = validationEngine.validateSettings(settingsWithAudio(audio));

        assertFalse(audio.isValid());
        assertTrue(result.isFailure(), "engine must reject what the model rejects");
    }

    @Test
    void imageQualityLossless_isAccepted() throws Exception {
        ImageSettings image = JsonUtils.getObjectMapper().readValue(
                "{\"quality\":-1,\"outputFormat\":\"PNG\"}",
                ImageSettings.class);

        ValidationResult result = validationEngine.validateSettings(settingsWithImage(image));

        assertTrue(image.isValid());
        assertTrue(result.isSuccess(), "lossless quality -1 must be accepted, got: " + result.getErrors());
    }

    @Test
    void imageQualityLossless_doesNotTriggerLowQualityWarning() throws Exception {
        ImageSettings image = JsonUtils.getObjectMapper().readValue(
                "{\"quality\":-1,\"outputFormat\":\"PNG\"}",
                ImageSettings.class);

        ValidationResult result = validationEngine.validateSettings(settingsWithImage(image));

        assertTrue(result.getWarnings().stream().noneMatch(w -> w.contains("Low image quality")),
                "lossless must not warn about low quality: " + result.getWarnings());
    }

    @Test
    void modelBoundsAreTheSingleSourceOfTruth() {
        assertTrue(VideoSettings.MIN_BITRATE > 0);
        assertTrue(VideoSettings.MAX_BITRATE > VideoSettings.MIN_BITRATE);
        assertTrue(AudioSettings.MIN_BITRATE > 0);
        assertTrue(AudioSettings.MAX_BITRATE > AudioSettings.MIN_BITRATE);
        assertTrue(AudioSettings.isValidSampleRate(44100));
        assertFalse(AudioSettings.isValidSampleRate(12345));
        assertTrue(AudioSettings.isValidChannels(-1));
        assertTrue(AudioSettings.isValidChannels(6));
        assertFalse(AudioSettings.isValidChannels(4));
        assertTrue(ImageSettings.isValidQuality(-1));
        assertTrue(ImageSettings.isValidQuality(0));
        assertTrue(ImageSettings.isValidQuality(100));
        assertFalse(ImageSettings.isValidQuality(101));
    }
}
