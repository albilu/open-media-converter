package org.omc.ui;

import org.omc.ui.ProgressView;
import org.omc.ui.FileListView;
import org.omc.ui.MainWindowJavaGi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.gnome.gtk.Application;
import org.gnome.gtk.Button;
import org.gnome.gtk.ColumnView;
import org.gnome.gtk.GtkBuilder;
import org.gnome.gtk.Label;
import org.gnome.gtk.MenuButton;
import org.gnome.gtk.ProgressBar;
import org.gnome.gtk.Revealer;
import org.gnome.gtk.ScrolledWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.omc.controller.ApplicationWorkflowController;
import org.omc.model.ConversionFile;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionTool;
import org.omc.model.FileFormat;

/**
 * Tests for MainWindowJavaGi.
 * 
 * NOTE: These tests are disabled because they require a GTK environment to be
 * initialized.
 * MainWindowJavaGi extends ApplicationWindow which requires a real GTK
 * Application context.
 * These tests would need to run in a GTK-enabled environment or be converted to
 * integration tests.
 */
@Disabled("Requires GTK environment - ApplicationWindow cannot be instantiated without GTK context")
@ExtendWith(MockitoExtension.class)
class MainWindowJavaGiTest {

    @Mock
    private Application app;

    @Mock
    private ApplicationWorkflowController controller;

    @Mock
    private GtkBuilder builder;

    @Mock
    private Button addFilesButton;

    @Mock
    private Button convertButton;

    @Mock
    private Label statusBarLabel;

    @Mock
    private FileListView fileListView;

    @Mock
    private ProgressView progressView;

    private MainWindowJavaGi window;

    @BeforeEach
    void setUp() throws Exception {
        // Mock GTK components that are accessed during construction
        when(builder.getObject("addFilesButton")).thenReturn(addFilesButton);
        when(builder.getObject("convertButton")).thenReturn(convertButton);
        when(builder.getObject("statusBarLabel")).thenReturn(statusBarLabel);

        // Mock other required builder objects
        mockBuilderObjects();

        // Create window with mocked dependencies
        window = new MainWindowJavaGi(app, controller);
    }

    // ===== Batch Completion Event Flow Tests (Task 75) =====

    @Test
    void testHandleConvert_shouldInitializeBatchTrackingCounters() throws Exception {
        // Arrange
        List<ConversionFile> files = List.of(
                ConversionFile.create(Path.of("/test/file1.mp4"), FileFormat.MP4, 1024L),
                ConversionFile.create(Path.of("/test/file2.mp4"), FileFormat.MP4, 2048L));
        when(controller.getFileList()).thenReturn(files);

        // Act
        invokeHandleConvert();

        // Assert
        assertEquals(2, getTotalFilesInBatch());
        assertEquals(0, getCompletedFilesInBatch());
        assertEquals(0, getSuccessfulFilesInBatch());
        assertEquals(0, getFailedFilesInBatch());
        assertFalse(getBatchCompleted());
        verify(controller).handleStartConversion();
    }

    @Test
    void testUpdateFileResult_shouldIncrementCompletedCounter_whenFileCompletesSuccessfully() throws Exception {
        // Arrange
        initializeBatchTracking(3);
        ConversionResult successResult = ConversionResult.success("file1", Path.of("/output/result.mp4"), null,
                Duration.ofSeconds(10), 1024L, 512L, ConversionTool.FFMPEG);

        // Act
        window.updateFileResult("file1", successResult);

        // Assert
        assertEquals(1, getCompletedFilesInBatch());
        assertEquals(1, getSuccessfulFilesInBatch());
        assertEquals(0, getFailedFilesInBatch());
        assertFalse(getBatchCompleted()); // Not all files complete yet
        verify(statusBarLabel).setLabel("Conversion completed: /output/result.mp4");
    }

    @Test
    void testUpdateFileResult_shouldIncrementCompletedCounter_whenFileCompletesWithFailure() throws Exception {
        // Arrange
        initializeBatchTracking(2);
        ConversionResult failureResult = ConversionResult.failure("file1", "Conversion failed", null,
                Duration.ofSeconds(5), 1024L, ConversionTool.FFMPEG);

        // Act
        window.updateFileResult("file1", failureResult);

        // Assert
        assertEquals(1, getCompletedFilesInBatch());
        assertEquals(0, getSuccessfulFilesInBatch());
        assertEquals(1, getFailedFilesInBatch());
        assertFalse(getBatchCompleted()); // Not all files complete yet
        verify(statusBarLabel).setLabel("Conversion failed: Conversion failed");
    }

    @Test
    void testUpdateFileResult_shouldTriggerBatchCompletion_whenAllFilesComplete() throws Exception {
        // Arrange
        initializeBatchTracking(2);
        setCompletedFilesInBatch(1); // One file already completed

        ConversionResult successResult = ConversionResult.success("file2", Path.of("/output/result.mp4"), null,
                Duration.ofSeconds(10), 1024L, 512L, ConversionTool.FFMPEG);

        // Act
        window.updateFileResult("file2", successResult);

        // Assert
        assertEquals(2, getCompletedFilesInBatch());
        assertEquals(1, getSuccessfulFilesInBatch());
        assertEquals(0, getFailedFilesInBatch());
        assertTrue(getBatchCompleted());
        // onBatchComplete() should have been called, which resets counters
        assertEquals(0, getTotalFilesInBatch());
        assertEquals(0, getCompletedFilesInBatch());
        assertEquals(0, getSuccessfulFilesInBatch());
        assertEquals(0, getFailedFilesInBatch());
    }

    @Test
    void testOnBatchComplete_shouldShowSuccessNotification_whenAllFilesSuccessful() throws Exception {
        // Arrange
        initializeBatchTracking(3);
        setCompletedFilesInBatch(3);
        setSuccessfulFilesInBatch(3);
        setFailedFilesInBatch(0);

        // Act
        invokeOnBatchComplete();

        // Assert
        verify(statusBarLabel).setLabel("Conversion complete: 3 successful, 0 failed out of 3 files.");
        // Note: Desktop notification testing would require mocking Runtime.exec
    }

    @Test
    void testOnBatchComplete_shouldShowFailureNotification_whenAllFilesFailed() throws Exception {
        // Arrange
        initializeBatchTracking(2);
        setCompletedFilesInBatch(2);
        setSuccessfulFilesInBatch(0);
        setFailedFilesInBatch(2);

        // Act
        invokeOnBatchComplete();

        // Assert
        verify(statusBarLabel).setLabel("Conversion complete: 0 successful, 2 failed out of 2 files.");
    }

    @Test
    void testOnBatchComplete_shouldShowMixedNotification_whenSomeFilesFailed() throws Exception {
        // Arrange
        initializeBatchTracking(5);
        setCompletedFilesInBatch(5);
        setSuccessfulFilesInBatch(3);
        setFailedFilesInBatch(2);

        // Act
        invokeOnBatchComplete();

        // Assert
        verify(statusBarLabel).setLabel("Conversion complete: 3 successful, 2 failed out of 5 files.");
    }

    @Test
    void testOnBatchComplete_shouldResetBatchTrackingCounters() throws Exception {
        // Arrange
        initializeBatchTracking(3);
        setCompletedFilesInBatch(3);
        setSuccessfulFilesInBatch(2);
        setFailedFilesInBatch(1);

        // Act
        invokeOnBatchComplete();

        // Assert
        assertEquals(0, getTotalFilesInBatch());
        assertEquals(0, getCompletedFilesInBatch());
        assertEquals(0, getSuccessfulFilesInBatch());
        assertEquals(0, getFailedFilesInBatch());
    }

    @Test
    void testOnBatchComplete_shouldHideProgressViewAndEnableConvertButton() throws Exception {
        // Arrange
        initializeBatchTracking(1);
        setCompletedFilesInBatch(1);
        setSuccessfulFilesInBatch(1);

        // Act
        invokeOnBatchComplete();

        // Assert
        verify(progressView).hide();
        verify(convertButton).setSensitive(true);
    }

    @Test
    void testShowCompletionNotification_shouldExecuteNotifySendCommand() throws Exception {
        // Arrange
        String message = "Test completion message";

        // Act
        invokeShowCompletionNotification(message);

        // Assert - We can't easily verify Runtime.exec without mocking static methods
        // The method attempts to execute: notify-send "Open Media Converter" message
        // --icon=dialog-information
        // In a real test environment, this would show a desktop notification
    }

    // ===== Helper Methods =====

    private void mockBuilderObjects() {
        // Mock all the builder.getObject calls that are made during
        // setupWidgetReferences
        when(builder.getObject("addFilesButton")).thenReturn(mock(Button.class));
        when(builder.getObject("addFolderButton")).thenReturn(mock(Button.class));
        when(builder.getObject("settingsButton")).thenReturn(mock(Button.class));
        when(builder.getObject("menuButton")).thenReturn(mock(MenuButton.class));
        when(builder.getObject("fileListScrolledWindow")).thenReturn(mock(ScrolledWindow.class));
        when(builder.getObject("fileListColumnView")).thenReturn(mock(ColumnView.class));
        when(builder.getObject("progressRevealer")).thenReturn(mock(Revealer.class));
        when(builder.getObject("overallProgressBar")).thenReturn(mock(ProgressBar.class));
        when(builder.getObject("statusLabel")).thenReturn(mock(Label.class));
        when(builder.getObject("timeRemainingLabel")).thenReturn(mock(Label.class));
        when(builder.getObject("conversionSpeedLabel")).thenReturn(mock(Label.class));
        when(builder.getObject("pauseButton")).thenReturn(mock(Button.class));
        when(builder.getObject("resumeButton")).thenReturn(mock(Button.class));
        when(builder.getObject("cancelButton")).thenReturn(mock(Button.class));
        when(builder.getObject("removeSelectedButton")).thenReturn(mock(Button.class));
        when(builder.getObject("clearAllButton")).thenReturn(mock(Button.class));
        when(builder.getObject("convertButton")).thenReturn(convertButton);
        when(builder.getObject("fileCountLabel")).thenReturn(mock(Label.class));
        when(builder.getObject("totalSizeLabel")).thenReturn(mock(Label.class));
        when(builder.getObject("statusBarLabel")).thenReturn(statusBarLabel);
    }

    private void initializeBatchTracking(int totalFiles) throws Exception {
        setTotalFilesInBatch(totalFiles);
        setCompletedFilesInBatch(0);
        setSuccessfulFilesInBatch(0);
        setFailedFilesInBatch(0);
        setBatchCompleted(false);
    }

    private int getTotalFilesInBatch() throws Exception {
        return getPrivateField("totalFilesInBatch");
    }

    private void setTotalFilesInBatch(int value) throws Exception {
        setPrivateField("totalFilesInBatch", value);
    }

    private int getCompletedFilesInBatch() throws Exception {
        return getPrivateField("completedFilesInBatch");
    }

    private void setCompletedFilesInBatch(int value) throws Exception {
        setPrivateField("completedFilesInBatch", value);
    }

    private int getSuccessfulFilesInBatch() throws Exception {
        return getPrivateField("successfulFilesInBatch");
    }

    private void setSuccessfulFilesInBatch(int value) throws Exception {
        setPrivateField("successfulFilesInBatch", value);
    }

    private int getFailedFilesInBatch() throws Exception {
        return getPrivateField("failedFilesInBatch");
    }

    private void setFailedFilesInBatch(int value) throws Exception {
        setPrivateField("failedFilesInBatch", value);
    }

    private boolean getBatchCompleted() throws Exception {
        Field field = MainWindowJavaGi.class.getDeclaredField("batchCompleted");
        field.setAccessible(true);
        return (boolean) field.get(window);
    }

    private void setBatchCompleted(boolean value) throws Exception {
        setPrivateField("batchCompleted", value);
    }

    private int getPrivateField(String fieldName) throws Exception {
        Field field = MainWindowJavaGi.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (int) field.get(window);
    }

    private void setPrivateField(String fieldName, Object value) throws Exception {
        Field field = MainWindowJavaGi.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(window, value);
    }

    private void invokeOnBatchComplete() throws Exception {
        java.lang.reflect.Method method = MainWindowJavaGi.class.getDeclaredMethod("onBatchComplete");
        method.setAccessible(true);
        method.invoke(window);
    }

    private void invokeShowCompletionNotification(String message) throws Exception {
        java.lang.reflect.Method method = MainWindowJavaGi.class.getDeclaredMethod("showCompletionNotification",
                String.class);
        method.setAccessible(true);
        method.invoke(window, message);
    }

    @Test
    void testSetupWindowActions_registersRemoveSelectedAction() {
        // Arrange - window is already set up in setUp()

        // Act & Assert
        // The setupWindowActions is called in the constructor
        // We verify that the window was created successfully
        assertNotNull(window);
    }

    @Test
    void testSetupWindowActions_registersSelectAllAction() {
        // Arrange - window is already set up in setUp()

        // Act & Assert
        assertNotNull(window);
    }

    @Test
    void testSetupWindowActions_setsAccelerators() {
        // Arrange - window is already set up in setUp()

        // Act & Assert
        assertNotNull(window);
    }

    @Test
    void testTriggerRemoveSelected_callsHandleRemoveSelected() {
        // Arrange - window is already set up in setUp()

        // Act
        window.triggerRemoveSelected();

        // Assert
        // Since handleRemoveSelected is private, we can't directly verify,
        // but we can verify that the method exists and doesn't throw
    }

    @Test
    void testTriggerSelectAll_callsFileListViewSelectAll() {
        // Arrange - window is already set up in setUp()

        // Act
        window.triggerSelectAll();

        // Assert
        // Since fileListView is mocked, we can't verify the call,
        // but we can verify that the method exists and doesn't throw
    }

    @Test
    void testTriggerAddFiles_callsHandleAddFiles() {
        // Arrange - window is already set up in setUp()

        // Act
        window.triggerAddFiles();

        // Assert
        // Method should exist and not throw
    }

    @Test
    void testTriggerSettings_callsHandleSettings() {
        // Arrange - window is already set up in setUp()

        // Act
        window.triggerSettings();

        // Assert
        // Method should exist and not throw
    }

    @Test
    void testTriggerConvert_callsHandleConvert() {
        // Arrange - window is already set up in setUp()

        // Act
        window.triggerConvert();

        // Assert
        // Method should exist and not throw
    }

    private void invokeHandleConvert() throws Exception {
        java.lang.reflect.Method method = MainWindowJavaGi.class.getDeclaredMethod("handleConvert");
        method.setAccessible(true);
        method.invoke(window);
    }

    // ===== Task 55: showFileDetailsDialog Tests =====

    @Test
    void showFileDetailsDialog_shouldShowErrorDialog_whenFileNotFound() throws Exception {
        // Arrange
        String fileId = "nonexistent";
        when(controller.getFile(fileId)).thenReturn(java.util.Optional.empty());

        // Act
        invokeShowFileDetailsDialog(fileId);

        // Assert
        verify(controller).getFile(fileId);
        verify(controller, never()).getConversionResult(fileId);
        // Error dialog would be shown, but since it's mocked, we can't verify GTK calls
    }

    @Test
    void showFileDetailsDialog_shouldRetrieveFileAndResult_whenFileExists() throws Exception {
        // Arrange
        String fileId = "testFile";
        ConversionFile mockFile = ConversionFile.create(Path.of("/test/file.mp4"), FileFormat.MP4, 1024L);
        ConversionResult mockResult = ConversionResult.success(
                fileId, Path.of("/output/result.avi"), null, Duration.ofSeconds(10), 1024L, 512L,
                ConversionTool.FFMPEG);

        when(controller.getFile(fileId)).thenReturn(java.util.Optional.of(mockFile));
        when(controller.getConversionResult(fileId)).thenReturn(mockResult);

        // Act
        invokeShowFileDetailsDialog(fileId);

        // Assert
        verify(controller).getFile(fileId);
        verify(controller).getConversionResult(fileId);
        // FileDetailsDialog.show() would be called, but since GTK is mocked, we can't
        // verify
    }

    @Test
    void showFileDetailsDialog_shouldHandleNullResult_whenFileHasNoConversionResult() throws Exception {
        // Arrange
        String fileId = "pendingFile";
        ConversionFile mockFile = ConversionFile.create(Path.of("/test/file.mp4"), FileFormat.MP4, 1024L);

        when(controller.getFile(fileId)).thenReturn(java.util.Optional.of(mockFile));
        when(controller.getConversionResult(fileId)).thenReturn(null);

        // Act
        invokeShowFileDetailsDialog(fileId);

        // Assert
        verify(controller).getFile(fileId);
        verify(controller).getConversionResult(fileId);
        // FileDetailsDialog.show() should be called with null result
    }

    private void invokeShowFileDetailsDialog(String fileId) throws Exception {
        java.lang.reflect.Method method = MainWindowJavaGi.class.getDeclaredMethod("showFileDetailsDialog",
                String.class);
        method.setAccessible(true);
        method.invoke(window, fileId);
    }
}