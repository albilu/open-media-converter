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
    void updateFileProgress_withValidInputs_shouldNotTouchWidgets() {
        // When
        progressView.updateFileProgress("file1", sampleConversionProgress);

        // Then: per-file tracking is internal only, widgets are driven by
        // updateOverallProgress
        verifyNoInteractions(progressBar, statusLabel, timeRemainingLabel, conversionSpeedLabel);
    }

    @Test
    void updateFileProgress_withNullFileId_shouldDoNothing() {
        // When / Then: no throw, no widget interaction
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> progressView.updateFileProgress(null, sampleConversionProgress));
        verifyNoInteractions(progressBar, statusLabel, timeRemainingLabel, conversionSpeedLabel);
    }

    @Test
    void updateFileProgress_withNullProgress_shouldDoNothing() {
        // When / Then: no throw, no widget interaction
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> progressView.updateFileProgress("file1", null));
        verifyNoInteractions(progressBar, statusLabel, timeRemainingLabel, conversionSpeedLabel);
    }

    @Test
    void clearFileProgress_shouldNotTouchWidgets() {
        // Given
        progressView.updateFileProgress("file1", sampleConversionProgress);

        // When / Then: no throw, no widget interaction
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> progressView.clearFileProgress());
        verifyNoInteractions(progressBar, statusLabel, timeRemainingLabel, conversionSpeedLabel);
    }
}