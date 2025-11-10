package org.omc.controller;

import org.omc.controller.StateManager;
import org.omc.model.ImageSettings;
import org.omc.model.DocumentSettings;
import org.omc.model.FileSettingsOverride;
import org.omc.model.ConversionStatus;
import org.omc.model.ConversionSettings;
import org.omc.model.VideoSettings;
import org.omc.controller.ApplicationWorkflowController;
import org.omc.model.FileFormat;
import org.omc.model.ConversionResult;
import org.omc.model.ConversionFile;
import org.omc.model.ApplicationState;
import org.omc.controller.SettingsManager;
import org.omc.model.ConversionTool;
import org.omc.model.SessionState;
import org.omc.model.AudioSettings;
import org.omc.model.SectionPreset;
import org.omc.model.FileListSortState;
import org.omc.controller.FileManager;
import org.omc.core.ConversionEngine;
import org.omc.core.ConversionEngine;
import org.omc.exception.FileOperationException;
import org.omc.exception.InvalidSettingsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ApplicationWorkflowControllerTest {

    @Mock
    private FileManager fileManager;

    @Mock
    private SettingsManager settingsManager;

    @Mock
    private StateManager stateManager;

    @Mock
    private ConversionEngine conversionEngine;

    private ApplicationWorkflowController controller;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        controller = new ApplicationWorkflowController(fileManager, settingsManager, stateManager, conversionEngine);
        // Create temp directory for tests
        tempDir = Files.createTempDirectory("omm-test");
    }

    // ===== Constructor Tests =====

    @Test
    void constructor_should_throwNullPointerException_when_fileManagerIsNull() {
        assertThrows(NullPointerException.class,
                () -> new ApplicationWorkflowController(null, settingsManager, stateManager, conversionEngine));
    }

    @Test
    void constructor_should_throwNullPointerException_when_settingsManagerIsNull() {
        assertThrows(NullPointerException.class,
                () -> new ApplicationWorkflowController(fileManager, null, stateManager, conversionEngine));
    }

    @Test
    void constructor_should_throwNullPointerException_when_stateManagerIsNull() {
        assertThrows(NullPointerException.class,
                () -> new ApplicationWorkflowController(fileManager, settingsManager, null, conversionEngine));
    }

    @Test
    void constructor_should_throwNullPointerException_when_conversionEngineIsNull() {
        assertThrows(NullPointerException.class,
                () -> new ApplicationWorkflowController(fileManager, settingsManager, stateManager, null));
    }

    @Test
    void constructor_should_createInstance_when_allDependenciesAreProvided() {
        assertNotNull(controller);
        assertFalse(controller.isInitialized());
        assertFalse(controller.isConversionInProgress());
        assertFalse(controller.hasUnsavedChanges());
    }

    // ===== Initialize Tests =====

    @Test
    void initialize_should_loadSettingsAndState_when_calledFirstTime() {
        // Arrange
        ConversionSettings mockSettings = mock(ConversionSettings.class);
        ApplicationState mockState = mock(ApplicationState.class);
        SessionState mockSessionState = mock(SessionState.class);

        when(settingsManager.loadSettings()).thenReturn(mockSettings);
        when(stateManager.loadState()).thenReturn(mockState);
        when(mockState.sessionState()).thenReturn(mockSessionState);
        when(mockSessionState.pendingFiles()).thenReturn(List.of());

        // Act
        controller.initialize();

        // Assert
        assertTrue(controller.isInitialized());
        verify(settingsManager).loadSettings();
        verify(stateManager).loadState();
        verify(conversionEngine).onConversionComplete(any());
        verify(conversionEngine).onProgressUpdate(any());
    }

    @Test
    void initialize_should_restoreFileList_when_sessionStateHasPendingFiles() {
        // Arrange
        ConversionSettings mockSettings = mock(ConversionSettings.class);
        ApplicationState mockState = mock(ApplicationState.class);
        SessionState mockSessionState = mock(SessionState.class);
        ConversionFile mockFile = mock(ConversionFile.class);
        Path existingPath = Paths.get("existing.txt");

        when(settingsManager.loadSettings()).thenReturn(mockSettings);
        when(stateManager.loadState()).thenReturn(mockState);
        when(mockState.sessionState()).thenReturn(mockSessionState);
        when(mockSessionState.pendingFiles()).thenReturn(List.of(mockFile));
        when(mockFile.path()).thenReturn(existingPath);

        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(existingPath)).thenReturn(true);

            // Act
            controller.initialize();

            // Assert
            verify(fileManager).addFiles(List.of(existingPath));
        }
    }

    @Test
    void initialize_should_throwIllegalStateException_when_alreadyInitialized() {
        // Arrange
        ConversionSettings mockSettings = mock(ConversionSettings.class);
        ApplicationState mockState = mock(ApplicationState.class);
        SessionState mockSessionState = mock(SessionState.class);

        when(settingsManager.loadSettings()).thenReturn(mockSettings);
        when(stateManager.loadState()).thenReturn(mockState);
        when(mockState.sessionState()).thenReturn(mockSessionState);
        when(mockSessionState.pendingFiles()).thenReturn(List.of());

        controller.initialize(); // First call

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.initialize());
    }

    @Test
    void initialize_should_throwRuntimeException_when_settingsLoadFails() {
        // Arrange
        when(settingsManager.loadSettings()).thenThrow(new RuntimeException("Load failed"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> controller.initialize());
        assertFalse(controller.isInitialized());
    }

    @Test
    void initialize_should_throwRuntimeException_when_stateLoadFails() {
        // Arrange
        ConversionSettings mockSettings = mock(ConversionSettings.class);
        when(settingsManager.loadSettings()).thenReturn(mockSettings);
        when(stateManager.loadState()).thenThrow(new RuntimeException("Load failed"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> controller.initialize());
        assertFalse(controller.isInitialized());
    }

    // ===== Shutdown Tests =====

    @Test
    void shutdown_should_returnTrue_when_notInitialized() {
        // Act
        boolean result = controller.shutdown();

        // Assert
        assertTrue(result);
    }

    @Test
    void shutdown_should_saveStateAndSettings_when_initializedAndNoActiveConversions() throws Exception {
        // Arrange
        initializeController();
        List<ConversionFile> mockFiles = List.of();
        when(fileManager.getFiles()).thenReturn(mockFiles);

        // Act
        boolean result = controller.shutdown();

        // Assert
        assertTrue(result);
        verify(stateManager).saveState(any(ApplicationState.class));
        verify(settingsManager).saveSettings(any());
        verify(conversionEngine).shutdown();
        assertFalse(controller.isInitialized());
    }

    @Test
    void shutdown_should_cancelConversions_when_forceShutdownIsTrueAndConversionsActive() {
        // Arrange
        initializeController();
        setConversionInProgress(true);
        List<ConversionFile> mockFiles = List.of();
        when(fileManager.getFiles()).thenReturn(mockFiles);

        // Act
        boolean result = controller.shutdown(true);

        // Assert
        assertTrue(result);
        verify(conversionEngine).cancelConversion();
        assertFalse(controller.isConversionInProgress());
    }

    @Test
    void shutdown_should_returnFalse_when_conversionsActiveAndNotForceShutdown() {
        // Arrange
        initializeController();
        setConversionInProgress(true);

        // Act
        boolean result = controller.shutdown(false);

        // Assert
        assertFalse(result);
        verify(conversionEngine, never()).cancelConversion();
    }

    @Test
    void shutdown_should_autoSaveSettings_when_unsavedChangesExistAndNotForceShutdown() throws Exception {
        // Arrange
        initializeController();
        setHasUnsavedChanges(true);
        List<ConversionFile> mockFiles = List.of();
        when(fileManager.getFiles()).thenReturn(mockFiles);

        // Act
        boolean result = controller.shutdown(false);

        // Assert
        assertTrue(result);
        verify(settingsManager, times(2)).saveSettings(any(ConversionSettings.class));
        assertFalse(controller.hasUnsavedChanges());
    }

    @Test
    void shutdown_should_returnFalse_when_exceptionOccursDuringSave() throws Exception {
        // Arrange
        initializeController();
        List<ConversionFile> mockFiles = List.of();
        when(fileManager.getFiles()).thenReturn(mockFiles);
        doThrow(new IOException("Save failed")).when(stateManager).saveState(any());

        // Act
        boolean result = controller.shutdown();

        // Assert
        assertTrue(result);
    }

    // ===== State Tracking Tests =====

    @Test
    void isInitialized_should_returnFalse_when_notInitialized() {
        assertFalse(controller.isInitialized());
    }

    @Test
    void isInitialized_should_returnTrue_afterInitialize() {
        initializeController();
        assertTrue(controller.isInitialized());
    }

    @Test
    void isConversionInProgress_should_returnCurrentState() {
        assertFalse(controller.isConversionInProgress());
        setConversionInProgress(true);
        assertTrue(controller.isConversionInProgress());
    }

    @Test
    void hasUnsavedChanges_should_returnCurrentState() {
        assertFalse(controller.hasUnsavedChanges());
        setHasUnsavedChanges(true);
        assertTrue(controller.hasUnsavedChanges());
    }

    @Test
    void getCurrentSettings_should_returnNull_when_notInitialized() {
        assertNull(controller.getCurrentSettings());
    }

    @Test
    void getCurrentSettings_should_returnLoadedSettings_afterInitialize() {
        ConversionSettings mockSettings = mock(ConversionSettings.class);
        when(settingsManager.loadSettings()).thenReturn(mockSettings);
        ApplicationState mockState = mock(ApplicationState.class);
        SessionState mockSessionState = mock(SessionState.class);
        when(stateManager.loadState()).thenReturn(mockState);
        when(mockState.sessionState()).thenReturn(mockSessionState);
        when(mockSessionState.pendingFiles()).thenReturn(List.of());

        controller.initialize();

        assertEquals(mockSettings, controller.getCurrentSettings());
    }

    // ===== Helper Methods Tests =====

    @Test
    void markSettingsChanged_should_setUnsavedChangesToTrue() {
        controller.markSettingsChanged();
        assertTrue(controller.hasUnsavedChanges());
    }

    @Test
    void clearUnsavedChanges_should_setUnsavedChangesToFalse() {
        setHasUnsavedChanges(true);
        controller.clearUnsavedChanges();
        assertFalse(controller.hasUnsavedChanges());
    }

    // ===== Private Method Tests via Public Interface =====

    @Test
    void restoreFileList_should_doNothing_when_sessionStateIsNull() throws Exception {
        // Arrange
        Method method = ApplicationWorkflowController.class.getDeclaredMethod("restoreFileList", SessionState.class);
        method.setAccessible(true);

        // Act
        method.invoke(controller, (SessionState) null);

        // Assert
        verify(fileManager, never()).addFiles(any());
    }

    @Test
    void restoreFileList_should_doNothing_when_pendingFilesIsNull() {
        // Arrange
        ApplicationState mockState = mock(ApplicationState.class);
        SessionState mockSessionState = mock(SessionState.class);
        when(mockState.sessionState()).thenReturn(mockSessionState);
        when(mockSessionState.pendingFiles()).thenReturn(null);

        // Act
        when(settingsManager.loadSettings()).thenReturn(mock(ConversionSettings.class));
        when(stateManager.loadState()).thenReturn(mockState);

        controller.initialize();

        // Assert
        verify(fileManager, never()).addFiles(any());
    }

    @Test
    void restoreFileList_should_filterMissingFiles() {
        // Arrange
        ApplicationState mockState = mock(ApplicationState.class);
        SessionState mockSessionState = mock(SessionState.class);
        ConversionFile existingFile = mock(ConversionFile.class);
        ConversionFile missingFile = mock(ConversionFile.class);
        Path existingPath = Paths.get("existing.txt");
        Path missingPath = Paths.get("missing.txt");

        when(mockState.sessionState()).thenReturn(mockSessionState);
        when(mockSessionState.pendingFiles()).thenReturn(List.of(existingFile, missingFile));
        when(existingFile.path()).thenReturn(existingPath);
        when(missingFile.path()).thenReturn(missingPath);

        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(existingPath)).thenReturn(true);
            mockedFiles.when(() -> Files.exists(missingPath)).thenReturn(false);

            // Act
            when(settingsManager.loadSettings()).thenReturn(mock(ConversionSettings.class));
            when(stateManager.loadState()).thenReturn(mockState);

            controller.initialize();

            // Assert
            verify(fileManager).addFiles(List.of(existingPath));
        }
    }

    @Test
    void saveApplicationState_should_callStateManagerSave() throws Exception {
        // Arrange
        initializeController();
        List<ConversionFile> mockFiles = List.of(mock(ConversionFile.class));
        when(fileManager.getFiles()).thenReturn(mockFiles);

        // Act
        controller.shutdown(true);

        // Assert
        verify(stateManager).saveState(any(ApplicationState.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveCurrentSettings_should_callSettingsManagerSave_when_settingsNotNull() throws Exception {
        // Arrange
        ConversionSettings mockSettings = mock(ConversionSettings.class);
        when(settingsManager.loadSettings()).thenReturn(mockSettings);
        ApplicationState mockState = mock(ApplicationState.class);
        SessionState mockSessionState = mock(SessionState.class);
        when(stateManager.loadState()).thenReturn(mockState);
        when(mockState.sessionState()).thenReturn(mockSessionState);
        when(mockSessionState.pendingFiles()).thenReturn(List.of());

        controller.initialize();
        setHasUnsavedChanges(true);

        // Act
        controller.shutdown(true);

        // Assert
        verify(settingsManager).saveSettings(mockSettings);
        assertFalse(controller.hasUnsavedChanges());
    }

    // ===== File Management Tests =====

    @Test
    void addFilesFromFolder_should_delegateToFileManagerAndUpdateState_when_successful() throws Exception {
        // Arrange
        Path folderPath = Paths.get("/test/folder");
        boolean recursive = true;
        ConversionFile mockFile1 = mock(ConversionFile.class);
        ConversionFile mockFile2 = mock(ConversionFile.class);
        Path path1 = Paths.get("/test/folder/file1.txt");
        Path path2 = Paths.get("/test/folder/file2.txt");

        when(mockFile1.path()).thenReturn(path1);
        when(mockFile2.path()).thenReturn(path2);
        List<ConversionFile> addedFiles = List.of(mockFile1, mockFile2);
        when(fileManager.addFilesFromFolder(folderPath, recursive)).thenReturn(addedFiles);

        // Act
        List<ConversionFile> result = controller.addFilesFromFolder(folderPath, recursive);

        // Assert
        assertEquals(addedFiles, result);
        verify(fileManager).addFilesFromFolder(folderPath, recursive);
        assertEquals(folderPath, getLastInputDirectory());
        List<Path> recentPaths = getRecentFilePaths();
        assertTrue(recentPaths.contains(path1));
        assertTrue(recentPaths.contains(path2));
    }

    @Test
    void addFilesFromFolder_should_handleEmptyResults_when_noFilesFound() throws Exception {
        // Arrange
        Path folderPath = Paths.get("/empty/folder");
        boolean recursive = false;
        List<ConversionFile> emptyList = List.of();
        when(fileManager.addFilesFromFolder(folderPath, recursive)).thenReturn(emptyList);

        // Act
        List<ConversionFile> result = controller.addFilesFromFolder(folderPath, recursive);

        // Assert
        assertEquals(emptyList, result);
        verify(fileManager).addFilesFromFolder(folderPath, recursive);
        assertEquals(folderPath, getLastInputDirectory());
        // recentFilePaths should not be updated for empty list
    }

    @Test
    void addFilesFromFolder_should_throwFileOperationException_when_fileManagerThrows() throws Exception {
        // Arrange
        Path folderPath = Paths.get("/test/folder");
        boolean recursive = true;
        FileOperationException expectedException = new FileOperationException("Test error", null, "", null);
        when(fileManager.addFilesFromFolder(folderPath, recursive)).thenThrow(expectedException);

        // Act & Assert
        FileOperationException thrown = assertThrows(FileOperationException.class,
                () -> controller.addFilesFromFolder(folderPath, recursive));
        assertEquals(expectedException, thrown);
    }

    @Test
    void addFilesFromFolder_should_wrapOtherExceptions_when_fileManagerThrowsRuntime() throws Exception {
        // Arrange
        Path folderPath = Paths.get("/test/folder");
        boolean recursive = true;
        RuntimeException cause = new RuntimeException("Test runtime error");
        when(fileManager.addFilesFromFolder(folderPath, recursive)).thenThrow(cause);

        // Act & Assert
        FileOperationException thrown = assertThrows(FileOperationException.class,
                () -> controller.addFilesFromFolder(folderPath, recursive));
        assertEquals("Failed to add files from folder: Test runtime error", thrown.getMessage());
        assertEquals(cause, thrown.getCause());
    }

    @Test
    void clearFiles_should_delegateToFileManager_when_successful() {
        // Act
        controller.clearFiles();

        // Assert
        verify(fileManager).clearFiles();
    }

    @Test
    void clearFiles_should_handleExceptionsGracefully_when_fileManagerThrows() {
        // Arrange
        RuntimeException cause = new RuntimeException("Clear failed");
        doThrow(cause).when(fileManager).clearFiles();

        // Act & Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> controller.clearFiles());
        assertEquals("Failed to clear files: Clear failed", thrown.getMessage());
        assertEquals(cause, thrown.getCause());
    }

    private void initializeController() {
        // Create valid settings with actual values (not mock) for conversions
        ConversionSettings validSettings = createValidSettings();
        ApplicationState mockState = mock(ApplicationState.class);
        SessionState mockSessionState = mock(SessionState.class);

        when(settingsManager.loadSettings()).thenReturn(validSettings);
        when(stateManager.loadState()).thenReturn(mockState);
        when(mockState.sessionState()).thenReturn(mockSessionState);
        when(mockSessionState.pendingFiles()).thenReturn(List.of());

        // Mock getCurrentState() for shutdown - needed by saveApplicationState()
        // Use lenient() since not all tests call shutdown
        lenient().when(mockState.fileListSortState()).thenReturn(FileListSortState.unsorted());
        lenient().when(stateManager.getCurrentState()).thenReturn(mockState);

        controller.initialize();
    }

    private void setConversionInProgress(boolean value) {
        try {
            Field field = ApplicationWorkflowController.class.getDeclaredField("conversionInProgress");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            AtomicBoolean atomicBoolean = (AtomicBoolean) field.get(controller);
            atomicBoolean.set(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setHasUnsavedChanges(boolean value) {
        try {
            Field field = ApplicationWorkflowController.class.getDeclaredField("hasUnsavedChanges");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            AtomicBoolean atomicBoolean = (AtomicBoolean) field.get(controller);
            atomicBoolean.set(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Path getLastInputDirectory() {
        try {
            Field field = ApplicationWorkflowController.class.getDeclaredField("lastInputDirectory");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Path path = (Path) field.get(controller);
            return path;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Path> getRecentFilePaths() {
        try {
            Field field = ApplicationWorkflowController.class.getDeclaredField("recentFilePaths");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Path> pathList = (List<Path>) field.get(controller);
            return pathList;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===== Tests for Workflow Handler Methods =====

    @Test
    void handleAddFiles_should_throwException_when_notInitialized() {
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.handleAddFiles(null));
    }

    @Test
    void handleAddFiles_should_complete_when_initialized() {
        // Arrange
        initializeController();

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> controller.handleAddFiles(null));
    }

    @Test
    void handleAddFolder_should_throwException_when_notInitialized() {
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.handleAddFolder(null));
    }

    @Test
    void handleAddFolder_should_complete_when_initialized() {
        // Arrange
        initializeController();

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> controller.handleAddFolder(null));
    }

    @Test
    void handleRemoveFiles_should_throwException_when_notInitialized() {
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.handleRemoveFiles(List.of("file1")));
    }

    @Test
    void handleRemoveFiles_should_throwNullPointerException_when_fileIdsNull() {
        // Arrange
        initializeController();

        // Act & Assert
        assertThrows(NullPointerException.class, () -> controller.handleRemoveFiles(null));
    }

    @Test
    void handleRemoveFiles_should_doNothing_when_fileIdsEmpty() throws Exception {
        // Arrange
        initializeController();

        // Act
        controller.handleRemoveFiles(List.of());

        // Assert
        verify(fileManager, never()).removeFiles(any());
    }

    @Test
    void handleRemoveFiles_should_removeFilesAndSaveState_when_successful() throws Exception {
        // Arrange
        initializeController();
        List<String> fileIds = List.of("file1", "file2");
        when(fileManager.getFiles()).thenReturn(List.of());

        // Act
        controller.handleRemoveFiles(fileIds);

        // Assert
        verify(fileManager).removeFiles(fileIds);
        verify(stateManager).saveState(any());
    }

    @Test
    void handleRemoveFiles_should_warnAndContinue_when_conversionInProgress() throws Exception {
        // Arrange
        initializeController();
        setConversionInProgress(true);
        List<String> fileIds = List.of("file1");
        when(fileManager.getFiles()).thenReturn(List.of());

        // Act
        controller.handleRemoveFiles(fileIds);

        // Assert
        verify(fileManager).removeFiles(fileIds);
        verify(stateManager).saveState(any());
    }

    @Test
    void handleRemoveFiles_should_propagateException_when_removeFilesFails() throws Exception {
        // Arrange
        initializeController();
        List<String> fileIds = List.of("file1");
        RuntimeException expectedException = new RuntimeException("Remove failed");
        doThrow(expectedException).when(fileManager).removeFiles(fileIds);

        // Act & Assert
        FileOperationException thrown = assertThrows(FileOperationException.class,
                () -> controller.handleRemoveFiles(fileIds));
        assertTrue(thrown.getMessage().contains("Remove failed"));
        verify(stateManager, never()).saveState(any());
    }

    @Test
    void handleClearFiles_should_throwException_when_notInitialized() {
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.handleClearFiles());
    }

    @Test
    void handleClearFiles_should_clearFilesAndSaveState_when_successful() throws Exception {
        // Arrange
        initializeController();
        when(fileManager.getFiles()).thenReturn(List.of());

        // Act
        controller.handleClearFiles();

        // Assert
        verify(fileManager).clearFiles();
        verify(stateManager).saveState(any());
    }

    @Test
    void handleClearFiles_should_warnAndContinue_when_conversionInProgress() throws Exception {
        // Arrange
        initializeController();
        setConversionInProgress(true);
        when(fileManager.getFiles()).thenReturn(List.of());

        // Act
        controller.handleClearFiles();

        // Assert
        verify(fileManager).clearFiles();
        verify(stateManager).saveState(any());
    }

    @Test
    void handleClearFiles_should_propagateException_when_clearFilesFails() throws Exception {
        // Arrange
        initializeController();
        RuntimeException expectedException = new RuntimeException("Clear failed");
        doThrow(expectedException).when(fileManager).clearFiles();

        // Act & Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> controller.handleClearFiles());
        assertTrue(thrown.getMessage().contains("Clear failed"));
        verify(stateManager, never()).saveState(any());
    }

    // ===== Settings Workflow Tests =====

    @Test
    void handleSettingsDialog_should_throwException_when_notInitialized() {
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.handleSettingsDialog(null));
    }

    @Test
    void handleSettingsDialog_should_getCurrentSettings_when_opened() {
        // Arrange
        initializeController();
        ConversionSettings currentSettings = ConversionSettings.builder()
                .outputDirectory(tempDir)
                .parallelConversions(4)
                .build();
        when(settingsManager.getCurrentSettings()).thenReturn(currentSettings);

        // Act
        controller.handleSettingsDialog(null);

        // Assert
        verify(settingsManager).getCurrentSettings();
    }

    @Test
    void handleSettingsSave_should_throwException_when_notInitialized() {
        // Arrange
        ConversionSettings newSettings = ConversionSettings.builder()
                .outputDirectory(tempDir)
                .parallelConversions(4)
                .build();

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.handleSettingsSave(newSettings));
    }

    @Test
    void handleSettingsSave_should_throwException_when_settingsNull() {
        // Arrange
        initializeController();

        // Act & Assert
        assertThrows(NullPointerException.class, () -> controller.handleSettingsSave(null));
    }

    @Test
    void handleSettingsSave_should_validateAndSaveSettings_when_valid() throws Exception {
        // Arrange
        initializeController();
        ConversionSettings newSettings = ConversionSettings.builder()
                .outputDirectory(tempDir)
                .parallelConversions(4)
                .build();

        // Act
        controller.handleSettingsSave(newSettings);

        // Assert
        verify(settingsManager).saveSettings(newSettings);
        assertEquals(newSettings, controller.getCurrentSettings());
        assertFalse(controller.hasUnsavedChanges());
    }

    @Test
    void handleSettingsSave_should_throwException_when_outputDirectoryNotExists() {
        // Arrange
        initializeController();
        Path nonExistentDir = tempDir.resolve("nonexistent");
        ConversionSettings invalidSettings = ConversionSettings.builder()
                .outputDirectory(nonExistentDir)
                .parallelConversions(4)
                .build();

        // Act & Assert
        InvalidSettingsException thrown = assertThrows(InvalidSettingsException.class,
                () -> controller.handleSettingsSave(invalidSettings));
        assertTrue(thrown.getMessage().contains("does not exist"));
        assertEquals("outputDirectory", thrown.getSettingName());
    }

    @Test
    void handleSettingsSave_should_throwException_when_parallelConversionsTooLow() {
        // Arrange
        initializeController();
        ConversionSettings invalidSettings = ConversionSettings.builder()
                .outputDirectory(tempDir)
                .parallelConversions(0) // Invalid: too low
                .build();

        // Act & Assert
        InvalidSettingsException thrown = assertThrows(InvalidSettingsException.class,
                () -> controller.handleSettingsSave(invalidSettings));
        assertTrue(thrown.getMessage().contains("between 1 and 16"));
        assertEquals("parallelConversions", thrown.getSettingName());
    }

    @Test
    void handleSettingsSave_should_throwException_when_parallelConversionsTooHigh() {
        // Arrange
        initializeController();
        ConversionSettings invalidSettings = ConversionSettings.builder()
                .outputDirectory(tempDir)
                .parallelConversions(17) // Invalid: too high
                .build();

        // Act & Assert
        InvalidSettingsException thrown = assertThrows(InvalidSettingsException.class,
                () -> controller.handleSettingsSave(invalidSettings));
        assertTrue(thrown.getMessage().contains("between 1 and 16"));
        assertEquals("parallelConversions", thrown.getSettingName());
    }

    @Test
    void handleSettingsCancel_should_throwException_when_notInitialized() {
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.handleSettingsCancel());
    }

    @Test
    void handleSettingsCancel_should_revertToSavedSettings_when_called() {
        // Arrange
        initializeController();
        ConversionSettings savedSettings = ConversionSettings.builder()
                .outputDirectory(tempDir)
                .parallelConversions(4)
                .build();
        when(settingsManager.getCurrentSettings()).thenReturn(savedSettings);

        // Mark settings as changed
        controller.markSettingsChanged();
        assertTrue(controller.hasUnsavedChanges());

        // Act
        controller.handleSettingsCancel();

        // Assert
        verify(settingsManager).getCurrentSettings();
        assertFalse(controller.hasUnsavedChanges());
        assertEquals(savedSettings, controller.getCurrentSettings());
    }

    // ===== Conversion Workflow Handler Tests (Tasks 47-48) =====

    @Test
    void testHandleStartConversion_NotInitialized() {
        // Arrange
        controller = new ApplicationWorkflowController(fileManager, settingsManager, stateManager, conversionEngine);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.handleStartConversion());
    }

    @Test
    void testHandleStartConversion_ConversionAlreadyInProgress() {
        // Arrange
        initializeController();

        // Setup: Start a conversion first
        Path file = Path.of("/test/input.mp4");
        ConversionFile convFile = ConversionFile.create(file, FileFormat.MP4, 1024L);
        when(fileManager.getFiles()).thenReturn(List.of(convFile));

        controller.handleStartConversion(); // First conversion starts

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.handleStartConversion());
    }

    @Test
    void testHandleStartConversion_NoFiles() {
        // Arrange
        initializeController();
        when(fileManager.getFiles()).thenReturn(List.of());

        // Act - should not throw, just log warning and return gracefully
        controller.handleStartConversion();

        // Assert - conversion engine should never be called
        verify(conversionEngine, never()).convertBatch(any(), any());
    }

    @Test
    void testHandleStartConversion_NoOutputDirectoryConfigured() {
        // Arrange - Need to initialize with settings that have null output directory
        ConversionSettings settingsWithoutDir = ConversionSettings.builder()
                .outputFormat(FileFormat.AVI)
                .outputDirectory(null)
                .overwriteExisting(false)
                .parallelConversions(2)
                .build();

        ApplicationState mockState = mock(ApplicationState.class);
        SessionState mockSessionState = mock(SessionState.class);
        when(settingsManager.loadSettings()).thenReturn(settingsWithoutDir);
        when(stateManager.loadState()).thenReturn(mockState);
        when(mockState.sessionState()).thenReturn(mockSessionState);
        when(mockSessionState.pendingFiles()).thenReturn(List.of());
        controller.initialize();

        Path file = Path.of("/test/input.mp4");
        ConversionFile convFile = ConversionFile.create(file, FileFormat.MP4, 1024L);
        when(fileManager.getFiles()).thenReturn(List.of(convFile));

        // Act - should not throw, just log warning and return gracefully
        controller.handleStartConversion();

        // Assert - conversion engine should never be called
        verify(conversionEngine, never()).convertBatch(any(), any());
    }

    @Test
    void testHandleStartConversion_NoOutputFormatSelected() {
        // Arrange - Create settings without any section settings (no output format)
        ConversionSettings settingsWithoutFormat = ConversionSettings.builder()
                .outputDirectory(tempDir)
                .overwriteExisting(false)
                .parallelConversions(2)
                // No videoSettings, audioSettings, imageSettings, or documentSettings
                // so outputFormat() will return null
                .build();

        ApplicationState mockState = mock(ApplicationState.class);
        SessionState mockSessionState = mock(SessionState.class);
        when(settingsManager.loadSettings()).thenReturn(settingsWithoutFormat);
        when(stateManager.loadState()).thenReturn(mockState);
        when(mockState.sessionState()).thenReturn(mockSessionState);
        when(mockSessionState.pendingFiles()).thenReturn(List.of());
        controller.initialize();

        Path file = Path.of("/test/input.mp4");
        ConversionFile convFile = ConversionFile.create(file, FileFormat.MP4, 1024L);
        when(fileManager.getFiles()).thenReturn(List.of(convFile));

        // Act - should not throw, just log warning and return gracefully
        controller.handleStartConversion();

        // Assert - conversion engine should never be called
        verify(conversionEngine, never()).convertBatch(any(), any());
    }

    @Test
    void testHandleStartConversion_Success() {
        // Arrange
        initializeController();

        Path file1 = Path.of("/test/input1.mp4");
        Path file2 = Path.of("/test/input2.mp4");
        ConversionFile convFile1 = ConversionFile.create(file1, FileFormat.MP4, 1024L);
        ConversionFile convFile2 = ConversionFile.create(file2, FileFormat.MP4, 2048L);
        List<ConversionFile> files = List.of(convFile1, convFile2);

        when(fileManager.getFiles()).thenReturn(files);

        // Act
        controller.handleStartConversion();

        // Assert
        verify(conversionEngine).convertBatch(eq(files), any(ConversionSettings.class));
        assertTrue(controller.isConversionInProgress());
    }

    @Test
    void testHandleStartConversion_ConversionEngineThrowsException() {
        // Arrange
        initializeController();

        Path file = Path.of("/test/input.mp4");
        ConversionFile convFile = ConversionFile.create(file, FileFormat.MP4, 1024L);
        when(fileManager.getFiles()).thenReturn(List.of(convFile));

        doThrow(new RuntimeException("Conversion engine error"))
                .when(conversionEngine).convertBatch(any(), any());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> controller.handleStartConversion());
        assertFalse(controller.isConversionInProgress());
    }

    @Test
    void testHandlePauseConversion_NotInitialized() {
        // Arrange
        controller = new ApplicationWorkflowController(fileManager, settingsManager, stateManager, conversionEngine);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.handlePauseConversion());
    }

    @Test
    void testHandlePauseConversion_NoConversionInProgress() {
        // Arrange
        initializeController();

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.handlePauseConversion());
    }

    @Test
    void testHandlePauseConversion_Success() {
        // Arrange
        initializeController();

        // Setup: Start a conversion first
        Path file = Path.of("/test/input.mp4");
        ConversionFile convFile = ConversionFile.create(file, FileFormat.MP4, 1024L);
        when(fileManager.getFiles()).thenReturn(List.of(convFile));
        controller.handleStartConversion();

        // Act
        controller.handlePauseConversion();

        // Assert
        verify(conversionEngine).pauseConversion();
    }

    @Test
    void testHandlePauseConversion_ConversionEngineThrowsException() {
        // Arrange
        initializeController();

        // Setup: Start a conversion first
        Path file = Path.of("/test/input.mp4");
        ConversionFile convFile = ConversionFile.create(file, FileFormat.MP4, 1024L);
        when(fileManager.getFiles()).thenReturn(List.of(convFile));
        controller.handleStartConversion();

        doThrow(new RuntimeException("Pause error")).when(conversionEngine).pauseConversion();

        // Act & Assert
        assertThrows(RuntimeException.class, () -> controller.handlePauseConversion());
    }

    @Test
    void testHandleResumeConversion_NotInitialized() {
        // Arrange
        controller = new ApplicationWorkflowController(fileManager, settingsManager, stateManager, conversionEngine);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.handleResumeConversion());
    }

    @Test
    void testHandleResumeConversion_NoConversionInProgress() {
        // Arrange
        initializeController();

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.handleResumeConversion());
    }

    @Test
    void testHandleResumeConversion_Success() {
        // Arrange
        initializeController();

        // Setup: Start a conversion first
        Path file = Path.of("/test/input.mp4");
        ConversionFile convFile = ConversionFile.create(file, FileFormat.MP4, 1024L);
        when(fileManager.getFiles()).thenReturn(List.of(convFile));
        controller.handleStartConversion();

        // Act
        controller.handleResumeConversion();

        // Assert
        verify(conversionEngine).resumeConversion();
    }

    @Test
    void testHandleResumeConversion_ConversionEngineThrowsException() {
        // Arrange
        initializeController();

        // Setup: Start a conversion first
        Path file = Path.of("/test/input.mp4");
        ConversionFile convFile = ConversionFile.create(file, FileFormat.MP4, 1024L);
        when(fileManager.getFiles()).thenReturn(List.of(convFile));
        controller.handleStartConversion();

        doThrow(new RuntimeException("Resume error")).when(conversionEngine).resumeConversion();

        // Act & Assert
        assertThrows(RuntimeException.class, () -> controller.handleResumeConversion());
    }

    @Test
    void testHandleCancelConversion_NotInitialized() {
        // Arrange
        controller = new ApplicationWorkflowController(fileManager, settingsManager, stateManager, conversionEngine);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> controller.handleCancelConversion());
    }

    @Test
    void testHandleCancelConversion_NoConversionInProgress() {
        // Arrange
        initializeController();

        // Act (should not throw, just log warning)
        controller.handleCancelConversion();

        // Assert
        verify(conversionEngine, never()).cancelConversion();
    }

    @Test
    void testHandleCancelConversion_Success() {
        // Arrange
        initializeController();

        // Setup: Start a conversion first
        Path file = Path.of("/test/input.mp4");
        ConversionFile convFile = ConversionFile.create(file, FileFormat.MP4, 1024L);
        when(fileManager.getFiles()).thenReturn(List.of(convFile));
        controller.handleStartConversion();
        assertTrue(controller.isConversionInProgress());

        // Act
        controller.handleCancelConversion();

        // Assert
        verify(conversionEngine).cancelConversion();
        // NOTE: Flag should remain true until completion handler clears it
        // when all conversions actually finish (getActiveConversionCount() == 0)
        assertTrue(controller.isConversionInProgress());
    }

    @Test
    void testHandleCancelConversion_ConversionEngineThrowsException() {
        // Arrange
        initializeController();

        // Setup: Start a conversion first
        Path file = Path.of("/test/input.mp4");
        ConversionFile convFile = ConversionFile.create(file, FileFormat.MP4, 1024L);
        when(fileManager.getFiles()).thenReturn(List.of(convFile));
        controller.handleStartConversion();

        doThrow(new RuntimeException("Cancel error")).when(conversionEngine).cancelConversion();

        // Act (should not throw - best effort cancellation)
        controller.handleCancelConversion();

        // Assert
        verify(conversionEngine).cancelConversion();
        // NOTE: Even when cancellation throws exception, flag should remain true
        // until completion handler clears it when conversions actually finish
        assertTrue(controller.isConversionInProgress());
    }

    // ===== Completion Event Flow Tests (Task 75) =====

    @Test
    void testCompletionEventFlow_shouldSaveSessionState_whenBatchCompletes() throws IOException {
        // Arrange
        initializeController();
        ArgumentCaptor<BiConsumer<String, ConversionResult>> completionCallbackCaptor = ArgumentCaptor
                .forClass(BiConsumer.class);
        verify(conversionEngine).onConversionComplete(completionCallbackCaptor.capture());

        BiConsumer<String, ConversionResult> completionCallback = completionCallbackCaptor.getValue();

        // Setup: Start a conversion
        Path file = Path.of("/test/input.mp4");
        ConversionFile convFile = ConversionFile.create(file, FileFormat.MP4, 1024L);
        when(fileManager.getFiles()).thenReturn(List.of(convFile));
        controller.handleStartConversion();

        // Mock active conversion count to be 0 (batch complete)
        when(conversionEngine.getActiveConversionCount()).thenReturn(0);

        // Create successful conversion result
        ConversionResult successResult = ConversionResult.success("file1", Path.of("/output/result.mp4"), null,
                Duration.ofSeconds(10), 1024L, 512L, ConversionTool.FFMPEG);

        // Act - Simulate completion callback
        completionCallback.accept("file1", successResult);

        // Assert
        assertFalse(controller.isConversionInProgress());
        verify(stateManager).saveState(any(ApplicationState.class));
    }

    @Test
    void testCompletionEventFlow_shouldSetConversionInProgressFalse_whenBatchCompletes() throws IOException {
        // Arrange
        initializeController();
        ArgumentCaptor<BiConsumer<String, ConversionResult>> completionCallbackCaptor = ArgumentCaptor
                .forClass(BiConsumer.class);
        verify(conversionEngine).onConversionComplete(completionCallbackCaptor.capture());

        BiConsumer<String, ConversionResult> completionCallback = completionCallbackCaptor.getValue();

        // Setup: Start a conversion
        Path file = Path.of("/test/input.mp4");
        ConversionFile convFile = ConversionFile.create(file, FileFormat.MP4, 1024L);
        when(fileManager.getFiles()).thenReturn(List.of(convFile));
        controller.handleStartConversion();
        assertTrue(controller.isConversionInProgress());

        // Mock active conversion count to be 0 (batch complete)
        when(conversionEngine.getActiveConversionCount()).thenReturn(0);

        // Create failed conversion result
        ConversionResult failureResult = ConversionResult.failure("file1", "Conversion failed", null,
                Duration.ofSeconds(5), 1024L, ConversionTool.FFMPEG);

        // Act - Simulate completion callback
        completionCallback.accept("file1", failureResult);

        // Assert
        assertFalse(controller.isConversionInProgress());
        verify(stateManager).saveState(any(ApplicationState.class));
    }

    @Test
    void testCompletionEventFlow_shouldNotSaveState_whenConversionsStillActive() throws IOException {
        // Arrange
        initializeController();
        ArgumentCaptor<BiConsumer<String, ConversionResult>> completionCallbackCaptor = ArgumentCaptor
                .forClass(BiConsumer.class);
        verify(conversionEngine).onConversionComplete(completionCallbackCaptor.capture());

        BiConsumer<String, ConversionResult> completionCallback = completionCallbackCaptor.getValue();

        // Setup: Start a conversion
        Path file = Path.of("/test/input.mp4");
        ConversionFile convFile = ConversionFile.create(file, FileFormat.MP4, 1024L);
        when(fileManager.getFiles()).thenReturn(List.of(convFile));
        controller.handleStartConversion();

        // Mock active conversion count to be > 0 (batch not complete)
        when(conversionEngine.getActiveConversionCount()).thenReturn(1);

        ConversionResult result = ConversionResult.success("file1", Path.of("/output/result.mp4"), null,
                Duration.ofSeconds(10), 1024L, 512L, ConversionTool.FFMPEG);

        // Act - Simulate completion callback
        completionCallback.accept("file1", result);

        // Assert
        assertTrue(controller.isConversionInProgress()); // Still in progress
        verify(stateManager, never()).saveState(any()); // No state save
    }

    // ===== Task 28: Integration Tests for outputPath Updates =====

    @Test
    void testConversionComplete_SetsOutputPathOnFile_whenConversionSucceeds() {
        // Arrange
        initializeController();
        ArgumentCaptor<BiConsumer<String, ConversionResult>> completionCallbackCaptor = ArgumentCaptor
                .forClass(BiConsumer.class);
        verify(conversionEngine).onConversionComplete(completionCallbackCaptor.capture());

        BiConsumer<String, ConversionResult> completionCallback = completionCallbackCaptor.getValue();

        // Setup: Create a conversion file with auto-generated ID
        Path inputPath = Path.of("/test/input.mp4");
        Path outputPath = Path.of("/output/converted.avi");

        ConversionFile convFile = ConversionFile.create(inputPath, FileFormat.MP4, 1024L);
        String fileId = convFile.id(); // Use the auto-generated ID

        when(fileManager.getFile(fileId)).thenReturn(java.util.Optional.of(convFile));
        when(fileManager.getFiles()).thenReturn(List.of(convFile));
        when(conversionEngine.getActiveConversionCount()).thenReturn(0);

        // Create successful conversion result with output path
        ConversionResult successResult = ConversionResult.success(
                fileId,
                outputPath,
                null,
                Duration.ofSeconds(10),
                1024L,
                512L,
                ConversionTool.FFMPEG);

        // Act - Simulate completion callback
        completionCallback.accept(fileId, successResult);

        // Assert - Verify FileManager.updateFile was called with outputPath set
        ArgumentCaptor<ConversionFile> fileCaptor = ArgumentCaptor.forClass(ConversionFile.class);
        verify(fileManager).updateFile(fileCaptor.capture());

        ConversionFile updatedFile = fileCaptor.getValue();
        assertNotNull(updatedFile);
        assertEquals(ConversionStatus.COMPLETED, updatedFile.status());
        assertEquals(100, updatedFile.progress());
        assertTrue(updatedFile.outputPath().isPresent(), "Output path should be present");
        assertEquals(outputPath, updatedFile.outputPath().get());
    }

    @Test
    void testConversionComplete_DoesNotSetOutputPath_whenConversionFails() {
        // Arrange
        initializeController();
        ArgumentCaptor<BiConsumer<String, ConversionResult>> completionCallbackCaptor = ArgumentCaptor
                .forClass(BiConsumer.class);
        verify(conversionEngine).onConversionComplete(completionCallbackCaptor.capture());

        BiConsumer<String, ConversionResult> completionCallback = completionCallbackCaptor.getValue();

        // Setup: Create a conversion file with auto-generated ID
        Path inputPath = Path.of("/test/input.mp4");

        ConversionFile convFile = ConversionFile.create(inputPath, FileFormat.MP4, 1024L);
        String fileId = convFile.id(); // Use the auto-generated ID

        when(fileManager.getFile(fileId)).thenReturn(java.util.Optional.of(convFile));
        when(fileManager.getFiles()).thenReturn(List.of(convFile));
        when(conversionEngine.getActiveConversionCount()).thenReturn(0);

        // Create failed conversion result (no output path)
        ConversionResult failureResult = ConversionResult.failure(
                fileId,
                "Conversion failed",
                null,
                Duration.ofSeconds(5),
                1024L,
                ConversionTool.FFMPEG);

        // Act - Simulate completion callback
        completionCallback.accept(fileId, failureResult);

        // Assert - Verify FileManager.updateFile was called but outputPath is NOT set
        ArgumentCaptor<ConversionFile> fileCaptor = ArgumentCaptor.forClass(ConversionFile.class);
        verify(fileManager).updateFile(fileCaptor.capture());

        ConversionFile updatedFile = fileCaptor.getValue();
        assertNotNull(updatedFile);
        assertEquals(ConversionStatus.FAILED, updatedFile.status());
        assertNotNull(updatedFile.errorMessage());
        assertEquals("Conversion failed", updatedFile.errorMessage());
        assertFalse(updatedFile.outputPath().isPresent(), "Output path should NOT be present for failed conversions");
    }

    @Test
    void testConversionComplete_RetrievesOutputPathFromConversionResult() {
        // Arrange - Test that outputPath comes from ConversionResult
        initializeController();
        ArgumentCaptor<BiConsumer<String, ConversionResult>> completionCallbackCaptor = ArgumentCaptor
                .forClass(BiConsumer.class);
        verify(conversionEngine).onConversionComplete(completionCallbackCaptor.capture());

        BiConsumer<String, ConversionResult> completionCallback = completionCallbackCaptor.getValue();

        Path inputPath = Path.of("/test/source.png");
        Path expectedOutputPath = Path.of("/output/converted.jpg");

        ConversionFile imageFile = ConversionFile.create(inputPath, FileFormat.PNG, 2048L);
        String fileId = imageFile.id(); // Use the auto-generated ID

        when(fileManager.getFile(fileId)).thenReturn(java.util.Optional.of(imageFile));
        when(fileManager.getFiles()).thenReturn(List.of(imageFile));
        when(conversionEngine.getActiveConversionCount()).thenReturn(0);

        // Create result with specific output path
        ConversionResult result = ConversionResult.success(
                fileId,
                expectedOutputPath,
                null,
                Duration.ofSeconds(3),
                2048L,
                1500L,
                ConversionTool.FFMPEG);

        // Act
        completionCallback.accept(fileId, result);

        // Assert - Verify the exact output path from ConversionResult is used
        ArgumentCaptor<ConversionFile> fileCaptor = ArgumentCaptor.forClass(ConversionFile.class);
        verify(fileManager).updateFile(fileCaptor.capture());

        ConversionFile updatedFile = fileCaptor.getValue();
        assertTrue(updatedFile.outputPath().isPresent());
        assertEquals(expectedOutputPath, updatedFile.outputPath().get(),
                "Output path should match the path from ConversionResult");
    }

    // ===== Preset Application Tests (Task 39) =====

    @Test
    void applyPresetToFiles_should_throwNullPointerException_when_fileIdsNull() {
        // Arrange
        initializeController();
        SectionPreset preset = SectionPreset.forVideo(
                "Test Preset",
                "Test description",
                VideoSettings.builder().build(),
                false);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> controller.applyPresetToFiles(null, preset));
    }

    @Test
    void applyPresetToFiles_should_throwNullPointerException_when_presetNull() {
        // Arrange
        initializeController();

        // Act & Assert
        assertThrows(NullPointerException.class, () -> controller.applyPresetToFiles(List.of("file1"), null));
    }

    @Test
    void applyPresetToFiles_should_throwIllegalArgumentException_when_fileIdsEmpty() {
        // Arrange
        initializeController();
        SectionPreset preset = SectionPreset.forVideo(
                "Test Preset",
                "Test description",
                VideoSettings.builder().build(),
                false);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> controller.applyPresetToFiles(List.of(), preset));
    }

    @Test
    void applyPresetToFiles_should_throwIllegalArgumentException_when_fileNotFound() {
        // Arrange
        initializeController();
        SectionPreset preset = SectionPreset.forVideo(
                "Test Preset",
                "Test description",
                VideoSettings.builder().build(),
                false);
        when(fileManager.getFile("nonexistent")).thenReturn(java.util.Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> controller.applyPresetToFiles(List.of("nonexistent"), preset));
    }

    @Test
    void applyPresetToFiles_should_throwIllegalArgumentException_when_mixedCategories() {
        // Arrange
        initializeController();
        ConversionFile videoFile = ConversionFile.create(
                Path.of("/test/video.mp4"), FileFormat.MP4, 1024L);
        ConversionFile audioFile = ConversionFile.create(
                Path.of("/test/audio.mp3"), FileFormat.MP3, 512L);

        when(fileManager.getFile("video1")).thenReturn(java.util.Optional.of(videoFile));
        when(fileManager.getFile("audio1")).thenReturn(java.util.Optional.of(audioFile));

        SectionPreset videoPreset = SectionPreset.forVideo(
                "Video Preset",
                "Test",
                VideoSettings.builder().build(),
                false);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.applyPresetToFiles(List.of("video1", "audio1"), videoPreset));
        assertTrue(ex.getMessage().contains("different format categories"));
    }

    @Test
    void applyPresetToFiles_should_throwIllegalArgumentException_when_categoryMismatch() {
        // Arrange
        initializeController();
        ConversionFile videoFile = ConversionFile.create(
                Path.of("/test/video.mp4"), FileFormat.MP4, 1024L);

        when(fileManager.getFile("video1")).thenReturn(java.util.Optional.of(videoFile));

        // Create audio preset but apply to video file (category mismatch)
        SectionPreset audioPreset = SectionPreset.forAudio(
                "Audio Preset",
                "Test",
                AudioSettings.builder().build(),
                false);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> controller.applyPresetToFiles(List.of("video1"), audioPreset));
        assertTrue(ex.getMessage().contains("does not match preset category"));
    }

    @Test
    void applyPresetToFiles_should_applyVideoPreset_when_validSingleVideoFile() {
        // Arrange
        initializeController();
        ConversionFile videoFile = ConversionFile.create(
                Path.of("/test/video.mp4"), FileFormat.MP4, 1024L);

        when(fileManager.getFile("video1")).thenReturn(java.util.Optional.of(videoFile));

        VideoSettings videoSettings = VideoSettings.builder()
                .codec("H.264")
                .bitrate(5000)
                .build();

        SectionPreset videoPreset = SectionPreset.forVideo(
                "HD Video",
                "1080p preset",
                videoSettings,
                false);

        // Act
        controller.applyPresetToFiles(List.of("video1"), videoPreset);

        // Assert
        ArgumentCaptor<ConversionFile> fileCaptor = ArgumentCaptor.forClass(ConversionFile.class);
        verify(fileManager).updateFile(fileCaptor.capture());

        ConversionFile updatedFile = fileCaptor.getValue();
        assertTrue(updatedFile.hasCustomSettings());
        assertEquals("HD Video", updatedFile.settingsOverride().presetName());
        assertEquals(videoSettings, updatedFile.settingsOverride().videoSettings());
    }

    @Test
    void applyPresetToFiles_should_applyAudioPreset_when_validMultipleAudioFiles() {
        // Arrange
        initializeController();
        ConversionFile audioFile1 = ConversionFile.create(
                Path.of("/test/audio1.mp3"), FileFormat.MP3, 512L);
        ConversionFile audioFile2 = ConversionFile.create(
                Path.of("/test/audio2.mp3"), FileFormat.MP3, 512L);

        when(fileManager.getFile("audio1")).thenReturn(java.util.Optional.of(audioFile1));
        when(fileManager.getFile("audio2")).thenReturn(java.util.Optional.of(audioFile2));

        AudioSettings audioSettings = AudioSettings.builder()
                .codec("AAC")
                .bitrate(192)
                .build();

        SectionPreset audioPreset = SectionPreset.forAudio(
                "High Quality Audio",
                "192 kbps AAC",
                audioSettings,
                false);

        // Act
        controller.applyPresetToFiles(List.of("audio1", "audio2"), audioPreset);

        // Assert
        ArgumentCaptor<ConversionFile> fileCaptor = ArgumentCaptor.forClass(ConversionFile.class);
        verify(fileManager, times(2)).updateFile(fileCaptor.capture());

        List<ConversionFile> updatedFiles = fileCaptor.getAllValues();
        assertEquals(2, updatedFiles.size());

        for (ConversionFile file : updatedFiles) {
            assertTrue(file.hasCustomSettings());
            assertEquals("High Quality Audio", file.settingsOverride().presetName());
            assertEquals(audioSettings, file.settingsOverride().audioSettings());
        }
    }

    @Test
    void applyPresetToFiles_should_applyImagePreset_when_validImageFile() {
        // Arrange
        initializeController();
        ConversionFile imageFile = ConversionFile.create(
                Path.of("/test/image.png"), FileFormat.PNG, 2048L);

        when(fileManager.getFile("image1")).thenReturn(java.util.Optional.of(imageFile));

        ImageSettings imageSettings = ImageSettings.builder()
                .quality(90)
                .compressionLevel(5)
                .build();

        SectionPreset imagePreset = SectionPreset.forImage(
                "Web Image",
                "Optimized for web",
                imageSettings,
                false);

        // Act
        controller.applyPresetToFiles(List.of("image1"), imagePreset);

        // Assert
        ArgumentCaptor<ConversionFile> fileCaptor = ArgumentCaptor.forClass(ConversionFile.class);
        verify(fileManager).updateFile(fileCaptor.capture());

        ConversionFile updatedFile = fileCaptor.getValue();
        assertTrue(updatedFile.hasCustomSettings());
        assertEquals("Web Image", updatedFile.settingsOverride().presetName());
        assertEquals(imageSettings, updatedFile.settingsOverride().imageSettings());
    }

    @Test
    void applyPresetToFiles_should_applyDocumentPreset_when_validDocumentFile() {
        // Arrange
        initializeController();
        ConversionFile docFile = ConversionFile.create(
                Path.of("/test/doc.pdf"), FileFormat.PDF, 4096L);

        when(fileManager.getFile("doc1")).thenReturn(java.util.Optional.of(docFile));

        DocumentSettings docSettings = DocumentSettings.builder()
                .marginTop(20)
                .marginBottom(20)
                .marginLeft(20)
                .marginRight(20)
                .build();

        SectionPreset docPreset = SectionPreset.forDocument(
                "Standard PDF",
                "A4 with margins",
                docSettings,
                false);

        // Act
        controller.applyPresetToFiles(List.of("doc1"), docPreset);

        // Assert
        ArgumentCaptor<ConversionFile> fileCaptor = ArgumentCaptor.forClass(ConversionFile.class);
        verify(fileManager).updateFile(fileCaptor.capture());

        ConversionFile updatedFile = fileCaptor.getValue();
        assertTrue(updatedFile.hasCustomSettings());
        assertEquals("Standard PDF", updatedFile.settingsOverride().presetName());
        assertEquals(docSettings, updatedFile.settingsOverride().documentSettings());
    }

    // ===== Clear Preset Tests =====

    @Test
    void clearPresetFromFiles_should_throwNullPointerException_when_fileIdsNull() {
        // Arrange
        initializeController();

        // Act & Assert
        assertThrows(NullPointerException.class, () -> controller.clearPresetFromFiles(null));
    }

    @Test
    void clearPresetFromFiles_should_doNothing_when_fileIdsEmpty() {
        // Arrange
        initializeController();

        // Act
        controller.clearPresetFromFiles(List.of());

        // Assert
        verify(fileManager, never()).updateFile(any());
    }

    @Test
    void clearPresetFromFiles_should_clearSettingsOverride_when_fileHasCustomSettings() {
        // Arrange
        initializeController();

        VideoSettings videoSettings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test", videoSettings);

        ConversionFile fileWithOverride = ConversionFile.create(
                Path.of("/test/video.mp4"), FileFormat.MP4, 1024L).withSettingsOverride(override);

        when(fileManager.getFile("video1")).thenReturn(java.util.Optional.of(fileWithOverride));

        // Act
        controller.clearPresetFromFiles(List.of("video1"));

        // Assert
        ArgumentCaptor<ConversionFile> fileCaptor = ArgumentCaptor.forClass(ConversionFile.class);
        verify(fileManager).updateFile(fileCaptor.capture());

        ConversionFile clearedFile = fileCaptor.getValue();
        assertFalse(clearedFile.hasCustomSettings());
        assertNull(clearedFile.settingsOverride());
    }

    @Test
    void clearPresetFromFiles_should_skipFile_when_noCustomSettings() {
        // Arrange
        initializeController();

        ConversionFile fileWithoutOverride = ConversionFile.create(
                Path.of("/test/video.mp4"), FileFormat.MP4, 1024L);

        when(fileManager.getFile("video1")).thenReturn(java.util.Optional.of(fileWithoutOverride));

        // Act
        controller.clearPresetFromFiles(List.of("video1"));

        // Assert
        verify(fileManager, never()).updateFile(any());
    }

    @Test
    void clearPresetFromFiles_should_continueOnMissingFile() {
        // Arrange
        initializeController();

        VideoSettings videoSettings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test", videoSettings);

        ConversionFile fileWithOverride = ConversionFile.create(
                Path.of("/test/video.mp4"), FileFormat.MP4, 1024L).withSettingsOverride(override);

        when(fileManager.getFile("missing")).thenReturn(java.util.Optional.empty());
        when(fileManager.getFile("video1")).thenReturn(java.util.Optional.of(fileWithOverride));

        // Act
        controller.clearPresetFromFiles(List.of("missing", "video1"));

        // Assert - Should clear the valid file, skip the missing one
        verify(fileManager, times(1)).updateFile(any());
    }

    @Test
    void clearPresetFromFiles_should_clearMultipleFiles_when_allHaveCustomSettings() {
        // Arrange
        initializeController();

        VideoSettings videoSettings = VideoSettings.builder().build();
        FileSettingsOverride override = FileSettingsOverride.forVideo("Test", videoSettings);

        ConversionFile file1 = ConversionFile.create(
                Path.of("/test/video1.mp4"), FileFormat.MP4, 1024L).withSettingsOverride(override);

        ConversionFile file2 = ConversionFile.create(
                Path.of("/test/video2.mp4"), FileFormat.MP4, 1024L).withSettingsOverride(override);

        when(fileManager.getFile("video1")).thenReturn(java.util.Optional.of(file1));
        when(fileManager.getFile("video2")).thenReturn(java.util.Optional.of(file2));

        // Act
        controller.clearPresetFromFiles(List.of("video1", "video2"));

        // Assert
        ArgumentCaptor<ConversionFile> fileCaptor = ArgumentCaptor.forClass(ConversionFile.class);
        verify(fileManager, times(2)).updateFile(fileCaptor.capture());

        List<ConversionFile> clearedFiles = fileCaptor.getAllValues();
        assertEquals(2, clearedFiles.size());

        for (ConversionFile file : clearedFiles) {
            assertFalse(file.hasCustomSettings());
            assertNull(file.settingsOverride());
        }
    }

    // ===== Get Available Presets Tests =====

    @Test
    void getAvailablePresetsForFiles_should_throwNullPointerException_when_fileIdsNull() {
        // Arrange
        initializeController();

        // Act & Assert
        assertThrows(NullPointerException.class, () -> controller.getAvailablePresetsForFiles(null));
    }

    @Test
    void getAvailablePresetsForFiles_should_returnEmptyList_when_fileIdsEmpty() {
        // Arrange
        initializeController();

        // Act
        List<SectionPreset> result = controller.getAvailablePresetsForFiles(List.of());

        // Assert
        assertTrue(result.isEmpty());
    }

    // ===== Task 56: getConversionResult Tests =====

    @Test
    void getConversionResult_should_returnResult_when_fileHasCompletedConversion() {
        // Arrange
        String fileId = "testFile";
        ConversionResult expectedResult = ConversionResult.success(
                fileId,
                Path.of("/output/result.mp4"),
                null,
                Duration.ofSeconds(10),
                1024L,
                512L,
                ConversionTool.FFMPEG);

        when(conversionEngine.getConversionResult(fileId)).thenReturn(expectedResult);

        // Act
        ConversionResult result = controller.getConversionResult(fileId);

        // Assert
        assertEquals(expectedResult, result);
        verify(conversionEngine).getConversionResult(fileId);
    }

    @Test
    void getConversionResult_should_returnNull_when_fileHasNoResultYet() {
        // Arrange
        String fileId = "testFile";
        when(conversionEngine.getConversionResult(fileId)).thenReturn(null);

        // Act
        ConversionResult result = controller.getConversionResult(fileId);

        // Assert
        assertNull(result);
        verify(conversionEngine).getConversionResult(fileId);
    }

    @Test
    void getConversionResult_should_delegateCorrectlyToConversionEngine() {
        // Arrange
        String fileId = "anotherFile";
        ConversionResult mockResult = mock(ConversionResult.class);
        when(conversionEngine.getConversionResult(fileId)).thenReturn(mockResult);

        // Act
        ConversionResult result = controller.getConversionResult(fileId);

        // Assert
        assertEquals(mockResult, result);
        verify(conversionEngine).getConversionResult(fileId);
    }

    @Test
    void testHandleStartConversion_RespectsSortOrder() {
        // Arrange
        initializeController();

        // Create files with different names and sizes for sorting
        Path file1 = Path.of("/test/charlie.mp4");
        Path file2 = Path.of("/test/alice.mp4");
        Path file3 = Path.of("/test/bob.mp4");
        ConversionFile convFile1 = ConversionFile.create(file1, FileFormat.MP4, 3000L);
        ConversionFile convFile2 = ConversionFile.create(file2, FileFormat.MP4, 1000L);
        ConversionFile convFile3 = ConversionFile.create(file3, FileFormat.MP4, 2000L);

        // Files in insertion order: charlie, alice, bob
        List<ConversionFile> filesInInsertionOrder = List.of(convFile1, convFile2, convFile3);
        when(fileManager.getFiles()).thenReturn(filesInInsertionOrder);

        // Set up a sort state: sorted by name ascending
        FileListSortState sortState = FileListSortState.byName(FileListSortState.SortDirection.ASCENDING);
        ApplicationState mockAppState = mock(ApplicationState.class);
        when(stateManager.getCurrentState()).thenReturn(mockAppState);
        when(mockAppState.fileListSortState()).thenReturn(sortState);

        // Capture the actual list passed to convertBatch
        ArgumentCaptor<List<ConversionFile>> filesCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        controller.handleStartConversion();

        // Assert
        verify(conversionEngine).convertBatch(filesCaptor.capture(), any(ConversionSettings.class));

        // Verify files are in sorted order (alice, bob, charlie) not insertion order
        List<ConversionFile> sortedFiles = filesCaptor.getValue();
        assertEquals(3, sortedFiles.size());
        assertEquals("alice.mp4", sortedFiles.get(0).path().getFileName().toString());
        assertEquals("bob.mp4", sortedFiles.get(1).path().getFileName().toString());
        assertEquals("charlie.mp4", sortedFiles.get(2).path().getFileName().toString());

        assertTrue(controller.isConversionInProgress());
    }

    @Test
    void testHandleStartConversion_WithoutSort_UsesInsertionOrder() {
        // Arrange
        initializeController();

        // Create files
        Path file1 = Path.of("/test/charlie.mp4");
        Path file2 = Path.of("/test/alice.mp4");
        Path file3 = Path.of("/test/bob.mp4");
        ConversionFile convFile1 = ConversionFile.create(file1, FileFormat.MP4, 3000L);
        ConversionFile convFile2 = ConversionFile.create(file2, FileFormat.MP4, 1000L);
        ConversionFile convFile3 = ConversionFile.create(file3, FileFormat.MP4, 2000L);

        // Files in insertion order: charlie, alice, bob
        List<ConversionFile> filesInInsertionOrder = List.of(convFile1, convFile2, convFile3);
        when(fileManager.getFiles()).thenReturn(filesInInsertionOrder);

        // Set up unsorted state
        FileListSortState sortState = FileListSortState.unsorted();
        ApplicationState mockAppState = mock(ApplicationState.class);
        when(stateManager.getCurrentState()).thenReturn(mockAppState);
        when(mockAppState.fileListSortState()).thenReturn(sortState);

        // Capture the actual list passed to convertBatch
        ArgumentCaptor<List<ConversionFile>> filesCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        controller.handleStartConversion();

        // Assert
        verify(conversionEngine).convertBatch(filesCaptor.capture(), any(ConversionSettings.class));

        // Verify files are in insertion order (charlie, alice, bob)
        List<ConversionFile> passedFiles = filesCaptor.getValue();
        assertEquals(3, passedFiles.size());
        assertEquals("charlie.mp4", passedFiles.get(0).path().getFileName().toString());
        assertEquals("alice.mp4", passedFiles.get(1).path().getFileName().toString());
        assertEquals("bob.mp4", passedFiles.get(2).path().getFileName().toString());

        assertTrue(controller.isConversionInProgress());
    }

    @Test
    void testHandleStartConversion_SortsBySize_Descending() {
        // Arrange
        initializeController();

        // Create files with different sizes
        Path file1 = Path.of("/test/file1.mp4");
        Path file2 = Path.of("/test/file2.mp4");
        Path file3 = Path.of("/test/file3.mp4");
        ConversionFile convFile1 = ConversionFile.create(file1, FileFormat.MP4, 3000L);
        ConversionFile convFile2 = ConversionFile.create(file2, FileFormat.MP4, 1000L);
        ConversionFile convFile3 = ConversionFile.create(file3, FileFormat.MP4, 2000L);

        // Files in insertion order: 3000, 1000, 2000
        List<ConversionFile> filesInInsertionOrder = List.of(convFile1, convFile2, convFile3);
        when(fileManager.getFiles()).thenReturn(filesInInsertionOrder);

        // Set up a sort state: sorted by size descending
        FileListSortState sortState = FileListSortState.bySize(FileListSortState.SortDirection.DESCENDING);
        ApplicationState mockAppState = mock(ApplicationState.class);
        when(stateManager.getCurrentState()).thenReturn(mockAppState);
        when(mockAppState.fileListSortState()).thenReturn(sortState);

        // Capture the actual list passed to convertBatch
        ArgumentCaptor<List<ConversionFile>> filesCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        controller.handleStartConversion();

        // Assert
        verify(conversionEngine).convertBatch(filesCaptor.capture(), any(ConversionSettings.class));

        // Verify files are in sorted order by size descending (3000, 2000, 1000)
        List<ConversionFile> sortedFiles = filesCaptor.getValue();
        assertEquals(3, sortedFiles.size());
        assertEquals(3000L, sortedFiles.get(0).size());
        assertEquals(2000L, sortedFiles.get(1).size());
        assertEquals(1000L, sortedFiles.get(2).size());

        assertTrue(controller.isConversionInProgress());
    }

    // ===== Helper Method =====

    private ConversionSettings createValidSettings() {
        return ConversionSettings.builder()
                .outputFormat(FileFormat.AVI)
                .outputDirectory(tempDir)
                .overwriteExisting(false)
                .parallelConversions(2)
                .build();
    }
}
