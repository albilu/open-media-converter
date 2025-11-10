// filepath: src/main/java/org/omc/core/ProcessRegistry.java

package org.omc.core;

/**
 * Interface for registering and unregistering active conversion processes.
 * This allows the ConversionEngine to track and forcibly terminate processes
 * when cancellation is requested.
 * 
 * Requirements:
 * - REQ-004.2: Conversion cancellation support
 */
public interface ProcessRegistry {

    /**
     * Registers a process as active for a specific file conversion.
     * 
     * @param fileId  the file ID associated with this conversion
     * @param process the process to register
     */
    void registerProcess(String fileId, Process process);

    /**
     * Unregisters a process when conversion completes or is cancelled.
     * 
     * @param fileId the file ID associated with this conversion
     */
    void unregisterProcess(String fileId);

    /**
     * Creates a no-op process registry that ignores registration calls.
     * 
     * @return no-op registry
     */
    static ProcessRegistry noOp() {
        return new ProcessRegistry() {
            @Override
            public void registerProcess(String fileId, Process process) {
                // No-op
            }

            @Override
            public void unregisterProcess(String fileId) {
                // No-op
            }
        };
    }
}
