package org.omc.core;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.omc.model.ConversionTool;
import org.omc.model.ToolConfiguration;
import org.omc.service.ToolDiscovery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissingToolsStartupTest {
    @TempDir Path root;

    @Test
    void absentConvertersDisableCategoriesWithoutPreventingStartup() {
        try (var discovery = mockConstruction(ToolDiscovery.class,
                (mock, context) -> when(mock.discoverTools()).thenReturn(new ToolConfiguration()))) {
            var factory = new DependencyFactory(root.resolve("config"));
            try {
                assertNotNull(factory.createApplicationController());
                assertTrue(factory.getApplicationWorkflowController().getAvailableCategories().isEmpty());
                for (ConversionTool tool : ConversionTool.values()) {
                    assertFalse(factory.getToolManager().isToolAvailable(tool));
                }
            } finally {
                factory.shutdown();
            }
        }
    }
}
