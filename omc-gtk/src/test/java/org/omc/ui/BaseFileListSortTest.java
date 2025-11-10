// filepath: src/test/java/org/omc/ui/BaseFileListSortTest.java

package org.omc.ui;

import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.omc.model.ConversionFile;
import org.omc.model.FileFormat;

/**
 * Base class for file list sort tests providing common utilities.
 */
abstract class BaseFileListSortTest {

    @TempDir
    Path tempDir;

    /**
     * Creates a ConversionFile for testing.
     * Note: Does not create actual file on disk, just creates a valid Path.
     */
    protected ConversionFile createFile(String fileName, long size, FileFormat format) {
        Path filePath = tempDir.resolve(fileName);
        return ConversionFile.create(filePath, format, size);
    }
}