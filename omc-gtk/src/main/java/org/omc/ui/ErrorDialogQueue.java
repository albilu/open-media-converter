package org.omc.ui;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Pure (GTK-free) queue that caps the number of error dialogs shown at once.
 *
 * <p>
 * When a batch conversion fails, one error event per file can arrive within a
 * few idle cycles. Opening a window per error floods the screen and makes the
 * application unusable. This queue shows at most {@code maxConcurrent} dialogs;
 * excess dialogs wait in FIFO order and are shown as earlier ones close.
 * </p>
 *
 * <p>
 * Thread-safe because {@link ErrorDialog} may be called from any thread, even
 * though the GTK show-runnables themselves only ever run on the main thread
 * (they are invoked from inside GLib idle callbacks).
 * </p>
 */
final class ErrorDialogQueue {

    private final int maxConcurrent;
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private int active;

    /**
     * Creates a queue.
     *
     * @param maxConcurrent maximum number of dialogs shown at once (>= 1)
     * @throws IllegalArgumentException if maxConcurrent is less than 1
     */
    ErrorDialogQueue(int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent must be >= 1, got " + maxConcurrent);
        }
        this.maxConcurrent = maxConcurrent;
    }

    /**
     * Offers a dialog for display. Shown immediately while the concurrency cap
     * allows it; otherwise queued and shown when {@link #onDialogClosed()} is
     * called for an earlier dialog.
     *
     * @param showDialog runnable that shows the dialog (main thread)
     */
    synchronized void offer(Runnable showDialog) {
        if (active < maxConcurrent) {
            active++;
            showDialog.run();
        } else {
            pending.add(showDialog);
        }
    }

    /**
     * Notifies the queue that a shown dialog was closed, freeing a slot. Shows
     * the next queued dialog, if any. Spurious calls (no queued dialogs) are
     * ignored.
     */
    synchronized void onDialogClosed() {
        Runnable next = pending.poll();
        if (next != null) {
            next.run();
            return;
        }
        if (active > 0) {
            active--;
        }
    }

    /**
     * Returns the number of dialogs waiting to be shown.
     *
     * @return queued dialog count
     */
    synchronized int queuedCount() {
        return pending.size();
    }
}
