package org.omc.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** An owned Linux process group with race-free startup and suspend/resume. */
public final class ToolProcess extends Process {
    private final Process delegate;
    private final ActiveTime clock = new ActiveTime();
    private boolean held = true;
    private boolean paused;
    private boolean destroyed;

    private ToolProcess(Process delegate) {
        this.delegate = delegate;
        clock.pause();
    }

    /**
     * Starts a tool in a private session and registers it before allowing exec.
     * Arguments, working directory and redirects remain intact.
     */
    public static ToolProcess start(ProcessBuilder builder, String fileId, ProcessRegistry registry)
            throws IOException, InterruptedException {
        if (registry != null) registry.awaitRunning();
        Path executable = Path.of(builder.command().getFirst());
        if (executable.isAbsolute() && !Files.isExecutable(executable)) {
            throw new IOException("Converter is not executable: " + executable);
        }
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-cp", absoluteClassPath(),
                ToolProcessLauncher.class.getName()));
        command.addAll(builder.command());
        builder.command(command);
        // Parent JVM instrumentation/options must not attach to every launcher.
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        ToolProcess process = new ToolProcess(builder.start());
        boolean registered = false;
        boolean released = false;
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!process.ready()) {
                if (!process.isAlive() || System.nanoTime() >= deadline) {
                    throw new IOException("Converter launcher did not enter its private process group");
                }
                Thread.sleep(5);
            }
            if (fileId != null && registry != null) {
                registry.registerProcess(fileId, process);
                registered = true;
            }
            process.release();
            released = true;
            return process;
        } finally {
            if (!released) {
                process.destroyForcibly();
                if (registered) registry.unregisterProcess(fileId);
            }
        }
    }

    private static String absoluteClassPath() {
        return Stream.of(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                .map(entry -> Path.of(entry).toAbsolutePath().toString())
                .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
    }

    private boolean ready() throws IOException {
        if (!delegate.isAlive()) return false;
        String stat;
        try {
            stat = Files.readString(Path.of("/proc", Long.toString(pid()), "stat"));
        } catch (java.nio.file.NoSuchFileException exited) {
            return false;
        }
        String[] fields = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
        return fields[0].equals("T") && Long.parseLong(fields[2]) == pid();
    }

    private synchronized void release() {
        held = false;
        if (!paused && !destroyed) {
            signal(18);
            clock.resume();
        }
    }

    /** Suspends every process in the converter's owned group. */
    public synchronized void pause() {
        if (!paused && !destroyed) {
            signal(19);
            paused = true;
            clock.pause();
        }
    }

    /** Resumes the same converter processes without restarting conversion. */
    public synchronized void resume() {
        if (paused && !destroyed) {
            if (!held) signal(18);
            paused = false;
            if (!held) clock.resume();
        }
    }

    private void signal(int signal) {
        // Only this wrapper's session leader is ever used as a process group ID.
        if (delegate.isAlive() && ToolProcessLauncher.signal(-Math.toIntExact(pid()), signal) != 0
                && delegate.isAlive()) {
            throw new IllegalStateException("Cannot signal converter process group " + pid());
        }
    }

    /** Returns active execution time, excluding pauses. */
    public Duration activeElapsed() { return clock.elapsed(); }

    /** Returns whether this converter is suspended. */
    public synchronized boolean isPaused() { return paused; }

    /** Waits up to an active-time budget, excluding all paused intervals. */
    public boolean waitForActive(long timeout, TimeUnit unit) throws InterruptedException {
        long start = clock.elapsed().toNanos();
        while (isAlive() && clock.elapsed().toNanos() - start < unit.toNanos(timeout)) {
            if (delegate.waitFor(100, TimeUnit.MILLISECONDS)) return true;
        }
        return !isAlive();
    }

    @Override public synchronized Process destroyForcibly() {
        if (!destroyed) {
            destroyed = true;
            // SIGKILL also works on stopped processes; never resume on cancel.
            if (delegate.isAlive()) {
                ToolProcessLauncher.signal(-Math.toIntExact(pid()), 9);
                delegate.descendants().forEach(ProcessHandle::destroyForcibly);
                delegate.destroyForcibly();
            }
            clock.pause();
        }
        return this;
    }

    @Override public void destroy() { destroyForcibly(); }
    @Override public InputStream getInputStream() { return delegate.getInputStream(); }
    @Override public InputStream getErrorStream() { return delegate.getErrorStream(); }
    @Override public OutputStream getOutputStream() { return delegate.getOutputStream(); }
    @Override public int waitFor() throws InterruptedException { return delegate.waitFor(); }
    @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.waitFor(timeout, unit);
    }
    @Override public int exitValue() { return delegate.exitValue(); }
    @Override public boolean isAlive() { return delegate.isAlive(); }
    @Override public long pid() { return delegate.pid(); }
    @Override public ProcessHandle toHandle() { return delegate.toHandle(); }
    @Override public Stream<ProcessHandle> descendants() { return delegate.descendants(); }
    @Override public CompletableFuture<Process> onExit() { return delegate.onExit().thenApply(p -> this); }
}
