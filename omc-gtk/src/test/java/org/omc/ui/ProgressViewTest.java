package org.omc.ui;

import org.omc.ui.ProgressView;
import org.omc.model.BatchProgress;
import org.omc.model.ConversionProgress;
import org.gnome.gtk.Label;
import org.gnome.gtk.ProgressBar;
import org.gnome.gtk.Revealer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgressViewTest {

    @Mock
    private Revealer revealer;

    @Mock
    private ProgressBar progressBar;

    @Mock
    private Label statusLabel;

    @Mock
    private Label timeRemainingLabel;

    @Mock
    private Label conversionSpeedLabel;

    private ProgressView progressView;

    private BatchProgress sampleBatchProgress;
    private ConversionProgress sampleConversionProgress;

    @BeforeEach
    void setUp() {
        progressView = new ProgressView(revealer, progressBar, statusLabel, timeRemainingLabel, conversionSpeedLabel);

        // Create sample BatchProgress
        sampleBatchProgress = BatchProgress.update(
                10, // totalFiles
                3, // completedFiles
                1, // failedFiles
                2, // inProgressFiles
                1000000L, // totalBytes
                400000L, // processedBytes
                Instant.now().minusSeconds(60) // startTime
        );

        // Create sample ConversionProgress
        sampleConversionProgress = ConversionProgress.initial("file1", 100000L).update(50000L);
    }

    @Test
    void constructor_shouldInitializeFields() {
        // Given - mocks are injected
        // When - constructor called via @InjectMocks
        // Then - verify fields are set (implicitly via mocks)
        assertNotNull(progressView);
    }

    @Test
    void show_shouldSetRevealerToTrue() {
        // When
        progressView.show();

        // Then
        verify(revealer).setRevealChild(true);
    }

    @Test
    void hide_shouldSetRevealerToFalse() {
        // When
        progressView.hide();

        // Then
        verify(revealer).setRevealChild(false);
    }

    @Test
    void updateOverallProgress_withValidBatchProgress_shouldUpdateUI() {
        // When
        progressView.updateOverallProgress(sampleBatchProgress);

        // Then
        verify(progressBar).setFraction(sampleBatchProgress.overallPercentage() / 100.0);
        verify(progressBar).setText(String.format("%d%%", sampleBatchProgress.overallPercentage()));
        verify(statusLabel).setLabel(sampleBatchProgress.formatStatusMessage());
        verify(timeRemainingLabel).setLabel("Time remaining: " + sampleBatchProgress.formatEta());
        verify(conversionSpeedLabel).setLabel("Speed: " + sampleBatchProgress.formatSpeed());
    }

    @Test
    void updateOverallProgress_withNullBatchProgress_shouldDoNothing() {
        // When
        progressView.updateOverallProgress(null);

        // Then
        verifyNoInteractions(progressBar, statusLabel, timeRemainingLabel, conversionSpeedLabel);
    }

    @Test
    void updateFileProgress_withValidInputs_shouldStoreProgress() {
        // When
        progressView.updateFileProgress("file1", sampleConversionProgress);

        // Then
        ConversionProgress retrieved = progressView.getFileProgress("file1");
        assertEquals(sampleConversionProgress, retrieved);
    }

    @Test
    void updateFileProgress_withNullFileId_shouldDoNothing() {
        // When
        progressView.updateFileProgress(null, sampleConversionProgress);

        // Then
        assertNull(progressView.getFileProgress("file1"));
    }

    @Test
    void updateFileProgress_withNullProgress_shouldDoNothing() {
        // When
        progressView.updateFileProgress("file1", null);

        // Then
        assertNull(progressView.getFileProgress("file1"));
    }

    @Test
    void clearFileProgress_shouldClearMap() {
        // Given
        progressView.updateFileProgress("file1", sampleConversionProgress);

        // When
        progressView.clearFileProgress();

        // Then
        assertNull(progressView.getFileProgress("file1"));
    }

    @Test
    void getFileProgress_withNonExistentFileId_shouldReturnNull() {
        // When
        ConversionProgress result = progressView.getFileProgress("nonexistent");

        // Then
        assertNull(result);
    }

    @Test
    void updateProgress_withValidInputs_shouldUpdateUI() {
        // Given
        int currentFile = 2;
        int totalFiles = 5;
        double overallProgress = 0.4;
        long timeRemainingSeconds = 120;
        String speed = "1.5 MB/s";

        // When
        progressView.updateProgress(currentFile, totalFiles, overallProgress, timeRemainingSeconds, speed);

        // Then
        verify(progressBar).setFraction(overallProgress);
        verify(progressBar).setText("40%");
        verify(statusLabel).setLabel("Converting 2 of 5 files...");
        verify(timeRemainingLabel).setLabel("Time remaining: 2m 0s");
        verify(conversionSpeedLabel).setLabel("Speed: 1.5 MB/s");
    }

    @Test
    void updateProgress_withZeroProgress_shouldUpdateUI() {
        // When
        progressView.updateProgress(1, 5, 0.0, 0, null);

        // Then
        verify(progressBar).setFraction(0.0);
        verify(progressBar).setText("0%");
        verify(statusLabel).setLabel("Converting 1 of 5 files...");
        verify(timeRemainingLabel).setLabel("Time remaining: 0s");
        verify(conversionSpeedLabel).setLabel("Speed: --");
    }

    @Test
    void updateProgress_withFullProgress_shouldUpdateUI() {
        // When
        progressView.updateProgress(5, 5, 1.0, 0, "2.0 MB/s");

        // Then
        verify(progressBar).setFraction(1.0);
        verify(progressBar).setText("100%");
        verify(statusLabel).setLabel("Converting 5 of 5 files...");
        verify(timeRemainingLabel).setLabel("Time remaining: 0s");
        verify(conversionSpeedLabel).setLabel("Speed: 2.0 MB/s");
    }

    @Test
    void updateProgress_withNegativeTimeRemaining_shouldShowDash() {
        // When
        progressView.updateProgress(1, 1, 0.5, -10, "fast");

        // Then
        verify(timeRemainingLabel).setLabel("Time remaining: --");
    }

    @Test
    void updateProgress_withLargeTimeRemaining_shouldFormatHours() {
        // When
        progressView.updateProgress(1, 1, 0.5, 7265, "slow"); // 2h 1m 5s

        // Then
        verify(timeRemainingLabel).setLabel("Time remaining: 2h 1m");
    }

    @Test
    void updateProgress_withNullSpeed_shouldShowDash() {
        // When
        progressView.updateProgress(1, 1, 0.5, 60, null);

        // Then
        verify(conversionSpeedLabel).setLabel("Speed: --");
    }
}