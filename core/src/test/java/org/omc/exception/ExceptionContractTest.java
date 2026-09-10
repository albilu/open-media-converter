package org.omc.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Contract tests for the exception hierarchy (P2 audit fixes):
 * null-safe detailed messages, no duplicated context, bounded tool output.
 */
class ExceptionContractTest {

    private static final int ONE_MB = 1024 * 1024;

    @Test
    void getDetailedMessage_withNullErrorCode_returnsPlainMessage() {
        MediaConverterException exception = new MediaConverterException("boom happened", null);

        // Must not NPE: the error code is optional per the constructor contract
        assertEquals("boom happened", exception.getDetailedMessage());
    }

    @Test
    void getDetailedMessage_withNullErrorCodeAndCause_returnsPlainMessage() {
        Throwable cause = new IllegalStateException("root cause");
        MediaConverterException exception = new MediaConverterException("boom", null, cause);

        assertEquals("boom", exception.getDetailedMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void getDetailedMessage_withErrorCode_includesCodePrefix() {
        MediaConverterException exception =
                new MediaConverterException("boom", ErrorCode.INTERNAL_ERROR);

        assertEquals("[ERR-9001] boom", exception.getDetailedMessage());
    }

    @Test
    void fileOperationException_contextDoesNotDuplicateFilePath() {
        Throwable cause = new java.io.IOException("disk full");
        FileOperationException exception = new FileOperationException(
                "cannot write output", ErrorCode.FILE_NOT_WRITABLE, "/tmp/out.mp4", cause);

        // The file path lives in the dedicated field only; the generic context
        // must stay null instead of carrying a duplicate copy.
        assertNull(exception.getContext(), "context must not duplicate filePath");
        assertEquals("/tmp/out.mp4", exception.getFilePath());
    }

    @Test
    void fileOperationException_detailedMessageStillShowsFilePath() {
        FileOperationException exception = new FileOperationException(
                "cannot write output", ErrorCode.FILE_NOT_WRITABLE, "/tmp/out.mp4");

        String detailed = exception.getDetailedMessage();

        assertTrue(detailed.contains("/tmp/out.mp4"), "detailed message should mention the path");
        assertEquals(1, countOccurrences(detailed, "/tmp/out.mp4"),
                "path should appear exactly once, not duplicated");
    }

    @Test
    void toolExecutionException_truncatesOutputBeyondOneMegabyte() {
        String hugeOutput = "x".repeat(2 * ONE_MB);

        ToolExecutionException exception = new ToolExecutionException(
                "ffmpeg failed", ErrorCode.TOOL_EXECUTION_FAILED, "ffmpeg",
                "/usr/bin/ffmpeg", 1, hugeOutput);

        String capped = exception.getToolOutput();
        assertTrue(capped.length() <= ONE_MB + 100,
                "output must be capped near 1MB but was " + capped.length());
        assertTrue(capped.startsWith("xxxx"), "cap must keep the head of the output");
        // Exact marker used by the services (leading and trailing newlines included)
        assertTrue(capped.endsWith("\n[Output truncated - exceeded 1MB limit]\n"),
                "cap must append a truncation marker, mirroring the services' convention");
    }

    @Test
    void toolExecutionException_withCause_truncatesOutputBeyondOneMegabyte() {
        String hugeOutput = "y".repeat(ONE_MB + 1);
        Throwable cause = new java.io.IOException("broken pipe");

        ToolExecutionException exception = new ToolExecutionException(
                "ffmpeg failed", ErrorCode.TOOL_EXECUTION_FAILED, "ffmpeg",
                "/usr/bin/ffmpeg", 1, hugeOutput, cause);

        assertSame(cause, exception.getCause());
        assertTrue(exception.getToolOutput().endsWith("\n[Output truncated - exceeded 1MB limit]\n"));
    }

    @Test
    void toolExecutionException_keepsShortOutputUnchanged() {
        String output = "Encoder error: invalid codec";

        ToolExecutionException exception = new ToolExecutionException(
                "ffmpeg failed", ErrorCode.TOOL_EXECUTION_FAILED, "ffmpeg",
                "/usr/bin/ffmpeg", 1, output);

        assertEquals(output, exception.getToolOutput());
    }

    @Test
    void toolExecutionException_acceptsNullOutput() {
        ToolExecutionException exception = new ToolExecutionException(
                "ffmpeg failed", ErrorCode.TOOL_EXECUTION_FAILED, "ffmpeg",
                "/usr/bin/ffmpeg", 1, null);

        assertNull(exception.getToolOutput());
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
