package org.omc.core;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Short-lived Linux launcher: creates a private session, stops for registration,
 * then replaces itself with the converter. Uses the bundled Java runtime so no
 * shell or external setsid executable is required.
 */
public final class ToolProcessLauncher {
    private ToolProcessLauncher() {}

    private static MethodHandle function(String name, FunctionDescriptor descriptor) {
        Linker linker = Linker.nativeLinker();
        return linker.downcallHandle(linker.defaultLookup().find(name).orElseThrow(), descriptor);
    }

    private static final MethodHandle KILL = function("kill",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

    static int signal(int pid, int signal) {
        try {
            return (int) KILL.invokeExact(pid, signal);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable impossible) {
            // A native downcall cannot throw a checked Java exception.
            throw new AssertionError(impossible);
        }
    }

    /** Creates an isolated process group and execs the supplied argument vector. */
    public static void main(String[] args) throws Throwable {
        if (args.length == 0) throw new IllegalArgumentException("Missing tool command");
        MethodHandle setsid = function("setsid", FunctionDescriptor.of(ValueLayout.JAVA_INT));
        if ((int) setsid.invokeExact() < 0) throw new IllegalStateException("Cannot isolate converter process");
        if (signal(Math.toIntExact(ProcessHandle.current().pid()), 19) != 0) {
            throw new IllegalStateException("Cannot hold converter for registration");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment argv = arena.allocate(ValueLayout.ADDRESS, args.length + 1L);
            for (int i = 0; i < args.length; i++) {
                argv.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(args[i]));
            }
            argv.setAtIndex(ValueLayout.ADDRESS, args.length, MemorySegment.NULL);
            MethodHandle exec = function("execvp", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            int result = (int) exec.invokeExact(argv.getAtIndex(ValueLayout.ADDRESS, 0), argv);
            throw new IllegalStateException("Cannot execute converter " + args[0] + " (" + result + ")");
        }
    }
}
