package org.omc.exception;

/**
 * Exception thrown when external tool operations fail.
 * Examples: tool not found, tool execution failed, incompatible version.
 * 
 * Requirements: REQ-007.1, REQ-004.1, REQ-004.2
 */
public class ToolExecutionException extends MediaConverterException {
    private final String toolName;
    private final String toolPath;
    private final Integer exitCode;
    private final String toolOutput;

    /**
     * Creates a ToolExecutionException with tool name.
     *
     * @param message   The error message
     * @param errorCode The error code
     * @param toolName  The name of the tool that failed
     */
    public ToolExecutionException(String message, ErrorCode errorCode, String toolName) {
        super(message, errorCode);
        this.toolName = toolName;
        this.toolPath = null;
        this.exitCode = null;
        this.toolOutput = null;
    }

    /**
     * Creates a ToolExecutionException with full tool context.
     *
     * @param message    The error message
     * @param errorCode  The error code
     * @param toolName   The name of the tool that failed
     * @param toolPath   The path to the tool executable
     * @param exitCode   The tool exit code
     * @param toolOutput The tool output/error messages
     */
    public ToolExecutionException(String message, ErrorCode errorCode, String toolName,
            String toolPath, Integer exitCode, String toolOutput) {
        super(message, errorCode);
        this.toolName = toolName;
        this.toolPath = toolPath;
        this.exitCode = exitCode;
        this.toolOutput = toolOutput;
    }

    /**
     * Creates a ToolExecutionException with full context and cause.
     *
     * @param message    The error message
     * @param errorCode  The error code
     * @param toolName   The name of the tool that failed
     * @param toolPath   The path to the tool executable
     * @param exitCode   The tool exit code
     * @param toolOutput The tool output/error messages
     * @param cause      The underlying cause
     */
    public ToolExecutionException(String message, ErrorCode errorCode, String toolName,
            String toolPath, Integer exitCode, String toolOutput,
            Throwable cause) {
        super(message, errorCode, cause);
        this.toolName = toolName;
        this.toolPath = toolPath;
        this.exitCode = exitCode;
        this.toolOutput = toolOutput;
    }

    /**
     * Gets the name of the tool that failed.
     *
     * @return The tool name
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * Gets the path to the tool executable.
     *
     * @return The tool path, or null if not set
     */
    public String getToolPath() {
        return toolPath;
    }

    /**
     * Gets the tool exit code.
     *
     * @return The exit code, or null if not available
     */
    public Integer getExitCode() {
        return exitCode;
    }

    /**
     * Gets the tool output/error messages.
     *
     * @return The tool output, or null if not available
     */
    public String getToolOutput() {
        return toolOutput;
    }

    @Override
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] %s", getErrorCode().getCode(), getMessage()));

        if (toolName != null) {
            sb.append(String.format("\n  Tool: %s", toolName));
        }
        if (toolPath != null) {
            sb.append(String.format("\n  Path: %s", toolPath));
        }
        if (exitCode != null) {
            sb.append(String.format("\n  Exit Code: %d", exitCode));
        }
        if (toolOutput != null && !toolOutput.isBlank()) {
            // Truncate long output
            String output = toolOutput.length() > 500
                    ? toolOutput.substring(0, 500) + "..."
                    : toolOutput;
            sb.append(String.format("\n  Output: %s", output));
        }

        return sb.toString();
    }
}
