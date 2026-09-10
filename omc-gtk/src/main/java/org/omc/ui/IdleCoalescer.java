package org.omc.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gnome.glib.GLib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coalesces rapid UI updates into a single GLib idle callback per flush.
 *
 * <p>
 * Engine callbacks (progress events) can fire many times per second from
 * background threads. Scheduling one {@code GLib.idleAdd} per event floods the
 * main loop with redundant redraws. This class keeps only the LATEST runnable
 * per coalescing key (typically a file id, or {@code "batch"} for batch
 * progress) and schedules a single idle callback that flushes all pending
 * updates at once.
 * </p>
 *
 * <p>
 * Terminal events (completions, status changes) are submitted via
 * {@link #submitForced(String, Runnable)}: they are queued in a separate list,
 * never replaced or dropped, and force a flush to be scheduled immediately so
 * they reach the UI without waiting for further progress updates.
 * </p>
 *
 * <p>
 * Thread-safe: submissions may arrive from any thread; the flush itself always
 * runs on the GTK main thread via the scheduler (GLib idle by default, which
 * can be replaced for unit testing).
 * </p>
 */
public final class IdleCoalescer {

    private static final Logger logger = LoggerFactory.getLogger(IdleCoalescer.class);

    /**
     * Schedules a flush task on the main loop. Implementations must eventually
     * run the task on the UI thread. Returning {@code false} means the task
     * could not be scheduled, in which case the caller runs it inline as a
     * last-resort fallback so updates are never lost.
     */
    @FunctionalInterface
    public interface IdleScheduler {
        boolean schedule(Runnable flush);
    }

    /**
     * Default scheduler: one-shot GLib idle callback at default priority.
     * Returning {@code false} from the callback means "do not repeat", matching
     * the codebase's existing idle pattern.
     */
    private static final IdleScheduler GLIB_IDLE_SCHEDULER = task -> {
        try {
            int sourceId = GLib.idleAdd(0, () -> {
                task.run();
                return false;
            });
            return sourceId != 0;
        } catch (Throwable t) {
            // GTK not available (tests, early shutdown): let the caller fall
            // back to inline execution instead of losing the update.
            logger.warn("GLib idle scheduling failed; falling back to inline flush", t);
            return false;
        }
    };

    private final IdleScheduler scheduler;

    // Latest runnable per coalescing key; LinkedHashMap keeps first-seen key
    // order so updates flush in arrival order across keys.
    private final Map<String, Runnable> coalesced = new LinkedHashMap<>();
    // Terminal events: never deduplicated, never dropped, FIFO.
    private final List<Runnable> forced = new ArrayList<>();
    private boolean flushScheduled;

    /**
     * Creates a coalescer that flushes on the GLib main loop.
     */
    public IdleCoalescer() {
        this(GLIB_IDLE_SCHEDULER);
    }

    /**
     * Creates a coalescer with a custom scheduler (for unit tests).
     *
     * @param scheduler the flush scheduler
     */
    IdleCoalescer(IdleScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Submits a coalescable update. Only the latest submission per key is kept;
     * earlier pending updates for the same key are dropped (they are stale by
     * the time the idle runs).
     *
     * @param key  coalescing key (e.g. file id, or {@code "batch"})
     * @param task the UI update to run on the main thread
     */
    public synchronized void submit(String key, Runnable task) {
        coalesced.put(key, task);
        scheduleFlush();
    }

    /**
     * Submits a terminal event (completion, status change) that must never be
     * dropped or coalesced away. Forces a flush to be scheduled immediately if
     * none is pending.
     *
     * @param key  identifying key (used only for debugging)
     * @param task the UI update to run on the main thread
     */
    public synchronized void submitForced(String key, Runnable task) {
        forced.add(task);
        scheduleFlush();
    }

    /**
     * Returns whether unflushed work exists (coalesced or forced).
     *
     * @return true if a flush would run pending work
     */
    public synchronized boolean hasPendingWork() {
        return !coalesced.isEmpty() || !forced.isEmpty();
    }

    /**
     * Drops all pending work. Used during shutdown to avoid scheduling UI
     * updates on a window that is being destroyed.
     */
    public synchronized void clear() {
        coalesced.clear();
        forced.clear();
        flushScheduled = false;
    }

    private void scheduleFlush() {
        if (flushScheduled) {
            return;
        }
        flushScheduled = true;
        boolean accepted = false;
        try {
            accepted = scheduler.schedule(this::flush);
        } finally {
            if (!accepted) {
                flushScheduled = false;
            }
        }
    }

    /**
     * Runs all pending work. Coalesced updates run first (in key arrival
     * order) so a terminal event always lands after the progress update it
     * supersedes.
     */
    private synchronized void flush() {
        flushScheduled = false;
        if (coalesced.isEmpty() && forced.isEmpty()) {
            return;
        }
        List<Runnable> toRun = new ArrayList<>(coalesced.size() + forced.size());
        toRun.addAll(coalesced.values());
        toRun.addAll(forced);
        coalesced.clear();
        forced.clear();

        for (Runnable task : toRun) {
            try {
                task.run();
            } catch (RuntimeException e) {
                // One failing update must not abort the remaining flush.
                logger.error("Error running coalesced UI update", e);
            }
        }
    }
}
