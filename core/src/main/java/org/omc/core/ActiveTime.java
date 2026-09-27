package org.omc.core;

import java.time.Duration;

/** Monotonic elapsed time that excludes intervals spent paused. */
public final class ActiveTime {
    private final long started = System.nanoTime();
    private long pausedAt;
    private long excluded;

    /** Freezes this clock; repeated calls have no effect. */
    public synchronized void pause() {
        if (pausedAt == 0) pausedAt = System.nanoTime();
    }

    /** Restarts this clock without charging paused time. */
    public synchronized void resume() {
        if (pausedAt != 0) {
            excluded += System.nanoTime() - pausedAt;
            pausedAt = 0;
        }
    }

    /** Returns elapsed execution time, excluding pauses. */
    public synchronized Duration elapsed() {
        return Duration.ofNanos((pausedAt == 0 ? System.nanoTime() : pausedAt) - started - excluded);
    }
}
