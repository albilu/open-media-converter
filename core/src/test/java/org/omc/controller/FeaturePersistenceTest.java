package org.omc.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omc.core.ConfigurationManager;
import org.omc.core.ConversionEngine;
import org.omc.core.ValidationEngine;
import org.omc.model.*;
import org.omc.service.FileHandler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FeaturePersistenceTest {
    @TempDir Path root;

    @Test
    void sessionRoundTripPreservesWindowFileIdentityAndCustomSettings() throws Exception {
        var configuration = new ConfigurationManager(root.resolve("config"), root.resolve("data"), root.resolve("cache"));
        var handler = new FileHandler(configuration);
        var validation = new ValidationEngine(handler);
        var settings = new SettingsManager(configuration, validation);
        var states = new StateManager(configuration);
        var files = new FileManager(handler, validation);
        Path input = Files.writeString(root.resolve("saved.png"), "image fixture");
        var override = FileSettingsOverride.forImage("Saved choice", ImageSettings.builder().quality(92).build());
        var file = ConversionFile.create(input, FileFormat.PNG, Files.size(input)).withSettingsOverride(override)
                .withStatus(ConversionStatus.IN_PROGRESS).withProgress(55);
        files.restoreFiles(List.of(file));
        var controller = new ApplicationWorkflowController(files, settings, states, mock(ConversionEngine.class));
        controller.initialize();
        WindowState geometry = new WindowState(1234, 876, 0, 0, true, false);
        controller.updateWindowState(geometry);
        assertTrue(controller.shutdown(true));

        var restoredFiles = new FileManager(handler, validation);
        var restored = new ApplicationWorkflowController(restoredFiles, new SettingsManager(configuration, validation),
                new StateManager(configuration), mock(ConversionEngine.class));
        restored.initialize();
        assertEquals(geometry, restored.getWindowState());
        ConversionFile actual = restored.getFileList().getFirst();
        assertEquals(file.id(), actual.id());
        assertEquals(override, actual.settingsOverride());
        assertEquals(ConversionStatus.PENDING, actual.status());
        assertEquals(0, actual.progress());
        assertNotNull(restored.getCurrentSettings().videoSettings());
        assertNotNull(restored.getCurrentSettings().audioSettings());
        assertNotNull(restored.getCurrentSettings().imageSettings());
        assertNotNull(restored.getCurrentSettings().documentSettings());
        restored.shutdown(true);
    }

    @Test
    void restoringCompletedFilesKeepsOutputAndDropsMissingInputs() throws Exception {
        var configuration = new ConfigurationManager(root.resolve("config"), root.resolve("data"), root.resolve("cache"));
        var handler = new FileHandler(configuration);
        var manager = new FileManager(handler, new ValidationEngine(handler));
        Path input = Files.writeString(root.resolve("present.png"), "input");
        Path output = Files.writeString(root.resolve("result.jpg"), "output");
        var complete = ConversionFile.create(input, FileFormat.PNG, 5).withStatus(ConversionStatus.COMPLETED)
                .withProgress(100).withOutputPath(output);
        var missing = ConversionFile.create(root.resolve("missing.png"), FileFormat.PNG, 5);
        manager.restoreFiles(List.of(complete, missing));
        assertEquals(List.of(complete), manager.getFiles());
        assertEquals(output, manager.getFiles().getFirst().outputPath().orElseThrow());
    }
}
