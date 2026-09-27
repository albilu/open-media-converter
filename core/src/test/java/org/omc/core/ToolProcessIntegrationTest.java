package org.omc.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real process groups, startup races, active time and stopped cancellation. */
@Timeout(20)
class ToolProcessIntegrationTest {
    @TempDir Path root;

    @Test
    void freezesDescendantsAndTimeoutThenResumesSameProcesses() throws Exception {
        Path heartbeat = root.resolve("heartbeat"), child = root.resolve("child");
        var builder = new ProcessBuilder("/bin/sh", "-c",
                "(while :; do echo tick >> \"$1\"; sleep .02; done) & echo $! > \"$2\"; wait",
                "fixture", heartbeat.toString(), child.toString());
        ToolProcess process = ToolProcess.start(builder, null, ProcessRegistry.noOp());
        try (var executor = Executors.newSingleThreadExecutor()) {
            await(() -> Files.exists(heartbeat) && Files.exists(child));
            long childPid = Long.parseLong(Files.readString(child).trim());
            process.pause();
            await(() -> stopped(process.pid()) && stopped(childPid));
            long bytes = Files.size(heartbeat);
            var elapsed = process.activeElapsed();
            var timeout = executor.submit(() -> process.waitForActive(200, TimeUnit.MILLISECONDS));
            Thread.sleep(450);
            assertEquals(bytes, Files.size(heartbeat));
            assertEquals(elapsed, process.activeElapsed());
            assertFalse(timeout.isDone(), "Paused time must not consume the process timeout");
            process.resume();
            assertFalse(timeout.get(2, TimeUnit.SECONDS));
            assertTrue(Files.size(heartbeat) > bytes);
            assertTrue(ProcessHandle.of(childPid).orElseThrow().isAlive(), "The same child resumes");
            process.pause();
            await(() -> stopped(process.pid()) && stopped(childPid));
            process.destroyForcibly();
            assertTrue(process.waitFor(2, TimeUnit.SECONDS));
            await(() -> dead(childPid));
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void registrationCanPauseBeforeAnyToolInstructionExecutes() throws Exception {
        Path marker = root.resolve("executed");
        var registry = new ProcessRegistry() {
            public void registerProcess(String id, Process process) { ((ToolProcess) process).pause(); }
            public void unregisterProcess(String id) { }
        };
        var process = ToolProcess.start(new ProcessBuilder("/usr/bin/touch", marker.toString()), "file", registry);
        try {
            Thread.sleep(150);
            assertTrue(stopped(process.pid()));
            assertFalse(Files.exists(marker), "Tool code must not run before registration releases it");
            process.resume();
            assertTrue(process.waitFor(2, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
            assertTrue(Files.exists(marker));
        } finally { process.destroyForcibly(); }
    }

    @Test
    void cancellationDuringRegistrationCannotLeakOrExecuteTheTool() throws Exception {
        Path marker = root.resolve("executed");
        AtomicReference<Process> owned = new AtomicReference<>();
        var registry = new ProcessRegistry() {
            public void registerProcess(String id, Process process) {
                owned.set(process);
                throw new CancellationException("Cancelled during startup");
            }
            public void unregisterProcess(String id) { }
        };
        assertThrows(CancellationException.class, () -> ToolProcess.start(
                new ProcessBuilder("/usr/bin/touch", marker.toString()), "file", registry));
        assertTrue(owned.get().waitFor(2, TimeUnit.SECONDS));
        assertFalse(Files.exists(marker));
    }

    private static boolean stopped(long pid) throws java.io.IOException {
        String stat = Files.readString(Path.of("/proc", Long.toString(pid), "stat"));
        return stat.substring(stat.lastIndexOf(')') + 2).startsWith("T ");
    }

    private static boolean dead(long pid) throws java.io.IOException {
        Path stat = Path.of("/proc", Long.toString(pid), "stat");
        if (!Files.exists(stat)) return true;
        return Files.readString(stat).contains(") Z ");
    }

    private static void await(Check condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.get() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(condition.get());
    }

    @FunctionalInterface private interface Check { boolean get() throws Exception; }
}
